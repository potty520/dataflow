package com.dgp.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量代理隧道：支持 DIRECT / SOCKS5 / HTTP(CONNECT) 三种方式访问不同网络的数据库与 FTP。
 *
 * <p>为什么需要它：JDBC 驱动与 FTP 客户端都不直接支持 SOCKS/HTTP 代理，标准做法是
 * 先建立一个"本地端口 → 代理 → 远端 host:port"的转发通道，再让驱动连接 127.0.0.1:本地端口。
 *
 * <p>实现零外部依赖（SOCKS5 与 HTTP CONNECT 均手工握手），线程安全：每个连接独立 Socket。
 */
public final class ProxyTunnel {

    private ProxyTunnel() {
    }

    /** 通过代理（或直连）建立到 targetHost:targetPort 的 Socket。 */
    public static Socket open(String proxyType, String proxyHost, int proxyPort,
                              String proxyUser, String proxyPass,
                              String targetHost, int targetPort, int timeoutMs) throws IOException {
        String type = proxyType == null ? "DIRECT" : proxyType.toUpperCase();
        if ("DIRECT".equals(type) || proxyHost == null || proxyHost.isBlank()) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            s.setSoTimeout(timeoutMs);
            return s;
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(proxyHost, proxyPort), timeoutMs);
        s.setSoTimeout(timeoutMs);
        if ("SOCKS5".equals(type)) {
            socks5(s, proxyUser, proxyPass, targetHost, targetPort);
        } else {
            httpConnect(s, proxyUser, proxyPass, targetHost, targetPort);
        }
        s.setSoTimeout(0);
        return s;
    }

    // ---------------------------------------------------------------- SOCKS5 (RFC1928)
    private static void socks5(Socket s, String user, String pass, String host, int port) throws IOException {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        boolean auth = user != null && !user.isBlank();
        out.write(new byte[]{0x05, (byte) (auth ? 2 : 1), (byte) (auth ? 0x02 : 0x00)});
        out.flush();
        int ver = readByte(in);
        int method = readByte(in);
        if (ver != 5 || method == 0xFF) throw new IOException("SOCKS5 代理不支持所选认证方式");
        if (method == 0x02) {
            byte[] u = user.getBytes(StandardCharsets.UTF_8);
            byte[] p = (pass == null ? "" : pass).getBytes(StandardCharsets.UTF_8);
            out.write(0x01);
            out.write(u.length);
            out.write(u);
            out.write(p.length);
            out.write(p);
            out.flush();
            readByte(in);
            int st = readByte(in);
            if (st != 0) throw new IOException("SOCKS5 用户名/口令认证失败");
        }
        byte[] h = host.getBytes(StandardCharsets.UTF_8);
        out.write(new byte[]{0x05, 0x01, 0x00, 0x03, (byte) h.length});
        out.write(h);
        out.write((port >> 8) & 0xFF);
        out.write(port & 0xFF);
        out.flush();
        readByte(in);
        int rep = readByte(in);
        readByte(in); // reserved
        int atyp = readByte(in);
        int skip;
        switch (atyp) {
            case 0x01 -> skip = 4;
            case 0x03 -> skip = readByte(in);
            case 0x04 -> skip = 16;
            default -> throw new IOException("SOCKS5 返回未知地址类型");
        }
        skipFully(in, skip + 2);
        if (rep != 0) throw new IOException("SOCKS5 连接被拒绝，code=" + rep);
    }

    // ---------------------------------------------------------------- HTTP CONNECT
    private static void httpConnect(Socket s, String user, String pass, String host, int port) throws IOException {
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        StringBuilder sb = new StringBuilder();
        sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append(':').append(port).append("\r\n");
        if (user != null && !user.isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (user + ":" + (pass == null ? "" : pass)).getBytes(StandardCharsets.UTF_8));
            sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n");
        }
        sb.append("Proxy-Connection: Keep-Alive\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        String status = readLine(in);
        if (status == null || !status.contains(" 200")) {
            throw new IOException("HTTP 代理 CONNECT 失败：" + status);
        }
        // 读完响应头
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            // skip headers
        }
    }

    // ---------------------------------------------------------------- 本地转发器（供 JDBC/FTP 使用）
    public static final class Forwarder implements AutoCloseable {
        private final ServerSocket server;
        private final Thread acceptThread;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Forwarder(ServerSocket server, String targetHost, int targetPort, String proxyType,
                          String proxyHost, int proxyPort, String proxyUser, String proxyPass, int timeoutMs) {
            this.server = server;
            this.acceptThread = new Thread(() -> {
                while (!closed.get()) {
                    try {
                        Socket client = server.accept();
                        Thread t = new Thread(() -> {
                            try (client) {
                                Socket remote = ProxyTunnel.open(proxyType, proxyHost, proxyPort, proxyUser,
                                        proxyPass, targetHost, targetPort, timeoutMs);
                                try (remote) {
                                    Thread up = pump(client.getInputStream(), remote.getOutputStream());
                                    Thread down = pump(remote.getInputStream(), client.getOutputStream());
                                    up.join();
                                    down.join();
                                }
                            } catch (Exception ignore) {
                                // 单个连接失败不影响转发器
                            }
                        }, "dgp-tunnel-" + targetHost);
                        t.setDaemon(true);
                        t.start();
                    } catch (IOException e) {
                        if (!closed.get()) {
                            // accept 失败时退出（通常为关闭）
                            return;
                        }
                    }
                }
            }, "dgp-tunnel-accept-" + targetHost);
            this.acceptThread.setDaemon(true);
        }

        public int port() {
            return server.getLocalPort();
        }

        private Thread pump(InputStream in, OutputStream out) {
            Thread t = new Thread(() -> {
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                } catch (IOException ignore) {
                    // 连接关闭
                }
            });
            t.setDaemon(true);
            t.start();
            return t;
        }

        @Override
        public void close() {
            closed.set(true);
            try {
                server.close();
            } catch (IOException ignore) {
                // ignore
            }
            acceptThread.interrupt();
        }
    }

    /** 启动本地转发通道：返回的 Forwarder.port() 可直接用于 jdbc:mysql://127.0.0.1:port/… */
    public static Forwarder forward(String targetHost, int targetPort, String proxyType, String proxyHost,
                                    int proxyPort, String proxyUser, String proxyPass, int timeoutMs) throws IOException {
        ServerSocket ss = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
        Forwarder f = new Forwarder(ss, targetHost, targetPort, proxyType, proxyHost, proxyPort,
                proxyUser, proxyPass, timeoutMs);
        f.acceptThread.start();
        return f;
    }

    // ---------------------------------------------------------------- IO helpers
    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("代理连接被关闭");
        return b;
    }

    private static void skipFully(InputStream in, int n) throws IOException {
        int left = n;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) throw new IOException("代理响应不完整");
                skipped = 1;
            }
            left -= (int) skipped;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 && c < 0 ? null : sb.toString();
    }
}

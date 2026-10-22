package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * FTP 文件传输（明文 FTP）：直连或经 SOCKS5 / HTTP 代理。
 *
 * <p>代理支持：通过自定义 {@link SocketFactory} 让控制连接与数据连接都走 {@link ProxyTunnel}，
 * 从而可访问不同网络的文件服务器。
 */
@Service
public class FtpTransferService {

    private static final Logger log = LoggerFactory.getLogger(FtpTransferService.class);
    private static final int TIMEOUT = 15_000;

    /** 建连并登录。task 需包含 remoteHost/remotePort/remoteUser/remotePasswordEnc 与 proxy* 字段。 */
    public FTPClient connect(Map<String, Object> task, String decryptedPassword) {
        String host = Db.str(task.get("remoteHost"));
        if (host == null || host.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "FTP 主机不能为空");
        int port = task.get("remotePort") == null ? 21 : Db.num(task.get("remotePort")).intValue();
        String proxyType = Db.str(task.get("proxyType"));
        String proxyHost = Db.str(task.get("proxyHost"));
        Integer proxyPort = task.get("proxyPort") == null ? null : Db.num(task.get("proxyPort")).intValue();
        boolean useProxy = proxyHost != null && !proxyHost.isBlank() && proxyPort != null
                && !"DIRECT".equalsIgnoreCase(proxyType);
        String pUser = Db.str(task.get("proxyUser"));
        String pPass = Db.str(task.get("proxyPassword"));
        try {
            FTPClient ftp = new FTPClient();
            if (useProxy) {
                ftp.setSocketFactory(new TunnelSocketFactory(proxyType, proxyHost, proxyPort, pUser, pPass));
            }
            ftp.setConnectTimeout(TIMEOUT);
            ftp.setDefaultTimeout(TIMEOUT);
            ftp.setDataTimeout(java.time.Duration.ofSeconds(60));
            ftp.connect(host, port);
            ftp.setSoTimeout(TIMEOUT);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 连接被拒绝：" + ftp.getReplyString());
            }
            if (!ftp.login(Db.str(task.get("remoteUser")), decryptedPassword)) {
                throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 登录失败：" + ftp.getReplyString());
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.setControlKeepAliveTimeout(java.time.Duration.ofSeconds(30));
            return ftp;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL,
                    "FTP 连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    public Map<String, Object> test(Map<String, Object> task, String password) {
        long t0 = System.currentTimeMillis();
        FTPClient ftp = connect(task, password);
        try {
            String[] files = ftp.listNames();
            String pwd = ftp.printWorkingDirectory();
            return com.dgp.common.M.of("ok", true, "latencyMs", System.currentTimeMillis() - t0,
                    "workingDir", pwd, "fileCount", files == null ? 0 : files.length,
                    "welcome", ftp.getReplyString() == null ? "" : ftp.getReplyString().trim());
        } catch (IOException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 测试失败：" + e.getMessage());
        } finally {
            try {
                ftp.disconnect();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    /** 上传本地文件到 FTP（自动创建目录）。 */
    public void upload(FTPClient ftp, Path local, String remoteDir, String remoteFile) {
        try {
            if (remoteDir != null && !remoteDir.isBlank() && !"/".equals(remoteDir)) {
                mkdirs(ftp, remoteDir);
                if (!ftp.changeWorkingDirectory(remoteDir)) {
                    throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 切换目录失败：" + remoteDir);
                }
            }
            try (InputStream in = Files.newInputStream(local)) {
                if (!ftp.storeFile(remoteFile, in)) {
                    throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 上传失败：" + ftp.getReplyString());
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 上传异常：" + e.getMessage());
        }
    }

    /** 从 FTP 下载文件到本地路径。 */
    public void download(FTPClient ftp, String remoteDir, String remoteFile, Path local) {
        try {
            if (remoteDir != null && !remoteDir.isBlank() && !"/".equals(remoteDir)) {
                if (!ftp.changeWorkingDirectory(remoteDir)) {
                    throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 切换目录失败：" + remoteDir);
                }
            }
            try (OutputStream out = Files.newOutputStream(local)) {
                if (!ftp.retrieveFile(remoteFile, out)) {
                    throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 下载失败：" + ftp.getReplyString());
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "FTP 下载异常：" + e.getMessage());
        }
    }

    private void mkdirs(FTPClient ftp, String dir) throws IOException {
        String cur = ftp.printWorkingDirectory();
        try {
            String[] parts = dir.split("/");
            for (String p : parts) {
                if (p.isBlank()) continue;
                if (!ftp.changeWorkingDirectory(p)) {
                    if (!ftp.makeDirectory(p)) {
                        // 目录可能并发创建，忽略
                    }
                    ftp.changeWorkingDirectory(p);
                }
            }
        } finally {
            if (cur != null) ftp.changeWorkingDirectory(cur);
        }
    }

    /** 走代理的 SocketFactory（控制与数据连接都经代理）。 */
    private static final class TunnelSocketFactory extends SocketFactory {
        private final String proxyType;
        private final String proxyHost;
        private final int proxyPort;
        private final String proxyUser;
        private final String proxyPass;

        TunnelSocketFactory(String proxyType, String proxyHost, int proxyPort, String user, String pass) {
            this.proxyType = proxyType;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUser = user;
            this.proxyPass = pass;
        }

        private Socket create(String host, int port) throws IOException {
            return ProxyTunnel.open(proxyType, proxyHost, proxyPort, proxyUser, proxyPass, host, port, TIMEOUT);
        }

        @Override
        public Socket createSocket() {
            return new Socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return create(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return create(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return create(host.getHostAddress(), port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return create(address.getHostAddress(), port);
        }
    }
}

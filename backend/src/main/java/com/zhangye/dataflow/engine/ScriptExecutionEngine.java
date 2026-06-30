package com.zhangye.dataflow.engine;

import com.zhangye.dataflow.entity.AggDatasource;
import com.zhangye.dataflow.entity.DevScript;
import com.zhangye.dataflow.entity.DevScriptExecutionLog;
import com.zhangye.dataflow.mapper.AggDatasourceMapper;
import com.zhangye.dataflow.mapper.DevScriptExecutionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ScriptExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutionEngine.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final Path TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), "dataflow-scripts").toPath();

    private final DevScriptExecutionLogMapper executionLogMapper;
    private final AggDatasourceMapper datasourceMapper;

    public ScriptExecutionEngine(DevScriptExecutionLogMapper executionLogMapper,
                                 AggDatasourceMapper datasourceMapper) {
        this.executionLogMapper = executionLogMapper;
        this.datasourceMapper = datasourceMapper;
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            log.error("Failed to create temp dir: {}", TEMP_DIR, e);
        }
    }

    @Async
    public CompletableFuture<DevScriptExecutionLog> executeScript(DevScript script) {
        return executeScript(script, "MANUAL");
    }

    @Async
    public CompletableFuture<DevScriptExecutionLog> executeScript(DevScript script, String triggerBy) {
        long startTime = System.currentTimeMillis();
        DevScriptExecutionLog execLog = new DevScriptExecutionLog();
        execLog.setScriptId(script.getId());
        execLog.setTenantId(script.getTenantId());
        execLog.setStatus("RUNNING");
        execLog.setTriggerBy(triggerBy != null ? triggerBy : "MANUAL");
        execLog.setCreateTime(LocalDateTime.now());
        executionLogMapper.insert(execLog);

        try {
            ExecutionResult result;
            String scriptType = script.getScriptType();
            if (scriptType == null) scriptType = "SQL";

            switch (scriptType.toUpperCase()) {
                case "SHELL":
                    result = executeShell(script.getContent(), DEFAULT_TIMEOUT_SECONDS);
                    break;
                case "PYTHON":
                case "PYSPARK":
                    result = executePython(script.getContent(), DEFAULT_TIMEOUT_SECONDS);
                    break;
                case "HIVE_SQL":
                case "JDBC_SQL":
                case "SQL":
                case "SPARK_SQL":
                    result = executeJdbcSql(script);
                    break;
                default:
                    result = new ExecutionResult(-1, "", "Unsupported script type: " + scriptType);
            }

            long duration = System.currentTimeMillis() - startTime;
            execLog.setExitCode(result.exitCode);
            execLog.setOutput(result.output);
            execLog.setErrorLog(result.error);
            execLog.setDurationMs(duration);
            execLog.setStatus(result.exitCode == 0 ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            log.error("Script execution failed: scriptId={}", script.getId(), e);
            long duration = System.currentTimeMillis() - startTime;
            execLog.setExitCode(-1);
            execLog.setErrorLog(getStackTrace(e));
            execLog.setDurationMs(duration);
            execLog.setStatus("FAILED");
        }

        executionLogMapper.updateById(execLog);
        return CompletableFuture.completedFuture(execLog);
    }

    private ExecutionResult executeShell(String content, long timeoutSeconds) throws Exception {
        Path scriptFile = TEMP_DIR.resolve("script_" + System.nanoTime() + ".sh");
        try {
            Files.write(scriptFile, content.getBytes("UTF-8"));
            scriptFile.toFile().setExecutable(true);

            ProcessBuilder pb = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString());
            pb.directory(TEMP_DIR.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, stdout, "Process timed out after " + timeoutSeconds + "s\n" + stderr);
            }

            int exitCode = process.exitValue();
            return new ExecutionResult(exitCode, stdout, stderr);
        } finally {
            try { Files.deleteIfExists(scriptFile); } catch (IOException ignored) {}
        }
    }

    private ExecutionResult executePython(String content, long timeoutSeconds) throws Exception {
        Path scriptFile = TEMP_DIR.resolve("script_" + System.nanoTime() + ".py");
        try {
            Files.write(scriptFile, content.getBytes("UTF-8"));

            ProcessBuilder pb = new ProcessBuilder("python3", scriptFile.toAbsolutePath().toString());
            pb.directory(TEMP_DIR.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, stdout, "Process timed out after " + timeoutSeconds + "s\n" + stderr);
            }

            int exitCode = process.exitValue();
            return new ExecutionResult(exitCode, stdout, stderr);
        } finally {
            try { Files.deleteIfExists(scriptFile); } catch (IOException ignored) {}
        }
    }

    private ExecutionResult executeJdbcSql(DevScript script) {
        AggDatasource ds = null;
        if (script.getDatasourceId() != null) {
            ds = datasourceMapper.selectById(script.getDatasourceId());
        }
        if (ds == null) {
            // Fallback: try to find any available datasource for the tenant
            ds = datasourceMapper.selectList(null).stream()
                    .filter(d -> d.getTenantId().equals(script.getTenantId()))
                    .findFirst().orElse(null);
        }
        if (ds == null) {
            return new ExecutionResult(-1, "", "No datasource configured for script execution");
        }

        try {
            Connection conn = getConnection(ds);
            try {
                StringBuilder output = new StringBuilder();
                String content = script.getContent();
                if (content == null || content.trim().isEmpty()) {
                    return new ExecutionResult(0, "Empty script content, nothing to execute", "");
                }

                // Split by semicolons for multi-statement execution
                String[] statements = content.split(";");
                for (String stmt : statements) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty()) continue;

                    try (Statement jdbcStmt = conn.createStatement()) {
                        boolean hasResultSet = jdbcStmt.execute(trimmed);
                        if (hasResultSet) {
                            try (ResultSet rs = jdbcStmt.getResultSet()) {
                                ResultSetMetaData meta = rs.getMetaData();
                                int colCount = meta.getColumnCount();
                                int rowCount = 0;
                                while (rs.next() && rowCount < 100) {
                                    StringBuilder row = new StringBuilder();
                                    for (int i = 1; i <= colCount; i++) {
                                        if (i > 1) row.append("\t");
                                        row.append(rs.getString(i));
                                    }
                                    output.append(row).append("\n");
                                    rowCount++;
                                }
                                output.append("Rows returned: ").append(rowCount).append("+\n");
                            }
                        } else {
                            int updateCount = jdbcStmt.getUpdateCount();
                            output.append("Affected rows: ").append(updateCount).append("\n");
                        }
                    }
                }
                return new ExecutionResult(0, output.toString(), "");
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.error("JDBC execution failed", e);
            return new ExecutionResult(-1, "", getStackTrace(e));
        }
    }

    private Connection getConnection(AggDatasource ds) throws SQLException {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        String url;
        String driver;

        switch (dbType) {
            case "mysql":
                driver = "com.mysql.cj.jdbc.Driver";
                url = "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                        + "?useSSL=false&serverTimezone=Asia/Shanghai";
                break;
            case "postgresql":
                driver = "org.postgresql.Driver";
                url = "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName();
                break;
            case "h2":
                driver = "org.h2.Driver";
                url = "jdbc:h2:" + (ds.getFilePath() != null ? ds.getFilePath() : ds.getDatabaseName());
                break;
            case "oracle":
                driver = "oracle.jdbc.OracleDriver";
                url = "jdbc:oracle:thin:@" + ds.getHost() + ":" + ds.getPort() + ":" + ds.getDatabaseName();
                break;
            case "sqlserver":
                driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                url = "jdbc:sqlserver://" + ds.getHost() + ":" + ds.getPort() + ";databaseName=" + ds.getDatabaseName();
                break;
            default:
                driver = "com.mysql.cj.jdbc.Driver";
                url = "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                        + "?useSSL=false&serverTimezone=Asia/Shanghai";
        }

        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            log.warn("Driver {} not found, using default", driver);
        }

        return DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static class ExecutionResult {
        public final int exitCode;
        public final String output;
        public final String error;

        public ExecutionResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output != null ? output : "";
            this.error = error != null ? error : "";
        }
    }
}

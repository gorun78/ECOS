package com.chinacreator.gzcm.workspace.workbook;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Python 运行时 — 通过 ProcessBuilder 调用 python3。
 * <p>
 * 限制：
 * <ul>
 *   <li>30 秒超时</li>
 *   <li>stdout 收集结果（期望 JSON）</li>
 *   <li>工作目录: data/workbooks/{sessionId}/</li>
 * </ul>
 */
@Component
public class PythonRuntime {

    private static final Logger log = LoggerFactory.getLogger(PythonRuntime.class);

    /** 执行超时（秒） */
    private static final int TIMEOUT_SECONDS = 30;

    /** 内存限制: 512MB (虚拟地址空间，字节) */
    private static final long MEMORY_LIMIT_BYTES = 512L * 1024L * 1024L;

    /** stdout 读取上限: 10MB (字符数) */
    private static final int STDOUT_MAX_CHARS = 10 * 1024 * 1024;

    /** Python 解释器命令 */
    private static final String PYTHON_CMD = "python3";

    /**
     * 执行 Python 代码。
     *
     * @param code      Python 源代码
     * @param sessionId 会话 ID（用于工作目录隔离）
     * @return {stdout: "...", stderr: "...", exit_code: N, elapsed_ms: N, timed_out: bool}
     */
    public Map<String, Object> execute(String code, String sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        if (code == null || code.isBlank()) {
            result.put("error", "Python code is empty");
            result.put("elapsed_ms", 0);
            return result;
        }

        try {
            // 创建工作目录
            String workDirPath = "data/workbooks/" + (sessionId != null && !sessionId.isBlank() ? sessionId : "default");
            Path workDir = Paths.get(workDirPath);
            Files.createDirectories(workDir);

            // 将代码写入临时文件（避免 shell 转义问题）
            Path scriptFile = workDir.resolve("_script.py");
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

            // 构建进程（通过 prlimit 限制内存为 512MB）
            ProcessBuilder pb = new ProcessBuilder("prlimit", "--as=" + MEMORY_LIMIT_BYTES,
                    PYTHON_CMD, scriptFile.toAbsolutePath().toString());
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // 读取 stdout 和 stderr
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stdout.length() + line.length() + 1 > STDOUT_MAX_CHARS) {
                            stdout.append("[stdout truncated at 10MB limit]");
                            break;
                        }
                        stdout.append(line).append("\n");
                    }
                } catch (Exception ignored) {
                }
            });

            Thread errThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {
                }
            });

            outThread.start();
            errThread.start();

            // 等待进程结束，带超时
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                outThread.join(1000);
                errThread.join(1000);
                result.put("timed_out", true);
                result.put("error", "Execution timed out after " + TIMEOUT_SECONDS + " seconds");
                result.put("elapsed_ms", elapsed);
                result.put("stdout", stdout.toString().trim());
                result.put("stderr", stderr.toString().trim());
            } else {
                outThread.join(1000);
                errThread.join(1000);
                int exitCode = process.exitValue();
                result.put("exit_code", exitCode);
                result.put("timed_out", false);
                result.put("elapsed_ms", elapsed);
                result.put("stdout", stdout.toString().trim());
                result.put("stderr", stderr.toString().trim());
                if (exitCode != 0 && stderr.length() > 0) {
                    result.put("error", stderr.toString().trim());
                }
            }

            // 清理临时脚本
            try {
                Files.deleteIfExists(scriptFile);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Python execution error: {}", e.getMessage(), e);
            result.put("error", "Python runtime error: " + e.getMessage());
            result.put("elapsed_ms", elapsed);
        }

        return result;
    }
}

package com.chinacreator.gzcm.workspace.workbook;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * R 运行时 — 探测性实现。
 * <p>
 * 启动时探测 {@code which Rscript}：
 * <ul>
 *   <li>不存在时，返回友好提示</li>
 *   <li>存在时，通过 ProcessBuilder 执行 Rscript</li>
 * </ul>
 */
@Component
public class RRuntime {

    private static final Logger log = LoggerFactory.getLogger(RRuntime.class);

    private static final String RSCRIPT_CMD = "Rscript";
    private static final int TIMEOUT_SECONDS = 30;

    private volatile boolean available = false;
    private String rscriptPath;

    @PostConstruct
    public void probe() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", RSCRIPT_CMD);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    rscriptPath = reader.readLine();
                    if (rscriptPath != null && !rscriptPath.isBlank()) {
                        available = true;
                        log.info("R runtime detected at: {}", rscriptPath);
                    }
                }
            } else {
                log.info("R runtime not found. Install with: sudo apt install r-base");
            }
        } catch (Exception e) {
            log.info("R runtime probe failed: {}. Install with: sudo apt install r-base", e.getMessage());
        }
    }

    /**
     * 返回 R 运行时可用性信息。
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 获取可用性详情。
     */
    public Map<String, Object> availabilityInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("available", available);
        if (available) {
            info.put("path", rscriptPath);
        } else {
            info.put("message", "R 运行时未安装，请执行: sudo apt install r-base");
        }
        return info;
    }

    /**
     * 执行 R 代码。
     *
     * @param code R 源代码
     * @return 执行结果 Map
     */
    public Map<String, Object> execute(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        // 不可用时返回友好提示
        if (!available) {
            result.put("available", false);
            result.put("message", "R 运行时未安装，请执行: sudo apt install r-base");
            result.put("elapsed_ms", 0);
            return result;
        }

        if (code == null || code.isBlank()) {
            result.put("available", true);
            result.put("error", "R code is empty");
            result.put("elapsed_ms", 0);
            return result;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(rscriptPath, "-e", code);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                result.put("timed_out", true);
                result.put("error", "R execution timed out after " + TIMEOUT_SECONDS + " seconds");
                result.put("elapsed_ms", elapsed);
            } else {
                result.put("exit_code", process.exitValue());
                result.put("stdout", output.toString().trim());
                result.put("elapsed_ms", elapsed);
                result.put("available", true);
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("R execution error: {}", e.getMessage(), e);
            result.put("available", true);
            result.put("error", "R runtime error: " + e.getMessage());
            result.put("elapsed_ms", elapsed);
        }

        return result;
    }
}

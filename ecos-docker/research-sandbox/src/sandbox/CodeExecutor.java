package sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 受控代码执行器（Phase 1 代码进化能力，2026-11-25 用户授权）。
 *
 * <p><b>安全模型是分层的，主防线不是静态检查</b>。静态检查只做「防呆」，不是安全边界：</p>
 * <ol>
 *   <li><b>主防线 = 容器</b>：无网络（DNS 黑洞 + 实测外网 000）、只读根文件系统、
 *       cap-drop ALL、no-new-privileges、非 root(10001)、内存与 PID 配额、
 *       无 docker socket、无宿主卷。即便代码完全逃过静态检查，它也拿不到出网或写盘能力。</li>
 *   <li><b>进程 = 一次性</b>：每次执行起独立 {@code javac}/{@code java} 子进程，
 *       执行完即销毁；<b>超时到点强杀</b>（先 destroy，再 destroyForcibly）。</li>
 *   <li><b>资源</b>：JVM 侧 {@code -Xmx} 限堆；父进程限输出字节数，防日志炸弹。</li>
 *   <li><b>静态筛查 = 防呆</b>：拦明显越权 API（进程/反射/文件写/网络），
 *       让常见误用快速失败而不是等到容器层。它<b>不是</b>安全保证，
 *       因为字符串拼接、反射等手段可以绕过——真正的保证在容器。</li>
 * </ol>
 *
 * <p><b>为什么必须这么设计</b>：可行性报告 R2 把「沙盒任意代码执行」列为<b>极高风险</b>。
 * 用户已授权做，但授权不等于放弃防护。此处的取舍是：把「能不能逃出去」交给容器，
 * 把「会不会误用」交给静态检查，把「会不会拖死」交给超时与配额。</p>
 */
final class CodeExecutor {

    /** 单次执行硬上限（毫秒）。超过即强杀，禁无限等待。 */
    static final long MAX_TIMEOUT_MS = 30_000L;
    static final long DEFAULT_TIMEOUT_MS = 10_000L;
    /** 输出上限（每路）。超出截断并标注，避免日志炸弹撑爆内存。 */
    static final int MAX_OUTPUT_BYTES = 256 * 1024;
    /** 源码上限。 */
    static final int MAX_CODE_BYTES = 64 * 1024;
    /** Python 的静态禁用片段（同样是防呆，不是安全边界）。 */
    private static final List<Map.Entry<String, Pattern>> DENY_PY = List.of(
            Map.entry("启动外部进程", Pattern.compile("\\b(subprocess|os\\.system|os\\.popen|os\\.exec\\w*|os\\.spawn\\w*|pty\\.)\\b")),
            Map.entry("网络", Pattern.compile("\\b(socket|urllib|requests|httpx|http\\.client|ftplib|smtplib|telnetlib|xmlrpc)\\b")),
            Map.entry("写文件", Pattern.compile("\\b(open\\s*\\([^)]*['\\\"][wax+]|shutil\\.(copy|move|rmtree)|os\\.(remove|unlink|rmdir|rename|makedirs))\\b")),
            Map.entry("动态求值", Pattern.compile("\\b(eval|exec|compile|__import__)\\s*\\(")),
            Map.entry("读环境敏感值", Pattern.compile("\\b(os\\.environ|os\\.getenv)\\b")),
            Map.entry("导入系统模块", Pattern.compile("\\b(import|from)\\s+(ctypes|cffi|importlib|pickle|marshal|sysconfig)\\b"))
    );

    private CodeExecutor() {
    }

    /**
     * 执行一段 Python 源码（仅标准库）。
     *
     * <p>2026-11-26 用户裁决：沙盒<b>只保留 Python 执行器</b>，Java 编译/执行路径已移除。
     * 共用静态防呆、一次性子进程、硬超时强杀、输出截断。</p>
     */
    static Map<String, Object> runPython(String code, Long timeoutMs) {
        long started = System.nanoTime();

        if (code == null || code.isBlank()) {
            throw new SandboxError(400, "code 不能为空");
        }
        byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
        if (codeBytes.length > MAX_CODE_BYTES) {
            throw new SandboxError(413, "源码 " + codeBytes.length + "B 超上限 " + MAX_CODE_BYTES + "B");
        }

        for (Map.Entry<String, Pattern> rule : DENY_PY) {
            if (rule.getValue().matcher(code).find()) {
                throw new SandboxError(403, "代码包含受限用法：" + rule.getKey()
                        + "（沙盒只允许纯计算；如确需该能力须走人工评审）");
            }
        }

        long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS
                : Math.max(1000L, Math.min(timeoutMs, MAX_TIMEOUT_MS));

        Path work = null;
        try {
            work = Files.createTempDirectory("sbxpy");
            Path src = work.resolve("main.py");
            Files.writeString(src, code, StandardCharsets.UTF_8);

            // -I 隔离模式（不读用户 site-packages / PYTHONPATH）、-B 不写 pyc
            ProcResult run = exec(List.of("python3", "-I", "-B", src.toString()), work, timeout);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("language", "python");
            out.put("compiled", true);          // Python 无独立编译阶段，语义上视为通过
            out.put("stage", run.timedOut ? "timeout" : "run");
            out.put("exitCode", run.exit);
            out.put("stdout", run.stdout);
            out.put("stderr", run.stderr);
            out.put("timedOut", run.timedOut);
            out.put("truncated", run.truncated);
            out.put("timeoutMs", timeout);
            out.put("measuredMetric", parseMetric(run.stdout));
            out.put("measuredResult", parseResult(run.stdout));
            out.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            return out;
        } catch (IOException e) {
            throw new SandboxError(500, "执行环境异常: " + e.getMessage());
        } finally {
            deleteQuietly(work);
        }
    }

    /**
     * 从程序输出里提取结构化结果（V367 批次 B0）。
     *
     * <p>约定：程序把结果以 {@code RESULT <json>} 单行打印（≤64KB）；没有则返回 null。
     * 只认这一行，避免把日志里的其它 JSON 当结论。</p>
     */
    static Object parseResult(String stdout) {
        if (stdout == null) return null;
        var m = Pattern.compile("(?m)^\\s*RESULT\\s+(.+?)\\s*$").matcher(stdout);
        if (!m.find()) return null;
        String json = m.group(1);
        if (json.length() > 64 * 1024) {
            throw new SandboxError(413, "RESULT 超过 64KB");
        }
        try {
            return MiniJson.parse(json);
        } catch (RuntimeException e) {
            throw new SandboxError(422, "RESULT 不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 从程序输出里提取指标。
     *
     * <p>约定：程序把结果以 {@code METRIC <数字>} 单行打印；没有则返回 null
     * （<b>不猜测</b>，也不把其它输出当指标，避免「看起来成功」的假读数）。</p>
     */
    static Object parseMetric(String stdout) {
        if (stdout == null) {
            return null;
        }
        var m = Pattern.compile("(?m)^\\s*METRIC\\s+(-?\\d+(?:\\.\\d+)?)\\s*$").matcher(stdout);
        if (!m.find()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── 子进程执行 ──────────────────────────────────────────────────────────

    private static final class ProcResult {
        int exit;
        String stdout;
        String stderr;
        boolean timedOut;
        boolean truncated;
    }

    private static ProcResult exec(List<String> cmd, Path dir, long timeoutMs) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        // 剥离环境变量：子进程不应看到宿主的任何凭据/代理设置
        pb.environment().clear();
        pb.environment().put("PATH", "/opt/java/openjdk/bin:/usr/bin:/bin");
        pb.environment().put("LANG", "C.UTF-8");
        pb.environment().put("HOME", dir.toString());

        Process proc = pb.start();
        proc.getOutputStream().close(); // 不喂 stdin

        StreamDrain outDrain = new StreamDrain(proc.getInputStream(), MAX_OUTPUT_BYTES);
        StreamDrain errDrain = new StreamDrain(proc.getErrorStream(), MAX_OUTPUT_BYTES);
        Thread tOut = new Thread(outDrain, "drain-out");
        Thread tErr = new Thread(errDrain, "drain-err");
        tOut.setDaemon(true);
        tErr.setDaemon(true);
        tOut.start();
        tErr.start();

        ProcResult r = new ProcResult();
        try {
            boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                r.timedOut = true;
                proc.destroy();
                if (!proc.waitFor(2000, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly();   // 硬杀：防「忽略中断」的恶意循环
                    proc.waitFor(2000, TimeUnit.MILLISECONDS);
                }
            }
            tOut.join(2000);
            tErr.join(2000);
            r.exit = finished ? proc.exitValue() : -1;
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("执行被中断", e);
        }
        r.stdout = outDrain.text();
        r.stderr = errDrain.text();
        r.truncated = outDrain.truncated || errDrain.truncated;
        return r;
    }

    /** 有上限地读取子进程输出，超出即截断（防日志炸弹）。 */
    private static final class StreamDrain implements Runnable {
        private final InputStream in;
        private final int limit;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        volatile boolean truncated;

        StreamDrain(InputStream in, int limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            int n;
            try {
                while ((n = in.read(chunk)) > 0) {
                    int room = limit - buf.size();
                    if (room <= 0) {
                        truncated = true;
                        continue; // 继续读空缓冲，避免子进程因管道满而阻塞
                    }
                    buf.write(chunk, 0, Math.min(n, room));
                    if (n > room) {
                        truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // 进程被杀时管道会断，属预期
            }
        }

        String text() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响结果；tmpfs 随容器销毁
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }
}

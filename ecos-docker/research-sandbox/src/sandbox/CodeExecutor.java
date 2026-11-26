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
    /** 堆上限（MB）。 */
    static final int MAX_HEAP_MB = 256;
    static final int DEFAULT_HEAP_MB = 96;

    /**
     * 静态禁用的 API 片段（防呆层，不声称是安全边界）。
     *
     * <p>刻意用「完整类名/方法签名」而不是宽泛关键词，减少误伤合法命名
     * （例如变量叫 {@code processCount} 不应被拦）。</p>
     */
    private static final List<Map.Entry<String, Pattern>> DENY = List.of(
            Map.entry("启动外部进程", Pattern.compile("\\b(Runtime\\s*\\.\\s*getRuntime|ProcessBuilder)\\b")),
            Map.entry("反射越权", Pattern.compile("\\b(Class\\.forName|setAccessible\\s*\\(|MethodHandles|Unsafe)\\b")),
            Map.entry("写文件", Pattern.compile("\\b(Files\\s*\\.\\s*(write|createFile|delete|move|copy)|FileOutputStream|FileWriter|RandomAccessFile)\\b")),
            Map.entry("网络", Pattern.compile("\\b(Socket|ServerSocket|URL|URLConnection|HttpClient|DatagramSocket|InetAddress)\\b")),
            Map.entry("加载本地库", Pattern.compile("\\b(System\\s*\\.\\s*load|System\\s*\\.\\s*loadLibrary)\\b")),
            Map.entry("关闭JVM", Pattern.compile("\\b(System\\s*\\.\\s*(exit|halt)|Runtime\\s*\\.\\s*halt)\\b")),
            Map.entry("线程爆炸", Pattern.compile("\\b(new\\s+Thread\\s*\\(|Executors\\s*\\.)\\b")),
            Map.entry("读环境敏感值", Pattern.compile("\\b(System\\s*\\.\\s*(getenv|getProperties))\\b"))
    );

    private CodeExecutor() {
    }

    /**
     * 执行一段 Java 源码，返回结构化结果。
     *
     * <p>调用方必须已确认容器隔离生效——本类不重复做容器级检查。</p>
     */
    static Map<String, Object> run(String code, Long timeoutMs, Integer heapMb) {
        long started = System.nanoTime();

        if (code == null || code.isBlank()) {
            throw new SandboxError(400, "code 不能为空");
        }
        byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
        if (codeBytes.length > MAX_CODE_BYTES) {
            throw new SandboxError(413, "源码 " + codeBytes.length + "B 超上限 " + MAX_CODE_BYTES + "B");
        }

        // 静态防呆：命中即拒，并在信息里指明命中的类别（便于作者修正）
        for (Map.Entry<String, Pattern> rule : DENY) {
            if (rule.getValue().matcher(code).find()) {
                throw new SandboxError(403, "代码包含受限用法：" + rule.getKey()
                        + "（沙盒只允许纯计算；如确需该能力须走人工评审）");
            }
        }

        if (!code.contains("class ")) {
            throw new SandboxError(400, "源码必须包含一个 class 定义");
        }
        String mainClass = detectMainClass(code);

        long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS
                : Math.max(1000L, Math.min(timeoutMs, MAX_TIMEOUT_MS));
        int heap = heapMb == null ? DEFAULT_HEAP_MB
                : Math.max(32, Math.min(heapMb, MAX_HEAP_MB));

        Path work = null;
        try {
            work = Files.createTempDirectory("sbxrun");
            Path src = work.resolve(mainClass + ".java");
            Files.writeString(src, code, StandardCharsets.UTF_8);

            // 1) 编译（也有超时，防病态泛型把编译器拖死）
            ProcResult compile = exec(List.of("javac", "-encoding", "UTF-8", "-nowarn",
                    "-d", work.toString(), src.toString()), work, Math.min(timeout, 15_000L));
            if (compile.exit != 0) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("compiled", false);
                out.put("stage", "compile");
                out.put("stderr", compile.stderr);
                out.put("stdout", compile.stdout);
                out.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
                return out;
            }

            // 2) 运行（独立进程 + 硬超时）
            List<String> cmd = new ArrayList<>(List.of("java",
                    "-Xmx" + heap + "m", "-XX:+UseSerialGC",
                    "-XX:TieredStopAtLevel=1",      // 缩短 JIT，减少启动开销与攻击面
                    "-Djava.security.manager=disallow",
                    "-cp", work.toString(), mainClass));
            ProcResult run = exec(cmd, work, timeout);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("compiled", true);
            out.put("stage", run.timedOut ? "timeout" : "run");
            out.put("exitCode", run.exit);
            out.put("stdout", run.stdout);
            out.put("stderr", run.stderr);
            out.put("timedOut", run.timedOut);
            out.put("truncated", run.truncated);
            out.put("timeoutMs", timeout);
            out.put("heapMb", heap);
            out.put("measuredMetric", parseMetric(run.stdout));
            out.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            return out;
        } catch (IOException e) {
            throw new SandboxError(500, "执行环境异常: " + e.getMessage());
        } finally {
            deleteQuietly(work);
        }
    }

    /** 从 {@code class Xxx} 声明里取主类名（取第一个 public 类或第一个类）。 */
    static String detectMainClass(String code) {
        var m = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(code);
        String first = null;
        while (m.find()) {
            String name = m.group(1);
            if (first == null) {
                first = name;
            }
            // 优先选带 main 的类
            int from = m.end();
            int to = Math.min(code.length(), from + 4000);
            String tail = code.substring(from, to);
            if (tail.contains("void main(") || tail.contains("void main (")) {
                return name;
            }
        }
        if (first == null) {
            throw new SandboxError(400, "无法从源码中识别类名");
        }
        return first;
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

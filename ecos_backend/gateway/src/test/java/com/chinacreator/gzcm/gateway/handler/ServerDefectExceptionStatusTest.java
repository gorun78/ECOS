package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-26 收口 <b>D-B4-58</b>「服务端缺陷异常型在 HTTP 面被归成 4xx 客户端错误」的防复发门禁。
 *
 * <p>缺陷形态：{@code GlobalExceptionHandler.handleNullPointerException} 与
 * {@code handleIllegalState} 声明 {@code @ResponseStatus(BAD_REQUEST)}。两枚异常都是
 * <b>服务端自身</b>的产物（null 解引用／不变式被破坏），异常对象里没有任何「客户端少传字段」的证据；
 * 而异常型比 {@code handleAny(Exception)}→500 更具体 ⇒ 在 Spring 的处理器选择里先命中，
 * 于是真实的服务端故障在传输层被洗成 400：不进 5xx 告警面、还引导客户端去改一个本来没错的请求。</p>
 *
 * <p>判据四腿：</p>
 * <ol>
 *   <li><b>A 反射面（gateway 属主）</b>：两枚出口的状态行注解必须是
 *       {@code INTERNAL_SERVER_ERROR}，且机读码必须是 {@code ECOS-SYS-500}。</li>
 *   <li><b>B 过度矫正反向腿</b>：四枚真·入参型出口（Validation／NumberFormat／
 *       HttpMessageNotReadable／Business）必须<b>仍</b>是 4xx——只收「服务端缺陷冒充客户端错误」，
 *       不收 400 通道本身；这条判据让「一气把兜底全改 500」的改法当场变红。</li>
 *   <li><b>C 全后端零容忍</b>：扫描全部承载 {@code @ExceptionHandler} 的 main 源文件，
 *       服务端缺陷异常型<b>一律不得</b>映射到 4xx（收口前实测违例恰为本批 2 枚，收口后 0，
 *       零基线＝新增即红）；扫描面阈值防「空扫描＝假绿」。</li>
 *   <li><b>D 行为腿</b>：真 handler 返体 {@code code}＝500、{@code errorCode}＝登记码、
 *       文案为通用脱敏串（不含异常类名／堆栈／"参数"字样），并<b>跨面同号</b>——
 *       返体 {@code code} 必须等于 {@code @ResponseStatus} 的数值（§3.2.7）。</li>
 * </ol>
 *
 * <p>取证纪律：C 腿配 {@code @TempDir} 合成夹具（违例文件与合规对照文件各一枚），
 * 先证明「能变红」再宣布过门；扫描先剥注释再抹平字符串字面量，防把 javadoc 里的
 * {@code @ResponseStatus(BAD_REQUEST)} 沿革说明读成违例（与 D-B4-40／62 同口径）。</p>
 *
 * <p><b>本门自己的一次「差点瞎」</b>：状态名首版按 {@code HttpStatus\.([A-Z]+)} 取枚举名，
 * 而 {@code INTERNAL_SERVER_ERROR}／{@code UNPROCESSABLE_ENTITY} 都含下划线 ⇒ 真仓与夹具同时抛
 * {@code No enum constant ...INTERNAL}，两枚用例以 <b>Error</b>（不是 Failures）形态暴露。
 * 若当时的处置是 catch 住解析失败，判据就退化成「5xx 一律解析失败⇒一律不计数」的恒绿门；
 * 现按整枚取回修判据，并在夹具里补一枚「下划线 4xx 必须入面、下划线 5xx 必须不入面」的正反对账。</p>
 */
@Timeout(120)
class ServerDefectExceptionStatusTest {

    /** 服务端缺陷异常型：出现于 {@code @ExceptionHandler} 且状态行落 4xx 即违例。 */
    private static final List<String> SERVER_DEFECT_TYPES = List.of(
            "NullPointerException", "IllegalStateException", "ClassCastException",
            "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
            "UnsupportedOperationException", "ConcurrentModificationException",
            "ArithmeticException", "StackOverflowError", "OutOfMemoryError");

    /** 判据 B：真·入参型出口（客户端可自修），必须留在 4xx 面。 */
    private static final List<String> CLIENT_INPUT_METHODS = List.of(
            "handleValidation", "handleNumberFormat", "handleHttpMessageNotReadable", "handleBusiness");

    /** 判据 A：本批收口的两枚出口。 */
    private static final List<String> SERVER_DEFECT_METHODS = List.of(
            "handleNullPointerException", "handleIllegalState");

    /** 扫描面阈值守卫：2026-10-22 实测承载 @ExceptionHandler 的 main 文件 9 枚。 */
    private static final int MIN_SCANNED_HANDLER_FILES = 7;

    private static final Pattern HANDLER_ANN =
            Pattern.compile("@ExceptionHandler\\s*(\\([^)]*\\))");
    private static final Pattern STATUS_TOKEN =
            Pattern.compile("HttpStatus\\s*\\.\\s*([A-Z][A-Z0-9_]*)");
    private static final Pattern STATUS_ANN = Pattern.compile("@ResponseStatus\\s*\\(");

    // ── 判据 A/B：gateway 属主的反射面 ──────────────────

    @Test
    @DisplayName("判据A — NPE／IllegalState 两枚出口的状态行注解必须是 500（不得回 4xx）")
    void serverDefectHandlersDeclareServerError() throws NoSuchMethodException {
        for (String method : SERVER_DEFECT_METHODS) {
            HttpStatus status = statusLineOf(method);
            assertTrue(status.is5xxServerError(),
                    method + " 的状态行必须是 5xx（服务端缺陷不得冒充客户端错误），实际=" + status);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status,
                    method + " 按 §3.2.7 归「未分类内部错误」=500，实际=" + status);
        }
    }

    @Test
    @DisplayName("判据B — 四枚真·入参型出口必须仍是 4xx（防「兜底一律改 500」的过度矫正）")
    void clientInputHandlersStayOnClientErrorSide() throws NoSuchMethodException {
        for (String method : CLIENT_INPUT_METHODS) {
            HttpStatus status = statusLineOf(method);
            assertTrue(status.is4xxClientError(),
                    method + " 是客户端可自修的入参失败，状态行必须留在 4xx，实际=" + status);
        }
    }

    // ── 判据 C：全后端零容忍 ────────────────────────────

    @Test
    @DisplayName("判据C — 全后端 main 面「服务端缺陷异常型 → 4xx」的违例数 = 0（基线已关账）")
    void noServerDefectTypeIsMappedToClientError() throws IOException {
        Path backend = locateRepoRoot().resolve("ecos_backend");
        List<Violation> violations = new ArrayList<>();
        int scannedFiles = 0;
        try (Stream<Path> walk = Files.walk(backend)) {
            List<Path> files = walk.filter(f -> Files.isRegularFile(f)
                            && f.toString().replace('\\', '/').endsWith(".java")
                            && f.toString().replace('\\', '/').contains("/src/main/java/")
                            && !f.toString().replace('\\', '/').contains("/target/"))
                    .sorted().toList();
            for (Path f : files) {
                String raw = Files.readString(f, StandardCharsets.UTF_8);
                if (!raw.contains("@ExceptionHandler")) {
                    continue;
                }
                scannedFiles++;
                violations.addAll(scanSource(backend.relativize(f).toString().replace('\\', '/'), raw));
            }
        }
        assertTrue(scannedFiles >= MIN_SCANNED_HANDLER_FILES,
                "扫描面异常收缩（实扫承载 @ExceptionHandler 的 main 文件 " + scannedFiles
                        + " < 阈值 " + MIN_SCANNED_HANDLER_FILES + "）——「空扫描＝假绿」，先修门再判绿");
        assertEquals(0, violations.size(),
                "服务端缺陷异常型被归成 4xx 的违例共 " + violations.size() + " 枚：\n  "
                        + render(violations)
                        + "\n修法：改 @ResponseStatus(INTERNAL_SERVER_ERROR) 并把机读码指 ECOS-SYS-500"
                        + "（真·入参失败应走 Validation/Business 等具名 400 通道，不是把缺陷文案写成参数错）。");
    }

    /** 供判据 C 与夹具共用的扫描体：注解对在「到方法体首 { 之前」的区间内配对。 */
    private static List<Violation> scanSource(String rel, String raw) {
        String code = blankOutCommentsAndLiterals(raw);
        List<Violation> out = new ArrayList<>();
        Matcher m = HANDLER_ANN.matcher(code);
        while (m.find()) {
            String carried = m.group(1);
            List<String> types = new ArrayList<>();
            for (String t : SERVER_DEFECT_TYPES) {
                if (carried.contains(t)) {
                    types.add(t);
                }
            }
            if (types.isEmpty()) {
                continue;
            }
            int body = code.indexOf('{', m.end());
            String between = body < 0 ? code.substring(m.end()) : code.substring(m.end(), body);
            if (!STATUS_ANN.matcher(between).find()) {
                // 无 @ResponseStatus ⇒ 由返回体（ResponseEntity）自带状态行，本判据不判读
                continue;
            }
            Matcher st = STATUS_TOKEN.matcher(between);
            if (st.find()) {
                HttpStatus status = HttpStatus.valueOf(st.group(1));
                if (status.is4xxClientError()) {
                    long nl = code.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    out.add(new Violation(rel, (int) nl + 1, String.join("|", types), status.name()));
                }
            }
        }
        return out;
    }

    // ── 判据 D：行为腿 ─────────────────────────────────

    @Test
    @DisplayName("行为 — 两枚出口返体：code=500＋ECOS-SYS-500＋通用脱敏文案（不含异常类名/参数措辞）")
    void carriersReturnGenericServerErrorEnvelope() throws NoSuchMethodException {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> npe = handler.handleNullPointerException(
                new NullPointerException("Cannot invoke \"String.length()\" because \"name\" is null"));
        assertEquals(500, npe.getCode(), "code 字段承载 HTTP 语义，必须与状态行同号");
        assertEquals(SystemErrorCode.ECOS_SYS_500, npe.getErrorCode(),
                "机读码必须是登记过的 ECOS-SYS-500，实际=" + npe.getErrorCode());
        assertNotNull(npe.getMessage());
        assertTrue(npe.getMessage().contains("服务内部错误"),
                "文案必须按服务端缺陷口径，不再引导客户端改参数，实际=" + npe.getMessage());
        assertFalse(npe.getMessage().contains("参数"),
                "旧文案「请求参数不完整或缺少必要字段」不得回归（实际=" + npe.getMessage() + "）");
        assertFalse(npe.getMessage().contains("null"),
                "NPE 自述细节不得进对外文案（堆栈只入日志），实际=" + npe.getMessage());

        ApiResponse<Void> ise = handler.handleIllegalState(new IllegalStateException("pipeline already closed"));
        assertEquals(500, ise.getCode());
        assertEquals(SystemErrorCode.ECOS_SYS_500, ise.getErrorCode());
        assertTrue(ise.getMessage().contains("服务内部错误"),
                "「服务未就绪」旧文案与 400 状态行互不成立，已按 500 通用口径替换（实际=" + ise.getMessage() + "）");

        // 返体 code 与状态行必须同号。这条腿是 M1 取证逼出来的：只把注解改回 BAD_REQUEST 时，
        // 本腿（只看返体）与 B 腿（只看入参型）都不红，全靠 A/C 两腿才红——「注解与返体分叉」
        // 这一形态当时没有判据覆盖（§3.2.7 要求 code 与状态行同号），现补跨面一致性断言。
        assertEquals(statusLineOf("handleNullPointerException").value(), npe.getCode(),
                "handleNullPointerException 的返体 code 必须与 @ResponseStatus 同号（禁状态行 400／包体 500 分叉）");
        assertEquals(statusLineOf("handleIllegalState").value(), ise.getCode(),
                "handleIllegalState 的返体 code 必须与 @ResponseStatus 同号");
    }

    // ── 夹具：先证明 C 腿能变红 ─────────────────────────

    @Test
    @DisplayName("夹具 — 合成违例必被记、合规对照与注释形态必不被记（防恒 0 命中）")
    void scanReportsSyntheticViolationAndIgnoresCompliantShape(@TempDir Path tmp) throws IOException {
        String bad = "class BadAdvice {\n"
                + "  @ExceptionHandler(IllegalStateException.class)\n"
                + "  @ResponseStatus(HttpStatus.BAD_REQUEST)\n"
                + "  public Object h(IllegalStateException ex) { return null; }\n}\n";
        String ok = "class GoodAdvice {\n"
                + "  @ExceptionHandler(NullPointerException.class)\n"
                + "  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)\n"
                + "  public Object h(NullPointerException ex) { return null; }\n"
                + "  @ExceptionHandler(BusinessException.class)\n"
                + "  @ResponseStatus(HttpStatus.BAD_REQUEST)\n"
                + "  public Object b(BusinessException ex) { return null; }\n}\n";
        String decoy = "class OnlyInComment {\n"
                + "  // @ExceptionHandler(IllegalStateException.class)\n"
                + "  // @ResponseStatus(HttpStatus.BAD_REQUEST)\n"
                + "  String s = \"@ExceptionHandler(NullPointerException.class)\";\n}\n";
        String delegating = "class EntityAdvice {\n"
                + "  @ExceptionHandler(IllegalStateException.class)\n"
                + "  public Object h(IllegalStateException ex) { return ResponseEntity.status(503); }\n}\n";

        Path badFile = tmp.resolve("src/main/java/a/BadAdvice.java");
        write(badFile, bad);
        List<Violation> only = scanSource("a/BadAdvice.java", bad);
        assertEquals(1, only.size(), "合成违例必须被记 1 枚（否则判据 C 是恒 0 的假绿门）");
        assertEquals("IllegalStateException", only.get(0).types());
        assertEquals("BAD_REQUEST", only.get(0).status());

        write(tmp.resolve("src/main/java/a/GoodAdvice.java"), ok);
        assertEquals(0, scanSource("a/GoodAdvice.java", ok).size(),
                "500 型服务端出口与入参型 400 出口都不得入违例面（判据只盯「缺陷型→4xx」）");
        write(tmp.resolve("src/main/java/a/OnlyInComment.java"), decoy);
        assertEquals(0, scanSource("a/OnlyInComment.java", decoy).size(),
                "注释与字符串字面量里的同形文本不计数（先剥注释＋抹字面量）");
        write(tmp.resolve("src/main/java/a/EntityAdvice.java"), delegating);
        assertEquals(0, scanSource("a/EntityAdvice.java", delegating).size(),
                "无 @ResponseStatus（由 ResponseEntity 自带状态行）不属本判据面");

        // 多型同一条注解：只点名其中属于服务端缺陷的那一枚，且不得因首枚命中而漏记第二条
        String multi = "class MultiAdvice {\n"
                + "  @ExceptionHandler({NullPointerException.class, ForbiddenException.class})\n"
                + "  @ResponseStatus(HttpStatus.NOT_FOUND)\n"
                + "  public Object h(Exception ex) { return null; }\n"
                + "  @ExceptionHandler(ClassCastException.class)\n"
                + "  @ResponseStatus(HttpStatus.BAD_REQUEST)\n"
                + "  public Object c(ClassCastException ex) { return null; }\n}\n";
        List<Violation> both = scanSource("a/MultiAdvice.java", multi);
        assertEquals(2, both.size(), "同文件两枚违例必须逐枚计数（「每文件只记首个」＝D-B4-62 同型盲区）");
        assertEquals("NullPointerException", both.get(0).types(),
                "混合注解里只登记属于服务端缺陷集合的那一枚异常型");
        assertEquals("ClassCastException", both.get(1).types());

        // 状态名含下划线的 4xx 必须照样入违例面：首版判据用 `HttpStatus\\.([A-Z]+)` 取枚举名，
        // INTERNAL_SERVER_ERROR 被截成 "INTERNAL" 直接抛 IllegalArgumentException（靠本夹具腿先撞红），
        // 若当时改成 catch 吞掉解析失败就会变成「所有 5xx 都解析失败→都不计数」的恒绿盲区。
        String underscore = "class UnderscoreAdvice {\n"
                + "  @ExceptionHandler(IllegalStateException.class)\n"
                + "  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)\n"
                + "  public Object h(IllegalStateException ex) { return null; }\n"
                + "  @ExceptionHandler(ClassCastException.class)\n"
                + "  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)\n"
                + "  public Object c(ClassCastException ex) { return null; }\n}\n";
        List<Violation> us = scanSource("a/UnderscoreAdvice.java", underscore);
        assertEquals(1, us.size(), "下划线形态的 4xx 必须入违例面、下划线形态的 5xx 必须不入");
        assertEquals("UNPROCESSABLE_ENTITY", us.get(0).status(),
                "状态名必须整枚取回（含下划线），实际=" + us.get(0).status());
    }

    // ── 实现 ────────────────────────────────────────────

    private record Violation(String rel, int line, String types, String status) {
    }

    private static String render(List<Violation> vs) {
        StringBuilder sb = new StringBuilder();
        for (Violation v : vs) {
            sb.append(v.rel()).append(':').append(v.line()).append("  [")
                    .append(v.types()).append("] → ").append(v.status()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private static HttpStatus statusLineOf(String method) throws NoSuchMethodException {
        Class<?> param = switch (method) {
            case "handleNullPointerException" -> NullPointerException.class;
            case "handleIllegalState" -> IllegalStateException.class;
            case "handleValidation" -> com.chinacreator.gzcm.common.exception.ValidationException.class;
            case "handleNumberFormat" -> NumberFormatException.class;
            case "handleHttpMessageNotReadable" ->
                    org.springframework.http.converter.HttpMessageNotReadableException.class;
            case "handleBusiness" -> com.chinacreator.gzcm.common.exception.BusinessException.class;
            default -> throw new IllegalStateException("未登记的方法：" + method);
        };
        ExceptionHandler handlerAnn = GlobalExceptionHandler.class
                .getMethod(method, param).getAnnotation(ExceptionHandler.class);
        assertNotNull(handlerAnn, method + " 必须仍带 @ExceptionHandler（异常型出口单源在注解）");
        assertTrue(List.of(handlerAnn.value()).contains(param),
                method + " 的 @ExceptionHandler 异常型漂移：判据面与实现面对不上");
        ResponseStatus ann = GlobalExceptionHandler.class.getMethod(method, param)
                .getAnnotation(ResponseStatus.class);
        assertNotNull(ann, method + " 必须仍带 @ResponseStatus（状态行单源在注解）");
        return ann.value();
    }

    private static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("无法定位仓库根：自 user.dir 向上 12 级未找到 ECOS 双树目录");
    }

    /** 剥注释并抹平字符串/字符字面量（保留换行以维持行号）；口径同 ErrorCodeMachineCodeSingleSourceTest。 */
    static String blankOutCommentsAndLiterals(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (c == '/' && n == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? src.length() : end;
                for (int k = i; k < stop; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                if (end >= 0) {
                    out.append("  ");
                    i = end + 2;
                } else {
                    i = stop;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                int nl = src.indexOf('\n', i);
                int stop = nl < 0 ? src.length() : nl;
                for (int k = i; k < stop; k++) {
                    out.append(' ');
                }
                i = stop;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                out.append(quote);
                i++;
                while (i < src.length()) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < src.length()) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        out.append(quote);
                        i++;
                        break;
                    }
                    out.append(d == '\n' ? '\n' : ' ');
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}

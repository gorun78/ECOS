package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-23 之后、<b>#142/B4-3e-24</b> 收口 <b>D-B4-55</b> 的防复发门禁。
 *
 * <p>缺陷形态：响应信封 {@code ApiResponse.errorCode}（机读码字段）由
 * {@code String.valueOf(<int> getErrorCode())} 填充 ⇒ 真正的机读码（{@code ECOS-SEC-501} 等）
 * 只活在 {@code message} 前缀，调用方按 {@code errorCode} 匹配码的通道恒落空。</p>
 *
 * <p>判据三腿：</p>
 * <ol>
 *   <li><b>A 零容忍</b>：{@code ecos_backend/**}{@code /src/main/java} 面禁再出现
 *       {@code String.valueOf(x.getErrorCode())}（改前实测 3 处，收口后 0）。</li>
 *   <li><b>B 属主在位</b>：{@code GlobalExceptionHandler} 的三条异常出口
 *       （DataBridge／Business／DataAccess）必须各经 {@code MachineErrorCode.orNumeric} 一次。</li>
 *   <li><b>C 只减不增棘轮</b>：以字面量三位数充当 {@code errorCode} 的承载点（<b>D-B4-64</b>，
 *       已于 <b>#142/B4-3e-25</b> 关账）基线 <b>0</b> 枚。登记时实测 2 枚（404 路由腿／409 冲突腿），
 *       改指 §3.2.7 {@code ECOS-SYS-*} 族登记码后归零。零基线同样双向：任何一枚出现即红，
 *       且「实 0 而基线仍挂 2」也红——基线与文件白名单必须同批下修，由判据内的元自证枚看守。</li>
 * </ol>
 *
 * <p>关账前逐项实测（改面值不是纯内部重构，须先证明没有按旧面值匹配的下游）：
 * 前端读 {@code errorCode} 共 69 处，其中与 {@code "404"}／{@code "409"} 字面量比较的 <b>0</b> 处；
 * {@code ecos-tests} 只断言 {@code ECOS-COG-503D}／{@code ENGINE_DISABLED}；
 * 后端测试面无任何一条腿绑这两枚字面量。故 {@code code} 与状态行保持原值即可保证契约不回归。</p>
 *
 * <p>取证纪律：三条判据各配 {@code @TempDir} 合成夹具，先证明「能变红」再宣布过门；
 * 预处理<b>按判据分模式</b>——A/B 剥注释＋抹字符串字面量（本类的 javadoc 里就写了违例形态的原样文本），
 * C 只剥注释，因为它的面值本身就是字符串字面量，抹字面量会把它自己抹成恒 0 命中＝空扫描假绿
 * （首版正是复用 A/B 的预处理而真仓与夹具全报 0，靠 C 腿的双向基线判据才暴露）；
 * 扫描面另有阈值守卫，实扫文件数低于阈值即红。</p>
 */
@Timeout(120)
class ErrorCodeMachineCodeSingleSourceTest {

    /** 判据 A：数字串冒充机读码的形态（允许任意变量名与空白）。 */
    private static final Pattern NUMERIC_STRINGIFY = Pattern.compile(
            "String\\.valueOf\\(\\s*[A-Za-z_$][\\w.]*\\.getErrorCode\\(\\)\\s*\\)");

    /** 判据 B：单源属主的调用形态。 */
    private static final Pattern OWNER_CALL = Pattern.compile(
            "MachineErrorCode\\.orNumeric\\s*\\(");

    /** 判据 C：字面量三位数当 errorCode 的形态（D-B4-64，已于 #142/B4-3e-25 关账）。 */
    private static final Pattern LITERAL_NUMERIC_CODE = Pattern.compile(
            "ApiResponse\\.(?:<[A-Za-z]+>)?error\\(\\s*\\d{3}\\s*,\\s*\"\\d{3}\"");

    /**
     * 判据 C 基线：<b>0 枚</b>（2026-10-22 #142/B4-3e-25 把 404 路由腿与 409 冲突腿改指
     * {@code ECOS-SYS-404}／{@code ECOS-SYS-409} 后实测归零）。
     * <p>基线与文件白名单必须<b>同批</b>下修，且二者自相一致（下面的元自证枚）——
     * 「实 0 而基线仍挂 2」也是一种假进度，因为它会让下一枚违例被基线吸收掉。</p>
     */
    private static final int LITERAL_NUMERIC_BASELINE = 0;
    private static final List<String> LITERAL_NUMERIC_BASELINE_FILES = List.of();

    /** 扫描面阈值守卫：2026-10-22 实测 main/java 文件数远大于此。 */
    private static final int MIN_SCANNED_MAIN_FILES = 800;

    /** 判据 B 必须命中的三条出口方法名。 */
    private static final List<String> CARRYING_METHODS =
            List.of("handleDataBridge", "handleBusiness", "handleDataAccess");

    // ── 判据 A/B/C：真仓 ────────────────────────────────

    @Test
    @DisplayName("判据A — 全后端 main 面零枚 String.valueOf(x.getErrorCode()) 冒充机读码")
    void noNumericStringifyInErrorCodeField() throws IOException {
        Path backend = locateRepoRoot().resolve("ecos_backend");
        List<Hit> hits = scan(backend, "src/main/java", NUMERIC_STRINGIFY, false);
        assertEquals(0, hits.size(),
                "机读码字段被数字串冒充的违例共 " + hits.size() + " 枚：\n  " + render(hits)
                        + "\n修法：改用 MachineErrorCode.orNumeric(message, ex.getErrorCode())（单源属主）。");
    }

    @Test
    @DisplayName("判据B — GlobalExceptionHandler 三条异常出口各经属主 orNumeric 一次")
    void threeCarryingSitesGoThroughOwner() throws IOException {
        Path handler = locateRepoRoot().resolve(
                "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java");
        String code = blankOutCommentsAndLiterals(Files.readString(handler, StandardCharsets.UTF_8));
        for (String method : CARRYING_METHODS) {
            String body = methodBody(code, method);
            assertEquals(1, count(OWNER_CALL, body),
                    method + " 必须恰有 1 处 MachineErrorCode.orNumeric（机读码取法只允许一个属主）");
            assertEquals(0, count(NUMERIC_STRINGIFY, body),
                    method + " 不得再用数字串冒充 errorCode");
        }
    }

    @Test
    @DisplayName("判据C — 字面量三位数当 errorCode 的余面 = 基线 0 枚（只减不增，D-B4-64 已关账）")
    void literalNumericErrorCodeFaceIsRatcheted() throws IOException {
        // 元自证：基线与文件白名单必须同批下修。「实 0 而基线挂 2」＝假进度，
        // 因为下一枚违例会被残留基线吸收；本断言让这种半关账直接红。
        assertEquals(LITERAL_NUMERIC_BASELINE, LITERAL_NUMERIC_BASELINE_FILES.size(),
                "基线枚数与文件白名单条数不一致＝关账只剪了一半（同批必剪两处）");
        Path backend = locateRepoRoot().resolve("ecos_backend");
        List<Hit> hits = scan(backend, "src/main/java", LITERAL_NUMERIC_CODE, true);
        List<String> files = hits.stream().map(Hit::rel).distinct().sorted().toList();
        assertEquals(LITERAL_NUMERIC_BASELINE, hits.size(),
                "D-B4-64 余面漂移：实测 " + hits.size() + " 枚（基线 " + LITERAL_NUMERIC_BASELINE
                        + "），明细：\n  " + render(hits)
                        + "\n多于基线＝新增违例；基线已归零，任何一枚都算红。");
        assertEquals(LITERAL_NUMERIC_BASELINE_FILES, files,
                "基线文件清单与实际命中文件不一致（按路径钉死，防改到别处仍算过门）");
    }

    // ── 行为腿：真 handler 的字段语义 ───────────────────

    @Test
    @DisplayName("行为 — message 起头有码→errorCode 取码；无码→逐字回落数字串；两腿在码字段分形")
    void errorCodeCarriesMachineCodeWhenPresent() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        DataBridgeException unavailable = new DataBridgeException(
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": pipeline.execute 未获安全裁决");
        DataBridgeException deny = new DataBridgeException(403, 403, "Pipeline 执行被 ABAC 策略拒绝");

        ApiResponse<Void> u = handler.handleDataBridge(unavailable).getBody();
        ApiResponse<Void> d = handler.handleDataBridge(deny).getBody();
        assertTrue(u != null && d != null);
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_CODE, u.getErrorCode(),
                "不可用腿机读码必须上 errorCode 字段，实际=" + u.getErrorCode());
        assertEquals("403", d.getErrorCode(),
                "无码腿必须保持收口前的数字串形态（既有契约不回归），实际=" + d.getErrorCode());
        assertNotEquals(u.getErrorCode(), d.getErrorCode(),
                "§4.7.2：不可用与无权限必须在 errorCode 字段也可分辨");

        BusinessException plain = new BusinessException(400, "参数缺失");
        assertEquals("400", handler.handleBusiness(plain).getBody().getErrorCode(),
                "BusinessException 无码腿同样回落数字串");

        // DataAccessException 无 (httpStatus, errorCode, message) 三参构造：默认形态即
        // (500, DEFAULT_ERROR_CODE=-100, message)，本腿只验「脱敏文案下的码仍上码字段」。
        ApiResponse<Void> daoBody =
                handler.handleDataAccess(new DataAccessException("ECOS-DATA-031: DB 连接失败")).getBody();
        assertNotNull(daoBody);
        assertEquals("ECOS-DATA-031", daoBody.getErrorCode(),
                "DataAccess 脱敏文案下的机读码仍必须上码字段（码不含敏感细节）");
        assertEquals("系统繁忙，请稍后重试", daoBody.getMessage(),
                "对外文案保持脱敏（码上字段不等于文案裸露）");

        DataBridgeException prose = new DataBridgeException(500, 500, "上游返回 ECOS-SEC-501 的提示");
        assertEquals("500", handler.handleDataBridge(prose).getBody().getErrorCode(),
                "文案中间转述的码不得冒充本请求错误码（只认 message 起始位）");
    }

    /**
     * #142/B4-3e-25 关账 D-B4-64 的行为腿：404 路由腿与 409 冲突腿的 {@code errorCode} 必须是
     * §3.2.7 {@code ECOS-SYS-*} 登记码，而 {@code code} 与 {@code @ResponseStatus} 状态行必须
     * <b>逐字不变</b>——本批改的是机读码字段，不碰 HTTP 语义（§4.5.4「API 路径与状态语义只增不改」）。
     */
    @Test
    @DisplayName("行为 — 404/409/兜底 500 出口：errorCode 上登记码，code 与状态行不变（D-B4-64 关账腿）")
    void literalNumericCarriersNowEmitRegisteredMachineCodes() throws NoSuchMethodException {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> notFound = handler.handleRouteNotFound(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/no-such-route"));
        assertEquals(SystemErrorCode.ECOS_SYS_404, notFound.getErrorCode(),
                "404 出口的 errorCode 必须是登记机读码，实际=" + notFound.getErrorCode());
        assertEquals(404, notFound.getCode(), "code 字段仍承载 HTTP 语义（不得被机读码顶替）");
        assertEquals(HttpStatus.NOT_FOUND,
                statusLineOf("handleRouteNotFound", Exception.class), "状态行必须仍为 404");

        ApiResponse<Void> conflict = handler.handleDataIntegrityViolation(
                new DuplicateKeyException("duplicate key value violates unique constraint \"t_code_key\""));
        assertEquals(SystemErrorCode.ECOS_SYS_409, conflict.getErrorCode(),
                "409 出口的 errorCode 必须是登记机读码，实际=" + conflict.getErrorCode());
        assertEquals(409, conflict.getCode(), "code 字段仍承载 HTTP 语义");
        assertEquals(HttpStatus.CONFLICT,
                statusLineOf("handleDataIntegrityViolation", DataIntegrityViolationException.class),
                "状态行必须仍为 409");

        ApiResponse<Void> any = handler.handleAny(new IllegalStateException("unexpected"));
        assertEquals(SystemErrorCode.ECOS_SYS_500, any.getErrorCode(),
                "兜底出口同样取常量单源，不再是散落的字面量");
        assertEquals(500, any.getCode(), "兜底 code 不变");

        assertNotEquals(notFound.getErrorCode(), conflict.getErrorCode(),
                "两枚出口必须彼此可分辨（旧形态是 HTTP 状态抄送，域语义缺失）");
    }

    /** 读 handler 方法上的 {@code @ResponseStatus}，证明本批未改 HTTP 状态行。 */
    private static HttpStatus statusLineOf(String method, Class<?> param) throws NoSuchMethodException {
        ResponseStatus ann = GlobalExceptionHandler.class.getMethod(method, param)
                .getAnnotation(ResponseStatus.class);
        assertNotNull(ann, method + " 必须仍带 @ResponseStatus（状态行单源在注解）");
        return ann.value();
    }

    // ── 夹具：先证明能变红 ─────────────────────────────

    @Test
    @DisplayName("夹具① — 合成违例必须被 A 腿记 1 枚；注释/字符串里的同形文本不记（假绿盲区）")
    void fixtureA_reportsRealViolationAndIgnoresComments(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("svc/a/src/main/java/p/Good.java"),
                "class Good { String e = MachineErrorCode.orNumeric(m, c); }");
        write(tmp.resolve("svc/a/src/main/java/p/Bad.java"),
                "class Bad { Object r = ApiResponse.error(c, String.valueOf(ex.getErrorCode()), m); }");
        write(tmp.resolve("svc/a/src/main/java/p/OnlyInComment.java"),
                "class OnlyInComment {\n  // String.valueOf(ex.getErrorCode()) 只是说明文字\n"
                        + "  /* String.valueOf(biz.getErrorCode()) */\n"
                        + "  String s = \"String.valueOf(other.getErrorCode())\";\n}");
        write(tmp.resolve("svc/a/src/test/java/p/TestBad.java"),
                "class TestBad { Object r = String.valueOf(ex.getErrorCode()); }");

        List<Hit> hits = scan(tmp, "src/main/java", NUMERIC_STRINGIFY, false);
        assertEquals(1, hits.size(), "只允许生产代码里的真实违例被记，明细：\n  " + render(hits));
        assertTrue(hits.get(0).rel().endsWith("p/Bad.java"), render(hits));
    }

    @Test
    @DisplayName("夹具② — B 腿对「属主缺失」与「属主重复/漏一条出口」都变红")
    void fixtureB_requiresExactlyOneOwnerCallPerSite(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("Handler.java");
        write(f, "class H {\n"
                + "  Object handleDataBridge(E ex) { return ApiResponse.error(c, String.valueOf(ex.getErrorCode()), m); }\n"
                + "  Object handleBusiness(E ex) { return ApiResponse.error(c, MachineErrorCode.orNumeric(m, c), m); }\n"
                + "  Object handleDataAccess(E ex) { return ApiResponse.error(c, MachineErrorCode.orNumeric(m, c), m); }\n}");
        String code = blankOutCommentsAndLiterals(Files.readString(f, StandardCharsets.UTF_8));
        assertEquals(0, count(OWNER_CALL, methodBody(code, "handleDataBridge")),
                "收口前形态：handleDataBridge 无属主腿 ⇒ B 腿必须能变红");
        assertEquals(1, count(OWNER_CALL, methodBody(code, "handleBusiness")), "另两条出口应恰命中 1");

        write(f, "class H {\n"
                + "  Object handleDataBridge(E ex) { return MachineErrorCode.orNumeric(m1, c); MachineErrorCode.orNumeric(m2, c); }\n"
                + "  Object handleBusiness(E ex) { return MachineErrorCode.orNumeric(m, c); }\n"
                + "  Object handleDataAccess(E ex) { return MachineErrorCode.orNumeric(m, c); }\n}");
        String twice = blankOutCommentsAndLiterals(Files.readString(f, StandardCharsets.UTF_8));
        assertEquals(2, count(OWNER_CALL, methodBody(twice, "handleDataBridge")),
                "同一出口两处属主＝取法仍有第二份，必须能被「恰 1」判据卡住");
    }

    @Test
    @DisplayName("夹具③ — C 腿面值计数对「新增/残留/清零」三态可分辨（零基线下任何一枚即红）")
    void fixtureC_isRedOnBothSidesOfBaseline(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("svc/c/src/main/java/p/One.java"),
                "class One { Object r = ApiResponse.<Void>error(404, \"404\", m); }");
        write(tmp.resolve("svc/c/src/main/java/p/Two.java"),
                "class Two { Object r = ApiResponse.error(409, \"409\", m); }");
        write(tmp.resolve("svc/c/src/main/java/p/Three.java"),
                "class Three { Object r = ApiResponse.error(500, \"500\", m); }");
        assertEquals(3, scan(tmp, "src/main/java", LITERAL_NUMERIC_CODE, true).size(),
                "三枚字面量数字码必须全部计入（基线 0 ⇒ 多出的每一枚都会让真仓判据红，不是只看'有没有超过 3'）");
        // 「只剥注释、保留字面量」模式的反面：注释里的同形文本不得算命中，
        // 否则该模式会退化成「原文扫描」，把 javadoc 里的例子读成违例。
        write(tmp.resolve("svc/c/src/main/java/p/OnlyComment.java"),
                "class OnlyComment {\n"
                        + "  // ApiResponse.error(404, \"404\", m) 只是说明\n"
                        + "  /* ApiResponse.<Void>error(500, \"500\", m) */\n}");
        assertEquals(3, scan(tmp, "src/main/java", LITERAL_NUMERIC_CODE, true).size(),
                "注释里的同形文本必须不计数（判据 C 的「保留字面量」不等于「不剥注释」）");
        write(tmp.resolve("svc/c/src/main/java/p/One.java"),
                "class One { Object r = ApiResponse.error(200, MACHINE_CODE, m); }");
        assertEquals(2, scan(tmp, "src/main/java", LITERAL_NUMERIC_CODE, true).size(),
                "剪到 2 枚＝基线该同批下修，否则「基线 2 而实 2」的假进度无人察觉");
        write(tmp.resolve("svc/c/src/main/java/p/Two.java"),
                "class Two { Object r = ApiResponse.error(409, \"ECOS-CONFLICT-409\", m); }");
        write(tmp.resolve("svc/c/src/main/java/p/Three.java"),
                "class Three { Object r = ApiResponse.error(500, \"ECOS-SYS-500\", m); }");
        assertEquals(0, scan(tmp, "src/main/java", LITERAL_NUMERIC_CODE, true).size(),
                "全部改指机读码后为 0 枚——本门只认面值形态，不受标识符名影响");
    }

    // ── 实现 ────────────────────────────────────────────

    private record Hit(String rel, int line, String text) {
    }

    /**
     * 在 {@code root} 下按 {@code dirMarker} 段筛文件，剥注释后逐枚命中。
     *
     * @param literalsMatter {@code true}＝只剥注释、字面量参算命中（判据 C 的面值就是字面量）；
     *                       {@code false}＝连字面量一起抹平（判据 A 不许把文案当成代码）
     */
    private static List<Hit> scan(Path root, String dirMarker, Pattern p, boolean literalsMatter)
            throws IOException {
        List<Hit> out = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(f -> Files.isRegularFile(f)
                            && f.toString().replace('\\', '/').endsWith(".java")
                            && f.toString().replace('\\', '/').contains("/" + dirMarker + "/")
                            && !f.toString().replace('\\', '/').contains("/target/"))
                    .sorted().toList();
            scanned = files.size();
            for (Path f : files) {
                String raw = Files.readString(f, StandardCharsets.UTF_8);
                String code = literalsMatter ? blankOutCommentsOnly(raw) : blankOutCommentsAndLiterals(raw);
                Matcher m = p.matcher(code);
                while (m.find()) {
                    long nl = code.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    out.add(new Hit(root.relativize(f).toString().replace('\\', '/'),
                            (int) nl + 1, m.group()));
                }
            }
        }
        if (root.endsWith(Paths.get("ecos_backend"))) {
            assertTrue(scanned >= MIN_SCANNED_MAIN_FILES,
                    "扫描面异常收缩（实扫 " + scanned + " < 阈值 " + MIN_SCANNED_MAIN_FILES
                            + "）——「空扫描＝假绿」，先修门再判绿");
        }
        return out;
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 取方法名对应的最外层 {@code )} 平衡方法体；找不到即抛（判据 B 的「漏一条出口」须红而非跳过）。 */
    private static String methodBody(String code, String method) {
        Matcher head = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(code);
        if (!head.find()) {
            throw new IllegalStateException("未找到方法 " + method + "：判据面漂移，须先修门");
        }
        int brace = code.indexOf('{', head.end());
        if (brace < 0) {
            throw new IllegalStateException("方法 " + method + " 无方法体");
        }
        int depth = 0;
        for (int i = brace; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(brace, i + 1);
                }
            }
        }
        throw new IllegalStateException("方法 " + method + " 花括号不平衡");
    }

    private static String render(List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        for (Hit h : hits) {
            sb.append(h.rel()).append(':').append(h.line()).append("  ").append(h.text()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    /**
     * 剥注释并抹平字符串/字符字面量内容（保留换行以维持行号与行首语义）。
     * 用于判据 A/B：字面量里的 {@code String.valueOf(...)} 是文案不是代码。
     */
    static String blankOutCommentsAndLiterals(String src) {
        return blank(src, true);
    }

    /**
     * 只剥注释、<b>保留字符串字面量本体</b>。用于判据 C：
     * 该判据的面值就是 {@code "404"} 这样的字面量参数，抹平字面量会让判据恒 0 命中＝假绿。
     *
     * <p>本仓已有单源实现 {@code common-api} 的 {@code testscan/SourceCommentStripper}，但它位于
     * <b>test</b> 源树且该模块未发布 test-jar ⇒ 跨模块不可引；与同仓其余门禁一样自带实现。</p>
     */
    static String blankOutCommentsOnly(String src) {
        return blank(src, false);
    }

    private static String blank(String src, boolean alsoBlankLiterals) {
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
                        out.append(alsoBlankLiterals ? "  " : src.substring(i, i + 2));
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        out.append(quote);
                        i++;
                        break;
                    }
                    out.append(alsoBlankLiterals ? (d == '\n' ? '\n' : ' ') : d);
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
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
}

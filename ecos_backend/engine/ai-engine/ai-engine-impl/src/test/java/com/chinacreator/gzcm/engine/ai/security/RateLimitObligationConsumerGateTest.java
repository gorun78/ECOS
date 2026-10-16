package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 限流义务键面的<b>单源 + 真消费</b>门禁（#142/B4-3d-4，收口差距 <b>D-B4-21</b>；
 * 与 D-B4-20 的 {@code DataScopeSingleSourceGateTest}、D-B4-17 的
 * {@code RlsExecutionContractGateTest}、D-B4-25 的 {@code ClsColumnSurfaceContractGateTest} 同族）。
 *
 * <p><b>钉什么</b>：引号字面量 {@code "rateLimitKey"} 与 {@code "ai.tool."} 在
 * {@code ecos_backend/**&#47;src/main/java/**} 内<b>只允许</b>出现在单源
 * {@link GuardrailDecisionContract}；同时钉三条"消费方真在链上"的代码位置事实
 * （咽喉调用位在 dispatch 之前、消费方是装配位、结果段不碰限流键）。
 * D-B4-21 的原形态是"策略签发、生产侧零消费方"，只加类不加门禁，
 * 下一版重构就能把这个类静默摘掉而全绿。</p>
 *
 * <p><b>为什么只钉这两个字面量</b>（刻意取舍，不是遗漏 ⇒ 登记 <b>D-B4-31</b>）：
 * 义务词 {@code "obligations"}/{@code "obligation"} 与 {@code "mask"}/{@code "watermark"}/
 * {@code "deniedReason"} 在主源码里还有多处<b>与护栏裁决无关</b>的同名用途
 * （实测 {@code "deniedReason"} 7 个文件、{@code "watermark"} 5 个文件持有），
 * 把它们一并钉进本门禁会得到一份需要逐条做属主判定的违例清单，反而稀释信号。
 * 该面的收敛须先做属主裁决再扩白名单，登记为 D-B4-31。</p>
 *
 * <p><b>假绿防护</b>：① 扫描根解析 0 个文件即失败；② 先剥注释再比对代码位置（注释里提键名
 * 是文档行为）；③ 复核 {@code data_access.rego} 仍在签发这两个字面量，
 * 防止"策略与门禁同时把键删掉"这种自洽空转；④ 委托位/调用位真实存在性单独断言，
 * 防止门禁靠"无人引用"变绿。</p>
 */
class RateLimitObligationConsumerGateTest {

    /** 唯一允许持有该键面字面量的主源码文件（按仓根相对路径比对，跨机不依赖绝对路径）。 */
    private static final String SOLE_OWNER_SUFFIX =
            "runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/guardrail/"
                    + "GuardrailDecisionContract.java";

    private static final String GATE_PATH =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiToolRateLimitGate.java";
    private static final String EXECUTOR_PATH =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java";
    private static final String APPLIER_PATH =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/ToolObligationApplier.java";
    private static final String OBLIGATIONS_PATH =
            "ecos_backend/engine/ai-engine/ai-engine-api/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/guardrail/ToolObligations.java";
    private static final String CONTRACT_PATH =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/guardrail/GuardrailDecisionContract.java";
    private static final String ERROR_CLASS_PATH =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/error/AiErrorClass.java";

    /** dispatch 的代码位置标记（限流腿必须排在它之前）。 */
    private static final String DISPATCH_MARKER = "toolRegistry.execute(toolName, arguments)";

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        return fail("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker），"
                + "本门禁不得默认通过");
    }

    private static Path repoFile(String relative) {
        Path p = repoRoot().resolve(relative);
        if (!Files.isRegularFile(p)) {
            fail("门禁依赖的文件不存在，不得当作\"无违例\": " + relative);
        }
        return p;
    }

    private static List<Path> backendMainSources() throws IOException {
        Path root = repoRoot().resolve("ecos_backend");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .forEach(files::add);
        }
        return files;
    }

    /** 只判代码行为：块注释与行注释一律抹平（换行保留 ⇒ 违例行号仍可定位）。 */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ");
    }

    private static String codeOf(String relativePath) {
        return stripComments(readString(relativePath));
    }

    private static String readString(String relativePath) {
        return new String(readBytes(repoFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new AssertionError("读取源码失败: " + p, e);
        }
    }

    // ── 验收 1：键面单源 ────────────────────────────────────────────────────

    @Test
    @DisplayName("rateLimitKey/ai.tool. 字面量单源：主源码内除 GuardrailDecisionContract 外零持有")
    void rateLimitKeyLiteralsHaveSingleOwner() throws IOException {
        List<Path> files = backendMainSources();
        // 假绿防护①：扫描根必须真吃到文件
        assertTrue(files.size() > 500,
                "主源码扫描根解析到的文件数异常（" + files.size() + "）——「读不到」不得当成「无违例」");

        String quotedKey = "\"" + GuardrailDecisionContract.KEY_RATE_LIMIT_KEY + "\"";
        String quotedPrefix = "\"" + GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "\"";
        List<String> violations = new ArrayList<>();
        int ownerHits = 0;
        for (Path f : files) {
            String norm = f.toString().replace('\\', '/');
            String code = stripComments(new String(readBytes(f), StandardCharsets.UTF_8));
            boolean hitsKey = code.contains(quotedKey);
            boolean hitsPrefix = code.contains(quotedPrefix);
            if (!hitsKey && !hitsPrefix) continue;
            if (norm.endsWith(SOLE_OWNER_SUFFIX)) {
                ownerHits++;
                continue;
            }
            violations.add(norm.substring(norm.indexOf("ecos_backend/"))
                    + " → " + quotedKey + "=" + hitsKey + " " + quotedPrefix + "=" + hitsPrefix);
        }

        assertTrue(violations.isEmpty(),
                "发现第二套限流键面字面量（键名两侧各写各的正是 D-B4-21/D-B4-20 的根因形态）:\n"
                        + String.join("\n", violations));
        // 假绿防护②：单源确实持有（否则"零违例"只是键面消失的假象）
        assertEquals(1, ownerHits, "单源文件未持有键面字面量 ⇒ 本门禁已空转: " + ownerHits);
    }

    @Test
    @DisplayName("策略侧仍在签发该义务：data_access.rego 的字面量与单源逐字一致")
    void policySideStillIssuesTheObligation() {
        String rego = readString("ecos-docker/opa-policies/data_access.rego");

        assertTrue(rego.contains("\"" + GuardrailDecisionContract.OBLIGATION_RATE_LIMIT + "\""),
                "data_access.rego 不再签发 rateLimit ⇒ 消费方与本门禁的验收依据同时失效");
        assertTrue(rego.contains("\"" + GuardrailDecisionContract.KEY_RATE_LIMIT_KEY + "\""));
        assertTrue(rego.contains("ai.tool."),
                "rego 的 sprintf 键模板被改 ⇒ 单源前缀常量与策略结论已经分叉: "
                        + GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX);
        assertTrue(rego.contains(GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX),
                "策略键模板须与单源前缀逐字相同（" + GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "）");
    }

    // ── 验收 2：消费方真在咽喉链上，且位置在 dispatch 之前 ──────────────────

    @Test
    @DisplayName("咽喉调用位在 dispatch 之前（限流决定\"能否发起\"，不放结果回传段）")
    void rateLimitLegRunsBeforeDispatch() {
        String code = codeOf(EXECUTOR_PATH);

        int enforceAt = code.indexOf("rateLimitEnforce(toolName, decision, callId, startNs)");
        int dispatchAt = code.indexOf(DISPATCH_MARKER);
        assertTrue(enforceAt >= 0, "咽喉未调用限流腿（摘掉这一行就该红，而不是靠单测里没人调的类）");
        assertTrue(dispatchAt >= 0, "未识别到 dispatch 位置标记，本断言不得默认通过: " + DISPATCH_MARKER);
        assertTrue(enforceAt < dispatchAt,
                "限流腿排到 dispatch 之后＝先花后端预算再拒绝，义务语义失效");
        assertTrue(code.indexOf("ToolResult rateBlock") < code.indexOf("try {", enforceAt - 1),
                "限流拦截位必须在 try/dispatch 块之外，拒绝路径不得吞进后端异常分类");
    }

    @Test
    @DisplayName("消费方装配位真实存在且未装配时 fail-closed（禁退回「有义务没人消费」）")
    void consumerIsWiredAndFailsClosedWhenAbsent() {
        String executor = codeOf(EXECUTOR_PATH);
        String gate = codeOf(GATE_PATH);

        assertTrue(executor.contains("setRateLimitGate"), "咽喉未暴露限流消费方装配位");
        assertTrue(executor.contains("AiToolRateLimitGate.RateOutcome"), "咽喉未使用限流判定结果类型");
        assertTrue(executor.contains("AI_TOOL_RATE_LIMIT_GATE_UNWIRED"),
                "消费方未装配时未落显式不可用标记 ⇒ 会被静默放行（D-B4-21 原形态）");
        assertTrue(gate.contains("@Component"), "消费方不是可装配组件");
        assertTrue(gate.contains("GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX"),
                "消费方未委托单源前缀（那意味着第二套键面回来了）");
        assertFalse(gate.contains("RATE_LIMIT_KEY_PREFIX +"),
                "消费方不得用前缀常量拼键 —— 键原文只能来自策略义务，否则执行面重建了一套键面");
    }

    @Test
    @DisplayName("义务解析委托单源读数，且结果段不消费限流键")
    void payloadParsingDelegatesAndApplierStaysOut() {
        String obligations = codeOf(OBLIGATIONS_PATH);
        String applier = codeOf(APPLIER_PATH);

        assertTrue(obligations.contains("GuardrailDecisionContract.readRateLimitDeclaration"),
                "解析侧未委托单源读数（改回自拼键名会重新产生丢义务形态）");
        assertTrue(obligations.contains("declaresRateLimit()"), "载荷未暴露限流声明位");
        assertFalse(applier.contains("rateLimitKey("),
                "限流键在结果回传段被消费＝消费位置错误（应在 dispatch 之前）");
        assertFalse(applier.contains("AiToolRateLimitGate"),
                "结果段不得自己起一个限流器（那会是第二套执行面）");
    }

    @Test
    @DisplayName("三态分类落在单源错误码表上（未新增 429 护栏码，理由见差距文档 v1.0.21 ⑤c）")
    void verdictsMapOntoFrozenErrorClasses() {
        String executor = codeOf(EXECUTOR_PATH);
        String errorClasses = codeOf(ERROR_CLASS_PATH);

        assertTrue(executor.contains("AiErrorClass.GUARDRAIL_DENIED"),
                "配额用尽未归 GUARDRAIL_DENIED（403 不重试）");
        assertTrue(executor.contains("AiErrorClass.GUARDRAIL_FAIL_CLOSED"),
                "形态不合契约未归 GUARDRAIL_FAIL_CLOSED（503）");
        assertFalse(errorClasses.contains("GUARDRAIL_RATE"),
                "D.5 码表被私自扩了一枚护栏限流码 —— 码表冻结，扩码须走裁决批（本批复用 GUARDRAIL_DENIED）");
    }

    @Test
    @DisplayName("登记不闭项在位（D-B4-31/32/33 不得被\"顺手标已闭\"）")
    void followUpGapsAreRegistered() {
        String contract = readString(CONTRACT_PATH);
        String gate = readString(GATE_PATH);

        assertTrue(contract.contains("D-B4-31"),
                "义务词单源未扩的差距登记被抹掉 ⇒ 后续批无从判断本门禁为何只钉两个字面量");
        assertTrue(gate.contains("D-B4-32"), "跨实例配额/数值配置化的差距登记被抹掉");
        assertTrue(gate.contains("D-B4-33"),
                "限流腿拒绝态未落 V204 台账的差距登记被抹掉 ⇒ 台账面缺口会被读成已收口");
    }
}

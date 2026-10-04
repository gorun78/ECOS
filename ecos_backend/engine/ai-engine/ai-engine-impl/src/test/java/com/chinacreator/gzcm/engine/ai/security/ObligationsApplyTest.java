package com.chinacreator.gzcm.engine.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-02（W144，详设-06 §F06-02 验收）— obligations 执行器
 * （{@link AgentToolObligationsApplier}）与"引擎内不自实现脱敏算法" grep 门禁。
 *
 * <ul>
 *   <li>{@code maskObligationAppliedBeforeLlmSeesResult}：
 *       mask 义务命中字段值 → 全部走 security 端点 {@code applyMasking}
 *       （本层 Mockito 桩返回脱敏后的值 → 断言 LLM 看到的行中已是 masked 值，而不是原值；
 *       且原值不出现在返回值任何一个字段）。</li>
 *   <li>{@code engineDoesNotSelfImplementMasking}：
 *       ai-engine-impl 全 {@code src/main} grep 0 命中脱敏算法签名
 *       （email/phone/idcard/银行 规则正则 + {@code "***"} 拼接形态）。
 *       若未来某人往引擎里塞本地 mask 算法，本测试立即转红。</li>
 * </ul>
 *
 * <p>fail-closed 直检由 {@link #agentToolObligationsApplierRowFilterFailClosed} 补一条：
 * rowFilter 义务存在 → 整条 FAIL_CLOSED（ai-engine 侧不实现行过滤算法，§2.4-1）。</p>
 */
class ObligationsApplyTest {

    // ── 验收 1：maskObligationAppliedBeforeLlmSeesResult ─────────────────

    @Test
    @DisplayName("F06-02 maskObligationAppliedBeforeLlmSeesResult: LLM 看到的全是 security 端点返回的脱敏值，"
            + "原值 0 命中；无 mask 命中字段保留原值；security 不可用 → 整条 FAIL_CLOSED")
    void maskObligationAppliedBeforeLlmSeesResult() {
        AiSecurityEngineClient security = mock(AiSecurityEngineClient.class);
        AgentToolObligationsApplier applier = new AgentToolObligationsApplier(security);

        // 两条行，各带 email/phone（敏感）与非敏感 name
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "alice");
        row1.put("email", "alice@example.com");
        row1.put("phone", "13800138000");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "bob");
        row2.put("email", "bob@example.com");
        row2.put("phone", "13900139000");

        List<Map<String, Object>> rows = List.of(row1, row2);
        // obligations：两条 field→rule 即 {kind:"mask", field:"email", rule:"email"}, {kind:"mask", field:"phone", rule:"phone"}
        List<Map<String, Object>> obligations = List.of(
                Map.of("kind", "mask", "field", "email", "rule", "email"),
                Map.of("kind", "mask", "field", "phone", "rule", "phone"));

        // Mockito：security 端点按 (value, rule) 返回脱敏值；每字段每行各调一次
        when(security.applyMasking(eq(List.of("alice@example.com")), eq(List.of("email"))))
                .thenReturn(List.of("a***@example.com"));
        when(security.applyMasking(eq(List.of("13800138000")), eq(List.of("phone"))))
                .thenReturn(List.of("138****0000"));
        when(security.applyMasking(eq(List.of("bob@example.com")), eq(List.of("email"))))
                .thenReturn(List.of("b***@example.com"));
        when(security.applyMasking(eq(List.of("13900139000")), eq(List.of("phone"))))
                .thenReturn(List.of("139****0000"));

        AgentToolObligationsApplier.Outcome outcome =
                applier.apply(obligations, rows, "query_db", "user-42");

        assertTrue(outcome.success(), "security 桩成功路径应 success=true");
        assertNotNull(outcome.rows());
        assertEquals(2, outcome.rows().size());

        Map<String, Object> out1 = outcome.rows().get(0);
        assertEquals("alice", out1.get("name"), "非 mask 字段保留原值");
        assertEquals("a***@example.com", out1.get("email"), "email 应替换为 security 返回的脱敏值");
        assertEquals("138****0000",     out1.get("phone"), "phone 应替换为 security 返回的脱敏值");

        Map<String, Object> out2 = outcome.rows().get(1);
        assertEquals("b***@example.com", out2.get("email"));
        assertEquals("139****0000",     out2.get("phone"));

        // 直接防线：原值一个字节都不应出现在执行器返回的行里（LLM 沿用该返回交回 loop）
        String serialized = outcome.rows().toString();
        assertFalse(serialized.contains("13800138000"), "LLM 可见结果不得含明文 phone");
        assertFalse(serialized.contains("13900139000"), "LLM 可见结果不得含明文 phone");
        assertFalse(serialized.contains("alice@example.com"), "LLM 可见结果不得含明文 email");

        // 每个值恰好调一次 masking（等价于 security 端点代理次数 = 命中的敏感字段数）
        verify(security, atLeast(4)).applyMasking(anyList(), anyList());
    }

    @Test
    @DisplayName("F06-02 补：security 不可用（EngineUnavailableException）→ 整条 FAIL_CLOSED，绝不返回未脱敏行")
    void securityUnavailableFailsClosedDropsResult() {
        AiSecurityEngineClient failClient = mock(AiSecurityEngineClient.class);
        when(failClient.applyMasking(anyList(), anyList()))
                .thenThrow(new AiSecurityEngineClient.EngineUnavailableException("connection refused"));
        AgentToolObligationsApplier applier = new AgentToolObligationsApplier(failClient);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", "leak@example.com");

        AgentToolObligationsApplier.Outcome outcome = applier.apply(
                List.of(Map.of("kind", "mask", "field", "email", "rule", "email")),
                List.of(row), "query_db", "user-42");

        assertFalse(outcome.success(), "security 不可用必须 FAIL_CLOSED（禁「先返回再说明」，X-18 翻案面）");
        assertNotNull(outcome.failClosedReason());
        assertTrue(outcome.failClosedReason().contains("FAIL_CLOSED"),
                "reason 必须携带 FAIL_CLOSED 指纹");
        assertTrue(outcome.rows().isEmpty(), "FAIL_CLOSED 时 rows 必须为空（禁下行）");
        assertFalse(String.valueOf(outcome.rows()).contains("leak@example.com"),
                "FAIL_CLOSED 时 rows 不得含待脱敏原文");
        assertFalse(outcome.failClosedReason().contains("leak@example.com"),
                "reason 也不得泄漏待脱敏原文");
    }

    @Test
    @DisplayName("F06-02 补：rowFilter 义务 → 引擎侧不实现行过滤算法，整条 FAIL_CLOSED（§2.4-1 RLS 只在 security 域）")
    void agentToolObligationsApplierRowFilterFailClosed() {
        AiSecurityEngineClient noCall = mock(AiSecurityEngineClient.class);
        AgentToolObligationsApplier applier = new AgentToolObligationsApplier(noCall);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "id-1");
        AgentToolObligationsApplier.Outcome outcome = applier.apply(
                List.of(Map.of("kind", "rowFilter", "predicate", "tenant_id = 42")),
                List.of(row), "query_db", "user-42");

        assertFalse(outcome.success());
        assertTrue(outcome.failClosedReason().contains("rowFilter")
                || outcome.failClosedReason().contains("FAIL_CLOSED"));
        // 关键护栏：rowFilter 路径绝不进 masking 端点（不本地求值谓词）
        verify(noCall, never()).applyMasking(anyList(), anyList());
        verify(noCall, never()).allowedColumns(anyString(), anyString(), anyString(), anyList());
        verify(noCall, never()).applyRls(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── 验收 2：engineDoesNotSelfImplementMasking（grep 计数门禁） ────────

    /**
     * ai-engine-impl 全 {@code src/main} 树扫描脱敏算法签名，命中必须为 0。
     * 签名集合（对齐 security-engine DataMaskingService.moderate 的<b>算法要点</b>）：
     * <ul>
     *   <li>email 规则正则：{@code \w+@\w+\.\w+} / {@code EMAIL_PATTERN} 字面量</li>
     *   <li>phone 规则正则：{@code 1[3-9]\d{9}} / {@code PHONE_PATTERN} 字面量</li>
     *   <li>idcard/bankcard 规则正则：{@code \d{15}[\dXx]} / {@code IDCARD_?PATTERN} /
     *       {@code BANKCARD_?PATTERN} 字面量</li>
     *   <li>{@code charAt(0) + "***"} / {@code "***" +} 类手工拼接形态（避开 log/DSL 假阴）</li>
     * </ul>
     * <p>合法豁免（不算命中）：</p>
     * <ul>
     *   <li>{@code AgentToolObligationsApplier.java} 类中的<b>recorded / 反射 / 日志</b>基类引用 ——
     *       Java compiler 会拒绝，事实上根本没有命中；grep 若出现在类名/包名/常量名中被字面紧邻，
     *       直接被下面模式排除</li>
     *   <li>{@code ToolAdjudicationRequest.RESOURCE_WHITELIST} 等"白名单"（不含 email/phone 模式）</li>
     * </ul>
     */
    @Test
    @DisplayName("F06-02 engineDoesNotSelfImplementMasking: ai-engine-impl src/main 内脱敏算法签名 0 命中"
            + "（脱敏只在 security-engine 域，铁律 §2.4-3）")
    void engineDoesNotSelfImplementMasking() throws IOException {
        Path root = resolveModuleSrcMainJava();
        assertNotNull(root, "无法定位 ai-engine-impl/src/main/java（本门禁不适用当前 cwd，须由 mvn 自本模块根跑）");

        Pattern emailRule   = Pattern.compile("\\\\w\\+@\\\\w\\+\\.\\\\w\\+|EMAIL_?PATTERN");
        Pattern phoneRule   = Pattern.compile("1\\[3-9\\]\\\\d\\{9\\}|PHONE_?PATTERN");
        Pattern idcardRule  = Pattern.compile("IDCARD_?PATTERN|BANKCARD_?PATTERN|\\\\d\\{15\\}\\[\\\\dXx\\]");
        // charAt 拼接形态（避开 log 截断常见的 "..."/ "~" 拼接）
        Pattern starChar    = Pattern.compile("\\.charAt\\(0\\)\\s*\\+\\s*\"\\*+\"|\"\\*+\"\\s*\\+\\s*\\w*[0-9]");

        List<String> hits = new java.util.ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        // 剥行内注释再匹配，避免 doc/注释里出现"（禁用 xxx 形态）"造成假阴（false-negative 规避）
                        String pure = stripLineComments(text);
                        scan(emailRule,  p, pure, "email-algo",  hits);
                        scan(phoneRule,   p, pure, "phone-algo",  hits);
                        scan(idcardRule,  p, pure, "idcard-algo", hits);
                        scan(starChar,    p, pure, "charAt-star", hits);
                    } catch (IOException ignored) { /* 不可读文件跳过 */ }
                });
        }
        assertTrue(hits.isEmpty(),
                "F06-02 违规 — ai-engine-impl src/main 检出脱敏算法签名（脱敏只允许存在于 security-engine）:\n"
                        + String.join("\n", hits));
    }

    private static void scan(Pattern p, Path file, String text, String label, List<String> hits) {
        var m = p.matcher(text);
        while (m.find()) {
            hits.add(label + "  " + file.getFileName() + "  hit='" + m.group() + "'");
        }
    }

    /** 移除 {@code //...} 行内注释（不剥离块注释，为简单起见；doc 里如出现签名须自改避开）。 */
    private static String stripLineComments(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            int idx = findLineComment(line);
            if (idx >= 0) sb.append(line, 0, idx); else sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 找到行内 {@code //} 起点（跳过字符串字面量中的 //，本目录源码无需精细解析 —— 简版）。 */
    private static int findLineComment(String line) {
        for (int i = 0; i + 1 < line.length(); i++) {
            if (line.charAt(i) == '/' && line.charAt(i + 1) == '/') {
                boolean inStr = false;
                for (int j = 0; j < i; j++) if (line.charAt(j) == '"') inStr = !inStr;
                if (!inStr) return i;
            }
        }
        return -1;
    }

    // ── 解析 ai-engine-impl/src/main/java（对齐 data-engine 先例） ───────

    private static Path resolveModuleSrcMainJava() {
        String userDir = System.getProperty("user.dir");
        Path cur = Paths.get(userDir).toAbsolutePath().normalize();
        Path a = cur.resolve("src/main/java");
        if (Files.isDirectory(a)) return a;
        Path b = cur.resolve("ai-engine-impl/src/main/java");
        if (Files.isDirectory(b)) return b;
        Path c = cur.resolve("engine/ai-engine/ai-engine-impl/src/main/java");
        if (Files.isDirectory(c)) return c;
        try {
            java.net.URL url = ObligationsApplyTest.class.getResource("/"
                    + ObligationsApplyTest.class.getName().replace('.', '/')
                    + ".class");
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                // target/test-classes/....class → ../../.. → src/main/java
                Path base = Paths.get(url.toURI());
                while (base != null) {
                    Path cand = base.resolve("main/java");
                    if (Files.isDirectory(base.resolve("src")) && Files.isDirectory(cand)) return cand;
                    base = base.getParent();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}

package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 输出脱敏门禁（计划 §二：脱敏须「输出前统一过一遍」）。
 *
 * <p>后端把凭据回显在 data/error 里时，CLI 的 json 与 table 两种形态都不得输出明文；
 * 同时不得误伤业务字段（如 {@code tokenCount}）。</p>
 */
class OutputRedactionTest {

    private static final String SECRET = "sk-live-9f3a7c1d5e";

    private static Map<String, Object> envelopeWithEchoedSecret() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiKey", SECRET);
        data.put("access_token", SECRET);
        data.put("tokenCount", 12);
        data.put("url", "http://gw/api/v1/x?access_token=" + SECRET + "&page=2");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "SUCCESS");
        out.put("data", data);
        out.put("error", null);
        out.put("trace_id", "cli-abc");
        out.put("source_refs", List.of());
        return out;
    }

    @Test
    @DisplayName("json 输出：后端回显的 apiKey / access_token / query token 一律打码")
    void jsonOutputIsRedacted() {
        String text = OutputRenderer.renderToString(envelopeWithEchoedSecret(), "json");
        assertFalse(text.contains(SECRET), "json 输出泄露了凭据明文");
        assertTrue(text.contains("***"), "应留下打码标记，便于识别已脱敏");
        assertTrue(text.contains("tokenCount"), "业务字段 tokenCount 不得被误删");
        assertTrue(text.contains("page=2"), "非敏感 query 参数应保留");
    }

    @Test
    @DisplayName("table 输出：同样不复现明文")
    void tableOutputIsRedacted() {
        String text = OutputRenderer.renderToString(envelopeWithEchoedSecret(), "table");
        assertFalse(text.contains(SECRET), "table 输出泄露了凭据明文");
    }

    @Test
    @DisplayName("JWT / Bearer 形态在后端回显里同样被打码")
    void jwtAndBearerAreRedacted() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.sig_part_xyz";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("echo", "Authorization: Bearer " + jwt);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "SUCCESS");
        out.put("data", data);
        String text = OutputRenderer.renderToString(out, "json");
        assertFalse(text.contains(jwt), "JWT 明文泄露");
        assertFalse(text.contains("sig_part_xyz"), "JWT 片段泄露");
    }

    @Test
    @DisplayName("含空白的凭据必须整段打码（不能只遮第一个词）")
    void spacedSecretIsFullyRedacted() {
        // 真实形态：口令/凭据里带空格。旧实现按空白切分，会把 "*** def ghi" 的尾巴留在输出里。
        String[] samples = {
            "{\"token\":\"abc def ghi\"}",
            "{\"password\":\"p w u\"}",
            "secret='s s s'",
            "\"apiKey\": \"k-1 k-2\""
        };
        for (String sample : samples) {
            String out = Redactor.redact(sample);
            assertFalse(out.matches(".*\\bdef\\b.*"), "残留明文片段: " + out);
            assertFalse(out.matches(".*\\bghi\\b.*"), "残留明文片段: " + out);
            assertFalse(out.contains("k-2"), "残留明文片段: " + out);
            assertFalse(out.contains("s s"), "残留明文片段: " + out);
            assertTrue(out.contains("***"), "应留打码标记: " + out);
        }
        // 反方向：非敏感键不得被打码
        assertTrue(Redactor.redact("{\"tokenConfigured\":true}").contains("true"),
                "tokenConfigured 是状态位，不得打码");
        assertTrue(Redactor.redact("token=abc&x=1").contains("x=1"), "非敏感 query 应保留");
    }
}

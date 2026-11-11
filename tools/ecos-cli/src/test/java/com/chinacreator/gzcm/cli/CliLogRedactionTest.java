package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 结构化日志不得落敏感内容（计划 §二：默认不落 token/body）。
 *
 * <p>真实写盘 + 读回校验，而不是只测正则：日志是长期留存产物，
 * 一次误传 {@code Map.of("token", …)} 就会把凭据写在磁盘上。</p>
 */
class CliLogRedactionTest {

    private static final String SECRET = "sk-log-7d1c9a3f";
    private String previousHome;

    @BeforeEach
    void setUp() {
        previousHome = System.getProperty("user.home");
        Path tmp;
        try {
            tmp = Files.createTempDirectory("ecos-cli-log-");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        System.setProperty("user.home", tmp.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", previousHome);
    }

    private static String readLog() throws Exception {
        Path file = CliLog.logFile();
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    @Test
    @DisplayName("token/body/authorization 等字段名一律打码，不落盘")
    void sensitiveKeysAreMasked() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("token", SECRET);
        fields.put("body", "{\"password\":\"" + SECRET + "\"}");
        fields.put("authorization", "Bearer " + SECRET);
        fields.put("approvalToken", SECRET);
        fields.put("command", "agent run");
        CliLog.event("probe", fields);

        String log = readLog();
        assertFalse(log.isBlank(), "日志未写入，用例失去意义");
        assertFalse(log.contains(SECRET), "敏感值落盘了");
        assertTrue(log.contains("***"), "应留打码标记");
        assertTrue(log.contains("agent run"), "非敏感字段必须保留（否则日志失去排障价值）");
    }

    @Test
    @DisplayName("值里夹带的 JWT/URL token 也被脱敏（字段名安全不代表值安全）")
    void sensitiveValuesAreRedacted() throws Exception {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.sigsegment";
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("detail", "call http://gw/x?access_token=" + SECRET + "&page=2");
        fields.put("hint", "Authorization: Bearer " + jwt);
        CliLog.event("probe2", fields);

        String log = readLog();
        assertFalse(log.contains(SECRET), "URL query 里的 token 落盘了");
        assertFalse(log.contains(jwt), "JWT 落盘了");
        assertTrue(log.contains("page=2"), "非敏感 query 应保留");
    }

    @Test
    @DisplayName("敏感键判定：tokenConfigured 属布尔状态，不误伤")
    void tokenConfiguredIsNotTreatedAsSecret() {
        assertFalse(CliLog.isSensitiveKey("tokenConfigured"));
        assertEquals(true, CliLog.isSensitiveKey("token"));
        assertEquals(true, CliLog.isSensitiveKey("bodyJson"));
        assertEquals(true, CliLog.isSensitiveKey("Authorization"));
        assertEquals(false, CliLog.isSensitiveKey("command"));
        assertEquals(false, CliLog.isSensitiveKey("elapsedMs"));
    }

    @Test
    @DisplayName("业务载荷键也必须打码：payload/requestBody/responseBody 不得原样进 jsonl")
    void payloadLikeKeysAreRedacted() {
        // --payload-json / --basis-json 的内容是业务正文，长期落盘等同数据外洩
        for (String key : new String[] {"payload", "payloadJson", "payload_json", "Payload",
                "basisJson", "requestBody", "responseBody", "situationSnapshotJson"}) {
            assertTrue(CliLog.isSensitiveKey(key), "业务载荷键未打码: " + key);
        }
        // 不得误伤正常观测字段
        for (String key : new String[] {"command", "status", "traceId", "elapsedMs", "event", "level"}) {
            assertFalse(CliLog.isSensitiveKey(key), "观测字段被误判为敏感: " + key);
        }
    }
}

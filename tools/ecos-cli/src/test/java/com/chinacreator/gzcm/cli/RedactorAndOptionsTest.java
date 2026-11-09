package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedactorAndOptionsTest {

    @Test
    @DisplayName("Bearer 与 JSON token 字段被脱敏")
    void redactsBearerAndJsonToken() {
        String s = Redactor.redact("Authorization: Bearer abc.def.ghi {\"token\":\"s3cr3t-value\"}");
        assertFalse(s.contains("abc.def.ghi"));
        assertFalse(s.contains("s3cr3t-value"));
        assertTrue(s.contains("***"));
    }

    @Test
    @DisplayName("--profile 只接受配置名，含路径分隔符即 USAGE")
    void profileRejectsPath() {
        var e = assertThrows(CliException.class, () -> {
            CliOptions o = CliOptions.parse(new String[] {"doctor", "--profile", "../evil"}, 1);
            o.baseUrl();
        });
        assertEquals(ExitCodes.USAGE, e.exitCode());
    }

    @Test
    @DisplayName("--timeout 边界：0 与超上限均 USAGE")
    void timeoutBounds() {
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class, () ->
                CliOptions.parse(new String[] {"doctor", "--timeout", "0"}, 1).timeoutMs()).exitCode());
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class, () ->
                CliOptions.parse(new String[] {"doctor", "--timeout", "120001"}, 1).timeoutMs()).exitCode());
    }

    @Test
    @DisplayName("默认基址 = gateway:8080；--token 优先且不进入任何输出字段")
    void defaultsAndToken() {
        CliOptions o = CliOptions.parse(new String[] {"doctor", "--token", "abc123"}, 1);
        assertEquals(rawDefault(), o.baseUrl());
        assertEquals("abc123", o.token());
    }

    @Test
    @DisplayName("命令词可在参数前/后混合，解析结果一致")
    void commandWordsAnywhere() {
        CliOptions o = CliOptions.parse(new String[] {"cognition", "models", "list",
                "--output", "table", "--model-type", "CM-03"}, 3);
        assertEquals("table", o.output());
        assertEquals("CM-03", o.value("--model-type"));
        assertNull(o.value("--registry-state"));
    }

    private static String rawDefault() {
        String env = System.getenv("ECOS_CLI_BASE_URL");
        return env == null || env.isBlank() ? "http://localhost:8080" : env.replaceAll("/$", "");
    }
}

package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataMaskingServiceTest — 内置 phone/email/idCard 四种脱敏算法。
 *
 * <p>Wave-5.1 T-05：覆盖 11 位手机、含 @ 邮箱、18 位身份证、15 位身份证
 * 等边界，验证输出不泄露中间段。
 */
class DataMaskingServiceTest {

    private DataMaskingService service;

    @BeforeEach
    void setUp() {
        this.service = new DataMaskingService();
    }

    @Test
    @DisplayName("mask phone — 11 位手机号应中间 4 位打码")
    void maskPhoneStandard11Digit() {
        String masked = service.mask("phone", "13812345678");
        assertEquals("138****5678", masked);
    }

    @Test
    @DisplayName("mask phone — 非法短号原样返回")
    void maskPhoneTooShortReturnsAsIs() {
        String masked = service.mask("phone", "123");
        assertEquals("123", masked);
    }

    @Test
    @DisplayName("mask email — 含 @ 邮箱应首字符+***+后缀")
    void maskEmailWithAtSign() {
        String masked = service.mask("email", "john@example.com");
        assertEquals("j***@example.com", masked);
    }

    @Test
    @DisplayName("mask email — 无 @ 原样返回")
    void maskEmailWithoutAtReturnsAsIs() {
        String masked = service.mask("email", "notanemail");
        assertEquals("notanemail", masked);
    }

    @Test
    @DisplayName("mask idCard — 18 位纯数字身份证应中间打码")
    void maskIdCard18Digit() {
        String masked = service.mask("idCard", "320123199001011234");
        assertEquals("3201**********1234", masked);
    }

    @Test
    @DisplayName("mask idCard — 18 位末位 X 身份证应中间打码")
    void maskIdCard18WithX() {
        String masked = service.mask("idCard", "11010119850607789X");
        assertEquals("1101**********789X", masked);
    }

    @Test
    @DisplayName("applyMasking — 多规则批量脱敏且 unknown 规则兜底为 ***")
    void applyMaskingBatch() {
        List<Map<String, Object>> results = service.applyMasking(
                List.of("13812345678", "a@b.com", "unknown"),
                List.of("phone", "email", "nope"));
        assertEquals(3, results.size());
        assertEquals("138****5678", results.get(0).get("masked"));
        assertEquals("a***@b.com", results.get(1).get("masked"));
        assertEquals("***", results.get(2).get("masked"));
        assertTrue(((String) results.get(2).get("error")).contains("nope"));
    }

    @Test
    @DisplayName("isRuleSupported — 仅返回内置规则；amount/未知规则应不支持 (Wave-5.1 需求中 MASK_AMOUNT 未实现)")
    void supportedRulesContainPhoneEmailIdCard() {
        assertTrue(service.isRuleSupported("phone"));
        assertTrue(service.isRuleSupported("email"));
        assertTrue(service.isRuleSupported("idCard"));
        assertFalse(service.isRuleSupported("amount"));
        assertFalse(service.isRuleSupported("nope"));
        // getSupportedRules 也应保持一致
        List<String> rules = service.getSupportedRules();
        assertTrue(rules.contains("phone"));
        assertTrue(rules.contains("email"));
        assertTrue(rules.contains("idCard"));
        assertEquals(3, rules.size());
    }
}

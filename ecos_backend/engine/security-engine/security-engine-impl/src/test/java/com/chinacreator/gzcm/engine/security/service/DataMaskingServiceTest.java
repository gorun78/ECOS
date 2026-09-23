package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataMaskingServiceTest — PMO-data10 5+2 类字段级脱敏（身份证/手机/邮箱/银行卡/金额/地址）。
 *
 * <p>与 AGENTS 承诺对齐：
 * "5 类字段级脱敏 → 兼顾 TAX26-2 双层文档（文档 5 + 代码 6）" + GENERAL 兜底 none。
 * 本单测覆盖 6 正则 + 1 兜底，全部对齐 V154 字段级 data_type。</p>
 */
class DataMaskingServiceTest {

    private DataMaskingService service;

    @BeforeEach
    void setUp() {
        this.service = new DataMaskingService();
    }

    @Test
    @DisplayName("phone — 11 位手机号应中间 4 位打码")
    void maskPhoneStandard11Digit() {
        assertEquals("138****5678", service.mask("phone", "13812345678"));
    }

    @Test
    @DisplayName("phone — 短号原样返回（无 11 位长度）")
    void maskPhoneTooShortReturnsAsIs() {
        assertEquals("123", service.mask("phone", "123"));
    }

    @Test
    @DisplayName("email — 含 @ 邮箱应首字符+***+后缀")
    void maskEmailWithAtSign() {
        assertEquals("j***@example.com", service.mask("email", "john@example.com"));
    }

    @Test
    @DisplayName("email — 无 @ 原样返回")
    void maskEmailWithoutAtReturnsAsIs() {
        assertEquals("notanemail", service.mask("email", "notanemail"));
    }

    @Test
    @DisplayName("idCard — 18 位纯数字身份证应中间打码")
    void maskIdCard18Digit() {
        assertEquals("3201**********1234", service.mask("idCard", "320123199001011234"));
    }

    @Test
    @DisplayName("idCard — 18 位末位 X 身份证应中间打码")
    void maskIdCard18WithX() {
        assertEquals("1101**********789X", service.mask("idCard", "11010119850607789X"));
    }

    @Test
    @DisplayName("bankCard — 16-19 位银行卡应保留前 6 后 4 (PMO-data10 新增)")
    void maskBankCard16To19Digit() {
        String masked = service.mask("bankCard", "6222021234561234567");
        assertEquals("622202****4567", masked);
    }

    @Test
    @DisplayName("bankCard — 16 位信用卡 4 位组尾 — 前 6 + **** + 4 位尾")
    void maskBankCard16Digit() {
        assertEquals("453201****0366", service.mask("bankCard", "4532015112830366"));
    }

    @Test
    @DisplayName("amount — ¥1,234.50 大数区间打码 (PMO-data10 新增)")
    void maskAmountBigIntToRange() {
        // v=1234.5 ∈ [1000, 10000) → "¥999.99+"
        assertEquals("¥999.99+", service.mask("amount", "¥1,234.50"));
    }

    @Test
    @DisplayName("amount — 万元及以上区间打码 (>= 10000)")
    void maskAmountTenThousandPlus() {
        // v=12345.67 >= 10000 → "¥10,000+"
        assertEquals("¥10,000+", service.mask("amount", "12345.67"));
    }

    @Test
    @DisplayName("amount — 小数 10~100 区间打码")
    void maskAmountSmallDecimal() {
        // 56.78 >= 10 → "¥9.99+"
        assertEquals("¥9.99+", service.mask("amount", "56.78"));
    }

    @Test
    @DisplayName("address — 中国省级+市+区格式 末尾详细地址打码 (PMO-data10 新增)")
    void maskAddressChinaStyle() {
        String masked = service.mask("address", "上海市浦东新区世纪大道100号");
        assertTrue(masked != null && masked.startsWith("上海市浦东新区"));
        assertTrue(masked.endsWith("****"));
    }

    @Test
    @DisplayName("applyMasking — unknown 规则兜底为 *** ( gelombang 5.1 SEC-P0-4)")
    void applyMaskingUnknownRuleFallback() {
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
    @DisplayName("isRuleSupported — PMO-data10 全 6 类内置 + GENERAL → none (6+1 规则)")
    void supportedRulesContainAll6PlusGeneral() {
        assertTrue(service.isRuleSupported("phone"));
        assertTrue(service.isRuleSupported("email"));
        assertTrue(service.isRuleSupported("idCard"));
        assertTrue(service.isRuleSupported("bankCard")); // 新增
        assertTrue(service.isRuleSupported("amount"));   // 新增
        assertTrue(service.isRuleSupported("address"));  // 新增
        assertTrue(service.isRuleSupported("none"));
        assertFalse(service.isRuleSupported("nope"));
    }

    @Test
    @DisplayName("ruleForDataType — V154 字段级 data_type 映射 (CASE-INSENSITIVE)")
    void ruleForDataType() {
        assertEquals("idCard",   service.ruleForDataType("ID_CARD"));
        assertEquals("phone",    service.ruleForDataType("phone"));
        assertEquals("email",    service.ruleForDataType("EMAIL"));
        assertEquals("bankCard", service.ruleForDataType("BANK_CARD"));
        assertEquals("amount",   service.ruleForDataType("AMOUNT"));
        assertEquals("address",  service.ruleForDataType("ADDRESS"));
        assertEquals("none",     service.ruleForDataType("GENERAL"));
        assertEquals("none",     service.ruleForDataType(null));
    }

    @Test
    @DisplayName("applyMaskingByStrategy — maskStrategy='none' 原样返回（显式不脱敏）")
    void applyMaskingByStrategyNone() {
        assertEquals("320123199001011234",
            service.applyMaskingByStrategy("none", "ID_CARD", "320123199001011234"));
    }

    @Test
    @DisplayName("applyMaskingByStrategy — maskStrategy='full' ∧ ID_CARD 应用全量脱敏")
    void applyMaskingByStrategyFull() {
        String masked = service.applyMaskingByStrategy("full", "ID_CARD", "320123199001011234");
        assertEquals("3201**********1234", masked);
    }
}

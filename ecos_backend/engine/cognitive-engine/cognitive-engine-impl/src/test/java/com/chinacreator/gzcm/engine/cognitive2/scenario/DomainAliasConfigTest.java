package com.chinacreator.gzcm.engine.cognitive2.scenario;

import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.DomainAliasResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F05-11 / REQ-COG-04 规格 2、3：领域名 → 能力映射（DG-7 匹配不到 ⇒ capability=null，行不丢）。
 *
 * <p><b>别名表单源 = sysman 配置门面</b>（§E.5 / 铁律 §十）："新增别名不改代码" ⇒ 本测试用门面
 * mock 提供别名表，验证解析器只消费配置、不内置兜底；门面未配置时直等枚举名之外一律 null。</p>
 */
class DomainAliasConfigTest {

    @Test
    void unknownDomainYieldsNullCapability() {
        CognitiveConfigQueryService cfg = mock(CognitiveConfigQueryService.class);
        when(cfg.domainAliases()).thenReturn(Map.of());
        DomainAliasResolver r = new DomainAliasResolver(cfg);
        assertNull(r.resolve(null));
        assertNull(r.resolve(""));
        assertNull(r.resolve("   "));
        assertNull(r.resolve("ZW_ORGAZABY"));
        assertNull(r.resolve("5$"), "乱码 domain 归 null，不抛异常");
    }

    @Test
    void aliasMapsKnownDomainToCapability() {
        CognitiveConfigQueryService cfg = mock(CognitiveConfigQueryService.class);
        when(cfg.domainAliases()).thenReturn(Map.of("经营", "FORECAST", "运营", "DETECT"));
        DomainAliasResolver r = new DomainAliasResolver(cfg);
        assertEquals("FORECAST", r.resolve("经营"));
        assertEquals("DETECT", r.resolve("运营"));
        // 直等 MindCapabilityMask 枚举名，无需配置
        assertEquals("PLAN", r.resolve("PLAN"));
        // 大小写不敏感匹配，但归一返回枚举名大写（capabilityMask 契约形态即大写，下游 fromName 严格等值）
        assertEquals("PLAN", r.resolve(" plan "), "trim + 大小写不敏感直等，返回枚举名");
    }

    @Test
    void unconfiguredFacadeYieldsEmptyAliasesAndNeverThrows() {
        // 门面异常（sysman 不可达）不得打断读路径：未知 domain 仍归 null
        CognitiveConfigQueryService cfg = mock(CognitiveConfigQueryService.class);
        when(cfg.domainAliases()).thenThrow(new IllegalStateException("sysman unavailable"));
        DomainAliasResolver r = new DomainAliasResolver(cfg);
        assertNull(r.resolve("经营"));
        assertNull(r.resolve("  "));
    }
}

package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.gateway.GatewayApplication;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;

/**
 * 缓存配置读取面的承流门（§2.9.8⑤；#147AA）。
 *
 * <p><b>为什么需要这门</b>：#147J 宣称「配置唯一读取面为 sysman-impl
 * {@code SysConfigCacheSettingsSource}」，但该类当时落在
 * {@code com.chinacreator.gzcm.services.sysman.cache}——不在 gateway
 * {@code @ComponentScan} 的任何 basePackages 内 ⇒ Bean 从不生成 ⇒
 * {@code RuntimeCacheManager} 的 {@code CacheSettingsSource} 恒缺席 ⇒
 * 缓存恒 DISABLED、配置面永不生效。<b>「类存在」不等于「Bean 在位」</b>，
 * 本门把「读取面必须落在 gateway 扫描面内且不被排除」钉成可判红断言。</p>
 *
 * @author ECOS Gateway #147AA
 */
class CacheSettingsSourceScanGateTest {

    private static final String SETTINGS_SOURCE_FQCN =
            "com.chinacreator.gzcm.sysman.cache.SysConfigCacheSettingsSource";

    /** 旧落位：必须在扫描面之外，否则本门的反证腿失效（说明扫描面被整包放宽）。 */
    private static final String LEGACY_UNSCANNED_FQCN =
            "com.chinacreator.gzcm.services.sysman.cache.SysConfigCacheSettingsSource";

    @Test
    @DisplayName("缓存配置读取面在 gateway 组件扫描面内，且未被 excludeFilters 摘掉")
    void cacheSettingsSourceIsInComponentScanPath() throws Exception {
        ComponentScan scan = GatewayApplication.class.getAnnotation(ComponentScan.class);
        assertTrue(scan != null && scan.basePackages().length > 0, "gateway 必须有显式组件扫描面");

        Class<?> source = Class.forName(SETTINGS_SOURCE_FQCN);
        List<String> bases = Arrays.asList(scan.basePackages());
        assertTrue(CacheSettingsSource.class.isAssignableFrom(source),
                "读取面必须实现 runtime 的 CacheSettingsSource SPI");
        assertTrue(source.isAnnotationPresent(Component.class),
                "读取面必须带 @Component，否则 ObjectProvider 恒缺席");
        assertTrue(isCovered(source.getName(), bases),
                "读取面包必须落在扫描面内，实际扫描面=" + bases + "，读取面=" + source.getPackageName());
        assertTrue(noExcludePatternMatches(source.getName(), scan.excludeFilters()),
                "读取面被 excludeFilters 命中即等于缓存配置恒缺席");
    }

    @Test
    @DisplayName("反证：旧落位（services.sysman.cache）不在扫描面内——防止整包放宽把该缺陷掩盖成绿")
    void legacyPlacementStillFailsThePredicate() {
        ComponentScan scan = GatewayApplication.class.getAnnotation(ComponentScan.class);
        List<String> bases = Arrays.asList(scan.basePackages());
        assertFalse(isCovered(LEGACY_UNSCANNED_FQCN, bases),
                "旧落位必须仍判不合格；若此处变绿，说明扫描面被整包放宽，须复核是否绕过收敛");
        assertFalse(isCovered("com.example.other.pkg.Foo", bases));
    }

    @Test
    @DisplayName("缓存底座本体（RuntimeCacheManager）同样必须在扫描面内")
    void runtimeCacheManagerIsInComponentScanPath() {
        ComponentScan scan = GatewayApplication.class.getAnnotation(ComponentScan.class);
        List<String> bases = Arrays.asList(scan.basePackages());
        String manager = "com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager";
        assertTrue(isCovered(manager, bases));
        assertTrue(noExcludePatternMatches(manager, scan.excludeFilters()),
                "runtime.core 下 agent/git/datapermission/compliance 被排除是有意为之，缓存包不得顺带被排");
    }

    static boolean isCovered(String fqcn, List<String> bases) {
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        return bases.stream().anyMatch(base -> pkg.equals(base) || pkg.startsWith(base + "."));
    }

    static boolean noExcludePatternMatches(String fqcn, ComponentScan.Filter[] filters) {
        for (ComponentScan.Filter filter : filters) {
            if (filter.type() == FilterType.REGEX
                    && Arrays.stream(filter.pattern()).anyMatch(p -> fqcn.matches(p))) {
                return false;
            }
            if (filter.type() == FilterType.ASSIGNABLE_TYPE
                    && Arrays.stream(filter.classes()).anyMatch(c -> c.getName().equals(fqcn))) {
                return false;
            }
        }
        return true;
    }
}

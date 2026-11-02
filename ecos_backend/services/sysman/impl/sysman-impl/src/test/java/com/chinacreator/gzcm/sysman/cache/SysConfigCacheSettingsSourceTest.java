package com.chinacreator.gzcm.sysman.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;

/**
 * 缓存配置唯一读取面（§2.9.8⑤ 裁决 + DC-3；#147J 建、#147AA 补 env 段与取证）。
 *
 * <p>#147J 交付本类时未附测试，且类当时根本不在组件扫描面内（见 gateway
 * {@code CacheSettingsSourceScanGateTest}）。本测试把「配置值到 {@code CacheSettings} 的映射、
 * 缺键回退、读取失败落 DISABLED、env 优先链、刷新后再读」逐条钉成可判红断言。</p>
 *
 * <p>所有用例走 {@link #hermetic} — 环境变量读取面被固定为「未设置」，
 * 否则宿主机的 {@code ECOS_CACHE_ENV} 会让同一份代码在别人的机器上判绿或判红。</p>
 *
 * @author ECOS Sysman #147AA
 */
class SysConfigCacheSettingsSourceTest {

    @AfterEach
    void clearEnvProperty() {
        System.clearProperty(SysConfigCacheSettingsSource.ENV_PROPERTY);
    }

    /** 与宿主环境变量解耦的被测对象。 */
    private static SysConfigCacheSettingsSource hermetic(SysConfigService service) {
        return new SysConfigCacheSettingsSource(service) {
            @Override
            String environmentVariable(String name) {
                return null;
            }
        };
    }

    /** 指定环境变量取值的被测对象（env 优先链取证用）。 */
    private static SysConfigCacheSettingsSource withVariable(SysConfigService service, String variable) {
        return new SysConfigCacheSettingsSource(service) {
            @Override
            String environmentVariable(String name) {
                return SysConfigCacheSettingsSource.ENV_VARIABLE.equals(name) ? variable : null;
            }
        };
    }

    private static SysConfigService config(String enabled, String ttl, String maxStale, String redis) {
        SysConfigService service = mock(SysConfigService.class);
        when(service.getString(SysConfigCacheSettingsSource.KEY_ENABLED)).thenReturn(enabled);
        when(service.getString(SysConfigCacheSettingsSource.KEY_DEFAULT_TTL)).thenReturn(ttl);
        when(service.getString(SysConfigCacheSettingsSource.KEY_MAX_STALE)).thenReturn(maxStale);
        when(service.getString(SysConfigCacheSettingsSource.KEY_REDIS)).thenReturn(redis);
        when(service.getString(SysConfigCacheSettingsSource.KEY_ENV)).thenReturn(null);
        return service;
    }

    @Test
    @DisplayName("四键到 CacheSettings 的映射：true/1 皆视为启用，数字按 long 取")
    void mapsConfiguredKeysIntoSettings() {
        SysConfigCacheSettingsSource source = hermetic(config("true", "300", "60", "false"));

        CacheSettings settings = source.settings();

        assertTrue(settings.enabled());
        assertEquals(300L, settings.defaultTtlSeconds());
        assertEquals(60L, settings.maxStaleSeconds());
        assertFalse(settings.redisEnabled());
        assertEquals(CacheSettings.DEFAULT_ENV, settings.env(), "配置链全空时取缺省环境段");
    }

    @Test
    @DisplayName("字符串 1 也视为启用；大小写与空白不敏感")
    void acceptsNumericTrueAndTrims() {
        SysConfigCacheSettingsSource source = hermetic(config(" 1 ", " 45 ", " 30 ", "TRUE"));

        CacheSettings settings = source.settings();

        assertTrue(settings.enabled());
        assertEquals(45L, settings.defaultTtlSeconds());
        assertEquals(30L, settings.maxStaleSeconds());
        assertTrue(settings.redisEnabled());
    }

    @Test
    @DisplayName("缺键/空值/非数字一律回退保守值（不缓存），负 TTL 归零")
    void absentOrGarbageValuesFallBackToNoCaching() {
        SysConfigCacheSettingsSource absent = hermetic(config(null, null, null, null));
        assertFalse(absent.settings().enabled());
        assertEquals(0L, absent.settings().defaultTtlSeconds());
        assertEquals(0L, absent.settings().maxStaleSeconds());

        SysConfigCacheSettingsSource garbage = hermetic(config("yes", "abc", "-100", ""));
        CacheSettings settings = garbage.settings();
        assertFalse(settings.enabled(), "非 true/1 的取值不得被当启用（防误开缓存）");
        assertEquals(0L, settings.defaultTtlSeconds(), "非数字回退 0＝不缓存");
        assertEquals(0L, settings.maxStaleSeconds(), "负陈旧上限归零，不留负数口径");
    }

    @Test
    @DisplayName("读取抛异常＝落 DISABLED（fail-closed，绝不沿用上一次值或默认打开）")
    void readFailureFallsClosed() {
        SysConfigService failing = mock(SysConfigService.class);
        when(failing.getString(anyString())).thenThrow(new IllegalStateException("db down"));

        CacheSettings settings = hermetic(failing).settings();

        assertEquals(CacheSettings.DISABLED, settings);
        assertFalse(settings.enabled());
    }

    @Test
    @DisplayName("env 优先链：-D ecos.cache.env > 环境变量 ECOS_CACHE_ENV > 表内 ecos.cache.env > 缺省段")
    void envPrecedencePutsDeploymentOverTable() {
        SysConfigService withTable = mock(SysConfigService.class);
        when(withTable.getString(SysConfigCacheSettingsSource.KEY_ENV)).thenReturn("from-table");

        assertEquals("from-table", hermetic(withTable).settings().env());
        assertEquals("from-variable",
                withVariable(withTable, "from-variable").settings().env(),
                "环境段是部署事实，环境变量级须压过表内值");

        SysConfigCacheSettingsSource source = withVariable(withTable, "from-variable");
        System.setProperty(SysConfigCacheSettingsSource.ENV_PROPERTY, "from-deployment");
        assertEquals("from-deployment", source.settings().env(),
                "-D 级最高，且在同一个实例上即时生效（无快照、无需重启）");
    }

    @Test
    @DisplayName("env 含冒号＝整份配置判废，落 DISABLED（键拼接歧义不容许）")
    void ambiguousEnvFailsClosed() {
        System.setProperty(SysConfigCacheSettingsSource.ENV_PROPERTY, "dev:eu");

        assertEquals(CacheSettings.DISABLED,
                hermetic(config("true", "300", "60", "false")).settings());
    }

    @Test
    @DisplayName("每次读取都穿透到配置缓存：改配置（关缓存）无需重启即生效")
    void settingsAreReadThroughSoChangesApplyWithoutRestart() {
        SysConfigService service = config("false", "300", "60", "false");
        SysConfigCacheSettingsSource source = hermetic(service);
        assertFalse(source.settings().enabled());

        when(service.getString(SysConfigCacheSettingsSource.KEY_ENABLED)).thenReturn("true");
        assertTrue(source.settings().enabled(),
                "同一 Bean 实例须立即反映配置变更——「关掉缓存」是安全动作，不能等重启");
        verify(service, times(2)).getString(SysConfigCacheSettingsSource.KEY_ENABLED);
    }
}

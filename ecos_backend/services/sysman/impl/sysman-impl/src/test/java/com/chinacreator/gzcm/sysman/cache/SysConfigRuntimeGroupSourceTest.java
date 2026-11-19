package com.chinacreator.gzcm.sysman.cache;

import com.chinacreator.gzcm.runtime.core.event.EventSettings;
import com.chinacreator.gzcm.runtime.core.storage.StorageSettings;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G-2 event / storage 两组配置源的取值与回落门禁。
 *
 * <p>锁三件事：① 库有值 ⇒ 采用；② 库缺/空白 ⇒ 回落 DEFAULTS（= 改造前现行缺省）；
 * ③ 读库异常 ⇒ 回落 DEFAULTS（配置面故障不得让事件总线/存储整体不可用）。</p>
 */
class SysConfigRuntimeGroupSourceTest {

    @Test
    @DisplayName("event：库内两键有值 ⇒ 采用")
    void eventReadsDbValues() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(SysConfigEventSettingsSource.KEY_AUTO_OFFSET_RESET)).thenReturn("earliest");
        when(svc.getString(SysConfigEventSettingsSource.KEY_GROUP_PREFIX)).thenReturn("ecos-prod");

        EventSettings s = new SysConfigEventSettingsSource(svc).settings();

        assertEquals("earliest", s.autoOffsetReset());
        assertEquals("ecos-prod", s.groupPrefix());
    }

    @Test
    @DisplayName("event：库缺失 ⇒ 回落 DEFAULTS（latest / 空前缀 = 改造前行为）")
    void eventFallsBackToDefaults() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        EventSettings s = new SysConfigEventSettingsSource(svc).settings();

        assertEquals(EventSettings.DEFAULTS.autoOffsetReset(), s.autoOffsetReset());
        assertEquals("", s.groupPrefix());
    }

    @Test
    @DisplayName("event：读库异常 ⇒ 回落 DEFAULTS（不抛给调用方）")
    void eventSurvivesDbFailure() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("db down"));

        assertEquals(EventSettings.DEFAULTS, new SysConfigEventSettingsSource(svc).settings());
    }

    @Test
    @DisplayName("storage：库内两键有值 ⇒ 采用")
    void storageReadsDbValues() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(SysConfigStorageSettingsSource.KEY_MINIO_BUCKET)).thenReturn("bucket-x");
        when(svc.getString(SysConfigStorageSettingsSource.KEY_MINERU_TIMEOUT)).thenReturn("60");

        StorageSettings s = new SysConfigStorageSettingsSource(svc).settings();

        assertEquals("bucket-x", s.minioBucket());
        assertEquals(60, s.mineruTimeoutSeconds());
    }

    @Test
    @DisplayName("storage：库缺失 ⇒ 回落 DEFAULTS（ecos-data / 300s = 代码硬编码 5 分钟）")
    void storageFallsBackToDefaults() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        StorageSettings s = new SysConfigStorageSettingsSource(svc).settings();

        assertEquals("ecos-data", s.minioBucket());
        assertEquals(300, s.mineruTimeoutSeconds());
    }

    @Test
    @DisplayName("storage：非法数字 ⇒ 回落 DEFAULTS（禁把脏值当 0）")
    void storageIgnoresMalformedNumber() {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(SysConfigStorageSettingsSource.KEY_MINIO_BUCKET)).thenReturn(null);
        when(svc.getString(SysConfigStorageSettingsSource.KEY_MINERU_TIMEOUT)).thenReturn("abc");

        assertEquals(300, new SysConfigStorageSettingsSource(svc).settings().mineruTimeoutSeconds());
    }
}

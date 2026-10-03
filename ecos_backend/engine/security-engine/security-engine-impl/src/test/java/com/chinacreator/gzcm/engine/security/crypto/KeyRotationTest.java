package com.chinacreator.gzcm.engine.security.crypto;

import com.chinacreator.gzcm.engine.security.crypto.kms.rotation.KeyRotationService.RotationPolicy;
import com.chinacreator.gzcm.engine.security.crypto.kms.rotation.KeyRotationService.RotationRecord;
import com.chinacreator.gzcm.engine.security.crypto.kms.rotation.KeyRotationServiceImpl;
import com.chinacreator.gzcm.sysman.kms.KMSAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * KMS 密钥轮换服务单测（{@link KeyRotationServiceImpl}，Mockito mock {@link KMSAdapter}）。
 *
 * <p>shouldRotate 分支：MANUAL → 永不轮换；TIME_BASED → 总需轮换（简化实现）；
 * USAGE_BASED → usageCount >= maxUsageCount。本测试用 MANUAL（省事，不轮换）与
 * TIME_BASED（触发轮换）两条明确分支验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class KeyRotationTest {

    private static final String KEY_ID = "k1";

    @Mock
    KMSAdapter kmsAdapter;

    KeyRotationServiceImpl rotationSvc;

    @BeforeEach
    void setup() {
        rotationSvc = new KeyRotationServiceImpl(kmsAdapter);
    }

    private KMSAdapter.KeyInfo keyInfo(String version) {
        return new KMSAdapter.KeyInfo(KEY_ID, "AES_256", version);
    }

    @Test
    @DisplayName("manualRotate 调用 rotateKey 并返回新版本")
    void manualRotate_callsRotateKeyAndReturnsNewVersion() throws Exception {
        Mockito.when(kmsAdapter.getKey(KEY_ID, null)).thenReturn(keyInfo("v1"));
        Mockito.when(kmsAdapter.rotateKey(KEY_ID)).thenReturn("v2");

        String result = rotationSvc.manualRotate(KEY_ID);

        assertEquals("v2", result, "manualRotate 应返回 KMS rotateKey 的新版本");
        verify(kmsAdapter, times(1)).getKey(KEY_ID, null);
        verify(kmsAdapter, times(1)).rotateKey(KEY_ID);
        // 轮换历史落地
        List<RotationRecord> history = rotationSvc.getRotationHistory(KEY_ID);
        assertEquals(1, history.size(), "手动轮换后历史应有 1 条");
    }

    @Test
    @DisplayName("autoRotate：MANUAL 策略（无需轮换）→ 返回当前版本，不调用 rotateKey")
    void autoRotate_whenNoRotationNeeded_returnsCurrentVersion() throws Exception {
        Mockito.when(kmsAdapter.getKey(KEY_ID, null)).thenReturn(keyInfo("v1"));
        RotationPolicy policy = new RotationPolicy("MANUAL");

        String result = rotationSvc.autoRotate(KEY_ID, policy);

        assertEquals("v1", result, "MANUAL 策略 shouldRotate=false → 返回当前版本");
        verify(kmsAdapter, never()).rotateKey(any());
        assertTrue(rotationSvc.getRotationHistory(KEY_ID).isEmpty(), "未轮换 → 无历史记录");
    }

    @Test
    @DisplayName("autoRotate：TIME_BASED（需要轮换）→ 调用 rotateKey")
    void autoRotate_whenNeeded_callsRotateKey() throws Exception {
        Mockito.when(kmsAdapter.getKey(KEY_ID, null)).thenReturn(keyInfo("v1"));
        Mockito.when(kmsAdapter.rotateKey(KEY_ID)).thenReturn("v2");
        RotationPolicy policy = new RotationPolicy("TIME_BASED");

        String result = rotationSvc.autoRotate(KEY_ID, policy);

        assertEquals("v2", result, "需要轮换时应返回新版本");
        verify(kmsAdapter, times(1)).rotateKey(KEY_ID);
    }

    @Test
    @DisplayName("autoRotate 轮换后历史被记录（oldVersion 来自 getKey）")
    void rotationHistory_recordedAfterAutoRotate() throws Exception {
        Mockito.when(kmsAdapter.getKey(KEY_ID, null)).thenReturn(keyInfo("v1"));
        Mockito.when(kmsAdapter.rotateKey(KEY_ID)).thenReturn("v3");
        RotationPolicy policy = new RotationPolicy("TIME_BASED");

        rotationSvc.autoRotate(KEY_ID, policy);
        List<RotationRecord> history = rotationSvc.getRotationHistory(KEY_ID);

        assertFalse(history.isEmpty(), "轮换后历史不可为空");
        assertTrue(history.size() >= 1);
        RotationRecord first = history.get(0);
        assertEquals("v1", first.getOldVersion(), "旧版本应来自 getKey mock");
        assertEquals("v3", first.getNewVersion(), "新版本应来自 rotateKey mock");
        assertEquals("AUTO", first.getRotationType(), "AUTO 分支 rotationType=AUTO");
        verify(kmsAdapter, times(1)).getKey(eq(KEY_ID), any());
    }
}

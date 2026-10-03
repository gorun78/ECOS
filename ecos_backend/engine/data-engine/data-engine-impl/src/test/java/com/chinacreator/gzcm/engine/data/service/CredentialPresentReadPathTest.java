package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;

/**
 * F02-01 §2（详细设计-02，W52 / P0 安全）—— 数据源读路径<b>永不回显密码</b>，
 * 对外只暴露派生布尔 {@code credentialPresent: true|false}。
 *
 * <p>历史形态：读端点直接返回 {@code DataSourceEntity}，密文字段
 * {@code passwordEncrypted} 会一并序列化给调用方——违反"读路径永不回显密码"。
 * F02-01 订正：新增派生 getter {@link DataSourceEntity#isCredentialPresent()}，
 * 由 {@code passwordEncrypted} 内容派生（null/空 = 未配置；非空密文 = 已配置）。
 * 前端 B.1 凭此渲染"已配置 / 未配置密码"两态，不再需要密文。</p>
 *
 * <p>本测试纯量级判定，不触库。</p>
 */
@DisplayName("F02-01 §2 数据源读路径 credentialPresent 派生（P0）")
class CredentialPresentReadPathTest {

    @Test
    @DisplayName("credentialPresent — 密文非空 → true（已配置）")
    void nonBlankCipher_present() {
        DataSourceEntity e = new DataSourceEntity();
        e.setPasswordEncrypted("base64-ciphertext-here");
        assertTrue(e.isCredentialPresent(), "密文非空即 credentialPresent=true");
    }

    @Test
    @DisplayName("credentialPresent — 密文 null → false（未配置）")
    void nullCipher_absent() {
        DataSourceEntity e = new DataSourceEntity();
        assertFalse(e.isCredentialPresent(), "未设密码 credentialPresent=false");
    }

    @Test
    @DisplayName("credentialPresent — 密文空白串 → false（未配置语义，防空串误判已配置）")
    void blankCipher_absent() {
        DataSourceEntity e = new DataSourceEntity();
        e.setPasswordEncrypted("   ");
        assertFalse(e.isCredentialPresent(), "空白串视同未配置");
    }
}

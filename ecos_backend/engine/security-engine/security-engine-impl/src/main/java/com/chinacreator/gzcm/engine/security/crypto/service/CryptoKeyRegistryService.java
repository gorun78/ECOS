package com.chinacreator.gzcm.engine.security.crypto.service;

import com.chinacreator.gzcm.engine.security.crypto.KeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.KeyManagementService.KeyManagementException;
import com.chinacreator.gzcm.engine.security.crypto.model.KeyMetadata;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 密钥登记面的编排层（#142/B4-1）：把 {@link KeyManagementService} 的
 * {@code queryKeys}/{@code rotateKey} 投影成设计 §4.3.1 L2585-2586 的契约形态，
 * 并补上契约要求、底座却没有的两条纪律。
 *
 * <p><b>为什么不直接在 Controller 里调 service</b>：轮换的「旧版本退役」是业务规则
 * （§4.7.2 矩阵纪律：<b>密钥轮换旧版本保留解密</b>），不是表示层逻辑；
 * 且详设-00 §1.6.4 的既定分层是 Controller 只做参数与响应。</p>
 *
 * <p><b>补齐的两条纪律（底座原样不满足契约）</b>：
 * <ol>
 *   <li><b>缺主密钥禁静默建钥</b>：{@code KeyManagementServiceFullImpl.rotateKey()}
 *       内部走 {@code createKey → KeyGenerator.generateKey()}，<b>全程不看</b>
 *       {@code ECOS_MASTER_KEY}——即「主密钥缺失也能造出新钥」，与 §4.3.3
 *       {@code ECOS-SEC-430}「加密主密钥缺失，禁静默建钥」正相反。
 *       故本类在轮换前做主密钥在场判定（判定面与 {@link KeyManagementServiceImpl#ENV_MASTER_KEY}
 *       同一单源常量），缺失即抛 {@link MasterKeyMissingException} 让端点映射 500
 *       {@code ECOS-SEC-430}，<b>不调</b> {@code rotateKey}。</li>
 *   <li><b>旧版本退役</b>：底座 {@code rotateKey} 生成 {@code v+1} 后把<b>两个版本都留成
 *       ACTIVE</b>（它只更新轮换时间戳），于是 {@code GET /crypto/keys} 会出现多个「当前有效」
 *       密钥、契约的 {@code retiredVersion} 也无从取值。本类在轮换成功后，把同 {@code keyId}
 *       下版本号小于新钥且仍为 ACTIVE 的<b>全部</b>旧版本标 {@code RETIRED}
 *       （含此前遗留的 ACTIVE 老钥，一次收敛），并如实回传本次被退役的版本号。</li>
 * </ol>
 * 「保留解密」的落点：退役<b>只改元数据状态</b>，{@code keyStore} 里的旧版本 Key 对象
 * 不删除不用后重写——{@code getKey(keyId, version)} 对旧版本仍可取，因此既有密文可继续解密，
 * 符合矩阵纪律；同时不改底座行为，不影响 {@code DataEncryptionServiceImpl} 等既有读写面。</p>
 *
 * <p><b>数据面现状（登记，不谎称）</b>：容器内装配的 {@code KeyManagementService} 实现是
 * {@code KeyManagementServiceFullImpl}（纯内存 {@code ConcurrentHashMap}，无 DAO），
 * {@code ecos_security_crypto_key}（V165）至今无 Mapper/DAO 读写方。因此本端点在
 * 「未建钥的干净 JVM」上返回<b>空清单</b>是真实状态，不是缺陷；
 * 但「密钥台账落库」这一项属 #142 之后的独立收口（登记在差距文档 B4-1 订正注），
 * 本批<b>不</b>用假数据把清单填满以冒充收口。</p>
 */
@Service
public class CryptoKeyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyRegistryService.class);

    /** §4.3.3：加密主密钥缺失 → 500 {@code ECOS-SEC-430}（禁静默建钥）。 */
    public static final String ERROR_MASTER_KEY_MISSING = "ECOS-SEC-430";

    /** 已登记但未在用的状态值（退役/软删/停用三态），用于 {@code retiredAt} 的取值判定。 */
    private static final Set<String> NON_ACTIVE_STATES = Set.of("RETIRED", "DELETED", "INACTIVE");

    /** 新钥状态（与底座 {@code createKey} 写死的 {@code ACTIVE} 同值，不另造词表）。 */
    private static final String STATE_ACTIVE = "ACTIVE";

    /** 本类写入的退役状态（契约 {@code retiredVersion} 的语义载体）。 */
    private static final String STATE_RETIRED = "RETIRED";

    private final KeyManagementService keyManagementService;

    public CryptoKeyRegistryService(KeyManagementService keyManagementService) {
        this.keyManagementService = keyManagementService;
    }

    /** 主密钥缺失（{@code ECOS-SEC-430}）——与「查无此钥」「底座异常」分开，禁混成一个 500。 */
    public static class MasterKeyMissingException extends Exception {
        public MasterKeyMissingException(String message) {
            super(message);
        }
    }

    /** {@code keyId} 未登记（无任何版本）。 */
    public static class KeyNotRegisteredException extends Exception {
        public KeyNotRegisteredException(String message) {
            super(message);
        }
    }

    /**
     * §4.3.1 L2585 {@code listCryptoKeys}：登记元数据清单，<b>永不返回密钥材料</b>。
     *
     * <p>投影只读 {@link KeyMetadata} 的七个契约字段，逐字段显式取值——
     * 不做「把整个 metadata 对象丢进响应」的省事写法，因为 {@code KeyMetadata}
     * 后续只要加一个含材料语义的字段就会立刻泄漏。字段映射（原文 → 现网模型）：
     * {@code keyVersion ← version}、{@code masterKeyRef ← masterKeyId}、
     * {@code rotateAt ← nextRotationTime}（底座仅有下次轮换时间，无独立 rotateAt 列）、
     * {@code retiredAt ← updatedTime} 且<b>仅当</b>状态属 {@code RETIRED/DELETED/INACTIVE}
     * 时才给值（在用钥匙不存在退役时刻，禁把更新时间冒充退役时间）。</p>
     */
    public List<Map<String, Object>> listKeyRegistrations(String status) throws KeyManagementException {
        List<KeyMetadata> all = keyManagementService.queryKeys(null, blankToNull(status));
        List<Map<String, Object>> items = new ArrayList<>();
        for (KeyMetadata m : all) {
            items.add(project(m));
        }
        return items;
    }

    /** 单条投影（public 便于契约门禁直接钉死键集合）。 */
    public static Map<String, Object> project(KeyMetadata m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("keyId", m.getKeyId());
        item.put("algorithm", m.getAlgorithm());
        item.put("keyVersion", m.getVersion());
        item.put("status", m.getStatus());
        item.put("masterKeyRef", m.getMasterKeyId());
        item.put("rotateAt", m.getNextRotationTime());
        item.put("retiredAt", isNonActive(m.getStatus()) ? m.getUpdatedTime() : null);
        return item;
    }

    /**
     * §4.3.1 L2586 {@code rotateCryptoKey}：轮换并退役旧版本。
     *
     * @return {@code {keyId, newKeyVersion, retiredVersion}}（契约三键，无多余字段）
     * @throws MasterKeyMissingException   主密钥不在场 ⇒ 端点 500 {@code ECOS-SEC-430}，且不建钥
     * @throws KeyNotRegisteredException   该 {@code keyId} 无任何已登记版本 ⇒ 端点 404
     * @throws KeyManagementException      底座异常 ⇒ 端点 500
     */
    public Map<String, Object> rotate(String keyId, String operator, String reason)
            throws MasterKeyMissingException, KeyNotRegisteredException, KeyManagementException {
        if (!masterKeyPresent()) {
            throw new MasterKeyMissingException(
                    "主密钥缺失（" + KeyManagementServiceImpl.ENV_MASTER_KEY + " 未设置），拒绝静默建钥");
        }
        Integer activeBefore = currentActiveVersion(keyId);
        if (activeBefore == null) {
            throw new KeyNotRegisteredException("密钥未登记: " + keyId);
        }

        KeyMetadata created = keyManagementService.rotateKey(keyId, operator);
        int newVersion = created.getVersion() == null ? activeBefore + 1 : created.getVersion();
        int retired = retireOlderActiveVersions(keyId, newVersion, operator);

        log.info("密钥轮换完成: keyId={} newVersion={} retiredVersion={} operator={} reasonLen={}",
                keyId, newVersion, retired, operator, reason == null ? 0 : reason.length());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyId", keyId);
        out.put("newKeyVersion", newVersion);
        out.put("retiredVersion", retired < 0 ? null : retired);
        return out;
    }

    /** 主密钥在场判定——{@code protected} 接缝，单测用子类覆写（禁为测试改生产装配）。 */
    protected boolean masterKeyPresent() {
        String v = System.getenv(KeyManagementServiceImpl.ENV_MASTER_KEY);
        return v != null && !v.isBlank();
    }

    /** 该 keyId 当前 ACTIVE 的最大版本号；无 ACTIVE 版本 → null。 */
    private Integer currentActiveVersion(String keyId) throws KeyManagementException {
        Integer best = null;
        for (KeyMetadata m : keyManagementService.queryKeys(null, null)) {
            if (!keyId.equals(m.getKeyId()) || !STATE_ACTIVE.equals(m.getStatus())) continue;
            int v = m.getVersion() == null ? 0 : m.getVersion();
            if (best == null || v > best) best = v;
        }
        return best;
    }

    /**
     * 把同 keyId 下版本低于新钥且仍 ACTIVE 的全部版本标 RETIRED（只改元数据，不删 Key 对象）。
     *
     * @return 本次被退役的最高版本号；没有任何版本被退役时返回 -1（调用方投影为 {@code null}）
     */
    private int retireOlderActiveVersions(String keyId, int newVersion, String operator)
            throws KeyManagementException {
        int retired = -1;
        Date now = new Date();
        for (KeyMetadata m : keyManagementService.queryKeys(null, null)) {
            if (!keyId.equals(m.getKeyId())) continue;
            int v = m.getVersion() == null ? 0 : m.getVersion();
            if (v >= newVersion || !STATE_ACTIVE.equals(m.getStatus())) continue;
            m.setStatus(STATE_RETIRED);
            m.setUpdatedTime(now);
            m.setUpdatedBy(operator);
            if (v > retired) retired = v;
        }
        return retired;
    }

    private static boolean isNonActive(String status) {
        return status != null && NON_ACTIVE_STATES.contains(status);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

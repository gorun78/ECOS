package com.chinacreator.gzcm.runtime.core.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * runtime 持有的安全加解密出口（H3-T-ARCH 裁决 C，2026-09-29）。
 *
 * <p>背景：{@code data-engine-impl} 此前编译期直依 {@code security-engine-impl}，
 * 既 {@code new KeyManagementServiceImpl() + new DataEncryptionServiceImpl(kms)} 自持实例
 * （DataSourceServiceImpl:108-109），又按接口注入 Bean（PipelineGitService:56），
 * 触发依赖方向铁律的 ArchUnit 违例 12 次。加解密能力属安全底座，业务引擎不得直取。</p>
 *
 * <p>实现取向：沿用 llm-gateway {@code SecurityEngineBridge} 已确立的
 * 「<b>类名反射 + 可选 Bean</b>」通道 —— 本类<b>不</b>在编译期依赖 security-engine 任何类型，
 * 运行期优先取同 JVM 的 {@code IDataEncryptionService} Bean（gateway fat-JAR 聚合部署态），
 * 无 Bean 时退化为反射自持实例（与迁移前的 {@code new} 模式等价，保证 standalone
 * {@code data-engine-boot} 部署态行为不变）。密钥语义、密钥体系与迁移前完全一致，
 * 本类只搬移<b>访问通道</b>；密钥持久化是 H3-T-KMS-PERSIST 的独立议题。</p>
 *
 * <p>fail-closed：security-engine 类不在 classpath 时 {@link #available()} 返回 false，
 * {@code encrypt}/{@code decrypt} 抛异常而不返回原文：<b>出口本身不可用</b>抛
 * {@link DataBridgeException}（503＋{@code ECOS-SYS-503}，§3.2.7 下游制品不可用，
 * #142/B4-3e-40 收口 D-B4-71 属主侧首域），<b>调用 security-engine 失败／反射失败</b>仍抛
 * {@link IllegalStateException}（＝本服务自身故障，非下游未就绪）。
 * 两者都由调用方按其既有降级策略处置（数据源写路径降级明文 + WARN，读路径返回 null）。</p>
 */
@Component
public class SecurityCryptoEgress {

    private static final Logger log = LoggerFactory.getLogger(SecurityCryptoEgress.class);

    /** security-engine 加解密服务接口（仅类名字面量，编译期零依赖） */
    private static final String ENCRYPTION_SERVICE_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService";
    /** security-engine 加解密服务实现（无 Bean 时的自持实例来源） */
    private static final String ENCRYPTION_SERVICE_IMPL_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl";
    /** security-engine 密钥管理接口（自持实例构造参数类型） */
    private static final String KMS_SERVICE_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService";
    /** security-engine 密钥管理实现 */
    private static final String KMS_SERVICE_IMPL_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl";

    private final ApplicationContext applicationContext;

    /** 解析后的加解密服务对象（{@code IDataEncryptionService} 实例），null = 不可用 */
    private volatile Object encryptionService;
    /** 解析后的密钥管理服务对象（{@code IKeyManagementService} 实例） */
    private volatile Object keyManagementService;
    /** 解析只做一次（成功或不可用均置位），避免每次调用重扫 classpath */
    private volatile boolean resolved;

    @Autowired(required = false)
    public SecurityCryptoEgress(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 出口是否可用（security-engine crypto 在 classpath 且可实例化/可取 Bean）。 */
    public boolean available() {
        resolve();
        return encryptionService != null;
    }

    /**
     * 加密。
     *
     * @param data  明文
     * @param keyId 密钥标识
     * @return 密文
     * @throws DataBridgeException 出口不可用（下游制品缺失＝503 + {@code ECOS-SYS-503}，不返回原文）
     * @throws IllegalStateException security-engine 抛错或反射调用失败（＝本服务自身故障）
     */
    public String encrypt(String data, String keyId) {
        return invoke("encrypt", data, keyId);
    }

    /**
     * 解密。
     *
     * @param encryptedData 密文
     * @param keyId         密钥标识
     * @return 明文
     * @throws DataBridgeException 出口不可用（下游制品缺失＝503 + {@code ECOS-SYS-503}，不返回原文）
     * @throws IllegalStateException 解密失败或反射调用失败（密钥丢失/轮换由调用方降级）
     */
    public String decrypt(String encryptedData, String keyId) {
        return invoke("decrypt", encryptedData, keyId);
    }

    /**
     * 幂等确保密钥存在（等价迁移前 {@code getKeyBytes} 空则 {@code createKey} 的逻辑）。
     *
     * @param keyId     密钥标识
     * @param algorithm 算法（本项目用 {@code AES}）
     * @param keySize   密钥长度（bit）
     * @throws DataBridgeException 出口不可用（下游制品缺失＝503 + {@code ECOS-SYS-503}）
     * @throws IllegalStateException ensureKey 调用失败或反射调用失败（＝本服务自身故障）
     */
    public void ensureKey(String keyId, String algorithm, int keySize) {
        resolve();
        Object kms = keyManagementService;
        if (kms == null) {
            // #142/B4-3e-40（D-B4-71 属主侧首域）：出口缺失＝下游制品不可用，按 §3.2.7 落显式
            // 503＋机读码 ECOS-SYS-503，禁再由 gateway 入口兜底折成 500（「不可用」不等于「服务内部错误」）。
            throw new DataBridgeException(503, 503,
                    SystemErrorCode.ECOS_SYS_503 + ": security-engine KMS 不可用, keyId=" + keyId);
        }
        try {
            Object existing = kms.getClass().getMethod("getKeyBytes", String.class).invoke(kms, keyId);
            if (existing instanceof byte[] bytes && bytes.length > 0) {
                return;
            }
            kms.getClass().getMethod("createKey", String.class, String.class, int.class)
                    .invoke(kms, keyId, algorithm, keySize);
            log.info("[runtime-crypto] KMS key created keyId={} algorithm={} size={}", keyId, algorithm, keySize);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("security-engine ensureKey 失败: "
                    + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("security-engine ensureKey 反射调用失败", e);
        }
    }

    private String invoke(String method, String value, String keyId) {
        resolve();
        Object target = encryptionService;
        if (target == null) {
            // #142/B4-3e-40（D-B4-71 属主侧首域）：同 JVM 无 Bean 且反射自持实例也不可用＝
            // 下游制品不可用 ⇒ 显式 503＋ECOS-SYS-503，禁落入口兜底折成 500（fail-closed 不变：绝不返回原文）。
            throw new DataBridgeException(503, 503,
                    SystemErrorCode.ECOS_SYS_503 + ": security-engine crypto 出口不可用, method=" + method);
        }
        try {
            Method m = target.getClass().getMethod(method, String.class, String.class);
            Object result = m.invoke(target, value, keyId);
            return result == null ? null : result.toString();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("security-engine " + method + " 失败: "
                    + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("security-engine " + method + " 反射调用失败", e);
        }
    }

    /** 懒解析一次：优先 Bean，其次反射自持实例。 */
    private synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        Class<?> encryptionItf;
        Class<?> kmsItf;
        try {
            encryptionItf = Class.forName(ENCRYPTION_SERVICE_FQN);
            kmsItf = Class.forName(KMS_SERVICE_FQN);
        } catch (ClassNotFoundException e) {
            log.warn("[runtime-crypto] security-engine crypto 不在 classpath, 加解密出口不可用: {}", e.getMessage());
            return;
        }
        Object bean = beanByType(encryptionItf);
        if (bean != null) {
            encryptionService = bean;
            keyManagementService = beanByType(kmsItf);
            log.info("[runtime-crypto] 加解密出口=同 JVM security-engine Bean (kmsBean={})",
                    keyManagementService != null);
            return;
        }
        try {
            Object kms = Class.forName(KMS_SERVICE_IMPL_FQN).getDeclaredConstructor().newInstance();
            Object enc = Class.forName(ENCRYPTION_SERVICE_IMPL_FQN)
                    .getDeclaredConstructor(kmsItf).newInstance(kms);
            keyManagementService = kms;
            encryptionService = enc;
            log.info("[runtime-crypto] 加解密出口=反射自持实例（无 Bean，standalone 部署态与迁移前 new 模式等价）");
        } catch (ReflectiveOperationException e) {
            log.warn("[runtime-crypto] 反射构造加解密实例失败, 出口不可用: {}", e.toString());
        }
    }

    private Object beanByType(Class<?> type) {
        if (applicationContext == null) {
            return null;
        }
        String[] names = applicationContext.getBeanNamesForType(type);
        if (names.length == 0) {
            return null;
        }
        if (names.length > 1) {
            log.warn("[runtime-crypto] {} 类型存在 {} 个 Bean, 取首个 name={}",
                    type.getSimpleName(), names.length, names[0]);
        }
        return applicationContext.getBean(names[0]);
    }
}

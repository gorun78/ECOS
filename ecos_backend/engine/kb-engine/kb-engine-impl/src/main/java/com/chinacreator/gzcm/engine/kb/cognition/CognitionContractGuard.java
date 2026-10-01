package com.chinacreator.gzcm.engine.kb.cognition;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.springframework.stereotype.Component;

/**
 * 场景认知契约守护 — 详细设计-04 F04-08 / REQ-KB-04（P0 守护型）。
 *
 * <p>三条纪律：</p>
 * <ol>
 *   <li><b>只读开放</b>：kb 对场景侧只读 hypothesis/belief/mind 三态，不提供写代理；
 *       任何试图经 kb 写入认知表的调用必须命中 {@link #assertReadOnly()} 陷阱 →
 *       {@code 405 ECOS-KB-070}（{@code COGNITION_WRITE_FORBIDDEN}）。</li>
 *   <li><b>契约名 ↔ 库内真身名映射</b>：文档/契约写 {@code kb_cognitive_hypothesis} 等别名，
 *       库内真身是 {@code public.ecos_cognitive_hypothesis}（K-49 守护对象表名不存在，G2-6）；
 *       映射交 R-12 / PRD 回写裁定，本类只做确定性 resolve 供读路径与守门测试复用。</li>
 *   <li><b>枚举冻结</b>：status 枚举见 {@link HypothesisStatus}（PROPOSED/EVIDENCED/BELIEVED/REFUTED），
 *       白名单判定唯一入口 {@link HypothesisStatus#isValidStatus(String)}。</li>
 * </ol>
 */
@Component
public class CognitionContractGuard {

    /** 认知三态库内真身表名（G2-6 守卫对象；schema = public，控制域外的认知引擎独立持久）。 */
    public static final String HYPOTHESIS_REAL_TABLE = "public.ecos_cognitive_hypothesis";
    public static final String BELIEF_REAL_TABLE = "public.ecos_cognitive_belief";
    public static final String MIND_REAL_TABLE = "public.ecos_cognitive_mind";

    /**
     * 契约名 → 库内真身表名。
     *
     * @param contractName 文档/前端/契约层使用的别名（{@code kb_cognitive_*}）
     * @return 库内真身表名；未知契约名 → {@code null}（调用方应视为不可解析，不猜表名）。
     */
    public String resolveReal(String contractName) {
        if (contractName == null) {
            return null;
        }
        String c = contractName.trim();
        switch (c) {
            case "kb_cognitive_hypothesis":
            case "ecos_cognitive_hypothesis":
                return HYPOTHESIS_REAL_TABLE;
            case "kb_cognitive_belief":
            case "ecos_cognitive_belief":
                return BELIEF_REAL_TABLE;
            case "kb_cognitive_mind":
            case "ecos_cognitive_mind":
                return MIND_REAL_TABLE;
            default:
                return null;
        }
    }

    /** 契约名是否为受守护的认知三态之一。 */
    public boolean isGuardedContract(String contractName) {
        return resolveReal(contractName) != null;
    }

    /**
     * 只读契约陷阱：任何写代理路径调用即 fail-loud。
     * <p>设计意图：kb 对认知三态<b>只读</b>，禁止"替 cognitive 写"。为防未来有人在 kb 里加写代理，
     * 该方法是唯一被约定"写路径必须先调"的守门点，命中即 {@code 405 ECOS-KB-070}。
     *
     * @throws KbErrorCodes.DomainException 恒抛出（写请求恒被拒）。
     */
    public void assertReadOnly() {
        throw KbErrorCodes.COGNITION_WRITE_FORBIDDEN
                .exception("认知契约（hypothesis/belief/mind）对 kb 只读，禁止经 kb 写代理，405 ECOS-KB-070");
    }
}

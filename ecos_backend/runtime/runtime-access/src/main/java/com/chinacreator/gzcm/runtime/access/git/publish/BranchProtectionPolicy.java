package com.chinacreator.gzcm.runtime.access.git.publish;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分支保护契约（§2.9.9④ 契约面；#147F）。
 *
 * <p><b>规则（纯判定，不执行网络操作）</b>：</p>
 * <ol>
 *   <li>受保护分支（缺省 {@code main}，可配多枚）禁止<b>强推</b>（force push）；</li>
 *   <li>受保护分支禁止<b>直推</b>——推送必须携带 {@link PublishManifest} 且清单完整；</li>
 *   <li>非保护分支可直推，但若环境要求签名仍须清单带签名；</li>
 *   <li>{@code commit/tag} 签名：本仓尚未接入签名基础设施，要求签名的调用返回
 *       {@link Violation#SIGNING_NOT_AVAILABLE}（禁放行、禁伪签名）。</li>
 * </ol>
 *
 * <p><b>承流边界（诚实登记）</b>：当前仓内<b>没有</b>面向用户的 push 端点，
 * 本策略是契约与判定面（供后续 push 通道与发布管线调用），本批不宣称"远程推送已被保护"。</p>
 *
 * @author ECOS Runtime底座 #147F
 */
public final class BranchProtectionPolicy {

    /** 违例种类（机器可读）。 */
    public enum Violation {
        /** 强推受保护分支。 */
        FORCE_PUSH_FORBIDDEN,
        /** 直推受保护分支（无/不完整发布清单）。 */
        DIRECT_PUSH_FORBIDDEN,
        /** 目标环境要求签名，但清单无签名。 */
        SIGNATURE_REQUIRED,
        /** 签名能力未接入（无法满足签名要求）。 */
        SIGNING_NOT_AVAILABLE
    }

    private final Set<String> protectedBranches;
    private final boolean manifestRequired;
    private final boolean signingRequired;

    public BranchProtectionPolicy(Set<String> protectedBranches,
                                  boolean manifestRequired, boolean signingRequired) {
        this.protectedBranches = protectedBranches == null
                ? Set.of("main") : new LinkedHashSet<>(protectedBranches);
        this.manifestRequired = manifestRequired;
        this.signingRequired = signingRequired;
    }

    /**
     * 严格缺省：main 受保护、要求清单、暂不强制签名（无签名基础设施；强制会让所有发布 UNKNOWN）。
     */
    public static BranchProtectionPolicy strictDefault() {
        return new BranchProtectionPolicy(Set.of("main"), true, false);
    }

    public Set<String> protectedBranches() {
        return Set.copyOf(protectedBranches);
    }

    public boolean manifestRequired() {
        return manifestRequired;
    }

    public boolean signingRequired() {
        return signingRequired;
    }

    /** 推送前检查；返回空列表 = 允许。 */
    public List<Violation> checkPush(String branch, boolean force, PublishManifest manifest) {
        List<Violation> violations = new ArrayList<>();
        boolean protectedBranch = branch != null && protectedBranches.contains(branch);
        if (force) {
            violations.add(Violation.FORCE_PUSH_FORBIDDEN);
        }
        if (protectedBranch && manifestRequired) {
            if (manifest == null || !manifest.isComplete()) {
                violations.add(Violation.DIRECT_PUSH_FORBIDDEN);
            }
        }
        boolean needSignature = signingRequired || (manifest != null && manifest.isComplete() && signingRequired);
        if (needSignature) {
            if (manifest == null || !manifest.hasSignature()) {
                violations.add(Violation.SIGNATURE_REQUIRED);
                violations.add(Violation.SIGNING_NOT_AVAILABLE);
            }
        }
        return violations;
    }

    /**
     * 发布动作的前置断言：清单必须完整；要求签名而清单无签名时显式拒绝（签名基础设施未接入）。
     */
    public void assertPublishAllowed(PublishManifest manifest) {
        if (manifestRequired) {
            if (manifest == null) {
                throw new IllegalArgumentException("发布必须携带签名清单（§2.9.9④）：缺 manifest");
            }
            List<String> missing = manifest.missingFields();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("发布清单不完整：缺 " + missing);
            }
        }
        if (signingRequired && (manifest == null || !manifest.hasSignature())) {
            throw new IllegalArgumentException("环境要求签名发布，但签名基础设施未接入/清单无签名"
                    + "（" + Violation.SIGNING_NOT_AVAILABLE + "）");
        }
    }
}

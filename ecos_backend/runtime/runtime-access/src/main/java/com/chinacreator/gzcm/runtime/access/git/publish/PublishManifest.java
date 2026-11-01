package com.chinacreator.gzcm.runtime.access.git.publish;

import java.util.ArrayList;
import java.util.List;

/**
 * 签名发布清单（§2.9.9④ 契约面；#147F）。
 *
 * <p>把「Commit SHA ≠ 发布授权」写成类型事实：清单必须同时绑定
 * <b>仓库标识 + Commit SHA + 内容哈希 + 目标环境 + 审批记录 + 策略版本</b>，
 * 缺任一项即不可用于发布。仅凭一个 commitId 就能发布，是本节要消灭的形态。</p>
 *
 * <p><b>签名状态</b>：本仓尚无 commit/tag 签名基础设施（GPG/SSH signing 未接），
 * 故 {@link #signature()} 只承载外部注入的签名串；缺失时清单可"完整"但
 * {<b>不可</b>越过需要签名强制的环境（{@link #requiresSignature()} 由策略决定，
 * 见 {@link BranchProtectionPolicy}）。禁把"没签名"报成"已签名"。</p>
 *
 * @param repositoryId      注册表 repositoryId（六枚之一）
 * @param commitSha         真实提交号（不是"待定"）
 * @param contentHash       归档内容摘要（sha256:...）
 * @param targetEnvironment standard / enterprise / ultimate / dev
 * @param approvalRecordId  服务端持久化的审批记录 ID（四眼裁决结果）
 * @param policyVersion     审批/发布策略版本（可回溯"当时按哪版规则放行"）
 * @param signature         外部签名（可空）
 * @author ECOS Runtime底座 #147F
 */
public record PublishManifest(String repositoryId, String commitSha, String contentHash,
                              String targetEnvironment, String approvalRecordId,
                              String policyVersion, String signature) {

    /** 必填键名（供违例清单与前端展示共用同一词表）。 */
    public static final List<String> REQUIRED_FIELDS = List.of(
            "repositoryId", "commitSha", "contentHash", "targetEnvironment",
            "approvalRecordId", "policyVersion");

    public PublishManifest {
        repositoryId = trim(repositoryId);
        commitSha = trim(commitSha);
        contentHash = trim(contentHash);
        targetEnvironment = trim(targetEnvironment);
        approvalRecordId = trim(approvalRecordId);
        policyVersion = trim(policyVersion);
        signature = trim(signature);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 缺项清单（空 = 六项齐备）。 */
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (repositoryId == null) missing.add("repositoryId");
        if (commitSha == null) missing.add("commitSha");
        if (contentHash == null) missing.add("contentHash");
        if (targetEnvironment == null) missing.add("targetEnvironment");
        if (approvalRecordId == null) missing.add("approvalRecordId");
        if (policyVersion == null) missing.add("policyVersion");
        return missing;
    }

    /** 六项齐备才可用于发布（Commit SHA 单独存在不构成授权）。 */
    public boolean isComplete() {
        return missingFields().isEmpty();
    }

    public boolean hasSignature() {
        return signature != null;
    }

    /**
     * 发布授权判定：完整清单 <b>且</b> 审批记录非空（冗余但可读的第二道）+ 若策略要求签名则必须有签名。
     *
     * @param signatureRequired 目标环境/分支是否强制签名
     * @throws IllegalStateException 不满足时携带缺项/缺签原因（禁返回 false 让调用方猜）
     */
    public void assertAuthorizesPublish(boolean signatureRequired) {
        List<String> missing = missingFields();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("发布清单不完整，禁止发布（§2.9.9④）：缺 " + missing);
        }
        if (signatureRequired && !hasSignature()) {
            throw new IllegalStateException("目标环境要求签名发布，但清单无签名（Commit SHA ≠ 发布授权）");
        }
    }
}

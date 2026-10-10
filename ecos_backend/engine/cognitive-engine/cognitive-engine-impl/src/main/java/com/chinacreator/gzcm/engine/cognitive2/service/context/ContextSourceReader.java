package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;

import java.util.List;

/**
 * Context Builder 权威源读取 SPI（《认知域与智能域补充详细设计 v1.0》§5.4 步骤 3「并行读取元数据」）。
 *
 * <p>设计口径：Context Builder 只读取请求声明的权威源清单（{@code requiredAssets}），
 * <b>不得无界检索全平台</b>（§5.4-2）。各数据面（data/ontology/kb/cognitive/ai 能力目录）
 * 的真实读取端点将由各自引擎既有只读接口承接 —— 本 SPI 是装配检查点，
 * 由实现方（本模块同进程 Service 或后续 REST 适配）注册为 Spring Bean；
 * 任一 {@code sourceType} 无注册实现 = 该面<b>未接线</b>，Builder 按失败语义处理
 * （必需源 ⇒ 503 E503_DEPENDENCY_MISSING；可选源 ⇒ warnings），<b>不伪造空集合</b>（§5.5 /
 * 附录 B "不要把引擎返回空内容就视为没有数据"）。</p>
 *
 * <p>实现纪律：</p>
 * <ul>
 *   <li>读取必须在源引擎或其授权读接口执行权限裁剪（§5.2：Builder 不得先读全量再自行过滤）；</li>
 *   <li>禁止 import 其他 engine 的 impl 包 —— 跨引擎只经契约包 / common（ArchUnit 守门）；</li>
 *   <li>读取结果必须带 ID/版本/时点三要素（SUP-CTX-01）；空返回 = 真空态（合法，清单显式空列表）；
 *       读失败（依赖不可达 / 超时）必须抛 {@link ContextReadException} 而非返回空。</li>
 * </ul>
 */
public interface ContextSourceReader {

    /** 本实现承接的来源类型（对齐 {@link ContextSourceRef#getSourceType()}：DATA_ASSET 等）。 */
    boolean supports(String sourceType);

    /**
     * 读取单个来源的引用/投影（只读；必须已在源侧完成权限裁剪）。
     *
     * @param request  原始构建请求（scope/goal/budget 供源侧裁剪与预算限流）
     * @param ref      权威源清单条目
     * @return 读取结果（{@link SourceReadResult}；失败请抛 {@link ContextReadException}）
     */
    SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref);

    /**
     * 读取结果：全部引用/投影 + 该源的访问结果。
     * 约定：{@code denied=true} 表示在源侧发生了权限裁剪且裁剪后<b>仍有剩余不可见内容</b>
     * （Builder 侧据此生成 PERMISSION_GAP）；依赖不可达必须抛异常，不在此返回。
     */
    class SourceReadResult {
        /** 来源版本（读取时点实际版本；与 ref.version 不一致 ⇒ 版本失配语义）。 */
        private String version;
        /** 读取时点（ISO-8601 UTC）。 */
        private String asOfTime;
        /** 事实投影（带 fieldKey/来源/时点；金额等确定性数值行必须挂 calculationRef）。 */
        private List<FactProjection> facts = new java.util.ArrayList<>();
        /** 该类型下的资产引用（知识/本体/认知资产条目；Builder 按 ref.sourceType 归桶）。 */
        private List<AssetProjection> assetRefs = new java.util.ArrayList<>();
        /** 能力引用（仅 CAPABILITY 面填充）。 */
        private List<CapabilityProjection> capabilities = new java.util.ArrayList<>();
        /** 源侧权限裁剪标记（部分行/列不可见 = true）。 */
        private boolean denied;
        /** 该源是否权威（源侧权威等级；冲突裁决优先依据，§5.4-6）。 */
        private boolean authoritative;
        /** 新鲜度：CURRENT / STALE / UNKNOWN。 */
        private String freshness = "UNKNOWN";

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getAsOfTime() { return asOfTime; }
        public void setAsOfTime(String asOfTime) { this.asOfTime = asOfTime; }
        public List<FactProjection> getFacts() { return facts; }
        public void setFacts(List<FactProjection> facts) { this.facts = facts; }
        public List<AssetProjection> getAssetRefs() { return assetRefs; }
        public void setAssetRefs(List<AssetProjection> assetRefs) { this.assetRefs = assetRefs; }
        public List<CapabilityProjection> getCapabilities() { return capabilities; }
        public void setCapabilities(List<CapabilityProjection> capabilities) { this.capabilities = capabilities; }
        public boolean isDenied() { return denied; }
        public void setDenied(boolean denied) { this.denied = denied; }
        public boolean isAuthoritative() { return authoritative; }
        public void setAuthoritative(boolean authoritative) { this.authoritative = authoritative; }
        public String getFreshness() { return freshness; }
        public void setFreshness(String freshness) { this.freshness = freshness; }
    }

    /** 事实投影行（SUP-CTX-01：sourceId/version 必填，由 Builder 强校验）。 */
    class FactProjection {
        private final String fieldKey;
        private final String value;
        private final String sourceId;
        private final String version;
        private final String asOfTime;

        public FactProjection(String fieldKey, String value, String sourceId, String version, String asOfTime) {
            this.fieldKey = fieldKey;
            this.value = value;
            this.sourceId = sourceId;
            this.version = version;
            this.asOfTime = asOfTime;
        }

        public String getFieldKey() { return fieldKey; }
        public String getValue() { return value; }
        public String getSourceId() { return sourceId; }
        public String getVersion() { return version; }
        public String getAsOfTime() { return asOfTime; }
    }

    /** 资产引用投影（refId 必填；分类由 Builder 按 sourceType 归桶）。 */
    class AssetProjection {
        private final String refId;
        private final String version;
        private final String status;
        private final String asOfTime;

        public AssetProjection(String refId, String version, String status, String asOfTime) {
            this.refId = refId;
            this.version = version;
            this.status = status;
            this.asOfTime = asOfTime;
        }

        public String getRefId() { return refId; }
        public String getVersion() { return version; }
        public String getStatus() { return status; }
        public String getAsOfTime() { return asOfTime; }
    }

    /** 能力引用投影（capabilityId 必填）。 */
    class CapabilityProjection {
        private final String capabilityId;
        private final String contractVersion;
        private final String modelRef;
        private final String modelVersion;
        private final String riskLevel;

        public CapabilityProjection(String capabilityId, String contractVersion,
                                    String modelRef, String modelVersion, String riskLevel) {
            this.capabilityId = capabilityId;
            this.contractVersion = contractVersion;
            this.modelRef = modelRef;
            this.modelVersion = modelVersion;
            this.riskLevel = riskLevel;
        }

        public String getCapabilityId() { return capabilityId; }
        public String getContractVersion() { return contractVersion; }
        public String getModelRef() { return modelRef; }
        public String getModelVersion() { return modelVersion; }
        public String getRiskLevel() { return riskLevel; }
    }
}

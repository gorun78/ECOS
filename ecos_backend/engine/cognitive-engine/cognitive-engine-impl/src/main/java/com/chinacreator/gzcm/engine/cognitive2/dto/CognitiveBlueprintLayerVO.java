package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 认知六层蓝图 · 单层 VO（D151）。
 *
 * <p>承载 {@code GET /api/v1/cognitive/blueprint} 响应数组的每一项。字段名兼顾前端
 * {@code cos/blueprintData.ts#mapApiBlueprintLayer} 的解析约定（{@code id/nameZh/coverage}）
 * 与本域的诚实性标记 {@code verified/sourceEngine/reason}。</p>
 *
 * <p><b>verified 语义（无假真理铁律 7）</b>：{@code true} = 该层 coverage/components 取自对应引擎
 * 真实健康端点；{@code false} = 探活失败或引擎非 UP，此时 coverage 恒 0、{@code reason} 说明原因，
 * 前端据此继续保持降级语义，**不得**把 verified=false 当真实数据展示。</p>
 *
 * @author ecos-factory
 * @since D151 (2026-11-16)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CognitiveBlueprintLayerVO {

    /** 层标识，与前端六层 id 对齐（strategic/knowledge/agent_os/semantic/data_platform/security_infra）。 */
    private String id;

    /** 层中文名。 */
    private String nameZh;

    /** 数据来源引擎健康端点 slug（cognitive/knowledge/ai/ontology/data/security）。 */
    private String sourceEngine;

    /** 覆盖度（0~100）。UP=100、非 UP/不可达=0；不按计数编造百分比。 */
    private int coverage;

    /** 是否为真实探活结果。false 时前端须保持降级语义。 */
    private boolean verified;

    /** 该层引擎健康端点的 components 原样透传（db/engine/agents/rules 等），供前端展示真值。 */
    private Map<String, Object> components;

    /** verified=false 时的短原因（引擎非 UP / 不可达 / 缺字段）。 */
    private String reason;

    public static CognitiveBlueprintLayerVO verified(String id, String nameZh, String sourceEngine,
                                                     int coverage, Map<String, Object> components, String reason) {
        CognitiveBlueprintLayerVO vo = new CognitiveBlueprintLayerVO();
        vo.id = id;
        vo.nameZh = nameZh;
        vo.sourceEngine = sourceEngine;
        vo.coverage = coverage;
        vo.verified = true;
        vo.components = components;
        vo.reason = reason;
        return vo;
    }

    public static CognitiveBlueprintLayerVO unverified(String id, String nameZh, String sourceEngine, String reason) {
        CognitiveBlueprintLayerVO vo = new CognitiveBlueprintLayerVO();
        vo.id = id;
        vo.nameZh = nameZh;
        vo.sourceEngine = sourceEngine;
        vo.coverage = 0;
        vo.verified = false;
        vo.reason = reason;
        return vo;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }

    public String getSourceEngine() { return sourceEngine; }
    public void setSourceEngine(String sourceEngine) { this.sourceEngine = sourceEngine; }

    public int getCoverage() { return coverage; }
    public void setCoverage(int coverage) { this.coverage = coverage; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public Map<String, Object> getComponents() { return components; }
    public void setComponents(Map<String, Object> components) { this.components = components; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

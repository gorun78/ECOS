package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 缺口项契约（《认知域与智能域补充详细设计 v1.0》§5.4 步骤 7 / §6.5 Gap 项结构）。
 *
 * <p>"缺失数据、缺失关系、缺失权限、缺失证据等"的可操作项。
 * {@code suggestedActions} 只是<b>建议</b>，不等于已执行动作（§6.5 纪律）。</p>
 */
@Data
public class Gap {

    /** 缺口类别（§6.5 枚举）：DATA_MISSING / DQ_FAILURE / ONTOLOGY_GAP / KNOWLEDGE_GAP /
     *  EVIDENCE_GAP / CAPABILITY_GAP / PERMISSION_GAP / CONFLICT 等。 */
    public static final String CATEGORY_DATA_MISSING = "DATA_MISSING";
    public static final String CATEGORY_DQ_FAILURE   = "DQ_FAILURE";
    public static final String CATEGORY_ONTOLOGY_GAP  = "ONTOLOGY_GAP";
    public static final String CATEGORY_KNOWLEDGE_GAP = "KNOWLEDGE_GAP";
    public static final String CATEGORY_EVIDENCE_GAP  = "EVIDENCE_GAP";
    public static final String CATEGORY_CAPABILITY_GAP = "CAPABILITY_GAP";
    public static final String CATEGORY_PERMISSION_GAP = "PERMISSION_GAP";
    public static final String CATEGORY_CONFLICT = "CONFLICT";
    public static final String CATEGORY_DEPENDENCY_MISSING = "DEPENDENCY_MISSING";

    /** 稳定 ID（{@code gap-xxxx}；Builder 组装时生成 uuid，可复现场景下纳入 digest 输入）。 */
    private String gapId;

    /** 缺口类别（见上方 CATEGORY_* 常量）。 */
    private String category;

    /** 严重度：BLOCKER / HIGH / MEDIUM / LOW（§6.5）。 */
    private String severity;

    /** 关联资产或蓝图步骤引用（§6.5 targetRef）。 */
    private String targetRef;

    /** 可读描述。 */
    private String description;

    /** 形成缺口判断的证据引用（§6.5 evidenceRefs）。 */
    private List<String> evidenceRefs = new ArrayList<>();

    /** 建议动作（不等于已执行动作）。 */
    private List<String> suggestedActions = new ArrayList<>();

    /** 是否需要审批（§6.5 requiresApproval）。 */
    private boolean requiresApproval;

    /** 状态：OPEN / IN_PROGRESS / RESOLVED / ACCEPTED_RISK（§6.5；Builder 产出一律 OPEN）。 */
    private String status = STATUS_OPEN;

    /** 解决该缺口的提案/测试/发布版本引用（OPEN 态为 null）。 */
    private String resolutionRef;

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_ACCEPTED_RISK = "ACCEPTED_RISK";

    public static final String SEVERITY_BLOCKER = "BLOCKER";
    public static final String SEVERITY_HIGH    = "HIGH";
    public static final String SEVERITY_MEDIUM  = "MEDIUM";
    public static final String SEVERITY_LOW     = "LOW";
}

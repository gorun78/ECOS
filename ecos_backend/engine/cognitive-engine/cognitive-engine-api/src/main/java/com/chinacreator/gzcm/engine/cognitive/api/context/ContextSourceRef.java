package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Context Builder 权威源清单条目（§5.4 步骤 2「建立权威源清单」的输入元素）。
 *
 * <p>来源清单必须<b>由请求方（Blueprint 依赖 / 任务类型）显式给出</b>，
 * Context Builder 不得无界检索全平台（§5.4-2）。
 * 条目自身的 version 字段若与期望版本不一致 ⇒ 409 E409_VERSION_STALE（§5.5 版本不匹配）。</p>
 */
@Data
public class ContextSourceRef {

    /** 来源类型：DATA_ASSET / ONTOLOGY / KNOWLEDGE / COGNITIVE / CAPABILITY。 */
    private String sourceType;

    /** 来源稳定 ID（必填）。 */
    private String sourceId;

    /**
     * 期望版本/快照（可选）：非空时执行严格版本比对（§5.4-5 统一引用和版本），
     * 读回版本不等 ⇒ 版本失配语义（409）。
     */
    private String version;

    /** 是否必需（必填语义；缺省 true）。false = 可选来源，不可用时只进 warnings 不阻断。 */
    private boolean required = true;

    /** 成功条件依赖（与 {@code goal.successCriteria} 条目对齐；true = 该源缺失 ⇒ ready=false）。 */
    private boolean successCritical = false;

    /** 关联的证据引用（可选）。 */
    private List<String> evidenceRefs = new ArrayList<>();
}

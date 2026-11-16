package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.ScenarioErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 动作/运行请求体必填字段缺失（F07-10 五必填 / F07-14 run body，HTTP 400 / ACTION_FIELDS_MISSING）。 */
public class ActionValidationException extends WorkspaceDomainException {

    /** 机读码单源：本类只在此处引用属主常量一次（棘轮按文件内引用枚数计，重复写会打破单源）。 */
    private static final String CODE = ScenarioErrorCode.CODE_ACTION_FIELDS_MISSING;

    private final List<String> missingFields;

    public ActionValidationException(List<String> missingFields) {
        super(400, CODE, "必填字段缺失: " + missingFields);
        this.missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        putDetail("missingFields", this.missingFields);
    }

    /**
     * 请求体里出现物理表不存在的字段（D90）。
     *
     * <p>与「必填缺失」同为<b>调用方请求错误</b>，故共用 400 与同一机读码；
     * 但详情面区分字段：{@code invalidFields} 是被拒的字段名，
     * {@code allowedFields} 是该表真实可写列（前端可直接用于表单渲染，
     * 不必再猜哪些字段合法——D90 里前端正是按被污染的 schema 面渲染出非法字段）。</p>
     */
    public ActionValidationException(Collection<String> invalidFields, Set<String> allowedFields) {
        super(400, CODE, "非法字段: " + invalidFields + "；该实体可用字段: " + allowedFields);
        this.missingFields = List.of();
        putDetail("invalidFields", List.copyOf(invalidFields));
        putDetail("allowedFields", List.copyOf(allowedFields));
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}

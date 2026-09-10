package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 规则详情 VO — 规则全量字段 + 版本历史列表。
 *
 * @author PMO-48-A T3
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqRuleDetailVO {

    /** 规则完整信息 */
    private DqRuleVO rule;

    /** 版本历史（按 version_number 降序） */
    private List<DqRuleVersionVO> versions;
}

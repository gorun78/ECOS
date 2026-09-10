package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 规则逻辑删除结果 — DELETE 端点出参。
 * <p>
 * 表头不强制，仅承载删除前关键标识，便于前端确认与审计追溯。
 * </p>
 *
 * @author PMO-48-B T7c
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogicDeleteResult {

    /** 被删除的规则 ID */
    private String id;

    /** 删除时间戳（删除动作执行时记录，对应 deleted_at 列） */
    private LocalDateTime beforeDeleteAt;

    /** 删除前是否已逻辑删除（幂等保护：重复删除返回 true 不抛异常） */
    private boolean alreadyDeleted;
}

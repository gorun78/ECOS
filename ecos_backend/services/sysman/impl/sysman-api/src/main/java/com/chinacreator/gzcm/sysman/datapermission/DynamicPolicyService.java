package com.chinacreator.gzcm.sysman.datapermission;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.model.DynamicPolicy;

/**
 * 动态数据权限策略服务：
 * - 基于策略与上下文（如金额上限、时间范围）生成附加 WHERE 条件。
 */
public interface DynamicPolicyService {

    /**
     * 生成动态策略条件表达式（不直接注入 SQL，只返回 AND 连接的条件片段）。
     *
     * @param policies 动态策略列表
     * @param context  上下文变量（amountLimit、startTime、endTime 等）
     * @return 组合后的条件表达式，如 "amount < 10000 AND (time >= '09:00' AND time <= '18:00')"
     */
    String buildCondition(List<DynamicPolicy> policies, Map<String, Object> context);
}



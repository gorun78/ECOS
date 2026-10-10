package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 场景蓝图领域异常基类（补充设计 v1.0 §6）。
 *
 * <p>与场景域 {@link ScenarioDomainException} 同继承体系（携带真实 HTTP 状态码 +
 * 业务 code + 结构化 details），HTTP 映射由既有 {@code WorkspaceExceptionHandler}
 * 唯一持有（它已覆盖整棵 {@code ScenarioDomainException} 子类树，本包不新增 advice）。</p>
 */
public abstract class BlueprintDomainException extends ScenarioDomainException {

    protected BlueprintDomainException(int httpStatus, String code, String message) {
        super(httpStatus, code, message);
    }
}

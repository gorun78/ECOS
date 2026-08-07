package com.chinacreator.gzcm.engine.ontology;

import com.chinacreator.gzcm.engine.ontology.model.ActionType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ActionType 服务接口 — CRUD + execute。
 */
public interface ActionTypeService {

    /** 查询全部 ActionType（可按 objectTypeId 过滤） */
    List<ActionType> listActionTypes(String objectTypeId);

    /** 按 ID 查询单条 */
    Optional<ActionType> getActionType(String id);

    /** 创建 ActionType */
    ActionType createActionType(ActionType body);

    /** 更新 ActionType */
    ActionType updateActionType(String id, ActionType body);

    /** 删除 ActionType */
    boolean deleteActionType(String id);

    /**
     * 执行 ActionType — 入参为 {@code Map} 包含 objectId / context。
     *
     * @return 执行结果（preconditionCheck / execution / postActions / auditId）
     */
    Map<String, Object> executeAction(String actionId, Map<String, Object> payload);
}

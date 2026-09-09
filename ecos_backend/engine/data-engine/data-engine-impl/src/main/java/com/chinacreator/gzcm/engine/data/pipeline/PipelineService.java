package com.chinacreator.gzcm.engine.data.pipeline;

import java.util.List;

/**
 * Pipeline 定义管理服务接口（实现层契约）。
 * <p>出入参强类型（XxxSaveDTO / XxxVO / XxxQuery 命名），禁止 Map。
 *
 * @author DataBridge Datanet Team
 */
public interface PipelineService {

    /**
     * 创建 Pipeline 定义（含节点）。
     *
     * @param dto 保存请求体
     * @return 创建后的定义实体
     */
    PipelineDefinition createDefinition(PipelineSaveDTO dto);

    /**
     * 更新 Pipeline 定义。
     *
     * @param id  定义 ID
     * @param dto 保存请求体
     * @return 更新后的定义实体
     */
    PipelineDefinition updateDefinition(String id, PipelineSaveDTO dto);

    /**
     * 删除 Pipeline 定义（软删除：状态 → ARCHIVED）。
     */
    void deleteDefinition(String id);

    /**
     * 获取单个 Pipeline 定义。
     */
    PipelineDefinition getDefinition(String id);

    /**
     * 获取 Pipeline 定义列表（过滤 ARCHIVED）。
     */
    List<PipelineDefinition> listDefinitions();

    /**
     * 分页查询某定义的执行历史。
     *
     * @param id       定义 ID
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PipelineExecutionPageVO listExecutions(String id, int page, int pageSize);

    /**
     * 执行前置 ABAC 裁决（架构铁律 §2.4）：security-engine 允许才放行，
     * 不可用时默认 DENY。被拒绝时抛 {@code BusinessException}(403)。
     *
     * @param id 定义 ID
     */
    void checkAbacBeforeExecute(String id);

    /**
     * 定义实体 → VO。节点 config 敏感字段序列化前脱敏（§4.3）。
     *
     * @param def        定义实体
     * @param withNodes  是否携带 nodes（详情/创建/更新 true，列表 false）
     * @return PipelineVO
     */
    PipelineVO toVO(PipelineDefinition def, boolean withNodes);

    /**
     * 执行记录实体 → VO。
     *
     * @param exec 执行记录
     * @return PipelineExecutionVO
     */
    PipelineExecutionVO toExecutionVO(PipelineExecution exec);
}

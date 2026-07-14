package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.datanet.pipeline.PipelineDefinition;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 定义管理服务接口。
 *
 * @author DataBridge Datanet Team
 */
public interface PipelineService {

    /**
     * 创建 Pipeline 定义（含节点）。
     *
     * @param body 请求体，包含 name/description/nodes/edges
     * @return 创建的 Pipeline 定义
     */
    PipelineDefinition createDefinition(Map<String, Object> body);

    /**
     * 更新 Pipeline 定义。
     *
     * @param id   定义 ID
     * @param body 请求体
     * @return 更新后的定义
     */
    PipelineDefinition updateDefinition(String id, Map<String, Object> body);

    /**
     * 删除 Pipeline 定义（软删除：状态 → ARCHIVED）。
     */
    void deleteDefinition(String id);

    /**
     * 获取单个 Pipeline 定义。
     */
    PipelineDefinition getDefinition(String id);

    /**
     * 获取 Pipeline 定义列表。
     */
    List<PipelineDefinition> listDefinitions();
}

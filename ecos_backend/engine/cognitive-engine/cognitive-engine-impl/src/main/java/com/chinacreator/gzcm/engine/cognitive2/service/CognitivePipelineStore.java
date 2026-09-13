package com.chinacreator.gzcm.engine.cognitive2.service;

import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipeline;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineEntity;

/**
 * 认知管线存储接口 — 解耦 JdbcTemplate 实现，保留测试友好性。
 *
 * <p>原 {@code Map<String, CognitivePipeline>} 内存存储的替代品。
 * 生产实现见 {@link CognitivePipelineRepository}（JdbcTemplate + kb_cognitive_pipeline 表）。</p>
 */
public interface CognitivePipelineStore {

    /**
     * 保存管线定义到 PostgreSQL。
     *
     * @param pipeline 管线对象（id 必填，nodes 已序列化到 config 字段）
     */
    void save(CognitivePipeline pipeline);

    /**
     * 按业务 ID 查询管线（仅未删除）。
     *
     * @param pipelineId 管线业务 ID
     * @return 可能存在
     */
    Optional<CognitivePipeline> findById(String pipelineId);

    /**
     * 列出所有未删除的管线，按创建时间倒序。
     *
     * @return 管线列表（可能为空）
     */
    List<CognitivePipeline> findAll();
}

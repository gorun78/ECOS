package com.chinacreator.gzcm.engine.cognitive2.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipeline;

/**
 * 认知管线 JDBC 仓库 — kb_cognitive_pipeline 表 CRUD。
 *
 * <p>实现 {@link CognitivePipelineStore} 接口，将认知管线定义
 * 持久化到 PostgreSQL（原 ConcurrentHashMap 内存态）。
 *
 * <p>JSONB 读写：INSERT 使用 {@code ?::jsonb} 强转，SELECT 使用
 * {@code rs.getString()} 读取 JSON 字符串（与 WorkflowTaskRepository 风格一致）。
 *
 * <p>逻辑删除：所有查询过滤 {@code is_deleted = 0}，
 * 删除操作设置 {@code is_deleted = 1}（铁律 4.8）。</p>
 */
@Repository
public class CognitivePipelineRepository implements CognitivePipelineStore {

    private static final Logger log = LoggerFactory.getLogger(CognitivePipelineRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public CognitivePipelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** RowMapper — kb_cognitive_pipeline 行 → CognitivePipeline */
    private final RowMapper<CognitivePipeline> ROW_MAPPER = (rs, rowNum) -> {
        CognitivePipeline pipeline = new CognitivePipeline();
        pipeline.setId(rs.getString("pipeline_id"));
        pipeline.setName(rs.getString("name"));

        String configJson = rs.getString("config");
        pipeline.setNodes(extractNodes(configJson));
        pipeline.setDescription(extractDescription(configJson));

        pipeline.setCreatedAt(rs.getTimestamp("created_at"));
        pipeline.setUpdatedAt(rs.getTimestamp("updated_at"));
        return pipeline;
    };

    @Override
    public void save(CognitivePipeline pipeline) {
        String configJson = buildConfigJson(pipeline);
        int rows = jdbc.update(
                "UPDATE kb_cognitive_pipeline SET " +
                "  name = ?, config = ?::jsonb, updated_at = NOW() " +
                "WHERE pipeline_id = ? AND is_deleted = 0",
                pipeline.getName(), configJson, pipeline.getId());
        if (rows == 0) {
            jdbc.update(
                    "INSERT INTO kb_cognitive_pipeline " +
                    "(pipeline_id, name, status, config, created_by, created_at, updated_at, is_deleted) " +
                    "VALUES (?, ?, 'DRAFT', ?::jsonb, 'system', NOW(), NOW(), 0)",
                    pipeline.getId(), pipeline.getName(), configJson);
            log.info("CognitivePipeline inserted: pipelineId={}", pipeline.getId());
        } else {
            log.info("CognitivePipeline updated: pipelineId={}", pipeline.getId());
        }
    }

    @Override
    public Optional<CognitivePipeline> findById(String pipelineId) {
        List<CognitivePipeline> list = jdbc.query(
                "SELECT id, pipeline_id, name, status, config, created_by, created_at, updated_at, result, is_deleted " +
                "FROM kb_cognitive_pipeline WHERE pipeline_id = ? AND is_deleted = 0",
                ROW_MAPPER, pipelineId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<CognitivePipeline> findAll() {
        return jdbc.query(
                "SELECT id, pipeline_id, name, status, config, created_by, created_at, updated_at, result, is_deleted " +
                "FROM kb_cognitive_pipeline WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT 200",
                ROW_MAPPER);
    }

    /** 逻辑删除（铁律 4.8） — DELETE 不物理删除，仅标记 is_deleted = 1 */
    public int logicalDelete(String pipelineId) {
        int rows = jdbc.update(
                "UPDATE kb_cognitive_pipeline SET is_deleted = 1, updated_at = NOW() " +
                "WHERE pipeline_id = ? AND is_deleted = 0",
                pipelineId);
        if (rows > 0) {
            log.info("CognitivePipeline logical deleted: pipelineId={}", pipelineId);
        }
        return rows;
    }

    /**
     * 将管线元素序列化为 config JSON 字符串。
     * <p>结构：{@code {"description": "...", "nodes": [...]}}。</p>
     */
    @SuppressWarnings("unchecked")
    private String buildConfigJson(CognitivePipeline pipeline) {
        try {
            List<Map<String, Object>> nodes =
                    MAPPER.readValue(pipeline.toNodesJson(), new TypeReference<>() {});
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("description", pipeline.getDescription() != null ? pipeline.getDescription() : "");
            cfg.put("nodes", nodes);
            return MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("Failed to build config JSON for pipeline {}: {}", pipeline.getId(), e.getMessage());
            return "{}";
        }
    }

    /** 从 config JSON 中提取 nodes 数组并反序列化为 CognitivePipelineNode 列表 */
    private java.util.List<com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode> extractNodes(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> cfg = MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Object nodesObj = cfg.get("nodes");
            if (nodesObj == null) {
                return new ArrayList<>();
            }
            return MAPPER.convertValue(nodesObj, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to extract nodes from config: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 从 config JSON 中提取 description 字段（可能为空） */
    private String extractDescription(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> cfg = MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Object desc = cfg.get("description");
            return desc != null ? desc.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

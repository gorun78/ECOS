package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.dao.PipelineDefinitionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline Repository — 数据模型 CRUD 门面（委托 {@link PipelineDefinitionDao} 落库）。
 *
 * <p>B4a 收口：本类不再私持 {@code JdbcTemplate}、不再 inline raw-SQL；所有
 * SQL 下沉 {@link PipelineDefinitionDao}（{@code ..dao..}），本 Repository 仅做
 * 业务编排（参数前置校验、行 mapper 组装、状态默认值补齐），原方法签名与
 * 返回契约保持不变（API 只增不改）。
 *
 * @author DataBridge Datanet Team
 */
@Repository
public class PipelineRepository {

    private static final Logger log = LoggerFactory.getLogger(PipelineRepository.class);

    private final PipelineDefinitionDao dao;

    public PipelineRepository(PipelineDefinitionDao dao) {
        this.dao = dao;
    }

    // H8-T1: ecos_pipeline_node.depends_on 列由 db/migration 单一来源维护（V25_1 已含
    // CREATE + ALTER ADD COLUMN IF NOT EXISTS），运行时不再内嵌 DDL。

    // ==================== PipelineDefinition ====================

    public PipelineDefinition insertDefinition(PipelineDefinition def) {
        String id = def.getId() != null ? def.getId() : UUID.randomUUID().toString().replace("-", "");
        String definitionJson = PipelineDefinitionDao.buildDefinitionJson(def);
        dao.insertDefinition(id, def.getName(), def.getDescription(),
                definitionJson,
                def.getStatus() != null ? def.getStatus() : "DRAFT");
        return findDefinitionById(id);
    }

    public PipelineDefinition updateDefinition(String id, String name, String description, String status) {
        StringBuilder set = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (name != null) {
            set.append("name = ?, ");
            params.add(name);
        }
        if (description != null) {
            set.append("description = ?, ");
            params.add(description);
        }
        if (status != null) {
            set.append("status = ?, ");
            params.add(status);
        }

        if (set.isEmpty()) {
            return findDefinitionById(id);
        }

        set.append("updated_at = NOW()");
        params.add(id);

        dao.updateDefinition(id, set.toString(), params);
        return findDefinitionById(id);
    }

    /**
     * 更新 Pipeline 定义的 schedule（cron + scheduleId），存入 definition JSONB 列。
     */
    public void updateDefinitionSchedule(String id, String scheduleCron, String scheduleId) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> schedule = new LinkedHashMap<>();
            schedule.put("cron", scheduleCron);
            if (scheduleId != null) schedule.put("scheduleId", scheduleId);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schedule", schedule);
            String json = om.writeValueAsString(root);
            dao.updateDefinitionScheduleJson(json, id);
        } catch (Exception e) {
            log.warn("Failed to update schedule for definition {}: {}", id, e.getMessage());
        }
    }

    public void deleteDefinition(String id) {
        dao.archiveDefinition(id);
    }

    public PipelineDefinition findDefinitionById(String id) {
        List<Map<String, Object>> rows = dao.findDefinitionRow(id);
        if (rows.isEmpty()) return null;
        return PipelineDefinitionDao.mapDefinition(rows.get(0));
    }

    public List<PipelineDefinition> findAllDefinitions() {
        List<Map<String, Object>> rows = dao.findDefinitionRowsActive();
        List<PipelineDefinition> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(PipelineDefinitionDao.mapDefinition(row));
        }
        return result;
    }

    public boolean definitionExists(String id) {
        Integer count = dao.countDefinition(id);
        return count != null && count > 0;
    }

    // ==================== PipelineNode ====================

    public void insertNode(PipelineNode node) {
        String id = node.getId() != null ? node.getId() : UUID.randomUUID().toString().replace("-", "");
        dao.insertNode(id, node.getDefinitionId(), node.getNodeId(), node.getType(),
                node.getConfig(), node.getDependsOn() != null ? node.getDependsOn() : "[]",
                node.getPositionX(), node.getPositionY());
    }

    public void deleteNodesByDefinitionId(String definitionId) {
        dao.deleteNodesByDefinitionId(definitionId);
    }

    public void updateNodeDependsOn(String definitionId, String nodeId, String dependsOnJson) {
        dao.updateNodeDependsOn(definitionId, nodeId, dependsOnJson);
    }

    public List<PipelineNode> findNodesByDefinitionId(String definitionId) {
        List<Map<String, Object>> rows = dao.findNodeRowsByDefinitionId(definitionId);
        List<PipelineNode> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(PipelineDefinitionDao.mapNode(row));
        }
        return result;
    }

    // ==================== PipelineExecution ====================

    public PipelineExecution insertExecution(PipelineExecution exec) {
        String id = exec.getId() != null ? exec.getId() : UUID.randomUUID().toString().replace("-", "");
        dao.insertExecution(id, exec.getDefinitionId(),
                exec.getStatus() != null ? exec.getStatus() : "PENDING");
        return findExecutionById(id);
    }

    public void updateExecutionStatus(String id, String status, String errorMessage, Long rowsProcessed) {
        boolean finishedNow = "COMPLETED".equals(status) || "FAILED".equals(status);
        dao.updateExecutionStatus(id, status, errorMessage, rowsProcessed, finishedNow);
    }

    public PipelineExecution findExecutionById(String id) {
        List<Map<String, Object>> rows = dao.findExecutionRow(id);
        if (rows.isEmpty()) return null;
        return PipelineDefinitionDao.mapExecution(rows.get(0));
    }

    /**
     * 按 definitionId 分页查询执行历史（V4 执行历史端点使用）。
     *
     * @param definitionId Pipeline 定义 ID
     * @param page         页码（从 1 开始）
     * @param pageSize     每页大小
     * @return 执行记录列表（按 started_at 倒序）
     */
    public List<PipelineExecution> findExecutionsByDefinitionId(String definitionId, int page, int pageSize) {
        int offset = (Math.max(page, 1) - 1) * Math.max(pageSize, 1);
        List<Map<String, Object>> rows = dao.findExecutionRowsByDefinitionId(definitionId, Math.max(pageSize, 1), offset);
        List<PipelineExecution> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(PipelineDefinitionDao.mapExecution(row));
        }
        return result;
    }

    /**
     * 按 definitionId 统计执行记录总数（分页 total 用）。
     */
    public long countExecutionsByDefinitionId(String definitionId) {
        Integer count = dao.countExecutionsByDefinitionId(definitionId);
        return count != null ? count : 0L;
    }
}

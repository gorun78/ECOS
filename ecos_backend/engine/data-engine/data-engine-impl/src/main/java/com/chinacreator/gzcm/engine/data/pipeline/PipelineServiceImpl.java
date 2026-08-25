package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Pipeline 定义管理服务实现 — 基于 JdbcTemplate 持久化。
 * <p>
 * 定时调度走 runtime-task（TaskSchedulerService），遵循架构规则 2.3。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PipelineRepository repository;

    /** runtime-task 调度服务（可选注入，无 bean 时跳过调度注册） */
    @Autowired(required = false)
    private TaskSchedulerService taskSchedulerService;

    public PipelineServiceImpl(PipelineRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PipelineDefinition createDefinition(Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空");
        }

        PipelineDefinition def = new PipelineDefinition();
        def.setId(UUID.randomUUID().toString().replace("-", ""));
        def.setName(name);
        def.setDescription((String) body.getOrDefault("description", ""));
        def.setStatus((String) body.getOrDefault("status", "DRAFT"));

        PipelineDefinition created = repository.insertDefinition(def);

        // 处理节点
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) body.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> nodeMap : nodes) {
                PipelineNode node = new PipelineNode();
                node.setId(UUID.randomUUID().toString().replace("-", ""));
                node.setDefinitionId(created.getId());
                node.setNodeId((String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id")));
                node.setType((String) nodeMap.getOrDefault("type", "TRANSFORM_SQL"));
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) nodeMap.get("config");
                try {
                    node.setConfig(config != null ? MAPPER.writeValueAsString(config) : "{}");
                } catch (Exception e) {
                    node.setConfig("{}");
                }

                Object px = nodeMap.get("positionX");
                node.setPositionX(px instanceof Number n ? n.intValue() : 0);
                Object py = nodeMap.get("positionY");
                node.setPositionY(py instanceof Number n ? n.intValue() : 0);

                repository.insertNode(node);
            }
        }

        // 处理 edges → 计算每个节点的 depends_on
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) body.get("edges");
        if (edges != null && !edges.isEmpty()) {
            // 构建 nodeId → [依赖它的节点列表]
            Map<String, List<String>> deps = new LinkedHashMap<>();
            for (Map<String, Object> edge : edges) {
                String from = (String) edge.get("from");
                String to = (String) edge.get("to");
                deps.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
            // 更新节点的 depends_on
            for (Map<String, Object> nodeMap : nodes) {
                String nodeId = (String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id"));
                List<String> dependsOn = deps.getOrDefault(nodeId, Collections.emptyList());
                try {
                    String json = MAPPER.writeValueAsString(dependsOn);
                    repository.updateNodeDependsOn(created.getId(), nodeId, json);
                } catch (Exception e) {
                    log.warn("Failed to set depends_on for node {}", nodeId, e);
                }
            }
        }

        log.info("Created pipeline definition: {} (id={})", name, created.getId());

        // 处理定时调度：body.schedule.cron 非空 → 走 runtime-task (TaskSchedulerService)
        registerSchedule(created, body);

        return created;
    }

    @Override
    @Transactional
    public PipelineDefinition updateDefinition(String id, Map<String, Object> body) {
        if (!repository.definitionExists(id)) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }

        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String status = (String) body.get("status");

        PipelineDefinition updated = repository.updateDefinition(id, name, description, status);

        // 如果传入了 nodes，则替换节点列表
        if (body.containsKey("nodes")) {
            repository.deleteNodesByDefinitionId(id);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) body.get("nodes");
            if (nodes != null) {
                for (Map<String, Object> nodeMap : nodes) {
                    PipelineNode node = new PipelineNode();
                    node.setId(UUID.randomUUID().toString().replace("-", ""));
                    node.setDefinitionId(id);
                    node.setNodeId((String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id")));
                    node.setType((String) nodeMap.getOrDefault("type", "TRANSFORM_SQL"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) nodeMap.get("config");
                    try {
                        node.setConfig(config != null ? MAPPER.writeValueAsString(config) : "{}");
                    } catch (Exception e) {
                        node.setConfig("{}");
                    }

                    Object px = nodeMap.get("positionX");
                    node.setPositionX(px instanceof Number n ? n.intValue() : 0);
                    Object py = nodeMap.get("positionY");
                    node.setPositionY(py instanceof Number n ? n.intValue() : 0);

                    repository.insertNode(node);
                }
            }
        }

        // 处理定时调度：若 body 含 schedule.cron，先取消旧 scheduleId 再注册新的
        if (body.containsKey("schedule")) {
            updateSchedule(id, body);
        }

        log.info("Updated pipeline definition: id={}", id);
        return updated;
    }

    @Override
    @Transactional
    public void deleteDefinition(String id) {
        if (!repository.definitionExists(id)) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }
        // 删除前取消已注册的 runtime-task 调度
        cancelExistingSchedule(id);
        repository.deleteNodesByDefinitionId(id);
        repository.deleteDefinition(id);
        log.info("Deleted pipeline definition: id={}", id);
    }

    @Override
    public PipelineDefinition getDefinition(String id) {
        PipelineDefinition def = repository.findDefinitionById(id);
        if (def == null) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }
        return def;
    }

    @Override
    public List<PipelineDefinition> listDefinitions() {
        return repository.findAllDefinitions();
    }

    // ==================== 定时调度（runtime-task 接入，架构规则 2.3）====================

    /**
     * 注册定时调度：从 body.schedule.cron 读取 cron 表达式，若非空则通过
     * TaskSchedulerService.scheduleTask(desc, cron) 注册到 runtime-task，
     * 并将 scheduleId 持久化到 definition JSONB（repository.updateDefinitionSchedule）。
     * body.schedule.cron 为空或 taskSchedulerService 不可用时跳过。
     */
    @SuppressWarnings("unchecked")
    private void registerSchedule(PipelineDefinition def, Map<String, Object> body) {
        if (taskSchedulerService == null) {
            log.debug("TaskSchedulerService unavailable, skip schedule registration: {}", def.getId());
            return;
        }
        String cron = extractScheduleCron(body);
        if (cron == null || cron.isEmpty()) {
            return;
        }
        try {
            TaskDescription desc = buildScheduleTaskDesc(def, cron);
            String scheduleId = taskSchedulerService.scheduleTask(desc, cron);
            repository.updateDefinitionSchedule(def.getId(), cron, scheduleId);
            log.info("Pipeline schedule registered: definitionId={}, cron={}, scheduleId={}",
                    def.getId(), cron, scheduleId);
        } catch (Exception e) {
            log.warn("Failed to register pipeline schedule: definitionId={}, error={}",
                    def.getId(), e.getMessage());
        }
    }

    /**
     * 更新定时调度：先取消旧的 scheduleId（若存在），再按 body.schedule.cron 注册新的。
     * body.schedule.cron 为空字符串时仅取消旧调度（取消调度不重新注册）。
     */
    @SuppressWarnings("unchecked")
    private void updateSchedule(String definitionId, Map<String, Object> body) {
        // 取消旧调度
        cancelExistingSchedule(definitionId);

        if (taskSchedulerService == null) {
            log.debug("TaskSchedulerService unavailable, skip schedule update: {}", definitionId);
            return;
        }
        String cron = extractScheduleCron(body);
        if (cron == null || cron.isEmpty()) {
            // 仅清空持久化的 schedule
            repository.updateDefinitionSchedule(definitionId, null, null);
            return;
        }
        try {
            PipelineDefinition def = repository.findDefinitionById(definitionId);
            if (def == null) {
                return;
            }
            TaskDescription desc = buildScheduleTaskDesc(def, cron);
            String scheduleId = taskSchedulerService.scheduleTask(desc, cron);
            repository.updateDefinitionSchedule(definitionId, cron, scheduleId);
            log.info("Pipeline schedule updated: definitionId={}, cron={}, scheduleId={}",
                    definitionId, cron, scheduleId);
        } catch (Exception e) {
            log.warn("Failed to update pipeline schedule: definitionId={}, error={}",
                    definitionId, e.getMessage());
        }
    }

    /**
     * 取消已注册的调度：从 definition 的 extensions.scheduleId 读取旧 scheduleId，
     * 调用 TaskSchedulerService.cancelSchedule。
     */
    private void cancelExistingSchedule(String definitionId) {
        if (taskSchedulerService == null) {
            return;
        }
        try {
            PipelineDefinition def = repository.findDefinitionById(definitionId);
            if (def == null || def.getExtensions() == null) {
                return;
            }
            Object sid = def.getExtensions().get("scheduleId");
            if (sid != null && !sid.toString().isEmpty()) {
                taskSchedulerService.cancelSchedule(sid.toString());
                log.info("Pipeline schedule cancelled: definitionId={}, scheduleId={}",
                        definitionId, sid);
            }
        } catch (Exception e) {
            log.warn("Failed to cancel pipeline schedule: definitionId={}, error={}",
                    definitionId, e.getMessage());
        }
    }

    /**
     * 从 body.schedule（Map 或 {cron: "..."}）提取 cron 字符串。
     */
    @SuppressWarnings("unchecked")
    private String extractScheduleCron(Map<String, Object> body) {
        if (body == null) return null;
        Object scheduleObj = body.get("schedule");
        if (scheduleObj instanceof Map) {
            Object cron = ((Map<String, Object>) scheduleObj).get("cron");
            return cron != null ? cron.toString().trim() : null;
        }
        // 兼容顶层 scheduleCron / schedule_cron 字段
        Object direct = body.get("scheduleCron");
        if (direct == null) direct = body.get("schedule_cron");
        return direct != null ? direct.toString().trim() : null;
    }

    /**
     * 构造 runtime-task 调度任务描述：taskType=PIPELINE，parameters.definitionId 指向本定义。
     */
    private TaskDescription buildScheduleTaskDesc(PipelineDefinition def, String cron) {
        TaskDescription desc = new TaskDescription();
        desc.setTaskType("PIPELINE");
        desc.setTaskName("Pipeline-Schedule-" + def.getName());
        desc.setDescription("Scheduled pipeline: " + def.getName() + " (cron=" + cron + ")");
        Map<String, Object> params = new HashMap<>();
        params.put("definitionId", def.getId());
        desc.setParameters(params);
        desc.setAsync(true);
        return desc;
    }
}

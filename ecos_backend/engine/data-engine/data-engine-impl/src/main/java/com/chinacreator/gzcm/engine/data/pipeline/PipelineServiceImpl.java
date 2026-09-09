package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline 定义管理服务实现 — 基于 JdbcTemplate 持久化。
 * <p>
 * 强类型契约（XxxSaveDTO/XxxVO/XxxQuery），异常统一走 DataBridgeException 体系
 * （BusinessException/NotFoundException/ValidationException），日志分级，不打印敏感信息。
 * 定时调度走 runtime-task（TaskSchedulerService），遵循架构规则 2.3。
 * 写操作异步审计 + 敏感字段脱敏 + ABAC 裁决统一收敛 {@link PipelineSecurityService}（架构铁律 §2.4）。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 节点类型全集（与 PipelineExecutionService.executeNode switch 同源，架构铁律 §4.8.2） */
    private static final Set<String> VALID_NODE_TYPES = Set.of(
            "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_CDC",
            "TRANSFORM_SQL", "OUTPUT_OBJECT", "TRANSFORM_UDF", "JOIN", "SINK");

    /** 列表（摘要）场景下不返回 nodes */
    private final PipelineRepository repository;
    private final PipelineSecurityService securityService;

    /** runtime-task 调度服务（可选注入，无 bean 时跳过调度注册） */
    @Autowired(required = false)
    private TaskSchedulerService taskSchedulerService;

    public PipelineServiceImpl(PipelineRepository repository, PipelineSecurityService securityService) {
        this.repository = repository;
        this.securityService = securityService;
    }

    // ==================== CRUD ====================

    @Override
    @Transactional
    public PipelineDefinition createDefinition(PipelineSaveDTO dto) {
        // 参数校验 → ValidationException
        validateSaveDto(dto, true);

        PipelineDefinition def = new PipelineDefinition();
        def.setId(UUID.randomUUID().toString().replace("-", ""));
        def.setName(dto.getName());
        def.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        def.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : "DRAFT");
        def.setExtensions(dto.getExtensions());
        def.setScheduleCron(extractScheduleCron(dto));

        PipelineDefinition created = repository.insertDefinition(def);

        // 处理节点 + 推导 dependsOn（edges）
        saveNodesAndDependencies(created.getId(), dto);

        log.info("Created pipeline definition: {} (id={})", created.getName(), created.getId());

        // 处理定时调度：dto.schedule.cron 非空 → 走 runtime-task (TaskSchedulerService)
        registerSchedule(created, dto);

        // 异步审计（不阻塞）
        securityService.auditWrite("PIPELINE_CREATE", created.getId(), "system");

        return created;
    }

    @Override
    @Transactional
    public PipelineDefinition updateDefinition(String id, PipelineSaveDTO dto) {
        if (!repository.definitionExists(id)) {
            throw NotFoundException.entity("Pipeline 定义", id);
        }

        PipelineDefinition updated = repository.updateDefinition(
                id, dto.getName(), dto.getDescription(),
                dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : null);

        // 如果传入了 nodes，则替换节点列表
        if (dto.getNodes() != null) {
            repository.deleteNodesByDefinitionId(id);
            saveNodesAndDependencies(id, dto);
        }

        // 处理定时调度：若 dto 含 schedule，先取消旧 scheduleId 再注册新的
        if (dto.getSchedule() != null) {
            updateSchedule(id, dto);
        }

        log.info("Updated pipeline definition: id={}", id);
        securityService.auditWrite("PIPELINE_UPDATE", id, "system");
        return updated;
    }

    @Override
    @Transactional
    public void deleteDefinition(String id) {
        if (!repository.definitionExists(id)) {
            throw NotFoundException.entity("Pipeline 定义", id);
        }
        // 删除前取消已注册的 runtime-task 调度
        cancelExistingSchedule(id);
        repository.deleteNodesByDefinitionId(id);
        repository.deleteDefinition(id);
        log.info("Deleted pipeline definition (soft-delete → ARCHIVED): id={}", id);
        securityService.auditWrite("PIPELINE_DELETE", id, "system");
    }

    @Override
    public PipelineDefinition getDefinition(String id) {
        PipelineDefinition def = repository.findDefinitionById(id);
        if (def == null) {
            throw NotFoundException.entity("Pipeline 定义", id);
        }
        return def;
    }

    @Override
    public List<PipelineDefinition> listDefinitions() {
        return repository.findAllDefinitions();
    }

    @Override
    public PipelineExecutionPageVO listExecutions(String id, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.min(Math.max(pageSize, 1), 200);
        long total = repository.countExecutionsByDefinitionId(id);
        if (total == 0) {
            return PipelineExecutionPageVO.empty(p, ps);
        }
        List<PipelineExecution> items = repository.findExecutionsByDefinitionId(id, p, ps);
        PipelineExecutionPageVO vo = new PipelineExecutionPageVO();
        vo.setPage(p);
        vo.setPageSize(ps);
        vo.setTotal(total);
        vo.setItems(items.stream().map(this::toExecutionVO).collect(Collectors.toList()));
        return vo;
    }

    // ==================== ABAC 裁决（execute 前置） ====================

    /**
     * 执行前置 ABAC 裁决：security-engine 允许才放行，不可用默认 DENY。
     *
     * @param id Pipeline 定义 ID
     */
    public void checkAbacBeforeExecute(String id) {
        PipelineSecurityService.AllowedResult result = securityService.evaluateExecute(id);
        if (!result.allowed()) {
            throw new BusinessException(403, "Pipeline 执行被 ABAC 策略拒绝: " + result.reason());
        }
    }

    // ==================== 转换辅助 ====================

    /**
     * 定义实体 → VO（详情/创建/更新：含 nodes + edges；列表：仅摘要）。
     * 节点 config 敏感字段在序列化前脱敏（§4.3）。
     */
    @Override
    public PipelineVO toVO(PipelineDefinition def, boolean withNodes) {
        PipelineVO vo = new PipelineVO();
        vo.setId(def.getId());
        vo.setName(def.getName());
        vo.setDescription(def.getDescription());
        vo.setStatus(def.getStatus());
        vo.setExtensions(def.getExtensions());
        vo.setCreatedAt(toEpochMilli(def.getCreatedAt()));
        vo.setUpdatedAt(toEpochMilli(def.getUpdatedAt()));

        if (withNodes) {
            List<PipelineVO.NodeVO> nodeVOs = new ArrayList<>();
            for (PipelineNode node : repository.findNodesByDefinitionId(def.getId())) {
                PipelineVO.NodeVO nvo = new PipelineVO.NodeVO();
                nvo.setNodeId(node.getNodeId());
                nvo.setType(node.getType());
                nvo.setConfig(securityService.parseAndMaskConfig(node.getConfig()));
                nvo.setDependsOn(parseStringList(node.getDependsOn()));
                nvo.setPositionX(node.getPositionX());
                nvo.setPositionY(node.getPositionY());
                nodeVOs.add(nvo);
            }
            vo.setNodes(nodeVOs);
        }
        return vo;
    }

    /**
     * 执行记录实体 → VO。
     */
    @Override
    public PipelineExecutionVO toExecutionVO(PipelineExecution exec) {
        PipelineExecutionVO vo = new PipelineExecutionVO();
        vo.setId(exec.getId());
        vo.setDefinitionId(exec.getDefinitionId());
        vo.setStatus(exec.getStatus());
        vo.setStartedAt(toEpochMilli(exec.getStartedAt()));
        vo.setCompletedAt(toEpochMilli(exec.getCompletedAt()));
        vo.setErrorMessage(exec.getErrorMessage());
        vo.setRowsProcessed(exec.getRowsProcessed());
        return vo;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse depends_on json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static Long toEpochMilli(java.time.LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ==================== 节点持久化 ====================

    /**
     * 保存节点并根据 edges 推导 dependsOn 回写。
     */
    private void saveNodesAndDependencies(String definitionId, PipelineSaveDTO dto) {
        List<PipelineSaveDTO.NodeSpec> nodes = dto.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (PipelineSaveDTO.NodeSpec nodeSpec : nodes) {
            PipelineNode node = new PipelineNode();
            node.setId(UUID.randomUUID().toString().replace("-", ""));
            node.setDefinitionId(definitionId);
            node.setNodeId(resolveNodeId(nodeSpec));
            node.setType(nodeSpec.getType() != null && !nodeSpec.getType().isBlank() ? nodeSpec.getType() : "TRANSFORM_SQL");
            node.setConfig(toJson(nodeSpec.getConfig()));
            node.setPositionX(nodeSpec.getPositionX() != null ? nodeSpec.getPositionX() : 0);
            node.setPositionY(nodeSpec.getPositionY() != null ? nodeSpec.getPositionY() : 0);
            repository.insertNode(node);
        }

        // 根据 edges 计算每个节点的 depends_on 并回写
        List<PipelineSaveDTO.EdgeSpec> edges = dto.getEdges();
        if (edges != null && !edges.isEmpty()) {
            // 构建 nodeId → [依赖它的节点列表]（to 依赖 from）
            Map<String, List<String>> deps = new LinkedHashMap<>();
            for (PipelineSaveDTO.EdgeSpec edge : edges) {
                deps.computeIfAbsent(edge.getTo(), k -> new ArrayList<>()).add(edge.getFrom());
            }
            for (PipelineSaveDTO.NodeSpec nodeSpec : nodes) {
                String nodeId = resolveNodeId(nodeSpec);
                List<String> dependsOn = deps.getOrDefault(nodeId, Collections.emptyList());
                try {
                    String json = MAPPER.writeValueAsString(dependsOn);
                    repository.updateNodeDependsOn(definitionId, nodeId, json);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to set depends_on for node {}: {}", nodeId, e.getMessage());
                }
            }
        }
    }

    private String resolveNodeId(PipelineSaveDTO.NodeSpec nodeSpec) {
        String nodeId = nodeSpec.getNodeId();
        if (nodeId == null || nodeId.isBlank()) {
            nodeId = nodeSpec.getId();
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new ValidationException("nodeId", "节点必须指定 nodeId 或 id");
        }
        return nodeId;
    }

    private String toJson(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize node config: {}", e.getMessage());
            return "{}";
        }
    }

    // ==================== 参数校验 ====================

    private void validateSaveDto(PipelineSaveDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new ValidationException("body", "请求体不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ValidationException("name", "Pipeline 名称不能为空");
        }
        // 创建场景至少 1 个节点
        if (isCreate && (dto.getNodes() == null || dto.getNodes().isEmpty())) {
            throw new ValidationException("nodes", "至少需要 1 个节点");
        }
        // 节点类型与唯一性校验
        if (dto.getNodes() != null) {
            Set<String> seenNodeIds = new HashSet<>();
            for (PipelineSaveDTO.NodeSpec node : dto.getNodes()) {
                if (node.getType() == null || !VALID_NODE_TYPES.contains(node.getType())) {
                    throw new ValidationException("type", "非法节点类型: " + node.getType()
                            + "（合法值: " + String.join("/", VALID_NODE_TYPES) + "）");
                }
                String nodeId = node.getNodeId() != null && !node.getNodeId().isBlank()
                        ? node.getNodeId() : node.getId();
                if (nodeId == null || nodeId.isBlank()) {
                    throw new ValidationException("nodeId", "节点必须指定 nodeId");
                }
                if (!seenNodeIds.add(nodeId)) {
                    throw new ValidationException("nodeId", "节点 id 重复: " + nodeId);
                }
            }
        }
    }

    private String extractScheduleCron(PipelineSaveDTO dto) {
        if (dto.getSchedule() != null && dto.getSchedule().getCron() != null) {
            return dto.getSchedule().getCron().trim();
        }
        return null;
    }

    // ==================== 定时调度（runtime-task 接入，架构规则 2.3）====================

    /**
     * 注册定时调度：从 dto.schedule.cron 读取 cron 表达式，若非空则通过
     * TaskSchedulerService.scheduleTask(desc, cron) 注册到 runtime-task，
     * 并将 scheduleId 持久化到 definition JSONB（repository.updateDefinitionSchedule）。
     */
    private void registerSchedule(PipelineDefinition def, PipelineSaveDTO dto) {
        if (taskSchedulerService == null) {
            log.debug("TaskSchedulerService unavailable, skip schedule registration: {}", def.getId());
            return;
        }
        String cron = extractScheduleCron(dto);
        if (cron == null || cron.isEmpty()) {
            return;
        }
        try {
            TaskDescription desc = buildScheduleTaskDesc(def, cron);
            String scheduleId = taskSchedulerService.scheduleTask(desc, cron);
            Map<String, Object> extensions = def.getExtensions() != null
                    ? new LinkedHashMap<>(def.getExtensions()) : new LinkedHashMap<>();
            extensions.put("scheduleId", scheduleId);
            def.setScheduleCron(cron);
            def.setExtensions(extensions);
            repository.updateDefinitionSchedule(def.getId(), cron, scheduleId);
            log.info("Pipeline schedule registered: definitionId={}, cron={}, scheduleId={}",
                    def.getId(), cron, scheduleId);
        } catch (Exception e) {
            log.warn("Failed to register pipeline schedule: definitionId={}, error={}",
                    def.getId(), e.getMessage());
        }
    }

    /**
     * 更新定时调度：先取消旧的 scheduleId（若存在），再按 dto.schedule.cron 注册新的。
     * dto.schedule.cron 为空字符串时仅取消旧调度（取消调度不重新注册）。
     */
    private void updateSchedule(String definitionId, PipelineSaveDTO dto) {
        // 取消旧调度
        cancelExistingSchedule(definitionId);

        if (taskSchedulerService == null) {
            log.debug("TaskSchedulerService unavailable, skip schedule update: {}", definitionId);
            return;
        }
        String cron = extractScheduleCron(dto);
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

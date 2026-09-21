package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景沙盘布局服务 — React Flow 画布序列化落库（PMO-60 v2.0 P1）。
 *
 * <p>对应表 {@code ecos_scenario_sandbox_layout}（DDL V150）。
 * 乐观锁：{@code layout_version} 冲突时抛 {@code BusinessException(409, "version_conflict")}。</p>
 */
@Service
public class ScenarioSandboxLayoutService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSandboxLayoutService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ScenarioSandboxLayoutService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取场景沙盘布局。若无记录则返回默认布局（1 hub + 6 resource 节点，layoutVersion=0）。
     *
     * @param scenarioId 场景 id
     * @return 布局 VO（可能是默认布局）
     */
    public SandboxLayoutVO getLayout(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "SBX-400: scenarioId 必填");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT scenario_id, layout_jsonb, layout_version, update_time " +
            "FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0", scenarioId);
        if (rows.isEmpty()) {
            // 返回默认布局
            return buildDefaultLayout(scenarioId);
        }
        Map<String, Object> row = rows.get(0);
        SandboxLayoutVO vo = new SandboxLayoutVO();
        vo.setScenarioId(String.valueOf(row.get("scenario_id")));
        vo.setLayout(parseJsonb(String.valueOf(row.get("layout_jsonb"))));
        vo.setLayoutVersion(row.get("layout_version") instanceof Number n ? n.intValue() : 1);
        Object updateTime = row.get("update_time");
        vo.setUpdateTime(updateTime != null ? updateTime.toString() : null);
        return vo;
    }

    /**
     * 保存场景沙盘布局（乐观锁）。
     *
     * @param scenarioId       场景 id
     * @param dto              布局保存入参（nodes/edges/viewport + expectedVersion）
     * @return 保存后的布局 VO（新版本号）
     * @throws BusinessException 409 version_conflict 当 expectedVersion 与当前版本不匹配
     */
    @Transactional
    public SandboxLayoutVO saveLayout(String scenarioId, SandboxSaveDTO dto) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "SBX-400: scenarioId 必填");
        }
        if (dto == null) {
            throw new BusinessException(400, "SBX-400: 保存体不可为空");
        }
        String operator = currentOperator();
        int expected = dto.getExpectedVersion() != null ? dto.getExpectedVersion() : 0;

        // 组装 layout_jsonb
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("nodes", dto.getNodes() != null ? dto.getNodes() : List.of());
        layout.put("edges", dto.getEdges() != null ? dto.getEdges() : List.of());
        layout.put("viewport", dto.getViewport() != null ? dto.getViewport() : new LinkedHashMap<>());
        String layoutJson;
        try {
            layoutJson = objectMapper.writeValueAsString(layout);
        } catch (Exception e) {
            throw new BusinessException(500, "SBX-500: 布局 JSON 序列化失败: " + e.getMessage());
        }

        // 乐观锁：UPDATE 现有记录（version 匹配）或 INSERT 新记录（无现有）
        int updated = jdbc.update(
            "UPDATE ecos_scenario_sandbox_layout SET layout_jsonb=?::jsonb, " +
            "layout_version=layout_version+1, update_time=NOW(), update_by=? " +
            "WHERE scenario_id=? AND is_deleted=0 AND layout_version=?",
            layoutJson, operator, scenarioId, expected);

        if (updated == 0) {
            // 可能是首次保存（无记录）→ 尝试 INSERT
            boolean exists = jdbc.queryForList(
                "SELECT 1 FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0",
                Integer.class, scenarioId).stream().findFirst().isPresent();
            if (exists) {
                // 版本冲突
                Integer currentVersion = jdbc.queryForList(
                    "SELECT layout_version FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0",
                    Integer.class, scenarioId).stream().findFirst().orElse(1);
                throw new BusinessException(409, "version_conflict: expected=" + expected + " current=" + currentVersion);
            }
            // 首次保存 → INSERT
            if (expected != 0) {
                throw new BusinessException(409, "version_conflict: expected=" + expected + " but no record exists");
            }
            try {
                jdbc.update(
                    "INSERT INTO ecos_scenario_sandbox_layout (scenario_id, layout_jsonb, layout_version, create_by, update_by, is_deleted) " +
                    "VALUES (?, ?::jsonb, 1, ?, ?, 0)",
                    scenarioId, layoutJson, operator, operator);
            } catch (DataIntegrityViolationException e) {
                log.warn("saveLayout INSERT 唯一约束冲突: scenario={}", scenarioId);
                throw new BusinessException(409, "version_conflict: 并发首次保存冲突");
            }
        }

        log.info("保存沙盘布局 scenario={} expectedVersion={} by={}", scenarioId, expected, operator);
        return getLayout(scenarioId);
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** 构建默认布局：1 hub 节点 + 6 resource 节点散开。 */
    private SandboxLayoutVO buildDefaultLayout(String scenarioId) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        // hub 节点（中心）
        nodes.add(buildNode("hub", "场景中心", 400, 300));
        // 6 类 resource 节点（环形分布）
        String[] types = {"dataset", "object", "knowledge", "agent", "security", "interface"};
        String[] labels = {"数据集", "本体实体", "知识资产", "AI Agent", "安全策略", "接口引用"};
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60.0 * i - 90.0); // 从顶部开始
            double x = 400 + 250 * Math.cos(angle);
            double y = 300 + 250 * Math.sin(angle);
            nodes.add(buildNode(types[i] + "_node", labels[i], (int) x, (int) y));
        }
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            edges.add(Map.of("id", "e_hub_" + i, "source", "hub", "target", types[i] + "_node"));
        }
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("nodes", nodes);
        layout.put("edges", edges);
        layout.put("viewport", Map.of("x", 0, "y", 0, "zoom", 1.0));

        SandboxLayoutVO vo = new SandboxLayoutVO();
        vo.setScenarioId(scenarioId);
        vo.setLayout(layout);
        vo.setLayoutVersion(0);
        vo.setUpdateTime(null);
        return vo;
    }

    private Map<String, Object> buildNode(String id, String label, int x, int y) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("position", Map.of("x", x, "y", y));
        node.put("data", Map.of("label", label));
        return node;
    }

    private Object parseJsonb(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            log.warn("沙盘 layout JSONB 解析失败，返 raw: {}", e.getMessage());
            return json;
        }
    }

    private String currentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("无法从 SecurityContext 取操作人，回退 system: {}", e.getMessage());
        }
        return "system";
    }
}

package com.chinacreator.gzcm.workspace.config;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景配置服务（模板/算法库，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>只承载元数据：模板是「新建场景的预填包」；算法库登记执行承载与版本，不实现算法。
 * 写操作发治理审计；删除为逻辑删除。</p>
 */
@Service
public class ScenarioConfigService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioConfigService.class);

    private final WorkspaceDataDao dao;
    private final WorkspaceSecurityAudit audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ScenarioConfigService(WorkspaceDataDao dao, WorkspaceSecurityAudit audit) {
        this.dao = dao;
        this.audit = audit;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    public List<Map<String, Object>> listTemplates() {
        return dao.queryForList(
            "SELECT id, name, category, description, definition_defaults, default_analysis, " +
            "status, version_no, create_time, update_time FROM ecos_scenario_template WHERE is_deleted = 0 ORDER BY name");
    }

    public Map<String, Object> getTemplate(String id) {
        List<Map<String, Object>> rows = dao.queryForList(
            "SELECT id, name, category, description, definition_defaults, default_analysis, " +
            "status, version_no, create_time, update_time FROM ecos_scenario_template WHERE id = ? AND is_deleted = 0",
            id);
        if (rows.isEmpty()) {
            throw new NotFoundException("TPL-404: 模板不存在: " + id);
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> saveTemplate(Map<String, Object> dto, String operator) {
        String id = str(dto.get("id"));
        if (id == null) id = "tpl_" + UUID.randomUUID().toString().substring(0, 12);
        String name = require(dto, "name");
        String category = upper(str(dto.get("category")), "GENERAL");
        String description = str(dto.get("description"));
        String defaults = jsonOrNull(dto.get("definitionDefaults"));
        String analyses = jsonOrNull(dto.get("defaultAnalysis"));
        String status = upper(str(dto.get("status")), "ACTIVE");
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException(400, "TPL-400: status 必为 ACTIVE|DISABLED");
        }
        List<Map<String, Object>> old = dao.queryForList(
            "SELECT id FROM ecos_scenario_template WHERE id = ? AND is_deleted = 0", id);
        if (old.isEmpty()) {
            dao.insertScenarioTemplate(id, name, category, description, defaults, analyses, status, operator);
            audit.record("scenario_template_create", id, Map.of("name", name, "status", status));
        } else {
            dao.updateScenarioTemplate(id, name, category, description, defaults, analyses, status, operator);
            audit.record("scenario_template_update", id, Map.of("name", name, "status", status));
        }
        return getTemplate(id);
    }

    @Transactional
    public void deleteTemplate(String id, String operator) {
        int n = dao.deleteScenarioTemplate(id, operator);
        if (n == 0) {
            throw new NotFoundException("TPL-404: 模板不存在: " + id);
        }
        audit.record("scenario_template_delete", id, Map.of("operator", operator));
    }

    public List<Map<String, Object>> listAlgorithms() {
        return dao.queryForList(
            "SELECT id, name, analysis_type, version, provider_type, implementation_ref, params_schema, output_schema, " +
            "status, version_no, create_time, update_time FROM ecos_algorithm_registry WHERE is_deleted = 0 " +
            "ORDER BY analysis_type, name, version");
    }

    public Map<String, Object> getAlgorithm(String id) {
        List<Map<String, Object>> rows = dao.queryForList(
            "SELECT id, name, analysis_type, version, provider_type, implementation_ref, params_schema, output_schema, " +
            "status, version_no, create_time, update_time FROM ecos_algorithm_registry WHERE id = ? AND is_deleted = 0",
            id);
        if (rows.isEmpty()) {
            throw new NotFoundException("ALG-404: 算法不存在: " + id);
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> saveAlgorithm(Map<String, Object> dto, String operator) {
        String id = str(dto.get("id"));
        if (id == null) id = "alg_" + UUID.randomUUID().toString().substring(0, 12);
        String name = require(dto, "name");
        String analysisType = upper(require(dto, "analysisType"), null);
        String version = str(dto.get("version"));
        if (version == null) version = "1";
        String providerType = upper(str(dto.get("providerType")), "DETERMINISTIC_SPI");
        String implRef = require(dto, "implementationRef");
        String params = jsonOrNull(dto.get("paramsSchema"));
        String output = jsonOrNull(dto.get("outputSchema"));
        String status = upper(str(dto.get("status")), "ACTIVE");
        if (!List.of("ACTIVE", "DISABLED", "DEPRECATED").contains(status)) {
            throw new BusinessException(400, "ALG-400: status 必为 ACTIVE|DISABLED|DEPRECATED");
        }
        List<Map<String, Object>> old = dao.queryForList(
            "SELECT id FROM ecos_algorithm_registry WHERE id = ? AND is_deleted = 0", id);
        if (old.isEmpty()) {
            dao.insertScenarioAlgorithm(id, name, analysisType, version, providerType, implRef, params, output, status, operator);
            audit.record("scenario_algorithm_create", id, Map.of("name", name, "analysisType", analysisType, "status", status));
        } else {
            dao.updateScenarioAlgorithm(id, name, analysisType, version, providerType, implRef, params, output, status, operator);
            audit.record("scenario_algorithm_update", id, Map.of("name", name, "status", status));
        }
        return getAlgorithm(id);
    }

    @Transactional
    public void deleteAlgorithm(String id, String operator) {
        int n = dao.deleteScenarioAlgorithm(id, operator);
        if (n == 0) {
            throw new NotFoundException("ALG-404: 算法不存在: " + id);
        }
        audit.record("scenario_algorithm_delete", id, Map.of("operator", operator));
    }

    private String require(Map<String, Object> dto, String key) {
        String v = str(dto.get(key));
        if (v == null) {
            throw new BusinessException(400, "ALG/TPL-400: 缺少必填项: " + key);
        }
        return v;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String upper(String v, String fallback) {
        return v == null ? fallback : v.toUpperCase();
    }

    private String jsonOrNull(Object v) {
        if (v == null) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            throw new BusinessException(400, "ALG/TPL-400: JSON 序列化失败: " + e.getMessage());
        }
    }
}

package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 认知框架注册表 DAO（详细设计-认知引擎 v1.0 §5/§9.1；V311）。
 *
 * <p>SQL 落点单源：所有框架读写收口本类（IR06），表 = {@code ecos_cognitive.ecos_cognitive_framework}。
 * 版本不可变守卫在写 SQL 上：发布/停用 UPDATE 均带状态条件，0 行 = 调用侧显式 409。</p>
 */
@Repository
public class CognitiveFrameworkDao extends JdbcTemplateDaoSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> STEP_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    /** 列表（可选 problemType/domain/status 过滤；确定性排序）。 */
    public List<CognitiveFrameworkDefinition> list(String problemType, String domain, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ecos_cognitive.ecos_cognitive_framework WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (problemType != null && !problemType.isBlank()) {
            sql.append(" AND applicability_json -> 'problemTypes' ? ?");
            args.add(problemType.trim());
        }
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND applicability_json -> 'domains' ? ?");
            args.add(domain.trim());
        }
        sql.append(" ORDER BY framework_id, version");
        return toDefinitions(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    public Optional<CognitiveFrameworkDefinition> find(String frameworkId, String version) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ecos_cognitive.ecos_cognitive_framework "
                        + "WHERE framework_id = ? AND version = ? AND is_deleted = 0",
                frameworkId, version);
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDefinition(rows.get(0)));
    }

    public boolean exists(String frameworkId, String version) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ecos_cognitive.ecos_cognitive_framework "
                        + "WHERE framework_id = ? AND version = ? AND is_deleted = 0",
                Integer.class, frameworkId, version);
        return n != null && n > 0;
    }

    public void insert(CognitiveFrameworkDefinition d, String contentJson) {
        jdbcTemplate.update(
                "INSERT INTO ecos_cognitive.ecos_cognitive_framework ("
                        + "id, framework_id, version, name, category, purpose, status, "
                        + "applicability_json, preconditions_json, input_schema_ref, output_schema_ref, "
                        + "steps_json, required_capabilities_json, validation_profile_ref, budget_policy_ref, "
                        + "stop_conditions_json, governance_json, contract_resolvable, content_json, content_hash, "
                        + "create_by, update_by) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,CAST(? AS jsonb),?,?,?)",
                java.util.UUID.randomUUID().toString(), d.getFrameworkId(), d.getVersion(), d.getName(),
                d.getCategory(), d.getPurpose(), d.getStatus(),
                json(Map.of("problemTypes", nullSafe(d.getProblemTypes()), "domains", nullSafe(d.getDomains()))),
                json(nullSafe(d.getPreconditions())), d.getInputSchemaRef(), d.getOutputSchemaRef(),
                json(d.getSteps() == null ? List.of() : d.getSteps()),
                json(nullSafe(d.getRequiredCapabilities())), d.getValidationProfileRef(), d.getBudgetPolicyRef(),
                json(nullSafe(d.getStopConditions())),
                json(d.getGovernance() == null ? Map.of() : d.getGovernance()),
                d.isContractResolvable() ? 1 : 0, contentJson, d.getContentHash(),
                d.getCreatedBy(), d.getCreatedBy());
    }

    /** 发布：仅 DRAFT/VALIDATING 可发布；返回受影响行数（0 ⇒ 调用侧 409）。 */
    public int publish(String frameworkId, String version, String actor) {
        return jdbcTemplate.update(
                "UPDATE ecos_cognitive.ecos_cognitive_framework SET status = 'PUBLISHED', "
                        + "publish_time = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP, update_by = ? "
                        + "WHERE framework_id = ? AND version = ? AND is_deleted = 0 "
                        + "AND status IN ('DRAFT','VALIDATING')",
                actor, frameworkId, version);
    }

    /** 治理状态迁移：仅 PUBLISHED/DEPRECATED 可作为源；返回受影响行数。 */
    public int updateStatus(String frameworkId, String version, String target, String actor) {
        return jdbcTemplate.update(
                "UPDATE ecos_cognitive.ecos_cognitive_framework SET status = ?, "
                        + "update_time = CURRENT_TIMESTAMP, update_by = ? "
                        + "WHERE framework_id = ? AND version = ? AND is_deleted = 0 "
                        + "AND status IN ('DRAFT','VALIDATING','PUBLISHED','DEPRECATED')",
                target, actor, frameworkId, version);
    }

    public List<CognitiveFrameworkDefinition> listByStatus(String status) {
        return list(null, null, status);
    }

    private List<CognitiveFrameworkDefinition> toDefinitions(List<Map<String, Object>> rows) {
        List<CognitiveFrameworkDefinition> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(toDefinition(row));
        }
        return out;
    }

    private CognitiveFrameworkDefinition toDefinition(Map<String, Object> row) {
        CognitiveFrameworkDefinition d = new CognitiveFrameworkDefinition();
        d.setFrameworkId(str(row.get("framework_id")));
        d.setVersion(str(row.get("version")));
        d.setName(str(row.get("name")));
        d.setCategory(str(row.get("category")));
        d.setPurpose(str(row.get("purpose")));
        d.setStatus(str(row.get("status")));
        Map<String, Object> applicability = readMap(row.get("applicability_json"));
        d.setProblemTypes(readStringList(applicability.get("problemTypes")));
        d.setDomains(readStringList(applicability.get("domains")));
        d.setPreconditions(readStringList(read(row.get("preconditions_json"))));
        d.setInputSchemaRef(str(row.get("input_schema_ref")));
        d.setOutputSchemaRef(str(row.get("output_schema_ref")));
        d.setSteps(readSteps(row.get("steps_json")));
        Map<String, Object> content = readMap(row.get("content_json"));
        d.setMethods(readSteps(content.get("methods")));
        d.setRequiredCapabilities(readStringList(read(row.get("required_capabilities_json"))));
        d.setValidationProfileRef(str(row.get("validation_profile_ref")));
        d.setBudgetPolicyRef(str(row.get("budget_policy_ref")));
        d.setStopConditions(readStringList(read(row.get("stop_conditions_json"))));
        d.setGovernance(readMap(row.get("governance_json")));
        d.setContractResolvable(toInt(row.get("contract_resolvable")) == 1);
        d.setContentHash(str(row.get("content_hash")));
        d.setCreatedBy(str(row.get("create_by")));
        d.setCreatedAt(str(row.get("create_time")));
        d.setPublishedBy(str(row.get("update_by")));
        d.setPublishedAt(str(row.get("publish_time")));
        return d;
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<String> nullSafe(List<String> in) {
        return in == null ? List.of() : in;
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("framework json serialize failed", e);
        }
    }

    private static Object read(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MAPPER.readValue(String.valueOf(raw), Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> readMap(Object raw) {
        Object v = read(raw);
        return v instanceof Map ? castMap(v) : new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> readSteps(Object raw) {
        Object v = read(raw);
        if (v instanceof List) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : (List<?>) v) {
                if (item instanceof Map) {
                    out.add(castMap(item));
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> readStringList(Object v) {
        Object raw = v instanceof String s ? read(s) : v;
        if (raw instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}

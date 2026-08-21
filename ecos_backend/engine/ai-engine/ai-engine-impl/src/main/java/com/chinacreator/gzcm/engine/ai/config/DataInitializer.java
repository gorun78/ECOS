package com.chinacreator.gzcm.engine.ai.config;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.CronJobService;
import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.engine.ai.repository.CronJobExecutionRepository;
import com.chinacreator.gzcm.engine.ai.repository.CronJobRepository;
import com.chinacreator.gzcm.engine.ai.repository.SkillRepository;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 种子数据初始化器 — 应用启动时确保表结构存在并预置种子数据。
 * <p>
 * 预置内容：
 * <ul>
 *   <li>5 个角色 + 22 条权限：admin, data-manager, ontology-designer,
 *       knowledge-engineer, analyst</li>
 *   <li>2 个 CronJob："每日数据质量巡检"、"每周认知诊断报告"</li>
 *   <li>3 个 Skill："数据治理"、"企业经营诊断"、"政务一件事"</li>
 *   <li>6 个内置 Agent：data-engine, ontology-engine, cognitive-engine,
 *       security-engine, kb-engine, ai-engine</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // 六大内置 Agent 的模板数据
    private static final List<Map<String, Object>> BUILTIN_AGENTS = List.of(
        // 1. 数据引擎 Agent
        Map.of(
            "id", "builtin-data-agent",
            "name", "数据引擎Agent",
            "description", "ECOS 内置数据引擎 Agent，负责数据查询、表结构探查、数据血缘追踪和数据质量检查。",
            "toolWhitelist", List.of("query_db", "list_tables", "get_table_schema", "get_lineage", "run_dq_check"),
            "systemPrompt", "你是 ECOS 数据引擎的智能助手，专门负责数据治理相关操作。你能够查询数据库、列出表结构、获取数据血缘关系以及执行数据质量检查。请在回答时保持严谨、精确，引用具体的表名和字段名。"
        ),
        // 2. 本体引擎 Agent
        Map.of(
            "id", "builtin-ontology-agent",
            "name", "本体引擎Agent",
            "description", "ECOS 内置本体引擎 Agent，负责领域管理、对象类型查询、对象检索和关系链接。",
            "toolWhitelist", List.of("list_domains", "get_object_type", "query_objects", "get_links", "get_functions", "search"),
            "systemPrompt", "你是 ECOS 本体引擎的智能助手，专门负责企业本体建模和信息组织。你能够列出知识领域、查询对象类型定义、检索具体业务对象、浏览对象间关联关系。"
        ),
        // 3. 认知引擎 Agent
        Map.of(
            "id", "builtin-cognitive-agent",
            "name", "认知引擎Agent",
            "description", "ECOS 内置认知引擎 Agent，负责 RAG 检索、知识图谱查询、诊断分析和因果链追踪。",
            "toolWhitelist", List.of("rag_search", "kg_query", "diagnose", "scenario_analysis", "get_goals", "get_causal_chain"),
            "systemPrompt", "你是 ECOS 认知引擎的智能助手，具备企业级认知推理能力。你能够进行 RAG 增强检索、知识图谱结构化查询、业务诊断分析、情景模拟推理、目标体系查询以及因果链追踪。"
        ),
        // 4. 安全引擎 Agent
        Map.of(
            "id", "builtin-security-agent",
            "name", "安全引擎Agent",
            "description", "ECOS 内置安全引擎 Agent，负责权限校验、用户权限查询和数据分级分类。",
            "toolWhitelist", List.of("check_permission", "my_permissions", "data_class"),
            "systemPrompt", "你是 ECOS 安全引擎的智能助手，专注于权限管理和数据安全。你能够校验用户对特定资源的访问权限、查询当前用户拥有的所有权限列表，以及获取数据的分类分级信息。"
        ),
        // 5. 知识库 Agent
        Map.of(
            "id", "builtin-kb-agent",
            "name", "知识库Agent",
            "description", "ECOS 内置知识库 Agent，负责文件搜索、文件读写、知识抽取和文档补丁。",
            "toolWhitelist", List.of("search_files", "read_file", "write_file", "patch", "knowledge_extract"),
            "systemPrompt", "你是 ECOS 知识库的智能助手，负责企业知识文档的管理和维护。你能够搜索文件系统中的文档、读取文件内容、创建/更新文件、应用文本补丁，以及从非结构化文本中抽取结构化知识。"
        ),
        // 6. AI 编排 Agent
        Map.of(
            "id", "builtin-ai-agent",
            "name", "AI编排Agent",
            "description", "ECOS 内置 AI 编排 Agent，负责多 Agent 委托调度和 Agent 元信息查询。",
            "toolWhitelist", List.of("delegate_to_agent"),
            "systemPrompt", "你是 ECOS AI 引擎的编排助手，负责协调多个子 Agent 完成复杂任务。你能够将用户任务拆解为子任务并委托给合适的 Agent 执行，也能够查询所有已注册 Agent 的元信息以辅助决策。"
        )
    );

    private final CronJobRepository cronJobRepository;
    private final CronJobExecutionRepository executionRepository;
    private final SkillRepository skillRepository;
    private final CronJobService cronJobService;
    private final SkillService skillService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    public DataInitializer(CronJobRepository cronJobRepository,
                           CronJobExecutionRepository executionRepository,
                           SkillRepository skillRepository,
                           CronJobService cronJobService,
                           SkillService skillService,
                           JdbcTemplate jdbcTemplate) {
        this.cronJobRepository = cronJobRepository;
        this.executionRepository = executionRepository;
        this.skillRepository = skillRepository;
        this.cronJobService = cronJobService;
        this.skillService = skillService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureTables();
        seedRoles();
        seedCronJobs();
        seedSkills();
        seedBuiltinAgents();
    }

    private void ensureTables() {
        cronJobRepository.ensureTable();
        executionRepository.ensureTable();
        skillRepository.ensureTable();
    }

    private void seedCronJobs() {
        long count = cronJobRepository.count();
        if (count > 0) {
            log.info("CronJob table already has {} records, skip seeding", count);
            return;
        }

        // 种子数据 1: 每日数据质量巡检
        Map<String, Object> job1 = Map.of(
            "name", "每日数据质量巡检",
            "cronExpression", "0 0 8 * * ?",
            "description", "每天早8点自动扫描数据质量指标，检查空值率、重复率、异常值，生成数据质量日报",
            "enabled", true,
            "createdBy", "system"
        );
        CronJobEntity entity1 = cronJobService.createCronJob(job1);
        log.info("Seed CronJob created: id={} name={}", entity1.getId(), entity1.getName());

        // 种子数据 2: 每周认知诊断报告
        Map<String, Object> job2 = Map.of(
            "name", "每周认知诊断报告",
            "cronExpression", "0 0 9 * * 1",
            "description", "每周一早9点生成企业认知健康诊断报告，涵盖知识图谱更新、Agent运行效率、数据治理评分",
            "enabled", true,
            "createdBy", "system"
        );
        CronJobEntity entity2 = cronJobService.createCronJob(job2);
        log.info("Seed CronJob created: id={} name={}", entity2.getId(), entity2.getName());

        log.info("Seeded {} CronJobs", 2);
    }

    private void seedSkills() {
        long count = skillRepository.count();
        if (count > 0) {
            log.info("Skill table already has {} records, skip seeding", count);
            return;
        }

        // 种子数据 1: 数据治理
        Map<String, Object> skill1 = Map.of(
            "name", "数据治理",
            "description", "提供数据标准管理、数据质量规则配置、数据血缘分析、元数据管理等功能模块",
            "version", "1.0.0",
            "enabled", true,
            "category", "data-governance",
            "packageInfo", "{\"tools\":[\"data-quality-check\",\"metadata-scan\",\"lineage-trace\"],\"prompts\":[\"data-governance-planning\",\"data-quality-report\"],\"model\":\"deepseek-v4-pro\"}",
            "createdBy", "system"
        );
        skillService.createSkill(skill1);

        // 种子数据 2: 企业经营诊断
        Map<String, Object> skill2 = Map.of(
            "name", "企业经营诊断",
            "description", "基于企业运营数据进行经营健康度诊断，包括财务报表分析、业务增长趋势、风险预警等功能",
            "version", "1.0.0",
            "enabled", true,
            "category", "business-diagnosis",
            "packageInfo", "{\"tools\":[\"financial-analysis\",\"trend-prediction\",\"risk-assessment\"],\"prompts\":[\"business-health-check\",\"executive-summary\"],\"model\":\"deepseek-v4-pro\"}",
            "createdBy", "system"
        );
        skillService.createSkill(skill2);

        // 种子数据 3: 政务一件事
        Map<String, Object> skill3 = Map.of(
            "name", "政务一件事",
            "description", "支持政务服务'一件事'集成办理场景，涵盖材料预审、流程导航、多部门协同等功能",
            "version", "1.0.0",
            "enabled", true,
            "category", "government-service",
            "packageInfo", "{\"tools\":[\"document-review\",\"workflow-navigation\",\"multi-dept-coordination\"],\"prompts\":[\"service-guidance\",\"approval-chain\"],\"model\":\"deepseek-v4-pro\"}",
            "createdBy", "system"
        );
        skillService.createSkill(skill3);

        log.info("Seeded {} Skills", 3);
    }

    private void seedRoles() {
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM td_role", Long.class);
            if (count != null && count > 0) {
                log.info("td_role table already has {} records, skip seeding", count);
                return;
            }
        } catch (Exception e) {
            log.warn("Failed to check td_role count, proceeding with seed: {}", e.getMessage());
        }

        String now = LocalDateTime.now().toString().substring(0, 19);

        // ── 5 个角色 ──────────────────────────────────────────
        Object[][] roles = {
            {"role_admin",              "系统管理员",  "ADMIN",              "系统最高权限角色"},
            {"role_data_manager",       "数据管理员",  "DATA_MANAGER",       "数据源、管道、质量规则、血缘与数据目录管理"},
            {"role_ontology_designer",  "本体建模师",  "ONTOLOGY_DESIGNER",  "本体、版本、函数全生命周期管理"},
            {"role_knowledge_engineer", "知识工程师",  "KNOWLEDGE_ENGINEER", "知识图谱、规则、分类、知识抽取管理"},
            {"role_analyst",            "业务分析师",  "ANALYST",            "只读分析角色，含AI问答、诊断、场景分析"}
        };

        for (Object[] r : roles) {
            jdbcTemplate.update(
                "INSERT INTO td_role (ROLE_ID, ROLE_NAME, ROLE_CODE, DESCRIPTION, STATUS, CREATED_TIME, CREATED_BY) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?::timestamp, 'system')",
                r[0], r[1], r[2], r[3], now);
        }
        log.info("Seeded 5 roles into td_role");

        // ── 权限定义（去重） ──────────────────────────────────
        Object[][] perms = {
            // admin — 全局通配
            {"perm_global_all",         "全局所有权限",   "*:*",       "*",       "*",     "超级管理员的全局通配权限"},

            // data-manager
            {"perm_datasource_all",     "数据源全权限",   "data-source:*",    "data-source",    "*",    "数据源管理全权限"},
            {"perm_pipeline_all",       "管道全权限",     "pipeline:*",       "pipeline",       "*",    "管道管理全权限"},
            {"perm_dqrule_all",         "质量规则全权限",  "dq-rule:*",        "dq-rule",        "*",    "数据质量规则管理全权限"},
            {"perm_lineage_read",       "血缘只读",       "lineage:read",     "lineage",         "read", "数据血缘只读权限"},
            {"perm_datacatalog_read",   "数据目录只读",    "data-catalog:read", "data-catalog",  "read", "数据目录只读权限"},

            // ontology-designer
            {"perm_ontology_all",       "本体全权限",     "ontology:*",          "ontology",          "*", "本体模型全权限"},
            {"perm_ontologyver_all",    "本体版本全权限",  "ontology-version:*",  "ontology-version",  "*", "本体版本管理全权限"},
            {"perm_ontologyfunc_all",   "本体函数全权限",  "ontology-function:*", "ontology-function", "*", "本体函数管理全权限"},

            // knowledge-engineer
            {"perm_kg_all",             "知识图谱全权限",  "kg:*",                "kg",                "*", "知识图谱全权限"},
            {"perm_rule_all",           "规则全权限",      "rule:*",              "rule",              "*", "规则管理全权限"},
            {"perm_classification_all", "分类全权限",      "classification:*",    "classification",    "*", "分类管理全权限"},
            {"perm_kextract_all",       "知识抽取全权限",  "knowledge-extract:*", "knowledge-extract", "*", "知识抽取全权限"},
            {"perm_rag_read",           "RAG只读",        "rag:read",            "rag",               "read", "RAG检索只读权限"},

            // analyst (只读为主)
            {"perm_datasource_read",    "数据源只读",      "data-source:read",    "data-source",    "read", "数据源只读"},
            {"perm_ontology_read",      "本体只读",        "ontology:read",       "ontology",       "read", "本体模型只读"},
            {"perm_kg_read",            "知识图谱只读",    "kg:read",             "kg",             "read", "知识图谱只读"},
            {"perm_ai_chat",            "AI问答",         "ai:chat",             "ai",             "chat", "AI智能问答"},
            {"perm_ai_diagnose",        "AI诊断",         "ai:diagnose",         "ai",             "diagnose", "AI诊断分析"},
            {"perm_ai_scenario",        "AI场景分析",      "ai:scenario",         "ai",             "scenario", "AI场景分析"}
        };

        for (Object[] p : perms) {
            jdbcTemplate.update(
                "INSERT INTO td_permission (PERMISSION_ID, PERMISSION_NAME, PERMISSION_CODE, RESOURCE_ID, ACTION, DESCRIPTION, CREATED_TIME, CREATED_BY) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?::timestamp, 'system')",
                p[0], p[1], p[2], p[3], p[4], p[5], now);
        }
        log.info("Seeded {} permissions into td_permission", perms.length);

        // ── 角色-权限映射 ────────────────────────────────────
        String[][] rolePerms = {
            // admin → *
            {"role_admin", "perm_global_all"},

            // data-manager
            {"role_data_manager", "perm_datasource_all"},
            {"role_data_manager", "perm_pipeline_all"},
            {"role_data_manager", "perm_dqrule_all"},
            {"role_data_manager", "perm_lineage_read"},
            {"role_data_manager", "perm_datacatalog_read"},

            // ontology-designer
            {"role_ontology_designer", "perm_ontology_all"},
            {"role_ontology_designer", "perm_ontologyver_all"},
            {"role_ontology_designer", "perm_ontologyfunc_all"},

            // knowledge-engineer
            {"role_knowledge_engineer", "perm_kg_all"},
            {"role_knowledge_engineer", "perm_rule_all"},
            {"role_knowledge_engineer", "perm_classification_all"},
            {"role_knowledge_engineer", "perm_kextract_all"},
            {"role_knowledge_engineer", "perm_rag_read"},

            // analyst
            {"role_analyst", "perm_datasource_read"},
            {"role_analyst", "perm_datacatalog_read"},
            {"role_analyst", "perm_ontology_read"},
            {"role_analyst", "perm_kg_read"},
            {"role_analyst", "perm_rag_read"},
            {"role_analyst", "perm_ai_chat"},
            {"role_analyst", "perm_ai_diagnose"},
            {"role_analyst", "perm_ai_scenario"}
        };

        for (String[] rp : rolePerms) {
            jdbcTemplate.update(
                "INSERT INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME) "
                + "VALUES (?, ?, ?::timestamp)",
                rp[0], rp[1], now);
        }
        log.info("Seeded {} role-permission mappings into td_role_permission", rolePerms.length);
    }

    @SuppressWarnings("unchecked")
    private void seedBuiltinAgents() {
        if (agentRepo == null) {
            log.warn("AgentRegistryRepository 未就绪，跳过内置 Agent 种子数据初始化");
            return;
        }

        for (Map<String, Object> template : BUILTIN_AGENTS) {
            String agentId = String.valueOf(template.get("id"));
            AgentRegistryEntity existing = agentRepo.findById(agentId);
            if (existing != null) {
                log.debug("Built-in agent {} already exists, skip seeding", agentId);
                continue;
            }

            AgentRegistryEntity entity = new AgentRegistryEntity();
            entity.setId(agentId);
            entity.setName(String.valueOf(template.get("name")));
            entity.setRole("builtin");
            entity.setStatus("active");

            // metadata JSON
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("description", String.valueOf(template.get("description")));
            meta.put("systemPrompt", String.valueOf(template.get("systemPrompt")));
            meta.put("model", "deepseek-chat");
            meta.put("maxIterations", 5);
            meta.put("temperature", 0.1);
            meta.put("modelProvider", "deepseek");
            meta.put("modelName", "deepseek-chat");
            try {
                entity.setMetadata(mapper.writeValueAsString(meta));
            } catch (Exception e) {
                entity.setMetadata("{}");
            }

            // capability JSON — tool whitelist
            List<String> tools = (List<String>) template.get("toolWhitelist");
            try {
                entity.setCapability(mapper.writeValueAsString(Map.of("tools", tools != null ? tools : List.of())));
            } catch (Exception e) {
                entity.setCapability("{}");
            }

            agentRepo.insert(entity);
            log.info("Seed built-in Agent created: id={} name={}", entity.getId(), entity.getName());
        }

        log.info("Seeded {} built-in Agents", BUILTIN_AGENTS.size());
    }
}

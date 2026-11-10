package com.chinacreator.gzcm.engine.ai.config;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.CronJobService;
import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotSeedCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import com.chinacreator.gzcm.engine.ai.dao.CronJobDao;
import com.chinacreator.gzcm.engine.ai.dao.CronJobExecutionDao;
import com.chinacreator.gzcm.engine.ai.dao.DataSeedDao;
import com.chinacreator.gzcm.engine.ai.dao.SkillDao;
import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
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

    /** 旧 builtin-* 种子 id（Copilot 六智能体上线后停用保留，可改回 ACTIVE 复原）。 */
    private static final List<String> LEGACY_BUILTIN_IDS = List.of(
            "builtin-data-agent", "builtin-ontology-agent", "builtin-cognitive-agent",
            "builtin-security-agent", "builtin-kb-agent", "builtin-ai-agent");


    private final CronJobDao cronJobRepository;
    private final CronJobExecutionDao executionRepository;
    private final SkillDao skillRepository;
    private final CronJobService cronJobService;
    private final SkillService skillService;
    private final DataSeedDao dataSeedDao;

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    public DataInitializer(CronJobDao cronJobRepository,
                           CronJobExecutionDao executionRepository,
                           SkillDao skillRepository,
                           CronJobService cronJobService,
                           SkillService skillService,
                           DataSeedDao dataSeedDao) {
        this.cronJobRepository = cronJobRepository;
        this.executionRepository = executionRepository;
        this.skillRepository = skillRepository;
        this.cronJobService = cronJobService;
        this.skillService = skillService;
        this.dataSeedDao = dataSeedDao;
    }

    @Override
    public void run(String... args) {
        // H8-T1: ecos_cron_job / ecos_cron_job_execution / ecos_skill 建表 DDL 收编至
        // db/migration（V162），运行时不再内嵌 DDL；仅保留种子数据初始化。
        seedRoles();
        seedCronJobs();
        seedSkills();
        seedBuiltinAgents();
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
            long count = dataSeedDao.countRoles();
            if (count > 0) {
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
            dataSeedDao.insertRole((String) r[0], (String) r[1], (String) r[2], (String) r[3], now);
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
            dataSeedDao.insertPermission((String) p[0], (String) p[1], (String) p[2],
                    (String) p[3], (String) p[4], (String) p[5], now);
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
            dataSeedDao.insertRolePermission(rp[0], rp[1], now);
        }
        log.info("Seeded {} role-permission mappings into td_role_permission", rolePerms.length);
    }

    @SuppressWarnings("unchecked")
    private void seedBuiltinAgents() {
        if (agentRepo == null) {
            log.warn("AgentRegistryRepository 未就绪，跳过 Copilot 内置智能体种子初始化");
            return;
        }

        // 1) 六个 Copilot 内置智能体：合并 upsert（缺键补齐；seedVersion 只升不覆盖用户改值）
        for (CopilotSeedCatalog.Seed seed : CopilotSeedCatalog.all()) {
            try {
                AgentRegistryEntity existing = agentRepo.findById(seed.id());
                if (existing == null) {
                    agentRepo.insert(toEntity(seed));
                    log.info("Copilot 内置智能体创建: id={} tools={}", seed.id(),
                            CopilotToolCatalog.namesForAgent(seed.id()).size());
                } else {
                    agentRepo.update(mergeSeed(existing, seed));
                }
            } catch (Exception e) {
                log.warn("Copilot 内置智能体种子失败（不中断其余种子）: id={} reason={}",
                        seed.id(), e.getMessage());
            }
        }

        // 2) 旧 builtin-* 种子停用（保留行以保内置删除 403 / 归档审计；可改回 ACTIVE 复原）
        for (String legacyId : LEGACY_BUILTIN_IDS) {
            try {
                AgentRegistryEntity legacy = agentRepo.findById(legacyId);
                if (legacy != null && "ACTIVE".equalsIgnoreCase(legacy.getStatus())) {
                    legacy.setStatus("INACTIVE");
                    agentRepo.update(legacy);
                    log.info("旧内置 Agent 已停用: {}", legacyId);
                }
            } catch (Exception e) {
                log.warn("旧内置 Agent 停用失败: id={} reason={}", legacyId, e.getMessage());
            }
        }

        log.info("Copilot 内置智能体种子完成: {} 个", CopilotSeedCatalog.all().size());
    }

    private static AgentRegistryEntity toEntity(CopilotSeedCatalog.Seed seed) {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(seed.id());
        entity.setName(seed.name());
        entity.setRole("builtin");
        entity.setStatus("ACTIVE");
        entity.setEndpoint("");
        try {
            entity.setMetadata(mapper.writeValueAsString(CopilotSeedCatalog.seedMetadata(seed)));
            entity.setCapability(mapper.writeValueAsString(java.util.Map.of(
                    "tools", CopilotToolCatalog.namesForAgent(seed.id()))));
        } catch (Exception e) {
            entity.setMetadata("{}");
            entity.setCapability("{}");
        }
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 种子合并：只补缺失键 / 升级 seedVersion 相关字段，不覆盖用户在工作台改过的值
     * （systemPrompt/toolWhitelist/guardrails/rag/publish 等用户键存在即保留）。
     */
    private AgentRegistryEntity mergeSeed(AgentRegistryEntity existing, CopilotSeedCatalog.Seed seed) {
        try {
            Map<String, Object> meta = mapper.readValue(
                    existing.getMetadata() == null || existing.getMetadata().isBlank()
                            ? "{}" : existing.getMetadata(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, Object> seedMeta = CopilotSeedCatalog.seedMetadata(seed);
            for (Map.Entry<String, Object> entry : seedMeta.entrySet()) {
                // routes/quickQuestions/name/engine 描述随种子演进可刷新；用户键（提示词/白名单/绑定）存在即保留
                boolean refreshKey = "kind".equals(entry.getKey()) || "engine".equals(entry.getKey())
                        || "routes".equals(entry.getKey()) || "quickQuestions".equals(entry.getKey())
                        || "model".equals(entry.getKey()) || "modelProvider".equals(entry.getKey())
                        || "modelName".equals(entry.getKey()) || "maxIterations".equals(entry.getKey())
                        || "temperature".equals(entry.getKey()) || "seedVersion".equals(entry.getKey());
                boolean userTouched = meta.containsKey(entry.getKey())
                        && !"seedVersion".equals(entry.getKey());
                if (refreshKey || !userTouched) {
                    meta.put(entry.getKey(), entry.getValue());
                }
            }
            existing.setMetadata(mapper.writeValueAsString(meta));
            existing.setName(existing.getName() == null || existing.getName().isBlank()
                    ? seed.name() : existing.getName());
            if (existing.getRole() == null || existing.getRole().isBlank()) {
                existing.setRole("builtin");
            }
            existing.setStatus("ACTIVE");
            existing.setUpdatedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("种子合并失败（保留现状）: id={} reason={}", seed.id(), e.getMessage());
        }
        return existing;
    }
}

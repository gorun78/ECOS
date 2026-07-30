package com.chinacreator.gzcm.engine.ai.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.CronJobService;
import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.engine.ai.repository.CronJobExecutionRepository;
import com.chinacreator.gzcm.engine.ai.repository.CronJobRepository;
import com.chinacreator.gzcm.engine.ai.repository.SkillRepository;

/**
 * 种子数据初始化器 — 应用启动时确保表结构存在并预置种子数据。
 * <p>
 * 预置内容：
 * <ul>
 *   <li>2 个 CronJob："每日数据质量巡检"、"每周认知诊断报告"</li>
 *   <li>3 个 Skill："数据治理"、"企业经营诊断"、"政务一件事"</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CronJobRepository cronJobRepository;
    private final CronJobExecutionRepository executionRepository;
    private final SkillRepository skillRepository;
    private final CronJobService cronJobService;
    private final SkillService skillService;

    public DataInitializer(CronJobRepository cronJobRepository,
                           CronJobExecutionRepository executionRepository,
                           SkillRepository skillRepository,
                           CronJobService cronJobService,
                           SkillService skillService) {
        this.cronJobRepository = cronJobRepository;
        this.executionRepository = executionRepository;
        this.skillRepository = skillRepository;
        this.cronJobService = cronJobService;
        this.skillService = skillService;
    }

    @Override
    public void run(String... args) {
        ensureTables();
        seedCronJobs();
        seedSkills();
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
}

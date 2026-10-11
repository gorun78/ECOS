package com.chinacreator.gzcm.engine.cognitive2;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchitectureTest — cognitive-engine 架构约束（PMO-D1）
 *
 * <p>断言：
 * <ol>
 *   <li>所有类在 engine.cognitive 包下</li>
 *   <li>service 接口以 I 开头</li>
 *   <li>controller 在 controller 包</li>
 *   <li>entity/model 不依赖 service 层</li>
 *   <li>引擎间依赖边界：Allowed cross-engine deps: kb</li>
 * </ol>
 */
public class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .importPackages("com.chinacreator.gzcm.engine.cognitive2");
    }

    // ── 包约束 ──

    @Test
    void allPackagesShouldBeUnderEngine() {
        ArchRule rule = classes().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.cognitive2..")
            .should().resideInAnyPackage(
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.runtime..",
                "com.chinacreator.gzcm.common..",
                "java..",
                "javax..",
                "jakarta..",
                "org.springframework..",
                "org.mybatis..",
                "com.fasterxml..",
                "org.slf4j..",
                "org.junit..",
                "com.tngtech.."
            );
        rule.check(classes);
    }

    // ── 命名规范 ──

    @Test
    void serviceInterfacesShouldStartWithI() {
        ArchRule rule = classes().that()
            .resideInAPackage("..service..")
            .and().areInterfaces()
            .should().haveSimpleNameStartingWith("I")
            .orShould().haveSimpleNameStartingWith("Knowledge")  // kb-engine legacy
            .orShould().haveSimpleNameStartingWith("Causal")     // cognitive-engine legacy
            .orShould().haveSimpleNameStartingWith("Engine")     // IEngine variants
            // 2026-10-02 详细设计-04（commit b617c8bc）新增的两个非 I 前缀契约：
            //   - CognitivePipelineStore：认知管线持久化端口，其 impl 是 JdbcTemplate 承载的
            //     CognitivePipelineRepository（R2 数据访问收口产物）；此处仅作为解耦端口，
            //     命名沿 "…Store" 而非 "IService…" 是历史接口文件里的既有命名。
            //   - MentalEvent：Cognitive 事件契约（PMO-59 P2b / ADR-9，topic `ecos.cognitive`），
            //     承载 COGNITIVE_HYPOTHESIS_INVALIDATED / BELIEF_UPDATED / EVIDENCE_REGISTERED
            //     三类心智层事件；是事件 DTO 而非服务门面，故不套 I 前缀。
            // 精确白名单（haveSimpleName）而非前缀，避免放宽 "..service.. 下所有 Cognitive*/Mental*"。
            // 2026-10-08 详细设计-05 D.3/C.2（F05-09/F05-10）新增的同理端口：
            //   - ForecastRunSnapshotReader：预测运行服务（PRD-09 FC-02 只读快照 / FC-03 情景复制重算）
            //     的解耦端口，命名沿用设计文档 §C.2 步骤1 冻结名，非服务门面故不套 I 前缀。
            .orShould().haveSimpleName("CognitivePipelineStore")
            .orShould().haveSimpleName("MentalEvent")
            .orShould().haveSimpleName("ForecastRunSnapshotReader")
            // 补充设计 v1.0 §5.4-3：Context Builder 权威源读取 SPI（decoupling port，
            //     同 Store/Reader 解耦端口命名口径，非服务门面故不套 I 前缀）。
            .orShould().haveSimpleName("ContextSourceReader")
            .orShould().haveSimpleName("ContextSecurityTrimmer")
            // 补充设计 v1.0 §9.2 步骤1/3/4 + §9.3 末段：确定性计算产物只读接缝（decoupling port，
            //     与 ForecastRunSnapshotReader 同纪律：无实现 bean ⇒ fail-loud 标不适用，禁伪造数值）。
            //     非服务门面故不套 I 前缀。
            .orShould().haveSimpleName("OutcomeMetricValueReader")
            // 详设-09 v1.2 §十一 H-4（2026-10-12）新增的两个解耦端口，同上命名口径：
            //   - ForecastIntervalTripleReader：区间三值（P10/P50/P90）只读接缝。ST08 禁认知直读
            //     ecos_dw、铁律 §2.5 引擎边界 ⇒ 认知侧只声明读缝，装配层给 REST 适配器；
            //     无 bean ⇒ 覆盖率如实 null（禁插值，R-62 ①），非服务门面故不套 I 前缀。
            //   - BacktestReviewRoleDecider：复审角色裁决端口。红线是"认知侧零角色→权限映射"，
            //     本端口只搬运 security-engine 的裁决结论（DENY/不可用 ⇒ 不建任务），
            //     是端口而非门面，故不套 I 前缀。
            .orShould().haveSimpleName("ForecastIntervalTripleReader")
            .orShould().haveSimpleName("BacktestReviewRoleDecider")
            // 详设-09 v1.2 §十一 H-2b（2026-10-12）：DSL 求值器的两个内嵌策略接口
            //   - CaliberFormulaEvaluator.ValueSupply：事实/指标取值供给（调用方从 run 快照装配，
            //     求值器自身不访问 DB，C.8 纯函数纪律）；
            //   - CaliberFormulaEvaluator.UnitSupply：V3 单位体系运行期视图（量纲二次防御，与
            //     ontology 侧 CaliberDslValidator 同哲学）。
            //   两者是计算器的策略形态、不是服务门面，故不套 I 前缀。
            .orShould().haveSimpleName("ValueSupply")
            .orShould().haveSimpleName("UnitSupply");
        rule.allowEmptyShould(true).check(classes);
    }

    // ── 分层约束 ──

    @Test
    void entityClassesShouldNotDependOnServiceLayer() {
        ArchRule rule = noClasses().that()
            .resideInAnyPackage("..entity..", "..model..", "..bean..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..");
        rule.check(classes);
    }

    @Test
    void controllersShouldBeInControllerPackage() {
        ArchRule rule = classes().that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..controller..");
        rule.check(classes);
    }

    // ── 引擎间依赖边界（P1-2） ──

    @Test
    void shouldNotDependOnOtherEngines() {
        ArchRule rule = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.cognitive2..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.chinacreator.gzcm.engine.data..", "com.chinacreator.gzcm.engine.ontology..", "com.chinacreator.gzcm.engine.ai..", "com.chinacreator.gzcm.engine.security..");
        rule.check(classes);
    }
}

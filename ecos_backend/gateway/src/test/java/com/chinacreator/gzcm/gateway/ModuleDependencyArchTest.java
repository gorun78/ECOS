package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * PMO-C C2：模块依赖方向 ArchUnit 断言。
 *
 * <p>验证《架构铁律》v1.6 §0.3.1 单向依赖规则：
 * <ul>
 *   <li>workspace 不依赖 *-engine-impl（只能依赖 engine-api + runtime）</li>
 *   <li>service 不横向 Maven/Java 依赖（sysman/datanet/buszhi/dccheng/aiming 互拒 import）</li>
 *   <li>runtime 不反向依赖 engine/service/workspace</li>
 *   <li>（补充）engine-impl 不依赖 5 service + workspace（正向下沉只走 API 契约）</li>
 * </ul>
 *
 * <p>真实包名事实（grep 一次即可复现）：
 * <ul>
 *   <li>workspace 模块：{@code com.chinacreator.gzcm.workspace..}（workspace-impl/sub-module）</li>
 *   <li>6 引擎 impl：包在 {@code com.chinacreator.gzcm.engine.{data|ontology|kb|cognitive2|ai|security}..}，
 *       impl 类直接挂在 {@code engine.data..} 等根下（<b>没有</b> {@code .impl.} 中间层 2 级路径）</li>
 *   <li>5 个 services：包在 {@code com.chinacreator.gzcm.services.{sysman|datanet|buszhi|dccheng|aiming}..}</li>
 *   <li>runtime/common：{@code com.chinacreator.gzcm.runtime..} + {@code com.chinacreator.gzcm.common..}</li>
 * </ul>
 *
 * <p><b>getClassPath 策略</b>：扫描 {@code target/classes} - 若存在，加入 import；
 * 若为空，回退到 classpath（gateway fat jar 在 test classpath 含 engine/services 依赖，对 v2 微服务态生效）。
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='ModuleDependencyArchTest'}
 *
 * @author PMO-C
 */
public class ModuleDependencyArchTest {

    private static JavaClasses allClasses;

    /** PMO-74 H11-T3：契约规则专用宇宙 = api 目录 + impl 目录合并 import（api 类此前不在 allClasses 中）。 */
    private static JavaClasses contractClasses;
    /** H11-T3：定义在各 *-api 契约模块 target/classes 内的类 FQCN 集合。 */
    private static Set<String> apiClassNames = new HashSet<>();
    /** H11-T3：定义在各 *-impl 实现模块 target/classes 内的类 FQCN 集合。 */
    private static Set<String> implClassNames = new HashSet<>();
    /** H11-T3：字段注入存量基线（gateway/src/test/resources/archunit/field-injection-baseline.txt）。 */
    private static final Set<String> FIELD_INJECTION_BASELINE = loadFieldInjectionBaseline();

    @BeforeAll
    static void setUp() {
        // 1) 优先扫 target/classes（编译态精准）
        // 路径查找约定：ecos_backend/{module}/{module}-impl/target/classes 形式
        List<Path> candidatePaths = new ArrayList<>();
        candidatePaths.add(Paths.get("workspace/workspace-impl/target/classes"));
        candidatePaths.add(Paths.get("workspace/workspace-service/target/classes"));
        candidatePaths.add(Paths.get("engine/data-engine/data-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/ontology-engine/ontology-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/kb-engine/kb-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/cognitive-engine/cognitive-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/ai-engine/ai-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/security-engine/security-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("services/sysman/impl/sysman-impl/target/classes"));
        candidatePaths.add(Paths.get("services/datanet/target/classes"));
        // PMO-74 H11-T3 修正：P8-B 后 buszhi-impl 实际路径为 services/buszhi/impl/buszhi-impl
        // （旧候选路径 services/buszhi/buszhi-impl 永不存在，buszhi 类此前从未进入宇宙）
        candidatePaths.add(Paths.get("services/buszhi/impl/buszhi-impl/target/classes"));
        candidatePaths.add(Paths.get("services/dccheng/target/classes"));
        candidatePaths.add(Paths.get("services/aiming/target/classes"));
        candidatePaths.add(Paths.get("runtime/runtime-core/target/classes"));
        candidatePaths.add(Paths.get("runtime/common-api/target/classes"));
        candidatePaths.add(Paths.get("gateway/target/classes"));

        // 向上查找 ecos_backend 项目根
        Path cwd = Paths.get("").toAbsolutePath();
        Path backendRoot = null;
        Path cur = cwd;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                backendRoot = cur;
                break;
            }
            cur = cur.getParent();
        }

        List<Path> classPaths = new ArrayList<>();
        if (backendRoot != null) {
            for (Path p : candidatePaths) {
                Path full = backendRoot.resolve(p).normalize();
                if (Files.isDirectory(full)) {
                    classPaths.add(full);
                }
            }
        }

        if (!classPaths.isEmpty()) {
            allClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPaths(classPaths.toArray(new Path[0]));
            System.out.println("[ModuleDependencyArch] importPaths: " + classPaths.size() + " dirs, "
                    + allClasses.size() + " classes");
        } else {
            // 2) 回退：扫 classpath（gateway -am 编译后 test classpath 含依赖）
            allClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.chinacreator.gzcm");
            System.out.println("[ModuleDependencyArch] classpath fallback: " + allClasses.size() + " classes");
        }

        if (allClasses.size() == 0) {
            throw new IllegalStateException("ArchUnit import 为零 — 既无 target/classes 也无 classpath 类，测试无意义");
        }

        // ── PMO-74 H11-T3：api/impl 契约宇宙 ────────────────────────────────
        // api 与 impl 共享包根（如 kb-engine-api 类就在 com.chinacreator.gzcm.engine.kb..），
        // 无法用 package 谓词区分，故按「target/classes 目录来源 → FQCN 集合」建模。
        List<Path> apiDirs = new ArrayList<>();
        for (String rel : new String[]{
                "engine/data-engine/data-engine-api/target/classes",
                "engine/ontology-engine/ontology-engine-api/target/classes",
                "engine/kb-engine/kb-engine-api/target/classes",
                "engine/cognitive-engine/cognitive-engine-api/target/classes",
                "engine/ai-engine/ai-engine-api/target/classes",
                "engine/security-engine/security-engine-api/target/classes",
                "services/sysman/impl/sysman-api/target/classes",
                "runtime/common-api/target/classes"}) {
            if (backendRoot != null) {
                Path full = backendRoot.resolve(rel).normalize();
                if (Files.isDirectory(full)) {
                    apiDirs.add(full);
                }
            }
        }
        List<Path> implDirs = new ArrayList<>();
        for (Path p : classPaths) {
            String s = p.toString().replace('\\', '/');
            if (s.contains("-impl/target/classes")) {
                implDirs.add(p);
            }
        }
        JavaClasses apiImported = apiDirs.isEmpty() ? null : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(apiDirs.toArray(new Path[0]));
        JavaClasses implImported = implDirs.isEmpty() ? null : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(implDirs.toArray(new Path[0]));
        if (apiImported != null) {
            apiImported.forEach(c -> apiClassNames.add(c.getName()));
        }
        if (implImported != null) {
            implImported.forEach(c -> implClassNames.add(c.getName()));
        }
        List<Path> contractPaths = new ArrayList<>(apiDirs);
        contractPaths.addAll(implDirs);
        // impl 侧还在 classPaths 里但未命中 -impl 命名的目录（gateway 等）不需要参与 api→impl 判定；
        // 但目标类必须整体在宇宙内依赖才可解析，故并入 allClasses 已知目录。
        for (Path p : classPaths) {
            if (!contractPaths.contains(p)) {
                contractPaths.add(p);
            }
        }
        contractClasses = contractPaths.isEmpty() ? null : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(contractPaths.toArray(new Path[0]));
        System.out.println("[ModuleDependencyArch] contract universe: apiClasses=" + apiClassNames.size()
                + " implClasses=" + implClassNames.size()
                + " total=" + (contractClasses == null ? 0 : contractClasses.size()));
    }

    /**
     * 规则 1：workspace 不依赖 *-engine-impl 与 5 个 service（铁律 §0.3.1：
     * workspace 调全部 5 service 走 REST；禁 Maven 依赖 engine-impl）。
     */
    @Test
    void workspaceMustNotDependOnEngineImplOrService() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.chinacreator.gzcm.workspace..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.engine.security..",
                "com.chinacreator.gzcm.services.sysman..",
                "com.chinacreator.gzcm.services.datanet..",
                "com.chinacreator.gzcm.services.buszhi..",
                "com.chinacreator.gzcm.services.dccheng..",
                "com.chinacreator.gzcm.services.aiming.."
            )
            .because("铁律 v1.6 §0.3.1：workspace → services 仅 REST 跨服务，" +
                    "禁 Maven 依赖 *-engine-impl；同包符号不算（如 workspace 自身 package 也算）")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * 规则 2：5 service 不横向 Maven/Java 依赖（铁律 §0.3.1：
     * "所有横向/反向依赖拒绝"）。
     * 这里为简洁把「不指自己」语义委托给 noClasses().that().reside..., rule 引用驻留。
     * 实现方式：用 5 个 service 子包作为「被依赖包」白名单，再交叉 5 行规则。
     */
    @Test
    void servicesMustNotCrossDependency() {
        String[] services = {
            "com.chinacreator.gzcm.services.sysman..",
            "com.chinacreator.gzcm.services.datanet..",
            "com.chinacreator.gzcm.services.buszhi..",
            "com.chinacreator.gzcm.services.dccheng..",
            "com.chinacreator.gzcm.services.aiming.."
        };
        for (String self : services) {
            ArchRule rule = noClasses()
                .that().resideInAPackage(self)
                .should().dependOnClassesThat()
                .resideInAnyPackage(except(services, self))
                .because("铁律 v1.6 §0.3.1：service 不横向 Maven/Java 依赖")
                .allowEmptyShould(true);
            rule.check(allClasses);
        }
    }

    /**
     * 规则 3：runtime/common 不反向依赖 engine/service/workspace
     * （铁律 §0.3.1：runtime 是横切底座，被 engine/services 依赖，不反向依赖业务模块）。
     */
    @Test
    void runtimeMustNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.chinacreator.gzcm.runtime..",
                "com.chinacreator.gzcm.common.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.chinacreator.gzcm.engine..",
                "com.chinacreator.gzcm.services..",
                "com.chinacreator.gzcm.workspace.."
            )
            .because("铁律 v1.6 §0.3.1：runtime 是横切底座，不反向依赖 engine/service/workspace")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * 规则 4：engine-impl 不反向依赖 5 业务 application service + workspace。
     *
     * <p><b>合法例外</b>（PMO-49 P2-5 / 架构铁律 §0.3.1）：
     * <ul>
     *   <li>{@code engine.ai..} 可依赖 {@code com.chinacreator.gzcm.services.agent.runtime..}
     *       —— Aim (fire·W) engine 在 aiming :18084 与 agent-service runtime (HermesMCP / Planner /
     *       Memory / Executor) 是同一聚合服务，inline import 合法。</li>
     *   <li>{@code engine.ai..} / {@code engine.cognitive2..} 可依赖 {@code llm-gateway} runtime
     *       （runtime/llm-gateway 横切底座，非 application service）。</li>
     * </ul>
     *
     * <p>本规则只禁止 engine-impl → 5 个**业务 application service** ({@code services.sysman.. /
     * {@code services.datanet.. / services.buszhi.. / services.dccheng.. / services.aiming..}) +
     * workspace 反向依赖。
     */
    @Test
    void engineImplMustNotDependOnServiceOrWorkspace() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.engine.security.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.chinacreator.gzcm.services.sysman..",
                "com.chinacreator.gzcm.services.datanet..",
                "com.chinacreator.gzcm.services.buszhi..",
                "com.chinacreator.gzcm.services.dccheng..",
                "com.chinacreator.gzcm.services.aiming..",
                "com.chinacreator.gzcm.workspace.."
            )
            .because("铁律 v1.6 §0.3.1：engine-impl → engine-api → common-api 单向（合法例外: " +
                    "engine.ai + services.agent.runtime 为同一 aim 聚合服务）")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * 规则 4.5（补充）：engine-impl 不依赖 gateway（铁律 §0.3.1：
     * gateway 是顶层组织层，向下不依赖；引擎不得反向 import gateway 类）。
     */
    @Test
    void engineImplMustNotDependOnGateway() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.chinacreator.gzcm.engine..",
                "com.chinacreator.gzcm.services..",
                "com.chinacreator.gzcm.workspace.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.chinacreator.gzcm.gateway..")
            .because("铁律 v1.6 §0.3.1：gateway 是顶层 facade，不向 engine/service/workspace 内嵌依赖")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * 辅助：从 services 数组里排除 self，返回 String[]。
     */
    private static String[] except(String[] all, String self) {
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (!s.equals(self)) {
                out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    /**
     * PMO-74 H2-T1 规则 A：业务包禁止自建调度（{@code @Scheduled} 注解 +
     * {@code java.util.concurrent.ScheduledExecutorService} 类型依赖），
     * 唯一出口是 {@code runtime-task} 的 {@code TaskSchedulerService} / {@code ITaskManagementService}。
     *
     * <p>白名单（本规则不覆盖）：
     * <ul>
     *   <li>{@code com.chinacreator.gzcm.runtime..}（含 runtime-task / runtime-core 底座自身实现）；</li>
     *   <li>{@code com.chinacreator.gzcm.gateway..}（顶层 facade 的 twin 演示设备模拟器等非业务调度）；</li>
     *   <li>{@code com.chinacreator.gzcm.engine.security..}（H2-T3 裁定的 InMemory ABAC 缓存 TTL
     *       清理线程豁免；security-engine 全模块在禁改清单，本规则不覆盖，另案治理）；</li>
     *   <li>{@code com.chinacreator.gzcm.services.apigateway..}（独立 api-gateway 微服务，
     *       本批次未纳入）。</li>
     * </ul>
     *
     * <p>选 ArchUnit 字节码断言而非源码 grep 的理由：仓库内合规文件（MentalScanTask、
     * KgSyncServiceImpl、KBExtractScheduleServiceImpl、StructuredExtractController、
     * DqAutoRepairServiceImpl、MetadataStrategyCompatController、CognitiveEvidenceService、
     * IntegrationMetadataService 等）大量在 Javadoc/注释里以文字形式描述禁令，grep 会
     * 误报（如"禁止自建 ScheduledExecutorService"这类注释）。字节码层只看实际引用，
     * 精确且不受注释/字符串影响。</p>
     */
    @Test
    void businessMustNotSelfSchedule() {
        String[] businessPackages = businessPackagesForScheduling();

        DescribedPredicate<JavaClass> scheduledAnnotation =
                new DescribedPredicate<JavaClass>("@Scheduled 注解类型") {
                    @Override
                    public boolean test(JavaClass input) {
                        return "org.springframework.scheduling.annotation.Scheduled"
                                .equals(input.getName());
                    }
                };
        DescribedPredicate<JavaClass> scheduledExecutorService =
                new DescribedPredicate<JavaClass>("java.util.concurrent.ScheduledExecutorService") {
                    @Override
                    public boolean test(JavaClass input) {
                        return "java.util.concurrent.ScheduledExecutorService"
                                .equals(input.getName());
                    }
                };

        ArchRule noScheduledAnno = noClasses()
                .that().resideInAnyPackage(businessPackages)
                .should().dependOnClassesThat(scheduledAnnotation)
                .because("铁律 §2.5-3 / 后端规范 §九：业务定时任务必须经 runtime-task " +
                        "TaskSchedulerService.scheduleTask/schedulePeriodicTask 注册；" +
                        "禁用 Spring @Scheduled 自建触发器。")
                .allowEmptyShould(true);
        noScheduledAnno.check(allClasses);

        ArchRule noScheduledPool = noClasses()
                .that().resideInAnyPackage(businessPackages)
                .should().dependOnClassesThat(scheduledExecutorService)
                .because("铁律 §2.5-3 / 后端规范 §九：禁用 java.util.concurrent.ScheduledExecutorService " +
                        "与 Executors.newScheduledThreadPool 自建调度池（其返回类型即本类，" +
                        "任何赋值/字段声明都会命中此断言）。")
                .allowEmptyShould(true);
        noScheduledPool.check(allClasses);
    }

    /**
     * PMO-74 H2-T1 规则 B：业务包禁止直接依赖 spring-kafka（{@code KafkaTemplate} /
     * {@code @KafkaListener} / {@code DefaultKafkaProducerFactory} 等），
     * 唯一出口是 {@code runtime-event} 的 {@code EventBusService} + {@code common-api}
     * 中的 {@code KafkaTopics} 常量。
     *
     * <p>白名单：{@code com.chinacreator.gzcm.runtime.eventbus..}（Kafka 实现本体在底座）；
     * {@code com.chinacreator.gzcm.services.apigateway..}（api-gateway 微服务，另行治理）。
     *
     * <p><b>检测边界（勿高估）</b>：本规则仅覆盖编译期类型引用（{@code import org.springframework.kafka.*}
     * 与直接 {@code new KafkaTemplate(...)} 类构造）。以下为结构性盲区（同 C71 / W113 型）：
     * <ol>
     *   <li>{@code Class.forName("org.springframework.kafka...")} 反射引用 — 类型引用扫描不可见</li>
     *   <li>Maven 依赖间接传递 — 只检查包引用检查不到反射加载入堆栈</li>
     *   <li>security/ontology 侧 Object-反射审计（{@code SecurityEngineClient} /
     *       {@code KnowledgeNavSecurityEngineClient}）虽然当前不含 spring-kafka 类型引用，
     *       但同型反射调用（跨审计路径）本测试无法退化验证。</li>
     * </ol>
     * 本测试不能替代反射绕过专项检查，请勿以"通过本测试 ⇒ Kafka 出口收敛"下结论。<br>
     * 【2026-09-30 校订 P-10 / K-51】原注释"不含 spring-kafka 类型引用，天然合规，无需豁免"
     * 高估了 ArchUnit 覆盖范围，已订正。</p>
     */
    @Test
    void businessMustNotDependOnSpringKafkaDirectly() {
        String[] businessPackages = businessPackagesForKafka();

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(businessPackages)
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework.kafka..")
                .because("铁律 §2.5-4 / 后端规范 §九：跨进程事件通道唯一收敛于 runtime-event " +
                        "EventBusService + common-api KafkaTopics 常量；" +
                        "业务禁直接依赖 org.springframework.kafka.*（KafkaTemplate / @KafkaListener / " +
                        "ProducerFactory 等）。")
                .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * H2-T1 业务包白名单（调度侧）：engine.data/kb/cognitive2/ai/ontology + services(5) + workspace。
     * 排除 runtime / gateway / engine.security / services.apigateway — 见规则 A Javadoc。
     */
    private static String[] businessPackagesForScheduling() {
        return new String[]{
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.services.sysman..",
                "com.chinacreator.gzcm.services.datanet..",
                "com.chinacreator.gzcm.services.buszhi..",
                "com.chinacreator.gzcm.services.dccheng..",
                "com.chinacreator.gzcm.services.aiming..",
                "com.chinacreator.gzcm.workspace.."
        };
    }

    /**
     * H2-T1 业务包白名单（Kafka 侧）：与调度侧同，engine.security 一并纳入
     * （security engine 无 KafkaTemplate 依赖，纳入更严谨）。
     * 排除 runtime / gateway / services.apigateway。
     */
    private static String[] businessPackagesForKafka() {
        return new String[]{
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.engine.security..",
                "com.chinacreator.gzcm.services.sysman..",
                "com.chinacreator.gzcm.services.datanet..",
                "com.chinacreator.gzcm.services.buszhi..",
                "com.chinacreator.gzcm.services.dccheng..",
                "com.chinacreator.gzcm.services.aiming..",
                "com.chinacreator.gzcm.workspace.."
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    // PMO-74 H11-T3 新增门禁（2026-09-28）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * H11-T3 注入范围业务包：六引擎 + 5 application service + 两个 P8 内阁库
     * （sysman-impl 包根 {@code com.chinacreator.gzcm.sysman..}、buszhi-impl 包根
     * {@code com.chinacreator.gzcm.buszhi..}，均不在 H2-T1 旧白名单内，本规则补齐）。
     * 排除 runtime 底座与 gateway facade。
     */
    private static String[] businessPackagesForInjection() {
        return new String[]{
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.engine.security..",
                "com.chinacreator.gzcm.services..",
                "com.chinacreator.gzcm.sysman..",
                "com.chinacreator.gzcm.buszhi..",
                "com.chinacreator.gzcm.workspace.."
        };
    }

    /**
     * H11-T3 规则一（注入方式·字段注入半区）：业务类禁止 {@code @Autowired} 字段注入，
     * 一律构造器注入（后端规范 §二 / 根 AGENTS「Don't bypass DI」）。
     *
     * <p>存量治理由 H11 后续批次承接（当前基线 85 类，见
     * {@code gateway/src/test/resources/archunit/field-injection-baseline.txt}，
     * 基线只减不增；内部类按 {@code 基线类名$} 前缀归并）。命中基线之外的新字段注入
     * → 本门禁 FAIL，即「禁新增」语义。</p>
     */
    @Test
    void businessMustNotUseAutowiredFieldInjection() {
        DescribedPredicate<JavaClass> notInBaseline =
                new DescribedPredicate<JavaClass>("不在 H11-T3 字段注入存量基线内") {
                    @Override
                    public boolean test(JavaClass input) {
                        return !inFieldInjectionBaseline(input.getName());
                    }
                };

        ArchCondition<JavaClass> noAutowiredFields =
                new ArchCondition<JavaClass>("不声明 @Autowired 字段（必须构造器注入）") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaField field : item.getFields()) {
                            if (field.isAnnotatedWith(
                                    "org.springframework.beans.factory.annotation.Autowired")) {
                                events.add(SimpleConditionEvent.violated(field,
                                        item.getName() + " 字段 '" + field.getName()
                                                + "' 使用 @Autowired 字段注入"));
                            }
                        }
                    }
                };

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(businessPackagesForInjection())
                .and(notInBaseline)
                .should(noAutowiredFields)
                .because("PMO-74 H11-T3 / 后端开发规范 §二：依赖注入一律构造器注入，"
                        + "禁字段级 @Autowired；存量以基线文件登记，只减不增")
                .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * H11-T3 规则二（注入方式·new 绕过半区）：业务类禁止 {@code new} 出
     * Spring 管理的 Bean（@Service/@Component/@RestController/@Repository/@Controller），
     * 必须经容器注入（根 AGENTS「Don't bypass @Autowired with new」）。
     *
     * <p>字节码判据：{@code JavaClass.getConstructorCallsFromSelf()} 的
     * {@code new X()} 构造调用目标类若带上述 stereotype 注解（仅当 X 在本测试
     * import 宇宙内可判注解；宇宙外第三方类不误报）。当前实测 0 违规，门禁直接全绿。</p>
     */
    @Test
    void businessMustNotInstantiateSpringManagedBeansWithNew() {
        List<String> stereotypes = List.of(
                "org.springframework.stereotype.Service",
                "org.springframework.stereotype.Component",
                "org.springframework.stereotype.Repository",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.web.bind.annotation.Controller");

        ArchCondition<JavaClass> noNewOfManagedBeans =
                new ArchCondition<JavaClass>("不以 new 实例化 Spring 容器管理 Bean") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                            JavaClass owner = call.getTarget().getOwner();
                            for (String anno : stereotypes) {
                                if (owner.isAnnotatedWith(anno)) {
                                    events.add(SimpleConditionEvent.violated(item,
                                            item.getName() + " 以 new 构造容器 Bean "
                                                    + owner.getName() + "（@" + anno
                                                    + "），绕过依赖注入"));
                                    break;
                                }
                            }
                        }
                    }
                };

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(businessPackagesForInjection())
                .should(noNewOfManagedBeans)
                .because("PMO-74 H11-T3 / 架构铁律：Bean 一律容器装配（构造器注入），"
                        + "禁止业务代码 new 出 @Service/@Component/@RestController/@Repository 实例")
                .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * H11-T3 规则三（api/impl 契约）：*-api 契约模块的类不得依赖 *-impl 实现模块的类
     * （后端规范 §二 契约唯一原则 / data-engine-agents「不 import *-engine-impl」）。
     *
     * <p>实现：api 与 impl 共享包根，无法按 package 区分，故按 target/classes 目录来源
     * 建 FQCN 集合（{@link #apiClassNames} / {@link #implClassNames}），在合并宇宙上断言
     * 「源 ∈ api 集 → 不得依赖 目标 ∈ impl 集」。目录缺失（未构建）时跳过并打印 SKIP。</p>
     */
    @Test
    void apiModulesMustNotDependOnImplClasses() {
        if (contractClasses == null || apiClassNames.isEmpty() || implClassNames.isEmpty()) {
            System.out.println("[ModuleDependencyArch] SKIP apiModulesMustNotDependOnImplClasses — "
                    + "api/impl target/classes 未构建（api=" + apiClassNames.size()
                    + ", impl=" + implClassNames.size() + "）");
            return;
        }
        DescribedPredicate<JavaClass> fromApiModule =
                new DescribedPredicate<JavaClass>("类定义于 *-api 契约模块") {
                    @Override
                    public boolean test(JavaClass input) {
                        return apiClassNames.contains(input.getName());
                    }
                };
        DescribedPredicate<JavaClass> intoImplModule =
                new DescribedPredicate<JavaClass>("类定义于 *-impl 实现模块") {
                    @Override
                    public boolean test(JavaClass input) {
                        return implClassNames.contains(input.getName());
                    }
                };
        ArchRule rule = noClasses()
                .that(fromApiModule)
                .should().dependOnClassesThat(intoImplModule)
                .because("PMO-74 H11-T3 / 后端规范 §二：api 模块是契约唯一来源，"
                        + "不得依赖 impl 类（铁律 §0.3 依赖方向 engine-api ← engine-impl 单向）")
                .allowEmptyShould(true);
        rule.check(contractClasses);
    }

    // ── H11-T3 基线工具 ─────────────────────────────────────────────────────

    private static boolean inFieldInjectionBaseline(String fqcn) {
        if (FIELD_INJECTION_BASELINE.contains(fqcn)) {
            return true;
        }
        // 内部类 Outer$Inner 归并到基线登记的 Outer
        for (String entry : FIELD_INJECTION_BASELINE) {
            if (fqcn.startsWith(entry + "$")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> loadFieldInjectionBaseline() {
        Set<String> out = new HashSet<>();
        try (InputStream in = ModuleDependencyArchTest.class.getClassLoader()
                .getResourceAsStream("archunit/field-injection-baseline.txt")) {
            if (in == null) {
                throw new IllegalStateException(
                        "缺少 H11-T3 基线文件 gateway/src/test/resources/archunit/field-injection-baseline.txt");
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        out.add(t);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取字段注入基线失败", e);
        }
        System.out.println("[ModuleDependencyArch] field-injection baseline entries: " + out.size());
        return out;
    }
}
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

    /**
     * F05-15 X-35：业务包自建 RestTemplate 存量基线
     * （gateway/src/test/resources/archunit/rest-template-baseline.txt，只减不增；
     * cognitive2 收口类禁止登记）。
     */
    private static final Set<String> REST_TEMPLATE_BASELINE =
            loadBaseline("archunit/rest-template-baseline.txt");

    /**
     * F05-15 X-36：业务包自建并发（Executors 工厂 / new Thread）存量基线
     * （gateway/src/test/resources/archunit/self-schedule-baseline.txt，只减不增；
     * CognitivePipelineExecutor 已迁 runtime-task 扇出，禁止登记）。
     */
    private static final Set<String> SELF_SCHEDULE_BASELINE =
            loadBaseline("archunit/self-schedule-baseline.txt");

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
        // F06-17 X-26：llm-gateway 此前不在扫描宇宙，llm 域并发/RestTemplate 违规无法被捕获
        candidatePaths.add(Paths.get("runtime/llm-gateway/target/classes"));
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
     *       —— 该例外<b>已随详细设计-06 §八 接缝义务 1（M0）关闭</b>：引擎侧改为依赖
     *       {@code runtime/llm-gateway} 的 {@code AgentRuntimeProvider} SPI，
     *       由 {@link #engineMustNotDependOnServices()} 强制。</li>
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
     * 详细设计-06 §八 接缝义务 1（<b>M0 直改，不等裁决</b>）/ ARCH_SPEC C257 / PRD-01 REQ-PLT-28 /
     * ADR-19 §2.1 —— 引擎侧不得依赖任何部署态 service。
     *
     * <p>改造前口径：{@code engine.ai..} 直接 import
     * {@code services.agent.runtime.{mcp,evolution,toolrouter}} 等类型，使引擎反向依赖
     * service。现六个运行时能力已收口到 {@code runtime/llm-gateway} 的
     * {@code com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider} SPI：
     * 契约在底座、实现由 {@code services/agent-service} 提供、gateway fat-JAR 与
     * {@code services/aiming} 在组装期注入。故本规则把 engine → services 全面禁用，
     * 不留豁免。</p>
     *
     * <p>门禁双处纪律：本用例只收紧依赖方向，<b>不</b>改
     * {@code _win_tasks/check-legacy-modules.ps1} 的 {@code baselineCount=12} 与
     * {@code ArchitectureTest.baselineModules=12}。</p>
     */
    @Test
    void engineMustNotDependOnServices() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.chinacreator.gzcm.engine..",
                "com.chinacreator.gzcm.runtime.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.chinacreator.gzcm.services..")
            .because("详细设计-06 §八 接缝义务 1：引擎/runtime 一律不得依赖部署态 service；"
                    + "Agent 运行时能力只经 runtime/llm-gateway 的 AgentRuntimeProvider SPI 获取")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * F06-17 X-26 / 验收命令命名入口（详细设计-06 §F06-17）：llm-gateway 必须在扫描宇宙内。
     *
     * <p>此前 candidatePaths 缺 {@code runtime/llm-gateway/target/classes}，llm 域违规
     * （内置调度池自建、RestTemplate 直连）永远判不出来（X-26 / X-25 一部分）。
     * 本用例只做宇宙归属断言 —— 是 X-26 缺失行为的防倒退守卫：
     * 若 llm-gateway 未构建 → target/classes 缺失 → 直接 FAIL（禁止静默空宇宙）；
     * 若已构建但未并入 allClasses（例如未来重构走 fallback 分支时丢失）→ FAIL。
     * 真正并发/RestTemplate 违规断言在 {@link #businessMustNotSelfSchedule()} 与
     * {@code SelfScheduleArchTest#noExecutorFactoryNoRunAsyncNoRawThreadInBusiness} 承接。</p>
     *
     * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='ModuleDependencyArchTest#llmGatewayIsInsideTheUniverse'}
     */
    @Test
    void llmGatewayIsInsideTheUniverse() {
        long llmClasses = allClasses.stream()
                .filter(c -> c.getPackageName().startsWith("com.chinacreator.gzcm.runtime.llm"))
                .count();
        org.junit.jupiter.api.Assertions.assertTrue(llmClasses > 0,
                "X-26 未达：llm-gateway 类数量 = " + llmClasses
                        + "，runtime/llm-gateway/target/classes 未进入扫描宇宙。"
                        + "检查 setUp candidatePaths 是否保留 llm-gateway 路径。");
        System.out.println("[ModuleDependencyArch] llm-gateway 在宇宙内：" + llmClasses + " classes");
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

        // ── F05-15 X-36 规则 C（字节码）：业务包禁用 Executors 工厂自建线程池 ──
        // CognitivePipelineExecutor 的 Executors.newFixedThreadPool(4) 已迁 CognitiveRuntimeFanout
        // （runtime-task 通道），禁止回潮：该类不得登记基线。存量以基线文件登记，只减不增。
        DescribedPredicate<JavaClass> notInSelfScheduleBaseline =
                new DescribedPredicate<JavaClass>("不在 F05-15 自建并发存量基线内") {
                    @Override
                    public boolean test(JavaClass input) {
                        return !inBaseline(SELF_SCHEDULE_BASELINE, input.getName());
                    }
                };
        DescribedPredicate<JavaClass> executorsFactory =
                new DescribedPredicate<JavaClass>("java.util.concurrent.Executors 工厂类") {
                    @Override
                    public boolean test(JavaClass input) {
                        return "java.util.concurrent.Executors".equals(input.getName());
                    }
                };
        ArchRule noExecutorsFactory = noClasses()
                .that().resideInAnyPackage(businessPackages)
                .and(notInSelfScheduleBaseline)
                .should().dependOnClassesThat(executorsFactory)
                .because("F05-15 X-36 / 铁律 §2.5-3：业务包禁 Executors.new* 自建线程池；" +
                        "同步扇出经 runtime-task（TaskSchedulerService/ITaskManagementService）或" +
                        "底座统一 Executor Bean。存量以 self-schedule-baseline.txt 登记，只减不增。")
                .allowEmptyShould(true);
        noExecutorsFactory.check(allClasses);

        // ── F05-15 X-36 规则 D（源码兜网）：业务源码禁裸 new Thread( ──
        // 字节码先行（规则 A/B/C 已覆盖调度与池）；本半区补源码面：Executors 之外的裸线程。
        // 扫描前跳过注释行（scanForLiteral skipCommentLines=true），Javadoc 里的禁令散文不误报；
        // archive/legacy 目录不参与。存量登记 self-schedule-baseline.txt（FQCN 由路径推导）。
        // F06-17 X-27：扩展检测面至 CompletableFuture.runAsync/supplyAsync 与 java.lang.Thread
        // （含 .start() 调用），使"借用公共池"与"裸线程隐式启动"三类漏检形态均被断言覆盖。
        List<Path> threadRoots = businessConcurrencySourceRoots();
        // X-26：businessConcurrencySourceRoots 此前不含 runtime/，
        // llm-gateway 包内的新线程源码在此才正式进入扫描面。
        if (threadRoots.isEmpty()) {
            System.out.println("[ModuleDependencyArch] SKIP new Thread 源码半区 — backendRoot 未定位");
        } else {
            List<String> threadOffenders = new ArrayList<>();
            for (String literal : new String[]{
                    "new Thread(",
                    "CompletableFuture.runAsync(",
                    "CompletableFuture.supplyAsync("}) {
                for (String hit : scanForLiteral(threadRoots, literal, true)) {
                    String filePart = hit.substring(0, hit.indexOf(':')).replace('\\', '/');
                    if (!filePart.contains("/src/main/java/")
                            || filePart.contains("/archive/") || filePart.contains("/legacy/")
                            || filePart.contains("/config/")) {
                        continue;
                    }
                    String fqcn = sourcePathToFqcn(filePart);
                    if (fqcn != null && inBaseline(SELF_SCHEDULE_BASELINE, fqcn)) {
                        continue;
                    }
                    threadOffenders.add(hit);
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(threadOffenders.isEmpty(),
                    "F05-15/F06-17 X-36/X-27 业务源码存在自建并发（基线外）：\n"
                            + String.join("\n", threadOffenders));
        }
    }

    /**
     * F05-15（X-35）：业务包禁止自建 {@code org.springframework.web.client.RestTemplate}。
     *
     * <p>收口口径（唯一方案，禁第二套）：base-url 一律经 runtime-access
     * {@code ServiceEndpointResolver.resolve(service, RouteMode)} 双模寻址（route-manifest 单源），
     * 客户端一律注入各模块 config 包共享 Bean 工厂产出的 RestTemplate
     * （如 cognitive2 {@code CognitiveRestClientConfig#cognitiveRestTemplate}，
     * connect 3s / read 10s 超时口径由工厂统一下发）。</p>
     *
     * <p><b>豁免</b>：{@code ..config..} 包——共享客户端 Bean 工厂是铁律允许的唯一集中构造点，
     * 业务 Service 只准构造器注入复用；判据取字节码 {@code getConstructorCallsFromSelf()}
     * 的调用目标 owner（覆盖 RestTemplate 全部构造重载），比源码 grep 精确且不受注释干扰。</p>
     *
     * <p>存量以 {@code archunit/rest-template-baseline.txt} 登记（只减不增）；
     * cognitive2 收口类（EngineCapabilityRegistryImpl / EntityLinker）禁止登记——
     * 违者本门禁 FAIL。</p>
     */
    @Test
    void businessMustNotConstructRestTemplate() {
        DescribedPredicate<JavaClass> subject =
                new DescribedPredicate<JavaClass>("业务包且非 config 共享客户端工厂、非 RestTemplate 存量基线") {
                    @Override
                    public boolean test(JavaClass input) {
                        String pkg = input.getPackageName();
                        if (pkg.endsWith(".config") || pkg.contains(".config.")) {
                            return false; // 共享 Bean 工厂豁免（见方法 Javadoc）
                        }
                        return !inBaseline(REST_TEMPLATE_BASELINE, input.getName());
                    }
                };

        ArchCondition<JavaClass> noSelfRestTemplate =
                new ArchCondition<JavaClass>("不 new org.springframework.web.client.RestTemplate") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                            if ("org.springframework.web.client.RestTemplate"
                                    .equals(call.getTarget().getOwner().getName())) {
                                events.add(SimpleConditionEvent.violated(item,
                                        item.getName() + " 自建 RestTemplate（应注入共享 Bean + " +
                                                "ServiceEndpointResolver 双模寻址，X-35）"));
                            }
                        }
                    }
                };

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(businessPackagesForInjection())
                .and(subject)
                .should(noSelfRestTemplate)
                .because("F05-15 X-35：业务包禁自建 RestTemplate/硬编码 localhost 基址；" +
                        "存量以 rest-template-baseline.txt 登记，只减不增；cognitive2 收口类不在基线")
                .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * PMO-74 H2-T1 规则 B：业务包禁止直接依赖 spring-kafka（{@code KafkaTemplate} /
     * {@code @KafkaListener} / {@code DefaultKafkaProducerFactory} 等），
     * 唯一出口是 {@code runtime-event} 的 {@code EventBusService} + {@code common-api}
     * 中的 {@code KafkaTopics} 常量。
     *
     * <p>白名单：{@code com.chinacreator.gzcm.runtime.eventbus..}（Kafka 实现本体在底座）；
     * {@code com.chinacreator.gzcm.services.apigateway..}（api-gateway 微服务，另行治理）。
     * security/ontology 引擎侧的 Object-反射审计（{@code SecurityEngineClient} /
     * {@code KnowledgeNavSecurityEngineClient}）不含 spring-kafka 类型引用，
     * 天然合规，无需豁免。</p>
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
        return inBaseline(FIELD_INJECTION_BASELINE, fqcn);
    }

    /** 通用基线成员判定：FQCN 精确命中或内部类（{@code 基线类名$} 前缀）归并命中。 */
    private static boolean inBaseline(Set<String> baseline, String fqcn) {
        if (baseline.contains(fqcn)) {
            return true;
        }
        for (String entry : baseline) {
            if (fqcn.startsWith(entry + "$")) {
                return true;
            }
        }
        return false;
    }

    /** 从 classpath 读取 archunit 基线文件（# 注释行与空行忽略；缺失即启动失败，禁静默空基线）。 */
    private static Set<String> loadBaseline(String resource) {
        Set<String> out = new HashSet<>();
        try (InputStream in = ModuleDependencyArchTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("缺少基线文件 gateway/src/test/resources/" + resource);
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
            throw new IllegalStateException("读取基线失败: " + resource, e);
        }
        System.out.println("[ModuleDependencyArch] baseline " + resource + " entries: " + out.size());
        return out;
    }

    private static Set<String> loadFieldInjectionBaseline() {
        return loadBaseline("archunit/field-injection-baseline.txt");
    }

    /**
     * F05-15 X-36 源码半区扫描根：engine/services/workspace/gateway 的全部
     * {@code src/main/java} 目录（runtime 底座与 archive/legacy 不参与，跨平台定位 backendRoot）。
     */
    private static List<Path> businessConcurrencySourceRoots() {
        List<Path> out = new ArrayList<>();
        Path cur = Paths.get("").toAbsolutePath();
        Path backendRoot = null;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                backendRoot = cur;
                break;
            }
            cur = cur.getParent();
        }
        if (backendRoot == null) {
            return out;
        }
        for (String rel : new String[]{"engine", "services", "workspace", "gateway"}) {
            Path dir = backendRoot.resolve(rel).normalize();
            if (Files.isDirectory(dir)) {
                out.add(dir);
            }
        }
        // F06-17 X-26：llm-gateway 的调度/并发违规此前因不在扫描面被判不出（AgentSchedulerImpl）；
        // 此处精确加入 runtime/llm-gateway（而非整个 runtime/），其余 runtime 模块
        // （runtime-core/runtime-access/runtime-task/runtime-event/runtime-monitor）
        // 是线程基础设施提供者，其自有线程池属合法服务实现，不适用业务包禁令。
        Path llmGwDir = backendRoot.resolve("runtime/llm-gateway").normalize();
        if (Files.isDirectory(llmGwDir)) {
            out.add(llmGwDir);
        }
        return out;
    }

    /** {@code .../src/main/java/a/b/C.java}（相对或绝对路径均可，用 / 归一）→ FQCN；非 src/main/java 下返回 null。 */
    private static String sourcePathToFqcn(String path) {
        String s = path.replace('\\', '/');
        int idx = s.indexOf("/src/main/java/");
        if (idx < 0 || !s.endsWith(".java")) {
            return null;
        }
        return s.substring(idx + "/src/main/java/".length(), s.length() - ".java".length())
                .replace('/', '.');
    }

    // ════════════════════════════════════════════════════════════════════════
    // F04-18 kb 契约加固（2026-10-30 water·K batch）
    // ════════════════════════════════════════════════════════════════════════

    /** 定位 kb 源码根（kb-engine-impl/src/main + kb-engine-api/src/main）。 */
    private static List<Path> kbSourceRoots() {
        List<Path> out = new ArrayList<>();
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
        if (backendRoot == null) {
            return out;
        }
        Path impl = backendRoot.resolve("engine/kb-engine/kb-engine-impl/src/main").normalize();
        Path api = backendRoot.resolve("engine/kb-engine/kb-engine-api/src/main").normalize();
        if (Files.isDirectory(impl)) out.add(impl);
        if (Files.isDirectory(api)) out.add(api);
        return out;
    }

    /**
     * F04-18 · kb 契约隔离 · ArchUnit 字节码规则：
     * kb-engine-impl + kb-engine-api 的<b>所有</b>类不得依赖
     * {@code com.chinacreator.gzcm.engine.ontology..}（跨引擎耦合）。
     * kb 侧须自持领域对象（{@code engine.kb.model.ExtractedSubGraph}）替代。
     */
    @Test
    void kbMustNotDependOnOntologyInternals() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.chinacreator.gzcm.engine.kb..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.chinacreator.gzcm.engine.ontology..")
            .because("F04-18 kb 契约隔离：kb 不引入 engine.ontology.* 内部类，" +
                    "以 kb-local ExtractedSubGraph 替代；运行时 retriever/rewriter 也禁止反向耦合。")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * F04-14 / F04-18 附加门禁：kb-engine 业务包禁止<b>自建执行器</b> —
     * 字节码断言 kb 侧不得依赖 {@code java.util.concurrent.Executors}、
     * {@code java.util.concurrent.ExecutorService}、
     * {@code java.util.concurrent.CompletableFuture}。
     * 统一出口 = {@code runtime-task} 的 {@code ITaskManagementService}。
     */
    @Test
    void kbMustNotCreateOwnExecutors() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.chinacreator.gzcm.engine.kb..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "java.util.concurrent.Executors",
                    "java.util.concurrent.ExecutorService",
                    "java.util.concurrent.CompletableFuture")
            .because("F04-14：kb 不自建执行器/任务池，统一走 runtime-task 的 ITaskManagementService")
            .allowEmptyShould(true);
        rule.check(allClasses);
    }

    /**
     * F04-13 附加门禁：kb 源码不得直连 LLM REST — 文本扫描 {@code api/v1/llm}
     * 字面量。LLM 唯一入口 = {@code runtime.llm.gateway.LLMGateway}。
     * 允许注释层的文字性 mention（限定为 {@code <b>字符串字面量</b> or {@code import} 出现），
     * 简化实现为「不含该字符串，或仅出现在 Javadoc/&lt;code&gt; 标签内」，
     * 当前 F04-13 完成态下应为 0 命中。
     */
    @Test
    void kbMustNotCallLlmEndpointDirectly() {
        List<Path> roots = kbSourceRoots();
        if (roots.isEmpty()) {
            System.out.println("[kbMustNotCallLlmEndpointDirectly] SKIP — kb 源码根未找到（未构建）");
            return;
        }
        List<String> offenders = scanForLiteral(roots, "api/v1/llm", true);
        org.junit.jupiter.api.Assertions.assertTrue(offenders.isEmpty(),
                "F04-13 kb 禁直连 LLM REST：kb MAIN 源码含 \"api/v1/llm\" 字面量（非注释）：\n"
                        + offenders.stream().limit(20).reduce("", (a, b) -> a + "\n" + b)
                        + (offenders.size() > 20 ? "\n... (+" + (offenders.size() - 20) + " more)" : ""));
    }

    /**
     * F04-15 附加门禁：kb 源码不得反射审计 Kafka send — 文本扫描 {@code getMethod("send"}。
     * 收敛：审计事件一律经 {@code runtime-event} 的 {@code EventBusService}，
     * 禁止业务侧自建 KafkaTemplate 反射；命中该字面量即视为绕过 base 通道回滚。
     */
    @Test
    void kbMustNotReflectivelyInvokeSend() {
        List<Path> roots = kbSourceRoots();
        if (roots.isEmpty()) {
            System.out.println("[kbMustNotReflectivelyInvokeSend] SKIP — kb 源码根未找到（未构建）");
            return;
        }
        List<String> offenders = scanForLiteral(roots, "getMethod(\"send\"", true);
        org.junit.jupiter.api.Assertions.assertTrue(offenders.isEmpty(),
                "F04-15 kb 禁反射 Kafka send：kb MAIN 源码含 getMethod(\"send\")（非注释）：\n"
                        + offenders.stream().limit(20).reduce("", (a, b) -> a + "\n" + b)
                        + (offenders.size() > 20 ? "\n... (+" + (offenders.size() - 20) + " more)" : ""));
    }

    // ════════════════════════════════════════════════════════════════════════
    // REQ-COG-09 认知域产物写权威单源（PRD-05 §6.4 第四冻结项 · 2026-10-08 详设-05 批）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 业务域 {@code ecos_dw} 的写语句形态（DR01 语义下的"直连"：INSERT INTO / UPDATE / DELETE FROM
     * 直接点名 ecos_dw 本体或 ecos_dw 下的表）。
     */
    private static final java.util.regex.Pattern DW_WRITE_SQL = java.util.regex.Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+ecos_dw(?:\\.[a-z0-9_]+)?\\b");

    /** 定位 cognitive 源码根（impl/src/main + api/src/main，与 kbSourceRoots 同纪律）。 */
    private static List<Path> cognitiveSourceRoots() {
        List<Path> out = new ArrayList<>();
        Path cur = Paths.get("").toAbsolutePath();
        Path backendRoot = null;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                backendRoot = cur;
                break;
            }
            cur = cur.getParent();
        }
        if (backendRoot == null) {
            return out;
        }
        Path impl = backendRoot.resolve("engine/cognitive-engine/cognitive-engine-impl/src/main").normalize();
        Path api = backendRoot.resolve("engine/cognitive-engine/cognitive-engine-api/src/main").normalize();
        if (Files.isDirectory(impl)) out.add(impl);
        if (Files.isDirectory(api)) out.add(api);
        return out;
    }

    /**
     * PRD-05 §6.4 REQ-COG-09 规格 2（第四冻结项，验收标识
     * {@code mvn -Dtest=ModuleDependencyArchTest#cognitiveDoesNotWriteEcosDwDirectly}）：
     * 认知域确定性产物只准经 <b>data-engine 唯一写通道</b>
     * （{@code POST /api/v1/datanet/write-channel}）提交落 {@code ecos_dw}，
     * cognitive 侧既不能在编译期耦合写通道 owner 的内部类，也不能在源码里写 ecos_dw 的 SQL。
     *
     * <p>两半区（与 kb 系列同纪律）：
     * <ol>
     *   <li><b>字节码</b>：{@code engine.cognitive..} / {@code engine.cognitive2..} 不得依赖
     *       {@code engine.data..} 或 {@code services.datanet..}
     *       （跨引擎写权限 = owner 独有；cognitive 只用 REST 说话）；</li>
     *   <li><b>源码</b>：cognitive src/main（注释剥离后）零 {@code ecos_dw} 写语句。</li>
     * </ol>
     *
     * <p><b>本门禁能证什么、不能证什么（诚实边界）</b>：钉的是"不经 Java 依赖或 SQL 直连 ecos_dw"
     * 这条静态不变量；产物表 V197 的库侧落点实测（SELECT catalog / 行数）属需求检视报告 §14.4
     * 未授权项 ⇒ 该半项记<b>「未执行」</b>，静态面由
     * {@code DeterministicForecastArtifactTest#resultsLandInEcosDwNotEngineSchema} 承担。
     */
    @Test
    void cognitiveDoesNotWriteEcosDwDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                    "com.chinacreator.gzcm.engine.cognitive..",
                    "com.chinacreator.gzcm.engine.cognitive2..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.chinacreator.gzcm.engine.data..",
                    "com.chinacreator.gzcm.services.datanet..")
            .because("REQ-COG-09 规格 2 / ADR-14 / ST08：确定性产物只经 data-engine 唯一写通道 REST 提交，"
                    + "cognitive 禁编译期依赖 data-engine 或 datanet 内部类以直写 ecos_dw")
            .allowEmptyShould(true);
        rule.check(allClasses);

        List<Path> roots = cognitiveSourceRoots();
        if (roots.isEmpty()) {
            System.out.println("[cognitiveDoesNotWriteEcosDwDirectly] SKIP — cognitive 源码根未找到（未构建）");
            return;
        }
        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml"))
                        .toList();
                for (Path p : files) {
                    String code = stripCommentsForScan(p, readSourceUtf8(p));
                    java.util.regex.Matcher m = DW_WRITE_SQL.matcher(code);
                    while (m.find()) {
                        offenders.add(root.relativize(p) + ":" + lineNumberOf(code, m.start())
                                + " | " + m.group());
                    }
                }
            } catch (IOException e) {
                // 根目录不可读 — 视为无内容（与 scanForLiteral 同口径）
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(offenders.isEmpty(),
                "REQ-COG-09 cognitive 直写 ecos_dw（绕 data-engine 唯一写通道，ST08）：\n"
                        + offenders.stream().limit(20).reduce("", (a, b) -> a + "\n" + b)
                        + (offenders.size() > 20 ? "\n... (+" + (offenders.size() - 20) + " more)" : ""));
    }

    /**
     * 注释剥离后返回可扫描文本：{@code .java} 去单行/块注释，{@code .xml}（Mapper）去
     * {@code <!-- -->} 文档注释。目的只有一个 —— 避免 Javadoc/注释里的历史性写法造成假阳。
     */
    private static String stripCommentsForScan(Path p, String src) {
        boolean xml = p.toString().endsWith(".xml");
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (!xml && c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (xml && c == '<' && src.startsWith("<!--", i)) {
                int end = src.indexOf("-->", i + 4);
                i = (end < 0) ? src.length() : end + 3;
                sb.append(' ');
                continue;
            }
            if (!xml && c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = (end < 0) ? src.length() : end + 2;
                sb.append(' ');
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int lineNumberOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 宽松 UTF-8 读（存量 pom.xml 非 UTF-8，严格解码会抛 MalformedInputException 使门禁假崩）。 */
    private static String readSourceUtf8(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 扫描给定根下所有 .java 命中 literal。
     *
     * @param skipCommentLines 是否跳过纯注释行（Javadoc 星号前缀 / 单行 // 前缀 /
     *                         成对的块注释符）。F04-13/15 门禁传 true —— 允许 Javadoc
     *                         内的历史性（"@F04-13 从 /api/v1/llm/* 收敛到 LLMGateway"）提及，
     *                         但拒绝代码/字符串/constant 处的真实直连。
     * @param literal          要命中的字面量
     */
    private static List<String> scanForLiteral(List<Path> roots, String literal, boolean skipCommentLines) {
        List<String> found = new ArrayList<>();
        for (Path root : roots) {
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(root)) {
                walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> {
                         try {
                             List<String> lines = java.nio.file.Files.readAllLines(p, StandardCharsets.UTF_8);
                             boolean inBlockComment = false;
                             for (int i = 1; i <= lines.size(); i++) {
                                 String line = lines.get(i - 1);
                                 String trimmed = line.strip();
                                 if (skipCommentLines) {
                                     if (!inBlockComment && trimmed.startsWith("/*")) {
                                         if (!(trimmed.contains("*/"))) {
                                             inBlockComment = true;
                                         }
                                         continue;
                                     }
                                     if (inBlockComment) {
                                         if (trimmed.contains("*/")) {
                                             inBlockComment = false;
                                         }
                                         continue;
                                     }
                                     if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                                         continue;
                                     }
                                 }
                                 if (line.contains(literal)) {
                                     found.add(root.relativize(p) + ":" + i + " | " + line.trim());
                                 }
                             }
                         } catch (IOException ex) {
                             // 单文件读失败不上抛
                         }
                     });
            } catch (IOException e) {
                // 根目录不可读 — 视为无内容
            }
        }
        return found;
    }
}
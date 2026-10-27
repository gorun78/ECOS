package com.chinacreator.gzcm.engine.ai.wagent.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W 编排域「禁止依赖」守卫（详细设计-10 §6.2/§6.3 三禁红线 + 铁律 §0.6 调度单源 + §2.5 #1 数据访问单基类；
 * 差距分析 §10.7.1 缺项 {@code WAgentForbiddenDependencyArchTest} 的补实体，R9① 裁决 = 缺的门禁补代码实现）。
 *
 * <p>三条红线（判定对象 = 已编译字节码的依赖/访问图，故注释、javadoc、字符串里的字样一律不算违例，
 * 天然免疫 {@code db-migration-lint} 的 D-15 类正则假绿/假红）：</p>
 * <ol>
 *   <li><b>禁跨 Engine impl</b>：W 域触达他域只走他域 {@code *-api} 门面（REST 前缀见
 *       {@code WAgentEngineCatalog}），禁 import 他域 {@code impl} 类；</li>
 *   <li><b>禁自建数据访问层</b>：W 域零 {@code org.springframework.jdbc..}/{@code java.sql..}/{@code javax.sql..}
 *       —— 与 {@code NoDirectEngineTableAccessTest}（禁 MyBatis 双轨 + DAO 唯一基类）互补：那边钉"只能用基类"，
 *       这边钉"绝不自持连接/语句"；</li>
 *   <li><b>禁自建调度</b>：调度单源 = runtime-task（{@code runtime-task}），W 域零 {@code @Scheduled}/
 *       {@code Executors}/线程池/{@code Timer}/裸 {@code new Thread(...)}。</li>
 * </ol>
 *
 * <p><b>反假绿四锁</b>（对齐 #140 子批 C 已确立口径）：① 导入宇宙非空 + 关键成员钉死（防
 * {@code importPackages} 空集恒绿）；② 谓词反向对照——同一判定用在非 wagent 的 ai 生产类上必须抓到
 * 已实测的真违例集合（抓不到即谓词失效）；③ 豁免面双向钉死（SSE 轮询里的 {@code Thread.sleep} 只允许
 * 方法调用、禁构造调用，属主清单等值断言）；④ 源码级 import 单点钉死（跨引擎门面引用恰 1 条，
 * 防止常量内联使字节码看不见该依赖而让红线 1 变成永真）。</p>
 */
class WAgentForbiddenDependencyArchTest {

    private static final String AI_ROOT = "com.chinacreator.gzcm.engine.ai";
    private static final String WAGENT_ROOT = AI_ROOT + ".wagent";
    private static final String ENGINE_ROOT = "com.chinacreator.gzcm.engine.";

    /** 反空集锁①：2026-10-15 实测 wagent main 下 92 个 .java，生产类数量只减即需登记。 */
    private static final int WAGENT_PRODUCTION_FLOOR = 92;

    /** 反空集锁①（成员级）：三禁各有一个代表载体必须在导入宇宙内。 */
    private static final List<String> WAGENT_PINNED_MEMBERS = List.of(
            WAGENT_ROOT + ".WAgentEngineCatalog",
            WAGENT_ROOT + ".controller.WAgentRunController",
            WAGENT_ROOT + ".dao.WAgentEvidenceDao",
            WAGENT_ROOT + ".dataobject.WAgentEvidenceDO",
            WAGENT_ROOT + ".readiness.EngineProbeClient");

    /** 红线 2 判定面：SQL/JDBC 三族包前缀（{@code runtime..JdbcTemplateDaoSupport} 属允许基类，不在内）。 */
    private static final List<String> JDBC_PACKAGES = List.of(
            "org.springframework.jdbc.", "java.sql.", "javax.sql.");

    /** 红线 3 判定面：自建调度类型（裸 {@code new Thread(...)} 另走访问图，见 {@link #BARE_THREAD_CONSTRUCTOR}）。 */
    private static final List<String> SCHEDULE_TYPES = List.of(
            "org.springframework.scheduling.",
            "java.util.concurrent.Executors",
            "java.util.concurrent.ThreadPoolExecutor",
            "java.util.concurrent.ScheduledThreadPoolExecutor",
            "java.util.concurrent.ScheduledExecutorService",
            "java.util.concurrent.ForkJoinPool",
            "java.util.Timer",
            "java.util.TimerTask");

    private static final String BARE_THREAD_CONSTRUCTOR = "java.lang.Thread";

    /**
     * 反空集锁②（红线 2 反向对照）：同谓词用于非 wagent 的 ai 生产类，2026-10-15 实测抓到 8 个真依赖
     * （{@code RowMapper}/{@code ResultSet}/{@code SQLException}）。若这里归零，说明字节码里看不见 JDK/Spring
     * 依赖，红线 2 的"零违例"就是假的。
     */
    private static final Set<String> JDBC_REVERSE_CONTROL = Set.of(
            AI_ROOT + ".controller.AiErrorAdvice",
            AI_ROOT + ".service.DiagnosticAgentService",
            AI_ROOT + ".dao.AgentDao",
            AI_ROOT + ".dao.CronJobDao",
            AI_ROOT + ".dao.CronJobExecutionDao",
            AI_ROOT + ".dao.DiagnosticAgentDao",
            AI_ROOT + ".dao.SkillDao",
            AI_ROOT + ".dao.AgentSessionDao");

    /** 反空集锁②（红线 3 反向对照）：非 wagent 侧实测真用线程池/裸线程的 5 个生产类。 */
    private static final Set<String> SCHEDULE_REVERSE_CONTROL = Set.of(
            AI_ROOT + ".service.AgentDelegationService",
            AI_ROOT + ".service.AgentEvaluator",
            AI_ROOT + ".service.AgentMetricsCollector",
            AI_ROOT + ".service.AgentStudioService",
            AI_ROOT + ".service.ToolExecutorService");

    /**
     * 红线 3 唯一豁免（双向钉死）：SSE 事件流与就绪探测在<b>请求线程</b>内做阻塞轮询，只用
     * {@code Thread.sleep}/{@code currentThread().interrupt()}（方法调用），不建线程、不调度。
     * 豁免面 = 属主集合等值 + 访问类型等值，两侧任一变化都必须先改本名录。
     */
    private static final Set<String> THREAD_METHOD_CALL_OWNERS = Set.of(
            WAGENT_ROOT + ".controller.WAgentRunEventStreamController",
            WAGENT_ROOT + ".readiness.EngineProbeClient");

    /** 反空集锁④（扫描面基线）：2026-10-15 实测 ai 模块 main 源码 252 个 .java（其中 wagent 92）；
     *  2026-10-16 +1（252→253）= 收口 gateway 聚合面 {@code TaskExecutor} 注入歧义新增的
     *  {@code ai.config.RunEventStreamExecutorConfig}（W 域 SSE 泵命名池；受本守卫红线 3 与
     *  {@code TerminologyArchTest} 双门禁夹制，只能以非 {@code WAgent} 名住在 wagent 包外的模块级 config）；
     *  2026-10-16 +1（253→255）= #142/B4-3d-6（D-B4-28 收口）新增
     *  {@code ai.security.AiToolColumnSurfaceGate}（AI 工具结果列面消费闸，ai 批内接线、不入 wagent 计数基线）；
     *  2026-10-18 +1（255→256）= #143/B5-1（工具目录双轨收敛）新增
     *  {@code ai.wagent.tool.WAgentContractToolSource}（W 契约目录进咽喉的读取端，落 wagent 包内 ⇒
     *  同时受本守卫与 {@code NoDirectEngineTableAccessTest} 夹制）；
     *  2026-10-18 +1（257）= #143/B5-2（SSE 七帧契约收口）新增
     *  {@code ai.stream.AgentLoopSseFrames}（承流帧序状态机，词表唯一消费方；置于 ai.stream 包
     *  以避开 {@code ArchitectureTest} 的 service 接口命名门，由 {@code SseFrameSingleSourceGateTest} 夹制）；
     *  2026-10-18 +1（257→258）= #143/B5-5a（D-B5-7 OAG 写出面收口）新增
     *  {@code ai.stream.OagSseFrames}（OAG 通道唯一出帧面，与 agent-loop 写出器同包同纪律，
     *  词表消费 {@code common-api} 的 {@code OagSseFrameContract}）；
     *  2026-10-18 +1（258→259）= #143/B5-5b（D-B5-8 WAgent run 流八帧收口）新增
     *  {@code ai.wagent.stream.WAgentSseFrames}（W 通道唯一出帧面）。包位取 wagent 子树而非 {@code ai.stream}：
     *  首版落 {@code ai.stream} 时被 {@code TerminologyArchTest.wAgentClassesResideInWagentPackageOnly}
     *  （F10-01 术语门）抓红 ⇒ 按"改码不改闸"迁入 wagent 包，改类名绕门不在选项内；
     *  2026-10-26 +5（259→264）= #146b（Readiness 八度量供数面收口）新增五个同包取数实现：
     *  {@code ai.wagent.readiness.ReadinessMetricFetchClient} / {@code DataFreshnessMetricSource} /
     *  {@code CaliberConflictCountMetricSource} / {@code KbProfileStockMetricSource} /
     *  {@code CognitiveModelReadinessMetricSource}（跨引擎取数面，见下方名录断言扩 2 枚）。
     *  <p>计数口径 = {@code find src/main/java -name '*.java' | wc -l} 于 2026-10-26 实测 <b>264</b>；
     *  2026-10-27 +1（264→265）＝ #146o 会话运维链新增 {@code AgentSessionOpsController}（#12/#13/#15）。</p>
     */
    private static final int AI_SOURCE_FILE_FLOOR = 265;

    /** 反空集锁④：跨引擎门面引用单源（详设-06 F06-11 forecast.explain 走 cognitive api 常量；
     *  #146b 后 = 3 条，新增两枚同属 cognitive.api 契约枚举/门禁值消费，形态仍是 api 门面）。 */
    private static final Pattern ENGINE_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?com\\.chinacreator\\.gzcm\\.engine\\.([a-z]+)(\\.[A-Za-z0-9_.$]*)\\s*;");
    /** 反空集锁①（正控制边）：wagent DAO 必须看得见唯一基类依赖，证明依赖图真的建了。 */
    private static final String DAO_BASE_CLASS =
            "com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport";

    // ═══ 0) 反空集锁：宇宙非空 + 关键成员在位 ═══

    @Test
    @DisplayName("守卫自检：wagent 导入宇宙非空且三禁代表载体在位（防空集恒绿）")
    void importedUniverseIsNotEmptyAndPinnedMembersArePresent() {
        JavaClasses wagent = importClasses(WAGENT_ROOT);
        Set<String> production = productionNames(wagent);
        assertTrue(production.size() >= WAGENT_PRODUCTION_FLOOR,
                "wagent 生产类数低于实测基线 " + WAGENT_PRODUCTION_FLOOR + "，实测 " + production.size()
                        + "（导入面或分包结构变化 ⇒ 本守卫扫描面已失真，需重新登记）");
        for (String pinned : WAGENT_PINNED_MEMBERS) {
            assertTrue(production.contains(pinned), "关键载体未进入导入宇宙，该红线不可信: " + pinned);
        }
        // 正控制边：依赖图必须真看得见 wagent DAO 的唯一基类依赖，否则下面所有"零违例"都是空转。
        Set<String> evidenceDaoTargets = importClasses(WAGENT_ROOT).stream()
                .filter(javaClass -> (WAGENT_ROOT + ".dao.WAgentEvidenceDao").equals(javaClass.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("WAgentEvidenceDao 不在导入宇宙内"))
                .getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getName())
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(evidenceDaoTargets.contains(DAO_BASE_CLASS),
                "依赖图未见 wagent DAO -> 唯一基类边（实测目标集=" + evidenceDaoTargets
                        + "）—— 导入面失真，本守卫的零违例不可信");
    }

    // ═══ 1) 禁跨 Engine impl ═══

    @Test
    @DisplayName("§6.3 红线：wagent 零跨 Engine impl 依赖（只允许他域 *-api 门面）")
    void wAgentNeverDependsOnForeignEngineImpl() {
        List<String> offenders = violations(importClasses(WAGENT_ROOT), WAGENT_ROOT,
                dependencyTo(FOREIGN_ENGINE_IMPL));
        assertTrue(offenders.isEmpty(),
                "W 域出现跨 Engine impl 依赖（跨册只经 REST/门面，§6.3）:\n" + String.join("\n", offenders));
    }

    @Test
    @DisplayName("§6.3 反向对照：同谓词在 ai 全域也须零 impl 渗漏，且源码级门面引用恰为钉死的 1 条")
    void aiModuleCrossEngineSurfaceIsTheSingleApiFacade() {
        List<String> offenders = violations(importClasses(AI_ROOT), AI_ROOT, dependencyTo(FOREIGN_ENGINE_IMPL));
        assertTrue(offenders.isEmpty(), "ai 全域存在跨 Engine impl 依赖（须先裁属主再落码）:\n"
                + String.join("\n", offenders));

        List<ImportHit> facadeImports = foreignEngineImportsFromSource();
        assertEquals(List.of(
                        "ScenarioToolCatalog.java -> com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths",
                        "CognitiveModelReadinessMetricSource.java -> com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType",
                        "CognitiveModelReadinessMetricSource.java -> com.chinacreator.gzcm.engine.cognitive.api.GateOutcome"),
                facadeImports.stream()
                        .map(hit -> hit.fileName() + " -> " + hit.fqn())
                        .collect(Collectors.toList()),
                "ai 全域跨引擎 import 名录变化（api 门面=允许形态，impl=红线 1）；"
                        + "本断言同时证明字节码侧的'零违例'不是因为扫不到该类");
    }

    // ═══ 2) 禁自建数据访问层 ═══

    @Test
    @DisplayName("铁律 §2.5 #1：wagent 零 JDBC/java.sql/javax.sql 依赖（数据访问唯一形态 = DAO extends JdbcTemplateDaoSupport）")
    void wAgentHoldsNoDirectJdbcOrSqlTypes() {
        List<String> offenders = violations(importClasses(WAGENT_ROOT), WAGENT_ROOT,
                dependencyTo(target -> startsWithAny(target, JDBC_PACKAGES)));
        assertTrue(offenders.isEmpty(),
                "W 域自持 JDBC/SQL 类型（应经 wagent.dao 基类单源）:\n" + String.join("\n", offenders));
    }

    @Test
    @DisplayName("§2.5 反向对照：同一 JDBC 谓词在非 wagent 的 ai 生产类必须抓到实测 8 个真依赖（抓不到=红线 2 是假的）")
    void jdbcPredicateBitesOutsideWAgent() {
        JavaClasses ai = importClasses(AI_ROOT);
        Set<String> holders = owners(ai, dependencyTo(target -> startsWithAny(target, JDBC_PACKAGES)),
                javaClass -> !javaClass.getPackageName().startsWith(WAGENT_ROOT));
        assertTrue(holders.containsAll(JDBC_REVERSE_CONTROL),
                "JDBC 谓词在非 wagent 侧归零/缺项 —— 依赖图未覆盖 JDK/Spring 目标，wagent 的'零违例'不可信。"
                        + "缺: " + difference(JDBC_REVERSE_CONTROL, holders) + " 实测: " + holders);
        assertTrue(holders.size() >= JDBC_REVERSE_CONTROL.size(),
                "JDBC 依赖持有类数低于实测 8（判定面变化需重新登记）: " + holders);
    }

    // ═══ 3) 禁自建调度 ═══

    @Test
    @DisplayName("铁律 §0.6 调度单源：wagent 零 @Scheduled/线程池/Timer/裸 new Thread（调度只经 runtime-task 投递）")
    void wAgentNeverBuildsItsOwnScheduling() {
        JavaClasses wagent = importClasses(WAGENT_ROOT);
        List<String> offenders = new ArrayList<>(
                violations(wagent, WAGENT_ROOT, dependencyTo(target -> startsWithAny(target, SCHEDULE_TYPES))));
        offenders.addAll(bareThreadConstructorViolations(wagent, WAGENT_ROOT));
        assertTrue(offenders.isEmpty(),
                "W 域自建调度（调度单源 = runtime-task；响应内 execution_dispatch=pending 才是合法形态）:\n"
                        + String.join("\n", offenders));
    }

    @Test
    @DisplayName("§0.6 反向对照：同一调度谓词在非 wagent 的 ai 生产类必须抓到实测 5 个真线程池/裸线程持有者")
    void schedulingPredicateBitesOutsideWAgent() {
        JavaClasses ai = importClasses(AI_ROOT);
        Set<String> holders = new TreeSet<>(owners(ai, dependencyTo(target -> startsWithAny(target, SCHEDULE_TYPES)),
                javaClass -> !javaClass.getPackageName().startsWith(WAGENT_ROOT)));
        holders.addAll(bareThreadConstructorOwners(ai, javaClass -> !javaClass.getPackageName().startsWith(WAGENT_ROOT)));
        assertTrue(holders.containsAll(SCHEDULE_REVERSE_CONTROL),
                "调度谓词在非 wagent 侧缺项 —— 判定面对 Executors/ThreadPoolExecutor/new Thread 不敏感，"
                        + "wagent 的'零自建调度'不可信。缺: "
                        + difference(SCHEDULE_REVERSE_CONTROL, holders) + " 实测: " + holders);
    }

    @Test
    @DisplayName("豁免面双向钉死：wagent 对 java.lang.Thread 只允许方法调用（sleep/interrupt），属主集合等值")
    void threadMethodCallsInsidePollingLoopsAreTheOnlyExemption() {
        JavaClasses wagent = importClasses(WAGENT_ROOT);
        Set<String> methodCallOwners = new TreeSet<>();
        List<String> constructorCalls = new ArrayList<>();
        for (JavaClass javaClass : production(wagent)) {
            for (JavaAccess<?> access : javaClass.getAllAccessesFromSelf()) {
                if (!BARE_THREAD_CONSTRUCTOR.equals(access.getTargetOwner().getName())) {
                    continue;
                }
                if (access instanceof JavaConstructorCall) {
                    constructorCalls.add(javaClass.getName() + " -> " + access.getDescription());
                } else {
                    methodCallOwners.add(javaClass.getName());
                }
            }
        }
        assertTrue(constructorCalls.isEmpty(),
                "出现裸 new Thread(...)（红线 3；sleep/interrupt 属方法调用不在禁列）:\n"
                        + String.join("\n", constructorCalls));
        assertEquals(THREAD_METHOD_CALL_OWNERS, methodCallOwners,
                "Thread 方法调用属主集合变化（SSE 轮询/就绪探测豁免名录需同步登记，禁静默扩大）");
        // 锁③：证明该判定确实看见这两个文件（若访问图为空，上面等值断言会因空集假绿 ⇒ 此处显式反证）
        assertFalse(methodCallOwners.isEmpty(), "Thread 访问边集为空 = 字节码访问图没建起来，本用例失去意义");
    }

    // ═══ 判定与工具 ═══

    /** 他域 impl = {@code engine.<其它引擎>.…} 且不在该引擎的 {@code .api.} 门面段下。 */
    private static final Predicate<String> FOREIGN_ENGINE_IMPL = target -> {
        if (!target.startsWith(ENGINE_ROOT)) {
            return false;
        }
        String rest = target.substring(ENGINE_ROOT.length());
        int firstDot = rest.indexOf('.');
        if (firstDot <= 0) {
            return false;
        }
        String engine = rest.substring(0, firstDot);
        if ("ai".equals(engine)) {
            return false;
        }
        return !rest.startsWith(engine + ".api.");
    };

    private static JavaClasses importClasses(String rootPackage) {
        return new ClassFileImporter().importPackages(rootPackage);
    }

    private static boolean isTestSide(JavaClass javaClass) {
        String simpleName = javaClass.getSimpleName();
        return simpleName.endsWith("Test") || simpleName.endsWith("IT")
                || javaClass.getPackageName().endsWith(".arch");
    }

    private static List<JavaClass> production(JavaClasses classes) {
        List<JavaClass> out = new ArrayList<>();
        classes.stream().filter(javaClass -> !javaClass.isAnnotation())
                .filter(javaClass -> !isTestSide(javaClass))
                .forEach(out::add);
        return out;
    }

    private static Set<String> productionNames(JavaClasses classes) {
        return production(classes).stream().map(JavaClass::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Predicate<Dependency> dependencyTo(Predicate<String> targetName) {
        return dependency -> targetName.test(dependency.getTargetClass().getName());
    }

    private static boolean startsWithAny(String target, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (target.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 命中集合（owner -> 依赖描述），限定属主必须在 ownerPackagePrefix 下。 */
    private static List<String> violations(JavaClasses classes, String ownerPackagePrefix,
            Predicate<Dependency> forbidden) {
        List<String> out = new ArrayList<>();
        for (JavaClass javaClass : production(classes)) {
            if (!javaClass.getPackageName().startsWith(ownerPackagePrefix)) {
                continue;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                if (forbidden.test(dependency)) {
                    out.add(javaClass.getName() + " -> " + dependency.getDescription());
                }
            }
        }
        return out;
    }

    /** 命中属主集合（反向对照用），ownerFilter 决定统计哪一侧。 */
    private static Set<String> owners(JavaClasses classes, Predicate<Dependency> forbidden,
            Predicate<JavaClass> ownerFilter) {
        Set<String> out = new TreeSet<>();
        for (JavaClass javaClass : production(classes)) {
            if (!ownerFilter.test(javaClass)) {
                continue;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                if (forbidden.test(dependency)) {
                    out.add(javaClass.getName());
                    break;
                }
            }
        }
        return out;
    }

    private static List<String> bareThreadConstructorViolations(JavaClasses classes, String ownerPackagePrefix) {
        List<String> out = new ArrayList<>();
        for (JavaClass javaClass : production(classes)) {
            if (!javaClass.getPackageName().startsWith(ownerPackagePrefix)) {
                continue;
            }
            out.addAll(threadConstructorCalls(javaClass));
        }
        return out;
    }

    private static Set<String> bareThreadConstructorOwners(JavaClasses classes, Predicate<JavaClass> ownerFilter) {
        Set<String> out = new TreeSet<>();
        for (JavaClass javaClass : production(classes)) {
            if (ownerFilter.test(javaClass) && !threadConstructorCalls(javaClass).isEmpty()) {
                out.add(javaClass.getName());
            }
        }
        return out;
    }

    private static List<String> threadConstructorCalls(JavaClass javaClass) {
        List<String> out = new ArrayList<>();
        for (JavaAccess<?> access : javaClass.getAllAccessesFromSelf()) {
            if (access instanceof JavaConstructorCall
                    && BARE_THREAD_CONSTRUCTOR.equals(access.getTargetOwner().getName())) {
                out.add(javaClass.getName() + " -> " + access.getDescription());
            }
        }
        return out;
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> out = new LinkedHashSet<>(expected);
        out.removeAll(actual);
        return out;
    }

    /** 源码级 import 扫描（锁④）：只认真 import 语句，注释/javadoc 行首非 import 天然不匹配。 */
    private static List<ImportHit> foreignEngineImportsFromSource() {
        Path root = aiSourceRoot();
        List<ImportHit> hits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> sources = paths.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted().collect(Collectors.toList());
            assertEquals(AI_SOURCE_FILE_FLOOR, sources.size(),
                    "ai 模块源码文件数与登记基线不一致（2026-10-18 实测 259）—— 扫描面失真则 import 名录不可信；"
                            + "实测 " + sources.size());
            for (Path source : sources) {
                for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                    Matcher matcher = ENGINE_IMPORT.matcher(line);
                    if (!matcher.matches()) {
                        continue;
                    }
                    if ("ai".equals(matcher.group(2))) {
                        continue;
                    }
                    hits.add(new ImportHit(source.getFileName().toString(),
                            ENGINE_ROOT + matcher.group(2) + matcher.group(3)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("ai 源码扫描失败: " + e.getMessage(), e);
        }
        return hits;
    }

    /** surefire 工作目录在不同构建入口下可能是模块根或仓根，双候选 + 失败即点名 cwd。 */
    private static Path aiSourceRoot() {
        List<String> candidates = List.of(
                "src/main/java/com/chinacreator/gzcm/engine/ai",
                "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai");
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("ai 源码扫描根缺失，cwd=" + Path.of("").toAbsolutePath()
                + " 候选=" + candidates);
    }

    private record ImportHit(String fileName, String fqn) {
    }
}

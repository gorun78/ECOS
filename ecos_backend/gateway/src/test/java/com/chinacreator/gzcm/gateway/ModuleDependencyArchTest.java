package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    @BeforeAll
    static void setUp() {
        // 1) 优先扫 target/classes（编译态精准）
        // 路径查找约定：ecos_backend/{module}/{module}-impl/target/classes 形式
        List<Path> candidatePaths = new ArrayList<>();
        candidatePaths.add(Paths.get("workspace/workspace-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/data-engine/data-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/ontology-engine/ontology-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/kb-engine/kb-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/cognitive-engine/cognitive-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/ai-engine/ai-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("engine/security-engine/security-engine-impl/target/classes"));
        candidatePaths.add(Paths.get("services/sysman/impl/sysman-impl/target/classes"));
        candidatePaths.add(Paths.get("services/datanet/target/classes"));
        candidatePaths.add(Paths.get("services/buszhi/buszhi-impl/target/classes"));
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
}
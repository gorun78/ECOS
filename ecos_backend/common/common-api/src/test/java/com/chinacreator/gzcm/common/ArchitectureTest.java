package com.chinacreator.gzcm.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ECOS 架构保护测试 — 5条铁律，精修阶段防崩塌。
 *
 * <h3>DIKW 分层模型</h3>
 * <pre>
 *   W层 (Wisdom)  — worldmodel (世界模型), cognitive (认知引擎)
 *   K层 (Knowledge)— buszhi (业务智能), aimod (AI模块)
 *   I层 (Information)— dccheng (数据治理/本体设计)
 *   D层 (Data)     — datanet (数据网络/管线)
 * </pre>
 *
 * <p>依赖方向: W → K → I → D (上层可依赖下层，下层禁止依赖上层)</p>
 *
 * @author ECOS Architecture Guard
 */
public class ArchitectureTest {

    private static JavaClasses classes;
    private static final Path PROJECT_ROOT = findProjectRoot();

    /**
     * 向上查找项目根目录（包含 pom.xml 和 common 子目录）。
     */
    private static Path findProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml")) && Files.exists(dir.resolve("common"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get(".").toAbsolutePath();
    }

    @BeforeAll
    static void importClasses() {
        List<Path> classPaths = new ArrayList<>();

        // 扫描所有子模块的 target/classes 目录
        String[] modules = {
            "common/common-api",
            "runtime/runtime-core",
            "runtime/runtime-security",
            "runtime/runtime-task",
            "runtime/runtime-monitor",
            "runtime/runtime-crypto",
            "runtime/hermes-engine",
            "sysman/sysman-api",
            "sysman/sysman-impl",
            "sysman/sysman-boot",
            "datanet/datanet-api",
            "datanet/datanet-impl",
            "datanet/datanet-boot",
            "buszhi/buszhi-impl",
            "dccheng/dccheng-api",
            "dccheng/dccheng-impl",
            "cognitive/cognitive-api",
            "cognitive/cognitive-impl",
            "aimod/aimod-impl",
            "worldmodel/worldmodel-impl",
            "workspace/workspace-impl",
            "portal/portal-impl",
            "market/market-impl",
            "gateway"
        };

        for (String module : modules) {
            Path targetClasses = PROJECT_ROOT.resolve(module).resolve("target/classes");
            if (Files.isDirectory(targetClasses)) {
                classPaths.add(targetClasses);
            }
        }

        if (!classPaths.isEmpty()) {
            try {
                classes = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPaths(classPaths.toArray(new Path[0]));
            } catch (Exception e) {
                System.err.println("WARNING: 文件导入失败，回退到 classpath 导入: " + e.getMessage());
            }
        }

        if (classes == null || classes.size() == 0) {
            // 最终回退：只扫 classpath
            classes = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.chinacreator.gzcm");
        }
        System.out.println("Imported " + classes.size() + " classes for architecture analysis.");
    }

    // ================================================================
    // 铁律 1: D层不能依赖K层/W层
    // ================================================================
    @Test
    public void D层不能依赖K层_W层() {
        noClasses()
                .that().resideInAnyPackage(
                        "..datanet..",
                        "..dccheng.."
                )
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..buszhi..",
                        "..aimod..",
                        "..worldmodel..",
                        "..cognitive.."
                )
                .because("D层（datanet/dccheng）禁止依赖K层（buszhi/aimod）和W层（worldmodel/cognitive）")
                .check(classes);
    }

    // ================================================================
    // 铁律 2: Controller只能调自己模块的Service
    // 跨模块访问必须通过事件(PipelineEvent)或API调用，不能直接import Service
    // ================================================================
    @Test
    public void Controller只能调自己的Service() {
        // 禁止 datanet 的 Controller 访问非 datanet 的 Service
        noClasses()
                .that().resideInAnyPackage("..datanet..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..dccheng..service..",
                        "..dccheng..",
                        "..buszhi..",
                        "..aimod..",
                        "..worldmodel..",
                        "..cognitive..",
                        "..sysman..",
                        "..workspace..",
                        "..portal.."
                )
                .because("datanet Controller 只能调用 datanet 自己的 Service")
                .check(classes);

        // 禁止 dccheng 的 Controller 访问非 dccheng 的 Service
        noClasses()
                .that().resideInAnyPackage("..dccheng..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..datanet..",
                        "..buszhi..",
                        "..aimod..",
                        "..worldmodel..",
                        "..cognitive..",
                        "..sysman..",
                        "..workspace..",
                        "..portal.."
                )
                .because("dccheng Controller 只能调用 dccheng 自己的 Service")
                .check(classes);

        // 禁止 buszhi 的 Controller 访问非 buszhi 的 Service
        noClasses()
                .that().resideInAnyPackage("..buszhi..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..datanet..",
                        "..dccheng..",
                        "..aimod..",
                        "..worldmodel..",
                        "..cognitive..",
                        "..sysman..",
                        "..workspace..",
                        "..portal.."
                )
                .because("buszhi Controller 只能调用 buszhi 自己的 Service")
                .check(classes);

        // 禁止 aimod 的 Controller 访问非 aimod 的 Service
        noClasses()
                .that().resideInAnyPackage("..aimod..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..datanet..",
                        "..dccheng..",
                        "..buszhi..",
                        "..worldmodel..",
                        "..cognitive..",
                        "..sysman..",
                        "..workspace..",
                        "..portal.."
                )
                .because("aimod Controller 只能调用 aimod 自己的 Service")
                .check(classes);

        // 禁止 worldmodel 的 Controller 访问非 worldmodel 的 Service
        noClasses()
                .that().resideInAnyPackage("..worldmodel..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..datanet..",
                        "..dccheng..",
                        "..buszhi..",
                        "..aimod..",
                        "..cognitive..",
                        "..workspace..",
                        "..portal.."
                )
                .because("worldmodel Controller 只能调用 worldmodel 自己的 Service")
                .check(classes);

        // 禁止 cognitive 的 Controller 访问非 cognitive 的 Service
        noClasses()
                .that().resideInAnyPackage("..cognitive..controller..")
                .should().accessClassesThat()
                .resideInAnyPackage(
                        "..datanet..",
                        "..dccheng..",
                        "..buszhi..",
                        "..aimod..",
                        "..worldmodel..",
                        "..workspace..",
                        "..portal.."
                )
                .because("cognitive Controller 只能调用 cognitive 自己的 Service")
                .check(classes);
    }

    // ================================================================
    // 铁律 3: 禁止新增Maven模块
    // ================================================================
    @Test
    public void 禁止新增Maven模块() {
        Path pomFile = PROJECT_ROOT.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            System.out.println("⚠ pom.xml 不存在，跳过模块数检查");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(pomFile);
            // 只统计默认 <modules> 块中的 <module>（非 profile 内的）
            int moduleCount = 0;
            boolean inDefaultModules = false;
            boolean inProfiles = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<profiles>")) {
                    inProfiles = true;
                }
                if (trimmed.equals("<modules>") && !inProfiles) {
                    inDefaultModules = true;
                    continue;
                }
                if (trimmed.equals("</modules>") && inDefaultModules) {
                    inDefaultModules = false;
                    break; // 只统计第一个（默认）modules块
                }
                if (inDefaultModules && trimmed.startsWith("<module>") && !trimmed.startsWith("<!--")) {
                    moduleCount++;
                }
            }

            int baselineModules = 13;
            if (moduleCount > baselineModules) {
                throw new AssertionError(
                    String.format("❌ 禁止新增Maven模块！当前: %d, 基线: %d", moduleCount, baselineModules));
            }
            System.out.printf("✓ Maven 模块数检查通过: %d (基线: %d)%n", moduleCount, baselineModules);
        } catch (IOException e) {
            throw new RuntimeException("无法读取 pom.xml", e);
        }
    }

    // ================================================================
    // 铁律 4: 禁止新增Docker容器
    // ================================================================
    @Test
    public void 禁止新增Docker容器() {
        // 基线容器清单（按 docker-compose 文件分别定义）
        Map<String, Integer> baselineImages = new LinkedHashMap<>();
        baselineImages.put("docker-compose-standard.yml", 2);   // postgres + gateway
        baselineImages.put("docker-compose-enterprise.yml", 4); // postgres + neo4j + minio + gateway
        baselineImages.put("docker-compose-flagship.yml", 5);   // postgres + neo4j + minio + doris-fe + doris-be + gateway
        baselineImages.put("docker-compose-doris.yml", 2);      // doris-fe + doris-be

        boolean violation = false;
        for (Map.Entry<String, Integer> entry : baselineImages.entrySet()) {
            Path composeFile = PROJECT_ROOT.resolve(entry.getKey());
            if (!Files.exists(composeFile)) {
                continue;
            }
            try {
                long count = Files.readAllLines(composeFile).stream()
                        .filter(l -> l.trim().startsWith("image:"))
                        .count();
                int baseline = entry.getValue();
                if (count > baseline) {
                    violation = true;
                    System.err.printf("❌ %s: %d images (基线: %d)%n",
                            entry.getKey(), count, baseline);
                } else {
                    System.out.printf("✓ %s: %d images (基线: %d)%n",
                            entry.getKey(), count, baseline);
                }
            } catch (IOException e) {
                System.err.println("WARNING: 无法读取 " + entry.getKey());
            }
        }

        if (violation) {
            throw new AssertionError("禁止新增Docker容器！以上 compose 文件中 image 数量超出基线");
        }
    }

    // ================================================================
    // 铁律 5: Controller必须通过Service访问数据库，不能直接JdbcTemplate
    // ================================================================
    @Test
    public void Controller必须通过Service访问数据库_不能直接JdbcTemplate() {
        noClasses()
                .that().resideInAnyPackage("..controller..")
                .should().accessClassesThat()
                .resideInAPackage("org.springframework.jdbc..")
                .because("Controller 禁止直接使用 JdbcTemplate，必须通过 Service 层访问数据库")
                .check(classes);
    }
}

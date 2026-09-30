package com.chinacreator.gzcm.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * ECOS 架构保护测试 — 铁律守护（现代化版，PMO-E1）。
 *
 * <h3>更新历史</h3>
 * <ul>
 *   <li>PMO-E1 (2026-08-24): 删除旧 DIKW 分层断言（铁律1/2，已被 D1 六引擎 ArchitectureTest 取代），
 *       更新 modules 清单到五引擎现状，compose 基线指向 ecos-docker/，铁律5 已启用（PMO-E2 完成 33 Controller JdbcTemplate 下沉）</li>
 * </ul>
 *
 * <h3>当前守护的铁律</h3>
 * <ol>
 *   <li>铁律3: 禁止新增 Maven 模块（基线 11）</li>
 *   <li>铁律4: 禁止新增 Docker 容器（基线按 ecos-docker/ 三版本）</li>
 *   <li>铁律5: Controller 必须通过 Service 访问数据库（PMO-E2 已完成 33 Controller JdbcTemplate 下沉）</li>
 * </ol>
 *
 * <p>引擎间依赖边界、包结构、命名规范由 D1 六引擎 ArchitectureTest 独立守护。
 *
 * @author ECOS Architecture Guard
 */
public class ArchitectureTest {

    private static JavaClasses classes;
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path ECOS_ROOT = findEcosRoot();

    /**
     * 向上查找 ecos_backend 项目根目录。
     *
     * <p>PMO-74 H11-T3 修复：旧判据是「pom.xml + common 子目录」，但 PMO-E1/PMO-C 之后
     * 顶层 `common/` 已重命名为 `runtime/common-api`，该目录全仓不存在，判据永远匹配失败，
     * PROJECT_ROOT 退化到当前工作目录，导致模块清单整体失配（实测铁律5 空转）。
     * 新判据用 reactor 中稳定存在的 `gateway` 模块目录，与 ModuleDependencyArchTest 同口径。
     */
    private static Path findProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("gateway"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get(".").toAbsolutePath();
    }

    /**
     * 向上查找 ECOS 仓库根目录（包含 ecos_backend 和 ecos-docker）。
     */
    private static Path findEcosRoot() {
        Path dir = PROJECT_ROOT;
        while (dir != null) {
            if (Files.exists(dir.resolve("ecos-docker"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return PROJECT_ROOT.getParent();
    }

    @BeforeAll
    static void importClasses() {
        List<Path> classPaths = new ArrayList<>();

        // PMO-74 H11-T3：模块清单不再硬编码。旧数组本身就是漂移源——`common/common-api`、
        // `sysman/sysman-*`、`buszhi/buszhi-impl` 在 PMO-E1/PMO-C 重命名后已全部失配，
        // 且缺 services/{datanet,dccheng,aiming} 与 services/sysman|buszhi/impl/*，
        // 即使 PROJECT_ROOT 正确也只覆盖部分 Controller。
        // 现改为遍历 PROJECT_ROOT 下所有已构建的 `target/classes`，跳过 archive/（Q3 归档区）。
        Path archiveRoot = PROJECT_ROOT.resolve("archive");
        try (java.util.stream.Stream<Path> walk = Files.walk(PROJECT_ROOT)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> !p.startsWith(archiveRoot))
                    .filter(p -> p.getFileName().toString().equals("classes"))
                    .filter(p -> p.getParent() != null
                            && p.getParent().getFileName().toString().equals("target"))
                    .sorted()
                    .forEach(classPaths::add);
        } catch (IOException e) {
            System.err.println("WARNING: 模块 target/classes 遍历失败: " + e.getMessage());
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
            classes = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.chinacreator.gzcm");
        }
        System.out.println("Imported " + classes.size() + " classes for architecture analysis.");
    }

    // ================================================================
    // 铁律 3: 禁止新增 Maven 模块
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
                    break;
                }
                if (inDefaultModules && trimmed.startsWith("<module>") && !trimmed.startsWith("<!--")) {
                    moduleCount++;
                }
            }

            // PMO-E1: baseline 13→11（D2 删 6 空壳 + D2 前已减到 11）
            // PMO-74 H11-T2: 11→12 —— `services/agent-service` 经裁决归入 reactor 以消除幽灵依赖
            // （ai-engine-impl / aiming 显式依赖它，此前未挂 reactor 会解析 .m2 陈旧 SNAPSHOT）。
            // 与 _win_tasks/check-legacy-modules.ps1 的 baselineCount 同批更正。
            int baselineModules = 12;
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
    // 铁律 4: 禁止新增 Docker 容器
    // PMO-E1: compose 文件从 ecos_backend/ 旧文件改为 ecos-docker/ 下新文件
    // 使用 base + overlay 结构：image 数 = base + edition 文件中的 image 总和
    // ================================================================
    @Test
    public void 禁止新增Docker容器() {
        Path dockerDir = ECOS_ROOT.resolve("ecos-docker");
        if (!Files.isDirectory(dockerDir)) {
            System.out.println("⚠ ecos-docker/ 不存在，跳过 Docker 容器检查");
            return;
        }

        // 基线容器清单（base + edition overlay 的 image 总和）
        // standard: base(postgres:16) + standard(0) = 1
        // enterprise: base(postgres:16) + enterprise(neo4j:5) = 2
        // ultimate: base(postgres:16) + ultimate(neo4j:5 + doris-fe + doris-be) = 4
        Map<String[], Integer> baselineImages = new LinkedHashMap<>();
        baselineImages.put(new String[]{"docker-compose.base.yml", "docker-compose.standard.yml"}, 1);
        baselineImages.put(new String[]{"docker-compose.base.yml", "docker-compose.enterprise.yml"}, 2);
        baselineImages.put(new String[]{"docker-compose.base.yml", "docker-compose.ultimate.yml"}, 4);

        boolean violation = false;
        for (Map.Entry<String[], Integer> entry : baselineImages.entrySet()) {
            String[] files = entry.getKey();
            int baseline = entry.getValue();
            long totalCount = 0;
            StringBuilder fileNames = new StringBuilder();

            for (String fname : files) {
                Path composeFile = dockerDir.resolve(fname);
                if (Files.exists(composeFile)) {
                    try {
                        long count = Files.readAllLines(composeFile).stream()
                                .filter(l -> l.trim().startsWith("image:"))
                                .count();
                        totalCount += count;
                        if (fileNames.length() > 0) fileNames.append(" + ");
                        fileNames.append(fname);
                    } catch (IOException e) {
                        System.err.println("WARNING: 无法读取 " + fname);
                    }
                }
            }

            if (totalCount > baseline) {
                violation = true;
                System.err.printf("❌ %s: %d images (基线: %d)%n",
                        fileNames, totalCount, baseline);
            } else {
                System.out.printf("✓ %s: %d images (基线: %d)%n",
                        fileNames, totalCount, baseline);
            }
        }

        if (violation) {
            throw new AssertionError("禁止新增Docker容器！以上 compose 文件中 image 数量超出基线");
        }
    }

    // ================================================================
    // 铁律 5: Controller必须通过Service访问数据库，不能直接JdbcTemplate
    // PMO-E2: @Disabled 已移除 — 33 Controller JdbcTemplate 已全部下沉到 Service 层
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

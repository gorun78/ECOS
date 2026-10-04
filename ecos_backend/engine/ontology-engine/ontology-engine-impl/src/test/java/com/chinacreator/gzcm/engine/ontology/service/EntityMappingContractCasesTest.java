package com.chinacreator.gzcm.engine.ontology.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F03-05 实体映射契约 · 目标态语义用例（W71/C55，REQ-ONTO-03 + PRD-03 §3.3 的<b>结构性</b>半）。
 *
 * <p>PRD-03 §3.3 三用例中"换列生效 / 指向不存在列产生 INVALID_MAPPING 且不实例化"是
 * <b>kb 侧实例抽取</b>行为，已由 kb-engine 的 {@code KbMappingSkipTest} / {@code KbDryRunContractTest}
 * 承接（C4 + Q2 语义 + 纯 Mockito 零网络）。本类只落<b>归属 ontology 引擎</b>、离线可验的三条结构护栏，
 * 与 {@code EntityMappingContractFrozenTest} 的字段集 ratchet 互补、不重复：
 * <ol>
 *   <li><b>硬规则③ 映射不落 kb 表</b>（ArchUnit/grep 断言）：kb-engine 映射契约仅<b>内存装配</b>，
 *       kb 源码不得出现对"映射表"的 {@code INSERT INTO …mapping} 落库语句（防契约数据回写 kb）。</li>
 *   <li><b>E.5 接缝不越界</b>：ontology 映射读侧（契约面）不得直查业务域
 *       {@code information_schema / ecos_dw}（V3 列存在性只经 data-engine REST，见设计-03 D.4/E.5）。</li>
 *   <li><b>schema diff 只增</b>（正向）：契约基线文件的冻结字段集是"只增"基线，
 *       删字段由 {@code EntityMappingContractFrozenTest} 拦截；本类补"只增"的正向白线——
 *       新增字段允许存在、基线集仍完整（防未来"改名换皮"绕过冻结 ratchet）。</li>
 * </ol>
 *
 * <p>纯文件 walk，不触库/Spring/网络。
 */
class EntityMappingContractCasesTest {

    /** kb 源码落库到"映射表"的形态（硬规则③ 反例）。 */
    private static final Pattern KB_MAPPING_INSERT =
            Pattern.compile("INSERT\\s+INTO[^;]*?\\bmapping\\b", Pattern.CASE_INSENSITIVE);

    /** ontology 契约读侧越界直查业务域列元数据/数仓（E.5 反例）。 */
    private static final Pattern ONTOLOGY_CROSS_DOMAIN =
            Pattern.compile("information_schema|\\becos_dw\\b", Pattern.CASE_INSENSITIVE);

    private Path root() {
        Path p = Path.of(".").toAbsolutePath().normalize();
        int up = 0;
        while (p != null && up < 8) {
            if (Files.isDirectory(p.resolve("docs")) && Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
            p = p.getParent();
            up++;
        }
        fail("未找到 ECOS 根（含 docs/ + ecos_backend/）");
        return null;
    }

    @Test
    @DisplayName("硬规则③：kb-engine 映射契约仅内存装配，无 INSERT INTO …mapping 落库语句")
    void kbMappingNotPersisted() {
        Path kbSrc = root().resolve(Path.of(
                "ecos_backend", "engine", "kb-engine", "kb-engine-impl", "src", "main", "java"));
        if (!Files.isDirectory(kbSrc)) {
            fail("kb-engine-impl 源码根未发现");
        }
        for (Path f : regularFiles(kbSrc)) {
            if (!f.toString().endsWith(".java")) continue;
            String s = read(f);
            if (s == null) continue;
            if (KB_MAPPING_INSERT.matcher(s).find()) {
                fail("kb 侧出现对映射表的 INSERT 落库语句（违 F03-05 硬规则③：映射不落 kb 表，仅内存装配）: "
                        + f);
            }
        }
    }

    @Test
    @DisplayName("E.5 接缝：ontology 契约读侧不越界直查 information_schema / ecos_dw")
    void ontologyReadSideNoCrossDomain() {
        Path c = Path.of("src", "main", "java",
                "com", "chinacreator", "gzcm", "engine", "ontology",
                "controller", "OntologyEntityMappingController.java");
        String src = read(c);
        if (src == null) {
            fail("OntologyEntityMappingController 源码应可读");
        }
        assertFalse(ONTOLOGY_CROSS_DOMAIN.matcher(src).find(),
                "契约读侧不得直查 information_schema / ecos_dw（列存在性 V3 只经 data-engine REST，E.5 接缝）");
    }

    @Test
    @DisplayName("schema diff 只增：契约基线冻结字段集完整且允许新增（防改名换皮绕过冻结 ratchet）")
    void contractBaselineOnlyAdditive() {
        String json = read(root().resolve(Path.of(
                "docs", "30-设计", "contracts", "ontology-entity-mappings.schema.json")));
        assertTrue0(json != null, "契约基线文件应存在（F03-05 纳入版本控制）");
        // 冻结集闭包（不可删/不可改义）——即便 schema 加新字段，这 7 个键语义必须保持
        for (String key : new String[]{"frozenFields", "entityCode", "datasetId", "resourceName",
                "materialized", "fieldMappings", "source", "target"}) {
            assertTrue0(json.contains("\"" + key + "\""),
                    "基线仍须含关键键 " + key + "（删除/改名应在 FrozenTest 报，而非在此静默）");
        }
    }

    /** 无 AssertJ 时的断言别名，保持本类可读性一致。 */
    private static void assertTrue0(boolean cond, String msg) {
        if (!cond) fail(msg);
    }

    private static java.util.List<Path> regularFiles(Path root) {
        try (var st = Files.walk(root)) {
            return st.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String read(Path f) {
        try {
            return Files.isRegularFile(f) ? new String(Files.readAllBytes(f), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

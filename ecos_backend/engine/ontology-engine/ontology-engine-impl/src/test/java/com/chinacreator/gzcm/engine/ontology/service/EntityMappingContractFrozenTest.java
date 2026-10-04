package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F03-05 实体映射契约冻结（W70/C54，REQ-ONTO-03「只增不改」硬规则①）。
 *
 * <p>基线文件 {@code docs/30-设计/contracts/ontology-entity-mappings.schema.json}
 * 冻结 PRD-03 §3.1 / 设计-03 D.2 的响应字段集。本测试做<b>只增不改</b>的单向护栏：
 * <ol>
 *   <li>基线基路径 {@code GET /api/v1/ontology/entity-mappings} 必须存在（契约可发现）；</li>
 *   <li>基线要求的每个字段名必须仍由响应 VO {@link OntologyMappingVO} 承载——
 *       字段被删除或改名即构建失败（"只删/改名 = 回归"）；</li>
 *   <li>基线文件字段集必须完整（防校验本身被悄悄清空造成假绿）。</li>
 * </ol>
 *
 * <p>纯反射 + 文件读，不触库/Spring/网络。"只增"（正向加字段）本测试不拦截，符合"只增不改"语义。
 */
class EntityMappingContractFrozenTest {

    /** 基线冻结的 entityMapping 字段集（= 基线 JSON 的 frozenFields）。 */
    private static final List<String> FROZEN_ENTITY_FIELDS = List.of(
            "entityCode", "datasetId", "resourceName", "materialized", "fieldMappings");

    private final Set<String> voFields = voFieldNames(OntologyMappingVO.class);

    @Test
    @DisplayName("基线冻结字段集未被 VO 丢弃（删/改名即红）：只增不改单向护栏")
    void frozenFieldsStillCarriedByVo() {
        for (String f : FROZEN_ENTITY_FIELDS) {
            assertTrue(voFields.contains(f),
                    "冻结契约字段 " + f + " 已从 OntologyMappingVO 移除/改名（违反只增不改，REQ-ONTO-03）");
        }
    }

    @Test
    @DisplayName("基线契约文件存在且 entityMappings/fieldMappings 顶层键齐备（防校验空洞假绿）")
    void baselineContractFilePresentAndNonTrivial() {
        String json = read(ecsRoot().resolve(Path.of(
                "docs", "30-设计", "contracts", "ontology-entity-mappings.schema.json")));
        assertTrue(json != null, "契约基线文件应存在（F03-05 纳入版本控制）: docs/30-设计/contracts/ontology-entity-mappings.schema.json");
        for (String key : List.of("entityCode", "datasetId", "resourceName", "materialized",
                "fieldMappings", "source", "target")) {
            assertTrue(json.contains("\"" + key + "\""),
                    "基线 JSON 应显式声明冻结字段 " + key + "（防 base contract regression）");
        }
    }

    @Test
    @DisplayName("契约基路径稳定：controller 仍挂在 GET /api/v1/ontology/entity-mappings")
    void baseMappingPathStable() {
        // surefire CWD = ontology-engine-impl → controller 直读 CWD/src/main/java
        String src = read(Path.of("src", "main", "java",
                "com", "chinacreator", "gzcm", "engine", "ontology",
                "controller", "OntologyEntityMappingController.java"));
        assertTrue(src != null, "OntologyEntityMappingController 源码应可读");
        assertTrue(src.contains("@RequestMapping(\"/api/v1/ontology\")"), "契约基前缀应保留 /api/v1/ontology");
        assertTrue(src.contains("@GetMapping(\"/entity-mappings\")"), "契约端点 /entity-mappings 应保留（只增不改）");
        assertFalse(src.contains("INSERT INTO"), "契约端点必须只读，不得写 kb/映射表");
    }

    // ── 工具 ──

    /** 反射枚举 VO 声明字段名（含继承链）。 */
    private static Set<String> voFieldNames(Class<?> c) {
        Set<String> names = new HashSet<>();
        for (Class<?> t = c; t != null && t != Object.class; t = t.getSuperclass()) {
            for (java.lang.reflect.Field f : t.getDeclaredFields()) {
                names.add(f.getName());
            }
        }
        return names;
    }

    /** surefire CWD = ontology-engine-impl；ECOS 根 = 首个同时含 docs/ 与 ecos_backend/ 的祖先。 */
    static Path ecsRoot() {
        Path p = Path.of(".").toAbsolutePath().normalize();
        int up = 0;
        while (p != null && up < 8) {
            if (Files.isDirectory(p.resolve("docs")) && Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
            p = p.getParent();
            up++;
        }
        fail("未找到 ECOS 根（需同时含 docs/ 与 ecos_backend/）");
        return Path.of(".");
    }

    static String read(Path f) {
        try {
            return Files.isRegularFile(f) ? new String(Files.readAllBytes(f), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

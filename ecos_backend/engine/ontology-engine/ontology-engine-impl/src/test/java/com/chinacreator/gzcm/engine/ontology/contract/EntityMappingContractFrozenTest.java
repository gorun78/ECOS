package com.chinacreator.gzcm.engine.ontology.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F03-05 rule 1 — 实体映射契约冻结
 * （{@code docs/30-设计/contracts/ontology-entity-mappings.schema.json}）。
 *
 * <p>CI 单测：比对基线，字段删除 / 改名 / 类型变更 → 构建失败。本测试锁定：</p>
 * <ul>
 *   <li>五顶层字段 {@code entityCode / datasetId / resourceName / materialized / fieldMappings} 存在；</li>
 *   <li>三 field-mapping 子字段 {@code entityField / physicalColumn / transform} 存在；</li>
 *   <li>{@code entityCode / entityField / physicalColumn} 带 {@code minLength: 1} 约束。</li>
 * </ul>
 *
 * <p>路径解析：仓库根为同时含 {@code pom.xml} 与 {@code ecos_backend} 的最近祖先；
 * 契约文件 = {@code <root>/docs/30-设计/contracts/ontology-entity-mappings.schema.json}。
 * 以 {@code Files.readString}（Java 11+）UTF-8 读入。</p>
 */
class EntityMappingContractFrozenTest {

    // 契约基线唯一 ASCII 基名（docs/ 下的中文目录名随平台编码/locale 而变，故按基名 glob 定位，
    // 不在源码嵌入中文路径字面量，避免 Windows GBK 默认编码下源码歧义）。
    private static final String CONTRACT_BASENAME = "ontology-entity-mappings.schema.json";

    @Test
    void schemaBaselineFileExists() {
        Path schema = locateSchema();
        assertTrue(Files.exists(schema), "F03-05 契约基线文件缺失：" + schema.toAbsolutePath());
        try {
            assertFalse(Files.readString(schema, StandardCharsets.UTF_8).isBlank(), "schema 基线文件为空");
        } catch (IOException e) {
            fail("读取 schema 基线失败：" + e.getMessage());
        }
    }

    @Test
    void requiredTopLevelFieldsAreFrozen() {
        String s = readBaseline();
        for (String field : new String[]{"entityCode", "datasetId", "resourceName", "materialized", "fieldMappings"}) {
            assertTrue(s.contains("\"" + field + "\""),
                    "F03-05 契约冻结字段 '" + field + "' 缺失（rule 1：不许删/改名）");
        }
    }

    @Test
    void requiredFieldMappingSubFieldsAreFrozen() {
        String s = readBaseline();
        for (String field : new String[]{"entityField", "physicalColumn", "transform"}) {
            assertTrue(s.contains("\"" + field + "\""),
                    "F03-05 fieldMappings 冻结字段 '" + field + "' 缺失");
        }
    }

    @Test
    void minLengthOneEnforcedOnIdentifiers() {
        String s = readBaseline();
        assertHasMinLengthNear("entityCode", s);
        assertHasMinLengthNear("entityField", s);
        assertHasMinLengthNear("physicalColumn", s);
    }

    /** 在字段 key 之后 400 字符窗口内必须出现 minLength 约束（就近语义）。 */
    private static void assertHasMinLengthNear(String field, String s) {
        int fieldIdx = s.indexOf("\"" + field + "\"");
        assertTrue(fieldIdx >= 0, "字段 '" + field + "' 未定位到");
        int windowEnd = Math.min(s.length(), fieldIdx + 400);
        int minLenIdx = s.substring(fieldIdx, windowEnd).indexOf("minLength");
        assertTrue(minLenIdx >= 0, "字段 '" + field + "' 缺少 minLength: 1 约束");
    }

    private static String readBaseline() {
        try {
            return Files.readString(locateSchema(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("读取 schema 基线失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 契约基线 = 仓库根 {@code docs/} 子树内唯一名为 {@link #CONTRACT_BASENAME} 的文件。
     * 沿 {@code user.dir} 上溯至<b>同时含 {@code docs} 与 {@code ecos_backend} 目录</b>的祖先
     * （即仓库根），再深搜该名唯一的 {@code .json}。
     * <p>不嵌入中文目录名（Windows GBK 默认编码下源码字面量会被歧义化）。</p>
     */
    private static Path locateSchema() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && cur != null; depth++) {
            Path docsDir = cur.resolve("docs");
            if (Files.isDirectory(docsDir) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                try (Stream<Path> walk = Files.walk(docsDir)) {
                    Path hit = walk
                            .filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().equals(CONTRACT_BASENAME))
                            .findFirst()
                            .orElse(null);
                    if (hit != null) return hit;
                } catch (IOException ignored) { /* walk 失败 → 走 parents */ }
            }
            cur = cur.getParent();
        }
        fail("F03-05 契约基线未定位到（未找到含 docs 与 ecos_backend 的祖先）");
        return null;
    }
}

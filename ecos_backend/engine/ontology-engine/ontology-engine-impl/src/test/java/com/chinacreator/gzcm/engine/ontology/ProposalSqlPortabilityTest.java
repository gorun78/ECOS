package com.chinacreator.gzcm.engine.ontology;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W72 / 数据库访问规范守卫《ProposalSqlPortabilityTest》— 源码扫描，防回归。
 *
 * <p>逐行扫描 {@code engine/ontology-engine} 与 {@code services/buszhi} 下所有 {@code .java}，
 * 命中以下任一红线即 FAIL（跳过 {@code src/test} 与注释行）：</p>
 * <ul>
 *   <li><b>MC03</b> — PG 私有类型强转：{@code ?::jsonb} / {@code ?::bigint} / {@code ?::text} / {@code ?::date}</li>
 *   <li><b>IR04</b> — 提案表 {@code SELECT * FROM ecos_ontology_proposals}（裸表名 + 通配列）</li>
 *   <li><b>§四附则1</b> — 任何 {@code FROM ecos_ontology_proposals}（裸表名，缺 schema 限定点）</li>
 * </ul>
 *
 * <p>说明：全仓其它引擎（glossary/lineage/mapping/auto-discover 等）的参数强转（{@code ?::jsonb}/{@code
 * ?::text[]}）属各自表域，<b>不在本守卫范围</b>（见各 wave 的 DR/MC 裁决；统一清偿见 W77/W83），
 * 否则本守卫会误报范围外存量。本守卫聚焦<b>提案链路</b>：W72 整改落点的两个文件
 * （{@code OntologyProposalController} / {@code OntologyProposalService}），
 * 逐行校验其无 MC03 参数私有强转、无提案表通配 SELECT *、无提案表裸名——永久防回归。</p>
 */
@DisplayName("W72：提案 SQL 移植性红线守卫（MC03 / IR04 / §四附则1）")
class ProposalSqlPortabilityTest {

    /** 参数占位符后的 PG 私有类型强转（MC03）。{@code (?!\s*\[)} 排除数组强转（如 {@code ?::text[]}）。 */
    private static final Pattern PARAM_PG_CAST = Pattern.compile(
            "\\?\\s*::\\s*(jsonb|bigint|text|date)\\b(?!\\s*\\[)", Pattern.CASE_INSENSITIVE);

    /** 提案表通配列查询（IR04）。 */
    private static final Pattern PROPOSAL_SELECT_STAR = Pattern.compile(
            "SELECT\\s+\\*\\s+FROM\\s+ecos_ontology_proposals\\b", Pattern.CASE_INSENSITIVE);

    /** 提案表裸表名（FROM/UPDATE/INSERT INTO/DELETE FROM 后紧跟，无 schema 前缀点）（§四附则1）。 */
    private static final Pattern BARE_PROPOSAL_TABLE = Pattern.compile(
            "(?<![\\w.])(?:FROM|UPDATE|INSERT\\s+INTO|DELETE\\s+FROM)"
                    + "\\s+ecos_ontology_proposals\\b", Pattern.CASE_INSENSITIVE);

    /** W72 整改落点（提案链路两个文件；相对仓库根）。 */
    private static final List<String> SCAN_RELS = List.of(
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/service/OntologyProposalService.java");

    @Test
    @DisplayName("提案 Controller/Service 源码无 (?::jsonb|::bigint|::text|::date) / 无提案表 SELECT * / 无提案表裸名")
    void proposalSqlIsPortable() throws IOException {
        Path root = findRepoRoot();
        List<String> violations = new ArrayList<>();
        for (String rel : SCAN_RELS) {
            Path f = root.resolve(rel);
            if (!Files.isRegularFile(f)) {
                continue;
            }
            List<String> lines = Files.readAllLines(f);
            int lin = 0;
            for (String raw : lines) {
                lin++;
                String trimmed = raw.trim();
                // 跳过注释行
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                report(PARAM_PG_CAST, raw, root, f, lin, "MC03 参数占位符 PG 私有强转", violations);
                if (PROPOSAL_SELECT_STAR.matcher(raw).find()) {
                    violations.add(relLabel(root, f) + ":" + lin + " IR04 提案表 SELECT *： " + trimmed);
                }
                if (BARE_PROPOSAL_TABLE.matcher(raw).find()) {
                    violations.add(relLabel(root, f) + ":" + lin
                            + " §四附则1 提案表裸名(缺 schema 限定)： " + trimmed);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W72 提案 SQL 移植性红线违规（MC03/IR04/§四附则1）：\n" + String.join("\n", violations));
    }

    private static void report(Pattern p, String raw, Path root, Path f, int lin, String tag, List<String> out) {
        Matcher m = p.matcher(raw);
        while (m.find()) {
            out.add(relLabel(root, f) + ":" + lin + " " + tag + " [" + m.group() + "] " + raw.trim());
        }
    }

    private static String relLabel(Path root, Path f) {
        try {
            return root.relativize(f).toString();
        } catch (IllegalArgumentException e) {
            return f.getFileName().toString();
        }
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git")) || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        // 兜底：向上找含 ecos_backend/ 的目录
        cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("ecos_backend"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git / ecos_backend 均缺失）");
    }
}

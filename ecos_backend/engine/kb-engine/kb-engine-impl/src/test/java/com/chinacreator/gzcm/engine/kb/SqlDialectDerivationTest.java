package com.chinacreator.gzcm.engine.kb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册04 E.5 方言单源护栏（K-27 / W110 验收标识 {@code SqlDialectDerivationTest#ecosSqlFilesMatchMigrationHead}）。
 *
 * <p><b>问题</b>：{@code ecos-sql/{postgresql,mysql,oracle}/05_ecos_knowledge.sql} 三套手写且
 * <b>落后 live</b>（2026-09-29 采数时三方对齐的仍是 V175 之前的形态），且 PG 方言文件内<b>无</b>
 * {@code vector(...)} 列定义而 live 已建。E.5 的收口方式是"从 {@code db/migration/} 单源派生方言文件"
 * —— <b>派生器本身是授权项</b>（跨方言属性归一到单源需回写文档 §E.5 才合规），但"三方漂移"可从
 * 构建期感知。本护栏锁三栏：</p>
 * <ol>
 *   <li><b>三方表集一致</b>：三套方言文件提取 {@code CREATE TABLE ... &lt;tbl&gt;} 的表名集合必须相等
 *       —— 任一方言独立增删表即红（这是"活漂移"的最强信号，无关类型/宽度差异）；</li>
 *   <li><b>12 张知识域基线表存在</b>：以 2026-09-29 采数口径 12 表（graph_node / graph_edge /
 *       graph_subgraph / knowledge_article / knowledge_embedding / expert_rule /
 *       ecos_knowledge_graph_node / ecos_knowledge_graph_edge / ecos_glossary_term /
 *       ecos_knowledge_document / ecos_marketplace_asset / ecos_marketplace_access_request）
 *       在每套方言都存在 —— 防未来派生器漏表；</li>
 *   <li><b>vector 形态镜像单源</b>：live 单源目录 V137 已建 vector+HNSW，PG 方言文件若将来由单源
 *       派生，vector 列定义必须同步出现。<b>当前</b>辩证事实 = PG 方言文件尚无 vector（K-27），
 *       本护栏不锁"必须有 vector"（那是派生器落地后的红线），而是锁"如出现必须与单源 V137 的
 *       1536 维对齐"—— 未派生前 green，误写错误维度即红。</li>
 * </ol>
 */
class SqlDialectDerivationTest {

    static final Set<String> BASELINE_KNOELEDGE_TABLES = new TreeSet<>(Set.of(
        "graph_node", "graph_edge", "graph_subgraph",
        "knowledge_article", "knowledge_embedding", "expert_rule",
        "ecos_knowledge_graph_node", "ecos_knowledge_graph_edge",
        "ecos_glossary_term", "ecos_knowledge_document",
        "ecos_marketplace_asset", "ecos_marketplace_access_request"));

    @Test
    @DisplayName("三方言文件表集一致（活漂移守卫）")
    void pgMysqlOracleKnowledgeTablesAlign() throws IOException {
        Set<String> pg = tablesIn("postgresql");
        Set<String> mysql = tablesIn("mysql");
        Set<String> ora = tablesIn("oracle");
        assertEquals(new TreeSet<>(BASELINE_KNOELEDGE_TABLES), sub(pg),
            "PG 方言文件非基线表集（新增/删除表须同时落入三套方言）: " + pg);
        assertEquals(pg, sub(mysql), "MySQL 方言与 PG 表集漂移: pg=" + pg + " mysql=" + mysql);
        assertEquals(pg, sub(ora), "Oracle 方言与 PG 表集漂移: pg=" + pg + " oracle=" + ora);
    }

    @Test
    @DisplayName("ecosSqlFilesMatchMigrationHead: 12 张知识域基线表在三套方言里都在（防派生器漏表）")
    void ecosSqlFilesMatchMigrationHead() throws IOException {
        for (String d : new String[]{"postgresql", "mysql", "oracle"}) {
            Set<String> got = tablesIn(d);
            for (String t : BASELINE_KNOELEDGE_TABLES) {
                assertTrue(got.contains(t), d + " 方言文件缺基线表 " + t + "，实有 " + got);
            }
        }
    }

    @Test
    @DisplayName("若 PG 方言文件落地 vector 列，必须与单源 V137 的 1536 维对齐")
    void vectorColumnIfPresentMatchesV137() throws IOException {
        String pg = content("postgresql");
        Matcher m = Pattern.compile("\\bvector\\((\\d+)\\)", Pattern.CASE_INSENSITIVE)
            .matcher(pg);
        if (m.find()) {
            int dim = Integer.parseInt(m.group(1));
            assertEquals(1536, dim,
                "PG 方言 vector(dim) 与单源 V137/1536 不一致: 实测 dim=" + dim
                + " —— 派生器必须与 V137 单源对齐");
        }
        // 未落地 vector 也合法（现状 = K-27 漂移登记），不强制
    }

    // ── 文件走查 ───────────────────────────────────────────────

    static Set<String> tablesIn(String dialect) throws IOException {
        String body = content(dialect);
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z_][\\w.]*)",
                Pattern.CASE_INSENSITIVE).matcher(body);
        while (m.find()) {
            String q = m.group(1);
            int dot = q.lastIndexOf('.');
            out.add(dot >= 0 ? q.substring(dot + 1) : q);
        }
        return out;
    }

    static Set<String> sub(Set<String> s) {
        Set<String> o = new TreeSet<>();
        for (String t : s) if (BASELINE_KNOELEDGE_TABLES.contains(t)) o.add(t);
        return o;
    }

    static String content(String dialect) throws IOException {
        // D-4 (2026-10-06 数据层 P3 物理归集同步)：ecos-sql/{dialect} 目录已由 P3 批次整体归集至
        // docs/50-db_script/sql/8split/{dialect}（银 L3 单源方言目录），本护栏只更切路径不落新断言。
        // R9 只增改——原本指向 `ecos-sql/<dialect>/05_ecos_knowledge.sql` 的读入句保留 2026-09-29
        // 采数口径的注释界桩不动。
        Path p = Mc02JsonbRetirementTest.repoRoot()
            .resolve("docs/50-db_script/sql/8split/" + dialect + "/05_ecos_knowledge.sql");
        if (!Files.isRegularFile(p)) fail("方言文件缺失: " + p);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}

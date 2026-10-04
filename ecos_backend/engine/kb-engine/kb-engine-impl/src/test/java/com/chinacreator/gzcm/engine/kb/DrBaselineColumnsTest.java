package com.chinacreator.gzcm.engine.kb;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册04 §E.3 V180 行门禁 —— 知识域 DR 基线列补齐（K-25 DR06~DR08 验收标识 {@code DrBaselineColumnsTest}）。
 *
 * <p><b>离线走查（零 live 库）</b>：V180 以 DO 块对迁移单源 24 张知识域表批量
 * {@code ADD COLUMN IF NOT EXISTS} 审计划线列（DR05 is_deleted / DR06 审计四列 /
 * DR07 version_no / DR08 domain / trace_id），本护栏锁三件事：</p>
 * <ol>
 *   <li><b>24 行表清单逐项锚定</b>——防未来静默缩表使基线列补齐范围收窄（K-25 口径）；</li>
 *   <li><b>七列模板七句全在</b>（domain VARCHAR(50) DEFAULT 'default' / create_time /
 *       update_time / create_by / update_by / version_no VARCHAR(20) DEFAULT '1' /
 *       is_deleted SMALLINT DEFAULT 0 / trace_id VARCHAR(64)）——任一列被删即红；</li>
 *   <li><b>幂等形态守卫</b>：全部走 {@code ADD COLUMN IF NOT EXISTS} + 表存在性
 *       {@code information_schema.tables} 预检，零 DROP / 零 ALTER DROP（IR03）。</li>
 * </ol>
 *
 * <p>沙盒纪律：双身份登记表（{@code public.ecos_glossary_term.domain} /
 * {@code public.sys_compliance_rule.domain}）保持业务混用列原样，只 COMMENT 登记不 ALTER ——
 * 本护栏不扩到该 COMMENT 段的列宽断言。</p>
 */
class DrBaselineColumnsTest {

    /**
     * V180 DO 块 VALUES 清单 28 行（schema, table），逐行镜像脚本。
     * K-25 口径：卷04 live 实测知识域表 30 张；本护栏锚定迁移单源可枚举的 28 张（其余 2 张
     * 含 live-only 无单源 DDL 的 v1 实表，见 V180 头注释说明）。
     */
    private static final String[][] TABLES = {
        {"ecos_knowledge", "knowledge_article"},
        {"ecos_knowledge", "knowledge_embedding"},
        {"ecos_knowledge", "graph_node"},
        {"ecos_knowledge", "graph_edge"},
        {"ecos_knowledge", "graph_subgraph"},
        {"ecos_knowledge", "kg_sync_log"},
        {"ecos_knowledge", "kb_extract_watermark"},
        {"ecos_knowledge", "kb_extract_candidate"},
        {"ecos_knowledge", "kb_extract_audit"},
        {"ecos_knowledge", "kb_scheduled_extract"},
        {"ecos_knowledge", "kb_doc"},
        {"ecos_knowledge", "kb_doc_chunk"},
        {"ecos_knowledge", "kb_nav_category"},
        {"ecos_knowledge", "kb_nav_tag"},
        {"ecos_knowledge", "kb_nav_article_rel"},
        {"public", "kb_ontology_snapshot"},
        {"public", "kb_lineage_event"},
        {"public", "extraction_drafts"},
        {"public", "ecos_knowledge_document"},
        {"public", "ecos_knowledge_graph_node"},
        {"public", "sys_compliance_rule"},
        {"public", "sys_rule_version"},
        {"public", "sys_extraction_source"},
        {"public", "ecos_glossary_term"},
        {"public", "ecos_glossary_term_relation"},
        {"public", "ecos_marketplace_asset"},
        {"public", "ecos_marketplace_access_request"},
        {"public", "access_request"},
    };

    @Test
    @DisplayName("V180: 28 行清单逐项锚定（防静默缩表收窄基线范围）")
    void v180CoversAllTwentyEightTables() throws IOException {
        String d = load();
        int anchored = 0;
        for (String[] t : TABLES) {
            // 末行 tuple 无尾随逗号，故只锚定 (schema,table) 本体（whitespace 归一后比对）
            String v = String.format("('%s','%s')", t[0], t[1]).replace(" ", "");
            assertTrue(compact(d).contains(v), "V180 清单行丢失: " + v);
            anchored++;
        }
        org.junit.jupiter.api.Assertions.assertEquals(28, anchored);
    }

    @Test
    @DisplayName("V180: DR05~DR08 + trace_id 七列模板句全在（任一列缩水即红）")
    void v180BaselineColumnTemplateComplete() throws IOException {
        String d = load();
        assertTrue(d.contains("'ALTER TABLE %s ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT %L'"),
            "DR08 domain 列句缺失");
        assertTrue(d.contains("%L', fulltbl, 'default'"), "DR08 domain 默认值 'default' 缺失");
        assertTrue(d.contains("create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"), "DR06 create_time 缺失");
        assertTrue(d.contains("update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"), "DR06 update_time 缺失");
        assertTrue(d.contains("create_by VARCHAR(100)"), "DR06 create_by 缺失");
        assertTrue(d.contains("update_by VARCHAR(100)"), "DR06 update_by 缺失");
        assertTrue(d.contains("version_no VARCHAR(20) NOT NULL DEFAULT %L"), "DR07 version_no 缺失");
        assertTrue(d.contains("fulltbl, '1'"), "DR07 version_no 默认值 '1' 缺失");
        assertTrue(d.contains("is_deleted SMALLINT NOT NULL DEFAULT 0"), "DR05 is_deleted 缺失");
        assertTrue(d.contains("trace_id VARCHAR(64)"), "trace_id 链路位缺失");
    }

    @Test
    @DisplayName("V180: 幂等 + 零 DROP 形态守卫（IR03 只加不删）")
    void v180IsIdempotentAndDropFree() throws IOException {
        String d = load();
        assertTrue(d.contains("information_schema.tables"), "表存在性预检缺失（非幂等）");
        assertTrue(d.contains("ADD COLUMN IF NOT EXISTS"), "IF NOT EXISTS 幂等形态缺失");
        Matcher drop = Pattern.compile("(?i)\\bDROP\\s+(TABLE|COLUMN)\\b").matcher(d);
        // 回滚说明注释段允许出现 DROP 字样，取去注释后断言
        String noComment = Mc02JsonbRetirementTest.stripComments(d);
        Matcher drop2 = Pattern.compile("(?i)\\bDROP\\s+(TABLE|COLUMN)\\b").matcher(noComment);
        assertTrue(!drop2.find(), "V180 非注释代码出现 DROP TABLE/COLUMN（IR03 违规）: " + (drop.find() ? drop.group() : ""));
    }

    private static String load() throws IOException {
        Path dir = Mc02JsonbRetirementTest.repoRoot().resolve(
            "ecos_backend/gateway/src/main/resources/db/migration");
        try (Stream<Path> walk = Files.list(dir)) {
            for (Path p : walk.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("V180__") && n.endsWith(".sql")) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        fail("V180__kb_dr_baseline_columns.sql 不存在于迁移单源目录");
        return null;
    }

    private static String compact(String s) {
        return s.replace(" ", "");
    }
}

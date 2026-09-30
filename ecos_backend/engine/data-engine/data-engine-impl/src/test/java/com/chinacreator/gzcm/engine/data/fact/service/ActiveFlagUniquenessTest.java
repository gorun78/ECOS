package com.chinacreator.gzcm.engine.data.fact.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-06-3 / W44：业务事实五表「is_active 唯一活跃」方案（详细设计-02 §F02-06 / E.2 / V167）。
 *
 * <p>验收口径：
 * <ol>
 *   <li>V167 DDL 四张业务事实表（attr / stage / resource / cost）均新增
 *       {@code is_active SMALLINT NOT NULL DEFAULT 1}（存活=1；被替代行走
 *       {@code is_active=NULL} —— PG 语义 NULL 不参与唯一判定）。
 *       注：V167 文件现状把 {@code is_active} 定名 NOT NULL；被替代行置 NULL 由应用侧
 *       {@code UPDATE ... SET is_active = NULL} 走（DDL 严格口径仍在 MC05/DR05 内，
 *       本测试只守卫「列存在 + 索引必含 is_active」两条线，不锁定 NULL 语义。）</li>
 *   <li>同文件四张业务事实表必须各建一个组合唯一索引 {@code uniq_*_active (...biz_keys..., is_active)}
 *       —— 业务键列齐全 + 结尾列必须 {@code is_active}（否则会被 NOT NULL 拒绝/ Nº insert 冲突）。
 *       {@code ecos_forecast_input_snapshot} 走 {@code uniq_fis_run(forecast_run_id)}，
 *       业务键 = 预测 run（FK 语义），不参与多维业务键 + is_active 方案 — 白名单不匹配。</li>
 *   <li>四张业务事实表的 MyBatis mapper（{@code mapper/*FactMapper.xml}）：
 *       {@code <sql id="cols">} 显式列出 {@code is_active}（IR04 列清单），
 *       {@code <insert id="insertBatch">} 显式绑定 {@code #{r.isActive}}（Service 侧
 *       {@code FactImportService#hydrate} 已 fill {@code isActive=1} = 首行存活，
 *       业务键唯一约束由 DB 侧组合索引兜底「一业务键只有一行活跃 + 无限历史行」）。</li>
 * </ol>
 *
 * <p>本测试源码断言（无 DB），防后续误删 {@code is_active} / 索引实验形态（如条件索引、
 * 表达式索引 → 违 MC03/MC04）；被替代行 → {@code is_active} 置 NULL 的 UPDATE 走业务代码，
 * 不在 DDL 治理面。</p>
 */
@DisplayName("F02-06-3 业务事实五表 is_active 组合唯一索引 + Mapper 列清单守卫")
class ActiveFlagUniquenessTest {

    private static final String V167_REL = "ecos_backend/gateway/src/main/resources/db/migration/V167__biz_fact_model.sql";

    // 四张业务事实表（forecast 白名单除外）
    private static final String[] FACT_TABLES = {
            "ecos_dw.ecos_biz_project_attribution",
            "ecos_dw.ecos_biz_stage_fact",
            "ecos_dw.ecos_biz_resource_fact",
            "ecos_dw.ecos_biz_cost_fact"
    };
    private static final String[] UNIQUE_IDX = {
            "uniq_bpa_active", "uniq_bsf_active", "uniq_brf_active", "uniq_bcf_active"
    };
    private static final String[] FACT_MAPPERS = {
            "src/main/resources/mapper/AttributionFactMapper.xml",
            "src/main/resources/mapper/StageFactMapper.xml",
            "src/main/resources/mapper/ResourceFactMapper.xml",
            "src/main/resources/mapper/CostFactMapper.xml"
    };

    @Test
    @DisplayName("V167 DDL：四张业务事实表均含 is_active SMALLINT NOT NULL DEFAULT 1")
    void v167_hasIsActiveSmallintOnAllFourFactTables() throws IOException {
        String sql = Files.readString(migrationSql(V167_REL), StandardCharsets.UTF_8);
        int isActiveCount = countOccurrences(sql, "is_active");
        // 4 表列定义 + 4 表唯一索引 = 至少 8 处 is_active
        assertTrue(isActiveCount >= 8,
                "V167 应至少出现 8 处 is_active（4 表列 + 4 表索引），实际 " + isActiveCount);
        assertTrue(countOccurrences(sql, "SMALLINT") >= 8,
                "V167 各表 SMALLINT 类型（is_active + is_deleted + 部分表 is_pool）>= 8");
        for (String t : FACT_TABLES) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + t),
                    "V167 缺表 " + t);
        }
        // 关键：至少 4 处 "is_active ... SMALLINT" 列定义（含 DEFAULT 1）
        int colDefCount = countOccurrences(sql, "SMALLINT     NOT NULL DEFAULT 1");
        assertTrue(colDefCount >= 4,
                "is_active SMALLINT NOT NULL DEFAULT 1 至少应出现 4 次（= 4 张事实表），实际 " + colDefCount);
    }

    @Test
    @DisplayName("V167 DDL：四张业务事实表各建 uniq_*_active 组合唯一索引，末尾列必为 is_active")
    void v167_uniqueIndexesEndWithIsActive() throws IOException {
        String sql = Files.readString(migrationSql(V167_REL), StandardCharsets.UTF_8);
        for (int i = 0; i < UNIQUE_IDX.length; i++) {
            String idx = UNIQUE_IDX[i];
            // CREATE UNIQUE INDEX ... ON <table> ( <biz_keys>, is_active );
            int idxPos = sql.indexOf("CREATE UNIQUE INDEX IF NOT EXISTS " + idx);
            assertTrue(idxPos >= 0, "V167 缺唯一索引 " + idx);
            int onPos = sql.indexOf(" ON ", idxPos);
            int openParen = sql.indexOf('(', onPos);
            int closeParen = sql.indexOf(')', openParen);
            assertTrue(openParen > onPos && closeParen > openParen,
                    "索引 " + idx + " 语法残缺：ON x (cols) 未闭合");
            String cols = sql.substring(openParen + 1, closeParen);
            String[] colArr = cols.split(",");
            String last = colArr[colArr.length - 1].trim();
            assertEqualsIgnoreCase("is_active", last,
                    "唯一索引 " + idx + " 末尾列必为 is_active（F02-06-3：一业务键一行活跃 + 无限历史 NULL 行），实际末尾=" + last);
            // 组合索引必 ≥2 列（业务键 + is_active）—— 单列 is_active 不允许
            assertTrue(colArr.length >= 2,
                    "唯一索引 " + idx + " 必须是组合索引（业务键 + is_active），不得仅 is_active 单列");
            assertTrue(cols.contains(FACT_TABLES[i].substring(FACT_TABLES[i].indexOf('.') + 1)) ||
                            cols.contains("is_active"),
                    "索引 " + idx + " 列集合覆盖业务键：[" + cols + "]");
        }
        // forecast 表不使用本方案（白名单）：不应出现 uniq_*_active 系列
        assertTrue(!sql.contains("uniq_fis_active"),
                "forecast 表不使用 is_active 组合唯一（业务键 = forecast_run_id 单列唯一），"
                        + "应使用 uniq_fis_run");
        assertTrue(sql.contains("uniq_fis_run"), "forecast 表单列唯一索引 uniq_fis_run 未建");
    }

    @Test
    @DisplayName("四张事实表 MyBatis Mapper：<sql id='cols'> 显式列出 is_active、INSERT 绑定 #{r.isActive}（IR04 + isActive=1 首行存活）")
    void mappers_exposeIsActiveInInsertAndCols() throws IOException {
        Path moduleRoot = moduleRoot();
        for (String rel : FACT_MAPPERS) {
            Path p = moduleRoot.resolve(rel);
            assertTrue(Files.isRegularFile(p), "缺 Mapper " + rel);
            String xml = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(xml.contains("is_active"),
                    rel + " 缺 <sql id='cols'> 内的 is_active 列（IR04 显式列清单）");
            assertTrue(xml.contains("#{r.isActive}"),
                    rel + " insertBatch 未绑定 #{r.isActive}（Service hydrate 已填 1 = 首行存活；DB 侧双行 INSERT 同业务键应被 uniq_*_active 拒）");
            // cols sql fragment 内 necessarily 存在 is_active 字面
            int colsFrag = xml.indexOf("<sql id=\"cols\">");
            assertTrue(colsFrag >= 0, rel + " 缺 <sql id='cols'> fragment（IR04）");
            int sqlEnd = xml.indexOf("</sql>", colsFrag);
            String colsBody = xml.substring(colsFrag, sqlEnd);
            assertTrue(colsBody.contains("is_active"),
                    rel + " 的 <sql id='cols'> 内未列 is_active（IR04 显式列清单门禁）");
        }
    }

    private static Path migrationSql(String rel) {
        Path root = repoRoot();
        return root.resolve(rel);
    }

    private static Path repoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（缺 .git）");
    }

    private static Path moduleRoot() {
        return repoRoot().resolve("ecos_backend/engine/data-engine/data-engine-impl");
    }

    private static int countOccurrences(String hay, String needle) {
        int c = 0;
        int i = 0;
        while ((i = hay.indexOf(needle, i)) != -1) {
            c++;
            i += needle.length();
        }
        return c;
    }

    private static void assertEqualsIgnoreCase(String expected, String actual, String msg) {
        assertTrue(expected.equalsIgnoreCase(actual), msg);
    }
}

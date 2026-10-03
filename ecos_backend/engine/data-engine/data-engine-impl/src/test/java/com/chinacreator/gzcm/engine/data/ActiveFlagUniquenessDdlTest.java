package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * F02-06-3 / W-活跃唯一性（详细设计-02 §E.2 + F02-06 验收 {@code ActiveFlagUniquesModule}，M2）——
 * 「一个业务键只有一行活跃 + 无限历史行」方案的 <b>DDL 声明面</b>离线护栏。
 *
 * <p>方案设计（doc 行 136）：新列 {@code is_active SMALLINT}——存活行 =1、被替代行置 NULL；
 * 唯一索引 {@code uniq_*_active (业务键…, is_active)}，NULL 不参与唯一判定（PG/MySQL/达梦一致语义），
 * 从而"第二行存活 → 索引拒；旧行置 NULL 后新行可入"。doc 行 140 验收名为
 * {@code ActiveFlagUniquenessTest（同键第二行存活 → 索引拒；旧行置 NULL 后新行可入）}——
 * 该<b>运行时行为</b>需活库 INSERT（属实跑库授权闸）。本护栏锁定其<b>前置 DDL 契约</b>（离线可验）：</p>
 * <ul>
 *   <li>四张"软替代"事实表（project_attribution / stage_fact / resource_fact / cost_fact）均声明
 *       {@code is_active SMALLINT NOT NULL DEFAULT 1}（DR05 布尔 SMALLINT + "1=存活"默认），
 *       且各含一个<b>列清单包含 is_active 的 UNIQUE 索引</b>（该索引正是"唯一活跃"的落地原语，
 *       MC03 非条件索引、MC04 非表达式索引）。</li>
 *   <li>第 5 表 forecast_input_snapshot 语义不同（快照按 {@code forecast_run_id} 唯一，非软替代），
 *       白名单断言其仅声明 {@code is_active} 列、不套用 active 唯一索引——防未来误给它套 active 索引改变快照语义。</li>
 *   <li>白线：DDL 实含 {@code uniq_bpa/bsf/brf/bcf_active} 四个索引名——防空文件静默绿。</li>
 * </ul>
 *
 * <p><b>不触库、不 Spring 容器、不联网</b>：纯文件读取 + 正则解析。
 * 单源目录 {@code gateway/.../db/migration/V167__biz_fact_model.sql}，不越权扫全树。</p>
 */
@DisplayName("F02-06-3 活跃唯一性 DDL 声明面（is_active 列 + 含 is_active 唯一索引，M2）")
class ActiveFlagUniquenessDdlTest {

    /** 单源文件（V167 业务事实五表）。 */
    private static final String V167 = "V167__biz_fact_model.sql";

    @ParameterizedTest(name = "{0}: is_active SMALLINT NOT NULL DEFAULT 1 + 含 is_active 的 UNIQUE 索引")
    @CsvSource({
            "ecos_biz_project_attribution, uniq_bpa_active",
            "ecos_biz_stage_fact,          uniq_bsf_active",
            "ecos_biz_resource_fact,       uniq_brf_active",
            "ecos_biz_cost_fact,           uniq_bcf_active"
    })
    void factTable_declaresIsActiveAndActiveUniqueIndex(String table, String uniqIndex) throws IOException {
        String body = readV167();

        // a) 列声明：is_active SMALLINT NOT NULL DEFAULT 1（"1=存活"默认；DR05 布尔 SMALLINT）
        String createBlock = extractCreateTableBlock(body, table);
        assertTrue(createBlock != null, "未在 " + V167 + " 定位 CREATE TABLE " + table);
        assertTrue(Pattern.compile("\\bis_active\\s+SMALLINT\\s+NOT\\s+NULL\\s+DEFAULT\\s+1").matcher(createBlock).find(),
                table + " 缺 `is_active SMALLINT NOT NULL DEFAULT 1` 列声明（F02-06-3 活跃唯一性前置，DR05）");

        // b) 唯一索引：CREATE [UNIQUE] INDEX ... ON <schema>.<table>( … is_active … )
        String idxBlock = extractUniqueIndexBody(body, table);
        assertTrue(idxBlock != null, table + " 未发现 CREATE UNIQUE INDEX … ON … ." + table + " (…) —— 唯一活跃索引缺失");
        assertTrue(Pattern.compile("\\bis_active\\b").matcher(idxBlock).find(),
                table + " 的唯一索引列清单<b>不含</b> is_active —— F02-06-3「同键唯一活跃」未落地"
                        + "（活跃唯一性依赖业务键 + is_active 的组合唯一索引：NULL 老行不占位、第二存活行被拒）");

        // c) 白线：具名索引实存（防空文件/改索引名静默绿）
        assertTrue(Pattern.compile("CREATE\\s+UNIQUE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+" + uniqIndex,
                Pattern.CASE_INSENSITIVE).matcher(body).find(),
                V167 + " 缺命名唯一索引 " + uniqIndex + "（白线：活跃唯一性声明被改/删）");
    }

    @Test
    @DisplayName("forecast_input_snapshot 快照表：仅声明 is_active 列，不套用 active 唯一索引（保持 forecast_run_id 唯一语义）")
    void snapshotTable_isActivePresentButNotActiveUniqueIndex() throws IOException {
        String body = readV167();
        String createBlock = extractCreateTableBlock(body, "ecos_forecast_input_snapshot");
        assertTrue(createBlock != null, "未在 V167 定位 CREATE TABLE ecos_forecast_input_snapshot");
        assertTrue(Pattern.compile("\\bis_active\\b").matcher(createBlock).find(),
                "ecos_forecast_input_snapshot 应含 is_active 列（五表统一审计口径）");
        // 该表唯一性走 forecast_run_id（快照语义），不应有含 is_active 的组合唯一索引
        String idxBody = extractUniqueIndexBody(body, "ecos_forecast_input_snapshot");
        boolean hasActiveInUnique = idxBody != null && Pattern.compile("\\bis_active\\b").matcher(idxBody).find();
        assertTrue(!hasActiveInUnique,
                "ecos_forecast_input_snapshot 唯一索引不应含 is_active（快照按 forecast_run_id 唯一，"
                        + "套用 active 唯一索引会改变其语义，F02-06-3 五表中四表软替代 + 快照一体例外）");
    }

    // ─────────────────────────── 工具 ───────────────────────────

    private String readV167() throws IOException {
        Path dir = resolveMigrationDir();
        Path f = dir.resolve(V167);
        assertTrue(Files.isRegularFile(f), "单源目录缺失 " + V167 + " —— DDL 单源漂移，F02-06-3 前置不成立");
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private Path resolveMigrationDir() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                Path c = cur.resolve("ecos_backend/gateway/src/main/resources/db/migration");
                if (Files.isDirectory(c)) {
                    return c;
                }
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根定位 db/migration 单源目录");
        return null; // unreachable
    }

    /** CREATE TABLE … <schema>.<table> ( … ); 块（锚点行含表名，累积到首个 ");"）。 */
    private String extractCreateTableBlock(String sql, String table) {
        Pattern anchor = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\\w\\.]*" + Pattern.quote(table) + "\\b",
                Pattern.CASE_INSENSITIVE);
        String[] lines = sql.split("\\R", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (anchor.matcher(stripComment(lines[i])).find()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            b.append(lines[i]).append('\n');
            if (lines[i].trim().endsWith(");")) {
                return b.toString();
            }
        }
        return null;
    }

    /**
     * 目标表上第一个 UNIQUE 索引的列清单块：从 {@code CREATE [UNIQUE] INDEX …} 起，
     * 找到 {@code ON <schema>.<table>( … )} 部分（可能跨行）直到 {@code )}。返回括号内列清单文本。
     */
    private String extractUniqueIndexBody(String sql, String table) {
        // 去掉注释行影响后，按 "ON ... <table> (" 定位
        Pattern onRe = Pattern.compile(
                "(?is)ON\\s+" + Pattern.quote("ecos_dw." + table) + "\\s*\\(([^)]*)\\)");
        // 需要确保该 ON 属于一个 UNIQUE INDEX（向前回看限定名 + UNIQUE）
        Pattern headRe = Pattern.compile(
                "(?is)CREATE\\s+UNIQUE\\s+INDEX[^;]*?ON\\s+" + Pattern.quote("ecos_dw." + table) + "\\s*\\(([^)]*)\\)");
        var m = headRe.matcher(sql);
        if (m.find()) {
            return m.group(0) + " ::COLS:: " + m.group(1);
        }
        // 回退：若没有 CREATE UNIQUE INDEX 命中但存在 ON ... ( … )（防止漏 SHALL）——仍取列清单
        var m2 = onRe.matcher(sql);
        if (m2.find()) {
            return " ::COLS:: " + m2.group(1);
        }
        return null;
    }

    private static String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}

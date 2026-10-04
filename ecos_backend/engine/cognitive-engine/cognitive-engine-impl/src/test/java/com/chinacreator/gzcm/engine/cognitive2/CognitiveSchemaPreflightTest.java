package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 §E.2 / F05-02 要点1·3 离线 DDL 走查（护 E1 数据源物理建成）。
 *
 * <p>本类跑<b>迁移脚本文件级别</b>的静态走查，不涉及 live 库。文档 §Cg-2 的
 * {@code to_regclass} 库内实跑属 §14.4 授权闸，另登记见 doc【校订】。</p>
 *
 * <p>E1 detect 数据源 = {@code ecos_cognitive.ecos_cognitive_mind}（V187）+
 * {@code ecos_cognitive.ecos_cognitive_scenario_mind}（V188）。二者必须在
 * V188 落地前先行落地（V187 编号更低自然先行）；本护栏锁：
 * <ol>
 *   <li>V187/V188 双双建表（CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind
 *       / ecos_cognitive_scenario_mind）；</li>
 *   <li>主键 VARCHAR(36)（MC01）；两表 is_deleted SMALLINT（DR05）；</li>
 *   <li>V187 有 {@code capability_mask_json TEXT NOT NULL DEFAULT '[]'} 与
 *       {@code closed_loop_bounds_json TEXT NOT NULL DEFAULT '[]'}（DR04/MC02）；</li>
 *   <li>V188 有 {@code mind_id VARCHAR(36) NOT NULL}（X-19 全库首个 mind_id 列）；</li>
 *   <li>两表 DDL 中<b>无</b> JSONB 二进制形态（新表 DR04 + MC02 无 jsonb）；</li>
 *   <li>V187 编号 < V188（顺序保证 E1 数据源先于场景绑定建表）。</li>
 * </ol>
 */
final class CognitiveSchemaPreflightTest {

    private static final Pattern MIND_CREATE =
        Pattern.compile("CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+ecos_cognitive\\.ecos_cognitive_mind",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENARIO_MIND_CREATE =
        Pattern.compile("CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+ecos_cognitive\\.ecos_cognitive_scenario_mind",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIND_PK =
        Pattern.compile("CONSTRAINT\\s+pk_ecos_cognitive_mind\\s+PRIMARY\\s+KEY\\s*\\(id\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SMIND_PK =
        Pattern.compile("CONSTRAINT\\s+pk_ecos_cognitive_scenario_mind\\s+PRIMARY\\s+KEY\\s*\\(id\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAP_MASK =
        Pattern.compile("capability_mask_json\\s+TEXT\\s+NOT\\s+NULL\\s+DEFAULT\\s+'\\[\\]'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSED_LOOP =
        Pattern.compile("closed_loop_bounds_json\\s+TEXT\\s+NOT\\s+NULL\\s+DEFAULT\\s+'\\[\\]'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIND_ID_COL =
        Pattern.compile("mind_id\\s+VARCHAR\\s*\\(36\\)\\s+NOT\\s+NULL", Pattern.CASE_INSENSITIVE);
    private static final Pattern PK_VARCHAR36 =
        Pattern.compile("id\\s+VARCHAR\\s*\\(36\\)\\s+NOT\\s+NULL", Pattern.CASE_INSENSITIVE);
    /** 新表 DDL 中禁 jsonb 二进制列定义（DR04/MC02 只加不删纪律的<b>新</b>表侧）。 */
    private static final Pattern JSONB_COLUMN_DEF =
        Pattern.compile("\\bjsonb\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("V187/V188 双表落位 + E1 数据源结构齐备（cap 掩码 / closed loop / pk / mind_id）")
    void mindTablesResolveBeforeE1() throws Exception {
        String v187 = CognitiveDocPaths.migrationOrNull(187);
        String v188 = CognitiveDocPaths.migrationOrNull(188);
        assertNotNull(v187, "V187 迁移脚本缺失——E1 数据源未按 §E.2 要点1 落地");
        assertNotNull(v188, "V188 迁移脚本缺失——场景↔Mind 绑定表未按 §E.2 要点1·3 落地");

        assertTrue(MIND_CREATE.matcher(v187).find(),
            "V187 未建 ecos_cognitive.ecos_cognitive_mind（E1 数据源主表缺失）");
        assertTrue(SCENARIO_MIND_CREATE.matcher(v188).find(),
            "V188 未建 ecos_cognitive.ecos_cognitive_scenario_mind（场景绑定表缺失）");

        assertTrue(MIND_PK.matcher(v187).find(), "V187 主键 pk_ecos_cognitive_mind 缺失");
        assertTrue(SMIND_PK.matcher(v188).find(), "V188 主键 pk_ecos_cognitive_scenario_mind 缺失");
        assertTrue(PK_VARCHAR36.matcher(v187).find(),
            "V187 主键 id 未走 VARCHAR(36)（违 MC01）");
        assertTrue(PK_VARCHAR36.matcher(v188).find(),
            "V188 主键 id 未走 VARCHAR(36)（违 MC01）");

        assertTrue(CAP_MASK.matcher(v187).find(),
            "V187 缺 capability_mask_json TEXT NOT NULL DEFAULT '[]'（REQ-COG-04 M1 载体）");
        assertTrue(CLOSED_LOOP.matcher(v187).find(),
            "V187 缺 closed_loop_bounds_json TEXT NOT NULL DEFAULT '[]'（闭环边界载体）");
        assertTrue(MIND_ID_COL.matcher(v188).find(),
            "V188 缺 mind_id VARCHAR(36) NOT NULL——X-19 全库首个 mind_id 列未落地");

        // 新表 DR04 + MC02 无 JSONB 二进制 —— 用 DDL 主体（剥行注释）后精确子串检查
        String v187Body = CognitiveDocPaths.stripComments(v187);
        String v188Body = CognitiveDocPaths.stripComments(v188);
        assertTrue(!containsJsonbColumnDef(v187Body),
            "V187 含 JSONB 列定义，违 DR04/MC02（新表 JSON 语义列必带 TEXT + _json 后缀）");
        assertTrue(!containsJsonbColumnDef(v188Body),
            "V188 含 JSONB 列定义，违 DR04/MC02");
    }

    @Test
    @DisplayName("V187 编号 < V188（E1 数据源先于场景绑定建表，顺序漂移会导致 E1 JOIN 无主表可引用）")
    void v187SequencedBeforeV188() throws Exception {
        String v187 = CognitiveDocPaths.migrationOrNull(187);
        String v188 = CognitiveDocPaths.migrationOrNull(188);
        assertNotNull(v187);
        assertNotNull(v188);
        assertTrue(187 < 188, "版本前缀数值关系反常：V187 未先于 V188");
    }

    /**
     * 精确判据：只在 DDL 列定义行（{@code <identifier> JSONB...}）匹配，避免误伤
     * 头注/项链中的说明字面。逐行处理：单行内剥字符串字面量，再匹配
     * {@code (\b\w+\s+jsonb\b)} 形式的<b>类型声明</b>。
     */
    private static boolean containsJsonbColumnDef(String body) {
        List<String> lines = List.of(body.split("\n"));
        for (String raw : lines) {
            String line = CognitiveDocPaths.stripSqlStringLiterals(raw);
            if (Pattern.compile("\\w+\\s+jsonb\\b", Pattern.CASE_INSENSITIVE).matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("已知缺口不伪造：V189 需存在（hypothesis 状态机留痕 + 存量映射）")
    void v189HypothesisStatusConvergeLanded() throws Exception {
        String v189 = CognitiveDocPaths.migrationOrNull(189);
        assertNotNull(v189, "V189 缺失——假设状态机终态收敛（R-15 ①）未按 §E.2 落地");
        assertTrue(CognitiveDocPaths.stripComments(v189).contains("old_status"),
            "V189 缺 old_status 留痕列，违 R-15 ①（终态四值仅对新数据生效 + 存量映射保留 old_status）");
        assertTrue(Pattern.compile("VALID\\s*→?\\s*BELIEVED|'VALID'\\s+THEN\\s+'BELIEVED'",
            Pattern.CASE_INSENSITIVE).matcher(v189).find(),
            "V189 缺 VALID→BELIEVED 存量映射（R-15 ①）");
    }
}

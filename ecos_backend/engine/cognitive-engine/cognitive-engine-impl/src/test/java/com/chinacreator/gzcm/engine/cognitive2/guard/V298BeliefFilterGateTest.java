package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 台账 §8.4.2 行 432 新登记余项（#146az / #146ay 尾部登记「belief 同缺承参过滤列」收口）——
 * V298 belief 归属三列 + E2/E4 读链承参过滤面形态门禁（纯源码/DDL 扫描，零 DB 依赖）。
 *
 * <p>断言面（方案 A，用户裁决 2026-10-31：绝不在 belief 表私设 scenario_id 列）：
 * <ol>
 *   <li><b>V298 DDL 形态冻结</b>：{@code mind_id/subject_type/subject_id} 三枚
 *       {@code ADD COLUMN IF NOT EXISTS} 在位且落 {@code ecos_cognitive.} 权威载体；
 *       红线 = <b>禁 scenario_id 建列入本 DDL</b>（场景维度只能经绑表推导）+ 禁 jsonb/timestamptz；</li>
 *   <li><b>E4 读链载体收口</b>：{@code BeliefReader} 指权威载体
 *       {@code ecos_cognitive.ecos_cognitive_belief}（幻影公域载体恒红），承参过滤面
 *       {@code mind_id = ?} 严格等值＋绑表子查询 {@code scenario_id = ?}（V296/V297
 *       HypothesisReader 收口同法），<b>禁裸 {@code scenario_id = ?} 直判 belief 行</b>
 *       （权威载体无该列 ⇒ 恒 E501）；V262 {@code probability} 列已在 SELECT 投影上填
 *       （V262 头注明示的同批应用侧收口面，禁回归回 null 挂账）；</li>
 *   <li><b>E2 评分读腿同法</b>：{@code BeliefScorer} scenario 过滤只能走绑表子查询
 *       （{@code IN (SELECT mind_id FROM ...scenario_mind ... scenario_id = ?)}），
 *       禁 {@code AND scenario_id = ?} 直判；</li>
 *   <li><b>V262 概率列位冻结</b>：{@code probability NUMERIC(6,5)} 在权威载体 DDL 在位，
 *       禁 DEFAULT（NULL=未标定语义，禁 0 替真值）。</li>
 * </ol>
 *
 * <p>变异探针（交付时实跑取证后复原）：① V298 删任一列 ALTER → case①红；
 * ② BeliefReader 表常量回退 {@code public.} 前缀 → case②红；
 * ③ BeliefScorer 改回 {@code AND scenario_id = ?} 直判 → case③红；
 * ④ V262 给 probability 加 DEFAULT 0 → case④红。</p>
 */
class V298BeliefFilterGateTest {

    private static final Path REPO = CogScope.REPO;
    private static final Path MIGRATIONS = REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
    private static final Path V262 = MIGRATIONS.resolve("V262__cognitive_belief_probability.sql");
    private static final Path V298 = MIGRATIONS.resolve("V298__cognitive_belief_mind_subject_cols.sql");
    private static final Path READER = CogScope.MAIN.resolve("com/chinacreator/gzcm/engine/cognitive2/dao/BeliefReader.java");
    private static final Path SCORER = CogScope.MAIN.resolve("com/chinacreator/gzcm/engine/cognitive2/dao/BeliefScorer.java");

    private static final String CARRIER = "ecos_cognitive.ecos_cognitive_belief";
    private static final String BINDING_TABLE = "ecos_cognitive.ecos_cognitive_scenario_mind";

    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("case① V298 DDL 形态冻结：mind_id/subject_type/subject_id 三枚权威载体 ALTER；禁 scenario_id 入 belief + jsonb/timestamptz")
    void v298DdlShapeIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V298), "V298 单源文件缺失：" + V298);
        String ddl = CogScope.stripComments(Files.readString(V298));
        for (String col : new String[]{"mind_id", "subject_type", "subject_id"}) {
            assertTrue(ddl.contains("ADD COLUMN IF NOT EXISTS " + col),
                    "V298 belief 缺归属列：" + col);
        }
        assertTrue(ddl.contains("ALTER TABLE " + CARRIER),
                    "V298 ALTER 未落权威载体 " + CARRIER);
        assertFalse(Pattern.compile("(?i)ADD\\s+COLUMN[^;]*scenario_id").matcher(ddl).find(),
                "V298 给 belief 建了 scenario_id 列（方案 A 裁决：场景维度经绑表推导，禁私设列）");
        assertFalse(ddl.contains("jsonb"), "V298 出现 JSONB（MC02）");
        assertFalse(ddl.contains("timestamptz"), "V298 出现 timestamptz（MC03 家族纪律）");
    }

    @Test
    @DisplayName("case② E4 读链收口：BeliefReader 权威载体 + mind 严格等值 + 绑表场景子查询 + V262 probability 上填（禁载体回退/禁 scenario_id 直判）")
    void beliefReaderCarrierAndFilterFaceIsClosed() throws IOException {
        String reader = CogScope.stripComments(Files.readString(READER));
        assertTrue(reader.contains("\"" + CARRIER + "\""),
                "BeliefReader 表常量漂移（须 " + CARRIER + " 权威载体）");
        assertFalse(reader.contains("public.ecos_cognitive_belief"),
                "BeliefReader 仍指公域旧载体（V191 后权威载体在 ecos_cognitive）");
        assertTrue(reader.contains("mind_id = ?"),
                "带 mind 参分支缺 mind_id 严格等值过滤（禁静默兜底到未绑定 mind）");
        assertTrue(reader.contains("\"" + BINDING_TABLE + "\""),
                "绑表常量值漂移（须权威载体 ecos_cognitive.ecos_cognitive_scenario_mind）");
        assertTrue(reader.contains("SELECT mind_id FROM \" + SCENARIO_MIND_SUBQUERY"),
                "场景维度未走绑表子查询（方案 A：belief 无 scenario_id 列，只能经绑表推导）");
        assertTrue(reader.contains(" WHERE scenario_id = ?"),
                "绑表子查询缺 scenario_id = ? 过滤（占位符顺序乱 = 参数错序事故）");
        // 裸 scenario_id 直判 belief 行 = 权威载体无列 ⇒ 恒 E501（V298 前旧形态）
        assertFalse(Pattern.compile("FROM\\s+\"\\s*\\+\\s*TABLE\\s*\\+?[\\s\\S]{0,200}?WHERE\\s+is_deleted\\s*=\\s*0\\s+AND\\s+scenario_id\\s*=\\s*\\?")
                        .matcher(reader).find(),
                "BeliefReader 仍以 scenario_id = ? 直判 belief 行（权威载体无该列，必 E501）");
        // V262 同批应用侧收口面：probability 进投影 + VO 位置 5 上填，禁回归回挂账 null
        assertTrue(reader.contains("SELECT id, variable_name, domain, update_time, probability"),
                "E4 读链 SELECT 缺 V262 probability 列位（V262 头注明示本收口面，禁回归挂账）");
        assertTrue(reader.contains("probabilityOf(r.get(\"probability\"))"),
                "BeliefVO.probability 未从行真值上填（禁 0 替真值，禁静默 null）");
    }

    @Test
    @DisplayName("case③ E2 评分读腿同法：BeliefScorer 场景过滤只走绑表子查询，禁 scenario_id 直判")
    void beliefScorerScenarioFilterGoesThroughBindingTable() throws IOException {
        String scorer = CogScope.stripComments(Files.readString(SCORER));
        assertTrue(scorer.contains("\"" + CARRIER + "\""),
                "BeliefScorer 表常量漂移（须 " + CARRIER + " 权威载体）");
        assertTrue(scorer.contains("\"" + BINDING_TABLE + "\""),
                "绑表常量值漂移（须权威载体 ecos_cognitive.ecos_cognitive_scenario_mind）");
        assertTrue(scorer.contains("SELECT mind_id FROM \" + SCENARIO_MIND_TABLE"),
                "E2 scenario 过滤未走绑表子查询（方案 A 同 E4 口径）");
        assertTrue(scorer.contains(" WHERE scenario_id = ?"),
                "绑表子查询缺 scenario_id = ? 过滤");
        assertFalse(scorer.contains(" AND scenario_id = ?"),
                "BeliefScorer 仍以 AND scenario_id = ? 直判 belief 行（权威载体无该列，必 E501）");
    }

    @Test
    @DisplayName("case④ V262 概率列位冻结：probability NUMERIC(6,5) 权威载体在位 + 可空禁 DEFAULT（NULL=未标定）")
    void v262ProbabilityColumnIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V262), "V262 单源文件缺失：" + V262);
        String ddl = CogScope.stripComments(Files.readString(V262));
        assertTrue(ddl.contains("ALTER TABLE " + CARRIER + " ADD COLUMN IF NOT EXISTS probability NUMERIC(6,5)"),
                "V262 probability 列位漂移（须 " + CARRIER + " ADD COLUMN IF NOT EXISTS probability NUMERIC(6,5)）");
        assertFalse(Pattern.compile("(?i)probability\\s+NUMERIC\\s*\\(\\s*6\\s*,\\s*5\\s*\\)\\s+DEFAULT")
                        .matcher(ddl).find(),
                "V262 probability 建了 DEFAULT（NULL=未标定语义，DEFAULT 0 = 0 替真值，违 REQ-COG-09 规格 4）");
    }

}

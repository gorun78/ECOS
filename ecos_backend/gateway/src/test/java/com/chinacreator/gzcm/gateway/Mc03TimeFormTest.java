package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.gateway.DdlShapeScanner.Bucket;
import com.chinacreator.gzcm.gateway.DdlShapeScanner.Scan;

/**
 * #144 门禁批 · MC03 可移植形态全源门禁（时间形态零带时区 + PG 专有语法面，差距文档篇6 §7.7
 * 行 452 具名缺失门禁 {@code Mc03PortabilityTest} 的落地实现，别名归并见差距文档本批订正注）。
 *
 * <p><b>差距来源</b>：差距文档 篇6 原文「kb/cognitive 多处 {@code ?::jsonb}、{@code jsonb_array_length}、
 * {@code @> ?::jsonb}、{@code ILIKE} … 违 MC01/MC02/MC03；…{@code Mc03PortabilityTest} …均不存在
 * ⇒ 违例可长期静默累积，后续收口成本与<b>不可移植风险</b>最高」。既有载体只校验 Mapper/Java 侧
 * 的个别域段 ⇒ <b>DDL 单源 240 枚脚本</b>的 MC03 面无人守护。按 R9①「缺的门禁＝补代码实现」补本类。</p>
 *
 * <p><b>判定面 = DDL 单源文本，不含 Mapper XML</b>（裁决 D-#144A-2，理由照直写）：
 * MC03 原文的处置手段是「必须 MyBatis {@code databaseId} 方言片段或 Adapter 层，且提供降级分支」，
 * 那是<b>运行期代码面</b>，属 {@code SqlDialectDerivationTest}（同归本批登记，见差距文档订正注）；
 * 本类管的是<b>迁移脚本自身的可移植性</b>——脚本落 PG 之外的库时这些写法必然失败，
 * 与 MC01/MC02 同一口径（规范 §七 清单「多库兼容：新 DDL 无 PG 私有函数/类型
 * （{@code gen_random_uuid()} / {@code JSONB} 直操 / {@code ->>} 运算符 / {@code timestamptz} /
 * {@code ON CONFLICT} 裸写）」逐字对应）。两面的存量清单<b>各自单源</b>，禁止互相引用计数。</p>
 *
 * <p><b>分桶口径</b>（与 {@link DdlShapeScanner}、{@link Mc02TypeWhitelistTest}、
 * {@link DrBaselineColumnsTest} 共用，裁决 D-#140E-3）：V200+ 治理段做<b>成员级</b>
 * 「文件→命中数」名录（零容忍新增），V&lt;150 与 V150~199 段做<b>计数级</b>钉死。
 * 时间形态（{@code timestamptz} / {@code TIMESTAMP WITH TIME ZONE}）同规，但治理段基线为 0、
 * 存量段基线为 <b>25</b>（V48 outbox/saga 正文列定义——本批实测<b>新增发现</b>，此前全仓零处记载，
 * 而各脚本头部自述注释一律写「无 timestamptz ✓」，属"注释自述合规≠正文合规"的现行实例）。
 * "治理段零违例"必须由<b>存量段确实命中 25</b> 这条实文件反向对照 + 合成串自检共同支撑，
 * 否则零命中只是解析面空转的另一种假绿形态（正是差距文档 D-15/D-16 两个 lint 假绿的成因形态）。</p>
 *
 * <p><b>反假绿四锁</b>：①扫描面非空（240 枚脚本、正文非空）；②<b>反向对照</b>——必须真的看见
 * 已知密集命中点（V111 的 {@code ::} 18 处、V12 的 {@code ON CONFLICT} 11 处、V18 的
 * {@code RETURNING} 4 处、V48 的 {@code gen_random_uuid}），看不见已知违例＝谓词空转；
 * ③GOVERNED 名录与存量计数<b>双向相等</b>（新增红、消失也红）；④合成串自检——每个 PG 专有形态
 * 判据都要"能判非法已知串、且不判非法合法串"，探针凭证（真实文件注入后本类变红）见差距文档
 * §修订记录 v1.0.12。</p>
 *
 * <p><b>不纳入本门禁的形态（写明，不留含糊）</b>：字面量数组形态 {@code []}（实测 V200+ 命中 5 处
 * 全部位于 V246 的 {@code '[]'::jsonb} 种子值内，是 JSON 字面量而非 PG 数组类型，按类型面判会假红）
 * 与 {@code ILIKE}（属 Mapper/运行期 SQL 面，同一理由归 {@code SqlDialectDerivationTest}）。
 * 本类只判 DDL 脚本文本内的四类 PG 专有语法：{@code ::} cast、{@code ON CONFLICT}、
 * {@code RETURNING}、{@code gen_random_uuid()}，外加 JSONB 运算符与带时区时间形态。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='Mc03TimeFormTest'}（{@code ecos_backend/} 下执行）。</p>
 */
@DisplayName("#144/MC03：DDL 可移植形态门禁（时间形态全源零容忍·V200+ 文件级名录·存量计数钉死）")
class Mc03TimeFormTest {

    /** 带时区时间形态（MC02 点名禁 {@code timestamptz} 私有语义）。 */
    private static final Pattern TIME_TZ = Pattern.compile(
            "(?i)timestamptz|TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE");

    /** PG {@code ::} 显式 cast（MC03 点名）。 */
    private static final Pattern PG_CAST = Pattern.compile("::[A-Za-z_]");

    /** {@code ON CONFLICT} 裸写（MC03 点名，§七 清单原文同名）。 */
    private static final Pattern ON_CONFLICT = Pattern.compile("(?i)\\bON\\s+CONFLICT\\b");

    /** {@code RETURNING} 裸写（MC03 点名）。 */
    private static final Pattern RETURNING = Pattern.compile("(?i)\\bRETURNING\\b");

    /** {@code gen_random_uuid()}（MC01 点名，与 §七 清单「多库兼容」条同名）。 */
    private static final Pattern GEN_UUID = Pattern.compile("(?i)gen_random_uuid\\s*\\(");

    /** JSONB 运算符 {@code ->} / {@code ->>} / {@code #>}（MC03 点名，禁入 Mapper，同理禁入脚本）。 */
    private static final Pattern JSON_OP = Pattern.compile("->>?|#>>?");

    /**
     * GOVERNED 段（V200+）成员级名录：键=文件名，值=该文件内 PG 专有语法命中数（实测 2026-10-15）。
     *
     * <p>8 枚文件 = 7 枚种子脚本的 {@code ON CONFLICT} upsert（V246/V263/V264/V265/V266/V280/V281）
     * + V276 的 1 处 {@code ::} cast。这些是 V200 治理<b>之后</b>新增脚本里的现行违例 ⇒ 按裁决
     * D-#144A-3 进「待收口＋新增禁止」名录：存量脚本已实跑、按 IR03 不回改，收口去向 =
     * 为多方言交付另立 01-ddl-多方言 对应脚本（属 144-c 需授权范围）；
     * 本门禁的作用是<b>确保 V288 起不再引入第 9 枚</b>。</p>
     */
    private static final Map<String, Integer> GOVERNED_PG_SYNTAX_PIN = Map.ofEntries(
            Map.entry("V246__security_policy_seed.sql", 12),
            Map.entry("V263__cognitive_sys_config_seed.sql", 1),
            Map.entry("V264__ai_engine_sys_config_seed.sql", 1),
            Map.entry("V265__wagent_sys_config_seed.sql", 6),
            Map.entry("V266__wagent_tool_contract_seed.sql", 1),
            Map.entry("V276__ecos_cognitive_model_features_json.sql", 1),
            Map.entry("V280__fc_backtest_review_threshold_seed.sql", 1),
            Map.entry("V281__ecos_entity_table_mapping.sql", 1));

    /**
     * 存量段计数钉死（键={@code 桶|规则}，值=命中数，实测 2026-10-15；只登记非零项）。
     *
     * <p><b>本批新增发现（此前全仓零处记载）</b>：{@code V<150|TIME_TZ=25} 全部来自
     * {@code V48__ecos_outbox_and_saga.sql} 的正文列定义
     * （{@code created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()} 等 25 枚），
     * 即 MC02 点名禁止的「{@code timestamptz} 私有语义」在 outbox/saga 六张表上是<b>实存违例</b>。
     * 此前之所以无人知道：①lint 的 D-15 假绿（DR04/DR05 用 Singleline 正则恒 0 命中，见差距文档）；
     * ②各脚本头部自述注释都写「无 timestamptz ✓」——自述与正文相反，正是
     * 「注释自述合规 ≠ 正文合规」的现行实例（本类锁②对 V200 的原文/剥离双向对照就是为这类形态设的）。
     * 该文件属 V&lt;150 段 ⇒ 按规范 §三「存量不补」只做计数钉死，不返工 ALTER；
     * V200+ 段才是零容忍面（见 {@link #noTimezoneBearingColumnTypeInGovernedSegment}）。</p>
     */
    private static final Map<String, Integer> LEGACY_COUNT_PIN = Map.ofEntries(
            Map.entry("V<150|PG_CAST", 50), Map.entry("V<150|ON_CONFLICT", 72),
            Map.entry("V<150|RETURNING", 4), Map.entry("V<150|GEN_UUID", 2),
            Map.entry("V<150|TIME_TZ", 25),
            Map.entry("V150~199|PG_CAST", 14), Map.entry("V150~199|ON_CONFLICT", 4),
            Map.entry("V150~199|GEN_UUID", 2));

    /**
     * 登记式「正文全注释」文件名录（整体作废脚本，只保留作废依据注释，实测仅 V47 一枚）。
     * 双向相等：新增此类文件须先登记（作废须走裁决），已登记文件被补上正文也要从名录里删掉。
     */
    private static final Set<String> REGISTERED_COMMENT_ONLY_SCRIPTS = Set.of(
            "V47__ecos_schema_isolation.sql");

    /** 全源脚本数下限（DDL 单源目录，铁律 v2.2 §3.1）。2026-10-16 二期 W2：V289 落地 ⇒ 238→239。
     *  2026-10-17 二期 W5（a982d713）V290 落地只加脚本未改本锚 ⇒ 模块全量 test 红；
     *  2026-10-21 #142/B4-3e-3 按实测订正 239→240。 */
    private static final int SCRIPT_FLOOR = 240;

    @Test
    @DisplayName("锁①：扫描面非空——240 枚脚本全部可读，正文为空者只允许登记在册的作废脚本")
    void scanSurfaceIsNonEmpty() throws IOException {
        Map<String, String> srcs = DdlShapeScanner.sources();
        assertEquals(SCRIPT_FLOOR, srcs.size(), "单源脚本数与钉死口径不一致（须同批改本值）");
        Scan scan = DdlShapeScanner.scan();
        assertTrue(scan.tables().size() >= 300, "建表解析数=" + scan.tables().size() + " 低于下限（扫描面退化）");
        Set<String> blank = new TreeSet<>();
        for (Map.Entry<String, String> e : srcs.entrySet()) {
            if (e.getValue().isBlank()) blank.add(e.getKey());
        }
        assertEquals(REGISTERED_COMMENT_ONLY_SCRIPTS, blank,
                "「剥离注释后正文为空」的文件集合与登记名录不一致。实测：" + blank
                        + "\n（V47 系 2026-10-11 卷09 H-7 裁决整体作废、只留作废依据注释的脚本；"
                        + "出现第二枚即须先裁决再登记，禁把空正文当作合规）");
    }

    @Test
    @DisplayName("锁②：反向对照——必须真看见 V111 的 ::、V12 的 ON CONFLICT、V18 的 RETURNING、V48 的 gen_random_uuid")
    void scannerActuallySeesTheKnownViolationShapes() throws IOException {
        Map<String, String> srcs = DdlShapeScanner.sources();
        assertEquals(18, count(PG_CAST, srcs.get("V111__ecos_dq_governance_v1.sql")),
                "V111 的 :: cast 命中数应为 18（实测），检不到 ⇒ 谓词或注释剥离退化");
        assertEquals(11, count(ON_CONFLICT, srcs.get("V12__ecos_sys_dict.sql")),
                "V12 的 ON CONFLICT 命中数应为 11");
        assertEquals(4, count(RETURNING, srcs.get("V18__ecos_goal_seed_highway.sql")),
                "V18 的 RETURNING 命中数应为 4");
        assertEquals(1, count(GEN_UUID, srcs.get("V48__ecos_outbox_and_saga.sql")),
                "V48 的 gen_random_uuid 命中数应为 1");
        assertEquals(1, count(ON_CONFLICT, srcs.get("V281__ecos_entity_table_mapping.sql")),
                "V281 的 ON CONFLICT 命中数应为 1（该枚在 V200+ 段，是名录里的现行待收口项）");

        // 「注释自述合规 ≠ 正文合规」的反向对照：V200 头部注释写着
        // "-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓"（原文第 60 行实存）。
        // 本判据只吃剥离注释后的正文 ⇒ 原文须能命中该字样、剥离后须为 0；
        // 若哪天连原文都不命中，说明文件被换或扫描面退化；若剥离后仍命中，说明违例进了正文。
        String v200Raw = DdlShapeScanner.rawSource("V200__ai_tool_registry.sql");
        assertTrue(Pattern.compile("(?i)timestamptz").matcher(v200Raw).find(),
                "V200 原文里连注释形式的 timestamptz 自述都没有 ⇒ 样本文件已变更，本对照失去意义，须换样本");
        assertEquals(0, count(TIME_TZ, srcs.get("V200__ai_tool_registry.sql")),
                "V200 剥离注释后仍命中 timestamptz ⇒ 违例进了正文（本门禁全源零容忍，必须红）");
    }

    @Test
    @DisplayName("谓词自检：六条判据都必须能判非法已知串，且不判非法等价合法串（防恒假谓词把零命中当真绿）")
    void predicatesBiteOnSyntheticStrings() {
        assertBites(TIME_TZ, "created_at TIMESTAMPTZ NOT NULL",
                "ts TIMESTAMP WITH TIME ZONE", "ts TIMESTAMP WITH time ZONE");
        assertClean(TIME_TZ, "ts TIMESTAMP", "ts TIMESTAMP(3)", "ts TIMESTAMP WITHOUT TIME ZONE");
        assertBites(PG_CAST, "SELECT x::jsonb FROM t", "CAST(a AS INT), b::text");
        assertClean(PG_CAST, "SELECT x FROM t", "a -> b");
        assertBites(ON_CONFLICT, "ON CONFLICT (id) DO UPDATE", "on\nCONFLICT (id)");
        assertClean(ON_CONFLICT, "conflict_level VARCHAR(20)", "unique_on_conflict VARCHAR(20)");
        assertBites(RETURNING, "INSERT ... RETURNING id", "RETURNING");
        assertClean(RETURNING, "returning_count INTEGER", "return_count INTEGER", "returns TEXT");
        assertBites(GEN_UUID, "id VARCHAR(36) DEFAULT gen_random_uuid()", "DEFAULT GEN_RANDOM_UUID()");
        assertClean(GEN_UUID, "id VARCHAR(36)  -- 应用侧 UUID");
        assertBites(JSON_OP, "attrs ->> 'k'", "attrs #> '{a}'");
        assertClean(JSON_OP, "layer VARCHAR(20)", "delta NUMERIC(10,2)");
    }

    @Test
    @DisplayName("时间形态：V200+ 治理段零带时区（零容忍）；V<200 存量 25 枚由计数钉死且此处做反向对照")
    void noTimezoneBearingColumnTypeInGovernedSegment() throws IOException {
        Map<String, String> srcs = DdlShapeScanner.sources();
        Map<String, Integer> governed = new TreeMap<>();
        Map<String, Integer> legacy = new TreeMap<>();
        for (Map.Entry<String, String> e : srcs.entrySet()) {
            int n = count(TIME_TZ, e.getValue());
            if (n == 0) continue;
            if (DdlShapeScanner.bucket(DdlShapeScanner.majorVersion(e.getKey())) == Bucket.GOVERNED) {
                governed.merge(e.getKey(), n, Integer::sum);
            } else {
                legacy.merge(e.getKey(), n, Integer::sum);
            }
        }
        assertEquals(Map.of(), governed,
                "V200+ 段带时区时间形态违例（MC02 点名禁 timestamptz 私有语义；§七 清单同名条）：\n" + governed
                        + "\n（本判据治理段基线为 0，任何命中都是新增 ⇒ 收口方向是 TIMESTAMP + 显式时区约定）");

        // 反向对照用实文件而非只用合成串：治理段"零命中"必须与存量段"确实命中 25"同时成立，
        // 才说明判据是活的。存量那 25 枚全在 V48（outbox/saga），按规范 §三 存量不补、只钉计数。
        assertEquals(Map.of("V48__ecos_outbox_and_saga.sql", 25), legacy,
                "存量段 timestamptz 实存 25 枚（V48 正文列定义，本批实测新增发现）。"
                        + "检不到 ⇒ TIME_TZ 判据或注释剥离面退化，治理段的\"零违例\"随之失去意义");
    }

    @Test
    @DisplayName("锁③：V200+ 段 PG 专有语法=文件级名录（双向相等；第 9 枚文件即红，收口后不清名录也红）")
    void governedPgSyntaxEqualsPendingClosureRoster() throws IOException {
        Map<String, String> srcs = DdlShapeScanner.sources();
        Map<String, Integer> actual = new TreeMap<>();
        for (Map.Entry<String, String> e : srcs.entrySet()) {
            int major = DdlShapeScanner.majorVersion(e.getKey());
            if (DdlShapeScanner.bucket(major) != Bucket.GOVERNED) continue;
            int n = count(PG_CAST, e.getValue()) + count(ON_CONFLICT, e.getValue())
                    + count(RETURNING, e.getValue()) + count(GEN_UUID, e.getValue())
                    + count(JSON_OP, e.getValue());
            if (n > 0) actual.put(e.getKey(), n);
        }
        assertEquals(new TreeMap<>(GOVERNED_PG_SYNTAX_PIN), actual,
                "V200+ 段 PG 专有语法（::/ON CONFLICT/RETURNING/gen_random_uuid/JSONB 运算符）"
                        + "与名录不一致。实测：\n" + render(actual)
                        + "\n（多一枚=红线回潮，种子脚本请改用显式 DELETE+INSERT 或方言片段；"
                        + "少一枚=该脚本已收口，必须同批把本名录收缩）");
    }

    @Test
    @DisplayName("存量段（V<200）PG 专有语法按桶×规则计数钉死：只增不减，减少也须同步改基线并留订正注")
    void legacyCountsArePinned() throws IOException {
        Map<String, String> srcs = DdlShapeScanner.sources();
        Map<String, Integer> actual = new TreeMap<>();
        Map<String, Pattern> rules = new LinkedHashMap<>();
        rules.put("TIME_TZ", TIME_TZ);
        rules.put("PG_CAST", PG_CAST);
        rules.put("ON_CONFLICT", ON_CONFLICT);
        rules.put("RETURNING", RETURNING);
        rules.put("GEN_UUID", GEN_UUID);
        rules.put("JSON_OP", JSON_OP);
        for (Map.Entry<String, String> e : srcs.entrySet()) {
            Bucket bucket = DdlShapeScanner.bucket(DdlShapeScanner.majorVersion(e.getKey()));
            if (bucket == Bucket.GOVERNED) continue;
            for (Map.Entry<String, Pattern> r : rules.entrySet()) {
                int n = count(r.getValue(), e.getValue());
                if (n > 0) actual.merge(DdlShapeScanner.bucketLabel(bucket) + "|" + r.getKey(), n, Integer::sum);
            }
        }
        assertEquals(new TreeMap<>(LEGACY_COUNT_PIN), actual,
                "存量段计数与基线不一致（增减都须同批改基线并留订正注）。实测计数：\n" + render(actual));
    }

    // ────────────────────────────────────────────────────────────
    // 判定与工具
    // ────────────────────────────────────────────────────────────

    private static int count(Pattern p, String src) {
        if (src == null) throw new AssertionError("样本脚本正文取不到（文件名拼写或扫描面退化）");
        Matcher m = p.matcher(src);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static void assertBites(Pattern p, String... samples) {
        for (String s : samples) {
            assertTrue(count(p, s) > 0, "判据检不到已知非法串（本类零命中即假绿）: " + s
                    + "  模式=" + p.pattern());
        }
    }

    private static void assertClean(Pattern p, String... samples) {
        for (String s : samples) {
            assertEquals(0, count(p, s), "判据把合法形态误判为违例（会导致种子脚本被无端拦截）: " + s
                    + "  模式=" + p.pattern());
        }
    }

    private static String render(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append("  ");
        }
        return sb.toString().trim().isEmpty() ? "(空)" : sb.toString();
    }
}

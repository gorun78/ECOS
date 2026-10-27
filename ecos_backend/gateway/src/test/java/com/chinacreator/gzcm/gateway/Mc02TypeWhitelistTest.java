package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.gateway.DdlShapeScanner.Bucket;
import com.chinacreator.gzcm.gateway.DdlShapeScanner.Column;
import com.chinacreator.gzcm.gateway.DdlShapeScanner.Scan;
import com.chinacreator.gzcm.gateway.DdlShapeScanner.Table;

/**
 * #144 门禁批 · MC02 类型白名单全源门禁（差距文档 §9.7.1「静态可即时实装」档命名落差项之一）。
 *
 * <p><b>差距来源</b>：差距文档 §7.7（篇 6 kb 侧门禁行）与 §8.4.4（篇 8 行）具名缺失门禁
 * {@code Mc02TypeWhitelistTest}（同族别名 {@code Mc02RetirementTest}）按名 find 零命中；
 * 既有载体 {@code AiDdlComplianceTest}、{@code WAgentDdlShapeComplianceTest} 只在<b>各自域段</b>
 * 内校验类型红线 ⇒ <b>全源 242 枚脚本的类型面</b>无人守护。按 R9①「缺的门禁＝补代码实现」补本类。</p>
 *
 * <p><b>白名单判定面 = 规范原文 + 裁决 D-#144A-1（已同步回写规范，不留双轨）</b>：
 * MC02 的<b>存在理由</b>是「多库兼容：达梦/金仓官方 PG 兼容模式亦不保证私有类型」（规范 §六 设计原则
 * 与 MC02 括注），因此本门禁按「是否为 PG 私有形态」判定，而不是按字面枚举闭集判定，
 * 否则会把 ANSI 通用类型当成红线逼出无意义返工。具体分档（实测全源只有 17 种类型头令牌）：
 * <ul>
 *   <li><b>原文白名单</b>：{@code VARCHAR / TIMESTAMP / SMALLINT / INTEGER / BIGINT / TEXT /
 *       NUMERIC(p,s) / JSON}</li>
 *   <li><b>D-#144A-1 增补的 ANSI 通用等价形态</b>：{@code DATE}、{@code TIME}、{@code CHAR}、
 *       {@code CHARACTER}、{@code INT}（{@code INTEGER} 的 ANSI 标准缩写）。依据：五者在
 *       Oracle/MySQL/达梦/金仓均有同名原生类型，不属「PG 私有」；且全仓 {@code *_date} 列已广泛
 *       用于业务日期语义（改 TIMESTAMP 是语义劣化，不是收口）。</li>
 *   <li><b>D-#144A-1 增补：{@code DECIMAL(p,s)}＝{@code NUMERIC(p,s)} 同形态</b>。依据是 MC02
 *       原文本身：该条禁的是「裸 {@code NUMERIC}/{@code DECIMAL} <b>无参</b>」，反向即带精度合法；
 *       PG 里 DECIMAL 本就是 NUMERIC 的同义实现。实测 22 枚 DECIMAL <b>全部带 (p,s)</b>，
 *       故按原文判定为合规，不塞进"待收口"名录充数（那属反向工程）。</li>
 *   <li><b>仍判违例</b>：{@code JSONB}（原文点名禁）、{@code serial}/{@code bigserial}（原文点名禁
 *       ＋MC01）、{@code BOOLEAN}（白名单无此型；规范 DDL 模板的布尔形态是 {@code SMALLINT} 且列名
 *       走 DR05 的 {@code is_} 前缀）、{@code DOUBLE}/{@code FLOAT}（白名单无浮点，且 §七 清单
 *       「金额列非浮点」是同一取向）、{@code UUID}（PG 私有）、数组、{@code timestamptz}、
 *       裸 {@code NUMERIC}/{@code DECIMAL}。</li>
 * </ul>
 * 增补只放宽「PG 私有 vs ANSI 通用」这一根判据，不放宽任何原文点名禁止的形态；
 * 与本类同步落地的规范订正注见 {@code .trae/rules/数据库访问规范.md} §六 MC02 行后订正注
 * （原文不删，只追加）。</p>
 *
 * <p><b>分桶口径</b>（与 {@link DdlShapeScanner} 一致，裁决 D-#140E-3）：
 * V200+ 治理段做<b>成员级</b>名录（逐表逐列钉死，零容忍新增）；V&lt;150 与 V150~199 段做
 * <b>计数级</b>钉死。计数级的已知弱点在此写明、不掩盖：同一老脚本内「换一枚同规则违例」
 * （如把 JSONB 列改名后再加一枚 JSONB）计数不变 ⇒ 检不出；但老脚本受「版本号只增不改」
 * 台账纪律约束（{@code MigrationVersionUniquenessTest} 的顺延门），实际改动面已收敛到
 * 「必须新增 V200+ 脚本」，而新增段是成员级零容忍。两枚门合起来才是不漏项口径，单看本类会高估。</p>
 *
 * <p><b>反假绿四锁</b>：①扫描面非空（242 枚脚本 / 建表数下限）；②<b>解析器反向对照</b>——
 * 必须真的看见已知的三类违例形态：V281 的 {@code JSONB}、V285 的<b>双引号大写列名</b>上的
 * {@code BIGSERIAL}（解析器只认裸标识符时这张表会解析成零列，违例整张漏掉＝本类最危险的假绿形态，
 * 故把它钉成断言）、以及 V&lt;200 段的 {@code BOOLEAN}；③白名单<b>谓词自身</b>的判据自检
 * （已知非法类型必须判非法、已知合法类型必须判合法，防「恒 false 谓词空转绿」）；
 * ④GOVERNED 段名录双向相等（新增违例红、违例收口后也必须同步收缩名录）。</p>
 *
 * <p><b>本批实测发现（真违例，非存量登记可掩盖项）</b>：V281（R9③ 表名台账补建批产物）在
 * <b>新建</b>表上落了 {@code field_mappings JSONB}、{@code doc_anchor_json JSONB}、
 * {@code materialized BOOLEAN} 三处红线违例，且 dev PG 已实跑建表（information_schema 实查
 * {@code data_type=jsonb/jsonb/boolean}）⇒ 属「违例可长期静默累积」的现行实例，
 * 已列本类 {@link #PENDING_CLOSURE_GOVERNED_VIOLATIONS} 待收口名录并开 #144-c（V288 修正脚本
 * 需授权实跑）。收口后本名录必须清空，否则④锁变红。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='Mc02TypeWhitelistTest'}（{@code ecos_backend/} 下执行）。</p>
 */
@DisplayName("#144/MC02：DDL 类型白名单全源门禁（V200+ 成员级零容忍·存量计数钉死·谓词自检）")
class Mc02TypeWhitelistTest {

    /** MC02 白名单头令牌（规范 v1.2 扩列后的原文闭集）。 */
    private static final Set<String> WHITELIST_HEADS = Set.of(
            "VARCHAR", "TIMESTAMP", "SMALLINT", "INTEGER", "BIGINT", "TEXT", "NUMERIC", "JSON");

    /**
     * 裁决 D-#144A-1：ANSI 通用类型（非 PG 私有）视为白名单等价形态。
     * 依据与边界见类注释——只放宽「私有 vs 通用」判据，不放宽原文点名禁止的形态。
     */
    private static final Set<String> ANSI_PORTABLE_HEADS = Set.of(
            "DATE", "TIME", "CHAR", "CHARACTER", "INT");

    /**
     * 待收口名录（GOVERNED 段成员级违例）。格式：{@code 文件名#表名.列名:类型}。
     * 每一项都是<b>已定位、已登记、有收口编号</b>的现行违例，不是豁免——收口（V288 脚本 + 实跑）
     * 后必须把本名录清空，清空前若有人新增别的项，④锁即红。
     *
     * <p>实测 5 项，全部落在 V200+ 治理段（即"红线生效后再新建的表"）：
     * V208 的 {@code DOUBLE}、V281 的 {@code JSONB}×2 + {@code BOOLEAN}、V285 的 {@code BIGSERIAL}。
     * 其中 V281/V285/V208 三张表 dev PG <b>已实跑存在</b>（{@code information_schema} 只读实查），
     * 按 IR03「只加不删」＋「老脚本不改」纪律，收口只能追加新迁移 ⇒ 属 144-c 需授权项。</p>
     */
    private static final Set<String> PENDING_CLOSURE_GOVERNED_VIOLATIONS = Set.of(
            "V208__ecos_scenario_mind_v2.sql#public.ecos_scenario_mind_variant.initial_confidence:DOUBLE",
            "V281__ecos_entity_table_mapping.sql#ecos_ontology.ecos_entity_table_mapping"
                    + ".field_mappings:JSONB",
            "V281__ecos_entity_table_mapping.sql#ecos_ontology.ecos_entity_table_mapping"
                    + ".doc_anchor_json:JSONB",
            "V281__ecos_entity_table_mapping.sql#ecos_ontology.ecos_entity_table_mapping"
                    + ".materialized:BOOLEAN",
            "V285__td_runtime_task_persistence_create.sql#public.td_runtime_task_log.LOG_SEQ:BIGSERIAL");

    /** GOVERNED 段建表/列解析数下限（实测口径由本类首跑钉死，只增不减）。 */
    private static final int GOVERNED_TABLE_FLOOR = 60;

    /** 全源脚本数下限（与 {@code MigrationVersionUniquenessTest} 同口径，各自单源不互引）。
     *  2026-10-16 二期 W2：V289（td_user IAM 读面补列）落地 ⇒ 238→239；V288 由规范 §十 预留给 #144-c，本批不占。
     *  2026-10-17 二期 W5（a982d713）：V290（kb 导航面代码契约列）落地只加脚本未改本锚 ⇒ 该模块全量 test 当场红；
     *  2026-10-21 #142/B4-3e-3 按实测订正 239→240（三同类门与 {@code MigrationVersionUniquenessTest} 同批改）。
     *  2026-10-27 #141/B3 认知治理批：V291（3e-89）+ V292（R4 Mind 聚合根）落地只加脚本未改本锚
     *  ⇒ 按实测订正 240→242（三同类门同批再改）。
     *  2026-10-27 #142/B4 #146i：V294（fc_* 三表＋演示主体 seed）落地。勘误：V293（#146f td_audit_log
     *  登记）本批锚定时漏数（已入库既有脚本）⇒ 初值 243 系漏数，按实测订正 243→244
     *  （DrBaseline/Mc03/MigrationVersionUniquenessTest 同批改）。 */
    private static final int SCRIPT_FLOOR = 244;

    /**
     * 存量段计数钉死（键={@code 桶|类型头}，值=违例列数，实测 2026-10-15 取证，双向相等）。
     *
     * <p>读这份数字的正确方式：{@code V<150} 的 JSONB=119 与 {@code V150~199} 的 JSONB=17
     * 是<b>已知存量债</b>（规范 §三「存量不补」口径），钉计数只为防"新写法混进老脚本/老形态被扩散"；
     * 真正的零容忍面是 V200+ 成员级名录。裁决 D-#144A-1 生效后，本表已不含
     * {@code DATE}/{@code CHAR}/{@code INT}/{@code DECIMAL(p,s)}（ANSI 通用形态，不再计为违例）。</p>
     */
    private static final Map<String, Integer> LEGACY_COUNT_PIN = Map.of(
            "V<150|JSONB", 119, "V<150|BIGSERIAL", 40, "V<150|BOOLEAN", 36,
            "V<150|DOUBLE", 8, "V<150|UUID", 1,
            "V150~199|JSONB", 17, "V150~199|BOOLEAN", 14, "V150~199|BIGSERIAL", 9,
            "V150~199|FLOAT", 2, "V150~199|DOUBLE", 1);

    @Test
    @DisplayName("锁①：扫描面非空——全源脚本数、建表数、GOVERNED 段表数均达下限（空扫描面=假绿）")
    void scanSurfaceIsNonEmpty() throws IOException {
        Scan scan = DdlShapeScanner.scan();
        assertTrue(scan.tables().size() >= 300,
                "全源解析出的 CREATE TABLE 数=" + scan.tables().size() + " 低于下限 300（扫描面退化）");
        assertEquals(SCRIPT_FLOOR, DdlShapeScanner.scriptCount(),
                "单源脚本数与钉死口径不一致（须同批改本值与本类分桶基线）");
        assertTrue(scan.governed().size() >= GOVERNED_TABLE_FLOOR,
                "V200+ 段表数=" + scan.governed().size() + " 低于下限 " + GOVERNED_TABLE_FLOOR);
    }

    @Test
    @DisplayName("锁②：解析器反向对照——必须真看见 V281 的 JSONB、V285 引号大写列名的 BIGSERIAL、V<200 的 BOOLEAN")
    void scannerActuallySeesTheKnownViolationShapes() throws IOException {
        Scan scan = DdlShapeScanner.scan();
        Map<String, Table> byKey = scan.byQualifiedName();

        Table v281 = findTable(byKey, "V281__ecos_entity_table_mapping.sql", "ecos_entity_table_mapping");
        assertTrue(hasColumn(v281, "field_mappings", "JSONB"),
                "解析器看不到 V281 的 field_mappings JSONB ⇒ 类型判定面退化（该违例是已知实存项）");
        assertTrue(hasColumn(v281, "doc_anchor_json", "JSONB"),
                "解析器看不到 V281 的 doc_anchor_json JSONB");
        assertTrue(hasColumn(v281, "materialized", "BOOLEAN"),
                "解析器看不到 V281 的 materialized BOOLEAN");

        Table v285log = findTable(byKey, "V285__td_runtime_task_persistence_create.sql", "td_runtime_task_log");
        // 关键反向对照：V285 全表用双引号大写列名。解析器若只认裸标识符 ⇒ 这张表解析成零列，
        // 其 BIGSERIAL 主键违例会被整张漏掉（本类落地过程中实测到的假绿向量）。
        assertTrue(v285log.parsedAnyColumn(),
                "V285 的 td_runtime_task_log 解析出零列 ⇒ 双引号大写列名解析路径断了（该表违例整张漏检）");
        assertTrue(hasColumnIgnoreCase(v285log, "LOG_SEQ", "BIGSERIAL"),
                "解析器看不到 V285 td_runtime_task_log 的 \"LOG_SEQ\" BIGSERIAL（引号标识符路径未生效）");

        long legacyBoolean = countIllegal(scan.preGoverned(), "BOOLEAN");
        assertTrue(legacyBoolean > 0,
                "V<200 段未见任何 BOOLEAN 违例（实测存量存在）⇒ 桶划分或类型归一化出错");
    }

    @Test
    @DisplayName("锁③：白名单谓词自身自检——已知非法类型必须判非法、已知合法形态必须判合法（防恒假谓词空转）")
    void whitelistPredicateIsNotVacuous() {
        for (String illegal : List.of("JSONB", "BOOLEAN", "SERIAL", "BIGSERIAL", "NUMERIC", "DECIMAL",
                "TIMESTAMPTZ", "UUID", "TEXT[]", "FLOAT", "DOUBLE", "DOUBLEPRECISION", "BYTEA")) {
            assertTrue(isIllegalType(illegal), "谓词把已知非法类型判成合法: " + illegal);
        }
        for (String legal : List.of("VARCHAR(36)", "VARCHAR(64)", "TIMESTAMP", "TIMESTAMP(3)",
                "TIMESTAMPWITHOUTTIMEZONE", "SMALLINT", "INTEGER", "BIGINT", "TEXT", "NUMERIC(18,2)",
                "NUMERIC(10,0)", "DECIMAL(5,4)", "JSON", "DATE", "CHAR(1)", "INT")) {
            assertFalse(isIllegalType(legal), "谓词把白名单/ANSI 通用形态判成非法: " + legal);
        }
    }

    @Test
    @DisplayName("锁④：V200+ 治理段违例=待收口名录（成员级双向相等；新增违例即红，收口后不清名录也红）")
    void governedSegmentViolationsEqualPendingClosureList() throws IOException {
        Set<String> actual = new TreeSet<>(illegalViolations(DdlShapeScanner.scan().governed()));
        assertEquals(new TreeSet<>(PENDING_CLOSURE_GOVERNED_VIOLATIONS), actual,
                "V200+ 段类型违例清单与待收口名录不一致。实测：\n" + String.join("\n", actual)
                        + "\n（新增项=红线回潮，必须先改设计；消失项=收口已落地，必须同批清空本名录，"
                        + "否则豁免腐化成后门）");
    }

    @Test
    @DisplayName("V200+ 段零「解析成空列」的建表（空列表=整张表漏检，必须显式登记而非当作合规）")
    void governedSegmentHasNoBlindTables() throws IOException {
        List<String> blind = new ArrayList<>();
        for (Table t : DdlShapeScanner.scan().governed()) {
            if (!t.parsedAnyColumn()) blind.add(t.fileName() + "#" + t.tableName());
        }
        assertEquals(Set.of(), new TreeSet<>(blind),
                "V200+ 段存在解析不出任何列的建表 ⇒ 类型与基线列判定对该表空转（须先补解析口径）");
    }

    @Test
    @DisplayName("存量段（V<200）违例按桶×规则计数钉死：只增不减、减少也须同步改基线")
    void legacyViolationCountsArePinned() throws IOException {
        Scan scan = DdlShapeScanner.scan();
        Map<String, Integer> actual = new TreeMap<>();
        for (Bucket bucket : List.of(Bucket.LEGACY, Bucket.MID)) {
            for (Column c : illegalColumns(scan.filter(bucket))) {
                String rule = c.type().replaceAll("\\(.*", "").replaceAll("\\[.*", "");
                actual.merge(DdlShapeScanner.bucketLabel(bucket) + "|" + rule, 1, Integer::sum);
            }
        }
        Map<String, Integer> pin = new TreeMap<>(LEGACY_COUNT_PIN);
        // 基线留空时 actual 必非空（V<150 段的 JSONB/BOOLEAN 实测存量巨大），故本断言天然红，
        // 不存在「名录空 = 零违例」的空转绿；锁②的 BOOLEAN>0 反向对照另有一道兜底。
        assertEquals(pin, actual, "存量段类型违例计数与基线不一致（增减都须同批改基线并留订正注）。实测计数：\n"
                + render(actual));
    }

    // ────────────────────────────────────────────────────────────
    // 判定与工具
    // ────────────────────────────────────────────────────────────

    /**
     * 类型是否落在 MC02 白名单外。
     *
     * <p>判定链（顺序即口径，勿随意调换）：数组最优先 → 带精度的 {@code NUMERIC/DECIMAL} 放行
     * → 其余 {@code NUMERIC/DECIMAL}（裸无参）判非法 → {@code TIMESTAMP WITHOUT TIME ZONE} 放行
     * → 白名单头令牌 → D-#144A-1 的 ANSI 通用头令牌。</p>
     */
    private static boolean isIllegalType(String normalizedType) {
        String t = normalizedType.toUpperCase(Locale.ROOT);
        if (t.endsWith("[]")) return true;
        if (t.startsWith("NUMERIC") || t.startsWith("DECIMAL")) {
            return !t.matches("(?:NUMERIC|DECIMAL)\\(\\d+,\\d+\\)");
        }
        int paren = t.indexOf('(');
        String head = paren < 0 ? t : t.substring(0, paren);
        if (head.equals("TIMESTAMPWITHOUTTIMEZONE")) return false;
        return !WHITELIST_HEADS.contains(head) && !ANSI_PORTABLE_HEADS.contains(head);
    }

    private static List<Column> illegalColumns(List<Table> tables) {
        List<Column> out = new ArrayList<>();
        for (Table t : tables) {
            for (Column c : t.columns()) {
                if (isIllegalType(c.type())) out.add(new Column(t.tableName(), c.name(), c.type()));
            }
        }
        return out;
    }

    private static List<String> illegalViolations(List<Table> tables) {
        List<String> out = new ArrayList<>();
        for (Table t : tables) {
            for (Column c : t.columns()) {
                if (isIllegalType(c.type())) {
                    out.add(t.fileName() + "#" + t.tableName() + "." + c.name() + ":" + c.type());
                }
            }
        }
        return out;
    }

    private static long countIllegal(List<Table> tables, String typeHead) {
        long n = 0;
        for (Table t : tables) {
            for (Column c : t.columns()) {
                if (isIllegalType(c.type()) && c.type().startsWith(typeHead)) n++;
            }
        }
        return n;
    }

    private static Table findTable(Map<String, Table> byKey, String fileName, String tableBareName) {
        for (Table t : byKey.values()) {
            if (t.fileName().equals(fileName)
                    && DdlShapeScanner.simpleName(t.tableName()).equals(tableBareName)) {
                return t;
            }
        }
        throw new AssertionError("解析结果里没有 " + fileName + " 的 " + tableBareName
                + "（该表实存 ⇒ 扫描面或命名口径退化）");
    }

    private static boolean hasColumn(Table t, String column, String typeHead) {
        for (Column c : t.columns()) {
            if (c.name().equalsIgnoreCase(column) && c.type().startsWith(typeHead)) return true;
        }
        return false;
    }

    private static boolean hasColumnIgnoreCase(Table t, String column, String typeHead) {
        return hasColumn(t, column, typeHead);
    }

    private static String render(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append("  ");
        }
        return sb.toString();
    }
}

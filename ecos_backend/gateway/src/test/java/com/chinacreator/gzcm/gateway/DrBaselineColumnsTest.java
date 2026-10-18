package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * #144 门禁批 · DR06/DR07/DR08 基线列全源门禁（差距文档 §9.7.1「静态可即时实装」档具名类补齐）。
 *
 * <p><b>差距来源</b>：差距文档 篇6 §7.7 行 452 原文——「kb/cognitive 多处 … 违 MC01/MC02/MC03；
 * {@code Mc02RetirementTest}/{@code Mc03PortabilityTest}/{@code DrBaselineColumnsTest}/
 * {@code MigrationVersionUniquenessTest} 均不存在 ⇒ <b>违例可长期静默累积</b>」。
 * 前两者已由 #140/E-1 与本批 {@link Mc02TypeWhitelistTest} 补上，本类补 {@code DrBaselineColumnsTest}。
 * 既有载体 {@code AiDdlComplianceTest}、{@code WAgentDdlShapeComplianceTest} 只各自校验
 * AI 域段/WAgent 域段的建表形态 ⇒ 全源 239 枚脚本的<b>基线列面</b>无人守护（域内局部覆盖冒充
 * 全域不变量＝差距文档定义的假绿形态）。按 R9①「缺的门禁＝补代码实现」补本类。</p>
 *
 * <p><b>红线依据</b>（《数据库访问规范》v1.4 §一 原文，逐字对应）：
 * DR06 审计 5 字段固定 {@code create_time/update_time/create_by/update_by/is_deleted}；
 * DR07 {@code version_no VARCHAR(20) NOT NULL}；DR08 新表必带
 * {@code domain VARCHAR(50) NOT NULL DEFAULT 'default'} ⇒ 本类判定面 = <b>7 枚基线列</b>
 * 的存在性，外加 version_no/domain 两列的<b>类型形态</b>（VARCHAR(20)/VARCHAR(50)）。
 * §三 存量处置行原文「96 张新表无 domain/version_no 列 ⇒ <b>存量不补；V150+ 新表必带</b>」
 * 是本类分桶判定的规范依据，不是本类的发明。</p>
 *
 * <p><b>分桶口径</b>（与 {@link DdlShapeScanner}、{@link Mc02TypeWhitelistTest} 同一份，
 * 裁决 D-#140E-3：三枚门禁共用解析器与基线，禁各写一套）：
 * V200+ 治理段做<b>成员级</b>缺列名录（逐表钉死、零容忍新增）；V&lt;150 与 V150~199 段做
 * <b>计数级</b>钉死。已知弱点照例写明不掩盖：老脚本内「改名再加一枚」计数不变 ⇒ 检不出，
 * 但老脚本受版本号顺延纪律约束，实际新增面收敛到 V200+ 段（成员级）。
 * 注：本类 GOVERNED 阈值取 <b>V200</b>（而非规范 §三 的 V150）——依据是 v1.4 变更记录
 * 「V200 彻底治理落地，8 schema 封闭枚举」，V150~199 段规范已生效但仍含迁移期过渡表，
 * 故该段落在计数级钉死桶里；该口径选择在 {@code 差距文档} §9.7.1 本批订正注里有对应登记。</p>
 *
 * <p><b>反假绿四锁</b>：①扫描面非空（建表数 / 脚本数 / V200+ 表数下限）；②<b>解析器反向对照</b>——
 * 必须同时看见「缺一枚」的表（V242 缺 create_by/update_by）与「缺七枚」的表
 * （V285 {@code td_runtime_task_log}，其列名全为双引号大写形态）以及「七枚齐全」的表
 * （V227 {@code ecos_wagent_goal}）——只见缺不见全＝判定恒假，只见全不见缺＝判定恒真；
 * ③基线名录与计数<b>双向相等</b>（新表缺列红、有人补齐了不收缩名录也红，防豁免腐化）；
 * ④V200+ 段「解析出零列」的表必须为空名录——否则该表在本门禁下是空转绿（实测向量：
 * 双引号大写列名一度使 V285 两枚表解析成零列，见 {@link DdlShapeScanner} 解析口径③）。</p>
 *
 * <p><b>DR06 命名的历史别名不算合规</b>：{@code created_at/updated_at} 系规范 §三 登记的存量形态
 * （V150 走 RENAME + {@code v_*} 兼容视图双轨，「新代码写 create_time/update_time」），
 * 故 V200+ 段出现 {@code created_at} 而无 {@code create_time} 仍判缺列——这是<b>有意的严</b>，
 * 不是误报；实测命中见待收口名录里的 V281。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='DrBaselineColumnsTest'}（{@code ecos_backend/} 下执行）。</p>
 */
@DisplayName("#144/DR06-08：DDL 基线列全源门禁（V200+ 成员级零容忍·存量计数钉死·别名不豁免）")
class DrBaselineColumnsTest {

    /** DR06 审计 5 字段（规范原文逐字）。 */
    private static final Set<String> DR06_COLUMNS = Set.of(
            "create_time", "update_time", "create_by", "update_by", "is_deleted");

    /** DR07/DR08 追加的基线列。 */
    private static final String DR07_COLUMN = "version_no";
    private static final String DR08_COLUMN = "domain";

    /** 判定面全集 = 5 + 1 + 1 = 7 枚（缺任一枚即记为该表缺基线列）。 */
    private static final List<String> REQUIRED = List.of(
            "create_by", "create_time", "domain", "is_deleted", "update_by", "update_time", "version_no");

    /**
     * 待收口名录（GOVERNED 段缺基线列的表，成员级）。格式：{@code 文件名#表名->缺列1,缺列2}。
     * 每一项 = 已定位、已登记、有收口去向（#144-c 追加迁移，需授权实跑）的现行违例，不是豁免。
     * 补齐后必须同批清空本名录（锁③双向相等）。
     */
    private static final Set<String> PENDING_CLOSURE_GOVERNED_TABLES = Set.of(
            "V242__ecos_runtime_alert.sql#public.ecos_runtime_alert_record->create_by,update_by",
            "V243__ecos_runtime_event_dlq.sql#public.ecos_runtime_event_dlq->create_by,update_by",
            "V244__ecos_runtime_audit_retry.sql#public.ecos_runtime_audit_retry->create_by,update_by",
            "V245__ecos_runtime_llm_usage.sql#public.ecos_runtime_llm_usage->create_by,update_by",
            "V281__ecos_entity_table_mapping.sql#ecos_ontology.ecos_entity_table_mapping"
                    + "->create_by,create_time,update_by,update_time",
            "V285__td_runtime_task_persistence_create.sql#public.td_runtime_task_plan->create_time,update_time",
            "V285__td_runtime_task_persistence_create.sql#public.td_runtime_task_status"
                    + "->create_by,create_time,domain,update_by,update_time,version_no",
            "V285__td_runtime_task_persistence_create.sql#public.td_runtime_task_log"
                    + "->create_by,create_time,domain,is_deleted,update_by,update_time,version_no",
            "V285__td_runtime_task_persistence_create.sql#public.td_runtime_task_execution"
                    + "->create_by,create_time,domain,is_deleted,update_by,update_time,version_no");

    /**
     * 存量段计数钉死（键={@code 桶|列名}，值=缺该列的建表数，实测 2026-10-15 取证）。
     *
     * <p>读法：{@code V<150} 缺 version_no=160 / 缺 domain=156 正是规范 §三 登记的
     * 「96 张新表无 domain/version_no ⇒ 存量不补」同一件事在全源口径下的实测数
     * （规范那行只统计了当时纳入治理的 96 张，本门禁统计全 311 枚建表，故更大——
     * 差异已在差距文档本批订正注里登记，不留"两份数字互相打脸"）。
     * 计数级钉死的弱点：老脚本内改名再加一枚检不出；新增一律落 V200+ 段＝成员级零容忍。</p>
     */
    private static final Map<String, Integer> LEGACY_MISSING_COUNT_PIN = Map.ofEntries(
            Map.entry("V<150|create_by", 142), Map.entry("V<150|create_time", 141),
            Map.entry("V<150|domain", 156), Map.entry("V<150|is_deleted", 132),
            Map.entry("V<150|update_by", 139), Map.entry("V<150|update_time", 142),
            Map.entry("V<150|version_no", 160),
            Map.entry("V150~199|create_by", 26), Map.entry("V150~199|create_time", 26),
            Map.entry("V150~199|domain", 29), Map.entry("V150~199|is_deleted", 26),
            Map.entry("V150~199|update_by", 26), Map.entry("V150~199|update_time", 26),
            Map.entry("V150~199|version_no", 29));

    /** V200+ 段建表数下限（与 {@link Mc02TypeWhitelistTest} 同口径但各自单源）。 */
    private static final int GOVERNED_TABLE_FLOOR = 60;

    /** 全源建表数下限（实测 311，只增不减）。 */
    private static final int TOTAL_TABLE_FLOOR = 300;

    /** 全源脚本数下限（DDL 单源目录，铁律 v2.2 §3.1）。
     *  2026-10-16 二期 W2：V289（td_user IAM 读面补列）落地 ⇒ 238→239；V288 由规范 §十 预留给 #144-c，本批不占。 */
    private static final int SCRIPT_FLOOR = 239;

    @Test
    @DisplayName("锁①：扫描面非空——脚本数、建表数、V200+ 建表数均达下限（空扫描面=假绿）")
    void scanSurfaceIsNonEmpty() throws IOException {
        Scan scan = DdlShapeScanner.scan();
        assertTrue(scan.tables().size() >= TOTAL_TABLE_FLOOR,
                "全源解析出的 CREATE TABLE 数=" + scan.tables().size() + " 低于下限 " + TOTAL_TABLE_FLOOR);
        assertEquals(SCRIPT_FLOOR, DdlShapeScanner.scriptCount(),
                "单源脚本数与钉死口径不一致（须同批改本值与分桶基线）");
        assertTrue(scan.governed().size() >= GOVERNED_TABLE_FLOOR,
                "V200+ 段建表数=" + scan.governed().size() + " 低于下限 " + GOVERNED_TABLE_FLOOR);
    }

    @Test
    @DisplayName("锁②：反向对照——必须同时看见「缺一枚」「缺七枚」「七枚齐全」三形态（恒真/恒假都检不出违例）")
    void scannerActuallySeesAllThreeShapes() throws IOException {
        Scan scan = DdlShapeScanner.scan();

        Table partial = requireTable(scan, "V242__ecos_runtime_alert.sql", "partial");
        List<String> miss = missingBaseline(partial);
        assertTrue(!miss.isEmpty() && miss.size() < REQUIRED.size(),
                "V242 段样本表的缺列数应落在 (0,7) 区间，实测 " + miss + " ⇒ 缺列判定退化");

        Table none = requireTable(scan, "V285__td_runtime_task_persistence_create.sql", "all");
        assertEquals(REQUIRED, missingBaseline(none),
                "V285 运行时任务表应七枚基线列全缺（该表列名全为双引号大写形态，"
                        + "曾是本类落地过程中实测到的解析盲区），实测：" + missingBaseline(none));

        Table full = requireTable(scan, "V227__wagent_goal.sql", "complete");
        assertEquals(List.of(), missingBaseline(full),
                "V227 ecos_wagent_goal 实测七枚基线列齐全；若此处判缺 ⇒ 列名归一化（大小写/引号）出错");
    }

    @Test
    @DisplayName("锁④：V200+ 段不得存在「解析出零列」的建表（零列=本门禁对该表空转，必须显式登记）")
    void governedSegmentHasNoBlindTables() throws IOException {
        List<String> blind = new ArrayList<>();
        for (Table t : DdlShapeScanner.scan().governed()) {
            if (!t.parsedAnyColumn()) blind.add(t.fileName() + "#" + t.tableName());
        }
        assertEquals(Set.of(), new TreeSet<>(blind),
                "V200+ 段存在解析不出任何列的建表 ⇒ 基线列判定对该表空转（须先补 DdlShapeScanner 解析口径）");
    }

    @Test
    @DisplayName("锁③：V200+ 段缺基线列表=待收口名录（成员级双向相等；新表缺列即红，补齐不清名录也红）")
    void governedMissingBaselineEqualsPendingClosureList() throws IOException {
        Set<String> actual = new TreeSet<>();
        for (Table t : DdlShapeScanner.scan().governed()) {
            List<String> miss = missingBaseline(t);
            if (!miss.isEmpty()) {
                actual.add(t.fileName() + "#" + t.tableName() + "->" + String.join(",", miss));
            }
        }
        assertEquals(new TreeSet<>(PENDING_CLOSURE_GOVERNED_TABLES), actual,
                "V200+ 段缺基线列（DR06/07/08）表名录与待收口名录不一致。实测：\n"
                        + String.join("\n", actual)
                        + "\n（新增项=红线回潮，建表时就要带齐；消失项=收口已落地，必须同批清空本名录）");
    }

    @Test
    @DisplayName("V200+ 段 DR07/DR08 类型形态：version_no/domain 存在但非 VARCHAR(20)/VARCHAR(50) 即红")
    void governedVersionNoAndDomainTypeFormIsPinned() throws IOException {
        Set<String> actual = new TreeSet<>(formViolations(DdlShapeScanner.scan().governed()));
        assertEquals(Set.of(), actual,
                "V200+ 段 DR07/DR08 类型形态违例（须与《数据库访问规范》v1.4 §一 一致）。实测：\n"
                        + String.join("\n", actual)
                        + "\n（本名录实测为空＝零违例；空名录不等于恒绿——同批的"
                        + "「谓词在合成表上必须能变红」用例专门证明本判据能红）");
    }

    @Test
    @DisplayName("锁②补：谓词在合成表上必须能变红（缺列记缺、created_at 不豁免、VARCHAR(64) 记形态违例）")
    void predicateBitesOnSyntheticTables() {
        assertTrue(REQUIRED.size() == 7 && REQUIRED.containsAll(DR06_COLUMNS)
                        && REQUIRED.contains(DR07_COLUMN) && REQUIRED.contains(DR08_COLUMN),
                "判定面（7 枚基线列）被缩减 ⇒ 本门禁会静默放过 DR06/07/08 的某一枚，必须与规范 §一 同批改");

        Table complete = synthetic("id VARCHAR(36)", "create_time TIMESTAMP", "update_time TIMESTAMP",
                "create_by VARCHAR(100)", "update_by VARCHAR(100)", "is_deleted SMALLINT",
                "version_no VARCHAR(20)", "domain VARCHAR(50)");
        assertEquals(List.of(), missingBaseline(complete), "七枚齐全被判缺 ⇒ 列名匹配口径出错");
        assertEquals(List.of(), formViolations(List.of(complete)), "合法形态被判违例");

        Table noCreateBy = synthetic("id VARCHAR(36)", "create_time TIMESTAMP", "update_time TIMESTAMP",
                "update_by VARCHAR(100)", "is_deleted SMALLINT", "version_no VARCHAR(20)",
                "domain VARCHAR(50)");
        assertEquals(List.of("create_by"), missingBaseline(noCreateBy), "缺 create_by 未记缺");

        Table legacyAlias = synthetic("id VARCHAR(36)", "created_at TIMESTAMP", "updated_at TIMESTAMP",
                "create_by VARCHAR(100)", "update_by VARCHAR(100)", "is_deleted SMALLINT",
                "version_no VARCHAR(20)", "domain VARCHAR(50)");
        assertEquals(List.of("create_time", "update_time"), missingBaseline(legacyAlias),
                "created_at/updated_at 被当作 create_time/update_time 豁免 ⇒ 规范 §三「新代码写 create_time」失守");

        Table wrongForm = synthetic("id VARCHAR(36)", "create_time TIMESTAMP", "update_time TIMESTAMP",
                "create_by VARCHAR(100)", "update_by VARCHAR(100)", "is_deleted SMALLINT",
                "version_no VARCHAR(64)", "domain TEXT");
        assertEquals(2, formViolations(List.of(wrongForm)).size(),
                "version_no VARCHAR(64) 与 domain TEXT 未记形态违例 ⇒ DR07/DR08 类型面空转");
    }

    @Test
    @DisplayName("存量段（V<200）缺基线列按桶×列计数钉死：只增不减，减少也须同步改基线并留订正注")
    void legacyMissingCountsArePinned() throws IOException {
        Scan scan = DdlShapeScanner.scan();
        Map<String, Integer> actual = new TreeMap<>();
        for (Bucket bucket : List.of(Bucket.LEGACY, Bucket.MID)) {
            for (Table t : scan.filter(bucket)) {
                for (String m : missingBaseline(t)) {
                    actual.merge(DdlShapeScanner.bucketLabel(bucket) + "|" + m, 1, Integer::sum);
                }
            }
        }
        assertEquals(new TreeMap<>(LEGACY_MISSING_COUNT_PIN), actual,
                "存量段缺基线列计数与基线不一致。实测计数：\n" + render(actual));
    }

    // ────────────────────────────────────────────────────────────
    // 判定与工具
    // ────────────────────────────────────────────────────────────

    /**
     * 一张表的缺基线列清单（按 {@link #REQUIRED} 顺序返回，便于名录可 diff）。
     *
     * <p>口径：列名小写化后精确匹配——{@code created_at} 不豁免 {@code create_time}
     * （规范 §三：V150 已 RENAME 双轨，新代码写 create_time/update_time）。</p>
     */
    private static List<String> missingBaseline(Table t) {
        Set<String> present = new LinkedHashSet<>(t.columnNames());
        List<String> miss = new ArrayList<>();
        for (String r : REQUIRED) {
            if (!present.contains(r.toLowerCase(Locale.ROOT))) miss.add(r);
        }
        return miss;
    }

    /**
     * 取指定 V 文件内的样本表。{@code role} = {@code partial}（缺一到六枚）/
     * {@code all}（七枚全缺）/ {@code complete}（七枚齐全），
     * 只用于失败消息定位与角色挑选；挑不到即抛失败——防止「样本表被改名或删除后本用例静默跳过」。
     */
    private static Table requireTable(Scan scan, String fileName, String role) {
        for (Table t : scan.tables()) {
            if (!t.fileName().equals(fileName)) continue;
            List<String> miss = missingBaseline(t);
            boolean hit = switch (role) {
                case "partial" -> !miss.isEmpty() && miss.size() < REQUIRED.size();
                case "complete" -> miss.isEmpty();
                default -> miss.size() == REQUIRED.size();
            };
            if (hit) return t;
        }
        throw new AssertionError("V 文件 " + fileName + " 内没有「" + role + "」形态的样本表"
                + "（该形态实测存在 ⇒ 解析面或命名口径退化，本反向对照必须红）");
    }

    /**
     * DR07/DR08 类型形态违例清单：{@code version_no} 必须 {@code VARCHAR(20)}、
     * {@code domain} 必须 {@code VARCHAR(50)}（规范 §一 原文带长度；长度是乐观锁字段
     * 与租户域字段可跨库对齐的前提）。列不存在时不在本判据面内（由缺列名录负责，避免同一缺陷记两遍）。
     */
    private static List<String> formViolations(List<Table> tables) {
        List<String> out = new ArrayList<>();
        for (Table t : tables) {
            for (Column c : t.columns()) {
                String n = c.lowerName();
                if ((n.equals(DR07_COLUMN) && !c.type().startsWith("VARCHAR(20)"))
                        || (n.equals(DR08_COLUMN) && !c.type().startsWith("VARCHAR(50)"))) {
                    out.add(t.fileName() + "#" + t.tableName() + "." + c.name() + ":" + c.type());
                }
            }
        }
        return out;
    }

    /** 合成表（只用于谓词自检，不读文件、不进任何名录）。 */
    private static Table synthetic(String... columnDefs) {
        List<Column> cols = new ArrayList<>();
        for (String def : columnDefs) {
            int sp = def.indexOf(' ');
            cols.add(new Column("synthetic", def.substring(0, sp), def.substring(sp + 1)));
        }
        return new Table("synthetic.sql", 290, Bucket.GOVERNED, "ecos_synthetic_probe", cols);
    }

    private static String render(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }
}

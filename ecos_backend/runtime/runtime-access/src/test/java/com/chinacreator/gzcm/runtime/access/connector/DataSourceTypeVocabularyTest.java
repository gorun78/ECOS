package com.chinacreator.gzcm.runtime.access.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源类型词表<b>单源</b>门（#142/B4-3e-63，收口差距 <b>D-B4-78</b>）。
 *
 * <p><b>本门为什么必须存在</b>：同一套「数据源类型」词表此前散在四处各自演化——前端
 * {@code types.ts} 19 项、服务准入面 {@code SUPPORTED_TYPES} 10 项、连接器工厂归一表 16 条、
 * {@code JdbcConnector} 的方言分支 8 项，四处互不校验。实测出的两类真缺陷：
 * ①同物异名被 400 拒（前端 {@code gaussdb} → {@code GAUSSDB} ≠ 后端规范名 {@code GAUSS}）；
 * ②「归一到 JDBC 却无方言」的类型（MARIADB／DB2／HSQLDB／MONGODB）静默按 PostgreSQL 拼 URL，
 * 拿 PG 驱动去连别的库。本批把四处收敛为 {@link DataSourceTypeVocabulary} 一枚属主，
 * 其余面派生；本门钉住<b>派生面与收口前逐枚等价</b>，并钉住「无方言＝显式失败」。</p>
 *
 * <p>判据基线全部为<b>独立钉值</b>（从登记文档/源码抄录的字面量），不由枚举自身推导——
 * 否则枚举改坏时门会跟着一起改，失去见证力。「能变红」由合成夹具证明：
 * {@link #aliasFaceProblems(Map)} 与 {@link #dialectFaceProblems(Map)} 对故意改坏的解析结果
 * 必须报出具名问题。</p>
 */
class DataSourceTypeVocabularyTest {

    /**
     * 独立钉值：服务准入面声明序。#142/B4-3e-71 起 = 11 枚：
     * 收口前的 10 枚＋DORIS（B4 被拒闭集 9 枚逐枚定档批显式接承流——
     * 承流 / 前端面 / 显示面三维均已备，草案的
     * {@code registrable=false} 属「候选未裁」而非「应拒」）。
     */
    private static final List<String> REGISTRABLE_FROZEN = List.of(
            "POSTGRESQL", "MYSQL", "ORACLE", "MSSQL", "DM",
            "KINGBASE", "GAUSS", "CLICKHOUSE", "MINIO", "FILESYSTEM",
            "DORIS");

    /** 独立钉值（收口前 {@code ConnectorFactory.SOURCE_TYPES} 手抄 4 枚）。 */
    private static final List<String> PIPELINE_WHITELIST_FROZEN = List.of(
            "JDBC", "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST");

    /** 独立钉值（收口前 {@code ConnectorFactory.RELATIONAL_ALIASES} 手抄 16 条键→值，逐枚冻结）。 */
    private static final Map<String, String> PRE_CLOSE_ALIAS_KEYS = frozenAliasKeys();

    /** 词表可登记且归一到 JDBC 的成员，必须全部带方言（承流面自洽判据的输入面）。 */
    private static final List<String> NO_DIALECT_BUSINESS_TYPES = List.of(
            "MARIADB", "HSQLDB", "DB2", "MONGODB");

    // ─── 准入面／白名单面：枚数与成员逐枚不变 ───────────────────────────

    @Test
    @DisplayName("准入面＝收口 11 枚逐枚一致（#142/B4-3e-71 起 DORIS 扩面；改此项须同批订正台账）")
    void registrableFaceUnchanged() {
        List<String> actual = List.copyOf(DataSourceTypeVocabulary.registrableTypes());
        assertEquals(REGISTRABLE_FROZEN, actual,
                "服务准入面成员或顺序与已登记基线不符（扩面须同批改本基线并同步台账 v1.0.153）");
        assertEquals(REGISTRABLE_FROZEN.size(), actual.size(),
                "准入面枚数钉值 " + REGISTRABLE_FROZEN.size());
        // 正向对照：派生面自身非空（防空枚举＝假绿）
        assertEquals(REGISTRABLE_FROZEN.size(),
                DataSourceTypeVocabulary.registrableTypes().size());
    }

    @Test
    @DisplayName("pipeline 白名单面＝收口前 4 枚逐枚一致")
    void pipelineWhitelistFaceUnchanged() {
        List<String> actual = List.copyOf(DataSourceTypeVocabulary.pipelineWhitelistTypes());
        assertEquals(PIPELINE_WHITELIST_FROZEN, actual, "pipeline 白名单与收口前登记不符");
        assertEquals(4, actual.size(), "白名单枚数钉值 4");
    }

    // ─── 连接器归一面：收口前 16 条键解析结果逐枚不变 ────────────────────

    @Test
    @DisplayName("归一面：收口前 16 条键逐枚解析结果不变；唯一新增键 GAUSSDB→JDBC 属命名归一")
    void aliasFaceResolvesExactlyAsBefore() {
        Map<String, String> resolved = resolveAliasFaceViaFactory();
        List<String> problems = aliasFaceProblems(resolved);
        assertTrue(problems.isEmpty(), () -> "归一面回归：" + problems);

        // 键面总数：16 条既有＋GAUSSDB 别名＝17（独立钉值，防「静默少派生一条」）
        Set<String> aliasFaceKeys = DataSourceTypeVocabulary.aliasIndex().keySet();
        assertEquals(17, aliasFaceKeys.size(), "派生别名面键总数钉值 17");
        assertEquals("JDBC", ConnectorFactory.knownType("GAUSSDB"),
                "D-B4-78 病灶：前端 gaussdb→GAUSSDB 此前无键可归一，现须解析到 JDBC");
        assertEquals("JDBC", ConnectorFactory.knownType("gaussdb"),
                "knownType 大小写不敏感，前端原样小写名也要能归一");
    }

    @Test
    @DisplayName("夹具①：归一面少一条键／改错目标时判据必须报出具名红")
    void aliasFaceCriterionGoesRedOnFixture() {
        Map<String, String> broken = new LinkedHashMap<>(resolveAliasFaceViaFactory());
        broken.remove("DM");                       // 静默丢一条键
        broken.put("GAUSS", "GAUSSDB");            // 值被改成非连接器目标
        broken.put("MYSQL", "SOURCE_CSV");         // 归一到别族
        List<String> problems = aliasFaceProblems(broken);
        assertEquals(3, problems.size(), () -> "夹具应报 3 条，实报 " + problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("DM")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("GAUSS")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("MYSQL")), problems.toString());
        // 判据自身不失明：基线面非空（清空基线＝假绿的防回归对照）
        assertFalse(PRE_CLOSE_ALIAS_KEYS.isEmpty(), "冻结基线不得为空面");
        assertEquals(16, PRE_CLOSE_ALIAS_KEYS.size(), "冻结基线条数钉值 16");
    }

    // ─── 方言面：可登记 JDBC 成员必须有方言；无方言者显式失败 ──────────────

    @Test
    @DisplayName("方言面自洽：可登记且归一 JDBC 的成员零缺方言；无方言者一律不可登记")
    void dialectFaceConsistent() {
        assertEquals(List.of(), DataSourceTypeVocabulary.missingDialectForRegistrableJdbcTypes(),
                "可登记的 JDBC 类型必须带方言，否则建连腿只能显式失败");
        List<String> problems = dialectFaceProblems(viaDialectFace());
        assertTrue(problems.isEmpty(), () -> "方言面异常：" + problems);
    }

    @Test
    @DisplayName("夹具②：方言面被改坏（登记为可却丢方言）时判据必须报出具名红")
    void dialectFaceCriterionGoesRedOnFixture() {
        Map<String, DataSourceTypeVocabulary.Dialect> broken = new LinkedHashMap<>(viaDialectFace());
        broken.put("KINGBASE", null);
        List<String> problems = dialectFaceProblems(broken);
        assertEquals(1, problems.size(), () -> "夹具应报 1 条，实报 " + problems);
        assertTrue(problems.get(0).contains("KINGBASE"), problems.toString());
        // 正向对照：真实面 9 枚业务成员带方言 —— #142/B4-3e-71 起 DORIS 接承流后随 8→9
        //（若扫描面为空则上面两条都是假绿）
        assertEquals(9, registrableDialectFace().size(), "带方言的可登记业务成员枚数钉值 9");
    }

    /** 真实词表里「可登记业务成员 → 方言」（8 枚，白名单泛型入口不计）。 */
    private static Map<String, DataSourceTypeVocabulary.Dialect> registrableDialectFace() {
        Map<String, DataSourceTypeVocabulary.Dialect> m = new LinkedHashMap<>();
        for (String type : REGISTRABLE_FROZEN) {
            DataSourceTypeVocabulary v = DataSourceTypeVocabulary.find(type);
            if (v != null && v.dialect() != null) {
                m.put(type, v.dialect());
            }
        }
        return m;
    }

    @Test
    @DisplayName("无方言业务类型建连＝显式失败，不再静默按 PostgreSQL 拼 URL")
    void typesWithoutDialectFailExplicitly() {
        JdbcConnector jdbc = new JdbcConnector();
        String cfg = "{\"host\":\"127.0.0.1\",\"port\":\"5432\",\"database\":\"d\"}";
        for (String type : NO_DIALECT_BUSINESS_TYPES) {
            Map<String, Object> r = jdbc.testConnectionDetailed(cfg, type);
            assertEquals(Boolean.FALSE, r.get("success"), type + " 无方言必须 success=false");
            assertNull(r.get("driverClass"), type + " 无方言不得给出驱动类（收口前此处静默回落到 PG）");
            String err = String.valueOf(r.get("error"));
            assertTrue(err.contains("未登记 JDBC 方言"),
                    type + " 的失败文案必须点名「未登记 JDBC 方言」，实得：" + err);
            assertFalse(err.contains("缺少 jdbcUrl 或 host"),
                    type + " 已有 host，不得误报成「配置缺参数」（实得：" + err + "）");
        }
    }

    @Test
    @DisplayName("正向对照：带方言成员（含 GAUSSDB 别名）方言腿通过，driverClass 由词表给出")
    void dialectBearingTypesResolveDriverClass() {
        JdbcConnector jdbc = new JdbcConnector();
        String cfg = "{\"host\":\"127.0.0.1\",\"port\":\"3306\",\"database\":\"d\"}";
        Map<String, String> expectedDriver = Map.of(
                "MYSQL", "com.mysql.cj.jdbc.Driver",
                "GAUSS", "org.opengauss.Driver",
                "GAUSSDB", "org.opengauss.Driver",
                "CLICKHOUSE", "com.clickhouse.jdbc.ClickHouseDriver");
        for (Map.Entry<String, String> e : expectedDriver.entrySet()) {
            Map<String, Object> r = jdbc.testConnectionDetailed(cfg, e.getKey());
            assertEquals(e.getValue(), r.get("driverClass"),
                    e.getKey() + " 的驱动类须由词表派生（键面 type/driverClass/success/error/message 不变）");
            assertFalse(String.valueOf(r.get("error")).contains("未登记 JDBC 方言"),
                    e.getKey() + " 带方言，不得落「未登记 JDBC 方言」腿");
        }
    }

    @Test
    @DisplayName("URL scheme 有却无登记驱动＝显式失败，禁回落 org.postgresql.Driver")
    void unknownSchemeWithUnregisteredDriverFailsExplicitly() {
        JdbcConnector jdbc = new JdbcConnector();
        Map<String, Object> r = jdbc.testConnectionDetailed(
                "{\"jdbcUrl\":\"jdbc:db2://127.0.0.1:50000/samp\"}", "DB2");
        assertEquals(Boolean.FALSE, r.get("success"));
        assertNull(r.get("driverClass"));
        assertTrue(String.valueOf(r.get("error")).contains("未登记 JDBC 驱动类"),
                "实得：" + r.get("error"));
    }

    @Test
    @DisplayName("缺参腿与无方言腿分态：host 缺失仍报「缺少 jdbcUrl 或 host」")
    void missingHostLegKeepsItsOwnWording() {
        JdbcConnector jdbc = new JdbcConnector();
        Map<String, Object> r = jdbc.testConnectionDetailed("{\"database\":\"d\"}", "MYSQL");
        assertEquals(Boolean.FALSE, r.get("success"));
        String err = String.valueOf(r.get("error"));
        assertTrue(err.contains("缺少 jdbcUrl 或 host"), "实得：" + err);
        assertFalse(err.contains("未登记 JDBC 方言"), "MYSQL 有方言，不得报方言缺失（实得：" + err + "）");
    }

    // ─── 方言→URL 拼装腿配对门（解析真实源码，禁新增方言静默走 PG 的 default 腿）──

    /**
     * 独立钉值：{@code JdbcConnector.buildJdbcUrl} 的显式拼装腿面集（＝收口前该类自带的 8 枚类型分支）。
     * 本集合<b>不</b>由 {@link DataSourceTypeVocabulary.Dialect} 推导——推导了就等于「方言面自己给自己作证」，
     * 新增一枚方言却忘补 URL 腿时门会跟着变绿，而那正是与 D-B4-78 同一族的 fail-open
     * （{@code switch} 的 {@code default} 腿与 {@code POSTGRESQL} 合并，读不到分支就拿 PG 拼）。
     */
    private static final Set<String> DIALECT_URL_LEGS_FROZEN = Set.of(
            "POSTGRESQL", "MYSQL", "ORACLE", "SQLSERVER", "DM", "KINGBASE", "GAUSS", "CLICKHOUSE");

    /** 拼装腿的显式 case 标签面集与方言面／钉值的三方对账判据（真实仓与夹具共用同一段）。 */
    private static List<String> dialectUrlLegProblems(Set<String> caseLabels) {
        List<String> problems = new ArrayList<>();
        for (DataSourceTypeVocabulary.Dialect d : DataSourceTypeVocabulary.Dialect.values()) {
            if (!caseLabels.contains(d.name())) {
                problems.add("方言 " + d.name() + " 在 buildJdbcUrl 无显式 case 腿"
                        + "——它会落进与 POSTGRESQL 合并的 default 腿被静默拼成 jdbc:postgresql://（禁他族回落）");
            }
        }
        for (String label : caseLabels) {
            boolean known = false;
            for (DataSourceTypeVocabulary.Dialect d : DataSourceTypeVocabulary.Dialect.values()) {
                if (d.name().equals(label)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                problems.add("拼装腿 case " + label + " 不在词表方言面（死腿：该方言已被删除或改名）");
            }
        }
        if (!caseLabels.equals(DIALECT_URL_LEGS_FROZEN)) {
            problems.add("拼装腿面集与独立钉值 8 枚不一致，实得 " + new java.util.TreeSet<>(caseLabels));
        }
        return problems;
    }

    /** 解析同模块真实源码里的 {@code buildJdbcUrl} 方法体（读不到即红，禁默认通过）。 */
    private static String buildJdbcUrlBodyFromRealSource() {
        Path p = Path.of("src/main/java/com/chinacreator/gzcm/runtime/access/connector/JdbcConnector.java")
                .toAbsolutePath();
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("读不到 JdbcConnector 源码：" + p
                    + " —— 本门按真实源码解析，工作目录不是 runtime-access 模块根＝判据失明，禁当作通过");
        }
        String src;
        try {
            src = Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException("读取 JdbcConnector 源码失败：" + e.getMessage(), e);
        }
        int head = src.indexOf("private String buildJdbcUrl(");
        if (head < 0) {
            throw new IllegalStateException("源码里找不到 buildJdbcUrl 锚点——拼装腿判据的读取面已漂移，禁默认通过");
        }
        int open = src.indexOf('{', head);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, i + 1);
            }
        }
        throw new IllegalStateException("buildJdbcUrl 方法体大括号不配对，解析器无法定界");
    }

    /** 从方法体里取显式 {@code case NAME:} 标签（只认全大写下划线形态，其余分支不参与方言派发）。 */
    private static Set<String> caseLabelsOf(String body) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("case\\s+([A-Z][A-Z0-9_]*)\\s*:").matcher(body);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    @DisplayName("方言面枚数钉 8，且每枚方言在 buildJdbcUrl 都有显式拼装腿（无方言腿即红）")
    void everyDialectHasItsOwnUrlLeg() {
        assertEquals(8, DataSourceTypeVocabulary.Dialect.values().length,
                "方言枚数独立钉值（动方言面必须同批改本门，禁静默增减）");
        String body = buildJdbcUrlBodyFromRealSource();
        List<String> problems = dialectUrlLegProblems(caseLabelsOf(body));
        assertTrue(problems.isEmpty(), "方言→URL 拼装腿不配对：" + problems);
        // 「无方言＝拼不出」这条腿是本批收口 fail-open 的承流形态，必须在位而非仅靠测试观察
        assertTrue(body.contains("dialect == null) return null"),
                "buildJdbcUrl 必须保留「无方言即返回 null」的显式腿，禁回落他族。实得方法体头 200 字："
                        + body.substring(0, Math.min(200, body.length())));
    }

    @Test
    @DisplayName("夹具⑤：拼装腿缺一枚／多一枚死腿时判据必须各自报出具名红")
    void dialectUrlLegCriterionGoesRedOnFixture() {
        Set<String> missingGauss = new HashSet<>(DIALECT_URL_LEGS_FROZEN);
        missingGauss.remove("GAUSS");
        List<String> p1 = dialectUrlLegProblems(missingGauss);
        assertTrue(p1.stream().anyMatch(s -> s.contains("GAUSS") && s.contains("无显式 case 腿")),
                "缺一枚方言腿必须点名 GAUSS 并报「无显式 case 腿」，实得：" + p1);

        Set<String> withDeadLeg = new HashSet<>(DIALECT_URL_LEGS_FROZEN);
        withDeadLeg.add("INFLUXDB");
        List<String> p2 = dialectUrlLegProblems(withDeadLeg);
        assertTrue(p2.stream().anyMatch(s -> s.contains("INFLUXDB") && s.contains("死腿")),
                "多一枚词表外拼装腿必须点名 INFLUXDB 并报「死腿」，实得：" + p2);

        assertEquals(0, dialectUrlLegProblems(new HashSet<>(DIALECT_URL_LEGS_FROZEN)).size(),
                "面集与方言面一致时必须 0 条（对照，证明上面两条红不是判据恒真）");
    }

    // ─── 词表解析判据：词表外一律 null（禁猜默认），禁猜测式名字归一 ─────────

    @Test
    @DisplayName("find：大小写不敏感命中规范名与别名；词表外与空值返回 null；不做下划线↔连字符猜测")
    void findResolvesOnlyRegisteredNames() {
        assertEquals(DataSourceTypeVocabulary.GAUSS, DataSourceTypeVocabulary.find("gaussdb"));
        assertEquals(DataSourceTypeVocabulary.MSSQL, DataSourceTypeVocabulary.find(" SQLSERVER "));
        assertEquals(DataSourceTypeVocabulary.POSTGRESQL, DataSourceTypeVocabulary.find("postgresql"));
        assertNull(DataSourceTypeVocabulary.find(null), "空入参必须 null，禁回落默认类型");
        assertNull(DataSourceTypeVocabulary.find("  "));
        assertNull(DataSourceTypeVocabulary.find("REST_API"),
                "词表外名须由门点名，不允许判据替被检面补名（下划线↔连字符猜测归一已禁）");
        assertNull(DataSourceTypeVocabulary.find("S3"));
    }

    @Test
    @DisplayName("词表内不得出现重复规范名或别名冲突（buildIndex 的 IllegalStateException 真会抛）")
    void indexRejectsConflictingNames() {
        // 规范名唯一：allNames ＝ 19 成员 + 2 别名 = 21（独立钉值）
        assertEquals(21, DataSourceTypeVocabulary.allNames().size(),
                "词表名总数钉值 21（19 成员＋SQLSERVER＋GAUSSDB 两枚别名）");
        // 别名不指向白名单成员，也不与规范名相撞
        for (DataSourceTypeVocabulary v : DataSourceTypeVocabulary.values()) {
            for (String a : v.aliases()) {
                DataSourceTypeVocabulary hit = DataSourceTypeVocabulary.find(a);
                assertNotNull(hit, "别名 " + a + " 必须可解析");
                assertEquals(v, hit, "别名 " + a + " 归属须为 " + v.name());
                assertFalse(v.pipelineWhitelist(), a + " 的属主是 pipeline 白名单成员，不得兼作业务别名");
            }
        }
        // 冲突面能变红：同一别名挂两枚成员时判据必须报出具名红
        List<String> conflicts = aliasConflictProblems(List.of(
                new NameOwner("GAUSSDB", "GAUSS"), new NameOwner("GAUSSDB", "MSSQL")));
        assertEquals(1, conflicts.size(), () -> "夹具应报 1 条别名冲突，实报 " + conflicts);
        assertTrue(conflicts.get(0).contains("GAUSSDB"), conflicts.toString());
        assertEquals(List.of(), aliasConflictProblems(realAliasOwnerPairs()),
                "真实词表不得有别名冲突");
    }

    // ─── 连接器目标面：MINIO／FILESYSTEM 不进 JDBC ────────────────────────

    @Test
    @DisplayName("MINIO／FILESYSTEM 的连接器目标不被归一成 JDBC（承收口前 testConnection 契约）")
    void nonRelationalTargetsUnchanged() {
        assertEquals("MINIO", ConnectorFactory.knownType("MINIO"));
        assertEquals("FILESYSTEM", ConnectorFactory.knownType("FILESYSTEM"));
        // 白名单成员原样返回
        assertEquals("SOURCE_CSV", ConnectorFactory.knownType("source_csv"));
        ConnectorFactory factory = new ConnectorFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getConnector("S3"),
                "词表外类型须由工厂显式拒绝");
        assertTrue(factory.getRegisteredBeanTypes().isEmpty());
    }

    // ─── 判据实现（供真实面与合成夹具共用）────────────────────────────

    /** 经工厂公开静态面取「业务类型名 → 连接器目标」的真实解析结果。 */
    private static Map<String, String> resolveAliasFaceViaFactory() {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : PRE_CLOSE_ALIAS_KEYS.keySet()) {
            resolved.put(key, ConnectorFactory.knownType(key));
        }
        return resolved;
    }

    /** 真实词表的「别名 → 属主成员名」，规范名一并入面（别名与规范名相撞同样是冲突）。 */
    private static List<NameOwner> realAliasOwnerPairs() {
        List<NameOwner> m = new ArrayList<>();
        for (DataSourceTypeVocabulary v : DataSourceTypeVocabulary.values()) {
            m.add(new NameOwner(v.name(), v.name()));
            for (String a : v.aliases()) {
                m.add(new NameOwner(a, v.name()));
            }
        }
        return m;
    }

    /** 真实词表的「带方言成员 → 方言」。 */
    private static Map<String, DataSourceTypeVocabulary.Dialect> viaDialectFace() {
        Map<String, DataSourceTypeVocabulary.Dialect> m = new LinkedHashMap<>();
        for (DataSourceTypeVocabulary v : DataSourceTypeVocabulary.values()) {
            if (v.dialect() != null) {
                m.put(v.name(), v.dialect());
            }
        }
        return m;
    }

    /** 归一面判据：收口前 16 条键必须逐枚存在且目标不变。 */
    private static List<String> aliasFaceProblems(Map<String, String> resolved) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : PRE_CLOSE_ALIAS_KEYS.entrySet()) {
            String got = resolved.get(e.getKey());
            if (got == null) {
                out.add(e.getKey() + " 解析结果丢失（收口前归一为 " + e.getValue() + "）");
            } else if (!got.equals(e.getValue())) {
                out.add(e.getKey() + " 解析为 " + got + "，收口前为 " + e.getValue());
            }
        }
        return out;
    }

    /** 方言面判据：可登记且归一到 JDBC 的成员必须带方言。 */
    private static List<String> dialectFaceProblems(Map<String, DataSourceTypeVocabulary.Dialect> face) {
        List<String> out = new ArrayList<>();
        for (String type : REGISTRABLE_FROZEN) {
            DataSourceTypeVocabulary v = DataSourceTypeVocabulary.find(type);
            if (v == null || !"JDBC".equals(v.connectorTarget())) {
                continue; // 非 JDBC 目标者本就没有方言面
            }
            if (face.get(type) == null) {
                out.add(type + " 可登记且归一到 JDBC，却没有方言 ⇒ 建连腿只能显式失败");
            }
        }
        return out;
    }

    /** 别名冲突判据：同一名称（规范名或别名）不得挂两枚成员。 */
    private static List<String> aliasConflictProblems(List<NameOwner> pairs) {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> out = new ArrayList<>();
        for (NameOwner p : pairs) {
            String prev = seen.put(p.name(), p.owner());
            if (prev != null && !prev.equals(p.owner())) {
                out.add("名称 " + p.name() + " 同时属于 " + prev + " 与 " + p.owner());
            }
        }
        return out;
    }

    /** 词表名归属对（判据输入形态，与枚举解耦以便合成夹具喂入）。 */
    private record NameOwner(String name, String owner) {
    }

    private static Map<String, String> frozenAliasKeys() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("POSTGRESQL", "JDBC");
        m.put("MYSQL", "JDBC");
        m.put("ORACLE", "JDBC");
        m.put("SQLSERVER", "JDBC");
        m.put("CLICKHOUSE", "JDBC");
        m.put("DORIS", "JDBC");
        m.put("MARIADB", "JDBC");
        m.put("HSQLDB", "JDBC");
        m.put("DB2", "JDBC");
        m.put("MONGODB", "JDBC");
        m.put("MSSQL", "JDBC");
        m.put("DM", "JDBC");
        m.put("KINGBASE", "JDBC");
        m.put("GAUSS", "JDBC");
        m.put("MINIO", "MINIO");
        m.put("FILESYSTEM", "FILESYSTEM");
        return java.util.Collections.unmodifiableMap(m);
    }
}

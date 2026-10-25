package com.chinacreator.gzcm.runtime.access.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据源类型词表单源（#142/B4-3e-63，收口 D-B4-78）。
 *
 * <p><b>本枚举是「数据源业务类型」词表的唯一属主。</b>此前同一套类型名散在四处各自演化：
 * 前端 {@code pages/data-workbench/types.ts} 枚举 19 项（小写）、服务准入白名单
 * {@code DataSourceServiceImpl.SUPPORTED_TYPES} 10 项、连接器工厂归一表
 * {@code ConnectorFactory.RELATIONAL_ALIASES} 16 条、{@code JdbcConnector} 三张按类型
 * 分支的方言表 8 项。四处互不校验，实测出的直接后果是「同物异名」被 400 拒
 * （前端 {@code gaussdb} 归一成大写 {@code GAUSSDB}，后端规范名却是 {@code GAUSS}）与
 * 「无方言类型静默按 PostgreSQL 拼 URL」的 fail-open。</p>
 *
 * <p>收敛后的分工（本批只动承流侧，准入集不扩面）：</p>
 * <ul>
 *   <li>{@link #registrable()} 决定服务准入面——{@code DataSourceServiceImpl.SUPPORTED_TYPES}
 *       改为由本枚举派生，枚数与收口前<b>逐枚相同</b>（10 枚），本批不因归一命名而放宽准入；
 *       其后 #142/B4-3e-71 被拒闭集逐枚定档，DORIS 显式接承流，准入面 10→11 枚；</li>
 *   <li>{@link #connectorTarget()} 决定连接器归一面——{@code ConnectorFactory} 的白名单与
 *       别名表改为由本枚举派生，原有 16 条别名键的解析结果不变（由回归案钉住）；</li>
 *   <li>{@link #dialect()}／{@link #defaultPort()}／{@link #driverClass()} 决定 JDBC 方言面——
 *       {@code JdbcConnector} 不再自带类型分支，无方言的类型<b>显式失败</b>，禁回落 PostgreSQL。</li>
 * </ul>
 *
 * <p>门禁：{@code runtime-access} {@code DataSourceTypeVocabularyTest}（自洽＋派生面回归）＋
 * {@code gateway} {@code DatasourceTypeVocabularySingleSourceTest}（前端枚举↔后端承流面对账，
 * 被拒面只减不增）。</p>
 *
 * @author #142/B4-3e-63（D-B4-78）
 */
public enum DataSourceTypeVocabulary {

    // ── 可登记的关系型/对象存储/文件类型（registrable ＝ 服务准入面，收口前既有 10 枚）──

    /** PostgreSQL —— 方言基准。 */
    POSTGRESQL(List.of(), "JDBC", Dialect.POSTGRESQL, "5432",
            "org.postgresql.Driver", true, false),

    /** MySQL。 */
    MYSQL(List.of(), "JDBC", Dialect.MYSQL, "3306",
            "com.mysql.cj.jdbc.Driver", true, false),

    /** Oracle（serviceName／SID 形态）。 */
    ORACLE(List.of(), "JDBC", Dialect.ORACLE, "1521",
            "oracle.jdbc.OracleDriver", true, false),

    /** SQL Server —— 收口前工厂别名表里 {@code SQLSERVER} 与 {@code MSSQL} 两条键各自存在，此处定为「同一枚的两名」。 */
    MSSQL(List.of("SQLSERVER"), "JDBC", Dialect.SQLSERVER, "1433",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", true, false),

    /** 达梦。 */
    DM(List.of(), "JDBC", Dialect.DM, "5236",
            "dm.jdbc.driver.DmDriver", true, false),

    /** 人大金仓。 */
    KINGBASE(List.of(), "JDBC", Dialect.KINGBASE, "54321",
            "com.kingbase8.Driver", true, false),

    /** openGauss —— 前端历史名 {@code gaussdb} 属同物异名，登为别名（D-B4-78 的直接病灶）。 */
    GAUSS(List.of("GAUSSDB"), "JDBC", Dialect.GAUSS, "5432",
            "org.opengauss.Driver", true, false),

    /** ClickHouse。 */
    CLICKHOUSE(List.of(), "JDBC", Dialect.CLICKHOUSE, "8123",
            "com.clickhouse.jdbc.ClickHouseDriver", true, false),

    /** MinIO 对象存储 —— 无 JDBC 方言；准入但无连接器 bean，由属主服务显式分支承流（版本档位另判）。 */
    MINIO(List.of(), "MINIO", null, null, null, true, false),

    /** 本地文件系统 —— 同上，无连接器 bean。 */
    FILESYSTEM(List.of(), "FILESYSTEM", null, null, null, true, false),

    // ── 工厂别名面既有、但服务准入面从未纳入的类型（DORIS 已判「接承流」；其余 registrable＝未裁面 false）──

    /**
     * Doris —— 铁律 2.5#1 要求统一经 JdbcConnector；FE 走 MySQL 协议，方言按 MySQL 承流。
     *
     * <p>#142/B4-3e-71（B4 被拒闭集 9 枚逐枚定档）：本枚判「接承流」——
     * ①后端承流已有真实承重：{@code data-engine-impl} {@code SqlConsoleRegressionDorisTest}
     * 证 DORIS 归一 JDBC 走 DORIS_CONFIG 建连、{@code JdbcAdapterFactory#DorisAdapter} 注册、
     * {@code StorageType.DORIS} 落 APPLICATION 层（详设-02 §4.6 storage_kind 三枚之一）；
     * ②前端面早已存在 {@code DORIS} 显示枚（{@code CONNECTION_TYPES} 声明序第 3 枚，
     * i18n／desc／icon 通道三侧齐备）；③数据面既有 PMO-48-T5 字段（{@code feHost}／
     * {@code feHttpPort}）。本枚因此扩准——不属于「静默扩面」，是显式裁决：
     * 承流已有、前端已备、显示面已备，草案里唯一欠的
     * {@code should be registered but false} 属「候选未裁」而非「应拒」。
     * 扩面时三门同批：{@link #registrable()} → true、gate
     * {@code DatasourceTypeVocabularySingleSourceTest.ACCEPTED_FROZEN} 10→11 加 "doris"
     * 并剪 "doris" 出 {@code REJECTED_FROZEN}、{@code DataSourceTypeCatalogCarrierTest}
     * {@code REGISTRABLE_ORDER} 10→11 加 "DORIS"。</p>
     */
    DORIS(List.of(), "JDBC", Dialect.MYSQL, "9030",
            "com.mysql.cj.jdbc.Driver", true, false),

    /**
     * MariaDB —— 方言按 MySQL 协议承流（MariaDB 与 MySQL 同协议族，MySQL 驱动按 URL
     * {@code jdbc:mysql://} 连接 MariaDB 为发行方文档的常态形态），缺省端口 3306 同 MySQL 族。
     *
     * <p>#142/B4-3e-72：本枚此前 {@code dialect()=null}＝建连腿显式失败（收口 D-B4-78 时按
     * 「无方言＝显式失败」处置的 4 枚之一）。本批<b>只补方言腿，不扩准入面</b>：
     * {@code registrable} 仍为 {@code false}——服务准入面是否接承流是另一枚裁决
     * （B4 被拒闭集 9 枚逐枚定档口径，同 DORIS 的显式接承流才扩面），且前端可选面
     * 19 枚无 {@code mariadb}，扩准入会被 {@code DatasourceTypeVocabularySingleSourceTest}
     * 双向对账点名。补方言后本枚的建连腿从「未登记 JDBC 方言」移到真建连腿：
     * 驱动缺失时回落 3e-63 建立的「JDBC 驱动未加载」可读文案，不再静默按他族拼 URL（收口前形态），
     * 也不会再拿 PG 驱动去连（收口前 default 腿形态）。</p>
     */
    MARIADB(List.of(), "JDBC", Dialect.MYSQL, "3306",
            "com.mysql.cj.jdbc.Driver", false, false),

    /** HSQLDB —— 同上，且 runtime-access 无该驱动依赖。 */
    HSQLDB(List.of(), "JDBC", null, null, null, false, false),

    /** DB2 —— 同上，且 runtime-access 无该驱动依赖。 */
    DB2(List.of(), "JDBC", null, null, null, false, false),

    /** MongoDB —— 收口前注释自陈「走 JDBC 是简化归一」，本批按「无方言＝显式失败」处置。 */
    MONGODB(List.of(), "JDBC", null, null, null, false, false),

    // ── pipeline 规范类型（连接器 bean 名／节点白名单，非业务类型，故不可登记）──

    /**
     * JDBC 规范类型（白名单成员）——泛型入口。
     *
     * <p>它带方言不是「业务类型可登记」，而是承接收口前既有契约：
     * {@code JdbcConnector.testConnection(config)} 与 pipeline 侧
     * {@code resolveDriverClass("JDBC", jdbcUrl)} 都以 {@code JDBC} 为名进来，
     * 收口前落到 {@code default} 腿按 PostgreSQL 形态拼装。本批把这条回落<b>显式登记</b>在此，
     * 而不是继续藏在 switch 的 {@code default} 里——未裁业务类型（DB2／HSQLDB／MONGODB；
     * MARIADB 已于 #142/B4-3e-72 按 MySQL 族补方言）从此拿不到这条回落，无方言即显式失败。</p>
     */
    JDBC(List.of(), "JDBC", Dialect.POSTGRESQL, "5432",
            "org.postgresql.Driver", false, true),

    /** JDBC 规范类型别名形态（白名单成员，收口前既有；与 {@link #JDBC} 同方言）。 */
    SOURCE_JDBC(List.of(), "JDBC", Dialect.POSTGRESQL, "5432",
            "org.postgresql.Driver", false, true),

    /** CSV/Excel 文件导入连接器规范类型（bean 名 {@code SOURCE_CSV}）。 */
    SOURCE_CSV(List.of(), "SOURCE_CSV", null, null, null, false, true),

    /** REST API 数据源连接器规范类型（bean 名 {@code SOURCE_REST}）。 */
    SOURCE_REST(List.of(), "SOURCE_REST", null, null, null, false, true);

    /** JDBC 方言族——URL 拼装形态的属主枚举（拼装实现仍在 {@code JdbcConnector}，此处只点名）。 */
    public enum Dialect {
        POSTGRESQL, MYSQL, ORACLE, SQLSERVER, DM, KINGBASE, GAUSS, CLICKHOUSE
    }

    private final List<String> aliases;
    private final String connectorTarget;
    private final Dialect dialect;
    private final String defaultPort;
    private final String driverClass;
    private final boolean registrable;
    private final boolean pipelineWhitelist;

    DataSourceTypeVocabulary(List<String> aliases, String connectorTarget, Dialect dialect,
                             String defaultPort, String driverClass,
                             boolean registrable, boolean pipelineWhitelist) {
        this.aliases = aliases;
        this.connectorTarget = connectorTarget;
        this.dialect = dialect;
        this.defaultPort = defaultPort;
        this.driverClass = driverClass;
        this.registrable = registrable;
        this.pipelineWhitelist = pipelineWhitelist;
    }

    /** 该名的连接器归一目标（{@code JDBC}／{@code MINIO}／{@code FILESYSTEM}／{@code SOURCE_CSV}／{@code SOURCE_REST}）。 */
    public String connectorTarget() {
        return connectorTarget;
    }

    /** JDBC 方言；{@code null} ＝ 无方言，建连腿必须显式失败，禁回落他族。 */
    public Dialect dialect() {
        return dialect;
    }

    /** 缺省端口（无方言者为 {@code null}）。 */
    public String defaultPort() {
        return defaultPort;
    }

    /** JDBC 驱动类（无方言者为 {@code null}）。 */
    public String driverClass() {
        return driverClass;
    }

    /** 是否可登记为数据源（＝服务准入面成员）。 */
    public boolean registrable() {
        return registrable;
    }

    /** 是否为 pipeline 规范类型白名单成员。 */
    public boolean pipelineWhitelist() {
        return pipelineWhitelist;
    }

    /** 规范名（枚举名，大写）。 */
    public String canonical() {
        return name();
    }

    /**
     * 按大小写不敏感的名解析词表成员（规范名或别名皆命中）。
     *
     * <p>词表内别名一律大写且不含下划线，故<b>不</b>做「下划线↔连字符」猜测归一——
     * 前端 {@code rest_api} 之类派生出 {@code REST_API} 的名就是词表面未覆盖，须由门点名，
     * 不允许判据替被检面补名。</p>
     *
     * @return 命中的成员；{@code null} ＝ 词表外（调用方须显式拒绝，禁猜默认值）
     */
    public static DataSourceTypeVocabulary find(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return INDEX.get(raw.trim().toUpperCase(Locale.ROOT));
    }

    /** 词表全部成员名（规范名＋别名，大写，保持声明序）。 */
    public static Set<String> allNames() {
        return INDEX.keySet();
    }

    /** 服务准入面＝可登记类型的规范名集（单源派生，禁再手抄）。 */
    public static Set<String> registrableTypes() {
        Set<String> out = new LinkedHashSet<>();
        for (DataSourceTypeVocabulary v : values()) {
            if (v.registrable) {
                out.add(v.name());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** pipeline 规范类型白名单（连接器工厂 {@code SOURCE_TYPES} 的单源派生）。 */
    public static Set<String> pipelineWhitelistTypes() {
        Set<String> out = new LinkedHashSet<>();
        for (DataSourceTypeVocabulary v : values()) {
            if (v.pipelineWhitelist) {
                out.add(v.name());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * 连接器工厂归一表（业务类型名／别名 → 连接器目标）的单源派生。
     * 规范名与别名都成键，值为 {@link #connectorTarget()}。
     */
    public static Map<String, String> aliasIndex() {
        Map<String, String> out = new LinkedHashMap<>();
        for (DataSourceTypeVocabulary v : values()) {
            if (v.pipelineWhitelist) {
                continue; // 白名单成员不进别名表（承收口前 SOURCE_TYPES/RELATIONAL_ALIASES 分离语义）
            }
            out.put(v.name(), v.connectorTarget);
            for (String a : v.aliases) {
                out.put(a, v.connectorTarget);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** 该成员的别名（大写）。 */
    public List<String> aliases() {
        return aliases;
    }

    /** 可登记且归一到 JDBC 的成员必须带方言——供门禁判「无方言即不可登记」的一致性。 */
    public static List<String> missingDialectForRegistrableJdbcTypes() {
        List<String> problems = new ArrayList<>();
        for (DataSourceTypeVocabulary v : values()) {
            if (v.registrable && "JDBC".equals(v.connectorTarget) && v.dialect == null) {
                problems.add(v.name() + " 可登记且归一到 JDBC，却没有方言（建连腿会显式失败）");
            }
        }
        return problems;
    }

    private static final Map<String, DataSourceTypeVocabulary> INDEX = buildIndex();

    private static Map<String, DataSourceTypeVocabulary> buildIndex() {
        Map<String, DataSourceTypeVocabulary> m = new LinkedHashMap<>();
        for (DataSourceTypeVocabulary v : values()) {
            if (m.put(v.name(), v) != null) {
                throw new IllegalStateException("词表规范名重复：" + v.name());
            }
            for (String a : v.aliases) {
                DataSourceTypeVocabulary prev = m.put(a, v);
                if (prev != null && prev != v) {
                    throw new IllegalStateException("词表别名冲突：" + a + " 同时属于 " + prev.name() + " 与 " + v.name());
                }
            }
        }
        return Collections.unmodifiableMap(m);
    }
}

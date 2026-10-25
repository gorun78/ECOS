package com.chinacreator.gzcm.runtime.access.connector;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连接器工厂 — 根据数据源类型返回对应的连接器。
 * <p>
 * 已注册的连接器类型：
 * <ul>
 *   <li>JDBC — 关系型数据库（JdbcConnector），别名:
 *       POSTGRESQL / MYSQL / ORACLE / SQLSERVER / CLICKHOUSE / DORIS / MARIADB / HSQLDB / DB2 / MONGODB</li>
 *   <li>SOURCE_CSV — CSV/Excel 文件导入（CsvConnector）</li>
 *   <li>SOURCE_REST — REST API 数据源（RestApiConnector）</li>
 * </ul>
 * <p>
 * <b>注意</b>：SOURCE_TYPES 白名单只接受 pipeline 规范类型；关系型别名
 * （POSTGRESQL 等）走 {@link #knownType(String)} 归一化路径，
 * 白名单与 bean 注册类型二者都命中才注册 connector。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class ConnectorFactory {

    private final List<Connector> connectors;

    /**
     * Pipeline 节点类型白名单 — 所有合法的 SOURCE 类型。
     *
     * <p>#142/B4-3e-63（D-B4-78）：由手抄枚举改为 {@link DataSourceTypeVocabulary} 派生。
     * 成员集不变（JDBC／SOURCE_JDBC／SOURCE_CSV／SOURCE_REST），由
     * {@code DataSourceTypeVocabularyTest} 逐枚钉住。</p>
     */
    private static final Set<String> SOURCE_TYPES = DataSourceTypeVocabulary.pipelineWhitelistTypes();

    /**
     * 数据源业务类型（含别名）→ pipeline 规范类型映射（PMO-37 T1）。
     *
     * <p>#142/B4-3e-63（D-B4-78）：本表原先手抄 16 条，与服务准入面
     * {@code DataSourceServiceImpl.SUPPORTED_TYPES}、{@code JdbcConnector} 的方言分支各自演化，
     * 造成「前端 {@code gaussdb} ↔ 后端 {@code GAUSS} 同物异名被 400 拒」与
     * 「归一到 JDBC 却无方言的类型静默按 PostgreSQL 拼 URL」两类真缺陷。
     * 现由 {@link DataSourceTypeVocabulary} 单源派生：键＝业务成员规范名＋别名，
     * 值＝该成员的连接器目标。收口前 16 条键的解析结果逐枚不变，唯一新增键是别名
     * {@code GAUSSDB → JDBC}（命名归一，非准入扩面——可登记集仍由
     * {@code registrable()} 决定，枚数现由词表钉值 11，#142/B4-3e-71 起含 DORIS）。</p>
     */
    private static final Map<String, String> RELATIONAL_ALIASES = DataSourceTypeVocabulary.aliasIndex();

    public ConnectorFactory(List<Connector> connectors) {
        this.connectors = connectors;
    }

    /**
     * 根据连接类型获取连接器。
     *
     * @param type 连接类型（如 "JDBC", "SOURCE_CSV", "SOURCE_REST",
     *             "POSTGRESQL", "MYSQL" 等）
     * @return 匹配的连接器
     * @throws IllegalArgumentException 如果没有匹配的连接器
     */
    public Connector getConnector(String type) {
        // 先归一化别名 (POSTGRESQL → JDBC 等), 再校验白名单 / bean
        String normalized = knownType(type);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Unsupported connector type: " + type
                    + ". Known types: " + sourceTypesOrAliases());
        }
        return connectors.stream()
                .filter(c -> c.supportedType().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No connector bean found for type: " + normalized
                        + " (from " + type + "). Registered beans: " + getRegisteredBeanTypes()));
    }

    /**
     * 返回当前 Spring 容器中所有已注册的连接器类型。
     */
    public Set<String> getRegisteredBeanTypes() {
        return connectors.stream()
                .map(Connector::supportedType)
                .collect(Collectors.toSet());
    }

    /**
     * 返回所有合法的源类型（白名单 + 关系型别名）。
     */
    public static Set<String> getSourceTypes() {
        return sourceTypesOrAliases();
    }

    /**
     * 将业务数据源类型（POSTGRESQL/MYSQL/...）归一化为 pipeline 规范类型（JDBC）。
     * 白名单内类型原样返回。无匹配时返回 null（调用方据此抛 IAE）。
     *
     * @param type 业务类型或规范类型（大小写不敏感）
     */
    public static String knownType(String type) {
        if (type == null || type.isBlank()) return null;
        String t = type.trim().toUpperCase();
        if (SOURCE_TYPES.contains(t)) return t;
        return RELATIONAL_ALIASES.get(t);
    }

    private static Set<String> sourceTypesOrAliases() {
        Set<String> all = new LinkedHashSet<>(SOURCE_TYPES);
        all.addAll(RELATIONAL_ALIASES.keySet());
        return all;
    }
}

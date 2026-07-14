package com.chinacreator.gzcm.datanet.connector;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连接器工厂 — 根据数据源类型返回对应的连接器。
 * <p>
 * 已注册的连接器类型：
 * <ul>
 *   <li>JDBC — 关系型数据库（JdbcConnector）</li>
 *   <li>SOURCE_CSV — CSV/Excel 文件导入（CsvConnector）</li>
 *   <li>SOURCE_REST — REST API 数据源（RestApiConnector）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Component
public class ConnectorFactory {

    private final List<Connector> connectors;

    /** Pipeline 节点类型白名单 — 所有合法的 SOURCE 类型 */
    private static final Set<String> SOURCE_TYPES = Set.of(
            "JDBC", "SOURCE_JDBC",
            "SOURCE_CSV",
            "SOURCE_REST"
    );

    public ConnectorFactory(List<Connector> connectors) {
        this.connectors = connectors;
    }

    /**
     * 根据连接类型获取连接器。
     *
     * @param type 连接类型（如 "JDBC", "SOURCE_CSV", "SOURCE_REST"）
     * @return 匹配的连接器
     * @throws IllegalArgumentException 如果没有匹配的连接器
     */
    public Connector getConnector(String type) {
        // 先校验是否为已知类型
        if (!SOURCE_TYPES.contains(type.toUpperCase()) && !isDynamicType(type)) {
            throw new IllegalArgumentException(
                    "Unsupported connector type: " + type
                    + ". Known types: " + SOURCE_TYPES);
        }
        return connectors.stream()
                .filter(c -> c.supportedType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No connector bean found for type: " + type
                        + ". Registered beans: " + getRegisteredBeanTypes()));
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
     * 返回所有合法的源类型（白名单）。
     */
    public static Set<String> getSourceTypes() {
        return SOURCE_TYPES;
    }

    /**
     * 判断是否为动态注册类型（不在预定义白名单但存在 Bean）。
     */
    private boolean isDynamicType(String type) {
        return connectors.stream()
                .anyMatch(c -> c.supportedType().equalsIgnoreCase(type));
    }
}

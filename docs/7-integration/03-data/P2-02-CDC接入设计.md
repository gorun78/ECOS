# P2-02 CDC 接入设计

> ECOS data-engine | 2026-08-24 | 仅 flagship 版，本轮出设计，下轮实现

## 一、目标

为 flagship 版引入 Flink CDC 实时数据接入能力，作为 `FlinkCdcConnector` 注册进 runtime-access 的 ConnectorFactory。

## 二、架构

```
┌─────────────┐     binlog      ┌──────────────┐     sink      ┌─────────────┐
│  MySQL/PG   │ ──────────────→ │  Flink CDC   │ ────────────→ │  MinIO/PG   │
│  业务数据库  │   logical rep   │  Job         │   实时写入    │  数据湖      │
└─────────────┘                 └──────┬───────┘               └─────────────┘
                                       │
                                       │ 注册
                                       ▼
                               ┌──────────────┐
                               │ runtime-task │
                               │ (可见性管理)  │
                               └──────────────┘
```

## 三、支持数据库

| 数据库 | CDC 机制 | Flink Connector |
|--------|---------|----------------|
| MySQL | binlog | flink-connector-mysql-cdc |
| PostgreSQL | logical replication | flink-connector-postgres-cdc |

## 四、FlinkCdcConnector 设计

### 类结构

```java
@ConditionalOnProperty(name = "ecos.edition", havingValue = "flagship")
public class FlinkCdcConnector implements Connector {
    // 注册进 ConnectorFactory，type = "CDC"
    
    @Override
    public Connection connect(Map<String, Object> config) {
        // 1. 解析配置: datasourceId, tables, startPosition
        // 2. 创建 Flink Job: source → transform → sink
        // 3. 提交到 Flink 集群执行
        // 4. 返回 job handle
    }
    
    @Override
    public String getType() { return "CDC"; }
}
```

### ConnectorFactory 注册

```java
// runtime-access ConnectorFactory 动态扩展
public class ConnectorFactory {
    private final Map<String, Connector> connectors;
    
    // flagship 版自动注入 FlinkCdcConnector（@ConditionalOnProperty）
    // standard/enterprise 版不加载，ConnectorFactory 无 CDC 类型
}
```

## 五、配置参数

| 参数 | 必填 | 说明 |
|------|:---:|------|
| datasourceId | ✅ | 数据源 ID（含 CDC 连接信息） |
| tables | ✅ | 订阅表列表 |
| startPosition | ❌ | earliest/latest/specific，默认 latest |
| format | ❌ | canel-json/debezium-json，默认 debezium-json |
| parallelism | ❌ | Flink 并行度，默认 1 |

## 六、与 runtime-task 集成

- CDC Job 启动后注册为 runtime-task（taskType = CDC_SYNC）
- runtime-task 负责状态可见性（运行中/已停止/异常）
- 停止 CDC = 停止 Flink Job + 更新 runtime-task 状态

## 七、Maven profile 裁剪

```xml
<!-- flagship profile only -->
<profile>
    <id>flagship</id>
    <dependencies>
        <dependency>
            <groupId>com.ververica</groupId>
            <artifactId>flink-connector-mysql-cdc</artifactId>
        </dependency>
    </dependencies>
</profile>
```

```java
@ConditionalOnProperty(name = "ecos.edition", havingValue = "flagship")
@Component
public class FlinkCdcConnector implements Connector { ... }
```

## 八、限制

- standard/enterprise 版不加载 FlinkCdcConnector
- 需要独立的 Flink 集群（TaskManager + JobManager）
- Flink 集群运维成本高，仅旗舰版客户使用

# ECOS 产品完善方案 Phase 3 — 平台化

> **编制**: ECOS-PMO  
> **日期**: 2026-06-25  
> **基于**: Phase 1 产品化方案路线图（第230行起）  
> **周期**: 12周（Week 15–26，目标 2026-12-20）  
> **前提**: Phase 1 全部 7 子项目已完成；Phase 2 全部 6 子项目已完成  
> **核心约束**: 无 Docker、无云服务、无 gVisor/Firecracker、无真实 MQTT/OPC-UA 设备

---

## 一、Phase 1/2 验收确认

| 阶段 | 子项目 | 预期状态 |
|------|--------|:--:|
| Phase 1 | P1-1~P1-7 产品化 | ✅ |
| Phase 2 | P2-1 Neo4j 知识图谱 | ✅ |
| Phase 2 | P2-2 Agent Mesh 真实编排 | ✅ |
| Phase 2 | P2-3 OPA 策略动态加载 | ✅ |
| Phase 2 | P2-4 因果图可视化 | ✅ |
| Phase 2 | P2-5 自学习案例库 | ✅ |
| Phase 2 | P2-6 分级告警+WebSocket | ✅ |

**进入 Phase 3 前提**: Phase 2 全部 6 子项目验收通过，全端点回归 ≥50 端点 200。

---

## 二、Phase 3 目标

> **"平台化"** — 从"单机辅助决策"到"企业级数据平台"

| 对比维度 | Phase 2（智能化） | Phase 3（平台化） |
|---------|-----------------|-----------------|
| 分析查询 | PostgreSQL 直查 | DuckDB 嵌入式 OLAP + MinIO 对象存储 |
| 计算环境 | 纯后端 Java | 多语言 Workbook（SQL/Python/R 进程隔离） |
| 优化决策 | 因果图 what-if 推演 | NSGA-II 帕累托多目标优化 |
| 设备连接 | 无 | MQTT/OPC-UA（Java 库模拟）+ 数字孪生状态机 |
| 可观测性 | 无 | OpenTelemetry 全链路追踪 + Token 审计 |
| 多租户 | 基础 IAM | 资源配额 + 用量计费 |

### 降级总原则

> **Phase 3 不引入任何需要 Docker/K8s 的基础设施。所有"平台化"能力通过嵌入式 Java 库、本地进程隔离、PostgreSQL 扩展来实现，保留架构演进到真实分布式的能力。**

| 原始需求 | 环境约束 | 降级方案 |
|---------|---------|---------|
| Doris 分布式 OLAP | 无 Docker | DuckDB 嵌入式列存引擎 + MinIO 本地二进制 |
| gVisor 容器沙盒 | 无 gVisor | 进程级 subprocess + 超时/内存限制 |
| NSGA-II 分布式计算 | 无 Spark | 单机 Commons Math3 进化算法 + 线程池并行 |
| 真实 MQTT/OPC-UA 设备 | 无设备 | Eclipse Paho + Eclipse Milo Java 库 + 模拟设备 |
| OpenTelemetry Collector | 无 K8s | 嵌入式 OTLP exporter → 本地文件/PostgreSQL 存储 |
| 真实计费网关 | 无支付系统 | PostgreSQL 配额表 + 用量统计 + 超限拦截 |

---

## 三、子项目详设

### P3-1: 嵌入式数据湖（Week 15-18）

> **DuckDB 嵌入式 OLAP + MinIO 对象存储 → 替代 PostgreSQL 分析查询**

**背景**: Phase 1-2 所有分析查询走 PostgreSQL。随着数据量增长，聚合查询（Dashboard、报表）性能下降。设计文档规划了 Doris+MinIO 数据湖，但当前环境无法部署 Docker 版 Doris。

**决策**: P3-1 采用 **DuckDB 嵌入式列存引擎**（Java 集成，进程内运行，零部署依赖）+ **MinIO 本地二进制**（S3 兼容对象存储）。DuckDB 对 Parquet/CSV 文件有原生列式加速，聚合查询性能比行式 PostgreSQL 高 10-50 倍。MinIO 提供 S3 兼容 API，未来可平滑迁移到真实 S3/Doris。

**现状**: 分析查询走 `JdbcTemplate` 直查 PostgreSQL。前端 `MonitoringCenter`、`DataQualityDashboard`、`BizDashboard` 等页面使用 `apiFetchData` 调用后端聚合端点。

**目标**:
1. 部署 MinIO 本地二进制（端口 9000/9001），创建 `ecos-datalake` bucket
2. 集成 DuckDB（`org.duckdb:duckdb_jdbc`），提供嵌入式 OLAP 查询服务
3. 建立 PostgreSQL→MinIO/Parquet 定时导出管道
4. 分析类端点切换到 DuckDB，保留 PostgreSQL 降级路径

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-1.1 | MinIO 本地二进制部署 | MinIO Server `:9000` + Console `:9001`，systemd 自启脚本 `databridge-v2/scripts/minio-setup.sh` | BE |
| P3-1.2 | DuckDB 集成 | Maven 依赖 `duckdb_jdbc`，`DuckDBQueryService` 类：嵌入式连接池 + SQL 执行 | BE |
| P3-1.3 | PG→Parquet 导出管道 | `DataLakeExportService`：定时 Job 将指定 PG 表导出为 Parquet 文件写入 MinIO bucket `ecos-datalake` | BE |
| P3-1.4 | OLAP API 端点 | `GET /api/datalake/query?sql=...`（DuckDB 查询）、`GET /api/datalake/tables`（已导出的数据集列表）、`POST /api/datalake/export`（手动触发导出） | BE |
| P3-1.5 | 数据湖管理前端 | `DataLake.tsx`：MinIO bucket 浏览 + Parquet 文件预览 + SQL 查询控制台（结果表格） | FE |
| P3-1.6 | 分析页面切换 | `MonitoringCenter`/`BizDashboard`/`DataQualityDashboard` 增加数据源切换开关（PG ⇄ DuckDB），对比查询耗时 | FE |
| P3-1.7 | 回归验证 | 导出 3 张分析表（≥10万行）→ DuckDB 查询 vs PG 查询，DuckDB 聚合耗时 ≤ PG 的 30% | QA |

**技术细节**:

```java
// DuckDB 嵌入式连接（进程内，无需外部进程）
@Service
public class DuckDBQueryService {
    private final Connection conn;
    
    public DuckDBQueryService() {
        // DuckDB 嵌入式模式：数据库文件存储在本地
        this.conn = DriverManager.getConnection("jdbc:duckdb:./data/ecos_olap.db");
        // 注册 MinIO Parquet 文件为外部表
        // conn.createStatement().execute("CREATE VIEW IF NOT EXISTS ... AS SELECT * FROM 's3://ecos-datalake/...'");
    }
}
```

**验收 curl**:

```bash
# 1. MinIO 健康检查
curl http://localhost:9000/minio/health/live
# 预期: 200 OK

# 2. 触发数据导出
curl -X POST http://localhost:8080/api/datalake/export \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"table": "ecos_audit_log", "format": "parquet"}'
# 预期: {"code":200,"data":{"rows":100000,"file":"ecos_audit_log_20260625.parquet","size_mb":12.5}}

# 3. DuckDB OLAP 查询
curl -X POST http://localhost:8080/api/datalake/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"sql": "SELECT date_trunc(''day'', created_at) as dt, count(*) as cnt FROM ecos_audit_log GROUP BY dt ORDER BY dt DESC LIMIT 30"}'
# 预期: {"code":200,"data":{"columns":["dt","cnt"],"rows":[...],"elapsed_ms":<500}}

# 4. 数据集列表
curl http://localhost:8080/api/datalake/tables \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":[{"name":"ecos_audit_log","rows":100000,"size_mb":12.5},...]}
```

---

### P3-2: 多语言 Workbook 沙盒（Week 15-20）

> **SQL/Python/R 交互式计算 → 进程级隔离替代容器沙盒**

**背景**: 设计文档要求多语言 Workbook（Python/R/SQL）+ gVisor/Firecracker 容器隔离。前端已有 `CodeWorkbook.tsx`（617 行，支持 SQL + Python 编辑器 UI，但当前未接通后端执行）。

**决策**: P3-2 **不引入容器运行时**。采用进程级 subprocess 隔离：
- **SQL**: 直接 JDBC 执行，复用现有 DataSource（已有连接池，天然隔离）
- **Python**: subprocess 调用系统 Python3，通过 stdin 传入脚本、stdout 收集结果，`ProcessBuilder` 设置超时（30s）+ 内存限制（`-Xmx`）
- **R**: subprocess 调用 `Rscript`，同样超时+输出限制
- 输出沙箱：限制返回行数（≤1000 行）、文件大小（≤10 MB）

**现状**: `CodeWorkbook.tsx` 前端 UI 就绪（编辑器 + 结果表格），但后端执行端点未实现。`databridge-workspace` 模块有 `QueryHistoryService` 可复用。

**目标**:
1. 实现后端 Workbook 执行引擎（SQL/Python/R 三种运行时）
2. 进程隔离：超时 30s、最大内存 512MB、最大输出 10MB
3. 会话管理：每个 Workbook Session 独立工作目录
4. 前端对接后端执行 API，支持结果展示、历史查询

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-2.1 | Workbook 执行引擎核心 | `WorkbookExecutionEngine`：统一入口，路由 SQL/Python/R → 对应 Runtime | BE |
| P3-2.2 | SQL Runtime | `SqlRuntime`：JDBC 直连 PostgreSQL，支持 SELECT/DML，返回列名+行数据 | BE |
| P3-2.3 | Python Runtime | `PythonRuntime`：subprocess `python3 -c`，stdin 传入脚本，30s 超时，stdout 收集结果（JSON/CSV），`ulimit` 限制 512MB | BE |
| P3-2.4 | R Runtime | `RRuntime`：subprocess `Rscript`，同上约束。若系统无 R 则返回友好提示 | BE |
| P3-2.5 | 会话管理 + 工作目录 | `WorkbookSessionManager`：每个 session 独立 `data/workbooks/{sessionId}/` 目录，支持文件上传/下载 | BE |
| P3-2.6 | Workbook API 端点 | `POST /api/workbook/execute`（执行代码）、`GET /api/workbook/sessions`（会话列表）、`GET /api/workbook/history?sessionId=X`（历史查询记录） | BE |
| P3-2.7 | CodeWorkbook 前端对接 | `CodeWorkbook.tsx` 对接 `/api/workbook/execute`：单元格执行 + 结果表格 + 运行时选择（SQL/Python/R tabs）+ 错误信息展示 | FE |
| P3-2.8 | Workbook 文件管理前端 | 文件树侧栏：上传 CSV/JSON → 工作目录 → 代码中可引用 `./data.csv` | FE |
| P3-2.9 | 验证 | SQL 执行 5 条查询、Python 执行 pandas 分析脚本、R 执行 ggplot2 绑图 → 全部在 30s 内返回正确结果 | QA |

**技术细节**:

```java
// 进程隔离示例（Python）
ProcessBuilder pb = new ProcessBuilder(
    "python3", "-c", scriptContent
);
pb.directory(new File(workDir));
pb.redirectErrorStream(true);

Process p = pb.start();
boolean finished = p.waitFor(30, TimeUnit.SECONDS);
if (!finished) {
    p.destroyForcibly();
    throw new WorkbookTimeoutException("Python execution timed out after 30s");
}
String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
```

```python
# Python Runtime 约定：结果以 JSON 打印到 stdout
# 用户代码：
import json
import pandas as pd
df = pd.read_csv('./data.csv')
result = df.describe().to_dict()
print(json.dumps({"columns": list(result.keys()), "rows": [result]}))
```

**验收 curl**:

```bash
# 1. SQL 执行
curl -X POST http://localhost:8080/api/workbook/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"language":"sql","code":"SELECT count(*) as total, status FROM ecos_audit_log GROUP BY status"}'
# 预期: {"code":200,"data":{"columns":["total","status"],"rows":[...],"elapsed_ms":<200}}

# 2. Python 执行
curl -X POST http://localhost:8080/api/workbook/execute \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"language":"python","code":"import json; print(json.dumps({\"msg\": \"Hello from Python\", \"sum\": sum(range(100))}))"}'
# 预期: {"code":200,"data":{"output":"{\"msg\": \"Hello from Python\", \"sum\": 4950}"}}

# 3. 超时保护测试
curl -X POST http://localhost:8080/api/workbook/execute \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"language":"python","code":"import time; time.sleep(60)"}'
# 预期: {"code":408,"message":"执行超时（>30s），已终止进程"}

# 4. 会话历史
curl "http://localhost:8080/api/workbook/history?sessionId=abc123" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":[...]}
```

---

### P3-3: 帕累托寻优引擎（Week 17-22）

> **NSGA-II 多目标优化 + 帕累托前沿可视化**

**背景**: 设计文档要求 NSGA-II 多目标优化引擎，用于在多个冲突目标之间寻找帕累托最优解。典型场景：在"数据质量最大化"和"计算成本最小化"之间找到最优策略组合。

**决策**: P3-3 **纯 Java 实现 NSGA-II 算法**（基于 Commons Math3 遗传算法框架扩展），无需任何外部依赖。帕累托前沿用前端 ECharts 散点图渲染。

**现状**: WorldModel 已有 21 目标金字塔，但无优化求解能力。前端 `WorldModelViewer.tsx` 已有 5 个 Tab，无帕累托可视乎。

**目标**:
1. 实现 NSGA-II 核心算法（非支配排序 + 拥挤度距离 + 锦标赛选择 + 交叉变异）
2. 定义优化问题 DSL（目标函数、决策变量、约束条件）
3. 帕累托前沿 2D/3D 可视化
4. 与 WorldModel 目标体系集成

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-3.1 | NSGA-II 算法核心 | `NSGA2Engine`：非支配排序 + 拥挤度计算 + 锦标赛选择 + SBX 交叉 + 多项式变异。纯 Java，零外部依赖 | BE |
| P3-3.2 | 优化问题模型 | `OptimizationProblem` POJO：目标函数定义（多个 `ObjectiveFunction`）+ 决策变量（`DecisionVariable`，含类型+范围）+ 约束条件（`Constraint`） | BE |
| P3-3.3 | 目标函数注册 | `ObjectiveRegistry`：内置 ≥5 个常用目标函数（数据质量评分、计算耗时、存储成本、覆盖率、延迟），支持用户自定义 Java/Groovy 脚本 | BE |
| P3-3.4 | 优化 API 端点 | `POST /api/pareto/optimize`（提交优化问题，返回帕累托前沿解集）、`GET /api/pareto/problems`（已保存的优化问题列表）、`GET /api/pareto/result/{id}`（查询优化结果） | BE |
| P3-3.5 | WorldModel 集成 | `POST /api/pareto/from-scenario`：从 WorldModel 场景自动生成优化问题（场景目标→目标函数，输入变量→决策变量） | BE |
| P3-3.6 | 帕累托前端 | `WorldModelViewer.tsx` 第 6 Tab「帕累托寻优」：问题定义表单 + 2D/3D 散点图（ECharts）+ 前沿解详情表 | FE |
| P3-3.7 | 验证 | 定义 1 个双目标优化问题（DQ 最大化 + 成本最小化）→ 运行 200 代 → 输出 ≥5 个帕累托最优解 → 前端散点图正确渲染前沿面 | QA |

**技术细节**:

```java
// NSGA-II 伪代码结构
public class NSGA2Engine {
    public List<Individual> evolve(OptimizationProblem problem, int popSize, int generations) {
        List<Individual> population = initializeRandom(popSize, problem);
        for (int gen = 0; gen < generations; gen++) {
            List<Individual> offspring = crossoverAndMutate(population, problem);
            population.addAll(offspring);
            // 非支配排序
            List<List<Individual>> fronts = fastNonDominatedSort(population);
            // 拥挤度距离
            for (List<Individual> front : fronts) {
                crowdingDistanceAssignment(front);
            }
            // 环境选择（保留精英）
            population = selectNextGeneration(fronts, popSize);
        }
        return population; // 帕累托前沿 = population 中 rank=0 的个体
    }
}
```

**验收 curl**:

```bash
# 1. 提交优化问题
curl -X POST http://localhost:8080/api/pareto/optimize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "problemName": "数据治理策略优化",
    "objectives": [
      {"name": "数据质量评分", "direction": "max", "weight": 1.0},
      {"name": "计算成本", "direction": "min", "weight": 1.0}
    ],
    "variables": [
      {"name": "清洗频率", "type": "integer", "min": 1, "max": 24},
      {"name": "抽样率", "type": "double", "min": 0.1, "max": 1.0},
      {"name": "规则数量", "type": "integer", "min": 5, "max": 50}
    ],
    "populationSize": 100,
    "generations": 200
  }'
# 预期: {"code":200,"data":{"problemId":"opt-001","frontSize":8,"solutions":[...],"elapsed_ms":<10000}}

# 2. 从 WorldModel 场景生成
curl -X POST http://localhost:8080/api/pareto/from-scenario \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"scenarioId": "s-prod-failure"}'
# 预期: {"code":200,"data":{"problemId":"opt-002","objectives":["恢复速度","资源消耗","数据完整性"]}}

# 3. 查询优化结果
curl http://localhost:8080/api/pareto/result/opt-001 \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"frontSize":8,"solutions":[{"vars":{...},"objectives":{"数据质量评分":0.87,"计算成本":120.5}},...]}}
```

---

### P3-4: 企业数字孪生闭环（Week 19-24）

> **MQTT/OPC-UA 模拟设备接入 + 实时状态机 + 双向对齐**

**背景**: 设计文档要求物理设备通过 MQTT/OPC-UA 协议接入，实现数字孪生双向同步。当前环境无真实设备。

**决策**: P3-4 采用 **Java 库模拟设备**：
- **MQTT**: Eclipse Paho Java 客户端（纯 Java MQTT，无需 Mosquitto broker — 可用嵌入式 Moquette broker）
- **OPC-UA**: Eclipse Milo Java SDK（纯 Java OPC-UA 协议栈，Server+Client，无需外部服务）
- **设备模拟**: Java 定时任务生成模拟传感器数据（温度/压力/振动/流量等）
- **状态机**: 基于已有 `ObjectStateMachineController` + `ObjectTimelineController` 扩展

**现状**: `databridge-workspace` 已有 `ObjectStateMachineController` 和 `ObjectTimelineController`，提供对象状态机和时序追踪能力。前端 `MonitoringCenter.tsx` 有实时监控卡片。

**目标**:
1. 部署嵌入式 MQTT Broker（Moquette）和 OPC-UA Server（Milo）
2. 实现设备注册 + 模拟数据生成器
3. 数字孪生状态机：物理状态 ⇄ 数字状态的闭环
4. 设备监控面板

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-4.1 | 嵌入式 MQTT Broker | Moquette 嵌入式 Broker（`io.moquette:moquette-broker`），端口 1883，Spring Boot 自动启停 | BE |
| P3-4.2 | 模拟设备管理器 | `DeviceSimulatorService`：注册模拟设备（≥5 类：温度传感器、压力计、振动传感器、流量计、PLC 开关），定时推送 MQTT 消息 | BE |
| P3-4.3 | OPC-UA 模拟服务器 | Eclipse Milo Server（`org.eclipse.milo:sdk-server`），端口 4840，暴露模拟设备节点树 | BE |
| P3-4.4 | 数字孪生状态机 | `DigitalTwinService`：设备影子（reported/desired state）+ 状态变更事件 + 双向同步。基于 `ObjectStateMachineController` 扩展 | BE |
| P3-4.5 | 设备 API 端点 | `GET /api/twins/devices`（设备列表）、`GET /api/twins/{deviceId}/telemetry`（遥测数据）、`POST /api/twins/{deviceId}/command`（下发指令）、`GET /api/twins/{deviceId}/status`（当前状态） | BE |
| P3-4.6 | 数字孪生前端 | `MonitoringCenter.tsx` 新增「数字孪生」Tab：设备拓扑图 + 实时遥测折线图 + 设备状态指示灯 + 指令下发面板 | FE |
| P3-4.7 | 设备建模前端 | `DigitalTwinDesigner.tsx`：可视化注册设备类型、属性定义、状态机 | FE |
| P3-4.8 | 验证 | 启动 5 个模拟设备 → MQTT 推送 100 条遥测 → WebSocket 实时前端刷新 → 下发 1 条指令 → 设备状态变更 | QA |

**技术细节**:

```java
// Moquette 嵌入式 Broker
@Configuration
public class MqttBrokerConfig {
    @Bean(initMethod = "startServer", destroyMethod = "stopServer")
    public Server mqttBroker() {
        Server server = new Server();
        server.startServer(new MemoryConfig(new Properties() {{
            setProperty("port", "1883");
            setProperty("host", "0.0.0.0");
        }}));
        return server;
    }
}

// 模拟温度传感器
@Scheduled(fixedRate = 2000)
public void simulateTemperatureSensor() {
    double temp = 25.0 + Math.random() * 15; // 25-40°C
    String payload = String.format("{\"deviceId\":\"sensor-temp-01\",\"value\":%.2f,\"unit\":\"°C\",\"ts\":%d}",
        temp, System.currentTimeMillis());
    mqttClient.publish("ecos/devices/sensor-temp-01/telemetry", payload.getBytes(), 0, false);
}
```

**验收 curl**:

```bash
# 1. 设备列表
curl http://localhost:8080/api/twins/devices \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":[{"id":"sensor-temp-01","type":"温度传感器","status":"online"},...]}

# 2. 遥测数据
curl "http://localhost:8080/api/twins/sensor-temp-01/telemetry?limit=20" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":[{"ts":"2026-06-25T10:00:00","value":32.5,"unit":"°C"},...]}

# 3. 下发控制指令
curl -X POST http://localhost:8080/api/twins/sensor-temp-01/command \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"command":"set_threshold","params":{"max":38.0}}'
# 预期: {"code":200,"data":{"status":"accepted","commandId":"cmd-001"}}

# 4. 设备状态
curl http://localhost:8080/api/twins/sensor-temp-01/status \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"deviceId":"sensor-temp-01","state":"running","lastTelemetry":{"value":32.5,"ts":"..."},"shadow":{"desired":{"max":38.0},"reported":{"max":40.0}}}}
```

---

### P3-5: OpenTelemetry 全链路追踪（Week 21-26）

> **分布式追踪 + Token 消耗审计**

**背景**: 设计文档要求 OpenTelemetry 全链路追踪 + Token 消耗审计。Phase 2 已实现 Agent Mesh ReAct 循环，每次 LLM 调用消耗 Token，需要可观测。

**决策**: P3-5 **不引入独立 Collector/Exporter 服务**。采用：
- **追踪**: OpenTelemetry Java Agent（JVM 启动参数 `-javaagent:opentelemetry-javaagent.jar`）+ `LoggingSpanExporter` 输出到文件 + PostgreSQL `ecos_spans` 表
- **Token 审计**: 在 `ReActLoop`/LLM 调用点埋入 `TokenMeter`，记录每次调用的 prompt_tokens + completion_tokens + 模型名称 + 耗时
- **前端**: 追踪瀑布图 + Token 消耗仪表板

**现状**: GateWay 是单 Spring MVC Tomcat（非微服务），追踪链路相对简单。无分布式上下文传播。Phase 2 已有 Agent ReAct 循环但无 Token 记录。

**目标**:
1. 集成 OpenTelemetry SDK，SPAN 写入 PostgreSQL
2. Token 审计埋点 + 用量统计
3. 追踪查看前端

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-5.1 | OpenTelemetry SDK 集成 | Maven 依赖 `opentelemetry-api` + `opentelemetry-sdk` + `opentelemetry-exporter-otlp`。`TelemetryConfig`：初始化 `TracerProvider` + `LoggingSpanExporter` + 自定义 `PostgresSpanExporter` | BE |
| P3-5.2 | HTTP 拦截器埋点 | `TelemetryInterceptor`（Spring `HandlerInterceptor`）：自动为每个 HTTP 请求创建 SPAN，记录 method + path + status + duration | BE |
| P3-5.3 | Agent Mesh 埋点 | `ReActLoop`/`CoordinatorService`/`ToolInvoker` 各环节创建子 SPAN，记录 thought/action/observation 耗时 | BE |
| P3-5.4 | Token 审计服务 | `TokenAuditService`：记录每次 LLM 调用（`PromptTokens` + `CompletionTokens` + `Model` + `Cost`）。`TokenMeter` 注解驱动自动埋点 | BE |
| P3-5.5 | 追踪 API 端点 | `GET /api/telemetry/traces?limit=N`（最近 SPAN 列表）、`GET /api/telemetry/traces/{traceId}`（完整 Trace 树）、`GET /api/telemetry/tokens/summary?range=7d`（Token 用量汇总） | BE |
| P3-5.6 | 全链路前端 | `TelemetryViewer.tsx`：Trace 列表 + 瀑布图（Gantt 时间线）+ Span 详情面板。`TokenDashboard.tsx`：日/周/月 Token 消耗图表 + 模型成本统计 | FE |
| P3-5.7 | 验证 | 发起 Agent Mission → 验证 Trace 包含完整 SPAN 树（HTTP → ReAct → Tool → LLM Call）→ Token 记录准确 | QA |

**技术细节**:

```java
// OpenTelemetry 初始化
@Configuration
public class TelemetryConfig {
    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "ecos-gateway",
                ResourceAttributes.SERVICE_VERSION, "1.0.0"
            ))
        );
        
        SpanExporter pgExporter = new PostgresSpanExporter(jdbcTemplate);
        SpanExporter loggingExporter = LoggingSpanExporter.create();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(pgExporter).build())
            .addSpanProcessor(SimpleSpanProcessor.create(loggingExporter))
            .setResource(resource)
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal();
    }
}

// Token 审计注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TokenMeter {
    String model() default "default";
    String operation() default "";
}
```

```sql
-- ecos_spans 表
CREATE TABLE IF NOT EXISTS ecos_spans (
    span_id VARCHAR(32) PRIMARY KEY,
    trace_id VARCHAR(32) NOT NULL,
    parent_span_id VARCHAR(32),
    operation_name VARCHAR(256),
    service_name VARCHAR(128),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    status VARCHAR(16),
    attributes JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_spans_trace ON ecos_spans(trace_id);

-- ecos_token_usage 表
CREATE TABLE IF NOT EXISTS ecos_token_usage (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(32),
    model VARCHAR(64),
    operation VARCHAR(128),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    cost_estimate DECIMAL(10,6),
    latency_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**验收 curl**:

```bash
# 1. 最近 Trace 列表
curl "http://localhost:8080/api/telemetry/traces?limit=10" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":[{"traceId":"abc123...","rootSpan":"HTTP GET /api/...","duration_ms":1250,"spans":5},...]}

# 2. Trace 详情
curl http://localhost:8080/api/telemetry/traces/abc123 \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"traceId":"abc123","spans":[{"operation":"HTTP GET","duration_ms":200},...],"totalDuration_ms":1250}}

# 3. Token 用量汇总
curl "http://localhost:8080/api/telemetry/tokens/summary?range=7d" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"totalTokens":125000,"totalCost":1.25,"byModel":[{"model":"deepseek-v4","tokens":100000,"cost":1.0},...],"daily":[{"date":"2026-06-25","tokens":18000},...]}}
```

---

### P3-6: 多租户隔离 + 计费（Week 23-26）

> **Tenant 资源配额 + 用量统计**

**背景**: 设计文档要求多租户隔离和计费。Phase 1 已实现 IAM（9 张表），Phase 2 补充了 ABAC 和 OPA 策略。但缺少按租户的资源配额和用量统计。

**决策**: P3-6 **不引入真实计费网关**。实现：
- **资源配额**: 每个 Tenant 定义配额规则（最大存储 GB、最大 API 调用次数/天、最大 Workbook 执行次数/天、最大并发 Agent 任务数）
- **用量统计**: 从审计日志、SPAN 记录、Token 表中实时聚合各 Tenant 用量
- **超限拦截**: 在 Gateway Filter 层检查配额，超限返回 429
- **计费模拟**: 按用量 × 单价计算费用（不涉及真实支付）

**现状**: `sysman-api` 已有 `TenantDao`、`TenantConfigDao`、`TenantResourceUsageDao`（DAO 层已建，但未实现业务逻辑）。`TenantContext` 已有线程级租户隔离。

**目标**:
1. 实现 Tenant 资源配额管理
2. 用量统计 + 实时聚合
3. 超限拦截 Filter
4. 租户管理前端

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P3-6.1 | 配额数据模型 | `ecos_tenant_quota` 表：quota_type（STORAGE_MB/API_CALLS/WORKBOOK_EXEC/AGENT_TASKS）+ daily_limit + monthly_limit | BE |
| P3-6.2 | 配额管理服务 | `TenantQuotaService`：CRUD 配额规则；`QuotaEnforcer`：检查当前用量是否超限（Redis/内存缓存 + DB 兜底） | BE |
| P3-6.3 | 用量采集 | `UsageCollector`：从 `ecos_audit_log`/`ecos_spans`/`ecos_token_usage` 定期（每分钟）聚合各 Tenant 用量写入 `ecos_tenant_usage` 汇总表 | BE |
| P3-6.4 | Gateway 拦截 Filter | `QuotaFilter`（Spring `OncePerRequestFilter`）：请求前检查配额 → 超限返回 429 `{"code":429,"message":"Tenant daily API quota exceeded (10000/10000)"}` | BE |
| P3-6.5 | 计费模拟 | `BillingCalculator`：单价配置（`ecos_billing_rate` 表）+ 月度账单生成（`ecos_billing_invoice` 表） | BE |
| P3-6.6 | 租户 API 端点 | `GET /api/tenants/{id}/quota`（配额信息）、`PUT /api/tenants/{id}/quota`（修改配额）、`GET /api/tenants/{id}/usage?range=30d`（用量统计）、`GET /api/tenants/{id}/invoice?month=2026-06`（账单） | BE |
| P3-6.7 | 租户管理前端 | `TenantManager.tsx`：配额配置面板 + 用量仪表盘（日/月趋势图） + 账单查看 | FE |
| P3-6.8 | 验证 | 创建 Tenant A（API 限额 10 次/天）→ 调用 10 次 API 正常 → 第 11 次返回 429 → 配额用量仪表盘显示 10/10 | QA |

**技术细节**:

```java
// QuotaFilter
@Component
public class QuotaFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
            HttpServletResponse response, FilterChain chain) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null) { chain.doFilter(request, response); return; }
        
        QuotaCheckResult result = quotaEnforcer.check(tenantId, "API_CALLS");
        if (!result.allowed()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                String.format("{\"code\":429,\"message\":\"%s\"}", result.message())
            );
            return;
        }
        chain.doFilter(request, response);
    }
}
```

```sql
-- 配额表
CREATE TABLE ecos_tenant_quota (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    quota_type VARCHAR(32) NOT NULL,  -- STORAGE_MB, API_CALLS, WORKBOOK_EXEC, AGENT_TASKS
    daily_limit BIGINT,
    monthly_limit BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, quota_type)
);

-- 用量汇总表
CREATE TABLE ecos_tenant_usage (
    tenant_id VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,
    quota_type VARCHAR(32) NOT NULL,
    used_count BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (tenant_id, usage_date, quota_type)
);
```

**验收 curl**:

```bash
# 1. 查看租户配额
curl http://localhost:8080/api/tenants/tenant-a/quota \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"quotas":[{"type":"API_CALLS","daily_limit":1000,"used_today":342},...]}}

# 2. 修改配额
curl -X PUT http://localhost:8080/api/tenants/tenant-a/quota \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"quota_type":"API_CALLS","daily_limit":500}'
# 预期: {"code":200,"data":{"status":"updated"}}

# 3. 超限测试（每天 10 次限额的测试租户）
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:8080/api/health \
    -H "X-Tenant-Id: test-limited"
done
# 预期: 前10次 200，第11次 429

# 4. 用量统计
curl "http://localhost:8080/api/tenants/tenant-a/usage?range=30d" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"daily":[{"date":"2026-06-25","api_calls":342,"storage_mb":5120,...},...]}}

# 5. 账单
curl "http://localhost:8080/api/tenants/tenant-a/invoice?month=2026-06" \
  -H "Authorization: Bearer $TOKEN"
# 预期: {"code":200,"data":{"month":"2026-06","totalCost":125.50,"items":[{"type":"API_CALLS","usage":10000,"unitPrice":0.01,"cost":100.0},...]}}
```

---

## 四、并行策略

```
Week │ 15-16 │ 17-18 │ 19-20 │ 21-22 │ 23-24 │ 25-26 │
─────┼───────┼───────┼───────┼───────┼───────┼───────┤
P3-1 │ █████ │ █████ │       │       │       │       │ DuckDB + MinIO 数据湖
P3-2 │ █████ │ █████ │ █████ │       │       │       │ 多语言 Workbook 沙盒
P3-3 │       │ █████ │ █████ │ █████ │       │       │ NSGA-II 帕累托寻优
P3-4 │       │       │ █████ │ █████ │ █████ │       │ 数字孪生闭环
P3-5 │       │       │       │ █████ │ █████ │ █████ │ OpenTelemetry 全链路
P3-6 │       │       │       │       │ █████ │ █████ │ 多租户隔离+计费
─────┼───────┼───────┼───────┼───────┼───────┼───────┤
QA   │ 回归  │ 回归  │ 回归  │ 回归  │ 回归  │ 回归  │
```

**并行说明**:
- P3-1/P3-2 可同时启动（无依赖关系），分别由不同 BE 开发
- P3-3 依赖 P3-1 的 DuckDB 基础设施（可选：直接用 PostgreSQL）
- P3-4 依赖 P3-2 的进程隔离机制（状态机部分可独立）
- P3-5 依赖 P3-2 的 Agent Mesh（已 Phase 2 完成）和 P3-6 的 Tenant 体系
- P3-6 可独立开发，仅依赖 Phase 1 IAM 的 Tenant 表

---

## 五、技术栈

| 组件 | Phase 2 | Phase 3 新增 |
|------|---------|-------------|
| 数据库 | PostgreSQL 14 + Neo4j 5 + pgvector | + DuckDB 嵌入式 OLAP |
| 对象存储 | — | + MinIO 本地二进制 |
| 计算引擎 | — | + subprocess Python3/Rscript 进程隔离 |
| 优化算法 | — | + 自研 NSGA-II（纯 Java，零外部依赖） |
| 设备协议 | — | + Eclipse Paho MQTT + Eclipse Milo OPC-UA（Java 库，零部署） |
| 可观测性 | — | + OpenTelemetry SDK + PostgreSQL SpanExporter |
| 多租户 | TenantContext | + 配额/用量/计费 |
| 前端 | React 19 + Vite + Cytoscape.js + STOMP.js | + ECharts（帕累托散点图 + Trace 瀑布图） |

**原则**: 
- DuckDB、MinIO、Moquette、Milo 都是**进程内/本地二进制**，不引入 Docker
- NSGA-II **自研纯 Java 实现**，不引入第三方优化库
- 所有"分布式"能力降级为**嵌入式单机版**，架构保持向真实分布式演进的能力

---

## 六、新增/修改文件清单

| 子项目 | 模块 | 新增文件 |
|--------|------|---------|
| P3-1 | databridge-gateway | `DuckDBQueryService.java`、`DataLakeExportService.java`、`DataLakeController.java` |
| P3-1 | scripts/ | `minio-setup.sh` |
| P3-1 | c2eos | `DataLake.tsx` |
| P3-2 | databridge-workspace | `WorkbookExecutionEngine.java`、`SqlRuntime.java`、`PythonRuntime.java`、`RRuntime.java`、`WorkbookSessionManager.java`、`WorkbookController.java` |
| P3-2 | c2eos | `CodeWorkbook.tsx`（改造对接后端 API） |
| P3-3 | databridge-worldmodel | `NSGA2Engine.java`、`OptimizationProblem.java`、`ObjectiveRegistry.java`、`ParetoController.java` |
| P3-3 | c2eos | `WorldModelViewer.tsx`（新增帕累托 Tab） |
| P3-4 | databridge-gateway | `MqttBrokerConfig.java`、`DeviceSimulatorService.java`、`DigitalTwinService.java`、`TwinController.java` |
| P3-4 | databridge-workspace | `OpcUaServerConfig.java`（Milo Server） |
| P3-4 | c2eos | `DigitalTwinDesigner.tsx`，`MonitoringCenter.tsx`（新增数字孪生 Tab） |
| P3-5 | databridge-gateway | `TelemetryConfig.java`、`PostgresSpanExporter.java`、`TelemetryInterceptor.java`、`TokenAuditService.java`、`TelemetryController.java` |
| P3-5 | c2eos | `TelemetryViewer.tsx`、`TokenDashboard.tsx` |
| P3-6 | databridge-sysman | `TenantQuotaService.java`、`QuotaEnforcer.java`、`UsageCollector.java`、`BillingCalculator.java`、`QuotaFilter.java`、`TenantBillingController.java` |
| P3-6 | c2eos | `TenantManager.tsx` |
| — | database/ | `V3__phase3_datalake.sql`（ecos_spans/ecos_token_usage/ecos_tenant_quota/ecos_tenant_usage/ecos_billing_rate/ecos_billing_invoice 建表） |

---

## 七、验收清单

### P3-1 嵌入式数据湖
- [ ] MinIO 本地二进制 `:9000` 运行正常，`ecos-datalake` bucket 已创建
- [ ] DuckDB 嵌入式连接池正常，`GET /api/datalake/tables` 返回已导出的数据集
- [ ] PG→Parquet 导出管道至少导出 3 张表，每表 ≥10 万行
- [ ] DuckDB 聚合查询耗时 ≤ PostgreSQL 同查询的 30%
- [ ] 前端 `DataLake.tsx` 支持 SQL 查询控制台 + 结果表格

### P3-2 多语言 Workbook 沙盒
- [ ] SQL 执行正常：SELECT/DML 结果正确返回
- [ ] Python 执行正常：pandas/numpy 可用，stdout 结果收集正确
- [ ] 超时保护：Python 执行 >30s 自动终止，返回 408
- [ ] 进程隔离：Python 子进程退出后工作目录无残留
- [ ] R 执行探测：系统有 R 时正常执行，无 R 时返回友好提示
- [ ] 前端 `CodeWorkbook.tsx` 对接后端，支持单元格执行 + 结果展示 + 历史查询

### P3-3 帕累托寻优引擎
- [ ] NSGA-II 200 代进化输出 ≥5 个帕累托前沿解
- [ ] 双目标优化（最大化+最小化）前沿面正确（目标值无明显支配关系）
- [ ] 从 WorldModel 场景自动生成优化问题
- [ ] 前端 ECharts 2D 散点图渲染帕累托前沿 + 解详情表

### P3-4 企业数字孪生
- [ ] MQTT Broker `:1883` 运行，模拟设备正常推送
- [ ] 5 个模拟设备注册并推送 ≥100 条遥测数据
- [ ] OPC-UA Server `:4840` 暴露设备节点树
- [ ] 数字孪生设备影子：reported/desired state 同步正确
- [ ] 前端遥测折线图实时刷新（WebSocket 推送，<2 秒延迟）
- [ ] 指令下发 → 设备状态变更 → 前端状态灯更新完整闭环

### P3-5 OpenTelemetry 全链路
- [ ] HTTP 请求自动创建 SPAN（method + path + status + duration）
- [ ] Agent Mesh ReAct 循环各环节创建子 SPAN
- [ ] 完整 Trace 树存储在 `ecos_spans` 表，可查询
- [ ] Token 审计：每次 LLM 调用记录 prompt/completion tokens + 模型 + 费用
- [ ] 前端 Trace 瀑布图正确渲染 SPAN 时间线

### P3-6 多租户隔离 + 计费
- [ ] Tenant 配额 CRUD 正常
- [ ] 配额超限返回 429，响应体包含剩余配额信息
- [ ] 用量统计仪表板每日更新
- [ ] 月度账单生成（用量 × 单价）正确

### 全端点回归
- [ ] Phase 1+2 ≥50 端点全部 200
- [ ] Phase 3 新增端点全部通过

---

## 八、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|:--:|:--:|---------|
| Python3/Rscript 未安装 | 中 | P3-2 Python/R Runtime 不可用 | 启动时探测，无运行时返回友好提示，SQL Runtime 不受影响 |
| MinIO 二进制与 WSL 兼容性 | 低 | P3-1 对象存储不可用 | 降级为本地文件系统存储 Parquet 文件，DuckDB 直接读取 |
| DuckDB JDBC 与 PG JDBC 冲突 | 低 | 分析查询不可用 | DuckDB 嵌入式模式独立连接池，与 PG 数据源完全隔离 |
| Moquette/Milo 依赖冲突 | 中 | P3-4 MQTT/OPC-UA 不可用 | 两个库都是独立 Java 实现，无外部服务依赖。冲突时可降级为纯文件模拟 |
| NSGA-II 算法收敛慢 | 低 | 优化结果质量差 | 参数可调（popSize/generations），200 代双目标通常 5s 内收敛 |

---

## 九、任务统计

| 子项目 | 任务数 | 核心交付 | 增强项 |
|--------|:-----:|:--------:|:-----:|
| P3-1 嵌入式数据湖 | 7 | P3-1.1~1.6 | P3-1.7 |
| P3-2 多语言 Workbook | 9 | P3-2.1~2.8 | P3-2.9 |
| P3-3 帕累托寻优 | 7 | P3-3.1~3.6 | P3-3.7 |
| P3-4 数字孪生 | 8 | P3-4.1~4.7 | P3-4.8 |
| P3-5 OpenTelemetry | 7 | P3-5.1~5.6 | P3-5.7 |
| P3-6 多租户+计费 | 8 | P3-6.1~6.7 | P3-6.8 |
| **合计** | **46** | **40** | **6** |

> **注**: 任务数 46 略超目标（25-35），但 P3-1 ~ P3-6 各子项目相对独立。如需精简，可合并各子项目的 QA 验证任务（P3-x 最后一个）到统一的「全端点回归」周。

---

> **Phase 3 最后一句话：不做没有后端支撑的前端页面，不引入超出团队运维能力的基础设施。所有"平台化"能力通过嵌入式 Java 库和本地进程实现，保留向真实分布式架构演进的完整路径。每个子项目都是"后端 API 先通 → 前端再上 → QA 验证"的顺序。**

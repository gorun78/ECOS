# PMO-29: Function 沙箱执行引擎

> **架构铁律**: 必须遵循 `/home/guorongxiao/ECOS/docs/ARCHITECTURE-RULES.md`
> **差距分析**: `/home/guorongxiao/ECOS/docs/4-onto/01-差距分析.md` §7.2
> 来源: 肖国荣 | 日期: 2026-08-08 | 优先级: 🔴 P0
> **引擎**: ontology-engine:18083 | **工期**: 2天 | **协同**: ECOS-BE
> **工具**: 执行前用 `codebase-memory-mcp search_graph` 查看 PreconditionEngine/PostActionExecutor 作为参考模式

---

## §背景

DIKW体系中，I（信息）层的核心价值是"为数据赋予业务含义"。Function类型是实现这一价值的关键——将原始DB字段通过计算表达式转化为业务指标（如"毛利率=(营收-成本)/营收"）。

当前：前端可创建 Function 类型属性（含 `functionType`/`functionExpression` 字段），但后端无执行引擎。输入表达式后无编译、无沙箱、无执行、无缓存、无审计——Function 属性只是存储了一个字符串。

---

## §设计约束

**Function引擎不是通用脚本引擎**。是受限的SQL聚合表达式计算器：

| 允许 | 禁止 |
|------|------|
| `SUM()`, `AVG()`, `COUNT()`, `MIN()`, `MAX()` | `Runtime.exec()`, 反射, 网络IO, 文件IO |
| `+`, `-`, `*`, `/`, `%` | 变量声明 (`var`, `let`, `const`) |
| `CASE WHEN ... THEN ... ELSE ... END` | 循环 (`for`, `while`) |
| `COALESCE()`, `NULLIF()` | 子查询（嵌套SELECT） |
| `ABS()`, `ROUND()`, `CEIL()`, `FLOOR()` | 跨表JOIN（单表计算） |
| `WHERE` 条件过滤 | 用户自定义函数 |

---

## §前置：codebase-memory-mcp 探索

```bash
# 查看现有引擎模式
codebase-memory-mcp search_graph --label Class --pattern ".*Engine.*" --project ecos
codebase-memory-mcp trace_path --function "PreconditionEngine.evaluate" --direction both
codebase-memory-mcp trace_path --function "PostActionExecutor.execute" --direction both

# 查看表达式相关代码
codebase-memory-mcp search_graph --label Class --pattern ".*Expression.*|.*Function.*"
codebase-memory-mcp search_code --pattern "functionType|functionExpression" --project ecos
```

---

## §禁止清单

1. ❌ 不引入第三方表达式引擎（Aviator/MVEL/SpEL）——手写解析器，简单可控
2. ❌ 不新增Maven依赖（铁律0.4）
3. ❌ Function不夸表JOIN——单表计算，跨表走cognitive-engine
4. ❌ 不新增Maven模块（铁律0.4）
5. ❌ 无审计日志的Function执行拒收——每次调用必须记录到 `ecos_function_audit_log`
6. ❌ 不暴露底层异常堆栈——白名单拦截返回友好错误"不支持的操作: xxx"

---

## §Task

### T1: FunctionValidator — 表达式白名单验证（0.5天）

**新建文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/engine/FunctionValidator.java`

**职责**:
1. 正则匹配禁止模式（`Runtime`, `exec`, `Class.forName`, `System.`, `java.`, `javax.`, `new `, `import `）
2. 白名单函数库验证——不在白名单内的函数名→拒绝
3. DAG无环检查——Function A引用Property X(是Function)→Property X引用Property Y(是DB字段)，不能成环
4. 语法基本结构校验（括号匹配、引号成对）

**白名单函数**:
```
SUM, AVG, COUNT, MIN, MAX, ABS, ROUND, CEIL, FLOOR, COALESCE, NULLIF,
CONCAT, UPPER, LOWER, TRIM, SUBSTRING, LENGTH, REPLACE,
CASE, WHEN, THEN, ELSE, END, WHERE, AND, OR, NOT, IN, LIKE, BETWEEN,
CAST, AS, DISTINCT, GROUP, BY, ORDER, ASC, DESC, HAVING
```

**验收**(T1):
```bash
# 在测试中验证
cd /home/guorongxiao/ECOS/ecos_backend
# FunctionValidator 单元测试应通过：
# - 合法表达式: "SUM(amount) / COUNT(*)" → true
# - 非法表达式: "Runtime.getRuntime().exec('ls')" → false + "forbidden: Runtime"
# - 循环依赖: A→B→A → false + "circular dependency"
```

---

### T2: FunctionSandboxEngine — 编译+执行（1天）

**新建文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/engine/FunctionSandboxEngine.java`

**职责**:
1. **编译**：将 Function 表达式编译为 JdbcTemplate 可执行的参数化 SQL
   - 输入: `SUM(amount) / COUNT(*) FROM fin_revenue WHERE period='2026-07'`
   - 输出: `SELECT (SUM(amount) / COUNT(*)) AS result FROM fin_revenue WHERE period=?`
2. **执行**：JdbcTemplate.queryForObject → 单值返回
3. **超时**：`Statement.setQueryTimeout(5)` → 5s超时
4. **类型推断**：根据SQL聚合函数推断结果类型（SUM→Double, COUNT→Long, AVG→Double）

**核心方法签名**:
```java
public class FunctionSandboxEngine {
    /**
     * 编译并执行Function表达式
     * @param expression 原始表达式（含FROM entity_name）
     * @param entityTableMapping 实体→DB表映射
     * @return 执行结果（单值）
     */
    public FunctionResult execute(String expression, Map<String, String> entityTableMapping);

    /**
     * 仅编译（不执行），返回生成的SQL供前端预览
     */
    public String compile(String expression);

    /**
     * 测试执行（带完整审计日志）
     */
    public FunctionResult test(String expression, String entityName, String callerId);
}
```

**FunctionResult**:
```java
public class FunctionResult {
    private Object value;          // 计算结果
    private String sqlType;        // NUMERIC/STRING/BOOLEAN
    private long executionTimeMs;
    private String compiledSql;    // 生成的SQL（供调试）
}
```

**验收**(T2):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

# 测试Function
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/ontology/functions/test \
  -H 'Content-Type: application/json' \
  -d '{"expression":"COUNT(*) FROM fin_revenue","entityName":"fin_revenue"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'PASS: {d[\"data\"][\"value\"]}条记录, {d[\"data\"][\"executionTimeMs\"]}ms' if d.get('success') else 'FAIL')"
# 期望: PASS: N条记录, <100ms

# 验证安全拦截
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/ontology/functions/test \
  -H 'Content-Type: application/json' \
  -d '{"expression":"Runtime.getRuntime().exec(\"ls\")","entityName":"fin_revenue"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: 安全拦截' if not d.get('success') else 'FAIL: 危险表达式未拦截')"
# 期望: PASS: 安全拦截
```

---

### T3: FunctionCacheManager + 审计日志（0.5天）

**新建文件**:
- `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/engine/FunctionCacheManager.java`
- `gateway/src/main/resources/db/migration/V4.2__function_audit.sql`

**FunctionCacheManager**:
- Caffeine Cache: `functionResults` (key=expression+entityName, TTL=300s)
- 本体变更时（提案执行后）主动失效相关缓存
- 最大缓存条目: 1000

**审计表**:
```sql
CREATE TABLE IF NOT EXISTS ecos_function_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    function_name   VARCHAR(256),
    expression      TEXT NOT NULL,
    entity_name     VARCHAR(128),
    result_value    TEXT,
    execution_time_ms INTEGER,
    caller_id       VARCHAR(64),
    status          VARCHAR(16) NOT NULL,  -- SUCCESS/ERROR/TIMEOUT/FORBIDDEN
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**验收**(T3):
```bash
# 执行Function后查审计日志
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/ontology/functions/audit?page=1&pageSize=5" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); items=d.get('data',{}).get('items',[]); print(f'PASS: {len(items)}条审计记录' if len(items)>0 else 'FAIL: 无审计')"
# 期望: PASS: ≥1条审计记录

# 执行相同表达式两次→第二次应更快（缓存命中）
# 手动验证执行时间差异
```

---

### T4: FunctionController 端点（0.5天）

**新建文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/FunctionController.java`

**端点**:

| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/ontology/functions/test` | POST | 测试Function（沙箱执行+返回结果+生成的SQL） |
| `/api/v1/ontology/functions/compile` | POST | 仅编译（返回生成的SQL，不执行） |
| `/api/v1/ontology/functions/{propertyId}/execute` | GET | 执行已存储的Function属性 |
| `/api/v1/ontology/functions/audit` | GET | 审计日志分页查询 |
| `/api/v1/ontology/functions/whitelist` | GET | 返回白名单函数列表 |

**三滤波器注册**:

| 层 | 文件 | 操作 |
|:--|------|------|
| 1 | `gateway/.../VersionPrefixRewriteFilter.java` | 确认 `/api/v1/ontology/functions/` → `/api/ontology/functions/` |
| 2 | `sysman/.../security/SecurityConfig.java` | 加 `/api/v1/ontology/functions/**` 到 permitAll |
| 3 | `sysman/.../security/ClearanceInterceptor.java` | 加 `/api/v1/ontology/functions` 到豁免列表 |

**curl验收**(T4):
```bash
# 编译预览（不执行）
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/ontology/functions/compile \
  -H 'Content-Type: application/json' \
  -d '{"expression":"SUM(amount)/COUNT(*) FROM fin_revenue WHERE period='\''2026-07'\''","entityName":"fin_revenue"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); sql=d.get('data',{}).get('sql',''); print(f'PASS: {sql[:60]}...' if sql else 'FAIL')"
# 期望: PASS: SELECT (SUM(amount)/COUNT(*)) AS result FROM fin_revenue...

# 白名单查询
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/ontology/functions/whitelist" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); fns=d.get('data',[]); print(f'PASS: {len(fns)}个白名单函数' if len(fns)>0 else 'FAIL')"
# 期望: PASS: ≥30个白名单函数
```

---

## §验证门禁

```bash
# V1: 编译
cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -q 2>&1 | tail -3

# V2: 安全测试 (危险表达式拦截)
# V3: 单元测试 (FunctionValidator 合法/非法/循环依赖)

# V4: 端到端
# test → 返回结果+SQL → 审计日志有记录 → 缓存第二次更快 → 白名单可查
```

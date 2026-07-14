# ECOS 本体模型 Palantir 对齐 — PMO指令

> **来源**: 肖国荣 Palantir对比分析  
> **日期**: 2026-06-27  
> **前提**: 本体模型基础扎实（8个Controller全部真实），差距在ObjectQL跨对象查询 + Functions计算属性  
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。

---

## 背景

ECOS本体模型已有：Entity/Property/Relationship/Action/Rule/Version/Domain 全链路CRUD + ObjectQL单表查询。但与Palantir对标缺两个致命能力，导致"不改代码只换配置"这条路走不通：

| 差距 | 问题 | 场景 |
|------|------|------|
| ObjectQL不能跨对象查 | 单表查询，不支持JOIN/Link遍历 | 老板问"所有滞后项目的供应商是谁？"——做不到 |
| 无Functions计算属性 | 指标只能硬编码，无法在Ontology层定义 | "营收完成率=实际/目标"——数据工程师每次要写SQL |

P1补两个企业级能力：Ontology级权限打通 + Action钩子机制。

---

## Phase 1: P0 核心差距（2周）

### P0-1: ObjectQL 跨对象Link遍历查询（5天）

> **目标**: ObjectQL支持沿Ontology Relationship定义的Link做JOIN查询  
> **参考**: 
> - 现有`ObjectQLParser.java` — 当前只生成 `SELECT ... FROM {entity} WHERE ...`
> - 现有`ObjectQLController.java` (86行) — 解析+执行+历史
> - 现有`OntologyRelationship` — source_entity/target_entity/relationship_type
> - 现有`ObjectRelationshipController.java` (133行) — 关系查询端点参考

#### 任务拆解

| ID | 任务 | 产出 | 工作量 |
|:--|------|------|:--:|
| P0-1.1 | ObjectQL `links` 语法扩展 | 修改`ObjectQLParser.java`，支持解析links数组 | 2d |
| P0-1.2 | JOIN SQL生成 | Parser支持生成`LEFT JOIN ... ON ...` | 1d |
| P0-1.3 | ObjectQLController集成 | 修改`ObjectQLController.java`对接新Parser | 0.5d |
| P0-1.4 | Link安全校验 | 验证link目标实体在Ontology中存在，不在白名单则拒 | 0.5d |
| P0-1.5 | curl验收+边界测试 | 单link/多link/跨2层link/无效link拒绝 | 1d |

#### 新的ObjectQL语法

```json
{
  "entity": "Contract",
  "links": [
    {
      "entity": "Supplier",
      "alias": "supplier",
      "on": {"from": "supplier_id", "to": "Supplier.id"}
    }
  ],
  "fields": ["Contract.id", "Contract.name", "supplier.name"],
  "filter": {
    "field": "supplier.credit_score",
    "op": ">=",
    "value": 80
  },
  "sort": "Contract.value DESC",
  "limit": 20
}
```

#### 验收标准

```bash
# 1. 单Link查询：查所有合同及其供应商名称
curl -X POST http://localhost:8081/sys-man/api/query/objectql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ***  -d '{
    "query": "{\"entity\":\"Contract\",\"links\":[{\"entity\":\"Supplier\",\"alias\":\"s\",\"on\":{\"from\":\"supplier_id\",\"to\":\"s.id\"}}],\"fields\":[\"Contract.id\",\"Contract.name\",\"s.name\"]}"
  }'
# 期望: 200 + 返回合同+供应商名称的联合结果

# 2. 跨Link过滤：查信用分>80的供应商的所有合同
curl -X POST http://localhost:8081/sys-man/api/query/objectql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ***  -d '{
    "query": "{\"entity\":\"Contract\",\"links\":[{\"entity\":\"Supplier\",\"alias\":\"s\",\"on\":{\"from\":\"supplier_id\",\"to\":\"s.id\"}}],\"filter\":{\"field\":\"s.credit_score\",\"op\":\">=\",\"value\":80}}"
  }'
# 期望: 200 + 只返回高信用供应商的合同

# 3. 无效Link拒绝：查一个Ontology里不存在的实体
curl -X POST http://localhost:8081/sys-man/api/query/objectql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ***  -d '{
    "query": "{\"entity\":\"Contract\",\"links\":[{\"entity\":\"GhostEntity\",\"alias\":\"g\",\"on\":{\"from\":\"id\",\"to\":\"g.id\"}}]}"
  }'
# 期望: 400 + "Link target entity 'GhostEntity' not found in ontology"

# 4. 无Link兼容：老语法（不带links）仍然正常工作
curl -X POST http://localhost:8081/sys-man/api/query/objectql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ***  -d '{"query": "{\"entity\":\"Supplier\",\"filter\":{\"field\":\"credit_score\",\"op\":\">=\",\"value\":80}}"}"'
# 期望: 200 + 行为与改动前一致
```

#### 技术约束

1. **修改文件**: 只改 `ObjectQLParser.java` + `ObjectQLController.java`（workspace模块），不改其他Controller
2. **JOIN生成规则**: 
   - `links[].on.from` = 源实体外键字段名
   - `links[].on.to` = 目标实体主键（格式 `{alias}.{field}`）
   - 生成 `LEFT JOIN {link.entity} AS {alias} ON {from_entity}.{from} = {alias}.{field}`
3. **安全**: link的target entity必须在`ecos_ontology_entity`中存在，否则400拒绝
4. **不引入**: GraphQL、ORM、JPA Criteria——纯字符串拼接+JdbcTemplate参数化
5. **兼容性**: 不带`links`字段的老语法100%兼容

---

### P0-2: Functions 计算属性（5天）

> **目标**: OntologyProperty支持FUNCTION类型，数据工程师可在Ontology层面定义计算指标  
> **策略**: MVP支持三种Function类型——`EXPRESSION`（表达式）/ `AGGREGATION`（跨link聚合）/ `LOOKUP`（link取值）。存储表达式，查询时ObjectRuntime/ObjectQL动态计算  
> **参考**:
> - 现有`OntologyProperty.java` (84行) — property_type当前只有STRING等基本类型
> - 现有`ObjectController.java` (547行) — 对象查询/详情端点
> - 现有`OntologyPropertyController.java` (80行) — 属性CRUD端点

#### 任务拆解

| ID | 任务 | 产出 | 工作量 |
|:--|------|------|:--:|
| P0-2.1 | DB迁移：property表加字段 | `ecos_ontology_property` 加 `function_type`/`function_expression` | 0.5d |
| P0-2.2 | FunctionEvaluator引擎 | `FunctionEvaluator.java` — 解析表达式/聚合/LOOKUP并计算 | 2d |
| P0-2.3 | ObjectController集成 | 修改`ObjectController.java` — 返回详情时对FUNCTION属性调用计算 | 1d |
| P0-2.4 | ObjectQL集成 | 修改`ObjectQLParser.java` — SELECT中FUNCTION属性→子查询/计算 | 1d |
| P0-2.5 | curl验收+边界测试 | 三种类型各测一个，零除/空值边界 | 0.5d |

#### Function类型定义

```json
// 类型1: EXPRESSION — 同实体内算术/字符串/日期运算
{
  "code": "full_name",
  "name": "全称",
  "property_type": "FUNCTION",
  "function_type": "EXPRESSION",
  "function_expression": "CONCAT(last_name, first_name)"
}

// 类型2: AGGREGATION — 跨link聚合
{
  "code": "total_contract_value",
  "name": "合同总额",
  "property_type": "FUNCTION",
  "function_type": "AGGREGATION",
  "function_expression": "SUM(Contract.value) WHERE Contract.supplier_id = THIS.id"
}

// 类型3: LOOKUP — 简单link取值（减少JOIN）
{
  "code": "supplier_name",
  "name": "供应商名称",
  "property_type": "FUNCTION",
  "function_type": "LOOKUP",
  "function_expression": "LOOKUP(Supplier.name BY id = THIS.supplier_id)"
}
```

#### 验收标准

```bash
# 1. 创建FUNCTION属性
curl -X POST http://localhost:8081/sys-man/api/v1/ecos/entities/{entityId}/properties \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ***  -d '{
    "code": "completion_rate",
    "name": "完成率",
    "property_type": "FUNCTION",
    "function_type": "EXPRESSION",
    "function_expression": "actual_value / target_value * 100"
  }'
# 期望: 201 + 返回创建的property，含function_type/function_expression

# 2. Object详情自动计算FUNCTION
curl http://localhost:8081/sys-man/api/v1/ecos/objects/Project/p001 \
  -H "Authorization: Bearer *** 期望: 200 + 返回的properties中包含 "completion_rate": 62.5（计算值，非存储值）

# 3. 跨link AGGREGATION —— 供应商对象自动带"合同总额"
curl http://localhost:8081/sys-man/api/v1/ecos/objects/Supplier/s001 \
  -H "Authorization: Bearer *** 期望: 200 + 返回 "total_contract_value": 15200000

# 4. 零除边界
# 创建一个target_value=0的项目，查询其completion_rate
# 期望: 200 + "completion_rate": null (而非500/除零异常)
```

#### 技术约束

1. **FUNCTION属性不存物理列**: 查询时动态计算，不写入DDL
2. **EXPRESSION表达式安全**: 白名单函数（CONCAT/UPPER/LOWER/ROUND/ABS/COALESCE/DATEDIFF），禁止SQL注入
3. **AGGREGATION解析**: `SUM({target_entity}.{field}) WHERE {condition}` → 子查询
4. **LOOKUP解析**: `LOOKUP({target_entity}.{field} BY {pk} = THIS.{fk})` → 单行子查询
5. **错误处理**: 表达式计算异常时返回null + log error，不抛500
6. **不引入**: 规则引擎（Drools）、脚本引擎（Groovy/JS）、ML推理——MVP纯SQL计算

---

## Phase 2: P1 企业级能力（Phase 1完成后启动，2周）

### P1-1: Ontology级权限打通（3天）

> **目标**: ObjectQL/ObjectRuntime查询时自动应用ABAC权限过滤  
> **已有**: `DataPermissionInterceptor` / `RowLevelSecurityService` / `ColumnLevelSecurityService` / `AbacController`

| ID | 任务 | 产出 | 工作量 |
|:--|------|------|:--:|
| P1-1.1 | OntologyPermissionConfig表 | 新表`ecos_ontology_permission`：entity_id/role_id/column_mask/row_filter | 0.5d |
| P1-1.2 | OntologyPermissionService | 查权限配置→生成SQL过滤条件（列裁剪+行过滤） | 1d |
| P1-1.3 | ObjectQL集成 | `ObjectQLParser` 生成SQL前注入权限WHERE子句 | 1d |
| P1-1.4 | curl验收 | 2角色×2实体验证列裁剪+行过滤 | 0.5d |

验收：
```bash
# 角色A（只读项目经理）查Contract → 只能看到自己部门、看不到bank_account列
# 角色B（CEO）查Contract → 看到全部列、全部行
```

### P1-2: Action钩子机制（3天）

> **目标**: OntologyAction支持preHooks/postHooks  
> **已有**: `OntologyActionController` (89行) / `ObjectActionController` (296行)

| ID | 任务 | 产出 | 工作量 |
|:--|------|------|:--:|
| P1-2.1 | ActionHook定义 | 新表`ecos_ontology_action_hook` + Java模型 | 0.5d |
| P1-2.2 | Hook执行引擎 | `ActionHookExecutor` — 顺序执行preHooks→action→postHooks | 1d |
| P1-2.3 | 内置Hook类型 | VALIDATION + NOTIFICATION + AUDIT_LOG 三种 | 1d |
| P1-2.4 | curl验收 | 创建带钩子的Action→执行→验证钩子触发 | 0.5d |

验收：
```bash
# 创建Action "approveSupplier" 带preHook(VALIDATION:status=PENDING) + postHook(AUDIT_LOG)
# 执行→状态变为APPROVED→审计日志中出现一条记录
```

---

## 资源与排期

```
Week 1-2 (Phase 1 — P0):
  P0-1 (BE) ∥ P0-2 (BE)    — ObjectQL跨对象 + Functions引擎
    
Week 3-4 (Phase 2 — P1):
  P1-1 (BE) ∥ P1-2 (BE)    — 权限打通 + Action钩子
```

| Phase | 内容 | 总工时 | 并行后工期 |
|:--|------|:--:|:--:|
| P0 | ObjectQL跨对象 + Functions | 10d | 2周 |
| P1 | 权限打通 + Action钩子 | 6d | 2周 |
| **合计** | | **16d** | **4周** |

---

## 禁止清单

1. **禁止**修改已有API路径或参数签名——只增不改
2. **禁止**引入GraphQL/ORM/JPA Criteria——纯JdbcTemplate+SQL拼接
3. **禁止**引入Drools/Groovy/JS引擎——MVP阶段Functions只用SQL表达式
4. **禁止**新建Maven模块——全部在现有workspace/dccheng模块内扩展
5. **禁止**改动已有的OntologyProperty表结构（只加列，不改列）

## 验收方式

Phase 1完成后肖总亲自检查：
1. `curl` 本指令中的全部验收命令
2. 浏览器打开ECOS → 在DataCatalog搜索栏输入ObjectQL跨对象查询 → 得到正确结果
3. 在OntologyDesigner中为Project实体添加completion_rate Function属性 → 在ObjectExplorer中查看Project详情时看到自动计算值

# PMO指令: CEO周一晨会场景闭环

> **来源**: ECOS产品差距分析（修订版）| **日期**: 2026-06-28
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。不接受"功能正常"类描述。
> **产品定位**: 企业经营管控平台。CEO登录后5分钟内了解企业健康度。不是数据工程平台。

## 背景

ECOS有36个真实Controller，DIKW四层全通。但CEO打开首页看到的BizDashboard返回空数组——4张业务表（`ecos_biz_department`/`ecos_biz_project`/`ecos_biz_contract`/`ecos_biz_metrics`/`ecos_biz_target`）全是空库。

**不是缺模块，是缺第一条数据。** 接上一条管道，整个DIKW金字塔就开始转。

## 禁止清单

1. **禁止新建任何基础设施模块**（Neo4j/Lakehouse/LowCode/Doris新功能/数字孪生新功能）
2. **禁止产出分析文档或审计报告**——唯一产出是git commit
3. **禁止新增Maven模块**——所有任务在现有模块内完成
4. **禁止对标Palantir Foundry设计文档**——ECOS卖给老板，不是数据工程师
5. **禁止任务间相互依赖阻塞**——P0可全并行执行

---

## P0: 数据管道 — 让BizDashboard有数（3天）

### T0.1: 项目型企业业务表Flyway迁移（0.5天）

**目标**: 确保5张业务表存在于PG，表结构与FrontendBridgeController查询对齐

**改文件**: `databridge-sysman/sysman-impl/src/main/resources/db/migration/V26__ecos_biz_seed_tables.sql`

**内容**:
```sql
-- 部门表
CREATE TABLE IF NOT EXISTS ecos_biz_department (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    manager VARCHAR(50),
    parent_id VARCHAR(32)
);

-- 项目表（FrontendBridgeController.buildProjectStats依赖）
CREATE TABLE IF NOT EXISTS ecos_biz_project (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    dept_id VARCHAR(32),
    budget DECIMAL(18,2),
    revenue DECIMAL(18,2),
    cost DECIMAL(18,2),
    progress DECIMAL(5,2),
    status VARCHAR(20),
    start_date DATE,
    end_date DATE
);

-- 合同表
CREATE TABLE IF NOT EXISTS ecos_biz_contract (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    project_id VARCHAR(32),
    supplier_id VARCHAR(32),
    amount DECIMAL(18,2),
    type VARCHAR(20),
    sign_date DATE
);

-- 经营指标表
CREATE TABLE IF NOT EXISTS ecos_biz_metrics (
    id VARCHAR(32) PRIMARY KEY,
    dept_id VARCHAR(32),
    metric_type VARCHAR(50),
    metric_value DECIMAL(18,2),
    metric_month VARCHAR(7)
);

-- 年度目标表
CREATE TABLE IF NOT EXISTS ecos_biz_target (
    id VARCHAR(32) PRIMARY KEY,
    dept_id VARCHAR(32),
    target_type VARCHAR(50),
    target_value DECIMAL(18,2),
    target_year INT
);
```

**验收**:
```bash
# 验证表创建成功
PGPASSWORD=root psql -h localhost -U root -d sys_man -c "\dt ecos_biz_*"
# 期望: 列出5张表
```

---

### T0.2: 模拟经营数据种子脚本（1天）

**目标**: 灌入一套项目型企业的完整模拟数据，覆盖CEO周一晨会场景

**改文件**: `databridge-sysman/sysman-impl/src/main/resources/db/migration/V27__ecos_biz_seed_data.sql`

**数据内容**:
- 3个部门: 工程部/采购部/财务部
- 3个项目: 浙北路桥（进度滞后12%）/ 湘江新城（正常） / 赣深高铁（超前8%）
- 5份合同: 3份收入合同+2份采购合同
- 3家供应商: 华强钢构（准时率从92%降到67%）/ 建通建材 / 中联重科
- 12个月经营指标 (2026.01~2026.12)
- 年度目标: 营收10亿/利润8000万/回款率85%
- 指标数据体现: 前6个月实际值、后6个月预测值、年度完成率62%

**数据一致性要求**:
- 3个项目营收之和 = 经营指标中项目型营收
- 浙北路桥项目供应商 = 华强钢构
- 华强钢构近3个月（4/5/6月）交货准时率: 67%/71%/63%

**验收**:
```bash
# 登录获取token
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 验证BizDashboard返回真实数据（不是空数组）
curl -s http://localhost:8081/sys-man/api/v1/ecos/biz/dashboard \
  -H "Authorization: Bearer $TOKEN" | jq '.data'
# 期望: departments数组≥3条, projectStats.totalCount=3, targets数组≥3条
# 期望: metrics数组≥12条, 最新一条metric_month="2026-06"
```

---

### T0.3: 前端BizDashboard对接验证（0.5天，FE）

**目标**: 确保前端BizDashboard页面能正确渲染后端返回的数据

**改文件**: 
- `c2eos/src/pages/BizDashboard.tsx` （检查是否存在，不存在则新建）
- `c2eos/src/api/biz.ts` （API调用层）

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep -c error
# 期望: 0（零新增TS错误）
```

```bash
# 验证页面可访问（需Gateway+前端运行）
curl -s http://localhost:5173/ecos/biz-dashboard | head -20
# 期望: 返回HTML（SPA路由），非404
```

---

## P1: 因果链模型 — WorldModel有第一条因果逻辑（2天）

### T1.1: 项目型企业因果链种子数据（1天）

**目标**: 在WorldModel建项目型企业的因果链模板 `材料供应准时率 → 项目进度偏差 → 营收完成率 → 利润目标`

**改文件**: `databridge-sysman/sysman-impl/src/main/resources/db/migration/V28__ecos_worldmodel_causal_seed.sql`

**内容**:
- 4个目标节点: 
  - G1: 年度营收10亿（完成率62%）
  - G2: 项目进度达成率≥95%
  - G3: 供应商交货准时率≥90%
  - G4: 年度利润8000万（完成率58%）
- 3条因果链:
  - G3→G2: 供应商交货准时率影响项目进度（权重0.7）
  - G2→G1: 项目进度影响营收完成率（权重0.8）
  - G1→G4: 营收影响利润（权重0.9）
- 2个场景:
  - Scenario A: 更换供应商，成本+5%，进度恢复正常
  - Scenario B: 谈判催货，成本不变，进度部分恢复

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 验证目标列表
curl -s http://localhost:8081/sys-man/api/v1/worldmodel/goals \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# 期望: ≥4

# 验证因果链
curl -s http://localhost:8081/sys-man/api/v1/worldmodel/causal-links \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# 期望: ≥3

# 验证因果图（节点+边）
curl -s http://localhost:8081/sys-man/api/v1/worldmodel/causal-graph \
  -H "Authorization: Bearer $TOKEN" | jq '.data | keys'
# 期望: 包含nodes和edges
```

---

### T1.2: 因果链与实时数据联动验证（1天）

**目标**: 让DQ规则引擎能实时计算因果节点上的偏差值

**改文件**: `databridge-gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/DqDashboardController.java`（追加1个端点）

**新增端点**: `GET /api/dq/causal-deviation` — 沿因果链计算偏差传导值

**逻辑**:
```
输入: causal-link-id = "G3→G2"
输出: {
  sourceNode: {name: "供应商交货准时率", target: 90, actual: 67, deviation: -25.6%},
  targetNode: {name: "项目进度偏差", target: ≤5%, actual: 12%, deviation: +7%},
  propagatedImpact: "供应商交货准时率低于目标25.6%，导致项目进度滞后7个百分点"
}
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

curl -s "http://localhost:8081/sys-man/api/dq/causal-deviation?linkId=G3-G2" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.propagatedImpact'
# 期望: 包含"供应商"和"进度"关键字，非空字符串
```

---

## P2: 经营诊断Agent — Agent开始干活（5天）

### T2.1: Agent诊断工具链注册（2天）

**目标**: 注册3个经营诊断专用工具到Agent平台

**改文件**: 
- `databridge-aimod/aimod-impl/src/main/resources/db/migration/V29__ecos_diagnostic_tools.sql` — 工具注册
- `databridge-aimod/aimod-impl/src/main/java/com/chinacreator/gzcm/aimod/service/DiagnosticToolService.java` — 工具实现

**3个工具**:
1. `query_worldmodel_deviation` — 读取目标vs实际偏差
2. `trace_causal_chain` — 沿因果链追溯根因
3. `generate_scenarios` — 调用Scenario生成应对方案

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 验证工具注册成功
curl -s http://localhost:8081/sys-man/api/v1/agent/tools?category=diagnostic \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# 期望: ≥3

# 测试工具1 — 读取偏差
curl -s -X POST http://localhost:8081/sys-man/api/v1/agent/tools/execute \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"query_worldmodel_deviation","params":{}}' | jq '.data'
# 期望: 返回偏差列表，包含华强钢构67%准时率
```

---

### T2.2: 经营诊断Agent创建+配置（2天）

**目标**: 在AgentStudio创建一个"经营诊断Agent"，挂上3个工具+行业Prompt

**改文件**: 
- `databridge-aimod/aimod-impl/src/main/resources/db/migration/V30__ecos_diagnostic_agent.sql` — Agent配置种子数据
- `c2eos/src/pages/AgentStudio.tsx` — （如需要前端新建Agent表单适配）

**Agent配置**:
- 名称: 经营诊断Agent
- 工具: query_worldmodel_deviation + trace_causal_chain + generate_scenarios
- 系统Prompt: 注入项目型企业know-how（产值率/两金压降/回款率/供应商风险）
- 触发条件: DQ规则检测到偏差（任一目标实际值<目标值80%）

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 验证Agent配置
curl -s http://localhost:8081/sys-man/api/v1/agent/config/diagnostic \
  -H "Authorization: Bearer $TOKEN" | jq '.data.tools | length'
# 期望: 3

# 端到端测试：提交诊断请求
curl -s -X POST http://localhost:8081/sys-man/api/v1/agent/call \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agent":"diagnostic","query":"浙北路桥项目进度滞后的根因是什么？"}' | jq '.data.answer'
# 期望: 回答包含"华强钢构"和"交货延迟"
```

---

### T2.3: 前端诊断结果展示（1天，FE）

**目标**: CEO在BizDashboard看到红色偏差标注→点击→Agent诊断结果弹窗

**改文件**: 
- `c2eos/src/pages/BizDashboard.tsx` — 追加偏差高亮+诊断入口
- `c2eos/src/components/DiagnosticPanel.tsx` — 新建诊断结果面板

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep -c error
# 期望: 0（零新增TS错误）

pnpm build 2>&1 | tail -3
# 期望: "✓ built in Xs"
```

---

## 验收方式

肖总亲自检查：
1. `git log --since="2026-06-28"` 查看提交节奏（每天至少1个commit）
2. 逐条执行上述curl验收命令
3. 浏览器打开 `http://localhost:5173/ecos/biz-dashboard` → 看到项目统计数字不是0 → 点击偏差标记 → Agent诊断结果弹窗

## 跨行业验证（P2完成后）

做完上面全部后，换一套Ontology模板验证跨行业适配：

| 行业 | Ontology核心实体 | 因果链 | KPI | 验证方式 |
|------|-----------------|--------|-----|---------|
| 项目型 | 项目→合同→供应商 | 材料→进度→营收→利润 | 产值率/两金/回款率 | ✅ 本指令已覆盖 |
| 制造型 | 产线→工单→物料 | 设备效率→产量→交付→利润 | OEE/良品率/库存周转 | 换V31种子数据 |
| 商贸型 | 渠道→SKU→客户 | 库存→动销→回款→利润 | GMV/毛利率/周转天数 | 换V32种子数据 |

**不改一行代码，只换SQL种子数据。** 这是ECOS Ontology驱动架构的真正力量——当前尚未验证。

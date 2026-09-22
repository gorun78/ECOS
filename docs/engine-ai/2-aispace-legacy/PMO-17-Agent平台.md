# PMO指令：Phase2-4-Agent平台 — Agent全生命周期（市场/部署/监控）

> 来源: 完善计划 Phase 2-4 T9 | 工期: 1周 | 范围: 前端AgentStudioView重写 | 依赖: PMO-08(ai-engine补齐)

---

## §背景

当前AgentStudioView是1302行mock。后端有6个内置Agent模板+CRUD+instantiate+test。需要完整的前端管理体验。

---

## §Task

### T9-1: Agent市场（2天）

**文件**: 新建 `components/aiworkbench/agent-studio/AgentMarket.tsx`

**功能**:
- 6内置模板以卡片形式展示（model/temperature/icon/description）
- 每个卡片有"实例化"按钮 → 弹窗输入name→调 `/api/v1/aip/agents/instantiate`
- 已实例化的Agent标记"已创建"
- 搜索/筛选：按名称/引擎类型

### T9-2: Agent管理（1天）

**文件**: 新建 `components/aiworkbench/agent-studio/AgentManager.tsx`

**功能**:
- Agreement列表（名称/状态/模型/最后修改/操作）
- 编辑：名称/System Prompt/模型/温度/最大轮次
- 上线/下线开关（更新status字段）
- 版本回滚（保留最近3个版本，从 `ecos_agent_version` 表读取）

**DB表**:
```sql
CREATE TABLE IF NOT EXISTS ecos_agent_version (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    config TEXT,         -- JSON: 该版本的完整配置
    created_at TIMESTAMP DEFAULT NOW()
);
```

### T9-3: Agent监控面板（2天）

**文件**: 新建 `components/aiworkbench/agent-studio/AgentMonitor.tsx`

**功能**:
- 指标卡片（调用量/成功率/平均延迟/P99延迟）
- 趋势图（最近24h/7d/30d）— 使用Canvas自绘简单折线图
- 错误列表（最近10条错误：时间/Agent/错误信息/traceId）

**后端API**（如不存在则新增）:
| 端点 | 用途 |
|------|------|
| `GET /api/v1/agent-metrics/{agentId}` | 单个Agent指标 |
| `GET /api/v1/agent-metrics/{agentId}/errors` | 最近错误列表 |

**验收**:
- 市场页：6卡片展示 → 点击实例化 → 新Agent出现在管理列表
- 管理页：Agent列表CRUD → 下线Agent后状态变灰
- 监控页：选择Agent → 显示指标卡片+趋势图 → 错误列表可展开

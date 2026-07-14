# ECOS 业务目标驱动的数据落地方法论

> 核心理念：业务目标 → 情景建模 → 本体设计 → 对象落地 → 质量管控 → 持续运营

---

## 一、方法论总览：DIKW×PDCA 双环驱动

```
业务目标 (Why)
    ↓
World Model 情景建模 — 要达成什么？关键情景是什么？
    ↓
Ontology Designer 本体设计 — 需要哪些实体？它们之间什么关系？
    ↓
Object Runtime 对象落地 — 录入真实数据，建立关系图谱
    ↓
Data Quality 质量管控 — 规则扫描，问题闭环
    ↓
Monitoring 持续监控 — 目标达成度，数据健康度
    ↑_____________________________↓
           PDCA 持续迭代
```

ECOS 的 DIKW 四层天然对应这个流程：

| 层级 | 做的事 | ECOS 工具 |
|------|--------|-----------|
| **W · 智慧层** | 定义目标、情景、因果链 | World Model |
| **K · 知识层** | 设计流程、建立规则 | Workflow Designer · Pipeline |
| **I · 信息层** | 建模实体、属性、关系 | Ontology Designer · 术语库 |
| **D · 数据层** | 录入对象、质量检查、持续运营 | Object Runtime · Data Quality |

---

## 二、实操五步法

### Step 1: 定义业务目标（World Model）
> 工具：World Model → 目标管理 → 情景分析

**做什么：**
- 明确 3-5 个核心业务目标（可量化）
- 每个目标分解为关键情景（Scenario）
- 建立目标→情景→因果链路

**高速信科示例：**
```
目标1: 设备故障响应时间从4小时缩短到30分钟
  情景A: 设备自动告警 → 派单 → 维修 → 关闭
  情景B: 巡检发现隐患 → 登记 → 计划维修

目标2: 项目交付准时率从70%提升到90%
  情景A: 进度滞后预警 → 资源调配
  情景B: 承建方履约评估

目标3: 数据质量评分从65分提升到85分
  情景A: 数据缺失自动发现
  情景B: 问题工单闭环
```

### Step 2: 本体建模（Ontology Designer）
> 工具：Ontology Designer → 实体定义 → 属性设计 → 关系建模

**做什么：**
- 从情景中提取核心业务实体
- 定义每个实体的属性和约束
- 建模实体间关系（1:1, 1:N, N:M）

**高速信科示例：**
```
从「设备故障响应」情景提取：
  实体: Equipment (设备)
    - equipCode, equipType, installLocation, roadSection, manufacturer, status
  实体: Inspection (巡检记录)
    - inspectType, targetId, inspectDate, result, inspector
  实体: Alert (告警事件)
    - alertLevel, alertSource, alertTime, description, status

  关系: Equipment ← inspected_by → Inspection
        Equipment ← triggers → Alert
        Alert ← resolved_by → Inspection
```

### Step 3: 对象落地（Object Runtime）
> 工具：Object Runtime → 创建对象 → 建立关系 → 状态流转

**做什么：**
- 按实体模板录入真实业务对象
- 建立对象间的关系
- 设置状态机，定义状态转换规则

**高速信科示例（已预置数据）：**
```
Equipment: 5台设备
  CAM-G4-001 监控摄像头 → 运行中
  ETC-G4-001 ETC门架 → 运行中
  VD-G4-005 车检器 → 故障 ⚠️

Inspection: 3条巡检
  巡检VD-G4-005 → 异常 → 需更换模块

Alert: 3条告警
  CAM-G4-001离线 → 严重 → 处理中
```

### Step 4: 质量管控（Data Quality）
> 工具：Data Quality Dashboard → 配置规则 → 自动扫描 → 问题跟踪

**做什么：**
- 配置数据质量规则（完整性/准确性/一致性/及时性）
- 定时自动扫描
- 问题发现→指派→处理→关闭闭环

**高速信科规则示例（已预置8条）：**
```
规则1: 设备编码不能为空 (COMPLETENESS · HIGH)
规则2: 路段里程>0 (ACCURACY · HIGH)
规则3: 巡检结果不为空 (COMPLETENESS · MEDIUM)
规则4: 告警必须在30分钟内响应 (TIMELINESS · HIGH)
规则5: 项目进度不能超过100% (ACCURACY · MEDIUM)
规则6: 供应商评级不能为空 (COMPLETENESS · LOW)
规则7: 设备安装日期不能晚于今天 (ACCURACY · MEDIUM)
规则8: 告警级别必须在枚举范围内 (CONSISTENCY · HIGH)
```

### Step 5: 持续监控（Monitoring）
> 工具：Monitoring Center · BizDashboard · Mission Control

**做什么：**
- 业务目标达成度仪表盘
- 数据质量趋势
- 系统运行健康度
- 定期复盘，调整目标和规则

---

## 三、实战案例：高速信科「设备故障30分钟响应」

### 业务目标
> 设备故障从发生到响应 ≤ 30分钟

### 数据落地路径

```
W层（目标）  → 「设备故障响应≤30min」录入 World Model Goal
K层（流程）  → Workflow: 告警→派单→处理→关闭
I层（模型）  → Equipment实体 + Alert实体 + 关系模型
D层（数据）  → 5台设备对象 + 3条告警 + 巡检记录
Q层（质量）  → DQ规则: 告警响应时间≤30min (TIMELINESS)
M层（监控）  → 告警响应时间仪表盘，每日趋势
```

### 在 ECOS 中的操作步骤

1. **World Model** → 新建目标"设备故障30分钟响应"
2. **Ontology Designer** → 确认 Equipment/Alert 实体属性完整
3. **Object Runtime** → 录入全部机电设备（已预置5台）
4. **Workflow Designer** → 设计「设备告警处理」流程
5. **Data Quality** → 配置响应时间规则
6. **Monitoring** → 添加响应时间KPI到仪表盘

---

## 四、关键原则

| 原则 | 说明 |
|------|------|
| **目标先行** | 先定义业务目标，再反推需要什么数据，避免"为了建数据而建数据" |
| **模型驱动** | Ontology 是数据治理的"宪法"，先建模再落数据 |
| **质量内建** | DQ规则随实体一起定义，不要等数据脏了再治理 |
| **闭环思维** | 每个目标都有衡量指标，每个问题都有跟踪闭环 |
| **迭代演进** | 从核心实体开始，逐步扩展，不追求一步到位 |

---

## 五、与 BI/数仓的差异

| 维度 | 传统BI/数仓 | ECOS方法 |
|------|------------|----------|
| 起点 | 数据有什么？ | 业务要什么？ |
| 建模 | 星型/雪花模型 | 本体语义模型 |
| 质量 | 事后清洗 | 事前规则+实时扫描 |
| 关系 | 外键关联 | 语义关系图谱 |
| 目标 | 报表好看 | 业务闭环 |

---

> **一句话总结：业务目标定义"要什么"→ World Model建模"怎么达成"→ Ontology设计"需要什么数据"→ Object Runtime"录入真实数据"→ Data Quality"持续保障质量"→ Monitoring"验证目标达成"。**

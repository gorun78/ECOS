# PMO-20: cognitive-engine重写 — 因果推理+情景推演+混合推理

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🔴 P0
> **引擎**: cognitive-engine:18089 | **依赖**: kb-engine-api (仅API，不impl)
> **工期**: 5天 | **协同**: ECOS-PM + ECOS-BE

---

## §背景

认知引擎是六引擎中**最薄弱的**——两个核心Service均为空壳：
- `CausalReasonerServiceImpl.inferCausalGraph()` 返回空ArrayList
- `ScenarioSimulatorServiceImpl.runSimulation()` 返回假数据(confidence=0.7)

Phase 3要求cognitive-engine成为**项目场景工作台支撑引擎**，提供因果推理(≥3层)、情景推演(What-if)、混合推理(RAG+KG+规则融合)三大能力。

**引擎职责边界（2026-08-07明确）**：
- kb-engine：知识库生命周期（存储/检索/抽取/同步）
- cognitive-engine：场景推理（因果链/情景推演/混合推理/诊断报告）
- cognitive不存数据，不管理规则CRUD，不做知识抽取

---

## §禁止清单

1. ❌ 不新增DB表 — 推理结果实时计算，不持久化（铁律3.3）
2. ❌ 不直接import kb-engine-impl — 只依赖kb-engine-api（铁律2.1）
3. ❌ 不跨Phase预创建文件（铁律5.1 #1）
4. ❌ 不引入规则引擎（Drools等）— SpEL表达式评估即可
5. ❌ 不新增Maven模块（铁律0.4）

---

## §Task

### T1: CausalReasonerServiceImpl重写（3天）

**文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CausalReasonerServiceImpl.java`

**现状**: 24行，inferCausalGraph返回`new ArrayList<>()`，estimateCausalEffect返回`0.5`

**目标**: KG路径遍历+LLM推理混合，因果链≥3层

**实现要求**：
1. 输入偏差指标（如"毛利率下降5%"），输出因果链JSON
2. **KG路径遍历**：通过kb-engine API查询Neo4j中关联节点，沿`CAUSES`/`AFFECTS`/`CORRELATES`关系遍历≥3层
3. **LLM推理补充**：KG无覆盖的链路，调ai-engine的LLM推理（DeepSeek），生成可能的因果路径+置信度
4. **根因定位**：遍历到叶子节点后，LLM生成根因描述+改进建议
5. **置信度计算**：KG路径(confidence=0.8)→LLM推理(0.5-0.7)两级

**输出格式**：
```json
{
  "rootCause": "大客户A切换供应商",
  "causalChain": [
    {"depth": 1, "node": "毛利率下降5%", "confidence": 1.0, "source": "metric"},
    {"depth": 2, "node": "营收下降3%", "confidence": 0.85, "source": "KG"},
    {"depth": 3, "node": "大客户A订单减少40%", "confidence": 0.78, "source": "KG"},
    {"depth": 4, "node": "大客户A切换供应商", "confidence": 0.65, "source": "LLM"}
  ],
  "suggestions": ["分散客户集中度风险", "启动竞品分析"],
  "affectedMetrics": ["revenue", "gross_margin", "customer_concentration"]
}
```

**curl验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 诊断请求
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/cognitive/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"metric": "毛利率", "deviation": -5.0, "domain": "finance"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     chain=d.get('data',{}).get('causalChain',[]); \
     print(f'PASS: {len(chain)}层因果链' if len(chain)>=3 else f'FAIL: 仅{len(chain)}层')"
# 期望: PASS: ≥3层因果链
```

---

### T2: ScenarioSimulatorServiceImpl重写（2天）

**文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/ScenarioSimulatorServiceImpl.java`

**现状**: 25行，返回假SimulationResult

**目标**: What-if推演——改输入变量→LLM预测输出→对比基线

**实现要求**：
1. 输入：场景名称 + 变量集（如`{"原材料价格": "+10%"}`）
2. 从kb-engine拉取当前本体对象状态作为基线
3. 调用ai-engine LLM预测变量变更后的输出变化
4. 对比基线：生成变化Δ值+趋势方向
5. confidence基于LLM返回的确定性+KG路径覆盖度

**输出格式**：
```json
{
  "baseline": {"毛利率": 35.2, "营收": 1200},
  "predicted": {"毛利率": 32.1, "营收": 1180},
  "deltas": {"毛利率": -3.1, "营收": -20},
  "trends": {"毛利率": "down", "营收": "down"},
  "confidence": 0.72,
  "assumptions": ["原材料成本传递系数0.8", "售价不变"]
}
```

**curl验收**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/cognitive/scenario/simulate \
  -H 'Content-Type: application/json' \
  -d '{"name":"原材料涨价10%","variables":{"raw_material_cost":"+10%"},"domain":"finance"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     p=d.get('data',{}).get('predicted',{}); b=d.get('data',{}).get('baseline',{}); \
     print('PASS: 预测已生成' if p and b else 'FAIL: 返回空')"
# 期望: PASS
```

---

### T3: KnowledgeReasonerService增强（2天）

**文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/KnowledgeReasonerService.java`

**现状**: 有HYBRID模式骨架，但因果/情景为stub

**目标**: RAG+KG+规则三重融合

**实现要求**：
1. **HYBRID模式落地**：输入NL问题 → 并行三路 → 融合排序
   - RAG路：调kb-engine `/api/v1/kb/rag` 检索文档片段
   - KG路：调kb-engine `/api/v1/kb/graph/query` 查图谱节点
   - 规则路：调kb-engine `/api/v1/kb/rules` 匹配适用规则
2. **融合排序**：三路结果按置信度加权合并，去重
3. **来源标注**：每个推理片段标注来源(RAG/KG/RULE)+置信度
4. 超时保护：任一路>10s自动降级跳过

**curl验收**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/knowledge/reason \
  -H 'Content-Type: application/json' \
  -d '{"query":"差旅费报销审批流程","mode":"HYBRID"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     sources=d.get('data',{}).get('sources',[]); \
     print(f'PASS: {len(sources)}个来源' if len(sources)>0 else 'FAIL: 无来源')"
# 期望: PASS: ≥1个来源
```

---

### T4: 新增DiagnosisController（1天）

**文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/DiagnosisController.java`（新建）

**端点**:
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/cognitive/diagnose` | POST | 因果诊断 |
| `/api/v1/cognitive/diagnose/history` | GET | 诊断历史(内存缓存最近10次) |

**三滤波器注册**（铁律1.2 🔴）:
- [ ] VersionPrefixRewriteFilter: 确认 `/api/v1/cognitive/`→`/api/cognitive/` 映射或移除
- [ ] SecurityConfig: 加 `/api/v1/cognitive/**` 到permitAll
- [ ] ClearanceInterceptor: 加 `/api/v1/cognitive` 到豁免列表

---

### T5: 新增ScenarioController（1天）

**文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/ScenarioController.java`（新建）

**端点**:
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/cognitive/scenario/simulate` | POST | 情景推演 |
| `/api/v1/cognitive/scenario/list` | GET | 预设场景模板列表 |
| `/api/v1/cognitive/scenario/compare` | POST | 多情景对比 |

**三滤波器注册**: 同上T4，路径`/api/v1/cognitive/**`已在T4注册，确认覆盖`/api/v1/cognitive/scenario/*`

---

## §验证门禁

```bash
# V1: 文件生存
find engine/cognitive-engine -name "*.java" -newer /home/guorongxiao/ECOS/docs/3-data/PMO-20-cognitive-engine重写.md -type f | sort

# V2: 集成点grep
grep -n "CausalReasonerService\|ScenarioSimulatorService\|DiagnosisController\|ScenarioController" \
  engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/KnowledgeReasonerService.java

# V3: 编译 (全量install)
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q 2>&1 | tail -5'

# V4: Gateway启动+curl (T1-T5各跑一条)
bash ~/start-gateway.sh &
# T1: causal chain ≥3层
# T2: scenario simulate
# T3: hybrid reason
# T4+T5: 三滤波器验证 (curl -I 返回200非403/404)
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 CausalReasoner重写 | 3天 | — |
| T2 ScenarioSimulator重写 | 2天 | — |
| T3 KnowledgeReasoner增强 | 2天 | T1(共用LLM调用模式) |
| T4 DiagnosisController | 1天 | T1 |
| T5 ScenarioController | 1天 | T2 |

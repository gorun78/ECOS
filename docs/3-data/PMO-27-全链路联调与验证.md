# PMO-27: Phase 3全链路联调+验证

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🔴 P0
> **范围**: 全栈 — Gateway + 前端 + 六引擎
> **工期**: 3天 | **协同**: ECOS-PM + ECOS-BE + ECOS-FE + ECOS-QA
> **依赖**: PMO-20/21/22/23/24/25/26 全部完成

---

## §背景

Phase 3的所有组件已就位，需要做端到端验证：数据→本体→知识→AI全链路贯通。

---

## §禁止清单

1. ❌ 验证不通过不进Phase 4
2. ❌ 验证时不改任何功能代码 — 只记录Bug，回PMO修复
3. ❌ 编译失败不进Gateway启动
4. ❌ 不跳步骤 — 严格按T1→T5顺序

---

## §Task

### T1: 编译+启动验证（0.5天）

**后端**:
```bash
# 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'

# 编译时间: 期望 <8分钟
# 清理端口
lsof -ti:8080 | xargs kill -9 2>/dev/null; sleep 2

# 启动Gateway (enterprise profile, 需要Neo4j)
bash ~/start-gateway.sh &

# 等待就绪
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health 2>/dev/null)
  if [ "$code" = "200" ]; then echo "Gateway UP ($i s)"; break; fi
  sleep 2
done
# 期望: Gateway启动 <60s
```

**前端**:
```bash
cd /home/guorongxiao/ECOS/ecos_frontend
npm run dev &
# 确认 :3000 可访问，DevTools Console无error
```

**验收标准**:
- [ ] 后端编译成功，0 error
- [ ] Gateway :8080 UP，`/api/health` 返回200
- [ ] 前端 :3000 UP，F12 Console 0 error
- [ ] 登录 admin/admin123 成功

---

### T2: 数据工作台全链路（1天）

**场景**: 接入科创信息PG财务数据库 → 建目录 → 配DQ规则 → 跑质量巡检 → 看血缘DAG

| 步骤 | 操作 | API | 验收 |
|:--|------|------|------|
| 1 | 向导式接入数据源 | `POST /api/v1/engine/data/datasource` | 测试连接成功→导入表结构 |
| 2 | 树形目录浏览 | `GET /api/v1/engine/data/catalog` | 展开3层(数据源→Schema→表→字段) |
| 3 | 右键菜单预览数据 | `POST /api/v1/engine/data/query` | 弹窗显示前100行 |
| 4 | 配置DQ规则(从模板) | `POST /api/v1/engine/data/quality/rules` | 空值率检查规则创建成功 |
| 5 | 手动执行DQ | `POST /api/v1/engine/data/quality/evaluate` | 返回检查结果 |
| 6 | 查看血缘DAG | `GET /api/v1/engine/data/lineage` | 显示字段级血缘图(非mock) |
| 7 | 确认DQ定时调度 | `GET /api/v1/runtime/task/list?type=SCHEDULED` | DQ任务已注册 |

**验收**: 7步全部通过，每步截图

---

### T3: 知识工作台全链路（1天）

**场景**: 上传科创制度文件 → 抽取实体+规则 → 建知识图谱 → RAG问答

| 步骤 | 操作 | API | 验收 |
|:--|------|------|------|
| 1 | 上传制度PDF | `POST /api/v1/kb/extraction/upload` | 返回task_id，状态PARSING |
| 2 | 等待抽取完成 | `GET /api/v1/kb/extraction/tasks/{id}` | 状态→PENDING_REVIEW |
| 3 | 左右分栏审核 | 前端UI验证 | 原文+抽取结果分栏显示 |
| 4 | 确认入库 | `POST /api/v1/kb/extraction/{id}/approve` | 状态→APPROVED |
| 5 | 实体链接 | `POST /api/v1/kb/entity/link` | 实体映射到本体对象 |
| 6 | 图谱可视化 | 前端验证GraphExplorerTab | 节点可拖拽+展开/收起 |
| 7 | RAG问答 | `POST /api/v1/kb/rag` | "差旅费报销审批流程"→返回制度条款+来源 |
| 8 | 力导向布局 | 前端验证 | 图谱节点自动布局，无重叠 |

**验收**: 8步通过

---

### T4: 认知引擎诊断验证（1.5天）

**场景A: 因果链诊断**

| 步骤 | 操作 | API | 验收 |
|:--|------|------|------|
| 1 | 输入诊断请求 | `POST /api/v1/cognitive/diagnose` | 返回因果链JSON |
| 2 | 因果链深度 | 检查`causalChain`数组 | **≥3层** |
| 3 | 根因定位 | 检查`rootCause`字段 | 非空、非泛泛"需进一步分析" |
| 4 | 改进建议 | 检查`suggestions`数组 | ≥2条建议 |
| 5 | 置信度 | 检查每层`confidence` | 各层有合理置信度值 |

**场景B: 情景推演**

| 步骤 | 操作 | API | 验收 |
|:--|------|------|------|
| 1 | 输入What-if | `POST /api/v1/cognitive/scenario/simulate` | 返回基线+预测+Δ值 |
| 2 | 基线与预测对比 | 检查`baseline` vs `predicted` | 数值有变化 |
| 3 | 趋势方向 | 检查`trends`字段 | 每个指标有up/down/flat |
| 4 | 多情景对比 | `POST /api/v1/cognitive/scenario/compare` | 返回多情景对比表 |

**场景C: 混合推理**

| 步骤 | 操作 | API | 验收 |
|:--|------|------|------|
| 1 | HYBRID推理 | `POST /api/v1/knowledge/reason` mode=HYBRID | 返回3路来源(RAG/KG/RULE) |
| 2 | 来源标注 | 检查`sources`数组 | 每条有`source` + `confidence` |

**验收**: 3个场景全部通过

---

### T5: 回归验证+脚本（1天）

**Phase 1回归**（sysman）:
- [ ] 登录/用户CRUD/角色CRUD/审计日志
- [ ] 安全中心三Tab

**Phase 2回归**（AI工作台）:
- [ ] ChatbotStudio对话
- [ ] AgentStudio 6内置Agent
- [ ] Agent测试控制台

**验证脚本**: `tests/phase3-verify.sh`

```bash
#!/bin/bash
# Phase 3验证脚本
BASE="http://localhost:8080"
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
AUTH="Authorization: Bearer $TOKEN"

echo "=== Phase 3 验证 ==="

# 1. 数据源
echo -n "1. 数据源列表: "
curl -s -H "$AUTH" $BASE/api/v1/engine/data/datasource | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 2. 数据血缘
echo -n "2. 数据血缘: "
curl -s -H "$AUTH" "$BASE/api/v1/engine/data/lineage" | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 3. DQ规则
echo -n "3. DQ规则: "
curl -s -H "$AUTH" "$BASE/api/v1/engine/data/quality/rules" | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 4. 认知诊断
echo -n "4. 认知诊断: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/cognitive/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"metric":"test","deviation":-5.0}' | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 5. 情景推演
echo -n "5. 情景推演: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/cognitive/scenario/simulate \
  -H 'Content-Type: application/json' \
  -d '{"name":"test","variables":{"x":"+10%"}}' | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 6. 混合推理
echo -n "6. 混合推理: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/knowledge/reason \
  -H 'Content-Type: application/json' \
  -d '{"query":"test","mode":"HYBRID"}' | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 7. RAG
echo -n "7. RAG检索: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/kb/rag \
  -H 'Content-Type: application/json' \
  -d '{"query":"test","topK":5}' | grep -q '"code":0' && echo "PASS" || echo "FAIL"

# 8. 前端编译
echo -n "8. 前端TS: "
cd /home/guorongxiao/ECOS/ecos_frontend
npx tsc --noEmit 2>&1 | grep -q "^$" && echo "PASS" || echo "CHECK (可能有既有error)"

echo "=== 验证完成 ==="
```

---

## §验收总表

| 场景 | 步骤数 | 状态 |
|------|:--:|:--:|
| T1 编译+启动 | 3 | ☐ |
| T2 数据全链路 | 7 | ☐ |
| T3 知识全链路 | 8 | ☐ |
| T4a 因果链诊断 | 5 | ☐ |
| T4b 情景推演 | 4 | ☐ |
| T4c 混合推理 | 2 | ☐ |
| T5 Phase 1/2回归 | 8 | ☐ |
| **合计** | **37** | |

**通过标准**: 37/37全部PASS → 进入Phase 4

---

## §工时

| Task | 工期 |
|:--|:--:|
| T1 编译+启动 | 0.5天 |
| T2 数据全链路 | 1天 |
| T3 知识全链路 | 1天 |
| T4 认知诊断 | 1.5天 |
| T5 回归+脚本 | 1天 |

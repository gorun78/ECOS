# PMO-26: 知识工作台功能增强+i18n

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🟡 P1
> **范围**: 前端 `src/pages/knowledge/` + `src/components/GraphCanvas.tsx` + 国际化
> **工期**: 5天 | **协同**: ECOS-FE
> **依赖**: PMO-24(合并拆分完成) + PMO-22(cognitive重写完成后可对接新API)

---

## §背景

知识工作台合并拆分(PMO-24)完成后，增强4项功能+国际化~800行硬编码中文。

---

## §禁止清单

1. ❌ 不硬编码Tailwind颜色（铁律4.1）
2. ❌ 不硬编码中文字符串（铁律4.3）
3. ❌ 不自定义SVG图标（铁律4.2）
4. ❌ 文件不超800行（铁律4.6）
5. ❌ 不改后端API路径

---

## §Task

### T1: 知识图谱力导向布局+交互增强（2天）

**文件**: 
- `src/pages/knowledge/tabs/GraphExplorerTab.tsx` (678行→增强，≤700行)
- `src/components/GraphCanvas.tsx` (修改，增加力导向布局)

**增强项**：

1. **力导向布局**：
   - 当前GraphCanvas为静态布局，需增加d3-force或自定义力导向算法
   - 节点斥力+边吸引力，自动避让重叠
   - 布局动画过渡（节点平滑移动到新位置）

2. **节点交互**：
   - 拖拽：节点可拖拽移动，松手后回弹到力导向平衡点
   - 展开/收起：点击节点→展开邻居节点(1层)→再次点击收起
   - 选中高亮：选中节点+其1阶邻居高亮，其余半透明

3. **路径高亮**：
   - 搜索框输入节点A→B→C→触发路径查找(`/api/v1/kb/graph/path`)
   - 路径节点+边高亮加粗，其余半透明

4. **全文搜索**：
   - 搜索框改调`POST /api/v1/kb/graph/search`（如不存在→调graph/query模糊匹配）
   - 搜索结果列表→点击跳转到对应节点+居中

**验收**:
```bash
# 搜索"应收账款"→图谱节点高亮
# 拖拽节点→松手回弹
# 展开节点→显示邻居节点
```

---

### T2: 知识抽取上传+左右分栏审核（2天）

**文件**:
- `src/pages/knowledge/tabs/KnowledgeExtractionTab.tsx` (重写，≤600行)
- 新建 `src/pages/knowledge/components/ExtractionReviewPanel.tsx` (≤300行)

**功能**：

1. **上传区**：拖拽或点击上传PDF/Word/TXT（调用后端`POST /api/v1/kb/extraction/upload`）
2. **抽取进度**：上传后轮询`GET /api/v1/kb/extraction/tasks/{id}`→进度条(UPLOADED→PARSING→EXTRACTING→PENDING_REVIEW)
3. **左右分栏审核**：
   - 左栏：原文(Markdown渲染，关键实体高亮)
   - 右栏：抽取结果列表(实体/关系/规则三类，每项有checkbox确认)
   - 可手动修正实体名称/类型
4. **确认入库**：
   - 勾选的实体→调`POST /api/v1/kb/extraction/{id}/approve`
   - 拒绝的→调`POST /api/v1/kb/extraction/{id}/reject`
   - 入库后自动触发实体链接(PMO-22 T2)

**验收**: 上传PDF→看到进度条→左右分栏审核→勾选确认→入库成功

---

### T3: RAG检索增强（1天）

**文件**: `src/pages/knowledge/tabs/RagTab.tsx` (113行→增强，≤250行)

**增强项**：

1. **来源标注格式化**：
   - 每条来源显示：文档标题+类型icon(📄法规/📊表格/📈报告)+置信度进度条+页码
   - 点击来源→跳转到对应文档原文位置

2. **追问上下文**：
   - 多轮对话：保持前3轮问答在context中
   - 自动压缩：超4轮→LLM自动摘要前文

3. **置信度展示**：
   - 答案区域顶部显示总置信度badge(绿>0.8/黄>0.6/红<0.6)
   - 低置信度提示"此答案基于有限信息，建议人工复核"

**验收**: RAG问答→来源格式化显示+置信度badge+追问上下文保持

---

### T4: 规则库树形导航（1天）

**文件**: `src/pages/knowledge/tabs/KnowledgeRuleRepositoryTab.tsx` (546行→增强，≤600行)

**增强项**：

1. **法规层级树**：法规→章节→条款三级导航
   - 数据从`GET /api/v1/kb/rules?groupBy=regulation`获取
   - 前端构建树形结构（如果后端返回平铺→groupBy regulation+chapter）

2. **本体对象关联**：每条规则→关联的本体对象类型可点击跳转
   - 点击"差旅费报销"→跳转到本体工作台对应对象类型

**验收**: 左侧法规树→点击章节→展开条款→右侧规则列表联动

---

### T5: i18n国际化+样式统一 — 知识工作台（2天）

**新增namespace**: `knowledge`（约100 keys）

**locale文件**: `src/i18n/locales/knowledge/zh-CN.json` + `en.json`

**涉及文件**（~25个）: 全部知识工作台相关页面/组件/tabs

**中文行数统计**（从差距分析）：
| 文件 | 中文行数 |
|------|:--:|
| KnowledgeGraphHome.tsx | 94 |
| KnowledgeComplianceCheckTab.tsx | 73 |
| GraphExplorerTab.tsx | 71 |
| KnowledgeExtractionTab.tsx | 66 |
| KnowledgeRuleRepositoryTab.tsx | 52 |
| SyncTab.tsx | 35 |
| ClosedLoopTab.tsx | 29 |
| GlossaryTab.tsx | 27 |
| AIPKnowledgeView.tsx | 27 |
| OntologyTab.tsx | 22 |
| ... 其余~15个文件 | ~300 |
| **合计** | **~800** |

**i18n验收**: 同PMO-25 T4脚本，目标TOTAL: 0（注释除外）

**样式验收**: 手动切4主题，知识工作台所有Tab无白块/黑块

---

## §验证门禁

```bash
# V1: i18n文件
test -f src/i18n/locales/knowledge/zh-CN.json && echo "PASS" 
test -f src/i18n/locales/knowledge/en.json && echo "PASS"

# V2: 硬编码中文清零
python3 -c "
import re, os
total = 0
for root, dirs, files in os.walk('src/pages/knowledge'):
    for f in files:
        if f.endswith('.tsx'):
            with open(os.path.join(root,f)) as fh:
                cnt = sum(1 for l in fh if re.search(r'[\u4e00-\u9fa5]', l))
            if cnt > 0: print(f'{f}: {cnt}'); total += cnt
# 额外文件
for f in ['KnowledgeGraph.tsx','KnowledgeGraphHome.tsx','KnowledgeGraphPage.tsx',
          'KnowledgeView.tsx','AIPKnowledgeView.tsx','GraphExplorerView.tsx',
          'ScenarioManagementView.tsx','WorldModelViewer.tsx','CognitiveEngineView.tsx']:
    fp = f'src/pages/{f}'
    if os.path.exists(fp):
        with open(fp) as fh:
            cnt = sum(1 for l in fh if re.search(r'[\u4e00-\u9fa5]', l))
        if cnt > 0: print(f'{f}: {cnt}'); total += cnt
print(f'TOTAL: {total}')
# 期望: 0
"

# V3: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"

# V4: Vite构建
cd /home/guorongxiao/ECOS/ecos_frontend && npm run build 2>&1 | tail -3
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 力导向布局 | 2天 | PMO-24(文件就位) |
| T2 知识抽取上传 | 2天 | PMO-22 T1(后端抽取API) |
| T3 RAG增强 | 1天 | — |
| T4 规则库树形 | 1天 | — |
| T5 i18n+样式 | 2天 | T1-T4 |

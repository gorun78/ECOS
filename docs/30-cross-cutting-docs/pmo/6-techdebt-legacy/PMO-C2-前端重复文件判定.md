# PMO指令: C2 前端重复文件判定（GuardrailsView + ObjectExplorerView）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-FE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①先 diff 判定，再决定合并/改名，禁止未判定就删 ②UI 零退化 ③判定结论如实报告（重复/分化都要写清证据）

## 零、现状摸底（已核实）

两对「同名文件」，但**各自被不同入口引用**，疑似「同名不同场景」而非简单重复：

| 文件 | 行数 | 被谁 import |
|------|------|------------|
| `src/pages/GuardrailsView.tsx` | 1,161 | `main.tsx:53`（路由 `guardrails` 权威入口） |
| `src/pages/aiworkbench/GuardrailsView.tsx` | 1,464 | `aiworkbench/index.tsx:17`（AI 工作台内部 tab） |
| `src/pages/ObjectExplorerView.tsx` | 1,304 | `OntologyWorkbenchLayout.tsx:36`（本体工作台） |
| `src/pages/business-workbench/ObjectExplorerView.tsx` | 1,553 | `BusinessWorkbenchLayout.tsx:34`（业务工作台） |

## 一、目标状态

判定每对文件是「真重复（可抽公共组件合并）」还是「功能分化（保留但需改名区分）」，并据此处理。

## 二、分阶段执行计划

| Task | 文件对 | 操作 |
|:-----|--------|------|
| P1-1 | GuardrailsView 两版本 | `diff` 逐行对比，判定：真重复→抽公共 `GuardrailsView`（保留 main.tsx 权威版为唯一实现，aiworkbench 改 import 公共版）；功能分化→两版保留但**改名**区分（如 aiworkbench 版改 `AiGuardrailsView`），消除同名混淆 |
| P1-2 | ObjectExplorerView 两版本 | 同上判定逻辑；真重复→抽公共组件，两 Layout 共用；功能分化→改名区分（如业务版改 `BusinessObjectExplorer`） |

**判定标准**（写进交付报告）：
- 真重复 = 核心渲染/逻辑 >80% 相同，差异仅少量文案/字段 → 合并
- 功能分化 = 两版各有一套独立业务逻辑/数据源/交互 → 保留+改名

## 三、禁止清单

- ❌ 未 diff 就删任一版本
- ❌ 合并时丢失任一版本的独有字段/交互（合并要取并集，不是简单保留一个）
- ❌ 改路由 path、改组件对外 props
- ❌ 判定结论造假——每个 Task 附 diff 摘要 + 判定理由

## 四、风险与回滚

- **误删风险**：两个版本都可能被活跃路由使用，删错 → 页面白屏。合并前必须 grep 确认两版的所有 import 点都已改指向。
- **回滚**：每对文件处理单独 commit。

## 五、验证门禁

```bash
# V1: TS 编译零新增错误
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | head -30

# V2: 无同名残留（若判定功能分化改名）
grep -rn "GuardrailsView\|ObjectExplorerView" src/ --include='*.tsx' --include='*.ts'
# 期望: 每个名字只剩一个实现（或已改名区分）

# V3: import 指向唯一实现
grep -rn "import.*GuardrailsView\|import.*ObjectExplorerView" src/ --include='*.tsx' --include='*.ts'
# 期望: 所有 import 指向判定后的唯一/改名后的文件
```

**交付物**：每对文件一份判定报告（真重复/功能分化 + diff 摘要 + 处理结果）。

## 六、工时估算

P1-1（2h）+ P1-2（2h）≈ **4h**

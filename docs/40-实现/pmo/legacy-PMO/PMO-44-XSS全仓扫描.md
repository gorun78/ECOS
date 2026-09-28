# PMO-44 T4b — dangerouslySetInnerHTML 全仓扫描报告

> 来源指令: PMO-44 前端 P0 规范（i18n/主题/图标/安全）§T4b
> 扫描范围: `ecos_frontend/src/**`
> 扫描命令: `grep -rn "dangerouslySetInnerHTML" src`（辅以 `.innerHTML =` / `insertAdjacentHTML` / `document.write` 同类注入点扫描）
> 日期: 2026-09-06 | 责任人: ecos-be

## 结论

**全仓仅 1 处 `dangerouslySetInnerHTML`，且已完成 DOMPurify 白名单消毒，无高危 XSS 风险。** 未发现 `insertAdjacentHTML` / `document.write` / 直接 `.innerHTML =` 赋值等其他注入点。

## 命中清单

| # | 文件 | 行 | 用法 | 是否消毒 | 风险 | 处置 |
|:--|:-----|:--:|:-----|:--------:|:----:|:-----|
| 1 | `src/pages/knowledge/components/ExtractionReviewPanel.tsx` | 182 | `dangerouslySetInnerHTML={{ __html: sanitizeHighlight(highlightedSource) }}` | ✅ 是 | 低 | 已由 T4a 加固（见下） |

## 消毒实现（T4a 已完成）

`ExtractionReviewPanel.tsx` 对所有用户可影响的高亮 HTML 统一走白名单消毒：

```ts
const SANITIZE_CONFIG = {
  ALLOWED_TAGS: ['mark', 'span', 'code', 'strong'],
  ALLOWED_ATTR: ['class', 'style'],
};
function sanitizeHighlight(html: string): string {
  return DOMPurify.sanitize(html, SANITIZE_CONFIG);
}
```

- 依赖: `dompurify@3.2.7`（node_modules 已安装，`src/**` 经 `import DOMPurify from 'dompurify'` 引用）。
- 白名单仅放行 `<mark>/<span>/<code>/<strong>` + `class/style` 属性，符合 §禁止清单 第 4 条（禁止全开）。
- 高亮层 value（sourceText → `<mark>` 包裹实体名）经实体名转义（`RegExp` 特殊字符 escape）+ DOMPurify，故 `<script>`、`onerror` 等注入均被 strip。

## 建议 / 跟进（仅报告，不在此指令内修改）

1. **依赖正式化**：`dompurify` 需在 `ecos_frontend/package.json` 的 `dependencies` 中固化并随 lock 提交（当前 node_modules 已含 3.2.7，build 可过；建议 `npm i dompurify` 落到 manifest 避免全新 clone 后 build 失败）。→ 建议单独张小卡 / 交 FE 收尾。
2. **keep open**：若后续新增 Markdown 渲染 / 富文本 / LLM 输出回显（如 AIPKnowledge `llmOutput`、`ragPrompt`）直接注入 HTML，必须复用 `sanitizeHighlight` 同款白名单，禁止裸写 `dangerouslySetInnerHTML`。
3. 当前 `llmOutput` / `ragPrompt` 以 JSX 文本节点渲染（`{llmOutput}`），**未**走 HTML，安全。

## 附：其他潜在注入点复核

- `a[download]`、`window.open(url)`、`iframe src`：无本批涉及点。
- URL 参数拼接进 `href`：Relevance 等无 `srcdoc`/`src` 注入。
- 结论：本仓当前无其他需处置的 HTML 注入面。

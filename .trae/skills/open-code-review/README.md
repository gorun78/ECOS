# open-code-review

## 说明

基于 [alibaba/open-code-review](https://github.com/alibaba/open-code-review)（`ocr` CLI）的 AI 代码评审技能，**标准模式**：由 OCR CLI 调用预先配置的 LLM 端点完成评审，产出结构化的行级评审意见。

适合在 CI/CD 流水线或本地命令行中做标准化代码评审，端点集中配置、输出稳定可消费。

## 适用场景

- 用户说"评审我的改动 / 评审工作区 / review my changes"
- 评审某个 PR / feature 分支（`--from main --to <branch>`）
- 评审单个 commit（`--commit <hash>`）
- 已有可用的 LLM 端点（Anthropic 或 OpenAI 兼容）

## 前置依赖

- `ocr` CLI：`npm install -g @alibaba-group/open-code-review`
- 已配置 LLM（二选一）：
  - 环境变量：`OCR_LLM_URL` / `OCR_LLM_TOKEN` / `OCR_LLM_MODEL` / `OCR_USE_ANTHROPIC`
  - 持久化配置：`ocr config set llm.*`
- 首次运行前执行 `ocr llm test` 验证连通性

## 工作流要点

1. **环境校验**：`which ocr` + `ocr llm test`
2. **收集业务背景**：从 commit/分支信息提取，通过 `--background` 传入
3. **运行评审**：`ocr review --audience agent -b "业务背景" [用户参数]`
4. **分级报告**：High / Medium / Low，Low 静默丢弃
5. **修复（可选）**：仅在用户明确要求 "review and fix" 时自动修复 High/Medium

## 输出

按优先级分组的评审意见，每条含：

| 字段 | 说明 |
|---|---|
| `path` | 文件路径 |
| `content` | 评审意见 |
| `start_line` / `end_line` | 行范围（均为 0 表示定位失败，需结合上下文处理） |
| `suggestion_code` | 可选的修复建议代码 |
| `existing_code` | 可选的原始代码片段 |

## 关键约束

- 始终使用 `--audience agent`（避免进度 UI 污染输出）
- 不硬编码、不臆造 API key，缺失时停下来让用户提供
- diff 超 50 行会触发额外风险分析阶段，延迟增加属正常
- 自定义规则解析优先级：`--rule` > `<repo>/.opencodereview/rule.json` > `~/.opencodereview/rule.json` > 内置默认

## 与同级 skill 的关系

- 与 [open-code-review-delegate](../open-code-review-delegate/README.md) 同源 OCR，区别在评审智能来自 OCR 配置端点，而非宿主 Agent。
- 选型对比见 [skills/README.md](../README.md#模式对比)。

## 参考

- 完整定义：[SKILL.md](SKILL.md)
- 上游项目：https://github.com/alibaba/open-code-review
- NPM：https://www.npmjs.com/package/@alibaba-group/open-code-review

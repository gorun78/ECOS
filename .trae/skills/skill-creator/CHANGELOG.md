# Changelog

## [2.5.0] - 2026-08-11

### Changed
- **CUSTOMIZE-8**：SKILL.md 渐进式披露重构——将 6 段大块内容拆分到 `references/` 目录，SKILL.md 中替换为指针（`See references/xxx.md`），常驻上下文从 36,984 字符减至 17,568 字符（-52.5%）（qingting）
  - `references/eval-test-cases.md`：Test Cases（Phase 3）
  - `references/eval-run-and-review.md`：Running and evaluating test cases + iteration loop（Phase 4）
  - `references/description-optimization.md`：Description Optimization
  - `references/platform-claude-ai.md`：Claude.ai-specific instructions
  - `references/platform-cowork.md`：Cowork-Specific Instructions
  - 所有 CUSTOMIZE 标记随标题保留在 SKILL.md，保留段落原样未动
- 版本号 `2.4.0 → 2.5.0`（qingting）

## [2.4.0] - 2026-07-30

### Changed
- **CUSTOMIZE-7**：Capture Intent 从四连问改为渐进式引导——按信息完整度分层追问，信息齐全一句确认、名称缺失追问触发词+用途、完全空白给示例引导，模仿自然对话节奏（qingting）

## [2.3.0] - 2026-07-30

### Added
- **CUSTOMIZE-6**：新增 `### Triggers — Recommended Prompt Phrases` 小节，列出触发 skill-creator 的推荐提示词（qingting）
  - 创建技能：`/skill-creator`、帮我创建技能、帮我写一个 skill、新建技能、我要做一个技能、把这段流程变成技能、✨创建技能
  - 编辑/更新技能：修改技能、更新技能、编辑 skill、技能加一个功能、把这个技能改一下

## [2.2.0] - 2026-07-30

### Added
- **CUSTOMIZE-5**：SKILL.md 元数据新增 `version` 字段（SemVer 格式），位于 `name` 之后、`description` 之前（qingting）
- Anatomy 图示更新：YAML frontmatter 必填项从 `(name, description)` 改为 `(name, version, description)`

## [2.1.0] - 2026-07-30

### Changed
- **CUSTOMIZE-1**：Phase 3（Test Cases）改为可选，默认跳过，仅用户明确说"run the tests"/"test this skill"/"let's evaluate"时执行（qingting）
- **CUSTOMIZE-2**：Phase 4（Running and evaluating test cases）改为可选，默认跳过整节（qingting）
- **CUSTOMIZE-3**：Phase 6（Package and Present）整节注释掉，不再执行打包（qingting）
- **CUSTOMIZE-4**：版本号 `2.0.0 → 2.1.0`（qingting）
- 结尾总结同步更新：Phase 3+4 标记 `(Optional)`，Phase 6 注释掉

### Details
6 处改动均以 `<!-- CUSTOMIZE: xxx | modified-by: qingting | modified-at: 2026-07-30 -->` 格式标注在 SKILL.md 中（YAML 区域用 `# CUSTOMIZE:`）。

## [2.0.0] - 2026-07-30

### Added
- **PATCH-2**：`name` 命名规则扩展（qingting）
- **PATCH-5**：references 严禁凭记忆复述（qingting）

# Worker 标题冒号陷阱（9次崩溃的教训）

## 症状

Worker 连续 crash，exit code 1，错误信息：`Unknown skill(s): 概要设计` 或类似。

## 根因

Hermes CLI `hermes kanban --board board swarm --worker PROFILE:TITLE` 将冒号当作分隔符：
- 第一个冒号 → `profile` 和 `title` 的分界
- 第二个冒号 → `title` 和 `skill` 的分界

## 真实案例

- **失败写法**: `arch-1785224485752:设计:概要设计`
  - swarm 解析为: `profile=arch-1785224485752`, `title=设计`, `skill=概要设计`
  - 结果: worker 找不到名为 "概要设计" 的 skill → crash (exit code 1) × 9次

- **正确写法**: `arch-1785224485752:概要设计`
  - swarm 解析为: `profile=arch-1785224485752`, `title=概要设计`
  - 结果: 成功执行

## 危险写法

- `arch-xxx:设计:概要设计` ❌
- `fullstack-xxx:修复:前端菜单` ❌
- `fullstack-xxx:构建:重启:前后端` ❌ (两个冒号)

## 修复

将标题中 `:` 替换为 `-`、空格等，确保 `profile:title` 中 title 部分不含冒号。

## 排查关键

错误信息是 "Unknown skill(s)" 而非 "title 格式错误"，容易误导。

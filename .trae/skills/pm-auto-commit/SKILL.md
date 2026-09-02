---
name: pm-auto-commit
description: "PM 自动 Git 提交推送 Skill：分支白名单校验 → 生成符合 Git提交规范的 commit message（type(模块): 动宾结构）→ add/commit/push。主干 main/master/develop 拒绝直推。Invoke when PM 需在零干预模式下自动提交并推送。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos]
triggers:
  - 自动提交
  - 自动推送
  - auto-commit
  - git 提交
metadata:
  hermes:
    tags: [pm, git, commit, push, auto, branch-guard]
    profiles: [pm]
    auto_trigger: false
    priority: high
---

# PM 自动 Git 提交推送 Skill

## 功能说明

在 PM 全自动流程（auto_pipeline）的「Git 提交推送」阶段，自动完成分支校验、commit message 生成、提交与推送。部署验证通过后由 auto_pipeline 调用。

## 🔴 分支红线（最高优先级）

1. **主干禁止直推**：`main` / `master` / `develop` / `test` 一律拒绝直接 push，必须走 MR/PR 流程。
2. **仅允许业务分支**：`feature/*`、`user/*`、`bugfix/*`、`hotfix/*` 前缀的分支可自动 push。
3. **推送前拉取**：`git pull --rebase` 同步远端，避免覆盖他人提交。
4. **禁止强推**：任何情况下禁止 `push -f`。

## Commit Message 规范（对齐 .hermes/rules/Git提交规范.md）

标准格式：`type(模块): 动宾结构精准描述`

| type | 用途 |
|------|------|
| feat | 新功能 |
| fix | 修复 |
| refactor | 重构 |
| perf | 性能 |
| style | 格式 |
| docs | 文档 |
| test | 测试 |
| chore | 构建/配置 |

- 标题行 `动宾结构`，禁用「修改代码」「update」等模糊文案
- 正文可为多段说明（实现内容 / 解决问题 / 实现方式），非强制
- 一次提交只做一件事，多业务混杂必须拆分

## 自动提交流程

```
Step 0: 校验分支（generate_commit_msg.py --repo <dir> --check-branch）
Step 1: git status + git diff --stat 收集变更范围
Step 2: 生成 commit message（type + 模块 + 动宾结构标题 + 正文）
Step 3: git add <具体文件>（禁止 git add -A 误纳敏感文件）
Step 4: git commit -m "<message>"
Step 5: git pull --rebase && git push
```

## 脚本清单

| 脚本 | 用途 |
|------|------|
| `scripts/generate_commit_msg.py` | 分支校验 + 变更收集 + commit message 骨架生成 |

## 使用方式

```bash
# 分支校验（在提交前必做，主干直接非零退出）
python3 -B <skill-dir>/scripts/generate_commit_msg.py --repo <TERMINAL_CWD> --check-branch

# 生成 commit message 骨架（供 PM 填入动宾结构标题）
python3 -B <skill-dir>/scripts/generate_commit_msg.py --repo <TERMINAL_CWD> \
  --type feat --scope 工作空间
```

## 安全过滤（提交前强制自检）

| 检查项 | 规则 |
|--------|------|
| 敏感文件 | `.env`、`*.key`、`credentials*`、`*.pem` 一律不纳入 add |
| 打包产物 | `target/`、`node_modules/`、`dist/` 不纳入 add |
| 本地配置 | `application-local.yml`、`vite.config.js`（skip-worktree）不纳入 add |

## 参考

- Git 提交规范：`.hermes/rules/Git提交规范.md`
- 自动编排契约：`kanban-dispatch-json-format/references/auto-pipeline-contract.md`

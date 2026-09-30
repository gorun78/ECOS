---
trigger: model_decision
description: 执行 git commit/push 前必读 Git 提交规范
---

提交/推送前：

1. Read `.trae/rules/Git提交规范.md`（commit message = `type(模块): 动宾结构`）
2. 主干 `main/master/develop` 禁止直推；风险操作（push/reset/force）先向用户确认
3. PM 场景可用 `pm-auto-commit` 技能执行

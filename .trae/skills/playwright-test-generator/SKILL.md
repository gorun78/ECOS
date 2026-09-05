---
name: playwright-test-generator
description: "Playwright TestAgent Generator Skill：基于 planner 产出的测试计划项，手动执行步骤并生成可执行的 Playwright .spec.ts 脚本。当 playwright-test-planner 产出测试计划后触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [qa, e2e, playwright, test-generation, testagent]
    related_skills: [playwright-test-planner, playwright-test-healer, qa-test-executor]
    artifact_type: TEST_CASES
    workflow_modes: [L3]
---

# Playwright Test Generator Skill

## 定位

TestAgent 三 Agent 协作的**第二环**：消费 planner 的测试计划项，手动执行每一步以学习最佳实践，再生成可执行的 Playwright `.spec.ts` 脚本。

## 触发条件

- `playwright-test-planner` 已产出测试计划
- 用户要求"生成 Playwright 测试脚本 / 把计划转成代码"

## 输入

- planner 产出的测试计划（Markdown）
- 计划项中的 seed 文件路径（如有）

## 输出

通过 `generator_write_test` 落盘的 `.spec.ts` 文件，要求：
- 单文件单测试
- 文件名为 fs-friendly 的场景名
- 测试置于与计划顶层项匹配的 `describe` 中
- 测试标题与场景名一致
- 每步执行前以注释标注步骤文本

## 工具说明

浏览器操作通过 `playwright-test` MCP server 注入（已在 `config.yaml` 的 `mcp_servers` 注册，headless 模式）。本 skill 使用 `generator_setup_page`、`browser_*`、`generator_read_log`、`generator_write_test` 等工具。

## 工作流

You are a Playwright Test Generator, an expert in browser automation and end-to-end testing.
Your specialty is creating robust, reliable Playwright tests that accurately simulate user interactions and validate
application behavior.

# For each test you generate
- Obtain the test plan with all the steps and verification specification
- Run the `generator_setup_page` tool to set up page for the scenario
- For each step and verification in the scenario, do the following:
  - Use Playwright tool to manually execute it in real-time.
  - Use the step description as the intent for each Playwright tool call.
- Retrieve generator log via `generator_read_log`
- Immediately after reading the test log, invoke `generator_write_test` with the generated source code
  - File should contain single test
  - File name must be fs-friendly scenario name
  - Test must be placed in a describe matching the top-level test plan item
  - Test title must match the scenario name
  - Includes a comment with the step text before each step execution. Do not duplicate comments if step requires
    multiple actions.
  - Always use best practices from the log when generating tests.

   <example-generation>
   For following plan:

   ```markdown file=specs/plan.md
   ### 1. Adding New Todos
   **Seed:** `tests/seed.spec.ts`

   #### 1.1 Add Valid Todo
   **Steps:**
   1. Click in the "What needs to be done?" input field

   #### 1.2 Add Multiple Todos
   ...
   ```

   Following file is generated:

   ```ts file=add-valid-todo.spec.ts
   // spec: specs/plan.md
   // seed: tests/seed.spec.ts

   test.describe('Adding New Todos', () => {
     test('Add Valid Todo', async { page } => {
       // 1. Click in the "What needs to be done?" input field
       await page.click(...);

       ...
     });
   });
   ```
   </example-generation>

## 边界

- **不做** 浏览器探索与计划生成（归 `playwright-test-planner`）
- **不做** 测试执行与门禁判定（归 `qa-test-executor`）
- **不做** 失败用例修复（归 `playwright-test-healer`）
- 浏览器以 headless 模式运行（服务器端，见方案文档 §2）

## 下游

生成的 `.spec.ts` 交由 `qa-test-executor` 执行；失败时由 `playwright-test-healer` 自愈。

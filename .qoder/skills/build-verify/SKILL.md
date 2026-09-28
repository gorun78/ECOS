---
name: build-verify
description: "构建验证 Skill：执行本地构建、单元测试、覆盖率检查、质量门禁判定。必须通过全部质量门禁并生成 BUILD_APPROVAL_RECORD（deliverable_allowed 判定）后才可分发给 Reviewer。当 fullstack 完成开发后自检时触发，或 PM 要求验证时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [build, test, verification, ci-cd, quality-gate, approval-record]
    related_skills: [fullstack-impl, frontend-builder, backend-builder]
    artifact_type: BUILD_ARTIFACT
    workflow_modes: [L1, L2, L3]
---

# Build Verify Skill (v2 — 交付门禁版)

## 核心原则

Build 验证后必须生成 `BUILD_APPROVAL_RECORD`，含明确的 `deliverable_allowed` 判定。`deliverable_allowed=false` 时禁止分发给 Reviewer/QA。所有质量门禁必须逐项报告通过/失败状态。

## 关键机制

### 交付门禁（Deliverable Gate）

```
构建 → Lint → 测试 → 覆盖率 → 安全扫描(L3) → APPROVAL_RECORD
                                              ↓
                              deliverable_allowed: true/false
                                              ↓
                              通过 → 分发 Reviewer
                              未通过 → 阻断，修复后重跑
```

### 质量门禁判定表

| 检查项 | L1 | L2 | L3 | 未通过处理 |
|--------|----|----|-----|-----------|
| 构建成功 | ✅ 必须 | ✅ 必须 | ✅ 必须 | 阻断，修复构建错误 |
| 单元测试 | ❌ 跳过 | ⚠️ 核心通过 | ✅ 全部通过 | 阻断，所有测试必须通过 |
| 测试通过率 | N/A | ≥ 99% | ≥ 99% | 阻断 |
| 覆盖率 | N/A | ≥ 60% | ≥ 75% | 阻断 |
| Lint 错误 | ⚠️ warning | ⚠️ warning | ✅ 0 error | 阻断（L3 必须 0 error） |
| 安全扫描 | ❌ 跳过 | ❌ 跳过 | ✅ 必须 | 阻断 |

## 触发条件

- Fullstack Impl Skill 调用（开发完成后自检）
- 用户说"构建验证"、"跑测试"、"检查代码"
- PM 要求验证构建产物时
- 代码合并前必须通过验证

## 输入

- **必需**：完整的源代码（SOURCE_PATCH#frontend + SOURCE_PATCH#backend）
- **必需**：OpenAPI 规范（用于接口一致性验证）
- **固定约束**：覆盖率阈值（L2 ≥ 60%，L3 ≥ 75%）

## 输出制品

- **BUILD_ARTIFACT**：构建产物
- **UNIT_TEST**：测试执行结果
- **BUILD_APPROVAL_RECORD**：构建批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 1: 环境检查

```bash
# 检查 Node.js 版本
node --version  # >= 18.0.0

# 检查 Java 版本
java --version  # >= 17.0.0

# 检查 Maven/Gradle
mvn --version

# 检查 npm
npm --version

# 检查前端依赖
ls node_modules  # 应存在
```

环境缺失时报告：

```json
{
  "artifact": "BUILD_ARTIFACT",
  "task_id": "{task_id}",
  "status": "BLOCKED",
  "blocker": "ENVIRONMENT_NOT_READY",
  "missing": ["Node.js 18+"],
  "current": ["16.x.x"],
  "deliverable_allowed": false,
  "recommendation": "安装 Node.js 18 LTS"
}
```

---

### Step 2: 依赖安装

```bash
# 前端依赖
cd frontend
npm install

# 后端依赖（Gradle）
cd backend
./gradlew dependencies --configuration implementation
```

---

### Step 3: 代码质量扫描（L3 必须）

```bash
# 前端 ESLint
cd frontend
npm run lint
# 期望：无 error（warning 可接受，L3 必须 0 error）

# 后端 SpotBugs / Checkstyle
cd backend
./gradlew spotbugsMain
# 期望：无 error
```

L3 Lint 失败时直接阻断：

```json
{
  "gate": "LINT_GATE",
  "status": "FAILED",
  "deliverable_allowed": false,
  "errors": [
    {
      "file": "frontend/src/views/UserListPage.vue",
      "line": 15,
      "column": 13,
      "message": "'userList' is defined but never used",
      "severity": "error"
    }
  ],
  "fix_required": "修复所有 Lint 错误后重新运行 build-verify"
}
```

---

### Step 4: 构建执行

#### 前端构建

```bash
cd frontend

# 类型检查
npm run type-check
# 期望：0 errors

# 构建
npm run build
# 输出：dist/
# 期望：构建成功，无 error
```

#### 后端构建

```bash
cd backend

# 编译
./gradlew compileJava compileTestJava
# 期望：BUILD SUCCESSFUL

# 打包
./gradlew bootJar
# 输出：build/libs/*.jar
# 期望：BUILD SUCCESSFUL
```

构建失败时直接阻断：

```json
{
  "gate": "BUILD_GATE",
  "status": "FAILED",
  "deliverable_allowed": false,
  "failed_step": "mvn bootJar",
  "error_log": "...",
  "fix_required": "修复构建错误后重新运行"
}
```

---

### Step 5: 单元测试执行

```bash
cd frontend

# 运行测试
npm run test:unit
# 输出：
#   PASS  tests/unit/userApi.test.ts
#   Test Suites: 15 passed, 15 total
#   Tests:       156 passed, 156 total
```

```bash
cd backend

# 运行测试
./gradlew test
# 输出：
#   UserServiceTest > should_create_user PASSED
#   BUILD SUCCESSFUL
#   156 tests completed, 0 failed
```

测试失败时直接阻断：

```json
{
  "gate": "TEST_GATE",
  "status": "FAILED",
  "deliverable_allowed": false,
  "failed_tests": [
    {
      "suite": "UserServiceTest",
      "test": "should_get_user_not_found",
      "error": "Expected exception NotFoundException"
    }
  ],
  "fix_required": "修复失败的测试用例后重新运行"
}
```

---

### Step 6: 覆盖率检查

```bash
cd frontend

# 覆盖率报告
npm run test:coverage
# 输出：coverage/lcov-report/index.html
#
# 覆盖率：
#   Statements  : 78.5%
#   Branches    : 72.3%
#   Functions   : 85.0%
#   Lines       : 78.5%

threshold = 0.75 if L3 else 0.60
if line_coverage < threshold:
    raise CoverageException(f"覆盖率 {line_coverage} 未达标，需要 ≥ {threshold}")
```

```bash
cd backend

# 覆盖率报告
./gradlew jacocoTestReport
# 输出：build/reports/jacoco/test/html/index.html
```

覆盖率不达标时阻断：

```json
{
  "gate": "COVERAGE_GATE",
  "status": "FAILED",
  "deliverable_allowed": false,
  "thresholds": {"L3": 0.75, "L2": 0.60},
  "actual": {"frontend": 0.72, "backend": 0.68},
  "failed": ["frontend.lines", "backend.lines"],
  "uncovered_files": ["src/utils/helper.ts", "src/service/OrderService.java"],
  "fix_required": "补充单元测试以提升覆盖率至阈值以上"
}
```

---

### Step 7: 安全扫描（L3 必须）

```bash
# 前端：检查已知漏洞
cd frontend
npm audit --audit-level=high

# 后端：OWASP dependency check
cd backend
./gradlew dependencyCheckAnalyze
```

安全扫描失败时阻断：

```json
{
  "gate": "SECURITY_GATE",
  "status": "FAILED",
  "deliverable_allowed": false,
  "vulnerabilities": [
    {"package": "lodash@4.17.20", "cve": "CVE-2021-23337", "severity": "HIGH"}
  ],
  "fix_required": "升级有漏洞的依赖包后重新运行"
}
```

---

### Step 8: 生成 BUILD_APPROVAL_RECORD

所有门禁通过后，生成批准记录：

```json
{
  "artifact": "BUILD_ARTIFACT",
  "name": "{项目名称} 构建产物",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "workflow_mode": "L3",
  "gates": {
    "BUILD_GATE": {"status": "PASSED", "details": "前端 dist/ + 后端 app.jar 产出成功"},
    "TEST_GATE": {"status": "PASSED", "details": "前端 156/156 通过，后端 156/156 通过"},
    "COVERAGE_GATE": {
      "status": "PASSED",
      "frontend_lines": 0.785,
      "backend_lines": 0.812,
      "threshold": 0.75
    },
    "LINT_GATE": {"status": "PASSED", "details": "L3 0 errors"},
    "SECURITY_GATE": {"status": "PASSED", "details": "0 vulnerabilities"}
  },
  "test_summary": {
    "frontend": {"passed": 156, "failed": 0, "coverage_lines": 0.785},
    "backend": {"passed": 156, "failed": 0, "coverage_lines": 0.812}
  },
  "deliverable_allowed": true,
  "artifacts": {
    "frontend_dist": "frontend/dist/",
    "backend_jar": "backend/build/libs/app-{version}.jar"
  },
  "openapi_ref": "OPENAPI@{openapi_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "prev_version": null,
  "next_version": null,
  "timestamp": "{ISO8601}"
}
```

---

### Step 9: 生成构建报告

```markdown
## 构建验证报告

**任务 ID**: {task_id}
**执行时间**: {timestamp}
**工作流模式**: L3
**deliverable_allowed**: ✅ true

### 质量门禁结果

| 门禁 | 状态 | 详情 |
|------|------|------|
| BUILD_GATE | ✅ PASS | 前端 dist/ + 后端 app.jar 产出成功 |
| TEST_GATE | ✅ PASS | 前端 156/156，后端 156/156，0 失败 |
| COVERAGE_GATE | ✅ PASS | 前端 78.5%，后端 81.2% ≥ 75% |
| LINT_GATE | ✅ PASS | L3 0 errors |
| SECURITY_GATE | ✅ PASS | 0 vulnerabilities |

### 测试结果

| 类型 | 通过 | 失败 | 覆盖率 |
|------|------|------|--------|
| 前端单元测试 | 156 | 0 | 78.5% |
| 后端单元测试 | 156 | 0 | 81.2% |
| **合计** | **312** | **0** | **79.8%** |

### 产物清单

- `frontend/dist/` - 前端静态资源
- `backend/build/libs/app-1.0.0.jar` - 后端 JAR

### 结论

**✅ 构建验证通过，质量门禁全部达标**

`deliverable_allowed = true`，可进入 Reviewer + QA 审核环节。
```

---

## 验证步骤

1. [ ] 环境检查通过
2. [ ] 依赖安装成功
3. [ ] Lint 扫描通过（L3 必须 0 error）
4. [ ] 前端构建成功
5. [ ] 后端构建成功
6. [ ] 单元测试全部通过
7. [ ] 覆盖率达标（L2 ≥ 60%，L3 ≥ 75%）
8. [ ] 安全扫描通过（L3 必须，0 vulnerabilities）
9. [ ] `BUILD_APPROVAL_RECORD` 已生成，`deliverable_allowed=true`
10. [ ] 构建报告生成

## 常见陷阱

1. **跳过覆盖率检查**：只管构建不管测试质量
2. **依赖缓存问题**：本地有缓存但 CI 环境没有，导致构建失败
3. **测试写得少**：为了覆盖率凑测试，不验证实际行为
4. **构建产物版本不一致**：前端和后端版本号不统一
5. **跳过安全扫描**：L3 不跑安全扫描就认为代码安全
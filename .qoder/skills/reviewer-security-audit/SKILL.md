---
name: reviewer-security-audit
description: "Reviewer 安全审查 Skill：接收 open-code-review 的 security category findings，对 OCR 未覆盖的漏洞做深度分析（IDOR、JWT、DOM XSS、CSRF）。输出 SECURITY_ASSESSMENT。PM 向 reviewer 分发安全审查任务或用户说'安全审查'时触发。"
version: 3.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, security-audit, vulnerability, ocr, sql-injection, idor, jwt, xss]
    related_skills: [open-code-review, reviewer-code-review, reviewer-arch-consistency]
    artifact_type: SECURITY_ASSESSMENT
    workflow_modes: [L3]
---

# Reviewer Security Audit Skill (OCR 增强版)

## 触发条件

- Reviewer Code Review Skill 调用（内部触发，作为 Step 6 执行）
- PM 向 Reviewer 分发任务时指定安全审查
- 用户说"安全审查"、"安全审计"、"漏洞扫描"

## 输入

- **必需**：源代码变更、OpenAPI 规范
- **可选**：OCR JSON 结果（包含 security category findings）、安全规范文档
- **固定约束**：OWASP Top 10、企业安全规范

## 输出制品

- **SECURITY_ASSESSMENT**：安全风险评估报告
  - OCR 已有 findings 汇总
  - OCR 未覆盖领域深度分析
  - CVSS 评分
  - 攻击场景说明
  - 修复建议

## 执行步骤

### Step 1: 接收 OCR security findings

> 从 `open-code-review` skill 获取 OCR 标记的 security category 漏洞。OCR 已覆盖：SQL 注入、硬编码凭证、不安全反序列化。

```python
# 从 OCR 结果中提取 security findings
ocr_security_findings = [
    f for f in ocr_findings
    if f["category"] == "security"
]

# OCR 覆盖的漏洞类型
ocr_covered = {
    "sql-injection": True,      # ✅ OCR 覆盖
    "hardcoded-credentials": True,  # ✅ OCR 覆盖
    "insecure-deserialization": True,  # ✅ OCR 覆盖
    "xss-reflected": "partial",  # ⚠️ 部分覆盖（主要在后端）
    "xss-stored": "partial",    # ⚠️ 部分覆盖
}
```

### Step 2: OCR 已覆盖漏洞汇总

```markdown
## OCR 已检测的安全漏洞

| 规则 | 数量 | 严重程度 | 状态 |
|------|------|---------|------|
| sql-injection | 2 | HIGH/CRITICAL | 需确认修复 |
| hardcoded-credentials | 1 | HIGH | 需确认修复 |
```

对这些 findings，验证其位置和上下文是否准确，复用 OCR 的建议。

### Step 3: OCR 未覆盖漏洞深度分析

OCR 在以下领域覆盖不足，必须显式检查：

#### 3.1 IDOR（不安全的直接对象引用）

```python
def detect_idor(source_code, openapi_spec):
    """
    OCR 未覆盖 IDOR，需要显式检测
    检查 Controller 是否直接使用传入的 ID 访问资源而无权限验证
    """
    violations = []

    # 识别所有需要认证的接口（从 OpenAPI 提取）
    protected_endpoints = get_protected_endpoints(openapi_spec)

    for file, content in source_code.items():
        if not is_controller(file):
            continue

        # 检查所有接受 path variable 的 GET/PUT/DELETE 方法
        id_pattern = r'@(GetMapping|PostMapping|PutMapping|DeleteMapping)\s*\(\s*"(/[^"]*)/\{([^}]+)\}'

        for match in re.finditer(id_pattern, content):
            http_method = match.group(1)
            path_template = match.group(2)
            id_param = match.group(3)

            # 检查是否有权限验证
            has_owner_check = (
                re.search(rf'{id_param}\.getUserId\(\)', content) or
                re.search(r'@PreAuthorize', content) or
                re.search(r'isOwner\(', content) or
                re.search(r'checkOwnership\(', content)
            )

            if not has_owner_check:
                violations.append({
                    "type": "IDOR",
                    "severity": "HIGH",
                    "cwe": "CWE-639",
                    "file": file,
                    "path": path_template,
                    "id_param": id_param,
                    "message": f"GET {path_template}/{{{id_param}}} 缺少资源所有权检查，可能存在越权访问",
                    "cvss": 7.5,
                    "attack_scenario": f"认证用户 A 可通过修改 {id_param} 参数访问用户 B 的资源"
                })

    return violations
```

**IDOR 漏洞示例**：

```java
// ❌ 危险：没有检查资源所有权
@GetMapping("/orders/{orderId}")
public Order getOrder(@PathVariable String orderId) {
    // 任何登录用户都能访问任意订单
    return orderService.getOrder(orderId);
}

// ✅ 安全：检查资源所有权
@GetMapping("/orders/{orderId}")
public Order getOrder(@PathVariable String orderId, @AuthenticationPrincipal User currentUser) {
    Order order = orderService.getOrder(orderId);
    if (!order.getUserId().equals(currentUser.getId()) && !currentUser.isAdmin()) {
        throw new AccessDeniedException("无权访问该订单");
    }
    return order;
}
```

#### 3.2 JWT 安全配置

```python
def detect_jwt_issues(source_code):
    """
    OCR 未覆盖 JWT 配置问题
    检查：过期时间、签名算法、密钥强度
    """
    violations = []

    jwt_patterns = {
        "no_expiration": r'JWT\.create\(\).*?\.sign\(',  # 创建 JWT 但没有 withExpiresAt
        "none_algorithm": r'Algorithm\.none\(\)',        # 使用 none 算法
        "weak_secret": r'SecretKey[^"]{,20}"[^"]{,20}"', # 密钥可能太弱
    }

    for file, content in source_code.items():
        # 检查 JWT 创建
        if 'JWT.create()' in content or 'jwts.create' in content.lower():
            if 'withExpiresAt' not in content and 'expiration' not in content:
                violations.append({
                    "type": "JWT_NO_EXPIRATION",
                    "severity": "HIGH",
                    "cwe": "CWE-613",
                    "file": file,
                    "cvss": 7.3,
                    "message": "JWT 创建时未设置过期时间，token 泄露后永久有效",
                    "attack_scenario": "攻击者获取有效 JWT 后可永久使用"
                })

            if 'Algorithm.none()' in content or 'Algorithm.none ' in content:
                violations.append({
                    "type": "JWT_ALGORITHM_NONE",
                    "severity": "CRITICAL",
                    "cwe": "CWE-347",
                    "file": file,
                    "cvss": 9.8,
                    "message": "JWT 使用 'none' 算法，攻击者可伪造任意 token",
                    "attack_scenario": "攻击者可将 alg 设为 none 并伪造管理员 token"
                })

    return violations
```

**JWT 漏洞示例**：

```java
// ❌ 危险：JWT 无过期时间
String token = JWT.create()
    .withClaim("userId", userId)
    .sign(Algorithm.none());  // 无签名，无过期

// ✅ 安全：JWT 有签名和过期时间
String token = JWT.create()
    .withClaim("userId", userId)
    .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))  // 1小时过期
    .sign(Algorithm.HMAC256(secretKey));
```

#### 3.3 DOM XSS（OCR 部分覆盖）

```typescript
// OCR 主要覆盖后端 XSS，前端 DOM XSS 需要显式检查

function detect_dom_xss(source_code):
    violations = []

    # 危险模式：innerHTML 拼接用户输入
    dangerous_patterns = [
        r'innerHTML\s*=\s*.*\+\s*',     # innerHTML = userInput +
        r'document\.write\(',            # document.write()
        r'\.html\(.*\)',                 # .html() jQuery
    ]

    for file, content in source_code.items():
        for pattern in dangerous_patterns:
            if re.search(pattern, content):
                # 检查是否有转义
                if not has_sanitization(content):
                    violations.append({
                        "type": "XSS_DOM",
                        "severity": "MEDIUM",
                        "cwe": "CWE-79",
                        "file": file,
                        "cvss": 6.1,
                        "message": "前端代码直接操作 innerHTML，存在 DOM XSS 风险",
                        "attack_scenario": "攻击者通过 URL 参数注入恶意脚本"
                    })

    return violations

// ❌ 危险：直接渲染用户输入
const renderUserInput = (content: string) => {
    document.getElementById('output').innerHTML = content;
}

// ✅ 安全：转义后再渲染
const renderUserInput = (content: string) => {
    const div = document.createElement('div');
    div.textContent = content;  // textContent 自动转义
    document.getElementById('output').appendChild(div);
}

// ✅ 安全：使用安全的 HTML 渲染库
import DOMPurify from 'dompurify';
const SafeComponent = ({ userInput }) => (
    <div>{DOMPurify.sanitize(userInput)}</div>
);
```

#### 3.4 CSRF（跨站请求伪造）

```python
def detect_csrf(source_code):
    """
    OCR 未覆盖 CSRF，检查是否有 CSRF 防护
    """
    violations = []

    for file, content in source_code.items():
        # 检查是否有 CSRF token 验证
        has_csrf_token = (
            'csrf' in content.lower() or
            'CsrfToken' in content or
            '@CsrfToken' in content or
            'csrfToken' in content
        )

        # 检查是否使用 SameSite Cookie
        has_samesite = 'SameSite' in content

        # 检查是否是 stateful API（需 CSRF 防护）
        has_stateful_api = (
            '@PostMapping' in content or
            '@PutMapping' in content or
            '@DeleteMapping' in content
        )

        if has_stateful_api and not has_csrf_token and not has_samesite:
            violations.append({
                "type": "CSRF_MISSING",
                "severity": "MEDIUM",
                "cwe": "CWE-352",
                "file": file,
                "cvss": 6.8,
                "message": "状态变更接口缺少 CSRF 防护",
                "attack_scenario": "攻击者诱导已登录用户点击恶意链接执行非预期操作"
            })

    return violations
```

### Step 4: 安全评估报告生成

```markdown
# 安全评估报告（OCR 增强版）

**任务 ID**: {task_id}
**执行时间**: {timestamp}
**审查范围**：{n} 个文件
**OCR Session**: {session_id}

---

## 漏洞汇总

### OCR 已检测（基础覆盖）

| 规则 | 数量 | 严重程度 | CVSS |
|------|------|---------|------|
| sql-injection | 2 | HIGH/CRITICAL | 9.8 |
| hardcoded-credentials | 1 | HIGH | 8.2 |

### OCR 未覆盖（深度分析新增）

| 漏洞类型 | 数量 | 严重程度 | CVSS |
|---------|------|---------|------|
| IDOR | 2 | HIGH | 7.5 |
| JWT 无过期 | 1 | HIGH | 7.3 |
| DOM XSS | 1 | MEDIUM | 6.1 |
| CSRF | 1 | MEDIUM | 6.8 |

### 总计

| 严重程度 | 数量 | CVSS 范围 |
|---------|------|----------|
| CRITICAL | 1 | 9.8 |
| HIGH | 4 | 7.3-8.2 |
| MEDIUM | 2 | 6.1-6.8 |

**整体风险评分**：8.2（HIGH）

---

## 漏洞详情

### CRITICAL

#### SEC-001：SQL 注入漏洞（来自 OCR）
- **CWE**: CWE-89
- **CVSS**: 9.8
- **文件**: UserServiceImpl.java:42
- **规则**: sql-injection
- **影响**: 攻击者可执行任意 SQL 语句
- **攻击场景**: 通过 keyword 参数注入 `' OR '1'='1` 可绕过认证
- **修复建议**:
  ```java
  @Query("SELECT u FROM User u WHERE u.name LIKE %:keyword%")
  List<User> searchByKeyword(@Param("keyword") String keyword);
  ```

### HIGH

#### SEC-002：IDOR 越权访问（新增）
- **CWE**: CWE-639
- **CVSS**: 7.5
- **文件**: OrderController.java:42
- **影响**: 用户可访问他人订单
- **攻击场景**: 修改 URL 中的 orderId 为他人订单 ID
- **修复建议**: 添加资源所有权检查

#### SEC-003：JWT 无过期时间（新增）
- **CWE**: CWE-613
- **CVSS**: 7.3
- **文件**: AuthService.java:58
- **影响**: Token 泄露后永久有效
- **修复建议**: 添加过期时间

---

## 修复优先级

| 优先级 | 漏洞 | 修复时间 |
|--------|------|---------|
| P0（立即修复） | SEC-001 SQL 注入 | 2h |
| P1（24h 内修复） | SEC-002 IDOR、SEC-003 JWT | 24h |
| P2（72h 内修复） | SEC-004 DOM XSS、SEC-005 CSRF | 72h |

## 结论

**安全评估**：❌ 不通过

发现 1 个 CRITICAL 和 4 个 HIGH 漏洞，必须全部修复后才能合并代码。

**建议**：
1. P0 漏洞（SQL 注入）必须立即修复
2. 修复后重新进行安全审查
3. 建议增加自动化安全扫描（SAST）到 CI 流程
```

## PM 回复模板

### 发现 P0 安全漏洞

```
🚨 P0 安全漏洞发现：{task_id}

漏洞：SQL 注入（SEC-001）
CVSS：9.8（严重）
影响：攻击者可执行任意 SQL

状态：代码冻结，等待 Fullstack 修复

安全评估报告：docs/03开发阶段/{NN}-审查报告/{task_id}_security_assessment.md
```

### 安全审查完成（含 OCR + 深度分析）

```
✅ 安全审查完成：{task_id}
OCR 检测：3 个漏洞
深度分析新增：4 个漏洞

汇总：
  CRITICAL: 1（SQL 注入）
  HIGH: 4（IDOR、JWT、硬编码凭证等）
  MEDIUM: 2（DOM XSS、CSRF）

安全评估：❌ 不通过
  - P0/P1 漏洞必须全部修复后才能合并
  - 建议增加 SAST 到 CI

安全评估报告：docs/03开发阶段/{NN}-审查报告/{task_id}_security_assessment.md
```

## 验证步骤

1. [ ] OCR security findings 已接收并验证
2. [ ] IDOR 检测已执行（OCR 未覆盖）
3. [ ] JWT 安全检测已执行（OCR 未覆盖）
4. [ ] DOM XSS 检测已执行（OCR 部分覆盖）
5. [ ] CSRF 检测已执行（OCR 未覆盖）
6. [ ] 每个漏洞有 CWE 编号和 CVSS 评分
7. [ ] 每个漏洞有攻击场景说明
8. [ ] 每个漏洞有可执行的修复建议

## 常见陷阱

1. **只依赖 OCR security findings**：OCR 覆盖不了 IDOR、JWT、DOM XSS、CSRF
2. **CVSS 评分不客观**：把中危评成低危
3. **修复建议不可执行**：只说"加强安全"不说具体怎么做
4. **忽视前端安全**：只检查后端，忽视前端 DOM XSS

## SECURITY_ASSESSMENT_APPROVAL_RECORD 格式

本 skill 输出的 `SECURITY_ASSESSMENT` 报告，会汇总到 `reviewer-code-review` skill 的 `REVIEW_REPORT_APPROVAL_RECORD`。

```json
{
  "artifact": "SECURITY_ASSESSMENT",
  "name": "{项目名称} 安全评估",
  "version": "v{version}",
  "status": "{PASS / FAIL}",
  "workflow_mode": "L3",
  "vulnerability_summary": {
    "critical": 0,
    "high": 2,
    "medium": 2,
    "low": 1
  },
  "security_gates": {
    "CRITICAL": {"count": 0, "threshold": 0},
    "HIGH": {"count": 2, "threshold": 0}
  },
  "deliverable_allowed": false,
  "reason": "存在 2 个 HIGH 安全漏洞（IDOR、JWT）",
  "source_patch_ref": "SOURCE_PATCH@{hash}"
}
```

+SECURITY_GATE 判定：CRITICAL > 0 或 HIGH > 0 → FAIL，deliverable_allowed=false。
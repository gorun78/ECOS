---
name: artifact-verifier
description: "Commander 制品验证 Skill：验证制品的完整性、artifact_ref 版本一致性、制品类型正确性。当需要验证制品时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [commander, artifact, verification, version-control, integrity]
    related_skills: [compliance-monitor, workflow-auditor]
    workflow_modes: [L1, L2, L3]
---

# Artifact Verifier Skill

## 核心原则

验证所有流转的制品是否符合规范：制品类型正确、版本哈希一致、内容完整、批准记录齐全。

## 验证规则

### 制品类型规范

| 制品类型 | 来源 Profile | 下游使用 | 必需字段 |
|---------|-------------|---------|---------|
| PRD | pm | arch | title, version, hash, status |
| ARCH_SPEC | arch | fullstack | architecture, modules, tech_stack |
| OPENAPI | arch | fullstack | paths, schemas, components |
| DDL | arch | fullstack | tables, indexes, constraints |
| SOURCE_PATCH | fullstack | reviewer, qa | files, diff, build_status |
| REVIEW_REPORT | reviewer | pm | gates, defects, deliverable_allowed |
| TEST_REPORT | qa | pm | pass_rate, test_cases, bugs |

### artifact_ref 格式规范

```
{ARTIFACT_TYPE}@{hash}
```

示例：
- `PRD@abc123`
- `ARCH_SPEC@def456`
- `SOURCE_PATCH@ghi789`

### 版本一致性规则

| 检查项 | 规则 | 违规判定 |
|--------|------|---------|
| 哈希一致性 | artifact_ref 中的 hash 必须与文件内容 hash 一致 | hash_mismatch |
| 版本递增 | 新版本号必须大于旧版本号 | version_not_incremented |
| 批准记录 | 制品必须有 ARTIFACT_APPROVAL_RECORD | missing_approval_record |
| deliverable_allowed | deliverable_allowed 必须为 true | not_deliverable |

## 触发条件

- Commander 收到制品提交事件
- Commander 定期轮询（每 15min）
- 用户说"验证制品"、"检查版本一致性"、"制品完整性检查"

## 输入

- **必需**：制品元数据（artifact_ref、版本号、类型）
- **必需**：文件系统访问权限（通过文件系统 MCP）
- **可选**：批准记录、审计历史

## 输出制品

- **ARTIFACT_VERIFICATION_RESULT**：制品验证结果（artifact_type: ARTIFACT_VERIFICATION_RESULT）
- **VERSION_CONFLICT_NOTIFICATION**：版本冲突通知（发送给 PM）

## 执行步骤

### Step 1: 解析 artifact_ref

```python
def parse_artifact_ref(artifact_ref):
    """
    解析 artifact_ref，提取制品类型和哈希
    """
    parts = artifact_ref.split("@")
    if len(parts) != 2:
        return {
            "valid": False,
            "error": f"artifact_ref 格式错误：{artifact_ref}，应为 TYPE@hash"
        }
    
    return {
        "valid": True,
        "type": parts[0],
        "hash": parts[1],
        "artifact_ref": artifact_ref
    }
```

### Step 2: 验证制品类型

```python
def validate_artifact_type(artifact_type):
    """
    验证制品类型是否合法
    """
    valid_types = ["PRD", "ARCH_SPEC", "OPENAPI", "DDL", "SOURCE_PATCH", "REVIEW_REPORT", "TEST_REPORT"]
    
    if artifact_type not in valid_types:
        return {
            "valid": False,
            "error": f"未知制品类型：{artifact_type}，有效值：{', '.join(valid_types)}"
        }
    
    return {"valid": True}
```

### Step 3: 验证哈希一致性

```python
def validate_hash_consistency(artifact_ref, file_path):
    """
    验证 artifact_ref 中的哈希与文件内容哈希是否一致
    """
    parsed = parse_artifact_ref(artifact_ref)
    if not parsed["valid"]:
        return parsed
    
    # 读取文件内容
    file_content = filesystem.read_file(file_path)
    
    # 计算文件内容哈希
    import hashlib
    actual_hash = hashlib.sha256(file_content.encode()).hexdigest()[:6]  # 取前6位
    
    if actual_hash != parsed["hash"]:
        return {
            "valid": False,
            "error": f"哈希不匹配：artifact_ref 中为 {parsed['hash']}，实际文件内容哈希为 {actual_hash}",
            "type": "hash_mismatch",
            "severity": "P2"
        }
    
    return {"valid": True}
```

### Step 4: 验证批准记录

```python
def validate_approval_record(artifact_ref):
    """
    验证制品是否有批准记录
    """
    parsed = parse_artifact_ref(artifact_ref)
    if not parsed["valid"]:
        return parsed
    
    # 检查批准记录
    approval_record = get_approval_record(parsed["type"], parsed["hash"])
    
    if not approval_record:
        return {
            "valid": False,
            "error": f"制品 {artifact_ref} 无批准记录",
            "type": "missing_approval_record",
            "severity": "P1"
        }
    
    if approval_record["status"] != "APPROVED":
        return {
            "valid": False,
            "error": f"制品状态为 {approval_record['status']}，必须 APPROVED",
            "type": "not_approved",
            "severity": "P1"
        }
    
    if not approval_record.get("deliverable_allowed"):
        return {
            "valid": False,
            "error": "制品 deliverable_allowed=false，禁止分发",
            "type": "not_deliverable",
            "severity": "P1"
        }
    
    return {"valid": True, "approval_record": approval_record}
```

### Step 5: 验证版本递增

```python
def validate_version_increment(artifact_type, current_version, previous_version):
    """
    验证版本号是否正确递增
    """
    if not previous_version:
        return {"valid": True}  # 首次版本，无需检查
    
    # 解析版本号
    def parse_version(v):
        parts = v.lstrip("v").split(".")
        return tuple(map(int, parts))
    
    try:
        current = parse_version(current_version)
        previous = parse_version(previous_version)
        
        if current <= previous:
            return {
                "valid": False,
                "error": f"版本号未递增：当前版本 {current_version} <= 上一版本 {previous_version}",
                "type": "version_not_incremented",
                "severity": "P2"
            }
        
        return {"valid": True}
    except Exception as e:
        return {
            "valid": False,
            "error": f"版本号解析失败：{e}",
            "type": "version_parse_error",
            "severity": "P3"
        }
```

### Step 6: 汇总验证结果

```python
def generate_verification_result(artifact_ref, results):
    """
    生成制品验证结果
    """
    all_valid = all(r["valid"] for r in results)
    
    issues = [r for r in results if not r["valid"]]
    
    result = {
        "artifact_ref": artifact_ref,
        "audit_time": get_current_time(),
        "valid": all_valid,
        "issues": issues,
        "issue_summary": {
            "P0": len([i for i in issues if i.get("severity") == "P0"]),
            "P1": len([i for i in issues if i.get("severity") == "P1"]),
            "P2": len([i for i in issues if i.get("severity") == "P2"]),
            "P3": len([i for i in issues if i.get("severity") == "P3"])
        }
    }
    
    # 如果有问题，通知 PM
    if len(issues) > 0:
        notify_pm(result)
    
    return result
```

## 输出格式

### 制品验证结果

```markdown
# 制品验证结果 - {artifact_ref}

## 基本信息
- 制品引用：{artifact_ref}
- 制品类型：{PRD/ARCH_SPEC/OPENAPI/DDL/SOURCE_PATCH/REVIEW_REPORT/TEST_REPORT}
- 验证时间：{timestamp}
- 验证状态：{VALID/INVALID}

## 验证项

| 验证项 | 状态 | 说明 |
|--------|------|------|
| artifact_ref 格式 | ✅/❌ | 正确/错误 |
| 制品类型 | ✅/❌ | 合法/未知 |
| 哈希一致性 | ✅/❌ | 一致/不一致 |
| 批准记录 | ✅/❌ | 存在/缺失 |
| deliverable_allowed | ✅/❌ | true/false |
| 版本递增 | ✅/❌ | 递增/未递增 |

## 发现的问题

### P0 问题（必须修复）
- [问题描述]

### P1 问题（应该修复）
- [问题描述]

### P2/P3 问题（建议优化）
- [问题描述]

## 结论

- 验证状态：{VALID/INVALID}
- 建议：{可分发/需修复后分发}
```

### 版本冲突通知

```
【版本冲突通知】
制品引用：{artifact_ref}
冲突类型：[hash_mismatch | missing_approval_record | version_not_incremented]
冲突描述：[具体冲突描述]
影响范围：[影响范围说明]
建议修正：[建议的修正动作]
紧急程度：[P0/P1/P2/P3]
时间：[发现时间]
```

## 验证步骤

1. [ ] artifact_ref 格式解析正确
2. [ ] 制品类型验证覆盖所有合法类型
3. [ ] 哈希一致性检查正确
4. [ ] 批准记录检查正确（必须 APPROVED）
5. [ ] deliverable_allowed 检查正确（必须 true）
6. [ ] 版本递增检查正确
7. [ ] 发现问题时正确通知 PM
8. [ ] 输出包含 issue_summary 统计

## 常见陷阱

1. **哈希计算方式不一致**：不同 Profile 使用不同的哈希算法
2. **版本号格式不统一**：有的用 v1.0，有的用 1.0
3. **批准记录检查不完整**：只检查是否存在，不检查 status 和 deliverable_allowed
4. **文件路径错误**：无法读取文件导致验证失败
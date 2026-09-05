# Vue3 门禁判定函数

## 核心判定函数

### 门禁结果类型

```typescript
interface GateResult {
  can_proceed: boolean
  blocking_issues: Issue[]
  warnings: Issue[]
}

interface Issue {
  type: 'critical' | 'high' | 'medium' | 'low'
  code: string
  message: string
  location?: string
}
```

### 组件级门禁判定

```typescript
/**
 * 组件级 QA 门禁判定
 * @param qaResult QA 检查结果
 * @returns 门禁结果
 */
function evaluateComponentGate(qaResult: QAResult): GateResult {
  const issues: Issue[] = []

  // Critical: 编译失败
  if (qaResult.typescript.status === 'fail') {
    issues.push({
      type: 'critical',
      code: 'TS001',
      message: 'TypeScript 编译失败',
      location: qaResult.typescript.errors?.[0]?.file
    })
  }

  // Critical: ESLint 错误
  if (qaResult.eslint.status === 'fail') {
    issues.push({
      type: 'critical',
      code: 'ES001',
      message: 'ESLint 检查未通过'
    })
  }

  // High: 视觉还原度不达标
  if (qaResult.design_comparison.p0 !== 'pass') {
    issues.push({
      type: 'high',
      code: 'VIS001',
      message: `视觉还原度 P0 未通过 (delta: ${qaResult.design_comparison.metrics?.diff_percentage}%)`
    })
  }

  return {
    can_proceed: issues.filter(i => i.type === 'critical').length === 0,
    blocking_issues: issues.filter(i => i.type === 'critical'),
    warnings: issues.filter(i => i.type !== 'critical')
  }
}
```

### 审批状态判定

```typescript
/**
 * 组件审批状态判定
 * @param approval 审批记录
 * @returns 是否允许交付
 */
function canDeliver(approval: ComponentApproval): boolean {
  return (
    approval.status === 'approved' &&
    approval.code_approval.typescript_check === 'pass' &&
    approval.code_approval.eslint_check === 'pass' &&
    approval.render_approval.visual_match === 'pass' &&
    approval.deliverable_allowed === true
  )
}
```

### 第四阶段交付门禁

```typescript
/**
 * 第四阶段交付门禁判定
 * @param deliveryCheck 交付检查清单
 * @returns 门禁结果
 */
function evaluateDeliveryGate(deliveryCheck: DeliveryCheck): GateResult {
  const issues: Issue[] = []

  // 所有组件必须已批准
  const unapproved = deliveryCheck.components.filter(c => c.status !== 'approved')
  if (unapproved.length > 0) {
    issues.push({
      type: 'critical',
      code: 'DLV001',
      message: `${unapproved.length} 个组件未批准`,
      location: unapproved.map(c => c.name).join(', ')
    })
  }

  // 关键 issues 必须为 0
  if (deliveryCheck.critical_issues > 0) {
    issues.push({
      type: 'critical',
      code: 'DLV002',
      message: `存在 ${deliveryCheck.critical_issues} 个关键问题`
    })
  }

  // 响应式验证
  if (!deliveryCheck.responsive_verified) {
    issues.push({
      type: 'high',
      code: 'DLV003',
      message: '响应式未完成验证'
    })
  }

  return {
    can_proceed: issues.filter(i => i.type === 'critical').length === 0,
    blocking_issues: issues.filter(i => i.type === 'critical'),
    warnings: issues.filter(i => i.type !== 'critical')
  }
}
```

## 简化判定规则表

| 门禁 | 通过条件 | 阻断级别 |
|------|----------|----------|
| TS 检查 | status = pass | Critical |
| ESLint | status = pass | Critical |
| 单元测试 | status = pass | Medium |
| 渲染截图 | screenshots 存在 | Critical |
| 视觉还原 P0 | p0 = pass | High |
| 审批状态 | status = approved | Critical |
| 交付许可 | deliverable_allowed = true | Critical |
| 关键问题数 | = 0 | Critical |

## 快速判定清单

```
组件进入下一阶段的条件：
├── TypeScript 编译 ✅
├── ESLint 检查 ✅
├── 单元测试 ✅
├── 渲染截图存在 ✅
└── QA 门禁 can_proceed = true ✅

组件进入交付包的条件：
├── approval.status = approved ✅
├── deliverable_allowed = true ✅
├── 无 Critical/High issues ✅
└── 响应式已验证 ✅
```

---
*本文档提取门禁判定逻辑为可复用函数*
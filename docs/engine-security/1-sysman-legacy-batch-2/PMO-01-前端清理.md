# PMO指令：Phase1-sysman-01 — 前端清理

> **完善计划**: T0 | **工期**: 1天 | **范围**: 前端只删不改逻辑

---

## §禁止清单

1. ❌ 不改任何后端Java文件
2. ❌ 不改SecurityCenter.tsx内部逻辑（T4才拆分）
3. ❌ 不改TenantManager.tsx（保留文件）
4. ❌ TenantManager.tsx和SecurityCenter.tsx的其他引用如果报TS错误，用`// @ts-ignore`临时跳过，不删import

---

## §Task

### 删除重复文件

| 文件 | 操作 |
|------|------|
| `ecos_frontend/src/pages/business-workbench/SecurityCenterView.tsx` | 删除 |
| `ecos_frontend/src/pages/security-center/SecurityCenter.tsx` | 删除 |

### 去除租户管理入口

**文件**: `ecos_frontend/src/App.tsx`

找到 `navLabels` 对象，删除 `tenants: "租户管理"` 这一行。

### 侧边栏确认

确认侧边栏仅保留：安全中心、数据工作台、本体工作台、知识工作台、AI工作台。当前"用户管理""系统配置""安全审计""租户管理"四个独立入口先不动——T4统一收口。

> **本次不改侧边栏**——只删文件+去掉租管label。侧边栏调整放T4。

---

## §验收

```bash
# V1: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS" && echo "HAS ERRORS" || echo "TS CLEAN"

# V2: 文件已删
[ ! -f /home/guorongxiao/ECOS/ecos_frontend/src/pages/business-workbench/SecurityCenterView.tsx ] && echo "DELETED OK" || echo "STILL EXISTS"
[ ! -f /home/guorongxiao/ECOS/ecos_frontend/src/pages/security-center/SecurityCenter.tsx ] && echo "DELETED OK" || echo "STILL EXISTS"

# V3: 租管label已删
grep "tenants.*租户" /home/guorongxiao/ECOS/ecos_frontend/src/App.tsx && echo "NOT REMOVED" || echo "REMOVED OK"
```

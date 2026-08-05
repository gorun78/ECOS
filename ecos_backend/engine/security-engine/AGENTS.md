# security-engine — 安全引擎

> 端口: **18081** | PMO: **ecos-be** | 依赖: PostgreSQL

## 我负责的
- 认证（JWT签发/验证/刷新）
- 授权（RBAC/ABAC/PBAC）
- 审计日志（操作审计、数据访问审计）
- 数据脱敏（手机/邮箱/身份证/金额）
- 数据权限（行级/列级）
- 加密服务（密钥管理）

## 我暴露的端点

### Phase 1 当前可用
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/security/mask` | POST | 数据脱敏（SHA256/PHONE/EMAIL/ID_CARD/AMOUNT） |
| `/api/security/evaluate-filter` | POST | 安全过滤规则评估 |
| `/api/security/decrypt` | POST | 解密仿真 |
| `/api/security/audit-logs` | GET | 审计日志查询 |

### Phase 2+ 规划
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/security/policy/evaluate` | POST | ABAC策略评估 |
| `/api/v1/security/audit/log` | POST | 写审计日志 |
| `/api/v1/security/rls/apply` | POST | 行级安全 |
| `/api/v1/security/cls/columns` | POST | 列级安全 |

> 完整接入规则: `docs/1-sysman/03-安全接入规则.md`

## 我的数据库表
- 用户表、角色表、权限表、审计日志表、脱敏规则表、ABAC策略表

## 我依赖的外部端点
无。security-engine是底层引擎。

## 禁止
1. 不存储明文密码
2. 不硬编码密钥
3. 审计日志不可物理删除（只标记archived）

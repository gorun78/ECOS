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
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/auth/login | POST | 登录（实际在Gateway，security-engine提供验证逻辑） |
| /api/v1/security/users | * | 用户CRUD |
| /api/v1/security/roles | * | 角色CRUD |
| /api/v1/security/permissions | * | 权限CRUD |
| /api/v1/audit/logs | GET | 审计日志查询 |
| /api/v1/abac/policies | * | ABAC策略管理 |
| /api/v1/data-masking/rules | * | 脱敏规则管理 |
| /api/v1/data-permission/rules | * | 数据权限规则 |
| /api/v1/policy-engine/evaluate | POST | 策略评估 |

## 我的数据库表
- 用户表、角色表、权限表、审计日志表、脱敏规则表、ABAC策略表

## 我依赖的外部端点
无。security-engine是底层引擎。

## 禁止
1. 不存储明文密码
2. 不硬编码密钥
3. 审计日志不可物理删除（只标记archived）

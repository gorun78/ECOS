# PMO-A+2: 审计并修复全系统 Bean 配置冲突

> **架构铁律**: 必须遵循 [ECOS架构规则](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-BE / ECOS-QA
> **前置**: PMO-A+1 已发现 2 个预存冲突

## § 背景

A+1 执行迁移尝试时，Gateway 启动发现 2 个预存 Bean 冲突：
1. `AbacPolicyDaoImpl` — security-engine-impl 源码与 runtime-crypto JAR 同名类
2. `CognitiveEngineHealthController` vs `CognitiveController` — `/api/v1/cognitive/health` 双重映射

A+2 需彻底审计全系统同类问题，避免后续迁移踩坑。

## T1: 审计 Gateway 所有 excludeFilter（已知冲突）

**目标**: 检查 GatewayApplication 当前 excludeFilters 是否完整，补充缺失冲突

检查项：
- [ ] security-engine-impl 的 `abac/dao/impl/AbacPolicyDaoImpl` 是否已在 excludeFilter
- [ ] `cognitive2/CognitiveEngineHealthController` 是否已在 excludeFilter
- [ ] 有无其他模块存在同名类问题（搜索 security-engine-impl 目录中所有与 runtime-crypto JAR 同名的类）

## T2: 搜索其他潜在双重类名冲突

**目标**: 全局搜索同接口被多个模块提供（跨模块同名 Bean）

搜索策略：
1. 统计 gateway classpath 上所有 `@Component`/`@Service`/`@Repository` Bean
2. 找同名（简单类名）但不同全限定名的 Bean
3. 特别关注：engine/ 目录下的 dao/impl 与 runtime/ 目录下同名类

```bash
# 在 security-engine-impl 目录搜索与 runtime-crypto JAR 同源的类
find engine/security-engine/security-engine-impl/src -name '*Dao*.java' -o -name '*Service*.java' | xargs grep -l '@Component\|@Service\|@Repository'
```

## T3: 统一端点映射冲突

**目标**: 解决 `/api/v1/cognitive/health` 双重映射

检查项：
- cognitive2 引擎的 `CognitiveEngineHealthController` 是否还在使用
- 如果 cognitive/CognitiveController 的 `/health` 端点已够用，exclude cognitive2 版本
- 如果两者都有用，合并到一处

## T4: 全量编译 + Gateway 启动验证

**目标**: T1-T3 修复后，确保 BUILD SUCCESS + Gateway 启动无冲突

```bash
cd ecos_backend
unset HOME && export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
mvn install -DskipTests -Dmaven.test.skip=true
bash ~/start-gateway.sh  # 验证启动成功
```

## § 验收标准

- Gateway 启动日志无 `ConflictingBeanDefinitionException`
- Gateway 启动日志无 `Ambiguous mapping` 警告
- 所有 `/health` 端点最多只有一个模块响应（无重复映射）
- `mvn install` BUILD SUCCESS

## § 交付物

- Commit: `PMO-A+2: Audit and fix Bean conflicts`
- 更新 `ecos_backend/gateway/src/main/java/.../GatewayApplication.java`
- 更新 docs（如有新增冲突点记录）

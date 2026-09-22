# PMO-A+1: 迁安全 → security-engine（护）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①security-engine（护）是安全能力的唯一权威，runtime-security/runtime-crypto 是历史遗留 ②纯 package 移动 + import 改写，不改方法体 ③每 Task 独立 commit ④迁完 `runtime.security`/`runtime.crypto` 两模块从 pom 移除

## §背景

runtime-security（37 文件）+ runtime-crypto（19 文件）= 56 文件，是安全能力的"历史大本营"，但它们的 package 还挂在 `runtime.core.*` 下（历史遗留）。按「护」定位，安全能力应归 security-engine。

已核实：**外部消费方仅 1 处**（`sysman/SysManRuntimeConfig.java`），其余是 runtime-core 内部残留。这是纯搬家，风险低。

## §迁移清单（56 文件 → security-engine）

**runtime-security 37 文件**（package `runtime.core.*` → `engine.security.*`）：

| 源 package 段 | 目标 package 段 | 文件数 |
|------|------|-----:|
| `runtime.core.security.abac.*` | `engine.security.abac.*` | 5 |
| `runtime.core.security.policy.*` | `engine.security.policy.*` | 10 |
| `runtime.core.compliance.*` | `engine.security.compliance.*` | 9 |
| `runtime.core.crypto.kms.*` | `engine.security.crypto.kms.*` | 6 |
| `runtime.core.datapermission.*` | `engine.security.datapermission.*` | 7 |

**runtime-crypto 19 文件**（package `runtime.core.crypto.*` → `engine.security.crypto.*`）：
- IDataEncryptionService / IKeyManagementService / KeyManagementService / KeyManagementServiceImpl / KeyManagementServiceFullImpl / DataEncryptionServiceImpl / EncryptionUtils / Encrypted / EncryptedFieldProcessor / KeyMetadata / CryptoKeyDao / CryptoKeyAuditDao / SecretDao / SecretDaoImpl / Secret / SecretAccessLog / SecretShare / ISecretService / SecretServiceImpl

> 注：runtime-security 里的 `crypto.kms` 子包与 runtime-crypto 的 `crypto` 主包合并为 `engine.security.crypto.*`。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 迁 runtime-crypto 19 文件到 `engine/security-engine/security-engine-impl/.../engine/security/crypto/`，改 package 声明 | `mvn install -DskipTests` 通过 |
| T2 | 迁 runtime-security 37 文件到 `engine/security-engine/.../engine/security/`（abac/policy/compliance/crypto.kms/datapermission 五个子包），改 package 声明 | 同上 |
| T3 | 改写消费方 import：`sysman/SysManRuntimeConfig.java` + `runtime-core/.../security/spi/PolicyDecisionPoint.java` + 迁入类之间的内部 import | 同上 |
| T4 | `runtime/pom.xml` 移除 `runtime-security`、`runtime-crypto` 两个 module | `mvn validate` 通过 |

## §禁止清单

1. ❌ 不改任何方法体、SQL、业务逻辑——纯 package + import 移动
2. ❌ 不碰 security-engine 已有的 Controller/Service（SecurityController 等已有 14 个 Controller 保留）
3. ❌ 不删 runtime-security/runtime-crypto 目录（软删除，先移 module；目录物理删除等全量验证后）
4. ❌ 不用 `mvn compile` 替代 `mvn install`（.m2 旧 JAR 不覆盖）

## §验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: 活跃模块不再 import runtime.core.security/compliance/crypto/datapermission（runtime 目录死代码除外）
grep -rln "import com.chinacreator.gzcm.runtime.core.\(security\|compliance\|crypto\|datapermission\)" \
  /home/guorongxiao/ECOS/ecos_backend --include="*.java" | grep -v target | grep -v "/runtime/"
# 期望: 0 匹配

# V3: security-engine 编译通过，迁入类存在
ls engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/crypto/KeyManagementService.java
```

## §工时

2 天（56 文件 package 改写 + 内部 import 反复编译调错）。

## §风险

- **package 前缀统一**：security-engine 现有 package 是 `engine.security.controller`，迁入后 Service 归 `engine.security.service`/`engine.security.*`，注意与现有 controller 的 package 层级对齐。
- **runtime-core 内部残留**：`runtime-core/.../security/spi/PolicyDecisionPoint.java` 是 runtime-core 对 security 的 SPI 引用点，迁走 security 后它可能变成死代码或需改指向 engine.security——T3 要处理，判断标准：若它定义的是 SPI 接口（引擎层依赖）则迁 engine.security.spi，若是 runtime-core 独有则保留并改 import。
- **@MapperScan 连带**：gateway 的 `@MapperScan` 有 `com.chinacreator.gzcm.runtime.**.mapper`，迁走 security/crypto 后若它们的 DAO 用 MyBatis Mapper，需确认新 package 仍在 MapperScan 范围（`engine.security` 若不在，需在 gateway @MapperScan 补）。

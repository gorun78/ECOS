# PMO-R1: 安全迁移返工（让 engine.security 副本转正）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **返工原因**: 上一轮 A+1 只做了"复制"没做"迁移"——类复制到 engine.security（package 已改对，dao 加 Security 前缀），但原类没删、pom module 没移除、消费方 import 没改。结果 engine.security 60 类零消费方 = 死代码，runtime-crypto/runtime-security 还在构建里。

## §迁移三动作铁律（本指令核心，违反即返工失败）

**迁移 = 移动，不是复制。** 三个动作缺一不可：

1. **删原类**（`git rm -r` 原目录，不是 `cp` 后留副本）
2. **改消费方 import**（活跃模块不再 import 旧 package）
3. **移除 pom module**（原 module 退出构建）

**硬门禁（三条任一不满足 = FAIL）**：
```bash
# 门禁1: 活跃模块不再 import 旧 security/crypto package
grep -rln "runtime.core.\(crypto\|security\|compliance\|datapermission\)" \
  --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/" \
  | grep -v "runtime.core.database"
# 期望: 0 匹配（database 是合法保留包，必须排除）

# 门禁2: pom 无旧 module
grep -n "runtime-security\|runtime-crypto" runtime/pom.xml
# 期望: 0 匹配

# 门禁3: 全量编译
# BUILD SUCCESS
```

## §现状（已核实，勿重复勘察）

- **engine.security 副本已存在且可用**：60 类，package 全改对（`engine.security.*`），内部只依赖 `runtime.core.database.ISystemDatabaseAccess`（合法保留包），dao 已改名 `SecurityCryptoKeyDao` 等避免冲突。**不要再复制这些类。**
- 原类仍保留：`runtime-crypto`（19 类）、`runtime-security`（37 类）、`runtime-core/security` 顶层（4 类死代码）。
- `runtime/pom.xml` 第 21/23 行仍有 `runtime-security`、`runtime-crypto` 两个 module。
- 唯一活跃消费方：`sysman/.../config/SysManRuntimeConfig.java` 的 8 个 crypto import。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 改 `SysManRuntimeConfig.java` 8 个 crypto import：`runtime.core.crypto` → `engine.security.crypto`（纯前缀替换，类名不变，见下清单） | 编译通过 |
| T2 | `git rm -r runtime/runtime-crypto`（19 类，副本已在 engine.security） | 目录不存在 |
| T3 | `git rm -r runtime/runtime-security`（37 类，副本已在 engine.security） | 目录不存在 |
| T4 | `git rm` runtime-core/security 顶层 4 死代码（PolicyEnforcementPoint/ColumnPermission/RowFilter/spi/PolicyDecisionPoint，0 外部引用已核实） | 文件不存在 |
| T5 | `runtime/pom.xml` 移除 `runtime-security`、`runtime-crypto` 两个 `<module>` | `mvn validate` 通过 |
| T6 | 全量编译 + 三版本 profile validate + 跑硬门禁 | BUILD SUCCESS + 门禁全绿 |

### T1 import 替换清单（8 行，纯前缀替换）

```
runtime.core.crypto.KeyManagementService                    → engine.security.crypto.KeyManagementService
runtime.core.crypto.service.impl.KeyManagementServiceFullImpl → engine.security.crypto.service.impl.KeyManagementServiceFullImpl
runtime.core.crypto.IKeyManagementService                   → engine.security.crypto.IKeyManagementService
runtime.core.crypto.service.impl.KeyManagementServiceImpl   → engine.security.crypto.service.impl.KeyManagementServiceImpl
runtime.core.crypto.IDataEncryptionService                  → engine.security.crypto.IDataEncryptionService
runtime.core.crypto.impl.DataEncryptionServiceImpl          → engine.security.crypto.impl.DataEncryptionServiceImpl
runtime.core.crypto.service.ISecretService                  → engine.security.crypto.service.ISecretService
runtime.core.crypto.service.impl.SecretServiceImpl          → engine.security.crypto.service.impl.SecretServiceImpl
```

> 执行时用 `grep -rln "runtime.core.crypto" --include="*.java" | grep -v target | grep -v "/runtime/"` 兜底，确认除 SysManRuntimeConfig 外无其他活跃消费方。

## §禁止清单

1. ❌ **禁止复制**——engine.security 副本已存在，不要再 cp/mv 任何类到 engine.security
2. ❌ 不改 engine.security 里任何类的方法体/SQL/逻辑（副本已可用，零改动转正）
3. ❌ 不碰 `runtime.core.database` 包（合法保留，被 sysman 15 个 dao + engine.security 5 个 dao 依赖）
4. ❌ 不删 runtime-security/runtime-crypto 的 target 残留（git rm 只删 git 跟踪的源码，target 由 gitignore 管，可 `rm -rf` 顺手清）
5. ❌ 不用 `mvn compile` 替代 `mvn install`（.m2 旧 JAR 不覆盖）

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS
```

三版本 profile validate：`mvn validate -Pstandard -q && mvn validate -Penterprise -q && mvn validate -Pultimate -q`

## §工时

1 天（改 1 消费方 8 import + 删 2 目录 60 类 + 删 4 死代码 + 移除 module + 编译）。

## §风险

- **.m2 旧 JAR**：删 module 后全量 install，若仍报 runtime-crypto/runtime-security 旧类，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-crypto*` 和 `runtime-security*`。
- **@MapperScan 连带**：runtime-security 里有 dao（AbacPolicyDaoImpl 等）用 MyBatis @Mapper，若 gateway @MapperScan 扫 `runtime.**.mapper`，删后需确认 engine.security 的 dao 已在扫描范围（grep gateway @MapperScan 确认）。
- **ConfigDao 冲突是 pre-existing**：与本指令无关，不要在本指令里尝试解决（那是 A+2 审计遗留，待 ARCH 决策）。

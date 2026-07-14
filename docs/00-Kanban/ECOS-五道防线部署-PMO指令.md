# PMO指令: 5道防线部署（细修防崩塌）

> **来源**: 肖国荣 架构保卫方案 | **日期**: 2026-06-29  
> **前置**: 三套发布脚本已交付（standard/enterprise/flagship docker-compose + Maven三profile）  
> **铁律**: 防线不通过→禁止提交。代码改动必须可回滚。不产出解释性文档。

## 背景

三套发布脚本到位后，ECOS进入"细修阶段"——修Bug、补交互、优化SQL。此阶段最大风险不是新功能错误，是**顺手重构**导致的架构崩塌。

5道防线 = 5层自动检查，让架构崩塌在发生前被阻止。

---

## 防线1: ArchUnit 跨模块架构测试（P0，2天）

### 1.1 新建全ECOS架构测试类

**文件**: `databridge-common/src/test/java/com/chinacreator/gzcm/common/ArchitectureGuardTest.java`

**内容**:

```java
package com.chinacreator.gzcm.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;

public class ArchitectureGuardTest {
    private static JavaClasses allClasses;

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinacreator.gzcm");
    }

    // ── 层次违规 ──────────────────────────────

    @Test
    void workspace层不得依赖buszhi层() {
        noClasses().that().resideInAPackage("..workspace..")
            .should().dependOnClassesThat()
            .resideInAPackage("..buszhi..")
            .check(allClasses);
    }

    @Test
    void workspace层不得依赖worldmodel层() {
        noClasses().that().resideInAPackage("..workspace..")
            .should().dependOnClassesThat()
            .resideInAPackage("..worldmodel..")
            .check(allClasses);
    }

    @Test
    void common层不得依赖任何业务模块() {
        noClasses().that().resideInAPackage("..common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..buszhi..", "..dccheng..", "..datanet..",
                "..workspace..", "..worldmodel..", "..aimod..",
                "..portal..", "..market..", "..cognitive..",
                "..gateway..", "..sysman.."
            )
            .check(allClasses);
    }

    @Test
    void 禁止下層依賴上層() {
        // datanet(D) 不得依赖 buszhi(K)
        noClasses().that().resideInAPackage("..datanet..")
            .should().dependOnClassesThat()
            .resideInAPackage("..buszhi..")
            .check(allClasses);
    }

    // ── Controller规范 ────────────────────────

    @Test
    void Controller不得new_JdbcTemplate() {
        noClasses().that().resideInAPackage("..controller..")
            .and().areNotAnonymousClasses()
            .should().accessField(JdbcTemplate.class)
            .check(allClasses);
        // JdbcTemplate必须通过构造器注入，不得字段直接new
    }

    @Test
    void Controller返回类型必须是ApiResponse() {
        classes().that().resideInAPackage("..controller..")
            .and().arePublic()
            .and().haveSimpleNameEndingWith("Controller")
            .should()  // 不强校验每个方法，但检查类存在
            .resideInAPackage("..controller..");
        // TODO: 逐步强化到方法级检查
    }

    // ── 模块物理边界 ───────────────────────────

    @Test
    void 标准版不得引用Doris() {
        // 任何模块不得直接import org.apache.doris
        noClasses().that().resideOutsideOfPackage("..olap..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.apache.doris..")
            .check(allClasses);
    }

    @Test
    void 标准版不得引用Neo4j_Driver() {
        // 任何模块不得直接import org.neo4j.driver.*
        noClasses().that().resideOutsideOfPackage("..graph..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.neo4j.driver..")
            .check(allClasses);
    }

    // ── 模块间无循环依赖 ───────────────────────

    @Test
    void 模块之间不得存在循环依赖() {
        slices().matching("com.chinacreator.gzcm.(*)..")
            .should().beFreeOfCycles()
            .check(allClasses);
    }

    // ── 禁止清单 ───────────────────────────────

    @Test
    void 禁止直接使用System_out_println() {
        noClasses().that().resideInAPackage("com.chinacreator.gzcm..")
            .should().callMethod(System.class, "out")
            .check(allClasses);
    }

    @Test
    void 禁止在Controller外使用HttpServletRequest() {
        noClasses().that().resideOutsideOfPackages("..controller..", "..filter..", "..security..")
            .should().dependOnClassesThat()
            .areAssignableTo(jakarta.servlet.http.HttpServletRequest.class)
            .check(allClasses);
    }
}
```

### 1.2 补ArchUnit依赖

**文件**: `databridge-common/pom.xml`

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

### 1.3 验收

```bash
cd /home/guorongxiao/databridge-v2
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
/home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn test -pl databridge-common -Dtest=ArchitectureGuardTest
# 期望: Tests run: 10, Failures: 0
# 如有失败: 修复代码或论证放宽规则
```

---

## 防线2: API契约测试（P0，1.5天）

### 2.1 契约文件

**文件**: `/home/guorongxiao/ECOS/05-质量保障/api-contracts.txt`

**内容**: 从已验证的36个端点提取

```text
# ECOS API 契约基线 — 2026-06-29
# 格式: METHOD|PATH|EXPECTED_CODE|OPTIONAL_JQ_ASSERTION
# 每个commit后自动跑，任何FAIL必须立即修复

# Auth
POST|/api/v1/auth/login|200|.data.accessToken != null

# BizDashboard
GET|/api/v1/ecos/biz/dashboard|200|.data.departments != null

# WorldModel
GET|/api/v1/worldmodel/goals|200|
GET|/api/v1/worldmodel/causal-links|200|

# DQ
GET|/api/v1/dq/rules|200|
POST|/api/v1/dq/execute-all|200|

# Pipeline
GET|/api/v1/pipeline/definitions|200|

# ObjectQL
POST|/api/query/objectql|200|.code == 0

# Tenant
GET|/api/v1/system/tenants|200|.code == 0
POST|/api/v1/system/tenants|201|
GET|/api/v1/system/tenants/{id}|200|
DELETE|/api/v1/system/tenants/{id}|200|

# Cognitive
GET|/api/v1/cognitive/health|200|

# Task
GET|/api/v1/task/list|200|
POST|/api/v1/task/submit|200|

# Object
POST|/api/v1/ecos/objects/project|200|
GET|/api/v1/ecos/objects/project/{id}|200|

# Agent
GET|/api/v1/agents|200|

# Health
GET|/api/health|200|.data.status == "UP"
GET|/actuator/health|200|

# System Config
GET|/api/v1/system/config|200|

# IAM
GET|/api/v1/system/users|200|
GET|/api/v1/system/roles|200|
GET|/api/v1/system/organizations|200|

# Security
GET|/api/v1/system/security/config|200|
```

### 2.2 执行脚本

**文件**: `/home/guorongxiao/ECOS/05-质量保障/contract-tests.sh`

```bash
#!/bin/bash
set -e
CONTRACTS="/home/guorongxiao/ECOS/05-质量保障/api-contracts.txt"
BASE="http://localhost:8080"
TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@123"}' | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

PASS=0; FAIL=0

while IFS='|' read -r METHOD PATH EXPECTED JQ; do
    [ -z "$METHOD" ] && continue
    [[ "$PATH" == \#* ]] && continue
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X "$METHOD" "$BASE$PATH" \
        -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" 2>/dev/null)
    if [ "$HTTP" = "$EXPECTED" ]; then
        echo "✅ $METHOD $PATH → $HTTP"
        PASS=$((PASS+1))
    else
        echo "❌ $METHOD $PATH → $HTTP (expected $EXPECTED)"
        FAIL=$((FAIL+1))
    fi
done < "$CONTRACTS"

echo "================================"
echo "结果: $PASS PASS, $FAIL FAIL"
[ $FAIL -gt 0 ] && exit 1
exit 0
```

### 2.3 验收

```bash
bash /home/guorongxiao/ECOS/05-质量保障/contract-tests.sh
# 期望: 全部PASS, exit 0
```

---

## 防线3: Maven Enforcer 依赖矩阵（P1，1天）

### 3.1 根POM追加

**文件**: `pom.xml`（`<build><plugins>`内）

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.4.1</version>
    <executions>
        <execution>
            <id>enforce-banned-deps</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <!-- 禁止跨层依赖 -->
                    <bannedDependencies>
                        <excludes>
                            <!-- workspace(I) 不得依赖 buszhi(K) -->
                            <exclude>com.chinacreator.gzcm:buszhi-impl:*:*:*</exclude>
                            <!-- common 不得依赖任何业务jar -->
                            <exclude>com.chinacreator.gzcm:workspace-impl:*:*:*</exclude>
                            <exclude>com.chinacreator.gzcm:worldmodel-impl:*:*:*</exclude>
                            <exclude>com.chinacreator.gzcm:cognitive-impl:*:*:*</exclude>
                            <exclude>com.chinacreator.gzcm:aimod-impl:*:*:*</exclude>
                            <!-- 标准版不得含olap依赖 -->
                            <!-- (profile级规则，compile时由profile排除) -->
                        </excludes>
                    </bannedDependencies>
                    <!-- 要求所有模块声明自己的依赖 -->
                    <requireJavaVersion>
                        <version>[17,)</version>
                    </requireJavaVersion>
                    <requireMavenVersion>
                        <version>[3.9,)</version>
                    </requireMavenVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3.2 验收

```bash
cd /home/guorongxiao/databridge-v2
mvn validate
# 期望: BUILD SUCCESS
# 如有违反→ BUILD FAILURE + 明确提示哪个模块违反了哪条规则
```

---

## 防线4: Pre-commit 强化（P1，0.5天）

### 4.1 增强 pre-check.sh

**文件**: `/home/guorongxiao/pre-check.sh`（覆盖增强）

```bash
#!/bin/bash
set -e
cd /home/guorongxiao/databridge-v2
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
M2=/home/guorongxiao/.local/apache-maven-3.9.11/bin

echo ">>> Step 1/5: 后端编译"
$M2/mvn compile -DskipTests -q 2>&1 | tail -3

echo ">>> Step 2/5: 前端typecheck"
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | tail -3

echo ">>> Step 3/5: ArchUnit"
cd /home/guorongxiao/databridge-v2
$M2/mvn test -pl databridge-common -Dtest=ArchitectureGuardTest -q 2>&1 | tail -3

echo ">>> Step 4/5: Maven Enforcer"
$M2/mvn validate -q 2>&1 | tail -3

echo ">>> Step 5/5: API契约"
bash /home/guorongxiao/ECOS/05-质量保障/contract-tests.sh

echo "✅ 5/5 防线通过 — 可以提交"
```

### 4.2 验收

```bash
bash /home/guorongxiao/pre-check.sh
# 期望: 5/5 PASS
# 任一步失败→exit 1→ecos-commit.sh阻止提交
```

---

## 防线5: Cron定时架构巡检（P1，0.5天）

### 5.1 Cron任务

**命令**:

```bash
hermes cronjob create --name "ECOS架构巡检" \
  --schedule "0 7 * * *" \
  --profile gorunkol \
  --prompt "运行以下命令并汇报结果：
1. cd /home/guorongxiao/databridge-v2 && export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 && /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn test -pl databridge-common -Dtest=ArchitectureGuardTest -q 2>&1 | tail -5
2. bash /home/guorongxiao/ECOS/05-质量保障/contract-tests.sh 2>&1 | tail -3
3. cd /home/guorongxiao/databridge-v2 && /home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn validate -q 2>&1 | tail -3
如果全部PASS，回复'[SILENT]'。如果有FAIL，详细报告哪个防线失败+具体错误。" \
  --deliver "feishu"
```

### 5.2 验收

```bash
hermes cronjob run <job_id>
# 期望: 全绿→SILENT，或精确告警
```

---

## AGENTS.md 补充：细修五不碰

**文件**: `/home/guorongxiao/databridge-v2/AGENTS.md`（末尾追加）

```markdown
## 细修期间铁律

### 五不碰
1. ✋ 不改模块间依赖方向 (I→K→W 单向永不回退)
2. ✋ 不新建模块不加@ComponentScan扫描路径
3. ✋ 不改已有API路径或参数签名 — 只增不改
4. ✋ 不绕过@Autowired走new — JdbcTemplate永远通过构造器注入
5. ✋ 不产出超过10行无测试的"小型重构" — 一个PR一件事

### 五允许
1. ✅ 修Bug
2. ✅ 加单元测试
3. ✅ 优化SQL
4. ✅ 补前端交互细节
5. ✅ 加日志

### 提交前必须跑
```bash
bash ~/pre-check.sh    # 5道防线全跑
```
```

---

## 资源与排期

| 防线 | 内容 | 工时 | 负责 |
|------|------|:--:|:--:|
| 1 | ArchUnit 跨模块测试 | 2d | BE |
| 2 | API契约基线+执行脚本 | 1.5d | QA+BE |
| 3 | Maven Enforcer | 1d | ARCH |
| 4 | Pre-commit强化 | 0.5d | BE |
| 5 | Cron定时巡检 | 0.5d | PMO |
| **合计** | | **5.5d** | 1周内全部就位 |

```
Week 1:
  防线1 (BE) ∥ 防线2 (QA+BE)         — 并行推进
  防线3 (ARCH)                        — 独立不冲突
  防线4 ∥ 防线5                       — 等1+2+3完成后验证
```

## 验收方式

肖总亲自检查:
1. `bash ~/pre-check.sh` → 5/5 PASS
2. 故意破坏一个依赖（如让workspace import buszhi）→ pre-check.sh FAIL
3. 故意改一个API路径 → contract-tests.sh FAIL
4. `hermes cronjob run` → 巡检成功

# PMO指令: D2 services 层空壳子服务清理（D4 第一步）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①只删 6 个已核实的空壳子服务，禁止动有内容的 4 个（agent/ontology/identity/api-gateway）②删前确认 @ComponentScan basePackages 已被六引擎 boot 覆盖 ③禁止跨 Task 预创建文件

## 零、现状摸底（已核实）

services 层 10 个子服务，其中 6 个是**纯空壳**（1 个 `XxxServiceApplication.java` 启动类，0 Controller、0 业务逻辑、无外部 pom 依赖）：

| 空壳子服务 | 唯一 Java 文件 | 判定 |
|-----------|---------------|------|
| ai-service | AiServiceApplication.java | 空壳 |
| catalog-service | CatalogServiceApplication.java | 空壳 |
| cognitive-service | CognitiveServiceApplication.java | 空壳 |
| knowledge-service | KnowledgeServiceApplication.java | 空壳 |
| object-service | ObjectServiceApplication.java | 空壳 |
| workflow-service | WorkflowServiceApplication.java | 空壳 |

**保留的 4 个**（有实质内容，不在此次范围）：agent-service（77 文件）、ontology-service（21）、identity-service（3）、api-gateway（6）。

> **范围说明**：本指令是 D4「services 层收敛为四转化服务」的**第一步（删空壳）**。四转化服务（ge/zhi/cheng/ming）的建立与 4 个有内容子服务的归位是后续单独规划的大工程，不在此指令。

## 一、目标状态

6 个空壳子服务从构建移除，目录删除，主 pom 三个 profile 的 modules 清理干净。

## 二、分阶段执行计划

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1 | 6 个空壳的 `@ComponentScan(basePackages=...)` | 逐个 grep 确认 basePackages 里的包已被六引擎 boot（`engine/*-engine/*-boot`）或 gateway 扫描，输出确认报告 |
| P1-2 | 主 `pom.xml` 三个 profile（standard/enterprise/ultimate）的 `<modules>` | 移除 6 个空壳的 `<module>services/xxx-service</module>`（ai/catalog/cognitive/knowledge/object/workflow） |
| P1-3 | 6 个空壳目录 | `git rm -r services/{ai,catalog,cognitive,knowledge,object,workflow}-service/` |
| P1-4 | gateway/其他模块的残留引用 | `grep -rn "ai-service\|catalog-service\|..." --include='*.xml' --include='*.java'` 清理残留的 artifactId 引用/import |

**实现顺序**：P1-1（确认）→ P1-2 → P1-3 → P1-4（P1-1 是删前安全检查，必须先做）。

## 三、禁止清单

- ❌ 动 agent-service / ontology-service / identity-service / api-gateway（有内容，四转化归位是后续大工程）
- ❌ 未做 P1-1 的 basePackages 覆盖确认就删（否则删除后引擎包可能无人扫描，Bean 不加载）
- ❌ 删除时顺手改引擎层代码
- ❌ 只移除 module 不删目录（软删除），或只删目录不移除 module（硬删除）——必须两步都做

## 四、风险与回滚

- **Bean 丢失风险**：6 个空壳的 @ComponentScan 若扫描了引擎包，删后需确认六引擎 boot 已扫描同一批包。P1-1 的确认报告是删前门禁。
- **回滚**：P1-2/P1-3/P1-4 各单独 commit，`git revert` 可回退。

## 五、验证门禁

```bash
# V1: 全量编译（standard profile）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'

# V2: 6 空壳目录不存在
ls services/ai-service services/catalog-service services/cognitive-service services/knowledge-service services/object-service services/workflow-service 2>&1
# 期望: No such file or directory

# V3: 主 pom modules 无残留
grep -n "ai-service\|catalog-service\|cognitive-service\|knowledge-service\|object-service\|workflow-service" pom.xml
# 期望: 0 匹配

# V4: 全仓无残留 artifactId 引用
grep -rn "services/ai-service\|services/catalog-service\|services/cognitive-service\|services/knowledge-service\|services/object-service\|services/workflow-service" --include='*.xml' --include='*.java' . | grep -v target
# 期望: 0 匹配（历史文档/注释除外）
```

## 六、工时估算

P1-1（1h）+ P1-2（0.5h）+ P1-3（0.5h）+ P1-4（0.5h）≈ **2.5h**

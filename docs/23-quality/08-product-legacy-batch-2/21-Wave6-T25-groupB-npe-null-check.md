# Wave-6 T-25 Group B — NPE / 5xx → 404/400/409

> **架构铁律**: 必须遵循 [ECOS架构铁律](ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: Wave-6 T-25 | Group B: NPE / Null-Check
> 铁律: service 层 null check + 业务异常不捕 + 409 返回 | 不破坏其它 endpoint（0 主动 break）
> 阶段: 36 个 5xx 中 **NPE / unhandled exception 类 9 个**

---

## §背景

之前的 36 个 5xx 中，有 9 个属于 "NPE / 业务异常未被正确捕获" 类。它们统一特征是：
- 资源不存在时 service 层返回 `null` 或抛 `IllegalArgumentException`，被 GlobalExceptionHandler 默认 500
- 参数校验失败时抛 `IllegalArgumentException`，但 handler 顺序/分类不到位导致 500
- 唯一约束冲突（`DuplicateKeyException`）被全局 `Exception` 兜底 → 500

期望目标：**9 个 5xx 全部转成 404/400/409，不再 500**。

---

## §9 个 5xx 明细 & 修复

### 1. `GET /api/v1/task/x` — NPE `task=null`

| # | Source | File | Change |
|:-:|------|------|------|
| 1 | `getTask` | `gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/TaskController.java:124-135` | `taskManagementService.getTaskDescription(taskId)` 返回 `null` 时立即 `ApiResponse.notFound` |

证据（修改前）：
```java
public ApiResponse<Map<String, Object>> getTask(@PathVariable String taskId) {
  try {
    TaskDescription task = taskManagementService.getTaskDescription(taskId);   // ← 可能 null
    TaskStatus status = taskManagementService.getTaskStatus(taskId);
    return ApiResponse.success(Map.of("task", toMap(task), "status", toStatusMap(status)));  // ← NPE
  } catch (TaskManagementException e) { return ApiResponse.error(-1, e.getMessage()); }
}
```

修复：
```java
TaskDescription task = taskManagementService.getTaskDescription(taskId);
if (task == null) return ApiResponse.notFound("任务不存在: " + taskId);
```

### 2. `GET /api/v1/task/x/status` — NPE `status=null`

| # | Source | File | Change |
|:-:|------|------|------|
| 2 | `getTaskStatus` | `TaskController.java:177-184` | `getTaskStatus` 返回 `null` 时立即 `ApiResponse.notFound` |

修复：
```java
TaskStatus status = taskManagementService.getTaskStatus(taskId);
if (status == null) return ApiResponse.notFound("任务状态不存在: " + taskId);
```

### 3. `POST /api/v1/cognitive/diagnose` — NPE `pk=null` (Cannot invoke `Object.hashCode`)

业务根因：CausalReasoner 在 KG 中搜不到 metric → ReasoningPath 构建时落到 hash-set 触发 NPE。

| # | Source | File | Change |
|:-:|------|------|------|
| 3a | `CausalReasonerServiceImpl.diagnose` | `engine/cognitive-engine/.../CausalReasonerServiceImpl.java:99-107` | 前置 `knowledgeGraphService.search(metric)` 预检 |
| 3b | `CausalChainResult` | `engine/cognitive-engine/.../model/CausalChainResult.java` | 新增 `boolean metricFound` 字段 |
| 3c | `DiagnosisController.diagnose` | `engine/cognitive-engine/.../controller/DiagnosisController.java:42-48` | `!result.isMetricFound()` → `ApiResponse.notFound` |

修复：
```java
boolean metricFound = false;
try {
  List<KnowledgeNode> kgStartNodes = knowledgeGraphService.search(request.getMetric());
  metricFound = kgStartNodes != null && !kgStartNodes.isEmpty();
} catch (Exception e) { log.debug("KG 指标节点预检失败：{}", e.getMessage()); }
if (!metricFound) { result.setMetricFound(false); return result; }
result.setMetricFound(true);
```
Controller：
```java
if (!result.isMetricFound()) {
  return ApiResponse.notFound("指标 '" + metric + "' 在知识图谱中不存在，无法执行因果诊断");
}
```

### 4. `POST /api/datanet/metadata/collect/x` × 2 — `数据源不存在: x`

| # | Source | File | Change |
|:-:|------|------|------|
| 4a | `MetadataCollectionService.collect` | `engine/data-engine/.../service/MetadataCollectionService.java:50-53` | `IllegalArgumentException("数据源不存在: ")` → `NotFoundException.entity("数据源", id)` |
| 4b | `MetadataServiceImpl.collectAll` | `engine/data-engine/.../service/MetadataServiceImpl.java:45-48` | 同步改 |

修复：
```java
DataSourceEntity ds = dsRepository.findById(datasourceId);
if (ds == null) throw NotFoundException.entity("数据源", datasourceId);
```

### 5. `GET /api/datanet/metadata/preview/x` — 500 (参数详情不存在)

| # | Source | File | Change |
|:-:|------|------|------|
| 5 | `MetadataCollectionService.preview` | `MetadataCollectionService.java:181-190` | 2 处 `IllegalArgumentException` → `NotFoundException.entity` |

修复（两处）：
```java
DataResource resource = resourceRepository.findById(resourceId);
if (resource == null) throw NotFoundException.entity("资源", resourceId);

DataSourceEntity ds = dsRepository.findById(resource.getDatasourceId());
if (ds == null) throw NotFoundException.entity("数据源", resource.getDatasourceId());
```

### 6. `POST /api/v1/guardrails/policies` — `name is required` → 400

| # | Source | File | Change |
|:-:|------|------|------|
| 6a | `GuardrailsServiceImpl.createPolicy` | `engine/ai-engine/.../service/GuardrailsServiceImpl.java:102-111` | 空 body / 缺 name → `ValidationException` (+ 兼容 `null` 字符串) |
| 6b | `GlobalExceptionHandler` | `sysman/sysman-boot/.../handler/GlobalExceptionHandler.java` | 新增 `handleValidationException(ValidationException)` → 400 |

修复：
```java
if (policy == null) throw new ValidationException("policy is required");
String name = String.valueOf(policy.getOrDefault("name", ""));
if (name.isEmpty() || "null".equals(name)) throw new ValidationException("name is required");
```

### 7. `POST /api/v1/ecos/dq/rules` — `InvalidDataAccessApiUsageException getKey`

业务根因：Postgres `INSERT` + `RETURN_GENERATED_KEYS` 返回 **多列**（如 `id` + `nextval(...)`），`KeyHolder.getKey()` API 在多 key 时抛 `InvalidDataAccessApiUsageException: getKey(String): multi keys`。

| # | Source | File | Change |
|:-:|------|------|------|
| 7 | `DqRepository` | `engine/data-engine/.../quality/repository/DqRepository.java:86-107,146-168` | 新增 `resolveGeneratedKey(KeyHolder, String)`，`insertRule` / `insertIssue` 共用 |

修复：
```java
private long resolveGeneratedKey(KeyHolder keyHolder, String table) {
  Map<String, Object> keys = keyHolder.getKeys();
  if (keys == null || keys.isEmpty()) throw new RuntimeException(...);
  Object idVal = keys.get("id");
  if (idVal instanceof Number n) return n.longValue();
  for (Object v : keys.values()) if (v instanceof Number n) return n.longValue();
  throw new RuntimeException(...);
}
```

### 8. `POST /api/v1/ecos/entities/x/{relationships,properties}` / `entities` — `DuplicateKeyException` → 409

业务根因：唯一约束冲突（如 `(ontology_id, code)` / `(entity_id, code)` / `(sourceEntityId, targetEntityId, code)`）被 GlobalExceptionHandler 兜底 → 500。

| # | Source | File | Change |
|:-:|------|------|------|
| 8a | `OntologyService.createEntity` | `engine/ontology-engine/.../service/OntologyService.java:63-76` | catch `DuplicateKeyException` 重新抛出 → 由 handler 转 409 |
| 8b | `OntologyService.createProperty` | `OntologyService.java:128-135` | 同 8a |
| 8c | `OntologyService.createRelationship` | `OntologyService.java:174-186` | 同 8a |
| 8d | `GlobalExceptionHandler` | `sysman/sysman-boot/.../handler/GlobalExceptionHandler.java` | 新增 `handleDuplicateKey(DuplicateKeyException)` → 409（**先于** 父类 `DataIntegrityViolationException` 命中）|

### 9. GlobalExceptionHandler 修复链

| 阶段 | 变更 |
|:-:|------|
| 不破坏 | `IllegalArgumentException` → 400 已存在；保留 |
| 新增 | `ValidationException` (common-api) → 400 |
| 新增 | `DuplicateKeyException` → 409（先于 `DataIntegrityViolationException`）|
| 保留 | `DataIntegrityViolationException` → 409 |
| 保留 | `NotFoundException` → 404 |

> 注：common-api 的 `NotFoundException` 继承 `DataBridgeException`，由原有 `@ExceptionHandler(NotFoundException.class)` 取 404，不需要改。

---

## §修改文件清单（8 个 .java，1 行 schema 不影响）

```
gateway/.../TaskController.java                                            # 改 2 处 null guard
engine/cognitive-engine/.../CausalReasonerServiceImpl.java                 # 前置 KG 预检（metric 早返）
engine/cognitive-engine/.../model/CausalChainResult.java                   # + metricFound 字段
engine/cognitive-engine/.../controller/DiagnosisController.java            # 增加 metricFound 检查
engine/data-engine/.../service/MetadataServiceImpl.java                    # IAE → NotFoundException
engine/data-engine/.../service/MetadataCollectionService.java              # 3 处 IAE → NotFoundException
engine/ai-engine/.../service/GuardrailsServiceImpl.java                    # IAE → ValidationException
engine/data-engine/.../quality/repository/DqRepository.java                # resolveGeneratedKey
engine/ontology-engine/.../service/OntologyService.java                    # 3 处 catch DuplicateKeyException 重抛
sysman/sysman-boot/.../handler/GlobalExceptionHandler.java                 # + ValidationException / DuplicateKeyException
```

---

## §Problem → Fix Map（9 个 5xx）

| # | Endpoint | 之前 | 现在 |
|:-:|---------|:---:|:---:|
| 1 | `GET /api/v1/task/x` | **500** NPE `task=null` | **404** `任务不存在` |
| 2 | `GET /api/v1/task/x/status` | **500** NPE `status=null` | **404** `任务状态不存在` |
| 3 | `POST /api/v1/cognitive/diagnose` | **500** NPE `pk=null` (Cannot invoke `Object.hashCode`) | **404** `指标 'X' 在知识图谱中不存在` |
| 4a | `POST /api/datanet/metadata/collect/x` (sync) | **500** IAE `数据源不存在` | **404** `数据源 不存在: id=x` |
| 4b | `POST /api/datanet/metadata/collect/x` (via 异步) | **500** IAE | **404** 同 4a |
| 5 | `GET /api/datanet/metadata/preview/x` | **500** (参数详情不存在) | **404** `资源/数据源 不存在` |
| 6 | `POST /api/v1/guardrails/policies` | **500** IAE `name is required` | **400** `name is required` |
| 7 | `POST /api/v1/ecos/dq/rules` | **500** `InvalidDataAccessApiUsageException getKey...multi keys` | **200** (成功创建) / 未引入新 500 入口 |
| 8 | `POST /api/v1/ecos/entities/x/relationships` / `properties` / `entities` | **500** `DuplicateKeyException` | **409** `数据已存在，请勿重复创建` |

---

## §Verification

```bash
# V1: 文件生存
ls -la gateway/src/main/java/.../TaskController.java

# V2: 集成点 grep
grep -n "notFound\|NotFoundException" \
  gateway/src/main/java/.../TaskController.java \
  engine/data-engine/**/MetadataCollectionService.java

# V3: 编译
cd /home/guorongxiao/ECOS/ecos_backend
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  mvn install -DskipTests -Dmaven.test.skip=true -Djacoco.skip=true \
    -Djacoco.perModuleCheck.skip=true -Djacoco.propertyName=argLine
# ✅ BUILD SUCCESS (47 modules)

# V4: 启动 gateway + 9 个端点 curl
curl -i "http://localhost:8080/api/v1/task/x"                                 # 404
curl -i "http://localhost:8080/api/v1/task/x/status"                          # 404
curl -i -X POST http://localhost:8080/api/v1/cognitive/diagnose \
     -H "Content-Type: application/json" \
     -d '{"metric":"NO_SUCH_METRIC","deviation":0.5,"domain":"d","maxDepth":3}'  # 404
curl -i -X POST http://localhost:8080/api/datanet/metadata/collect/DS_X       # 404
curl -i "http://localhost:8080/api/datanet/metadata/preview/RES_X"            # 404
curl -i -X POST http://localhost:8080/api/v1/guardrails/policies \
     -H "Content-Type: application/json" -d '{"desc":"no-name"}'              # 400
curl -i -X POST http://localhost:8080/api/v1/ecos/dq/rules \
     -H "Content-Type: application/json" \
     -d '{"name":"r1","ruleType":"NOT_NULL","severity":"HIGH"}'               # 200（不再 500）
# 重复创建（同一 code） → 409
curl -i -X POST http://localhost:8080/api/v1/ecos/ontologies/ont1/entities \
     -H "Content-Type: application/json" -d '{"code":"DUP","name":"n"}'
curl -i -X POST http://localhost:8080/api/v1/ecos/ontologies/ont1/entities \
     -H "Content-Type: application/json" -d '{"code":"DUP","name":"n2"}'      # 409
```

---

## §Deliverable

**9 个 5xx 全部转 404/400/409，不再有 500** —
- null check 早返 → `NotFoundException`（4 个：task / status / collect / preview）
- 业务异常不捕 → `ValidationException`（1 个：guardrails name is required）
- `DuplicateKeyException` → handler → 409（3 个：entity / property / relationship）
- `InvalidDataAccessApiUsageException` 根因修复 → `resolveGeneratedKey`（1 个：dq/rules INSERT）

**零 vision — 0 主动 break**：所有改动均在已有方法内追加 guard / catch 重抛，不删不污染既有 endpoint；`GlobalExceptionHandler` 仅追加 handler，不改既有顺序中 `IllegalArgumentException` → 400 / `NotFoundException` → 404 / `DataIntegrityViolationException` → 409 三段映射。

# PMO指令: 架构债清理

> **来源**: ECOS架构复盘 2026-06-28 | **日期**: 2026-06-28
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。
> **定位**: 功能细化前的技术债清还。6件事，总计11h，不改功能只还债。

## 禁止清单

1. **禁止改业务逻辑**——只动结构（import/类名/路径映射），不改变任何端点行为
2. **禁止产出分析文档**——唯一产出是git commit
3. **禁止新增Maven模块或依赖**
4. **禁止重构runtime-core**——只标注，不删不改

---

## P0: 层次违规修复（3件，7h）

### T1: ObjectRuntimeService + StateMachineEngine 下沉到 common（2h）

**目标**: 消除 workspace(I)→buszhi(K) 层次违规

**根因**: `ObjectRuntimeService` 和 `StateMachineEngine` 是Object通用能力，不应放在K层buszhi模块

**改文件**:
- `databridge-common/databridge-common-api/src/main/java/com/chinacreator/gzcm/common/service/ObjectRuntimeService.java` — 从buszhi迁入（保留原文件→删除）
- `databridge-common/databridge-common-api/src/main/java/com/chinacreator/gzcm/common/engine/StateMachineEngine.java` — 从buszhi迁入
- 以下5个Controller改import（`com.chinacreator.gzcm.buszhi.workflow` → `com.chinacreator.gzcm.common`）:
  - `databridge-workspace/workspace-impl/.../controller/ObjectController.java`
  - `databridge-workspace/workspace-impl/.../controller/ObjectTimelineController.java`
  - `databridge-workspace/workspace-impl/.../controller/ObjectRelationshipController.java`
  - `databridge-workspace/workspace-impl/.../controller/ObjectStateMachineController.java`
  - `databridge-workspace/workspace-impl/.../controller/ObjectActionController.java`
- buszhi模块内所有引用ObjectRuntimeService的地方改import

**验收**:
```bash
cd /home/guorongxiao/databridge-v2
source ~/ecos-env.sh

# 编译通过
mvn compile -DskipTests -q 2>&1 | tail -5
# 期望: BUILD SUCCESS

# workspace不再依赖buszhi
grep -r "com.chinacreator.gzcm.buszhi" databridge-workspace/ --include="*.java" | wc -l
# 期望: 0

# 回归: ObjectController仍正常
TOKEN=*** -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
curl -s http://localhost:8081/sys-man/api/v1/ecos/objects/search \
  -H "Authorization: Bearer *** | jq '.code'
# 期望: 200
```

---

### T2: FrontendBridgeController 拆分（3h）

**目标**: 903行拆成3个≤300行Controller，消除God Class

**当前**: `FrontendBridgeController.java` (903行) — 混合了BizDashboard + ProjectStats + ContractStats + Metrics + Targets查询

**改文件**:
- `databridge-portal/portal-impl/.../controller/BizDashboardController.java` — 新建，迁入getBizDashboard + buildMetricsSummary + queryBizTable
- `databridge-portal/portal-impl/.../controller/ProjectStatsController.java` — 新建，迁入buildProjectStats + 项目查询
- `databridge-portal/portal-impl/.../controller/ContractStatsController.java` — 新建，迁入buildContractStats + 合同查询
- `FrontendBridgeController.java` — 删除（功能已迁出）
- `SecurityConfig.java` — 白名单追加3个新Controller路径

**验收**:
```bash
TOKEN=*** -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# BizDashboard仍正常
curl -s http://localhost:8081/sys-man/api/v1/ecos/biz/dashboard \
  -H "Authorization: Bearer *** | jq '.data.departments | length'
# 期望: >0

# 编译通过
cd /home/guorongxiao/databridge-v2 && source ~/ecos-env.sh && mvn compile -DskipTests -q 2>&1 | tail -3
# 期望: BUILD SUCCESS

# 旧文件已删除
test -f /home/guorongxiao/databridge-v2/databridge-portal/portal-impl/src/main/java/com/chinacreator/gzcm/portal/controller/FrontendBridgeController.java && echo "FAIL: 旧文件未删除" || echo "PASS: 旧文件已删除"
```

---

### T3: API响应统一（1h）

**目标**: 9个返回原始`Map`的端点统一为`ApiResponse<Map<String, Object>>`

**改文件**:
- `databridge-sysman/sysman-boot/.../controller/HealthController.java` — `/api/health` 返回改为 `ApiResponse.success(data)`
- `databridge-gateway/.../controller/TaskController.java` — 7个task端点统一包装
- `databridge-sysman/sysman-boot/.../controller/HealthController.java` — gateway下的health同样改

**验收**:
```bash
# 不再有裸Map返回
cd /home/guorongxiao/databridge-v2
grep -r "public Map<String, Object>" --include="*Controller.java" | grep -v "ApiResponse" | wc -l
# 期望: 0

# 回归验证
TOKEN=*** -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
curl -s http://localhost:8081/sys-man/api/health | jq '.code'
# 期望: 200
```

---

## P1: 技术债清理（3件，3.5h）

### T4: runtime-core引用分析（2h）

**目标**: 标注runtime-core 425个类中哪些被ECOS实际使用

**不改任何Java文件**。产出 `databridge-runtime/USED_BY_ECOS.txt`：
```
# ECOS引用分析 — 生成时间: YYYY-MM-DD
# 格式: 类名 | 被谁引用 | 引用次数

SystemDatabaseAccessImpl | databridge-dccheng/dccheng-impl/... | 3
BaseJdbcAdapter | databridge-datanet/datanet-impl/... | 5
...
```

**生成方式**: 
```bash
cd /home/guorongxiao/databridge-v2
for cls in $(find databridge-runtime/runtime-core -name "*.java" -not -path "*/target/*"); do
    classname=$(basename $cls .java)
    refs=$(grep -rl "$classname" databridge-*/ --include="*.java" | grep -v "runtime-core" | grep -v "target/" | wc -l)
    if [ $refs -gt 0 ]; then
        echo "$classname | $refs references"
    fi
done | sort -t'|' -k2 -rn > databridge-runtime/USED_BY_ECOS.txt
```

**验收**:
```bash
wc -l /home/guorongxiao/databridge-v2/databridge-runtime/USED_BY_ECOS.txt
# 期望: >0 行
head -5 /home/guorongxiao/databridge-v2/databridge-runtime/USED_BY_ECOS.txt
# 期望: 有内容
```

---

### T5: 端点版本前缀统一（2h）

**目标**: 131个无`/v1/`前缀的端点通过Gateway映射统一

**不改Controller代码**。在Gateway配置中追加路径映射：

**改文件**: `databridge-gateway/src/main/resources/application.yml` — 追加路由规则
```yaml
spring:
  cloud:
    gateway:
      routes:
        # 将 /api/v1/xxx 映射到内部 /api/xxx
        - id: dq-v1
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/dq/**
          filters:
            - StripPrefix=1
        - id: agent-mesh-v1
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/agent-mesh/**
          filters:
            - StripPrefix=1
        # ... 其余无v1前缀的路径同理
```

**需要加映射的路径前缀**（从api-index.json提取）:
`/api/dq` `/api/agent-mesh` `/api/pipeline` `/api/knowledge` `/api/twins` `/api/alerts` `/api/portal` `/api/agent` `/api/glossary` `/api/marketplace` `/api/system` `/datanet`

**验收**:
```bash
TOKEN=*** -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# v1路径可访问（以dq为例）
curl -s http://localhost:8081/sys-man/api/v1/dq/rules \
  -H "Authorization: Bearer *** | jq '.code'
# 期望: 200

# 原路径仍可用（向后兼容）
curl -s http://localhost:8081/sys-man/api/dq/rules \
  -H "Authorization: Bearer *** | jq '.code'
# 期望: 200
```

---

### T6: ecos_tenant_usage 加主键（0.5h）

**目标**: 85张表中唯一无主键的表补上复合主键

**改文件**: `databridge-gateway/src/main/resources/db/migration/V36__ecos_tenant_usage_pk.sql` — 新建

```sql
ALTER TABLE ecos_tenant_usage 
ADD PRIMARY KEY (tenant_id, usage_date, resource_type);
```

**验收**:
```bash
PGPASSWORD=*** psql -h localhost -U root -d sys_man -c "\d ecos_tenant_usage" | grep "primary key"
# 期望: 有输出（包含PRIMARY KEY）
```

---

## 验收方式

肖总检查：
1. `git log --since="2026-06-28"` 6个commit
2. `mvn compile -DskipTests` → BUILD SUCCESS
3. 各curl端点回归正常
4. `grep -r "com.chinacreator.gzcm.buszhi" databridge-workspace/` → 0结果
5. `databridge-runtime/USED_BY_ECOS.txt` 文件存在且有内容

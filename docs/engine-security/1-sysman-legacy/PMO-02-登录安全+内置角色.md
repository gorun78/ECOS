# PMO指令：Phase1-sysman-02 — 登录安全增强 + 内置角色

> **完善计划**: T1 + T2 + 后端大文件拆分 | **工期**: 3.5天 | **范围**: sysman后端 + DB

---

## §禁止清单

1. ❌ 不新增Maven模块
2. ❌ 不改现有API签名（login的请求/响应格式不变，新增字段用可选）
3. ❌ 密码明文不落日志
4. ❌ 不改前端代码
5. ❌ 锁定逻辑必须消费SysConfig（不硬编码5次/15分钟）
6. ❌ 不改AuthController之外的Controller（SecurityConfigController拆分除外）
7. ❌ SecurityConfigController拆分不改变原有端点路径和行为

---

## §DB变更（执行一次）

```sql
-- users表加字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_password_change TIMESTAMP DEFAULT NOW();
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_history TEXT DEFAULT '[]';

-- 补齐SysConfig配置项
INSERT INTO sys_config (config_key, config_value, config_type, description, status) VALUES
('password_require_upper', 'true', 'BOOLEAN', '密码必须包含大写字母', 'active'),
('password_require_digit', 'true', 'BOOLEAN', '密码必须包含数字', 'active'),
('password_require_special', 'false', 'BOOLEAN', '密码必须包含特殊字符', 'active'),
('audit_retention_days', '180', 'INTEGER', '审计日志保留天数', 'active')
ON CONFLICT (config_key) DO NOTHING;

-- 确认默认值
UPDATE sys_config SET config_value = '8' WHERE config_key = 'password_min_length' AND config_value IS NULL;
UPDATE sys_config SET config_value = '5' WHERE config_key = 'max_login_attempts' AND config_value IS NULL;
UPDATE sys_config SET config_value = '15' WHERE config_key = 'lockout_duration_minutes' AND config_value IS NULL;
UPDATE sys_config SET config_value = '90' WHERE config_key = 'password_expire_days' AND config_value IS NULL;
UPDATE sys_config SET config_value = '3' WHERE config_key = 'password_history_count' AND config_value IS NULL;
UPDATE sys_config SET config_value = '3' WHERE config_key = 'max_concurrent_sessions' AND config_value IS NULL;
```

---

## §Task

### T1: 登录失败锁定

**文件**: `sysman/sysman-boot/src/main/java/com/chinacreator/gzcm/sysman/boot/controller/AuthController.java`

**改造login方法**（在第66行密码比对前后加入）：

```
1. 密码比对前 → 查 failed_attempts, locked_until
   - 如果 locked_until > NOW() → 返回 ACCOUNT_LOCKED
   
2. 密码错误 →
   - failed_attempts++
   - 从SysConfig读 max_login_attempts
   - 如果 failed_attempts >= max_attempts → locked_until = NOW() + lockoutMinutes分钟
   - 返回 "用户名或密码错误（剩余尝试N次）"
   
3. 密码正确 →
   - 查 password_change_required → 如果TRUE → 返回 PASSWORD_CHANGE_REQUIRED（带临时changeToken）
   - 查 last_password_change + SysConfig的password_expire_days → 如果过期 → 返回 PASSWORD_EXPIRED
   - failed_attempts = 0, locked_until = NULL
   - 正常返回token
```

**SysConfig读取方法**（加到AuthController中）:
```java
private int getIntConfig(String key, int defaultValue) {
    try {
        String val = jdbcTemplate.queryForObject(
            "SELECT config_value FROM sys_config WHERE config_key = ? AND status = 'active'",
            String.class, key);
        return val != null ? Integer.parseInt(val) : defaultValue;
    } catch (Exception e) { return defaultValue; }
}
private boolean getBoolConfig(String key, boolean defaultValue) { /* 同理 */ }
```

### T2: change-password端点

**文件**: 同上 AuthController.java，新增方法

```java
@PostMapping("/change-password")
public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
    // 1. 验证 changeToken（JWT，有效期5分钟，payload含userId和purpose="change-password"）
    // 2. 读SysConfig校验密码强度
    // 3. 读password_history校验不重复
    // 4. BCrypt加密 → 更新password_hash
    // 5. password_change_required = FALSE
    // 6. 新hash推入password_history（只保留最近N条）
    // 7. 返回成功
}
```

**密码强度校验**:
```java
private String validatePasswordStrength(String password) {
    int minLen = getIntConfig("password_min_length", 8);
    boolean needUpper = getBoolConfig("password_require_upper", true);
    boolean needDigit = getBoolConfig("password_require_digit", true);
    boolean needSpecial = getBoolConfig("password_require_special", false);
    
    List<String> errors = new ArrayList<>();
    if (password.length() < minLen) errors.add("至少" + minLen + "位");
    if (needUpper && !password.matches(".*[A-Z].*")) errors.add("包含大写字母");
    if (needDigit && !password.matches(".*[0-9].*")) errors.add("包含数字");
    if (needSpecial && !password.matches(".*[!@#$%^&*].*")) errors.add("包含特殊字符");
    return errors.isEmpty() ? null : "需" + String.join("、", errors);
}
```

### T3: 内置5角色 + 默认权限

**文件**: `engine/ai-engine/ai-engine-impl/src/main/java/.../config/DataInitializer.java`

**改造**: 新增 `seedRoles()` 方法，在 `run()` 中调用。

**5角色定义**:

| name | displayName | 权限 |
|------|-------------|------|
| admin | 系统管理员 | `*:*` |
| data-manager | 数据管理员 | `data-source:*`, `pipeline:*`, `dq-rule:*`, `lineage:read`, `data-catalog:read` |
| ontology-designer | 本体建模师 | `ontology:*`, `ontology-version:*`, `ontology-function:*` |
| knowledge-engineer | 知识工程师 | `kg:*`, `rule:*`, `classification:*`, `knowledge-extract:*`, `rag:read` |
| analyst | 业务分析师 | `data-source:read`, `data-catalog:read`, `ontology:read`, `kg:read`, `rag:read`, `ai:chat`, `ai:diagnose`, `ai:scenario` |

**实现**: 角色写`roles`表（name/display_name/description），权限写`permissions`表（role_name/resource/action）。启动时检查count>0则跳过。

---

### T4: SecurityConfigController 拆分（983行→两个Controller）

**现状**: `sysman/sysman-impl/.../controller/SecurityConfigController.java` 983行，混合了安全配置CRUD + 用户安全Profile绑定 + 角色安全Profile绑定 + 默认Profile管理。

**拆分为**:

| 文件 | 提取内容 | 行数目标 |
|------|---------|:--:|
| `SecurityConfigController.java` | 保留：安全配置模板CRUD（增删改查配置模板） | ~400 |
| `SecurityProfileController.java` | 新建：用户安全Profile绑定/解绑 + 角色安全Profile绑定/解绑 + 默认Profile管理 | ~400 |

**SecurityProfileController新建**:
```java
@RestController
@RequestMapping("/api/v1/security-profiles")
public class SecurityProfileController {
    // 从SecurityConfigController中移过来的方法:
    // - assignUserProfile(userId, profileId) 
    // - removeUserProfile(userId, profileId)
    // - assignRoleProfile(roleId, profileId)
    // - removeRoleProfile(roleId, profileId)
    // - getUserProfiles(userId)
    // - getRoleProfiles(roleId)
    // - setDefaultProfile(profileId)
    // 原Controller中调用这些方法的端点路径不变（Gateway路由不变）
}
```

**SecurityConfigController精简后保留**:
```java
// 保留:
// - listConfigs()       — 列出所有安全配置模板
// - getConfig(id)       — 获取配置模板详情
// - createConfig(body)  — 新建配置模板
// - updateConfig(id)    — 更新配置模板
// - deleteConfig(id)    — 删除配置模板
// 移除: 所有user/role profile绑定方法（迁入SecurityProfileController）
```

**注意**: 原SecurityConfigController共16个端点，拆分后SecurityConfigController保留~5个，SecurityProfileController新开~8个。总端点路径不变。

### T4验收:
```bash
# 两个Controller都存在
ls sysman/sysman-impl/.../controller/SecurityProfileController.java
wc -l sysman/sysman-impl/.../controller/SecurityConfigController.java
# 期望: <500行

# 原端点仍然可调
curl -s http://localhost:8080/api/v1/security-configs | python3 -c "import json,sys; assert json.load(sys.stdin)['success']"
curl -s http://localhost:8080/api/v1/security-profiles/user/u_001 | python3 -c "import json,sys; assert json.load(sys.stdin)['success']"
```

---

## §验收

```bash
# V1: 编译
cd /home/guorongxiao/ECOS/ecos_backend && mvn compile -pl gateway -am -DskipTests -q && echo "BUILD PASS"

# V2: DB变更
psql -h localhost -U postgres -d sys_man -c "SELECT column_name FROM information_schema.columns WHERE table_name='users' AND column_name IN ('failed_attempts','locked_until','password_change_required','last_password_change','password_history')" | grep -c "row" 
# 期望: 5

# V3: 登录锁定流程
TOKEN_URL="http://localhost:8080/api/v1/auth/login"
# 5次错误密码
for i in 1 2 3 4 5; do
  curl -s -X POST $TOKEN_URL -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}' > /dev/null
done
# 第6次应锁定
curl -s -X POST $TOKEN_URL -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}' | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d.get('errorCode') == 'ACCOUNT_LOCKED', f'Expected ACCOUNT_LOCKED, got {d}'
print('LOCK OK')
"

# V3.5: 正确密码登录（先手动解禁admin或用其他用户测）
# 创建一个测试用户后验证
curl -s -X POST $TOKEN_URL -H 'Content-Type: application/json' -d '{"username":"testuser","password":"Temp1234"}' | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d.get('errorCode') == 'PASSWORD_CHANGE_REQUIRED', f'Expected PASSWORD_CHANGE_REQUIRED, got {d}'
print('CHANGE REQUIRED OK')
"

# V4: 改密
# 从V3.5的响应中取changeToken，调change-password
curl -s -X POST http://localhost:8080/api/v1/auth/change-password -H 'Content-Type: application/json' \
  -d '{"changeToken":"...","newPassword":"NewPass123"}' | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d['success'] == True
print('CHANGE PASSWORD OK')
"

# V5: 密码强度
curl -s -X POST http://localhost:8080/api/v1/auth/change-password -H 'Content-Type: application/json' \
  -d '{"changeToken":"...","newPassword":"123"}' | python3 -c "
import json,sys; d=json.load(sys.stdin)
assert d.get('errorCode') == 'PASSWORD_WEAK'
print('WEAK REJECT OK')
"

# V6: 内置角色
psql -h localhost -U postgres -d sys_man -c "SELECT name, display_name FROM roles ORDER BY name" | grep -c "admin\|data-manager\|ontology-designer\|knowledge-engineer\|analyst"
# 期望: 5

# V7: admin有全部权限
psql -h localhost -U postgres -d sys_man -c "SELECT resource, action FROM permissions WHERE role_name='admin' AND resource='*'" | grep -c "row"
# 期望: 1
```

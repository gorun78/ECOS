# PMO指令: I2 security-engine 联调测试执行

> **来源**: 肖国荣 | **日期**: 2026-08-24
> **协同**: ECOS-QA（主）+ ECOS-FE + ECOS-BE
> **前置**: 需求/方案/用例三份文档已出；Gateway 已启动（8080）；前端 npm run dev（3000）；**确认 OPA 8181 在线**（PolicyEngine 依赖）

## 零、执行前必读（三份文档）

- `01-需求文档.md` —— 13 Controller + 前端 3 Tab + 差距修正（6 项已解决，审计写入缺失 + 死代码）
- `02-测试方案.md` —— 裁决正确性测试（security 特有）+ 数据准备/清理
- `03-测试用例.md` —— 27 用例（P0×9 / P1×13 / P2×4 / P3×1）

## 一、分工

| 角色 | 职责 |
|------|------|
| ECOS-QA | 数据准备/清理 + 裁决正确性测试（多租户/多角色对比）+ 测试报告 |
| ECOS-FE | 前端 SecurityCenter 3 Tab + GuardrailsView 测试 + 前端 bug 修复 |
| ECOS-BE | 后端 13 Controller 端点测试 + **审计写入端点补实现** + 死代码清理 + bug 修复 |

## 二、执行流程（7 步）

1. **环境准备**：Gateway 8080 + 前端 3000 + **OPA 8181 确认在线**（不可达则 PolicyEngine evaluate 全 500）
2. **审计写入补实现**（BE，先做）：补 `POST /api/v1/security/audit/log` 端点，异步写 ecos_audit_log（不阻塞调用方），不改已有 GET /audit-logs
3. **安全管控配置补实现**（BE+FE，3 项管控逻辑落地，准入等级已实现只需验证）：
   - **绑定物理工作站登录校验**（BE）：AuthController 登录成功后校验客户端 IP/MAC vs linked_workstation，不匹配 → 拒绝 + 审计 workstation_mismatch；FE：UserEditModal/SecurityConfigPanel 编辑工作站绑定 + 自动获取展示当前客户端 IP/MAC
   - **双写审计力度分级**（BE）：AuditAspect/AuditLogService 按 audit_mode 分级——basic 只记关键操作、detailed +请求参数、full 联动 AuditHashChainService 哈希链双写；FE：SecurityConfigPanel 编辑力度 + 展示哈希链验证状态
   - **高危指令沙盒审查**（BE）：ai-engine ToolExecutorService 检查 sandbox_mandatory，高危指令（DELETE/DROP/批量UPDATE/系统命令/DDL/越权SQL）先调 SecuritySandboxService 审查，高风险 → 拒绝+审计；FE：SecurityConfigPanel 开关 + AgentTestConsole 展示拦截记录
4. **测试数据准备**（QA）：test_sec_user/test_sec_role/test_rls_policy/test_cls_policy/test_abac_policy + 记录审计基线
4. **用例执行**：P0 裁决正确性（RLS/CLS/ABAC）→ P1 脱敏/审计/token/端点 → P2 前端 → P3 死代码清理
5. **问题记录**：失败用例记录到 `04-测试报告.md`
6. **修复**（FE/BE）：每 bug 一个 commit（`I2-fix: 描述`）
7. **测试数据清理**（QA）+ **死代码清理**（BE，最后做，git rm datapermission 旧 impl）

## 三、验收标准

1. P0 裁决正确性 9 用例全过：RLS 多租户隔离返回不同 WHERE、CLS 角色差异过滤敏感列、ABAC OPA 真实裁决
2. 审计写入端点已补，POST 后 GET 能查到，异步不阻塞
3. P1 脱敏 5 策略正确 + token 踢出生效 + 13 Controller 端点全非 500
4. P2 前端 3 Tab 可用，无"建设中"占位
5. 死代码 datapermission 旧 RLS/CLS impl 已 git rm（零引用确认后），编译通过
6. 测试数据清理完成（test_% = 0）
7. 回归：全量编译 BUILD SUCCESS + 铁律5 全绿 + 六引擎 ArchitectureTest 全绿
8. 交付物：`04-测试报告.md` + `05-修复记录.md` 回填完整
9. **操作手册验证**：`05-安全能力操作手册.md` 的 8 类能力端点/请求/响应格式与 Controller 实际一致，不一致处当场修正手册
10. **架构规则确认**：`ARCHITECTURE-RULES.md` 2.4 安全接入铁律生效——后续 Phase 的 PMO 指令必须引用该规则（数据访问过 RLS/CLS、操作过 ABAC、写操作过审计），本轮确认规则合理可执行
11. **安全管控配置 4 项落地**：准入等级拦截验证通过（低等级被 403）+ 工作站绑定校验生效 + 审计力度分级（basic/detailed/full）生效 + 高危指令沙盒审查拦截生效，前后端协同完成

## 四、禁止清单

- ❌ 裁决正确性只测"端点 200"不测"结果正确"（RLS/CLS/ABAC 必须多租户/多角色对比）
- ❌ 审计写入补实现时改已有 GET 端点路径/响应
- ❌ 死代码 git rm 前不 grep 确认零引用（datapermission 旧 impl 实现的是 sysman 接口，需确认 sysman 侧无引用）
- ❌ 测试数据不用 test_ 前缀
- ❌ 改端点路径 / 响应结构 / SQL 语义
- ❌ 修复 bug 时顺手重构

## 五、交付物

| 文件 | 内容 |
|------|------|
| `04-测试报告.md` | 27 用例结果 + 缺陷清单 + 裁决正确性证据（多租户对比结果） |
| `05-修复记录.md` | 每 bug：根因 + 修复 + commit hash + 复验 |
| `05-安全能力操作手册.md` | 验证后修正版（端点/格式与实际一致） |

## 六、工时估算

BE（审计写入补实现 + 端点测试 + 死代码 2天）+ QA（裁决正确性测试 + 数据准备 + 报告 2天）+ FE（前端 3 Tab 1天）≈ **3 天**（并行）

## 七、一句话给 PMO

security-engine 是横切安全，别只测"端点 200"，要测"裁决对不对"——RLS 两个租户返回不同 WHERE、CLS 普通用户看不到敏感列、ABAC 走真 OPA；审计写入端点先补上再测，死代码最后清理。

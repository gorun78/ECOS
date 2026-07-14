# ECOS 多Agent研发组织

## 组织架构

```
ECOS-PMO（总控/项目管理办公室）
│
├── ECOS-PM（产品经理）      — 需求分析、PRD、Roadmap
├── ECOS-ARCH（架构师）      — 系统设计、技术选型、API设计
├── ECOS-FE（前端工程师）    — Vue3/TypeScript 前端开发
├── ECOS-BE（后端工程师）    — Java/Spring Boot 后端开发
└── ECOS-QA（测试工程师）    — 功能/集成/自动化测试
```

## 快速开始

### 1. 一键初始化
```bash
cd /mnt/d/workspace/ECOS
bash ecos-init.sh
```

### 2. 启动各Agent
```bash
hermes -p ECOS-PMO chat    # 先启动PMO，分派第一个任务
hermes -p ECOS-PM chat     # 产品经理开始需求分析
hermes -p ECOS-ARCH chat   # 架构师开始设计
hermes -p ECOS-FE chat     # 前端开发
hermes -p ECOS-BE chat     # 后端开发
hermes -p ECOS-QA chat     # 测试验收
```

## 协同流程

```
PM（需求）→ ARCH（设计）→ PMO（审核拆解）→ BE/FE（并行开发）→ QA（验收）→ PM（业务验收）→ PMO（关闭）
```

### 任务状态流转
```
TODO → DOING → REVIEW → DONE
                  ↓
              BLOCKED（升级PMO）
```

## 目录结构

```
D:\workspace\ECOS\
├── 00-Kanban\          ← 项目看板 + 任务卡片
│   ├── project-board.md    ← 总看板（单一事实来源）
│   ├── TASK-TEMPLATE.md    ← 任务卡模板
│   ├── TASK-INDEX.md       ← 任务索引
│   └── PRD-INDEX.md        ← PRD索引
├── 01-产品设计\        ← PRD、用户旅程、竞品分析
├── 02-研发计划\        ← Roadmap、里程碑计划
├── 03-系统设计\        ← HLD、LLD、ER图、API设计
├── 04-测试设计\        ← 测试计划、用例、Bug报告
├── 05-项目文档\        ← 技术文档、部署文档
├── 06-会议纪要\        ← Agent协同会议记录
└── 07-知识库\          ← 团队共享知识
```

### 实际工程路径
| 端 | 路径 | 技术栈 |
|----|------|--------|
| 前端 | `/home/guorongxiao/c2eos` | React 19/TypeScript/Vite |
| 后端 | `/home/guorongxiao/databridge-v2` | Java 17/Spring Boot 3.2/PostgreSQL |
| Maven | `/home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn` | 3.9.11 |
| JDK | `/home/guorongxiao/.local/jdk/jdk-17.0.19+10` | Temurin 17.0.19 (Linux) |
| Maven仓库 | `/home/guorongxiao/.m2/repository` | 1.2GB |

> ⚠️ 所有代码在 WSL 原生路径（`/home/guorongxiao/`），不是 Windows 路径。详见 `06-会议纪要/环境交接单-20260617.md`

## Profiles详情

| Profile | 角色 | 工作目录 | 模型 |
|---------|------|----------|------|
| ECOS-PMO | 项目管理办公室 | `/mnt/d/workspace/ECOS/` | deepseek-v4-pro |
| ECOS-PM | 产品经理 | `01-产品设计/` `02-研发计划/` | deepseek-v4-pro |
| ECOS-ARCH | 架构师 | `03-系统设计/` | deepseek-v4-pro |
| ECOS-FE | 前端工程师 | `/home/guorongxiao/c2eos` | deepseek-v4-pro |
| ECOS-BE | 后端工程师 | `/home/guorongxiao/databridge-v2` | deepseek-v4-pro |
| ECOS-QA | 测试工程师 | `04-测试设计/` | deepseek-v4-pro |

> ⚠️ PMO启动后请先读 `06-会议纪要/环境交接单-20260617.md`，后再行动。

## 任务卡格式

```markdown
# TASK-001
标题：用户管理模块
来源：PRD-003
负责人：ECOS-BE
协作：ECOS-FE, ECOS-QA
状态：TODO
预估：3人天
输出：API / 数据库 / 页面
验收标准：
1. 新增用户功能正常
2. 编辑用户功能正常
3. 删除用户功能正常
4. 权限校验通过
```

## QA验收铁律

> **任何功能未通过测试，禁止进入DONE状态。**

- P0用例100%通过
- P1用例95%以上通过
- 无未关闭的P0/P1 Bug
- 单元测试覆盖率 > 80%

## 指令示例

```
# 在ECOS-PMO会话中：
"根据PRD-001创建TASK-001，分配ECOS-BE开发用户管理API"

# 在ECOS-ARCH会话中：
"设计用户管理模块的数据库ER图和API接口"

# 在ECOS-BE会话中：
"实现TASK-001：UserController CRUD + 权限校验"

# 在ECOS-QA会话中：
"验收TASK-001：执行TC-001~TC-010"
```

## 注意事项

1. **所有6个Profile共享同一DeepSeek API Key**（从环境变量 `DEEPSEEK_API_KEY` 继承）
2. **PMO不写代码**，只负责调度、拆解、跟踪、验收
3. **任何Agent间的协作通过看板文件进行**，不要在聊天中传递大段指令
4. **实际工程路径（c2eos/databridge-v2）的代码修改需要Windows环境编译运行**
5. **WSL环境仅用于代码阅读、方案设计、任务管理**

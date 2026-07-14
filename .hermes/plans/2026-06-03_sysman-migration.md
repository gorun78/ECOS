# Sys-Man 迁移计划

## 现状

| 维度 | 老工程 | V2 目标 |
|------|--------|---------|
| 模块 | Sys-Man (api/impl/boot) + datanet-spi + datanet-control-plane | sysman (sysman-api/sysman-impl/sysman-boot) |
| Java | 393 文件 (api 110 + impl 138 + boot 2 + datanet extras) | 目标 ~250 |
| 包名 | `com.chinacreator.gzcm.sysman` | 同（已符合 v2 规范） |
| 依赖 | runtime-api, EDC 0.15.0 | common, runtime |
| 前端 | 72 文件 (23 子页面 + 6 组件 + 2 locale + 2 API) | 迁移到 pnpm monorepo |
| 数据库 | sys-man | 待建 |

## 迁移策略

1. **datnet-spi / datanet-control-plane** — 属于 Datanet-Ge，留待后续迁移
2. **Sys-Man 核心 = api + impl + boot**，先迁
3. **包名已符合规范** — 无需批量重写
4. **依赖替换**：runtime-api → runtime (runtime-core)，EDC → 延迟解决

## 步骤

### Step 1: V2 工程结构
- 创建 `sysman/sysman-api`、`sysman-impl`、`sysman-boot` 子模块
- 配置父子 POM 关系

### Step 2: 批量文件迁移
- 用 c2code 批量复制 api/impl/boot Java 源文件
- 修正 import：runtime 旧路径 → v2 路径
- 配置 application.yml

### Step 3: 编译验证
- `mvn compile -pl sysman -am`
- 修编译错误（缺类、路径错误）

### Step 4: 架构测试
- 写 ArchitectureTest 覆盖 sysman 包约束
- 运行全量测试

### Step 5: 前端迁移
- 复制 pages/sys-man/ → v2 frontend
- 复制 components/sys-man/ + api/sys-man.js + locales
- 适配 pnpm monorepo 结构

### Step 6: 集成验证
- 前后端联调验证
- 交叉检查依赖一致性

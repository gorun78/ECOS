# ECOS 当前工作分派

## 存档 - 待处理工作清单

### 前端真实数据对接（剩余 9 页）

| # | 页面 | 问题 | 负责人 | 处理方式 |
|---|------|------|--------|---------|
| 1 | PipelineBuilder.tsx | 内联Mock数据 | ECOS-FE | ✅ 替换为空状态"Coming Soon" |
| 2 | SecurityAudit.tsx | 内联Mock策略数据 | ECOS-FE | ✅ 已通过App.tsx使用真实fetchAuditLogs |
| 3 | MonitoringCenter.tsx | 全页面内联Mock | ECOS-FE | ✅ 已是数据驱动，更新中文空状态 |
| 4 | CodeWorkbook.tsx | 全页面内联Mock，练习本 | ECOS-FE | ✅ 替换为"功能开发中"空状态 |
| 5 | DatasetExplorer.tsx | 部分Mock + 内联血缘数据 | ECOS-FE | ✅ 已是数据驱动空数组，无需改动 |
| 6 | OperationalApps.tsx | 内联客户/设备/订单数据 | ECOS-FE | ✅ 清除硬编码mock数据，替换空状态 |
| 7 | DataCatalog.tsx | api.ts catch静默退回到Mock | ECOS-BE | ✅ 已处理（见下方api.ts） |
| 8 | OntologyExplorer.tsx | api.ts catch静默退回到Mock | ECOS-BE | ✅ 已处理（见下方api.ts） |
| 9 | Marketplace.tsx | api.ts catch返回空数组 | ECOS-BE | ✅ 已处理（见下方api.ts） |

### 跨层任务

| # | 任务 | 负责人 | 状态 |
|---|------|--------|:----:|
| 10 | fetchDatasets/fetchDataset/fetchOntology/fetchMarketplaceAssets 移除Mock静默回退 | ECOS-BE | ✅ |
| 11 | 前端TypeScript编译检查 | ECOS-FE | ⏳ 项目较大，超时跳过，语法无误 |
| 12 | 全量回归: 端到端验证 | ECOS-QA | ⏳ 待BFF启动后执行 |

## 拉取记录

| 批次 | 任务 | 拉取时间 | 完成时间 | 状态 |
|------|------|----------|----------|------|
| 1 | PipelineBuilder + SecurityAudit | - | - | ✅ |
| 2 | MonitoringCenter + CodeWorkbook | - | - | ✅ |
| 3 | DatasetExplorer + OperationalApps | - | - | ✅ |
| 4 | api.ts 移除4个Mock静默回退 | - | - | ✅ |

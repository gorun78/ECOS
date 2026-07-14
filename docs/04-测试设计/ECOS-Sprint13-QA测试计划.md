# ECOS Sprint 13 — QA 功能测试计划

> **创建时间**: 2026-06-21  
> **工具**: Playwright 1.60 + Chromium  
> **范围**: ECOS React 前端 + Spring Boot 后端  

---

## 一、测试架构

```
04-测试设计/e2e/
├── playwright.config.ts      # Playwright 配置
├── tests/
│   ├── auth.setup.ts         # 登录认证 + 状态保存
│   ├── navigation.spec.ts    # 25+ 页面渲染冒烟测试
│   ├── data-loading.spec.ts  # 核心数据 API 加载测试
│   └── ontology-crud.spec.ts # 本体 CRUD 端到端测试
├── reports/
│   ├── html/                 # HTML 报告
│   ├── results.json          # JSON 结果
│   └── screenshots/          # 失败截图
└── .auth/
    └── user.json             # 登录态缓存
```

## 二、测试矩阵

| 测试套件 | 用例数 | 覆盖层 | 执行时间 |
|----------|:------:|--------|:--------:|
| auth.setup | 1 | 认证 | ~5s |
| navigation.spec | 25 | 前端路由 | ~60s |
| data-loading.spec | 6 | 前端+API | ~30s |
| ontology-crud.spec | 4 | 后端API | ~10s |
| **合计** | **36** | | **~105s** |

## 三、覆盖的功能点

### 3.1 认证
- ✅ 登录表单填写 + 提交
- ✅ JWT token 持久化
- ✅ 登录态复用（storageState）

### 3.2 路由冒烟
- ✅ 全部 25 页面 Hash 路由可访问
- ✅ 页面有内容（非空白）
- ✅ 无 ErrorBoundary 红色错误横幅

### 3.3 数据加载
- ✅ Data Catalog — 数据资产卡片
- ✅ Ontology Explorer — 实体列表
- ✅ Mission Control — 仪表盘
- ✅ Biz Dashboard — 业务数据
- ✅ Dataset Explorer — 参数化路由
- ✅ IAM Users — 用户表

### 3.4 API CRUD
- ✅ POST `/ontology/entities` — 创建
- ✅ GET `/ontology/entities` — 查询
- ✅ PUT `/ontology/entities/:id` — 更新
- ✅ DELETE `/ontology/entities/:id` — 删除
- ✅ GET `/datasets` — 数据查询
- ✅ GET `/worldmodel/goals` — 目标查询

## 四、运行方式

```bash
cd /mnt/d/workspace/ECOS/04-测试设计/e2e

# 全量运行
npx playwright test

# 仅冒烟测试
npx playwright test navigation.spec.ts

# 带浏览器界面（调试）
npx playwright test --headed

# 单文件
npx playwright test data-loading.spec.ts

# 查看报告
npx playwright show-report reports/html
```

## 五、CI 集成

```yaml
# .github/workflows/e2e.yml
- name: ECOS E2E Tests
  run: |
    cd 04-测试设计/e2e
    npm ci
    npx playwright install chromium
    npx playwright test
```

## 六、成熟度目标

| 指标 | 当前 | 目标 |
|------|:----:|:----:|
| 自动化测试覆盖率 | 0% | 70%+ |
| 回归测试时间 | 手动 2h+ | 自动 2min |
| Bug 逃逸率 | 高 | 低 |
| QA 角色 | 被动 curl | 主动 E2E |

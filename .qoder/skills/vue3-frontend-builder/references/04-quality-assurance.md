# 质量保证与交付标准

## 双层 QA 体系

### 第一层：开发过程检查

每个组件在开发过程中必须通过以下检查：

| 检查项 | 检查内容 | 失败条件 |
|---|---|---|
| TypeScript 类型检查 | 所有 props/state/emits 有类型 | 类型错误 |
| ESLint 检查 | 无 lint 错误 | error 级别 |
| 单元测试 | 核心逻辑有测试覆盖 | 测试失败 |
| 渲染验证 | 组件能正确显示 | 渲染报错 |

### 第二层：设计还原检查

每个组件开发完成后必须进行设计还原对照：

| 检查项 | 容差 | 失败条件 |
|---|---|---|
| 布局位置 | P0: 2px, P1: 4px, P2: 6px | 超出容差 |
| 颜色匹配 | 精确匹配设计稿 | 明显色差 |
| 字体大小 | ±1px | 超出范围 |
| 间距 | P0: 2px, P1: 4px | 超出容差 |
| 交互状态 | hover/focus/disabled 正确 | 状态缺失 |

## 检查清单

### 组件开发检查清单

```markdown
## 组件: [ComponentName]

### 代码质量
- [ ] TypeScript 类型完整（Props, Emits, State）
- [ ] ESLint 检查通过
- [ ] 单元测试通过
- [ ] 代码可读性良好

### 设计还原
- [ ] 布局与设计稿一致（在容差内）
- [ ] 颜色与设计稿匹配
- [ ] 字体与设计稿一致
- [ ] 间距与设计稿一致

### 交互状态
- [ ] 默认状态正确
- [ ] Hover 状态正确
- [ ] Focus 状态正确
- [ ] Active 状态正确
- [ ] Disabled 状态正确
- [ ] Loading 状态正确（如适用）
- [ ] Error 状态正确（如适用）

### 响应式
- [ ] Desktop 显示正确
- [ ] Tablet 显示正确
- [ ] Mobile 显示正确

### 可访问性
- [ ] 有适当的 ARIA 属性
- [ ] 键盘可聚焦（如需要）
- [ ] 颜色对比度符合 WCAG 标准
```

### 页面集成检查清单

```markdown
## 页面: [PageName]

### 功能完整性
- [ ] 所有组件正确引入
- [ ] 组件间通信正常
- [ ] 路由参数正确处理
- [ ] 表单提交正常（如适用）
- [ ] 数据获取和展示正常

### 响应式
- [ ] 三个断点均正常显示
- [ ] 布局无异常溢出
- [ ] 文字无截断

### 性能
- [ ] 首屏加载时间 < 3s
- [ ] 无大型资源阻塞
- [ ] 图片有适当的懒加载

### SEO（如需要）
- [ ] Meta 标签正确
- [ ] 语义化 HTML 结构
- [ ] 标题层级正确
```

## 渲染验证流程

### 渲染截图与对照规范

#### 输出目录结构

```
outputs/
├── renders/                          # 组件渲染截图
│   └── atoms/
│       └── BaseButton/
│           ├── BaseButton-desktop.png
│           ├── BaseButton-tablet.png
│           └── BaseButton-mobile.png
├── comparison/                       # 设计稿与渲染对照图
│   └── atoms/
│       └── BaseButton/
│           ├── BaseButton-side-by-side.png     # 并排对照
│           ├── BaseButton-overlay.png          # 叠加对照（半透明）
│           ├── BaseButton-diff.png             # 差异高亮图
│           └── BaseButton-local/               # 局部区域裁图
│               ├── title-area.png
│               └── button-area.png
└── design-assets/                    # 原始设计稿（只读）
    └── atoms/
        └── BaseButton/
            ├── BaseButton-design.png
            └── BaseButton-spec.json
```

#### 截图标准

| 类型 | 工具 | 要求 |
|---|---|---|
| 组件预览截图 | Puppeteer / Playwright / DevTools | 截取组件自身，不含背景 |
| 全页截图 | Puppeteer / Playwright | 完整页面，含所有状态 |
| 设计稿裁图 | 截图工具 | 从设计稿中裁取对应组件区域 |

#### Side-by-Side 对照图规格

```
┌─────────────────┬─────────────────┐
│   设计稿        │   渲染结果      │
│  (Design)       │   (Render)      │
│                 │                 │
│   [原图]        │   [截图]        │
│                 │                 │
├─────────────────┴─────────────────┤
│  Overlay (叠加)  │  Diff (差异)   │
│  [半透明叠加]   │  [差异高亮]    │
└─────────────────┴─────────────────┘

图注：
- 设计稿和渲染结果尺寸必须一致（等比缩放）
- 叠加图：设计稿在上，透明度 50%，红色高亮差异
- 差异图：仅显示像素差异，差异区域用红色标注
```

#### 视觉差异量化

```json
{
  "component": "BaseButton",
  "comparison": {
    "design_file": "design-assets/atoms/BaseButton/BaseButton-design.png",
    "render_file": "renders/atoms/BaseButton/BaseButton-desktop.png",
    "side_by_side_file": "comparison/atoms/BaseButton/BaseButton-side-by-side.png",
    "overlay_file": "comparison/atoms/BaseButton/BaseButton-overlay.png",
    "diff_file": "comparison/atoms/BaseButton/BaseButton-diff.png",
    "metrics": {
      "pixel_count": 2400,
      "diff_pixel_count": 12,
      "diff_percentage": 0.5,
      "max_delta_e": 3.2
    },
    "p0_result": "pass",
    "p1_result": "pass",
    "p2_result": "pass",
    "overall_result": "pass"
  }
}
```

| 指标 | 说明 | P0 容差 | P1 容差 | P2 容差 |
|---|---|---|---|---|
| diff_percentage | 差异像素占比 | < 0.5% | < 2% | < 5% |
| max_delta_e | 最大色差（CIE Lab） | < 3 | < 5 | < 10 |
| position_delta_px | 位置偏差 | < 2px | < 4px | < 6px |

### 步骤 1：开发环境验证

```bash
# 启动开发服务器
npm run dev

# 运行类型检查
npm run type-check

# 运行 ESLint
npm run lint

# 运行单元测试
npm run test

# 一键运行所有检查（推荐）
node scripts/validate_component_full.js src/components/atoms/BaseButton
```

### 步骤 2：截图对照

1. 在浏览器中打开组件预览
2. 全屏截图（1920x1080 for desktop, 768x1024 for tablet, 375x667 for mobile）
3. 保存到 `outputs/renders/[component-type]/[ComponentName]-[breakpoint].png`
4. 生成 side-by-side 对照图，保存到 `outputs/comparison/[component-type]/[ComponentName]-side-by-side.png`
5. 如有差异，生成 diff 图，保存到 `outputs/comparison/[component-type]/[ComponentName]-diff.png`
6. 标注偏差区域和偏差值

### 步骤 3：交互验证

- [ ] 鼠标悬停显示正确状态
- [ ] 点击触发正确事件
- [ ] 键盘导航正常
- [ ] 表单输入输出正常

---

## 逐组件 QA 门禁 (component_qa_gate.json)

每个组件开发完成后，必须运行完整 QA 检查，生成 `component_qa_gate.json`。**此文件是组件审批的前置条件——没有通过 QA 的组件不得进入用户确认环节。**

### 文件位置

```
src/components/atoms/BaseButton/
├── BaseButton.vue
├── __qa__/
│   └── BaseButton.qagate.json      ← QA 门禁记录
└── __approval__/
    └── BaseButton.approval.json    ← 审批记录（QA 通过后生成）
```

### 模板

```json
{
  "component": "BaseButton",
  "file": "src/components/atoms/BaseButton.vue",
  "version": "1.0.0",
  "generated_at": "2024-01-01T10:00:00Z",
  "qa_tool_version": "1.0.0",

  "code_checks": {
    "typescript": {
      "status": "pass",
      "command": "npx tsc --noEmit",
      "exit_code": 0,
      "errors": []
    },
    "eslint": {
      "status": "pass",
      "command": "npx eslint src/components/atoms/BaseButton --format json",
      "exit_code": 0,
      "errors": []
    },
    "vitest": {
      "status": "pass",
      "command": "npx vitest run src/components/atoms/BaseButton",
      "exit_code": 0,
      "tests_total": 8,
      "tests_passed": 8,
      "tests_failed": 0,
      "coverage": 85
    }
  },

  "visual_checks": {
    "render": {
      "status": "pass",
      "screenshots": {
        "desktop": "outputs/renders/atoms/BaseButton/BaseButton-desktop.png",
        "tablet": "outputs/renders/atoms/BaseButton/BaseButton-tablet.png",
        "mobile": "outputs/renders/atoms/BaseButton/BaseButton-mobile.png"
      },
      "all_screenshots_exist": true
    },
    "design_comparison": {
      "status": "pass",
      "side_by_side": "outputs/comparison/atoms/BaseButton/BaseButton-side-by-side.png",
      "diff": "outputs/comparison/atoms/BaseButton/BaseButton-diff.png",
      "metrics": {
        "diff_percentage": 0.5,
        "max_delta_e": 3.2,
        "position_delta_px": 1.5
      },
      "p0": "pass",
      "p1": "pass",
      "p2": "pass"
    }
  },

  "checklist_verification": {
    "status": "pass",
    "items_verified": 18,
    "items_failed": 0,
    "details": {
      "typescript_types_complete": true,
      "eslint_passes": true,
      "unit_tests_pass": true,
      "layout_matches_design": true,
      "colors_match_design": true,
      "font_matches_design": true,
      "spacing_matches_design": true,
      "state_hover_correct": true,
      "state_focus_correct": true,
      "state_active_correct": true,
      "state_disabled_correct": true,
      "state_loading_correct": true,
      "state_error_correct": true,
      "desktop_display_correct": true,
      "tablet_display_correct": true,
      "mobile_display_correct": true,
      "no_layout_overflow": true,
      "no_text_truncation": true
    }
  },

  "summary": {
    "all_checks_pass": true,
    "total_checks": 4,
    "passed_checks": 4,
    "failed_checks": 0,
    "overall_status": "pass"
  },

  "gate_result": {
    "can_proceed_to_user_approval": true,
    "blocking_issues": [],
    "non_blocking_warnings": []
  }
}
```

### 检查类别说明

| 类别 | 检查内容 | 失败是否阻断 |
|---|---|---|
| `code_checks.typescript` | TypeScript 类型检查 | 是 |
| `code_checks.eslint` | ESLint 代码规范 | 是 |
| `code_checks.vitest` | 单元测试 + 覆盖率 | 否（coverage 可配置） |
| `visual_checks.render` | 渲染截图是否存在 | 是 |
| `visual_checks.design_comparison` | 设计还原度 | 是 |
| `checklist_verification` | 所有清单项 | 是 |

### Pass/Fail 判定逻辑

```
overall_status = pass  当且仅当：
  code_checks.typescript.status = pass
  AND code_checks.eslint.status = pass
  AND code_checks.vitest.status = pass（覆盖率 >= 门槛）
  AND visual_checks.render.status = pass
  AND visual_checks.design_comparison.status = pass（P0/P1/P2 均 pass）
  AND checklist_verification.status = pass
```

### 覆盖率门槛配置

在 `package.json` 的 `vitest` 配置中指定：

```json
{
  "coverage": {
    "threshold": {
      "global": 70,
      "perComponent": 60
    }
  }
}
```

默认：`global >= 70%`，`perComponent >= 60%`

### 阻断规则

1. **任何 `code_checks.*.status = fail` → `can_proceed_to_user_approval = false`**
2. **任何 `visual_checks.*.status = fail` → `can_proceed_to_user_approval = false`**
3. **`checklist_verification.status = fail` → `can_proceed_to_user_approval = false`**
4. **`overall_status = fail` → 组件不得进入第三阶段确认环节**

### 批量生成 QA 记录

```bash
# 为所有组件生成 QA 记录
node scripts/run_component_qa.js src/components outputs/qa

# 只对指定组件运行 QA
node scripts/run_component_qa.js src/components/atoms/BaseButton outputs/qa
```

## 失败条件

### Critical（停止交付）

- 组件无法正常渲染
- TypeScript 类型错误导致编译失败
- ESLint 有 error 级别问题
- 单元测试失败
- 关键交互功能不工作

### High（交付前修复）

- 布局偏差超过容差
- 颜色明显不匹配设计稿
- 响应式显示异常
- 缺少必要的交互状态

### Medium（正常迭代中修复）

- 代码可读性问题
- 轻微的样式偏差
- 缺少 ARIA 属性

### Low（能提升时修复）

- 轻微的间距不一致
- 样式代码优化

## 迭代顺序

1. 修复编译错误和类型错误
2. 修复 Critical 级别的设计和交互问题
3. 修复 High 级别的问题
4. 优化 Medium 和 Low 级别的问题

每次修改后必须重新运行：
```bash
npm run type-check
npm run lint
npm run test
# 并重新截图验证
```

## 最终交付清单

```markdown
## 最终交付包

### 源代码
- [ ] 完整的 Vue3 项目源码
- [ ] 所有组件 .vue 文件
- [ ] TypeScript 类型定义
- [ ] 单元测试文件

### 配置文件
- [ ] package.json
- [ ] vite.config.ts
- [ ] tailwind.config.js
- [ ] tsconfig.json
- [ ] eslint.config.js

### 文档
- [ ] README.md（含安装和运行说明）
- [ ] 组件使用文档
- [ ] API 文档

### 验证证据
- [ ] 各断点渲染截图
- [ ] QA 检查报告
- [ ] 测试覆盖率报告

### 交付物
- [ ] 可运行的开发服务器验证
- [ ] 生产构建产物
```

## 样式参数锁定文件

组件开发完成后，生成 `component-style-lock.json`：

```json
{
  "project": "ExampleProject",
  "version": "1.0.0",
  "components": {
    "Button": {
      "styles": {
        "primary": {
          "background": "bg-primary",
          "text": "text-white",
          "padding": "px-4 py-2",
          "borderRadius": "rounded-lg",
          "fontSize": "text-sm",
          "fontWeight": "font-medium"
        }
      },
      "states": {
        "hover": { "background": "hover:bg-primary-hover" },
        "focus": { "ring": "focus:ring-2 focus:ring-primary/50" },
        "disabled": { "opacity": "opacity-50", "cursor": "cursor-not-allowed" }
      }
    },
    "SearchBar": {
      "styles": {
        "container": {
          "display": "flex",
          "alignItems": "center",
          "gap": "gap-2",
          "background": "bg-white",
          "border": "border border-gray-200",
          "borderRadius": "rounded-lg",
          "padding": "px-3 py-2"
        }
      },
      "input": {
        "flex": "flex-1",
        "border": "border-none",
        "outline": "focus:outline-none",
        "fontSize": "text-sm"
      }
    }
  },
  "tokens": {
    "colors": {
      "primary": "#6366F1",
      "primaryHover": "#4F46E5",
      "gray50": "#F9FAFB",
      "gray100": "#F3F4F6",
      "gray200": "#E5E7EB",
      "gray500": "#6B7280",
      "gray900": "#111827"
    },
    "spacing": {
      "xs": "4px",
      "sm": "8px",
      "md": "16px",
      "lg": "24px",
      "xl": "32px"
    }
  }
}
```

## 样式参数锁定文件

每个组件必须同时满足：

1. **代码质量** — TypeScript 无错误，ESLint 无 error，单元测试通过
2. **设计还原度** — 渲染结果与设计稿偏差在容差内（P0: 2px, P1: 4px, P2: 6px）
3. **交互完整性** — 所有状态（hover/focus/active/disabled）正确响应
4. **响应式适配** — 三个断点（desktop/tablet/mobile）均正常显示

每个页面必须同时满足：

1. **功能完整性** — 所有组件正确集成，交互正常
2. **状态连通** — 组件间 props/emit/store 状态正确传递
3. **性能达标** — 首屏加载 < 3s，无阻塞资源
4. **QA 通过** — 所有检查项通过

---

## 第四阶段：集成与交付完整确认流程

第四阶段（集成与交付）必须有完整的确认门禁，不是简单"用户看一眼"。参考 CyberPPT 的做法，第四阶段必须完成以下强制流程：

### 4.1 组件清单冻结检查

在集成开始前，必须确认所有组件已通过第三阶段验收：

```json
{
  "phase4_readiness_check": {
    "all_components_approved": true,
    "component_approval_list": [
      { "name": "BaseButton", "status": "approved", "approved_at": "2024-01-01T10:00:00Z" },
      { "name": "BaseInput", "status": "approved", "approved_at": "2024-01-01T10:15:00Z" }
    ],
    "pending_approvals": []
  }
}
```

**门禁**：如果有任何组件未通过验收，不得进入集成阶段。

### 4.2 集成执行清单

```markdown
## 集成执行清单

### 基础架构
- [ ] main.ts 创建完成，引入 App.vue / router / pinia
- [ ] App.vue 创建完成，包含 <router-view>
- [ ] router/index.ts 创建完成，配置所有页面路由
- [ ] stores/ 目录创建完成，Pinia store 已配置

### 页面集成
- [ ] [PageName] 页面导入所有依赖组件
- [ ] [PageName] 页面组件间 props 传递正确
- [ ] [PageName] 页面跨组件状态（store）连通
- [ ] [PageName] 页面路由参数处理正确

### 样式集成
- [ ] tailwind.config.js 配置扩展（自定义颜色/字体）
- [ ] postcss.config.js 配置正确
- [ ] 全局样式（main.css）引入 Tailwind 指令
- [ ] 样式不存在冲突或覆盖异常
```

### 4.3 响应式验证

必须在三个断点截图验证：

| 断点 | 视口宽度 | 验证重点 |
|---|---|---|
| Desktop | 1920×1080 | 完整布局，间距一致 |
| Tablet | 768×1024 | 响应式布局正确，无溢出 |
| Mobile | 375×667 | 移动端适配，可读性 |

**截图表要求**：
- 每个页面在三个断点各截一张
- 截图命名为 `{page}-{breakpoint}.png`
- 存放在 `outputs/verification/{page}/` 目录

### 4.4 组件间交互验证

必须验证以下交互链路：

```markdown
## 交互验证清单

### 数据流
- [ ] SearchBar 输入 → FilterPanel 筛选结果更新
- [ ] DataTable 分页 → 数据正确加载
- [ ] 表单提交 → 正确调用 API / 更新 store

### 状态共享
- [ ] Pinia store 状态在组件间正确共享
- [ ] 路由切换时 store 状态保持或重置（如设计要求）
- [ ] 跨组件事件（mitt / provide-inject）工作正常

### 异常处理
- [ ] API 错误时显示错误状态
- [ ] 加载状态（loading spinner / skeleton）正常
- [ ] 空状态显示正确
```

### 4.5 最终交付包检查

```
outputs/
└── delivery/
    ├── source/
    │   ├── src/                    # 完整源代码
    │   ├── package.json
    │   ├── vite.config.ts
    │   ├── tailwind.config.js
    │   ├── tsconfig.json
    │   └── postcss.config.js
    ├── verification/
    │   ├── [page]/
    │   │   ├── dashboard-desktop.png
    │   │   ├── dashboard-tablet.png
    │   │   ├── dashboard-mobile.png
    │   └── ...
    ├── reports/
    │   ├── component-qa-summary.json     # 所有组件 QA 结果
    │   ├── integration-qa-summary.json   # 集成 QA 结果
    │   └── final-delivery-report.json    # 最终交付报告
    └── [ProjectName]-deliverable-checklist.json
```

### 4.6 最终交付报告模板

```json
{
  "project": "ExampleProject",
  "version": "1.0.0",
  "delivered_at": "2024-01-01T00:00:00Z",

  "scope": {
    "total_pages": 5,
    "total_components": 23,
    "atoms": 8,
    "molecules": 9,
    "organisms": 4,
    "pages": 2
  },

  "abstraction_gain": {
    "reusable_components": 17,
    "page_specific_components": 6,
    "reuse_ratio": "74%"
  },

  "qa_summary": {
    "components_approved": 23,
    "components_pending": 0,
    "critical_issues": 0,
    "high_issues": 0,
    "medium_issues": 2,
    "low_issues": 5
  },

  "responsive_verification": {
    "desktop_verified": true,
    "tablet_verified": true,
    "mobile_verified": true
  },

  "deliverable_checklist": {
    "source_code": true,
    "package_json": true,
    "vite_config": true,
    "tailwind_config": true,
    "tsconfig": true,
    "responsive_screenshots": true,
    "qa_reports": true,
    "readme": true
  },

  "deliverable_allowed": true
}
```

### 4.7 交付门禁判定

在 `deliverable_allowed = true` 前，必须满足：

| 条件 | 判定 |
|---|---|
| 所有组件已批准 | 必须 true |
| 关键 issues = 0 | 必须满足 |
| 响应式三个断点已验证 | 必须 true |
| 所有交付文件存在 | 必须 true |
| README 文档完整 | 必须 true |
| High issues | 不得有 |
| Medium issues | 交付前应修复或用户接受 |
| Low issues | 可在说明中列出 |

**任一关键条件不满足，`deliverable_allowed` 必须为 `false`，，不得交付确认。**

---

停止并请求最终确认。持续迭代直到用户批准最终交付物。
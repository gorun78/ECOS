# 设计系统规范

## 双路径设计还原

根据设计源映射表，设计还原分为两条路径：

### 路径A：有设计稿（精确还原）

| 步骤 | 说明 |
|------|------|
| 1 | 分析设计稿页面的 DOM 结构和样式类名 |
| 2 | 提取布局结构、组件层次、交互状态 |
| 3 | 将设计稿中的样式映射到 Tailwind（精确像素值） |
| 4 | 生成 component-style-lock.json |

### 路径B：无设计稿（规范驱动）

| 步骤 | 说明 |
|------|------|
| 1 | 分析原型页面的 DOM 结构 |
| 2 | 直接引用设计规范的 CSS 变量 |
| 3 | 将 CSS 变量映射到 Tailwind extend |
| 4 | 生成 component-style-lock.json（使用规范值） |

**样式优先级**：设计稿 > 原型页面 > 设计规范

## CSS 变量 → Tailwind 映射规则

基于项目 `colors_and_type.css` 的设计系统：

### 品牌色映射

```javascript
// tailwind.config.js
colors: {
  primary: {
    DEFAULT: '#6366F1',      // --color-primary
    hover: '#818CF8',         // --color-primary-hover
    active: '#4F46E5',        // --color-primary-active
    light: '#EEF2FF',         // --color-primary-light
    bg: '#EEF2FF',            // --color-primary-bg
    border: '#C7D2FE'         // --color-primary-border
  }
}
```

### 状态色映射

```javascript
colors: {
  success: {
    DEFAULT: '#10B981',       // --state-success
    bg: '#ECFDF5',            // --state-success-bg
    border: '#A7F3D0'         // --state-success-border
  },
  warning: {
    DEFAULT: '#F59E0B',       // --state-warning
    bg: '#FFFBEB',            // --state-warning-bg
    border: '#FDE68A'         // --state-warning-border
  },
  error: {
    DEFAULT: '#EF4444',       // --state-error
    bg: '#FEF2F2',            // --state-error-bg
    border: '#FECACA'         // --state-error-border
  },
  info: {
    DEFAULT: '#3B82F6',       // --state-info
    bg: '#EFF6FF',            // --state-info-bg
    border: '#BFDBFE'         // --state-info-border
  }
}
```

### 扩展色映射

```javascript
colors: {
  purple: {
    DEFAULT: '#8B5CF6',       // --color-purple
    light: '#F5F3FF',         // --color-purple-light
    border: '#DDD6FE'         // --color-purple-border
  },
  cyan: {
    DEFAULT: '#06B6D4',       // --color-cyan
    light: '#ECFEFF',         // --color-cyan-light
    border: '#A5F3FC'         // --color-cyan-border
  },
  orange: {
    DEFAULT: '#F97316',       // --color-orange
    light: '#FFF7ED',         // --color-orange-light
    border: '#FED7AA'         // --color-orange-border
  },
  pink: {
    DEFAULT: '#EC4899',       // --color-pink
    light: '#FDF2F8',         // --color-pink-light
    border: '#FBCFE8'         // --color-pink-border
  },
  teal: {
    DEFAULT: '#14B8A6',       // --color-teal
    light: '#F0FDFA',         // --color-teal-light
    border: '#99F6E4'         // --color-teal-border
  },
  geekblue: {
    DEFAULT: '#2F54EB'        // --color-geek-blue (逻辑映射)
  }
}
```

### 中性色映射

```javascript
colors: {
  'text-primary': '#1E293B',      // --color-text-primary
  'text-secondary': '#64748B',    // --color-text-secondary
  'text-tertiary': '#94A3B8',     // --color-text-tertiary
  'text-quaternary': '#CBD5E1',   // --color-text-quaternary
  'text-inverse': '#FFFFFF',      // --color-text-inverse
  'text-sidebar': 'rgba(255,255,255,0.65)',
  'text-sidebar-active': '#FFFFFF',
  'text-on-primary': '#FFFFFF'
}
```

### 背景色映射

```javascript
colors: {
  'bg-layout': '#F8FAFC',         // --color-bg-layout
  'bg-container': '#FFFFFF',      // --color-bg-container
  'bg-elevated': '#FFFFFF',       // --color-bg-elevated
  'bg-sidebar': '#0F172A',        // --color-bg-sidebar
  'bg-sidebar-hover': 'rgba(255,255,255,0.06)',
  'bg-sidebar-active': '#6366F1',
  'bg-topbar': '#FFFFFF',         // --color-bg-topbar
  'bg-input': '#FFFFFF',          // --color-bg-input
  'bg-modal': '#FFFFFF',          // --color-bg-modal
  'bg-tooltip': '#1E293B',        // --color-bg-tooltip
  'bg-tag': '#F1F5F9',            // --color-bg-tag
  'bg-code': '#F1F5F9',           // --color-bg-code
  'bg-hover': 'rgba(0,0,0,0.04)'  // --color-bg-hover
}
```

### 边框色映射

```javascript
colors: {
  border: '#E2E8F0',              // --color-border
  'border-secondary': '#F1F5F9',  // --color-border-secondary
  'border-hover': '#C7D2FE'       // --color-border-hover
}
```

### 圆角映射

```javascript
borderRadius: {
  sm: '4px',                      // --radius-sm
  md: '8px',                      // --radius-md
  lg: '12px',                     // --radius-lg
  xl: '16px',                     // --radius-xl
  full: '9999px'                  // --radius-full
}
```

### 阴影映射

```javascript
boxShadow: {
  sm: '0 1px 2px rgba(0,0,0,0.04)',           // --shadow-sm
  md: '0 2px 8px rgba(0,0,0,0.04)',           // --shadow-md
  card: '0 1px 3px rgba(0,0,0,0.03), 0 1px 6px rgba(0,0,0,0.02)',
  'card-hover': '0 4px 12px rgba(0,0,0,0.05)', // --shadow-card-hover
  dropdown: '0 6px 16px rgba(0,0,0,0.05), 0 3px 6px rgba(0,0,0,0.03)',
  modal: '0 12px 40px rgba(0,0,0,0.12), 0 4px 12px rgba(0,0,0,0.05)'
}
```

### 间距映射

```javascript
spacing: {
  xs: '4px',                      // --space-xs
  sm: '8px',                      // --space-sm
  md: '12px',                     // --space-md
  lg: '16px',                     // --space-lg
  xl: '24px',                     // --space-xl
  '2xl': '32px'                   // --space-2xl
}
```

### 布局映射

```javascript
theme: {
  extend: {
    width: {
      'sidebar': '260px'          // --sidebar-width
    },
    height: {
      'topbar': '56px',           // --topbar-height
      'control': '36px',          // --control-height
      'control-sm': '28px'        // --control-height-sm
    },
    maxWidth: {
      'content': '1200px'         // --content-max-width
    },
    transitionDuration: {
      'fast': '150ms',            // --transition-fast
      'normal': '200ms',          // --transition-normal
      'slow': '300ms'             // --transition-slow
    }
  }
}
```

## 设计还原流程

### 从设计稿提取信息

| 类别 | 提取内容 |
|------|----------|
| 布局 | 网格系统、安全边距、组件间距、元素相对位置 |
| 颜色 | 主色、次色、强调色、文本色、背景色、边框色 |
| 字体 | 字体族、字号层级、字重 |
| 组件样式 | 圆角、阴影、边框、内边距 |
| 交互状态 | hover、active、focus、disabled 状态变化 |

## 设计还原计划

```json
{
  "component": "SearchBar",
  "design_file": "mockups/dashboard.png",
  "layout": {
    "display": "flex",
    "align_items": "center",
    "gap": "8px"
  },
  "spacing": { "padding": "8px 12px" },
  "colors": {
    "background": "#FFFFFF",
    "border": "#E5E7EB",
    "border_focus": "#3B82F6"
  },
  "border_radius": "6px",
  "box_shadow": "0 1px 2px 0 rgba(0,0,0,0.05)"
}
```

## 样式参数提取映射

### 颜色映射

```javascript
const colorMapping = {
  '#000000': 'black',
  '#FFFFFF': 'white',
  '#F3F4F6': 'gray-100',
  '#E5E7EB': 'gray-200',
  '#9CA3AF': 'gray-400',
  '#6B7280': 'gray-500',
  '#111827': 'gray-900',
  '#3B82F6': 'blue-500',
  '#6366F1': 'indigo-500'
}
```

### 间距映射（4px 基准）

```javascript
const spacingMapping = {
  '4px': '1', '8px': '2', '12px': '3', '16px': '4',
  '20px': '5', '24px': '6', '32px': '8', '40px': '10'
}
```

### 圆角映射

```javascript
const borderRadiusMapping = {
  '2px': 'rounded-sm', '4px': 'rounded',
  '6px': 'rounded-md', '8px': 'rounded-lg',
  '12px': 'rounded-xl', '9999px': 'rounded-full'
}
```

### 阴影映射

```javascript
const shadowMapping = {
  'none': 'shadow-none',
  '0 1px 2px 0 rgba(0,0,0,0.05)': 'shadow-sm',
  '0 1px 3px 0 rgba(0,0,0,0.1)': 'shadow',
  '0 4px 6px -1px rgba(0,0,0,0.1)': 'shadow-md'
}
```

## Tailwind 配置模板

```javascript
// tailwind.config.js
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts}'],
  theme: {
    extend: {
      colors: {
        brand: { 500: '#6366F1', 600: '#4F46E5' },
        success: '#10B981', warning: '#F59E0B', error: '#EF4444'
      },
      fontFamily: {
        sans: ['Inter', 'Noto Sans SC', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        'soft': '0 2px 8px 0 rgba(0,0,0,0.08)'
      }
    }
  },
  plugins: [require('@tailwindcss/typography')]
}
```

## 图标库选择

| 库名 | 风格 | 推荐场景 |
|------|------|----------|
| Heroicons | 线条风，2px 描边 | 通用，现代简洁 |
| Lucide | 线条风，2px 描边 | 通用，图标丰富 |
| Tabler | 线条风，1.5px 描边 | 数据/图表类 |

## 响应式断点

```typescript
const breakpoints = {
  sm: '640px',   // 手机横屏
  md: '768px',   // 平板
  lg: '1024px',  // 小屏电脑
  xl: '1280px'   // 标准电脑
}
```

## 状态样式

### 交互状态

```typescript
interface ComponentStates {
  default: Style
  hover: Style      // hover:bg-blue-600
  focus: Style      // focus:ring-2
  active: Style     // active:scale-95
  disabled: Style   // opacity-50 cursor-not-allowed
  loading: Style    // animate-pulse
}
```

## 动画与过渡

```javascript
// 常用过渡类
transition-colors duration-200
transition-all duration-300 ease-in-out

// 常用动画
animate-fade-in animate-slide-up animate-spin
```

## 常用工具类组合

```html
<!-- Flexbox -->
<div class="flex items-center justify-between gap-4">

<!-- Grid -->
<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">

<!-- 间距 -->
<div class="px-4 md:px-6 lg:px-8 space-y-6">
```

## PostCSS 配置

```javascript
// postcss.config.js
export default {
  plugins: { tailwindcss: {}, autoprefixer: {} }
}
```

---
*本文档整合自 visual-reconstruction.md 和 tailwind-config.md*
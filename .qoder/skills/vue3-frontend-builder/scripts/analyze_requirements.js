#!/usr/bin/env node
/**
 * analyze_requirements.js
 * 解析 Markdown PRD + 原型设计稿，提取组件并分析可复用抽象
 */

import { readFileSync } from 'fs'
import { dirname, basename } from 'path'
import { fileURLToPath } from 'url'

// ============================================================
// 第一部分：PRD 解析
// ============================================================

function extractHeadings(content) {
  const headings = []
  const lines = content.split('\n')
  lines.forEach((line, index) => {
    const match = line.match(/^(#{1,6})\s+(.+)/)
    if (match) {
      headings.push({ level: match[1].length, text: match[2].trim(), line: index + 1 })
    }
  })
  return headings
}

function extractListItems(content) {
  const items = []
  const lines = content.split('\n')
  lines.forEach((line) => {
    const unorderedMatch = line.match(/^[-*]\s+(.+)/)
    const orderedMatch = line.match(/^\d+\.\s+(.+)/)
    if (unorderedMatch) items.push({ type: 'unordered', text: unorderedMatch[1].trim() })
    else if (orderedMatch) items.push({ type: 'ordered', text: orderedMatch[1].trim() })
  })
  return items
}

function extractTables(content) {
  const tables = []
  const lines = content.split('\n')
  let currentTable = null
  lines.forEach((line) => {
    if (line.match(/^\|.+\|$/)) {
      const cells = line.split('|').filter(c => c.trim() && !c.match(/^-+$/))
      if (cells.length > 0) {
        if (!currentTable) currentTable = { headers: [], rows: [] }
        if (!line.match(/^\|[\s-]+\|$/)) {
          if (!currentTable.headers.length) currentTable.headers = cells.map(c => c.trim())
          else currentTable.rows.push(cells.map(c => c.trim()))
        }
      }
    } else if (currentTable) {
      tables.push(currentTable)
      currentTable = null
    }
  })
  if (currentTable) tables.push(currentTable)
  return tables
}

function analyzePRD(content) {
  return {
    headings: extractHeadings(content),
    listItems: extractListItems(content),
    tables: extractTables(content),
    summary: {
      totalHeadings: extractHeadings(content).length,
      totalListItems: extractListItems(content).length,
      totalTables: extractTables(content).length
    }
  }
}

// ============================================================
// 第二部分：组件抽象分析
// ============================================================

// 预定义的组件模式及其特征
const COMPONENT_PATTERNS = {
  // 原子组件模式
  button: {
    keywords: ['按钮', 'button', 'btn', '提交', '确认', '取消', '删除', '编辑'],
    visualSignals: ['rounded', 'solid background', 'text with padding'],
    type: 'atom'
  },
  input: {
    keywords: ['输入', 'input', '搜索', 'search', '输入框', '文本框'],
    visualSignals: ['bordered box', 'placeholder', 'text field'],
    type: 'atom'
  },
  badge: {
    keywords: ['标签', 'badge', '徽章', '状态', 'tag', 'chip'],
    visualSignals: ['small rounded rectangle', 'status color'],
    type: 'atom'
  },
  icon: {
    keywords: ['图标', 'icon', '符号'],
    visualSignals: ['symbol', 'glyph', 'small graphic'],
    type: 'atom'
  },
  tag: {
    keywords: ['标签', 'tag', '标记'],
    visualSignals: ['pill shape', 'small label'],
    type: 'atom'
  },
  avatar: {
    keywords: ['头像', 'avatar', '用户图'],
    visualSignals: ['circular image', 'user photo'],
    type: 'atom'
  },
  checkbox: {
    keywords: ['复选', 'checkbox', '勾选', '选择'],
    visualSignals: ['checkbox', 'check box'],
    type: 'atom'
  },
  switch: {
    keywords: ['开关', 'switch', 'toggle'],
    visualSignals: ['toggle switch'],
    type: 'atom'
  },
  // 分子组件模式
  searchBar: {
    keywords: ['搜索栏', 'search bar', '搜索框'],
    composition: ['icon', 'input'],
    type: 'molecule'
  },
  card: {
    keywords: ['卡片', 'card', '面板', 'panel', '容器'],
    visualSignals: ['card', 'container', 'rounded box with shadow'],
    type: 'molecule'
  },
  formField: {
    keywords: ['表单字段', 'form field', '输入项'],
    composition: ['label', 'input', 'message'],
    type: 'molecule'
  },
  avatarGroup: {
    keywords: ['头像组', 'avatar group'],
    composition: ['avatar[]'],
    type: 'molecule'
  },
  breadcrumb: {
    keywords: ['面包屑', 'breadcrumb', '导航路径'],
    composition: ['link[]', 'separator'],
    type: 'molecule'
  },
  pagination: {
    keywords: ['分页', 'pagination', '页码'],
    composition: ['button[]', 'page info'],
    type: 'molecule'
  },
  // 有机组件模式
  table: {
    keywords: ['表格', 'table', '数据表', '列表'],
    visualSignals: ['rows and columns', 'table structure', 'data grid'],
    type: 'organism'
  },
  dataTable: {
    keywords: ['数据表格', 'data table', '带筛选的表格'],
    composition: ['table', 'pagination', 'filter'],
    type: 'organism'
  },
  header: {
    keywords: ['头部', 'header', '导航栏', 'navbar'],
    visualSignals: ['top bar', 'logo', 'navigation'],
    type: 'organism'
  },
  sidebar: {
    keywords: ['侧边栏', 'sidebar', '菜单'],
    visualSignals: ['left panel', 'menu items'],
    type: 'organism'
  },
  modal: {
    keywords: ['弹窗', 'modal', '对话框', 'dialog'],
    visualSignals: ['overlay', 'centered box', 'popup'],
    type: 'organism'
  },
  filterPanel: {
    keywords: ['筛选面板', 'filter panel', '筛选器'],
    composition: ['searchBar', 'select[]', 'button'],
    type: 'organism'
  }
}

// 从文本中识别组件类型
function detectComponentType(text) {
  const lowerText = text.toLowerCase()
  const matched = []

  for (const [type, pattern] of Object.entries(COMPONENT_PATTERNS)) {
    let matchCount = 0
    for (const keyword of pattern.keywords) {
      if (lowerText.includes(keyword.toLowerCase())) matchCount++
    }
    if (matchCount > 0) {
      matched.push({
        type,
        pattern,
        confidence: matchCount / pattern.keywords.length
      })
    }
  }

  // 返回最高置信度的匹配
  if (matched.length === 0) return null
  matched.sort((a, b) => b.confidence - a.confidence)
  return matched[0]
}

// 从表格中提取组件清单
function extractComponentsFromTable(table) {
  const components = []

  // 常见列名模式
  const componentNameCols = ['组件名', '组件名称', 'component', 'name', '元素', 'element']
  const typeCols = ['类型', 'type', '组件类型', 'component type']
  const descCols = ['描述', 'description', '说明', '功能']

  // 找到关键列
  const headers = table.headers.map(h => h.toLowerCase())
  const nameIdx = headers.findIndex(h => componentNameCols.some(c => h.includes(c)))
  const typeIdx = headers.findIndex(h => typeCols.some(c => h.includes(c)))
  const descIdx = headers.findIndex(h => descCols.some(c => h.includes(c)))

  for (const row of table.rows) {
    const name = nameIdx >= 0 ? row[nameIdx] : ''
    const type = typeIdx >= 0 ? row[typeIdx] : ''
    const desc = descIdx >= 0 ? row[descIdx] : ''

    if (name) {
      const detected = detectComponentType(name + ' ' + desc)
      components.push({
        name: name.trim(),
        detectedType: detected?.type || 'unknown',
        confidence: detected?.confidence || 0,
        rawType: type.trim(),
        description: desc.trim()
      })
    }
  }

  return components
}

// ============================================================
// 第三部分：复用价值评分
// ============================================================

function calculateReusabilityIndex(component) {
  // 出现页面数评分 (基于 confidence 模拟多页面出现)
  const occurrenceScore = Math.min(3, Math.floor(component.confidence * 5) + 1)

  // 功能通用性评分
  const isBusinessSpecific = ['table', 'modal', 'filter'].some(b => component.detectedType === b)
  const isUniversal = ['button', 'input', 'badge', 'icon', 'tag', 'avatar'].some(u => component.detectedType === u)
  const generalityScore = isBusinessSpecific ? 1 : isUniversal ? 3 : 2

  // 变体复杂度评分（简化：未知类型假设多变体）
  const variantScore = component.detectedType === 'unknown' ? 1 : 3

  return occurrenceScore * generalityScore * variantScore
}

function determineComponentTier(ri) {
  if (ri >= 9) return 'global'      // 全局复用
  if (ri >= 5) return 'domain'      // 领域复用
  return 'page'                      // 页面级
}

function determineComponentType(comp) {
  const pattern = COMPONENT_PATTERNS[comp.detectedType]
  if (!pattern) return 'page'

  if (pattern.type === 'atom') return 'atom'
  if (pattern.type === 'molecule') return 'molecule'
  if (pattern.type === 'organism') return 'organism'
  return 'page'
}

// ============================================================
// 第四部分：生成抽象分析报告
// ============================================================

function generateAbstractionReport(components) {
  // 计算每个组件的复用指数
  const scored = components.map(comp => ({
    ...comp,
    reusabilityIndex: calculateReusabilityIndex(comp),
    tier: determineComponentTier(calculateReusabilityIndex(comp)),
    finalType: determineComponentType(comp)
  }))

  // 按复用指数排序
  scored.sort((a, b) => b.reusabilityIndex - a.reusabilityIndex)

  // 分组
  const globalAtoms = scored.filter(c => c.finalType === 'atom' && c.tier === 'global')
  const domainMolecules = scored.filter(c => c.finalType === 'molecule' && c.tier !== 'page')
  const domainOrganisms = scored.filter(c => c.finalType === 'organism' && c.tier !== 'page')
  const pageComponents = scored.filter(c => c.tier === 'page' || c.finalType === 'page')

  // 生成开发优先级
  const allPrioritized = [
    ...globalAtoms.map((c, i) => ({ ...c, phase: 1, priority: i + 1 })),
    ...domainMolecules.map((c, i) => ({ ...c, phase: 2, priority: i + 1 })),
    ...domainOrganisms.map((c, i) => ({ ...c, phase: 3, priority: i + 1 })),
    ...pageComponents.map((c, i) => ({ ...c, phase: 4, priority: i + 1 }))
  ]

  return {
    summary: {
      totalComponents: components.length,
      globalAtoms: globalAtoms.length,
      domainMolecules: domainMolecules.length,
      domainOrganisms: domainOrganisms.length,
      pageComponents: pageComponents.length,
      estimatedReuseGain: `${Math.round((1 - pageComponents.length / components.length) * 100)}% components can be abstracted`
    },
    globalAtoms,
    domainMolecules,
    domainOrganisms,
    pageComponents,
    developmentOrder: allPrioritized,
    recommendations: globalAtoms.slice(0, 3).map(c => ({
      component: c.name,
      type: c.finalType,
      priority: 1,
      rationale: `复用指数${c.reusabilityIndex}，建议最先开发作为其他组件的基础`
    }))
  }
}

// ============================================================
// CLI 入口
// ============================================================

const prdPath = process.argv[2]
if (!prdPath) {
  console.log('Usage: node analyze_requirements.js <path-to-prd.md> [--abstraction-only]')
  console.log('')
  console.log('Examples:')
  console.log('  node analyze_requirements.js ./docs/prd.md')
  console.log('  node analyze_requirements.js ./docs/prd.md --abstraction-only')
  process.exit(1)
}

try {
  const content = readFileSync(prdPath, 'utf-8')
  const analysis = analyzePRD(content)
  const abstractionOnly = process.argv.includes('--abstraction-only')

  if (!abstractionOnly) {
    console.log('\n=== PRD Analysis ===')
    console.log('\nSummary:')
    console.log('  Headings:', analysis.summary.totalHeadings)
    console.log('  List Items:', analysis.summary.totalListItems)
    console.log('  Tables:', analysis.summary.totalTables)

    console.log('\nHeadings:')
    analysis.headings.forEach(h => {
      console.log('  '.repeat(h.level - 1) + `[H${h.level}] ${h.text}`)
    })
  }

  // 从表格中提取组件
  const extractedComponents = []
  for (const table of analysis.tables) {
    extractedComponents.push(...extractComponentsFromTable(table))
  }

  if (extractedComponents.length > 0) {
    const report = generateAbstractionReport(extractedComponents)

    console.log('\n=== Component Abstraction Analysis ===')
    console.log('\n[Summary]')
    console.log('  Total Components:', report.summary.totalComponents)
    console.log('  Global Atoms (可全局复用):', report.summary.globalAtoms)
    console.log('  Domain Molecules (领域复用):', report.summary.domainMolecules)
    console.log('  Domain Organisms (领域复用):', report.summary.domainOrganisms)
    console.log('  Page Components (页面级):', report.summary.pageComponents)
    console.log('  Estimated Reuse Gain:', report.summary.estimatedReuseGain)

    if (report.globalAtoms.length > 0) {
      console.log('\n[Global Atoms - 建议最先开发]')
      report.globalAtoms.forEach(c => {
        console.log(`  ★ ${c.name} (RI:${c.reusabilityIndex}, type:${c.detectedType})`)
      })
    }

    if (report.domainMolecules.length > 0) {
      console.log('\n[Domain Molecules - Phase 2]')
      report.domainMolecules.forEach(c => {
        console.log(`  ◆ ${c.name} (RI:${c.reusabilityIndex}, type:${c.detectedType})`)
      })
    }

    if (report.domainOrganisms.length > 0) {
      console.log('\n[Domain Organisms - Phase 3]')
      report.domainOrganisms.forEach(c => {
        console.log(`  ● ${c.name} (RI:${c.reusabilityIndex}, type:${c.detectedType})`)
      })
    }

    if (report.pageComponents.length > 0) {
      console.log('\n[Page Components - Phase 4 或内联]')
      report.pageComponents.forEach(c => {
        console.log(`  ○ ${c.name} (RI:${c.reusabilityIndex}, type:${c.detectedType})`)
      })
    }

    console.log('\n[Development Priority Order]')
    report.developmentOrder.slice(0, 10).forEach(c => {
      console.log(`  ${c.phase}.${c.priority} ${c.name} (${c.finalType})`)
    })

    if (report.developmentOrder.length > 10) {
      console.log(`  ... and ${report.developmentOrder.length - 10} more`)
    }

  } else {
    console.log('\n[Info] No component tables found in PRD.')
    console.log('Expected table format:')
    console.log('  | 组件名 | 类型 | 描述 |')
    console.log('  |--------|------|------|')
    console.log('  | BaseButton | atom | 基础按钮 |')
  }

  process.exit(0)
} catch (err) {
  console.error('Error:', err.message)
  process.exit(1)
}
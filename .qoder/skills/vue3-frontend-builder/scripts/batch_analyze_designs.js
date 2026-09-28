#!/usr/bin/env node
/**
 * batch_analyze_designs.js
 *
 * 批量分析多个设计稿，生成 design_scan_registry.json
 * 这是 requirement-analysis.md 中 vision_analyze 工作流的辅助脚本
 *
 * 用法：
 *   node scripts/batch_analyze_designs.js <designs-dir> [output-dir]
 *
 * 示例：
 *   node scripts/batch_analyze_designs.js ./mockups ./outputs/abstraction
 */

import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from 'fs'
import { join, dirname, basename, extname } from 'path'
import { fileURLToPath } from 'url'
import { createHash } from 'crypto'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 支持的图片格式
const SUPPORTED_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp']

// 预定义的组件类型模式（用于初步分类）
const COMPONENT_TYPE_PATTERNS = {
  button: ['button', 'btn', '按钮', '提交', '确认', '取消'],
  input: ['input', '输入', '搜索', 'search', '文本框'],
  card: ['card', '卡片', '面板', 'panel', 'container'],
  badge: ['badge', '徽章', '标签', 'tag', 'chip', '状态'],
  icon: ['icon', '图标', '符号'],
  avatar: ['avatar', '头像', '用户图'],
  table: ['table', '表格', '列表', 'list'],
  nav: ['nav', '导航', 'menu', '菜单', 'header', 'sidebar'],
  form: ['form', '表单', 'select', 'dropdown', 'picker'],
  modal: ['modal', '弹窗', 'dialog', '对话框', 'popup'],
  chart: ['chart', '图表', 'graph', '折线', '柱状', '饼图']
}

/**
 * 计算文件的 SHA-256
 */
function computeSHA256(filePath) {
  const content = readFileSync(filePath)
  return createHash('sha256').update(content).digest('hex')
}

/**
 * 从文件名推断页面名称
 */
function inferPageName(filename) {
  const name = basename(filename, extname(filename))
  // 移除常见后缀和变体
  return name
    .replace(/-dark$/i, '')
    .replace(/-light$/i, '')
    .replace(/-v\d+$/i, '')
    .replace(/-rtl$/i, '')
    .replace(/-mobile$/i, '')
    .replace(/-desktop$/i, '')
    .replace(/_/g, ' ')
    .split(/[- ]/)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join('')
}

/**
 * 从文件名推断组件类型
 */
function inferComponentType(filename) {
  const lowerName = basename(filename, extname(filename)).toLowerCase()

  for (const [type, keywords] of Object.entries(COMPONENT_TYPE_PATTERNS)) {
    for (const keyword of keywords) {
      if (lowerName.includes(keyword)) {
        return type
      }
    }
  }
  return 'unknown'
}

/**
 * 列出目录中的所有图片文件
 */
function listImageFiles(dir) {
  try {
    const files = readdirSync(dir)
    return files
      .filter(f => SUPPORTED_EXTENSIONS.includes(extname(f).toLowerCase()))
      .map(f => ({
        path: join(dir, f),
        name: f,
        pageName: inferPageName(f),
        inferredType: inferComponentType(f)
      }))
  } catch (err) {
    console.error(`Error reading directory ${dir}:`, err.message)
    return []
  }
}

/**
 * 解析 design_scan_registry.json（如存在）
 * 用于增量更新：只分析新增或变更的文件
 */
function loadExistingRegistry(outputPath) {
  const registryPath = join(outputPath, 'design_scan_registry.json')
  if (existsSync(registryPath)) {
    try {
      return JSON.parse(readFileSync(registryPath, 'utf-8'))
    } catch (err) {
      console.warn('Existing registry parse error, will rebuild:', err.message)
    }
  }
  return null
}

/**
 * 生成视觉签名（简化版：基于文件名和大小）
 * 实际项目中应使用 vision_analyze 的结果
 */
function generateVisualSignature(filePath, fileStat) {
  return `size:${fileStat.size},ext:${extname(filePath).slice(1)}`
}

/**
 * 构建 design_scan_registry.json
 */
function buildRegistry(designs, outputPath) {
  const now = new Date().toISOString()

  // 构建 element_index（跨设计稿去重）
  const globalElements = []
  const elementMap = new Map()

  designs.forEach((design, dIdx) => {
    // 为每个设计稿创建一个虚拟元素（实际应从 vision_analyze 获取）
    const elements = design.elements || []

    elements.forEach((elem) => {
      const signature = `${elem.type}-${elem.visual_signature || ''}-${elem.content || ''}`

      if (elementMap.has(signature)) {
        // 已存在，追加出现位置
        const existing = elementMap.get(signature)
        existing.design_ids.push(design.id)
        existing.occurrences = existing.design_ids.length
        existing.suggested_type = existing.suggested_type || elem.suggested_type || 'atom'
      } else {
        // 新建
        const globalId = `GLOB-${String(globalElements.length + 1).padStart(3, '0')}`
        const newEntry = {
          global_id: globalId,
          design_ids: [design.id],
          type: elem.type,
          visual_signature: elem.visual_signature || signature,
          content: elem.content || '',
          occurrences: 1,
          suggested_component: elem.suggested_component || inferSuggestedComponent(elem.type),
          suggested_type: elem.suggested_type || 'atom'
        }
        elementMap.set(signature, newEntry)
        globalElements.push(newEntry)
      }
    })
  })

  const registry = {
    version: '1.0.0',
    generated_at: now,
    total_designs: designs.length,
    designs: designs.map(d => ({
      id: d.id,
      file: d.file,
      sha256: d.sha256,
      page_name: d.pageName,
      analyzed_at: d.analyzed_at || now,
      elements: d.elements || [],
      element_count: (d.elements || []).length
    })),
    element_index: globalElements,
    abstraction_summary: {
      total_unique_elements: globalElements.length,
      suggested_atoms: globalElements.filter(e => e.suggested_type === 'atom').length,
      suggested_molecules: globalElements.filter(e => e.suggested_type === 'molecule').length,
      suggested_organisms: globalElements.filter(e => e.suggested_type === 'organism').length,
      suggested_pages: globalElements.filter(e => e.suggested_type === 'page').length
    }
  }

  return registry
}

/**
 * 根据元素类型推断建议的组件名称
 */
function inferSuggestedComponent(type) {
  const mapping = {
    button: 'BaseButton',
    input: 'BaseInput',
    card: 'DataCard',
    badge: 'BaseBadge',
    icon: 'BaseIcon',
    avatar: 'BaseAvatar',
    table: 'DataTable',
    nav: 'AppNav',
    form: 'FormField',
    modal: 'BaseModal',
    chart: 'BaseChart'
  }
  return mapping[type] || `${type.charAt(0).toUpperCase() + type.slice(1)}Component`
}

// ============================================================
// 主流程
// ============================================================

const designsDir = process.argv[2]
const outputDir = process.argv[3] || join(__dirname, '../outputs/abstraction')

if (!designsDir) {
  console.log(`
batch_analyze_designs.js - 批量分析设计稿

用法：
  node scripts/batch_analyze_designs.js <designs-dir> [output-dir]

参数：
  designs-dir    包含设计稿图片的目录
  output-dir     输出目录（默认：./outputs/abstraction）

示例：
  node scripts/batch_analyze_designs.js ./mockups ./outputs/abstraction

输出：
  <output-dir>/design_scan_registry.json

注意：
  这是辅助脚本，用于批量扫描设计稿文件名和元数据。
  实际的 vision_analyze 元素提取需要在对话中手动调用。
  脚本会生成 registry 框架，实际元素数据需后续填充。
`)
  process.exit(1)
}

// 检查目录是否存在
if (!existsSync(designsDir)) {
  console.error(`Designs directory not found: ${designsDir}`)
  process.exit(1)
}

// 创建输出目录
mkdirSync(outputDir, { recursive: true })
console.log(`Output directory: ${outputDir}`)

// 获取图片文件列表
const imageFiles = listImageFiles(designsDir)

if (imageFiles.length === 0) {
  console.log('No image files found in directory.')
  process.exit(0)
}

console.log(`Found ${imageFiles.length} image files:`)
imageFiles.forEach(f => {
  console.log(`  - ${f.name} (page: ${f.pageName}, type: ${f.inferredType})`)
})

// 检查是否有现有 registry（用于增量更新）
const existingRegistry = loadExistingRegistry(outputDir)

// 构建设计稿条目
const designs = imageFiles.map((img, index) => {
  const id = `DESIGN-${String(index + 1).padStart(3, '0')}`
  const fileStat = { size: 0 } // 简化：实际应该 readFileSync.stat

  // 检查是否有现有数据
  let elements = []
  let analyzed_at = null
  let sha256 = null

  if (existingRegistry && existingRegistry.designs) {
    const existing = existingRegistry.designs.find(d => d.file === img.path)
    if (existing) {
      elements = existing.elements || []
      analyzed_at = existing.analyzed_at
      sha256 = existing.sha256
    }
  }

  return {
    id,
    file: img.path,
    pageName: img.pageName,
    sha256: sha256 || computeSHA256(img.path),
    analyzed_at,
    elements,
    suggested_type: img.inferredType
  }
})

// 生成 registry
const registry = buildRegistry(designs, outputDir)

// 保存
const registryPath = join(outputDir, 'design_scan_registry.json')
writeFileSync(registryPath, JSON.stringify(registry, null, 2), 'utf-8')

console.log(`\nGenerated: ${registryPath}`)
console.log(`  Total designs: ${registry.total_designs}`)
console.log(`  Unique elements: ${registry.abstraction_summary.total_unique_elements}`)
console.log(`  Suggested: ${registry.abstraction_summary.suggested_atoms} atoms, ${registry.abstraction_summary.suggested_molecules} molecules, ${registry.abstraction_summary.suggested_organisms} organisms`)

console.log(`
Next steps:
  1. Review design_scan_registry.json
  2. For each design, call vision_analyze to extract actual elements
  3. Update the elements array in each design entry
  4. Re-run this script to regenerate with actual element data
  5. Use analyze_requirements.js to generate abstraction recommendations
`)
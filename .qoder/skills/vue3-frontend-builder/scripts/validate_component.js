#!/usr/bin/env node
/**
 * validate_component.js
 * 验证 Vue3 组件的代码质量
 */

import { readFileSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 简单验证：检查文件是否存在基本结构
function validateComponent(filePath) {
  const content = readFileSync(filePath, 'utf-8')
  const errors = []
  const warnings = []

  // 检查 <script setup> 存在
  if (!content.includes('<script setup')) {
    errors.push('Missing <script setup>')
  }

  // 检查 <template> 存在
  if (!content.includes('<template>')) {
    errors.push('Missing <template>')
  }

  // 检查 defineProps
  if (content.includes('defineProps') && !content.match(/defineProps<|withDefaults\s*\(\s*defineProps/)) {
    warnings.push('defineProps should use TypeScript generic or withDefaults')
  }

  // 检查 defineEmits
  if (content.includes('defineEmits') && !content.match(/defineEmits</)) {
    warnings.push('defineEmits should use TypeScript generic')
  }

  // 检查 <style scoped>
  if (!content.includes('<style scoped>') && !content.includes('<style>')) {
    warnings.push('Missing <style> section (scoped recommended)')
  }

  return { errors, warnings, valid: errors.length === 0 }
}

// CLI 入口
const filePath = process.argv[2]
if (!filePath) {
  console.error('Usage: node validate_component.js <path-to-component.vue>')
  process.exit(1)
}

try {
  const result = validateComponent(filePath)
  console.log('\n=== Component Validation ===')
  console.log('File:', filePath)
  console.log('Valid:', result.valid)

  if (result.errors.length > 0) {
    console.log('\nErrors:')
    result.errors.forEach(e => console.log('  -', e))
  }

  if (result.warnings.length > 0) {
    console.log('\nWarnings:')
    result.warnings.forEach(w => console.log('  -', w))
  }

  process.exit(result.valid ? 0 : 1)
} catch (err) {
  console.error('Error reading file:', err.message)
  process.exit(1)
}
#!/usr/bin/env node
/**
 * build_component_signature.js
 * 为组件生成签名文件，记录组件结构和样式参数
 */

import { readFileSync, writeFileSync, existsSync } from 'fs'
import { join, dirname, basename } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 解析 Vue 组件提取信息
function parseComponent(content) {
  const signature = {
    name: null,
    props: [],
    emits: [],
    state: [],
    computed: [],
    methods: [],
    slots: [],
    styles: {}
  }

  // 提取组件名
  const nameMatch = content.match(/name:\s*['"]([^'"]+)['"]/)
  if (nameMatch) signature.name = nameMatch[1]

  // 提取 props
  const propsMatch = content.match(/interface\s+Props\s*\{([^}]+)\}/s)
  if (propsMatch) {
    const propsContent = propsMatch[1]
    const propLines = propsContent.split('\n')
    propLines.forEach(line => {
      const match = line.match(/\s*(\w+)(\?)?:\s*([^,|]+)/)
      if (match) {
        signature.props.push({
          name: match[1],
          optional: !!match[2],
          type: match[3].trim()
        })
      }
    })
  }

  // 提取 emits
  const emitsMatch = content.match(/defineEmits<\{([^}]+)\}/s)
  if (emitsMatch) {
    const emitsContent = emitsMatch[1]
    const emitLines = emitsContent.split('\n')
    emitLines.forEach(line => {
      const match = line.match(/\(e:\s*['"]([^'"]+)['"][^)]*\)/)
      if (match) {
        signature.emits.push(match[1])
      }
    })
  }

  // 提取 refs
  const refMatches = content.matchAll(/const\s+(\w+)\s*=\s*ref\(/g)
  for (const match of refMatches) {
    signature.state.push({ name: match[1], type: 'ref' })
  }

  // 提取 reactive
  const reactiveMatches = content.matchAll(/const\s+(\w+)\s*=\s*reactive\(/g)
  for (const match of reactiveMatches) {
    signature.state.push({ name: match[1], type: 'reactive' })
  }

  // 提取 computed
  const computedMatches = content.matchAll(/const\s+(\w+)\s*=\s*computed\(/g)
  for (const match of computedMatches) {
    signature.computed.push(match[1])
  }

  // 提取 defineExpose
  const exposeMatch = content.match(/defineExpose\(\{([^}]+)\}/s)
  if (exposeMatch) {
    const exposeContent = exposeMatch[1]
    const methodMatches = exposeContent.matchAll(/(\w+):\s*\(/g)
    for (const match of methodMatches) {
      signature.methods.push(match[1])
    }
  }

  return signature
}

// 为组件生成签名文件
function buildSignature(componentPath, outputDir) {
  if (!existsSync(componentPath)) {
    throw new Error(`Component not found: ${componentPath}`)
  }

  const content = readFileSync(componentPath, 'utf-8')
  const signature = parseComponent(content)

  const componentName = basename(componentPath, '.vue')
  const signatureFile = join(outputDir || '.', `${componentName}.signature.json`)

  const output = {
    component: componentName,
    file: componentPath,
    signature: signature,
    generated_at: new Date().toISOString(),
    locked: true
  }

  writeFileSync(signatureFile, JSON.stringify(output, null, 2))
  console.log('Generated:', signatureFile)

  return output
}

// CLI
const componentPath = process.argv[2]
const outputDir = process.argv[3]

if (!componentPath) {
  console.log('Usage: node build_component_signature.js <component.vue> [output-dir]')
  process.exit(1)
}

try {
  const result = buildSignature(componentPath, outputDir)
  console.log('\nComponent Signature:')
  console.log('  Name:', result.signature.name || result.component)
  console.log('  Props:', result.signature.props.length)
  console.log('  Emits:', result.signature.emits.length)
  console.log('  State:', result.signature.state.length)
  console.log('  Computed:', result.signature.computed.length)
  console.log('  Methods:', result.signature.methods.length)
  process.exit(0)
} catch (err) {
  console.error('Error:', err.message)
  process.exit(1)
}
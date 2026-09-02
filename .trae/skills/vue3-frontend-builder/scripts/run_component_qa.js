#!/usr/bin/env node
/**
 * run_component_qa.js
 *
 * 批量为所有组件运行 QA 验证
 * 生成每个组件的 component_qa_gate.json
 *
 * 用法：
 *   node scripts/run_component_qa.js <components-dir> <output-dir>
 *
 * 示例：
 *   node scripts/run_component_qa.js src/components outputs/qa
 */

import { readdirSync, writeFileSync, existsSync, mkdirSync, statSync } from 'fs'
import { join, basename, extname } from 'path'
import { fileURLToPath } from 'url'
import { spawn } from 'child_process'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const PROJECT_ROOT = join(__dirname, '..')

/**
 * 执行命令
 */
function runCommand(cmd, args, cwd = PROJECT_ROOT) {
  return new Promise((resolve) => {
    const proc = spawn(cmd, args, { cwd, shell: true })
    let stdout = ''
    let stderr = ''
    proc.stdout.on('data', (data) => { stdout += data.toString() })
    proc.stderr.on('data', (data) => { stderr += data.toString() })
    proc.on('close', (code) => resolve({ exit_code: code, stdout, stderr }))
    proc.on('error', (err) => resolve({ exit_code: -1, stdout: '', stderr: err.message }))
    setTimeout(() => { proc.kill(); resolve({ exit_code: -2, stdout, stderr: 'timeout' }) }, 300000)
  })
}

/**
 * 递归查找所有 Vue 文件
 */
function findVueFiles(dir, files = []) {
  try {
    const entries = readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = join(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules' && !entry.name.startsWith('.') && !entry.name.startsWith('__')) {
          findVueFiles(fullPath, files)
        }
      } else if (entry.name.endsWith('.vue')) {
        files.push(fullPath)
      }
    }
  } catch (e) { /* ignore */ }
  return files
}

/**
 * 推断组件层级
 */
function inferLayer(dir) {
  const parts = dir.split('/')
  if (parts.includes('atoms')) return 'atoms'
  if (parts.includes('molecules')) return 'molecules'
  if (parts.includes('organisms')) return 'organisms'
  if (parts.includes('pages')) return 'pages'
  return 'unknown'
}

async function runQAForComponent(vueFile, outputDir) {
  const componentName = basename(vueFile, extname(vueFile))
  const layer = inferLayer(vueFile)
  const componentDir = join(outputDir, 'components', layer, componentName)
  mkdirSync(componentDir, { recursive: true })

  console.log(`  QA: ${layer}/${componentName}`)

  // 调用 validate_component_full.js
  const { exit_code, stdout, stderr } = await runCommand('node', [
    join(__dirname, 'validate_component_full.js'),
    vueFile,
    outputDir
  ])

  return {
    component: componentName,
    layer,
    file: vueFile,
    status: exit_code === 0 ? 'pass' : 'fail',
    exit_code
  }
}

async function main() {
  const componentsDir = process.argv[2]
  const outputDir = process.argv[3] || join(PROJECT_ROOT, 'outputs/qa')

  if (!componentsDir) {
    console.log(`
run_component_qa.js - 批量组件 QA 验证

用法：
  node scripts/run_component_qa.js <components-dir> [output-dir]

示例：
  node scripts/run_component_qa.js src/components outputs/qa

依赖：
  - validate_component_full.js
  - 项目需要有 package.json 和 tsconfig.json
`)
    process.exit(1)
  }

  const absComponentsDir = join(PROJECT_ROOT, componentsDir)
  if (!existsSync(absComponentsDir)) {
    console.error(`Components directory not found: ${absComponentsDir}`)
    process.exit(1)
  }

  const vueFiles = findVueFiles(absComponentsDir)
  console.log(`\nFound ${vueFiles.length} components in ${componentsDir}\n`)

  mkdirSync(outputDir, { recursive: true })

  const results = []
  for (const vueFile of vueFiles) {
    const relPath = vueFile.replace(PROJECT_ROOT + '/', '')
    const result = await runQAForComponent(relPath, outputDir)
    results.push(result)
  }

  // 生成汇总报告
  const summary = {
    generated_at: new Date().toISOString(),
    total: results.length,
    pass: results.filter(r => r.status === 'pass').length,
    fail: results.filter(r => r.status === 'fail').length,
    by_layer: {}
  }

  for (const result of results) {
    if (!summary.by_layer[result.layer]) {
      summary.by_layer[result.layer] = { total: 0, pass: 0, fail: 0 }
    }
    summary.by_layer[result.layer].total++
    if (result.status === 'pass') {
      summary.by_layer[result.layer].pass++
    } else {
      summary.by_layer[result.layer].fail++
    }
  }

  const summaryFile = join(outputDir, 'qa-summary.json')
  writeFileSync(summaryFile, JSON.stringify(summary, null, 2), 'utf-8')

  console.log(`\n=== QA Summary ===`)
  console.log(`Total: ${summary.total}, Pass: ${summary.pass}, Fail: ${summary.fail}`)
  for (const [layer, stats] of Object.entries(summary.by_layer)) {
    console.log(`  ${layer}: ${stats.pass}/${stats.total} passed`)
  }
  console.log(`\nSummary: ${summaryFile}`)

  if (summary.fail > 0) {
    console.log(`\nFailed components:`)
    results.filter(r => r.status === 'fail').forEach(r => {
      console.log(`  - ${r.layer}/${r.component}`)
    })
    process.exit(1)
  }
}

main().catch(err => {
  console.error('QA run error:', err)
  process.exit(1)
})
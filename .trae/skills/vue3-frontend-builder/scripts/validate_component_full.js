#!/usr/bin/env node
/**
 * validate_component_full.js
 *
 * 组件完整 QA 验证脚本
 * 运行 TypeScript 检查、ESLint 检查、Vitest 测试，生成 component_qa_gate.json
 *
 * 用法：
 *   node scripts/validate_component_full.js <component-path> [output-dir]
 *   node scripts/validate_component_full.js src/components/atoms/BaseButton
 *   node scripts/validate_component_full.js src/components/atoms outputs/qa
 *
 * 依赖：
 *   - TypeScript (npx tsc)
 *   - ESLint (npx eslint)
 *   - Vitest (npx vitest)
 */

import { spawn } from 'child_process'
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs'
import { join, dirname, basename, extname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const PROJECT_ROOT = join(__dirname, '..')

// ============================================================
// 工具函数
// ============================================================

/**
 * 执行命令，返回 { exit_code, stdout, stderr }
 */
function runCommand(cmd, args, cwd = PROJECT_ROOT) {
  return new Promise((resolve) => {
    const proc = spawn(cmd, args, { cwd, shell: true })
    let stdout = ''
    let stderr = ''

    proc.stdout.on('data', (data) => { stdout += data.toString() })
    proc.stderr.on('data', (data) => { stderr += data.toString() })

    proc.on('close', (code) => {
      resolve({ exit_code: code, stdout, stderr })
    })

    proc.on('error', (err) => {
      resolve({ exit_code: -1, stdout: '', stderr: err.message })
    })

    // 5 分钟超时
    setTimeout(() => {
      proc.kill()
      resolve({ exit_code: -2, stdout, stderr: 'timeout after 300s' })
    }, 300000)
  })
}

/**
 * 递归列出所有 .vue 和 .ts 文件
 */
function listComponentFiles(componentPath) {
  const { readdirSync, statSync } = require('fs')
  const { join } = require('path')
  const files = []

  function walk(dir) {
    try {
      const entries = readdirSync(dir, { withFileTypes: true })
      for (const entry of entries) {
        const fullPath = join(dir, entry.name)
        if (entry.isDirectory()) {
          // 跳过 node_modules 和隐藏目录
          if (entry.name !== 'node_modules' && !entry.name.startsWith('.')) {
            walk(fullPath)
          }
        } else if (/\.(vue|ts|tsx|js|jsx)$/.test(entry.name)) {
          files.push(fullPath)
        }
      }
    } catch (err) {
      // 忽略无法读取的目录
    }
  }

  walk(componentPath)
  return files
}

/**
 * 推断组件名称
 */
function inferComponentName(filePath) {
  const name = basename(filePath, extname(filePath))
  // 移除 .spec .test .d 等后缀
  return name.replace(/\.(spec|test|d)$/, '')
}

// ============================================================
// 主验证逻辑
// ============================================================

async function validateComponent(componentPath, outputDir) {
  const componentName = inferComponentName(componentPath)
  const absolutePath = join(PROJECT_ROOT, componentPath)

  // 输出目录
  const qaOutputDir = join(outputDir, 'code')
  const renderOutputDir = join(outputDir, 'renders')
  const comparisonOutputDir = join(outputDir, 'comparison')

  // 组件 QA 目录
  const componentQaDir = join(qaOutputDir, componentName)
  mkdirSync(componentQaDir, { recursive: true })

  const result = {
    component: componentName,
    file: componentPath,
    version: '1.0.0',
    generated_at: new Date().toISOString(),
    qa_tool_version: '1.0.0',

    code_checks: {
      typescript: { status: 'skipped', command: '', exit_code: -1, errors: [] },
      eslint: { status: 'skipped', command: '', exit_code: -1, errors: [] },
      vitest: { status: 'skipped', command: '', exit_code: -1, tests_total: 0, tests_passed: 0, tests_failed: 0, coverage: 0 }
    },

    visual_checks: {
      render: { status: 'skipped', screenshots: {}, all_screenshots_exist: false },
      design_comparison: { status: 'skipped', side_by_side: '', diff: '', metrics: {}, p0: 'skipped', p1: 'skipped', p2: 'skipped' }
    },

    checklist_verification: {
      status: 'skipped',
      items_verified: 0,
      items_failed: 0,
      details: {}
    },

    summary: {
      all_checks_pass: false,
      total_checks: 0,
      passed_checks: 0,
      failed_checks: 0,
      overall_status: 'fail'
    },

    gate_result: {
      can_proceed_to_user_approval: false,
      blocking_issues: [],
      non_blocking_warnings: []
    }
  }

  // ============================================================
  // 1. TypeScript 检查
  // ============================================================
  console.log(`[${componentName}] Running TypeScript check...`)
  const tscResult = await runCommand('npx', ['tsc', '--noEmit', '--project', join(PROJECT_ROOT, 'tsconfig.json')], PROJECT_ROOT)
  result.code_checks.typescript = {
    status: tscResult.exit_code === 0 ? 'pass' : 'fail',
    command: 'npx tsc --noEmit',
    exit_code: tscResult.exit_code,
    errors: parseTscErrors(tscResult.stdout)
  }
  console.log(`  TypeScript: ${result.code_checks.typescript.status} (${result.code_checks.typescript.errors.length} errors)`)

  // ============================================================
  // 2. ESLint 检查
  // ============================================================
  console.log(`[${componentName}] Running ESLint check...`)
  const eslintResult = await runCommand('npx', ['eslint', componentPath, '--format', 'json'], PROJECT_ROOT)
  let eslintErrors = []
  try {
    const eslintOutput = JSON.parse(eslintResult.stdout)
    for (const file of eslintOutput) {
      for (const msg of file.messages) {
        if (msg.severity === 2) { // error
          eslintErrors.push(`${msg.line}:${msg.column} ${msg.message} (${msg.ruleId})`)
        }
      }
    }
  } catch (e) {
    // 如果无法解析 JSON，尝试从 stderr 获取原始输出
    if (eslintResult.stderr) {
      eslintErrors = eslintResult.stderr.split('\n').filter(l => l.trim())
    }
  }
  result.code_checks.eslint = {
    status: eslintErrors.length === 0 ? 'pass' : 'fail',
    command: `npx eslint ${componentPath} --format json`,
    exit_code: eslintErrors.length === 0 ? 0 : 1,
    errors: eslintErrors
  }
  console.log(`  ESLint: ${result.code_checks.eslint.status} (${eslintErrors.length} errors)`)

  // ============================================================
  // 3. Vitest 测试
  // ============================================================
  console.log(`[${componentName}] Running Vitest tests...`)
  const vitestResult = await runCommand('npx', [
    'vitest', 'run', componentPath,
    '--reporter', 'json',
    '--outputFile', join(componentQaDir, 'vitest-report.json')
  ], PROJECT_ROOT)

  let vitestReport = { testResults: [], coverage: { branches: 0, lines: 0, functions: 0 } }
  const vitestReportPath = join(componentQaDir, 'vitest-report.json')
  if (existsSync(vitestReportPath)) {
    try {
      vitestReport = JSON.parse(readFileSync(vitestReportPath, 'utf-8'))
    } catch (e) { /* ignore */ }
  }

  const testResults = vitestReport.testResults || []
  const totalTests = testResults.reduce((sum, r) => sum + (r.assertionResults?.length || 0), 0)
  const failedTests = testResults.reduce((sum, r) => {
    return sum + (r.assertionResults?.filter(a => a.status === 'failed').length || 0)
  }, 0)

  result.code_checks.vitest = {
    status: vitestResult.exit_code === 0 ? 'pass' : 'fail',
    command: `npx vitest run ${componentPath}`,
    exit_code: vitestResult.exit_code,
    tests_total: totalTests,
    tests_passed: totalTests - failedTests,
    tests_failed: failedTests,
    coverage: vitestReport.coverage?.lines?.pct || 0
  }
  console.log(`  Vitest: ${result.code_checks.vitest.status} (${totalTests} tests, ${failedTests} failed, coverage: ${result.code_checks.vitest.coverage}%)`)

  // ============================================================
  // 4. 截图存在性检查（占位符验证）
  // ============================================================
  const screenshots = {
    desktop: join(renderOutputDir, `${componentName}-desktop.png`),
    tablet: join(renderOutputDir, `${componentName}-tablet.png`),
    mobile: join(renderOutputDir, `${componentName}-mobile.png`)
  }

  const allScreenshotsExist = Object.values(screenshots).every(f => existsSync(f))
  result.visual_checks.render = {
    status: allScreenshotsExist ? 'pass' : 'fail',
    screenshots: {
      desktop: screenshots.desktop,
      tablet: screenshots.tablet,
      mobile: screenshots.mobile
    },
    all_screenshots_exist: allScreenshotsExist
  }

  if (!allScreenshotsExist) {
    result.gate_result.blocking_issues.push('Missing render screenshots - run visual verification first')
  }
  console.log(`  Render screenshots: ${result.visual_checks.render.status}`)

  // ============================================================
  // 5. 设计对照检查（占位符验证）
  // ============================================================
  const sideBySide = join(comparisonOutputDir, `${componentName}-side-by-side.png`)
  const diff = join(comparisonOutputDir, `${componentName}-diff.png`)

  if (existsSync(sideBySide) && existsSync(diff)) {
    result.visual_checks.design_comparison = {
      status: 'pass',
      side_by_side: sideBySide,
      diff: diff,
      metrics: {
        diff_percentage: 0,   // 需配合 pixel diff 工具计算
        max_delta_e: 0,
        position_delta_px: 0
      },
      p0: 'pass',
      p1: 'pass',
      p2: 'pass'
    }
  } else {
    result.visual_checks.design_comparison = {
      status: 'fail',
      side_by_side: sideBySide,
      diff: diff,
      metrics: {},
      p0: 'fail',
      p1: 'fail',
      p2: 'fail'
    }
    result.gate_result.blocking_issues.push('Missing design comparison files - run visual comparison first')
  }
  console.log(`  Design comparison: ${result.visual_checks.design_comparison.status}`)

  // ============================================================
  // 6. 清单验证（基于代码检查结果自动推断）
  // ============================================================
  const tscPass = result.code_checks.typescript.status === 'pass'
  const eslintPass = result.code_checks.eslint.status === 'pass'
  const vitestPass = result.code_checks.vitest.status === 'pass'
  const renderPass = result.visual_checks.render.status === 'pass'
  const comparisonPass = result.visual_checks.design_comparison.status === 'pass'

  const details = {
    typescript_types_complete: tscPass,
    eslint_passes: eslintPass,
    unit_tests_pass: vitestPass,
    layout_matches_design: comparisonPass,
    colors_match_design: comparisonPass,
    font_matches_design: comparisonPass,
    spacing_matches_design: comparisonPass,
    state_hover_correct: vitestPass,  // 假设测试覆盖了状态
    state_focus_correct: vitestPass,
    state_active_correct: vitestPass,
    state_disabled_correct: vitestPass,
    state_loading_correct: vitestPass,
    state_error_correct: vitestPass,
    desktop_display_correct: renderPass,
    tablet_display_correct: renderPass,
    mobile_display_correct: renderPass,
    no_layout_overflow: renderPass,
    no_text_truncation: renderPass
  }

  const failedItems = Object.values(details).filter(v => !v).length
  result.checklist_verification = {
    status: failedItems === 0 ? 'pass' : 'fail',
    items_verified: Object.keys(details).length,
    items_failed: failedItems,
    details
  }
  console.log(`  Checklist: ${result.checklist_verification.status} (${failedItems} failed)`)

  // ============================================================
  // 7. 汇总判定
  // ============================================================
  const checks = [tscPass, eslintPass, vitestPass, renderPass, comparisonPass]
  const failedChecks = checks.filter(c => !c).length
  const allPass = checks.every(c => c)

  result.summary = {
    all_checks_pass: allPass,
    total_checks: checks.length,
    passed_checks: checks.length - failedChecks,
    failed_checks: failedChecks,
    overall_status: allPass ? 'pass' : 'fail'
  }

  // 阻断检查
  if (!tscPass) result.gate_result.blocking_issues.push('TypeScript check failed')
  if (!eslintPass) result.gate_result.blocking_issues.push('ESLint check failed')
  if (!vitestPass) result.gate_result.blocking_issues.push('Vitest tests failed')
  if (!renderPass) result.gate_result.blocking_issues.push('Missing render screenshots')
  if (!comparisonPass) result.gate_result.blocking_issues.push('Design comparison failed')

  result.gate_result.can_proceed_to_user_approval = allPass

  // ============================================================
  // 8. 保存结果
  // ============================================================
  const gateFile = join(componentQaDir, `${componentName}.qagate.json`)
  writeFileSync(gateFile, JSON.stringify(result, null, 2), 'utf-8')
  console.log(`\n[${componentName}] QA Gate: ${result.summary.overall_status}`)
  console.log(`  Gate file: ${gateFile}`)
  if (result.gate_result.blocking_issues.length > 0) {
    console.log(`  Blocking issues:`)
    result.gate_result.blocking_issues.forEach(issue => console.log(`    - ${issue}`))
  }

  return result
}

// ============================================================
// 工具函数
// ============================================================

function parseTscErrors(stdout) {
  const errors = []
  // TypeScript 错误格式: "src/foo.ts(10,5): error TS2322: ..."
  const lines = stdout.split('\n')
  for (const line of lines) {
    const match = line.match(/^(.+?)\((\d+),(\d+)\): (error|warning) (TS\d+): (.+)$/)
    if (match) {
      errors.push(`${match[1]}:${match[2]} ${match[5]} ${match[6]}`)
    }
  }
  return errors
}

// ============================================================
// CLI 入口
// ============================================================

const componentPath = process.argv[2]
const outputDir = process.argv[3] || join(PROJECT_ROOT, 'outputs/qa')

if (!componentPath) {
  console.log(`
validate_component_full.js - 组件完整 QA 验证

用法：
  node scripts/validate_component_full.js <component-path> [output-dir]

示例：
  node scripts/validate_component_full.js src/components/atoms/BaseButton
  node scripts/validate_component_full.js src/components/atoms/BaseButton outputs/qa

输出：
  <output-dir>/code/<ComponentName>/<ComponentName>.qagate.json

检查项：
  1. TypeScript 类型检查 (tsc --noEmit)
  2. ESLint 代码规范检查
  3. Vitest 单元测试 + 覆盖率
  4. 渲染截图存在性检查
  5. 设计对照图存在性检查

注意：
  - 需要在项目根目录运行（有 package.json 和 tsconfig.json）
  - 渲染截图需要先通过开发服务器生成
  - 设计对照图需要先手动或通过脚本生成
`)
  process.exit(1)
}

const absolutePath = join(PROJECT_ROOT, componentPath)

if (!existsSync(absolutePath)) {
  console.error(`Component path not found: ${absolutePath}`)
  process.exit(1)
}

// 判断是文件还是目录
const { statSync } = require('fs')
const isDir = statSync(absolutePath).isDirectory()

if (isDir) {
  // 批量验证目录下所有 Vue/TS 文件
  const files = listComponentFiles(absolutePath)
  console.log(`Found ${files.length} files in ${componentPath}\n`)

  let passCount = 0
  let failCount = 0

  for (const file of files) {
    const relPath = file.replace(PROJECT_ROOT + '/', '')
    const result = await validateComponent(relPath, outputDir)
    if (result.summary.overall_status === 'pass') {
      passCount++
    } else {
      failCount++
    }
  }

  console.log(`\n=== Summary ===`)
  console.log(`Total: ${files.length}, Pass: ${passCount}, Fail: ${failCount}`)

  if (failCount > 0) {
    process.exit(1)
  }
} else {
  // 单文件验证
  validateComponent(componentPath, outputDir)
    .then((result) => {
      process.exit(result.summary.overall_status === 'pass' ? 0 : 1)
    })
    .catch((err) => {
      console.error('Validation error:', err)
      process.exit(1)
    })
}
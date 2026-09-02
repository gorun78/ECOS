#!/usr/bin/env node
/**
 * generate_approval_records.js
 *
 * 批量为组件生成审批记录模板（component_approval_record.json）
 * 基于 QA 门禁结果自动填充可填充的字段
 *
 * 用法：
 *   node scripts/generate_approval_records.js <components-dir> <qa-output-dir> [output-dir]
 *
 * 示例：
 *   node scripts/generate_approval_records.js src/components outputs/qa outputs/approvals
 */

import { readdirSync, writeFileSync, existsSync, mkdirSync } from 'fs'
import { join, basename, extname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const PROJECT_ROOT = join(__dirname, '..')

/**
 * 递归查找所有 Vue 文件
 */
function findVueFiles(dir, files = []) {
  try {
    const entries = readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = join(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules' && !entry.name.startsWith('.')) {
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
function inferLayer(filePath) {
  if (filePath.includes('/atoms/')) return 'atoms'
  if (filePath.includes('/molecules/')) return 'molecules'
  if (filePath.includes('/organisms/')) return 'organisms'
  if (filePath.includes('/pages/')) return 'pages'
  return 'shared'
}

/**
 * 生成审批记录模板
 */
function generateApprovalRecord(componentName, layer, vueFile, qaResult) {
  const now = new Date().toISOString()

  // 从 QA 结果推断
  const codeApproval = qaResult?.code_checks || {}
  const visualApproval = qaResult?.visual_checks || {}
  const qaSummary = qaResult?.summary || {}

  return {
    component: componentName,
    file: vueFile,
    version: '1.0.0',
    approval: {
      status: qaSummary.overall_status === 'pass' ? 'approved' : 'pending',
      approved_at: qaSummary.overall_status === 'pass' ? now : null,
      approved_by: qaSummary.overall_status === 'pass' ? 'system-auto' : null,
      version_approved: 'v1'
    },
    code_approval: {
      typescript_check: codeApproval.typescript?.status || 'skipped',
      typescript_errors: codeApproval.typescript?.errors || [],
      eslint_check: codeApproval.eslint?.status || 'skipped',
      eslint_errors: codeApproval.eslint?.errors || [],
      test_check: codeApproval.vitest?.status || 'skipped',
      test_coverage: codeApproval.vitest?.coverage || 0,
      test_failures: codeApproval.vitest?.tests_failed || 0
    },
    render_approval: {
      render_verified: visualApproval.render?.status === 'pass',
      design_file: null,  // 需要手动填写
      render_screenshot: visualApproval.render?.screenshots?.desktop || null,
      side_by_side: visualApproval.design_comparison?.side_by_side || null,
      visual_delta_px: visualApproval.design_comparison?.metrics?.position_delta_px || 0,
      visual_tolerance_px: 2,
      visual_match: visualApproval.design_comparison?.p0 || 'skipped'
    },
    content_lock: {
      locked: false,  // 需要在第三阶段锁定后更新
      lock_file: null,
      sha256: null
    },
    signature: {
      locked: false,
      signature_file: null,
      sha256: null
    },
    style_lock: {
      locked: false,
      style_file: null,
      sha256: null
    },
    deliverable_allowed: qaSummary.overall_status === 'pass',
    notes: qaSummary.overall_status === 'pass'
      ? ['Auto-generated from QA gate pass']
      : ['Pending QA pass before approval']
  }
}

function main() {
  const componentsDir = process.argv[2]
  const qaOutputDir = process.argv[3] || join(PROJECT_ROOT, 'outputs/qa')
  const outputDir = process.argv[4] || join(PROJECT_ROOT, 'outputs/approvals')

  if (!componentsDir) {
    console.log(`
generate_approval_records.js - 批量生成组件审批记录

用法：
  node scripts/generate_approval_records.js <components-dir> [qa-output-dir] [output-dir]

示例：
  node scripts/generate_approval_records.js src/components outputs/qa outputs/approvals

说明：
  基于 QA 门禁结果（qagate.json）生成审批记录模板。
  QA 通过的组件自动标记为 approved，通过的组件自动允许交付。
  QA 未通过的组件标记为 pending，需要修复后重新 QA。
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

  let passCount = 0
  let pendingCount = 0

  for (const vueFile of vueFiles) {
    const componentName = basename(vueFile, extname(vueFile))
    const layer = inferLayer(vueFile)
    const relPath = vueFile.replace(PROJECT_ROOT + '/', '')

    // 尝试加载 QA 结果
    let qaResult = null
    const qaGatePath = join(qaOutputDir, 'components', layer, componentName, `${componentName}.qagate.json`)
    if (existsSync(qaGatePath)) {
      try {
        qaResult = JSON.parse(readFileSync(qaGatePath, 'utf-8'))
      } catch (e) { /* ignore */ }
    }

    const record = generateApprovalRecord(componentName, layer, relPath, qaResult)

    const approvalDir = join(outputDir, layer)
    mkdirSync(approvalDir, { recursive: true })

    const recordPath = join(approvalDir, `${componentName}.approval.json`)
    writeFileSync(recordPath, JSON.stringify(record, null, 2), 'utf-8')

    if (record.approval.status === 'approved') {
      passCount++
      console.log(`  [APPROVED] ${layer}/${componentName}`)
    } else {
      pendingCount++
      console.log(`  [PENDING]  ${layer}/${componentName}`)
    }
  }

  console.log(`\n=== Approval Summary ===`)
  console.log(`Total: ${vueFiles.length}, Approved: ${passCount}, Pending: ${pendingCount}`)
  console.log(`Records: ${outputDir}`)

  if (pendingCount > 0) {
    console.log(`\n${pendingCount} components pending QA pass. Fix and re-run:`)
    console.log(`  node scripts/run_component_qa.js ${componentsDir} ${qaOutputDir}`)
    console.log(`  node scripts/generate_approval_records.js ${componentsDir} ${qaOutputDir} ${outputDir}`)
  }
}

main().catch(err => {
  console.error('Approval record generation error:', err)
  process.exit(1)
})
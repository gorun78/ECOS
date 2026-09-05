#!/usr/bin/env node
// wave4-runner.mjs — Wave-4.1 7 域联调 runner
// 用法: node ~/ecos-tests/integration/wave4/wave4-runner.mjs [--filter=W]
//       目录: ./wave4/{01..07}.mjs
//       跳过 ENV_BLOCKED (exit 77), fail 才阻塞
// 退出: 0=全 PASS, 1=有 FAIL, 2=全 ENV_BLOCKED

import { readdir, readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, basename } from 'node:path';
import { spawn } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DIR = __dirname;
const filter = process.argv.find((a) => a.startsWith('--filter='))?.split('=')[1];
const CONCURRENCY = Number(process.argv.find((a) => a.startsWith('--concurrency='))?.split('=')[1] || 1);

const files = (await readdir(DIR)).filter((f) => /^[0-9]{2}-.+\.mjs$/.test(f)).sort();
const filtered = filter ? files.filter((f) => f.startsWith(filter)) : files;
console.log(`[wave4-runner] 扫描: ${DIR}`);
console.log(`[wave4-runner] 共 ${filtered.length}/${files.length} 用例, 并发=${CONCURRENCY}\n`);

const results = [];
const nodePath = process.execPath; // 当前 node 路径
async function runOne(file) {
  const t0 = Date.now();
  const child = spawn(nodePath, [join(DIR, file)], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, ECOS_BASE: process.env.ECOS_BASE || 'http://localhost:8080' },
  });
  return new Promise((resolve) => {
    let stdout = '', stderr = '';
    child.stdout.on('data', (d) => (stdout += d.toString()));
    child.stderr.on('data', (d) => (stderr += d.toString()));
    child.on('close', (code) => {
      const ms = Date.now() - t0;
      const tag = code === 0 ? 'PASS' : code === 77 ? 'ENV' : 'FAIL';
      // 提取 [label] 末行
      const labelMatch = (stdout + stderr).match(/'(\d{2}[-.\w]+[^'\\n]*)'[\s\S]*$/);
      const lastLine = (stdout || stderr).trim().split('\n').slice(-3).join(' ⏎ ').slice(0, 400);
      results.push({ file, code, tag, ms, lastLine, stdout, stderr });
      console.log(`  ${tag === 'PASS' ? '✅' : tag === 'ENV' ? '⏸️ ' : '❌'} [${code}] ${basename(file)} (${ms}ms)`);
      if (code === 0) {
        // PASS: 打印摘要关键行
        const lines = stdout.split('\n').filter(l => l.trim().startsWith('  ✅') || l.trim().startsWith('  ❌'));
        lines.forEach(l => console.log('   │' + l));
      } else {
        // FAIL / ENV: 打印全部错误
        (stderr || stdout).split('\n').slice(-25).forEach(l => console.log('   │' + l.slice(0, 300)));
      }
      resolve();
    });
  });
}

// sequential (CONCURRENCY=1 default)
const queue = [...filtered];
await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
  while (queue.length) {
    const f = queue.shift();
    if (f) await runOne(f);
  }
}));

const pass = results.filter(r => r.code === 0).length;
const env = results.filter(r => r.code === 77).length;
const fail = results.filter(r => r.code !== 0 && r.code !== 77).length;

console.log(`\n${'═'.repeat(60)}`);
console.log(`  📊 wave4 Runner 总计: ${results.length} 用例`);
console.log(`  ✅ PASS ${pass} | ⏸️  ENV_BLOCKED ${env} | ❌ FAIL ${fail}`);
console.log(`  交付判定: ${fail === 0 ? (env === results.length ? 'NO-GO (全 ENV_BLOCKED)' : 'GO (deliverable_allowed=true)') : 'NO-GO (有 FAIL)'}`);
console.log(`${'═'.repeat(60)}`);
process.exit(fail > 0 ? 1 : (env === results.length ? 2 : 0));

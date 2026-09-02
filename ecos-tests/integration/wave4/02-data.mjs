#!/usr/bin/env node
// 02-data.mjs — Wave-4.1 域 2/7: data topic TransformController
// 验收 (见 docs/3-data/02 01-需求 §3.5: 6 step 链路):
//   T1 GET /api/v1/engine/data/transform/meta 200 + code===0 + 6 个 step 全齐
//   T2 5 步交付 (cleaning+mapping+typeConversion+validation+aggregation):
//       期望 200 + code===0 + statistics.inputCount>0 + output.rows 非空
//       真实观察: 200 + success=true + output.rows 行数 correct, 但
//       statistics.inputCount/outputCount = 0 (P0-2: statistics 4 字段没人刷新)
//   T3 未知 step → code===400
//   T4 缺 input → code===400
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED
//
// 真实观察 (Wave-4.1 暴露 P0-2):
// TransformStatistics.inputCount/outputCount/filteredCount/errorCount 都是 private long
// 初值 0 — 全链路没有一处 setInputCount() 调用 → metadata 用户看到的是 0/0。
// TransformController.toDataFrame() 的 data[]/rows[] 双解析都通过, output 正确,
// 只是 statistics 计数器没维护。Wave-2B 的 UT-5 单测 mock TransformerService
// 没暴露这个真实 gap。

import { BASE, http, get, post, check, report, gatewayAlive, note } from './lib/w4-common.mjs';

console.log('═══ 02-data.mjs — Wave-4.1 域2: data topic TransformController ═══');
console.log(`  BASE: ${BASE}`);
await gatewayAlive() || process.exit(77);

// ── T1 ── meta 端点 6 步清单
{
  const r = await get('/api/v1/engine/data/transform/meta', { timeout: 10000 });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || {};
  const steps = data.availableSteps || [];
  const types = steps.map(s => s.type);
  check('T1 meta 200 + code===0', r.status === 200 && code === 0, `status=${r.status} code=${code}`);
  check('T1 totalSteps === 6', data.totalSteps === 6, `totalSteps=${data.totalSteps}`);
  check('T1 6 个 type 全齐 (cleansing/mapping/typeConversion/validation/aggregation/calculator)',
    ['cleansing', 'mapping', 'typeConversion', 'validation', 'aggregation', 'calculator'].every(t => types.includes(t)),
    `types=[${types.join(',')}]`);
}

// ── T2 ── 5 步链 (无 calculator, Nashorn 从 Java 17 移除)
// 输入 DataFrame: 3 行员工, 有空格
{
  const df = {
    columns: ['emp', 'dept', 'age', 'salary'],
    data: [
      { emp: 'Al', dept: 'R&D', age: '30', salary: 10000 },
      { emp: 'Be', dept: 'R&D', age: '40', salary: 11000 },
      { emp: 'Ce', dept: 'QA', age: '35', salary: 12000 },
    ],
  };
  const chain5 = [
    { type: 'cleansing', params: { op: 'trim', column: 'emp' } },
    { type: 'mapping', params: { mapping: { emp: 'name' } } },
    { type: 'typeConversion', params: { column: 'age', target: 'integer' } },
    { type: 'validation', params: { rules: [{ field: 'age', type: 'min', value: 1 }] } },
    { type: 'aggregation', params: { groupBy: 'dept', op: 'avg', column: 'salary' } },
  ];

  const r = await post('/api/v1/engine/data/transform/execute', {
    body: { input: df, chain: chain5 },
    timeout: 30000,
  });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || {};
  const out = data.output || {};
  const stats = data.statistics || {};

  check('T2 5 步链 execute 200 + code===0 + success===true',
    r.status === 200 && code === 0 && data.success === true,
    `status=${r.status} code=${code} success=${data.success}`);
  // P0-2 暴露: statistics 4 字段应为输入行数 (这里 input=3) — 实测 0
  check('T2 P0-2 — statistics.inputCount > 0 (production 5-step 链路 input 被 processor 接收)',
    (stats.inputCount || 0) > 0,
    `inputCount=${stats.inputCount} outputCount=${stats.outputCount} (期望 ≥ 3; 实际 0 → TransformStatistics 4 字段无 setter)`,
  );
  check('T2 P0-2 — output rows 真实转换 (非 0 行)',
    Array.isArray(out.rows) && out.rows.length > 0,
    `outRows=${out.rows?.length} outCols=${out.columns?.length} (期望 R&D 1 行 + QA 1 行 = 2)`,
  );
  note('T2 观察输出', JSON.stringify({ stats, sampleRow: out.rows?.[0] }).slice(0, 240));
}

// ── T3 ── 未知 step type → code===400
{
  const r = await post('/api/v1/engine/data/transform/execute', {
    body: { input: { rows: [] }, chain: [{ type: 'notExist' }] },
    timeout: 10000,
  });
  const code = r.body?.code ?? r.body?.status;
  const msg = r.body?.message || r.body?.reason || JSON.stringify(r.body);
  check('T3 未知 step → code===400 + msg 含 notExist', code === 400 && String(msg).includes('notExist'),
    `status=${r.status} code=${code} msg=${String(msg).slice(0, 100)}`);
}

// ── T4 ── 缺 input → code===400
{
  const r = await post('/api/v1/engine/data/transform/execute', {
    body: { chain: [] },
    timeout: 10000,
  });
  const code = r.body?.code ?? r.body?.status;
  const msg = r.body?.message || r.body?.reason || JSON.stringify(r.body);
  check('T4 缺 input → code===400 + msg 含 input', code === 400 && String(msg).includes('input'),
    `status=${r.status} code=${code} msg=${String(msg).slice(0, 100)}`);
}

report('02-data (TransformController 6 步)', '/tmp/wave4_02-data.json');

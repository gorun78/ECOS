#!/usr/bin/env node
/**
 * check-scenario-openapi.mjs — SCENARIO_OPENAPI.md 静态门禁（F07-15）
 *
 * 断言：
 *   1. 12 个 D-2 operationId 全部出现在文件里；
 *   2. 每条端点的路径 + HTTP 方法与 D-2 表格一致（只查 presence + method+path 对齐，
 *      不做完整 OpenAPI 结构解析——那是后端 OpenApiPathParityTest 的职责）。
 *   3. 503 只在认知/引擎依赖端点上出现（N1/N2/N3/N9~N12 不许有 503；
 *      N4~N8 必须能出现 503）——用于反证 "503 掩成 200" 类违规不重新出现。
 *
 * 用法：node docs/40-实现/check-scenario-openapi.mjs
 * 退出：0=pass；非 0=fail（并打印失败项）。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const FILE = resolve(__dirname, 'SCENARIO_OPENAPI.md');
const text = readFileSync(FILE, 'utf8');

/** D-2 权威表：operationId → { path, method } */
const EXPECT = [
  ['getScenarioCompleteness',    'GET',   '/api/v1/workspace/scenarios/{id}/completeness'],
  ['listBindingCatalog',         'GET',   '/api/v1/workspace/binding-catalog'],
  ['getForecastScope',           'GET',   '/api/v1/workspace/scenarios/{id}/forecast-scope'],
  ['getCognitionDetect',         'GET',   '/api/v1/business/scenarios/{id}/cognition/detect'],
  ['getOperationEval',           'GET',   '/api/v1/business/scenarios/{id}/cognition/operation-eval'],
  ['listHypotheses',             'GET',   '/api/v1/business/scenarios/{id}/cognition/hypotheses'],
  ['listBeliefs',                'GET',   '/api/v1/business/scenarios/{id}/cognition/beliefs'],
  ['createScenarioAction',       'POST',  '/api/v1/workspace/scenarios/{id}/actions'],
  ['listScenarioActions',        'GET',   '/api/v1/workspace/scenarios/{id}/actions'],
  ['approveScenarioAction',      'POST',  '/api/v1/workspace/actions/{actionId}/approve'],
  ['listScenarioDecisions',      'GET',   '/api/v1/workspace/scenarios/{id}/decisions'],
  ['transitionScenarioStatus',   'PATCH', '/api/v1/workspace/scenarios/{id}/status'],
];

const ENGINE_503_IDS = new Set([
  'getCognitionDetect', 'getOperationEval', 'listHypotheses', 'listBeliefs', 'createScenarioAction',
]);

const fails = [];

for (const [opId, method, path] of EXPECT) {
  // 1) operationId presence
  if (!text.includes(`operationId: ${opId}`)) {
    fails.push(`[missing] operationId '${opId}' not found`);
    continue;
  }
  // 2) path + method alignment: find `path:` line then scan forward for method + operationId
  const idxPath = text.indexOf(path);
  if (idxPath < 0) {
    fails.push(`[missing] path '${path}' not present in OpenAPI fragment`);
    continue;
  }
  const after = text.slice(idxPath, idxPath + 2500);
  if (!new RegExp(`^\\s{4}${method.toLowerCase()}:`, 'm').test(after)) {
    fails.push(`[method] '${opId}' expected '${method}' under '${path}' — method line not found`);
  }
  if (!after.includes(`operationId: ${opId}`)) {
    fails.push(`[associate] '${method} ${path}' block does not carry operationId '${opId}'`);
  }
}

// 3) 503 placement policy (per-endpoint block delimited by next path line at yaml indent 2)
function extractBlock(pathStr) {
  const idxPath = text.indexOf('\n  ' + pathStr + ':');
  if (idxPath < 0) return '';
  const rest = text.slice(idxPath + 1);
  // find next yaml path line at indent 2 starting with `  /` and ending with `:`
  const next = rest.slice(1).search(/\n {2}\//);
  return next < 0 ? rest : rest.slice(0, next + 1);
}

/** 从 path block 内截取目标 method 的子块（到下一个同级 method 或块尾）。 */
function opScopedResponses(block, method) {
  const m = method.toLowerCase();
  const re = new RegExp('\\n {4}' + m + ':', 'm');
  const mm = block.match(re);
  if (!mm) return '';
  const start = (mm.index ?? 0) + 1;
  const rest = block.slice(start);
  // next sibling method at indent 4 (any of the http verbs) marks the end
  const nextVerb = rest.search(/\n {4}(get|post|put|patch|delete|head|options):/);
  return nextVerb < 0 ? rest : rest.slice(0, nextVerb);
}
for (const [opId, method, path] of EXPECT) {
  const block = extractBlock(path);
  if (!block) continue;
  // only look for method-scoped 503 (i.e. `'503':` appearing in the responses of this method)
  const scoped = opScopedResponses(block, method);
  const has503 = /'\s*503\s*'|\b503\b/.test(scoped);
  if (ENGINE_503_IDS.has(opId) && !has503) {
    fails.push(`[503-required] '${opId}' at '${method} ${path}' must declare a 503 response (engine/schema)`);
  }
  if (!ENGINE_503_IDS.has(opId) && /'\s*503\s*'/.test(scoped)) {
    fails.push(`[503-forbidden] '${opId}' at '${method} ${path}' should not carry a 503 (would mask real status)`);
  }
}

if (fails.length) {
  console.error('check-scenario-openapi: FAILED');
  for (const f of fails) console.error(' - ' + f);
  process.exit(1);
}
console.log(`check-scenario-openapi: OK (${EXPECT.length} endpoints, method+path+503 policy verified)`);

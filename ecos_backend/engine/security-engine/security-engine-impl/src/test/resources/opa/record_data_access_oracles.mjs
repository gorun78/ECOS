// data_access 录制型 oracle 的重录工具（与 data_access_oracles.json 同目录，属该文件的制品 provenance）。
//
// 为什么这份文件必须入库而不是用完即删：oracle JSON 的 _provenance 承诺「改了 rego 必须按
// _provenance.command 重录」；若录制器只活在一次性临时件里，该承诺不可兑现，oracle 就退化成
// 无法再生成、只能照着 rego 手抄的合成数据 —— 正是本仓定义的假绿形态。
//
// 仓根由本文件自身位置向上反解，禁内联任何本机绝对路径（本仓红线：机器环境信息不入库）。
//
// 用法（需 ecos-opa 容器在跑；只调用容器内 /opa eval 离线求值，不触碰 live policy store）：
//   node ecos_backend/engine/security-engine/security-engine-impl/src/test/resources/opa/record_data_access_oracles.mjs
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CONTAINER = process.env.OPA_CONTAINER || 'ecos-opa';
const OUT = path.join(HERE, 'data_access_oracles.json');
// Git Bash 会把 '/policies/...' 这类参数当 Windows 路径改写，必须关掉转换，否则 -d 指不到容器内文件。
const ENV = {...process.env, MSYS_NO_PATHCONV: '1'};

function resolveRepoRoot(from, maxUp = 14) {
  let dir = from;
  for (let i = 0; i < maxUp; i++) {
    if (fs.existsSync(path.join(dir, 'ecos-docker', 'opa-policies', 'data_access.rego'))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  throw new Error(`未找到 ecos-docker/opa-policies/data_access.rego（自 ${from} 上溯 ${maxUp} 级）—— 禁把"找不到"当成合法路径`);
}

const REPO = resolveRepoRoot(HERE);
const REGO = path.join(REPO, 'ecos-docker', 'opa-policies', 'data_access.rego');

const SUB = (userId, roles, department, tenantId) => ({userId, roles, department, tenantId});
const SCOPE = (departmentId, projectIds) => ({resource: {dataScope: {departmentId, projectIds}}});
const FACT = 'ecos_dw.fc_cost_allocation_fact';
const RESOURCE_FACT = 'ecos_dw.fc_resource_input_fact';

/**
 * 用例集＝§4.7.2 角色×通道矩阵的 AI/导出两列 + 三条反例，与 rolechannel 具名用例一一对应。
 * 增删用例必须先改详设矩阵行，不能只改这里。
 */
const CASES = [
  {name: 'fin_export', input: {purpose: 'export', asset: FACT, subject: SUB('u-fin', ['finance_lead'], 'd00', 't-demo')}},
  {name: 'fin_page', input: {purpose: 'page', asset: FACT, subject: SUB('u-fin', ['finance_lead'], 'd00', 't-demo')}},
  {name: 'fin_ai', input: {purpose: 'ai', asset: FACT, subject: SUB('u-fin', ['finance_lead'], 'd00', 't-demo')}},
  {name: 'proj_ai_p1', input: {purpose: 'ai', asset: FACT, subject: SUB('u-p1', ['project_owner', 'non_finance'], 'd01', 't-demo'), ...SCOPE('d01', ['p1'])}},
  {name: 'proj_ai_p2', input: {purpose: 'ai', asset: FACT, subject: SUB('u-p1', ['project_owner', 'non_finance'], 'd01', 't-demo'), ...SCOPE('d02', ['p2'])}},
  {name: 'proj_ai_no_scope', input: {purpose: 'ai', asset: FACT, subject: SUB('u-p1', ['project_owner', 'non_finance'], 'd01', 't-demo')}},
  {name: 'proj_export', input: {purpose: 'export', asset: FACT, subject: SUB('u-p1', ['project_owner', 'non_finance'], 'd01', 't-demo')}},
  {name: 'dept_ai', input: {purpose: 'ai', asset: RESOURCE_FACT, subject: SUB('u-d01', ['dept_user', 'non_finance'], 'd01', 't-demo'), ...SCOPE('d01', ['p1'])}},
  {name: 'dept_export', input: {purpose: 'export', asset: FACT, subject: SUB('u-d01', ['dept_user', 'non_finance'], 'd01', 't-demo')}},
  {name: 'norole_page', input: {purpose: 'page', asset: FACT, subject: SUB('u-none', ['no_such_role'], 'd09', 't-demo')}},
  {name: 'norole_export', input: {purpose: 'export', asset: FACT, subject: SUB('u-none', ['no_such_role'], 'd09', 't-demo')}},
  {name: 'norole_ai', input: {purpose: 'ai', asset: FACT, subject: SUB('u-none', ['no_such_role'], 'd09', 't-demo')}},
  {name: 'ai_channel_inferred', input: {action: 'TOOL_CALL:forecast.drilldown',
    subject: {userId: 'u-p1', roles: ['project_owner', 'non_finance'], department: 'd01'},
    resource: {dataScope: {departmentId: 'd01', projectIds: ['p1']}}, environment: {channel: 'AGENT'}}},
  {name: 'ai_channel_no_department', input: {action: 'TOOL_CALL:forecast.drilldown',
    subject: {userId: 'u-p1', roles: ['project_owner', 'non_finance'], department: ''},
    resource: {dataScope: {departmentId: 'd01', projectIds: ['p1']}}, environment: {channel: 'AGENT'}}},
];

/**
 * opa eval 的 `-I` 把 stdin 当作**输入文档本身**（不是 `{"input": …}` 信封）。
 * 曾按信封形态发送 ⇒ 策略读到 input.input.*，全部主体角色丢失 ⇒ 14 例整批退化成
 * NO_DATA_ACCESS_ROLE 且仍"看起来绿"。因此下面 evalInputEcho() 逐例回读 input 校验绑定，
 * 管道接错时立刻失败，而不是产出一份语义全错的 oracle。
 */
function evalRego(query, input) {
  const out = execFileSync('docker', ['exec', '-i', CONTAINER, '/opa', 'eval',
    '-d', '/policies/data_access.rego', '-I', '--format', 'json', query],
    {input: JSON.stringify(input), encoding: 'utf8', env: ENV});
  const parsed = JSON.parse(out);
  if (!parsed.result || parsed.result.length === 0) {
    throw new Error(`query ${query} 无求值结果（未定义？）: ${out.slice(0, 300)}`);
  }
  return parsed.result[0].expressions[0].value;
}

/** 输入绑定自检：同一 stdin 下回读 input，必须与发送的对象深度等价（OPA 按 Go 规则输出键序，故不做逐字串比）。 */
function assertInputBound(input) {
  const echo = evalRego('input', input);
  const sent = canonical(input), got = canonical(echo);
  if (sent !== got) {
    throw new Error(`输入未绑定到策略的 input 面（管道形态错误）：发送 ${sent.slice(0, 200)} 回读 ${got.slice(0, 200)}`);
  }
}

/** 递归按键排序的 JSON 规范化，用于深度等价比较。 */
function canonical(v) {
  return JSON.stringify(sortV(v));
  function sortX(o) {
    const out = {};
    for (const k of Object.keys(o).sort()) out[k] = sortV(o[k]);
    return out;
  }
  function sortV(x) {
    if (Array.isArray(x)) return x.map(sortV);
    if (x && typeof x === 'object') return sortX(x);
    return x;
  }
}

const version = execFileSync('docker', ['exec', CONTAINER, '/opa', 'version'], {encoding: 'utf8', env: ENV}).trim();

const rows = CASES.map((c) => {
  assertInputBound(c.input);
  const doc = evalRego('data.ecos.data_access', c.input);
  if (typeof doc?.allow !== 'boolean') {
    throw new Error(`用例 ${c.name} 的策略应答缺布尔 allow（rego 面漂移）：${JSON.stringify(doc).slice(0, 200)}`);
  }
  // serverBody ＝ OPA server 对 POST /v1/data/ecos/data_access 的应答原文（{"result":{…}}），
  // Java 侧因此把真应答形状喂给生产解析器，而不是自造中间形态。
  return {name: c.name, input: c.input, serverBody: {result: doc}, allow: doc.allow,
    obligations: doc.obligations};
});

if (rows.every((r) => r.allow === false)) {
  throw new Error('14 例全为 allow=false —— 整批拒绝通常不是策略结论而是求值面失效，拒绝写出');
}

const sha = createHash('sha256').update(fs.readFileSync(REGO)).digest('hex');

const bundle = {
  _provenance: {
    note: '录制型 oracle —— 逐字取自本仓 data_access.rego 在真 Rego 引擎（dev 容器 ecos-opa 的 /opa）上的求值结果；'
      + 'serverBody 是 OPA server 对文档查询 POST /v1/data/ecos/data_access 的应答形态。'
      + 'Java 侧用例据此断言裁决链，禁手工编辑本文件：改了 rego 必须用同目录 record_data_access_oracles.mjs 重录。',
    policySource: 'ecos-docker/opa-policies/data_access.rego',
    policySha256: sha,
    opaVersion: version,
    command: "printf '<input-json>' | docker exec -i ecos-opa /opa eval -d /policies/data_access.rego -I --format json 'data.ecos.data_access'",
    recorder: 'record_data_access_oracles.mjs（同目录；仓根由脚本自身位置反解；含逐例 input 绑定自检）',
    recordedAt: new Date().toISOString(),
  },
  cases: rows,
};

fs.writeFileSync(OUT, JSON.stringify(bundle, null, 2) + '\n', 'utf8');
console.log(`recorded ${rows.length} cases -> ${path.relative(REPO, OUT)}`);
console.log(`policySha256=${sha}`);
for (const r of rows) console.log(`  ${r.name} => allow=${r.allow} obligations=${JSON.stringify(r.obligations)}`);

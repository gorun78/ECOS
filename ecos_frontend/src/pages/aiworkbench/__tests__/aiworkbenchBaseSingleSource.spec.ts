// @vitest-environment node
/**
 * AI 工作台域基址单源回归门（#143/B5-25，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把 `pages/aiworkbench/api.ts` 代码行私设基址 **27 处**（单文件，本波最大单项）改由
 * `API_BASE` 派生，形态拆分 = **15 单引号 + 12 反引号 + 0 双引号**（逐条取自 `git show HEAD:`
 * 实跑读出）。至此 `pages/aiworkbench` 整域 **29** 个生产文件零私设基址——该域此前只有
 * `useLogicPipelineRun.ts`（#143/B5-24）与 SSE 流式腿（#143/B5-13）已派生化。</p>
 *
 * <p>本文件有两处别处没有的形状。其一：`API_BASE` 早已因流式腿被导入（第 12 行
 * `import { API_BASE, rawStreamUrlFetch } from '../../services/httpClient'`），所以「导入腿恰 1 条」
 * 判据在这里数的是**含 API_BASE 的 import 行**而非新增行——若有人另起第二处来源，本腿即红。其二：
 * 同一批导入带来了一条改前就已是派生态的站点（`rawStreamUrlFetch` 的
 * `` `${API_BASE}/v1/oag/chat/stream` ``，#143/B5-13 落），故本文件的派生面全量是
 * **27（本批挪形）+ 1（改前既存）= 28**，本门按全量面逐字钉死，不做「只看本批」的减法——
 * 减法会让「本批顺手把流式腿也改了形」这类越界改动读成绿。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**（`API_BASE` 单源取值 `"/api"`）。故本门把每个
 * 派生点按实际值还原成字符串，与改前快照做排序多重集比对并核对条数；单引号→模板串的改写会把
 * `${…}` 当字面量吞掉，因此快照里凡是模板串形态的站点，还原值必须原样含 `${encodeURIComponent(…)}`，
 * 本门逐字比，不做归一。</p>
 *
 * <p>另记一条口径差，防后续批误推：`/v1/ontology/proposals` 与 `/v1/aip/agents` 这类族在双形态
 * 棘轮门里是**跨文件重复族**，本批只挪属主形态、不删属主，故棘轮 dupBoth 不因本批下降；
 * 「族面不变」不等于「本文件干净」，两门各判各的。</p>
 *
 * <p>**#145/B6-3 起的判据形状**：本域属主 `api.ts` 把 `/v1/aip/agents`、`/v1/aip/audit-logs` 两条
 * 单引号腿挪成了两枚**导出常量声明**（面值不变，站点从使用处上移到声明行），第三条 `` `${API_BASE}
 * /v1/aip/agents/${encodeURIComponent(id)}` `` 改由常量派生。只认 `` `${API_BASE}…` `` 的旧判据会
 * 把这条派生腿读成隐形（实跑 27 对 28）。故解析表扩进 `AIP_AGENTS_BASE` / `AIP_AUDIT_LOGS_BASE`，
 * 28 条面值钉死不变。**本门刻意不把裸引用（`` apiFetchData(AIP_AGENTS_BASE) ``）计为站点**——声明行
 * 与使用行会双计同一枚面值，把条数虚抬；裸引用面由 #145/B6-3 的 `b6AipFamilySingleSource` 门按
 * 「绑定行不算腿、裸引用算腿」的口径承证，两门合起来才是完整面。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../services/httpClient';
import { AIP_AGENTS_BASE, AIP_AUDIT_LOGS_BASE } from '../../../pages/aiworkbench/api';

// 本门位于 `src/pages/aiworkbench/__tests__/`，到 `src/` 的相对深度是三档（少一档即读不到被测域）
const SRC_ROOT = fileURLToPath(new URL('../../../', import.meta.url));

/** 本域生产文件数（实跑读出，防扫描根漂移成零命中）。 */
const DOMAIN_DIR = 'pages/aiworkbench';
const DOMAIN_PROD_FILES = 29;

const FILES: ReadonlyArray<{ rel: string; importPath: string }> = [
  { rel: 'pages/aiworkbench/api.ts', importPath: '../../services/httpClient' },
];

/** 本文件派生面全量 = 本批挪形 27 + 改前既存流式腿 1（实跑读出）。 */
const SITES_PER_FILE: Readonly<Record<string, number>> = {
  'pages/aiworkbench/api.ts': 28,
};

const HARDCODED_RE = /["'`]\/api\b/;
/** 站点形态：`` `${已知常量}<尾串>` ``（#145/B6-3 起解析表含 AIP 域单源常量）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['AIP_AGENTS_BASE', AIP_AGENTS_BASE],
  ['AIP_AUDIT_LOGS_BASE', AIP_AUDIT_LOGS_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split('\n').filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, rel), 'utf8');
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx')
  );
}

function walk(dirRoot: string, out: string[]): string[] {
  for (const entry of readdirSync(join(SRC_ROOT, dirRoot))) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(SRC_ROOT, dirRoot, entry);
    if (statSync(full).isDirectory()) {
      walk(`${dirRoot}/${entry}`, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src)) {
    for (const m of line.matchAll(SITE_RE)) {
      const base = CONST_VALUES.get(m[1]);
      if (base !== undefined) out.push(base + m[2]);
    }
  }
  return out;
}

/**
 * 改前裸形态快照（27 条，带定界符原样收录，`git show HEAD:` 实跑读出，非手抄）：
 * agent-mesh/agents 2（单引号）+ 2（模板串）、guardrails/policies 1、pipeline/definitions 1、
 * ontology/actions 1（带 query 模板串）+ 2（单引号）+ 2（模板串）+ 1（execute）、
 * aip/models 1 + templates 1 + instantiate 1 + agents 1 + agents/{id} 1 + agent-metrics 2、
 * agent-loop/sessions 1（模板串带 query）+ 1、aip/eval 1 + 1、agent-loop/chat 1、
 * aip/audit-logs 1、ontology/proposals 1 + 1（execute）。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  "'/api/v1/agent-mesh/agents'",
  "'/api/v1/agent-mesh/agents'",
  '`/api/v1/agent-mesh/agents/${encodeURIComponent(id)}`',
  '`/api/v1/agent-mesh/agents/${encodeURIComponent(id)}`',
  "'/api/v1/guardrails/policies'",
  "'/api/v1/pipeline/definitions'",
  '`/api/v1/ontology/actions?objectTypeId=${encodeURIComponent(objectTypeId)}`',
  "'/api/v1/ontology/actions'",
  "'/api/v1/ontology/actions'",
  '`/api/v1/ontology/actions/${encodeURIComponent(id)}`',
  '`/api/v1/ontology/actions/${encodeURIComponent(id)}`',
  '`/api/v1/ontology/actions/${encodeURIComponent(id)}/execute`',
  "'/api/v1/aip/models'",
  "'/api/v1/aip/agents/templates'",
  "'/api/v1/aip/agents/instantiate'",
  "'/api/v1/aip/agents'",
  '`/api/v1/aip/agents/${encodeURIComponent(id)}`',
  '`/api/v1/aip/agent-metrics/${encodeURIComponent(agentId)}`',
  '`/api/v1/aip/agent-metrics/${encodeURIComponent(agentId)}/errors`',
  '`/api/v1/agent-loop/sessions?agentId=${encodeURIComponent(agentId)}`',
  "'/api/v1/agent-loop/sessions'",
  "'/api/v1/aip/eval/run'",
  '`/api/v1/aip/eval/${encodeURIComponent(evalId)}/report`',
  "'/api/v1/agent-loop/chat'",
  "'/api/v1/aip/audit-logs'",
  "'/api/v1/ontology/proposals'",
  '`/api/v1/ontology/proposals/${encodeURIComponent(id)}/execute`',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

/**
 * 改前既存的派生态站点（`git show HEAD:` 实跑读出，整文件仅 1 条），形态即还原后的运行期面值。
 * 与上面 27 条分列，是为了让「本批挪了哪 27 条」和「本批没动的那 1 条」在同一判据里各自可查。
 */
const PRE_EXISTING_DERIVED = '/api/v1/oag/chat/stream';

describe('pages/aiworkbench 域基址单源 — 私设基址清零 (#143/B5-25)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it(`整域 ${DOMAIN_PROD_FILES} 个生产文件代码行零私设基址（递归扫，注释里的端点说明不计）`, () => {
    const prodFiles = walk(DOMAIN_DIR, []);
    expect(prodFiles.length).toBe(DOMAIN_PROD_FILES);
    for (const rel of prodFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('域内一律从 httpClient 导入 API_BASE（含 API_BASE 的 import 行恰 1 条），且相对路径随目录深度正确', () => {
    for (const { rel, importPath } of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
        new RegExp(`from '${importPath}'|from "${importPath}"`),
      );
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），域内零 /api/api 双前缀', () => {
    for (const { rel } of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 28 条 URL 多重集 = 改前快照 27 + 既存 1，且条数吻合（模板串插值原样保留）', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 派生态条数不符`).toBe(SITES_PER_FILE[rel]);
      all.push(...vals);
    }
    expect(all.length).toBe(28);
    expect(all.filter((v) => v === PRE_EXISTING_DERIVED).length, '既存流式腿应恰 1 条且面值未变').toBe(1);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS, PRE_EXISTING_DERIVED].sort());
  });

  it('快照自身可校验：27 条 = 15 单引号 + 12 反引号 + 0 双引号且全部以 /api/v1/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(27);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(15);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(12);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/v1/')).toBe(true);
  });

  it('判据能变红：违例源命中（含模板串与单引号）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "  const raw = await apiFetchData<any[]>('/api/v1/aip/models');",
      '  return apiFetchData<OntologyProposalVo>(`/api/v1/ontology/proposals/${encodeURIComponent(id)}/execute`, {',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, rawStreamUrlFetch } from '../../services/httpClient';",
      '  const raw = await apiFetchData<any[]>(`${API_BASE}/v1/aip/models`);',
      '  return apiFetchData<OntologyProposalVo>(`${API_BASE}/v1/ontology/proposals/${encodeURIComponent(id)}/execute`, {',
      '  // GET /api/v1/aip/eval/run 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(2);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/aip/models',
      '/api/v1/ontology/proposals/${encodeURIComponent(id)}/execute',
    ]);
    // #145/B6-3：常量派生腿必须还原成同面值；未知常量不参与解析（防把任意 `${x}` 吃成 URL）
    expect(derivedValues('  await apiFetchData(`${AIP_AGENTS_BASE}/${encodeURIComponent(id)}`, { method: "PUT" });')).toEqual([
      '/api/v1/aip/agents/${encodeURIComponent(id)}',
    ]);
    expect(derivedValues('  await apiFetchData(`${SOME_UNKNOWN_BASE}/v1/aip/agents`);')).toEqual([]);
  });
});

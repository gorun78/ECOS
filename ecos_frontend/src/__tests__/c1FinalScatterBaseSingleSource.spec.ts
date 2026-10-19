// @vitest-environment node
/**
 * C1 基址单源末批（#143/B5-26）取证门 —— 散点 15 文件 21 处私设基址派生化，
 * `api-base-single-source` 基线由 15 枚归零（差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批与前九批的形状差在**站点分布**而非数量：15 文件跨 5 个目录深度（`hooks/`、`pages/` 根、
 * `pages/data-quality/api/`、`pages/knowledge/{pages,pages/components/govern,tabs}/`、`providers/`），
 * 故「导入腿相对路径随深度正确」在这里是真判据（写错一档 tsc 会报，但门禁本身检不出；深度表逐条实跑读出）。
 * 21 处形态拆分 = **8 单引号 + 2 双引号 + 11 反引号**（逐条取自 `git show HEAD:`，非手抄）；
 * 双引号两枚系 `pages/data-quality/api/_base.ts` 的 `DQ_GOV_BASE`/`DQ_LEGACY_BASE` 常量。</p>
 *
 * <p>两枚站点是前九批没有的形状，通用「行内派生态还原」吃不到，各自单列一条逐字腿：
 * ① `RuleRepoTable.tsx` 的 `compliance-rules?${…}` 模板**跨 5 行**，行级正则吃不进换行 ⇒ 改判
 * 「模板左半逐字 + 行内还原恰 4 条（差额即本腿）+ 整文件零裸面」；② `GraphBuilderTab.tsx` 不是传输
 * URL 而是 **JSX 显示文本**（改前 `waiting PMO-56 backend \`/api/v1/knowledge/sync/jobs\``），
 * 派生化会把面值带进模板插值，本批改成转义反引号形态，本腿按壳层剥离还原渲染字节并与改前逐字比。</p>
 *
 * <p>本批自伤一处、由本门形状判据抓出，记此防复发：转换脚本对 10 枚单/双引号站点漏剥 `/api` 前缀
 * ⇒ 产出 `` `${API_BASE}/api/v1/…` `` 双前缀（面值改变，「派生态还原 = HEAD 快照」一比即红）。
 * 关键判读：这种畸形在**源码串面**并不含 `/api/api`（前缀要等运行期拼接才现形），所以双前缀腿必须扫
 * 还原后的派生态面值而非源码行——本门据此把整 src 面按面值判，并在「能变红」腿里把「源码面 0 命中 /
 * 面值面 1 命中」并列表出来，作为 D-B5-40 盲区形状的第二例实证。</p>
 *
 * <p>闭合面（末条腿，实跑读出）：全仓 681 个生产文件里代码行 `["'`]/api` 命中**只剩单源本体**
 * `services/httpClient.ts` 的 2 处（`export const API_BASE = "/api"` + 健康探针 `fetch("/api/health")`）
 * ⇒ C1 私设基址面清零。另记与 D-B5-40 的分工，防止把「基线归零」读成「C1 全闭合」：
 * 「同一 URL 族的第二生产方」仍由 `urlFamilyDualFormRatchet`（dupBoth 30 族 / 69 属主）记账，属 B6 存量债。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';

// 本门位于 `src/__tests__/`，到 `src/` 的相对深度是一档
const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));
const CANONICAL = 'services/httpClient.ts';
/** 全仓生产文件数（与本门同口径实跑读出，防扫描根漂移成零命中）。 */
const PROD_FILES = 681;

/** 本批 15 文件：rel →（导入腿相对路径、本批派生点数）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string; sites: number }> = [
  { rel: 'hooks/useBindingCatalog.ts', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/ContractManager.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/DataAssetsDashboard.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/DataLineage.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/ProjectTracker.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/TenantManager.tsx', importPath: '../services/httpClient', sites: 2 },
  { rel: 'pages/TokenDashboard.tsx', importPath: '../services/httpClient', sites: 1 },
  { rel: 'pages/data-quality/api/_base.ts', importPath: '../../../services/httpClient', sites: 2 },
  { rel: 'pages/knowledge/pages/ExtractionPage.tsx', importPath: '../../../services/httpClient', sites: 1 },
  // 5 点里 1 点是跨行模板（见 MULTILINE_REL），行级还原只吃到 4 点
  { rel: 'pages/knowledge/pages/components/govern/RuleRepoTable.tsx', importPath: '../../../../../services/httpClient', sites: 5 },
  { rel: 'pages/knowledge/tabs/DataWorkbenchImportTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'pages/knowledge/tabs/DocumentUploadTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  // JSX 显示文本位，行级还原不吃（见 JSX_REL）
  { rel: 'pages/knowledge/tabs/GraphBuilderTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'pages/knowledge/tabs/KnowledgeRuleRepositoryTab.tsx', importPath: '../../../services/httpClient', sites: 1 },
  { rel: 'providers/graphqlClient.ts', importPath: '../services/httpClient', sites: 1 },
];

const MULTILINE_REL = 'pages/knowledge/pages/components/govern/RuleRepoTable.tsx';
const JSX_REL = 'pages/knowledge/tabs/GraphBuilderTab.tsx';
/**
 * 通用行级还原可吃到的站点：跨行模板那 1 点从 RuleRepoTable 的 5 点里减掉，
 * JSX 显示文本站整个不吃 ⇒ 19 = 21 − 1（跨行）− 1（JSX）。
 */
const LINE_VISIBLE = FILES
  .filter((f) => f.rel !== JSX_REL)
  .map((f) => (f.rel === MULTILINE_REL ? { ...f, sites: f.sites - 1 } : f));

/** 跨行模板站点：改后左半 / 改前左半（快照里右半在后续行，脚本以 ` …<<MULTILINE>>` 标注截断）。 */
const MULTILINE_HEAD = '`${API_BASE}/v1/knowledge/compliance-rules?${';
const MULTILINE_SNAP_HEAD = '`/api/v1/knowledge/compliance-rules?${';
/** JSX 显示文本站：改前整行（`git show HEAD:` 读出）与其中的渲染面值。 */
const JSX_PRE_HEAD =
  '<p className={`text-[10px] ${styles.muted} font-mono`}>waiting PMO-56 backend `/api/v1/knowledge/sync/jobs`</p>';
const JSX_SNAP = '`/api/v1/knowledge/sync/jobs`';

const HARDCODED_RE = /["'`]\/api\b/;
const DERIVED_RE = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
const NO_SLASH_AFTER_BASE = /`\$\{API_BASE\}(?!\/)/;
const DOUBLE_PREFIX_RE = /\/api\/api\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, ...rel.split('/')), 'utf8');
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
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
    for (const m of line.matchAll(DERIVED_RE)) out.push(`${API_BASE}${m[1]}`);
  }
  return out;
}

/**
 * 改前裸形态快照（21 条 = 本腿 19 条 + 跨行 1 条 + JSX 1 条，带定界符原样收录，
 * `git show HEAD:` 实跑读出）：19 条走通用行级还原腿。
 */
const LINE_SNAP: readonly string[] = [
  "'/api/v1/workspace/binding-catalog'",
  '`/api/v1/ecos/objects/Contract?${params.toString()}`',
  "'/api/v1/datanet'",
  '`/api/v1/engine/data/lineage?tableName=${encodeURIComponent(tbl)}`',
  '`/api/v1/ecos/objects/Project?${params.toString()}`',
  '`/api/v1/system/tenants/${selectedTenantId}/usage?range=${usageRange}`',
  '`/api/v1/system/tenants/${selectedTenantId}/invoice?month=${invoiceMonth}`',
  '`/api/telemetry/tokens/summary?range=${range}`',
  '"/api/v1/dq"',
  '"/api/v1/ecos/dq"',
  "'/api/v1/ontology/mappings'",
  "'/api/v1/knowledge/compliance-rules'",
  '`/api/v1/knowledge/compliance-rules/${editingId}`',
  '`/api/v1/knowledge/compliance-rules/${r.id}`',
  '`/api/v1/knowledge/compliance-rules/${r.id}`',
  "'/api/v1/ecos/versions'",
  "'/api/v1/knowledge/extract/history'",
  "'/api/v1/knowledge/rules?groupBy=regulation'",
  "'/api/graphql'",
];

const UNWRAPPED = LINE_SNAP.map((s) => s.slice(1, -1));

describe('C1 基址单源末批散点 15 文件 — 私设基址派生化 (#143/B5-26)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('本批 15 文件代码行零私设基址，且各自从 httpClient 导入 API_BASE（导入腿恰 1 条、相对路径随目录深度正确）', () => {
    for (const { rel, importPath } of FILES) {
      const lines = codeLines(read(rel));
      const hits = lines.filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
      const imports = lines.filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
        new RegExp(`from '${importPath}'|from "${importPath}"`),
      );
    }
  });

  it('面值逐字不变：19 条行内派生态按 API_BASE 还原后 = 改前裸形态快照（排序多重集 + 逐文件条数）', () => {
    const all: string[] = [];
    for (const { rel, sites } of LINE_VISIBLE) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 行内派生态条数不符`).toBe(sites);
      all.push(...vals);
    }
    expect(all.length).toBe(19);
    expect([...all].sort()).toEqual([...UNWRAPPED].sort());
  });

  it('跨行模板腿：左半派生逐字 + 行内还原恰 4 条（差额即本腿这条跨行站）+ 整文件零裸面', () => {
    const src = read(MULTILINE_REL);
    expect(codeLines(src).filter((l) => l.trim() === MULTILINE_HEAD).length).toBe(1);
    expect(MULTILINE_HEAD.replace('`${API_BASE}', API_BASE)).toBe(MULTILINE_SNAP_HEAD.slice(1));
    expect(derivedValues(src).length, '5 点里跨行那 1 点不该被行级还原吃到').toBe(4);
    expect(HARDCODED_RE.test(src), '跨行模板体里不得夹带 /api 字面量').toBe(false);
  });

  it('JSX 显示文本腿：按壳层剥离还原渲染面值 = 改前整行内文本逐字（转义反引号未改变渲染字节）', () => {
    const sites = codeLines(read(JSX_REL)).filter((l) => l.includes('waiting PMO-56 backend'));
    expect(sites.length).toBe(1);
    expect(JSX_PRE_HEAD).toContain(JSX_SNAP);
    const rendered = sites[0]
      .trim()
      .replace(/^.*\{`/, '')
      .replace(/`}.*$/, '')
      .replaceAll('\\`', '`')
      .replaceAll('${API_BASE}', API_BASE);
    expect(rendered).toBe(`waiting PMO-56 backend ${JSX_SNAP}`);
    expect(JSX_PRE_HEAD).toContain(rendered);
  });

  it('派生形态唯一：本批 15 文件内 `${API_BASE}` 后必接 /（既有 `${API_BASE}${rel}` 组合腿在别处，不在本面）', () => {
    const bad: string[] = [];
    for (const { rel } of FILES) {
      const src = read(rel);
      for (const line of codeLines(src)) if (NO_SLASH_AFTER_BASE.test(line)) bad.push(`${rel}: ${line.trim()}`);
      // 双前缀只在**还原后**现形（源码面是 `${API_BASE}/api/…`，字面 `/api/api` 并不存在于源码串），
      // 故本腿判派生态面值而非源码串——这与 D-B5-40 说的「引号判据对新形态瞎」是同一形状。
      for (const v of derivedValues(src)) if (DOUBLE_PREFIX_RE.test(v)) bad.push(`${rel}: 还原值 ${v}`);
    }
    expect(bad, `派生态畸形（\`\${API_BASE}\` 后不接斜杠 / 还原后双前缀）：\n${bad.join('\n')}`).toEqual([]);
  });

  it('C1 闭合面（整 src 实跑）：私设基址只剩单源本体 2 处，全仓派生态零 /api/api 双前缀', () => {
    const prod = walk('.', []);
    expect(prod.length, '生产文件数与本批实跑口径不符 ⇒ 扫描根漂移').toBe(PROD_FILES);
    const residual: string[] = [];
    const doubles: string[] = [];
    let derTotal = 0;
    for (const rel of prod) {
      const src = read(rel);
      for (const line of codeLines(src)) {
        if (HARDCODED_RE.test(line)) residual.push(`${rel} — ${line.trim()}`);
        for (const m of line.matchAll(DERIVED_RE)) {
          derTotal += 1;
          if (DOUBLE_PREFIX_RE.test(`${API_BASE}${m[1]}`)) doubles.push(`${rel} — ${line.trim()}`);
        }
      }
    }
    expect(residual.sort()).toEqual([
      `${CANONICAL} — const r = await fetch("/api/health");`,
      `${CANONICAL} — export const API_BASE = "/api";`,
    ]);
    // 全仓行内派生态下限锚在棘轮门 B5-25 实跑的 derSites 315 + 本批 21 −（跨行/JSX 两枚行级不吃）= 334
    expect(derTotal, '行内派生态面须随批累积，低于 334 即扫描根或判据漂移').toBeGreaterThanOrEqual(334);
    expect(doubles, `全仓派生态 /api/api 双前缀：\n${doubles.join('\n')}`).toEqual([]);
  });

  it('快照自身可校验：21 条 = 8 单引号 + 2 双引号 + 11 反引号且全部以 /api 开头（防快照清空成恒真）', () => {
    const allRaw = [...LINE_SNAP, MULTILINE_SNAP_HEAD, JSX_SNAP];
    expect(allRaw.length).toBe(21);
    expect(allRaw.filter((s) => s.startsWith("'")).length).toBe(8);
    expect(allRaw.filter((s) => s.startsWith('"')).length).toBe(2);
    expect(allRaw.filter((s) => s.startsWith('`')).length).toBe(11);
    for (const v of allRaw.map((s) => s.replace(/^["'`]/, ''))) expect(v.startsWith('/api')).toBe(true);
    expect(FILES.reduce((acc, f) => acc + f.sites, 0)).toBe(21);
    expect(LINE_VISIBLE.reduce((acc, f) => acc + f.sites, 0)).toBe(19);
  });

  it('判据能变红：双前缀/不接斜杠/裸形态三类违例各被判出，合规源不命中（防恒绿）', () => {
    const DEFECT = [
      '  const url = `${API_BASE}/api/v1/datanet`;', // 双前缀（本批真实自伤形状）
      '  const u2 = `${API_BASE}v1/ecos/versions`;', // 派生后不接斜杠
      "  const u3 = await apiFetchData('/api/v1/knowledge/rules');", // 裸形态残留
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../../services/httpClient';",
      '  const url = `${API_BASE}/v1/datanet`;',
      '  const u2 = await apiFetchData<RulesResponse>(`${API_BASE}/v1/knowledge/rules?groupBy=regulation`);',
      '  /** 注释里的 /api/v1/legacy 端点说明不计 */',
    ].join('\n');
    expect(derivedValues(DEFECT).filter((v) => DOUBLE_PREFIX_RE.test(v)).length).toBe(1);
    expect(codeLines(DEFECT).filter((l) => DOUBLE_PREFIX_RE.test(l)).length).toBe(0);
    expect(codeLines(DEFECT).filter((l) => NO_SLASH_AFTER_BASE.test(l)).length).toBe(1);
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(1);
    // 两类派生缺陷的还原面值各自畸形（双前缀 / 少斜杠粘连），一比即红
    expect(derivedValues(DEFECT)).toEqual(['/api/api/v1/datanet', '/apiv1/ecos/versions']);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(codeLines(CLEAN).filter((l) => NO_SLASH_AFTER_BASE.test(l))).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/datanet', '/api/v1/knowledge/rules?groupBy=regulation']);
  });
});

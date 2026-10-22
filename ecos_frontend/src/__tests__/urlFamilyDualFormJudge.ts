/**
 * urlFamily 双形态判据单源模块 (#142/B4-3e-47 从 `urlFamilyDualFormRatchet.test.ts` 拆出)。
 *
 * <p>拆出动因：主门文件顶在 `file-size.test.ts` 的 800 行上限（全 src 口径，含 `__tests__`）上，
 * 本批往里加一条「判据能变红」腿即成新违规。判据与台账注释同文件时再加一条腿只有两种坏选择——
 * 压缩历史注释（丢证据）或复制正则（造第二份判据）。本模块把判据本体单独成文件，主门与
 * `schemaPreviewContract`/`dataWorkbenchApiBaseSingleSource` 等后续腿都从这里取同一把尺子。</p>
 *
 * <p>**本模块不含断言**：所有红/绿判定仍写在门文件里；这里只保证「族键怎么算、形态怎么数」一处定义。
 * 判据语义（双形态口径严格强于裸形态口径、名字变体腿、注释行不计）的理由与实测证据保留在主门文件头。</p>
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
export const CANONICAL = 'services/httpClient.ts';

export type Forms = { raw: number; der: number };
export type Index = Map<string, Map<string, Forms>>;

/** 族键 = 去掉基址前缀并以 `{}` 归一插值后的路径串（含 query 骨架）。 */
export const normalize = (body: string): string => {
  let s = body;
  for (let i = 0; i < 5 && /\$\{[^{}]*\}/.test(s); i += 1) s = s.replace(/\$\{[^{}]*\}/g, '{}');
  return s.replace(/\$\{[^{}]*\}/g, '{}');
};

export const RAW_RE = /["'`]\/api((?:\\.|(?!["'`])[^\\])*)["'`]/g;
export const DER_BODY = '((?:\\\\.|(?!`)[^\\\\])*)';
export const DER_RE = new RegExp('`\\$\\{API_BASE\\}' + DER_BODY + '`', 'g');

/**
 * #145/B6-11 补的名字变体腿：派生态判据原本把 token 名**写死**成 `API_BASE`，于是
 * `import { API_BASE as X }` 之后写 `` `${X}/v1/…` `` 的族根构造点对本门完全隐形——
 * 这是 D-B5-40（"判据对派生态不可见"）在**名字轴**上的同形缺陷：形态是派生的、只是换了个名字。
 * 实测 prod 面改前仅一枚（`pages/Guardrails/constants.ts` 的 `API_BASE as API_ROOT`，它自持的常量
 * 恰好也叫 `API_BASE`、取值却是 `/api/v1/guardrails/policies`，本批随族单源一起收口）。
 * 这里按文件把 `API_BASE as X` 的 X 并进取 token 集，别名腿与本体腿同口径计入。
 */
export const ALIAS_IMPORT_RE = /(?:^|[,{\s])API_BASE\s+as\s+([A-Za-z0-9_]+)/g;

export function derivedRe(code: string): RegExp {
  const aliases = [...code.matchAll(ALIAS_IMPORT_RE)].map((m) => m[1]);
  if (aliases.length === 0) return DER_RE;
  return new RegExp('`\\$\\{(?:API_BASE|' + aliases.join('|') + ')\\}' + DER_BODY + '`', 'g');
}

export function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

export function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

/** 纯函数：从一段源码文本读出 {family → 形态计数}，供树级扫描与 fixture 双向复用。 */
export function familiesOf(code: string): Map<string, Forms> {
  const out = new Map<string, Forms>();
  const bump = (f: string, kind: keyof Forms) => {
    const cur = out.get(f) ?? { raw: 0, der: 0 };
    cur[kind] += 1;
    out.set(f, cur);
  };
  const lines = code.split('\n').filter((l) => !isComment(l)).join('\n');
  for (const m of lines.matchAll(RAW_RE)) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'raw');
  }
  for (const m of lines.matchAll(derivedRe(lines))) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'der');
  }
  return out;
}

export function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

export function scan(srcRoot: string): { idx: Index; rawSites: number; derSites: number; prodFiles: number } {
  const files: string[] = [];
  walk(srcRoot, files);
  const idx: Index = new Map();
  let rawSites = 0;
  let derSites = 0;
  let prodFiles = 0;
  for (const f of files) {
    const rel = relative(srcRoot, f).split(sep).join('/');
    if (rel === CANONICAL || isTestFile(rel)) continue;
    prodFiles += 1;
    for (const [fam, forms] of familiesOf(readFileSync(f, 'utf8'))) {
      if (!idx.has(fam)) idx.set(fam, new Map());
      const owners = idx.get(fam)!;
      const cur = owners.get(rel) ?? { raw: 0, der: 0 };
      cur.raw += forms.raw;
      cur.der += forms.der;
      owners.set(rel, cur);
      rawSites += forms.raw;
      derSites += forms.der;
    }
  }
  return { idx, rawSites, derSites, prodFiles };
}

export function dupByBothForms(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) if (owners.size > 1) out.set(fam, [...owners.keys()].sort());
  return out;
}

/** 旧判据口径（只认裸字面量）在同一索引上能看到的重复族 —— 用于量化盲区。 */
export function dupByRawOnly(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) {
    const rawOwners = [...owners.entries()].filter(([, f]) => f.raw > 0).map(([o]) => o).sort();
    if (rawOwners.length > 1) out.set(fam, rawOwners);
  }
  return out;
}

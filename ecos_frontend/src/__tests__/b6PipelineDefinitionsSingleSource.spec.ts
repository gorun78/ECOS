// @vitest-environment node
/**
 * `/v1/pipeline/definitions` 族单源守恒门（#145/B6-5，差距台账 §13.2.4 B6 腿）。
 *
 * <p>本批把棘轮基线里最后一个三属主族收口为 `api/dataClient.ts` 导出的
 * `PIPELINE_DEFINITIONS_BASE` 单源（属主 3 → 1，dupBoth 22 → 21）。属主落 api 层由 import 图裁决：
 * `pages/aiworkbench/api.ts` 本就经 `src/api.ts` 门面吃 dataClient，把常量反向放页面层会让 api 层
 * 倒过来依赖页面；`src/api.ts` 只是 `export * from './api/dataClient'` 的再导出门面，本身不含 URL 体，
 * 按 #145/B6-4 口径**不算属主**，所以消费者一律走显式属主路径，门面不参与来源裁决。</p>
 *
 * <p>本族形状与 B6-1~B6-4 有一处新东西：`pages/data-workbench/api/_base.ts` 的
 * `PIPELINE_DEFS` 是该域对外导出面（`index.ts` 有 `export * from './_base'`，且 #143/B5-8 在文件头
 * 登记了「URL 常量逐字不变」兼容红线），删掉会动域对外 API 面。本批把它改成
 * `export { PIPELINE_DEFINITIONS_BASE as PIPELINE_DEFS } from '<属主>'` 的**别名重导出**——
 * 该行不含 URL 体 ⇒ 不是第二个属主，但它是「域内旧名 → 单源」的唯一绑定证据，
 * 故本门把它单独钉一条腿（恰 1 行、且绑定关系逐字），别名一旦改指他处即红。</p>
 *
 * <p>由此产生两条新的判据形状，都在「判据能变红」腿里各配反向用例（沿用 B6-4 已登记的
 * 「声明行不算腿 / 裸引用算腿」口径）：⑧ **声明行自身的名字 token 不是腿**——
 * 属主文件里 `const PIPELINE_DEFINITIONS_BASE = …` 这行既匹配模板腿又含同名 token，
 * 若不排除会双计同一面值；⑨ **别名重导出行的两个名字 token 都不是腿**——
 * `export { A as B } from` 里 A、B 同现，按裸名计数会凭空造出两条腿。
 * 另沿用⑦：剥掉整条 import 语句后的行集不能用来断言导入腿（恒见 0），导入面另取「只去注释」的行集。</p>
 *
 * <p>守恒判据是**面值多重集**：派生只有一种正确性可证 = 运行期拼出来的字符串逐字不变。
 * 双模读出（HEAD 15 条 / 工作树 14 条）显示差额恰为 `_base.ts` 那枚被收口的属主声明，
 * 其余 14 条消费者腿面值一条未变（含 `${id}` 与 `${encodeURIComponent(id)}` 两种插值写法共存——
 * 本域历史上就没统一，本门按字面钉住、不归一，归一会把「顺手改了转义」读成绿）。
 * 减法按**文件整条扣**（扣 `_base.ts` 那一项），按面值筛会把同值的 6 条一起吃掉＝凭空少 5 条。</p>
 *
 * <p>与棘轮的分工：收口后 `` `${PIPELINE_DEFINITIONS_BASE}/…` `` 与裸引用对棘轮 RAW_RE/DER_RE 隐形
 * （族体不再出现在源码串里），dupBoth 下降即本门存在的理由——只靠棘轮会把「删掉声明」记成进度、
 * 把「请求面被吃掉」漏掉。子路径族 `${id}`、`${id}/execute`、`?pageSize=` 的属主本来就只有一个，
 * 按 B6-1 口径不并进常量。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { PIPELINE_DEFINITIONS_BASE } from '../api/dataClient';

// 本门位于 `src/__tests__/`，到 `src/` 的相对深度是一档
const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));

const FAMILY = '/api/v1/pipeline/definitions';
const OWNER = 'api/dataClient.ts';
const ALIAS_FILE = 'pages/data-workbench/api/_base.ts';
const CONSUMER = 'pages/data-workbench/api/pipeline.ts';
const AI_FILE = 'pages/aiworkbench/api.ts';
const OWNER_NAME = 'PIPELINE_DEFINITIONS_BASE';
const ALIAS_NAME = 'PIPELINE_DEFS';
const ID_REF = '${id}';

/** 本族四个生产文件。 */
const FILES: readonly string[] = [OWNER, ALIAS_FILE, CONSUMER, AI_FILE];

/** 工作树每文件应还原出的族腿条数（实跑读出）：属主 7 + 门面 0 + 域消费者 6 + AI 台 1 = 14。 */
const WT_SITES_PER_FILE: Readonly<Record<string, number>> = {
  [OWNER]: 7,
  [ALIAS_FILE]: 0,
  [CONSUMER]: 6,
  [AI_FILE]: 1,
};

const HARDCODED_RE = /["'`]\/api\b/;
/** 别名重导出行（规则 ⑨：两个名字 token 都不是腿，但它是旧名→单源的唯一绑定证据）。 */
const ALIAS_LINE_RE = new RegExp(
  String.raw`^\s*export\s*\{\s*PIPELINE_DEFINITIONS_BASE\s+as\s+PIPELINE_DEFS\s*\}\s*from\s*'[^']*api/dataClient'\s*;`,
);
const OWNER_DECL_RE = /^\s*(?:export\s+)?const\s+PIPELINE_DEFINITIONS_BASE\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/** 只去注释（导入面／重导出面判据用，规则 ⑦）。 */
function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
}

/** 剥掉整条 import 语句（含多行续行）后再按行判腿，防续行被吃成假腿。 */
function stripImports(src: string): string[] {
  return src.replace(/^\s*import\s[\s\S]*?;/gm, '').split(/\r?\n/).filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, ...rel.split('/')), 'utf8');
}

/** 常量表：属主名 + 域内别名都解析到同一面值（别名合法性由「别名腿」单独承证）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  [OWNER_NAME, PIPELINE_DEFINITIONS_BASE],
  [ALIAS_NAME, PIPELINE_DEFINITIONS_BASE],
]);

const SITE_RE = new RegExp(
  '`\\$\\{(' + [...CONST_VALUES.keys()].sort((a, b) => b.length - a.length).join('|') + ')' +
    '\\}((?:\\\\.|(?!`)[^\\\\])*)`',
  'g',
);

/**
 * 还原**整文件**的派生态面值（不限于本族）——四个文件都是多族宿主，只有按整面判才抓得到双前缀。
 * 规则：模板腿按常量表求值并从行文本摘掉 ⇒ 剩下同一行的裸名才是另一条腿；
 * 声明行（⑧）与别名重导出行（⑨）的名字 token 不计；import 语句已在 `stripImports` 阶段整条剥掉。
 */
function resolvedFaces(src: string): string[] {
  const out: string[] = [];
  for (const line of stripImports(src)) {
    if (ALIAS_LINE_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      const base = CONST_VALUES.get(m[1]);
      if (base === undefined) continue;
      out.push(base + m[2]);
      rest = rest.split(m[0]).join('');
    }
    if (OWNER_DECL_RE.test(line)) continue;
    for (const name of [OWNER_NAME, ALIAS_NAME]) {
      const cnt = rest.split(name).length - 1;
      for (let i = 0; i < cnt; i += 1) out.push(CONST_VALUES.get(name)!);
    }
  }
  return out;
}

/** 本族面 = 整面里以族基址起头的那批（族键前缀唯一，`/v1/pipeline/definitions` 无歧义族根）。 */
function familyFaces(src: string): string[] {
  return resolvedFaces(src).filter((v) => v.startsWith(FAMILY));
}

/**
 * 改前快照（HEAD 实跑读出，15 条，按文件分布 7／1／6／1）：
 * dataClient 声明 1 + 裸引用 1 + `${id}` 3 + `${id}/execute` 1 + `?pageSize=` 1；
 * _base 声明 1；pipeline 裸引用 2 + `${encodeURIComponent(id)}` 1 + `${id}` 2 + `${id}/execute` 1；
 * aiworkbench 内联派生 1。`${…}` 一律按字面保留（求值会把转义差异洗掉）。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = {
  [OWNER]: [
    FAMILY,
    FAMILY,
    `${FAMILY}/${ID_REF}`,
    `${FAMILY}/${ID_REF}`,
    `${FAMILY}/${ID_REF}`,
    `${FAMILY}/${ID_REF}/execute`,
    `${FAMILY}?pageSize=$\{pageSize}`,
  ],
  [ALIAS_FILE]: [FAMILY],
  [CONSUMER]: [
    FAMILY,
    FAMILY,
    `${FAMILY}/$\{encodeURIComponent(id)}`,
    `${FAMILY}/${ID_REF}`,
    `${FAMILY}/${ID_REF}`,
    `${FAMILY}/${ID_REF}/execute`,
  ],
  [AI_FILE]: [FAMILY],
};

const HEAD_TOTAL = 15;
/** 改前族基址裸值腿数（含 3 枚属主声明位）——实跑读出，防快照被手抄改形。 */
const HEAD_BASE_LEGS = 6;

describe('pipeline 定义族基址单源 — 面值守恒 (#145/B6-5)', () => {
  it('前提：API_BASE = "/api" 且属主常量面值 = 改前族基址（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(PIPELINE_DEFINITIONS_BASE).toBe(FAMILY);
  });

  it('属主唯一：四文件代码行里含族体的只有属主声明一行（注释里的端点说明不计）', () => {
    const hits: string[] = [];
    for (const rel of FILES) {
      for (const line of codeLines(read(rel))) {
        if (line.includes('v1/pipeline/definitions')) hits.push(`${rel} — ${line.trim()}`);
      }
    }
    expect(hits, `族体字面量散点未收口：\n${hits.join('\n')}`).toEqual([
      `${OWNER} — export const PIPELINE_DEFINITIONS_BASE = \`\${API_BASE}/v1/pipeline/definitions\`;`,
    ]);
  });

  it('四文件代码行零私设基址（注释里的 /api 端点说明不计）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('面值逐字不变：工作树 14 条 = 改前 15 条按文件整条扣掉 _base 那 1 项，且逐文件条数吻合', () => {
    const wtAll: string[] = [];
    for (const rel of FILES) {
      const vals = familyFaces(read(rel));
      expect(vals.length, `${rel} 族腿条数不符`).toBe(WT_SITES_PER_FILE[rel]);
      wtAll.push(...vals);
    }
    const expectWt: string[] = [];
    for (const rel of FILES) if (rel !== ALIAS_FILE) expectWt.push(...HEAD_FACES[rel]);
    expect(Object.values(HEAD_FACES).reduce((a, v) => a + v.length, 0)).toBe(HEAD_TOTAL);
    expect(HEAD_FACES[ALIAS_FILE]).toEqual([FAMILY]);
    expect(expectWt.length).toBe(14);
    expect(wtAll.length).toBe(14);
    expect([...wtAll].sort()).toEqual([...expectWt].sort());
    // 剩余面里族基址裸值恰 5 条（属主声明 1 + 属主裸引用 1 + pipeline 裸引用 2 + AI 台裸引用 1）
    expect(wtAll.filter((v) => v === FAMILY).length).toBe(5);
  });

  it('别名腿：_base.ts 恰一条「旧名 → 属主」重导出行、不含 URL 体，域内消费者仍只经旧名拿值', () => {
    const aliasLines = codeLines(read(ALIAS_FILE)).filter((l) => ALIAS_LINE_RE.test(l));
    expect(aliasLines.length, '别名重导出行必须恰 1 条').toBe(1);
    expect(OWNER_DECL_RE.test(aliasLines[0]), '重导出行不是声明行 ⇒ 不算属主').toBe(false);
    expect(
      codeLines(read(ALIAS_FILE)).filter((l) => /^\s*(?:export\s+)?const\s+PIPELINE_DEFS\b/.test(l)).length,
      '旧名不得再有本地声明',
    ).toBe(0);
    const consumerLegs = stripImports(read(CONSUMER));
    expect(consumerLegs.filter((l) => l.includes(ALIAS_NAME)).length).toBe(6);
    expect(consumerLegs.filter((l) => l.includes(OWNER_NAME)).length, '域内不得绕过门面直取属主').toBe(0);
    expect(
      codeLines(read(CONSUMER)).filter((l) => l.trimStart().startsWith('import ') && l.includes(ALIAS_NAME)).length,
    ).toBe(1);
  });

  it('来源唯一：属主声明恰 1 行、AI 台恰 1 条显式属主导入腿、src/api.ts 门面不含族体', () => {
    expect(codeLines(read(OWNER)).filter((l) => OWNER_DECL_RE.test(l)).length).toBe(1);
    const aiImports = codeLines(read(AI_FILE)).filter(
      (l) => l.trimStart().startsWith('import ') && l.includes(OWNER_NAME),
    );
    expect(aiImports.length, 'AI 台只能有一条属主导入腿').toBe(1);
    expect(aiImports[0]).toMatch(/from '\.\.\/\.\.\/api\/dataClient'/);
    const shim = codeLines(read('api.ts'));
    expect(shim.some((l) => l.includes('v1/pipeline/definitions')), '门面重复声明族体即第二个属主').toBe(false);
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /，且四文件整面还原值零 /api/api 双前缀', () => {
    const bad: string[] = [];
    for (const rel of FILES) {
      for (const line of codeLines(read(rel))) {
        if (/`\$\{API_BASE\}(?!\/)/.test(line)) bad.push(`${rel}: ${line.trim()}`);
      }
      for (const v of resolvedFaces(read(rel))) {
        if (/\/api\/api\b/.test(v)) bad.push(`${rel}: 还原值 ${v}`);
      }
    }
    expect(bad, `派生态畸形：\n${bad.join('\n')}`).toEqual([]);
  });

  it('判据能变红：声明行不双计(⑧)、别名行不造腿(⑨)、裸引用与模板腿各计一条、双前缀即红（防恒绿）', () => {
    // ⑧ 属主声明行：模板腿 1 条，名字 token 不再计第 2 条
    expect(resolvedFaces(`export const ${OWNER_NAME} = \`\${API_BASE}/v1/pipeline/definitions\`;`)).toEqual([FAMILY]);
    // 同一行「模板腿 + 裸名」= 两条腿（模板被摘掉后剩下的裸名才是独立请求点）
    expect(resolvedFaces(`  const u = [\`\${${OWNER_NAME}}/x\`, ${OWNER_NAME}];`)).toEqual([`${FAMILY}/x`, FAMILY]);
    // ⑨ 别名重导出行：两个名字 token 都不是腿
    expect(resolvedFaces(`export { ${OWNER_NAME} as ${ALIAS_NAME} } from '../../../api/dataClient';`)).toEqual([]);
    // 裸名解析到同一面值；别名与属主同权
    expect(resolvedFaces(`  const r = await get<unknown[]>(${ALIAS_NAME});`)).toEqual([FAMILY]);
    // 未知常量不吃（防把任意 `${x}` 吃成 URL）
    expect(resolvedFaces('  const q = `${SOME_UNKNOWN_BASE}/v1/pipeline/definitions`;')).toEqual([]);
    // 双前缀畸形（B5-26 实证过的自伤形状）只在**还原后的面值**上现形，源码串面看不见
    const DOUBLE = '  const p = `${API_BASE}/api/v1/pipeline/definitions`;';
    expect(HARDCODED_RE.test(DOUBLE), '源码串里并无 /api/api 字面量').toBe(false);
    expect(resolvedFaces(DOUBLE)).toEqual(['/api/api/v1/pipeline/definitions']);
    // 私设基址形态必须被清零腿判出
    expect(codeLines('  await fetch("/api/v1/pipeline/definitions");').filter((l) => HARDCODED_RE.test(l)).length).toBe(1);
  });

  it('快照自身可校验：15 条按 7／1／6／1 分布、族基址裸值 6 条且全部以族基址开头（防快照清空成恒真）', () => {
    expect(
      Object.entries(HEAD_FACES)
        .map(([k, v]) => `${k}:${v.length}`)
        .sort(),
    ).toEqual([
      'api/dataClient.ts:7',
      'pages/aiworkbench/api.ts:1',
      'pages/data-workbench/api/_base.ts:1',
      'pages/data-workbench/api/pipeline.ts:6',
    ]);
    const all = Object.values(HEAD_FACES).flat();
    expect(all.length).toBe(HEAD_TOTAL);
    for (const v of all) expect(v.startsWith(FAMILY)).toBe(true);
    expect(all.filter((v) => v === FAMILY).length).toBe(HEAD_BASE_LEGS);
    expect(Object.values(WT_SITES_PER_FILE).reduce((a, b) => a + b, 0)).toBe(14);
  });
});

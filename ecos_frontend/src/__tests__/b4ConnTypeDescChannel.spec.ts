// @vitest-environment node
/**
 * b4ConnTypeDescChannel.spec.ts — 数据源类型「说明文案面」单源取证（#142/B4-3e-65，收口 D-B4-94）
 *
 * <p><b>本门盯的病灶</b>：`helpers.ts#getSourceTypeLabel` 自带一份 5 枚私有 switch 表
 * （postgresql／s3／rest_api／sftp／sap），其余 <b>14 枚已知类型</b>一律落 `dw.src.unknown`。
 * 直接后果是同一块连接详情横幅里两条互相矛盾：上一行徽章走 19 枚单源（出「MySQL」），
 * 下一行副标题走 5 枚私表（出「未知数据源」）——用户看到的是「系统说它不知道一个它知道的类型」。
 * 这与上一批收口的 D-B4-93（词表外名一律显示成 PostgreSQL）是同族反向折叠：
 * 一个把未知折成已知，一个把已知折成未知。</p>
 *
 * <p><b>修法</b>：说明文案改由显示元数据面 `CONNECTION_TYPES.descKey` 承载，与名称面
 * `i18nKey`、分层面 `tier` 同一份表；未解析（`'unknown'` 或运行时值无归属）显式出
 * 「未知数据源（原值 X）」，禁借用任何一枚已知类型的说明。</p>
 *
 * <p><b>判据失明防护</b>：面值腿按行解析 {@code CONNECTION_TYPES} 的元素对象，
 * 逐元素要求同时带 value 与 descKey（只数两侧总数会被「错位配对」糊过去）；
 * 独立钉值 19 枚（与后端 gateway 跨面门同源同值，两门任一枚失明即红）。
 * 真实仓面违例集须为空，且违例集在夹具上必须非空——两条一起堵「判据恒真」。</p>
 *
 * <p><b>能变红的证明</b>：夹具四段式（去 descKey／空串／前缀跑到名称面／逐枚指向未解析键）
 * 各自具名违例；行为腿用真实双语语料反证「MySQL 拿到的是 MySQL 的说明，不是 PostgreSQL 的说明」。
 * @license Apache-2.0</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { CONNECTION_TYPES } from '../pages/data-workbench/types';
import { connTypeDescText } from '../pages/data-workbench/helpers';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/
const TYPES = path.join(SRC, 'pages/data-workbench/types.ts');
const HELPERS = path.join(SRC, 'pages/data-workbench/helpers.ts');
const BANNER = path.join(SRC, 'pages/data-workbench/tabs/connections/ConnectionDetailBanner.tsx');
const DW_ZH = path.join(SRC, 'locales/dw/zh-CN.json');
const DW_EN = path.join(SRC, 'locales/dw/en.json');

const read = (p: string) => readFileSync(p, 'utf8');
const corpus = (p: string) => JSON.parse(read(p)) as Record<string, string>;

/**
 * 剥注释后得到代码面（仓里既有教训：判据直读原文会把文档里点名的病灶名当成残留，
 * 见台账 D-B4-40「门禁剥注释盲区」）。只剥块注释与整行 `//`，
 * 不剥行内 `//`——本域文件里的字符串字面量不含 `//`，而正则会误伤 URL 形态。
 */
function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

/** 独立钉值：收口前实测显示元数据面 19 枚（与 gateway 跨面门 FRONTEND_TYPES_FROZEN 同源同值）。 */
const FROZEN_COUNT = 19;
/** 独立钉值：未解析读数键——它不是任何一枚类型的说明，逐枚指向它即折叠病灶。 */
const UNRESOLVED_KEY = 'dw.src.unknown';

/** 定位 `CONNECTION_TYPES` 数组块（声明改名＝本门失明，由调用方断言）。 */
function connectionTypesBlock(src: string): string {
  const start = src.indexOf('export const CONNECTION_TYPES');
  expect(start, 'types.ts 里找不到 CONNECTION_TYPES 声明（显示面改名＝本门失明）').toBeGreaterThanOrEqual(0);
  const end = src.indexOf('];', start);
  expect(end, 'CONNECTION_TYPES 数组块未闭合').toBeGreaterThan(start);
  return src.slice(start, end);
}

interface FaceRow { value: string; i18nKey: string; descKey: string | null; }

/** 逐行解析元素对象：每枚必须自带 value／i18nKey／descKey 三键。 */
function parseFaceRows(block: string): FaceRow[] {
  const rows: FaceRow[] = [];
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line.startsWith('{')) {
      continue;
    }
    const value = /value:\s*'([A-Za-z0-9_]+)'/.exec(line);
    if (!value) {
      continue;
    }
    const i18nKey = /i18nKey:\s*'([^']*)'/.exec(line);
    const descKey = /descKey:\s*'([^']*)'/.exec(line);
    rows.push({
      value: value[1],
      i18nKey: i18nKey ? i18nKey[1] : '',
      descKey: descKey ? descKey[1] : null,
    });
  }
  return rows;
}

/**
 * 说明面违例判据（真实仓面须 0 违例，夹具须具名违例）。
 * 每条违例带类型名，红了能直接定位是哪枚类型、缺哪一腿。
 */
function descFaceViolations(rows: FaceRow[]): string[] {
  const out: string[] = [];
  if (rows.length !== FROZEN_COUNT) {
    out.push(`显示面枚数 ${rows.length} ≠ 独立钉值 ${FROZEN_COUNT}（面被裁剪或解析器失明）`);
  }
  const seenDesc = new Map<string, string>();
  for (const row of rows) {
    if (row.descKey === null) {
      out.push(`${row.value}: 元素里没有 descKey（说明通道回到私表或未接线）`);
      continue;
    }
    if (row.descKey.length === 0) {
      out.push(`${row.value}: descKey 为空串（界面会裸出键名或空文案）`);
    } else if (row.descKey === UNRESOLVED_KEY) {
      out.push(`${row.value}: descKey 指向未解析键（已知类型被折成「未知数据源」＝D-B4-94 病灶）`);
    } else if (!row.descKey.startsWith('dw.src.')) {
      out.push(`${row.value}: descKey 前缀非 dw.src.（实得 ${row.descKey}，与名称面键面分裂）`);
    }
    if (row.descKey === row.i18nKey) {
      out.push(`${row.value}: descKey 与 i18nKey 同键（说明面被名称面冒充）`);
    }
    if (row.descKey) {
      const prior = seenDesc.get(row.descKey);
      if (prior) {
        out.push(`${row.value}: descKey 与 ${prior} 共用 ${row.descKey}（两枚类型同一条说明）`);
      } else {
        seenDesc.set(row.descKey, row.value);
      }
    }
  }
  return out;
}

describe('b4ConnTypeDescChannel: 类型说明文案面归一 CONNECTION_TYPES 单源 (#142/B4-3e-65)', () => {
  it('① helpers.ts 的 5 枚私有说明表已删除，全仓零残留', () => {
    const helpers = stripComments(read(HELPERS));
    expect(helpers, 'getSourceTypeLabel 仍在＝第三份类型→文案表活着').not.toMatch(/getSourceTypeLabel/);
    // 私有表形态防回潮：helpers.ts 内不得再出现 case 'xxx': return t('dw.src.*') 的映射腿
    expect(helpers, 'helpers.ts 里再现 dw.src.* 的 case 映射＝私表回潮').not.toMatch(/case\s+'[a-z0-9_]+'\s*:\s*return\s+t\('dw\.src\./);
    const banner = stripComments(read(BANNER));
    expect(banner).not.toMatch(/\bgetSourceTypeLabel\b/);
    // 全仓 import 面扫描（注释里的同名不算）
    const offenders = sourceFilesWithPrivateLabelImport();
    expect(offenders, `仍有文件 import 被删除的私表通道：${offenders.join(', ')}`).toEqual([]);
  });

  it('② 显示面 19 枚逐枚自带非空 descKey，键面与名称面互不冒充', () => {
    const rows = parseFaceRows(connectionTypesBlock(read(TYPES)));
    expect(descFaceViolations(rows), `说明面违例：\n${descFaceViolations(rows).join('\n')}`).toEqual([]);
    const imported = CONNECTION_TYPES.map((o) => o.descKey);
    expect(imported.length, 'import 面枚数与文本解析面不一致').toBe(FROZEN_COUNT);
    expect(new Set(imported).size, 'import 面 descKey 存在重复').toBe(FROZEN_COUNT);
  });

  it('③ 逐枚 descKey 双语语料在位非空，未解析键不混进说明面', () => {
    const rows = parseFaceRows(connectionTypesBlock(read(TYPES)));
    const zh = corpus(DW_ZH);
    const en = corpus(DW_EN);
    expect(rows.length).toBe(FROZEN_COUNT);
    for (const row of rows) {
      const key = row.descKey as string;
      expect(typeof zh[key], `zh-CN 缺 ${key}（${row.value}）⇒ 副标题裸出键名`).toBe('string');
      expect((zh[key] ?? '').length, `zh-CN ${key} 空串`).toBeGreaterThan(0);
      expect(typeof en[key], `en 缺 ${key}（${row.value}）`).toBe('string');
      expect((en[key] ?? '').length, `en ${key} 空串`).toBeGreaterThan(0);
      // 名称面与说明面的文案不得同字（同字＝说明通道没有独立语义）
      expect(zh[key], `${key} 与名称面 ${row.i18nKey} 同字＝说明面被名称面冒充`)
        .not.toBe(zh[row.i18nKey]);
    }
    expect(zh[UNRESOLVED_KEY], '未解析文案键缺失').toBeTruthy();
    expect(en[UNRESOLVED_KEY], '未解析文案键缺失').toBeTruthy();
    expect(rows.map((r) => r.descKey), '未解析键混进了说明面').not.toContain(UNRESOLVED_KEY);
  });

  it('④ 行为腿：已知类型出各自说明，未解析显式出「未知数据源（原值 X）」，禁借用他族说明', () => {
    const zh = corpus(DW_ZH);
    const en = corpus(DW_EN);
    for (const [lang, corpusMap] of [['zh', zh], ['en', en]] as const) {
      const t = (k: string) => corpusMap[k] ?? k;
      for (const o of CONNECTION_TYPES) {
        expect(connTypeDescText({ type: o.value, typeRaw: o.value.toUpperCase() }, t),
          `${lang} 面 ${o.value} 的说明文案未取到 descKey`).toBe(corpusMap[o.descKey]);
      }
      // 反向对照（D-B4-94 的用户面读数）：改前 mysql 落 default→「未知数据源」，现须拿到自身说明
      expect(connTypeDescText({ type: 'mysql', typeRaw: 'MYSQL' }, t))
        .not.toBe(corpusMap[UNRESOLVED_KEY]);
      expect(connTypeDescText({ type: 'clickhouse', typeRaw: 'CLICKHOUSE' }, t))
        .not.toBe(corpusMap['dw.src.postgresql']);
      // 未解析两形态：unknown 读数带原值；运行时值在显示面无归属同样点名，禁静默
      expect(connTypeDescText({ type: 'unknown', typeRaw: 'TIMESCALE_NEW' }, t))
        .toBe(`${corpusMap[UNRESOLVED_KEY]} (TIMESCALE_NEW)`);
      expect(connTypeDescText({ type: 'unknown', typeRaw: '' }, t)).toBe(corpusMap[UNRESOLVED_KEY]);
      expect(connTypeDescText({ type: 'doris_gone' as never, typeRaw: 'DORIS_GONE' }, t))
        .toBe(`${corpusMap[UNRESOLVED_KEY]} (doris_gone)`);
    }
  });

  it('⑤ 横幅两条通道同源：徽章出名称、副标题出说明，均读 CONNECTION_TYPES', () => {
    const banner = read(BANNER);
    const imports = banner.split(/\r?\n/).filter((l) => /^\s*import[\s{]/.test(l)).join('\n');
    expect(imports, '横幅须同时 import 两条单源通道').toMatch(/\bconnTypeBadgeText\b/);
    expect(imports, '横幅须同时 import 两条单源通道').toMatch(/\bconnTypeDescText\b/);
    expect(banner, '副标题未走单源说明通道＝第三份表回潮').toMatch(/connTypeDescText\(conn, t\)/);
    expect(banner).toMatch(/connTypeBadgeText\(conn, t\)/);
    // 两条通道读同一份表（helpers.ts 内都只从 CONNECTION_TYPES 取）
    const helpers = read(HELPERS);
    const descBody = helpers.slice(helpers.indexOf('export const connTypeDescText'));
    expect(descBody.slice(0, descBody.indexOf('\n};')), '说明通道未读单源表').toMatch(/CONNECTION_TYPES\.find/);
  });

  it('⑥ 夹具四段式：判据对「去键／空串／指向未解析键／共用同键」逐段具名变红', () => {
    const base = parseFaceRows(connectionTypesBlock(read(TYPES)));
    expect(base.length).toBe(FROZEN_COUNT);
    const of = (value: string) => base.find((r) => r.value === value) as FaceRow;

    const missing = descFaceViolations(base.map((r) => (r.value === 'mysql' ? { ...r, descKey: null } : r)));
    expect(missing.join('\n')).toMatch(/mysql: 元素里没有 descKey/);

    const blank = descFaceViolations(base.map((r) => (r.value === 'dm' ? { ...r, descKey: '' } : r)));
    expect(blank.join('\n')).toMatch(/dm: descKey 为空串/);

    const folded = descFaceViolations(base.map((r) => (r.value === 'kafka' ? { ...r, descKey: UNRESOLVED_KEY } : r)));
    expect(folded.join('\n')).toMatch(/kafka: descKey 指向未解析键（已知类型被折成「未知数据源」＝D-B4-94 病灶）/);

    const shared = descFaceViolations(base.map((r) => (r.value === 'oss' ? { ...r, descKey: of('s3').descKey } : r)));
    expect(shared.join('\n')).toMatch(/oss: descKey 与 s3 共用/);

    const wrongPrefix = descFaceViolations(base.map((r) => (r.value === 'fs' ? { ...r, descKey: 'dw.connType.fs' } : r)));
    expect(wrongPrefix.join('\n')).toMatch(/fs: descKey 前缀非 dw\.src\./);

    // 失明防护：解析到 0 枚（面改名/块截断）必须红，禁「空集＝绿」
    expect(descFaceViolations([]).join('\n')).toMatch(/显示面枚数 0 ≠ 独立钉值 19/);
    // 正向对照：真实仓面 0 违例（堵住「判据恒真」的反向假绿）
    expect(descFaceViolations(base)).toEqual([]);
    expect(of('rest_api').descKey, 'rest_api 说明键名漂移').toBe('dw.src.rest_api');
  });
});

/** 全仓 .ts/.tsx 里 import 具名 `getSourceTypeLabel` 的文件（注释不计）。 */
function sourceFilesWithPrivateLabelImport(): string[] {
  const hits: string[] = [];
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules') {
          walk(full);
        }
        continue;
      }
      if (!/\.tsx?$/.test(entry.name)) {
        continue;
      }
      const importText = stripComments(read(full)).split(/\r?\n/)
        .filter((l) => /^\s*import[\s{]/.test(l))
        .join('\n');
      if (/\bgetSourceTypeLabel\b/.test(importText)) {
        hits.push(path.relative(SRC, full));
      }
    }
  };
  walk(SRC);
  return hits;
}

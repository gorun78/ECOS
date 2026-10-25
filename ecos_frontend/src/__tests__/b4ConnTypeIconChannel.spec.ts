// @vitest-environment node
/**
 * b4ConnTypeIconChannel.spec.ts — 数据源类型「图标面」单源取证（#142/B4-3e-66，收口 D-B4-95）
 *
 * <p><b>本门盯的病灶</b>：`helpers.ts#getSourceIcon` 自带一份 <b>5 枚</b>私有 switch 表
 * （postgresql→Database／s3→Cloud／rest_api→Globe／sftp→FolderGit／sap→Cpu），
 * `default` 一律落 `'HardDrive'`。显示元数据面有 19 枚已知类型，其余 <b>14 枚</b>
 * （MySQL／Oracle／MSSQL／DM／KingBase／GaussDB／OSS／MinIO／CSV／Kafka／MongoDB／
 * Doris／ClickHouse／…）在连接详情横幅上共用同一枚「本地磁盘」图标——
 * 与上一批收口的 D-B4-94（说明面把 14 枚已知类型折成「未知数据源」）同族，
 * 是该族的<b>第四处显示通道</b>：把已知折成<b>同一个默认读数</b>。
 * 更糟的是被借走的那枚图标本身有主（`fs`＝本地文件系统），用户看到 MySQL 顶着磁盘＝
 * 显示面主动给出错误归属。</p>
 *
 * <p><b>修法</b>：图标名并入同一份显示元数据面 `CONNECTION_TYPES.iconName`，与名称面
 * `i18nKey`、说明面 `descKey`、分层面 `tier` 同源；19 枚逐枚配图且<b>两两不重复</b>，
 * `HardDrive` 归还给 `fs`。未解析读数改用显式的 `CircleQuestionMark`，由 `helpers.ts`
 * 单点持有、不入本表（与 `'unknown'` 不入选项面同构）。</p>
 *
 * <p><b>桥接面是本域特有的一层</b>：图标名是字符串，须经 `LucideIcon.tsx` 的 `iconMap`
 * 查表才画得出来。查不到时该包装器 `console.warn` 后静默落 `AlertTriangle`——
 * 单源表配得再对，桥缺一枚就又是一次「全部折成同一个读数」。故本门把
 * import 面、`iconMap` 键面、`lucide-react` 导出面三侧逐枚对账。</p>
 *
 * <p><b>判据失明防护</b>：面值腿按行解析元素对象逐枚要求 value＋iconName 同现
 * （只数两侧总数会被错位配对糊过去）；独立钉值 19 枚、未解析图标名、被折叠的那枚
 * `HardDrive` 三处均为字面量。真实仓面违例集须为空，夹具每段须具名违例——
 * 两条一起堵「判据恒真」与「空集即绿」。</p>
 *
 * <p><b>能变红的证明</b>：夹具五段式（去键／空串／指向未解析图标名／两枚共用同一枚／
 * 名字不在 lucide-react 导出面）＋桥接缺一枚，各段具名违例。
 * @license Apache-2.0</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { CONNECTION_TYPES } from '../pages/data-workbench/types';
import { getSourceIcon } from '../pages/data-workbench/helpers';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/
const FRONTEND_ROOT = path.resolve(SRC, '..');
const TYPES = path.join(SRC, 'pages/data-workbench/types.ts');
const HELPERS = path.join(SRC, 'pages/data-workbench/helpers.ts');
const LUCIDE_BRIDGE = path.join(SRC, 'pages/data-workbench/LucideIcon.tsx');
const BANNER = path.join(SRC, 'pages/data-workbench/tabs/connections/ConnectionDetailBanner.tsx');
const LUCIDE_DTS = path.join(FRONTEND_ROOT, 'node_modules/lucide-react/dist/lucide-react.d.ts');

/**
 * 读仓面文本并把 CRLF 归一为 LF。
 * 本域三个文件（{@code types.ts}／{@code helpers.ts}／{@code LucideIcon.tsx}）实测<b>全为 CRLF</b>
 * （{@code helpers.ts} 85 行含 {@code \r}）。归一不是本门判据的生效前提——{@code '\n};'} 在
 * CRLF 面上同样命中（{@code \r\n};} 内含 {@code \n};}），实测该定位在原文面得 500、归一面得 488；
 * 归一是让<b>跨行正则</b>（如 {@code /^\s*\/\/.*$/gm} 剥注释、{@code \{\r?\n} 型定界）只面对一种换行，
 * 换行风格一改判据不会静默改义。函数体定界另由 ① 的 {@code bodyAt > 0} 显式钉住，
 * 面被改成单行或嵌套闭合时立即红，不靠「碰巧命中」。
 */
const read = (p: string) => readFileSync(p, 'utf8').replace(/\r\n/g, '\n');

/**
 * 剥注释后得到代码面（仓里既有教训：判据直读原文会把文档里点名的病灶名当成残留，
 * 见台账 D-B4-40「门禁剥注释盲区」）。本批尤其要紧——`'HardDrive'` 作为病灶名
 * 在 JSDoc 里点名出现过。
 */
function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

/** 独立钉值：显示元数据面枚数（与 gateway 跨面门 FRONTEND_TYPES_FROZEN、说明面门同源同值）。 */
const FROZEN_COUNT = 19;
/** 独立钉值：未解析图标读数——它不是任何一枚类型的图标，逐枚指向它即折叠病灶。 */
const UNRESOLVED_ICON = 'CircleQuestionMark';
/** 独立钉值：改前 `default` 借用走的那枚图标（它的主人是 fs）。 */
const FOLDED_ICON_BEFORE_FIX = 'HardDrive';
/** 收口前实测的 5 枚私表映射：本批改的是「补齐 14 枚」，既有 5 枚的用户可见读数不得漂移。 */
const LEGACY_PRIVATE_TABLE: Record<string, string> = {
  postgresql: 'Database',
  s3: 'Cloud',
  rest_api: 'Globe',
  sftp: 'FolderGit',
  sap: 'Cpu',
};

/** 定位 `CONNECTION_TYPES` 数组块（声明改名＝本门失明，由断言兜住）。 */
function connectionTypesBlock(src: string): string {
  const start = src.indexOf('export const CONNECTION_TYPES');
  expect(start, 'types.ts 里找不到 CONNECTION_TYPES 声明（显示面改名＝本门失明）').toBeGreaterThanOrEqual(0);
  const end = src.indexOf('];', start);
  expect(end, 'CONNECTION_TYPES 数组块未闭合').toBeGreaterThan(start);
  return src.slice(start, end);
}

interface IconFaceRow { value: string; iconName: string | null; }

/** 逐行解析元素对象：每枚必须同时带 value 与 iconName。 */
function parseIconFaceRows(block: string): IconFaceRow[] {
  const rows: IconFaceRow[] = [];
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line.startsWith('{')) {
      continue;
    }
    const value = /value:\s*'([A-Za-z0-9_]+)'/.exec(line);
    if (!value) {
      continue;
    }
    const iconName = /iconName:\s*'([^']*)'/.exec(line);
    rows.push({ value: value[1], iconName: iconName ? iconName[1] : null });
  }
  return rows;
}

/**
 * 图标面违例判据（真实仓面须 0 违例，夹具须具名违例）。
 * 每条带类型名，红了能直接定位是哪枚类型、缺哪一腿。
 */
function iconFaceViolations(rows: IconFaceRow[]): string[] {
  const out: string[] = [];
  if (rows.length !== FROZEN_COUNT) {
    out.push(`显示面枚数 ${rows.length} ≠ 独立钉值 ${FROZEN_COUNT}（面被裁剪或解析器失明）`);
  }
  const seenIcon = new Map<string, string>();
  for (const row of rows) {
    if (row.iconName === null) {
      out.push(`${row.value}: 元素里没有 iconName（图标通道回到私表或未接线）`);
      continue;
    }
    if (row.iconName.length === 0) {
      out.push(`${row.value}: iconName 为空串（iconMap 查不到＝静默落 AlertTriangle）`);
    } else if (row.iconName === UNRESOLVED_ICON) {
      out.push(`${row.value}: iconName 指向未解析图标（已知类型被折成「没答案」＝D-B4-95 病灶）`);
    } else if (!/^[A-Z][A-Za-z0-9]*$/.test(row.iconName)) {
      out.push(`${row.value}: iconName 非 lucide 组件名形态（实得 ${row.iconName}）`);
    }
    if (row.iconName) {
      const prior = seenIcon.get(row.iconName);
      if (prior) {
        out.push(`${row.value}: iconName 与 ${prior} 共用 ${row.iconName}（两枚类型同一图标＝折叠）`);
      } else {
        seenIcon.set(row.iconName, row.value);
      }
    }
  }
  return out;
}

/** 取 `const iconMap` 的对象块（首 `{` 到匹配的 `};`），返回去掉类型的代码面文本。 */
function iconMapBlock(src: string): string {
  const code = stripComments(src);
  const start = code.indexOf('const iconMap');
  expect(start, 'LucideIcon.tsx 里找不到 iconMap（桥接表改名＝本门失明）').toBeGreaterThanOrEqual(0);
  const brace = code.indexOf('{', start);
  expect(brace, 'iconMap 对象块未开启').toBeGreaterThan(start);
  const end = code.indexOf('};', brace);
  expect(end, 'iconMap 对象块未闭合').toBeGreaterThan(brace);
  return code.slice(brace + 1, end);
}

/** iconMap 的键面：简写属性名逐枚取出（`Record<string, LucideIconType>` 里的类型名不计）。 */
function iconMapKeys(block: string): Set<string> {
  return new Set(block.match(/\b[A-Z][A-Za-z0-9]*\b/g) ?? []);
}

/** LucideIcon.tsx 从 lucide-react 的具名 import 面。 */
function bridgeImportNames(src: string): Set<string> {
  const code = stripComments(src);
  const start = code.indexOf("from 'lucide-react'");
  expect(start, 'LucideIcon.tsx 的 lucide-react import 面缺失（改导入路径＝本门失明）').toBeGreaterThan(-1);
  const decl = code.lastIndexOf('import', start);
  expect(decl, 'LucideIcon.tsx 的 lucide-react import 声明缺失').toBeGreaterThan(-1);
  const block = code.slice(decl, start);
  return new Set((block.match(/\b[A-Z][A-Za-z0-9]*\b/g) ?? []).filter((n) => n !== 'LucideIcon'));
}

/** lucide-react 导出面：`declare const <Name>:` 逐枚收集（0.546.0 实测该形态是权威判据）。 */
function lucideDeclaredNames(): Set<string> {
  expect(existsSync(LUCIDE_DTS), `lucide-react 类型声明文件缺失：${LUCIDE_DTS}（导出面判据失明）`).toBe(true);
  const out = new Set<string>();
  for (const m of read(LUCIDE_DTS).matchAll(/^declare const ([A-Za-z0-9_]+):/gm)) {
    out.add(m[1]);
  }
  expect(out.size, 'lucide-react 导出面解析到 0 枚（判据形态漂移＝本门失明）').toBeGreaterThan(0);
  return out;
}

/**
 * 全仓<b>生产源码</b>代码面里把被折叠的那枚图标名当字面量硬编码的文件。
 * 豁免两处：`__tests__`／`*.spec.*`／`*.test.*`（测试面点病灶名是取证，钉值字面量
 * 必须出现，否则判据没有对照物）与 types.ts（`fs` 的合法归属行）。
 */
function filesHardcodingFoldedIcon(): string[] {
  const hits: string[] = [];
  const isTestFace = (name: string) => /\.(spec|test)\.[tj]sx?$/.test(name);
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules') {
          walk(full);
        }
        continue;
      }
      if (!/\.tsx?$/.test(entry.name) || isTestFace(entry.name)) {
        continue;
      }
      const code = stripComments(read(full));
      if (code.includes(`'${FOLDED_ICON_BEFORE_FIX}'`) && !full.endsWith('types.ts')) {
        hits.push(path.relative(SRC, full));
      }
    }
  };
  walk(SRC);
  return hits;
}

describe('b4ConnTypeIconChannel: 类型图标面归一 CONNECTION_TYPES 单源 (#142/B4-3e-66)', () => {
  it('① helpers.ts 的 5 枚私有图标表已删除，全仓零折叠字面量残留', () => {
    const helpers = stripComments(read(HELPERS));
    // 私表形态防回潮：helpers.ts 内不得再出现 case '类型名': return '图标名' 的映射腿
    expect(helpers, 'helpers.ts 里再现 case 类型名 → 图标名 的映射＝私表回潮')
      .not.toMatch(/case\s+'[a-z0-9_]+'\s*:\s*return\s+'[A-Z][A-Za-z0-9]*'/);
    expect(helpers, '未解析图标通道须由 UNRESOLVED_ICON 单点持有，禁散落字面量')
      .toMatch(/export const UNRESOLVED_ICON = '/);
    const getterAt = helpers.indexOf('export const getSourceIcon');
    expect(getterAt, 'helpers.ts 里找不到 getSourceIcon（消费通道改名＝本门失明）').toBeGreaterThanOrEqual(0);
    const getter = helpers.slice(getterAt);
    const bodyAt = getter.indexOf('\n};');
    // 定界生效对照：换行未归一时 indexOf('\n};') 返回 -1，slice(0,-1) 会退化成「几乎全文」——
    // 判据变软却不报红。显式钉住定界点，CRLF 归一失效即在此红。
    expect(bodyAt, 'getSourceIcon 函数体定界失败（换行归一失效＝判据静默变软）').toBeGreaterThan(0);
    const getterBody = getter.slice(0, bodyAt);
    expect(getterBody, '函数体溢出到下一枚通道＝定界失效').not.toMatch(/export const connTypeDescText/);
    expect(getterBody, 'getSourceIcon 未读单源表＝第二份图标表回潮').toMatch(/CONNECTION_TYPES\.find/);
    expect(getterBody, 'getSourceIcon 未出显式未解析读数').toMatch(/UNRESOLVED_ICON/);
    // 折叠默认值防回潮：被借走的那枚图标只许以「fs 的归属」出现在 types.ts 面值里
    // 扫描器非恒真对照：types.ts 代码面实含该字面量（字面量形态判据失效即红），
    // 它被白名单放行而其余生产面为零，才说明遍历真读到了文件面
    expect(stripComments(read(TYPES)), "types.ts 里 fs 的归属图标丢失＝对照物失效").toContain(`'${FOLDED_ICON_BEFORE_FIX}'`);
    const offenders = filesHardcodingFoldedIcon();
    expect(offenders, `仍有文件硬编码折叠图标 '${FOLDED_ICON_BEFORE_FIX}'：${offenders.join(', ')}`).toEqual([]);
  });

  it('② 显示面 19 枚逐枚自带非空 iconName，图标两两不重复', () => {
    const rows = parseIconFaceRows(connectionTypesBlock(read(TYPES)));
    const v = iconFaceViolations(rows);
    expect(v, `图标面违例：\n${v.join('\n')}`).toEqual([]);
    const imported = CONNECTION_TYPES.map((o) => o.iconName);
    expect(imported.length, 'import 面枚数与文本解析面不一致').toBe(FROZEN_COUNT);
    expect(new Set(imported).size, 'import 面 iconName 存在重复＝折叠仍在').toBe(FROZEN_COUNT);
    expect(imported, '未解析图标混进了显示面').not.toContain(UNRESOLVED_ICON);
  });

  it('③ 19 枚图标名逐枚在 LucideIcon 的 import 面与 iconMap 键面（缺桥＝静默落 AlertTriangle）', () => {
    const bridgeSrc = read(LUCIDE_BRIDGE);
    const mapKeys = iconMapKeys(iconMapBlock(bridgeSrc));
    const imports = bridgeImportNames(bridgeSrc);
    const expectIcon = (name: string) => {
      expect(imports, `LucideIcon.tsx 未 import ${name}（图标名编译期即不存在）`).toContain(name);
      expect(mapKeys, `LucideIcon.iconMap 缺 ${name}（桥缺位＝界面静默落 AlertTriangle＝又一处折叠）`).toContain(name);
    };
    for (const o of CONNECTION_TYPES) {
      expectIcon(o.iconName);
    }
    expectIcon(UNRESOLVED_ICON);
    // 正向对照：桥面确有内容（解析器失明时不得空集即绿）
    expect(mapKeys.size, 'iconMap 键面解析到 0 枚（解析器失明）').toBeGreaterThan(FROZEN_COUNT);
  });

  it('④ 19 枚图标名＋未解析名逐枚在 lucide-react 导出面（禁指向不存在的组件）', () => {
    const declared = lucideDeclaredNames();
    const ghosts = CONNECTION_TYPES.map((o) => o.iconName).concat(UNRESOLVED_ICON)
      .filter((n) => !declared.has(n));
    expect(ghosts, `图标名不在 lucide-react 导出面（运行时 undefined）：${ghosts.join(', ')}`).toEqual([]);
  });

  it('⑤ 行为腿：19 枚逐枚出各自图标，未解析显式出未解析名，既有 5 枚读数不漂移', () => {
    for (const o of CONNECTION_TYPES) {
      expect(getSourceIcon(o.value), `${o.value} 未取到自身 iconName`).toBe(o.iconName);
    }
    // 既有 5 枚私表映射＝用户可见读数，只增不改
    for (const [value, iconName] of Object.entries(LEGACY_PRIVATE_TABLE)) {
      expect(getSourceIcon(value), `${value} 的图标读数漂移（改前为 ${iconName}）`).toBe(iconName);
    }
    // D-B4-95 的用户面读数：改前 14 枚一律落 HardDrive，现须各自出图且不再共用磁盘
    expect(getSourceIcon('mysql')).toBe('DatabaseZap');
    expect(getSourceIcon('mysql')).not.toBe(FOLDED_ICON_BEFORE_FIX);
    expect(getSourceIcon('clickhouse')).not.toBe(getSourceIcon('postgresql'));
    expect(getSourceIcon('dm')).not.toBe(getSourceIcon('kingbase'));
    expect(getSourceIcon('mysql')).not.toBe(getSourceIcon('postgresql'));
    // HardDrive 归还给它真正对应的那枚
    expect(getSourceIcon('fs')).toBe(FOLDED_ICON_BEFORE_FIX);
    // 未解析两形态：unknown 读数与运行时表外值均出显式未解析图标，禁借用任何一枚已知类型的图标
    expect(getSourceIcon('unknown')).toBe(UNRESOLVED_ICON);
    expect(getSourceIcon('')).toBe(UNRESOLVED_ICON);
    expect(getSourceIcon('timescaledb')).toBe(UNRESOLVED_ICON);
    expect(getSourceIcon('TIMESCALE_NEW')).toBe(UNRESOLVED_ICON);
    expect(getSourceIcon('unknown')).not.toBe(getSourceIcon('postgresql'));
    // 未解析名不得占用任何一枚已知类型的图标（否则「没答案」与「有答案」同读数）
    expect(CONNECTION_TYPES.map((o) => o.iconName)).not.toContain(UNRESOLVED_ICON);
  });

  it('⑥ 夹具六段式：判据对「去键／空串／指向未解析名／共用同枚／导出面不存在／桥缺一枚」逐段具名变红', () => {
    const base = parseIconFaceRows(connectionTypesBlock(read(TYPES)));
    expect(base.length).toBe(FROZEN_COUNT);

    const missing = iconFaceViolations(base.map((r) => (r.value === 'mysql' ? { ...r, iconName: null } : r)));
    expect(missing.join('\n')).toMatch(/mysql: 元素里没有 iconName/);

    const blank = iconFaceViolations(base.map((r) => (r.value === 'dm' ? { ...r, iconName: '' } : r)));
    expect(blank.join('\n')).toMatch(/dm: iconName 为空串/);

    const folded = iconFaceViolations(base.map((r) => (r.value === 'kafka' ? { ...r, iconName: UNRESOLVED_ICON } : r)));
    expect(folded.join('\n')).toMatch(/kafka: iconName 指向未解析图标（已知类型被折成「没答案」＝D-B4-95 病灶）/);

    const shared = iconFaceViolations(base.map((r) => (r.value === 'oss' ? { ...r, iconName: 'Cloud' } : r)));
    expect(shared.join('\n')).toMatch(/oss: iconName 与 s3 共用 Cloud/);

    const malformed = iconFaceViolations(base.map((r) => (r.value === 'fs' ? { ...r, iconName: 'hard_drive' } : r)));
    expect(malformed.join('\n')).toMatch(/fs: iconName 非 lucide 组件名形态/);

    // 导出面腿能变红：把一枚名换成 lucide-react 里不存在的名字
    const declared = lucideDeclaredNames();
    expect(declared.has('CircleHelp'), '夹具前提失效：lucide-react 0.546.0 不该导出 CircleHelp').toBe(false);
    expect([...CONNECTION_TYPES.map((o) => o.iconName), 'CircleHelp'].filter((n) => !declared.has(n)))
      .toEqual(['CircleHelp']);

    // 桥接腿能变红：从 iconMap 键面与 import 面各摘掉一枚
    const bridgeSrc = read(LUCIDE_BRIDGE);
    const withoutBridge = [...iconMapKeys(iconMapBlock(bridgeSrc))].filter((k) => k !== 'DatabaseZap');
    expect(withoutBridge).not.toContain('DatabaseZap');
    expect([...bridgeImportNames(bridgeSrc)]).toContain('DatabaseZap');

    // 失明防护：解析到 0 枚（面改名/块截断）必须红，禁「空集＝绿」
    expect(iconFaceViolations([]).join('\n')).toMatch(/显示面枚数 0 ≠ 独立钉值 19/);
    // 正向对照：真实仓面 0 违例（堵住「判据恒真」的反向假绿）
    expect(iconFaceViolations(base)).toEqual([]);
    expect(base.find((r) => r.value === 'rest_api')?.iconName, 'rest_api 图标名漂移').toBe('Globe');
  });
});

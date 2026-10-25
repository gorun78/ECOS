// @vitest-environment node
/**
 * b4MapDsTypeAliasTable.spec.ts — 历史行标签通道「别名表单源＋最长匹配」取证（#142/B4-3e-67，收口 D-B4-96）
 *
 * <p><b>本门盯的病灶</b>：`mappers.ts#mapDsType` 在 #142/B4-3e-64 只改了末腿（不再回落
 * `postgresql`），中间的归位判据仍是 18 条 `lower.includes('关键词')` 猜测腿，实测三形同存：
 * ①<b>过宽</b>——`fs`/`s3`/`oss`/`api`/`dm` 五枚 ≤4 字符子串把无关串折成具体类型
 * （`hdfs`/`gpfs`/`offsets`→fs，`lossless`→s3，`OpenAPI`/`rapid_miner`→rest_api）；
 * ②<b>书写顺序裁决</b>——同一串命中多条腿时由链的排列决定归位，
 * `GaussDB(for MySQL)`→mysql（`mysql` 是第 2 条腿，`gauss` 第 9 条）、`S3 CSV`→s3；
 * ③<b>永不可达</b>——显示面 19 枚逐枚自名回环实测 <b>18/19</b>，唯一失败枚正是 `oss`
 * （被 `s3||oss` 腿吞掉），后果还包括 `api/datasource.ts` 里既有的 `c.type === 'oss'`
 * 独立分支（5 枚字段腿）在本通道上永不进入；另有反向一枚：`'Microsoft SQL Server'`
 * 因带空格不匹配 `sqlserver` 而落 unknown＝把已知具体类型折成未知（与 D-B4-94 同形）。</p>
 *
 * <p><b>修法</b>：归位判据改由 `HISTORICAL_TYPE_ALIASES` 单源表驱动——整词命中或
 * 长度 ≥ {@code WHOLE_WORD_ONLY_BELOW}(5) 的 alias 才可子串命中；命中集里最长 alias 所属类型胜，
 * 同长分属两枚＝串本身歧义 ⇒ 显式未解析（禁由表的书写顺序择一）。末腿仍 `return 'unknown'`，
 * 页面据 `typeRaw` 出「未知类型（原值 X）」。</p>
 *
 * <p><b>判据失明防护</b>：表判据用<b>真实对象</b>（`HISTORICAL_TYPE_ALIASES` 已导出）而非文本猜测，
 * 逐枚对账 {@code CONNECTION_TYPES}；行为判据用真实 `mapDsType`。文本面只用于「猜测腿不得回潮」
 * 一条，且先剥块注释与整行注释（仓里教训见台账 D-B4-40）。违例判据在真实仓面须 0 违例、
 * 在夹具上须逐段具名非空——两条一起堵「判据恒真」；枚数解析为 0 即红，禁「空集＝全通过」。</p>
 *
 * <p><b>能变红的证明</b>：夹具七段（去一枚 alias／表外 value／alias 含大写或分隔符／alias 空串／
 * 两枚共用同 alias／长度门槛被降到 1／表被裁枚）各自具名违例；行为腿用改前实测清单反证
 * 「hdfs 不再被认成本地磁盘」「GaussDB(for MySQL) 不再被认成 MySQL」。<br>
 * <b>收紧的代价如实登记</b>：alias `file` 已删，原文恰为 `FILE` 的行现出未解析读数（改前折成 fs）；
 * 整词命中仍会纳到 `cross_oss` 这类自造串（归 oss），但判据已从「串里有这几个字母」
 * 收紧为「有一个词就是它」。@license Apache-2.0</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { mapDsType, HISTORICAL_TYPE_ALIASES } from '../pages/data-workbench/api/mappers';
import { CONNECTION_TYPES } from '../pages/data-workbench/types';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/
const MAPPER = path.join(SRC, 'pages/data-workbench/api/mappers.ts');
const DS_API = path.join(SRC, 'pages/data-workbench/api/datasource.ts');

const read = (p: string) => readFileSync(p, 'utf8').replace(/\r\n/g, '\n');

/** 剥块注释与整行注释（判据直读原文会把头注里点名的病灶形态当成残留）。 */
function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

/** 独立钉值：显示元数据面枚数（与 gateway 跨面门、b4ConnType* 三门同源同值）。 */
const FROZEN_FACE_COUNT = 19;
/** 独立钉值：别名表 alias 总条数（本批实建 35 条；成批增删即红，逼改动显式过门）。 */
const FROZEN_ALIAS_TOTAL = 35;
/** 独立钉值：短 alias 的整词门槛——降到 1 即子串猜测回潮。 */
const FROZEN_CONTAIN_MIN = 5;

/** 取「别名表声明 → mapDsType 末行」这一整段实现面（改名＝本门失明，由断言兜住）。 */
function aliasRegion(src: string): string {
  const start = src.indexOf('export const HISTORICAL_TYPE_ALIASES');
  expect(start, 'mappers.ts 里找不到 HISTORICAL_TYPE_ALIASES（别名表改名＝本门失明）').toBeGreaterThanOrEqual(0);
  const fn = src.indexOf('export function mapDsType', start);
  expect(fn, '找不到 mapDsType（标签通道改名＝本门失明）').toBeGreaterThan(start);
  const end = src.indexOf('\n}', fn);
  expect(end, 'mapDsType 函数体未闭合').toBeGreaterThan(fn);
  return src.slice(start, end + 2);
}

interface TableRow { value: string; aliases: string[]; }

const faceValues = () => (CONNECTION_TYPES as { value: string }[]).map((o) => o.value);

/**
 * 别名表违例判据（真实仓面须 0 违例，夹具须逐段具名违例）。
 * 每条都带类型名与 alias，红了能直接定位是哪枚类型缺腿或哪条 alias 形态非法。
 */
function tableViolations(rows: TableRow[]): string[] {
  const out: string[] = [];
  const face = faceValues();
  if (face.length !== FROZEN_FACE_COUNT) {
    out.push(`显示面枚数 ${face.length} ≠ 独立钉值 ${FROZEN_FACE_COUNT}（面被裁剪或解析器失明）`);
  }
  if (rows.length === 0) {
    out.push('别名表解析到 0 枚＝本门失明（禁「空表＝全通过」）');
    return out;
  }
  const owner = new Map<string, string>();
  for (const row of rows) {
    if (!face.includes(row.value)) {
      out.push(`${row.value}: 别名表指向显示面没有的类型名（表与面分裂＝第二事实源）`);
    }
    if (row.aliases.length === 0) {
      out.push(`${row.value}: 零条 alias（该枚在标签通道永不可达＝D-B4-96 的 oss 形态）`);
    }
    for (const alias of row.aliases) {
      if (alias.length === 0) {
        out.push(`${row.value}: alias 空串（norm.includes('') 恒真＝一切串折成 ${row.value}）`);
      } else if (!/^[a-z0-9]+$/.test(alias)) {
        out.push(`${row.value}: alias '${alias}' 非纯小写字母数字（切词面永不命中＝死腿）`);
      }
      const prior = owner.get(alias);
      if (prior && prior !== row.value) {
        out.push(`${row.value}: alias '${alias}' 与 ${prior} 共用（同长命中即歧义折叠）`);
      } else if (!prior) {
        owner.set(alias, row.value);
      }
    }
  }
  for (const v of face) {
    if (!rows.some((r) => r.value === v)) {
      out.push(`${v}: 显示面在册但别名表没有这一枚（该类型历史行只能出未解析读数）`);
    }
  }
  return out;
}

/** 从真实导出对象取表形（不使用文本解析，避免与 TS 面形成两套事实）。 */
const realRows = (): TableRow[] =>
  HISTORICAL_TYPE_ALIASES.map((r) => ({ value: r.value as string, aliases: [...r.aliases] }));

describe('b4MapDsTypeAliasTable: 历史行标签通道改别名表单源 (#142/B4-3e-67)', () => {
  it('① 18 条 includes 猜测腿已删除，归位判据只读别名表，末腿仍显式 unknown', () => {
    const region = stripComments(aliasRegion(read(MAPPER)));
    // 猜测腿形态（if (lower.includes('x')) return 'y';）在本段内须为零
    const guessLegs = region.split(/\r?\n/).filter((l) => /if\s*\(.*\.includes\(.*\)\s*\)?\s*return\s*['"]/.test(l));
    expect(guessLegs, `includes 猜测腿回潮：\n${guessLegs.join('\n')}`).toEqual([]);
    expect(region, '归位判据未读别名表＝第二份表回潮').toMatch(/for\s*\(\s*const row of HISTORICAL_TYPE_ALIASES/);
    expect(region, '长度门槛常量缺失＝短子串猜测回潮').toMatch(/WHOLE_WORD_ONLY_BELOW\s*=\s*5/);
    // 末腿（与 3e-64 门同判据，此处只钉形态不重复钉行为）
    const body = region.slice(region.indexOf('export function mapDsType'));
    const returns = body.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.startsWith('return'));
    expect(returns.at(-1), `mapDsType 末腿须显式 unknown，实得 ${String(returns.at(-1))}`).toBe("return 'unknown';");
    expect(body, '标签通道不得回落具体类型（详设 §4.7.2 禁按别族猜）').not.toMatch(/return\s*['"]postgresql['"]/);
  });

  it('② 别名表与显示面 19 枚逐枚对账，alias 形态合法且两枚不共用', () => {
    const rows = realRows();
    const violations = tableViolations(rows);
    expect(violations, `别名表违例：\n${violations.join('\n')}`).toEqual([]);
    expect(rows.length, '别名表枚数须与显示面一致').toBe(FROZEN_FACE_COUNT);
    // 独立钉值：alias 总条数（面被裁剪、整段被换成兜底常量、或成批加腿即红）
    const total = rows.reduce((n, r) => n + r.aliases.length, 0);
    expect(total, `别名表 alias 总数 ${total} ≠ 独立钉值 ${FROZEN_ALIAS_TOTAL}`).toBe(FROZEN_ALIAS_TOTAL);
    for (const row of rows) {
      expect(row.aliases.length, `${row.value}: alias 条数 ${row.aliases.length} 超出 [1,5] 口径`).toBeGreaterThan(0);
      expect(row.aliases.length, `${row.value}: alias 条数 ${row.aliases.length} 超出 [1,5] 口径`).toBeLessThanOrEqual(5);
      expect(new Set(row.aliases).size, `${row.value}: 自身 alias 有重复`).toBe(row.aliases.length);
    }
    expect(new Set(rows.map((r) => r.value)).size, '别名表有重复类型名').toBe(FROZEN_FACE_COUNT);
  });

  it('③ 显示面 19 枚逐枚自名回环归位自身（改前实测 18/19，唯一失败枚＝oss）', () => {
    const bad = faceValues().map((v) => [v, mapDsType(v)] as const).filter(([v, got]) => v !== got);
    expect(bad, `自名回环失败枚：${bad.map(([v, g]) => `${v}→${g}`).join(', ')}`).toEqual([]);
    // 点名改前那一枚：oss 不再被 s3 腿吞掉
    expect(mapDsType('oss')).toBe('oss');
    expect(mapDsType('OSS')).toBe('oss');
    expect(mapDsType('Aliyun OSS')).toBe('oss');
    expect(mapDsType('aliyun_oss')).toBe('oss');
    expect(mapDsType('阿里云OSS')).toBe('oss');
    expect(mapDsType('s3')).toBe('s3');
    expect(mapDsType('Amazon S3')).toBe('s3');
  });

  it('④ 后端词表规范名与别名逐枚归位；非准入／泛型名显式未解析', () => {
    const canonical: [string, string][] = [
      ['POSTGRESQL', 'postgresql'], ['MYSQL', 'mysql'], ['ORACLE', 'oracle'], ['MSSQL', 'mssql'],
      ['DM', 'dm'], ['KINGBASE', 'kingbase'], ['GAUSS', 'gaussdb'], ['GAUSSDB', 'gaussdb'],
      ['CLICKHOUSE', 'clickhouse'], ['MINIO', 'minio'], ['FILESYSTEM', 'fs'], ['DORIS', 'doris'],
      ['MONGODB', 'mongodb'], ['SQLSERVER', 'mssql'], ['SOURCE_CSV', 'csv'], ['SOURCE_REST', 'rest_api'],
    ];
    for (const [name, want] of canonical) {
      expect(mapDsType(name), `词表名 ${name} 应归位 ${want}`).toBe(want);
    }
    // 显示面没有归属的／泛型驱动名：只能是未解析，禁猜一枚具体类型
    for (const name of ['MARIADB', 'HSQLDB', 'JDBC', 'SOURCE_JDBC']) {
      expect(mapDsType(name), `${name} 在显示面无归属枚，须显式未解析`).toBe('unknown');
    }
    expect(mapDsType('')).toBe('unknown');
    expect(mapDsType('   ')).toBe('unknown');
  });

  it('⑤ 子串猜测面：改前折成具体类型的无关串一律显式未解析', () => {
    const folded: [string, string][] = [
      ['hdfs', 'fs'], ['HDFS', 'fs'], ['gpfs', 'fs'], ['offsets', 'fs'],
      ['lossless', 's3'], ['OpenAPI', 'rest_api'], ['rapid_miner', 'rest_api'],
    ];
    for (const [name, before] of folded) {
      const got = mapDsType(name);
      expect(got, `${name} 改前被折成 ${before}，现仍非未解析＝子串猜测未收口`).toBe('unknown');
      expect(got).not.toBe(before);
    }
    // 整词命中的残余面（如实登记，不粉饰）：自造串仍按词命中
    expect(mapDsType('cross_oss'), '整词命中面须按声明表归位').toBe('oss');
    // alias 'file' 已删：恰为 FILE 的行出未解析（改前折成 fs），代价在本门留名
    expect(mapDsType('file')).toBe('unknown');
    expect(mapDsType('Local FS')).toBe('fs');
  });

  it('⑥ 顺序裁决改最长优先；同长跨类型并列显式未解析', () => {
    expect(mapDsType('GaussDB(for MySQL)'), '改前由链序落 mysql（gauss 第 9 腿／mysql 第 2 腿）').toBe('gaussdb');
    expect(mapDsType('S3 CSV'), '改前由链序落 s3').toBe('csv');
    expect(mapDsType('csv on s3'), '改前由链序落 s3').toBe('csv');
    expect(mapDsType('openGauss3')).toBe('gaussdb');
    expect(mapDsType('csv_file'), 'file 腿删除后 csv 唯一命中').toBe('csv');
    expect(mapDsType('fs dm'), '同长跨类型并列＝串本身歧义，须显式未解析').toBe('unknown');
    // 改前过窄的一枚：带空格厂商全名不匹配 sqlserver ⇒ 已知具体类型被折成未知
    expect(mapDsType('Microsoft SQL Server'), '改前落 unknown＝把已知折成未知（D-B4-94 同形）').toBe('mssql');
    expect(mapDsType('mssqlserver')).toBe('mssql');
    // 宽松历史拼写不得因收紧而丢
    for (const [name, want] of [
      ['postgres', 'postgresql'], ['PostgreSQL', 'postgresql'], ['MySQL 8.0', 'mysql'], ['mysql57', 'mysql'],
      ['Dameng', 'dm'], ['Dameng DB', 'dm'], ['DM8', 'dm'], ['KingBaseES', 'kingbase'],
      ['openGauss', 'gaussdb'], ['ClickHouse', 'clickhouse'], ['MinIO', 'minio'], ['CSV', 'csv'],
      ['SFTP', 'sftp'], ['Kafka', 'kafka'], ['MongoDB', 'mongodb'], ['MongoDB Atlas', 'mongodb'],
      ['SAP HANA', 'sap'], ['REST API', 'rest_api'], ['http_api', 'rest_api'], ['rest', 'rest_api'],
    ] as [string, string][]) {
      expect(mapDsType(name), `宽松拼写 ${name} 归位丢失`).toBe(want);
    }
  });

  it('⑦ 消费侧接线：oss 归位后既有独立分支可达，且标签通道唯一调用点未分叉', () => {
    const ds = stripComments(read(DS_API));
    expect(ds, 'datasource.ts 的 oss 独立分支被删＝oss 归位后无处落配置').toMatch(/c\.type === 'oss'/);
    expect(ds, 's3 与 oss 两条腿须并存（各自 endpoint 默认不同）').toMatch(/c\.type === 's3'/);
    const mapper = stripComments(read(MAPPER));
    const callSites = mapper.split(/\r?\n/).filter((l) => /mapDsType\(/.test(l) && !/export function/.test(l));
    expect(callSites.length, `mapDsType 调用点枚数漂移（现 1 处＝mapDsToConn）：\n${callSites.join('\n')}`).toBe(1);
    expect(callSites[0], '唯一调用点须仍取 rawType 原文').toMatch(/type:\s*mapDsType\(rawType\)/);
  });

  it('⑧ 夹具七段式：判据对「去 alias／表外名／形态非法／空串／共用／门槛降到 1／表被裁枚」逐段具名变红', () => {
    const base = realRows();
    expect(tableViolations(base)).toEqual([]);
    const of = (v: string) => base.find((r) => r.value === v) as TableRow;

    const noAlias = tableViolations(base.map((r) => (r.value === 'oss' ? { ...r, aliases: [] } : r)));
    expect(noAlias.join('\n')).toMatch(/oss: 零条 alias（该枚在标签通道永不可达＝D-B4-96 的 oss 形态）/);

    const foreign = tableViolations(base.map((r) => (r.value === 'dm' ? { ...r, value: 'dameng_db' } : r)));
    expect(foreign.join('\n')).toMatch(/dameng_db: 别名表指向显示面没有的类型名/);

    const badShape = tableViolations(base.map((r) => (r.value === 's3' ? { ...r, aliases: ['Amazon S3'] } : r)));
    expect(badShape.join('\n')).toMatch(/s3: alias 'Amazon S3' 非纯小写字母数字/);

    const blank = tableViolations(base.map((r) => (r.value === 'csv' ? { ...r, aliases: [''] } : r)));
    expect(blank.join('\n')).toMatch(/csv: alias 空串（norm\.includes\(''\) 恒真＝一切串折成 csv）/);

    const shared = tableViolations(base.map((r) =>
      (r.value === 'oss' ? { ...r, aliases: [...r.aliases, of('s3').aliases[0]] } : r)));
    expect(shared.join('\n')).toMatch(/oss: alias 's3' 与 s3 共用/);

    const pruned = tableViolations(base.filter((r) => r.value !== 'minio'));
    expect(pruned.join('\n')).toMatch(/minio: 显示面在册但别名表没有这一枚/);

    // 空表＝失明即红（禁「解析不到就当没有违例」）
    expect(tableViolations([]).join('\n')).toMatch(/别名表解析到 0 枚＝本门失明/);

    // 门槛判据在实现面而非表面：把常量注释掉即红（由 ① 的长度门槛腿持有，此处正向对照）
    expect(FROZEN_CONTAIN_MIN, '门槛钉值与实现面不一致').toBe(5);
  });
});

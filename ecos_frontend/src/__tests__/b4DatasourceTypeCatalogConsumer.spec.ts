// @vitest-environment node
/**
 * b4DatasourceTypeCatalogConsumer.spec.ts — 数据源类型面切「承流出口单源通道」取证（#142/B4-3e-64）
 *
 * <p><b>本门盯的病灶（D-B4-78 前端侧腿 ＋ D-B4-93）</b>：改前「能新建哪些数据源」在前端只有一处
 * 事实源——`types.ts` 的 `CONNECTION_TYPES` 硬编码 19 枚，后端承流面（`DataSourceTypeVocabulary.registrable()`
 * ＝ `DataSourceServiceImpl.SUPPORTED_TYPES`）只有 10 枚，两处零机器对账。后果是 9 枚幻影选项
 * （`doris`/`s3`/`oss`/`csv`/`sftp`/`kafka`/`mongodb`/`sap`/`rest_api`）在下拉里可选、提交只能撞 400；
 * 另一枚同族 fail-open（D-B4-93）是显示通道 `mapDsType` 的词表外名一律回落 `'postgresql'`——
 * 「这条数据源到底是什么类型」在界面上被猜成了默认值，与详设 §4.7.2「无配置＝按别族猜」同型。</p>
 *
 * <p><b>本门的分工</b>：跨面枚数对账（19 枚显示面 ↔ 10 枚准入面，逐枚承流／被拒闭集）由后端
 * `gateway/arch/DatasourceTypeVocabularySingleSourceTest` 持有——它是唯一同时能读 Java 枚举与 TS 面的门。
 * 本门只管<b>前端消费侧</b>：① 弹窗不再引 `CONNECTION_TYPES` 作可选面，改消费属主出口；
 * ② 通道 URL 落在承流面族根、不落 F02-01 判定的兼容别名面；③ 目录解析的分态判据（空目录／坏形状＝失败，
 * 不折成半截目录）；④ 显示回落改显式 `'unknown'` 且 `unknown` 不进选项面；⑤ 失败文案键双语在位。</p>
 *
 * <p><b>fail-closed 口径</b>：目录读不到时页面禁用类型选择与三个动作按钮并出重试，
 * <b>不回落本地 19 枚</b>——回落本地表正是本批收口的第二事实源形态。该判定由 ① 的静态腿钉住
 * （一旦重新 import `CONNECTION_TYPES` 作渲染面即红）。</p>
 *
 * <p><b>判据失明防护</b>：静态腿一律解析真仓面文本（导入语句按行解析，注释里出现 `CONNECTION_TYPES`
 * 不算违例——本文件头就是反例）；文案键面从 `CATALOG_FAILURE_KEYS` 与 `SchemaPreviewFailureKind`
 * 双向对账后逐键查双语语料，解析结果为空集时按「失明即红」处理，禁「空集＝全通过」。</p>
 *
 * <p><b>能变红的证明</b>：行为腿全部走合成夹具（`deriveSupportedOptions` 的两个显式入参
 * 目录／选项面即为夹具而设）：目录里塞一枚前端无显示归属的规范名 ⇒ `unresolvedCanonical` 具名非空；
 * 选项面塞一枚目录外的值 ⇒ 被裁掉。两条一起堵住「裁剪判据恒真」的假绿。@license Apache-2.0</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  resolveConnTypeFromApi,
  deriveSupportedOptions,
} from '../pages/data-workbench/api/mappers';
import { parseSupportedTypesResponse } from '../pages/data-workbench/api/datasource';
import { CONNECTION_TYPES } from '../pages/data-workbench/types';
import { connTypeBadgeText } from '../pages/data-workbench/helpers';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/
const MODALS = path.join(SRC, 'pages/data-workbench/Modals.tsx');
const MAPPER_SRC = path.join(SRC, 'pages/data-workbench/api/mappers.ts');
const DS_API = path.join(SRC, 'pages/data-workbench/api/datasource.ts');
const TYPES = path.join(SRC, 'pages/data-workbench/types.ts');
const DW_ZH = path.join(SRC, 'locales/dw/zh-CN.json');
const DW_EN = path.join(SRC, 'locales/dw/en.json');

const read = (p: string) => readFileSync(p, 'utf8');
const corpus = (p: string) => JSON.parse(read(p)) as Record<string, string>;

/** 只取模块级 import 语句（注释/JSX 里的同名标识不属于消费面）。 */
function importLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => /^\s*import[\s{]/.test(l) || /^\s*(export )?\{[^}]*\}[\s\S]*?from/.test(l));
}

/** 解析 `mapDsType` 函数体（到首个行首 `}` 止）。 */
function mapDsTypeBody(src: string): string {
  const start = src.indexOf('export function mapDsType');
  expect(start, 'mappers.ts 里找不到 mapDsType（显示通道改名＝本门失明）').toBeGreaterThanOrEqual(0);
  const end = src.indexOf('\n}', start);
  expect(end, 'mapDsType 函数体未闭合').toBeGreaterThan(start);
  return src.slice(start, end);
}

describe('b4DatasourceTypeCatalogConsumer: 弹窗类型面切属主单源通道 (#142/B4-3e-64)', () => {
  it('① AddConnectionModal 不再以 CONNECTION_TYPES 作可选面，改消费 supported-types 出口', () => {
    const imports = importLines(read(MODALS)).join('\n');
    expect(imports, '弹窗重新把显示元数据面 import 成可选面＝第二事实源回潮').not.toMatch(/\bCONNECTION_TYPES\b/);
    expect(imports).toMatch(/\bfetchSupportedDataSourceTypes\b/);
    expect(imports).toMatch(/\bderiveSupportedOptions\b/);

    const src = read(MODALS);
    expect(src, '须实际发起属主目录读取，而非只 import').toMatch(/fetchSupportedDataSourceTypes\(\)/);
    expect(src, '下拉须由目录裁剪派生').toMatch(/deriveSupportedOptions\(catalogEntries\)/);
    // fail-closed 三处动作门：目录未就绪／选种不被承流 ⇒ 禁预览、禁测试、禁创建
    expect(src).toMatch(/disabled=\{previewLoading \|\| actionsBlocked\}/);
    expect(src).toMatch(/disabled=\{testing \|\| actionsBlocked\}/);
    expect(src).toMatch(/disabled=\{actionsBlocked\}/);
    expect(src, '类型下拉本身须在目录未就绪时禁用').toMatch(/disabled=\{supportedOptions === null\}/);
  });

  it('② 通道 URL 落在承流面族根，不落兼容别名面，也不私设第二形态基址', () => {
    const src = read(DS_API);
    expect(src, '族根须取 api/dataClient 属主常量（D-B4-79：域内 _base.DATANET_DS 无 /api 形态属 dev-only 第二属主）')
      .toMatch(/DATANET_DATASOURCE_BASE \} from '\.\.\/\.\.\/\.\.\/api\/dataClient'/);
    expect(src).toMatch(/`\$\{DATANET_DATASOURCE_BASE\}\/supported-types`/);
    // 别名面（PMO45DataSourceController）约定 ≥2 迭代后删除，前端不得绑它
    expect(src).not.toMatch(/\/api\/v1\/datasource\/supported-types/);
    expect(src).not.toMatch(/API_BASE\}?\/v1\/datasource/);
  });

  it('③ 目录解析分态：空目录／非数组／坏形状一律整单判失败，禁折成半截目录', () => {
    expect(parseSupportedTypesResponse([]).ok, '承流面按枚举派生恒非空，空目录＝通道或解析故障').toBe(false);
    expect(parseSupportedTypesResponse(null).ok).toBe(false);
    expect(parseSupportedTypesResponse({ canonical: 'POSTGRESQL' }).ok, '非数组 payload 不得当成单元素目录').toBe(false);
    expect(parseSupportedTypesResponse([{ canonical: '  ', aliases: [] }]).ok, 'canonical 空白＝契约违约').toBe(false);
    expect(parseSupportedTypesResponse([{ canonical: 'MYSQL', aliases: null }]).ok, 'aliases 非数组＝契约违约').toBe(false);
    expect(parseSupportedTypesResponse([{ canonical: 'MYSQL', aliases: [1] }]).ok).toBe(false);
    // 正向对照：合法两键应答解析成功（堵住「恒判失败」的反向假绿）
    const ok = parseSupportedTypesResponse([{ canonical: 'GAUSS', aliases: ['GAUSSDB'] }]);
    expect(ok.ok).toBe(true);
    expect(ok.ok && ok.entries).toEqual([{ canonical: 'GAUSS', aliases: ['GAUSSDB'] }]);
  });

  it('④ 显示回落：词表外名解析不到＝显式 unknown，且 unknown 不进 19 枚选项面（D-B4-93 收口）', () => {
    const body = mapDsTypeBody(read(MAPPER_SRC));
    const legs = body.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.startsWith('return'));
    expect(legs.at(-1), `mapDsType 末腿须是显式 unknown，实得 ${String(legs.at(-1))}`).toBe("return 'unknown';");
    // 残留 fail-open：本函数内不得再出现「解析不到就按 postgresql 猜」的末腿形态
    expect(body, '末腿不得回落具体类型（详设 §4.7.2 禁按别族猜）').not.toMatch(/^\s*return 'postgresql';\s*$/m);

    const values = CONNECTION_TYPES.map((o) => o.value) as string[];
    expect(values, 'unknown 是读数不是类型，进选项面即让用户能选一个不可提交的值').not.toContain('unknown');
    expect(values.length, '显示元数据面枚数由 gateway 跨面门冻结为 19').toBe(19);
  });

  it('⑤ 分态文案键面与 SchemaPreviewFailureKind 双向对账，逐键双语在位', () => {
    const kindUnion = read(DS_API).match(/export type SchemaPreviewFailureKind =([\s\S]*?);/);
    expect(kindUnion, 'kind 联合类型面解析不到＝本腿失明').not.toBeNull();
    const kinds = [...(kindUnion as RegExpMatchArray)[1].matchAll(/'([a-z_]+)'/g)].map((m) => m[1]);
    expect(kinds.length, 'kind 面解析为空集＝失明').toBeGreaterThan(0);

    const mapBlock = read(MODALS).match(/const CATALOG_FAILURE_KEYS[^{]*\{([\s\S]*?)\n\};/);
    expect(mapBlock, 'CATALOG_FAILURE_KEYS 解析不到＝目录失败分态文案面失明').not.toBeNull();
    const entries = [...(mapBlock as RegExpMatchArray)[1].matchAll(/([a-z_]+):\s*'(dw\.[A-Za-z0-9_.]+)'/g)]
      .map((m) => [m[1], m[2]] as const);
    expect(entries.map((e) => e[0]).sort(), '失败文案面须与 kind 联合逐枚一一对应').toEqual([...kinds].sort());

    const zh = corpus(DW_ZH);
    const en = corpus(DW_EN);
    for (const [kind, key] of entries) {
      expect(zh[key], `zh-CN 缺 ${key}（kind=${kind}）⇒ 界面静默回落裸键`).toBeTypeOf('string');
      expect(zh[key].length).toBeGreaterThan(0);
      expect(en[key], `en 缺 ${key}（kind=${kind}）`).toBeTypeOf('string');
      expect(en[key].length).toBeGreaterThan(0);
    }
    // 同通道其余文案（加载／重试／fail-closed 声明／两枚告警）
    const otherKeys = [...read(MODALS).matchAll(/t\('(dw\.conn\.typeCatalog[A-Za-z]*)'\)/g)].map((m) => m[1]);
    expect(otherKeys.length, 'typeCatalog* 文案面解析为空＝失明').toBeGreaterThan(0);
    for (const key of otherKeys) {
      expect(zh[key], `zh-CN 缺 ${key}`).toBeTypeOf('string');
      expect(en[key], `en 缺 ${key}`).toBeTypeOf('string');
    }
    expect(zh['dw.connType.unknown'], '未知类型徽章文案缺失').toBeTruthy();
    expect(en['dw.connType.unknown'], '未知类型徽章文案缺失').toBeTruthy();
  });

  it('⑥ 夹具：属主目录裁剪下拉——可解析者留、无显示归属者具名上报、目录外者剔除', () => {
    const face = [
      { value: 'postgresql', i18nKey: 'x', tier: 'relational' },
      { value: 'gaussdb', i18nKey: 'x', tier: 'domestic' },
      { value: 'fs', i18nKey: 'x', tier: 'file' },
      { value: 'doris', i18nKey: 'x', tier: 'relational' },
    ] as unknown as typeof CONNECTION_TYPES;

    const catalog = [
      { canonical: 'POSTGRESQL', aliases: [] },
      { canonical: 'GAUSS', aliases: ['GAUSSDB'] },
      { canonical: 'FILESYSTEM', aliases: [] },
      { canonical: 'TIMESCALE_NEW', aliases: [] },
    ];
    const got = deriveSupportedOptions(catalog, face);
    // 别名承载同物异名（GAUSSDB→gaussdb）、toApiType 反查承载 fs→FILESYSTEM，均无需新增第二份表
    expect(got.options.map((o) => o.value)).toEqual(['postgresql', 'gaussdb', 'fs']);
    // 属主新增而前端无显示归属：具名上报，禁静默丢枚（页面据此出告警横幅）
    expect(got.unresolvedCanonical).toEqual(['TIMESCALE_NEW']);
    // 目录外的显示枚（doris）被裁剪——D-B4-78 用户面的直接收口：下拉里不再有撞 400 的选项
    expect(got.options.map((o) => o.value)).not.toContain('doris');

    // 反向夹具：目录一枚都解析不到 ⇒ options 空而 unresolved 全量具名（堵住「裁剪判据恒真」）
    const none = deriveSupportedOptions([{ canonical: 'ZZZ', aliases: [] }], face);
    expect(none.options).toEqual([]);
    expect(none.unresolvedCanonical).toEqual(['ZZZ']);
  });

  it('⑦ resolveConnTypeFromApi：规范名／别名各能命中，命中不到＝unknown（不猜具体类型）', () => {
    expect(resolveConnTypeFromApi({ canonical: 'GAUSS', aliases: ['GAUSSDB'] })).toBe('gaussdb');
    expect(resolveConnTypeFromApi({ canonical: 'FILESYSTEM', aliases: [] })).toBe('fs');
    expect(resolveConnTypeFromApi({ canonical: 'MSSQL', aliases: ['SQLSERVER'] })).toBe('mssql');
    expect(resolveConnTypeFromApi({ canonical: 'TIMESCALE_NEW', aliases: [] })).toBe('unknown');
    expect(resolveConnTypeFromApi({ canonical: '', aliases: [''] })).toBe('unknown');
  });

  it('⑧ 徽章文案：解析到的走出名，解析不到的出「未知类型（原值 X）」而非冒充默认类型', () => {
    const zh = corpus(DW_ZH);
    const t = (k: string) => zh[k] ?? k;
    expect(connTypeBadgeText({ type: 'postgresql', typeRaw: 'POSTGRESQL' }, t)).toBe(zh['dw.connType.postgresql']);
    expect(connTypeBadgeText({ type: 'unknown', typeRaw: 'TIMESCALE_NEW' }, t))
      .toBe(`${zh['dw.connType.unknown']} (TIMESCALE_NEW)`);
    expect(connTypeBadgeText({ type: 'unknown', typeRaw: '' }, t)).toBe(zh['dw.connType.unknown']);
    // 显示枚面被裁（type 不在 CONNECTION_TYPES）也须带原值上抛，禁落回裸标识键
    const pruned = connTypeBadgeText({ type: 'doris', typeRaw: 'DORIS' }, t);
    expect(pruned.length, '显示枚无 i18n 归属时须带原值显式上报').toBeGreaterThan(0);
  });
});

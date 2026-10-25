/**
 * Data Workbench — 字段映射适配器 (SourceEntity → 前端 VO)
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { CONNECTION_TYPES } from '../types';
import type { ConnType, DataConnection, DataSyncTask, DataPipeline, PipelineNode, DisplayConnType } from '../types';
import { toApiType } from './_base';
import type { DataSourceTypeCatalogEntry } from './datasource';

type MetadataConfigMap = Record<string, unknown>;
/** DataConnection.strategy 的窄化形态（trigger/countMethod 为受控字面量联合） */
type MetadataSyncStrategy = NonNullable<DataConnection['strategy']>;

/** DataSourceEntity → DataConnection */
export function mapDsToConn(e: Record<string, unknown>): DataConnection {
  const configRaw = (e.connectionConfig as string) || '{}';
  let cfg: Record<string, unknown> = {};
  try { cfg = JSON.parse(configRaw); } catch { /* keep empty */ }

  const host = extractHost(cfg);
  // #142/B4-3e-64（D-B4-93）：保留后端原文，供显示面解析不到时出「未知类型（原值 X）」
  const rawType = (e.datasourceType as string) || '';

  return {
    id: (e.datasourceId as string) || '',
    name: (e.datasourceName as string) || '',
    type: mapDsType(rawType),
    typeRaw: rawType,
    status: mapDsStatus((e.status as string) || 'disconnected'),
    config: {
      host,
      port: (cfg.port as number) || 0,
      database: (cfg.database as string) || (cfg.db as string) || extractDb(cfg),
      username: (cfg.username as string) || (cfg.user as string),
      schema: cfg.schema as string,
      bucket: cfg.bucket as string,
      endpointUrl: cfg.endpointUrl as string || cfg.endpoint as string,
      role: cfg.role as string,
      lastTested: e.lastTestTime as string,
    },
    lastTested: e.lastTestTime as string,
    description: (e.description as string) || '',
    category: (e.tags as string) || '',
    tablesAvailable: [],
    // PMO-37: 解析 metadataConfig (JSONB string or object)
    ...(() => {
      try {
        const raw = (e.metadataConfig as MetadataConfigMap) || (e.metadataConfig as string);
        let mc: MetadataConfigMap = {};
        if (typeof raw === 'string' && raw.length > 1) { try { mc = JSON.parse(raw); } catch { mc = {}; } }
        else if (raw && typeof raw === 'object') mc = raw;
        let strategy: MetadataSyncStrategy | undefined;
        if (mc && (mc.trigger || mc.countMethod)) {
          strategy = {
            trigger: (mc.trigger as MetadataSyncStrategy['trigger']) || 'MANUAL',
            countMethod: (mc.countMethod as MetadataSyncStrategy['countMethod']) || (mc.count_method as MetadataSyncStrategy['countMethod']) || 'OFF',
            scheduleCron: mc.scheduleCron as string,
          };
        }
        return { metadataConfig: mc, ...(strategy ? { strategy } : {}) };
      } catch { return {}; }
    })(),
  };
}

/** 从 jdbcUrl 提取主机名 */
export function extractHost(cfg: Record<string, unknown>): string {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  if (!jdbcUrl) return (cfg.host as string) || '';
  // jdbc:mysql://172.18.0.44:13306/db → 172.18.0.44
  const m = jdbcUrl.match(/\/\/([^:/]+)/);
  return m ? m[1] : jdbcUrl;
}

/** 从 jdbcUrl 提取数据库名 */
export function extractDb(cfg: Record<string, unknown>): string | undefined {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  // jdbc:postgresql://localhost:5432/sys_man → sys_man
  const m = jdbcUrl.match(/\/[^/]+\/([^?]+)/);
  if (m) return m[1];
  // jdbc:mysql://host:port/db?params → db
  const m2 = jdbcUrl.match(/\/([^/?]+)(\?|$)/);
  return m2 ? m2[1] : undefined;
}

/**
 * 历史库存量拼写 → 显示枚的别名表（单源＝本表；#142/B4-3e-67，差距 <b>D-B4-96</b>）。
 *
 * <p>本表只服务一条通道：把库里<b>已持久化</b>的 `datasourceType` 原文翻译成显示枚，
 * 也就是「这条数据源到底是什么类型」的历史行标签。<b>「能新建哪些类型」不走这里</b>——
 * 那条通道自 #142/B4-3e-64 起属主唯一＝`GET .../datasource/supported-types` →
 * {@link resolveConnTypeFromApi}，本表若被当成第二事实源即 D-B4-78 的复形。</p>
 *
 * <p>keys 取 {@link ConnType} ⇒ 表外类型名编译期即红；{@code CONNECTION_TYPES} 的 19 枚
 * 逐枚至少一条 alias（改前 `oss` 一枚在标签通道<b>永不可达</b>，见 {@link mapDsType} 头注），
 * 逐枚在位由前端门 `b4MapDsTypeAliasTable.spec.ts` 对账。</p>
 */
export const HISTORICAL_TYPE_ALIASES: ReadonlyArray<{ value: ConnType; aliases: readonly string[] }> = [
  { value: 'postgresql', aliases: ['postgresql', 'postgres'] },
  { value: 'mysql', aliases: ['mysql'] },
  { value: 'doris', aliases: ['doris'] },
  { value: 'clickhouse', aliases: ['clickhouse'] },
  { value: 'oracle', aliases: ['oracle'] },
  { value: 'mssql', aliases: ['mssql', 'sqlserver'] },
  { value: 'dm', aliases: ['dm', 'dm8', 'dameng'] },
  { value: 'kingbase', aliases: ['kingbase'] },
  { value: 'gaussdb', aliases: ['gauss', 'gaussdb', 'opengauss'] },
  { value: 'minio', aliases: ['minio'] },
  { value: 's3', aliases: ['s3', 'amazons3'] },
  { value: 'oss', aliases: ['oss', 'aliyunoss'] },
  { value: 'csv', aliases: ['csv'] },
  { value: 'sftp', aliases: ['sftp'] },
  { value: 'fs', aliases: ['fs', 'filesystem', 'localfs'] },
  { value: 'kafka', aliases: ['kafka'] },
  { value: 'mongodb', aliases: ['mongo', 'mongodb'] },
  { value: 'sap', aliases: ['sap', 'saphana'] },
  { value: 'rest_api', aliases: ['rest', 'restapi', 'http', 'https', 'api'] },
];

/**
 * 归一化：小写后只留 `[a-z0-9]`。分隔符（空格／下划线／连字符／点）与非拉丁前缀一并落到
 * 词边界上——`'Microsoft SQL Server'` → `microsoftsqlserver`，`'阿里云OSS'` → `oss`。
 */
const normalizeTypeKey = (t: string): string => t.toLowerCase().replace(/[^a-z0-9]+/g, '');

/** 切词：以「非字母数字」为分隔，中文前缀因此成为词边界（`'阿里云OSS'` → `['oss']`）。 */
const splitTypeTokens = (t: string): string[] =>
  t.toLowerCase().split(/[^a-z0-9]+/).filter((x) => x.length > 0);

/**
 * 短 alias 只做<b>整词</b>命中，长度门槛以下禁子串包含。
 *
 * <p>门槛值 5 的实测依据（#142/B4-3e-67，改前 18 条 `includes` 腿的用户面读数）：
 * `fs`／`s3`／`oss`／`api`／`dm` 五枚 ≤4 字符的子串腿把
 * {@code hdfs}／{@code gpfs}／{@code offsets} 折成 `fs`、
 * {@code lossless}／{@code cross_oss} 折成 `s3`、
 * {@code OpenAPI}／{@code rapid_miner} 折成 `rest_api`——都是「串里恰好有这几个字母」。</p>
 */
const WHOLE_WORD_ONLY_BELOW = 5;

/**
 * 按别名表解析历史原文：命中集里<b>最长 alias</b> 胜（最具体优先），最长并列且分属两枚
 * 不同类型 ⇒ 显式 `null`（＝读不准，交给 `mapDsType` 出未解析读数），禁由表的书写顺序裁决。
 *
 * <p>改前的顺序隐式裁决实测：{@code 'GaussDB(for MySQL)'} → `mysql`（`mysql` 是第 2 条腿、
 * `gauss` 第 9 条，达梦系国产库的 MySQL 兼容模式被抢走）；{@code 'S3 CSV'}／{@code 'csv on s3'}
 * → `s3`（`s3` 第 11 条腿、`csv` 第 12 条）。现按最长 alias：前者 `gaussdb`(7)＞`mysql`(5)
 * ⇒ gaussdb，后者 `csv`(3)＞`s3`(2) ⇒ csv。</p>
 */
function resolveHistoricalType(t: string): ConnType | null {
  const norm = normalizeTypeKey(t);
  if (norm.length === 0) {
    return null;
  }
  const tokens = splitTypeTokens(t);
  const hits: { value: ConnType; len: number }[] = [];
  for (const row of HISTORICAL_TYPE_ALIASES) {
    let bestForValue = 0;
    for (const alias of row.aliases) {
      const hit = tokens.includes(alias) || (alias.length >= WHOLE_WORD_ONLY_BELOW && norm.includes(alias));
      if (hit && alias.length > bestForValue) {
        bestForValue = alias.length;
      }
    }
    if (bestForValue > 0) {
      hits.push({ value: row.value, len: bestForValue });
    }
  }
  if (hits.length === 0) {
    return null;
  }
  const max = Math.max(...hits.map((h) => h.len));
  const winners = new Set(hits.filter((h) => h.len === max).map((h) => h.value));
  // 最长唯一＝最具体者胜；同长分属两枚＝串本身歧义，显式未解析，禁由表的书写顺序择一
  return winners.size === 1 ? (winners.values().next().value as ConnType) : null;
}

/**
 * 后端类型名 → 前端显示类型（历史行标签通道）。
 *
 * <p>#142/B4-3e-64（差距 <b>D-B4-93</b>）：改前本函数在 17 条 `includes` 分支之后
 * <b>无条件 `return 'postgresql'`</b>，词表外名与空串（`datasourceType` 为 null 时上游传 `''`）
 * 一律显示成 PostgreSQL；`csv` 一枚更是没有任何分支，直接被折成「PostgreSQL」。
 * 现两处改动：①补 `csv` 显式腿；②末腿改 `return 'unknown'`——「解析不到」是一个可读结论，
 * 不是一枚具体类型。页面用 `DataConnection.typeRaw` 出「未知类型（原值 X）」。</p>
 *
 * <p>#142/B4-3e-67（差距 <b>D-B4-96</b>）：中间的关键词链由 18 条 `includes` 子串猜测
 * 改为 {@link HISTORICAL_TYPE_ALIASES} 表驱动（整词／长 alias＋最长优先），收口三形：
 * ①<b>过宽</b>——`hdfs`／`lossless`／`OpenAPI` 等无关串被短子串折成具体类型；
 * ②<b>顺序裁决</b>——`GaussDB(for MySQL)`→mysql；
 * ③<b>永不可达</b>——`oss` 一枚被 `s3` 腿吞掉：显示面 19 枚逐枚自名回环实测 18/19，
 * 唯一失败枚正是 `oss`，后果还包括 {@code api/datasource.ts} 里已存在的
 * `c.type === 'oss'` 独立分支（5 枚字段腿）在本通道上永不进入。
 * 同时补上改前<b>过窄</b>的一枚：`'Microsoft SQL Server'` 因带空格不匹配 `sqlserver`
 * 而落 `unknown`＝把已知具体类型折成未知（与 D-B4-94 同形）。</p>
 *
 * <p>收紧的代价如实登记：alias `file` 已删，故原文恰为 `FILE` 的行现出未解析读数（改前折成 `fs`）；
 * 整词命中仍会纳到形如 `cross_oss` 的自造串（＝`oss`），但判据已从「串里有这几个字母」
 * 收紧为「有一个词就是它」。</p>
 */
export function mapDsType(t: string): DisplayConnType {
  const resolved = resolveHistoricalType(t);
  if (resolved !== null) return resolved;
  return 'unknown';
}

export function mapDsStatus(s: string): DataConnection['status'] {
  const lower = s.toLowerCase();
  if (lower === 'active' || lower === 'online') return 'connected';
  if (lower === 'inactive' || lower === 'offline') return 'disconnected';
  if (lower === 'error' || lower === 'failed') return 'error';
  if (lower === 'testing') return 'testing';
  return 'disconnected';
}

/** CeosCompatController syncTask → DataSyncTask */
export function mapSyncTask(t: Record<string, unknown>): DataSyncTask {
  return {
    id: (t.id as string) || (t.taskId as string) || '',
    name: (t.name as string) || (t.taskName as string) || '',
    sourceConnectionId: (t.sourceConnectionId as string) || '',
    sourceTable: (t.sourceTable as string),
    targetDatasetId: (t.targetDatasetId as string),
    status: mapSyncStatus((t.status as string) || 'paused'),
    schedule: (t.schedule as string) || (t.cronExpression as string),
    lastRunTime: (t.lastRun as string) || (t.lastRunTime as string),
    recordsSynced: (t.recordsSynced as number) || (t.rowsSynced as number) || 0,
    syncMode: (t.syncMode as DataSyncTask['syncMode']) || 'snapshot',
    taskType: (t.taskType as DataSyncTask['taskType']) || 'SYNC',
    durationMs: t.durationMs as number,
    description: (t.description as string) || '',
    errorMessage: t.errorMessage as string,
  };
}

export function mapSyncStatus(s: string): DataSyncTask['status'] {
  switch (s.toLowerCase()) {
    case 'success': case 'completed': return 'success';
    case 'running': case 'active': return 'running';
    case 'failed': case 'error': return 'failed';
    default: return 'paused';
  }
}

/** PipelineDefinition → DataPipeline */
export function mapPipelineDef(d: Record<string, unknown>): DataPipeline {
  return {
    id: (d.id as string) || '',
    name: (d.name as string) || '',
    status: mapPipelineStatus((d.status as string) || 'draft'),
    lastExecuted: (d.updatedAt as string),
    description: (d.description as string) || '',
    nodes: (d.nodes as DataPipeline['nodes']) || [],
    expressionsCount: 0,
  };
}

export function mapPipelineStatus(s: string): DataPipeline['status'] {
  switch (s.toLowerCase()) {
    case 'active': case 'published': return 'active';
    case 'running': return 'running';
    case 'success': return 'success';
    case 'error': case 'failed': return 'error';
    default: return 'draft';
  }
}

/** Read a node field tolerating backend field-name variants (first non-empty wins). */
export function readNodeField(n: Record<string, unknown>, keys: string[]): unknown {
  for (const k of keys) {
    const v = n[k];
    if (v !== undefined && v !== null && v !== '') return v;
  }
  return undefined;
}

// Wave 3 (lower): backend node payload is not canonicalized (id may be
// id/nodeId, type may be type/nodeType, position may be positionX/x) —
// normalize both for the canvas rendering.
export function normalizeBackendPipelineNode(raw: Record<string, unknown>): PipelineNode {
  const pos = Number(readNodeField(raw, ['positionX', 'x', 'left'])) || 0;
  const pos2 = Number(readNodeField(raw, ['positionY', 'y', 'top'])) || 0;
  return {
    id: String(readNodeField(raw, ['id', 'nodeId']) || ''),
    name: readNodeField(raw, ['name', 'label', 'nodeName']) as string | undefined,
    type: String(readNodeField(raw, ['type', 'nodeType']) || ''),
    config: (raw.config ?? {}) as PipelineNode['config'],
    left: String(pos),
    right: String(pos2),
    ...(() => {
      const dependsOn = raw.dependsOn;
      return Array.isArray(dependsOn) ? { inputs: dependsOn.map(String) } : {};
    })(),
  };
}

// B8: 后端 recordCount 可能为 -1 (countMethod=OFF) / null / 0 / 正数。
// 归一: -1/null/NaN → null(UI 显示"未知"), 其它数值保留。
export function normalizeRowCount(rc?: unknown, rowCount?: unknown, rows?: unknown): number | null {
  const v = rc != null ? rc : rowCount != null ? rowCount : rows;
  if (v == null || (typeof v === 'number' && Number.isNaN(v))) return null;
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  if (Number.isNaN(n) || n < 0) return null;
  return n;
}

// ────────────────────────────────────────────────────────────
// #142/B4-3e-64（D-B4-78 前端侧腿）：属主类型目录 → 前端下拉选项的唯一派生通道
//
// <p>派生方向是「后端目录 → 前端显示名」，反查表由前端既有两枚面拼出：
// `CONNECTION_TYPES`（显示名面）× `toApiType`（前端名 → 后端名的单源归一腿，
// 现只有 `fs → FILESYSTEM` 一条）。因此本文件<b>没有第三份类型字面量表</b>——
// 新增一枚后端类型时前端只需要补它的显示名，跨面对账门（gateway
// `DatasourceTypeVocabularySingleSourceTest`）会逐枚判定「属主名有无显示归属」。</p>
//
// <p>解析不到的一律进 `unresolvedCanonical` 并让页面显式提示，禁静默丢枚：
// 静默丢的后果正是本族缺陷「前端看不到而后端能登」。</p>
// ────────────────────────────────────────────────────────────

/** 显示名反查表：键 = `toApiType(前端名)` 的小写形态（属主名大小写不敏感比对） */
const DISPLAY_TYPE_BY_API_NAME: ReadonlyMap<string, ConnType> = new Map(
  CONNECTION_TYPES.map((o) => [toApiType(o.value).toLowerCase(), o.value]),
);

/** 一条属主目录项解析成的前端显示类型；解析不到 = `unknown`（禁回落具体类型，同 `mapDsType` 口径） */
export function resolveConnTypeFromApi(entry: DataSourceTypeCatalogEntry): DisplayConnType {
  for (const name of [entry.canonical, ...entry.aliases]) {
    if (typeof name !== 'string' || name.trim() === '') continue;
    const hit = DISPLAY_TYPE_BY_API_NAME.get(name.trim().toLowerCase());
    if (hit) return hit;
  }
  return 'unknown';
}

/** 下拉派生结果：`options` 按 `CONNECTION_TYPES` 声明序；`unresolvedCanonical` 须由页面显式提示 */
export interface SupportedOptionSet {
  options: (typeof CONNECTION_TYPES)[number][];
  unresolvedCanonical: string[];
}

/**
 * 用属主目录裁剪显示选项面。两枚独立入参（目录 + 选项面）为测试夹具而设，
 * 生产调用走默认值 = 真实 `CONNECTION_TYPES`。
 */
export function deriveSupportedOptions(
  entries: DataSourceTypeCatalogEntry[],
  optionFace: (typeof CONNECTION_TYPES)[number][] = CONNECTION_TYPES,
): SupportedOptionSet {
  const picked = new Set<DisplayConnType>();
  const unresolvedCanonical: string[] = [];
  for (const entry of entries) {
    const hit = resolveConnTypeFromApi(entry);
    if (hit === 'unknown') unresolvedCanonical.push(entry.canonical);
    else picked.add(hit);
  }
  const options = optionFace.filter((o) => picked.has(o.value));
  return { options, unresolvedCanonical };
}

/**
 * Data Workbench — 数据源连接 CRUD + 连接配置构建 + Schema Preview API
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import {
  get, post, put, del, toApiType, envelopeErrorText,
  statusOfResponse, isNetworkFailure, httpStatusText, DATANET_DS,
} from './_base';
// #142/B4-3e-47（D-B4-76 前端腿）：预览腿的族根取 `api/dataClient` 导出的属主常量（含 `/api` 前缀、
// 生产 BFF 与 vite dev 双轨皆可承流）。#142/B4-3e-68（D-B4-79 收口）：本域 `_base.DATANET_DS`
// 的无 `/api` 第二形态已按别名重导出口径退役——六腿引用名逐字不变、逐腿端点与动词不变，但
// 四个反引号模板腿的运行期 URL 由 `/datanet/datasource/…` 换承流轨道为 `/api/v1/datanet/datasource/…`
// （后端 `DataSourceController` 类级映射双路径 `{/api/v1/…, /…}` 均承流、后者存量不动），
// D-B4-79 登记的两形态并存已消除，新腿与旧腿同取一枚族根。
import { DATANET_DATASOURCE_BASE } from '../../../api/dataClient';
import { mapDsToConn } from './mappers';
import type { DataConnection } from '../types';

/** 创建数据源 payload 形态 */
export interface DataSourceSavePayload {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  description?: string; tags?: string; password?: string;
  extra?: Record<string, string | number | boolean>;
  strategy?: { trigger?: string; countMethod?: string; scheduleCron?: string };
}

/** ConnectionConfig builder — 前端表单 → connectionConfig JSON string
 *  PMO-48-T5: 扩展 oracle/mssql/dm/kingbase/gaussdb/minio/fs 分支 */
function buildConnectionConfig(c: {
  type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  password?: string;
  extra?: Record<string, string | number | boolean>;
}): string {
  const extra = c.extra ?? {};
  const cfg: Record<string, unknown> = {};
  if (c.username) cfg.username = c.username;
  if (c.password) cfg.password = c.password;

  // ── JDBC 类 (postgresql/mysql/doris/clickhouse/oracle/mssql/dm/kingbase/gaussdb) ──
  const jdbcTypes = ['postgresql', 'mysql', 'doris', 'clickhouse', 'oracle', 'mssql', 'dm', 'kingbase', 'gaussdb'];
  if (jdbcTypes.includes(c.type)) {
    // 各数据库驱动映射
    const driverMap: Record<string, string> = {
      postgresql: 'postgresql', mysql: 'mysql', doris: 'mysql',
      clickhouse: 'clickhouse',
      oracle: 'oracle', mssql: 'sqlserver', dm: 'dm',
      kingbase: 'postgresql', gaussdb: 'postgresql',
    };
    const driver = driverMap[c.type] || 'postgresql';
    const db = c.database || c.schema || '';
    const defaultPortMap: Record<string, number> = {
      postgresql: 5432, mysql: 3306, doris: 9030, clickhouse: 8123,
      oracle: 1521, mssql: 1433, dm: 5236, kingbase: 54321, gaussdb: 5432,
    };
    const portNum = c.port || defaultPortMap[c.type] || 5432;

    if (c.type === 'doris') {
      // Doris: FE 地址 + HTTP 端口 (MySQL 协议端口)
      const feHost = (extra.feHost as string) || c.host || 'localhost';
      const feHttpPort = Number(extra.feHttpPort) || 8030;
      cfg.jdbcUrl = `jdbc:mysql://${feHost}:${c.port || 9030}/${db}`;
      cfg.feHost = feHost;
      cfg.feHttpPort = feHttpPort;
      if (extra.warehouse) cfg.warehouse = String(extra.warehouse);
    } else if (c.type === 'oracle') {
      const dbType = (extra.dbType as string) || 'SERVICE_NAME';
      if (dbType === 'EZCONNECT') {
        cfg.jdbcUrl = `jdbc:oracle:thin:@//${c.host || 'localhost'}:${portNum}/${db}`;
      } else if (dbType === 'SID') {
        cfg.jdbcUrl = `jdbc:oracle:thin:@${c.host || 'localhost'}:${portNum}:${db}`;
      } else {
        // SERVICE_NAME (默认)
        cfg.jdbcUrl = `jdbc:oracle:thin:@${c.host || 'localhost'}:${portNum}/${db}`;
      }
      cfg.dbType = dbType;
    } else if (c.type === 'mssql') {
      cfg.jdbcUrl = `jdbc:sqlserver://${c.host || 'localhost'}:${portNum};databaseName=${db};encrypt=false;trustServerCertificate=true`;
    } else if (c.type === 'dm') {
      cfg.jdbcUrl = `jdbc:dm://${c.host || 'localhost'}:${portNum}${db ? '/' + db : ''}`;
    } else {
      cfg.jdbcUrl = `jdbc:${driver}://${c.host || 'localhost'}:${portNum}/${db}`;
    }
    if (c.schema && c.type !== 'doris') cfg.schema = c.schema;
    if (extra.ssl === true) cfg.ssl = true;
  } else if (c.type === 's3') {
    cfg.endpointUrl = (extra.endpointUrl as string) || c.endpointUrl || c.host;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    cfg.accessKey = (extra.accessKey as string) || '';
    cfg.secretKey = (extra.secretKey as string) || '';
    cfg.region = (extra.region as string) || '';
  } else if (c.type === 'oss') {
    cfg.endpointUrl = c.endpointUrl || (extra.endpointUrl as string) || c.host;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    cfg.accessKey = (extra.accessKey as string) || '';
    cfg.secretKey = (extra.secretKey as string) || '';
    cfg.region = (extra.region as string) || '';
  } else if (c.type === 'minio') {
    // PMO-48-T5: MinIO 三阶梯 (listenPort + roleArn 判定 STS)
    cfg.endpointUrl = (extra.endpointUrl as string) || c.endpointUrl || `http://${c.host || 'localhost'}:${c.port || 9000}`;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    const listenPort = Number(extra.listenPort) || c.port || 9000;
    cfg.listenPort = listenPort;
    const roleArn = (extra.roleArn as string) || '';
    if (roleArn.startsWith('arn:aws:iam')) {
      // STS 模式
      cfg.roleArn = roleArn;
    } else {
      // AccessKey 模式
      cfg.accessKey = (extra.accessKey as string) || '';
      cfg.secretKey = (extra.secretKey as string) || '';
    }
    if (extra.pathPrefix) cfg.pathPrefix = String(extra.pathPrefix);
  } else if (c.type === 'csv') {
    cfg.filePath = (extra.filePath as string) || '';
    cfg.delimiter = (extra.delimiter as string) || ',';
    cfg.encoding = (extra.encoding as string) || 'UTF-8';
    cfg.hasHeader = extra.hasHeader === true;
    cfg.rowLimit = (extra.rowLimit as number) || 0;
  } else if (c.type === 'fs') {
    // PMO-48-T5: 本地文件系统
    cfg.rootPath = (extra.rootPath as string) || c.host || '';
    cfg.pattern = (extra.pattern as string) || '';
    cfg.recursive = extra.recursive === true;
  } else if (c.type === 'sftp' || c.type === 'sap') {
    cfg.host = c.host;
    cfg.port = c.port;
    if (extra.key) cfg.key = String(extra.key);
    if (extra.keyDecrypt) cfg.keyDecrypt = String(extra.keyDecrypt);
    if (extra.algo) cfg.algo = String(extra.algo);
    if (c.type === 'sap') {
      cfg.system = (extra.system as string) || '';
      cfg.client = (extra.client as string) || '';
      if (extra.appServer) cfg.appServer = String(extra.appServer);
      if (extra.instance) cfg.instance = String(extra.instance);
      if (extra.sysNum) cfg.sysNum = String(extra.sysNum);
      if (extra.funcModule) cfg.funcModule = String(extra.funcModule);
      if (extra.charset) cfg.charset = String(extra.charset);
    }
  } else if (c.type === 'rest_api') {
    cfg.endpointUrl = c.endpointUrl || c.host;
    if (extra) {
      if (extra.baseURLEnc) cfg.baseUrl = String(extra.baseURLEnc);
      if (extra.apiSubPath) cfg.apiSubPath = String(extra.apiSubPath);
      if (extra.apiMethod) cfg.apiMethod = String(extra.apiMethod);
      if (extra.authType) cfg.authType = String(extra.authType);
      if (extra.apiKey) cfg.apiKey = String(extra.apiKey);
      if (extra.clientCreds) cfg.clientCreds = String(extra.clientCreds);
      if (extra.credEnc) cfg.credEnc = String(extra.credEnc);
      if (extra.usernameEnc) cfg.usernameEnc = String(extra.usernameEnc);
      if (extra.passwordEnc) cfg.passwordEnc = String(extra.passwordEnc);
      if (extra.timeoutMs) cfg.timeoutMs = Number(extra.timeoutMs);
    }
  } else if (c.type === 'kafka') {
    cfg.host = (extra.bootstrapServers as string) || c.host;
    cfg.port = Number(extra.bootstrapPort) || 9092;
    cfg.topic = (extra.topic as string) || '';
    cfg.protocol = (extra.protocol as string) || 'PLAINTEXT';
    if (c.database) cfg.database = c.database;
  } else if (c.type === 'mongodb') {
    const db = c.database || '';
    cfg.jdbcUrl = `mongodb://${c.host || 'localhost'}:${c.port || 27017}/${db}${c.schema ? '/' + c.schema : ''}`;
    cfg.authSource = (extra.authSource as string) || 'admin';
    if (extra.username) {
      cfg.username = String(extra.username);
    }
    if (extra.password) {
      cfg.password = String(extra.password);
    }
  } else {
    // Fallback
    cfg.host = c.host;
    cfg.port = c.port;
    if (c.database) cfg.database = c.database;
    if (c.schema) cfg.schema = c.schema;
  }

  return JSON.stringify(cfg);
}

/** PMO-37: 策略 → metadataConfig JSON string (td_datasource.metadata_config JSONB) */
function toMetadataConfigJson(strategy: { trigger?: string; countMethod?: string; scheduleCron?: string }): string {
  const cfg: Record<string, unknown> = {
    trigger: strategy.trigger || 'MANUAL',
    countMethod: strategy.countMethod || 'OFF',
  };
  if (strategy.scheduleCron) cfg.scheduleCron = strategy.scheduleCron;
  return JSON.stringify(cfg);
}

/** 数据源连接列表（失败向上抛出，由调用方呈现错误态+重试，禁吞错回退空数组） */
export async function fetchDataConnections(): Promise<DataConnection[]> {
  const data = await get<unknown[]>(DATANET_DS);
  if (!Array.isArray(data)) return [];
  return data.map(mapDsToConn);
}

/** 创建数据源 → DataSourceController POST /datanet/datasource */
export async function createDataSource(payload: DataSourceSavePayload): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
      ...(payload.strategy ? { metadataConfig: toMetadataConfigJson(payload.strategy) } : {}),
    };
    const entity = await post<Record<string, unknown>>(DATANET_DS, dto);
    return mapDsToConn(entity);
  } catch (e) {
    console.warn('[data-workbench] createDataSource failed:', e);
    return null;
  }
}

/** 测试数据源连接 → POST /datanet/datasource/{id}/test */
export async function testDataSource(id: string): Promise<{ success: boolean; datasourceId: string } | null> {
  try {
    return await post<{ success: boolean; datasourceId: string }>(`${DATANET_DS}/${id}/test`, {});
  } catch (e) {
    console.warn('[data-workbench] testDataSource failed:', e);
    return null;
  }
}

/** 更新数据源 → PUT /datanet/datasource/{id} */
export async function updateDataSource(id: string, payload: DataSourceSavePayload): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
      ...(payload.strategy ? { metadataConfig: toMetadataConfigJson(payload.strategy) } : {}),
    };
    const entity = await put<Record<string, unknown>>(`${DATANET_DS}/${id}`, dto);
    return mapDsToConn(entity);
  } catch (e) {
    console.warn('[data-workbench] updateDataSource failed:', e);
    return null;
  }
}

/** 删除数据源 → DELETE /datanet/datasource/{id} */
export async function deleteDataSource(id: string): Promise<boolean> {
  try {
    await del<void>(`${DATANET_DS}/${id}`);
    return true;
  } catch (e) {
    console.warn('[data-workbench] deleteDataSource failed:', e);
    return false;
  }
}

/** 测试未保存的数据源连接 → POST /datanet/datasource/test (raw DTO, no id needed) */
export async function testDataSourceRaw(payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; role?: string;
  extra?: Record<string, string | number | boolean>;
}): Promise<{ success: boolean; message?: string } | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
    };
    const result = await post<{ success: boolean; message?: string }>(`${DATANET_DS}/test`, dto);
    return result;
  } catch (e) {
    console.warn('[data-workbench] testDataSourceRaw failed:', e);
    return null;
  }
}

// ────────────────────────────────────────────────────────────
// #142/B4-3e-47（差距 D-B4-76 前端腿）：Schema Preview 属主通道
//
// <p>改前三处同时错：①路径打幻影形态（全仓零承流，见本域基址单源门的退役条目）；
// ②上送「扁平表单字段」这第二份连接配置形态，而属主端点收的是 `DataSourceDTO`
// （`datasourceName`/`datasourceType`/`connectionConfig`）；③只读 `fields`/`tableNames`
// 两键，属主腿的键面是七键。三者合并的后果＝预览恒空、后端 400/503 被读成
// 「0 个字段」的成功面。本批按契约单源 `DataSourceServiceImpl#previewSchemaByConfig`
// 与承流端点 `DataSourceController#previewSchema` 的状态口径接线，请求体复用
// `buildConnectionConfig`（与 `POST /test` 同一拼法，域内不得再有第二份连接配置形态）。</p>
//
// <p>**分态口径（详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」）**：出参是判别联合，
// `ok:false` 带 `kind`（状态行 → 语义类别）与 `status`（原始状态行；未列举状态不猜语义、
// 落 `unknown`）。「通道不可用」「无权限」「成功但对象面为空」三类在页面必须可读地分开——
// 空面只在 `ok:true` 且 `tableNames.length === 0` 且未降级时才成立。</p>
// ────────────────────────────────────────────────────────────

/** 契约列面的一列。底座给不出的属性逐列留 `null`＝未知，禁折成空串/false 冒充「已知无」 */
export interface SchemaField {
  name: string | null;
  type: string | null;
  required: boolean | null;
  comment: string | null;
}

/** 未保存连接的表单字段（预览与 `POST /test` 共用同一份连接配置拼法） */
export interface SchemaPreviewPayload {
  type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; role?: string;
  /** 预览发生在保存之前，名称只作后端侧标识用；缺省走占位名 */
  name?: string;
  extra?: Record<string, string | number | boolean>;
}

/**
 * 失败语义类别（§4.7.2 分态，每类一条独立文案）：
 * `invalid_input`＝400 入参不合法；`denied`＝401/403 安全拒绝；`not_found`＝404 属主侧寻址不到；
 * `unavailable`＝502/503/504 通道或底座不可用；`server_error`＝500 后端自称的未预期失败；
 * `failed_envelope`＝HTTP 200 上的显式失败信封；`network`＝网络层无应答；
 * `invalid_response`＝信封成功但键面违约；`unknown`＝未列举状态行／未知抛出物。
 */
export type SchemaPreviewFailureKind =
  | 'invalid_input'
  | 'denied'
  | 'not_found'
  | 'unavailable'
  | 'server_error'
  | 'failed_envelope'
  | 'network'
  | 'invalid_response'
  | 'unknown';

/** 成功面：七键按契约逐键落位，缺省键取后端语义的空值而非猜值 */
export interface SchemaPreviewSuccess {
  ok: true;
  type: string;
  tableNames: string[];
  fields: SchemaField[];
  /** `fields` 所属对象名；`null`＝列面未取（禁把「哪张表的列」读丢） */
  fieldsObject: string | null;
  /** 后端只在列面腿失败时写该键；缺省＝未降级 */
  fieldsDegraded: boolean;
  fieldsReason: string | null;
  /** 仅对象存储／本地文件占位面携带，明写枚举待实现，与「该源确实没有对象」分态 */
  note: string | null;
}

export interface SchemaPreviewFailure {
  ok: false;
  kind: SchemaPreviewFailureKind;
  /** 后端原文优先（单源 `envelopeErrorText` 腿），取不到才留空、由页面按 `kind` 出文案 */
  error: string;
  status?: number;
}

/**
 * 出参判别联合。与 `SchemaPreviewParseResult` 同一缩窄口径：成功面独有 `tableNames`，
 * 失败面独有 `kind`，消费方用 `in` 判据分支（`ok` 只作运行时可读标记）。
 */
export type SchemaPreviewOutcome = SchemaPreviewSuccess | SchemaPreviewFailure;

/**
 * 解析结果面：`ok:false` 只带开发者可读的违约原因，绝不带半截成功面。
 *
 * <p>**判别方式（本仓 tsconfig 无 `strict`／`strictNullChecks` 关闭 ⇒ `ok: true`/`ok: false`
 * 字面量不参与缩窄，实测 tsc 报 TS2339）**：两侧各自带独有键（`value`／`reason`），
 * 消费方一律用 `in` 判据缩窄。`ok` 仍在运行时可见，供断言与页面分支读，但类型缩窄不靠它。</p>
 */
export type SchemaPreviewParseResult =
  | { ok: true; value: SchemaPreviewSuccess }
  | { ok: false; reason: string };

/**
 * 后端状态行 → 语义类别。只列承流端点可达且语义明确的状态；未列者落 `unknown`。
 * 按「相近」折算（503 折成 403、404 折成空结果）正是本批要防的形态。
 */
const FAILURE_STATUS_KINDS: Readonly<Record<number, SchemaPreviewFailureKind>> = {
  400: 'invalid_input',
  401: 'denied',
  403: 'denied',
  404: 'not_found',
  500: 'server_error',
  502: 'unavailable',
  503: 'unavailable',
  504: 'unavailable',
};

/** 请求体＝`DataSourceDTO` 的三枚属主校验项；类型名走 `toApiType` 单源归一 */
export function buildSchemaPreviewDto(payload: SchemaPreviewPayload): Record<string, string> {
  return {
    datasourceName: (payload.name ?? '').trim() || 'schema-preview',
    datasourceType: toApiType(payload.type),
    connectionConfig: buildConnectionConfig(payload),
  };
}

/**
 * 七键契约解析。必需键 `type`/`tableNames`/`fields`，可选键 `fieldsObject`/`fieldsDegraded`/
 * `fieldsReason`/`note`；任一键形状违约即整单判失败——把违约应答折成空面＝改前的假成功形态。
 */
export function parseSchemaPreviewResponse(raw: unknown): SchemaPreviewParseResult {
  const fail = (reason: string): SchemaPreviewParseResult => ({ ok: false, reason });
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return fail('payload is not an object');
  const r = raw as Record<string, unknown>;
  if (typeof r.type !== 'string') return fail('key "type" is not a string');
  if (!Array.isArray(r.tableNames)) return fail('key "tableNames" is not an array');
  if (r.tableNames.some((v) => typeof v !== 'string')) return fail('tableNames has a non-string item');
  if (!Array.isArray(r.fields)) return fail('key "fields" is not an array');

  const fields: SchemaField[] = [];
  for (const item of r.fields as unknown[]) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return fail('fields has a non-object item');
    const f = item as Record<string, unknown>;
    for (const k of ['name', 'type', 'comment'] as const) {
      const v = f[k];
      if (v !== undefined && v !== null && typeof v !== 'string') return fail(`field.${k} is neither string nor null`);
    }
    const req = f.required;
    if (req !== undefined && req !== null && typeof req !== 'boolean') return fail('field.required is neither boolean nor null');
    fields.push({
      name: (f.name ?? null) as string | null,
      type: (f.type ?? null) as string | null,
      required: (req ?? null) as boolean | null,
      comment: (f.comment ?? null) as string | null,
    });
  }

  const fo = r.fieldsObject;
  if (fo !== undefined && fo !== null && typeof fo !== 'string') return fail('fieldsObject is neither string nor null');
  const fd = r.fieldsDegraded;
  if (fd !== undefined && typeof fd !== 'boolean') return fail('fieldsDegraded is not a boolean');
  const fr = r.fieldsReason;
  if (fr !== undefined && fr !== null && typeof fr !== 'string') return fail('fieldsReason is neither string nor null');
  const nt = r.note;
  if (nt !== undefined && nt !== null && typeof nt !== 'string') return fail('note is neither string nor null');

  return {
    ok: true,
    value: {
      ok: true,
      type: r.type,
      tableNames: r.tableNames as string[],
      fields,
      fieldsObject: (fo ?? null) as string | null,
      fieldsDegraded: fd === true,
      fieldsReason: (fr ?? null) as string | null,
      note: (nt ?? null) as string | null,
    },
  };
}

/**
 * 单源错误 → 分态失败面。四腿互斥且顺序即判据：有状态行 → 查表；无状态行但属「后端有应答」
 * 错误族（`httpStatusText` 非 null）→ HTTP 200 伪失败信封；网络层 → `network`；其余 → `unknown`。
 * 把「后端有应答但没给状态行」与「请求没到达服务端」折成同一态，等于重犯 §4.7.2 的折叠。
 */
export function classifySchemaPreviewError(e: unknown): SchemaPreviewFailure {
  const error = envelopeErrorText(e, '');
  const status = statusOfResponse(e);
  if (typeof status === 'number') {
    const mapped = FAILURE_STATUS_KINDS[status] as SchemaPreviewFailureKind | undefined;
    return { ok: false, kind: mapped ?? 'unknown', error, status };
  }
  if (httpStatusText(e, '') !== null) return { ok: false, kind: 'failed_envelope', error };
  if (isNetworkFailure(e)) return { ok: false, kind: 'network', error };
  return { ok: false, kind: 'unknown', error };
}

/**
 * 属主通道：`POST` 预览端点（族根取 `api/dataClient` 的属主常量）。
 * 一律以 `ok` 判别、不抛错——本腿在「新建连接」弹窗里，失败不得污染保存动作的提示，
 * 但也不得把失败伪装成空面（伪装即 D-B4-76 的原始形态）。
 */
export async function fetchSchemaPreview(payload: SchemaPreviewPayload): Promise<SchemaPreviewOutcome> {
  try {
    const raw = await post<unknown>(`${DATANET_DATASOURCE_BASE}/preview-schema`, buildSchemaPreviewDto(payload));
    const parsed = parseSchemaPreviewResponse(raw);
    // 用 `in` 缩窄而非 `parsed.ok`：本仓 tsconfig 无 strict，布尔字面量判别不参与类型缩窄（实测 TS2339）
    if ('value' in parsed) return parsed.value;
    return { ok: false, kind: 'invalid_response', error: parsed.reason };
  } catch (e) {
    return classifySchemaPreviewError(e);
  }
}

// ────────────────────────────────────────────────────────────
// #142/B4-3e-64（差距 D-B4-78 前端侧腿 ＋ D-B4-93 前置）：数据源类型目录属主通道
//
// <p>改前「可新建哪些数据源」这件事在前端只有一处事实源——`types.ts` 的
// `CONNECTION_TYPES` 硬编码 19 枚，后端承流面（`DataSourceTypeVocabulary.registrable()`
// ＝服务准入面 `SUPPORTED_TYPES`）只有 10 枚（#142/B4-3e-71 起 doris 接承流扩 11 枚），两处零对账。
// 后果即 D-B4-78 的用户面：
// 下拉里能选 `doris/s3/oss/csv/sftp/kafka/mongodb/sap/rest_api` 这 9 枚中 8 枚后端不承流的类型，
// 提交只能撞 400（doris 已在 B4-3e-71 显式接承流）。后端属主出口 `supported-types` 早已存在（且只存在于 F02-01 判定的
// <b>兼容别名面</b> `/api/v1/datasource`，前端零消费）。本批：承流面补同口径出口
// （`DataSourceController#supportedTypes`），前端改消费承流面 ⇒ 下拉与准入恒等。</p>
//
// <p>**分态口径（§4.7.2「不可用 ≠ 无权限 ≠ 空目录」）**：目录读取失败一律以判别联合上抛，
// 页面按 `kind` 出文案并禁用类型选择与创建动作（fail-closed）。
// <b>禁止回落本地 19 枚</b>——回落本地表正是本批收口的「第二事实源」形态。
// 空数组同样判失败：承流面按枚举派生恒非空，读到空目录只能是解析/通道问题。</p>
//
// <p>错误分类单源复用 `classifySchemaPreviewError`（其语义是「状态行 → 语义类别」，
// 与预览腿同一份判据；函数名带 preview 前缀属既有命名债，本批不改名以免波及
// `schemaPreviewContract.spec.ts` 的冻结引用）。</p>
// ────────────────────────────────────────────────────────────

/** 属主目录的一条类型。键面契约 = `DataSourceController#supportedTypes`（恰两键） */
export interface DataSourceTypeCatalogEntry {
  canonical: string;
  aliases: string[];
}

/** 目录读取成功面 */
export interface SupportedTypesSuccess {
  ok: true;
  entries: DataSourceTypeCatalogEntry[];
}

/** 目录读取失败面：`kind` 由后端状态行经单源判据推出，未列举状态不猜语义落 `unknown` */
export interface SupportedTypesFailure {
  ok: false;
  kind: SchemaPreviewFailureKind;
  error: string;
  status?: number;
}

/** 出参判别联合（两侧各带独有键 `entries`／`kind`，消费方用 `in` 缩窄——同 `SchemaPreviewOutcome` 口径） */
export type SupportedTypesOutcome = SupportedTypesSuccess | SupportedTypesFailure;

/** 解析结果面：`ok:false` 只带开发者可读的违约原因，绝不带半截目录 */
export type SupportedTypesParseResult =
  | { ok: true; entries: DataSourceTypeCatalogEntry[] }
  | { ok: false; reason: string };

/**
 * 两键契约解析。任一元素形状违约（非对象／`canonical` 非非空字符串／`aliases` 非字符串数组）
 * 或目录为空 ⇒ 整单判失败。把违约应答折成「少一枚」或「空目录」＝ 让用户面对一个
 * 悄悄缺项的下拉面，与本批要防的假成功同型。
 */
export function parseSupportedTypesResponse(raw: unknown): SupportedTypesParseResult {
  const fail = (reason: string): SupportedTypesParseResult => ({ ok: false, reason });
  if (!Array.isArray(raw)) return fail('payload is not an array');
  if (raw.length === 0) return fail('catalog is empty (承流面按枚举派生恒非空，空目录＝通道或解析故障)');
  const entries: DataSourceTypeCatalogEntry[] = [];
  for (const item of raw as unknown[]) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return fail('item is not an object');
    const r = item as Record<string, unknown>;
    if (typeof r.canonical !== 'string' || r.canonical.trim() === '') {
      return fail('key "canonical" is not a non-blank string');
    }
    if (!Array.isArray(r.aliases)) return fail('key "aliases" is not an array');
    if (r.aliases.some((v) => typeof v !== 'string')) return fail('aliases has a non-string item');
    entries.push({ canonical: r.canonical, aliases: r.aliases as string[] });
  }
  return { ok: true, entries };
}

/**
 * 属主通道：`GET` 类型目录（族根取 `api/dataClient` 的属主常量，与预览腿同一族根）。
 * 一律以判别联合返回、不抛错；失败不回落本地 `CONNECTION_TYPES`。
 */
export async function fetchSupportedDataSourceTypes(): Promise<SupportedTypesOutcome> {
  try {
    const raw = await get<unknown>(`${DATANET_DATASOURCE_BASE}/supported-types`);
    const parsed = parseSupportedTypesResponse(raw);
    if ('entries' in parsed) return { ok: true, entries: parsed.entries };
    return { ok: false, kind: 'invalid_response', error: parsed.reason };
  } catch (e) {
    return classifySchemaPreviewError(e);
  }
}

/**
 * Data Workbench — 数据源连接 CRUD + 连接配置构建 + Schema Preview API
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { get, post, put, del, toApiType, envelopeErrorText, DATANET_DS } from './_base';
import { API_BASE } from '../../../services/httpClient';
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
// PMO-48-T5: Schema Preview API
// 违反契约返回 400/404，前端不伪装成功
// ────────────────────────────────────────────────────────────

export interface SchemaField {
  name: string;
  type: string;
  required: boolean;
  comment?: string;
}

export interface SchemaPreviewPayload {
  type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; extra?: Record<string, string | number | boolean>;
}

/** PMO-48-T5: 前端表单 → preview-schema 请求体 */
export function buildPreviewSchemaPayload(payload: SchemaPreviewPayload): Record<string, unknown> {
  const extra = payload.extra ?? {};
  const body: Record<string, unknown> = {
    type: toApiType(payload.type),
    host: payload.host || 'localhost',
    port: payload.port || 0,
    username: payload.username || '',
    password: payload.password || '',
  };
  if (payload.database) body.database = payload.database;
  if (payload.schema) body.schema = payload.schema;
  if (payload.bucket) body.bucket = payload.bucket;
  if (payload.endpointUrl) body.endpointUrl = payload.endpointUrl;

  // PMO-48-T5: 专属字段透传
  // JDBC 类
  if (payload.type === 'oracle') {
    body.dbType = (extra.dbType as string) || 'SERVICE_NAME';
  }
  // Doris (FE 地址 + HTTP 端口)
  if ((extra as Record<string, unknown>).feHost) body.feHost = (extra as Record<string, unknown>).feHost;
  if ((extra as Record<string, unknown>).feHttpPort) body.feHttpPort = (extra as Record<string, unknown>).feHttpPort;
  // MinIO (STS 三阶梯)
  if (payload.type === 'minio') {
    if (extra.listenPort) body.listenPort = (extra.listenPort as number);
    if (extra.roleArn) body.roleArn = (extra.roleArn as string);
    if (extra.accessKey) body.accessKey = (extra.accessKey as string);
    if (extra.secretKey) body.secretKey = (extra.secretKey as string);
    if (extra.pathPrefix) body.pathPrefix = (extra.pathPrefix as string);
  }
  // 对象存储 (S3/OSS/MinIO 共享)
  if (payload.type === 'minio' || payload.type === 's3') {
    if (extra.accessKey) body.accessKey = (extra.accessKey as string);
    if (extra.secretKey) body.secretKey = (extra.secretKey as string);
    if (extra.region) body.region = (extra.region as string);
  }
  // 文件类
  if (payload.type === 'fs') {
    body.rootPath = (extra.rootPath as string) || '';
    body.pattern = (extra.pattern as string) || '';
    body.recursive = extra.recursive === true;
  }
  if (payload.type === 'csv') {
    body.filePath = (extra.filePath as string) || '';
    body.delimiter = (extra.delimiter as string) || ',';
    body.encoding = (extra.encoding as string) || 'UTF-8';
    body.hasHeader = extra.hasHeader === true;
    body.rowLimit = (extra.rowLimit as number) || 0;
  }
  // SFTP/SAP
  if (payload.type === 'sftp') {
    if (extra.key) body.key = (extra.key as string);
    if (extra.algo) body.algo = (extra.algo as string);
  }
  if (payload.type === 'sap') {
    body.system = (extra.systemId as string) || '';
    body.client = (extra.client as string) || '';
    if (extra.appServer) body.appServer = (extra.appServer as string);
    if (extra.instance) body.instance = (extra.instance as string);
    if (extra.sysNum) body.sysNum = (extra.sysNum as string);
    if (extra.funcModule) body.funcModule = (extra.funcModule as string);
    if (extra.charset) body.charset = (extra.charset as string);
  }
  // REST API
  if (payload.type === 'rest_api') {
    if (extra.apiSubPath) body.apiSubPath = (extra.apiSubPath as string);
    if (extra.apiMethod) body.apiMethod = (extra.apiMethod as string);
    if (extra.apiKey) body.apiKey = (extra.apiKey as string);
    if (extra.clientCreds) body.clientCreds = (extra.clientCreds as string);
    if (extra.timeoutMs) body.timeoutMs = (extra.timeoutMs as number);
  }
  // Kafka
  if (payload.type === 'kafka') {
    body.bootstrapServers = (extra.bootstrapServers as string) || payload.host;
    body.topic = (extra.topic as string) || '';
    body.protocol = (extra.protocol as string) || 'PLAINTEXT';
  }
  // MongoDB
  if (payload.type === 'mongodb') {
    body.authSource = (extra.authSource as string) || 'admin';
    if (extra.username) body.username = (extra.username as string);
  }

  return body;
}

/** PMO-48-T5: 调用 preview-schema 接口
 *  独立 try/catch 隔离，失败不污染 createConnection 的 toast
 *  违反契约 (400/404/500) 返回 { ok: false, error } 而非抛错 */
export async function fetchSchemaPreview(payload: SchemaPreviewPayload): Promise<{ fields: SchemaField[]; tableNames: string[] } | { ok: false; error: string }> {
  const body = buildPreviewSchemaPayload(payload);
  try {
    const raw = await post<{ fields?: unknown; tableNames?: unknown }>(`${API_BASE}/v1/ecos/data/datasource/preview-schema`, body);
    const rawFields = raw?.fields;
    const rawTableNames = raw?.tableNames;
    const fields: SchemaField[] = Array.isArray(rawFields) ? rawFields as SchemaField[] : [];
    const tableNames: string[] = Array.isArray(rawTableNames) ? rawTableNames as string[] : [];
    return { fields, tableNames };
  } catch (e) {
    return { ok: false, error: envelopeErrorText(e, 'network error') };
  }
}

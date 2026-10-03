/**
 * Data Workbench — 元数据/资产·数据湖域（W66 拆分）。
 * Schema Preview / 数据采集(ingest)/数据湖状态 / 非结构化上传 / 文件夹采集。
 * @license Apache-2.0
 */
import { get, post, authHeaders } from './httpClient';
import { toApiType } from './apiCommon';

// ────────────────────────────────────────────────────────────
// PMO-48-T5: Schema Preview API (对齐 PMO45DataSourceController)
// POST /api/v1/ecos/data/datasource/preview-schema
// 输入: { type, host, port, database, username, password, schema?, extra? }
// 输出: { fields: SchemaField[], tableNames: string[] }
// 违反契约返回 400/404，前端不伪装成功
// ────────────────────────────────────────────────────────────

export interface SchemaField {
  name: string;
  type: string;
  required: boolean;
  comment?: string;
}

/** PMO-48-T5: 前端表单 → preview-schema 请求体 */
export function buildPreviewSchemaPayload(payload: {
  type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; extra?: Record<string, string | number | boolean>;
}): Record<string, unknown> {
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
export async function fetchSchemaPreview(payload: {
  type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; extra?: Record<string, string | number | boolean>;
}): Promise<{ fields: SchemaField[]; tableNames: string[] } | { ok: false; error: string }> {
  const body = buildPreviewSchemaPayload(payload);
  try {
    const res = await fetch('/api/v1/ecos/data/datasource/preview-schema', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      let msg = `HTTP ${res.status}`;
      try {
        const errJson = await res.json();
        msg = errJson.message || errJson.error || msg;
      } catch { /* ignore parse error */ }
      return { ok: false, error: msg };
    }
    const json = await res.json();
    const data = json.data ?? json;
    const fields: SchemaField[] = Array.isArray(data.fields) ? data.fields : [];
    const tableNames: string[] = Array.isArray(data.tableNames) ? data.tableNames : [];
    return { fields, tableNames };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : 'network error' };
  }
}

// ────────────────────────────────────────────────────────────
// 数据采集 API（采集型管道 SOURCE_JDBC → SINK_MINIO，MinIO 近源库）
// ────────────────────────────────────────────────────────────

export interface IngestTarget {
  datasourceId: string;
  name: string;
  type: string;
}

export interface IngestTaskItem {
  table: string;
  definitionId: string;
  taskId: string;
  status: string;
}

/** 可用目标数据湖（MINIO 类型数据源） */
export async function fetchIngestTargets(): Promise<IngestTarget[]> {
  try {
    const data = await get<unknown[] | Record<string, unknown>>('/api/v1/datanet/ingest/targets');
    const arr = Array.isArray(data) ? data : (data?.items as unknown[] | undefined) ?? [];
    return arr.map((t: unknown) => {
      const m = t as Record<string, unknown>;
      return {
        datasourceId: (m.datasourceId as string) || '',
        name: (m.name as string) || (m.datasourceName as string) || '',
        type: (m.type as string) || '',
      };
    });
  } catch (e) {
    console.warn('[data-workbench] fetchIngestTargets failed:', e);
    return [];
  }
}

/** 即时采集：提交采集型管道到 runtime-task，返回各表 taskId */
export async function runDataIngest(datasourceId: string, tableNames: string[]): Promise<{
  datasourceId: string; submitted: boolean; taskCount: number; tasks: IngestTaskItem[];
} | null> {
  try {
    const body = { datasourceId, tableNames };
    const data = await post<Record<string, unknown>>('/api/v1/datanet/ingest/run', body);
    return {
      datasourceId: (data?.datasourceId as string) || datasourceId,
      submitted: Boolean(data?.submitted),
      taskCount: Number(data?.taskCount) || 0,
      tasks: Array.isArray(data?.tasks)
        ? (data.tasks as unknown[]).map((t: unknown) => {
            const m = t as Record<string, unknown>;
            return {
              table: (m.table as string) || '',
              definitionId: (m.definitionId as string) || '',
              taskId: (m.taskId as string) || '',
              status: (m.status as string) || 'SUBMITTED',
            };
          })
        : [],
    };
  } catch (e) {
    console.warn('[data-workbench] runDataIngest failed:', e);
    return null;
  }
}

/** 查询采集任务状态（前端轮询进度） */
export async function fetchIngestStatus(taskId: string): Promise<{
  available: boolean; status?: string; progress?: number;
  result?: unknown; errorMessage?: string;
} | null> {
  try {
    return await get<{ available: boolean; status?: string; progress?: number; result?: unknown; errorMessage?: string }>(
      `/api/v1/datanet/ingest/status/${encodeURIComponent(taskId)}`
    );
  } catch (e) {
    console.warn('[data-workbench] fetchIngestStatus failed:', e);
    return null;
  }
}

/** 保存定时采集策略（cron 为空 = 停用） */
export async function saveIngestSchedule(
  datasourceId: string, tableNames: string[], cron: string
): Promise<{ enabled: boolean; cron: string; tables: string[] } | null> {
  try {
    const body = { datasourceId, tableNames, cron };
    return await post<{ enabled: boolean; cron: string; tables: string[] }>('/api/v1/datanet/ingest/schedule', body);
  } catch (e) {
    console.warn('[data-workbench] saveIngestSchedule failed:', e);
    return null;
  }
}

/** 查询定时采集策略 */
export async function fetchIngestSchedule(datasourceId: string): Promise<{
  enabled: boolean; cron: string; tables: string[];
} | null> {
  try {
    return await get<{ enabled: boolean; cron: string; tables: string[] }>(
      `/api/v1/datanet/ingest/schedule/${encodeURIComponent(datasourceId)}`
    );
  } catch (e) {
    console.warn('[data-workbench] fetchIngestSchedule failed:', e);
    return null;
  }
}

/** 数据湖（MinIO 近源库）健康状态 */
export async function fetchDatalakeStatus(): Promise<{
  endpoint?: string; bucket?: string; status?: string; initialized?: boolean; accessKey?: string;
} | null> {
  try {
    return await get<{ endpoint?: string; bucket?: string; status?: string; initialized?: boolean; accessKey?: string }>(
      '/api/v1/datanet/datalake/status'
    );
  } catch (e) {
    console.warn('[data-workbench] fetchDatalakeStatus failed:', e);
    return null;
  }
}

/** 初始化数据湖（ensure bucket，幂等） */
export async function initDatalake(): Promise<{ bucket?: string; status?: string } | null> {
  try {
    return await post<{ bucket?: string; status?: string }>('/api/v1/datanet/datalake/init', {});
  } catch (e) {
    console.warn('[data-workbench] initDatalake failed:', e);
    return null;
  }
}

// ────────────────────────────────────────────────────────────
// 非结构化近源层上传（数据侧前端打通 step ②③）
// POST /api/v1/datanet/datalake/unstructured/upload
// multipart/form-data: file + JSON metadata
// 对象 key = raw/unstructured/{source}/{docId}/{originalFileName}
// 后端复用 DataLakeResourceService.registerUnstructured 登记 RAW/UNSTRUCTURED/LAKE_OBJECT
// ────────────────────────────────────────────────────────────

/** 上传元数据（source/docId 必填；originalFileName 可选，缺省取 file.name） */
export interface UnstructuredUploadOptions {
  source: string;
  docId: string;
  originalFileName?: string;
  resourceName?: string;
  datasourceId?: string;
}

export interface UnstructuredUploadResult {
  code: number;
  message?: string;
  data?: {
    resourceId?: string;
    resourceName?: string;
    resourceType?: string;
    datasourceId?: string;
    sourcePath?: string;
    status?: string;
    layer?: string;
    zone?: string;
    created?: boolean;
  };
}

/** 上传非结构化文件到 MinIO 近源层并登记。multipart 不带 Content-Type（浏览器自动加 boundary） */
export async function uploadUnstructured(
  file: File,
  meta: UnstructuredUploadOptions
): Promise<UnstructuredUploadResult | { ok: false; error: string }> {
  const fd = new FormData();
  fd.append('file', file);
  fd.append(
    'req',
    new Blob(
      [
        JSON.stringify({
          source: meta.source,
          docId: meta.docId,
          originalFileName: meta.originalFileName ?? undefined,
          resourceName: meta.resourceName ?? undefined,
          datasourceId: meta.datasourceId ?? undefined,
        }),
      ],
      { type: 'application/json' }
    )
  );
  fd.set('req', fd.get('req') as Blob, 'req.json');
  try {
    const res = await fetch('/api/v1/datanet/datalake/unstructured/upload', {
      method: 'POST',
      headers: { ...authHeaders() },
      body: fd,
    });
    const json = await res.json().catch(() => ({ code: res.status, message: `HTTP ${res.status}` }));
    if (!res.ok) {
      return { ok: false as const, error: json?.message || `HTTP ${res.status}` };
    }
    return {
      code: Number(json?.code ?? -1),
      message: json?.message,
      data: json?.data,
    };
  } catch (e) {
    const msg = e instanceof Error ? e.message : 'network error';
    console.warn('[data-workbench] uploadUnstructured failed:', e);
    return { ok: false as const, error: msg };
  }
}

// ────────────────────────────────────────────────────────────
// 非结构化近源层 —— 文件夹数据源模式（不传二进制，服务端读取）
// GET  /api/v1/datanet/datalake/unstructured/folder/files?datasourceId=&docId=
// POST /api/v1/datanet/datalake/unstructured/collect
// 对象 key = raw/unstructured/{datasourceId}/{docId}/{fileName}
// ────────────────────────────────────────────────────────────

/** 文件夹数据源文件列表项（不暴露服务端绝对路径） */
export interface FolderFileVo {
  name: string;
  size?: number;
  lastModified?: string;
}

/** 文件夹采集入参：datasourceId 指向 FILESYSTEM 数据源（路径由其 connectionConfig 持有） */
export interface FolderCollectReq {
  datasourceId: string;
  docId: string;
  fileNames: string[];
}

/** 文件夹采集结果：逐文件明细 + 成功/失败计数 */
export interface FolderCollectResult {
  collected: number;
  failed: number;
  items: { name: string; status: string; error?: string }[];
}

/** 列出 FILESYSTEM 数据源根目录下的可采集文件（扩展名白名单过滤，docId 可选前缀过滤） */
export async function listFolderFiles(datasourceId: string, docId?: string): Promise<FolderFileVo[]> {
  try {
    const url = `/api/v1/datanet/datalake/unstructured/folder/files?datasourceId=${encodeURIComponent(datasourceId)}${docId ? `&docId=${encodeURIComponent(docId)}` : ''}`;
    const raw = await get<unknown[]>(url);
    if (!Array.isArray(raw)) return [];
    return (raw as Record<string, unknown>[])
      .map((f) => ({
        name: String(f.name || ''),
        size: typeof f.size === 'number' ? (f.size as number) : undefined,
        lastModified: (f.lastModified as string) || undefined,
      }))
      .filter((f) => f.name);
  } catch (e) {
    console.warn('[data-workbench] listFolderFiles failed:', e);
    throw e;
  }
}

/** 将 FILESYSTEM 数据源目录中的文件采集到近源层并登记（后端逐文件处理） */
export async function collectFolderFiles(req: FolderCollectReq): Promise<FolderCollectResult> {
  const raw = await post<Record<string, unknown> | null>('/api/v1/datanet/datalake/unstructured/collect', req);
  if (!raw) {
    return { collected: 0, failed: req.fileNames.length, items: [] };
  }
  return {
    collected: Number(raw.collected) || 0,
    failed: Number(raw.failed) || 0,
    items: Array.isArray(raw.items)
      ? (raw.items as Record<string, unknown>[]).map((i) => ({
          name: String(i.name || ''),
          status: String(i.status || ''),
          error: i.error ? String(i.error) : undefined,
        }))
      : [],
  };
}

/**
 * Data Workbench — 数据采集 (采集型管道 SOURCE_JDBC → SINK_MINIO) + 数据湖 + 非结构化近源层
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { get, post } from './_base';
// #143/B5-14：上传腿合流到 httpClient 裸响应通道（multipart 需未预读体 + 只要身份头）。
// `authOnlyHeaders()` 与 `rawAuthedUrlFetch` 的基头集合逐键相同（有 token 时仅
// `Authorization`，无 token 时为空对象），且该通道不写 `Content-Type` —— 浏览器必须
// 自己带 `multipart/form-data; boundary=…`，单源若补 Content-Type 会让后端丢 boundary。
import { API_BASE, rawAuthedUrlFetch } from '../../../services/httpClient';

/* ── 即时采集 / 定时采集 ──────────────────────────────────── */

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

/* ── 数据湖（MinIO 近源库）───────────────────────────────── */

/** 数据湖健康状态 */
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

/* ── 非结构化近源层上传（浏览器二进制）────────────────────── */

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
    const res = await rawAuthedUrlFetch(`${API_BASE}/v1/datanet/datalake/unstructured/upload`, {
      method: 'POST',
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

/* ── 非结构化近源层 —— 文件夹数据源模式 ───────────────────── */

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

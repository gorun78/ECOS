/**
 * 上下文来源版本解析单源（场景上下文构建用）。
 *
 * <p>为什么需要它：后端 `ContextBuilderService.validateRequest` 对
 * `requiredAssets[].version` 强制非空（缺则 400 ECOS-COG-400S），且各 reader
 * 明确<b>禁止</b>用请求声明的版本回填（回填等于自证一致）。所以前端必须从
 * 源侧读回真实版本后随请求上送。</p>
 *
 * <p>三条腿与后端 reader 的读取路径逐字对齐：
 * <ul>
 *   <li>DATA_ASSET → {@code /v1/datanet/assets/{id}}（DataEngineContextSourceReader）</li>
 *   <li>ONTOLOGY   → {@code /v1/ecos/ontologies/entities/{id}}（KnowledgeOntologyContextSourceReader）</li>
 *   <li>KNOWLEDGE  → {@code /v1/knowledge/articles/{id}}（同上）</li>
 * </ul>
 * 注意 ONTOLOGY 必须用实体 <b>id</b>（ent003）而非 code（ENT_CASE）：传 code 实测 404。</p>
 *
 * <p>URL 站点全部集中在本文件（单一生产方），页面层只调函数不拼地址——
 * 满足「同一 URL 族只允许一个生产方」的双形态棘轮约束。</p>
 */
import { apiFetchData } from '../api';
import { API_BASE } from './httpClient';
// DATA_ASSET 族根已有单源属主（api/dataClient.ts 的 DATANET_ASSETS_BASE），本文件**引而不造**，
// 避免形成第二生产方（URL 双形态棘轮：同族只允许一个声明位）。
import { DATANET_ASSETS_BASE } from '../api/dataClient';

/** ONTOLOGY / KNOWLEDGE / KNOWLEDGE_BASE 三族的基址（本文件是这三个族的唯一生产方）。 */
const ONTOLOGY_ENTITIES = `${API_BASE}/v1/ecos/ontologies/entities`;
const KNOWLEDGE_ARTICLES = `${API_BASE}/v1/knowledge/articles`;
const KNOWLEDGE_BASES = `${API_BASE}/v1/knowledge-bases`;

/** 版本字段取值顺序（与后端 reader 的 firstText 候选表同源）。 */
function pickVersion(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const candidates = [
    d.version, d.versionNo, d.version_no, d.assetVersion, d.asset_version,
    d.dataVersion, d.data_version, d.schemaVersion, d.schema_version,
    d.updatedAt, d.updated_at, d.updateTime, d.update_time,
  ];
  for (const c of candidates) {
    if (typeof c === 'string' && c.trim() !== '') return c.trim();
    if (typeof c === 'number' && Number.isFinite(c)) return String(c);
  }
  return null;
}

/**
 * 解析单个来源的真实版本。
 * @returns 版本字符串；来源不可读或无版本字段时返回 null（调用方须如实标注不可读）。
 */
export async function resolveSourceVersion(sourceType: string, sourceId: string): Promise<string | null> {
  const enc = encodeURIComponent(sourceId);
  try {
    switch (sourceType) {
      case 'DATA_ASSET':
        return pickVersion(await apiFetchData<unknown>(`${DATANET_ASSETS_BASE}/${enc}`));
      case 'ONTOLOGY':
        return pickVersion(await apiFetchData<unknown>(`${ONTOLOGY_ENTITIES}/${enc}`));
      case 'KNOWLEDGE':
        return pickVersion(await apiFetchData<unknown>(`${KNOWLEDGE_ARTICLES}/${enc}`));
      case 'KNOWLEDGE_BASE':
        // 整个知识库：取库内最新文档更新时间作为作用域版本（与后端 reader 同口径）
        return pickVersion(await apiFetchData<unknown>(`${KNOWLEDGE_BASES}/${enc}`));
      default:
        // COGNITIVE / CAPABILITY 等其它 reader 腿本批不预填（见 ScenarioContextBuilder 口径）
        return null;
    }
  } catch {
    return null;
  }
}

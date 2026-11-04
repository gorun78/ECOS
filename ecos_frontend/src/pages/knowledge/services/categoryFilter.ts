/**
 * categoryFilter.ts — 知识资产七分类筛选读端契约（#147U / F-KB-18）。
 *
 * 后端：`GET /api/v1/knowledge/articles/by-category?category=<code>&limit=<n>`
 *   - 分类**必填**、大小写不敏感、闭集 Fact/Concept/Procedure/Rule/Case/Evidence/Pattern；
 *     未知值 400（后端禁「非法值回落全量」）。
 *   - 显式 `by-category` 子路径：同一控制器也映射 `/assets`，裸根映射会劫持生命周期资产列表。
 *
 * 错误态语义（F04-03）：走 `kbFetchData` → 5xx / `ECOS-KB-*` 抛 `KbApiError`，
 * 组件据此显式错误码展示，不把失败渲染成「暂无数据」。
 */
import { kbFetchData } from './kbFetch';
import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';

/** 七分类闭集（与后端 `KnowledgeCategory` 的 code 逐值一致；禁前端自造第八类）。 */
export const KNOWLEDGE_CATEGORIES = [
  'Fact',
  'Concept',
  'Procedure',
  'Rule',
  'Case',
  'Evidence',
  'Pattern',
] as const;

export type KnowledgeCategoryCode = (typeof KNOWLEDGE_CATEGORIES)[number];

/** 列表行（只取筛选面要展示的字段；后端多余字段忽略）。 */
export interface KnowledgeArticleRow {
  id: string;
  title: string;
  knowledgeCategory?: string | null;
  status?: string | null;
}

const DEFAULT_LIMIT = 20;

/** 读端路径拼装（未选分类直接返回 null：后端分类必填，前端不得发无分类请求）。 */
export function categoryFilterPath(category: string | null | undefined, limit = DEFAULT_LIMIT): string | null {
  if (!category || !category.trim()) {
    return null;
  }
  const bounded = Math.max(1, Math.min(Math.trunc(limit) || DEFAULT_LIMIT, 100));
  return `${KNOWLEDGE_BASE}/articles/by-category?category=${encodeURIComponent(category.trim())}&limit=${bounded}`;
}

function toRow(raw: Record<string, unknown>): KnowledgeArticleRow {
  return {
    id: String(raw.id ?? ''),
    title: String(raw.title ?? raw.id ?? ''),
    knowledgeCategory: (raw.knowledgeCategory ?? raw.knowledge_category ?? null) as string | null,
    status: (raw.status ?? null) as string | null,
  };
}

/**
 * 按分类拉取知识资产。
 *
 * @throws KbApiError 4xx/5xx（含未知分类 400）——调用方须切错误态而非空态
 */
export async function fetchArticlesByCategory(
  category: string,
  limit = DEFAULT_LIMIT,
): Promise<KnowledgeArticleRow[]> {
  const path = categoryFilterPath(category, limit);
  if (!path) {
    return [];
  }
  const data = await kbFetchData<unknown>(path);
  const items = Array.isArray(data) ? data : (data as { data?: unknown })?.data;
  return Array.isArray(items) ? items.map((r) => toRow(r as Record<string, unknown>)) : [];
}

// ═══════════════ 游标分页（#147BA 后端 / #147BG 前端消费） ═══════════════

/** 游标分页结果（与后端 `{items, nextCursor}` 逐字对齐；游标不透明，前端只回传不解析）。 */
export interface KnowledgeArticlePage {
  items: KnowledgeArticleRow[];
  nextCursor: string | null;
}

/**
 * 分页读端路径（`/by-category/page`）。
 *
 * <p>与 {@link categoryFilterPath} 并列而非替换：旧端点仍在用，分页只在需要「继续加载」的面生效。
 * 空分类返回 null（后端分类必填，前端不得发无分类请求）；`limit` 夹紧 [1,100]；
 * 游标为空白时**不带** `cursor` 参数（首屏语义）。</p>
 */
export function categoryPagePath(
  category: string | null | undefined,
  limit = DEFAULT_LIMIT,
  cursor?: string | null,
): string | null {
  if (!category || !category.trim()) {
    return null;
  }
  const bounded = Math.max(1, Math.min(Math.trunc(limit) || DEFAULT_LIMIT, 100));
  const cursorPart = cursor && cursor.trim() ? `&cursor=${encodeURIComponent(cursor.trim())}` : '';
  return `${KNOWLEDGE_BASE}/articles/by-category/page?category=${encodeURIComponent(category.trim())}&limit=${bounded}${cursorPart}`;
}

function extractPage(data: unknown): { items?: unknown; nextCursor?: unknown } {
  if (data && typeof data === 'object') {
    const direct = data as { items?: unknown; nextCursor?: unknown };
    if (Array.isArray(direct.items)) {
      return direct;
    }
    const wrapped = (data as { data?: { items?: unknown; nextCursor?: unknown } }).data;
    if (wrapped && typeof wrapped === 'object') {
      return wrapped;
    }
  }
  return {};
}

/**
 * 按分类拉取一页（游标分页）。
 *
 * @param cursor 上一页返回的 `nextCursor`；首屏传 null/undefined
 * @throws KbApiError 4xx/5xx（含非法游标 400、未知分类 400）——调用方须切错误态而非空态
 */
export async function fetchArticlesByCategoryPage(
  category: string,
  limit = DEFAULT_LIMIT,
  cursor?: string | null,
): Promise<KnowledgeArticlePage> {
  const path = categoryPagePath(category, limit, cursor);
  if (!path) {
    return { items: [], nextCursor: null };
  }
  const data = await kbFetchData<unknown>(path);
  const page = extractPage(data);
  const items = Array.isArray(page.items)
    ? page.items.map((r) => toRow(r as Record<string, unknown>))
    : [];
  const rawNext = page.nextCursor == null ? '' : String(page.nextCursor).trim();
  return { items, nextCursor: rawNext ? rawNext : null };
}

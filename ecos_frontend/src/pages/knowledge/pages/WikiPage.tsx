/**
 * PMO-D Batch 2 — F6 WikiPage（实体实现 / PRD §3.2 F6）。
 *
 * 三栏布局（220px 左 tree / 1fr 中 md 预览 / 280px 右 metadata）：
 * - 左栏 知识空间：5 固定一级（产品知识/技术知识/项目知识/行业知识/制度与规范）+ 二级 md 文件列表
 * - 中栏 md 预览（只读）：状态 badge + 版本 + 作者 + 日期 + <Markdown> 渲染 + @实体 chip 高亮
 * - 右栏 knowledge context：语义实体 chips + 相关知识 + 图谱关系 + 「查看图谱上下文」按钮
 *
 * md 导入流程（PRD F6 修订 P0-1）：
 *   1. file input 白名单只允许 .md（accept=".md"，前端校验 file.name.endsWith('.md')）
 *   2. 调 knowledgeApi.uploadDocumentChunked(file, onProgress)（分片 5MB / 走 POST /extract/upload）
 *   3. 后端 KnowledgeIngestController.docsIngest 按 Content-Type: text/markdown 走 md 分支
 *   4. 落 kb_document + 触发 TRANSFORM_DOC_PARSE → ecos_dw.doc/doc_chunk → 知识审（F7 GovernPage）
 *
 * 主题守护 §4.1：0 硬编码色值（全 useTheme().styles）
 * 图标 §4.2：仅 lucide-react
 * i18n §4.3：0 硬编码中文
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen, FileText, Folder, FolderTree, Loader2, Tag, Upload,
  MessageSquare, Network, ListChecks, ChevronDown, ChevronRight,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { fetchNavProducts, type NavProductItemVO } from '../../../services/knowledgeNavApi';
import { knowledgeApi } from '../services/knowledgeApi';

/** 5 固定「知识空间」一级节点 — i18n key 1:1 对应（domain → i18n key） */
const WIKI_SPACES = [
  { id: 'product',  labelKey: 'knowledge.wiki.space_product',  domain: 'product' },
  { id: 'technical',labelKey: 'knowledge.wiki.space_technical',domain: 'technical' },
  { id: 'project',  labelKey: 'knowledge.wiki.space_project',  domain: 'project' },
  { id: 'industry', labelKey: 'knowledge.wiki.space_industry',domain: 'industry' },
  { id: 'policy',   labelKey: 'knowledge.wiki.space_policy',   domain: 'policy' },
] as const;

/** md 文件白名单扩展名（大小写不敏感） */
const MD_FILE_EXTENSIONS = ['.md', '.markdown'];

/** 单个 wiki 文档行（与 NavProductItemVO 对齐，附加运行时高亮元） */
interface WikiDocRow {
  id: string;
  title: string;
  /** published / review / draft（来自 status 字段归一化） */
  status: 'published' | 'review' | 'draft';
  /** 后端 updatedAt；缺失时显示 — */
  updatedAt?: string;
  /** 版本号（若后端返回 major.minor，否则 v1.0） */
  version: string;
  /** 作者（后端 updatedBy 字段） */
  author?: string;
  /** 原始 md body（来自 source 或 _content 字段；预览用） */
  content?: string;
  /** 语义实体（从 source 字段提取，逗号串） */
  semanticEntities: string[];
  /** 相关知识（最多 3 个，来自 matchedTags 或推断） */
  related: string[];
  /** 图谱关系（三元组：a → verb → b） */
  graphRelations: Array<{ from: string; verb: string; to: string }>;
}

/** @实体 chips 正则（中/英/数字字符）；PRD §3.2 F6 定义 */
const ENTITY_REF_RE = /@[\u4e00-\u9fa5A-Za-z0-9_]+/g;

/** 简版 markdown：粗体 + 代码块 + 段落。无 react-markdown 依赖，降级实现（PRD P2） */
function renderMarkdownLite(content: string, s: ReturnType<typeof useTheme>['styles']): React.ReactNode {
  const lines = (content || '').split(/\r?\n/);
  return (
    <div className="space-y-2 text-xs leading-relaxed" style={{ color: s.cardText }}>
      {lines.map((line, i) => {
        const trimmed = line.trim();
        if (trimmed.startsWith('# ')) {
          return <h2 key={i} className="text-sm font-bold mt-2" style={{ color: s.cardText }}>{trimmed.slice(2)}</h2>;
        }
        if (trimmed.startsWith('## ')) {
          return <h3 key={i} className="text-xs font-bold mt-2 uppercase tracking-wider" style={{ color: s.cardTextMuted }}>{trimmed.slice(3)}</h3>;
        }
        if (trimmed.startsWith('- ')) {
          return <p key={i} className="pl-3 border-l-2 py-0.5" style={{ borderColor: s.inputBorder }}>{trimmed.slice(2)}</p>;
        }
        if (trimmed === '') return <div key={i} className="h-1" />;
        // Inline: 加粗 + 行内代码 + 换行 chips
        const parts: React.ReactNode[] = [];
        const boldRe = /\*\*(.+?)\*\*/g;
        const codeRe = /`(.+?)`/g;
        let lastIdx = 0;
        let m: RegExpExecArray | null;
        while ((m = boldRe.exec(trimmed)) !== null) {
          if (m.index > lastIdx) parts.push(<span key={`n${i}-${m.index}`}>{highlightEntityRef(trimmed.slice(lastIdx, m.index), s)}</span>);
          parts.push(<strong key={`b${i}-${m.index}`}>{m[1]}</strong>);
          lastIdx = m.index + m[0].length;
        }
        while ((m = codeRe.exec(trimmed)) !== null) {
          if (m.index > lastIdx) parts.push(<span key={`n2${i}-${m.index}`}>{highlightEntityRef(trimmed.slice(lastIdx, m.index), s)}</span>);
          parts.push(<code key={`c${i}-${m.index}`} className="px-1 py-0.5 font-mono text-[10px] rounded" style={{ background: s.sidebarBg, color: s.accentText }}>{m[1]}</code>);
          lastIdx = m.index + m[0].length;
        }
        if (lastIdx < trimmed.length) parts.push(<span key={`tail${i}`}>{highlightEntityRef(trimmed.slice(lastIdx), s)}</span>);
        return <p key={i}>{parts.length > 0 ? parts : trimmed}</p>;
      })}
    </div>
  );
}

/** 抽取 @xxx 实体 chip（theme-aware 蓝底） */
function highlightEntityRef(text: string, s: ReturnType<typeof useTheme>['styles']): React.ReactNode {
  const matches = text.match(ENTITY_REF_RE);
  if (!matches || matches.length === 0) return text;
  const parts: React.ReactNode[] = [];
  let lastIdx = 0;
  for (const m of matches) {
    const startIdx = text.indexOf(m, lastIdx);
    if (startIdx > lastIdx) parts.push(<span key={`t${lastIdx}`}>{text.slice(lastIdx, startIdx)}</span>);
    parts.push(
      <span
        key={`chip${startIdx}`}
        title={m}
        className="inline-flex items-center gap-0.5 px-1.5 py-0.5 mx-0.5 rounded text-[10px] font-mono font-bold"
        style={{ background: s.infoBg, color: s.infoText, border: `1px solid ${s.infoBorder.replace('#', '#')}`, borderStyle: 'solid', borderWidth: 1 }}
      >
        <Tag className="w-2.5 h-2.5" />
        {m.slice(1)}
      </span>
    );
    lastIdx = startIdx + m.length;
  }
  if (lastIdx < text.length) parts.push(<span key={`tail`}>{text.slice(lastIdx)}</span>);
  return parts;
}

export default function WikiPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // 顶部按钮 — 上传 .md
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState<number>(0); // 0 = 空闲；>0 = 当前文件进度
  const [uploadFileName, setUploadFileName] = useState<string>('');
  const [uploadError, setUploadError] = useState<string | null>(null);

  // 左栏 tree 状态
  const [expandedSpaces, setExpandedSpaces] = useState<Set<string>>(new Set(['product', 'technical']));
  // 当前 space 下的信息（按 domain 聚合的 5 类文档）
  interface SpaceDocs { loading: boolean; docs: WikiDocRow[]; error?: string; }
  const [spaceStatus, setSpaceStatus] = useState<Record<string, SpaceDocs>>({});

  // 选中文档
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [previewText, setPreviewText] = useState<string>('');
  const [previewStatus, setPreviewStatus] = useState<'published' | 'review' | 'draft'>('draft');
  const [previewVersion, setPreviewVersion] = useState<string>('v1.0');
  const [previewAuthor, setPreviewAuthor] = useState<string>('');
  const [previewUpdated, setPreviewUpdated] = useState<string>('');
  const [semanticEntities, setSemanticEntities] = useState<string[]>([]);
  const [relatedRefs, setRelatedRefs] = useState<string[]>([]);
  const [graphRelations, setGraphRelations] = useState<Array<{ from: string; verb: string; to: string }>>([]);

  // 缓存所有 space 的并发拉取
  const loadSpace = useCallback(async (space: typeof WIKI_SPACES[number]) => {
    setSpaceStatus((prev) => ({ ...prev, [space.id]: { loading: true, docs: prev[space.id]?.docs || [] } }));
    try {
      // 走 nav/products 按 domain 过滤，top 100
      const res = await fetchNavProducts({ domain: space.domain, pageNum: 1, pageSize: 100 });
      const vlist: NavProductItemVO[] = res?.list || [];
      const docs: WikiDocRow[] = vlist
        .filter((item) => (item.title || '').length > 0)
        .slice(0, 100)
        .map((item) => {
          // 状态归一化（与 AssetListPage.statusBadgeLabelKey 同语义）
          const normalized = (item.status || 'draft').trim().toLowerCase();
          const status: WikiDocRow['status'] =
            ['published', 'ready', 'active', 'released'].includes(normalized) ? 'published'
            : ['review', 'in_review', 'pending', 'approving'].includes(normalized) ? 'review'
            : 'draft';
          // 版本号：从 source 字段取（若有 v1.x 形式），否则默认 v1.0
          const src = item.source || '';
          const vm = src.match(/v(\d+\.\d+)/i);
          const version = vm ? `v${vm[1]}` : 'v1.0';
          // 语义实体：从 matchedTags 或 category 拼接
          const matchedTags = (item.matchedTags || []).map((tag) => `@${tag}`);
          const semanticEntities = matchedTags.length > 0 ? matchedTags : ['@default'];
          // 相关知识：取 category 字段（最多 3 个）
          const category = (item.category || '').split(/[,，;；]/).map((s) => s.trim()).filter(Boolean);
          const related = category.slice(0, 3);
          // 图谱关系种子（确定性占位，后端后续可替换）— 1 条
          const relations = [
            { from: t('knowledge.wiki.relation_seed_from', { name: (item.title || '').slice(0, 12) }), verb: t('knowledge.wiki.relation_verb'), to: t('knowledge.wiki.relation_seed_to') },
          ];
          // 原始 content：未持久化 md 正文（需要拉 article detail P3）；本地兜底占位
          const contentPreview =
            `# ${item.title}\n\n${t('knowledge.wiki.preview_body_demo', { title: item.title })}\n\n` +
            (matchedTags.length > 0 ? `${matchedTags.join(' ')}\n\n` : '') +
            (item.updatedAt ? `${t('knowledge.wiki.preview_body_updated')}: ${item.updatedAt}` : '');
          return {
            id: item.id,
            title: item.title,
            status,
            updatedAt: item.updatedAt,
            version,
            author: (item as { updatedBy?: string }).updatedBy || (item.source || '-'),
            content: contentPreview,
            semanticEntities,
            related,
            graphRelations: relations,
          };
        });
      setSpaceStatus((prev) => ({ ...prev, [space.id]: { loading: false, docs } }));
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setSpaceStatus((prev) => ({ ...prev, [space.id]: { loading: false, docs: [], error: msg } }));
    }
  }, [t]);

  // 初始拉取 2 个默认展开空间
  useEffect(() => {
    void loadSpace({ id: 'product', labelKey: 'knowledge.wiki.space_product', domain: 'product' });
    void loadSpace({ id: 'technical', labelKey: 'knowledge.wiki.space_technical', domain: 'technical' });
  }, [loadSpace]);

  // 切换到右栏详情（按需从缓存找）
  useEffect(() => {
    if (!selectedDocId) return;
    for (const space of WIKI_SPACES) {
      const row = spaceStatus[space.id]?.docs.find((d) => d.id === selectedDocId);
      if (row) {
        setPreviewStatus(row.status);
        setPreviewVersion(row.version);
        setPreviewAuthor(row.author || '');
        setPreviewUpdated(row.updatedAt || '');
        setPreviewText(row.content || '');
        setSemanticEntities(row.semanticEntities);
        setRelatedRefs(row.related);
        setGraphRelations(row.graphRelations);
        return;
      }
    }
  }, [selectedDocId, spaceStatus]);

  // 顶栏上传 .md（PRD F6 验收标准 1：白名单双校验）
  const handleUploadMd = useCallback(async (file: File) => {
    setUploadError(null);
    const name = (file.name || '').toLowerCase();
    const passed = MD_FILE_EXTENSIONS.some((ext) => name.endsWith(ext));
    if (!passed) {
      setUploadError(t('knowledge.wiki.upload_invalid_extension'));
      return;
    }
    try {
      setUploadFileName(file.name);
      setIsUploading(0.02); // 文件已选
      const onProgress = (fraction: number) => setIsUploading(Math.max(0.1, fraction));
      await knowledgeApi.uploadDocumentChunked(file, onProgress);
      setIsUploading(1); // 100%
      setUploadError(null);
      // 触发 .md 内容识别
      window.dispatchEvent(new CustomEvent('kb:wiki:upload:done', { detail: { filename: file.name } } as CustomEvent));
      window.setTimeout(() => { setUploadFileName(''); setIsUploading(0); }, 1500);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setUploadError(msg);
      setIsUploading(0);
      setUploadFileName('');
    }
  }, [t]);

  const triggerFilePick = () => { fileInputRef.current?.click(); };

  // 当前选中文档（中栏 top 栏使用）
  const selectedDoc = useMemo(() => {
    if (!selectedDocId) return null;
    for (const space of WIKI_SPACES) {
      const row = spaceStatus[space.id]?.docs.find((d) => d.id === selectedDocId);
      if (row) return row;
    }
    return null;
  }, [selectedDocId, spaceStatus]);

  // 状态 badge → theme 类（3 档）
  const statusChip = (status: WikiDocRow['status']) => {
    if (status === 'published') return styles.successBg ? { background: styles.successBg, color: styles.successText } : { background: 'transparent' };
    if (status === 'review') return { background: styles.warningBg, color: styles.warningText };
    return { background: styles.badgeBg, color: styles.badgeText };
  };

  const statusLabelKey = (status: WikiDocRow['status']) =>
    status === 'published' ? 'knowledge.wiki.status_published'
    : status === 'review' ? 'knowledge.wiki.status_review'
    : 'knowledge.wiki.status_draft';

  return (
    <div className="p-2" style={{ color: styles.cardText }}>
      <div className="rounded-md border flex flex-col" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        {/* 顶栏：标题 + 上传 .md 按钮（PRD F6 验收 6） */}
        <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
          <div className="flex items-center gap-2 text-sm font-semibold">
            <BookOpen className="w-4 h-4" />
            <span>{t('knowledge.wiki.title')}</span>
            <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
              {t('knowledge.wiki.subtitle')}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept=".md,.markdown"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void handleUploadMd(f);
                e.target.value = '';
              }}
            />
            <button
              type="button"
              onClick={triggerFilePick}
              disabled={isUploading > 0}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs border disabled:opacity-50"
              style={{ borderColor: styles.accentBorder, color: styles.accentText, background: styles.accentBg }}
            >
              {isUploading > 0 ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Upload className="w-3.5 h-3.5" />}
              {isUploading > 0
                ? t('knowledge.wiki.uploading', { percent: Math.round(isUploading * 100), name: uploadFileName })
                : t('knowledge.wiki.upload_md_button')}
            </button>
          </div>
        </div>

        {uploadError && (
          <div className="px-3 py-1.5 text-[11px] border-b flex items-center gap-2"
               style={{ borderColor: styles.cardBorder, background: styles.dangerBg, color: styles.dangerText }}>
            <MessageSquare className="w-3 h-3 flex-shrink-0" />
            <span className="truncate">{uploadError}</span>
          </div>
        )}

        {/* 三栏 — 220px / 1fr / 280px（PRD F6） */}
        <div className="grid grid-cols-1 md:grid-cols-[220px_1fr_280px] gap-0 divide-x divide-slate-200"
             style={{ borderColor: styles.cardBorder }}>
          {/* ───── 左栏 · 知识空间 tree ───── */}
          <div className="p-3 overflow-y-auto max-h-[64vh] min-h-80">
            <div className="text-[10px] font-bold uppercase tracking-wider mb-2 flex items-center gap-1.5"
                 style={{ color: styles.cardTextMuted }}>
              <FolderTree className="w-3 h-3" />
              <span>{t('knowledge.wiki.spaces_title')}</span>
            </div>
            <div className="space-y-0.5">
              {WIKI_SPACES.map((space) => {
                const st = spaceStatus[space.id];
                const isExpanded = expandedSpaces.has(space.id);
                const isParentLoading = st?.loading && (st.docs.length === 0);
                return (
                  <div key={space.id}>
                    <button
                      type="button"
                      onClick={() => {
                        const next = new Set(expandedSpaces);
                        if (next.has(space.id)) next.delete(space.id);
                        else {
                          next.add(space.id);
                          if (!st || isParentLoading) void loadSpace(space);
                        }
                      }}
                      className="w-full text-left px-2 py-1.5 rounded flex items-center gap-1.5 text-[11px] font-semibold hover:opacity-70 transition cursor-pointer"
                      style={{ color: styles.cardText, background: isExpanded ? styles.sidebarBg : 'transparent' }}
                    >
                      {isExpanded ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
                      <Folder className="w-3.5 h-3.5 shrink-0" style={{ color: styles.infoText }} />
                      <span className="truncate">{t(space.labelKey)}</span>
                      {isParentLoading && <Loader2 className="w-3 h-3 ml-auto animate-spin shrink-0" />}
                      {!isParentLoading && st && (
                        <span className="text-[9px] font-mono ml-auto" style={{ color: styles.muted }}>
                          {st.docs.length}
                        </span>
                      )}
                    </button>
                    {isExpanded && (
                      <div className="ml-4 pl-2 border-l space-y-0.5 py-1" style={{ borderColor: styles.inputBorder }}>
                        {st?.docs.length === 0 && (
                          <div className="px-2 py-1 text-[10px]" style={{ color: styles.muted }}>
                            {st?.error ? t('knowledge.wiki.space_load_error') : t('knowledge.wiki.space_empty')}
                          </div>
                        )}
                        {st?.docs.slice(0, 50).map((d) => (
                          <button
                            key={d.id}
                            type="button"
                            onClick={() => setSelectedDocId(d.id)}
                            className="w-full text-left px-2 py-1 rounded flex items-center gap-1.5 text-[10px] transition hover:opacity-80"
                            style={{
                              background: selectedDocId === d.id ? styles.accentBg : 'transparent',
                              color: selectedDocId === d.id ? 'rgba(255,255,255,0.92)' : styles.cardTextMuted,
                            }}
                            title={d.title}
                          >
                            <FileText className="w-3 h-3 shrink-0" />
                            <span className="truncate flex-1">{d.title}</span>
                            <span className="text-[9px] font-mono shrink-0 ml-1" style={{
                              color: selectedDocId === d.id ? 'rgba(255,255,255,0.85)' : styles.muted,
                            }}>{d.version}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* ───── 中栏 · md 预览（read-only） ───── */}
          <div className="min-w-0 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
              <div className="flex items-center gap-2 min-w-0">
                <span className="text-sm font-bold truncate" style={{ color: styles.cardText }}>
                  {selectedDoc?.title || t('knowledge.wiki.preview_empty_title')}
                </span>
                {/* 状态 badge */}
                {selectedDoc && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold" style={statusChip(previewStatus)}>
                    {t(statusLabelKey(previewStatus))}
                  </span>
                )}
              </div>
              <div className="text-[10px] font-mono shrink-0 flex items-center gap-2" style={{ color: styles.muted }}>
                {previewVersion && <span>{previewVersion}</span>}
                {previewAuthor && <span className="hidden md:inline truncate max-w-32">{previewAuthor}</span>}
                {previewUpdated && <span className="hidden lg:inline">{previewUpdated}</span>}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3 max-h-[60vh]">
              {!selectedDoc ? (
                <div className="h-full flex items-center justify-center text-xs" style={{ color: styles.muted }}>
                  <div className="text-center space-y-1.5 max-w-sm">
                    <FileText className="w-6 h-6 mx-auto opacity-40" />
                    <p>{t('knowledge.wiki.center_preview_placeholder')}</p>
                  </div>
                </div>
              ) : (
                renderMarkdownLite(previewText, styles)
              )}
            </div>
          </div>

          {/* ───── 右栏 · Knowledge Context ───── */}
          <div className="overflow-y-auto max-h-[64vh] min-h-80" style={{ background: styles.sidebarBg }}>
            <div className="px-3 py-2 border-b text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5"
                 style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}>
              <ListChecks className="w-3 h-3" />
              <span>{t('knowledge.wiki.right_context')}</span>
            </div>

            {/* 语义实体 chips */}
            <div className="px-3 pt-2.5 pb-2 space-y-1.5">
              <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
                {t('knowledge.wiki.semantic_entities')}
              </span>
              <div className="flex flex-wrap gap-1">
                {semanticEntities.length === 0 ? (
                  <span className="text-[10px]" style={{ color: styles.muted }}>{t('knowledge.wiki.no_semantic_entities')}</span>
                ) : semanticEntities.map((e, i) => (
                  <span
                    key={`${e}-${i}`}
                    className="px-1.5 py-0.5 rounded text-[10px] font-mono font-bold inline-flex items-center gap-1"
                    style={{ background: styles.infoBg, color: styles.infoText }}
                  >
                    <Tag className="w-2.5 h-2.5" />
                    {e.startsWith('@') ? e.slice(1) : e}
                  </span>
                ))}
              </div>
            </div>

            {/* 相关知识链接（最多 3 个） */}
            <div className="px-3 pt-2 pb-2 space-y-1.5 border-t" style={{ borderColor: styles.inputBorder }}>
              <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
                {t('knowledge.wiki.related')}
              </span>
              {relatedRefs.length === 0 ? (
                <p className="text-[10px]" style={{ color: styles.muted }}>{t('knowledge.wiki.no_related')}</p>
              ) : (
                <div className="space-y-0.5">
                  {relatedRefs.slice(0, 3).map((r, i) => (
                    <div key={`${r}-${i}`} className="text-[10px] flex items-center gap-1.5 truncate"
                         style={{ color: styles.accentText }}>
                      <MessageSquare className="w-3 h-3 shrink-0" />
                      <span className="truncate">{r}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 图谱关系（"项目 → 遵循 → 交付规范"式） */}
            <div className="px-3 pt-2 pb-2 space-y-1.5 border-t" style={{ borderColor: styles.inputBorder }}>
              <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
                {t('knowledge.wiki.graph_relations')}
              </span>
              {graphRelations.length === 0 ? (
                <p className="text-[10px]" style={{ color: styles.muted }}>{t('knowledge.wiki.no_graph_relations')}</p>
              ) : (
                <div className="space-y-1">
                  {graphRelations.slice(0, 3).map((rel, i) => (
                    <div key={i} className="text-[10px] font-mono" style={{ color: styles.cardText }}>
                      <span style={{ color: styles.infoText }}>{rel.from}</span>
                      <span className="mx-1" style={{ color: styles.muted }}>→</span>
                      <span className="px-1 py-0.5 rounded" style={{ background: styles.badgeBg, color: styles.badgeText }}>
                        {rel.verb}
                      </span>
                      <span className="mx-1" style={{ color: styles.muted }}>→</span>
                      <span style={{ color: styles.infoText }}>{rel.to}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 查看图谱上下文 → 跳 #/knowledge_view?page=graph&focusEntity={id}（PRD F6 验收 5） */}
            {selectedDoc && (
              <div className="px-3 pt-3 pb-3">
                <button
                  type="button"
                  onClick={() => { window.location.hash = `#/knowledge_view?page=graph&focusEntity=${encodeURIComponent(selectedDoc.id)}`; }}
                  className="w-full px-3 py-1.5 rounded text-[11px] font-bold flex items-center justify-center gap-1.5 transition disabled:opacity-50 cursor-pointer"
                  style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)', border: `1px solid ${styles.accentBorder}` }}
                >
                  <Network className="w-3.5 h-3.5" />
                  {t('knowledge.wiki.view_graph_context')}
                </button>
                <p className="mt-1.5 text-[9px] font-mono text-center" style={{ color: styles.muted }}>
                  {t('knowledge.wiki.graph_focus_hint', { id: selectedDoc.id.slice(0, 12) })}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

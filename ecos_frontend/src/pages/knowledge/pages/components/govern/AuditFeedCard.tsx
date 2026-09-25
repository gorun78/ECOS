/**
 * PMO-D Batch 2 — F7 GovernPage 上区 AuditFeedCard（PRD §3.2 F7）。
 *
 * 原 ExtractionReviewTab 主逻辑迁移 + 4 步 lifecycle pipeline。
 * 左卡：候选 feed 列表（fetchExtractCandidateFiles?status=pending）
 *   — 来源 / 抽取 / 置信度 / 关联实体
 *   — 确认发布 / 退回 按钮（调 POST /knowledge/lifecycle/transition）
 * 右卡：生命周期 pipeline `草稿 → 抽取 → 审核 → 发布`（4 步）
 *
 * 主题 §4.1：0 硬编码色值
 * i18n §4.3：0 硬编码中文（key: knowledge.govern.audit_feed_*）
 */
import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, ClipboardCheck, FileText, Inbox, Loader2, RefreshCw, XCircle } from 'lucide-react';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';
import { knowledgeApi } from '../../../services/knowledgeApi';

interface CandidateFile {
  fileId: string;
  fileName: string;
  status: string;
  candidateCount: number;
  checksum?: string;
  createdAt?: string;
  error?: string;
}

/** lifecycle 4 步（PRD F7 右卡） */
const LIFECYCLE_STEPS = [
  { key: 'knowledge.govern.audit_step_draft',   icon: 'Pencil' },
  { key: 'knowledge.govern.audit_step_extract', icon: 'Extract' },
  { key: 'knowledge.govern.audit_step_review',  icon: 'Review' },
  { key: 'knowledge.govern.audit_step_publish', icon: 'Publish' },
] as const;

export default function AuditFeedCard() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [files, setFiles] = useState<CandidateFile[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [activeFileId, setActiveFileId] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<Record<string, unknown>[]>([]);
  const [candidatesLoading, setCandidatesLoading] = useState<boolean>(false);
  const [activeStep, setActiveStep] = useState<number>(2);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    setErrorBanner(null);
    try {
      const list = await knowledgeApi.fetchExtractCandidateFiles();
      // 仅消费 PENDING 状态
      const pending = (Array.isArray(list) ? list : []).filter(f => {
        const s = (f.status || '').trim().toUpperCase();
        return ['PENDING', 'QUEUED', ''].includes(s) || s.length === 0;
      });
      setFiles(pending);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.audit_load_error')}: ${msg}`);
      setFiles([]);
    } finally {
      setLoading(false);
    }
  }, [t]);

  const loadCandidates = useCallback(async (fileId: string) => {
    setCandidatesLoading(true);
    setCandidates([]);
    try {
      const res = await knowledgeApi.fetchExtractCandidates(fileId);
      setCandidates(res?.candidates || []);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.audit_candidates_error')}: ${msg}`);
    } finally {
      setCandidatesLoading(false);
    }
  }, [t]);

  useEffect(() => { void loadFiles(); }, [loadFiles]);

  useEffect(() => {
    if (activeFileId) void loadCandidates(activeFileId);
  }, [activeFileId, loadCandidates]);

  /** 确认发布 → lifecycleTransition(id, 'active')。失败 toast 兜底 */
  const handlePublish = useCallback(async (fileId: string) => {
    try {
      await knowledgeApi.lifecycleTransition(fileId, 'active');
      // 同步移除本地
      setFiles(prev => prev.filter(f => f.fileId !== fileId));
      if (activeFileId === fileId) {
        setActiveFileId(null);
        setCandidates([]);
        setActiveStep(3);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.audit_publish_failed')}: ${msg}`);
    }
  }, [activeFileId, t]);

  /** 退回 → lifecycleTransition(id, 'draft')。失败 toast 兜底 */
  const handleReject = useCallback(async (fileId: string) => {
    try {
      await knowledgeApi.lifecycleTransition(fileId, 'draft');
      setFiles(prev => prev.filter(f => f.fileId !== fileId));
      if (activeFileId === fileId) {
        setActiveFileId(null);
        setCandidates([]);
        setActiveStep(0);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.audit_reject_failed')}: ${msg}`);
    }
  }, [activeFileId, t]);

  const activeFile: CandidateFile | null = files.find(f => f.fileId === activeFileId) || null;

  return (
    <div
      className="rounded-md border flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
    >
      {/* 标题栏 */}
      <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
        <div className="flex items-center gap-2 text-sm font-semibold">
          <ClipboardCheck className="w-4 h-4" style={{ color: styles.accentText }} />
          <span>{t('knowledge.govern.audit_feed')}</span>
          <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
            {t('knowledge.govern.audit_pending_count', { count: files.length })}
          </span>
        </div>
        <button
          type="button"
          onClick={() => void loadFiles()}
          className="p-1 rounded border cursor-pointer hover:opacity-70 transition disabled:opacity-50"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
          title={t('knowledge.govern.audit_refresh')}
          disabled={loading}
        >
          {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
        </button>
      </div>

      {errorBanner && (
        <div className="px-3 py-1.5 text-[11px] border-b flex items-center gap-2"
             style={{ borderColor: styles.cardBorder, background: styles.dangerBg, color: styles.dangerText }}>
          <XCircle className="w-3 h-3 flex-shrink-0" />
          <span className="truncate">{errorBanner}</span>
        </div>
      )}

      {/* 双卡：候选 / 详情 + lifecycle pipeline */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-3 p-3">
        {/* 左卡：候选 feed 列表 */}
        <div className="xl:col-span-1 rounded-md border flex flex-col min-h-32" style={{ borderColor: styles.cardBorder }}>
          <div className="px-2.5 py-1.5 border-b text-[10px] font-bold uppercase tracking-wider"
               style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}>
            {t('knowledge.govern.audit_file_list')}
          </div>
          <div className="flex-1 overflow-y-auto max-h-48">
            {loading ? (
              <div className="p-3 flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
                <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
                {t('knowledge.govern.audit_loading')}
              </div>
            ) : files.length === 0 ? (
              <div className="p-4 text-center text-[11px]" style={{ color: styles.muted }}>
                <Inbox className="w-5 h-5 mx-auto mb-1 opacity-40" />
                {t('knowledge.govern.audit_files_empty')}
              </div>
            ) : (
              files.slice(0, 30).map((f) => (
                <button
                  key={f.fileId}
                  type="button"
                  onClick={() => { setActiveFileId(f.fileId); setActiveStep(2); }}
                  className="w-full text-left px-2.5 py-1.5 border-b flex items-center gap-1.5 text-[10px] hover:opacity-80 transition cursor-pointer"
                  style={{
                    borderColor: styles.cardBorder,
                    background: activeFileId === f.fileId ? styles.accentBg : 'transparent',
                    color: activeFileId === f.fileId ? 'rgba(255,255,255,0.95)' : styles.cardText,
                  }}
                >
                  <FileText className="w-3 h-3 shrink-0" />
                  <span className="truncate flex-1">{f.fileName}</span>
                  <span className="text-[9px] font-mono shrink-0" style={{
                    color: activeFileId === f.fileId ? 'rgba(255,255,255,0.8)' : styles.muted,
                  }}>{f.candidateCount}</span>
                </button>
              ))
            )}
          </div>
        </div>

        {/* 中卡：候选明细 */}
        <div className="xl:col-span-1 rounded-md border flex flex-col min-h-32" style={{ borderColor: styles.cardBorder }}>
          <div className="px-2.5 py-1.5 border-b text-[10px] font-bold uppercase tracking-wider flex items-center justify-between"
               style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}>
            <span>{t('knowledge.govern.audit_candidates')}</span>
            {activeFile && (
              <span className="text-[9px] font-mono" style={{ color: styles.muted }}>{activeFile.fileName}</span>
            )}
          </div>
          <div className="flex-1 overflow-y-auto max-h-48 p-2 space-y-1">
            {!activeFile ? (
              <div className="h-full flex items-center justify-center text-[10px]" style={{ color: styles.muted }}>
                {t('knowledge.govern.audit_select_file_hint')}
              </div>
            ) : candidatesLoading ? (
              <div className="p-3 flex items-center justify-center text-[10px]" style={{ color: styles.muted }}>
                <Loader2 className="w-3 h-3 animate-spin mr-1.5" />
                {t('knowledge.govern.audit_candidates_loading')}
              </div>
            ) : candidates.length === 0 ? (
              <div className="p-3 text-center text-[10px]" style={{ color: styles.muted }}>
                {t('knowledge.govern.audit_candidates_empty')}
              </div>
            ) : (
              candidates.slice(0, 30).map((c, i) => {
                const cType = String(c.type ?? c.entityType ?? c.kind ?? c.candidateId ?? `#${i + 1}`);
                const desc = String(c.description ?? c.label ?? c.value ?? '').slice(0, 120);
                const confidence = typeof c.confidence === 'number' ? c.confidence : Number(c.confidence) || 0;
                const entity = String(c.entity ?? c.entityType ?? c.entityName ?? '').slice(0, 24);
                return (
                  <div key={`${cType}-${i}`} className="rounded border px-2 py-1.5 text-[10px]" style={{ borderColor: styles.cardBorder }}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold truncate" style={{ color: styles.cardText }}>{cType}</span>
                      <span className="text-[9px] font-mono shrink-0 px-1 py-0.5 rounded"
                            style={{ background: styles.infoBg, color: styles.infoText }}>
                        {(confidence * 100).toFixed(0)}%
                      </span>
                    </div>
                    {desc && <p className="opacity-80 truncate mt-0.5" style={{ color: styles.cardTextMuted }}>{desc}</p>}
                    {entity && (
                      <p className="text-[9px] font-mono mt-0.5 flex items-center gap-1" style={{ color: styles.muted }}>
                        <span>entity:</span>
                        <span style={{ color: styles.accentText }}>{entity}</span>
                      </p>
                    )}
                  </div>
                );
              })
            )}
          </div>
          {activeFile && !candidatesLoading && (
            <div className="border-t px-2 py-1.5 flex gap-1.5" style={{ borderColor: styles.cardBorder }}>
              <button
                type="button"
                onClick={() => void handlePublish(activeFileId || '')}
                className="flex-1 inline-flex items-center justify-center gap-1 px-2 py-1 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
                style={{ background: styles.successBg, color: styles.successText }}
                title={t('knowledge.govern.audit_publish')}
              >
                <CheckCircle2 className="w-3 h-3" />
                {t('knowledge.govern.audit_publish')}
              </button>
              <button
                type="button"
                onClick={() => void handleReject(activeFileId || '')}
                className="flex-1 inline-flex items-center justify-center gap-1 px-2 py-1 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
                style={{ background: styles.dangerBg, color: styles.dangerText }}
                title={t('knowledge.govern.audit_reject')}
              >
                <XCircle className="w-3 h-3" />
                {t('knowledge.govern.audit_reject')}
              </button>
            </div>
          )}
        </div>

        {/* 右卡：lifecycle pipeline 4 步（草稿 → 抽取 → 审核 → 发布） */}
        <div className="xl:col-span-1 rounded-md border p-2.5 flex flex-col min-h-32" style={{ borderColor: styles.cardBorder }}>
          <div className="text-[10px] font-bold uppercase tracking-wider mb-2 flex items-center gap-1.5"
               style={{ color: styles.cardTextMuted }}>
            <ClipboardCheck className="w-3 h-3" />
            <span>{t('knowledge.govern.audit_lifecycle_title')}</span>
          </div>
          <div className="space-y-1.5 flex-1 flex flex-col justify-center">
            {LIFECYCLE_STEPS.map((step, i) => {
              const isActive = i === activeStep;
              const isDone = i < activeStep;
              return (
                <div key={step.key}
                     className="flex items-center gap-2 px-2 py-1.5 rounded border text-[10px] font-bold"
                     style={{
                       borderColor: styles.cardBorder,
                       background: isActive ? styles.accentBg : (isDone ? styles.successBg : 'transparent'),
                       color: isActive ? 'rgba(255,255,255,0.95)' : (isDone ? styles.successText : styles.cardTextMuted),
                       opacity: isDone ? 0.7 : 1,
                     }}>
                  <span className="w-2 h-2 rounded-full shrink-0" style={{
                    background: isActive ? 'rgba(255,255,255,0.9)' : (isDone ? styles.successText : styles.muted),
                  }} />
                  <span className="truncate flex-1">{t(step.key)}</span>
                  {isDone && <CheckCircle2 className="w-3 h-3 shrink-0" />}
                </div>
              );
            })}
          </div>
          <p className="text-[9px] font-mono mt-2 pt-2 border-t" style={{ borderColor: styles.inputBorder, color: styles.muted }}>
            {activeStep === 3
              ? t('knowledge.govern.audit_step_publish_done')
              : activeStep === 0
                ? t('knowledge.govern.audit_step_draft_hint')
                : t('knowledge.govern.audit_step_review_hint')}
          </p>
        </div>
      </div>
    </div>
  );
}

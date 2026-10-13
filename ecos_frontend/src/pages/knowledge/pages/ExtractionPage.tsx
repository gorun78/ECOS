/**
 * PMO-D Batch 2 — F4 ExtractionPage（实体实现 / PRD §3.2 F4）。
 *
 * 布局：grid grid-cols-[1.25fr_0.75fr] gap-4
 *   左栏 — 结构化 I→K（DW 层 → 图谱实体/关系）：
 *     - 映射契约 select（fetchOntologyMappings status=published 过滤 + 降级）
 *     - 3 抽取目标 checkbox（图谱实体/属性 · 图谱关系 · 向量索引）
 *     - 「预览并提交」按钮 → POST /knowledge/structured/extract/submit
 *     - I→K badge
 *   右栏 — 文档导入 Document→K（非结构化，经 DW 层）：
 *     - 4 能力 badge（文档导入 / OCR / 分块 / 实体关系抽取）
 *     - 「上传文档」按钮 → uploadDocumentChunked 分片
 *     - 「→ DW 层 → 知识审」标注
 *     - Document→K badge
 *   底部 — 3 KPI 卡片（.grid-3）：实体 X / 关系 Y / 候选知识 Z
 *     data from fetchStructuredJobs 最新 summary
 *
 * 降级（PRD F4 验收：mapping_unavailable）：
 *   映射契约列表不可用 / 无 status=published 条目 → select disabled + placeholder
 *   降级不阻塞右栏文档导入能力（左右栏独立功能域）
 *
 * 主题 §4.1：0 硬编码色值
 * 图标 §4.2：仅 lucide-react
 * i18n §4.3：0 硬编码中文（knowledge.extract.*）
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle, ArrowDownUp, Boxes, Check, FileUp, Loader2,
  Layers, Send, Sparkles, ScanEye, Scissors, Network,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetchData } from '../../../api';
import { knowledgeApi } from '../services/knowledgeApi';

/** 映射契约条目（GET /api/v1/ontology/mappings 返回项子集） */
interface OntologyMapping {
  id: string;
  name: string;
  entityCount?: number;
  status?: string;
  updatedAt?: string;
}

/** 3 抽取目标（PRD F4 左栏 checkbox） */
const EXTRACT_TARGETS = [
  { id: 'graph_entities',   labelKey: 'knowledge.extract.target_graph_entities' },
  { id: 'graph_relations',  labelKey: 'knowledge.extract.target_graph_relations' },
  { id: 'vector_index',     labelKey: 'knowledge.extract.target_vector_index' },
] as const;

/** 右栏 4 能力 badge（PRD F4 右栏） */
const DOC_CAPABILITIES = [
  { id: 'ingest',   labelKey: 'knowledge.extract.doc_cap_ingest',   icon: 'Ingest' },
  { id: 'ocr',      labelKey: 'knowledge.extract.doc_cap_ocr',      icon: 'Ocr' },
  { id: 'chunk',    labelKey: 'knowledge.extract.doc_cap_chunk',    icon: 'Chunk' },
  { id: 'extract',  labelKey: 'knowledge.extract.doc_cap_extract',  icon: 'Extract' },
] as const;

export default function ExtractionPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 左栏 状态 ──
  const [mappings, setMappings] = useState<OntologyMapping[]>([]);
  const [mappingsLoading, setMappingsLoading] = useState<boolean>(true);
  const [mappingsError, setMappingsError] = useState<string | null>(null);
  const [selectedMappingId, setSelectedMappingId] = useState<string>('');
  const [targetGraphEntities, setTargetGraphEntities] = useState<boolean>(true);
  const [targetGraphRelations, setTargetGraphRelations] = useState<boolean>(true);
  const [targetVectorIndex, setTargetVectorIndex] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitResult, setSubmitResult] = useState<{ ok: boolean; msg?: string } | null>(null);

  // ── 右栏 状态 ──
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [docPhase, setDocPhase] = useState<'idle' | 'uploading' | 'queued' | 'done' | 'failed'>('idle');
  const [docProgress, setDocProgress] = useState<number>(0);
  const [docFileName, setDocFileName] = useState<string>('');
  const [docError, setDocError] = useState<string | null>(null);

  // 上传后抽取状态轮询（真实端点，非假推进）
  const mountedRef = useRef<boolean>(true);
  const pollRef = useRef<number | null>(null);

  // ── 底部 KPI — 来自最近一次「预览」真实返回（无预览则不可用） ──
  const [lastPreview, setLastPreview] = useState<{ entities: number; relations: number } | null>(null);

  /** 拉映射契约（GET /api/v1/ontology/mappings）— 失败降级 disabled */
  const loadMappings = useCallback(async () => {
    setMappingsLoading(true);
    setMappingsError(null);
    try {
      const data = await apiFetchData<Array<OntologyMapping> | { mappings?: OntologyMapping[] }>('/api/v1/ontology/mappings');
      let list: OntologyMapping[];
      if (Array.isArray(data)) {
        list = data;
      } else {
        list = (data?.mappings as OntologyMapping[]) || [];
      }
      // 只保留 status=published 的契约（PRD F4 验收）
      const published = list.filter((m) => (m.status || '').toLowerCase() === 'published');
      setMappings(published);
      if (published.length === 0) {
        setMappingsError(t('knowledge.extract.mapping_unavailable'));
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setMappings([]);
      setMappingsError(`${t('knowledge.extract.mapping_unavailable')}: ${msg}`);
    } finally {
      setMappingsLoading(false);
    }
  }, [t]);

  useEffect(() => { void loadMappings(); }, [loadMappings]);

  /** 后端拉取可用（contract published loading 或 error） */
  const mappingsAvailable = mappings.length > 0 && !mappingsError;

  /** 轮询真实抽取状态（fetchExtractStatus），有界重试；未达终态不谎报完成。 */
  const pollExtractStatus = useCallback((taskId: string) => {
    const INTERVAL_MS = 3000;
    const MAX_ATTEMPTS = 20;
    let attempts = 0;
    const tick = async () => {
      if (!mountedRef.current) return;
      attempts += 1;
      try {
        const st = await knowledgeApi.fetchExtractStatus(taskId);
        if (!mountedRef.current) return;
        const s = String(st?.status || '').toUpperCase();
        if (typeof st?.progress === 'number' && st.progress >= 0 && st.progress <= 1) setDocProgress(st.progress);
        if (s === 'SUCCEEDED') { setDocPhase('done'); return; }
        if (s === 'FAILED') { setDocPhase('failed'); setDocError(st?.statusMessage || t('knowledge.extract.doc_extract_failed')); return; }
        if (attempts >= MAX_ATTEMPTS) { setDocError(t('knowledge.extract.doc_status_timeout')); return; }
        pollRef.current = window.setTimeout((): void => { void tick(); }, INTERVAL_MS);
      } catch {
        if (!mountedRef.current) return;
        setDocPhase('failed');
        setDocError(t('knowledge.extract.doc_status_unavailable'));
      }
    };
    pollRef.current = window.setTimeout((): void => { void tick(); }, INTERVAL_MS);
  }, [t]);

  /** 「预览」— 调真实 dryRun 端点 triggerStructuredExtract，展示其返回的真实计数。
   *  注：后端抽取执行端点仅接受 {dryRun, mode}，不接收所选映射/目标 →
   *  映射与目标选择「不参与预览/执行」（抽取执行绑定能力未上线），故相关控件禁用并标注。 */
  const handleSubmit = useCallback(async () => {
    setSubmitting(true);
    setSubmitResult(null);
    try {
      const resp = await knowledgeApi.triggerStructuredExtract({ dryRun: true, mode: 'INCREMENTAL' });
      const entities = resp?.entityCount ?? resp?.nodeCreated ?? 0;
      const relations = resp?.edgeCreated ?? 0;
      setLastPreview({ entities, relations });
      setSubmitResult({ ok: true, msg: t('knowledge.extract.preview_result', { entities, relations }) });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setSubmitResult({ ok: false, msg: `${t('knowledge.extract.submit_failed')}: ${msg}` });
    } finally {
      setSubmitting(false);
    }
  }, [t]);

  /** 右栏「上传文档」— uploadDocumentChunked 分片后，轮询真实抽取状态端点。
   *  绝不以 setTimeout 假推进；未到终态或状态不可得 → 明确标注，不谎报「完成」。 */
  const handleDocFile = useCallback(async (file: File) => {
    setDocError(null);
    setDocFileName(file.name);
    setDocPhase('uploading');
    setDocProgress(0);
    try {
      const onProgress = (fraction: number) => setDocProgress(Math.max(0.02, fraction));
      const { taskId } = await knowledgeApi.uploadDocumentChunked(file, onProgress);
      setDocPhase('queued');
      setDocProgress(1);
      if (taskId) void pollExtractStatus(taskId);
      else { setDocPhase('failed'); setDocError(t('knowledge.extract.doc_status_unavailable')); }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setDocPhase('failed');
      setDocError(msg);
    }
  }, [pollExtractStatus, t]);

  // 卸载时清理轮询定时器
  useEffect(() => () => {
    mountedRef.current = false;
    if (pollRef.current) window.clearTimeout(pollRef.current);
  }, []);

  // 4 能力 badge — i18n 1:1
  const capabilityIconFor = (icon: string): React.ReactNode => {
    switch (icon) {
      case 'Ingest':  return <FileUp className="w-3 h-3" />;
      case 'Ocr':     return <ScanEye className="w-3 h-3" />;
      case 'Chunk':   return <Scissors className="w-3 h-3" />;
      case 'Extract': return <Sparkles className="w-3 h-3" />;
      default:        return <Boxes className="w-3 h-3" />;
    }
  };

  const docPhaseChipClass = (phase: typeof docPhase) => {
    if (phase === 'done') return `${styles.successBg} ${styles.successText}`;
    if (phase === 'failed') return `${styles.dangerBg} ${styles.dangerText}`;
    if (phase === 'uploading') return `${styles.accentBg} ${styles.accentText}`;
    if (phase === 'queued') return `${styles.warningBg} ${styles.warningText}`;
    return `${styles.badgeBg} ${styles.badgeText}`;
  };

  const docPhaseLabelKey = (phase: typeof docPhase) =>
    phase === 'done' ? 'knowledge.extract.doc_phase_done'
    : phase === 'failed' ? 'knowledge.extract.doc_phase_failed'
    : phase === 'uploading' ? 'knowledge.extract.doc_phase_uploading'
    : phase === 'queued' ? 'knowledge.extract.doc_phase_queued'
    : 'knowledge.extract.doc_phase_idle';

  /** 预览按钮仅受自身提交态约束（dryRun 预览为全局执行，不依赖映射选择） */
  const submitDisabled = useMemo(() => submitting, [submitting]);

  return (
    <div className="p-2" style={{ color: styles.cardText }}>
      {/* 总标题 */}
      <div className="flex items-center gap-2 px-3 py-2 rounded-md border mb-3"
           style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <ArrowDownUp className="w-4 h-4" style={{ color: styles.accentText }} />
        <span className="text-base font-bold">{t('knowledge.nav.page_extract')}</span>
        <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
          {t('knowledge.extract.page_subtitle')}
        </span>
      </div>

      {/* 双栏 grid：1.25fr / 0.75fr（PRD F4 验收 1） */}
      <div className="grid grid-cols-1 md:grid-cols-[1.25fr_0.75fr] gap-3">
        {/* ═══════ 左栏 · 结构化 I→K（DW 层源头，1.25fr） ═══════ */}
        <div
          className="rounded-md border flex flex-col min-h-72"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          {/* 头：I→K badge + 标题 */}
          <div className="flex items-center justify-between px-3 py-2 border-b"
               style={{ borderColor: styles.cardBorder }}>
            <div className="flex items-center gap-2">
              <Boxes className="w-4 h-4" style={{ color: styles.accentText }} />
              <span className="text-sm font-semibold">{t('knowledge.extract.structured')}</span>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold"
                  style={{ background: styles.badgeBg, color: styles.badgeText }}>
              I → K
            </span>
          </div>

          <div className="p-3 space-y-3 flex-1">
            {/* 诚实标注：执行端点仅接受 {dryRun, mode}，所选映射/目标不参与预览/执行 */}
            <div className="px-2.5 py-1.5 rounded border text-[10px] font-mono flex items-start gap-1.5"
                 style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
              <AlertCircle className="w-3 h-3 shrink-0 mt-0.5" />
              <span>{t('knowledge.extract.execution_binding_unavailable')}</span>
            </div>
            {/* 映射契约 select（fetchOntologyMappings published 过滤） */}
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.extract.mapping_contract')}
              </label>
              <div className="relative">
                <select
                  value={selectedMappingId}
                  onChange={(e) => { setSelectedMappingId(e.target.value); setSubmitResult(null); }}
                  disabled
                  className="w-full px-2.5 py-1.5 text-[11px] rounded border outline-none disabled:opacity-50"
                  style={{
                    background: styles.inputBg,
                    color: mappingsAvailable ? styles.inputText : styles.muted,
                    borderColor: styles.inputBorder,
                  }}
                  title={mappingsError || ''}
                >
                  <option value="">
                    {mappingsLoading
                      ? t('knowledge.extract.mapping_loading')
                      : mappingsError
                        ? mappingsError
                        : t('knowledge.extract.mapping_placeholder')}
                  </option>
                  {mappings.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}{m.entityCount ? ` (${m.entityCount})` : ''}{m.updatedAt ? ` · ${(m.updatedAt || '').slice(0, 10)}` : ''}
                    </option>
                  ))}
                </select>
                {mappingsLoading && (
                  <Loader2 className="w-3.5 h-3.5 absolute right-2.5 top-1/2 -translate-y-1/2 animate-spin"
                           style={{ color: styles.muted }} />
                )}
              </div>
              {mappingsError && !mappingsLoading && (
                <p className="text-[10px] font-mono" style={{ color: styles.warningText }}>
                  {t('knowledge.extract.mapping_unavailable_hint')}
                </p>
              )}
            </div>

            {/* 3 checkbox（图谱实体/属性 · 图谱关系 · 向量索引） */}
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.extract.targets_label')}
              </label>
              <div className="space-y-1">
                {EXTRACT_TARGETS.map((target) => {
                  const checked =
                    target.id === 'graph_entities' ? targetGraphEntities
                    : target.id === 'graph_relations' ? targetGraphRelations
                    : targetVectorIndex;
                  const setChecked = (v: boolean) => {
                    if (target.id === 'graph_entities') setTargetGraphEntities(v);
                    else if (target.id === 'graph_relations') setTargetGraphRelations(v);
                    else setTargetVectorIndex(v);
                    setSubmitResult(null);
                  };
                  return (
                    <label
                      key={target.id}
                      className="flex items-center gap-2 px-2 py-1.5 rounded border text-[11px] opacity-70"
                      style={{ borderColor: styles.cardBorder, background: checked ? styles.sidebarBg : 'transparent' }}
                      title={t('knowledge.extract.execution_binding_unavailable')}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => setChecked(e.target.checked)}
                        disabled
                        className="h-3.5 w-3.5 cursor-not-allowed"
                      />
                      <span className="flex-1" style={{ color: checked ? styles.cardText : styles.cardTextMuted }}>
                        {t(target.labelKey)}
                      </span>
                      {checked && <Check className="w-3 h-3" style={{ color: styles.successText }} />}
                    </label>
                  );
                })}
              </div>
            </div>

            {/* 提交按钮（PRD F4 验收：调真实 API + toast） */}
            <div className="pt-1">
              <button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={submitDisabled}
                className={`w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[11px] font-bold transition disabled:opacity-50 cursor-pointer border ${styles.accentBg} ${styles.accentText} ${styles.accentBorder}`}
              >
                {submitting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                {submitting ? t('knowledge.extract.submitting') : t('knowledge.extract.preview_button')}
              </button>
              {submitResult && (
                <div
                  className={`mt-1.5 px-2 py-1 rounded text-[10px] font-mono flex items-center gap-1.5 ${submitResult.ok ? `${styles.successBg} ${styles.successText}` : `${styles.dangerBg} ${styles.dangerText}`}`}
                >
                  {submitResult.ok
                    ? <Check className="w-3 h-3 shrink-0" />
                    : <AlertCircle className="w-3 h-3 shrink-0" />}
                  <span className="whitespace-pre-line">{submitResult.msg || ''}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* ═══════ 右栏 · 文档导入 Document→K（0.75fr） ═══════ */}
        <div
          className="rounded-md border flex flex-col min-h-72"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          {/* 头：Document→K badge + 标题 */}
          <div className="flex items-center justify-between px-3 py-2 border-b"
               style={{ borderColor: styles.cardBorder }}>
            <div className="flex items-center gap-2">
              <FileUp className="w-4 h-4" style={{ color: styles.accentText }} />
              <span className="text-sm font-semibold">{t('knowledge.extract.document')}</span>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold"
                  style={{ background: styles.badgeBg, color: styles.badgeText }}>
              Document → K
            </span>
          </div>

          <div className="p-3 space-y-3 flex-1">
            {/* 4 能力 badge（文档导入 / OCR / 分块 / 实体关系抽取） */}
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.extract.doc_capabilities')}
              </label>
              <div className="grid grid-cols-2 gap-1.5">
                {DOC_CAPABILITIES.map((cap) => (
                  <div
                    key={cap.id}
                    className="inline-flex items-center gap-1.5 px-2 py-1 rounded border text-[10px] font-bold"
                    style={{ borderColor: styles.cardBorder, background: styles.sidebarBg, color: styles.cardTextMuted }}
                  >
                    {capabilityIconFor(cap.icon)}
                    <span>{t(cap.labelKey)}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* 「→ DW 层 → 知识审」标注（PRD F4 右栏） */}
            <div
              className="px-2.5 py-1.5 rounded border text-[10px] font-mono tracking-wider"
              style={{ borderColor: styles.inputBorder, background: styles.sidebarBg, color: styles.accentText }}>
              {t('knowledge.extract.doc_pipeline_hint')}
            </div>

            {/* 「上传文档」按钮 + 状态 */}
            <div className="space-y-1.5">
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.doc,.docx,.txt,.md,.markdown"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void handleDocFile(f);
                  e.target.value = '';
                }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={docPhase === 'uploading' || docPhase === 'queued'}
                className={`w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[11px] font-bold transition disabled:opacity-50 cursor-pointer border ${styles.accentBg} ${styles.accentText} ${styles.accentBorder}`}
              >
                {docPhase === 'uploading' ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <FileUp className="w-3.5 h-3.5" />
                )}
                {docPhase === 'uploading'
                  ? t('knowledge.extract.doc_uploading', { percent: Math.round(docProgress * 100) })
                  : t('knowledge.extract.doc_upload_button')}
              </button>

              {/* 上传状态 chip */}
              {docPhase !== 'idle' && (
                <div className="flex items-center gap-1.5 flex-wrap">
                  <span
                    className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-mono font-bold ${docPhaseChipClass(docPhase)}`}
                  >
                    {t(docPhaseLabelKey(docPhase))}
                  </span>
                  {docFileName && (
                    <span className="text-[10px] font-mono truncate max-w-[50%]" style={{ color: styles.muted }}>
                      {docFileName}
                    </span>
                  )}
                  {docPhase === 'uploading' && (
                    <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
                      {Math.round(docProgress * 100)}%
                    </span>
                  )}
                </div>
              )}
              {docError && (
                <p className="text-[10px] font-mono truncate" style={{ color: styles.dangerText }}>{docError}</p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 底部 3 KPI — 反映最近一次「预览」的真实返回；未预览或无端点来源 → 「—」不臆造 */}
      <div className="mt-3 space-y-1.5">
        <div className="text-[10px] font-mono" style={{ color: styles.muted }}>
          {lastPreview ? t('knowledge.extract.kpi_from_preview') : t('knowledge.extract.kpi_no_preview')}
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <KpiCard
            icon={<Boxes className="w-4 h-4" />}
            label={t('knowledge.extract.kpi_entities')}
            value={lastPreview ? lastPreview.entities : null}
            accent={styles.infoText}
            styles={styles}
          />
          <KpiCard
            icon={<Network className="w-4 h-4" />}
            label={t('knowledge.extract.kpi_relations')}
            value={lastPreview ? lastPreview.relations : null}
            accent={styles.successText}
            styles={styles}
          />
          <KpiCard
            icon={<Layers className="w-4 h-4" />}
            label={t('knowledge.extract.kpi_candidates')}
            value={null}
            unavailableHint={t('knowledge.extract.kpi_candidates_unavailable')}
            accent={styles.warningText}
            styles={styles}
          />
        </div>
      </div>
    </div>
  );
}

/** 通用 KPI 卡片：value=null → 「—」（不可用 / 未预览），不显示伪 0 */
function KpiCard({
  icon, label, value, accent, styles, unavailableHint,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | null;
  accent: string;
  unavailableHint?: string;
  styles: ReturnType<typeof useTheme>['styles'];
}) {
  const unavailable = value === null;
  return (
    <div
      className="rounded-md border p-3 space-y-1.5 flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
    >
      <div className={`flex items-center gap-1.5 ${unavailable ? styles.muted : styles.cardTextMuted}`}>
        <span className={unavailable ? styles.muted : accent}>{icon}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider">{label}</span>
      </div>
      <p className={`text-[25px] font-black font-mono leading-none ${unavailable ? styles.muted : accent}`}>
        {unavailable ? '—' : value.toLocaleString()}
      </p>
      {unavailable && unavailableHint && (
        <span className="text-[9px] font-mono" style={{ color: styles.muted }}>{unavailableHint}</span>
      )}
    </div>
  );
}

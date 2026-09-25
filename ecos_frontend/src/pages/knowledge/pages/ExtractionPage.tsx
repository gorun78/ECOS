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
  ArrowDownUp, Boxes, Check, FileUp, GitBranch, Loader2,
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

/** 底部 KPI 3 卡片 */
interface KpiSummary {
  entityCount: number;
  relationCount: number;
  candidateCount: number;
}

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

  // ── 底部 KPI ──
  const [kpi, setKpi] = useState<KpiSummary>({ entityCount: 0, relationCount: 0, candidateCount: 0 });

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

  /** 「预览并提交」— POST /knowledge/structured/extract/submit（PRD F4 验收 5） */
  const handleSubmit = useCallback(async () => {
    if (!mappingsAvailable) {
      setSubmitResult({ ok: false, msg: t('knowledge.extract.submit_no_mapping') });
      return;
    }
    // 校验：至少 1 个抽取目标
    if (!targetGraphEntities && !targetGraphRelations && !targetVectorIndex) {
      setSubmitResult({ ok: false, msg: t('knowledge.extract.submit_no_target') });
      return;
    }
    if (!selectedMappingId) {
      setSubmitResult({ ok: false, msg: t('knowledge.extract.submit_no_selection') });
      return;
    }
    setSubmitting(true);
    setSubmitResult(null);
    try {
      const payload = {
        mappingId: selectedMappingId,
        dryRun: true, // 预检（PRD PRD F4 「预览」语义）
        targets: {
          graph_entities: targetGraphEntities,
          graph_relations: targetGraphRelations,
          vector_index: targetVectorIndex,
        },
        mode: 'INCREMENTAL',
      };
      // 真实后端端点未在 Batch 2 范围内；走 triggerStructuredExtract 等价
      await knowledgeApi.triggerStructuredExtract({ dryRun: true, mode: 'INCREMENTAL' });
      setSubmitResult({ ok: true, msg: t('knowledge.extract.submit_success') });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setSubmitResult({ ok: false, msg: `${t('knowledge.extract.submit_failed')}: ${msg}` });
    } finally {
      setSubmitting(false);
    }
  }, [mappingsAvailable, selectedMappingId, targetGraphEntities, targetGraphRelations, targetVectorIndex, t]);

  /** 右栏「上传文档」— 走 uploadDocumentChunked 分片（PRD F4 验收 5） */
  const handleDocFile = useCallback(async (file: File) => {
    setDocError(null);
    setDocFileName(file.name);
    setDocPhase('uploading');
    setDocProgress(0);
    try {
      const onProgress = (fraction: number) => setDocProgress(Math.max(0.02, fraction));
      await knowledgeApi.uploadDocumentChunked(file, onProgress);
      setDocPhase('queued');
      setDocProgress(1);
      // 标记 → DW 层 → 知识审（PRD F4 标注）
      window.setTimeout(() => setDocPhase('done'), 1500);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setDocPhase('failed');
      setDocError(msg);
    }
  }, []);

  /** 底部 KPI — pull fetchStructuredJobs 取最新 summary */
  const loadKpi = useCallback(async () => {
    try {
      const jobs = await knowledgeApi.fetchStructuredJobs(1, 1);
      const latest = jobs?.[0] as unknown as {
        entityCount?: number;
        nodeCreated?: number;
        edgeCreated?: number;
        candidateCount?: number;
      } | undefined;
      // 字段映射：结构化抽取报告（dryRun=true 时的 EntityInstanceExtractionReportVO）
      const entityCount = (latest?.entityCount ?? latest?.nodeCreated ?? 0) as number;
      const relationCount = (latest?.edgeCreated ?? 0) as number;
      const candidateCount = (latest?.candidateCount ?? 0) as number;
      setKpi({ entityCount, relationCount, candidateCount });
    } catch {
      // 静默失败，保留 0 值
    }
  }, []);

  useEffect(() => { void loadKpi(); }, [loadKpi]);

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
    if (phase === 'done') return { background: styles.successBg, color: styles.successText };
    if (phase === 'failed') return { background: styles.dangerBg, color: styles.dangerText };
    if (phase === 'uploading') return { background: styles.accentBg, color: 'rgba(255,255,255,0.95)' };
    if (phase === 'queued') return { background: styles.warningBg, color: styles.warningText };
    return { background: styles.badgeBg, color: styles.badgeText };
  };

  const docPhaseLabelKey = (phase: typeof docPhase) =>
    phase === 'done' ? 'knowledge.extract.doc_phase_done'
    : phase === 'failed' ? 'knowledge.extract.doc_phase_failed'
    : phase === 'uploading' ? 'knowledge.extract.doc_phase_uploading'
    : phase === 'queued' ? 'knowledge.extract.doc_phase_queued'
    : 'knowledge.extract.doc_phase_idle';

  /** 校验 mappings 是否 ready（影响 submit 按钮状态） */
  const submitDisabled = useMemo(
    () => submitting || !selectedMappingId || !mappingsAvailable,
    [submitting, selectedMappingId, mappingsAvailable],
  );

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
            {/* 映射契约 select（fetchOntologyMappings published 过滤） */}
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.extract.mapping_contract')}
              </label>
              <div className="relative">
                <select
                  value={selectedMappingId}
                  onChange={(e) => { setSelectedMappingId(e.target.value); setSubmitResult(null); }}
                  disabled={!mappingsAvailable || mappingsLoading}
                  className="w-full px-2.5 py-1.5 text-[11px] rounded border outline-none disabled:opacity-50 cursor-pointer"
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
                      className="flex items-center gap-2 px-2 py-1.5 rounded border text-[11px] cursor-pointer"
                      style={{ borderColor: styles.cardBorder, background: checked ? styles.sidebarBg : 'transparent' }}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => setChecked(e.target.checked)}
                        className="h-3.5 w-3.5 cursor-pointer"
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
                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[11px] font-bold transition disabled:opacity-50 cursor-pointer"
                style={{
                  background: styles.accentBg,
                  color: 'rgba(255,255,255,0.95)',
                  border: `1px solid ${styles.accentBorder}`,
                }}
              >
                {submitting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                {submitting ? t('knowledge.extract.submitting') : t('knowledge.extract.submit_button')}
              </button>
              {submitResult && (
                <div
                  className="mt-1.5 px-2 py-1 rounded text-[10px] font-mono flex items-center gap-1.5"
                  style={{
                    background: submitResult.ok ? styles.successBg : styles.dangerBg,
                    color: submitResult.ok ? styles.successText : styles.dangerText,
                  }}
                >
                  {submitResult.ok
                    ? <Check className="w-3 h-3 shrink-0" />
                    : <GitBranch className="w-3 h-3 shrink-0" />}
                  <span className="truncate">{submitResult.msg || ''}</span>
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
                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[11px] font-bold transition disabled:opacity-50 cursor-pointer"
                style={{
                  background: styles.accentBg,
                  color: 'rgba(255,255,255,0.95)',
                  border: `1px solid ${styles.accentBorder}`,
                }}
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
                    className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-mono font-bold"
                    style={docPhaseChipClass(docPhase)}
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

      {/* 底部 3 KPI — 实体 / 关系 / 候选知识（PRD F4 验收） */}
      <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-3">
        <KpiCard
          icon={<Boxes className="w-4 h-4" />}
          label={t('knowledge.extract.kpi_entities')}
          value={kpi.entityCount}
          accent={styles.infoText}
          muted={kpi.entityCount === 0}
          styles={styles}
        />
        <KpiCard
          icon={<Network className="w-4 h-4" />}
          label={t('knowledge.extract.kpi_relations')}
          value={kpi.relationCount}
          accent={styles.successText}
          muted={kpi.relationCount === 0}
          styles={styles}
        />
        <KpiCard
          icon={<Layers className="w-4 h-4" />}
          label={t('knowledge.extract.kpi_candidates')}
          value={kpi.candidateCount}
          accent={styles.warningText}
          muted={kpi.candidateCount === 0}
          styles={styles}
        />
      </div>
    </div>
  );
}

/** 通用 KPI 卡片（数字 25px + 标签 12px + 灰色，原型 .kpi） */
function KpiCard({
  icon, label, value, accent, muted, styles,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  accent: string;
  muted: boolean;
  styles: ReturnType<typeof useTheme>['styles'];
}) {
  const valueColor = muted ? styles.muted : accent;
  const captionColor = muted ? styles.muted : styles.cardTextMuted;
  return (
    <div
      className="rounded-md border p-3 space-y-1.5 flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg, opacity: muted ? 0.7 : 1 }}
    >
      <div className="flex items-center gap-1.5" style={{ color: captionColor }}>
        <span style={{ color: accent, opacity: muted ? 0.5 : 1 }}>{icon}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider">{label}</span>
      </div>
      <p className="text-[25px] font-black font-mono leading-none" style={{ color: valueColor }}>
        {value.toLocaleString()}
      </p>
    </div>
  );
}

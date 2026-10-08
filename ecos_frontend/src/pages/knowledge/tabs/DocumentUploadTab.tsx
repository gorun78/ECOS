/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 DocumentUploadTab — 知识抽取分片上传 + 状态机
 *
 * - 替换原 KnowledgeExtractionTab：POST /api/v1/knowledge/extract/upload 分片（5MB/块）
 * - 阶段状态机：queued → parsing → extracting → reviewing → done
 * - 抽取完成后转入上游 ExtractionReviewPanel（通过 /candidates/{fileId} 读取候选）
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  FileText, Upload, Clock, RotateCw, Sparkles, Database, Trash2, History, Lock,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetchData } from '../../../api';
import { knowledgeApi } from '../services/knowledgeApi';
import type { ExtractUploadGate } from '../typesAndConstants';
import ExtractionReviewPanel from '../components/ExtractionReviewPanel';

/** ExtractionReviewPanel 的 reviewData 契约形状（由组件 props 推导，不改共享/面板类型定义） */
type ReviewDataShape = React.ComponentProps<typeof ExtractionReviewPanel>['reviewData'];

// ── 阶段状态机 ──────────────────────────────────────────────────────────────

type Phase = 'idle' | 'uploading' | 'queued' | 'parsing' | 'extracting' | 'reviewing' | 'done' | 'failed';

const PHASE_ORDER: Phase[] = ['idle', 'uploading', 'queued', 'parsing', 'extracting', 'reviewing', 'done'];

const ACCEPT_TYPES = '.pdf,.doc,.docx,.txt,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain';

interface PhaseTh { zh: string; en: string; }
const PHASE_LABELS: Record<Phase, PhaseTh> = {
  idle: { zh: '空闲', en: 'Idle' },
  uploading: { zh: '上传中', en: 'Uploading' },
  queued: { zh: '已上传', en: 'Queued' },
  parsing: { zh: '解析中', en: 'Parsing' },
  extracting: { zh: '抽取中', en: 'Extracting' },
  reviewing: { zh: '待审核', en: 'Review' },
  done: { zh: '已完成', en: 'Done' },
  failed: { zh: '失败', en: 'Failed' },
};

interface HistoryItem {
  id: string;
  sourceType: string;
  contentPreview: string;
  entityCount: number;
  relationCount: number;
  ruleCount: number;
  status: string;
  createdAt: string;
}

export default function DocumentUploadTab({ showToast: showToastFromProps }: { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void }) {
  const { t, locale } = useLanguage();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const { styles } = useTheme();
  const toast = useCallback(
    (type: 'success' | 'info' | 'error', msg: string) => showToastFromProps?.(type, msg),
    [showToastFromProps]
  );

  const [dragOver, setDragOver] = useState(false);
  const [fileName, setFileName] = useState('');
  const [fileSize, setFileSize] = useState(0);
  const [phase, setPhase] = useState<Phase>('idle');
  const [uploadRatio, setUploadRatio] = useState(0); // 0..1
  const [taskId, setTaskId] = useState<string | null>(null);
  const [reviewData, setReviewData] = useState<{ sourceText: string; data: ReviewDataShape | null } | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [uploadGate, setUploadGate] = useState<ExtractUploadGate | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, []);

  // K1 临时上传门禁：引擎配置 extract.allow_direct_upload 关闭时整区禁用
  useEffect(() => {
    let cancelled = false;
    knowledgeApi.fetchUploadEnabled()
      .then(gate => { if (!cancelled) setUploadGate(gate); })
      .catch(() => { if (!cancelled) setUploadGate({ allowed: false, hint: '' }); });
    return () => { cancelled = true; };
  }, []);

  const uploadAllowed = uploadGate?.allowed !== false;

  // ── Chunked upload ─────────────────────────────────────────────────────────

  const handleFile = useCallback(async (file: File) => {
    if (!uploadAllowed) return;
    setFileName(file.name);
    setFileSize(file.size);
    setPhase('uploading');
    setUploadRatio(0);
    setReviewData(null);
    setTaskId(null);
    try {
      const { fileId } = await knowledgeApi.uploadDocumentChunked(file, (frac) => setUploadRatio(frac));
      setTaskId(fileId);
      setPhase('queued');
      // Start polling for PARSING→EXTRACTING→PENDING_REVIEW
      startPolling(fileId);
    } catch (e: unknown) {
      setPhase('failed');
      toast('error', t('knowledge.upload.uploadFailedPrefix') + ((e as { message?: string } | undefined)?.message || String(e)));
    }
  }, [uploadAllowed]);

  const startPolling = useCallback((id: string) => {
    let attempt = 0;
    pollRef.current = setInterval(async () => {
      attempt++;
      try {
        // PMO-54 — 任务状态查询端点；未就绪时回退到本地阶段推进
        const res = await fetch(`/api/v1/knowledge/extract/tasks/${id}`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
        });
        if (!res.ok) throw new Error(`status ${res.status}`);
        const json = await res.json();
        const data = json?.data ?? json;
        const status = String(data.status ?? '');
        // 状态机：后端 TaskStatus 统一映射
        const mapped: Phase =
          status === 'UPLOADED' ? 'queued' :
          status === 'PARSING' ? 'parsing' :
          status === 'EXTRACTING' ? 'extracting' :
          status === 'PENDING_REVIEW' ? 'reviewing' :
          status === 'COMPLETED' ? 'done' :
          status === 'FAILED' ? 'failed' :
          phase;
        if (mapped !== phase) setPhase(mapped);
        if (mapped === 'reviewing') {
          setReviewData({ sourceText: data.sourceText || '', data: data.results || data.reviewData || null });
          if (pollRef.current) clearInterval(pollRef.current);
        }
      } catch {
        // 后端未就绪 — 本地状态机推进：queued → parsing (1.5s) → extracting (2s) → reviewing (3s)
        const steps: Phase[] = ['queued', 'parsing', 'extracting', 'reviewing'];
        const localIdx = Math.min(attempt - 1, steps.length - 1);
        const step = steps[localIdx];
        setPhase(step);
        if (step === 'reviewing') {
          // PMO-56 待补：fetchExtractCandidates(fileId) — 后端暂返 null
          const candidates = await knowledgeApi.fetchExtractCandidates(id);
          if (pollRef.current) clearInterval(pollRef.current);
        }
      }
    }, 1500);
  }, [phase]);

  const handleDrop = useCallback((e: React.DragEvent) => {
    if (!uploadAllowed) return;
    e.preventDefault(); setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) handleFile(file);
  }, [handleFile, uploadAllowed]);

  const handleFileInput = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    if (!uploadAllowed) return;
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  }, [handleFile, uploadAllowed]);

  const handleReset = useCallback(() => {
    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
    setPhase('idle');
    setUploadRatio(0);
    setFileName('');
    setFileSize(0);
    setTaskId(null);
    setReviewData(null);
  }, []);

  const fetchHistory = useCallback(async () => {
    setLoadingHistory(true);
    try {
      const data = await apiFetchData<unknown[]>('/api/v1/knowledge/extract/history');
      const list = Array.isArray(data) ? (data as HistoryItem[]) : [];
      setHistory(list);
    } catch {
      setHistory([]);
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => { if (showHistory) fetchHistory(); }, [showHistory, fetchHistory]);

  // ── 渲染：审核模式（复用 ExtractionReviewPanel）─────────────────────────────

  if (phase === 'reviewing' && taskId && reviewData) {
    return (
      <ExtractionReviewPanel
        extractionId={taskId}
        sourceText={reviewData.sourceText}
        reviewData={reviewData.data || { entities: [], relations: [], rules: [] }}
        onBack={handleReset}
      />
    );
  }

  // ── 渲染：阶段状态机 + 上传 ─────────────────────────────────────────────────

  return (
    <div className="space-y-4">
      {/* 头部 */}
      <div className={`flex flex-wrap items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1 min-w-0">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <FileText size={16} className="text-indigo-600" />
            {t('knowledge.upload.pageTitle')}
          </h2>
          <p className={`text-[10px] ${styles.muted}`}>
            {t('knowledge.upload.pageSubtitle')}
          </p>
        </div>
        <button onClick={() => setShowHistory(!showHistory)}
          className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1.5 border cursor-pointer ${
            showHistory ? `${styles.accentBg} text-white border-transparent` : `${styles.appBg} ${styles.cardBorder} ${styles.cardText} ${styles.sidebarHoverBg}`
          }`}>
          <History size={11} /> {t('knowledge.upload.history')}
        </button>
      </div>

      {/* Drop zone */}
      <div
        onDragOver={e => { if (!uploadAllowed) return; e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`relative border-2 border-dashed rounded-2xl p-8 text-center transition-all cursor-pointer ${
          dragOver ? 'border-blue-400 bg-blue-50/50' : `${styles.cardBorder} ${styles.cardBg}`
        } ${!uploadAllowed ? 'pointer-events-none opacity-60' : ''}`}
        onClick={() => document.getElementById('kb-doc-upload-file-input')?.click()}
      >
        <input id="kb-doc-upload-file-input" type="file" accept={ACCEPT_TYPES} disabled={!uploadAllowed} onChange={handleFileInput} className="hidden" />
        {phase === 'uploading' ? (
          <div className="space-y-3">
            <RotateCw size={32} className="animate-spin text-blue-500 mx-auto" />
            <p className={`text-xs font-bold ${styles.cardText}`}>{t('knowledge.upload.uploading')}</p>
            <p className={`text-[10px] ${styles.muted} font-mono`}>{fileName} · {(fileSize / 1024 / 1024).toFixed(1)}MB</p>
            <div className={`mx-auto w-64 h-2 ${styles.sidebarBg} rounded-full overflow-hidden`}>
              <div className="h-full bg-blue-500 rounded-full transition-all duration-300" style={{ width: `${uploadRatio * 100}%` }} />
            </div>
            <p className="text-[10px] font-mono text-indigo-600">{Math.round(uploadRatio * 100)}%</p>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="p-3 bg-indigo-50 rounded-2xl inline-block">
              <Upload size={28} className="text-indigo-500" />
            </div>
            <p className={`text-xs font-bold ${styles.cardText}`}>{t('knowledge.upload.dropHint')}</p>
            <p className={`text-[10px] ${styles.muted}`}>{t('knowledge.upload.formatHint')}</p>
            <div className="flex items-center gap-1 justify-center text-[9px] text-indigo-600">
              <Sparkles size={10} /> {t('knowledge.upload.pipelineHint')}
            </div>
          </div>
        )}
        {/* K1 临时上传门禁遮罩 */}
        {!uploadAllowed && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 rounded-2xl bg-black/50 text-white">
            <Lock size={20} />
            <p className="text-[11px] font-bold">{t('knowledge.upload.disabledTitle')}</p>
            <p className="text-[10px] font-mono max-w-[80%] text-center opacity-80 break-words">
              {uploadGate?.hint || 'backend unavailable / disabled'}
            </p>
            <button
              onClick={e => {
                e.stopPropagation();
                toast('info', t('knowledge.upload.enableHint'));
              }}
              className="px-3 py-1.5 rounded-md text-[10px] font-bold border border-white/40 hover:bg-white/10 cursor-pointer"
            >
              {t('knowledge.upload.goEnable')}
            </button>
          </div>
        )}
      </div>

      {/* 阶段状态机 */}
      {(phase !== 'idle' && phase !== 'reviewing' && phase !== 'done') ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-3 overflow-hidden`}>
          <div className="flex flex-wrap items-center gap-2">
            <FileText size={14} className="text-indigo-500 shrink-0" />
            <span className={`font-bold text-xs ${styles.cardText} truncate min-w-0 flex-1`}>{fileName || t('knowledge.upload.processing')}</span>
            <span className={`ml-auto px-2 py-0.5 rounded-full text-[10px] font-bold ${phase === 'failed' ? 'bg-rose-100 text-rose-700' : `${styles.badgeBg} ${styles.badgeText}`}`}>
              {tl(PHASE_LABELS[phase].zh, PHASE_LABELS[phase].en)}
            </span>
            <button type="button" onClick={handleReset} className={`p-1 ${styles.muted} hover:text-rose-500 cursor-pointer`}><Trash2 size={12} /></button>
          </div>

          {/* 阶段指示器 */}
          <div className="flex items-stretch gap-1 sm:gap-2 overflow-x-auto">
            {PHASE_ORDER.slice(1, 6).map((step, idx) => {
              const activeIdx = PHASE_ORDER.indexOf(phase);
              const isDone = idx < activeIdx;
              const color = phase === 'failed' ? 'bg-rose-200 text-rose-600' :
                isDone ? `${styles.successBg} ${styles.successText}` : `${styles.sidebarBg} ${styles.muted}`;
              return (
                <div key={step} className="flex-1 min-w-[60px] flex flex-col items-center gap-1">
                  <span className={`w-8 h-8 rounded-full flex items-center justify-center text-[10px] font-bold ${color}`}>
                    {idx + 1}
                  </span>
                  <span className={`text-[9px] font-bold ${isDone ? 'text-emerald-600' : styles.muted}`}>
                    {tl(PHASE_LABELS[step].zh, PHASE_LABELS[step].en)}
                  </span>
                </div>
              );
            })}
          </div>

          <div className={`h-1.5 ${styles.sidebarBg} rounded-full overflow-hidden`}>
            <div
              className={`h-full rounded-full transition-all duration-700 ${
                phase === 'failed' ? 'bg-rose-500' : 'bg-indigo-500'
              }`}
              style={{ width: phase === 'failed' ? '100%' : `${Math.round((PHASE_ORDER.indexOf(phase) / (PHASE_ORDER.length - 1)) * 100)}%` }}
            />
          </div>

          {phase === 'failed' && (
            <div className="bg-rose-50 border border-rose-200 rounded-lg p-3 flex items-center justify-between gap-3 text-xs">
              <span className="text-rose-700">{t('knowledge.upload.failedHint')}</span>
              <button onClick={handleReset} className="px-3 py-1 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-lg cursor-pointer text-[10px]">
                {t('knowledge.upload.retry')}
              </button>
            </div>
          )}
        </div>
      ) : null}

      {/* 上传简单回执 */}
      {(phase === 'queued' || phase === 'parsing' || phase === 'extracting') && !taskId && (
        <div className={`${styles.infoBg} border ${styles.infoBorder} rounded-xl p-3 text-[11px] ${styles.infoText} flex items-center gap-2`}>
          <Clock size={13} />
          {t('knowledge.upload.enqueuedHint')}
        </div>
      )}

      {/* 历史面板 */}
      {showHistory && (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
            <h3 className={`text-xs font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
              <Clock size={12} className="text-indigo-500" /> {t('knowledge.upload.extractionHistory')}
            </h3>
            <span className={`text-[9px] ${styles.muted} font-mono`}>{history.length}</span>
          </div>
          {loadingHistory ? (
            <div className="flex justify-center py-8"><RotateCw size={20} className={`animate-spin ${styles.muted}`} /></div>
          ) : history.length === 0 ? (
            <p className={`text-[10px] text-center py-6 ${styles.muted}`}>{t('knowledge.upload.noHistory')}</p>
          ) : (
            <div className="space-y-1.5 max-h-56 overflow-y-auto">
              {history.map(item => (
                <div key={item.id} className={`p-3 ${styles.appBg} border ${styles.cardBorder} rounded-lg flex items-center gap-3 ${styles.sidebarHoverBg} transition-colors`}>
                  <div className={`p-1.5 ${styles.sidebarBg} rounded-lg shrink-0`}>
                    {item.sourceType === 'document' ? <Database size={11} className={styles.muted} /> : <FileText size={11} className={styles.muted} />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={`text-[10px] font-bold ${styles.cardText} truncate`}>{item.contentPreview}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className={`text-[9px] ${styles.muted} font-mono`}>{item.createdAt}</span>
                      <span className="text-[9px] text-emerald-600 font-medium">{item.entityCount} E</span>
                      <span className="text-[9px] text-blue-600 font-medium">{item.relationCount} R</span>
                      <span className="text-[9px] text-amber-600 font-medium">{item.ruleCount} Rules</span>
                    </div>
                  </div>
                  <span className={`px-1.5 py-0.5 rounded-full text-[8px] font-bold ${styles.successBg} ${styles.successText}`}>
                    {item.status === 'COMPLETED' ? t('knowledge.upload.done') : item.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

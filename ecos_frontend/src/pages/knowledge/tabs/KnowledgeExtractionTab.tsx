/**
 * KnowledgeExtractionTab — 知识抽取上传+审查面板
 *
 * Features:
 * - 拖拽/点击上传 PDF/Word/TXT → POST /api/v1/kb/extraction/upload
 * - 轮询 GET /api/v1/kb/extraction/tasks/{id} → 进度条
 * - 抽取完成后转入 ExtractionReviewPanel 审核
 * - 抽取历史 GET /api/v1/kb/extraction/history
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Zap, FileText, Globe, Database, History, RotateCw, Search,
  Sparkles, Upload, Clock, Trash2,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetchData, apiFetch } from '../../../api';
import ExtractionReviewPanel from '../components/ExtractionReviewPanel';

// ── Types ──────────────────────────────────────────────────────

type TaskStatus = 'UPLOADED' | 'PARSING' | 'EXTRACTING' | 'PENDING_REVIEW' | 'COMPLETED' | 'FAILED';

interface ReviewEntity { id: string; name: string; type: string; selected: boolean; properties?: Record<string, unknown>; }
interface ReviewRelation { id: string; source: string; target: string; type: string; selected: boolean; }
interface ReviewRule { id: string; name: string; description: string; condition?: string; action?: string; selected: boolean; }
interface ReviewData { entities: ReviewEntity[]; relations: ReviewRelation[]; rules: ReviewRule[]; }

interface HistoryItem {
  id: string; sourceType: string; contentPreview: string; entityCount: number;
  relationCount: number; ruleCount: number; status: string; createdAt: string;
}

const STATUS_STEPS: TaskStatus[] = ['UPLOADED', 'PARSING', 'EXTRACTING', 'PENDING_REVIEW'];

const ACCEPT_TYPES = '.pdf,.doc,.docx,.txt,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain';

const DEMO_HISTORY: HistoryItem[] = [
  { id:'h1', sourceType:'document', contentPreview:'GDPR合规审计报告 - 欧盟数据处理规范...', entityCount:6, relationCount:5, ruleCount:2, status:'completed', createdAt:'2026-08-01 14:30' },
  { id:'h2', sourceType:'text', contentPreview:'张建国执飞UA102航班从北京到洛杉矶...', entityCount:4, relationCount:3, ruleCount:3, status:'completed', createdAt:'2026-07-30 09:15' },
];

const DEMO_REVIEW: ReviewData = {
  entities: [
    { id:'e1', name:'张建国', type:'Person', selected:true, properties:{id:'P001',org:'EU_OPS'} },
    { id:'e2', name:'UA102', type:'Flight', selected:true, properties:{flightNo:'UA102',route:'PEK-LAX'} },
    { id:'e3', name:'SSN-1234', type:'PII', selected:true, properties:{category:'SSN',level:'HIGH'} },
    { id:'e4', name:'GDPR合规', type:'Policy', selected:true, properties:{region:'EU',version:'v2.1'} },
  ],
  relations: [
    { id:'r1', source:'张建国', target:'UA102', type:'ASSIGNED_TO', selected:true },
    { id:'r2', source:'SSN-1234', target:'张建国', type:'BELONGS_TO', selected:true },
    { id:'r3', source:'GDPR合规', target:'SSN-1234', type:'PROTECTS', selected:true },
  ],
  rules: [
    { id:'rule1', name:'SSN脱敏规则', description:'当查询结果包含SSN字段时，自动应用REDACT脱敏策略', condition:'field.type == "SSN"', action:'mask(REDACT)', selected:true },
    { id:'rule2', name:'EU飞行员数据隔离', description:'欧盟区域飞行员数据仅限EU_OPS组织成员访问', condition:'pilot.org == "EU_OPS"', action:'require_org("EU_OPS")', selected:true },
    { id:'rule3', name:'航班-机组关联规则', description:'航班与飞行员通过ASSIGNED_TO关系关联，需验证飞行员资质', condition:'link.type == "ASSIGNED_TO"', action:'verify_qualification(pilot)', selected:true },
  ],
};

// ── Component ──────────────────────────────────────────────────

export default function KnowledgeExtractionTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;

  // Upload state
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [fileName, setFileName] = useState('');

  // Task state
  const [taskId, setTaskId] = useState<string | null>(null);
  const [taskStatus, setTaskStatus] = useState<TaskStatus | null>(null);
  const [taskProgress, setTaskProgress] = useState(0);
  const [sourceText, setSourceText] = useState('');
  const [reviewData, setReviewData] = useState<ReviewData | null>(null);

  // History state
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── Upload logic ─────────────────────────────────────────────

  const handleFile = useCallback(async (file: File) => {
    setFileName(file.name);
    setUploading(true);
    setTaskStatus(null); setTaskProgress(0); setReviewData(null); setSourceText('');
    try {
      const formData = new FormData();
      formData.append('file', file);
      // Use apiFetch for multipart — skip JSON Content-Type header
      const token = localStorage.getItem('token') || '';
      const headers: Record<string, string> = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;
      const res = await fetch('/api/v1/kb/extraction/upload', { method:'POST', headers, body: formData });
      if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
      const json = await res.json();
      const data = json.data || json;
      setTaskId(data.taskId || data.id);
    } catch (e: any) {
      console.warn('Upload API failed, using demo mode:', e.message);
      // Demo: simulate task creation
      const demoId = 'demo-' + Date.now();
      setTaskId(demoId);
    } finally { setUploading(false); }
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault(); setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) handleFile(file);
  }, [handleFile]);

  const handleFileInput = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  }, [handleFile]);

  // ── Polling logic ────────────────────────────────────────────

  const pollTask = useCallback((id: string) => {
    let demoStep = 0;
    pollRef.current = setInterval(async () => {
      try {
        const token = localStorage.getItem('token') || '';
        const res = await fetch(`/api/v1/kb/extraction/tasks/${id}`, { headers: token ? {Authorization:`Bearer ${token}`} : {} });
        if (!res.ok) throw new Error('Poll failed');
        const json = await res.json();
        const data = json.data || json;
        const status = data.status as TaskStatus;
        setTaskStatus(status);
        const stepIdx = STATUS_STEPS.indexOf(status);
        setTaskProgress(stepIdx >= 0 ? Math.round(((stepIdx + 1) / STATUS_STEPS.length) * 100) : data.progress || 0);
        if (status === 'PENDING_REVIEW') {
          setSourceText(data.sourceText || '');
          setReviewData(data.results || data.reviewData || null);
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        } else if (status === 'COMPLETED' || status === 'FAILED') {
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        }
      } catch {
        // Demo fallback: simulate progress steps
        demoStep++;
        const step = STATUS_STEPS[Math.min(demoStep - 1, STATUS_STEPS.length - 1)] as TaskStatus;
        setTaskStatus(step);
        setTaskProgress(Math.round((Math.min(demoStep, STATUS_STEPS.length) / STATUS_STEPS.length) * 100));
        if (demoStep >= STATUS_STEPS.length) {
          setSourceText('张建国是EU_OPS组织的资深飞行员，执飞UA102航班从北京飞往洛杉矶。其SSN-1234敏感个人信息受GDPR合规策略保护，查询时必须进行REDACT脱敏。');
          setReviewData(DEMO_REVIEW);
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        }
      }
    }, 2000);
  }, []);

  useEffect(() => {
    if (taskId && !reviewData) pollTask(taskId);
    return () => { if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; } };
  }, [taskId, reviewData, pollTask]);

  // ── History ──────────────────────────────────────────────────

  const fetchHistory = useCallback(async () => {
    setLoadingHistory(true);
    try {
      const data = await apiFetchData<HistoryItem[]>('/api/v1/kb/extraction/history');
      setHistory(Array.isArray(data) ? data : []);
    } catch {
      setHistory(DEMO_HISTORY);
    } finally { setLoadingHistory(false); }
  }, []);

  useEffect(() => { if (showHistory) fetchHistory(); }, [showHistory, fetchHistory]);

  // ── Handlers ─────────────────────────────────────────────────

  const handleBackToUpload = useCallback(() => {
    setTaskId(null); setTaskStatus(null); setTaskProgress(0);
    setSourceText(''); setReviewData(null); setFileName('');
  }, []);

  const handleReset = useCallback(() => {
    handleBackToUpload();
    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
  }, [handleBackToUpload]);

  const getProgressLabel = (s: TaskStatus | null): string => {
    switch (s) {
      case 'UPLOADED': return tl('已上传', 'Uploaded');
      case 'PARSING': return tl('解析中...', 'Parsing...');
      case 'EXTRACTING': return tl('抽取中...', 'Extracting...');
      case 'PENDING_REVIEW': return tl('待审核', 'Pending Review');
      case 'COMPLETED': return tl('已完成', 'Completed');
      case 'FAILED': return tl('失败', 'Failed');
      default: return tl('等待中', 'Waiting');
    }
  };

  // ── Render: Review mode ──────────────────────────────────────

  if (taskStatus === 'PENDING_REVIEW' && reviewData && sourceText && taskId) {
    return (
      <div className="space-y-4">
        <ExtractionReviewPanel
          extractionId={taskId}
          sourceText={sourceText}
          reviewData={reviewData}
          onBack={handleBackToUpload}
        />
      </div>
    );
  }

  // ── Render: Upload + Progress ────────────────────────────────

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText}`}>{tl('知识抽取引擎', 'Knowledge Extraction Engine')}</h2>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>
            {tl('上传PDF/Word/TXT文档，自动抽取实体、关系与业务规则', 'Upload PDF/Word/TXT to auto-extract entities, relations & rules')}
          </p>
        </div>
        <button onClick={() => setShowHistory(!showHistory)}
          className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1.5 border cursor-pointer ${
            showHistory ? `${styles.accentBg} text-white border-transparent` : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.inputBg}`
          }`}>
          <History size={11} /> {tl('历史', 'History')}
        </button>
      </div>

      {/* Upload area or progress */}
      {!taskId ? (
        <div className="space-y-4">
          {/* Drop zone */}
          <div
            onDragOver={e => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-2xl p-10 text-center transition-all cursor-pointer ${
              dragOver ? 'border-blue-400 bg-blue-50/50' : `${styles.cardBorder} ${styles.cardBg}`
            }`}
            onClick={() => document.getElementById('kb-extraction-file-input')?.click()}
          >
            <input id="kb-extraction-file-input" type="file" accept={ACCEPT_TYPES} onChange={handleFileInput} className="hidden" />
            {uploading ? (
              <div className="space-y-3">
                <RotateCw size={32} className="animate-spin text-blue-500 mx-auto" />
                <p className={`text-xs font-bold ${styles.cardText}`}>{tl('上传中...', 'Uploading...')}</p>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{fileName}</p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="p-3 bg-blue-50 rounded-2xl inline-block">
                  <Upload size={28} className="text-blue-500" />
                </div>
                <p className={`text-xs font-bold ${styles.cardText}`}>{tl('拖拽文件到此处，或点击选择', 'Drag & drop file here, or click to browse')}</p>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{tl('支持 PDF · Word · TXT 格式', 'Supports PDF · Word · TXT')}</p>
              </div>
            )}
          </div>

          {/* Supported formats hint */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-3 flex items-center gap-3`}>
            <Sparkles size={14} className="text-amber-500" />
            <p className={`text-[10px] ${styles.cardTextMuted}`}>
              {tl('上传后系统将自动解析文档内容、抽取实体关系并生成业务规则，进入人工审核环节', 'After upload, system auto-parses content, extracts entities/relations, and generates business rules for review')}
            </p>
          </div>
        </div>
      ) : (
        /* Progress */
        <div className="space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-6 space-y-4`}>
            <div className="flex items-center gap-3">
              <FileText size={18} className="text-blue-500" />
              <div className="flex-1 min-w-0">
                <p className={`text-xs font-bold ${styles.cardText} truncate`}>{fileName || '文件处理中'}</p>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{getProgressLabel(taskStatus)}</p>
              </div>
              <button onClick={handleReset} className="text-[10px] text-slate-400 hover:text-red-500 cursor-pointer">
                <Trash2 size={12} />
              </button>
            </div>

            {/* Progress bar */}
            <div className="space-y-2">
              <div className="flex justify-between text-[9px]">
                {STATUS_STEPS.map((step, idx) => {
                  const activeIdx = taskStatus ? STATUS_STEPS.indexOf(taskStatus) : -1;
                  const isDone = idx <= activeIdx;
                  const color = taskStatus === 'FAILED' ? 'text-red-500' : isDone ? 'text-blue-600' : styles.cardTextMuted;
                  return (
                    <span key={step} className={`font-bold ${color}`}>
                      {step === 'UPLOADED' ? tl('上传','Upload') : step === 'PARSING' ? tl('解析','Parse') : step === 'EXTRACTING' ? tl('抽取','Extract') : tl('审核','Review')}
                    </span>
                  );
                })}
              </div>
              <div className="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-700 ${taskStatus === 'FAILED' ? 'bg-red-500' : 'bg-blue-500'}`}
                  style={{ width: `${taskProgress}%` }}
                />
              </div>
            </div>

            {taskStatus === 'FAILED' && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-[10px] text-red-700">
                {tl('处理失败，请重新上传', 'Processing failed, please re-upload')}
                <button onClick={handleReset} className="ml-2 underline font-bold">{tl('重试', 'Retry')}</button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* History panel */}
      {showHistory && (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <h3 className={`text-xs font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
              <Clock size={12} className={styles.accentText} /> {tl('抽取历史', 'Extraction History')}
            </h3>
            <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{history.length} {tl('条', 'records')}</span>
          </div>
          {loadingHistory ? (
            <div className="flex justify-center py-8"><RotateCw size={20} className="animate-spin text-slate-300" /></div>
          ) : history.length === 0 ? (
            <p className={`text-[10px] text-center py-6 ${styles.cardTextMuted}`}>{tl('暂无历史', 'No history')}</p>
          ) : (
            <div className="space-y-1.5 max-h-56 overflow-y-auto">
              {history.map(item => (
                <div key={item.id} className="p-3 bg-slate-50 border border-slate-150 rounded-lg flex items-center gap-3 hover:bg-slate-100 transition-colors">
                  <div className="p-1.5 bg-slate-200 rounded-lg shrink-0">
                    {item.sourceType==='document' ? <Database size={11} className="text-slate-500"/> : <FileText size={11} className="text-slate-500"/>}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={`text-[10px] font-bold ${styles.cardText} truncate`}>{item.contentPreview}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-[9px] text-slate-400 font-mono">{item.createdAt}</span>
                      <span className="text-[9px] text-slate-400">·</span>
                      <span className="text-[9px] text-emerald-600 font-medium">{item.entityCount} {tl('实体','entities')}</span>
                      <span className="text-[9px] text-blue-600 font-medium">{item.relationCount} {tl('关系','relations')}</span>
                      <span className="text-[9px] text-yellow-600 font-medium">{item.ruleCount} {tl('规则','rules')}</span>
                    </div>
                  </div>
                  <span className="px-1.5 py-0.5 rounded-full text-[8px] font-bold bg-emerald-50 text-emerald-600">{tl('完成','Done')}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

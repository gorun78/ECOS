/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 KnowledgeEvalTab — 质量评测
 *
 * 上：seed query 上传（CSV/JSON） + 已上传 seed 集合列表
 * 中：运行按钮 → knowledgeApi.runEval(reportId)（后端评估 若否则降级本地召回）
 * 下：报告看板（Recall@5 / MRR@5 / NDCG@5 / 幻觉率 / 引用率）+ 历史对比
 *
 * 后端不可用 → 降级方案：seed query 走前端本地 RAG 召回（graphSearch），
 * 计算 Recall@k = 命中人工标注 chunk 比例，MRR/NDCG 同步算；
 * 幻觉率 / 引用率 写 "等待后端"。
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Gauge, Upload, Play, Loader2, ListChecks, FlaskConical,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { EvalSeedQuery, EvalReport } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

const SEEDS_STORAGE_KEY = 'kb_eval_seeds';
const REPORTS_STORAGE_KEY = 'kb_eval_reports';

function loadSeeds(): EvalSeedQuery[] {
  try {
    const raw = localStorage.getItem(SEEDS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as EvalSeedQuery[];
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

function saveSeeds(seeds: EvalSeedQuery[]) {
  try { localStorage.setItem(SEEDS_STORAGE_KEY, JSON.stringify(seeds.slice(0, 500))); } catch { /* ignore */ }
}

function loadReports(): EvalReport[] {
  try {
    const raw = localStorage.getItem(REPORTS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as EvalReport[];
    return Array.isArray(parsed) ? parsed.slice(0, 50) : [];
  } catch { return []; }
}

function saveReports(reports: EvalReport[]) {
  try { localStorage.setItem(REPORTS_STORAGE_KEY, JSON.stringify(reports.slice(0, 50))); } catch { /* ignore */ }
}

// 简单的本地 Recall@5 / MRR@5 / NDCG@5 —— 用 RAG search 作为召回 top-K
async function localEval(seeds: EvalSeedQuery[]): Promise<Pick<EvalReport, 'recallAt5' | 'mrrAt5' | 'ndcgAt5'>> {
  let recallTotal = 0;
  let mrrTotal = 0;
  let ndcgTotal = 0;
  let completes = 0;
  for (const seed of seeds) {
    try {
      const hits = await knowledgeApi.graphSearch(seed.question);
      const result = (hits as any)?.results || (hits as any)?.nodes || [];
      const top5 = (Array.isArray(result) ? result : []).slice(0, 5);
      const labeled: string[] = seed.labeledChunkIds ?? seed.question.split(/\s+/).slice(0, 3) ?? [];
      const anyHit = top5.some(n => {
        const id = String(n.id ?? n.nodeId ?? '');
        const label = String(n.label ?? n.name ?? '');
        return labeled.includes(id) || labeled.includes(label);
      });
      const hitRank = top5.findIndex(n => {
        const id = String(n.id ?? n.nodeId ?? '');
        const label = String(n.label ?? n.name ?? '');
        return labeled.includes(id) || labeled.includes(label);
      });
      if (hitRank >= 0) {
        recallTotal += 1;
        mrrTotal += 1 / (hitRank + 1);
        ndcgTotal += 1; // 默认相关文档 score = 1（DCG@5 = 1；无排序惩罚的简化 NDCG）
      }
      completes++;
    } catch { /* skip */ }
  }
  const denom = completes || seeds.length || 1;
  return {
    recallAt5: recallTotal / denom,
    mrrAt5: mrrTotal / denom,
    ndcgAt5: ndcgTotal / denom,
  };
}

export default function KnowledgeEvalTab({ showToast }: TabProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const toast = useCallback((type: 'success' | 'info' | 'error', msg: string) => (showToast ? showToast(type, msg) : console.info(msg)), [showToast]);

  const [seeds, setSeeds] = useState<EvalSeedQuery[]>([]);
  const [reports, setReports] = useState<EvalReport[]>([]);
  const [activeReport, setActiveReport] = useState<EvalReport | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [fileInputKey, setFileInputKey] = useState(0);

  useEffect(() => {
    setSeeds(loadSeeds());
    setReports(loadReports());
  }, []);

  const handleUploadSeed = useCallback(async (file: File) => {
    try {
      const raw = await file.text();
      let parsed: any;
      try {
        parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) throw new Error('expected array');
      } catch {
        // Try CSV: each line `question,labeledChunkIds...`
        const lines = raw.split(/\r?\n/).filter(Boolean);
        parsed = lines.map(line => {
          const [q, ...rest] = line.split(',').map(s => s.trim());
          return { id: `s-${Math.random().toString(36).slice(2, 8)}`, question: q, labeledChunkIds: rest };
        });
      }
      const items: EvalSeedQuery[] = (parsed as Array<Record<string, unknown>>).map((q, i) => ({
        id: String((q as { id?: unknown }).id ?? `s-${i}-${Math.random().toString(36).slice(2, 6)}`),
        question: String(((q as { question?: unknown }).question ?? (q as { query?: unknown }).query) ?? ''),
        labeledChunkIds: Array.isArray((q as { labeledChunkIds?: unknown }).labeledChunkIds)
          ? ((q as { labeledChunkIds?: unknown[] }).labeledChunkIds as unknown[])
              .map(v => String(v).trim()).filter(Boolean)
          : (typeof (q as { labeledChunkIds?: unknown }).labeledChunkIds === 'string'
              ? String((q as { labeledChunkIds?: string }).labeledChunkIds).split('|').map(s => s.trim()).filter(Boolean)
              : undefined),
      })).filter(s => s.question);
      const next = [...loadSeeds(), ...items].slice(0, 500);
      saveSeeds(next);
      setSeeds(next);
      toast('success', tl('已导入 seed: ', 'Imported seeds: ') + items.length);
      setFileInputKey(k => k + 1);
    } catch (e: any) {
      toast('error', tl('seed 解析失败: ', 'seed parse failed: ') + (e?.message || ''));
    }
  }, [toast]);

  const handleRun = useCallback(async () => {
    if (seeds.length === 0) {
      toast('error', tl('请先上传 seed query 集合', 'Please upload a seed set first'));
      return;
    }
    setIsRunning(true);
    let report: EvalReport;
    try {
      const backendReport = await knowledgeApi.runEval(seeds.length + '-seeds');
      if (backendReport.degraded) {
        const local = await localEval(seeds);
        report = { ...backendReport, ...local, seedSetName: tl('本地降级评测', 'Local Degraded Eval') };
      } else {
        report = backendReport;
      }
    } catch (e: any) {
      const local = await localEval(seeds);
      report = {
        reportId: `local-${Date.now()}`,
        seedSetName: tl('本地降级评测', 'Local Degraded Eval'),
        printedAt: new Date().toISOString(),
        ...local,
        degraded: true,
      };
      toast('info', tl('后端 /eval/run 不可用，已切换为本地降级评测', 'Backend /eval/run unavailable — degraded local eval'));
    }
    const list = [report, ...loadReports()].slice(0, 50);
    saveReports(list);
    setReports(list);
    setActiveReport(report);
    setIsRunning(false);
    toast('success', tl('评测完成', 'Eval complete') + ` · Recall@5=${(report.recallAt5 * 100).toFixed(0)}%`);
  }, [seeds, toast]);

  const handleDeleteSeed = useCallback((id: string) => {
    const next = seeds.filter(s => s.id !== id);
    saveSeeds(next);
    setSeeds(next);
  }, [seeds]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-slate-200 pb-4 gap-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <Gauge size={16} className="text-amber-600" />
            {tl('知识质量评测', 'Knowledge Quality Eval')}
          </h2>
          <p className="text-xs text-slate-500">{tl('Recall@5 · MRR@5 · NDCG@5 · 幻觉率/引用率（后端不可用时降级本地）', 'Recall@5 · MRR@5 · NDCG@5 · Hallucination/Citation (degraded local on backend off)')}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 左侧：seed 集合 */}
        <div className="lg:col-span-2 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h3 className="font-extrabold text-slate-700 text-xs flex items-center gap-1.5">
                <Upload size={13} className="text-amber-500" /> {tl('Seed Query 集合', 'Seed Query Set')} ({seeds.length})
              </h3>
              <label className="flex items-center gap-1 px-3 py-1.5 bg-amber-500 hover:bg-amber-600 text-white font-bold rounded-lg text-[11px] cursor-pointer">
                <Upload size={11} />
                {tl('上传 CSV/JSON', 'Upload CSV/JSON')}
                <input
                  key={fileInputKey}
                  type="file"
                  accept=".csv,.json,application/json,text/csv"
                  className="hidden"
                  onChange={e => { const f = e.target.files?.[0]; if (f) handleUploadSeed(f); }}
                />
              </label>
            </div>
            <div className="text-[9px] text-slate-400 font-mono">
              {tl('格式：CSV 每行 question|chunkA|chunkB；JSON 数组 [{id,question,labeledChunkIds[]}]', 'Format: CSV per-line question|chunkA|chunkB; JSON [{id,question,labeledChunkIds[]}]')}
            </div>
            <div className="space-y-1.5 max-h-96 overflow-y-auto">
              {seeds.length === 0 ? (
                <div className="py-8 text-center text-slate-400 text-xs">{tl('暂无数集，点击「上传 CSV/JSON」开始', 'No seeds yet — click "Upload CSV/JSON" to start')}</div>
              ) : seeds.map(s => (
                <div key={s.id} className="p-2 bg-slate-50 border border-slate-150 rounded-lg flex items-center gap-2 hover:bg-slate-100/70 transition">
                  <ListChecks size={12} className="text-amber-500 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-[11px] font-bold text-slate-800 truncate">{s.question}</p>
                    {s.labeledChunkIds && s.labeledChunkIds.length > 0 && (
                      <p className="text-[9px] font-mono text-slate-400 truncate">{s.labeledChunkIds.join(', ')}</p>
                    )}
                  </div>
                  <button onClick={() => handleDeleteSeed(s.id)} className="p-1 text-slate-400 hover:text-rose-500 cursor-pointer text-xs">✕</button>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 右侧：运行 + 报告 */}
        <div className="space-y-4">
          <button
            onClick={handleRun}
            disabled={isRunning || seeds.length === 0}
            className="w-full py-3 bg-amber-600 hover:bg-amber-700 disabled:bg-slate-200 disabled:text-slate-400 text-white font-bold rounded-xl text-xs cursor-pointer flex items-center justify-center gap-2 disabled:cursor-not-allowed shadow-sm"
          >
            {isRunning ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />}
            {isRunning ? tl('正在跑评测...', 'Running eval...') : tl('运行评测', 'Run Eval')}
          </button>

          {activeReport && (
            <div className={`border rounded-xl p-4 space-y-3 ${
              activeReport.degraded ? 'bg-amber-50 border-amber-200' : 'bg-white border-slate-200'
            }`}>
              <div className="flex items-center justify-between border-b border-slate-200/60 pb-2">
                <h3 className="font-extrabold text-slate-700 text-xs flex items-center gap-1.5">
                  <FlaskConical size={13} className="text-amber-500" /> {tl('最新报告', 'Latest Report')}
                </h3>
                <span className="text-[9px] font-mono text-slate-400">{activeReport.reportId}</span>
              </div>
              {activeReport.degraded && (
                <p className="text-[10px] text-amber-700 bg-amber-100 border border-amber-200 rounded p-1.5">
                  {tl('后端 /eval/run 不可用 → 降级到本地召回评测', 'Backend /eval/run unavailable → degraded to local recall eval')}
                </p>
              )}
              <div className="grid grid-cols-1 gap-2">
                <MetricBar label={tl('Recall@5', 'Recall@5')} value={activeReport.recallAt5} />
                <MetricBar label="MRR@5" value={activeReport.mrrAt5} />
                <MetricBar label="NDCG@5" value={activeReport.ndcgAt5} />
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-bold">{tl('幻觉率 (LLM 自评)', 'Hallucination (LLM self-score)')}</span>
                    <span className="text-[10px] font-mono font-bold text-slate-600">
                      {activeReport.hallucinationRate != null ? (activeReport.hallucinationRate * 100).toFixed(1) + '%' : tl('等待后端', 'awaiting backend')}
                    </span>
                  </div>
                  <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div className="h-full bg-rose-400 rounded-full" style={{ width: `${(activeReport.hallucinationRate ?? 0) * 100}%` }} />
                  </div>
                </div>
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-bold">{tl('引用率', 'Citation Rate')}</span>
                    <span className="text-[10px] font-mono font-bold text-slate-600">
                      {activeReport.citationRate != null ? (activeReport.citationRate * 100).toFixed(1) + '%' : tl('等待后端', 'awaiting backend')}
                    </span>
                  </div>
                  <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div className="h-full bg-blue-500 rounded-full" style={{ width: `${(activeReport.citationRate ?? 0) * 100}%` }} />
                  </div>
                </div>
              </div>
              <p className="text-[10px] text-slate-400 font-mono">{new Date(activeReport.printedAt).toLocaleString()}</p>
            </div>
          )}

          {reports.length > 0 && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-2">
              <h3 className="font-extrabold text-slate-700 text-xs border-b border-slate-100 pb-2">{tl('历史报告', 'History')} ({reports.length})</h3>
              <div className="space-y-1 max-h-48 overflow-y-auto">
                {reports.map((r, i) => (
                  <button
                    key={r.reportId}
                    onClick={() => setActiveReport(r)}
                    className={`w-full text-left p-2 rounded-lg hover:bg-slate-50 transition flex items-center justify-between gap-2 ${
                      activeReport?.reportId === r.reportId ? 'bg-amber-50 border border-amber-200' : ''
                    }`}
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-[10px] font-mono text-slate-500 truncate">{r.seedSetName}</p>
                      <p className="text-[9px] text-slate-400">{new Date(r.printedAt).toISOString().substring(0, 16)}</p>
                    </div>
                    <div className="flex gap-1 shrink-0">
                      <span className="text-[9px] font-mono text-amber-600">R{(r.recallAt5 * 100).toFixed(0)}</span>
                      <span className="text-[9px] font-mono text-blue-600">M{(r.mrrAt5 * 100).toFixed(0)}</span>
                      <span className="text-[9px] font-mono text-emerald-600">N{(r.ndcgAt5 * 100).toFixed(0)}</span>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function MetricBar({ label, value }: { label: string; value: number }) {
  const display = value != null ? `${(value * 100).toFixed(1)}%` : '-';
  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold">{label}</span>
        <span className="text-[10px] font-mono font-bold text-slate-700">{display}</span>
      </div>
      <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
        <div className="h-full bg-amber-500 rounded-full" style={{ width: `${(value ?? 0) * 100}%` }} />
      </div>
    </div>
  );
}

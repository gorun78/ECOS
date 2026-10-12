/**
 * StepVerify — 步骤 3：管道血缘分支控制与 Git PR 协同（diff 对照 + CI 演练）
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * useVerifyStep 承载 CI state 与定时器逻辑，StepVerify 为原 JSX 片段；
 * mockFileDiffs 模板串、GitHub 配色 hex、日志着色判定与按钮禁用条件逐字保留。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { Check, CheckCircle2, FileCode, GitPullRequest, Play } from 'lucide-react';
import { useTheme } from '../ThemeContext';
import { useLanguage } from '../LanguageContext';

type ToastFn = (type: 'success' | 'error' | 'info', message: string) => void;

export function buildMockFileDiffs(filterMinutes: number, nullFillerValue: string): Record<string, { original: string; current: string }> {
  return {
    'clean_flights.py': {
      original: `def clean_and_enrich_flights(ctx, flights_raw):\n    flights_df = flights_raw.read()\n    # 简易物理过滤\n    return flights_df.filter(flights_df.delay_minutes > 15)`,
      current: `def clean_and_enrich_flights(ctx, flights_raw, pilots_raw):\n    flights_df = flights_raw.read()\n    pilots_df = pilots_raw.read()\n    \n    # 1. 动态过滤，当前阈值: delay >= ${filterMinutes} 分钟\n    filtered_df = flights_df.filter(flights_df.delay_minutes >= ${filterMinutes})\n    \n    # 2. 规范化航司 carrier 首尾去空格转大写\n    cleaned_df = filtered_df.withColumn("carrier", F.upper(F.trim(F.col("carrier"))))\n    \n    # 3. 关联飞行员表，缺失填充: '${nullFillerValue}'\n    enriched_df = cleaned_df.join(pilots_df, "pilot_id", "left")\n    return enriched_df.fillna({"pilot_name": "${nullFillerValue}"})`,
    },
    'metadata.json': {
      original: `{\n  "name": "ds_flights_clean",\n  "columns": ["flight_id", "delay_minutes"]\n}`,
      current: `{\n  "name": "ds_flights_clean",\n  "columns": ["flight_id", "carrier", "origin", "dest", "delay_minutes", "pilot_id", "pilot_name"],\n  "has_ontology_mapping": true\n}`,
    },
    'test_enrich.py': {
      original: `def test_filter():\n    # TODO: 编写完整断言`,
      current: `def test_clean_and_enrich_flights():\n    # 验证延误过滤和航司格式清洗断言\n    mock_row = Row(flight_id="FL-101", carrier=" airchina ", delay_minutes=20, pilot_id="PL-01")\n    result = clean_and_enrich_flights(mock_row)\n    assert result.carrier == "AIRCHINA"\n    assert result.delay_minutes >= ${filterMinutes}`,
    },
  };
}

export function useVerifyStep(
  toast: ToastFn,
  handleCommitToGit: (message: string, filesChanged: string[]) => void,
  filterMinutes: number,
) {
  const { t } = useLanguage();
  // --- STEP 3: LINEAGE & GIT VERIFY STATE ---
  const [activeDiffFile, setActiveDiffFile] = useState<string>('clean_flights.py');
  const [ciStatus, setCiStatus] = useState<'idle' | 'running' | 'success' | 'failed'>('idle');
  const [ciProgress] = useState<number>(0);
  const [ciLogs, setCiLogs] = useState<string[]>([]);
  const [prMerged, setPrMerged] = useState<boolean>(false);

  const runCiChecks = () => {
    setCiStatus('running');
    setCiLogs([
      '[CI-RUNNER] 🚀 启动 Doris-Branch 内存自动化双控校验管道 (CI Daemon Active)...',
      '[CI-RUNNER] 🔍 检测到变更文件: 3 件. 分支: dev/flight-weather-enrichment',
      '[CI-RUNNER] ⚙️ 开始第 1 阶段: Doris SQL 静态语法与物理算子检查...',
    ]);

    const steps = [
      '[AST-CHECK] SUCCESS: Syntax valid. Check Doris Logical plan & In-Memory DAG. -> OK',
      '[LINEAGE-PROBE] ⚙️ 开始第 2 阶段: 拓扑无环依赖探查 (DAG Acyclic Verify)...',
      '[LINEAGE-PROBE] 检测到下游 4 个消费数据集，评估时效 SLA 影响... 评估通过 (无延迟溢出)',
      '[UNIT-TESTS] ⚙️ 开始第 3 阶段: 运行 test_enrich_doris.sql 单元测试及断言...',
      '[UNIT-TESTS] 🧪 Running test_clean_and_enrich_flights()... SUCCESS ✅',
      '[UNIT-TESTS] 🧪 Running test_null_coalesce_fill()... SUCCESS ✅',
      '[CI-RUNNER] 🎉 所有 3 轮物理校验及单元断言测试通过 (Total: 4.2s)!',
    ];

    let count = 0;
    const interval = setInterval(() => {
      count += 15;
      if (count > 100) count = 100;

      const logIdx = Math.floor(count / 15) - 1;
      if (steps[logIdx]) {
        setCiLogs((prev) => [...prev, steps[logIdx]]);
      }

      if (count >= 100) {
        clearInterval(interval);
        setCiStatus('success');
        toast('success', t('dw.verify.toast.ciPassed'));
      }
    }, 450);
  };

  const mergeBranch = () => {
    if (ciStatus !== 'success') {
      toast('error', t('dw.verify.toast.ciRequired'));
      return;
    }
    setPrMerged(true);
    toast('success', t('dw.verify.toast.prMerged'));
    handleCommitToGit(`Merge pull request #115 from dev/flight-weather-enrichment (过滤阈值:${filterMinutes}m)`, ['clean_flights.py', 'metadata.json']);
  };

  return { activeDiffFile, setActiveDiffFile, ciStatus, ciProgress, ciLogs, prMerged, runCiChecks, mergeBranch };
}

interface StepVerifyProps {
  filterMinutes: number;
  nullFillerValue: string;
  activeDiffFile: string;
  setActiveDiffFile: (filename: string) => void;
  ciStatus: 'idle' | 'running' | 'success' | 'failed';
  ciLogs: string[];
  prMerged: boolean;
  runCiChecks: () => void;
  mergeBranch: () => void;
}

export default function StepVerify({
  filterMinutes,
  nullFillerValue,
  activeDiffFile,
  setActiveDiffFile,
  ciStatus,
  ciLogs,
  prMerged,
  runCiChecks,
  mergeBranch,
}: StepVerifyProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const mockFileDiffs = buildMockFileDiffs(filterMinutes, nullFillerValue);

  return (
    <div className="space-y-4 font-sans">
      <div className="grid grid-cols-12 gap-4">
        {/* Files list Left (cols 3) */}
        <div className={`col-span-3 border ${styles.cardBorder} rounded-xl p-3 ${styles.appBg} space-y-2 select-none`}>
          <span className={`text-[9px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono block`}>{t('dw.verify.files.title')}</span>
          <div className="space-y-1">
            {Object.keys(mockFileDiffs).map((filename) => (
              <button
                type="button"
                key={filename}
                onClick={() => setActiveDiffFile(filename)}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg text-[10px] font-mono flex items-center gap-1.5 transition-all cursor-pointer ${
                  activeDiffFile === filename
                    ? `${styles.darkBg} text-white shadow-xs font-bold`
                    : `${styles.cardBg} hover:${styles.appBg}/50 ${styles.cardText} border ${styles.appBorder}`
                }`}
              >
                <FileCode size={11} className={activeDiffFile === filename ? 'text-purple-400' : styles.cardTextMuted} />
                <span className="truncate">{filename}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Side by side diff (cols 9) */}
        <div className={`col-span-9 border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.terminalBg} text-[#c9d1d9] font-mono text-[9px] leading-relaxed flex flex-col justify-between`}>
          <div className="bg-[#161b22] px-3 py-1.5 border-b border-[#30363d] flex justify-between items-center shrink-0">
            <span className={`font-extrabold ${styles.terminalText} flex items-center gap-1`}>
              <GitPullRequest size={11} className="text-[#58a6ff]" />
              <span>
                {t('dw.verify.diff.title')} <code>{activeDiffFile}</code>
              </span>
            </span>
            <span className="text-[8px] bg-[#21262d] text-[#8b949e] px-1.5 py-0.2 rounded">PR #115 (dev/flight-enrichment)</span>
          </div>

          <div className="p-3 grid grid-cols-2 gap-4 divide-x divide-[#30363d] h-36 overflow-y-auto select-text bg-[#0d1117]">
            <div>
              <span className="text-[8px] text-red-400 uppercase tracking-wider block mb-1 font-sans">{t('dw.verify.diff.mainLabel')}</span>
              <pre className={`whitespace-pre-wrap ${styles.terminalText} opacity-60 font-mono`}>{mockFileDiffs[activeDiffFile].original}</pre>
            </div>
            <div className="pl-4">
              <span className="text-[8px] text-emerald-400 uppercase tracking-wider block mb-1 font-sans">{t('dw.verify.diff.branchLabel')}</span>
              <pre className="whitespace-pre-wrap text-[#e6edf3] font-mono">{mockFileDiffs[activeDiffFile].current}</pre>
            </div>
          </div>

          <div className="bg-[#161b22] px-3 py-2 border-t border-[#30363d] flex justify-between items-center select-none">
            <span className={`text-[8px] ${styles.terminalText} font-sans`}>{t('dw.verify.branchProtection')}</span>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={runCiChecks}
                disabled={ciStatus === 'running'}
                className="px-2.5 py-1 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded text-[9px] transition-all flex items-center gap-1 cursor-pointer shadow-xs disabled:bg-[#3a414a]"
              >
                <Play size={9} />
                <span>{ciStatus === 'running' ? t('dw.verify.btn.ciRunning') : t('dw.verify.btn.runCi')}</span>
              </button>

              <button
                type="button"
                onClick={mergeBranch}
                disabled={ciStatus !== 'success' || prMerged}
                className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded text-[9px] transition-all flex items-center gap-1 cursor-pointer shadow-xs disabled:bg-[#21262d] disabled:text-[#484f58]"
              >
                <Check size={10} />
                <span>{prMerged ? t('dw.verify.btn.merged') : t('dw.verify.btn.approveMerge')}</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Run Logs simulation console */}
      <div className={`${styles.terminalBg} rounded-xl border ${styles.terminalBorder} p-3 h-28 overflow-y-auto font-mono text-[9px] leading-relaxed select-text`}>
        {ciLogs.length === 0 ? (
          <div className={`${styles.terminalText} italic h-full flex items-center justify-center text-center`}>
            {t('dw.verify.logs.empty')}
          </div>
        ) : (
          <div className="space-y-0.5">
            {ciLogs.map((log, lidx) => (
              <div
                key={lidx}
                className={
                  log.includes('SUCCESS') || log.includes('✅')
                    ? 'text-[#3fb950] font-bold'
                    : log.includes('ERROR') || log.includes('警告')
                    ? 'text-[#f85149] font-bold'
                    : log.startsWith('[CI-')
                    ? 'text-[#58a6ff]'
                    : 'text-[#c9d1d9]'
                }
              >
                {log}
              </div>
            ))}
            {ciStatus === 'success' && (
              <div className="text-[#3fb950] font-extrabold text-[10px] border-t border-[#30363d] pt-1 mt-1 flex items-center gap-1 animate-pulse">
                <CheckCircle2 size={12} />
                <span>[COMPILE METRICS] CI CHECKS: PASSED. STAGE DEPLOYMENT READY FOR PRODUCTION RE-BUILD.</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

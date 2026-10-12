/**
 * StepSchedule — 步骤 4：调度生命周期与 Data Health 熔断控制
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * useScheduleStep 承载阈值 state、流式模拟 useEffect（依赖数组原样保留）与
 * toggleStream / triggerIncident 逻辑，StepSchedule 为原 JSX 片段。
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState } from 'react';
import { RefreshCw, ShieldAlert } from 'lucide-react';
import { useTheme } from '../ThemeContext';
import { useLanguage } from '../LanguageContext';

type ToastFn = (type: 'success' | 'error' | 'info', message: string) => void;

export function useScheduleStep(
  toast: ToastFn,
  handleCommitToGit: (message: string, filesChanged: string[]) => void,
) {
  const { t } = useLanguage();
  // --- STEP 4: SCHEDULE & DATA HEALTH STATE ---
  const [scheduleTrigger] = useState<string>('ON_DATASET_UPDATE');
  const [nullTolerance, setNullTolerance] = useState<number>(1); // 1%
  const [minRowCount, setMinRowCount] = useState<number>(1000);
  const [freshnessDelay, setFreshnessDelay] = useState<number>(120); // 120 mins
  const [streamActive, setStreamActive] = useState<boolean>(false);
  const [streamDataRows, setStreamDataRows] = useState<number>(4500);
  const [incidentActive, setIncidentActive] = useState<boolean>(false);
  const [isMelted, setIsMelted] = useState<boolean>(false); // 熔断状态
  const [healthLogs, setHealthLogs] = useState<string[]>([]);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | undefined;
    if (streamActive && !isMelted) {
      interval = setInterval(() => {
        setStreamDataRows((prev) => {
          const added = Math.floor(Math.random() * 80) + 20;
          const next = prev + added;

          if (incidentActive) {
            setHealthLogs((curr) => [
              `[STREAM-LISTENER] 📥 实时捕获流式数据批次 ${Date.now().toString().slice(-4)}: 注入 ${added} 行`,
              `[DHC-PROBE] 📡 评估空值占比: 监控 [pilot_id] 的 Null 发生率为: 8.42% (警告: 规则上限为 ${nullTolerance}%) ❌`,
              `[DHC-ALERT] 🚨 CRITICAL ERROR: 空值溢出指标阈值已报警！当前状态严重。`,
              `[CIRCUIT-BREAKER] 🛑 [熔断机制触发] 自动停止流式调度 Pipeline，避免污染 downstream ontology!`,
              `[WEBHOOK] 📡 SLA警报发送成功！飞书/SLACK: "调度任务 flights_enrich 发生异常熔断：空值占比(8.42%)突破${nullTolerance}%"`,
            ]);
            setIsMelted(true);
            setStreamActive(false);
            toast('error', t('dw.psl.toast.incident'));
            handleCommitToGit('fix(health-breaker): 自动触发调度生命周期熔断逻辑，终止下游构建，保护物理 Gold 表', ['dhc-telemetry.log']);
          } else {
            setHealthLogs((curr) => [
              `[STREAM-LISTENER] 📥 实时拉取流式批次 ${Date.now().toString().slice(-4)}: 注入 ${added} 行 (总计 ${next} 行)`,
              `[DHC-PROBE] 📡 评估行数下限: 监控 ${next} 行 (阈值: >${minRowCount}) -> PASSED ✅`,
              `[DHC-PROBE] 📡 评估 [pilot_id] 空值率: 实际 0.11% (阈值: <${nullTolerance}%) -> PASSED ✅`,
              `[DHC-PROBE] 📡 评估时效延迟: 实际延时 15 分钟 (阈值: <${freshnessDelay}m) -> PASSED ✅`,
            ]);
          }
          return next;
        });
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [streamActive, incidentActive, nullTolerance, minRowCount, freshnessDelay, isMelted, toast, handleCommitToGit]);

  const toggleStream = () => {
    if (isMelted) {
      setIsMelted(false);
      setIncidentActive(false);
      setStreamDataRows(4500);
      setHealthLogs(['[SCHEDULER] 🔄 系统熔断已人工复位，清除 Data Health 异常事件，正在准备重新启动流监控。']);
      toast('info', t('dw.psl.toast.reset'));
      return;
    }
    setStreamActive(!streamActive);
    if (!streamActive) {
      setHealthLogs([
        `[SCHEDULER] 📅 注册自动调度策略: ${scheduleTrigger} [ds_flights_raw]`,
        `[SCHEDULER] 🛡️ 挂载 ${nullTolerance}% 字段空值限制、>${minRowCount} 行数判定、<${freshnessDelay}m 延迟判定...`,
        '[STREAM-LISTENER] 📡 高并发实时接收通道启动，开始周期监控...',
      ]);
    }
  };

  const triggerIncident = () => {
    if (!streamActive) {
      toast('error', t('dw.psl.toast.incidentNotReady'));
      return;
    }
    setIncidentActive(true);
    toast('info', t('dw.psl.toast.incidentInjected'));
  };

  return {
    nullTolerance,
    setNullTolerance,
    minRowCount,
    setMinRowCount,
    freshnessDelay,
    setFreshnessDelay,
    streamActive,
    streamDataRows,
    incidentActive,
    isMelted,
    healthLogs,
    toggleStream,
    triggerIncident,
  };
}

interface StepScheduleProps {
  nullTolerance: number;
  setNullTolerance: (value: number) => void;
  minRowCount: number;
  setMinRowCount: (value: number) => void;
  freshnessDelay: number;
  setFreshnessDelay: (value: number) => void;
  streamActive: boolean;
  isMelted: boolean;
  healthLogs: string[];
  toggleStream: () => void;
  triggerIncident: () => void;
}

export default function StepSchedule({
  nullTolerance,
  setNullTolerance,
  minRowCount,
  setMinRowCount,
  freshnessDelay,
  setFreshnessDelay,
  streamActive,
  isMelted,
  healthLogs,
  toggleStream,
  triggerIncident,
}: StepScheduleProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-4 font-sans select-none">
      <div className={`p-4 ${styles.appBg} border ${styles.cardBorder} rounded-xl space-y-3`}>
        <div className={`flex justify-between items-center pb-1.5 border-b ${styles.cardBorder}`}>
          <span className={`text-[10px] font-extrabold ${styles.cardText} uppercase tracking-wider font-mono`}>
            {t('dw.psl.sla.title')}
          </span>
          <span className={`text-[9px] font-bold px-2 py-0.5 rounded ${isMelted ? 'bg-rose-100 text-rose-800 animate-pulse' : 'bg-emerald-100 text-emerald-800'}`}>
            {isMelted ? t('dw.psl.sla.melted') : t('dw.psl.sla.healthy')}
          </span>
        </div>

        {/* Threshold control sliders */}
        <div className="grid grid-cols-3 gap-4 text-xs">
          <div className={`${styles.cardBg} p-2.5 rounded-lg border ${styles.cardBorder} flex flex-col gap-1.5`}>
            <div className={`flex justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
              <span>{t('dw.psl.slider.nullLimit')}</span>
              <span className={`font-bold ${styles.cardText}`}>{nullTolerance}%</span>
            </div>
            <input
              type="range"
              aria-label={t('dw.psl.slider.nullLimit')}
              min={1}
              max={10}
              value={nullTolerance}
              onChange={(e) => setNullTolerance(parseInt(e.target.value))}
              disabled={isMelted}
              className={`w-full h-1 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-slate-800`}
            />
            <p className={`text-[8px] ${styles.cardTextMuted}`}>{t('dw.psl.slider.nullLimitDesc')}</p>
          </div>

          <div className={`${styles.cardBg} p-2.5 rounded-lg border ${styles.cardBorder} flex flex-col gap-1.5`}>
            <div className={`flex justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
              <span>{t('dw.psl.slider.minRows')}</span>
              <span className={`font-bold ${styles.cardText}`}>{minRowCount} {t('dw.psl.unit.rows')}</span>
            </div>
            <input
              type="range"
              aria-label={t('dw.psl.slider.minRows')}
              min={500}
              max={2000}
              step={100}
              value={minRowCount}
              onChange={(e) => setMinRowCount(parseInt(e.target.value))}
              disabled={isMelted}
              className={`w-full h-1 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-slate-800`}
            />
            <p className={`text-[8px] ${styles.cardTextMuted}`}>{t('dw.psl.slider.minRowsDesc')}</p>
          </div>

          <div className={`${styles.cardBg} p-2.5 rounded-lg border ${styles.cardBorder} flex flex-col gap-1.5`}>
            <div className={`flex justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
              <span>{t('dw.psl.slider.freshness')}</span>
              <span className={`font-bold ${styles.cardText}`}>{freshnessDelay} {t('dw.psl.unit.minutes')}</span>
            </div>
            <input
              type="range"
              aria-label={t('dw.psl.slider.freshness')}
              min={60}
              max={300}
              step={30}
              value={freshnessDelay}
              onChange={(e) => setFreshnessDelay(parseInt(e.target.value))}
              disabled={isMelted}
              className={`w-full h-1 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-slate-800`}
            />
            <p className={`text-[8px] ${styles.cardTextMuted}`}>{t('dw.psl.slider.freshnessDesc')}</p>
          </div>
        </div>
      </div>

      <div className="flex gap-4 items-stretch">
        <div className={`flex-1 border ${styles.cardBorder} rounded-xl p-4 ${styles.cardBg} flex flex-col justify-between gap-3`}>
          <div className="space-y-1">
            <div className={`text-xs font-bold ${styles.cardText}`}>{t('dw.psl.sandbox.title')}</div>
            <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
              {t('dw.psl.sandbox.intro1')}<strong>{t('dw.psl.sandbox.injectDirty')}</strong>{t('dw.psl.sandbox.intro2')}
              <strong className="text-rose-600 font-extrabold ml-1">{t('dw.psl.sandbox.circuitBreaker')}</strong>
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggleStream}
              className={`flex-1 py-2 font-bold text-xs rounded-lg transition-all flex items-center justify-center gap-1 cursor-pointer shadow-xs ${
                isMelted
                  ? 'bg-rose-600 hover:bg-rose-700 text-white animate-pulse'
                  : streamActive
                  ? 'bg-amber-600 hover:bg-amber-700 text-white'
                  : `${styles.darkBg} hover:${styles.darkHover} text-white`
              }`}
            >
              <RefreshCw size={12} className={streamActive ? 'animate-spin' : ''} />
              <span>{isMelted ? t('dw.psl.btn.meltedReset') : streamActive ? t('dw.psl.btn.pauseStream') : t('dw.psl.btn.startStream')}</span>
            </button>

            <button
              type="button"
              onClick={triggerIncident}
              disabled={!streamActive || isMelted}
              className={`py-2 px-3 bg-red-100 hover:bg-red-200 text-red-700 font-bold text-xs rounded-lg transition-all flex items-center gap-1 cursor-pointer disabled:${styles.appBorder} disabled:text-${styles.cardTextMuted}`}
            >
              <ShieldAlert size={12} />
              <span>{t('dw.psl.btn.injectDirty')}</span>
            </button>
          </div>
        </div>

        {/* Simulated alerting logs terminal */}
        <div
          className={`w-[320px] rounded-xl border p-3 font-mono text-[9px] leading-relaxed max-h-40 overflow-y-auto transition-colors ${
            isMelted ? 'bg-rose-950/95 border-rose-800 text-rose-200 shadow-lg' : `${styles.terminalBg} ${styles.terminalBorder} ${styles.terminalText}`
          }`}
        >
          {healthLogs.length === 0 ? (
            <div className={`${styles.cardTextMuted} italic h-full flex items-center justify-center text-center`}>
              {t('dw.psl.logs.empty')}
            </div>
          ) : (
            <div className="space-y-1">
              {healthLogs.map((log, lidx) => (
                <div
                  key={lidx}
                  className={
                    log.includes('熔断') || log.includes('🚨') || log.includes('监控 [')
                      ? 'text-rose-400 font-bold'
                      : log.includes('Passed ✅') || log.includes('PASSED ✅')
                      ? 'text-emerald-400'
                      : log.startsWith('[SCHEDULER]')
                      ? 'text-blue-400'
                      : styles.terminalText
                  }
                >
                  {log}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

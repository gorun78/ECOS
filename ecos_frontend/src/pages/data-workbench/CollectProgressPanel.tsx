/**
 * CollectProgressPanel — 元数据立即采集的实时进度面板
 *
 * 设计风格对齐"连接器调试面板"（Diagnostic Log Terminal）：
 *   - 背景 `styles.sidebarBg` + `font-mono` + `select-text`
 *   - 顶部 thin-bordered 标题行（左侧 label，右侧版本号）
 *   - 进度条 + 百分比 + 已完成/总表数 + 失败数
 *   - 状态消息行（带左侧色条 = 当前步骤语义强调）
 *   - 底部任务 ID + 关闭按钮（任务结束后可关）
 *
 * 状态来源：ConnectionsTab 在 triggerCollectSync 后启动 2s 轮询 fetchCollectStatus，
 * 持续写入 status；面板只读展示。
 *
 * 主题感知 (useTheme)、国际化 (useLanguage)、lucide-react 图标。
 * 不硬编码中文 / 颜色。
 */
import React from 'react';
import { Loader2, XCircle, CheckCircle2, Clock } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface Props {
  taskId: string;
  status: {
    status?: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | string;
    progress?: number;
    message?: string;
    statusMessage?: string;
    processedRecords?: number;
    totalRecords?: number;
    collectedTables?: number;
    totalTables?: number;
    tablesOk?: number;
    tablesFailed?: number;
    errorMessage?: string;
  } | null;
  onClose: () => void;
}

export default function CollectProgressPanel({ taskId, status, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const st = status?.status ?? 'PENDING';
  const progress = Math.max(0, Math.min(100, status?.progress ?? 0));
  const done = st === 'SUCCEEDED';
  const failed = st === 'FAILED' || st === 'CANCELLED' || st === 'error';
  const finished = done || failed;
  const canClose = finished;

  const total = status?.totalTables ?? status?.totalRecords ?? 0;
  const ok = status?.tablesOk ?? status?.collectedTables ?? 0;
  const failedCnt = status?.tablesFailed ?? 0;
  const current = status?.statusMessage || status?.message || '';

  const tonCls = done ? styles.successText : failed ? styles.dangerText : styles.accentText;
  const barColor = done ? styles.successText : failed ? styles.dangerText : styles.accentText;

  // 图标按状态
  const Icon = done ? CheckCircle2 : failed ? XCircle : Loader2;
  const iconSpin = !finished;

  return (
    <div className={`${styles.sidebarBg} rounded-xl p-4 text-xs font-mono ${styles.sidebarText} border ${styles.sidebarBorder} select-text space-y-2 leading-relaxed`}>
      {/* 标题行 — 与 Diagnostic Log Terminal 一致 */}
      <div className={`text-[10px] ${styles.cardTextMuted} tracking-wider uppercase font-semibold mb-2 border-b ${styles.sidebarBorder} pb-1 flex justify-between items-center select-none`}>
        <span>{t('dw.collect.panelTitle')}</span>
        <span className={`${tonCls} font-semibold`}>{t('dw.collect.panelTag')}</span>
      </div>

      {/* 进度条 + 百分比 */}
      <div className="flex items-center gap-3">
        <div className={`flex-1 h-2 rounded-full overflow-hidden`} style={{ background: styles.appBg }}>
          <div
            className={`h-full transition-all duration-300`}
            style={{ width: `${progress}%`, background: barColor }}
          />
        </div>
        <span className={`text-xs font-bold w-12 text-right ${tonCls}`}>{progress}%</span>
      </div>

      {/* 状态徽章 */}
      <div className="flex items-center gap-2 text-[11px]">
        <Icon className={`w-4 h-4 ${tonCls} ${iconSpin ? 'animate-spin' : ''}`} strokeWidth={2} />
        <span className={`${styles.cardText} font-semibold`}>
          {t('dw.collect.state')} : <span className={`font-mono ${tonCls} font-bold`}>{st}</span>
        </span>
      </div>

      {/* 已完成 / 总表数 / 失败 */}
      <div className={`flex items-center gap-4 text-[11px]`}>
        <span className="flex items-center gap-1" style={{ color: styles.cardTextMuted }}>
          <span style={{ fontSize: 10 }}>{t('dw.collect.done')}</span>
          <span className={`text-[11px] font-bold`} style={{ color: styles.cardText }}>{ok}</span>
          <span style={{ fontSize: 10 }}>/{total || '-'}</span>
        </span>
        {failedCnt > 0 && (
          <span className="flex items-center gap-1">
            <span style={{ fontSize: 10, color: styles.dangerText }}>{t('dw.collect.failed')}</span>
            <span className={`text-[11px] font-bold`} style={{ color: styles.dangerText }}>{failedCnt}</span>
          </span>
        )}
        <span className="ml-auto flex items-center gap-1 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>
          <Clock className="w-3 h-3 opacity-60" />
          {new Date().toLocaleTimeString()}
        </span>
      </div>

      {/* 当前状态消息 — 色条边框强调 */}
      {current && (
        <div
          className={`text-[10px] border-l-2 pl-2 ${styles.cardTextMuted}`}
          style={{ borderColor: done ? undefined : failed ? undefined : barColor }}
        >
          {current}
        </div>
      )}

      {/* 失败时显示 errorMessage */}
      {status?.errorMessage && (
        <div className={`text-[11px] font-mono break-all`} style={{ color: styles.dangerText }}>
          {status.errorMessage}
        </div>
      )}

      {/* 任务 ID + 关闭（完成后可关） */}
      <div className="flex items-center justify-between pt-1" style={{ borderTop: `1px solid ${styles.sidebarBorder}` }}>
        <span className="text-[10px] font-mono" style={{ color: styles.cardTextMuted }}>
          task: <span className={styles.accentText}>{taskId.slice(0, 8)}…</span>
        </span>
        <button
          onClick={onClose}
          disabled={!canClose}
          className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${styles.cardBorder} flex items-center gap-1 transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed`}
          style={{
            background: canClose ? styles.accentBg : 'transparent',
            color: canClose ? styles.cardText : styles.cardTextMuted,
          }}
        >
          <XCircle className="w-3 h-3" strokeWidth={2} />
          {canClose ? t('dw.collect.close') : t('dw.collect.running')}
        </button>
      </div>
    </div>
  );
}
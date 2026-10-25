/**
 * SimulationResultPanel — 认知推演结果面板（详细设计-07 F07-22 / F07-12）。
 *
 * <p>本面板原为「零信任对账沙箱演练」：走旧的本地知识查询接口，失败时本地伪造
 * {@code verdict: GRANTED/DENIED}、固定 clientIp 并内联硬编码身份，属 P0 虚假验收族。
 * 现整体删除伪造分支：认知结论<b>只来自</b> {@link useCognition}（F07-12 四端点唯一通道）。
 * 引擎不可用（503）→ 横幅 + 重试，<b>禁 fallback 造数据</b>（3.3 六态矩阵：空/加载/错误/边界/权限/离线）。</p>
 *
 * @license Apache-2.0
 */
import React from 'react';
import {
  AlertTriangle,
  BrainCircuit,
  Loader,
  XCircle
} from 'lucide-react';
import type { ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { useCognition } from '../../hooks/useCognition';
import type { CognitionSlice } from '../../hooks/useCognition';

export interface SimulationResultPanelProps {
  /** 目标场景 ID — 认知四端点据此只读取数，禁组件内自造身份/角色/IP。 */
  scenarioId: string;
  styles: ThemeStyles;
}

/** 单个认知切片的状态提示映射到六态矩阵文案。 */
function stateLabelKey(slice: CognitionSlice<unknown>): string | null {
  if (slice.loading) return 'scenario.cognition.loading';
  if (slice.unavailable) return 'scenario.cognition.notEnabled';
  if (slice.error) return 'scenario.cognition.error';
  return null;
}

export default function SimulationResultPanel({ scenarioId, styles }: SimulationResultPanelProps) {
  const { t } = useLanguage();
  const { detect } = useCognition(scenarioId);

  const minds = detect.data ?? [];

  return (
    <div className="space-y-4">
      <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg`}>
        <div className="flex items-center gap-2">
          <BrainCircuit size={14} className={styles.accentText} />
          <span className={`text-xs font-bold ${styles.cardText}`}>{t('scenario.cognition.detect')}</span>
        </div>
        <p className={`text-[11px] ${styles.cardTextMuted} mt-1`}>{t('scenario.cog.desc')}</p>
      </div>

      {/* 加载态 */}
      {detect.loading && (
        <div className={`p-6 ${styles.cardBg} border ${styles.cardBorder} rounded-lg flex items-center justify-center gap-2 text-xs ${styles.cardTextMuted}`}>
          <Loader size={14} className="animate-spin" />
          {t(stateLabelKey(detect) ?? 'scenario.cognition.loading')}
        </div>
      )}

      {/* 引擎不可用（503）：横幅 + 重试，禁伪造数据 */}
      {!detect.loading && detect.unavailable && (
        <div className={`p-4 ${styles.cardBg} border ${styles.warningBorder ?? styles.cardBorder} ${styles.warningBg ?? styles.cardBg} rounded-lg flex items-start justify-between gap-3`}>
          <div className="flex items-start gap-2">
            <AlertTriangle size={16} className={styles.warningText} />
            <div>
              <p className={`text-xs font-bold ${styles.warningText}`}>{t('scenario.cognition.notEnabled')}</p>
              {detect.traceId && (
                <p className={`text-[10px] ${styles.cardTextMuted} font-mono mt-1`}>
                  {t('scenario.cognition.traceId')}: {detect.traceId}
                </p>
              )}
            </div>
          </div>
          <button
            onClick={detect.reload}
            className={`shrink-0 px-3 py-1 rounded border ${styles.accentBorder} ${styles.accentText} text-[11px] font-bold cursor-pointer hover:opacity-80`}
          >
            {t('scenario.cognition.retry')}
          </button>
        </div>
      )}

      {/* 普通错误态（400/404/403）：诚实文案，不白屏 */}
      {!detect.loading && !detect.unavailable && detect.error && (
        <div className={`p-4 ${styles.cardBg} border ${styles.cardBorder} rounded-lg flex items-start gap-2`}>
          <XCircle size={16} className={styles.dangerText} />
          <div>
            <p className={`text-xs font-bold ${styles.dangerText}`}>{t('scenario.cognition.error')}</p>
            <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5`}>{detect.error}</p>
          </div>
        </div>
      )}

      {/* 空态 */}
      {!detect.loading && !detect.unavailable && !detect.error && minds.length === 0 && (
        <div className={`p-8 ${styles.cardBg} border ${styles.cardBorder} rounded-lg text-center`}>
          <BrainCircuit size={24} className={`mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('scenario.cognition.empty')}</p>
        </div>
      )}

      {/* 数据态：只渲染后端真实返回，禁本地合成 verdict/answer */}
      {!detect.loading && minds.length > 0 && (
        <div className={`p-4 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-2`}>
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold`}>{t('scenario.cognition.mindLabel')}</span>
          <ul className="space-y-1.5">
            {minds.map((m) => (
              <li key={m.mindId} className={`flex items-center justify-between p-2 ${styles.inputBg} border ${styles.inputBorder} rounded text-[11px]`}>
                <span className={`${styles.cardText} font-mono`}>{m.name}</span>
                {m.isActive && (
                  <span className={`${styles.successText} text-[9px] font-bold uppercase`}>{t('scenario.cognition.active')}</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

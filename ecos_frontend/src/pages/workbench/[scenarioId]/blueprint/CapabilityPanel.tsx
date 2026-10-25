/**
 * CapabilityPanel — 场景蓝图能力盘（设计方案 §3.6 / 补充设计 §10 §13）。
 *
 * <p>唯一消费通道：{@link services/capabilityClient}（绑定 ai-engine
 * `GET /api/v1/ai/capabilities` 真实端点，2026-10-11 经 gateway 契约核验）。
 * 呈现后端冻结目录 11 项的 7 个已核验字段（capabilityId/family/contractVersion/
 * evidenceRequired/automationLevel/costUnit/toolset）；与当前蓝图 `capabilities`
 * 声明做交叉引用（「所需」徽标）。六态：加载 / 错误（含 503 引擎横幅 + traceId）/
 * 空 / 就绪。</p>
 *
 * <p><b>无假真理 P0</b>：目录来自后端，缺三要件行经客户端 parse 剔除（不补默认）；
 * `operationClass`(READ/SUGGEST/WRITE) 与 `riskLevel` 无 REST 源（WAgent Tool Contract
 * 内部字段，无 Controller 消费者），遵铁律不在本面渲染、不以本地推断伪造。
 * 交叉引用「所需」列只做集合命中判断，不改写任何后端真值。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Boxes,
  PlugZap,
  RefreshCw,
  Star
} from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import logger from '../../../../services/logger';
import {
  listCapabilities,
  isCapabilityApiError,
  parseCostUnit,
  parseToolset,
  automationTone,
  extractDeclaredCapabilityIds,
  isRequiredByBlueprint,
  type CapabilityRow,
  type CapabilityErrorCode,
  type AutomationTone,
} from '../../../../services/capabilityClient';

interface Props {
  /** 当前蓝图 capabilities 投影（自由结构，客户端尽力抽取声明 ID 集合）。 */
  declaredCapabilities?: unknown;
}

type Phase = 'loading' | 'ready' | 'empty' | 'engine' | 'error';

type ToneKey = 'info' | 'success' | 'muted';

const AUTO_TONE_CLASS: Record<AutomationTone, ToneKey> = {
  none: 'muted',
  assist: 'info',
  auto: 'success',
  unknown: 'muted',
};

export default function CapabilityPanel({ declaredCapabilities }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<CapabilityRow[]>([]);
  const [phase, setPhase] = useState<Phase>('loading');
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [errCode, setErrCode] = useState<CapabilityErrorCode | undefined>();
  const [traceId, setTraceId] = useState<string | undefined>();

  const declared = useMemo(
    () => extractDeclaredCapabilityIds(declaredCapabilities),
    [declaredCapabilities],
  );

  const load = useCallback(async () => {
    setPhase('loading');
    setErrMsg(null);
    setErrCode(undefined);
    setTraceId(undefined);
    try {
      const list = await listCapabilities();
      if (list.length === 0) {
        setRows([]);
        setPhase('empty');
        return;
      }
      // 按 capabilityId 稳定排序，保证渲染确定性（非后端顺序依赖）
      setRows([...list].sort((a, b) => a.capabilityId.localeCompare(b.capabilityId)));
      setPhase('ready');
    } catch (e) {
      logger.error('capability-load', e);
      if (isCapabilityApiError(e)) {
        setErrCode(e.code);
        setTraceId(e.traceId);
        setErrMsg(e.message);
        if (e.status === 503) {
          setPhase('engine');
          return;
        }
      } else {
        setErrMsg(e instanceof Error ? e.message : String(e));
      }
      setRows([]);
      setPhase('error');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const toneCls = (tone: 'info' | 'success' | 'muted'): string => {
    if (tone === 'success') return `${styles.successBg} ${styles.successText} ${styles.successBorder}`;
    if (tone === 'info') return `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`;
    return `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
  };

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <div className="flex items-center gap-2 flex-wrap mb-2">
        <Boxes size={13} className={styles.accentText} />
        <h3 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.cap.title')}</h3>
        <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.cap.subtitle')}</span>
        <button
          type="button"
          onClick={() => void load()}
          className={`ml-auto inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <RefreshCw size={11} />
          {t('workspace.cap.retry')}
        </button>
      </div>

      {/* 加载 */}
      {phase === 'loading' && (
        <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted} py-5 justify-center`}>
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          {t('workspace.cap.loading')}
        </div>
      )}

      {/* 空（冻结目录理论非空；真后端空即诚实呈现，禁补默认项） */}
      {phase === 'empty' && (
        <div className={`p-4 rounded border text-center text-xs ${styles.cardBorder} ${styles.cardBg} ${styles.cardTextMuted}`}>
          <Boxes size={18} className={`${styles.cardTextMuted}`} />
          <p>{t('workspace.cap.empty')}</p>
        </div>
      )}

      {/* 引擎不可用 503：横幅 + traceId */}
      {phase === 'engine' && (
        <div className={`p-3 rounded border text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          <PlugZap size={12} className="inline mr-1" />
          {t('workspace.cap.engine_unavailable', { code: errCode ?? 'ENGINE_DISABLED', traceId: traceId ?? '—' })}
        </div>
      )}

      {/* 通用错误 */}
      {phase === 'error' && (
        <div className={`p-3 rounded border text-[11px] ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <AlertTriangle size={12} className="inline mr-1" />
          {t('workspace.cap.error', { msg: errMsg ?? '' })}
          {traceId ? <span className={`ml-1 font-mono ${styles.cardTextMuted}`}>traceId={traceId}</span> : null}
        </div>
      )}

      {/* 就绪 */}
      {phase === 'ready' && (
        <div className="space-y-2">
          {declared.size > 0 && (
            <div className={`text-[10px] ${styles.cardTextMuted}`}>
              {t('workspace.cap.declared_hint', { n: declared.size })}
            </div>
          )}
          <div className="overflow-x-auto">
            <table className="w-full text-[11px] border-collapse">
              <thead>
                <tr className={`${styles.cardTextMuted} text-left`}>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.id')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.family')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.version')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.evidence')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.automation')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.cost')}</th>
                  <th className={`py-1 pr-2 font-bold uppercase ${styles.cardBorder}`}>{t('workspace.cap.col.toolset')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const required = isRequiredByBlueprint(r, declared);
                  const cost = parseCostUnit(r.costUnit);
                  const tool = parseToolset(r.toolset);
                  const auto = automationTone(r.automationLevel);
                  return (
                    <tr key={r.capabilityId} className={`${styles.cardBorder} border-b`}>
                      <td className={`py-1 pr-2 font-mono ${styles.cardText}`}>
                        <span className="inline-flex items-center gap-1">
                          {required ? <Star size={10} className={styles.accentText} /> : null}
                          {r.capabilityId}
                        </span>
                      </td>
                      <td className={`py-1 pr-2 ${styles.cardTextMuted}`}>{r.family}</td>
                      <td className={`py-1 pr-2 font-mono ${styles.cardTextMuted}`}>{r.contractVersion}</td>
                      <td className="py-1 pr-2">
                        <span className={`inline-block text-[10px] px-1.5 py-0.5 rounded border ${
                          r.evidenceRequired
                            ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                            : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`
                        }`}>
                          {r.evidenceRequired ? t('workspace.cap.evidence.required') : t('workspace.cap.evidence.optional')}
                        </span>
                      </td>
                      <td className="py-1 pr-2">
                        <span className={`inline-block text-[10px] px-1.5 py-0.5 rounded border ${toneCls(AUTO_TONE_CLASS[auto])}`}>
                          {t(`workspace.cap.auto.${auto}`)}
                          {Number.isFinite(r.automationLevel) ? ` · ${r.automationLevel}` : ''}
                        </span>
                      </td>
                      <td className={`py-1 pr-2 font-mono ${cost ? styles.cardText : styles.cardTextMuted}`}>
                        {cost ?? t('workspace.cap.unknown')}
                      </td>
                      <td className={`py-1 pr-2 font-mono ${tool ? styles.cardText : styles.cardTextMuted}`}>
                        {tool ?? t('workspace.cap.unknown')}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{t('workspace.cap.no_risk_note')}</p>
        </div>
      )}
    </div>
  );
}

/**
 * WhatIfSimulator — L3 What-if 推演试算（复盘 S2 收口 ②；设计方案 §3.2
 * 「反事实/情景推演：exploratory=true 显式标注，禁与核算结果混淆」）。
 *
 * <p>绑定端点（代码实证）：`POST /api/v1/cognitive/scenario/simulate` —
 * workspace 之外由 cognitive2 `ScenarioController.java:64` 承载；请求<b>不带 mode</b>
 * 时走探索通道（`:91-94`：`ScenarioSimulatorServiceImpl.simulate` →
 * `SimulationResult.setExploratory(true)` + `caliber=NON_ACCOUNTING`，`:84` 亦强制）。
 * 出口 `ApiResponse.success(result)` 把 `exploratory`/`caliber` 原样序列化
 * （`SimulationResult.java:20/:48`）。</p>
 *
 * <p><b>徽标判定源 = 响应回显字段</b>（`result.exploratory === true`），不按请求参数
 * 猜测：后端将来若在同一端点回确定性形态（`mode=DETERMINISTIC_DIFF` 的
 * SimulateResponseVO 无 exploratory 字段），此处即不打推演徽标——双向防混淆护栏。
 * 确定性重算通道（baselineRunId + overrides 六要素）不在本试算面暴露，待预测 run
 * 编排串通四源（forecastScenarioMode 单源判定）后另行接入。</p>
 *
 * <p>无假真理：推演失败后端仍回 200 + `status=FAILED`（ScenarioSimulatorServiceImpl:111）
 * ⇒ UI 如实渲染 FAILED 徽标与 summary，不掩成功；503/网络错误走 CognitiveErrorState
 * 横幅 + traceId。六态：idle / 提交中 / 结果 / 错误 / 空回显（字段「—」）/ 离线。</p>
 */
import React, { useCallback, useState } from 'react';
import { FlaskConical, Plus, Trash2, X } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import Badge from '../../../../components/common/Badge';
import {
  CognitiveErrorState,
  cognitiveErrorTitleKey,
} from '../../../../pages/scenario/cognition/CognitionStateViews';
import {
  CognitiveApiError,
  isCognitiveApiError,
} from '../../../../services/cognitiveClient';
import {
  simulateScenarioWhatIf,
  type WhatIfSimulationResultDto,
} from '../../../../services/cognitive/outcomesApi';

export interface WhatIfSimulatorProps {
  scenarioId: string;
}

type SimState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'done'; result: WhatIfSimulationResultDto }
  | { kind: 'error'; error: CognitiveApiError };

interface VarRow {
  key: string;
  value: string;
}

/** Map 维度的键值行：仅服务端回显的实有键；值非原始类型时以 mono JSON 截断呈现（不展开伪造）。 */
function pairRows(map: Record<string, unknown> | undefined): Array<[string, string]> | null {
  if (!map || Object.keys(map).length === 0) return null;
  return Object.entries(map).map(([k, v]) => {
    let s: string;
    if (v === null || v === undefined) s = '—';
    else if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') s = String(v);
    else {
      try {
        s = JSON.stringify(v);
      } catch {
        s = '—';
      }
    }
    return [k, s] as [string, string];
  });
}

export default function WhatIfSimulator({ scenarioId }: WhatIfSimulatorProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [name, setName] = useState('');
  const [domain, setDomain] = useState('');
  const [vars, setVars] = useState<VarRow[]>([{ key: '', value: '' }]);
  const [state, setState] = useState<SimState>({ kind: 'idle' });

  const inputCls = `w-full rounded border px-2 py-1 text-xs ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`;

  const collectVariables = (): Record<string, string | number> => {
    const out: Record<string, string | number> = {};
    for (const row of vars) {
      const k = row.key.trim();
      if (!k) continue;
      const raw = row.value.trim();
      const num = Number(raw);
      out[k] = raw !== '' && Number.isFinite(num) ? num : raw;
    }
    return out;
  };

  const submit = useCallback(async () => {
    const trimmed = name.trim();
    if (trimmed === '') return;
    setState({ kind: 'submitting' });
    try {
      const result = await simulateScenarioWhatIf({
        name: trimmed,
        domain: domain.trim() === '' ? undefined : domain.trim(),
        variables: collectVariables(),
      });
      setState({ kind: 'done', result });
    } catch (e) {
      setState({
        kind: 'error',
        error: isCognitiveApiError(e)
          ? e
          : new CognitiveApiError({ code: 'NETWORK', status: 0, message: String(e) }),
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name, domain, vars]);

  const reset = () => {
    setName('');
    setDomain('');
    setVars([{ key: '', value: '' }]);
    setState({ kind: 'idle' });
  };

  const canSubmit = name.trim() !== '' && state.kind !== 'submitting';
  const result = state.kind === 'done' ? state.result : null;
  /** 徽标唯一判定源：响应回显 exploratory === true（禁本地合成）。 */
  const exploratory = result?.exploratory === true;

  const sectionCls = `rounded border p-2 ${styles.cardBorder}`;

  return (
    <section
      className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}
      data-testid="whatif-simulator"
      data-scenario-id={scenarioId}
    >
      <header className="flex items-center gap-2 mb-2 flex-wrap">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <FlaskConical size={13} />
          {t('workspace.l3.sim.title')}
        </h2>
        {exploratory && (
          <Badge variant="warning" className="gap-1" >
            <FlaskConical size={11} />
            {t('workspace.forecast.mode.exploratory')}
          </Badge>
        )}
        {exploratory && result?.caliber && (
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
            {t('workspace.forecast.mode.caliber')}: {result.caliber}
          </span>
        )}
      </header>
      <p className={`text-[10px] ${styles.cardTextMuted} mb-3`}>{t('workspace.l3.sim.hint')}</p>

      {/* 表单 */}
      <div className={`grid gap-2 mb-2 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
        <label className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>
            {t('workspace.l3.sim.name_label')}
          </span>
          <input
            className={inputCls}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('workspace.l3.sim.name_ph')}
            data-testid="sim-name"
          />
        </label>
        <label className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>
            {t('workspace.l3.sim.domain_label')}
          </span>
          <input
            className={inputCls}
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
            placeholder={t('workspace.l3.sim.domain_ph')}
            data-testid="sim-domain"
          />
        </label>
      </div>

      <div className="mb-2">
        <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>
          {t('workspace.l3.sim.vars_label')}
        </span>
        <ul className="space-y-1.5">
          {vars.map((row, i) => (
            <li key={i} className="flex items-center gap-1.5">
              <input
                className={`${inputCls} font-mono`}
                value={row.key}
                onChange={(e) =>
                  setVars((prev) => prev.map((r, j) => (j === i ? { ...r, key: e.target.value } : r)))
                }
                placeholder={t('workspace.l3.sim.var_key_ph')}
                data-testid={`sim-var-key-${i}`}
              />
              <input
                className={`${inputCls} font-mono`}
                value={row.value}
                onChange={(e) =>
                  setVars((prev) => prev.map((r, j) => (j === i ? { ...r, value: e.target.value } : r)))
                }
                placeholder={t('workspace.l3.sim.var_value_ph')}
                data-testid={`sim-var-value-${i}`}
              />
              <button
                type="button"
                onClick={() => setVars((prev) => (prev.length > 1 ? prev.filter((_, j) => j !== i) : prev))}
                className={`inline-flex items-center justify-center rounded border px-1.5 py-1 cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
                aria-label={t('workspace.l3.sim.var_remove')}
                title={t('workspace.l3.sim.var_remove')}
              >
                <Trash2 size={11} />
              </button>
            </li>
          ))}
        </ul>
        <button
          type="button"
          onClick={() => setVars((prev) => [...prev, { key: '', value: '' }])}
          className={`mt-1.5 inline-flex items-center gap-1 text-[10px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <Plus size={11} />
          {t('workspace.l3.sim.var_add')}
        </button>
      </div>

      <div className="flex items-center gap-2 flex-wrap mb-3">
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void submit()}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
            state.kind === 'submitting' ? styles.warningBg : styles.infoBg
          } ${state.kind === 'submitting' ? styles.warningText : styles.infoText} border ${styles.cardBorder}`}
          data-testid="sim-submit"
        >
          {state.kind === 'submitting' ? t('workspace.l3.sim.running') : t('workspace.l3.sim.run')}
        </button>
        {state.kind === 'done' && (
          <button
            type="button"
            onClick={reset}
            className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[10px] cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          >
            <X size={11} />
            {t('workspace.l3.sim.reset')}
          </button>
        )}
        {name.trim() === '' && (
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.l3.sim.name_required')}</span>
        )}
      </div>

      {/* 提交中 */}
      {state.kind === 'submitting' && (
        <div className={`flex items-center justify-center gap-2 text-xs ${styles.cardTextMuted} py-6`}>
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          {t('workspace.l3.sim.running')}
        </div>
      )}

      {/* 错误（503/401/网络…经 callJson 错误矩阵，横幅 + traceId，绝不掩成结果） */}
      {state.kind === 'error' && (
        <CognitiveErrorState error={state.error} titleKey={cognitiveErrorTitleKey(state.error)} onRetry={() => void submit()} />
      )}

      {/* 结果（只渲染服务端回显；FAILED 状态如实呈现） */}
      {result && (
        <div className={`space-y-3 ${isMobile ? '' : ''}`} data-testid="sim-result">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge
              variant={result.status === 'COMPLETED' ? 'success' : result.status === 'FAILED' ? 'danger' : 'neutral'}
            >
              {result.status || t('workspace.l3.sim.status_unknown')}
            </Badge>
            {typeof result.confidence === 'number' && (
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`} data-testid="sim-confidence">
                {t('workspace.l3.sim.confidence')}: {Math.round(result.confidence * 100)}%
              </span>
            )}
            {!exploratory && (
              <span className={`text-[10px] ${styles.cardTextMuted}`} data-testid="sim-caliber-unknown">
                {t('workspace.l3.sim.caliber_unmarked')}
              </span>
            )}
          </div>

          {result.summary && (
            <p className={`text-xs ${styles.cardText}`} data-testid="sim-summary">{result.summary}</p>
          )}

          {result.assumptions.length > 0 && (
            <div className={sectionCls}>
              <h4 className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>
                {t('workspace.l3.sim.assumptions')}
              </h4>
              <ul className="space-y-1">
                {result.assumptions.map((a, i) => (
                  <li key={i} className={`text-[11px] ${styles.cardText}`}>
                    · {String(a)}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className={`grid gap-2 ${isMobile ? 'grid-cols-1' : 'grid-cols-3'}`}>
            <PairSection title={t('workspace.l3.sim.baseline')} rows={pairRows(result.baseline)} emptyKey="workspace.l3.sim.no_values" />
            <PairSection title={t('workspace.l3.sim.predicted')} rows={pairRows(result.predicted)} emptyKey="workspace.l3.sim.no_values" />
            <PairSection title={t('workspace.l3.sim.deltas')} rows={pairRows(result.deltas)} emptyKey="workspace.l3.sim.no_values" />
          </div>

          {/* 护栏文案：推演口径显式说明（防与核算结果混淆） */}
          <p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`} data-testid="sim-guard-note">
            {t('workspace.l3.sim.guard_note')}
          </p>
        </div>
      )}
    </section>
  );
}

/** 键值区块：服务端 Map 原样回显；无回传 ⇒ 空文案（不造行）。 */
function PairSection({ title, rows, emptyKey }: { title: string; rows: Array<[string, string]> | null; emptyKey: string }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className={`rounded border p-2 ${styles.cardBorder}`}>
      <h4 className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>{title}</h4>
      {rows === null ? (
        <p className={`text-[10px] ${styles.cardTextMuted}`}>{t(emptyKey)}</p>
      ) : (
        <ul className="space-y-0.5">
          {rows.map(([k, v]) => (
            <li key={k} className={`text-[10px] font-mono ${styles.cardText} flex gap-1.5`}>
              <span className="shrink-0 opacity-70">{k}</span>
              <span className="break-all">{v}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

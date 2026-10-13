/**
 * ContextBuildPanel — 蓝图资产盘点/上下文构建面板（设计方案 §3.4 步骤 2 / 补充设计 §5.2）。
 *
 * <p>唯一通道：{@link services/cognitiveClient#buildScenarioContext}
 * （POST `/api/v1/cognitive/context/build`，2026-10-11 网关实核）。请求三要素
 * （scenarioId + scope.tenantId + requiredAssets[]）与后端 400 校验规则同形：
 * 表单仅在满足三要素时可提交——这是禁用态提示，不替代服务端裁决。</p>
 *
 * <p>无假真理：ready=false 如实呈现「未就绪，不得继续依赖步骤」（§5.4 Run 侧约束）；
 * 错误族 400/403/409/422/429/503 按服务端 status+message+traceId 原样分支渲染，
 * 绝不本地合成 digest、就绪态或来源清单。幕 1（W Agent 目标理解）本批次不接
 * （无 DEEPSEEK key 时禁伪目标解析），目标段由蓝图既有 goalStatement 回显。</p>
 *
 * <p>六态：表单(idle)/提交中/结果/错误族/引擎 503 横幅(含 traceId)/未选蓝图。</p>
 */
import React, { useCallback, useMemo, useState } from 'react';
import LucideIcon from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  buildScenarioContext,
  packList,
  CONTEXT_SOURCE_TYPES,
  isCognitiveApiError,
  type ContextBuildResultDto,
  type ContextBuildInput,
  type ContextGapView,
  type ContextSourceRefInput,
} from '../../../../services/cognitiveClient';
import type { ScenarioBlueprintVO } from '../../../../services/scenarioBlueprintClient';

interface Props {
  scenarioId: string;
  blueprint: ScenarioBlueprintVO | null;
}

type Phase =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'result'; data: ContextBuildResultDto }
  | { kind: 'error'; status: number; code: string; msg: string; traceId?: string };

interface AssetRow extends ContextSourceRefInput {
  key: string;
}

let rowSeq = 0;
const newRow = (): AssetRow => ({ key: `r${++rowSeq}`, sourceType: 'DATA_ASSET', sourceId: '', required: true });

const ctxErrKey = (status: number): string => {
  switch (status) {
    case 400: return 'workspace.ctx.err.badreq';
    case 403: return 'workspace.ctx.err.denied';
    case 409: return 'workspace.ctx.err.stale';
    case 422: return 'workspace.ctx.err.conflict';
    case 429: return 'workspace.ctx.err.budget';
    case 503: return 'workspace.ctx.err.engine';
    default: return 'workspace.ctx.err.unknown';
  }
};

export default function ContextBuildPanel({ scenarioId, blueprint }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const scopeTenant = (blueprint?.scope as Record<string, unknown> | undefined)?.tenantId;
  const [tenantId, setTenantId] = useState(typeof scopeTenant === 'string' ? scopeTenant : '');
  const [rows, setRows] = useState<AssetRow[]>([]);
  const [asOfTime, setAsOfTime] = useState('');
  const [ttl, setTtl] = useState('');
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' });

  const canSubmit = useMemo(
    () =>
      scenarioId.trim() !== ''
      && tenantId.trim() !== ''
      && rows.length > 0
      && rows.every((r) => r.sourceId.trim() !== ''),
    [scenarioId, tenantId, rows],
  );

  const updateRow = (key: string, patch: Partial<AssetRow>): void => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };

  const submit = useCallback(async (): Promise<void> => {
    if (!canSubmit || !scenarioId) return;
    setPhase({ kind: 'submitting' });
    const input: ContextBuildInput = {
      requestId: typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : undefined,
      scenarioId,
      blueprintRef: blueprint ? { blueprintId: blueprint.id, version: String(blueprint.version) } : undefined,
      goal: blueprint?.goalStatement
        ? { statement: blueprint.goalStatement, successCriteria: blueprint.successCriteria ?? [] }
        : undefined,
      scope: { tenantId: tenantId.trim() },
      requiredAssets: rows.map((r) => ({
        sourceType: r.sourceType,
        sourceId: r.sourceId.trim(),
        version: r.version?.trim() || undefined,
        required: r.required !== false,
        successCritical: r.successCritical === true,
        evidenceRefs: r.evidenceRefs?.length ? r.evidenceRefs : undefined,
      })),
      expectedAsOfTime: asOfTime.trim() || undefined,
      ttlMillis: ttl.trim() !== '' && Number.isFinite(Number(ttl)) ? Number(ttl) : undefined,
    };
    try {
      const data = await buildScenarioContext(input);
      setPhase({ kind: 'result', data });
    } catch (e) {
      if (isCognitiveApiError(e)) {
        setPhase({ kind: 'error', status: e.status, code: e.code, msg: e.message, traceId: e.traceId });
      } else {
        setPhase({ kind: 'error', status: 0, code: 'UNKNOWN', msg: e instanceof Error ? e.message : String(e) });
      }
    }
  }, [canSubmit, scenarioId, blueprint, tenantId, rows, asOfTime, ttl]);

  if (!blueprint) {
    return (
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 text-xs ${styles.cardTextMuted}`}>
        <LucideIcon name="PackageSearch" size={13} className="inline mr-1" />
        {t('workspace.ctx.no_blueprint')}
      </div>
    );
  }

  const result = phase.kind === 'result' ? phase.data : null;
  const gaps = (result ? packList(result, 'gaps') : []) as ContextGapView[];
  const conflicts = result ? packList(result, 'conflicts') : [];
  const warnings = result ? packList(result, 'warnings') : [];

  const counts = result
    ? {
        sources: packList(result, 'sources').length,
        facts: packList(result, 'facts').length,
        knowledgeRefs: packList(result, 'knowledgeRefs').length,
        ontologyRefs: packList(result, 'ontologyRefs').length,
        cognitiveRefs: packList(result, 'cognitiveRefs').length,
        capabilityRefs: packList(result, 'capabilityRefs').length,
      }
    : null;

  const inputCls = `w-full text-xs px-2 py-1 rounded border ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`;

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-3`}>
      <div className="flex items-center gap-2 flex-wrap">
        <LucideIcon name="PackageSearch" size={15} className={styles.accentText} />
        <h3 className={`text-xs font-extrabold ${styles.cardText}`}>{t('workspace.ctx.title')}</h3>
        <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.ctx.subtitle')}</span>
      </div>

      {blueprint.goalStatement && (
        <div className={`text-[11px] ${styles.cardTextMuted} rounded border px-2 py-1.5 ${styles.cardBorder}`}>
          <span className="font-bold uppercase text-[9px] mr-1">{t('workspace.ctx.goal_echo')}</span>
          {blueprint.goalStatement}
        </div>
      )}

      {/* 请求表单（三要素与后端 400 规则同形） */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <label className="flex flex-col gap-1">
          <span className={`text-[9px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.ctx.label_tenant')}</span>
          <input className={inputCls} value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder={t('workspace.ctx.ph_tenant')} />
        </label>
        <label className="flex flex-col gap-1">
          <span className={`text-[9px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.ctx.label_asof')}</span>
          <input className={inputCls} value={asOfTime} onChange={(e) => setAsOfTime(e.target.value)} placeholder="2026-10-11T00:00:00Z" />
        </label>
        <label className="flex flex-col gap-1">
          <span className={`text-[9px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.ctx.label_ttl')}</span>
          <input className={inputCls} value={ttl} onChange={(e) => setTtl(e.target.value)} inputMode="numeric" placeholder="300000" />
        </label>
      </div>

      <div className="space-y-1.5">
        <div className="flex items-center justify-between">
          <span className={`text-[9px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.ctx.label_assets')}</span>
          <button
            type="button"
            onClick={() => setRows((prev) => [...prev, newRow()])}
            className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 rounded border cursor-pointer ${styles.cardBg} ${styles.accentText} ${styles.accentBorder}`}
          >
            <LucideIcon name="Plus" size={11} />
            {t('workspace.ctx.asset_add')}
          </button>
        </div>
        {rows.length === 0 && (
          <div className={`text-[11px] ${styles.cardTextMuted} border rounded px-2 py-2 ${styles.cardBorder}`}>{t('workspace.ctx.assets_empty')}</div>
        )}
        {rows.map((r) => (
          <div key={r.key} className={`border rounded p-2 grid grid-cols-2 sm:grid-cols-12 gap-1.5 items-center ${styles.cardBorder}`}>
            <select
              className={`${inputCls} sm:col-span-2`}
              value={r.sourceType}
              onChange={(e) => updateRow(r.key, { sourceType: e.target.value })}
            >
              {CONTEXT_SOURCE_TYPES.map((st) => (
                <option key={st} value={st}>{st}</option>
              ))}
            </select>
            <input
              className={`${inputCls} sm:col-span-4`}
              value={r.sourceId}
              onChange={(e) => updateRow(r.key, { sourceId: e.target.value })}
              placeholder={t('workspace.ctx.ph_source')}
            />
            <input
              className={`${inputCls} sm:col-span-2`}
              value={r.version ?? ''}
              onChange={(e) => updateRow(r.key, { version: e.target.value })}
              placeholder={t('workspace.ctx.col_version')}
            />
            <input
              className={`${inputCls} sm:col-span-2`}
              value={(r.evidenceRefs ?? []).join(',')}
              onChange={(e) => updateRow(r.key, { evidenceRefs: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) })}
              placeholder={t('workspace.ctx.col_evidence')}
            />
            <label className={`flex items-center gap-1 text-[10px] ${styles.cardText} sm:col-span-1 cursor-pointer`}>
              <input type="checkbox" checked={r.required !== false} onChange={(e) => updateRow(r.key, { required: e.target.checked })} />
              {t('workspace.ctx.col_required')}
            </label>
            <label className={`flex items-center gap-1 text-[10px] ${styles.cardText} sm:col-span-2 cursor-pointer`} title={t('workspace.ctx.col_critical')}>
              <input type="checkbox" checked={r.successCritical === true} onChange={(e) => updateRow(r.key, { successCritical: e.target.checked })} />
              {t('workspace.ctx.col_critical_short')}
            </label>
            <button
              type="button"
              onClick={() => setRows((prev) => prev.filter((x) => x.key !== r.key))}
              className={`text-[10px] font-bold px-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
              aria-label={t('workspace.ctx.asset_remove')}
            >
              <LucideIcon name="X" size={11} />
            </button>
          </div>
        ))}
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <button
          type="button"
          onClick={() => void submit()}
          disabled={!canSubmit || phase.kind === 'submitting'}
          className={`inline-flex items-center gap-1 text-xs font-bold px-3 py-1.5 rounded border cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
        >
          <LucideIcon name="Hammer" size={12} />
          {phase.kind === 'submitting' ? t('workspace.ctx.submitting') : t('workspace.ctx.submit')}
        </button>
        {!canSubmit && <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.ctx.need_inputs')}</span>}
      </div>

      {/* 错误族：按服务端 status 分支，message/traceId 原样呈现 */}
      {phase.kind === 'error' && (
        <div className={`rounded border p-3 text-[11px] space-y-1 ${phase.status === 503 ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}` : `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}`}>
          <div className="flex items-center gap-1.5 font-bold">
            <LucideIcon name={phase.status === 503 ? 'PlugZap' : 'AlertOctagon'} size={12} />
            {t(ctxErrKey(phase.status))}
          </div>
          <div className="opacity-80 break-words">{phase.msg}</div>
          {phase.traceId && <div className="font-mono text-[9px] opacity-60 break-all">traceId: {phase.traceId}</div>}
        </div>
      )}

      {/* 结果：digest/TTL/就绪/截断 + pack 计数 + gaps/conflicts/warnings */}
      {result && (
        <div className="space-y-2">
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded border ${
                result.ready ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
              }`}
            >
              <LucideIcon name={result.ready ? 'CircleCheck' : 'CircleOff'} size={11} />
              {result.ready ? t('workspace.ctx.ready') : t('workspace.ctx.not_ready')}
            </span>
            {result.truncated && (
              <span className={`text-[10px] px-2 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>{t('workspace.ctx.truncated')}</span>
            )}
            {result.blueprintRef?.blueprintId && (
              <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                {t('workspace.ctx.blueprint_ref', { id: result.blueprintRef.blueprintId, version: result.blueprintRef.version ?? '—' })}
              </span>
            )}
          </div>
          {!result.ready && <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.ctx.not_ready_hint')}</p>}

          <div className={`text-[10px] font-mono ${styles.cardTextMuted} break-all`}>
            {t('workspace.ctx.digest')}: {result.contextDigest ?? '—'}
            {result.ttlMillis !== undefined ? ` · TTL ${result.ttlMillis}ms` : ''}
            {result.expiresAt ? ` · exp ${result.expiresAt}` : ''}
          </div>

          {counts && (
            <div className={`text-[11px] ${styles.cardText}`}>
              {t('workspace.ctx.counts', {
                sources: counts.sources, facts: counts.facts, knowledge: counts.knowledgeRefs,
                ontology: counts.ontologyRefs, cognitive: counts.cognitiveRefs, capability: counts.capabilityRefs,
              })}
            </div>
          )}

          {gaps.length > 0 && (
            <div className="space-y-1">
              <div className={`text-[9px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.ctx.gaps_title', { n: gaps.length })}</div>
              {gaps.map((g, i) => (
                <div key={g.gapId ?? String(i)} className={`border rounded px-2 py-1.5 text-[10px] space-y-0.5 ${styles.cardBorder}`}>
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className={`font-mono font-bold ${styles.accentText}`}>{g.category ?? '—'}</span>
                    <span className={`${styles.cardTextMuted}`}>{g.severity ?? '—'}</span>
                    {g.status && <span className={`px-1 rounded border ${styles.cardBorder} ${styles.cardTextMuted}`}>{g.status}</span>}
                    {g.requiresApproval && <span className={`px-1 rounded border ${styles.warningBorder} ${styles.warningText}`}>{t('workspace.ctx.requires_approval')}</span>}
                  </div>
                  <div className={styles.cardText}>{g.description ?? '—'}</div>
                  {g.targetRef && <div className={`font-mono text-[9px] ${styles.cardTextMuted} break-all`}>{g.targetRef}</div>}
                </div>
              ))}
            </div>
          )}

          {conflicts.length > 0 && (
            <div className={`text-[10px] ${styles.warningText} ${styles.warningBg} border rounded px-2 py-1.5 ${styles.warningBorder}`}>
              {t('workspace.ctx.conflicts_title', { n: conflicts.length })}
              <div className="opacity-80 break-words">
                {conflicts.slice(0, 5).map((c, i) => (
                  <div key={i} className="font-mono text-[9px]">{typeof c === 'string' ? c : JSON.stringify(c)}</div>
                ))}
              </div>
            </div>
          )}

          {warnings.length > 0 && (
            <div className={`text-[10px] ${styles.cardTextMuted} space-y-0.5`}>
              <div className="font-bold uppercase text-[9px]">{t('workspace.ctx.warnings_title', { n: warnings.length })}</div>
              {warnings.slice(0, 8).map((w, i) => (
                <div key={i} className="break-words">{typeof w === 'string' ? w : JSON.stringify(w)}</div>
              ))}
            </div>
          )}

          {counts && counts.sources === 0 && gaps.length === 0 && conflicts.length === 0 && warnings.length === 0 && (
            <div className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.ctx.empty_pack')}</div>
          )}
        </div>
      )}
    </div>
  );
}

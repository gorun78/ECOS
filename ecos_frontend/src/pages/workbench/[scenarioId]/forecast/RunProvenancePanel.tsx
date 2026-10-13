/**
 * RunProvenancePanel — L3 运行溯源（复盘 S2 收口；前端设计方案 2026-10-11 §3.2 L3
 * 「受控运行：关联 blueprintVersion + contextDigest + 模型/工具版本 + 结果引用 + traceId」）。
 *
 * <p><b>字段来源逐项实证（能实拿的实拿，拿不到的一律「—」+ 来源缺失说明，禁造值）</b>：</p>
 * <ul>
 *   <li>蓝图版本 / contextDigest / artifactDigest / 蓝图 traceId：
 *       workspace `ScenarioBlueprintController`（`/api/v1/business/scenarios/{id}/blueprints`，
 *       经 `services/scenarioBlueprintClient#listBlueprints`）；后端 `ScenarioBlueprintVO`
 *       实有 `version(int)`、`context_digest`（Dao:35/:59 列映射）、`artifactDigest` 字段 —
 *       呈现口径为<b>场景当前权威蓝图</b>（UI 文案已注明），不是逐 run 回链。</li>
 *   <li>运行历史行：workspace `ScenarioRunController.history:58`
 *       （`GET /api/v1/workspace/scenarios/{id}/runs`）→ `WorkspaceDataDao.listRunHistory:447`
 *       实 SELECT：id/scenario_id/run_type/status/metric/deviation/degraded/..._result/create_time。
 *       逐 run 的 blueprint_version / context_digest <b>不在该列清单中</b>
 *       （V271 DDL 亦无）⇒ 渲染「—」并给 run_linkage_pending 说明（待后端增量）。</li>
 *   <li>traceId：gateway `RequestContextFilter:51` 对每个响应
 *       `setHeader("X-Request-Id", traceId)` — 经 `cognitiveClient#callJsonWithTrace` 实拿
 *       本次读取请求的 traceId；workspace run 出口未做 ApiResponse traceId 直通
 *       （`ScenarioRunController:54` 走裸 `ApiResponse.success(...)`），故此处采信响应头。</li>
 *   <li>模型/工具版本：`ScenarioRunService.run` 响应 Map（:170-178）与 ForecastRunView
 *       （formulaVersion 属预测 run 域，非本运行编排面）均未回传 run 级模型/工具版本
 *       ⇒ 恒「—」+ pending 说明。</li>
 * </ul>
 *
 * <p>六态（§3.3）：加载骨架 / 错误（含 503 引擎横幅 + traceId）/ 空（无 run 记录）/
 * 离线（NETWORK）/ 权限（403 经错误矩阵）/ 正常。移动断点复用 `useMediaQuery`。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Fingerprint, History, RefreshCw } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import Badge from '../../../../components/common/Badge';
import {
  CognitiveEmptyState,
  CognitiveErrorState,
  CognitiveSkeleton,
  cognitiveErrorTitleKey,
} from '../../../../pages/scenario/cognition/CognitionStateViews';
import {
  CognitiveApiError,
  isCognitiveApiError,
} from '../../../../services/cognitiveClient';
import {
  fetchScenarioRunHistory,
  type ScenarioRunRow,
} from '../../../../services/cognitive/outcomesApi';
import { listBlueprints, type ScenarioBlueprintVO } from '../../../../services/scenarioBlueprintClient';

export interface RunProvenancePanelProps {
  scenarioId: string;
  /** 拉取条数（默认 5，钳制 1~20 — 与 workspace history limit 上限内的保守读取面一致）。 */
  limit?: number;
}

type AsyncState<T> =
  | { kind: 'loading' }
  | { kind: 'ready'; value: T }
  | { kind: 'error'; error: CognitiveApiError };

/** 缺失值统一渲染「—」（禁 0 / 空串 / 上次值伪造）。 */
function orDash(v: string | number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  const s = String(v).trim();
  return s === '' ? '—' : s;
}

function toCognitiveError(e: unknown): CognitiveApiError {
  return isCognitiveApiError(e)
    ? e
    : new CognitiveApiError({ code: 'NETWORK', status: 0, message: String(e) });
}

export default function RunProvenancePanel({ scenarioId, limit = 5 }: RunProvenancePanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [runs, setRuns] = useState<AsyncState<{ rows: ScenarioRunRow[]; traceId?: string }>>({ kind: 'loading' });
  const [blueprint, setBlueprint] = useState<AsyncState<ScenarioBlueprintVO | null>>({ kind: 'loading' });

  const load = useCallback(async () => {
    setRuns({ kind: 'loading' });
    setBlueprint({ kind: 'loading' });
    // 两路并行独立降级：蓝图失败不吞 run 面，run 失败不吞蓝图面。
    const runsP = fetchScenarioRunHistory(scenarioId, limit)
      .then((r) => ({ kind: 'ready' as const, value: r }))
      .catch((e: unknown) => ({ kind: 'error' as const, error: toCognitiveError(e) }));
    const bpP = listBlueprints(scenarioId)
      .then((list) => {
        // 取真实回显中 version 最大者 = 当前权威版本（无行 ⇒ null 空态，不猜测）
        const latest = list.reduce<ScenarioBlueprintVO | null>(
          (acc, b) => (acc === null || (typeof b.version === 'number' && b.version > acc.version) ? b : acc),
          null,
        );
        return { kind: 'ready' as const, value: latest };
      })
      .catch((e: unknown) => ({ kind: 'error' as const, error: toCognitiveError(e) }));
    const [r, b] = await Promise.all([runsP, bpP]);
    setRuns(r);
    setBlueprint(b);
  }, [scenarioId, limit]);

  useEffect(() => {
    void load();
  }, [load]);

  const loading = runs.kind === 'loading' || blueprint.kind === 'loading';
  const runTrace = runs.kind === 'ready' ? runs.value.traceId : undefined;
  const bp = blueprint.kind === 'ready' ? blueprint.value : null;

  return (
    <section
      className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}
      data-testid="run-provenance"
      data-scenario-id={scenarioId}
    >
      <header className="flex items-center justify-between gap-2 mb-2 flex-wrap">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <Fingerprint size={13} />
          {t('workspace.l3.prov.title')}
        </h2>
        <button
          type="button"
          onClick={() => void load()}
          className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[10px] cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          data-testid="prov-reload"
        >
          <RefreshCw size={11} />
          {t('workspace.l3.prov.reload')}
        </button>
      </header>
      <p className={`text-[10px] ${styles.cardTextMuted} mb-3`}>{t('workspace.l3.prov.hint')}</p>

      {loading && <CognitiveSkeleton rows={3} />}

      {/* 溯源三连（版本 / 摘要 / traceId）：mono 小字结构化呈现 */}
      {!loading && (
        <div className={`grid gap-2 mb-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2 lg:grid-cols-3'}`}>
          <ProvTile
            label={t('workspace.l3.prov.blueprint_version')}
            value={bp ? `v${bp.version}` : '—'}
            note={
              blueprint.kind === 'error'
                ? t('workspace.l3.prov.blueprint_unavailable')
                : bp
                  ? `${orDash(bp.status)}`
                  : t('workspace.l3.prov.blueprint_none')
            }
            testId="prov-bp-version"
          />
          <ProvTile
            label={t('workspace.l3.prov.context_digest')}
            value={orDash(bp?.contextDigest)}
            mono
            note={bp?.contextDigest ? undefined : t('workspace.l3.prov.field_pending')}
            testId="prov-context-digest"
          />
          <ProvTile
            label={t('workspace.l3.prov.artifact_digest')}
            value={orDash(bp?.artifactDigest)}
            mono
            note={bp?.artifactDigest ? undefined : t('workspace.l3.prov.field_pending')}
            testId="prov-artifact-digest"
          />
          <ProvTile
            label={t('workspace.l3.prov.trace_id')}
            value={orDash(runTrace ?? bp?.traceId)}
            mono
            note={runTrace ? undefined : t('workspace.l3.prov.trace_pending')}
            testId="prov-trace-id"
          />
          <ProvTile
            label={t('workspace.l3.prov.model_tool_version')}
            value="—"
            note={t('workspace.l3.prov.model_tool_pending')}
            testId="prov-model-tool"
          />
          <ProvTile
            label={t('workspace.l3.prov.bp_trace_id')}
            value={orDash(bp?.traceId)}
            mono
            note={bp?.traceId ? undefined : t('workspace.l3.prov.field_pending')}
            testId="prov-bp-trace"
          />
        </div>
      )}

      {/* run 级蓝图回链缺口（如实说明，不伪造关联） */}
      {!loading && (
        <p className={`text-[10px] ${styles.cardTextMuted} mb-3`}>{t('workspace.l3.prov.run_linkage_pending')}</p>
      )}

      {/* 最近运行表（仅回显服务端实有列） */}
      <h3 className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1.5 flex items-center gap-1.5`}>
        <History size={12} />
        {t('workspace.l3.prov.runs_title')}
      </h3>

      {runs.kind === 'error' && (
        <CognitiveErrorState error={runs.error} titleKey={cognitiveErrorTitleKey(runs.error)} onRetry={() => void load()} />
      )}

      {runs.kind === 'ready' && runs.value.rows.length === 0 && (
        <CognitiveEmptyState labelKey="workspace.l3.prov.runs_empty" testId="prov-runs-empty" />
      )}

      {runs.kind === 'ready' && runs.value.rows.length > 0 && (
        <ul className="space-y-1.5">
          {runs.value.rows.map((r) => (
            <li
              key={r.runId}
              className={`rounded border px-2 py-1.5 text-[11px] flex flex-wrap items-center gap-2 ${styles.cardBorder}`}
              data-testid="prov-run-row"
            >
              <span className={`font-mono break-all ${styles.cardText}`} title={r.runId}>
                {r.runId.slice(0, 14)}
              </span>
              <Badge variant="neutral">{orDash(r.runType)}</Badge>
              <Badge variant={r.status === 'SUCCESS' ? 'success' : r.status ? 'warning' : 'neutral'}>
                {orDash(r.status)}
              </Badge>
              {r.degraded === true && (
                <span className={styles.warningText} data-testid="prov-run-degraded">
                  {t('workspace.l3.prov.degraded.yes')}
                </span>
              )}
              {r.degraded === false && <span className={styles.cardTextMuted}>{t('workspace.l3.prov.degraded.no')}</span>}
              <span className={`ml-auto font-mono text-[10px] ${styles.cardTextMuted}`}>{orDash(r.createTime)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** 溯源小块：label + mono 值 + 来源/缺失说明（缺值恒「—」）。 */
function ProvTile({
  label,
  value,
  note,
  mono,
  testId,
}: {
  label: string;
  value: string;
  note?: string;
  mono?: boolean;
  testId: string;
}) {
  const { styles } = useTheme();
  return (
    <div className={`rounded border px-2 py-1.5 ${styles.cardBorder}`} data-testid={testId}>
      <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted}`}>{label}</span>
      <span className={`block text-[11px] mt-0.5 break-all ${mono ? 'font-mono' : ''} ${styles.cardText}`}>{value}</span>
      {note && <span className={`block text-[10px] mt-0.5 ${styles.cardTextMuted}`}>{note}</span>}
    </div>
  );
}

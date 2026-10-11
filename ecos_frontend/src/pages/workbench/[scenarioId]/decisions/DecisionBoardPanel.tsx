/**
 * DecisionBoardPanel — 经营动作看板 + 回链（H-8 第一批 · P08，详设-09 §五 / §十一 H-8）
 *
 * <p>数据源 = workspace `GET /api/v1/workspace/scenarios/{id}/actions` 与
 * `GET /api/v1/workspace/scenarios/{id}/decisions`（经 `security-engine` RLS/CLS，默认 DENY）。
 * 读回为空 ⇒ 渲染既有空态（DENY 与「有权限但无数据」后端同形，前端不推断、不伪造行）。</p>
 *
 * <p>回链（P08 结案判据）：每条动作展示 `forecast_run_id`，并以路由内锚点
 * `?runId=` 跳转至本页 P09 四联视图；无 run 的动作显示「无回链」而非占位 id。</p>
 *
 * <p>审批：`POST /api/v1/workspace/actions/{actionId}/approve`，审批人由后端取 JWT 主体，
 * 前端不发 approver；失败按后端 code/traceId 原样呈现。</p>
 *
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Link2, ShieldAlert } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import Badge from '../../../../components/common/Badge';
import Button from '../../../../components/common/Button';
import MobileDataTable from '../../../../components/common/MobileDataTable';
import type { ColumnConfig } from '../../../../components/common/DataTable';
import DegradationBanner from '../../../../components/common/status/DegradationBanner';
import {
  CognitiveEmptyState,
  CognitiveSkeleton,
} from '../../../../pages/scenario/cognition/CognitionStateViews';
import {
  approveScenarioAction,
  formatExpectedImpact,
  isScenarioActionError,
  listScenarioActions,
  listScenarioDecisions,
  type ScenarioActionVO,
  type ScenarioDecisionVO,
} from '../../../../services/scenarioActionClient';

export interface DecisionBoardPanelProps {
  scenarioId: string;
  /** 当前 run 锚点（高亮命中行）。 */
  activeRunId?: string;
  /** 创建成功后外部触发刷新的版本号。 */
  refreshKey?: number;
}

export default function DecisionBoardPanel({ scenarioId, activeRunId = '', refreshKey = 0 }: DecisionBoardPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [actions, setActions] = useState<ScenarioActionVO[] | null>(null);
  const [decisions, setDecisions] = useState<ScenarioDecisionVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<string | null>(null);
  const [approveFailure, setApproveFailure] = useState<string | null>(null);
  const [busyActionId, setBusyActionId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setFailure(null);
    try {
      const [a, d] = await Promise.all([listScenarioActions(scenarioId), listScenarioDecisions(scenarioId)]);
      setActions(a);
      setDecisions(d);
    } catch (e) {
      setActions(null);
      setFailure(
        isScenarioActionError(e)
          ? `${e.message}${e.traceId ? ` · ${t('scenario.cognition.traceId')}: ${e.traceId}` : ''}`
          : e instanceof Error
            ? e.message
            : String(e),
      );
    } finally {
      setLoading(false);
    }
  }, [scenarioId, t]);

  useEffect(() => {
    void reload();
  }, [reload, refreshKey]);

  const decisionByAction = useMemo(() => {
    const m = new Map<string, ScenarioDecisionVO>();
    decisions.forEach((d) => {
      if (typeof d.actionId === 'string') m.set(d.actionId, d);
    });
    return m;
  }, [decisions]);

  const approve = useCallback(
    async (actionId: string) => {
      setBusyActionId(actionId);
      setApproveFailure(null);
      try {
        await approveScenarioAction(actionId);
        await reload();
      } catch (e) {
        setApproveFailure(
          isScenarioActionError(e)
            ? `${e.message}${e.traceId ? ` · ${t('scenario.cognition.traceId')}: ${e.traceId}` : ''}`
            : e instanceof Error
              ? e.message
              : String(e),
        );
      } finally {
        setBusyActionId(null);
      }
    },
    [reload, t],
  );

  const columns = useMemo<ColumnConfig<ScenarioActionVO>[]>(
    () => [
      { key: 'ownerUserId', label: t('scenario.p08.board.col.owner') },
      { key: 'dueDate', label: t('scenario.p08.board.col.due') },
      {
        key: 'expectedImpact',
        label: t('scenario.p08.board.col.impact'),
        align: 'right',
        render: (v) => <span className="font-mono">{formatExpectedImpact(v) ?? '—'}</span>,
      },
      { key: 'controlMetric', label: t('scenario.p08.board.col.metric') },
      {
        key: 'forecastRunId',
        label: t('scenario.p08.board.col.run_link'),
        render: (v: unknown, record) => {
          const runId = typeof v === 'string' && v.trim() ? v.trim() : '';
          if (!runId) {
            return (
              <span className={styles.cardTextMuted} data-testid={`no-run-${String(record.actionId ?? '')}`}>
                {t('scenario.p08.board.no_run')}
              </span>
            );
          }
          return (
            <Link
              to={`/workbench/${scenarioId}/decisions?runId=${encodeURIComponent(runId)}`}
              className={`inline-flex items-center gap-1 font-mono text-[11px] underline cursor-pointer ${styles.accentText}`}
              data-testid={`run-link-${runId}`}
            >
              <Link2 size={11} aria-hidden />
              {runId}
            </Link>
          );
        },
      },
      {
        key: 'status',
        label: t('scenario.p08.board.col.status'),
        render: (v: unknown) => {
          const status = typeof v === 'string' ? v : '';
          if (!status) return <span className={styles.cardTextMuted}>—</span>;
          const approved = /APPROVED|COMPLETED/i.test(status);
          return (
            <Badge variant={approved ? 'success' : 'warning'}>
              {approved ? t('scenario.p08.board.status.approved') : t('scenario.p08.board.status.pending')}
            </Badge>
          );
        },
      },
      {
        key: 'actionId',
        label: t('scenario.p08.board.col.action'),
        render: (_v, record) => {
          const actionId = typeof record.actionId === 'string' ? record.actionId : '';
          const decided = actionId ? decisionByAction.has(actionId) : false;
          if (!actionId) return <span className={styles.cardTextMuted}>—</span>;
          if (decided) {
            return (
              <span className={`inline-flex items-center gap-1 text-[11px] ${styles.successText}`}>
                <CheckCircle2 size={11} aria-hidden />
                {t('scenario.p08.board.approved')}
              </span>
            );
          }
          return (
            <Button
              variant="secondary"
              size="sm"
              disabled={busyActionId === actionId}
              onClick={() => void approve(actionId)}
              data-testid={`approve-${actionId}`}
            >
              {t('scenario.p08.board.approve')}
            </Button>
          );
        },
      },
    ],
    [approve, busyActionId, decisionByAction, scenarioId, styles.accentText, styles.cardTextMuted, styles.successText, t],
  );

  return (
    <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <header className="flex items-center justify-between gap-2 mb-2">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <ShieldAlert size={13} />
          {t('scenario.p08.board.title')}
        </h2>
        <button
          type="button"
          onClick={() => void reload()}
          disabled={loading}
          className={`text-[10px] font-bold underline cursor-pointer ${styles.accentText} disabled:opacity-50`}
          data-testid="board-refresh"
        >
          {t('scenario.p08.board.refresh')}
        </button>
      </header>

      {loading && <CognitiveSkeleton rows={3} />}

      {!loading && failure && <DegradationBanner active reasons={[failure]} />}

      {!loading && !failure && activeRunId && (
        <p className={`mb-2 text-[10px] font-mono ${styles.infoText}`} data-testid="board-active-run">
          {t('scenario.p08.board.active_run', { runId: activeRunId })}
        </p>
      )}

      {!loading && !failure && actions && actions.length === 0 && (
        <CognitiveEmptyState labelKey="scenario.p08.board.empty" testId="board-empty" />
      )}

      {!loading && !failure && actions && actions.length > 0 && (
        <MobileDataTable<ScenarioActionVO>
          columns={columns}
          data={actions}
          rowKey={(r) => String(r.actionId ?? `${r.ownerUserId}-${r.dueDate}`)}
          mobileConfig={{ headerKeys: ['ownerUserId', 'dueDate'], detailKeys: ['controlMetric', 'expectedImpact', 'forecastRunId'], actionColumnKey: 'actionId' }}
          hidePagination
        />
      )}

      {approveFailure && (
        <p role="alert" data-testid="board-approve-failure" className={`mt-2 text-[11px] ${styles.dangerText}`}>
          {approveFailure}
        </p>
      )}
    </section>
  );
}

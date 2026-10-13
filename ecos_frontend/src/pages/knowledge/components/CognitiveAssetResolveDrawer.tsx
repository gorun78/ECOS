/**
 * CognitiveAssetResolveDrawer — 认知资产只读解析抽屉（议题1 / 补充设计 §13 SUP-COG-02）。
 *
 * 打开即调 resolveCognitiveAsset 真实解析（服务端按 bel/hyp/mod 前缀路由）。
 * 六态矩阵（前端铁律 §8）：加载 / 空(lifecycle null→legacy 展示) / 错误(503 横幅+traceId+重试) / 边界 / 权限 / 离线。
 * 无假真理：解析失败一律如实呈现错误态，绝不用列表行兜底冒充解析结果。
 * 只读：无编辑/导入按钮（§8.4 C/K 互斥守护）。
 */
import React, { useCallback, useEffect, useState } from 'react';
import { X, AlertTriangle, RefreshCw, Loader2 } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  resolveCognitiveAsset,
  isCognitiveApiError,
  type CognitiveAssetResolution,
} from '../../../services/cognitiveClient';
import {
  assetStatusLabelKey,
  probKindLabelKey,
  isUncalibratedKind,
  isLegacyKind,
} from '../services/cognitiveAsset';

interface Props {
  assetId: string | null;
  onClose: () => void;
}

type State =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'ok'; data: CognitiveAssetResolution }
  | { kind: 'error'; code: string; status: number; message: string; traceId?: string };

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  const { styles } = useTheme();
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[10px] uppercase tracking-wider opacity-50">{label}</span>
      <span className="text-xs" style={{ color: styles.cardText }}>{value ?? '—'}</span>
    </div>
  );
}

export default function CognitiveAssetResolveDrawer({ assetId, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<State>({ kind: 'idle' });

  const run = useCallback(async (id: string) => {
    setState({ kind: 'loading' });
    try {
      const data = await resolveCognitiveAsset(id);
      setState({ kind: 'ok', data });
    } catch (e) {
      if (isCognitiveApiError(e)) {
        setState({ kind: 'error', code: e.code, status: e.status, message: e.message, traceId: e.traceId });
      } else {
        setState({ kind: 'error', code: 'NETWORK', status: 0, message: e instanceof Error ? e.message : String(e) });
      }
    }
  }, []);

  useEffect(() => {
    if (assetId) void run(assetId);
    else setState({ kind: 'idle' });
  }, [assetId, run]);

  if (!assetId) return null;

  const isEngineDown = state.kind === 'error' && (state.code === 'ENGINE_DISABLED' || state.code === 'SCHEMA_NOT_READY');

  return (
    <aside
      className="fixed right-0 top-0 h-full w-full sm:w-96 z-40 border-l flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}
      role="dialog"
      aria-label={t('knowledge.cognitiveAsset.drawer_title')}
    >
      <div className="h-12 flex items-center justify-between px-4 border-b" style={{ borderColor: styles.cardBorder }}>
        <span className="text-xs font-semibold">{t('knowledge.cognitiveAsset.drawer_title')}</span>
        <button type="button" onClick={onClose} className="p-1 rounded hover:opacity-70 cursor-pointer" aria-label={t('common.close') || 'close'}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4 text-xs">
        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 opacity-60">
            <Loader2 className="w-4 h-4 animate-spin" />{t('knowledge.cognitiveAsset.drawer_loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <div className="rounded-md border p-3 flex flex-col gap-2" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4" />
              <span>{isEngineDown ? t('knowledge.cognitiveAsset.engine_unavailable') : t('knowledge.cognitiveAsset.resolve_failed')}</span>
            </div>
            <div className="opacity-70 break-words">{state.message}</div>
            {state.traceId && (
              <div className="font-mono text-[10px] opacity-60 break-all">traceId: {state.traceId}</div>
            )}
            <button
              type="button"
              onClick={() => void run(assetId)}
              className="inline-flex items-center gap-1.5 self-start px-2.5 py-1 rounded border text-[11px] cursor-pointer"
              style={{ borderColor: styles.cardBorder, color: styles.cardText }}
            >
              <RefreshCw className="w-3 h-3" />{t('knowledge.cognitiveAsset.retry')}
            </button>
          </div>
        )}

        {state.kind === 'ok' && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <Field label={t('knowledge.cognitiveAsset.field_assetId')} value={state.data.assetId} />
              <Field
                label={t('knowledge.cognitiveAsset.field_class')}
                value={t(`knowledge.cognitiveAsset.class.${state.data.assetClass}`) || state.data.assetClass}
              />
              <Field
                label={t('knowledge.cognitiveAsset.field_lifecycle')}
                value={
                  state.data.lifecycleState
                    ? assetStatusLabelKey(state.data.lifecycleState)
                      ? t(assetStatusLabelKey(state.data.lifecycleState) as string) || state.data.lifecycleState
                      : state.data.lifecycleState
                    : t('knowledge.cognitiveAsset.lifecycle_legacy')
                }
              />
              <Field
                label={t('knowledge.cognitiveAsset.field_lifecycle_source')}
                value={state.data.lifecycleSource}
              />
            </div>

            {state.data.legacyStatus && !state.data.lifecycleState && (
              <Field label={t('knowledge.cognitiveAsset.field_legacy_status')} value={state.data.legacyStatus} />
            )}

            {state.data.assetClass === 'BELIEF' && (
              <div className="rounded-md border p-3 flex flex-col gap-3" style={{ borderColor: styles.cardBorder }}>
                <div className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.cognitiveAsset.confidence_title')}</div>
                <div className="grid grid-cols-2 gap-3">
                  <Field
                    label={t('knowledge.cognitiveAsset.field_probability')}
                    value={state.data.confidenceProbability !== null && state.data.confidenceProbability !== undefined
                      ? state.data.confidenceProbability.toFixed(4)
                      : '—'}
                  />
                  <Field
                    label={t('knowledge.cognitiveAsset.field_prob_kind')}
                    value={
                      probKindLabelKey(state.data.confidenceKind ?? null)
                        ? t(probKindLabelKey(state.data.confidenceKind ?? null) as string) || state.data.confidenceKind
                        : (state.data.confidenceKind ?? '—')
                    }
                  />
                </div>
                {isUncalibratedKind(state.data.confidenceKind ?? null) && (
                  <div className="flex items-center gap-1.5 text-[11px]" style={{ color: styles.warningText }}>
                    <AlertTriangle className="w-3 h-3" />{t('knowledge.cognitiveAsset.uncalibrated_warn')}
                  </div>
                )}
                {isLegacyKind(state.data.confidenceKind ?? null) && (
                  <div className="text-[11px] opacity-60">{t('knowledge.cognitiveAsset.legacy_kind_note')}</div>
                )}
                {state.data.calibrationRef && (
                  <Field label={t('knowledge.cognitiveAsset.field_calibration_ref')} value={state.data.calibrationRef} />
                )}
              </div>
            )}

            <div className="border-t pt-3 flex flex-col gap-3" style={{ borderColor: styles.cardBorder }}>
              <Field label={t('knowledge.cognitiveAsset.field_objectKey')} value={state.data.objectKey} />
              {/* 来源标识（§1.4.3 流程 3 / 复盘 S6）：resolve DTO 服务端实核不含
                  sourceScenarioId/blueprintId/originRef 类字段 → 如实呈现受限说明，不伪造跳转。 */}
              <Field
                label={t('knowledge.cognitiveAsset.source_title')}
                value={<span className="opacity-60">{t('knowledge.cognitiveAsset.source_unavailable')}</span>}
              />
            </div>

            <p className="text-[10px] opacity-50 leading-relaxed">{t('knowledge.cognitiveAsset.readonly_note')}</p>
          </>
        )}
      </div>
    </aside>
  );
}

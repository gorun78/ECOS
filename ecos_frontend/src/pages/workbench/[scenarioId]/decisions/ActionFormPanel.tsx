/**
 * ActionFormPanel — 经营动作创建表单（H-8 第一批 · P08 动作回链，详设-09 §五 / PRD-09 §2.4）
 *
 * <p>五必填与后端 `ActionProposalValidator.java:19-38` 逐字段对齐：
 * 负责人 / 截止日 / 审批人 / 预期影响额 / 控制指标。提交载荷携带
 * `forecast_run_id`（详设-09 §1.1 #5：动作必回链来源 run）。</p>
 *
 * <p>审批人反伪造（卷07 F07-10）：后端一律取 JWT subject，**请求体不含 approver**；
 * 本面板只把登录主体作为只读项渲染，用于「approver 缺失」的即时提示。</p>
 *
 * <p>真值纪律：400 `ACTION_FIELDS_MISSING` 的 `missingFields[]` 原样驱动字段级红字；
 * 503 `COGNITIVE_UNAVAILABLE` 走既有 `DegradationBanner`；前端不做任何数值兜底
 * （预期影响额为自由输入，非模型输出）。</p>
 *
 * @license Apache-2.0
 */

import React, { useCallback, useMemo, useState } from 'react';
import { CalendarDays, Crosshair, Link2, UserRound } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import Button from '../../../../components/common/Button';
import DegradationBanner from '../../../../components/common/status/DegradationBanner';
import DemoBadge from '../../../../components/common/status/DemoBadge';
import {
  ACTION_REQUIRED_FIELDS,
  createScenarioAction,
  emptyActionDraft,
  isScenarioActionError,
  readApproverSubject,
  toActionPayload,
  validateActionDraft,
  type ActionDraft,
  type ActionRequiredField,
  type ScenarioActionResult,
} from '../../../../services/scenarioActionClient';

export interface ActionFormPanelProps {
  scenarioId: string;
  /** 来源预测 run（URL 参数带入；缺省则动作无回链，后端不做 FORMAL 校验）。 */
  forecastRunId?: string;
  /** 创建成功回调（父级据此刷新看板）。 */
  onCreated?: (result: ScenarioActionResult) => void;
}

const INPUT_CLS = 'w-full rounded border px-2 py-1.5 text-xs outline-none';

export default function ActionFormPanel({ scenarioId, forecastRunId = '', onCreated }: ActionFormPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const approver = useMemo(() => readApproverSubject(), []);
  const [draft, setDraft] = useState<ActionDraft>(() => emptyActionDraft(forecastRunId));
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverMissing, setServerMissing] = useState<ActionRequiredField[]>([]);
  const [failure, setFailure] = useState<{ title: string; message: string; reasons: string[] } | null>(null);
  const [succeededId, setSucceededId] = useState<string | null>(null);

  const localMissing = useMemo(() => validateActionDraft(draft, approver), [draft, approver]);
  const missing = useMemo(() => {
    const merged = new Set<ActionRequiredField>([...(touched ? localMissing : []), ...serverMissing]);
    return merged;
  }, [localMissing, serverMissing, touched]);

  const setField = useCallback(<K extends keyof ActionDraft>(key: K, value: ActionDraft[K]) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
    setSucceededId(null);
  }, []);

  const submit = useCallback(async () => {
    setTouched(true);
    setServerMissing([]);
    setFailure(null);
    setSucceededId(null);
    const local = validateActionDraft(draft, approver);
    if (local.length > 0) return;
    setSubmitting(true);
    try {
      const result = await createScenarioAction(scenarioId, toActionPayload(draft));
      const id = typeof result.actionId === 'string' ? result.actionId : null;
      setSucceededId(id);
      onCreated?.(result);
    } catch (e) {
      if (isScenarioActionError(e)) {
        if (e.code === 'ACTION_FIELDS_MISSING' && e.missingFields.length > 0) {
          setServerMissing(e.missingFields);
        } else {
          setFailure({
            title: e.code === 'COGNITIVE_UNAVAILABLE' ? t('scenario.p08.form.err.engine_unavailable') : t('scenario.p08.form.err.submit_failed'),
            message: e.message,
            reasons: e.traceId ? [`${t('scenario.cognition.traceId')}: ${e.traceId}`] : [],
          });
        }
      } else {
        setFailure({
          title: t('scenario.p08.form.err.submit_failed'),
          message: e instanceof Error ? e.message : String(e),
          reasons: [],
        });
      }
    } finally {
      setSubmitting(false);
    }
  }, [approver, draft, onCreated, scenarioId, t]);

  const fieldError = (key: ActionRequiredField): string | null =>
    missing.has(key) ? t('scenario.p08.form.field_required') : null;

  return (
    <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <header className="flex items-center justify-between gap-2 mb-2">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <Link2 size={13} />
          {t('scenario.p08.form.title')}
        </h2>
        <DemoBadge />
      </header>
      <p className={`text-[10px] ${styles.cardTextMuted} mb-3`}>{t('scenario.p08.form.subtitle')}</p>

      <div className="space-y-3">
        {/* 负责人 */}
        <Field name="owner" label={t('scenario.p08.form.owner')} icon={<UserRound size={11} />} error={fieldError('owner')} required>
          <input
            type="text"
            value={draft.ownerUserId}
            onChange={(e) => setField('ownerUserId', e.target.value)}
            placeholder={t('scenario.p08.form.owner_placeholder')}
            aria-invalid={fieldError('owner') ? true : undefined}
            data-testid="action-field-owner"
            className={`${INPUT_CLS} ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>

        {/* 截止日 */}
        <Field name="dueDate" label={t('scenario.p08.form.due_date')} icon={<CalendarDays size={11} />} error={fieldError('dueDate')} required>
          <input
            type="date"
            value={draft.dueDate}
            onChange={(e) => setField('dueDate', e.target.value)}
            aria-invalid={fieldError('dueDate') ? true : undefined}
            data-testid="action-field-dueDate"
            className={`${INPUT_CLS} ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>

        {/* 预期影响额（NUMERIC(18,2)：字符串透传保精度，禁 0 兜底） */}
        <Field name="expectedImpact" label={t('scenario.p08.form.expected_impact')} icon={<Crosshair size={11} />} error={fieldError('expectedImpact')} required>
          <input
            type="text"
            inputMode="decimal"
            value={draft.expectedImpact}
            onChange={(e) => setField('expectedImpact', e.target.value)}
            placeholder={t('scenario.p08.form.expected_impact_placeholder')}
            aria-invalid={fieldError('expectedImpact') ? true : undefined}
            data-testid="action-field-expectedImpact"
            className={`${INPUT_CLS} font-mono ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>

        {/* 控制指标 */}
        <Field name="controlMetric" label={t('scenario.p08.form.control_metric')} error={fieldError('controlMetric')} required>
          <input
            type="text"
            value={draft.controlMetric}
            onChange={(e) => setField('controlMetric', e.target.value)}
            placeholder={t('scenario.p08.form.control_metric_placeholder')}
            aria-invalid={fieldError('controlMetric') ? true : undefined}
            data-testid="action-field-controlMetric"
            className={`${INPUT_CLS} ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>

        {/* 审批人：只读取登录主体（后端从 JWT 取，请求体不含此字段） */}
        <Field name="approver" label={t('scenario.p08.form.approver')} error={fieldError('approver')} required>
          <div
            role="text"
            data-testid="action-field-approver"
            aria-invalid={fieldError('approver') ? true : undefined}
            className={`${INPUT_CLS} ${styles.inputBg} ${styles.inputBorder} ${approver ? styles.cardText : styles.dangerText}`}
          >
            {approver || t('scenario.p08.form.approver_missing')}
          </div>
          <p className={`mt-1 text-[10px] ${styles.cardTextMuted}`}>{t('scenario.p08.form.approver_readonly_hint')}</p>
        </Field>

        {/* run 回链（来源锚点） */}
        <Field name="forecastRunId" label={t('scenario.p08.form.forecast_run')} hint={forecastRunId ? undefined : t('scenario.p08.form.forecast_run_absent')}>
          <input
            type="text"
            value={draft.forecastRunId}
            onChange={(e) => setField('forecastRunId', e.target.value)}
            placeholder={t('scenario.p08.form.forecast_run_placeholder')}
            data-testid="action-field-forecastRunId"
            className={`${INPUT_CLS} font-mono ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>

        {/* 说明（非必填） */}
        <Field name="description" label={t('scenario.p08.form.description')}>
          <textarea
            value={draft.description}
            onChange={(e) => setField('description', e.target.value)}
            rows={2}
            data-testid="action-field-description"
            className={`${INPUT_CLS} resize-y ${styles.inputBg} ${styles.inputBorder} ${styles.cardText}`}
          />
        </Field>
      </div>

      {/* 必填未齐：逐字段红字已在各 Field 渲染，此处给汇总（键名 = 后端 missingFields 词表） */}
      {missing.size > 0 && (
        <p role="alert" data-testid="action-form-missing" className={`mt-3 text-[11px] ${styles.dangerText}`}>
          {t('scenario.p08.form.missing_summary', { fields: Array.from(missing).join(', ') })}
        </p>
      )}

      {failure && (
        <div className="mt-3">
          <DegradationBanner active reasons={[failure.title, failure.message, ...failure.reasons]} />
        </div>
      )}

      {succeededId && (
        <p role="status" data-testid="action-form-success" className={`mt-3 text-[11px] ${styles.successText}`}>
          {t('scenario.p08.form.created', { id: succeededId })}
        </p>
      )}

      <div className="mt-4 flex items-center justify-between gap-2">
        <span className={`text-[10px] ${styles.cardTextMuted}`}>
          {t('scenario.p08.form.required_hint', { total: ACTION_REQUIRED_FIELDS.length })}
        </span>
        <Button variant="primary" size="sm" disabled={submitting} onClick={() => void submit()} data-testid="action-form-submit">
          {submitting ? t('scenario.p08.form.submitting') : t('scenario.p08.form.submit')}
        </Button>
      </div>
    </section>
  );
}

/** 字段容器：标签 + 必填星号 + 字段级错误（错误文案来自后端 missingFields 或本地校验）。 */
function Field({
  name,
  label,
  icon,
  required,
  error,
  hint,
  children,
}: {
  /** 稳定锚点名（= 后端 missingFields 词表键或载荷字段名），供门禁用例定位 */
  name?: string;
  label: string;
  icon?: React.ReactNode;
  required?: boolean;
  error?: string | null;
  hint?: string;
  children: React.ReactNode;
}) {
  const { styles } = useTheme();
  return (
    <div>
      <label className={`flex items-center gap-1 text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>
        {icon}
        <span>{label}</span>
        {required && <span className={styles.dangerText}>*</span>}
      </label>
      {children}
      {error && (
        <span className={`block mt-1 text-[10px] ${styles.dangerText}`} data-testid={name ? `action-error-${name}` : 'action-error'}>
          {error}
        </span>
      )}
      {hint && !error && <span className={`block mt-1 text-[10px] ${styles.cardTextMuted}`}>{hint}</span>}
    </div>
  );
}

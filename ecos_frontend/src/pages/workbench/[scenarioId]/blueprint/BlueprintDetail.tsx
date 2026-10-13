/**
 * BlueprintDetail — 蓝图逻辑模型只读投影（补充设计 §6.2）。
 *
 * <p>展示 goal/successCriteria、scope、依赖、能力、缺口、测试、审批发布摘要。
 * 字段一律来自后端 VO，空值显式呈现「—」，禁本地合成。子段以结构化列表渲染，
 * 原始后端字段名不外泄为面向业务用户的标题（标题走 i18n）。</p>
 */
import React from 'react';
import LucideIcon from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import type { ScenarioBlueprintVO } from '../../../../services/scenarioBlueprintClient';

function firstStr(row: Record<string, unknown>, keys: string[]): string | null {
  for (const k of keys) {
    const v = row[k];
    if (typeof v === 'string' && v.trim()) return v;
  }
  return null;
}

export default function BlueprintDetail({ bp }: { bp: ScenarioBlueprintVO }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const dash = t('workspace.bp.empty_field');

  const criteria = Array.isArray(bp.successCriteria) ? bp.successCriteria : [];
  const deps = bp.dependencies ?? [];
  const caps = bp.capabilities ?? [];
  const gaps = bp.gaps ?? [];
  const tests = bp.tests ?? [];
  const scope = bp.scope ?? {};

  return (
    <div className="grid gap-3 md:grid-cols-2">
      {/* 目标 */}
      <Section icon="Target" title={t('workspace.bp.goal_statement')}>
        <p className={`text-xs ${styles.cardText} leading-relaxed`}>{bp.goalStatement || dash}</p>
        {criteria.length > 0 && (
          <ul className={`mt-1.5 text-[11px] ${styles.cardTextMuted} list-disc pl-4 space-y-0.5`}>
            {criteria.map((c, i) => (
              <li key={i}>{String(c)}</li>
            ))}
          </ul>
        )}
      </Section>

      {/* 范围（行275 订正：嵌套 JSON 值不再单行直排，折叠进 <details> 展开查看） */}
      <Section icon="Layers" title={t('workspace.bp.scope_title')}>
        {Object.keys(scope).length === 0 ? (
          <span className={`text-xs ${styles.cardTextMuted}`}>{dash}</span>
        ) : (
          <ul className={`text-[11px] ${styles.cardText} space-y-0.5`}>
            {Object.entries(scope).map(([k, v]) =>
              typeof v === 'object' && v !== null ? (
                <li key={k}>
                  <details className={`rounded border px-1.5 py-1 ${styles.cardBorder}`}>
                    <summary className={`cursor-pointer select-none ${styles.cardTextMuted}`}>
                      {k}
                      <span className="ml-1 font-mono opacity-60">{'{…}'}</span>
                    </summary>
                    <pre className={`mt-1 text-[10px] font-mono whitespace-pre-wrap break-all ${styles.cardTextMuted}`}>
                      {JSON.stringify(v, null, 2)}
                    </pre>
                  </details>
                </li>
              ) : (
                <li key={k} className="flex gap-1">
                  <span className={styles.cardTextMuted}>{k}:</span>
                  <span className="truncate">{String(v)}</span>
                </li>
              ),
            )}
          </ul>
        )}
      </Section>

      {/* 依赖 */}
      <Section icon="Link2" title={`${t('workspace.bp.deps_title')} (${deps.length})`}>
        {deps.length === 0 ? (
          <span className={`text-xs ${styles.cardTextMuted}`}>{dash}</span>
        ) : (
          <ul className={`text-[11px] ${styles.cardText} space-y-0.5`}>
            {deps.map((d, i) => (
              <li key={i} className="truncate">
                <span className={`mr-1 font-mono ${styles.accentText}`}>{firstStr(d, ['type']) ?? '·'}</span>
                {firstStr(d, ['id', 'name', 'assetId']) ?? dash}
                {firstStr(d, ['status']) ? <span className={`ml-1 ${styles.cardTextMuted}`}>[{firstStr(d, ['status'])}]</span> : null}
              </li>
            ))}
          </ul>
        )}
      </Section>

      {/* 能力 */}
      <Section icon="Wrench" title={`${t('workspace.bp.caps_title')} (${caps.length})`}>
        {caps.length === 0 ? (
          <span className={`text-xs ${styles.cardTextMuted}`}>{dash}</span>
        ) : (
          <ul className={`text-[11px] ${styles.cardText} space-y-0.5`}>
            {caps.map((c, i) => (
              <li key={i} className="truncate font-mono">
                {firstStr(c, ['capabilityId', 'id']) ?? dash}
                {firstStr(c, ['contractVersion']) ? ` @${firstStr(c, ['contractVersion'])}` : ''}
              </li>
            ))}
          </ul>
        )}
      </Section>

      {/* 缺口 */}
      <Section icon="AlertTriangle" title={`${t('workspace.bp.gaps_title')} (${gaps.length})`}>
        {gaps.length === 0 ? (
          <span className={`text-xs ${styles.cardTextMuted}`}>{dash}</span>
        ) : (
          <ul className={`text-[11px] ${styles.cardText} space-y-1`}>
            {gaps.map((g, i) => (
              <li key={i}>
                <div className="flex items-center gap-1">
                  <span className={`font-bold ${styles.warningText}`}>{firstStr(g, ['gapId', 'id']) ?? `#${i + 1}`}</span>
                  {firstStr(g, ['category']) ? <span className={styles.cardTextMuted}>· {firstStr(g, ['category'])}</span> : null}
                  {firstStr(g, ['severity']) ? <span className={`ml-1 text-[10px] px-1 rounded border ${styles.dangerBorder} ${styles.dangerText}`}>{firstStr(g, ['severity'])}</span> : null}
                  {firstStr(g, ['status']) ? <span className={`ml-1 text-[10px] ${styles.cardTextMuted}`}>{firstStr(g, ['status'])}</span> : null}
                </div>
                {firstStr(g, ['description']) ? <p className={`${styles.cardTextMuted} mt-0.5`}>{firstStr(g, ['description'])}</p> : null}
              </li>
            ))}
          </ul>
        )}
      </Section>

      {/* 测试 */}
      <Section icon="FlaskConical" title={`${t('workspace.bp.tests_title')} (${tests.length})`}>
        {tests.length === 0 ? (
          <span className={`text-xs ${styles.cardTextMuted}`}>{dash}</span>
        ) : (
          <ul className={`text-[11px] ${styles.cardText} space-y-0.5`}>
            {tests.map((tc, i) => (
              <li key={i} className="truncate">
                <span className={`mr-1 font-mono ${styles.infoText}`}>{firstStr(tc, ['priority', 'level']) ?? 'T'}</span>
                {firstStr(tc, ['name', 'testId', 'id', 'description']) ?? dash}
                {firstStr(tc, ['status', 'result']) ? <span className={`ml-1 ${styles.cardTextMuted}`}>[{firstStr(tc, ['status', 'result'])}]</span> : null}
              </li>
            ))}
          </ul>
        )}
      </Section>

      {/* 审批与发布（跨列） */}
      <div className="md:col-span-2">
        <Section icon="Stamp" title={t('workspace.bp.approval_title')}>
          <div className={`grid gap-x-4 gap-y-1 text-[11px] sm:grid-cols-2 ${styles.cardText}`}>
            <Field label={t('workspace.bp.approval_ref')} value={bp.approvalRef || dash} mono />
            <Field label={t('workspace.bp.artifact_digest')} value={bp.artifactDigest || dash} mono />
            <Field label={t('workspace.bp.context_digest')} value={bp.contextDigest || dash} mono />
            <Field label={t('workspace.bp.released_at')} value={bp.releasedTime || dash} />
            <Field label={t('workspace.bp.actor')} value={bp.actor || dash} />
            <Field label={t('workspace.bp.traceId')} value={bp.traceId || dash} mono />
          </div>
        </Section>
      </div>
    </div>
  );
}

function Section({ icon, title, children }: { icon: string; title: string; children: React.ReactNode }) {
  const { styles } = useTheme();
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <h4 className={`text-[10px] font-bold uppercase mb-1.5 flex items-center gap-1.5 ${styles.cardTextMuted}`}>
        <LucideIcon name={icon} size={12} />
        {title}
      </h4>
      {children}
    </div>
  );
}

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  const { styles } = useTheme();
  return (
    <div className="flex gap-1 items-baseline">
      <span className={styles.cardTextMuted}>{label}:</span>
      <span className={mono ? 'font-mono truncate' : 'truncate'}>{value}</span>
    </div>
  );
}

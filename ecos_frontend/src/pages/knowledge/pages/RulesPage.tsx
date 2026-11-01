/**
 * RulesPage — 知识工作台「规则」Tab（R11 / §6.2.6⑥ 裁决落位；#147I）。
 *
 * 裁决（2026-10-16 PMO）：Rule Center 不升格为物理模块，产品入口合入知识工作台，
 * 与认知资产中心同构——本页只是「规则能力的第七个入口」，规则单一事实源仍是
 * ontology-engine 的 {@code ecos_ontology_rule}（引擎侧不新增 JAR/schema/容器）。
 *
 * 数据面：GET /api/v1/ecos/rules（既有端点，零新增端点）。
 * 未接入语义（DC-1）：版本/追踪缺失的规则显式显示 not_available，不伪造版本号；
 * 列表整体不可用（通道未就绪）时渲染 EmptyState，不以空表冒充「没有规则」。
 *
 * 主题 §4.1：0 硬编码色值（useTheme().styles）；i18n §4.3：0 硬编码中文。
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Loader2, RefreshCw, ScrollText } from 'lucide-react';
import { apiFetchData } from '../../../api';
import { API_BASE } from '../../../services/httpClient';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import EmptyState from '../../../components/common/EmptyState';

interface OntologyRuleVO {
  id: string;
  code?: string;
  name?: string;
  ruleType?: string;
  ruleCarrier?: string;
  ruleLayer?: string;
  scope?: string;
  versionNo?: string;
  gitRef?: string;
  status?: string;
  enabled?: number;
}

// 基址单源（C1/B5-25）：只允许 `${API_BASE}` 派生态，禁私设 "/api..." 字面量
const RULES_URL = `${API_BASE}/v1/ecos/rules`;

const LAYER_ORDER = ['STRUCTURAL', 'VALIDATION', 'BUSINESS', 'POLICY', 'COGNITIVE'] as const;

export default function RulesPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [rules, setRules] = useState<OntologyRuleVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setUnavailable(false);
    try {
      const data = await apiFetchData<OntologyRuleVO[]>(RULES_URL);
      // 后端不可用腿以 {status:'not_available', reason} 表达；数组才按列表消费
      if (Array.isArray(data)) {
        setRules(data);
        return;
      }
      const maybe = data as unknown as { status?: string; reason?: string };
      if (maybe?.status === 'not_available') {
        setUnavailable(true);
        setError(maybe.reason ?? null);
        return;
      }
      setRules([]);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('not_available') || msg.includes('不可用')) {
        setUnavailable(true);
      }
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const layerBuckets = useMemo(() => {
    const map: Record<string, number> = {};
    rules.forEach((r) => {
      const key = r.ruleLayer ?? '';
      map[key] = (map[key] ?? 0) + 1;
    });
    return map;
  }, [rules]);

  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h2 className={`text-base font-bold ${styles.cardText}`}>{t('knowledge.rules.title')}</h2>
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>{t('knowledge.rules.subtitle')}</p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardText} hover:opacity-90`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          {t('knowledge.rules.refresh')}
        </button>
      </header>

      <div className="flex flex-wrap gap-2">
        {LAYER_ORDER.map((layer) => (
          <span
            key={layer}
            className={`px-2.5 py-1 rounded-full text-[11px] font-semibold border ${styles.cardBorder} ${styles.cardTextMuted}`}
          >
            {layer}: {layerBuckets[layer] ?? 0}
          </span>
        ))}
      </div>

      {loading ? (
        <div className={`flex items-center gap-2 py-16 justify-center ${styles.cardTextMuted}`}>
          <Loader2 className="w-4 h-4 animate-spin" />
          <span className="text-xs">{t('knowledge.rules.loading')}</span>
        </div>
      ) : unavailable ? (
        <EmptyState
          icon={<AlertTriangle className="w-10 h-10 opacity-50" />}
          title={t('knowledge.rules.not_available_title')}
          description={error ?? t('knowledge.rules.not_available_desc')}
        />
      ) : rules.length === 0 ? (
        <EmptyState icon={<ScrollText className="w-10 h-10 opacity-40" />} title={t('knowledge.rules.empty_title')} description={t('knowledge.rules.empty_desc')} />
      ) : (
        <div className={`rounded-xl border overflow-x-auto ${styles.cardBorder} ${styles.cardBg}`}>
          <table className={`w-full text-xs ${styles.cardText}`}>
            <thead className={`text-[11px] ${styles.cardTextMuted}`}>
              <tr className={`border-b ${styles.cardBorder}`}>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_code')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_layer')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_carrier')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_scope')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_version')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_git_ref')}</th>
                <th className="text-left px-3 py-2 font-semibold">{t('knowledge.rules.col_status')}</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id} className={`border-b last:border-b-0 ${styles.cardBorder}`}>
                  <td className="px-3 py-2 font-mono">{r.code || r.id}</td>
                  <td className="px-3 py-2">{r.ruleLayer || t('knowledge.rules.trace_not_available')}</td>
                  <td className="px-3 py-2">{r.ruleCarrier || t('knowledge.rules.trace_not_available')}</td>
                  <td className="px-3 py-2">{r.scope || t('knowledge.rules.trace_not_available')}</td>
                  <td className="px-3 py-2">{r.versionNo || t('knowledge.rules.trace_not_available')}</td>
                  <td className="px-3 py-2 font-mono">{r.gitRef ? r.gitRef.slice(0, 12) : t('knowledge.rules.trace_not_available')}</td>
                  <td className="px-3 py-2">{r.status || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// 数据同步 Tab（Wave 0）
// 依据本体工作台的本体模型与实体-数据映射契约，从数据工作台抽取 → 形成知识图谱与向量库。
// 双通道（结构化→图谱 / 非结构化→向量库）各一个开关；「立即同步」Stub（Wave1 触发端点）。
import { useCallback, useEffect, useState } from 'react';
import { Database, RefreshCw, ToggleLeft, ToggleRight } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { knowledgeApi, type EntityMappingItem } from '../services/knowledgeApi';

export default function DatasyncTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [mappings, setMappings] = useState<EntityMappingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [structuredOn, setStructuredOn] = useState(true);
  const [unstructuredOn, setUnstructuredOn] = useState(true);

  const loadMappings = useCallback(async () => {
    setLoading(true);
    try {
      // 本体工作台映射契约（金 I 产出，知识工作台只读消费；架构铁律 §0.5 契约先行）
      setMappings(await knowledgeApi.fetchEntityMappings());
    } catch {
      setMappings([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadMappings(); }, [loadMappings]);

  return (
    <div className="h-full overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* 标题 + hero */}
        <div className="space-y-1">
          <h2 className="font-bold text-xl tracking-tight flex items-center gap-2" style={{ color: styles.cardText }}>
            <Database className="w-5 h-5" style={{ color: styles.accentText }} />
            {t('knowledge.datasync.title')}
          </h2>
          <p className="text-xs" style={{ color: styles.cardTextMuted }}>{t('knowledge.datasync.subtitle')}</p>
          <p className="text-xs leading-normal mt-2 pl-3 border-l-2" style={{ color: styles.cardText, borderColor: styles.accentBg }}>
            {t('knowledge.datasync.hero')}
          </p>
        </div>

        {/* 映射来源（本体工作台契约，只读） */}
        <div className={`border rounded-xl p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-sm" style={{ color: styles.cardText }}>
              {t('knowledge.datasync.mappings_title')}
            </h3>
            <button
              onClick={() => void loadMappings()}
              className="p-1 rounded border hover:opacity-70 transition"
              style={{ borderColor: styles.cardBorder }}
              title={t('knowledge.common.refresh')}
            >
              <RefreshCw className="w-3.5 h-3.5" style={{ color: styles.cardTextMuted }} />
            </button>
          </div>
          <p className="font-mono text-[10px] tracking-wider uppercase" style={{ color: styles.cardTextMuted }}>
            {t('knowledge.datasync.mappings_hint')}
          </p>
          {mappings.length === 0 ? (
            <div className="py-8 text-center text-xs" style={{ color: styles.cardTextMuted }}>
              {loading ? t('knowledge.glossarytab.加载中') : t('knowledge.datasync.mappings_empty')}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left font-mono text-[10px] tracking-wider uppercase" style={{ color: styles.cardTextMuted }}>
                    <th className="py-1.5 pr-3">Entity</th>
                    <th className="py-1.5 pr-3">Data Source</th>
                    <th className="py-1.5 pr-3">{t('knowledge.datasync.column.mappings')}</th>
                    <th className="py-1.5">Materialized</th>
                  </tr>
                </thead>
                <tbody>
                  {mappings.slice(0, 50).map((m, i) => (
                    <tr key={`${m.entityCode ?? i}`} className="border-t" style={{ borderColor: styles.cardBorder }}>
                      <td className="py-1.5 pr-3 font-mono" style={{ color: styles.cardText }}>{m.entityCode ?? '—'}</td>
                      <td className="py-1.5 pr-3 font-mono" style={{ color: styles.cardText }}>{m.resourceName ?? m.datasetId ?? '—'}</td>
                      <td className="py-1.5 pr-3" style={{ color: styles.cardText }}>{(m.fieldMappings ?? []).length}</td>
                      <td className="py-1.5">
                        <span className={m.materialized ? styles.successText : styles.cardTextMuted}>
                          {m.materialized ? '●' : '○'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* 双通道开关 + 立即同步 */}
        <div className={`border rounded-xl p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <ChannelToggle
              label={t('knowledge.datasync.channel_structured')}
              on={structuredOn}
              onToggle={() => setStructuredOn(v => !v)}
            />
            <ChannelToggle
              label={t('knowledge.datasync.channel_unstructured')}
              on={unstructuredOn}
              onToggle={() => setUnstructuredOn(v => !v)}
            />
          </div>
          <div className="flex items-center justify-between flex-wrap gap-2">
            {/* TODO Wave1: 接通 POST /api/v1/knowledge/datasync/trigger 后立即同步端点 */}
            <button
              disabled
              title={t('knowledge.datasync.sync_stub_hint')}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold opacity-50 cursor-not-allowed transition"
              style={{ background: styles.accentBg, color: '#fff' }}
            >
              <Database className="w-3.5 h-3.5" />
              {t('knowledge.datasync.sync_now')}
            </button>
            <p className="text-[10px] font-mono tracking-wider uppercase" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.datasync.schedule_hint')}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

/** 局部通道开关（避免重复逻辑） */
function ChannelToggle({ label, on, onToggle }: { label: string; on: boolean; onToggle: () => void }) {
  const { styles } = useTheme();
  return (
    <button
      onClick={onToggle}
      className="flex items-center justify-between p-3 rounded-lg border transition hover:opacity-80 text-left"
      style={{ borderColor: styles.cardBorder, background: styles.inputBg }}
    >
      <span className="text-xs font-semibold" style={{ color: styles.cardText }}>{label}</span>
      <span style={{ color: on ? styles.accentText : styles.cardTextMuted }}>
        {on ? <ToggleRight className="w-4 h-4" /> : <ToggleLeft className="w-4 h-4" />}
      </span>
    </button>
  );
}

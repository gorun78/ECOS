/**
 * ExplorerAnalyticsView — 分析视图（分类分布条形图 + 分组计数表，点击下钻添加过滤）
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { AreaChart } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { AnalyticsData, FilterQuery } from './types';

interface Props {
  analyticsData: AnalyticsData;
  processedCount: number;
  activeFilters: FilterQuery[];
  setActiveFilters: React.Dispatch<React.SetStateAction<FilterQuery[]>>;
  setActiveTab: (tab: 'table' | 'analytics') => void;
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function ExplorerAnalyticsView({
  analyticsData,
  processedCount,
  activeFilters,
  setActiveFilters,
  setActiveTab,
  showToast
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // AnalyticsData 是「对象形态」与「数组形态」的联合类型；仅对象形态可下钻，数组形态与旧写法行为一致（不渲染图表数据）
  const analytics = analyticsData && !Array.isArray(analyticsData) ? analyticsData : null;

  return (
    <div className={`flex-1 ${styles.cardBg} p-6 space-y-6 overflow-y-auto`}>
      <div className="space-y-1">
        <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <AreaChart size={14} className={styles.accentText} />
          {t('ow.explore.analyticsTitle')}
        </h3>
        <p className={`text-[10px] ${styles.cardTextMuted}`}>
          {t('ow.explore.analyticsDesc')}<strong>「{analytics ? analytics.property?.displayName : ''}」</strong>
        </p>
      </div>

      {processedCount === 0 ? (
        <div className={`text-center py-20 ${styles.muted}`}>{t('ow.empty.noDataForChart')}</div>
      ) : (
        <div className="grid grid-cols-2 gap-8 items-start">
          {/* Custom visual distribution bars */}
          <div className={`border ${styles.cardBorder} rounded-xl p-5 space-y-3 shadow-3xs ${styles.appBg}`}>
            <h4 className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('ow.explore.barChartTitle')}</h4>
            <div className="space-y-3 pt-2">
              {analytics?.data?.map((item) => (
                <div
                  key={item.name}
                  onClick={() => {
                    // Add filter on click
                    const propId = analytics.property?.id;
                    setActiveFilters([...activeFilters, {
                      propertyId: propId,
                      operator: 'equals',
                      value: item.name
                    }]);
                    setActiveTab('table');
                    showToast('info', t('ow.msg.chartDrillDown').replace('{prop}', propId).replace('{value}', item.name));
                  }}
                  className="group cursor-pointer space-y-1"
                >
                  <div className="flex justify-between text-[11px]">
                    <span className={`font-medium ${styles.cardTextMuted} group-hover:text-blue-600 font-mono transition-colors`}>{item.name}</span>
                    <span className={`${styles.cardTextMuted} font-mono`}><strong>{item.count}</strong> {t('ow.label.countUnit')} ({item.percentage}%)</span>
                  </div>
                  <div className={`h-4 w-full ${styles.appBg} rounded overflow-hidden flex`}>
                    <div
                      className={`${styles.accentBg} group-hover:bg-blue-500 transition-all rounded-r duration-500`}
                      style={{ width: `${item.percentage}%` } as React.CSSProperties}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Summary table list */}
          <div className={`border ${styles.cardBorder} rounded-xl p-5 space-y-3 ${styles.cardBg}`}>
            <h4 className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('ow.explore.groupCountTable')}</h4>
            <table className="w-full text-left border-collapse text-[11px]">
              <thead>
                <tr className={`border-b ${styles.cardBorder} ${styles.muted}`}>
                  <th className="pb-2">{t('ow.explore.groupCategory')}</th>
                  <th className="pb-2 text-right">{t('ow.explore.instanceCount')}</th>
                  <th className="pb-2 text-right">{t('ow.explore.percentage')}</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.divider} ${styles.cardTextMuted}`}>
                {analytics?.data?.map((item) => (
                  <tr key={item.name} className={styles.sidebarHoverBg}>
                    <td className={`py-2 font-mono ${styles.cardTextMuted} font-medium`}>{item.name}</td>
                    <td className={`py-2 text-right font-mono font-semibold ${styles.cardText}`}>{item.count}</td>
                    <td className={`py-2 text-right font-mono ${styles.cardTextMuted}`}>{item.percentage}%</td>
                  </tr>
                ))}
                <tr className={`border-t ${styles.cardBorder} ${styles.cardText} font-bold`}>
                  <td className="py-2">{t('ow.explore.total')}</td>
                  <td className="py-2 text-right font-mono">{processedCount}</td>
                  <td className="py-2 text-right font-mono">100.0%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

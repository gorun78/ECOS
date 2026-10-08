import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { AlertTriangle, RefreshCw, Check, X, Info, FileText } from 'lucide-react';
import type { GuardrailPolicy } from './types';
import { POLICY_TYPE_META } from './constants';
import { Badge } from './UiPrimitives';

export default function GuardrailsAuditTab({
  policies, onRefresh,
}: {
  policies: GuardrailPolicy[];
  onRefresh: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-sm flex flex-col overflow-hidden flex-1`}>
        <div className={`p-3 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
          <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
            <FileText size={13} className={styles.cardTextMuted} />
            <span>{t('gw.audit.title')}</span>
          </span>
          <button
            type="button"
            onClick={onRefresh}
            className={`px-2.5 py-1 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-md text-[10px] font-bold ${styles.cardText} flex items-center gap-1 cursor-pointer`}
          >
            <RefreshCw size={10} /> {t('gw.audit.refresh')}
          </button>
        </div>

        {/* Stub notice */}
        <div className={`m-4 p-4 ${styles.warningBg} border ${styles.warningBorder} rounded-xl flex items-start gap-3`}>
          <AlertTriangle size={16} className={`${styles.warningText} mt-0.5 shrink-0`} />
          <div className={`space-y-1 ${styles.cardText}`}>
            <p className={`font-extrabold ${styles.warningText} text-[11px]`}>{t('gw.audit.notReady')}</p>
            <p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`}>
              {t('gw.audit.notReadyBody')}
            </p>
          </div>
        </div>

        {/* Transition: show policy status snapshot */}
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          {policies.length === 0 ? (
            <div className={`h-full flex flex-col items-center justify-center ${styles.cardTextMuted} space-y-2`}>
              <Info size={24} className={styles.cardTextMuted} />
              <p className="font-bold">{t('gw.audit.empty')}</p>
            </div>
          ) : (
            <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden`}>
              <table className="w-full text-left text-[10px]">
                <thead className={`${styles.appBg} ${styles.cardTextMuted} font-bold uppercase border-b ${styles.cardBorder}`}>
                  <tr>
                    <th className="p-2">{t('gw.audit.colName')}</th>
                    <th className="p-2">{t('gw.audit.colType')}</th>
                    <th className="p-2">{t('gw.audit.colSeverity')}</th>
                    <th className="p-2">{t('gw.audit.colStatus')}</th>
                    <th className="p-2">{t('gw.audit.colEnabled')}</th>
                    <th className="p-2">{t('gw.audit.colCompiledAt')}</th>
                    <th className="p-2 text-right">{t('gw.audit.colNote')}</th>
                  </tr>
                </thead>
                <tbody className={`divide-y ${styles.divider} ${styles.cardText}`}>
                  {policies.map(p => (
                    <tr key={p.id} className={`${styles.sidebarHoverBg}`}>
                      <td className={`p-2 font-bold ${styles.cardText}`}>{p.name}</td>
                      <td className="p-2"><Badge tone="slate">{t(POLICY_TYPE_META[p.type]?.labelKey ?? 'gw.type.custom')}</Badge></td>
                      <td className="p-2"><Badge tone={p.severity === 'block' ? 'rose' : 'amber'}>{p.severity}</Badge></td>
                      <td className="p-2"><Badge tone={p.status === 'COMPILED' ? 'emerald' : 'amber'}>{p.status}</Badge></td>
                      <td className="p-2">{p.isEnabled ? <Check size={12} className="text-emerald-500" /> : <X size={12} className={styles.cardTextMuted} />}</td>
                      <td className={`p-2 font-mono ${styles.cardTextMuted}`}>{p.compiledAt || '—'}</td>
                      <td className={`p-2 text-right ${styles.cardTextMuted} italic`}>{t('gw.audit.pendingBind')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import LucideIcon from '../LucideIcon';
import type { DataHealthCheck } from '../types';
import { useTheme } from "../../../components/ThemeContext";


interface HealthTabProps {
  healthChecks: DataHealthCheck[];
  setHealthChecks: React.Dispatch<React.SetStateAction<DataHealthCheck[]>>;
  showToast: (type: string, message: string) => void;
  showAddCheck: boolean;
  setShowAddCheck: (v: boolean) => void;
  newCheckName: string;
  setNewCheckName: (v: string) => void;
  newCheckDs: string;
  setNewCheckDs: (v: string) => void;
  checkType: string;
  setCheckType: (v: string) => void;
  newCheck: Partial<DataHealthCheck>;
  t: (key: string) => string;
}

const HealthTab: React.FC<HealthTabProps> = ({
  healthChecks, setHealthChecks, showToast, showAddCheck,
  setShowAddCheck, newCheckName, setNewCheckName, newCheckDs,
  setNewCheckDs, checkType, setCheckType, newCheck, t
}) => {
  const { styles } = useTheme();
  const handleAddCheck = () => {
    if (!newCheckName.trim()) {
      showToast('error', '请输入规则名称');
      return;
    }
    const newId = `check-${Date.now()}`;
    const rule: DataHealthCheck = {
      id: newId,
      name: newCheckName,
      status: 'pending',
      checkType: (checkType || 'null_check') as DataHealthCheck['checkType'],
      targetTable: newCheckDs || '',
      datasetId: newCheckDs || '',
      threshold: 'N/A',
      lastChecked: new Date().toISOString(),
      message: '',
      config: {}
    };
    setHealthChecks(prev => [...prev, rule]);
    setNewCheckName('');
    setNewCheckDs('');
    setCheckType('null_check');
    setShowAddCheck(false);
    showToast('success', `规则 [${newCheckName}] 已添加`);
  };

  return (
    <div className={`flex-1 overflow-y-auto p-6 ${styles.cardBg}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className={`text-sm font-bold ${styles.cardText}`}>{t("dw.txt.693c4f")}</h3>
          <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>
            共 {healthChecks.length} 条质量检查规则
          </p>
        </div>
        <button
          onClick={() => setShowAddCheck(!showAddCheck)}
          className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-lg text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/></svg>
          <span>{t("dw.txt.57eece")}</span>
        </button>
      </div>

      {/* Add form */}
      {showAddCheck && (
        <div className={`mb-6 p-4 border ${styles.cardBorder} rounded-xl ${styles.appBg} space-y-3`}>
          <input value={newCheckName} onChange={e => setNewCheckName(e.target.value)} placeholder="规则名称" className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs" />
          <input value={newCheckDs} onChange={e => setNewCheckDs(e.target.value)} placeholder="数据集/表名" className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs" />
          <select value={checkType} onChange={e => setCheckType(e.target.value)} className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs">
            <option value="null_check">{t("dw.txt.b0fd45")}</option>
            <option value="range_check">{t("dw.txt.d086dc")}</option>
            <option value="uniqueness">{t("dw.txt.cc9056")}</option>
            <option value="freshness">{t("dw.txt.e442a8")}</option>
            <option value="row_count">{t("dw.txt.2f1eaf")}</option>
            <option value="schema_check">{t("dw.txt.5ea73e")}</option>
            <option value="custom_sql">{t("dw.txt.7b06b8")}</option>
          </select>
          <div className="flex gap-2">
            <button onClick={handleAddCheck} className="px-4 py-1.5 bg-emerald-600 text-white rounded-lg text-xs font-bold cursor-pointer">添加</button>
            <button onClick={() => setShowAddCheck(false)} className={`px-4 py-1.5 bg-slate-200 ${styles.cardTextMuted} rounded-lg text-xs cursor-pointer`}>取消</button>
          </div>
        </div>
      )}

      {/* Rules list */}
      <div className="space-y-4">
        {healthChecks.map((check) => (
          <div key={check.id} className={`border ${styles.cardBorder} rounded-xl p-4 ${styles.cardBg} hover:shadow-sm transition-shadow`}>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <LucideIcon name={check.checkType === 'null_check' ? 'shield-off' : check.checkType === 'uniqueness' ? 'fingerprint' : check.checkType === 'freshness' ? 'clock' : 'activity'} className="w-4 h-4 text-emerald-600" />
                <div>
                  <span className={`text-sm font-bold ${styles.cardText}`}>{check.name}</span>
                  <span className={`ml-2 text-[10px] ${styles.cardTextMuted}`}>{t("dw.txt.185a12")}: {check.targetTable}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                  check.status === 'ok' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                  check.status === 'pending' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                  'bg-rose-50 text-rose-700 border border-rose-200'
                }`}>
                  {check.status === 'ok' ? t("dw.txt.d8e14b") : check.status === 'pending' ? t("dw.txt.f1f3b7") : t("dw.txt.1c3a1a")}
                </span>
                <button
                  onClick={() => setHealthChecks(prev => prev.filter(c => c.id !== check.id))}
                  className="p-1 rounded hover:bg-rose-50 text-rose-400 cursor-pointer"
                >
                  <LucideIcon name="trash-2" className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Type-specific controls */}
            <div className="space-y-3 pl-7">
              {check.checkType === 'range_check' && (
                <div className="space-y-2">
                  <label className={`text-[11px] font-semibold ${styles.cardTextMuted} block`}>{t("dw.txt.e21195")}</label>
                  <div className="flex gap-4 items-center">
                    <input
                      type="range" min="100" max="50000" step="100"
                      value={check.config.minRows || 1000}
                      onChange={e => {
                        setHealthChecks(prev => prev.map(c => c.id === check.id ? {
                          ...c, config: { ...c.config, minRows: parseInt(e.target.value) }
                        } : c));
                      }}
                      className="flex-1"
                    />
                    <span className={`font-mono ${styles.cardText} font-semibold text-xs border ${styles.cardBg} px-2 py-0.5 rounded`}>
                      {check.config.minRows || 1000} {t("dw.txt.3960a8")}
                    </span>
                  </div>
                </div>
              )}

              {check.checkType === 'null_check' && (
                <div className="space-y-2">
                  <label className={`text-[11px] font-semibold ${styles.cardTextMuted} block`}>{t("dw.txt.d086dc")}</label>
                  <div className="flex gap-4 items-center">
                    <input
                      type="range" min="0" max="10" step="0.1"
                      value={check.config.maxNullPercentage || 2.0}
                      onChange={e => {
                        setHealthChecks(prev => prev.map(c => c.id === check.id ? {
                          ...c, config: { ...c.config, maxNullPercentage: parseFloat(e.target.value) }
                        } : c));
                      }}
                      className="flex-1"
                    />
                    <span className={`font-mono ${styles.cardText} font-semibold text-xs border ${styles.cardBg} px-2 py-0.5 rounded`}>
                      {check.config.maxNullPercentage || 2.0}%
                    </span>
                  </div>
                </div>
              )}

              {check.checkType === 'freshness' && (
                <div className="space-y-2">
                  <label className={`text-[11px] font-semibold ${styles.cardTextMuted} block`}>{t("dw.txt.e442a8")}</label>
                  <div className="flex gap-4 items-center">
                    <input
                      type="range" min="15" max="720" step="15"
                      value={check.config.maxDelayMinutes || 120}
                      onChange={e => {
                        setHealthChecks(prev => prev.map(c => c.id === check.id ? {
                          ...c, config: { ...c.config, maxDelayMinutes: parseInt(e.target.value) }
                        } : c));
                      }}
                      className="flex-1"
                    />
                    <span className={`font-mono ${styles.cardText} font-semibold text-xs border ${styles.cardBg} px-2 py-0.5 rounded`}>
                      {check.config.maxDelayMinutes || 120} {t("dw.txt.951352")}
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {healthChecks.length === 0 && (
        <div className={`text-center py-16 ${styles.cardTextMuted}`}>
          <LucideIcon name="shield-check" className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm font-semibold">{t("dw.txt.d8c7cb")}</p>
          <p className="text-xs mt-1">{t("dw.txt.6f933b")}</p>
        </div>
      )}
    </div>
  );
};

export default HealthTab;

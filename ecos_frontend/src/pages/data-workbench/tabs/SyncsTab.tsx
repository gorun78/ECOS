/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import LucideIcon from '../LucideIcon';
import type { DataSyncTask, DataConnection } from '../types';
import { useTheme } from "../../../components/ThemeContext";


interface SyncsTabProps {
  syncTasks: DataSyncTask[];
  setSyncTasks: (v: DataSyncTask[]) => void;
  showToast: (type: string, message: string) => void;
  showAddSync: boolean;
  setShowAddSync: (v: boolean) => void;
  newSyncName: string;
  setNewSyncName: (v: string) => void;
  newSyncConn: string;
  setNewSyncConn: (v: string) => void;
  newSyncTable: string;
  setNewSyncTable: (v: string) => void;
  newSyncMode: string;
  setNewSyncMode: (v: string) => void;
  newSyncSched: string;
  setNewSyncSched: (v: string) => void;
  handleCreateSyncTask: () => void;
  selectedTaskId: string | null;
  setSelectedTaskId: (v: string | null) => void;
  connections: DataConnection[];
  triggerSyncTask: (taskId: string) => void;
  t: (key: string) => string;
}

const SyncsTab: React.FC<SyncsTabProps> = ({
  syncTasks, setSyncTasks, showToast, showAddSync, setShowAddSync,
  newSyncName, setNewSyncName, newSyncConn, setNewSyncConn,
  newSyncTable, setNewSyncTable, newSyncMode, setNewSyncMode,
  newSyncSched, setNewSyncSched, handleCreateSyncTask,
  selectedTaskId, setSelectedTaskId, connections, triggerSyncTask, t
}) => {
  const { styles } = useTheme();
  return (
<div className="flex-1 flex overflow-hidden">
  {/* Sync tasks list panel */}
  <div className={`w-80 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col overflow-hidden shrink-0`}>
    <div className={`p-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/40`}>
      <h3 className={`text-xs font-bold ${styles.cardText}`}>{t("dw.txt.5da13f")}</h3>
      <button
        onClick={() => setShowAddSync(true)}
        className={`p-1 rounded ${styles.accentBg} text-white ${styles.accentHover} text-xs flex items-center gap-1 cursor-pointer font-medium`}
      >
        <LucideIcon name="Plus" size={12} />
        <span>{t("dw.txt.d85c7b")}</span>
      </button>
    </div>

    <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
      {syncTasks.map(task => {
        const isSelected = selectedTaskId === task.id;
        const conn = connections.find(c => c.id === task.sourceConnectionId);
        return (
          <button
            key={task.id}
            onClick={() => setSelectedTaskId(task.id)}
            className={`w-full text-left p-3.5 rounded-lg border transition-all text-xs flex flex-col gap-2 ${
              isSelected
                ? `${styles.badgeBg} ${styles.accentBorder} shadow-2xs`
                : `${styles.cardBorder} hover:${styles.appBg}`
            }`}
          >
            <div className="flex justify-between items-start">
              <span className={`font-semibold ${styles.cardText} truncate pr-1`}>{task.name}</span>
              <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-semibold uppercase ${
                task.status === 'success' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                task.status === 'failed' ? 'bg-red-50 text-red-700 border border-red-200' :
                task.status === 'running' ? 'bg-blue-50 text-blue-700 border border-blue-200 animate-pulse' :
                '${styles.sidebarBg} ${styles.cardTextMuted} border ${styles.cardBorder}'
              }`}>
                {task.status === 'success' ? t("dw.statusSuccess") :
                 task.status === 'failed' ? t("dw.statusFailed") :
                 task.status === 'running' ? t("dw.statusRunning") : t("dw.statusPaused")}
              </span>
            </div>
            <div className={`text-[10px] ${styles.cardTextMuted} font-mono flex flex-col gap-0.5`}>
              <span className="truncate">{t("dw.txt.911247")}: {conn?.name || task.sourceConnectionId}</span>
              <span>{t("dw.txt.4c96fe")}: {task.sourceTable}</span>
            </div>
          </button>
        );
      })}
    </div>
  </div>

  {/* Sync Task Detail View */}
  {(() => {
    const task = syncTasks.find(st => st.id === selectedTaskId);
    if (!task) return <div className={`flex-1 p-6 ${styles.cardTextMuted}`}>{t("dw.txt.11add3")}</div>;
    const conn = connections.find(c => c.id === task.sourceConnectionId);
    return (
      <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
        {/* Detail banner */}
        <div className={`p-6 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/50`}>
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-full border ${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
              <LucideIcon name="Import" size={20} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className={`text-sm font-bold ${styles.cardText}`}>{task.name}</span>
                <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} font-mono px-2 py-0.5 rounded-full uppercase`}>
                  {t("dw.txt.fadc11")}: {task.syncMode === 'snapshot' ? t("dw.txt.3d48a9") :
                         task.syncMode === 'incremental' ? t("dw.txt.cc5377") : t("dw.txt.23fdc3")}
                </span>
              </div>
              <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
                {t("dw.txt.ae8f43")}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => triggerSyncTask(task.id)}
              disabled={task.status === 'running'}
              className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} text-white text-xs font-semibold rounded transition-all cursor-pointer flex items-center gap-1.5 shadow-2xs`}
            >
              <LucideIcon name="Play" size={13} />
              <span>{t("dw.txt.a7e407")}</span>
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Configuration & Stats Grid */}
          <div className="grid grid-cols-3 gap-6">
            <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 text-xs space-y-3`}>
              <h4 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-1.5`}>{t("dw.txt.eed3c7")}</h4>
              <div className="space-y-2.5 font-sans">
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.911247")}</span>
                  <span className={`${styles.cardText} font-semibold`}>{conn?.name}</span>
                </div>
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.4c96fe")}</span>
                  <span className={`font-mono ${styles.cardText} font-semibold`}>{task.sourceTable}</span>
                </div>
                <hr className={`${styles.cardBorder}`} />
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.c6f0b6")}</span>
                  <span className={`font-mono ${styles.cardText} font-semibold ${styles.accentText} truncate block`}>
                    {task.targetDatasetId}
                  </span>
                </div>
              </div>
            </div>

            <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 text-xs space-y-3`}>
              <h4 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-1.5`}>{t("dw.txt.bbedcc")}</h4>
              <div className="space-y-2.5 font-sans">
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.952c30")}</span>
                  <span className={`${styles.cardText} font-semibold`}>
                    {task.schedule === 'manual' ? t("dw.txt.22365a") :
                     task.schedule === 'hourly' ? t("dw.txt.fab71c") :
                     task.schedule === 'daily' ? t("dw.txt.29c226") :
                     `${t("dw.txt.a33c7f")}: ${task.cronExpression}`}
                  </span>
                </div>
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.8244ee")}</span>
                  <span className={`${styles.cardText} font-medium`}>
                    {task.syncMode === 'snapshot' ? t("dw.txt.68ffda") :
                     task.syncMode === 'incremental' ? t("dw.txt.4efaa1") : t("dw.txt.21e0db")}
                  </span>
                </div>
              </div>
            </div>

            <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 text-xs space-y-3`}>
              <h4 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-1.5`}>{t("dw.txt.6651d2")}</h4>
              <div className="space-y-2.5 font-sans">
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.ca6183")}</span>
                  <span className={`font-mono ${styles.cardText} font-semibold`}>{task.lastRunTime || t("dw.txt.0dc027")}</span>
                </div>
                {task.status === 'success' && (
                  <>
                    <div>
                      <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.ba49ba")}</span>
                       <span className={`font-mono font-bold ${styles.accentText}`}>{task.recordsSynced?.toLocaleString()} {t("dw.txt.b2db8f")}</span>
                    </div>
                    <div>
                      <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.6974b2")}</span>
                      <span className={`font-mono ${styles.cardText}`}>{((task.durationMs || 0) / 1000).toFixed(1)} {t("dw.txt.9523bc")}</span>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Error Log Container if failed */}
          {task.status === 'failed' && task.errorMessage && (
            <div className={`border ${styles.cardBorder} ${styles.appBg} ${styles.cardText} rounded-xl p-4 space-y-3 select-text leading-relaxed font-mono text-xs`}>
              <h4 className={`font-bold font-sans flex items-center gap-1.5 ${styles.cardText}`}>
                <LucideIcon name="AlertOctagon" size={14} />
                {t("dw.txt.8bc36c")}
              </h4>
              <p className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-lg ${styles.cardText} leading-relaxed whitespace-pre-line text-[11px]`}>
                {task.errorMessage}
              </p>
            </div>
          )}
        </div>
      </div>
    );
  })()}
</div>
  );
};

export default SyncsTab;

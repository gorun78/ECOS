/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * GraphSyncTab — 知识图谱同步管理
 * 管理知识图谱Object类型同步状态、触发同步、查看同步日志
 */

import React, { useState, useEffect } from 'react';
import {
  Database, RefreshCw, Play, Check, Clock, XCircle,
  Loader2, RotateCw, ToggleLeft, ToggleRight
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { SyncStatus, SyncLog } from '../typesAndConstants';

export default function GraphSyncTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [syncStatuses, setSyncStatuses] = useState<SyncStatus[]>([]);
  const [syncLogs, setSyncLogs] = useState<SyncLog[]>([]);
  const [isLoadingStatus, setIsLoadingStatus] = useState(false);
  const [isSyncingAll, setIsSyncingAll] = useState(false);
  const [syncingType, setSyncingType] = useState<string | null>(null);
  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);

  const showToast = (type: string, msg: string) => { setToast({ type, msg }); setTimeout(() => setToast(null), 3000); };

  const fetchSyncStatus = async () => {
    setIsLoadingStatus(true);
    try {
      const statuses = await knowledgeApi.fetchSyncStatuses();
      setSyncStatuses(statuses);
    } catch (e: any) {
      showToast('error', '获取同步状态异常: ' + e.message);
    } finally {
      setIsLoadingStatus(false);
    }
  };

  const fetchSyncLogsData = async () => {
    try {
      const logs = await knowledgeApi.fetchSyncLogs();
      setSyncLogs(logs.slice(0, 10) as SyncLog[]);
    } catch (e) {
      console.error('Failed to fetch sync logs', e);
    }
  };

  useEffect(() => { fetchSyncStatus(); fetchSyncLogsData(); }, []);

  const handleFullSync = async () => {
    setIsSyncingAll(true);
    try {
      await knowledgeApi.triggerFullSync();
      showToast('success', '全量同步任务已触发');
      await fetchSyncStatus();
      await fetchSyncLogsData();
    } catch (e: any) {
      showToast('error', '同步异常: ' + e.message);
    } finally {
      setIsSyncingAll(false);
    }
  };

  const handleSyncObjectType = async (objectType: string) => {
    setSyncingType(objectType);
    try {
      await knowledgeApi.triggerObjectSync(objectType);
      showToast('success', `${objectType} 同步任务已触发`);
      await fetchSyncStatus();
      await fetchSyncLogsData();
    } catch (e: any) {
      showToast('error', '同步异常: ' + e.message);
    } finally {
      setSyncingType(null);
    }
  };

  // Toggle sync enabled for object type
  const handleToggleEnabled = (objectType: string, enabled: boolean) => {
    setSyncStatuses(prev =>
      prev.map(s => (s.objectType === objectType ? { ...s, enabled: !enabled } : s))
    );
    // API call to persist toggle state could go here
    // POST /api/knowledge/sync/object/{type}/toggle
  };

  // Calculate overall progress
  const totalAll = syncStatuses.reduce((sum, s) => sum + s.total, 0);
  const syncedAll = syncStatuses.reduce((sum, s) => sum + s.synced, 0);
  const progressPct = totalAll > 0 ? Math.round((syncedAll / totalAll) * 100) : 0;

  return (
    <div className="flex flex-col h-full space-y-4 text-slate-100">
      {/* Sync Status Overview Card */}
      <div className="bg-slate-900 border border-slate-700 rounded-xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold flex items-center gap-2">
            <Database size={14} className="text-blue-400" />
            图谱同步状态总览
          </h3>
          <div className="flex items-center gap-2">
            <button
              onClick={fetchSyncStatus}
              disabled={isLoadingStatus}
              className="px-3 py-1.5 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg border border-slate-600 flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
            >
              {isLoadingStatus
                ? <Loader2 size={12} className="animate-spin" />
                : <RefreshCw size={12} />
              }
              刷新状态
            </button>
            <button
              onClick={handleFullSync}
              disabled={isSyncingAll}
              className="px-3 py-1.5 text-[11px] font-bold bg-blue-600 hover:bg-blue-500 text-white rounded-lg flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
            >
              {isSyncingAll
                ? <Loader2 size={12} className="animate-spin" />
                : <Play size={12} />
              }
              全量同步
            </button>
          </div>
        </div>

        {/* Overall progress bar */}
        <div className="space-y-1.5">
          <div className={`flex justify-between text-[11px] ${styles.cardTextMuted}`}>
            <span>总同步进度</span>
            <span className="font-bold text-slate-300">{syncedAll} / {totalAll} ({progressPct}%)</span>
          </div>
          <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-blue-500 to-emerald-500 rounded-full transition-all duration-500"
              style={{ width: `${progressPct}%` }}
            />
          </div>
        </div>
      </div>

      {/* Object Type List Table */}
      <div className="bg-slate-900 border border-slate-700 rounded-xl overflow-hidden flex-1">
        <div className="px-4 py-3 border-b border-slate-700 flex items-center gap-2">
          <Database size={13} className={`${styles.cardTextMuted}`} />
          <span className="text-xs font-bold text-slate-300">Object类型同步列表</span>
        </div>

        <div className="overflow-auto">
          <table className="w-full text-[11px]">
            <thead>
              <tr className={`bg-slate-800 ${styles.cardTextMuted} uppercase tracking-wider`}>
                <th className="text-left px-4 py-2.5 font-bold">Object类型</th>
                <th className="text-center px-3 py-2.5 font-bold">已同步数</th>
                <th className="text-center px-3 py-2.5 font-bold">未同步数</th>
                <th className="text-left px-3 py-2.5 font-bold">最近同步时间</th>
                <th className="text-center px-3 py-2.5 font-bold">启用</th>
                <th className="text-center px-3 py-2.5 font-bold">操作</th>
              </tr>
            </thead>
            <tbody>
              {syncStatuses.length === 0 ? (
                <tr>
                  <td colSpan={6} className={`text-center py-8 ${styles.cardTextMuted}`}>
                    {isLoadingStatus
                      ? <span className="flex items-center justify-center gap-2"><Loader2 size={12} className="animate-spin" /> 加载中...</span>
                      : '暂无同步数据，点击"刷新状态"获取'}
                  </td>
                </tr>
              ) : (
                syncStatuses.map((item) => (
                  <tr
                    key={item.objectType}
                    className="border-t border-slate-800 hover:bg-slate-800/50 transition"
                  >
                    <td className="px-4 py-2.5 font-bold text-slate-200">
                      {item.objectType}
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <span className="text-emerald-400 font-mono">{item.synced}</span>
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <span className="text-amber-400 font-mono">{item.unsynced}</span>
                    </td>
                    <td className={`px-3 py-2.5 ${styles.cardTextMuted}`}>
                      {item.lastSyncTime || '—'}
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <button
                        onClick={() => handleToggleEnabled(item.objectType, item.enabled)}
                        className="cursor-pointer"
                        title={item.enabled ? '已启用' : '已禁用'}
                      >
                        {item.enabled
                          ? <ToggleRight size={16} className="text-emerald-500" />
                          : <ToggleLeft size={16} className={`${styles.cardTextMuted}`} />
                        }
                      </button>
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <button
                        onClick={() => handleSyncObjectType(item.objectType)}
                        disabled={syncingType === item.objectType}
                        className="px-2.5 py-1 text-[10px] font-bold bg-slate-800 hover:bg-slate-700 text-blue-400 rounded-md border border-slate-600 flex items-center gap-1 mx-auto transition cursor-pointer disabled:opacity-50"
                      >
                        {syncingType === item.objectType
                          ? <Loader2 size={10} className="animate-spin" />
                          : <RotateCw size={10} />
                        }
                        同步此类
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Sync History Logs */}
      <div className="bg-slate-900 border border-slate-700 rounded-xl overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-700 flex items-center gap-2">
          <Clock size={13} className={`${styles.cardTextMuted}`} />
          <span className="text-xs font-bold text-slate-300">同步历史日志 (最近10条)</span>
        </div>
        <div className="overflow-auto max-h-48">
          {syncLogs.length === 0 ? (
            <div className={`text-center py-6 ${styles.cardTextMuted} text-[11px]`}>
              暂无同步日志
            </div>
          ) : (
            syncLogs.map((log) => (
              <div
                key={log.id}
                className="flex items-center gap-3 px-4 py-2 border-t border-slate-800/50 text-[11px] hover:bg-slate-800/30 transition"
              >
                <span className={`${styles.cardTextMuted} font-mono w-36 shrink-0`}>
                  {log.timestamp}
                </span>
                <span className={`${styles.cardTextMuted} font-bold w-28 shrink-0`}>
                  {log.objectType}
                </span>
                <span className="text-slate-300 w-20 shrink-0">
                  {log.operation}
                </span>
                <span className={`flex items-center gap-1 font-bold ${
                  log.status === 'success' ? 'text-emerald-400' : 'text-red-400'
                }`}>
                  {log.status === 'success'
                    ? <Check size={11} />
                    : <XCircle size={11} />
                  }
                  {log.status === 'success' ? '成功' : '失败'}
                </span>
                {log.message && (
                  <span className={`${styles.cardTextMuted} truncate flex-1`}>
                    {log.message}
                  </span>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

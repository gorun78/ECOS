/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import type { DataConnection } from '../../types';
import type { FolderFileVo } from '../../api';
import { useTheme } from "../../../../components/ThemeContext";
import {
  fetchDataSourceResources, triggerCollectSync, saveMetadataStrategy,
  listFolderFiles, collectFolderFiles,
} from '../../api';
import { STRATEGY_OPTIONS, COUNT_METHOD_OPTIONS } from './constants';
import type { ActiveCollectTask, CollectStatusState } from './types';

interface MetadataStrategyPanelProps {
  conn: DataConnection;
  connections: DataConnection[];
  setConnections: (v: DataConnection[]) => void;
  showToast: (type: string, message: string) => void;
  t: (key: string) => string;
  folderFiles: FolderFileVo[];
  refreshFolderFiles: (dsId: string) => Promise<void>;
  collecting: boolean;
  setCollecting: (v: boolean) => void;
  collectTaskId: string | null;
  setCollectTaskId: (v: string | null) => void;
  setCollectStatus: (v: CollectStatusState | null) => void;
  activeTasks: ActiveCollectTask[];
}

/** 采集方案 Tab 的「元数据采集」面板（PMO-37 获取策略 + 立即采集 + 活跃任务状态） */
export default function MetadataStrategyPanel({
  conn, connections, setConnections, showToast, t, folderFiles, refreshFolderFiles,
  collecting, setCollecting, collectTaskId, setCollectTaskId, setCollectStatus, activeTasks,
}: MetadataStrategyPanelProps) {
  const { styles } = useTheme();

  return (
                <>
                  <div className="space-y-2">
                    <div>
                      <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>{t("dw.strategy.trigger")}</label>
                      <select
                        value={conn.strategy?.trigger || 'MANUAL'}
                        onChange={async e => {
                          const newTrigger = e.target.value;
                          const newCount = conn.strategy?.countMethod || 'OFF';
                          const newCron = conn.strategy?.scheduleCron;
                          setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, trigger: newTrigger as 'MANUAL' | 'ON_SAVE' | 'ON_SCHEDULE' } } : c));
                          await saveMetadataStrategy(conn.id, newTrigger, newCount, newCron);
                          // PMO-37 增强：ON_SAVE 自动模式 → 提交完整元数据采集任务
                          if (newTrigger === 'ON_SAVE' && collectTaskId === null) {
                            setCollecting(true);
                            try {
                              const r = await triggerCollectSync(conn.id);
                              if (r?.taskId) {
                                setCollectTaskId(r.taskId);
                                setCollectStatus(null);
                                showToast('info', t('dw.strategy.collectStarted').replace('{id}', r.taskId.slice(0, 8)));
                              } else {
                                showToast('warning', t('dw.strategy.autoCollectFallback') || '自动采集任务提交失败，已回退轻量拉取');
                                const fresh = await fetchDataSourceResources(conn.id);
                                const tables = Array.isArray(fresh) ? fresh : [];
                                setConnections(connections.map(c => c.id === conn.id ? { ...c, tablesAvailable: tables } : c));
                              }
                            } finally {
                              setCollecting(false);
                            }
                          }
                        }}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {STRATEGY_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    {/* 定时采集策略 — 选择 ON_SCHEDULE 时显示 cron 配置 */}
                    {conn.strategy?.trigger === 'ON_SCHEDULE' && (
                      <div className={`space-y-1.5 p-2 rounded-lg border border-dashed ${styles.cardBorder}`}>
                        <label className={`text-[10px] ${styles.cardTextMuted} block`}>{t("dw.strategy.cronLabel")}</label>
                        <select
                          value={conn.strategy?.scheduleCron || '0 0 * * *'}
                          onChange={async e => {
                            const newCron = e.target.value;
                            const newTrigger = conn.strategy?.trigger || 'ON_SCHEDULE';
                            const newCount = conn.strategy?.countMethod || 'OFF';
                            setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, scheduleCron: newCron } } : c));
                            await saveMetadataStrategy(conn.id, newTrigger, newCount, newCron);
                            showToast('success', t('dw.strategy.updateSuccess') || '策略已保存');
                          }}
                          className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText} font-mono`}
                        >
                          <option value="0 0 * * *">{t('dw.strategy.cron.daily')}</option>
                          <option value="0 */6 * * *">{t('dw.strategy.cron.sixHourly')}</option>
                          <option value="0 0 */2 * *">{t('dw.strategy.cron.twoDays')}</option>
                          <option value="0 0 * * 1">{t('dw.strategy.cron.weekly')}</option>
                        </select>
                        <p className={`text-[10px] ${styles.cardTextMuted} font-mono`}>cron: {t('dw.strategy.cron.format')}</p>
                      </div>
                    )}
                    <div>
                      <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>{t("dw.strategy.count")}</label>
                      <select
                        value={conn.strategy?.countMethod || 'OFF'}
                        onChange={async e => {
                          const newCount = e.target.value;
                          const newTrigger = conn.strategy?.trigger || 'MANUAL';
                          setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, countMethod: newCount as 'OFF' | 'ESTIMATE' | 'EXACT' } } : c));
                          await saveMetadataStrategy(conn.id, newTrigger, newCount);
                        }}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {COUNT_METHOD_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    <p className={`text-[10px] ${styles.cardTextMuted}`}>{t("dw.strategy.hint")}</p>
                  </div>

                  <div className="flex gap-2 flex-wrap items-center">
                    {/* 保存参数：仅保存策略配置并同步一次资源，不触发采集任务 */}
                    <button type="button"
                      disabled={collecting || collectTaskId !== null}
                      onClick={async () => {
                        try {
                          const ok = await saveMetadataStrategy(conn.id,
                            conn.strategy?.trigger || 'MANUAL',
                            conn.strategy?.countMethod || 'OFF',
                            conn.strategy?.scheduleCron);
                          // PMO-37 增强：ON_SAVE 自动模式的"保存参数" → 提交完整采集任务
                          if (ok && conn.strategy?.trigger === 'ON_SAVE' && collectTaskId === null) {
                            setCollecting(true);
                            try {
                              const r = await triggerCollectSync(conn.id);
                              if (r?.taskId) {
                                setCollectTaskId(r.taskId);
                                setCollectStatus(null);
                                showToast('success', t('dw.strategy.saveParamsSuccess') || '参数已保存');
                              } else {
                                const fresh = await fetchDataSourceResources(conn.id);
                                const tables = Array.isArray(fresh) ? fresh : [];
                                setConnections(connections.map(c => c.id === conn.id ? { ...c, tablesAvailable: tables } : c));
                                showToast('warning', t('dw.strategy.autoCollectFallback') || '参数已保存，自动采集任务提交失败已回退轻量拉取');
                              }
                            } finally {
                              setCollecting(false);
                            }
                          } else if (ok) {
                            showToast('success', t('dw.strategy.saveParamsSuccess') || '参数已保存');
                          } else {
                            showToast('error', t('dw.strategy.saveParamsFailed') || '参数保存失败');
                          }
                        } catch (e) {
                          console.warn('[data-workbench] save params failed:', e);
                          showToast('error', t('dw.strategy.saveParamsFailed') || '参数保存失败');
                        }
                      }}
                      className={`px-2 py-1 text-[11px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.cardText} transition-colors cursor-pointer disabled:opacity-40 flex items-center gap-1`}
                    >
                      <LucideIcon name="Save" size={11} />
                      {t('dw.strategy.saveParams') || '保存参数'}
                    </button>

                    {/* 立即采集（结构源主档口，走 triggerCollectSync 异步任务；fs 走 collectFolderFiles 全量采集） */}
                    <button type="button"
                      disabled={collecting || collectTaskId !== null}
                      onClick={async () => {
                        setCollecting(true);
                        try {
                          if (conn.type === 'fs') {
                            const files = folderFiles.length > 0 ? folderFiles : await listFolderFiles(conn.id);
                            if (files.length === 0) {
                              showToast('info', t('dw.folder.collectNoFiles'));
                              return;
                            }
                            const docId = `doc_${conn.id.replace(/[^a-zA-Z0-9_-]/g, '_')}_${Date.now()}`;
                            const res = await collectFolderFiles({
                              datasourceId: conn.id,
                              docId,
                              fileNames: files.map(f => f.name),
                            });
                            if (res.failed > 0) {
                              const firstErr = res.items?.find(i => i.status !== 'SUCCESS')?.error;
                              showToast('error', t('dw.folder.collectPartial')
                                .replace('{ok}', String(res.collected)).replace('{fail}', String(res.failed))
                                + (firstErr ? `（${firstErr}）` : ''));
                            } else {
                              showToast('success', t('dw.folder.collectSuccess')
                                .replace('{count}', String(res.collected)));
                            }
                            await refreshFolderFiles(conn.id);
                            return;
                          }
                          // 同步立即采集：走 triggerCollectSync（后端任务引擎异步执行 + 前端 2s 轮询进度）
                          const r = await triggerCollectSync(conn.id);
                          if (r?.taskId) {
                            setCollectTaskId(r.taskId);
                            setCollectStatus(null);
                            showToast('info', t('dw.strategy.collectStarted').replace('{id}', r.taskId.slice(0, 8)));
                          } else {
                            showToast('error', t('dw.strategy.collectFailed').replace('{err}', 'HTTP'));
                          }
                        } finally {
                          setCollecting(false);
                        }
                      }}
                      className={`px-2 py-1 text-[11px] font-semibold rounded transition-colors flex items-center gap-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} disabled:opacity-40`}
                    >
                      <LucideIcon name="RefreshCw" size={11} className={(collecting || collectTaskId !== null) ? 'animate-spin' : ''} />
                      {(collecting || collectTaskId !== null) ? t('dw.strategy.collecting') : t('dw.strategy.collectNow')}
                    </button>

                    {/* 上次采集时间展示 */}
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>
                      {t("dw.strategy.lastCollect")}:{' '}
                      {conn.metadataConfig?.lastCollectTime
                        ? new Date(String(conn.metadataConfig.lastCollectTime)).toLocaleString()
                        : t('dw.strategy.neverCollected')}
                    </span>
                  </div>

                  {/* 活跃采集任务状态指示器 — 对接异步任务中心 */}
                  {activeTasks.length > 0 && (
                    <div className={`space-y-1.5 p-2 rounded-lg ${styles.appBg} border ${styles.cardBorder}`}>
                      <div className={`flex items-center gap-2 text-[11px]`}>
                        <LucideIcon name="Loader2" size={13} className={`animate-spin ${styles.accentText}`} />
                        <span className={`font-semibold ${styles.cardText}`}>
                          {t('dw.strategy.taskRunning') || '任务执行中'}
                        </span>
                      </div>
                      {activeTasks.map((task, i) => (
                        <div key={task.taskId} className={`text-[10px] font-mono ${styles.cardTextMuted} flex items-center gap-2`}>
                          <span className={`${task.status === 'RUNNING' ? styles.successText : styles.warningText} font-bold`}>
                            {task.status}
                          </span>
                          <span>{task.taskId.slice(0, 8)}...{task.progress}%</span>
                          {task.startTime && (
                            <span className="opacity-70">{new Date(task.startTime).toLocaleTimeString()}</span>
                          )}
                        </div>
                      ))}
                      <button type="button"
                        onClick={() => window.open('#/task-center', '_blank')}
                        className={`text-[10px] ${styles.accentText} hover:underline cursor-pointer flex items-center gap-1`}
                      >
                        <LucideIcon name="ExternalLink" size={10} />
                        {t('dw.strategy.viewInTaskCenter') || '在任务中心查看'}
                      </button>
                    </div>
                  )}
                </>
  );
}

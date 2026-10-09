/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import { useTheme } from "../../../../components/ThemeContext";
import type { CollectDiffRecord } from './types';

interface CollectDiffRecordsProps {
  diffRecords: CollectDiffRecord[];
  loadingDiff: boolean;
  showDiffDetail: number | null;
  setShowDiffDetail: (v: number | null) => void;
  t: (key: string) => string;
}

/** 采集差异记录卡片列表 — 仅结构源有差异记录（fs 文件无需版本对比） */
export default function CollectDiffRecords({
  diffRecords, loadingDiff, showDiffDetail, setShowDiffDetail, t,
}: CollectDiffRecordsProps) {
  const { styles } = useTheme();

  return (
              <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden ${styles.appBg}`}>
                <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.sidebarBg}/60 flex items-center justify-between`}>
                  <span className={`text-[10px] font-semibold ${styles.cardText} flex items-center gap-1.5`}>
                    <LucideIcon name="GitCommit" size={12} className={styles.accentText} />
                    {t('dw.strategy.diffTitle') || '采集差异记录'}
                  </span>
                  {loadingDiff && <LucideIcon name="Loader2" size={11} className="animate-spin" />}
                </div>
                <div className="divide-y" style={{ borderColor: styles.cardBorder }}>
                  {diffRecords.map((rec, idx) => (
                    <div key={idx} className={`group`}>
                      {showDiffDetail === idx ? (
                        <button type="button"
                          onClick={() => setShowDiffDetail(null)}
                          className="w-full text-left px-3 py-2 text-[10px] font-mono whitespace-pre-wrap break-words cursor-pointer hover:bg-opacity-50 transition-colors"
                          style={{ background: styles.cardBg }}
                        >
                          {rec.diffMarkdown || '(空)'}
                        </button>
                      ) : (
                        <button type="button"
                          onClick={() => setShowDiffDetail(idx)}
                          className="w-full text-left px-3 py-2 transition-colors hover:bg-opacity-50 cursor-pointer"
                          style={{ background: styles.cardBg }}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                              {rec.collectedAt && new Date(rec.collectedAt).toLocaleString()}
                            </span>
                            <span className={`text-[10px] font-mono ${styles.cardText}`}>{rec.diffSummary || ''}</span>
                          </div>
                          {rec.gitCommit && (
                            <div className={`text-[9px] font-mono ${styles.cardTextMuted} mt-1 flex items-center gap-1`}>
                              <LucideIcon name="GitBranch" size={9} />
                              {rec.gitCommit}
                            </div>
                          )}
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
  );
}

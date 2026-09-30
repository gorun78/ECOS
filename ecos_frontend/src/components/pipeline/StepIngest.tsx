/**
 * StepIngest — 步骤 1：物理异构源拉取与轻量入湖
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * useIngestStep 承载原有 state 与 startIngest 定时器逻辑，StepIngest 为原 JSX 片段，
 * 日志文案、className、动画类与提交回调调用点均逐字保留。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { CheckCircle2, Wifi } from 'lucide-react';
import { useTheme } from '../ThemeContext';

type ToastFn = (type: 'success' | 'error' | 'info', message: string) => void;

export function useIngestStep(toast: ToastFn, handleCommitToGit: (message: string, filesChanged: string[]) => void) {
  // --- STEP 1: INGEST STATE ---
  const [ingestLogs, setIngestLogs] = useState<string[]>([]);
  const [isIngesting, setIsIngesting] = useState<boolean>(false);
  const [ingestProgress, setIngestProgress] = useState<number>(0);
  const [ingestSuccess, setIngestSuccess] = useState<boolean>(false);

  const startIngest = () => {
    setIsIngesting(true);
    setIngestSuccess(false);
    setIngestProgress(0);
    setIngestLogs([
      '[KMS-AGENT] 🔑 正在检索安全库 Vault 凭证 [postgres_prod_db_key]...',
      '[INGRESS-CONTROLLER] 📡 启动 Magritte 安全网关，建立 TLS 隧道...',
      '[INGRESS-CONTROLLER] SUCCESS: 连接至 10.22.4.91:5432 (PostgreSQL 生产库)',
    ]);

    const steps = [
      '[METADATA-PROBE] 🔍 探查目标物理 schema: flights_raw_archive',
      '[INGEST-JOB] 📥 下推全量 SNAPSHOT 数据拉取，限制单分区并发量 <= 8',
      '[INGEST-JOB] 🔄 正在写盘至 DFS Bronze 分区: /aviation/bronze/flights_raw/dt=2026-07-03/',
      '[DATA-COMPRESS] 🗄️ 启用 Snappy 块内压缩，平均压缩率 4.2x',
      '[SUCCESS] 🎉 Ingest 完成！成功拉取 15,000 条物理航班存根报文，大小: 24.5 MB',
    ];

    let count = 0;
    const interval = setInterval(() => {
      count += 20;
      setIngestProgress(count);

      const logIdx = Math.floor(count / 20) - 1;
      if (steps[logIdx]) {
        setIngestLogs((prev) => [...prev, steps[logIdx]]);
      }

      if (count >= 100) {
        clearInterval(interval);
        setIsIngesting(false);
        setIngestSuccess(true);
        toast('success', '步骤 1 (Ingest) 物理源拉取完毕，已落入 Bronze 层！');
        handleCommitToGit('chore(ingest): 物理源拉取完成，更新 /aviation/bronze/flights_raw 数据存根', ['flights_raw.parquet']);
      }
    }, 400);
  };

  return { ingestLogs, isIngesting, ingestProgress, ingestSuccess, startIngest };
}

interface StepIngestProps {
  ingestLogs: string[];
  isIngesting: boolean;
  ingestProgress: number;
  ingestSuccess: boolean;
  startIngest: () => void;
}

export default function StepIngest({
  ingestLogs,
  isIngesting,
  ingestProgress,
  ingestSuccess,
  startIngest,
}: StepIngestProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-4 font-sans">
      <div className={`p-4 ${styles.appBg} border ${styles.cardBorder} rounded-xl space-y-3`}>
        <div className="flex justify-between items-center">
          <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>物理连接凭证托管 (Credential Custody)</span>
          <span className="h-2 w-2 rounded-full bg-emerald-500" title="连接可用" />
        </div>

        <div className="grid grid-cols-2 gap-3 text-xs">
          <div className={`${styles.primaryBg} p-2.5 rounded-lg border ${styles.cardBorder} flex flex-col gap-0.5`}>
            <span className={`${styles.cardTextMuted} text-[9px] font-mono`}>CONNECTION NAME</span>
            <span className={`font-bold ${styles.cardText}`}>postgres_prod_db</span>
          </div>
          <div className={`${styles.primaryBg} p-2.5 rounded-lg border ${styles.cardBorder} flex flex-col gap-0.5`}>
            <span className={`${styles.cardTextMuted} text-[9px] font-mono`}>DRIVER GATEWAY</span>
            <span className={`font-bold ${styles.cardText}`}>Foundry JDBC Agent v3</span>
          </div>
        </div>
      </div>

      <div className="flex gap-4 items-stretch">
        <div className={`flex-1 border ${styles.cardBorder} rounded-xl p-4 ${styles.primaryBg} flex flex-col justify-between gap-3`}>
          <div className="space-y-1">
            <div className={`text-xs font-bold ${styles.cardText}`}>物理入湖动作模拟 (Magritte Ingest Sandbox)</div>
            <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
              点击右侧按钮测试握手提取，查看分布式 Ingress 将外部 DB 或 AWS S3 数据包进行块级分割、断点校验、并写入 Bronze 文件夹的底层全套物理日志。
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={startIngest}
              disabled={isIngesting}
              className={`flex-1 py-2 ${styles.darkBg} hover:${styles.darkHover} text-white font-bold text-xs rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer shadow-xs disabled:${styles.inputBg} disabled:text-${styles.cardTextMuted}`}
            >
              <Wifi size={13} className={isIngesting ? 'animate-ping' : ''} />
              <span>{isIngesting ? `提取拉取中... (${ingestProgress}%)` : '启动物理 Ingest 提取'}</span>
            </button>

            {ingestSuccess && (
              <div className="flex items-center gap-1 text-emerald-600 text-xs font-bold animate-pulse">
                <CheckCircle2 size={14} />
                <span>已成功落湖！</span>
              </div>
            )}
          </div>
        </div>

        <div className={`w-[280px] ${styles.terminalBg} rounded-xl border ${styles.terminalBorder} p-3 font-mono text-[9px] ${styles.terminalText} leading-relaxed max-h-40 overflow-y-auto`}>
          {ingestLogs.length === 0 ? (
            <div className={`${styles.cardTextMuted} italic h-full flex items-center justify-center text-center`}>
              等待触发 Ingest Ingress 仿真提取运行日志...
            </div>
          ) : (
            <div className="space-y-1">
              {ingestLogs.map((log, idx) => (
                <div
                  key={idx}
                  className={
                    log.startsWith('[SUCCESS]') || log.includes('SUCCESS')
                      ? 'text-emerald-400 font-bold'
                      : log.startsWith('[KMS')
                      ? 'text-amber-400'
                      : styles.terminalText
                  }
                >
                  {log}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

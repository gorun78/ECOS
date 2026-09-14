/**
 * MessageBubble — Agent Playground 消息气泡 + OAG 8 步进度条 + 来源溯源卡
 * @license Apache-2.0
 */
import React from 'react';
import {
  Cpu, User, Check, X, Loader2, Copy, Clock, Cpu as CpuIcon, ShieldCheck,
  Crosshair, Network, Zap, PenLine, Shield, Database, Brain, FileText, ClipboardCheck,
} from 'lucide-react';
import type { OagStepEvent } from '../../../types/aiworkbench';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';

// ── OAG 8 节点定义（与后端 OagPipelineEngine 对齐） ───────────────────
interface OagNodeMeta {
  key: string;
  label: string;
  icon: React.ComponentType<{ size?: number | string; className?: string }>;
}

export const OAG_NODES: OagNodeMeta[] = [
  { key: 'IntentClassifier', label: 'IntentClass', icon: Crosshair },
  { key: 'ContextLoader', label: 'ContextLoad', icon: Database },
  { key: 'QueryRewriter', label: 'QueryRewrite', icon: PenLine },
  { key: 'SecurityChecker', label: 'SecurityCheck', icon: ShieldCheck },
  { key: 'KnowledgeRetriever', label: 'KnowledgeRetr', icon: Network },
  { key: 'ReasoningEngine', label: 'Reasoning', icon: Brain },
  { key: 'ResponseCompiler', label: 'ResponseCompile', icon: FileText },
  { key: 'AuditLogger', label: 'AuditLog', icon: ClipboardCheck },
];

export type OagStepState = OagStepEvent & {
  /** 前端计算的缺失/完成状态：'pending' | 'running' | 'done' | 'error' */
  stepState: 'pending' | 'running' | 'done' | 'error';
};

/**
 * OagProgress — 8 节点进度条，hover 显示 step name + elapsedMs。
 */
export function OagProgress({ steps, hasError }: { steps: OagStepState[]; hasError: boolean }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const done = steps.filter((s) => s.stepState === 'done').length;
  const pct = Math.round((done / OAG_NODES.length) * 100);

  return (
    <div className={`rounded-xl border ${styles.cardBorder} ${styles.inputBg} p-2.5 space-y-2`}>
      <div className="flex items-center justify-between">
        <span className={`flex items-center gap-1.5 font-mono text-[10px] font-bold ${styles.accentText}`}>
          <Cpu size={11} className="animate-pulse" />
          {t('aiworkbench.playground.oagProgressTitle')}
        </span>
        <span className={`font-mono text-[9px] font-bold ${hasError ? styles.dangerText : styles.successText}`}>
          {hasError
            ? t('aiworkbench.playground.streamError')
            : `${done}/${OAG_NODES.length} · ${pct}%`}
        </span>
      </div>

      <div className={`h-1 rounded-full overflow-hidden ${styles.badgeBg}`}>
        <div
          className={`h-full rounded-full transition-all duration-500 ease-out ${hasError ? styles.dangerBg : styles.accentBg}`}
          style={{ width: `${pct}%` }}
        />
      </div>

      <div className="flex items-center gap-0.5">
        {steps.map((s, idx) => {
          const meta = OAG_NODES.find((n) => n.key === s.node) || OAG_NODES[idx];
          const IconComp = meta.icon;
          const isDone = s.stepState === 'done';
          const isRunning = s.stepState === 'running';
          const isError = s.stepState === 'error';
          return (
            <React.Fragment key={meta.key}>
              {idx > 0 && (
                <div
                  className={`flex-1 h-px ${isDone || isError ? styles.accentBorder : styles.cardBorder} border-t`}
                  style={{ borderTopStyle: 'dashed' }}
                />
              )}
              <span
                title={`${meta.label}${typeof s.nodeElapsedMs === 'number' ? ` · ${s.nodeElapsedMs}ms` : ''}${s.stepState !== 'pending' ? ` · ${s.stepState}` : ''}`}
                className={`shrink-0 w-6 h-6 rounded-md flex items-center justify-center transition-colors cursor-help ${
                  isDone
                    ? `${styles.successBg} ${styles.successText}`
                    : isRunning
                      ? `${styles.infoBg} ${styles.infoText} animate-pulse`
                      : isError
                        ? `${styles.dangerBg} ${styles.dangerText}`
                        : `${styles.badgeBg} ${styles.muted}`
                }`}
              >
                {isDone ? <Check size={11} /> : isError ? <X size={11} /> : isRunning ? <Loader2 size={11} className="animate-spin" /> : <IconComp size={11} />}
              </span>
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}

/**
 * SourceCard — AI 回复的来源溯源卡（traceId / model / tokens / elapsedMs）。
 */
export function SourceCard({
  traceId,
  model,
  tokensUsed,
  elapsedMs,
}: {
  traceId?: string;
  model?: string;
  tokensUsed?: number;
  elapsedMs?: number;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  if (!traceId && !model && tokensUsed === undefined) return null;

  const rows: Array<{ label: string; value: string; icon: React.ReactNode }> = [
    {
      label: t('aiworkbench.playground.sourceCardTrace'),
      value: traceId ? traceId.slice(0, 16) : '—',
      icon: <CpuIcon size={9} />,
    },
    {
      label: t('aiworkbench.playground.sourceCardModel'),
      value: model || '—',
      icon: <Zap size={9} />,
    },
    {
      label: t('aiworkbench.playground.sourceCardTokens'),
      value: typeof tokensUsed === 'number' ? String(tokensUsed) : '—',
      icon: <Network size={9} />,
    },
    {
      label: t('aiworkbench.playground.sourceCardElapsed'),
      value: typeof elapsedMs === 'number' ? `${elapsedMs}ms` : '—',
      icon: <Clock size={9} />,
    },
  ];

  return (
    <div
      className={`rounded-lg border ${styles.cardBorder} ${styles.inputBg} p-2 grid grid-cols-2 gap-x-3 gap-y-1 font-mono text-[9px]`}
      title={traceId || ''}
    >
      {rows.map((r) => (
        <div key={r.label} className="flex items-center gap-1 min-w-0">
          <span className={`${styles.muted} shrink-0`}>{r.icon}</span>
          <span className={`${styles.muted} shrink-0`}>{r.label}</span>
          <span className={`ml-auto truncate ${styles.cardText} font-bold`} title={r.value}>{r.value}</span>
        </div>
      ))}
    </div>
  );
}

export interface PlaygroundMsg {
  id: string;
  sender: 'user' | 'ai';
  content: string;
  timestamp: string;
  oagSteps?: OagStepState[];
  blockedReason?: string;
  errorMessage?: string;
  traceId?: string;
  model?: string;
  elapsedMs?: number;
  tokensUsed?: number;
  intent?: string;
  securityPassed?: boolean;
  /** OAG 最终 status：COMPLETED / BLOCKED / FAILED（由 onDone 写入） */
  status?: string;
  failed?: boolean;
  networkError?: boolean;
}

/**
 * MessageBubbleProps — 单条消息渲染（含 phase 状态）。
 * phase: idle = 已落定；streaming = 流式进行中。
 */
interface MessageBubbleProps {
  msg: PlaygroundMsg;
  agentName: string;
  agentAvatar: string;
  isStreaming: boolean;
  onCopy?: (text: string) => void;
  onRetry?: (msgId: string) => void;
}

/**
 * MessageBubble — 单条消息气泡。
 * 用户：右侧 accent、内容右对齐。
 * AI：左侧 avatar + OagProgress（若有）+ 正文 + 来源卡（若有）。
 */
export default function MessageBubble({
  msg,
  agentName,
  agentAvatar,
  isStreaming,
  onCopy,
  onRetry,
}: MessageBubbleProps) {
  const { styles } = useTheme();
  const { t, locale } = useLanguage();
  const isUser = msg.sender === 'user';
  const hasOag = Array.isArray(msg.oagSteps) && msg.oagSteps.length > 0;

  // 折叠 traceId 显示：前 12 字符 + …
  const shortTrace = msg.traceId ? (msg.traceId.length > 16 ? `${msg.traceId.slice(0, 12)}…` : msg.traceId) : '';

  // ISO 时间 → 本地化 HH:mm
  const time = (() => {
    if (!msg.timestamp) return '';
    const d = new Date(msg.timestamp);
    if (Number.isNaN(d.getTime())) return msg.timestamp;
    return d.toLocaleTimeString(locale === 'zh' ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' });
  })();

  // 错误态：error message 没内容时也展示（fallback）
  const displayText = msg.content || msg.errorMessage || (msg.blockedReason
    ? t('aiworkbench.playground.blocked') : '');

  const canCopy = !isStreaming && Boolean(displayText);
  const canRetry = Boolean(isStreaming && (msg.networkError || msg.failed));
  const showRetry = canRetry;

  // Avatar 组件（key prop 与 React 兼容）
  const avatar = (
    <span
      className={`p-1.5 rounded-lg shrink-0 h-7 w-7 flex items-center justify-center text-white font-bold shadow-xs ${
        isUser ? styles.accentBg : styles.sidebarActiveBg
      }`}
      aria-hidden
    >
      {isUser ? <User size={12} /> : <Cpu size={12} />}
    </span>
  );

  return (
    <div key={msg.id} className={`flex gap-2.5 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {avatar}
      <div className="max-w-[85%] space-y-1.5 min-w-0">
        {/* 头部：时间戳 + 角色 */}
        <div className={`flex items-center gap-1.5 text-[9px] font-mono ${styles.muted}`}>
          <span className="font-bold">{isUser ? (locale === 'zh' ? '我' : 'You') : agentName}</span>
          {time && <span>· {time}</span>}
          {msg.intent && (
            <span className={`px-1 rounded ${styles.badgeBg} font-bold`}>{msg.intent}</span>
          )}
        </div>

        {/* OAG 进度条 —— AI 消息独有 */}
        {!isUser && hasOag && (
          <OagProgress steps={msg.oagSteps || []} hasError={Boolean(msg.errorMessage)} />
        )}

        {/* 拦截横幅（BLOCKED 时显示 reason） */}
        {!isUser && msg.blockedReason && (
          <div className={`rounded-lg border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} px-2.5 py-1.5 text-[10px] font-medium flex items-start gap-1.5`}>
            <Shield size={11} className="shrink-0 mt-0.5" />
            <span className="flex-1"><span className="font-bold">{t('aiworkbench.playground.blocked')}</span> · {msg.blockedReason}</span>
          </div>
        )}

        {/* 正文气泡 */}
        <div
          className={`px-3 py-2 rounded-2xl leading-normal text-[11px] whitespace-pre-line font-sans break-words shadow-xs ${
            isUser
              ? `${styles.accentBg} text-white font-medium rounded-tr-none`
              : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder} border rounded-tl-none`
          }`}
        >
          {displayText || (isStreaming ? t('aiworkbench.playground.typing') : '')}
          {isStreaming && displayText && (
            <span className={`inline-block w-2 h-3.5 ml-0.5 rounded-sm align-middle animate-pulse ${styles.accentBg}`} />
          )}
          {/* 流式空占位 */}
          {isStreaming && !displayText && (
            <span className="flex items-center gap-1.5">
              <Loader2 size={10} className="animate-spin" />
              <span className={styles.muted}>{t('aiworkbench.playground.typing')}</span>
            </span>
          )}
        </div>

        {/* 错误/网络失败 + 重试按钮 */}
        {(msg.failed || msg.networkError) && (
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`px-1.5 py-0.5 rounded ${styles.dangerBg} ${styles.dangerText} text-[9px] font-mono font-bold`}>
              {msg.networkError ? t('aiworkbench.playground.loadError') : t('aiworkbench.playground.streamError')}
            </span>
            {showRetry && onRetry && (
              <button
                type="button"
                onClick={() => onRetry(msg.id)}
                className={`px-2 py-0.5 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} hover:opacity-90 cursor-pointer`}
              >
                {t('aiworkbench.playground.retry')}
              </button>
            )}
          </div>
        )}

        {/* 来源溯源卡（仅 AI 完成态） */}
        {!isUser && !isStreaming && (msg.traceId || msg.model || typeof msg.elapsedMs === 'number') && (
          <SourceCard
            traceId={msg.traceId}
            model={msg.model}
            tokensUsed={msg.tokensUsed}
            elapsedMs={msg.elapsedMs}
          />
        )}

        {/* 操作按钮：复制 / 重试 */}
        {!isUser && (canCopy || showRetry) && (
          <div className="flex items-center gap-1.5">
            {canCopy && onCopy && (
              <button
                type="button"
                onClick={() => onCopy(displayText)}
                className={`p-1 rounded ${styles.cardBg} border ${styles.cardBorder} ${styles.muted} hover:opacity-75 cursor-pointer`}
                title={t('aiworkbench.playground.copyMessage')}
                aria-label={t('aiworkbench.playground.copyMessage')}
              >
                <Copy size={10} />
              </button>
            )}
          </div>
        )}

        {/* stopped 占位（让客服知道消息已中断） */}
        {!isUser && isStreaming && (
          <div className={`flex items-center gap-1 text-[9px] font-mono ${styles.muted}`}>
            <Loader2 size={9} className="animate-spin" />
            <span>{t('aiworkbench.playground.streaming')}</span>
          </div>
        )}
        {/* 隐藏 traceId 参考信息，仅用于 hover 提示 */}
        {msg.traceId && !isStreaming && <span className="hidden" aria-hidden data-trace={shortTrace} />}
      </div>
    </div>
  );
}

/**
 * govern/retrieval — RAG 调试子 Tab（分册 04 B.3 govern/retrieval）。
 *
 * 主流程（B.2）：输入问题 → 看召回（命中片段/score/来源）→ 看生成（流式 SSE）
 * 数据字段（B.3）：命中片段、score、来源、`vectorDegraded`、掩蔽标记
 * 交互态（B.4）：流式输出（SSE 经统一封装 — `runRAGQuerySSE`，禁页面内手写）
 * 空/错误态（B.5）：向量降级显式横幅；5xx/ECOS-KB-* → 显式故障态
 * 权限（B.6）：生成前经 CLS/脱敏（敏感源显示 ••• 掩蔽标记）
 * 错误态分治（F04-03）：走 kbFetchData；KbApiError → 显式故障态
 * 主题 i18n：knowledge.retrieval.*
 */
import { useCallback, useRef, useState } from 'react';
import {
  AlertTriangle, BookOpen, EyeOff, Loader2, RefreshCw, Send, ServerCrash, Sparkles,
} from 'lucide-react';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';
import { knowledgeApi, KbApiError } from '../../../services/knowledgeApi';
import type { RagResult } from '../../../typesAndConstants';

interface BizError { code?: string; message: string }
interface SourceItem { title: string; type?: string; snippet: string; score: number; page?: number; masked?: boolean }

export default function RetrievalTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [query, setQuery] = useState('');
  const [running, setRunning] = useState(false);
  const [answer, setAnswer] = useState('');
  const [sources, setSources] = useState<SourceItem[]>([]);
  const [vectorDegraded, setVectorDegraded] = useState(false);
  const [bizError, setBizError] = useState<BizError | null>(null);
  const streamRef = useRef<string>('');

  const run = useCallback(async () => {
    if (!query.trim() || running) return;
    setRunning(true);
    setBizError(null);
    setAnswer('');
    setSources([]);
    setVectorDegraded(false);
    streamRef.current = '';
    try {
      // B.4：流式输出经统一封装 runRAGQuerySSE（禁页面内手写 SSE）
      await knowledgeApi.runRAGQuerySSE(query.trim(), (tok) => {
        streamRef.current += tok;
        setAnswer(streamRef.current);
      });
      // 拉取召回来源 + 生成结果（含 vectorDegraded 判定）
      try {
        const res: RagResult = await knowledgeApi.kbRunRAGQuery({ query: query.trim() });
        if (res.sources?.length) {
          setSources(res.sources.map((s) => ({ ...s, masked: isMaskedSource(s.title, s.snippet) })));
        }
        if (res.overallConfidence != null && res.overallConfidence < 0.3) {
          setVectorDegraded(true);
        }
      } catch (e) {
        // 第二次非关键（源列表）失败：如 KbApiError 显式提示
        if (e instanceof KbApiError && (e.status >= 500 || (e.code && e.code.startsWith('ECOS-KB-')))) {
          setBizError({ code: e.code, message: e.message });
        }
      }
    } catch (e) {
      if (e instanceof KbApiError && (e.status >= 500 || (e.code && e.code.startsWith('ECOS-KB-')))) {
        setBizError({ code: e.code, message: e.message });
      }
    } finally {
      setRunning(false);
    }
  }, [query, running]);

  return (
    <div className="flex flex-col gap-3 h-full min-h-0">
      {/* 向量降级显式横幅（B.5） */}
      {vectorDegraded && (
        <div className="flex items-center gap-2 px-3 py-2 rounded-md border text-xs"
             style={{ background: styles.warningBg, borderColor: styles.warningBorder, color: styles.warningText }}>
          <ServerCrash className="w-3.5 h-3.5 shrink-0" />
          <span className="font-bold">{t('knowledge.retrieval.vector_degraded')}</span>
          <span className="opacity-80 text-[10px]">{t('knowledge.retrieval.vector_degraded_hint')}</span>
        </div>
      )}

      {/* 错误态分治横幅（F04-03） */}
      {bizError && (
        <div className="flex items-start gap-2 px-3 py-2 rounded-md border text-xs"
             style={{ background: styles.dangerBg, borderColor: styles.dangerBorder, color: styles.dangerText }}>
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <div className="font-bold flex items-center gap-1.5">
              {t('knowledge.retrieval.error')}
              {bizError.code && <span className="font-mono text-[10px]">{bizError.code}</span>}
            </div>
            <p className="opacity-80 break-all mt-0.5">{bizError.message}</p>
          </div>
          <button type="button" onClick={() => { setBizError(null); void run(); }}
                  className="px-2 py-1 rounded border text-[10px] font-bold cursor-pointer shrink-0"
                  style={{ borderColor: styles.dangerBorder, color: styles.dangerText }}>
            <RefreshCw className="w-3 h-3 inline mr-1" />{t('knowledge.common.retry')}
          </button>
        </div>
      )}

      {/* 输入区 */}
      <div className="flex items-center gap-2">
        <div className="flex-1 flex items-center gap-2 rounded-md px-3 py-2"
             style={{ background: styles.inputBg, border: `1px solid ${styles.inputBorder}` }}>
          <Sparkles className="w-4 h-4 shrink-0" style={{ color: styles.muted }} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void run(); }}
            placeholder={t('knowledge.retrieval.placeholder')}
            aria-label={t('knowledge.retrieval.placeholder')}
            className="flex-1 text-[12px] outline-none bg-transparent"
            style={{ color: styles.inputText }}
          />
        </div>
        <button type="button" onClick={() => void run()}
                disabled={running || !query.trim()}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-[12px] font-bold cursor-pointer disabled:opacity-50"
                style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}>
          {running ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          {t('knowledge.retrieval.run')}
        </button>
      </div>

      {/* 结果区：召回 + 生成 2 栏 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 flex-1 min-h-0">
        {/* 召回 */}
        <div className="rounded-md border flex flex-col overflow-hidden" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="px-3 py-2 border-b text-[11px] font-bold" style={{ borderColor: styles.cardBorder, color: styles.cardText }}>
            {t('knowledge.retrieval.sources')}
          </div>
          <div className="flex-1 overflow-y-auto p-2 space-y-2">
            {sources.length === 0 ? (
              <div className="h-full flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
                {running ? <><Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />{t('knowledge.retrieval.loading')}</>
                         : t('knowledge.retrieval.sources_empty')}
              </div>
            ) : sources.map((s, i) => (
              <div key={i} className="rounded-md border p-2 space-y-1" style={{ borderColor: styles.cardBorder, background: styles.inputBg }}>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold truncate flex items-center gap-1" style={{ color: styles.cardText }}>
                    <BookOpen className="w-3 h-3 shrink-0" />
                    {s.title}
                    {s.type && <span className="text-[9px] font-mono" style={{ color: styles.muted }}>{s.type}</span>}
                  </span>
                  <span className="text-[10px] font-mono shrink-0" style={{ color: styles.accentText }}>
                    {(s.score * 100).toFixed(0)}%
                  </span>
                </div>
                <p className="text-[10px] leading-snug" style={{ color: styles.cardTextMuted }}>
                  {s.masked ? '•••' : (s.snippet || t('knowledge.retrieval.no_snippet'))}
                  {s.masked && (
                    <span className="ml-1 inline-flex items-center gap-0.5" title={t('knowledge.retrieval.masked_hint')}>
                      <EyeOff className="w-2.5 h-2.5" />
                    </span>
                  )}
                </p>
              </div>
            ))}
          </div>
        </div>

        {/* 生成（流式） */}
        <div className="rounded-md border flex flex-col overflow-hidden" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="px-3 py-2 border-b text-[11px] font-bold flex items-center gap-1.5"
               style={{ borderColor: styles.cardBorder, color: styles.cardText }}>
            <Sparkles className="w-3.5 h-3.5" />{t('knowledge.retrieval.answer')}
            {running && <span className="text-[9px] font-mono" style={{ color: styles.muted }}>· {t('knowledge.retrieval.streaming')}</span>}
          </div>
          <div className="flex-1 overflow-y-auto p-3 text-[12px] leading-relaxed" style={{ color: styles.cardText }}>
            {answer ? <p className="whitespace-pre-wrap">{answer}</p>
              : <span style={{ color: styles.muted }}>{t('knowledge.retrieval.answer_empty')}</span>}
          </div>
        </div>
      </div>
    </div>
  );
}

/** CLS 掩蔽判定：源标题/snippet 含敏感标记 → 显示 •••（B.6 生成前经 security 脱敏） */
function isMaskedSource(title: string, snippet: string): boolean {
  const raw = `${title} ${snippet}`.toLowerCase();
  return raw.includes('masked') || raw.includes('[redacted]') || raw.includes('•••') || raw.includes('[cls]');
}

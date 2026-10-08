import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { Binary, Play, RefreshCw, Terminal, Clock } from 'lucide-react';
import type { GuardrailPolicy, PreviewData } from './types';
import { Spinner } from './UiPrimitives';
import PreviewComparison from './PreviewComparison';

export default function CompilePreviewTab({
  policy, isCompiling, compileLogs, loadingPreview, previewData, onCompile, onRefreshPreview,
}: {
  policy: GuardrailPolicy | null;
  isCompiling: boolean;
  compileLogs: string[];
  loadingPreview: boolean;
  previewData: PreviewData | null;
  onCompile: (p: GuardrailPolicy) => void;
  onRefreshPreview: (p: GuardrailPolicy) => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="flex-1 flex flex-col min-h-0 gap-4">
      {!policy ? (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted} space-y-2`}>
          <Binary size={28} className={styles.cardTextMuted} />
          <p className="font-bold">{t('gw.compile.pickFirst')}</p>
        </div>
      ) : (
        <>
          {/* Compiler status banner */}
          <div className="bg-[var(--card,#0B0F19)] text-white rounded-xl p-4 border border-[var(--muted,#1E293B)] shrink-0 flex flex-col lg:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3 text-left">
              <div className="p-2.5 bg-rose-500/20 text-rose-400 rounded-full border border-rose-500/30">
                <Binary size={18} />
              </div>
              <div className="space-y-0.5">
                <span className="text-[9px] text-[var(--muted-foreground,#94A3B8)] font-bold uppercase tracking-wider block">{t('gw.compile.title')}</span>
                <div className="flex items-center gap-2">
                  <span className="font-extrabold text-sm text-[var(--text-primary,#F8FAFC)]">{policy.name}</span>
                  <span className={`px-2 py-0.5 rounded-full text-[9px] font-mono font-black uppercase inline-flex items-center gap-1 ${policy.status === 'COMPILED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse'}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${policy.status === 'COMPILED' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                    <span>{policy.status === 'COMPILED' ? 'COMPILED & DEPLOYED' : 'DRAFT (NEEDS COMPILATION)'}</span>
                  </span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              {policy.compiledAt && (
                <div className="text-right hidden sm:block">
                  <p className="text-[10px] text-[var(--muted-foreground,#94A3B8)] font-semibold flex items-center gap-1 justify-end"><Clock size={9} /> {t('gw.compile.last')}</p>
                  <p className="font-mono text-[10px] text-[var(--muted-foreground,#CBD5E1)] font-bold">{policy.compiledAt}</p>
                </div>
              )}
              <button
                type="button"
                onClick={() => onCompile(policy)}
                disabled={isCompiling}
                className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-75 text-white font-bold rounded-lg shadow-md transition-all flex items-center gap-1.5 cursor-pointer"
              >
                {isCompiling ? (
                  <><Spinner /><span>{t('gw.compile.compiling')}</span></>
                ) : (
                  <><Play size={12} /><span>{t('gw.compile.deploy')}</span></>
                )}
              </button>
            </div>
          </div>

          {/* Main grid: logs + preview */}
          <div className="flex-1 grid grid-cols-1 xl:grid-cols-5 gap-4 min-h-0 overflow-y-auto">
            {/* Compiler logs */}
            <div className="xl:col-span-2 bg-[var(--muted,#020202)] text-[var(--card,#E2E8F0)] rounded-xl border border-[var(--muted,#1E293B)] p-4 shadow-md font-mono flex flex-col space-y-2.5 shrink-0">
              <div className="flex items-center justify-between border-b border-[var(--muted,#1E293B)] pb-2">
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${isCompiling ? 'bg-rose-500 animate-pulse' : 'bg-emerald-500'}`} />
                  <span className="font-bold text-xs text-[var(--card,#94A3B8)] flex items-center gap-1"><Terminal size={12} /> Compiler Stage Stream Logs</span>
                </div>
                <span className="text-[9px] text-[var(--card,#64748B)] font-bold">SECURE_COMPILER_BUS</span>
              </div>
              <div className="space-y-1.5 max-h-72 overflow-y-auto text-[10px] leading-relaxed select-text">
                {compileLogs.length === 0 ? (
                  <p className="text-[var(--card,#64748B)] italic">{t('gw.compile.waitPrompt')}</p>
                ) : (
                  compileLogs.map((log, idx) => (
                    <p key={idx} className={
                      log.includes('✅') ? 'text-emerald-400 font-extrabold' :
                      log.includes('⚠️') ? 'text-amber-400 font-semibold' :
                      log.includes('🚫') || log.includes('ERROR') ? 'text-rose-400 font-bold' :
                      log.includes('🔒') ? 'text-sky-400 font-medium' : 'text-[var(--muted-foreground,#CBD5E1)]'
                    }>
                      {log}
                    </p>
                  ))
                )}
              </div>
            </div>

            {/* Dry-run preview */}
            <div className={`xl:col-span-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-sm p-4 flex-1 flex flex-col min-h-0`}>
              <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3 mb-3 shrink-0`}>
                <div className="space-y-0.5">
                  <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5 text-xs`}>
                    <RefreshCw size={13} className="text-emerald-500" />
                    <span>{t('gw.preview.title')}</span>
                  </span>
                  <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('gw.preview.sub')}</p>
                </div>
                <button
                  type="button"
                  onClick={() => onRefreshPreview(policy)}
                  disabled={loadingPreview}
                  className={`px-2.5 py-1 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-md text-[10px] font-bold ${styles.cardText} flex items-center gap-1 cursor-pointer`}
                >
                  <RefreshCw size={10} className={loadingPreview ? 'animate-spin' : ''} /> {t('gw.preview.refresh')}
                </button>
              </div>

              {loadingPreview ? (
                <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} gap-2`}>
                  <Spinner className="w-4 h-4" /><span>{t('gw.preview.generating')}</span>
                </div>
              ) : previewData ? (
                <PreviewComparison data={previewData} />
              ) : (
                <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted}`}>
                  <span>{t('gw.preview.empty')}</span>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

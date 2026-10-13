import { useState } from 'react';
import { AlertTriangle, Loader2, Info, Lock, Smartphone, Mail, IdCard, CircleDollarSign } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { maskData } from '../../../api';
import { inputClasses, btnPrimary } from './helpers';

// ── Masking Rule Manager ─────────────────────────────────────
export default function MaskingRuleManager() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [input, setInput] = useState('');
  const [maskType, setMaskType] = useState('SHA256');
  const [masking, setMasking] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const maskTypes = [
    { id: 'SHA256', key: 'sec.mask.type.sha256', icon: Lock },
    { id: 'PHONE', key: 'sec.mask.type.phone', icon: Smartphone },
    { id: 'EMAIL', key: 'sec.mask.type.email', icon: Mail },
    { id: 'ID_CARD', key: 'sec.mask.type.idcard', icon: IdCard },
    { id: 'AMOUNT', key: 'sec.mask.type.amount', icon: CircleDollarSign },
  ];

  const handleMask = async () => {
    if (!input.trim()) return;
    setMasking(true);
    setError(null);
    setResult(null);
    try {
      const res = await maskData({ input: input.trim(), maskType });
      setResult(res.masked);
    } catch (e: any) {
      setError(e.message || 'Masking failed');
    } finally {
      setMasking(false);
    }
  };

  // Demo fallback when backend is unavailable
  const demoMask = (text: string, type: string): string => {
    switch (type) {
      case 'SHA256': return text.length > 8 ? text.substring(0, 4) + '...' + text.substring(text.length - 4) : '****';
      case 'PHONE': return text.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
      case 'EMAIL': return text.replace(/(.{2}).*(@.*)/, '$1***$2');
      case 'ID_CARD': return text.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2');
      case 'AMOUNT': return '¥***.**';
      default: return '****';
    }
  };

  const handleDemoMask = () => {
    if (!input.trim()) return;
    setResult(demoMask(input.trim(), maskType));
  };

  return (
    <div className="p-6 space-y-6 overflow-auto h-full">
      <div>
        <h3 className={`text-lg font-bold mb-1 ${styles.cardText}`}>{t('sec.mask.title')}</h3>
        <p className={`text-sm ${styles.muted}`}>{t('sec.mask.desc')}</p>
      </div>

      {/* Mask type cards */}
      <div className="grid grid-cols-5 gap-3">
        {maskTypes.map(mt => {
          const MtIcon = mt.icon;
          return (
          <button type="button"
            key={mt.id}
            onClick={() => { setMaskType(mt.id); setResult(null); setError(null); }}
            className={`p-4 rounded-xl text-center transition-all cursor-pointer border-2 ${
              maskType === mt.id
                ? `${styles.accentBorder} ${styles.cardBg}`
                : `border-transparent ${styles.cardBg} opacity-70 hover:opacity-100`
            }`}
          >
            <div className={`mb-1 flex justify-center ${maskType === mt.id ? styles.accentText : styles.muted}`}><MtIcon size={24} /></div>
            <div className={`text-xs font-medium ${styles.cardText}`}>{t(mt.key)}</div>
          </button>
          );
        })}
      </div>

      {/* Input */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <label className={`block text-sm font-medium mb-2 ${styles.cardText}`}>{t('sec.mask.input')}</label>
        <textarea
          rows={3}
          value={input}
          onChange={e => { setInput(e.target.value); setResult(null); }}
          placeholder={t('sec.mask.placeholder')}
          className={inputClasses(styles)}
        />
      </div>

      {/* Buttons */}
      <div className="space-y-1.5">
        <div className="flex gap-2">
          <button type="button" onClick={handleMask} disabled={masking || !input.trim()} className={btnPrimary(styles)}>
            {masking ? <Loader2 size={14} className="animate-spin" /> : t('sec.mask.btn')}
          </button>
          <button type="button" onClick={handleDemoMask} disabled={!input.trim()}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium border ${styles.infoBg} ${styles.infoText} ${styles.infoBorder} transition-colors cursor-pointer disabled:opacity-50`}
            title={t('sec.mask.demoHint')}>
            <Info size={14} />{t('sec.mask.localDemo')}
          </button>
        </div>
        <p className={`text-[10px] ${styles.muted}`}>{t('sec.mask.demoHint')}</p>
      </div>

      {/* Error */}
      {error && (
        <div className={`p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={14} className="shrink-0 mt-0.5" /><span>{error}</span>
        </div>
      )}

      {/* Result */}
      {result !== null && (
        <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
          <h4 className={`text-sm font-medium mb-3 ${styles.cardText}`}>{t('sec.mask.result')}</h4>
          <div className={`p-4 rounded-lg ${styles.inputBg} border ${styles.inputBorder}`}>
            <p className={`font-mono text-sm ${styles.cardText}`}>{result}</p>
          </div>
        </div>
      )}
    </div>
  );
}

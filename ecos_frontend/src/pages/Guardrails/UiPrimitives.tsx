import React from 'react';
import { useTheme } from '../../components/ThemeContext';

// ─────────────────────────────────────────────────────────────
// Small UI primitives
// ─────────────────────────────────────────────────────────────

export function Toggle({ on, onClick, color = 'bg-blue-600' }: { on: boolean; onClick: (e: React.MouseEvent) => void; color?: string }) {
  const { styles } = useTheme();
  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onClick(e); }}
      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${on ? color : styles.inputBg}`}
    >
      <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${on ? 'translate-x-4' : 'translate-x-0'}`} />
    </button>
  );
}

export function Spinner({ className = 'w-3.5 h-3.5' }: { className?: string }) {
  return <span className={`${className} border-2 border-current border-t-transparent rounded-full animate-spin inline-block`} />;
}

export function Badge({ children, tone }: { children: React.ReactNode; tone: 'emerald' | 'amber' | 'rose' | 'slate' | 'blue' }) {
  const { styles } = useTheme();
  const tones: Record<string, string> = {
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    amber: 'bg-amber-50 text-amber-700 border-amber-200',
    rose: 'bg-rose-50 text-rose-700 border-rose-200',
    slate: `${styles.appBg} ${styles.cardText} ${styles.cardBorder}`,
    blue: 'bg-blue-50 text-blue-700 border-blue-200',
  };
  return <span className={`px-1.5 py-0.5 rounded text-[9px] font-black border ${tones[tone]}`}>{children}</span>;
}

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Helper mapping for Tailwind classes based on colors, extracted verbatim from OverviewView.tsx.

export const getDomainColorClasses = (color: string) => {
  switch (color) {
    case 'blue':
      return {
        bg: 'bg-blue-50/70',
        text: 'text-blue-700',
        border: 'border-blue-200',
        activeBg: 'bg-blue-600',
        dot: 'bg-blue-500',
        hoverBg: 'hover:bg-blue-50',
        focusRing: 'focus:ring-blue-400',
        leftBorder: 'border-l-4 border-l-blue-500'
      };
    case 'emerald':
      return {
        bg: 'bg-emerald-50/70',
        text: 'text-emerald-700',
        border: 'border-emerald-200',
        activeBg: 'bg-emerald-600',
        dot: 'bg-emerald-500',
        hoverBg: 'hover:bg-emerald-50',
        focusRing: 'focus:ring-emerald-400',
        leftBorder: 'border-l-4 border-l-emerald-500'
      };
    case 'amber':
      return {
        bg: 'bg-amber-50/70',
        text: 'text-amber-700',
        border: 'border-amber-200',
        activeBg: 'bg-amber-600',
        dot: 'bg-amber-500',
        hoverBg: 'hover:bg-amber-50',
        focusRing: 'focus:ring-amber-400',
        leftBorder: 'border-l-4 border-l-amber-500'
      };
    case 'purple':
      return {
        bg: 'bg-purple-50/70',
        text: 'text-purple-700',
        border: 'border-purple-200',
        activeBg: 'bg-purple-600',
        dot: 'bg-purple-500',
        hoverBg: 'hover:bg-purple-50',
        focusRing: 'focus:ring-purple-400',
        leftBorder: 'border-l-4 border-l-purple-500'
      };
    case 'rose':
      return {
        bg: 'bg-rose-50/70',
        text: 'text-rose-700',
        border: 'border-rose-200',
        activeBg: 'bg-rose-600',
        dot: 'bg-rose-500',
        hoverBg: 'hover:bg-rose-50',
        focusRing: 'focus:ring-rose-400',
        leftBorder: 'border-l-4 border-l-rose-500'
      };
    case 'indigo':
      return {
        bg: 'bg-indigo-50/70',
        text: 'text-indigo-700',
        border: 'border-indigo-200',
        activeBg: 'bg-indigo-600',
        dot: 'bg-indigo-500',
        hoverBg: 'hover:bg-indigo-50',
        focusRing: 'focus:ring-indigo-400',
        leftBorder: 'border-l-4 border-l-indigo-500'
      };
    case 'slate':
      return {
        bg: 'bg-slate-50',
        text: 'text-slate-700',
        border: 'border-slate-300',
        activeBg: 'bg-slate-700',
        dot: 'bg-slate-500',
        hoverBg: 'hover:bg-slate-100/50',
        focusRing: 'focus:ring-slate-400',
        leftBorder: 'border-l-4 border-l-slate-400'
      };
    default:
      return {
        bg: 'bg-slate-50',
        text: 'text-slate-700',
        border: 'border-slate-200',
        activeBg: 'bg-slate-600',
        dot: 'bg-slate-400',
        hoverBg: 'hover:bg-slate-50',
        focusRing: 'focus:ring-slate-400',
        leftBorder: 'border-l-4 border-l-slate-400'
      };
  }
};

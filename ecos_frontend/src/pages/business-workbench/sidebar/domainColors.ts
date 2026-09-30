/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Pure domain-color class helpers, extracted verbatim from Sidebar.tsx.

export const getDomainColorText = (color: string) => {
  switch (color) {
    case 'blue': return 'text-blue-500';
    case 'emerald': return 'text-emerald-500';
    case 'amber': return 'text-amber-500';
    case 'purple': return 'text-purple-500';
    case 'rose': return 'text-rose-500';
    case 'indigo': return 'text-indigo-500';
    case 'slate': return 'text-slate-500';
    default: return 'text-slate-500';
  }
};

export const getDomainColorDotClass = (color: string) => {
  switch (color) {
    case 'blue': return 'bg-blue-500';
    case 'emerald': return 'bg-emerald-500';
    case 'amber': return 'bg-amber-500';
    case 'purple': return 'bg-purple-500';
    case 'rose': return 'bg-rose-500';
    case 'indigo': return 'bg-indigo-500';
    case 'slate': return 'bg-slate-500';
    default: return 'bg-slate-500';
  }
};

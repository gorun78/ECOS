/**
 * Data Workbench — LucideIcon wrapper (transitional)
 * Maps string names to lucide-react icons.
 * ⚠️ PMO rule #7: prefer native lucide-react imports in new code.
 *   This wrapper exists to bridge the ceos_new DataIntegrationView import pattern.
 * @license Apache-2.0
 */
import React from 'react';
import {
  Workflow, Layers, Lightbulb, Database, Import, Cpu, ShieldAlert,
  Settings, FileCode, BookOpen, Download, Copy, X, Activity,
  Check, Play, Search, Plus, Trash2, Edit3, RefreshCw, AlertTriangle,
  GitBranch, Zap, BarChart3, Eye, Link, Unlink, type LucideIcon as LucideIconType,
} from 'lucide-react';

const iconMap: Record<string, LucideIconType> = {
  Workflow, Layers, Lightbulb, Database, Import, Cpu, ShieldAlert,
  Settings, FileCode, BookOpen, Download, Copy, X, Activity,
  Check, Play, Search, Plus, Trash2, Edit3, RefreshCw, AlertTriangle,
  GitBranch, Zap, BarChart3, Eye, Link, Unlink,
};

interface Props {
  name: string;
  size?: number;
  className?: string;
}

export default function LucideIcon({ name, size = 16, className }: Props) {
  const Icon = iconMap[name];
  if (!Icon) {
    console.warn(`[LucideIcon] unknown icon: "${name}"`);
    return <AlertTriangle size={size} className={className} />;
  }
  return <Icon size={size} className={className} />;
}

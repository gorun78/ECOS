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
  GitBranch, Zap, BarChart3, Eye, Link, Unlink, MessageSquare,
  ChevronLeft, ChevronRight, ChevronUp, ChevronDown, Table, Wifi, Terminal,
  Package, FolderTree, FolderOpen, Save, ExternalLink, GitCommit, Loader2,
  CheckCircle, XCircle, Cloud, FolderGit, Globe, HardDrive,
  // #142/B4-3e-66（D-B4-95）：CONNECTION_TYPES.iconName 单源的 12 枚新补图标名
  //   桥缺位＝iconMap 查不到＝console.warn 后静默落 AlertTriangle，又是一次「把已知折成同一个默认读数」
  DatabaseZap, DatabaseBackup, Server, Landmark, KeyRound, Gauge,
  CloudCog, Boxes, FileSpreadsheet, Radio, Leaf, CircleQuestionMark,
  type LucideIcon as LucideIconType,
} from 'lucide-react';

const iconMap: Record<string, LucideIconType> = {
  Workflow, Layers, Lightbulb, Database, Import, Cpu, ShieldAlert,
  Settings, FileCode, BookOpen, Download, Copy, X, Activity,
  Check, Play, Search, Plus, Trash2, Edit3, RefreshCw, AlertTriangle,
  GitBranch, Zap, BarChart3, Eye, Link, Unlink, MessageSquare,
  ChevronLeft, ChevronRight, ChevronUp, ChevronDown, Table, Wifi, Terminal,
  // PMO-data10: 'data-assets' tab
  Package, FolderTree, FolderOpen, Save, ExternalLink, GitCommit, Loader2,
  // 动态名面：Modals 测试结果三元式（仅以字符串出现，静态扫描不可见）
  CheckCircle, XCircle, Cloud, FolderGit, Globe, HardDrive,
  // #142/B4-3e-66（D-B4-95）：helpers.getSourceIcon() 不再自带私表，改为逐枚读
  //   CONNECTION_TYPES.iconName 单源。本表是字符串名→组件的唯一桥，19 枚图标名必须
  //   逐枚在位（前端门 b4ConnTypeIconChannel.spec.ts 会对账 import 面与本表键面）。
  DatabaseZap, DatabaseBackup, Server, Landmark, KeyRound, Gauge,
  CloudCog, Boxes, FileSpreadsheet, Radio, Leaf, CircleQuestionMark,
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

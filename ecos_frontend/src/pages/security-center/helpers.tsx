/**
 * SecurityCenter 共享 helpers（LucideIcon wrapper + 图表常量）
 * @license Apache-2.0
 */
import React from 'react';
import {
  Activity, ArrowRight, BookOpen, CheckSquare, ChevronRight, ClipboardList,
  Cpu, Database, Download, EyeOff, Filter, Flame, FolderGit, Globe, Info,
  Lock, PieChart as PieChartIcon, Play, Plus, RefreshCw, Settings, Shield,
  ShieldAlert, ShieldCheck, Tag, TrendingUp, UserPlus, Users, Workflow, X, Zap,
  HelpCircle
} from 'lucide-react';

/** LucideIcon wrapper — 字符串名 → lucide-react 原生组件 */
export function LucideIcon({ name, className = '', size = 16 }: { name: string; className?: string; size?: number }) {
  const icons: Record<string, any> = {
    Activity, ArrowRight, BookOpen, CheckSquare, ChevronRight, ClipboardList,
    Cpu, Database, Download, EyeOff, Filter, Flame, FolderGit, Globe, Info,
    Lock, 'PieChart': PieChartIcon, Play, Plus, RefreshCw, Settings, Shield,
    ShieldAlert, ShieldCheck, Tag, TrendingUp, UserPlus, Users, Workflow, X, Zap
  };
  const IconComponent = icons[name] || HelpCircle;
  return <IconComponent className={className} size={size} />;
}

/** Recharts 饼图色板 */
export const CHART_COLORS = ['#10b981', '#ef4444', '#f59e0b', '#3b82f6'];

/** 概览仪表盘饼图数据 */
export const pieData = [
  { name: '通过 (GRANTED)', value: 145 },
  { name: '拦截 (DENIED)', value: 34 },
  { name: '审计预警 (WARN)', value: 12 }
];

/** 安全评估趋势折线图数据 */
export const evaluationTrend = [
  { name: '06-25', count: 120, granted: 105, denied: 15 },
  { name: '06-26', count: 145, granted: 125, denied: 20 },
  { name: '06-27', count: 190, granted: 165, denied: 25 },
  { name: '06-28', count: 135, granted: 110, denied: 25 },
  { name: '06-29', count: 155, granted: 135, denied: 20 },
  { name: '06-30', count: 210, granted: 180, denied: 30 },
  { name: '07-01', count: 185, granted: 155, denied: 30 }
];

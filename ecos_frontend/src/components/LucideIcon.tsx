/**
 * LucideIcon — 字符串图标名的<b>唯一</b>注册表与解析组件（#142/B4-3e-76 三枚过渡桥下线）。
 *
 * <p><b>为什么这里还留一层</b>：仓里并非所有图标调用点都持有稳定的字面量——数据面
 * （本体对象类型表、数据源类型面、阶段/Tab 定义表、Scenario Wizard 步骤表）按约定以
 * <b>字符串</b>携带图标名（显示元数据面，与「只增不改」契约族同口径）。这类<b>动态通道</b>
 * 必须经一个注册表把字符串解析成组件，否则就是「字符串名→组件」的私表回潮（回归
 * D-B4-95 病灶）。本文件是全仓「字符串→图标组件」解析点的<b>单源属主</b>：
 * 历史散落的三枚 {@code LucideIcon.tsx} 过渡桥（{@code components/}、
 * {@code business-workbench/}、{@code data-workbench/}）之 iconMap 已全部下线并收敛至此。</p>
 *
 * <p><b>使用规约</b>：
 * <ul>
 *   <li>持有<b>静态字面量</b>的调用点一律直接 {@code import { X } from 'lucide-react'}
 *       写组件标签（前端铁律：图标仅走 lucide-react 原生导入；本文件的 {@link Icon}
 *       不承接静态调用点，防止桥回潮）；</li>
 *   <li>仅有<b>字符串名</b>的动态调用点（{@code name={x.icon}}、{@code name={cond ? 'A':'B'}}
 *       且两侧为变量/表达式、{@code name={map[key]}} 等）一律 {@code {IconName ? <IconName .../> : null}}
 *       本地三元，或经 {@link Icon} 组件渲染；</li>
 *   <li>数据面新增字符串图标名时<b>必须同步加入</b> {@link ICON_NAMES}；未注册的名称
 *       控制台 {@code console.warn} 后回落 {@link HelpCircle}（与旧全局桥逐桥对账后的
 *       诊断型回落读数一致）。</li>
 * </ul></p>
 *
 * <p><b>回落语义（三枚旧桥逐桥对账后定档）</b>：全局桥回落 {@code HelpCircle}、
 * 业务台桥回落 {@code HelpCircle}、数据台桥回落 {@code AlertTriangle}。数据源类型面
 * 19 枚 + 未解析名经本注册表解析后<b>全枚在位</b>（门 {@code b4ConnTypeIconChannel.spec.ts}
 * ③/④/⑤ 对账），故该域用户可见读数不变；其它域诊断型回落按「全局桥读数 HelpCircle」归一。
 * 静态调用点已全部改直连具名导入（{@code <X size={} className={}/>}），不再经本组件。</p>
 *
 * <p><b>注册表基线</b>：字面量超集 ∩ lucide-react 运行时导出面，实测 <b>130 枚</b>，
 * 本表逐枚在位。门 {@code b4ConnTypeIconChannel.spec.ts} ③ 逐枚对账 import 面与本表键面。</p>
 *
 * @license Apache-2.0
 */
import React from 'react';
import {
  Activity,
  AlertOctagon,
  AlertTriangle,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  BarChart3,
  BellRing,
  BookOpen,
  Bookmark,
  Bot,
  Box,
  Boxes,
  Brain,
  BrainCircuit,
  Briefcase,
  Building2,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleDot,
  CircleQuestionMark,
  ClipboardCheck,
  Clock,
  Cloud,
  CloudCog,
  CloudRain,
  Code,
  Compass,
  Cpu,
  Database,
  DatabaseBackup,
  DatabaseZap,
  Delete,
  Download,
  Edit,
  EyeOff,
  FileCheck,
  FileEdit,
  FileQuestion,
  FileSpreadsheet,
  FileText,
  FlaskConical,
  FolderGit,
  FolderTree,
  Fuel,
  FunctionSquare,
  Gauge,
  GitBranch,
  GitCompareArrows,
  Globe,
  Goal,
  HardDrive,
  Hash,
  Heart,
  HeartPulse,
  HelpCircle,
  History,
  Home,
  Inbox,
  Info,
  Key,
  KeyRound,
  Landmark,
  Layers,
  Layout,
  LayoutDashboard,
  LayoutGrid,
  Leaf,
  Lightbulb,
  Link,
  Link2,
  ListChecks,
  MessageSquare,
  MessagesSquare,
  Navigation,
  Network,
  Package,
  Palette,
  Pencil,
  Plane,
  PlaneLanding,
  Play,
  PlayCircle,
  Plus,
  Radio,
  Redo2,
  RefreshCcw,
  RefreshCw,
  Rocket,
  RotateCw,
  Route,
  Save,
  Search,
  SearchX,
  Send,
  Server,
  Settings,
  Settings2,
  Share2,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sliders,
  SlidersHorizontal,
  Stamp,
  TableProperties,
  Tag,
  Target,
  Terminal,
  Text,
  Trash2,
  TrendingUp,
  Undo2,
  UploadCloud,
  User,
  UserCheck,
  UserCog,
  UserPlus,
  UserSquare2,
  Users,
  Workflow,
  Wrench,
  XCircle,
  Zap,
  type LucideIcon as LucideIconType,
} from 'lucide-react';

/**
 * 字符串图标名 → 图标组件的<b>唯一</b>注册表。
 * 数据面新增字符串图标名必须同步在此登记（门 {@code b4ConnTypeIconChannel.spec.ts} ③ 对账）。
 */
export const ICON_NAMES: Record<string, LucideIconType> = {
  Activity,
  AlertOctagon,
  AlertTriangle,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  BarChart3,
  BellRing,
  BookOpen,
  Bookmark,
  Bot,
  Box,
  Boxes,
  Brain,
  BrainCircuit,
  Briefcase,
  Building2,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleDot,
  CircleQuestionMark,
  ClipboardCheck,
  Clock,
  Cloud,
  CloudCog,
  CloudRain,
  Code,
  Compass,
  Cpu,
  Database,
  DatabaseBackup,
  DatabaseZap,
  Delete,
  Download,
  Edit,
  EyeOff,
  FileCheck,
  FileEdit,
  FileQuestion,
  FileSpreadsheet,
  FileText,
  FlaskConical,
  FolderGit,
  FolderTree,
  Fuel,
  FunctionSquare,
  Gauge,
  GitBranch,
  GitCompareArrows,
  Globe,
  Goal,
  HardDrive,
  Hash,
  Heart,
  HeartPulse,
  History,
  Home,
  Inbox,
  Info,
  Key,
  KeyRound,
  Landmark,
  Layers,
  Layout,
  LayoutDashboard,
  LayoutGrid,
  Leaf,
  Lightbulb,
  Link,
  Link2,
  ListChecks,
  MessageSquare,
  MessagesSquare,
  Navigation,
  Network,
  Package,
  Palette,
  Pencil,
  Plane,
  PlaneLanding,
  Play,
  PlayCircle,
  Plus,
  Radio,
  Redo2,
  RefreshCcw,
  RefreshCw,
  Rocket,
  RotateCw,
  Route,
  Save,
  Search,
  SearchX,
  Send,
  Server,
  Settings,
  Settings2,
  Share2,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sliders,
  SlidersHorizontal,
  Stamp,
  TableProperties,
  Tag,
  Target,
  Terminal,
  Text,
  Trash2,
  TrendingUp,
  Undo2,
  UploadCloud,
  User,
  UserCheck,
  UserCog,
  UserPlus,
  UserSquare2,
  Users,
  Workflow,
  Wrench,
  XCircle,
  Zap,
};

interface IconProps {
  /** 图标名字符串（数据面动态通道的输入），必须命中 {@link ICON_NAMES}。 */
  name: string;
  size?: number;
  className?: string;
}

/**
 * <b>动态通道</b>图标解析组件：{@code <Icon name={x.icon} .../>}。
 * 仅在数据面/映射表按约定以字符串携带图标名时使用；静态调用点必须直连具名导入
 * {@code import { X } from 'lucide-react'} + {@code <X size={...} />}。
 * 未注册名称 {@code console.warn} 后回落 {@link HelpCircle}（诊断型，与旧全局桥读数一致）。
 */
export function Icon({ name, size = 14, className }: IconProps) {
  const IconComponent = ICON_NAMES[name];
  if (!IconComponent) {
    console.warn(`[Icon] unknown icon: "${name}"（新增数据面图标名须同步登记 ICON_NAMES）`);
    return <HelpCircle size={size} className={className} />;
  }
  return <IconComponent size={size} className={className} />;
}

export default Icon;

/**
 * SidebarDynamicIcon — 动态 Lucide 图标解析（Sidebar 内部私有实现）
 *
 * 从 `components/ontology/Sidebar.tsx` 的局部 `DynamicIcon` 原样抽取
 * （H6-T4 组件行数治理），映射规则与默认尺寸保持不变。
 * 消费端以 `import DynamicIcon from './SidebarDynamicIcon'` 引入，保持 JSX 标签名不变。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */
import React from 'react';
import * as LucideIcons from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface DynamicIconProps {
  name: string;
  size?: number;
  className?: string;
}

export default function SidebarDynamicIcon({ name, size = 14, className }: DynamicIconProps) {
  const IconComponent = (LucideIcons[name as keyof typeof LucideIcons] || LucideIcons.HelpCircle) as LucideIcon;
  return <IconComponent size={size} className={className} />;
}

/**
 * DynamicIcon — 动态 Lucide 图标解析组件
 *
 * 将字符串图标名动态映射为 lucide-react 原生组件。
 * 本体工作台 5 个视图文件共享此组件，避免重复定义。
 *
 * @license Apache-2.0
 */
import React from 'react';
import * as LucideIcons from 'lucide-react';

interface DynamicIconProps {
  name: string;
  size?: number;
  className?: string;
}

export default function DynamicIcon({ name, size = 14, className }: DynamicIconProps) {
  const IconComponent = (LucideIcons as any)[name] || LucideIcons.HelpCircle;
  return <IconComponent size={size} className={className} />;
}

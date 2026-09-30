/**
 * logic-canvas 图标解析组件 — 由 LogicView.tsx 机械抽取（H6-T4），实现与原文逐行一致。
 * 通过 lucide-react 命名空间按字符串名取图标，缺失时回退 HelpCircle。
 * @license Apache-2.0
 */
import * as Icons from 'lucide-react';

export const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

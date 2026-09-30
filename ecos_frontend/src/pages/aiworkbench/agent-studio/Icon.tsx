/**
 * Icon — lucide-react 动态图标包装组件。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），实现与原文逐行一致。
 * @license Apache-2.0
 */
import * as Icons from 'lucide-react';

export const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export default Icon;

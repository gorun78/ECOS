/**
 * Local LucideIcon shim — backed by lucide-react.
 * Supports both static (`name="Bot"`) and dynamic (`name={tab.icon}`) icon names.
 */
import React from 'react';
import * as Icons from 'lucide-react';

interface LucideIconProps {
  name: string;
  className?: string;
  size?: number;
}

export default function LucideIcon({ name, className = '', size = 16 }: LucideIconProps) {
  const IconComponent = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <IconComponent className={className} size={size} />;
}

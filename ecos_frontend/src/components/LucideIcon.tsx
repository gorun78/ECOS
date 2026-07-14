import * as LucideIcons from 'lucide-react';
export default function LucideIcon({ name, size = 14, className }: { name: string; size?: number; className?: string }) {
  const IconComponent = (LucideIcons as any)[name] || LucideIcons.HelpCircle;
  return <IconComponent size={size} className={className} />;
}

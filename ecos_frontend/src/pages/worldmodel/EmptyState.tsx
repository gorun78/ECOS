import React from "react";

// ── Empty State ──────────────────────────────
export default function EmptyState({ icon: Icon, msg }: { icon: React.FC<{ className?: string }>; msg: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center opacity-50">
      <Icon className="w-12 h-12 mb-3 opacity-30" />
      <p className="text-sm">{msg}</p>
    </div>
  );
}

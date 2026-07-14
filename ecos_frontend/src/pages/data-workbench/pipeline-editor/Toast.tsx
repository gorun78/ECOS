/**
 * Toast — inline notification for PipelineFlowEditor
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { X, CheckCircle, AlertCircle } from 'lucide-react';

// ─── Toast notification ───────────────────────────────────

const Toast: React.FC<{
  type: 'success' | 'error' | 'info';
  message: string;
  onClose: () => void;
}> = ({ type, message, onClose }) => {
  const bgColor =
    type === 'success'
      ? 'bg-emerald-600'
      : type === 'error'
        ? 'bg-red-600'
        : 'bg-blue-600';

  return (
    <div className="fixed top-4 right-4 z-50 animate-in slide-in-from-top-2">
      <div
        className={`flex items-center gap-2 px-4 py-2.5 ${bgColor} text-white text-sm rounded-lg shadow-lg`}
      >
        {type === 'success' && <CheckCircle size={16} />}
        {type === 'error' && <AlertCircle size={16} />}
        {type === 'info' && <AlertCircle size={16} />}
        <span>{message}</span>
        <button onClick={onClose} className="ml-2 opacity-70 hover:opacity-100">
          <X size={14} />
        </button>
      </div>
    </div>
  );
};


export default Toast;

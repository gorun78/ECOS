/**
 * EmptyState — 空状态占位（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import React from "react";
import { Plus, Upload } from "lucide-react";

const EmptyState: React.FC<{
  icon: React.FC<any>;
  title: string;
  description: string;
  onCreate?: () => void;
  onImport?: () => void;
  createLabel?: string;
  importLabel?: string;
}> = ({ icon: Icon, title, description, onCreate, onImport, createLabel, importLabel }) => (
  <div className="flex flex-col items-center justify-center py-16 text-center">
    <Icon className="w-16 h-16 opacity-15 mb-4" />
    <h3 className="text-sm font-semibold opacity-40 mb-1">{title}</h3>
    <p className="text-xs opacity-25 mb-4 max-w-xs">{description}</p>
    <div className="flex gap-2">
      {onCreate && (
        <button onClick={onCreate} className="px-4 py-2 rounded text-xs font-medium text-white bg-indigo-500 hover:bg-indigo-600 flex items-center gap-1.5">
          <Plus size={14} /> {createLabel || "新建"}
        </button>
      )}
      {onImport && (
        <button onClick={onImport} className="px-4 py-2 rounded text-xs border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5 flex items-center gap-1.5">
          <Upload size={14} /> {importLabel || "导入"}
        </button>
      )}
    </div>
  </div>
);

export default EmptyState;

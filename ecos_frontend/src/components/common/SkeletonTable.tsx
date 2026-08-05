/**
 * SkeletonTable — 表格骨架屏加载占位组件
 * @license Apache-2.0
 */

import React from "react";

interface SkeletonTableProps {
  rows?: number;
  columns?: number;
}

export default function SkeletonTable({ rows = 5, columns = 6 }: SkeletonTableProps) {
  return (
    <div className="overflow-auto flex-1">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            {Array.from({ length: columns }).map((_, i) => (
              <th
                key={i}
                className="px-3 py-2 border-b border-gray-200 dark:border-gray-700/30"
              >
                <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse w-20" />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, i) => (
            <tr key={i}>
              {Array.from({ length: columns }).map((_, j) => (
                <td
                  key={j}
                  className="px-3 py-3 border-b border-gray-100 dark:border-gray-700/20"
                >
                  <div
                    className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
                    style={{ width: `${50 + Math.random() * 45}%` }}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

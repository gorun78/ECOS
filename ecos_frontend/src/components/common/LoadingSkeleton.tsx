/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { useTheme } from "../ThemeContext";

export interface LoadingSkeletonProps {
  /** Number of rows/lines to render. Default: 5 */
  rows?: number;
  /** Layout variant. Default: "table" */
  variant?: "table" | "card" | "list";
  /** Optional CSS class name */
  className?: string;
}

function SkeletonBar({ className = "" }: { className?: string }) {
  return (
    <div
      className={`animate-pulse rounded bg-slate-200 dark:bg-slate-700 ${className}`}
    />
  );
}

export default function LoadingSkeleton({
  rows = 5,
  variant = "table",
  className = "",
}: LoadingSkeletonProps) {
  const { styles } = useTheme();

  if (variant === "list") {
    return (
      <div className={`space-y-3 ${className}`}>
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="flex items-center gap-3">
            <SkeletonBar className="w-8 h-8 rounded-full shrink-0" />
            <div className="flex-1 space-y-1.5">
              <SkeletonBar className="h-3 w-3/5" />
              <SkeletonBar className="h-2 w-2/5" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (variant === "card") {
    return (
      <div className={`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 ${className}`}>
        {Array.from({ length: rows }).map((_, i) => (
          <div
            key={i}
            className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-5 space-y-3`}
          >
            {/* Title bar */}
            <div className="flex items-center justify-between">
              <SkeletonBar className="h-4 w-2/5" />
              <SkeletonBar className="h-3 w-12 rounded-md" />
            </div>
            {/* Description lines */}
            <div className="space-y-1.5">
              <SkeletonBar className="h-2.5 w-full" />
              <SkeletonBar className="h-2.5 w-4/5" />
            </div>
            {/* Meta row */}
            <div className="flex gap-3 pt-2">
              <SkeletonBar className="h-2 w-16" />
              <SkeletonBar className="h-2 w-12" />
              <SkeletonBar className="h-2 w-14" />
            </div>
            {/* Progress bar */}
            <SkeletonBar className="h-1.5 w-full rounded-full" />
          </div>
        ))}
      </div>
    );
  }

  // variant === "table"
  return (
    <div className={`space-y-0 ${className}`}>
      {/* Header */}
      <div className={`flex items-center gap-4 px-3 py-2.5 border-b ${styles.cardBorder}`}>
        <SkeletonBar className="h-3 flex-1" />
        <SkeletonBar className="h-3 flex-1" />
        <SkeletonBar className="h-3 flex-1" />
        <SkeletonBar className="h-3 w-20" />
      </div>

      {/* Rows */}
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className={`flex items-center gap-4 px-3 py-3 border-b ${styles.cardBorder}`}
        >
          <SkeletonBar className="h-3 flex-1" />
          <SkeletonBar className="h-3 flex-1" />
          <SkeletonBar className="h-3 flex-1" />
          <SkeletonBar className="h-3 w-20" />
        </div>
      ))}
    </div>
  );
}

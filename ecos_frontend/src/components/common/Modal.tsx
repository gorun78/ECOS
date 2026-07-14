/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useCallback } from "react";
import { X } from "lucide-react";
import { useTheme } from "../ThemeContext";

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  /** If true, clicking the overlay closes the modal. Default: true */
  closeOnOverlay?: boolean;
  /** If true, pressing Escape closes the modal. Default: true */
  closeOnEscape?: boolean;
  className?: string;
  size?: "sm" | "md" | "lg" | "xl" | "full";
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  /** If true, shows loading spinner on confirm button */
  confirmLoading?: boolean;
  /** Disable the confirm button */
  confirmDisabled?: boolean;
}

const SIZE_MAP: Record<string, string> = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
  xl: "max-w-xl",
  full: "max-w-3xl",
};

export default function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  closeOnOverlay = true,
  closeOnEscape = true,
  className = "",
  size = "md",
  confirmText,
  cancelText,
  onConfirm,
  onCancel,
  confirmLoading = false,
  confirmDisabled = false,
}: ModalProps) {
  const { styles } = useTheme();

  const handleEscape = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === "Escape" && closeOnEscape) {
        onClose();
      }
    },
    [closeOnEscape, onClose]
  );

  useEffect(() => {
    if (isOpen && closeOnEscape) {
      window.addEventListener("keydown", handleEscape);
      return () => window.removeEventListener("keydown", handleEscape);
    }
  }, [isOpen, closeOnEscape, handleEscape]);

  // Lock body scroll when open
  useEffect(() => {
    if (isOpen) {
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.body.style.overflow = prev;
      };
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const renderFooter = () => {
    // If footer slot is provided, use it
    if (footer !== undefined) return footer;

    // If confirmText or cancelText is set, render default footer
    if (!confirmText && !cancelText) return null;

    return (
      <div className="flex items-center justify-end gap-2 pt-4">
        {cancelText && (
          <button
            onClick={() => {
              onCancel?.();
              onClose();
            }}
            className={`px-4 py-1.5 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer transition`}
          >
            {cancelText}
          </button>
        )}
        {confirmText && (
          <button
            disabled={confirmDisabled || confirmLoading}
            onClick={() => onConfirm?.()}
            className={`px-4 py-1.5 text-xs font-bold rounded-lg cursor-pointer transition flex items-center gap-1.5 ${
              confirmDisabled
                ? "bg-slate-300 text-slate-500 cursor-not-allowed dark:bg-slate-700"
                : `${styles.accentBg} ${styles.accentText} hover:opacity-90`
            }`}
          >
            {confirmLoading && (
              <span className="inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin" />
            )}
            {confirmText}
          </button>
        )}
      </div>
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Overlay */}
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-xs"
        onClick={closeOnOverlay ? onClose : undefined}
      />

      {/* Panel */}
      <div
        className={`relative z-10 w-full ${SIZE_MAP[size]} rounded-xl border ${styles.cardBorder} ${styles.cardBg} shadow-2xl animate-in fade-in zoom-in-95 duration-150 max-h-[85vh] flex flex-col ${className}`}
      >
        {/* Header */}
        {title && (
          <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder} shrink-0`}>
            <h2 className={`text-sm font-bold ${styles.cardText}`}>{title}</h2>
            <button
              onClick={onClose}
              className={`p-1 rounded-md ${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10 cursor-pointer transition`}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* If no title but close button still needed */}
        {!title && (
          <button
            onClick={onClose}
            className={`absolute top-3 right-3 p-1 rounded-md ${styles.cardTextMuted} hover:bg-black/10 dark:hover:bg-white/10 cursor-pointer transition z-10`}
          >
            <X className="w-4 h-4" />
          </button>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4 scrollbar-thin">
          {children}
        </div>

        {/* Footer */}
        {renderFooter() && (
          <div className={`px-5 py-3 border-t ${styles.cardBorder} shrink-0`}>
            {renderFooter()}
          </div>
        )}
      </div>
    </div>
  );
}

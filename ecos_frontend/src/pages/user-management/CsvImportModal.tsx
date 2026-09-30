/**
 * CsvImportModal — CSV 批量导入用户对话框（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import React, { useRef, useState } from "react";
import { X, Upload, FileSpreadsheet } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

interface CsvImportModalProps {
  onImport: (users: Record<string, string>[]) => Promise<void>;
  onClose: () => void;
}

export default function CsvImportModal({ onImport, onClose }: CsvImportModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";
  const fileRef = useRef<HTMLInputElement>(null);

  const [preview, setPreview] = useState<{ headers: string[]; rows: Record<string, string>[] } | null>(null);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState("");

  function parseCSV(text: string) {
    const lines = text.trim().split(/\r?\n/);
    if (lines.length < 2) {
      setError(isZh ? "CSV文件至少需要标题行和一行数据" : "CSV needs at least header + 1 data row");
      return;
    }
    const headers = lines[0].split(",").map(h => h.trim().replace(/^"|"$/g, ""));
    const rows = lines.slice(1).map(line => {
      const vals = line.split(",").map(v => v.trim().replace(/^"|"$/g, ""));
      const row: Record<string, string> = {};
      headers.forEach((h, i) => { row[h] = vals[i] || ""; });
      return row;
    });
    setPreview({ headers, rows });
    setError("");
  }

  function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => parseCSV(reader.result as string);
    reader.readAsText(file);
  }

  async function handleImport() {
    if (!preview || preview.rows.length === 0) return;
    setImporting(true);
    setError("");
    try {
      await onImport(preview.rows);
      onClose();
    } catch (e: any) {
      setError(e.message || "Import failed");
    } finally {
      setImporting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-2xl max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            <FileSpreadsheet className="w-4 h-4 inline mr-1.5" />
            {isZh ? "CSV 批量导入用户" : "CSV Batch Import Users"}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        {error && (
          <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>
        )}

        {!preview ? (
          <div className="text-center py-8">
            <Upload className="w-10 h-10 opacity-20 mx-auto mb-3" />
            <p className={`text-sm mb-3 ${styles.cardTextMuted}`}>
              {isZh ? "选择 CSV 文件（需包含标题行：username,realName,email,phone,orgId）" : "Select CSV file (header row required: username,realName,email,phone,orgId)"}
            </p>
            <input ref={fileRef} type="file" accept=".csv" onChange={handleFile} className="hidden" />
            <button
              onClick={() => fileRef.current?.click()}
              className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}
            >
              {isZh ? "选择文件" : "Choose File"}
            </button>
          </div>
        ) : (
          <>
            <div className={`text-xs mb-2 ${styles.cardTextMuted}`}>
              {isZh ? `预览 ${preview.rows.length} 条记录` : `Preview ${preview.rows.length} records`}
            </div>
            <div className="overflow-auto max-h-64 border rounded mb-3">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-gray-50 dark:bg-gray-800/50">
                    {preview.headers.map(h => (
                      <th key={h} className="text-left px-2 py-1.5 font-semibold opacity-70 border-b">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.slice(0, 20).map((row, i) => (
                    <tr key={i} className="border-b border-gray-100 dark:border-gray-700/20">
                      {preview.headers.map(h => (
                        <td key={h} className="px-2 py-1.5 opacity-70">{row[h] || "-"}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setPreview(null)} className={`px-3 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
                {isZh ? "重新选择" : "Reselect"}
              </button>
              <button onClick={onClose} className={`px-3 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
                {isZh ? "取消" : "Cancel"}
              </button>
              <button onClick={handleImport} disabled={importing}
                className={`px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
                {importing ? (isZh ? "导入中…" : "Importing…") : (isZh ? "确认导入" : "Confirm Import")}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

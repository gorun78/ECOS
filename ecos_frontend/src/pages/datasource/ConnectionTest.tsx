/**
 * ConnectionTest — 连接测试组件
 * 调用 testRawConnection API，显示成功/失败+错误详情
 *
 * @license Apache-2.0
 */

import React, { useState } from "react";
import {
  CheckCircle, XCircle, Loader2, Zap
} from "lucide-react";
import { testRawConnection } from "../../api";
import { useLanguage } from "../../components/LanguageContext";

interface ConnectionTestProps {
  jdbcUrl: string;
  username: string;
  password: string;
  datasourceType: string;
}

interface TestResult {
  success: boolean;
  message: string;
}

export default function ConnectionTest({
  jdbcUrl,
  username,
  password,
  datasourceType,
}: ConnectionTestProps) {
  const { locale } = useLanguage();
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<TestResult | null>(null);

  const handleTest = async () => {
    if (!jdbcUrl.trim()) return;
    setTesting(true);
    setResult(null);
    try {
      const connectionConfig = JSON.stringify({
        jdbcUrl: jdbcUrl.trim(),
        username: username.trim(),
        password,
      });
      const r = await testRawConnection({
        datasourceType: datasourceType || "JDBC",
        connectionConfig,
      });
      setResult({
        success: r.success,
        message: r.success
          ? (locale === "zh" ? "连接测试成功！" : "Connection test successful!")
          : (r.message || (locale === "zh" ? "连接测试失败" : "Connection test failed")),
      });
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Test failed";
      setResult({ success: false, message: msg });
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="space-y-3">
      <button
        onClick={handleTest}
        disabled={testing || !jdbcUrl.trim()}
        className="w-full px-4 py-2.5 text-sm font-semibold rounded-lg border border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100 disabled:opacity-50 transition flex items-center justify-center gap-2 cursor-pointer"
      >
        {testing ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            {locale === "zh" ? "测试中..." : "Testing..."}
          </>
        ) : (
          <>
            <Zap className="w-4 h-4" />
            {locale === "zh" ? "测试连接" : "Test Connection"}
          </>
        )}
      </button>

      {result && (
        <div
          className={`rounded-lg p-3 flex items-start gap-2 text-xs ${
            result.success
              ? "bg-green-50 text-green-700 border border-green-200"
              : "bg-red-50 text-red-700 border border-red-200"
          }`}
        >
          {result.success ? (
            <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" />
          ) : (
            <XCircle className="w-4 h-4 shrink-0 mt-0.5" />
          )}
          <div className="flex-1 min-w-0">
            <p className="font-semibold">
              {result.success
                ? locale === "zh"
                  ? "成功"
                  : "Success"
                : locale === "zh"
                  ? "失败"
                  : "Failed"}
            </p>
            <p className="mt-0.5 whitespace-pre-wrap break-all">{result.message}</p>
          </div>
        </div>
      )}

      {/* Hint for empty JDBC URL */}
      {!jdbcUrl.trim() && (
        <p className="text-[10px] text-amber-600">
          {locale === "zh"
            ? "请先在 Step 1 中输入 JDBC URL"
            : "Please enter JDBC URL in Step 1 first"}
        </p>
      )}
    </div>
  );
}

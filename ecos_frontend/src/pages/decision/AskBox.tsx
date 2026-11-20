/**
 * AskBox — 提问框 + 快捷提示（原型 v2 区块 2）。
 *
 * 行为纪律：提交只做**意图识别并展开对应任务卡**，不直接执行。
 * 四类任务的运行都需要额外入参（预测需历史序列、对比需两个 runId、
 * 风险推演需 domain/variable），直接跑只会在多数情况下因「没给参数」失败，
 * 把真实契约缺口伪装成故障。chip 点击仅填充文本并聚焦，不自动提交。
 *
 * @license Apache-2.0
 */
import React, { useRef, useState } from "react";
import { Search } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import type { TaskKind } from "./decisionTypes";

const INTENT_RULES: Array<{ re: RegExp; kind: TaskKind }> = [
  { re: /风险|异常|隐患|risk/i, kind: "risk" },
  { re: /比较|对比|方案|compare/i, kind: "compare" },
  { re: /行动|执行|跟踪|action/i, kind: "actions" },
];

/** 意图匹配：命中则返回对应任务，未命中落 forecast（与原型一致）。 */
export function matchIntent(text: string): TaskKind {
  for (const rule of INTENT_RULES) {
    if (rule.re.test(text)) return rule.kind;
  }
  return "forecast";
}

const CHIPS = ["forecast", "risk", "compare"] as const;

export interface AskBoxProps {
  onIntent: (kind: TaskKind) => void;
}

export default function AskBox({ onIntent }: AskBoxProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [text, setText] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const submit = () => {
    const q = text.trim();
    if (!q) {
      showToastGlobal("warning", t("platform.decision.ask.empty"));
      inputRef.current?.focus();
      return;
    }
    onIntent(matchIntent(q));
  };

  return (
    <section className={`rounded-2xl border p-4 md:p-5 ${styles.cardBg} ${styles.cardBorder}`}>
      <label htmlFor="decision-question" className={`block text-xs font-semibold mb-2 ${styles.cardText}`}>
        {t("platform.decision.ask.label")}
      </label>
      <div className="flex flex-col sm:flex-row gap-2">
        <div className="relative flex-1">
          <Search size={16} className={`absolute left-3 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
          <input
            id="decision-question"
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submit();
            }}
            placeholder={t("platform.decision.ask.placeholder")}
            autoComplete="off"
            className={`w-full h-11 pl-10 pr-3 rounded-xl border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} ${styles.placeholderText}`}
          />
        </div>
        <button
          type="button"
          onClick={submit}
          className={`h-11 px-5 rounded-xl text-sm font-semibold transition cursor-pointer ${styles.accentBg} ${styles.accentSolidText}`}
        >
          {t("platform.decision.ask.submit")}
        </button>
      </div>
      <div className="flex flex-wrap gap-2 mt-3">
        {CHIPS.map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => {
              setText(t(`platform.decision.chip.${k}`));
              inputRef.current?.focus();
            }}
            className={`rounded-full border px-3 py-1 text-xs cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted}`}
          >
            {t(`platform.decision.chip.${k}`)}
          </button>
        ))}
      </div>
    </section>
  );
}

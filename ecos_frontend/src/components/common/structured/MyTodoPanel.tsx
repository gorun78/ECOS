/**
 * MyTodoPanel — 「待我处理」面板（详设-08 §2.2「待办集中」/ §2.1 P01 右侧 / §2.3「等待用户」行）。
 *
 * 数据契约：items = Policy Guard `require_approval` 队列条目（01 册 evaluate 四态
 * allow / deny / require_approval / allow_with_mask 的服务端裁决结果），**非前端推断**；
 * 组件只渲染 require_approval 条目，其余三态仅计入汇总计数，不做本地改判。
 * 醒目呈现「等待你的确认」+ 截止提示；无待办时显式空态文案。
 * @license Apache-2.0
 */

import React from "react";
import { ListTodo } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";
import Badge from "../Badge";

/** 01 册 evaluate 四态 */
export type PolicyDecisionState = "allow" | "deny" | "require_approval" | "allow_with_mask";

/** 待办条目（队列消费结果投影） */
export interface MyTodoItem {
  id: string;
  /** 事项标题（业务语言，来自引擎） */
  title: string;
  /** 服务端裁决态 */
  state: PolicyDecisionState;
  /** 截止时间提示（引擎返回的原文，如 ISO 时间或业务描述）；缺失显示「未设定截止时间」 */
  deadline?: string | null;
  /** 关联任务（可选，引擎回传） */
  mission?: string | null;
}

export interface MyTodoPanelProps {
  /** 队列全量条目（含四态）；面板只展开 require_approval 条目 */
  items: MyTodoItem[];
  /** 打开单条待办（跳转 P01 对话流 / P02 详情由调用方决定） */
  onOpen?: (itemId: string) => void;
}

export default function MyTodoPanel({ items, onOpen }: MyTodoPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const approvalItems = items.filter((item) => item.state === "require_approval");

  return (
    <section
      className={`rounded-xl border p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}
      aria-label={t("common.todo.title")}
    >
      <header className="flex items-center gap-2 flex-wrap">
        <ListTodo className={`w-5 h-5 ${styles.warningText}`} aria-hidden="true" />
        <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("common.todo.title")}</h3>
        {approvalItems.length > 0 && (
          <Badge variant="warning">
            {t("common.todo.awaiting")} · {approvalItems.length}
          </Badge>
        )}
      </header>

      {approvalItems.length === 0 ? (
        <p className={`text-xs ${styles.cardTextMuted}`}>{t("common.todo.empty")}</p>
      ) : (
        <ul className="space-y-2">
          {approvalItems.map((item) => (
            <li
              key={item.id}
              className={`rounded-lg border p-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 ${styles.cardBorder}`}
            >
              <div className="space-y-1">
                <p className={`text-xs font-semibold ${styles.cardText}`}>{item.title}</p>
                <p className={`font-mono text-[10px] tracking-wider uppercase ${styles.warningText}`}>
                  {t("common.todo.awaiting")}
                </p>
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  {item.deadline ? t("common.todo.deadline", { time: item.deadline }) : t("common.todo.no_deadline")}
                </p>
              </div>
              {onOpen && (
                <Button variant="secondary" size="sm" onClick={() => onOpen(item.id)}>
                  {t("common.todo.open")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

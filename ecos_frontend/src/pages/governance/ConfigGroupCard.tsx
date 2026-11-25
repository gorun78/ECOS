/**
 * ConfigGroupCard — 一个 config_group 的配置卡片（桌面表格 / 移动卡片双形态）。
 *
 * 形态取「卡片 + 组头统计 + 行内主信息/说明两行」，解决原实现三处体验问题：
 *   1. 188 行铺开无层级 → 组可折叠，默认只展开首组（或搜索结果命中组）
 *   2. 四列平铺把「档位」这种恒为 all 的字段占成整列 → 档位降级为行内小注，
 *      主线让位给「键名 + 中文标签 + 说明 + 当前值」
 *   3. 消费态列恒显示「否」假信息 → 改为审计口真实数据，缺失时显式中性态
 *
 * 响应式：用 useMediaQuery('(max-width: 767px)') 切卡片态（§八 唯一允许断点原语）。
 * 颜色只走 useTheme() token；文案只走 t()；图标仅 lucide-react。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React from "react";
import { ChevronDown, ChevronRight, KeyRound } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useMediaQuery } from "../../hooks/useMediaQuery";
import ConfigValueCell from "./ConfigValueCell";
import {
  groupConsumedCount,
  valueKind,
  deltaState,
  maskSecret,
  type ConsumptionMap,
  type SysConfigGrouped,
  type SysConfigItem,
} from "./engineConfigModel";

interface ConfigGroupCardProps {
  group: SysConfigGrouped;
  consumption: ConsumptionMap;
  /** 消费态接口整体不可用时为 null → 一律渲染中性「不可用」，不猜「否」 */
  consumptionAvailable: boolean;
  expanded: boolean;
  onToggle: () => void;
}

export default function ConfigGroupCard({
  group,
  consumption,
  consumptionAvailable,
  expanded,
  onToggle,
}: ConfigGroupCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery("(max-width: 767px)");

  const consumed = groupConsumedCount(group.items, consumption);
  const overrides = group.items.filter((i) => deltaState(i) === "override").length;

  return (
    <section className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
      {/* 组头：整行可点折叠 */}
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        data-group={group.group}
        className={`w-full flex items-center gap-2 border-b px-3 py-2 text-left cursor-pointer hover:opacity-85 ${styles.cardBorder}`}
      >
        <span className={`shrink-0 ${styles.cardTextMuted}`}>
          {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </span>
        <span className="text-xs font-bold truncate">
          {t(`platform.governance.config.groupName.${group.group}`, group.group)}
        </span>
        <code className={`hidden sm:inline text-[10px] font-mono ${styles.cardTextMuted}`}>
          {group.group}
        </code>
        <span className="flex-1 min-w-2" />
        <span className={`shrink-0 text-[10px] font-mono ${styles.cardTextMuted}`}>
          {group.items.length}
        </span>
        {overrides > 0 && (
          <span
            className={`shrink-0 inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
            title={t("platform.governance.config.overrideHint")}
          >
            Δ{overrides}
          </span>
        )}
        {consumptionAvailable && consumed > 0 && (
          <span
            className={`shrink-0 inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${styles.successBg} ${styles.successText} ${styles.successBorder}`}
            title={t("platform.governance.config.consumed")}
          >
            {consumed}
          </span>
        )}
      </button>

      {expanded && (
        isMobile ? (
          <ul className={`divide-y ${styles.cardBorder}`}>
            {group.items.map((item) => (
              <MobileRow key={item.key} item={item} consumption={consumption[item.key]} consumptionAvailable={consumptionAvailable} />
            ))}
          </ul>
        ) : (
          <table className="w-full text-[11px] table-fixed">
            <colgroup>
              <col className="w-[42%]" />
              <col className="w-[26%]" />
              <col className="w-[24%]" />
              <col className="w-[8%]" />
            </colgroup>
            <thead>
              <tr className={`border-b text-left ${styles.cardBorder} ${styles.cardTextMuted}`}>
                <th className="px-3 py-1.5 font-bold uppercase text-[10px]">
                  {t("platform.governance.config.group")}
                </th>
                <th className="px-3 py-1.5 font-bold uppercase text-[10px]">
                  {t("platform.dictionary.value")}
                </th>
                <th className="px-3 py-1.5 font-bold uppercase text-[10px]">
                  {t("platform.governance.config.consumed")}
                </th>
                <th className="px-3 py-1.5 font-bold uppercase text-[10px]">
                  {t("platform.dictionary.type")}
                </th>
              </tr>
            </thead>
            <tbody>
              {group.items.map((item) => (
                <DesktopRow key={item.key} item={item} consumption={consumption[item.key]} consumptionAvailable={consumptionAvailable} />
              ))}
            </tbody>
          </table>
        )
      )}
    </section>
  );
}

/** 键名 + 中文标签 + 说明（桌面/移动共用） */
function Identity({ item }: { item: SysConfigItem }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const delta = deltaState(item);
  return (
    <div className="min-w-0 flex flex-col gap-0.5">
      <span className="inline-flex items-center gap-1.5 min-w-0">
        <KeyRound size={11} className={`shrink-0 ${styles.cardTextMuted}`} />
        <span className="font-mono font-bold truncate" title={item.key}>
          {item.key}
        </span>
        {delta === "override" && (
          <span
            className={`shrink-0 inline-block h-1.5 w-1.5 rounded-full ${styles.warningBg}`}
            title={t("platform.governance.config.overrideHint")}
          />
        )}
        {delta === "empty" && (
          <span
            className={`shrink-0 inline-block h-1.5 w-1.5 rounded-full ${styles.dangerBg}`}
            title={t("platform.governance.config.emptyValue")}
          />
        )}
      </span>
      <span className={`text-[10px] truncate ${styles.cardTextMuted}`} title={item.labelZh || item.label}>
        {item.labelZh || item.label || item.key}
      </span>
    </div>
  );
}

/** 说明 + 默认值（有才显示，避免空行占高） */
function Description({ item }: { item: SysConfigItem }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const hasDefault = (item.default_value ?? "") !== "";
  const desc = item.descriptionZh || item.description;
  // 后端个别行 description 与 label 逐字相同（如 audit_retention_days），重复展示是噪音
  const label = item.labelZh || item.label;
  const distinctDesc = desc && desc.trim() !== label.trim() ? desc : "";
  // 密文项的默认值同样是凭据：以明文列在说明里等于绕过打码（live 实测 minioadmin 曾直接外露）
  const defaultText = hasDefault
    ? valueKind(item) === "secret"
      ? maskSecret(item.default_value!) ?? ""
      : item.default_value!
    : "";
  if (!distinctDesc && !hasDefault) {
    return <span className={`text-[10px] ${styles.cardTextMuted}`}>{t("platform.governance.config.noDescription")}</span>;
  }
  return (
    <div className="min-w-0 flex flex-col gap-0.5">
      {distinctDesc && (
        <span className={`text-[10px] leading-relaxed line-clamp-2 ${styles.cardTextMuted}`} title={distinctDesc}>
          {distinctDesc}
        </span>
      )}
      {hasDefault && (
        <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
          {t("platform.governance.config.defaultValue")}: <span className="break-all">{defaultText}</span>
        </span>
      )}
    </div>
  );
}

/** 消费态徽标：三态严格区分（已消费 / 未检测到 / 不可用），绝不折叠成「否」 */
function ConsumptionBadge({
  consumption,
  available,
}: {
  consumption?: { consumed: boolean; consumedBy: string[] };
  available: boolean;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  if (!available) {
    return (
      <span className={`text-[10px] font-mono ${styles.cardTextMuted}`} title={t("platform.governance.config.auditUnavailable")}>
        —
      </span>
    );
  }
  if (consumption?.consumed) {
    return (
      <span
        className={`inline-flex flex-col gap-0.5 ${styles.successText}`}
        title={consumption.consumedBy.length > 0 ? `${t("platform.governance.config.consumedBy")}: ${consumption.consumedBy.join(", ")}` : undefined}
      >
        <span className="text-[10px] font-bold">{t("platform.governance.config.consumed")}</span>
        {consumption.consumedBy.length > 0 && (
          <span className={`text-[10px] font-mono truncate ${styles.cardTextMuted}`}>
            {consumption.consumedBy[0]}
          </span>
        )}
      </span>
    );
  }
  return (
    <span className={`text-[10px] ${styles.cardTextMuted}`}>
      {t("platform.governance.config.notConsumed")}
    </span>
  );
}

function DesktopRow({
  item,
  consumption,
  consumptionAvailable,
}: {
  item: SysConfigItem;
  consumption?: { consumed: boolean; consumedBy: string[] };
  consumptionAvailable: boolean;
}) {
  const { styles } = useTheme();
  const kind = valueKind(item);
  return (
    <tr className={`border-b last:border-b-0 align-top ${styles.cardBorder}`}>
      <td className="px-3 py-2">
        <Identity item={item} />
        <div className="mt-1">
          <Description item={item} />
        </div>
      </td>
      <td className="px-3 py-2">
        <ConfigValueCell item={item} kind={kind} />
      </td>
      <td className="px-3 py-2">
        <ConsumptionBadge consumption={consumption} available={consumptionAvailable} />
      </td>
      <td className={`px-3 py-2 font-mono text-[10px] ${styles.cardTextMuted}`}>{item.type}</td>
    </tr>
  );
}

function MobileRow({
  item,
  consumption,
  consumptionAvailable,
}: {
  item: SysConfigItem;
  consumption?: { consumed: boolean; consumedBy: string[] };
  consumptionAvailable: boolean;
}) {
  const { styles } = useTheme();
  const kind = valueKind(item);
  return (
    <li className="px-3 py-2.5 flex flex-col gap-1.5">
      <Identity item={item} />
      <div className="flex items-start justify-between gap-2">
        {/* 后端原始类型串属数据（非翻译文案），原样展示以保留大小写事实 */}
        <span className={`text-[10px] font-mono uppercase shrink-0 ${styles.cardTextMuted}`}>
          {item.type}
        </span>
        <span className="text-right min-w-0">
          <ConfigValueCell item={item} kind={kind} />
        </span>
      </div>
      <div className="flex items-start justify-between gap-2">
        <Description item={item} />
        <ConsumptionBadge consumption={consumption} available={consumptionAvailable} />
      </div>
    </li>
  );
}

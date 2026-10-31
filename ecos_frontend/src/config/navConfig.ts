/**
 * E.6 落点收口（#146ax，台账附录E E.6 第 1 件）：`src/config/navConfig.ts` 属主落点。
 *
 * <p>数据本体仍单源在 {@code src/navigation/navConfig.ts}（Sidebar、App、CommandPalette、
 * main 路由表等 90+ 消费方路径不变）。本文件是**再导出落点别名**：把附录E 要求的
 * `src/config/` 落点钉在位，不搬数据、不新建导航表。
 *
 * <p>判定不翻转登记：落点在位 ≠ 附录逐字合规终裁——消费面未变，见台账 v1.0.223 订正注。
 */
export * from "../navigation/navConfig";

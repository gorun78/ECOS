/**
 * E.6 落点收口（#146ax，台账附录E E.6 第 3 件）：`src/theme/*` 主题落点。
 *
 * <p>主题实现单源在 {@code src/components/ThemeContext.tsx}（ThemeId 四主题 /
 * ThemeProvider / useTheme 全部消费方路径不变）。本文件是**再导出落点别名**：把附录E
 * 要求的 `src/theme/` 落点钉在位，不搬实现、不新建主题层。
 *
 * <p>判定不翻转登记：落点在位 ≠ 附录逐字合规终裁——消费面未变，见台账 v1.0.223 订正注。
 */
export * from "../components/ThemeContext";

/**
 * E.6 落点收口（#146ax，台账附录E E.6 第 2 件）：`src/config/route-manifest.ts` 属主落点。
 *
 * <p>数据本体仍单源在仓根 {@code config/route-manifest.ts}（vite.config.ts / server.ts /
 * 既有测试以 `./config/route-manifest` 相对导入消费；详设-08 §九 routeManifest 门禁钉死该
 * 相对串，另搬即破坏既有消费面）。本文件是**再导出落点别名**：把附录E 要求的
 * `src/config/` 落点钉在位，不搬数据、不改消费路径。
 *
 * <p>判定不翻转登记：落点在位 ≠ 附录逐字合规终裁——消费面未变，见台账 v1.0.223 订正注。
 */
export * from "../../config/route-manifest";

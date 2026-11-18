-- D89：sc001 演示场景的两条 DATASET 绑定是悬空引用
-- 现象：sb001/sb002 的 target_ref 分别指向 ds_flight_schedules / ds_passenger_feedback，
--       但数据域资源登记表 td_data_resource 只有 res-dw-doc / res-dw-doc-chunk 两行，
--       两个数据集都不存在 ⇒ 场景面板显示已绑定数据集、点进去无数据；
--       V-WS-015 跨域对账 FAIL（resource_id IN (...) → 0 rows）。
-- 处置（2026-11-15 用户裁决，D89 选项②）：把两条绑定改指向真实存在的资源
--       res-dw-doc（ecos_dw.doc）与 res-dw-doc-chunk（ecos_dw.doc_chunk）。
--       只 UPDATE 这两行的 target_ref，不改结构、不删行。
-- 幂等：以 binding id + 旧 target_ref 双条件定位，已改过则 WHERE 不命中、零副作用。

UPDATE ecos_control.ecos_scenario_binding
SET target_ref = 'res-dw-doc',
    target_id  = 'res-dw-doc',
    remark     = 'D89 订正：原指向虚构数据集 ds_flight_schedules（td_data_resource 无此行），改指真实资源',
    update_time = NOW()
WHERE id = 'sb001'
  AND scenario_id = 'sc001'
  AND binding_type = 'DATASET'
  AND target_ref = 'ds_flight_schedules';

UPDATE ecos_control.ecos_scenario_binding
SET target_ref = 'res-dw-doc-chunk',
    target_id  = 'res-dw-doc-chunk',
    remark     = 'D89 订正：原指向虚构数据集 ds_passenger_feedback（td_data_resource 无此行），改指真实资源',
    update_time = NOW()
WHERE id = 'sb002'
  AND scenario_id = 'sc001'
  AND binding_type = 'DATASET'
  AND target_ref = 'ds_passenger_feedback';

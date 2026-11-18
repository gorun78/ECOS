-- =============================================================================
-- V291__sys_dict_clearance_level_code0.sql
-- D-B4-99 c) 「列值 0 无任何字典项」关账（增补行，只加不删）
--
-- 追溯: 差距分析台账 v1.0.169 行 ⑧ D-B4-99 c)
--       「DDL `V24` 列注释 0-4／Java 三枚私有 `LEVEL_NAMES` 0-4／
--         字典 `V12` codes 1-5（公开…最高机密）与 `V40` codes 1-5（L1内部…L5最高）
--         互相冲突，且列值 0 无任何字典项」
--
-- 裁决（本批）: DDL V24 列注释「准入等级 0-4：L0公开 L1内部 L2保密 L3机密 L4绝密」
--       是<b>列值面权威口径</b>（Java 生产读写路径全部按 0-4 走），而字典侧
--       V12（codes 1-5：公开/内部/机密/绝密/最高机密）⇒V40 覆盖
--       （codes 1-5：L1内部/…/L5最高）把字典基址锚在 1，既不含列值 0 又与
--       列值域 0-4 不一致。本枚只补清 code 0 ⇒ L0公开；V40 已存在的 1..5
--       不动（1 已被 V40 强制 DO UPDATE 为「L1内部」，与列值 1＝L1内部 语义对齐；
--       V12 与 V40 的 1-5 标签差异已由 V40 单源收敛）。
--       字典做法：只增行、不改 V40 已有行、不重排 sort_order —— 前端 
--       UserEditModal 与 Topbar 的档位显示走 locale 键（`user.security.clearance.l0..l4`）
--       与 `ClearanceLevel.nameOf` 单源（#142/B4-3e-88），不依赖 sys_dict。
--       规避副作用字典改动：不删除 V12 历史行、不 DO UPDATE 已有 1..5 行。
-- =============================================================================

INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order, ext_value)
VALUES ('dict-cl-0', 'clearance_level', '0', 'L0公开', 'L0 Public', 0, '#52c41a')
ON CONFLICT (dict_type, dict_code) DO UPDATE SET
    dict_label    = EXCLUDED.dict_label,
    dict_label_en = EXCLUDED.dict_label_en,
    sort_order    = EXCLUDED.sort_order,
    ext_value     = EXCLUDED.ext_value,
    updated_at    = NOW();

-- ============================================================================
-- V357 — storage.mineru.timeout-seconds 对齐代码真相（120 → 300）
-- ============================================================================
-- 背景：
--   V354 播种 `storage.mineru.timeout-seconds = 120`，头注称「取 runtime-access 现行缺省」——
--   **不实**。代码真相 = `MinerUHttpParser.REQUEST_TIMEOUT = Duration.ofMinutes(5)`（300 秒，
--   OCR 重活放宽）。播种值差了 2.5 倍。
--
-- 为什么必须修（2026-11-19 接线时）：
--   本批把该键接成 `MinerUHttpParser#requestTimeout()` 的真实读取源——配置面优先、
--   常量回落。若不订正，桥接生效瞬间 MinerU 解析超时从 300 秒**缩短到 120 秒**，
--   OCR 大文件会批量超时失败。借"配置化"改行为，违反「播种后语义不变」基线。
--
-- 修法（只改本次播种的值，不动运维后续可能的调整）：
--   仅当当前值**仍等于 V354 的原始播种值 120** 时才改；运维已自行调整则保持不动。
--   `default_value` 同步订正（reset 基线）。
--
-- 幂等：带值守卫，重跑无副作用。
-- 纪律：IR03「只加不删」——本脚本只改 config_value/default_value，不改结构。
-- ============================================================================

UPDATE sys_config
   SET config_value = '300', default_value = '300', updated_at = NOW()
 WHERE config_key = 'storage.mineru.timeout-seconds'
   AND config_value = '120';

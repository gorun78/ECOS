-- D12：分类树 level 与 parent_id 层级不一致
-- 现象：V58 种子插入 cat_raw/cat_curated/cat_semantic/cat_app 四行时未写 level 列，
--       该列 DEFAULT 1 ⇒ 挂在该根下的二级分类 level 全为 1。
--       前端 DataAssetsDashboard.tsx:151 用 categories.filter(c => c.level === 2) 渲染
--       资产分类下拉框 ⇒ 四个业务分类在 UI 上全部消失（下拉框只剩「全部」）。
-- 处置：按 parent 链回填 level（只 UPDATE 不一致行，不改结构）。
-- 口径：level = 到根的层级，根 = 1，子 = 父 + 1。

UPDATE ecos_data.td_data_category AS c
SET level = p.level + 1
FROM ecos_data.td_data_category AS p
WHERE c.parent_id = p.category_id
  AND c.level IS DISTINCT FROM p.level + 1;

-- 兜底：parent 指向不存在的行（脏引用）按根处理
UPDATE ecos_data.td_data_category
SET level = 1
WHERE (parent_id IS NULL OR parent_id = '')
  AND level IS DISTINCT FROM 1;

COMMENT ON COLUMN ecos_data.td_data_category.level IS
  '分类层级，根=1，子=父+1；前端按 level=2 取业务分类。与 parent_id 必须自洽（D12）。';

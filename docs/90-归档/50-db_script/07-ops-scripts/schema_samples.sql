-- 统计各 schema 的表名样例（前 3 张）
SELECT table_schema, table_name FROM information_schema.tables
WHERE table_schema IN ('ecos_security','ecos_ai','ecos_data','ecos_ontology','ecos_knowledge','ecos_cognitive','ecos_sysman','ecos_infra')
  AND table_type='BASE TABLE'
ORDER BY table_schema, table_name
LIMIT 15;

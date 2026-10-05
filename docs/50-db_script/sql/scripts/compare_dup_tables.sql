-- 比较 public 与 service schema 下同名表的行数，判断哪个是真相源
SELECT 'public.ecos_ontology' AS tbl, count(*) AS cnt FROM public.ecos_ontology
UNION ALL SELECT 'ecos_ontology.ecos_ontology', count(*) FROM ecos_ontology.ecos_ontology
UNION ALL SELECT 'public.ecos_agent', count(*) FROM public.ecos_agent
UNION ALL SELECT 'ecos_ai.ecos_agent', count(*) FROM ecos_ai.ecos_agent
UNION ALL SELECT 'public.ecos_pipeline_definition', count(*) FROM public.ecos_pipeline_definition
UNION ALL SELECT 'ecos_data.ecos_pipeline_definition', count(*) FROM ecos_data.ecos_pipeline_definition
UNION ALL SELECT 'public.ecos_audit_log', count(*) FROM public.ecos_audit_log
UNION ALL SELECT 'ecos_security.ecos_audit_log', count(*) FROM ecos_security.ecos_audit_log;

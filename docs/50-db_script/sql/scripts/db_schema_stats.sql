SELECT table_schema, count(*) FROM information_schema.tables
WHERE table_type='BASE TABLE'
GROUP BY table_schema
ORDER BY count(*) DESC;

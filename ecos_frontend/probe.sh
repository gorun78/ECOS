#!/bin/bash
set +e
echo curl smoke on 127.0.0.1:3000
for r in /dashboard /chatbot /data /agents /aip /login /ai-workbench /ontology_workbench /biz_dashboard /iam; do
    printf '%-25s ' "$r"
    curl -s -o /dev/null -w "status=%{http_code} size=%{size_download}B\n" "http://127.0.0.1:3000$r"
done
echo '--- main.tsx chunk (sanity) ---'
curl -s http://127.0.0.1:3000/ | head -c 500
echo
echo '--- lazy chunks (sample 3 probe) ---'
for r in /login /mission_control /ontology_workbench /ai-workbench /data-workbench; do
    printf '%-25s ' "$r"
    # Verify 200
    s=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:3000$r")
    echo "status=$s"
done

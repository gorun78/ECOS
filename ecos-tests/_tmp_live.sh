#!/bin/bash
echo "FE 3000 8 s 后:"
curl -s -o /dev/null -w "FE=%{http_code} time=%{time_total}s\n" --max-time 6 http://127.0.0.1:3000
echo "FE dev log tail:"
tail -n 15 /tmp/ecos-fe-dev.log 2>/dev/null
echo "=== END ==="

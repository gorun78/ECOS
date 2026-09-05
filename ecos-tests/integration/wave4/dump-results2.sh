#!/bin/bash
echo "=== /tmp/wave4_*.json ==="
ls -la /tmp/wave4_*.json 2>/dev/null || echo NONE
echo ""
echo "=== /tmp/wave4-test-results-v5.log (clean) ==="
cat /tmp/wave4-test-results-v5.log 2>/dev/null || echo NO_LOG

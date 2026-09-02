#!/usr/bin/env bash
# w4-probe2-run.sh — wraps w4-probe2.sh, pipe to head 200 lines
set +e
bash /home/guorongxiao/ECOS/ecos-gen-scratch/w4-probe2.sh 2>&1 | head -200

#!/bin/bash
export PATH=/home/guorongxiao/.local/bin:/usr/bin:/bin:$PATH
cd /home/guorongxiao/ECOS/ecos_frontend
exec node ./node_modules/tsx/dist/cli.mjs server.ts

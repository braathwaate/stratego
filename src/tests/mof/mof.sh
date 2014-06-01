#!/bin/bash
#
# Play against Master of the Flag, a world champion stratego program
#
# ncat -l -p 8096 -c ./mof.sh
# ncat -c ./run*s*sh 127.0.0.1 8096
#
$HOME/Downloads/node-v0.10.28/node mof.js | stdbuf -o0 tee /tmp/mof.out

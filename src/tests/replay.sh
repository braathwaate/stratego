#!/bin/bash
SE=$HOME/src/strategoevaluator
GAME=`pwd`/$1
cd $SE/manager
./stratego -t 0.2 -f $GAME -g

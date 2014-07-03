#!/bin/bash
SE=$HOME/src/strategoevaluator
STRATEGO=$HOME/src/stratego/src
AGENT1=$SE/agents/p*s/p*s
AGENT2=$SE/agents/c*s/*py
AGENT3=$STRATEGO/run2.sh
a=0

agent=$AGENT3
$SE/manager/stratego -o gameb.out -T 3000 $STRATEGO/run_stratego.sh $agent

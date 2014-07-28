#!/bin/bash
SE=$HOME/src/strategoevaluator
STRATEGO=$HOME/src/stratego/src
AGENT1=$SE/agents/p*s/p*s
AGENT2=$SE/agents/c*s/*py
OPTIONS="-m 1000"
a=0

rm *.out

for agent in $AGENT1 $AGENT2
do
	a=$((a+1));
	for (( i = 1; i <= 10; i++ ))
	do
	result=`$SE/manager/stratego -o game$a$i.out $OPTIONS $STRATEGO/run_stratego.sh $agent`
	read -r name color outcome turn rv bv <<< $result
	echo $i ":" $result
	mv $STRATEGO/ai.out ai$a$i.out
	r=$color$outcome
	if [ "$r" != "REDVICTORY" ] \
		&& [ "$r" != "BLUESURRENDER" ] \
		&& [ "$r" != "BLUEILLEGAL" ] \
		&& [ "$outcome" != "DRAW_DEFAULT" ]
	then
		exit
	fi
	done
done

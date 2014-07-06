#!/bin/bash
CANNONPATH=`readlink -f "$0"`
cd "`dirname "$CANNONPATH"`"

# use this for running the gui
# OPTIONS=

# use this for playing mof
OPTIONS="-g -2 -l3 -t"

# use this for playing against itself
# OPTIONS="-g -2 -t"

# use this for playing agents
# OPTIONS="-t"

# use this for playing against itself
exec java -ea com.cjmalloy.stratego.player.StrategoDriver $OPTIONS | stdbuf -o0 tail -n +1 | tee /tmp/out

# this is how to connect the debugger
# jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=8000

# use this for running ai test with the ability to attach jdb:
# exec java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n com.cjmalloy.stratego.player.StrategoDriver $OPTIONS | stdbuf -o0 tail -n +2 | tee /tmp/out



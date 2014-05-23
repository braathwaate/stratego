#!/bin/bash
CANNONPATH=`readlink -f "$0"`
cd "`dirname "$CANNONPATH"`"

# use this for playing against itself
exec java -ea com.cjmalloy.stratego.player.StrategoDriver -t | stdbuf -o0 tail -n +1 | tee /tmp/out

# use this for running ai test with the ability to attach jdb:
# jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=8000

# exec java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n com.cjmalloy.stratego.player.StrategoDriver -t | stdbuf -o0 tail -n +2 | tee /tmp/out

# use this for running the gui
#exec java -ea com.cjmalloy.stratego.player.StrategoDriver


Introduction
------------

Demon Of Ignorance is
a java version of the game of stratego.
This version of the game is forked from cjmalloy/stratego
and mainly offers a much improved AI and some other features.

Download
--------

[Stratego player v0.7.0][dl]
[dl]: https://github.com/braathwaate/stratego/releases/download/v.0.7.0/stratego_v0.7.0.jar

For two person play over TCP/IP, you need the Stratego server,
which you need to make from source.

Installation and Requirements
-----------------------------

Demon Of Ignorance requires java, which you probably already have or can
figure out how to download.

To run on most computers, just double click on the jar file to run.

If that doesn't work, you might need to
make the jar file executable
or set up your computer to select which program opens the jar file.

Finally, you can try to run it from the command line:

	java -jar stratego_v0.1.0.jar


About the AI (Artificial Intelligence)
--------------------------------------

The AI algorithm is the usual minimax with alpha-beta pruning.
Iterative deepening and heuristic history is used 
for move ordering to increase pruning success.
Killer move is used for efficiency.
Quiescent Search and First Move Weighing are used to reduce
to reduce the horizon effect.
Move generation abides by the Two-Square and More-Squares rules.

The search time is adjustable with the Settings menu.
At the default depth (1 tick)
it completes within a fraction of a second
usally completing about 4-6 ply depending on the speed of the desktop.

The evaluation function is based on material gained versus lost
as well as opponent piece rank discovery.
It weighs which pieces to attack based on the potential
of discovering important piece ranks versus the risk of loss.
Hence, an valuable AI piece will generally avoid unknown and unmoved opponent pieces
whereas lesser AI pieces are eager to sacrifice themselves if the loss
leads to key opponent piece discovery that assists in winning the game.

A primary goal of the AI is to keep its low ranked pieces unknown
and unmoved
while it tries to discover the ranks of the opponent's low ranked pieces,
primarily the opponent's One.
As the opponent's low ranked pieces become known,
the AI's low ranked pieces (Two and up) become locally invincible,
which allow it attack all higher ranked or unknown moved pieces with impunity.

The AI derives opponent piece rank based on how an
opponent piece interacts with its pieces.
When an opponent piece appears to act as a strong chase piece
or protector, the AI tries to attack it with pieces of
expendable rank to confirm its suspicions.

Static position analysis is used to determine piece destinations.
Success is largely dependent on the AI in gaining
knowledge of opponent piece rank.
(However, this does lead the AI to make pointless chases
which are mitigated only by the Two-Squares and More-Squares rules).

Once a piece gets in the general vicinity of its target(s),
the AI relies on the minimax algorithm to find
the optimal set of moves that
allows it to attack its target.

This algorithm results in a modest amateur level of play,
able to defeat most casual human players and most stratego bots.
A primary failing of other stratego bots is the lack of bluffing,
which allows the AI to easily determine piece ranks
without random attack.
However, even if the opponent does bluff, the AI does not heavily
weight suspected piece ranks until confirmed through attack by
a piece of expendable rank.

Areas for improvements are:

  1. Search tree.  Extreme pruning will be required to get to deeper levels.  A transposition table is needed.  There is code that forward prunes the search tree for defending against chases.  Can similar code be used to determine attack plans as well?  The AI always abides by the two squares rule but does not enforce the same of the opponent.  If the Two Squares Settings box is checked, making the opponent abide by the two squares rule, then chase attacks would be much more successful and worthy of deep analysis.
  2. Heuristic.  This has been tuned with countless runs against other bots.  Yet there still much room for improvement.  The primary issue is how much too weight suspected pieces given a bluffing opponent.
  3. Suspected Rank Analysis.  This is perhaps the most potent area for improvement, as it is how human players win.  Humans are able to evaluate unknown opponent pieces and make good decisions that violate worst case scenarios.  The AI deviates only slightly from worst case scenarios, but gives it signficant advantage over other bots which rely on worst case scenarios and completely miss how the AI can obliterate the opponent's pieces without the pieces clearly known.
  4. Plans.  There are very few plans.
	A. Chase opponent pieces that that could result in a favorable exchange.
	B. Attack flag structures.
	C. Protect its flag.
	D. Determine opponent piece ranks through bluffing or baiting.
  5. Setups.  Many of the initial setups, especially the non-bombed setups are ridiculous.  If you encounter one of these setups, remove the line from resource/ai.cfg.  Better yet, run an automated test against the AI evaluator and remove the setups that lose badly.  Another idea: design an automated test using just the bad setups and improve the ai win ratio with just bad setups.  (You can find the one that was used in the first line of ai.out.)
  6. Opponent bots.  Improve or add opponent bots in 
[Stratego AI Evaluator](https://github.com/braathwaate/strategoevaluator).
  7. Add AI player to TCP/IP server.  The Stratego server supports two person play over TCP/IP.  Currently it does not support playing the AI over TCP/IP.  The AI player should be added to the Lobby so that any player can play the AI over TCP/IP.   The server will need to fork the AI player upon player request.  Add a new View object (like the AITest object) to create an AI client for TCP/IP play.

AI Regression Testing
---------------------

Demon Of Ignorance supports the interface protocol defined in
[Stratego AI Evaluator](https://github.com/braathwaate/strategoevaluator).
This is used to test the strength of the AI
by playing the AI against the bots including itself and the prior release.
To use Demon Of Ignorance with the Stratego AI Evaluator,
use the -t option on the command line.

For a given set of initial setups,
each new version of the AI must result in 100% wins against the agents.
In addition, the number of moves to reach the win
are totaled.
The sum of the number of moves for all the games played against each agent
must be equal or less than that recorded by the prior AI code.
In other words,
while a modified AI may take more moves to win any individual game
(and it must win all games against all agents),
the aggregate of all moves to win a set of games must be less.
Finally, a new release must win a majority of games against the prior release.

References
----------
[COMPETITIVE PLAY IN STRATEGO, A.F.C. Arts](https://project.dke.maastrichtuniversity.nl/games/files/msc/Arts_thesis.pdf).

[Invincible. A Stratego Bot, V. de Boer](http://www.kbs.twi.tudelft.nl/Publications/MSc/2008-deBoer-Msc.html).


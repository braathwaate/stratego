Download
--------

[Stratego v0.2.0][dl]
[dl]: https://github.com/braathwaate/stratego/releases/download/v.0.2.0/stratego_v0.2.0.jar

Introduction
------------

Java remake of stratego.  Forked from cjmalloy/stratego.
This version has an improved AI and other features.

Installation and Requirements
-----------------------------

Stratego requires java, which you probably already have or can
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

The search time is adjustable with the Settings menu.
At the default depth (1 tick)
it completes within a fraction of a second
usally completing about 6-8 ply depending on the speed of the desktop.

Evaluation heuristic is based on material gained versus lost.
An AI piece will generally avoid unknown and unmoved opponent pieces.
If the AI piece is invincible, it will attack all unknown moved pieces.
Unknown pieces have a specified value,
which is about equal to a Five,
although this varies with play.
This results in an eagerness to use pieces
of ranks Six, Seven and Nine to attack opponent's pieces.

Static position analysis is used to determine piece destinations.
Success is largely dependent on the heuristic in
gaining opponent piece information.
I won't tell you how it works here because it is a bit of a spoiler.
As with human opponents, once you know their strategy
it is easy to thwart it.
This is much worse with the AI, because it does not adapt.
You need to read the code if you want to find out
what the AI tries to accomplish.

This simple algorithm results in an amateur level of play.  

Areas for improvements are:

  1. Improving the search tree.  Extreme pruning will be required to get to deeper levels.  A transposition table is needed.
  2. Improving the heuristic.
  3. Improving the static position analysis.

AI Regression Testing
---------------------

Stratego supports the protocol used in
[Stratego AI Evaluator](https://github.com/braathwaate/strategoevaluator).
This is used to test the strength of the AI.
To use Stratego with the Stratego AI Evaluator,
use the -t option on the command line

For a given set of initial setups,
any change to the AI code must result in 100% wins against the agents.
In addition, the number of moves to reach the win
are totaled.
The sum of the number of moves for all the games played against each agent
must be equal or less than that recorded by the prior AI code.
In other words,
while a modified AI may take more moves to win any individual game
(and it must win all games against all agents),
the aggregate of all moves to win a set of games must be less.

References
----------
[COMPETITIVE PLAY IN STRATEGO, A.F.C. Arts](https://project.dke.maastrichtuniversity.nl/games/files/msc/Arts_thesis.pdf).

[Invincible. A Stratego Bot, V. de Boer](http://www.kbs.twi.tudelft.nl/Publications/MSc/2008-deBoer-Msc.html).

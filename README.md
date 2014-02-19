Download
--------

[Stratego v0.2.0][dl]
[dl]: https://github.com/braathwaate/stratego/releases/download/v.0.2.0/stratego_v0.2.0.jar

Introduction
------------

Java remake of stratego.  Forked from cjmalloy/stratego.
This version has an improved ai and other features.

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
An ai piece will generally avoid unknown and unmoved opponent pieces.
If the ai piece is invincible, it will attack all unknown moved pieces.
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
This is much worse with the ai, because it does not adapt.
You need to read the code if you want to find out
what the ai tries to accomplish.

This simple algorithm results in an amateur level of play.  

Areas for improvements are:

  1. Regression testing.  Design an automatic way to benchmark improvements, such as allowing ai v. ai play.
  2. Improving the search tree.  Extreme pruning will be required to get to deeper levels.  A transposition table is needed.
  3. Improving the heuristic.
  4. Improving the static position analysis.


References
----------
[COMPETITIVE PLAY IN STRATEGO, A.F.C. Arts](https://project.dke.maastrichtuniversity.nl/games/files/msc/Arts_thesis.pdf).

[Invincible. A Stratego Bot, V. de Boer](http://www.kbs.twi.tudelft.nl/Publications/MSc/2008-deBoer-Msc.html).

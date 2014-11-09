/*
    This file is part of Stratego.

    Stratego is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Stratego is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Stratego.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.cjmalloy.stratego.player;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.BMove;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.UndoMove;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Settings;
import java.util.Random;



public class TestingBoard extends Board
{
	private static final int DEST_PRIORITY_DEFEND_FLAG = 8;
	private static final int DEST_PRIORITY_DEFEND_FLAG_BOMBS = 6;
	private static final int DEST_PRIORITY_DEFEND_FLAG_AREA = 4;
	private static final int DEST_PRIORITY_ATTACK_FLAG = 1;
	private static final int DEST_PRIORITY_FLEE = 3;
	private static final int DEST_PRIORITY_CHASE_HIGH = 2;
	private static final int DEST_PRIORITY_CHASE = 1;
	private static final int DEST_PRIORITY_LOW = 1;

	private static final int DEST_VALUE_FLEE = 3;

	private static final int DEST_VALUE_NIL = 9999;
	private static final int GUARDED_OPEN = 0;
	private static final int GUARDED_UNKNOWN = 1;
	private static final int GUARDED_MOVED = 2;

	protected Piece[][] pieces = new Piece[2][41];	// piece arrays
	protected int[] npieces = new int[2];		// piece counts
	protected int[] invincibleRank = new int[2];	// rank that always wins or ties
	protected int[] invincibleWinRank = new int[2];	// rank that always wins
	protected int[][] suspectedRank = new int[2][15];	// guessed ranks
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected Piece[][] activeRank = new Piece[2][15];	// moved rank Piece
	protected int[][] trayRank = new int[2][15];	// ranks in trays
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[][] lowerRankCount = new int[2][10];
	protected int[][][][] planA = new int[2][15][2][121];	// plan A
	protected int[][][][] planB = new int[2][15][2][121];	// plan B
	protected int[][] winRank = new int[15][15];	// winfight cache
	protected int[] piecesInTray = new int[2];
	protected int[] piecesNotBomb = new int[2];
	protected int[] sumValues = new int[2];
	protected int value;	// value of board
	protected Piece[] flag = new Piece[2];	// flags
	protected int[] unmovedValue = new int[121];	// unmoved value
	protected int[][] valueStealth = new int[2][15];
	protected int expendableRank[] = { 6, 7, 9 };
	protected int importantRank[] = { 1, 2, 3, 8 };
	protected long hashTest;
	protected int lowestUnknownRank;
	protected int lowestUnknownExpendableRank;
	protected int[] nUnknownExpendableRankAtLarge = new int[2];
	protected int dangerousKnownRank;
	protected int dangerousUnknownRank;
	protected Random rnd = new Random();

	// De Boer (2007) suggested a formula
	// for the relative values of pieces in Stratego:
	// • The Flag has the highest value of all
	// • The Spy’s value is half of the value of the Marshal
	// • If the opponent has the Spy in play,
	//	the value of the Marshal is multiplied with 0.8
	// • When the player has less than three Miners,
	//	the value of the Miners is multiplied with 4 − #left.
	// • The same holds for the Scouts
	// • Bombs have half the value of the strongest piece
	//	of the opponent on the board
	// • The value of every piece is incremented with 1/#left
	//	to increase their importance when only a few are left
	//
	// A.F.C Arts (2010) used the following value relations:
	// • First feature is multiplying the value of the Marshal
	//	(both player and opponent) with 0.8 if the
	//	opponent has a Spy on the game board.
	// • Second feature multiplies the value of the Miners with 4 − #left
	//	if the number of Miners is less than three.
	// • Third feature sets the value of the Bomb
	//	to half the value of the piece with the highest value.
	// • Fourth feature sets divides the value of the Marshal by two
	//	if the opponent has a Spy on the board.
	// • Fifth feature gives a penalty to pieces
	//	that are known to the opponent
	// • Sixth feature increases the value of a piece
	//	when the player has a more pieces of the same type
	//	than the opponent.
	//
	// Eventually the value of a piece is multiplied
	//	with the number of times that the piece is on the board,
	//	and summated over all the pieces.
	// Values are based upon the M.Sc. thesis by Stengard (2006).
	//
	//
	// In this implementation:
	// • Each of the lowest ranked pieces (1-4) is worth two of
	//	the next lowest rank.  (While this may not be accurate
	//	for competitive human play, it seems to hold true
	//	for AI play, because the loss of any of its low ranking
	//	pieces means almost certain loss once the remaining
	//	pieces are traded off.)
	// • If the opponent has the Spy in play,
	//	the value of the Marshal is multiplied with 0.75.
	// • An unknown piece is worth more than a known piece (stealth).
	// • Unmatched invincible pieces are all valued the same.
	// • Bombs are worthless, except if they surround a flag.
	//	These bombs are worth a little more than a Miner.
	// • The value of the Spy becomes equal to a Seven once the
	//	opponent Marshal is removed.
	// • The value of the Miner depends on the number of structures
	//	left than could surround a flag.
	//	- When the opposing player has more structures left than Miners
	//	  the value of the Miners is increased.
	//	- When the opposing player has less structures left than Miners
	//	  the value of the excess Miners becomes equal to a Seven.
	// • If the AI is winning, an AI piece is worth less than
	//	the opponents piece of the same rank.  If the AI is losing,
	//	its pieces are worth more.
	// • An unbombed flag is worth about a known Four.  (It is not
	//	the highest value because of the opponent never really
	//	knows for sure the location of the flag.  See the code
	//	for more information).
	// 

	private static final int VALUE_THREE = 200;
	private static final int VALUE_FOUR = 100;
	private static final int VALUE_FIVE = 50;
	private static final int VALUE_SIX = 30;
	private static final int [] startValues = {
		0,
		800,	// 1 Marshal
		400, 	// 2 General
		VALUE_THREE, 	// 3 Colonel
		VALUE_FOUR,	// 4 Major
		VALUE_FIVE,	// 5 Captain
		VALUE_SIX,	// 6 Lieutenant
		25,	// 7 Sergeant
		40,	// 8 Miner
		25,	// 9 Scout
		300,	// Spy
		0,	// Bomb (valued by code)
		1000,	// Flag (valued by code)
		0,	// Unknown (valued by code, minimum piece value)
		0	// Nil
	};
	public int [][] values = new int[2][15];

	static final int[][] lanes = {
		{ 45, 46, 56, 57, 67, 68, 78, 79 },
		{ 49, 50, 60, 61, 71, 72, 82, 83 },
		{ 53, 54, 64, 65, 75, 76, 86, 87 }
	};

	// If a piece has already moved, then
	// the piece cannot be a flag or a bomb,
	// so the attacker doesn't gain as much info
	// by discovering it.  (The ai scans for
	// for unmoved piece patterns as a part
	// of its flag discovery routine).

	private static final int VALUE_MOVED = 5;

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

		value = 0;
		hashTest = hash;	// for debugging (see move)

		for (int c = RED; c <= BLUE; c++) {
			flag[c]=null;
			npieces[c] = 0;
			piecesNotBomb[c] = 0;
			piecesInTray[c] = 0;
			for (int j=0;j<15;j++) {
				suspectedRank[c][j] = 0;
				knownRank[c][j] = 0;
				activeRank[c][j] = null;
				trayRank[c][j] = 0;
				neededRank[c][j] = false;
				values[c][j] = startValues[j];
				valueStealth[c][j] = 0;
			}
			for (int j=0;j<10;j++) {
				lowerRankCount[c][j] = 99;
			}
		} // color c

		// ai only knows about known opponent pieces
		// so update the grid to only what is known
		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			unmovedValue[i] = 0;
			Piece p = getPiece(i);
			if (p != null) {
				Piece np = new Piece(p);
				grid.setPiece(i, np);
				np.setAiValue(0);
				np.setIndex(i);
				if (p.isKnown()) {
					int r = p.getRank().toInt();
					knownRank[p.getColor()][r-1]++;
				} else if (p.getColor() == Settings.bottomColor)
					np.setRank(Rank.UNKNOWN);

				if (p.hasMoved() || p.isKnown()) {
					// count the number of moved pieces
					// to determine further movement penalties
					Rank rank = p.getRank();
					int r = rank.toInt();

		// only one piece of a rank is considered active
		// and preferably a piece that is known

					if (!isActiveRank(p.getColor(), r)
						|| p.isKnown())
						activeRank[p.getColor()][r-1]=np;
					if (rank != Rank.BOMB)
						piecesNotBomb[p.getColor()]++;
				}

				if (np.getRank() == Rank.FLAG)
					flag[p.getColor()] = np;
			}
		}

		// add in the tray pieces to trayRank
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			trayRank[p.getColor()][r-1]++;
			piecesInTray[p.getColor()]++;
		}

		// The number of expendable ranks still at large
		// determines the risk of discovery of low ranked pieces.
		// If there are few expendable pieces remaining,
		// the AI can be more aggressive with its unknown low ranked
		// pieces.
		for (int c = RED; c <= BLUE; c++) {
		nUnknownExpendableRankAtLarge[c] = 0;
		for (int r = 5; r <= 9; r++)
			nUnknownExpendableRankAtLarge[c] += unknownRankAtLarge(c, r);
		}

		// call genSuspectedRank early before calling aiValue()
		// but after trayRank and knownRank are calculated
		// because genSuspectedRank depends on unknownRankAtLarge()
		genSuspectedRank();

		for (int c = RED; c <= BLUE; c++) {

		// If all movable pieces are moved or known
		// and there is only one unknown rank, then
		// the remaining unknown moved pieces must be that rank.
		int unknownRank = 0;
		for (int r = 1; r <= 10; r++)
			if (unknownRankAtLarge(c, r) != 0) {
				if (unknownRank != 0) {
					unknownRank = 0;
					break;
				}
				unknownRank = r;
			}

		// If all pieces have been accounted for,
		// the rest must be bombs (or the flag)

		if (40 - piecesInTray[c] - piecesNotBomb[c]
			== 1 + Rank.getRanks(Rank.BOMB) - 
				trayRank[c][Rank.BOMB.toInt()-1]) {

			for (int i=12;i<=120;i++) {
				if (!isValid(i))
					continue;
				Piece p = getPiece(i);
				if (p == null)
					continue;
				if (p.getColor() != c)
					continue;
				if (p.isKnown())
					continue;
				if (p.hasMoved()) {
					if (unknownRank != 0) {
						p.setRank(Rank.toRank(unknownRank));
						makeKnown(p);
					}
				} else {

		// The remaining unmoved pieces must be bombs (or the flag)
		// Make the piece known to remove it from the piece lists
		// because it is no longer a possible attacker.
		// This makes Bombs known, but they could be a Flag.
		// So subsequent code needs to make this check:
		// - if a bomb is known and not suspected, it is a bomb.
		// - if a bomb is known and suspected, it could be either.
		// This happens in possibleFlag().

				makeKnown(p);

				Rank rank = p.getRank();
				if (c == Settings.topColor)
					assert (rank == Rank.FLAG || rank == Rank.BOMB) : "remaining ai pieces not bomb or flag?";
				else if (unknownRankAtLarge(c, Rank.BOMB) != 0) {
		// Set setSuspectedRank (not rank)
		// because the piece is suspected to
		// be a bomb, but it could be the flag.
		// See winFight().
		//
		// The value of a suspected bomb is zero.
		// Piece X Bomb and Bomb X Piece is a LOSS,
		// Eight X Bomb is a WIN.
		//
					p.setSuspectedRank(Rank.BOMB);
				} else
					p.setRank(Rank.FLAG);
				} // not moved
			} // for

		} // all pieces accounted for

		} // color

		// A rank becomes invincible when all lower ranking pieces
		// are gone or *known*.
		//
		// Invincibility means that a rank can attack unknown pieces
		// with the prospect of a win or even exchange.
		//
		for (int c = RED; c <= BLUE; c++) {
			int rank;
			for (rank = 1;rank<10;rank++)
				if (unknownNotSuspectedRankAtLarge(c, rank) > 0)
					break;
			invincibleRank[1-c] = rank;

			for (rank = 1;rank<10;rank++)
				if (rankAtLarge(1-c, rank) != 0)
					break;
			invincibleWinRank[c] = rank-1;

		// If the opponent no longer has any rank remaining that can
		// remove a player rank, the value of the player's rank
		// becomes the same, regardless of its rank.
		// (all invincible win ranks are valued equally).
		//
		// The rank value is set to the lowest value of any of the ranks.
		//
		// Example 1. Blue has a 2, 5, 8, 9.  Red has a 2, 4, 9, 9.
		// No change in the value because there are
		// no invincible Win Ranks.
		//
		// Example 2.  Blue has 5, 8, 9.  Red has 4 4 5 6.
		// No change in the value because Red are the only
		// invincible Win Ranks.
		//
		// Example 3.  Blue has 1, S, 6, 7.  Red has 1, S, 2, 5, 8.
		// The value of Red One is 3/4 of Red One original value.
		//
		// Example 4.  Blue has S, 6, 7.  Red has 1, 4, 8.
		// Red One and Red Four are both invincible Win Ranks.
		// The value of Red One is 3/4 of the value of
		// Red Five.  Red Four is equal to Red Five.  This makes
		// Red Four the most valuable piece on the board.
		//
		// Example 5.  Blue has 1, 6, 7.  Red has 1, S, 2, 5, 8.
		// Should Red One exchange with Blue One, even though
		// Blue one is worth less because Red has the Spy?
		// The exchange would create an invincible Win Rank, but
		// this needs more thought.
		//
		// a One is worth less if the opponent
		// has a Spy.  (This is a big reason why the Spy
		// is highly valued). 
		
		// (Note that percentage is used rather than subtracting
		// some absolute value.  That is because the value
		// of unmatched invincible pieces are set to the
		// value of the next higher rank.)

		// Note: a One can be worth *less* than a
		// Two (or even higher) rank if the opponent
		// no longer has any lower ranks but still has
		// a Spy.

			for (int i = 1; i < invincibleWinRank[c]; i++)
				values[c][i] = values[c][invincibleWinRank[c]];

			if (hasSpy(1-c))
				values[c][1] = values[c][1]*3/4;

		// Set the value of a (moved) UNKNOWN to the lowest value
		// of the remaining unknowns.
		// 
			int minvalue = 9999;
			for (rank = 1; rank <= 10; rank++)
				if (unknownRankAtLarge(c, rank) != 0
					&& values[c][rank] < minvalue)
					minvalue = values[c][rank];
			if (minvalue == 9999)
				minvalue = 0;	// no unknown ranks
			values[c][Rank.UNKNOWN.toInt()] = minvalue;

		// Demote the value of the spy if there is
		// no opponent marshal left at large.
		// It is worth less than any other piece now.

			if (rankAtLarge(1-c, Rank.ONE) == 0)
				values[c][Rank.SPY.toInt()]
					= values[c][Rank.UNKNOWN.toInt()] - 10;


		// Pieces become more valuable as they become fewer
		// (to prevent the piece from random attacking non-moved
		// pieces)

			if (40 - piecesInTray[c] -
				(1 + rankAtLarge(c, Rank.BOMB)) <= 3)
				for (rank = 1;rank<10;rank++)
					values[c][1] += 30;

		// Do invincible pieces have higher value than
		// non-invincible pieces?
		//
		// If invincible pieces exchange equally,
		// the next higher rank becomes invincible.
		// For example, both sides have a One, Three, and a Four.
		// Red has an unknown One and Blue has a known One,
		// making Red Three invincible.  If Red and Blue exchange
		// Threes, then Red's Four becomes invincible.
		//
		// If Red did not have a Four, then the exchange would
		// benefit Blue.  But then Red will eschew the
		// exchange anyway, because its pieces are more valuable
		// due to the lack of a Four.
		//
		// Therefore, invincible pieces do not have a higher value.

		// Sum the values of the remaining piece ranks.
		// This is used to determine whether the AI is winning.
		// If the AI is winning, it seeks exchanges as a
		// way to enter the endgame with superior ranks.
		// Thus, Eights, Nines and the Spy have little value
		// in this analysis, because it is the superior pieces
		// that count.  Yet these pieces have some value,
		// the ability to discover other pieces (10 points).
		//
		// Note: possibleFlag() depends on sumValues
		// Note: isWinning() revalues Eights.
		// 
			sumValues[c] = 0;
			for (rank = 1; rank <= 10; rank++) {
				int n = rankAtLarge(c, rank);
				if (rank >= 8)
					sumValues[c] += 10 * n;
				else
					sumValues[c] += values[c][rank] * n;
			}

		} // color

		// dangerousUnknownRank is set when an opponent
		// has an invincible unknown rank.  This changes
		// how the AI views pieces that approach its unknown
		// pieces.  Normally, the AI assumes that the opponent
		// will not subject its unknown low ranked pieces to attack.
		// But if the opponent has an invincible unknown rank,
		// an unknown opponent piece might approach an unknown AI piece
		// as part of an effort to capture a known AI piece,
		// because it only risks discovery rather than complete
		// loss of its piece.

		dangerousUnknownRank = 99;
		dangerousKnownRank = 99;
		if ((invincibleRank[Settings.bottomColor] == 1
			&& !hasSpy(Settings.topColor))
			|| (invincibleRank[Settings.bottomColor] != 1
				&& invincibleRank[Settings.bottomColor] >= invincibleRank[Settings.topColor])) {
			if (unknownRankAtLarge(Settings.bottomColor, invincibleRank[Settings.bottomColor]) != 0)
				dangerousUnknownRank = invincibleRank[Settings.bottomColor];
			else if (rankAtLarge(Settings.bottomColor, invincibleRank[Settings.bottomColor]) != 0)
				dangerousKnownRank = invincibleRank[Settings.bottomColor];
		}

		// Destination Value Matrices
		//
		// Note: at depths >8 moves (16 ply),
		// these matrices may not necessary
		// because these pieces will find their targets in the 
		// move tree.  However, they would still be useful in pruning
		// the move tree.
		for (int c = RED; c <= BLUE; c++)
		for (int rank = 0; rank < 15; rank++) {
			planA[c][rank][0][0] = 0;
			for (int j=12; j <= 120; j++) {
				planA[c][rank][0][j] = DEST_VALUE_NIL;
				planA[c][rank][1][j] = 0;
				planB[c][rank][0][j] = DEST_VALUE_NIL;
				planB[c][rank][1][j] = 0;
			}
		}

		// valuePieces should be called after all individual
		// piece values have been determined.
		// Destination Value Matrices depends on piece values
		// so that needs to be called later.

		possibleFlag();
		aiFlagSafety(); // depends on possibleFlag
		valuePieces();
		genValueStealth();	// depends on valuePieces

		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			Rank rank = p.getRank();
			if (rank == Rank.BOMB || rank == Rank.FLAG)
				continue;

		// Encourage lower ranked pieces to find pieces
		// of higher ranks.
		//
		// Note: this is a questionable heuristic
		// because the ai doesn't know what to do once
		// the piece reaches its destination.
		// However, the position can evolve into one
		// where material can be gained.
			chase(p);

		// Encourage higher ranked pieces to flee from pieces
		// of lower ranks
			flee(p);

		}

		// keep a piece of a high rank in motion,
		// preferably a 6, 7 or 9 to discover unknown pieces.
		// 5s are also drawn towards unknown pieces, but they
		// are more skittish and therefore blocked by moving
		// unknown pieces.
		for (int c = RED; c <= BLUE; c++)
			needExpendableRank(c);

		// The ai considers all isolated unmoved pieces to be bombs.
		//
		// So one way to fool the AI is to make the flag 
		// isolated on the front rank (by moving all pieces that
		// surround it) and then the AI will not attack it until
		// it is the last piece remaining.  I doubt that few
		// opponents will ever realize this without reading this code.
		//
		possibleBomb();

		// Opponent bombs that block the lanes can be a benefit or
		// a detriment to the AI.  Removing them costs an eight,
		// so unless there is good reason to remove them, 
		// keep the lane blocked off.
		// valueLaneBombs();
		genWinRank();
		targetUnknownBlockers();
		genPieceLists();

		assert flag[Settings.topColor] != null : "AI flag unknown";
	}

	void needExpendableRank(int c)
	{
		// if player is not winning, it is best to let
		// opponent attack and try for a draw

		if (isWinning(c) < 0)
			return;

		int count = 0;
		for ( int r : expendableRank )
			if (isActiveRank(c, r))
				count++;

		if (count >= 2)
			return;

		// if there are not any high rank pieces in motion
		// set the neededRank.  The search tree will
		// move the rank that can make the most progress.
		for (int r : expendableRank)
			// if (activeRank[c][r-1] == null)
			setNeededRank(c,r);
	}

	//
	// Generate piece lists for move generation.  This is the
	// list of all pieces except for Bombs and Flags.
	//
	// Note: suspected Bombs and Flags are also included,
	// because they could turn out to be some other piece.
	// For example:
	// -- R1
	// -- --
	// -- BS
	// BB BF
	//
	// Blue Spy is suspected to be a Bomb.  R1 moves towards
	// Blue Spy and loses.
	//
	void genPieceLists()
	{
		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			Rank rank = p.getRank();
			if (p.isKnown()
				&& (rank == Rank.BOMB || rank == Rank.FLAG))
				continue;

			pieces[p.getColor()][npieces[p.getColor()]++]=p;
		}

		pieces[0][npieces[0]++]=null;	 // null terminate list
		pieces[1][npieces[1]++]=null;	 // null terminate list
	}

	// Perhaps the key and most complex decision in Stratego is whether
	// to lose stealth and capture a piece.  The decision is based on the
	// probability of capturing a yet lower ranked piece or preventing
	// the capture of one of its lower ranked pieces by maintaining
	// continued stealth.
	//
	// For example, an unknown Marshal should not take a Four
	// (if the opponent still has a Two and two Threes),
	// but certainly a Three is tempting.
	// An unknown General should not take a Five, but a Four
	// would be tempting.
	//
	// Stealth value for pieces (1-5) is slightly less than
	// the average value of next higher ranked pieces
	// (player's and opponents)
	// still on the board times a risk factor if stealth is maintained.
	//
	// The risk factor is 0.4, which is a 40% probability
	// of capture of opponent piece
	// or prevention of capture of its own piece).
	//
	// So a One stealth value is equal to 400 *.4 (160 points)
	// if its Two or opponent's Two is still on the board.
	//
	// A Two stealth value is equal to 200 *.4 (80 points)
	// if its Threes or opponent's Threes are still on the board.
	//
	// A Three stealth value is equal to 100 *.4 (40 points)
	// if its Fours or opponent's Fours are still on the board.
	//
	// A Four stealth value is equal to 50 * .4 (20 points)
	// if Fives are still on the board.
	//
	// A Five stealth value is equal to 30 * .4 (12 points)
	// if Sixes are still on the board.
	//
	// Stealth value for pieces (6-9) derives not from opponent piece value,
	// but from opponent stealth value and bluffing.
	// Stealth value increases with increasing rank.  For example,
	// a known Nine has little value, particularly if the opponent
	// no longer has any Nines, because it almost always loses any attack.
	// Therefore its only value is the stealth value of an opponent piece.
	// An unknown Nine has this value AND the ability to bluff.
	//
	// In the example below, Red Six and Red Seven are unknown
	// and Blue Nine is known.  Red has a choice between R6xB9 and R7xB9.
	// Because the stealth value of a Seven is higher than a Six,
	// Red should play R6xB9.  Thus the Seven remains cloaked and
	// can bluff whereas known Red Six is slightly more of a deterrent
	// (and bait) to opponent pieces than a Red Seven.
	// R6 B9 R7
	//
	// Thus, the AI intends to keep its lower ranked and higher ranked
	// pieces cloaked while using its middle pieces for fighting.
	// Stealth value for 6-9 is:
	// Six 5
	// Seven 8
	// Eight 11
	// Nine 14
	//
	// If a piece is invincible without compare (it always wins
	// any attack), it has no stealth, because we want to encourage
	// the AI to use it to clean up the last remaining opponent
	// pieces on the board.
	//
	// Stealth value of opponent pieces is calculated somewhat
	// differently.  One can argue that because the ai
	// already has guessed the rank, that the piece would have
	// less stealth value, but because the ai cannot
	// rely on the guessed rank and because of the importance
	// of identifying low ranked pieces accurately, stealth
	// is still very important.
	//
	// The AI should not risk its more valuable pieces on 
	// just to discover the actual rank of an opponent piece.
	// For example, if Red Four has a chance to attack an unknown
	// Blue that it suspects is a One, it should flee and leave
	// the discovery to a lesser piece.
	//
	// The important ranks to discover are Ones, Twos and Threes.
	// Often, these suspected ranks can be confused, so there is not
	// much difference in stealth between them.
	// So for an opponent rank (1-3), stealth is:
	// One 60
	// Two 40
	// Three 30
	// Four 15
	//
	// For example,
	// B? B5 R4
	// The AI should not play R4XB5 if B? is a suspected Two, because the
	// value of a Four (100) > Five (50) + Two Stealth (40).
	// (This is the same if B? is unknown, see unknownValue())
	//
	// For example,
	// B? B? R5
	// The AI should not play R5XB? if B? is a suspected Four, because the
	// value of a Five (50) > Unknown (30) + Four Stealth (15).
	//
	// This means that higher ranked pieces (6,7,9) will be eager
	// to sacrifice themselves to discover ranks (1-2) and the
	// ranks (7,9) are eager to discover a Three.
	//
	// Another area where stealth values are important is in
	// unknownValue().  If an AI Four has a choice between
	// an exchange with a suspected rank of Three or an Unknown,
	// the difference is the Three stealth or Two stealth, so
	// the Four will be inclined to take its chances with the Unknown.
	//

	private void genValueStealth()
	{
		for (int c = RED; c <= BLUE; c++) {
		for (int r = 1; r < 15; r++) {
		int v = 0;
		if (r >= 6 && r <= 9)
			v = 5 + (r - 6) * 3;

		// Unknown Spy Flag stealth
		else if (r > 9)
			v = values[c][r]/5;

		// If the player is winning by more than the value
		// of the rank, keep valueStealth of (1-5) at zero
		// to encourage reduction of pieces.
		//
		// If the rank is the lowest invincible, keep it cloaked.
		// For example, Red has an
		// unknown One, two known Threes, and an unknown Four.
		// Blue has a known Two and an unknown Four.
		// invincibleRank[Red] = 4
		// invincibleRank[Blue] = 1
		// Red is winning by more than the value of a One,
		// but the unknown One should remain cloaked to
		// negate the power of the opponent Two.

		else if (isWinning(c) > values[c][r]
			&& r > invincibleRank[1-c])
			v = 0;

		else if (c == Settings.bottomColor && r <= 4) {
			if (r == 1)
				v = 60;
			else if (r == 2)
				v = 40;
			else if (r == 3)
				v = 30;
			else if (r == 4)
				v = 15;
		} else {
			int count = 0;
			int rs;
			for (rs = r+1; rs<8 && count == 0; rs++) {
				int n = rankAtLarge(1-c, rs);
				v += values[1-c][rs] * n;
				count += n;

				if (rs > invincibleRank[c]) {
					n = knownRankAtLarge(c, rs);
					v += values[c][rs] * n;
					count += n;
				}

			}

			if (rs == 8)
				v = values[c][r]/6;
			else
				v = v/count*4/10;
		}

		valueStealth[c][r-1] = v;

		} // rank

		// Bombs not surrounding the flag have no value,
		// but they do have stealth value, proportional to
		// the number of unknown bombs remaining.
		// Because if the player discovers all the bombs,
		// then all the remaining pieces are at risk.
		valueStealth[c][Rank.BOMB.toInt()-1] =
			(6 - unknownRankAtLarge(c, Rank.BOMB))*10;

		} // color
	}

	void valuePieces()
	{
		// is the AI winning?
		// if so, its pieces are worth less
		// to encourage it to exchange ranks
		// if not, its pieces are worth slightly more
		// to encourage it to avoid exchanging ranks
		//
		// But not too much more so it allows lower ranks
		// to be captured for higher ranks.  For this to work,
		// the value difference between ranks must be greater than
		// 1/6.
		//
		// if (sumValues[Settings.topColor] == 0) and
		// its flag is bombed and the opponent has an 8
		// the ai should surrender
		//
		if (sumValues[Settings.topColor] != 0)
		for (int rank = 1; rank <= 10; rank++) {
			long v = values[Settings.topColor][rank]/6;
			v *= sumValues[Settings.bottomColor];
			v /= sumValues[Settings.topColor];
			values[Settings.topColor][rank] =
				values[Settings.topColor][rank]*5/6 + (int)v;
		}
	}

	// An unknown piece blocking the destination
	// of a low ranked piece is worth discovering,
	// because it might be bluffing.
	// Perhaps it is blocking access to the flag?
	//
	// TBD: this code needs to be rethought out.  The goal is
	// to handle situations like the following example:
	// Red Two is cornered by unknown Blue, which probably is
	// bluffing to keep Red Two corralled.
	//
	// -- -- R? R?
	// B? -- R2 R?
	// -- -- xx xx
	// 
	void targetUnknownBlockers()
	{
		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece tp = getPiece(i);
			if (tp == null || tp.isKnown() || !tp.hasMoved())
				continue;

			// TBD: see below
			// genDestTmp(false, tp.getColor(), i, DEST_PRIORITY_BLOCKER);
			for (int lowr : importantRank) {
				Piece fp = activeRank[1-tp.getColor()][lowr-1];
				if (fp != null
					&& planv(planA[fp.getColor()][lowr-1], fp.getIndex(), i) > DEST_PRIORITY_CHASE) {
					tp.setBlocker(true);

				}
			}
		}
	}

	//
	// Flee from the piece p for pieces of higher "chaseRank"

	// A goal of this logic is to avoid becoming trapped by
	// a team of lower ranks.  In the example below, Red Three
	// should sense the approach of Blue One and Blue Two
	// and cease its attack on Blue Four.
	// (Fleeing takes priority over chasing).
	// x R3 -- -- B1
	// x -- -- -- --
	// x R4 B2 -- --
	// xxxxxxxxxxxxx
	//
	// This logic is also an offensive tactic, as the AI will
	// try to trap an opponent piece between on its side of
	// the board.
	//
	// As with the chase heuristic, more search depth would render
	// this superfluous.
	
	protected void flee(Piece p)
	{
		int retreat[][] = {
			{1, 1, 1, 1, 1},
			{2, 2, 2, 2, 2},
			{3, 3, 3, 3, 3},
			{4, 4, 4, 4, 4},
			{5, 5, 5, 5, 5}
		};

		int fi = p.getIndex();
		int fx = Grid.getX(fi);
		int fy = Grid.getY(fi);

		int[] tmp = new int[121];
		for (int j = 0; j <= 120; j++)
			tmp[j] = DEST_VALUE_NIL;

		for (int j = 0; j < 5; j++)
		for (int k = 0; k < 5; k++) {
			int x = fx + k - 2;
			int y = fy + j - 2;
			if (x < 0 || x > 9 || y < 0 || y > 9)
				continue;
			int i = Grid.getIndex(x, y);
			if (p.getColor() == Settings.topColor)
				tmp[i] = retreat[j][4-k];
			else
				tmp[i] = retreat[j][k];
		}

		if (p.isKnown() || p.isSuspectedRank())
			for (int j = 5; j > p.getRank().toInt(); j--) {
				if (lowerRankCount[p.getColor()][j-2] >= 2) {
					setFleePlan(planA[1-p.getColor()][j-1], tmp, DEST_PRIORITY_FLEE);
					setFleePlan(planB[1-p.getColor()][j-1], tmp, DEST_PRIORITY_FLEE);
				}
			}
		else

		// While an opponent piece does not deliberately
		// flee from an unknown ai piece of lower rank,
		// it is a negative result for the opponent to approach it.
		// One reason is because if an ai piece of higher
		// rank is fleeing and has a choice of fleeing directions,
		// where one direction is towards a lower ranked ai piece, it
		// should flee that direction because it is able to
		// to increase its distance away from the attacker
		// or draw the attacker towards the unknown ai piece
		// of lower rank.  This is one way of achieving
		// the desired result of an ai piece finding a lower
		// ranked protector.


			for (int j = 4; j > invincibleWinRank[1-p.getColor()]; j--) {
		// The AI discourages its low ranked pieces from getting
		// too close to unknown unmoved pieces,
		// because this is often how a piece becomes trapped.
		// This does not apply to its known invincible pieces,
		// which it allows to get close to any piece.

				if (knownRankAtLarge(1-p.getColor(), j) != 0) {
					if (p.hasMoved() || j <= invincibleRank[1-p.getColor()])
						continue;

		// If the chasing rank is unknown, it is discouraged
		// from getting close to *any* unknown piece,
		// because this is how it can become discovered.

				} else if (hasFewExpendables(p.getColor()))
					continue;

				setFleePlan(planA[1-p.getColor()][j-1], tmp, DEST_PRIORITY_FLEE);
				setFleePlan(planB[1-p.getColor()][j-1], tmp, DEST_PRIORITY_FLEE);
			}
	}

	protected void chaseWithUnknownExpendable(Piece p, int tmp[])
	{
		for (int r : expendableRank) {
			Piece a = activeRank[1-p.getColor()][r-1];
			if (a != null) {
				if (a.isKnown())
					continue;

		// If the expendable has fled from this rank before,
		// it is no longer a convincing bluffer.
		// Note that it could have fled from a higher ranked
		// piece, and still be a convincing bluffer, because
		// it may have fled to avoid detection (or capture,
		// in the case of a Spy).

				if (a.getActingRankFlee() == p.getRank())
					continue;
			}

			genPlanA(rnd.nextInt(2), tmp, 1-p.getColor(), r, DEST_PRIORITY_CHASE);
			genPlanB(rnd.nextInt(2), tmp, 1-p.getColor(), r, DEST_PRIORITY_CHASE);
		}
	}

	// Chase the piece "p"
	protected void chase(Piece p)
	{
		int i = p.getIndex();
		int chasedRank = p.getRank().toInt();
		int[][] destTmp = new int[2][];	// encourage forward motion
		for (int guarded = GUARDED_OPEN ; guarded <= GUARDED_UNKNOWN; guarded++) {
			destTmp[guarded] = genDestTmp(guarded, p.getColor(), i);
			// this nulls out incentive for chase sequences
			destTmp[guarded][i] = DEST_VALUE_NIL;
		}

		if (p.isKnown() || p.isSuspectedRank()) {
		// no point in chasing a Nine because
		// it can easily get away
			if (p.getRank() == Rank.NINE)
				return;

		// Only the closest non-invincible known rank is assigned
		// to chase an opponent piece.
		// This prevents a pile-up of ai pieces
		// chasing one opponent piece that is protected
		// by an unknown and possibly lower piece.
		//
			int found = 0;
			int minsteps = 99;
			for (int j = chasedRank - 1; j > invincibleRank[1-p.getColor()]; j--)
				if (knownRankAtLarge(1-p.getColor(),j) != 0
					&& Grid.steps(activeRank[1-p.getColor()][j-1].getIndex(), i) < minsteps) {
					minsteps = Grid.steps(activeRank[1-p.getColor()][j-1].getIndex(), i);
					found = j;
				}

		// Generate planA *and* planB.
		// If there are multiple moved pieces of the rank,
		// they will all chase the piece.

			if (found != 0) {
				genPlanA(rnd.nextInt(2), destTmp[GUARDED_UNKNOWN], 1-p.getColor(), found, DEST_PRIORITY_CHASE);
				genPlanB(rnd.nextInt(2), destTmp[GUARDED_UNKNOWN], 1-p.getColor(), found, DEST_PRIORITY_CHASE);
			}

		// Chase a known One with a Spy.
		// The chase is limited to the AI side of the board
		// until there are no opponent expendable pieces remaining
		// as a gross effort to limit the Spy's vulernability.

			else if (chasedRank == 1
				&& p.isKnown()
				&& (p.getColor() == Settings.topColor
					|| hasFewExpendables(p.getColor())
					|| p.getIndex() < 56))
				genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), 10, DEST_PRIORITY_CHASE);

		// Only 1 active unknown piece
		// is assigned to chase an opponent piece.
		//
		// A non-active rank is occasionally summoned
		// to avoid a draw when the lack of known lower ranked
		// pieces causes both sides to stand pat.
		// This also helps to make the AI somewhat less predictable.
		//
		// Note the use of GUARDED_OPEN.  See note below.
		//
			else
			for (int j = chasedRank - 1; j > invincibleRank[1-p.getColor()]; j--)
				if (rankAtLarge(1-p.getColor(),j) != 0) {
					Piece chaser = activeRank[1-p.getColor()][j-1];
					if (chaser != null || rnd.nextInt(50) == 0) {
						if (chaser != null && !chaser.isKnown())
							genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
						else
							genNeededPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
						break;
					}
				}

		// If the chase does not involve a known chaser,
		// send unknown expendable pieces to act as
		// a low ranked chasers.
		// This bluff can lead to forcing
		// the defender into making a bad decision,
		// such as attacking a bomb or the actual lower
		// ranked piece.
		// It can also box in the defender, so it has to move
		// some other pieces other than its known pieces.

		// If the chased piece is not known, it is still
		// worthwhile to send unknown expendable pieces
		// to push the chased piece into these pieces
		// so as to confirm the rank of the chased piece.
		// (There need not be a known chaser or the chaser
		// might be invincible).
		
		// Send all unknown expendable pieces, active or not.
		// Multiple pieces chasing the opponent low ranked
		// piece can create an opportunity for the real AI
		// piece to attack.
		//
		// (Recall that the AI always keeps an expendable piece
		// in motion, so call genPlan rather than genNeededPlan.
		// That rank could be known, so no chase will commence
		// until an unknown expendable piece moves, which usually
		// doesn't take very long to happen.)

			if (found == 0
				&& chasedRank <= 4
				&& (!isInvincible(p)
					|| chasedRank == 1 && hasSpy(1-p.getColor()))
				|| chasedRank == Rank.SPY.toInt())
				chaseWithUnknownExpendable(p, destTmp[GUARDED_UNKNOWN]);

		} else if (p.hasMoved()) {
		// chase unknown fleeing pieces as well
			if (p.getActingRankFlee() != Rank.NIL
				&& p.getActingRankFlee().toInt() <= 4)
				for (int j = p.getActingRankFlee().toInt(); j >= invincibleRank[1-p.getColor()]; j--)
					genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);

			else if (p.getActingRankFlee() == Rank.UNKNOWN) {
				for ( int j : expendableRank ) {
					genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
					genPlanB(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
				}

			} else if (p.getActingRankFlee() != Rank.NIL
				&& p.getActingRankFlee().toInt() >= 5) {
					genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), p.getActingRankFlee().toInt(), DEST_PRIORITY_CHASE);
					genPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), p.getActingRankFlee().toInt()+1, DEST_PRIORITY_CHASE);
			}

		// Also use Fives to chase unknown pieces
		// The AI doesn't have much other use for Fives
		// so this gives them something to do.

			genNeededPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), 5, DEST_PRIORITY_CHASE);
			genPlanB(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), 5, DEST_PRIORITY_CHASE);
		//
		// Chase completely unknown pieces with known AI pieces
		// and expendable unknown piece.
		//
		// If the unknown piece chases back, then
		// it will get a chase rank and the AI piece will no
		// longer chase it.  This is how the AI tries to assess
		// the rank of unknown piece without actually attacking it.
		
			for ( int j = 9; j >= 1; j--)
				if (knownRankAtLarge(1-p.getColor(), j) != 0) {
					genPlanA(destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
					genPlanB(destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
				}
			for ( int j : expendableRank ) {
				genPlanA(destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
				genPlanB(destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
			}
				
		} else { // unknown and unmoved

		// The lowest priority for expendable pieces is to
		// sacrifice themselves on unknown unmoved pieces
		// as a last ditch effort to avoid a draw.  Only Plan B
		// is given to avoid conflict with the higher priority Plan A.
		// Note that GUARDED_MOVED is set to avoid conflict
		// with moved pieces.

			if (unmovedValue[i] > VALUE_MOVED) {
				for ( int j : expendableRank )
					if (knownRankAtLarge(1-p.getColor(), j) != 0) {
						int destTmp2[] = genDestTmpGuarded(p.getColor(), i, Rank.toRank(j));
						genPlanA(destTmp2, 1-p.getColor(), j, DEST_PRIORITY_LOW);
						genPlanB(destTmp2, 1-p.getColor(), j, DEST_PRIORITY_LOW);
					}
			}
			return;
		}

		// Multiple lower known invincible ranks
		// and all the pieces of the rank will chase an opponent piece.
		//
		// Because lower ranks are often oversubscribed,
		// a secondary plan B is used that applies
		// to the non-active piece of the rank.
		// For example, this allows one Three to protect
		// the flag, while the other chases opponent
		// pieces, or attacks the opponent flag.
		//
		// Because the ai does not evaluate if
		// the chase will be worthwhile, it does not
		// call genNeededPlanA or B, because that would
		// cause it to move its low ranked pieces
		// which could then become subject to attack by
		// the opponent.
		//
		// A piece can be called up for the chase by
		// calling getNeededPlanA or B 
		// only if the piece is a known invincible piece,
		// the piece is an invincible win piece,
		// or there is only one lower opponent piece of lower
		// known rank remaining and the value of the chased piece
		// is worthwhile to lose stealth to capture.
		//
		// This last condition requires an example.
		// The AI has an unknown invincible Two and the
		// opponent One is known.   The opponent has a known Three
		// or a known Four. This code will move the Two to chase
		// the Three or Four. It will not move the Two to chase
		// a lesser valued piece.
		//
		// It is best not to chase a Two or Three with an unknown One
		// even if the Spy is off the board because if the One
		// is discovered (or guessed because it chased the Two),
		// then the Two becomes invincible.
		// So defensively, keep the One unknown as long as possible.
		// Once it becomes known, then it can chase the Two.
		//
		// On the other hand, an opponent Two or Three
		// can still cause havoc
		// even if the AI One is unknown.  The Two can chase known
		// pieces and block lanes.  So the Two needs to be chased
		// by expendable pieces posing as the One and the One needs
		// to approach the Two, but not chase it, aiming to
		// use the expendable pieces posing as Ones to force
		// the Two to flee towards it.  In addition, the One must
		// always be shielded by other pieces to prevent its
		// discovery.  In the AI implementation, the chase of
		// a known Two by an unknown One
		// is limited to its side of the board.
		//
		// Note that this defensive strategy limits discovery of
		// the opponent Spy.  It is unlikely that the Spy will move
		// to the opposite side of the board if the opponent One
		// is still unknown.  Because the chase is limited to
		// the player's side of the board, indirect discovery of
		// the Spy would not occur.  Thus, the AI does need to
		// send its expendable pieces to chase the Two (and
		// occasionally the One to maintain the bluff) to the
		// opposite side of the board.
		//
		// Ideally, the ai would predict if a chase
		// would be successful, and the only way I know
		// of doing that is to use the search tree.
		// So this code is the best that the ai can do.
		//

		for (int j = 1;
			j <= invincibleRank[1-p.getColor()]
				&& j < chasedRank;
			j++) {
			if (rankAtLarge(1-p.getColor(),j) == 0)
				continue;

			if (knownRankAtLarge(1-p.getColor(),j) != 0) {
				int destTmp2[] = genDestTmpGuarded(p.getColor(), i, Rank.toRank(j));
		// The chase must use the same priority for all pieces,
		// otherwise the chaser will only chase the pieces
		// with the higher chase priority.
		//
		// The sole exception is when the chased rank is invincible,
		// because until the opponent invincible rank is captured or
		// cornered, it will just likely
		// chase the players pieces around the board repetitively
		// until they are captured.  So the players invincible
		// rank needs to chase it alone and not be distracted by
		// any lessor ranks.
		//
		// The known invincible chase piece should corner
		// the chased piece rather than approach it and push it
		// around randomly.  This is especially true if the
		// chased piece is invincible.  The idea is to bring another
		// chase piece towards the chased piece and get the search
		// tree to discover the proper moves to capture it.
		// This also avoids mindless chases around the board.
		// (Mindless chases do often result in material gain,
		// but the goal of this programmer is to avoid them).

				int priority;
				if (isInvincible(p))
					priority = DEST_PRIORITY_CHASE_HIGH;
				else
					priority = DEST_PRIORITY_CHASE;

				genPlanA(1, destTmp2, 1-p.getColor(), j, priority);
				genPlanB(1, destTmp2, 1-p.getColor(), j, priority);

		// The AI is cautious in sending its unknown low ranked piece
		// to chase an opponent pieces because of the risk of
		// discovery of its piece and loss of stealth.
		//
		// One issue is whether to use GUARDED_OPEN or GUARDED_UNKNOWN.
		// With GUARDED_OPEN, the low ranked piece is drawn towards
		// the unknown but  will not be able to chase past an unknown
		// opponent piece because of discovery.  But the expendable
		// pieces can, and the idea is to use the expendable pieces
		// to push the opponent piece into an area where it can
		// be attacked by the low ranked piece. 
		// GUARDED_OPEN increases the risk of discovery of its
		// low ranked piece.
		//
		// With GUARDED_UNKNOWN, opponent pieces can hide
		// in the safety of other unknown pieces,
		// resulting in a draw even when the AI
		// had discovered all of the opponents low ranked pieces.
		//
		// TBD: at some point later in the game, GUARDED_OPEN
		// should be used for both the chaser and the expendables.

			} else if (j <= invincibleWinRank[1-p.getColor()]
				|| (j != 1
					&& lowerRankCount[p.getColor()][j-2] < 2
					&& valueStealth[1-p.getColor()][j-1] < values[p.getColor()][chasedRank])) {
				genPlanA(rnd.nextInt(2), destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
				// tbd: PlanB as well
				chaseWithUnknownExpendable(p, destTmp[GUARDED_UNKNOWN]);

		// The stealth of the AI One (like the Spy) is so important
		// that it cannot be risked by sending it to the opposite
		// side of the board until all the opponents expendable
		// pieces have become known.  Until then, it is
		// limited to a defensive position.

			} else if (j == 1
				&& p.isKnown()
				&& valueStealth[1-p.getColor()][j-1] < values[p.getColor()][chasedRank]
				&& (p.getColor() == Settings.topColor
					|| hasFewExpendables(p.getColor())
					|| p.getIndex() < 56)) {
				genPlanA(rnd.nextInt(2), destTmp[GUARDED_UNKNOWN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
				// tbd: PlanB as well
				chaseWithUnknownExpendable(p, destTmp[GUARDED_UNKNOWN]);
			}
		} // for

		// Chase the piece
		// with the same rank IF both ranks are known
		// and the attacker is winning.
		//
		// Unless the ai is winning by much, it must keep its low
		// ranked pieces concealed for further advantage,
		// so that is why it will only chase a known piece
		// with another known piece of the same rank.
		//
		// But if the ai is winning by at least a rank,
		// the ai sets valueStealth to zero, so that
		// the exchange of an unknown piece for a known piece
		// furthers the game.
		//
		// If the opponent has an invincible piece,
		// the AI must chase it to attempt an exchange.
		// While this just leads to another opponent rank becoming
		// invincible, it is the only way to end the game,
		// because otherwise the invincible piece just runs amok.
		// Eventually, if the AI is indeed winning, the
		// opponent will run out of invincible pieces.
		// (See EVEN).

		if (p.isKnown()
			&& (knownRankAtLarge(1-p.getColor(), chasedRank) != 0
				|| valueStealth[1-p.getColor()][chasedRank-1] == 0
				|| (isInvincible(p)
					&& !(chasedRank == 1 && hasSpy(1-p.getColor()))))
			&& isWinning(1-p.getColor()) >= 0)

			// go for an even exchange
			genNeededPlanA(rnd.nextInt(2), destTmp[GUARDED_OPEN], 1-p.getColor(), chasedRank, DEST_PRIORITY_CHASE);
	}

	// cache the winFight values for faster evaluation
	private void genWinRank()
	{
		for (Rank frank : Rank.values())
		for (Rank trank : Rank.values())
			winRank[frank.toInt()][trank.toInt()]
				= frank.winFight(trank);
	}

	private int unknownRankAtLarge(int color, int r)
	{
		return Rank.getRanks(Rank.toRank(r))
			- trayRank[color][r-1]
			- knownRank[color][r-1];
	}

	private int unknownNotSuspectedRankAtLarge(int color, int r)
	{
		return unknownRankAtLarge(color, r) - suspectedRankAtLarge(color, r);
	}

	private int unknownNotSuspectedRankAtLarge(int color, Rank rank)
	{
		return unknownNotSuspectedRankAtLarge(color, rank.toInt());
	}

	private int unknownRankAtLarge(int color, Rank rank)
	{
		return unknownRankAtLarge(color, rank.toInt());
	}

	private int knownRankAtLarge(int color, int r)
	{
		return knownRank[color][r-1];
	}

	private int rankAtLarge(int color, int rank)
	{
		return (Rank.getRanks(Rank.toRank(rank)) - trayRank[color][rank-1]);
	}

	private int rankAtLarge(int color, Rank rank)
	{
		return rankAtLarge(color, rank.toInt());
	}

	private int suspectedRankAtLarge(int color, int r)
	{
		return suspectedRank[color][r-1];
	}

	private int suspectedRankAtLarge(int color, Rank rank)
	{
		return suspectedRankAtLarge(color, rank.toInt());
	}

	// The number of unknown expendable pieces still at large
	// greatly determines the relative safety of a valuable rank
	// from discovery.  Note that expendables include Eights and
	// because some expendable ranks may remained buried
	// in bomb structures, this is rarely zero.
	private boolean hasFewExpendables(int color)
	{
		return nUnknownExpendableRankAtLarge[color] <= 4;
	}

	// Target ai or opponent flag
	//
	// Static position analysis is a poor substitute
	// for the search tree in determining whether the flag
	// is susceptible to attack and creating countermeasures.
	// But because the search tree
	// is shallow, the AI may not realize the opponent can
	// capture the flag before it is too late.
	//
	// One often used strategy is to simply load the flag area
	// with guards of various ranks.  While effective, this
	// defensive strategy often leads to draws or even losses
	// if the opponent is persistent.  It also makes it obvious
	// where the flag is located.
	//
	// Instead, the AI does open path analysis and always keeps
	// a defender of appropriate rank one step closer to the
	// square adjacent to the flag on the opponent approach path.
	//
	// (Note: it may well be possible to use a limited search tree
	// to accomplish a better result, albeit with much more
	// computation.)

	private void flagTarget(Piece flagp)
	{
		int color = flagp.getColor();
		int flagi = flagp.getIndex();

		{
		int[] destTmp = genDestTmp(GUARDED_UNKNOWN, color, flagi);

		if (flagp.isKnown()) {

		// Keep an expendable piece handy to ward off any approaching
		// unknowns

			for (int r : expendableRank) {
				Piece a = activeRank[color][r-1];
				if (a != null
					&& a.isKnown())
					continue;
				genPlanA(destTmp, color, r, DEST_PRIORITY_DEFEND_FLAG);
				break;
			}

		}
		}

		// Even when the flag is not completely bombed,
		// the approach could be guarded by an invincible rank
		// thwarting access.  So Eights are still needed
		// to clear flag structure bombs.
		for (int d : dir) {
			int bi = flagi + d;
			Piece bp = getPiece(bi);
			if (bp != null && bp.getRank() == Rank.BOMB) {
				int[] destTmp = genDestTmpGuarded(color, bi, Rank.EIGHT);
				genNeededPlanA(0, destTmp, 1-color, 8,  DEST_PRIORITY_ATTACK_FLAG);
			}
		}

		// TBD:
		// How to bluff the opponent into thinking some other
		// piece is the flag while still guarding the flag?
		if (color == Settings.topColor
			&& flagp != flag[color])
			return;

		// Note: If a strong piece has not moved, the ai does
		// not move it to protect the flag.  This is a tradeoff,
		// because while the strongest piece is desired to
		// protect the flag, moving it makes it subject to attack.
		// So only the strongest moved piece protects the flag
		// (checking activeRank) and genNeededPlanA is not called.
		// This also is a subterfuge measure, because if the ai
		// always called in its strongest piece to protect the flag,
		// the opponent could easily guess the location.
		//

		// Note that getDestTmp is called with "color" rather
		// than "1-color" and the plans are also called
		// with "color".  This is correct but not intuitive.
		// This means that the flag defender sees the call
		// to protect its flag only if there is an unguarded
		// path.  (Non-bombed flags are set to known.)
		// As long there is no unguarded path to the flag,
		// the attacker may not risk an attack, so
		// the defender is not called up.  Once there is an unguarded
		// path to the defender of lower rank than the closest
		// opponent, the defender is drawn towards the flag.

		int destTmp[] = genDestTmp(GUARDED_OPEN, color, flagi);
		int stepsAttacker = 99;
		Piece pAttacker = null;
		for (int i = 12; i < 120; i++) {
			Piece p = getPiece(i);
			if (p == null || p.getColor() != 1 - color)
				continue;
			if (destTmp[i] < stepsAttacker) {
				stepsAttacker = destTmp[i];
				pAttacker = p;
			}
		}
		if (pAttacker == null)
			return; // no open path

		// Now that we have found the closest attacker,
		// determine its closest approach to the flag

		int stepsTarget = 99;
		int targetIndex = 0;
		int destTmp2[] = genDestTmp(GUARDED_OPEN, color, pAttacker.getIndex());
		for (int d : dir) {
			int i = flagi + d;
			if (!isValid(i))
				continue;

		// if attacker is adjacent to flag, not much can be done

			if (i == pAttacker.getIndex())
				return;

			Piece p = getPiece(i);
			if (p != null)
				continue;
			if (destTmp2[i] < stepsTarget) {
				stepsTarget = destTmp2[i];
				targetIndex = i;
			}
		}
		assert targetIndex != 0 : "Open path needs open square";

		// targetIndex is the closest point adjacent to flag

		// TBD: Note that if the attacker is unknown
		// the AI relies on a Eight or less to defend.
		// Obviously, that would be no deterrent
		// to a lower-ranked unknown attacker.

		int attackerRank = pAttacker.getRank().toInt();
		if (pAttacker.getRank() == Rank.UNKNOWN)
			attackerRank = 8;

		int destTmp3[] = genDestTmp(GUARDED_OPEN, color, targetIndex);
		int r = getDefenderRank(color, destTmp3, attackerRank, stepsTarget);
		if (r == 0)
			return;	// no imminent danger

		if (flagp.isKnown())
			genNeededPlanA(0, destTmp3, color, r,  DEST_PRIORITY_DEFEND_FLAG);
		else if (isActiveRank(color,r))
			genPlanA(destTmp3, color, r, DEST_PRIORITY_DEFEND_FLAG);
	}

	// Determine if piece at "index" is at risk of attack
	// and if so, what rank should be moved to protect it?

	// TBD: a design flaw here is that ranks are given plans
	// but not specific pieces.  The AI can assign planA to
	// the active piece of a given rank and planB to all other
	// pieces of the rank.  So if there happens to be multiple
	// pieces of the same rank, and the closest piece is not
	// active, this algorithm fails.

	// Of course, this algorithm isn't perfect anyway.  If
	// the attacker has an open path, but the player does not,
	// the AI fails to defend.  This code cannot take the place
	// of the search tree which discovers how to move the players
	// pieces optimally.  This code is a pale effort to improve defensive
	// measures past the search horizon.

	private int getDefenderRank(int color, int destTmp[], int attackerRank, int stepsTarget)
	{
		int stepsDefender = 99;
		Piece pDefender = null;
		for (int i = 12; i < 120; i++) {
			Piece p = getPiece(i);
			if (p == null
				|| p.getColor() != color
				|| p.getRank().toInt() > attackerRank)
				continue;

			if (destTmp[i] < stepsDefender) {
				stepsDefender = destTmp[i];
				pDefender = p;
			}
		}

		if (pDefender == null	// attacker not stopable
			|| stepsDefender < stepsTarget) // no imminent danger
			return 0;

		return pDefender.getRank().toInt();
	}

	// Because the search tree is so shallow that it is often
	// too late to respond to an approaching attacker to the
	// flag bomb structure, defenders must always try to
	// position themselves between the bombs and the attackers.
	// Often an attack on the bomb structure involves a low ranked
	// piece and an unknown or an eight.  The low ranked piece
	// escorts the eight to the bomb structure.  So the defender
	// must push both the low ranked piece and the eight away.
	//
	// If the defender just focused on the closest attacker, it
	// would be possible for a low ranked attacker to draw away
	// the defender and allow the structure to be attacked.
	// For example,
	//
	// -- RB RF RB -- --
	// -- -- RB -- -- --
	// -- -- R1 -- -- --
	// -- -- -- -- -- --
	// -- -- xx xx B8 --
	// -- B1 xx xx -- --
	// Blue One and Blue Eight are both 5 steps away, too far
	// for the search tree depth.  Red One moves to the left
	// to position itself between Blue One and the bomb structure.
	// This allows Blue Eight to approach and attack the right bomb.
	private void flagBombTarget(Piece flagp)
	{
		int color = flagp.getColor();
		int flagi = flagp.getIndex();

		// Try to determine the closest bomb subject to attack
		// TBD: there could be more than one bomb and/or
		// more than one attacker, but this gets complex
		// TBD: if there are two unknown attackers,
		// but if one has a suspected rank, focus on the other one.

		int stepsAttacker = 99;
		int stepsProtector = 99;
		Piece pAttacker = null;
		Piece pProtector = null;
		int closestBomb = 0;
		for (int d : dir) {
			int bi = flagi + d;
			if (!isValid(bi))
				continue;
			Piece bp = getPiece(bi);
			assert (bp != null && bp.getRank() == Rank.BOMB) : "flagBombTarget() called on non-bombed flag";

			int destTmp[] = genDestTmp(GUARDED_OPEN, color, bi);
			for (int i = 12; i < 120; i++) {
				Piece p = getPiece(i);
				if (p == null || p.getColor() != 1 - color)
					continue;
				if (p.isKnown() && p.getRank() != Rank.EIGHT) {
					if (destTmp[i] < stepsProtector) {
						stepsProtector = destTmp[i];
						pProtector = p;
					}
					continue;
				}
				if (destTmp[i] < stepsAttacker) {
					stepsAttacker = destTmp[i];
					pAttacker = p;
					closestBomb = bi;
				}
			}
		} // dir
		if (pAttacker == null)
			return; // no open path

		int approacherIndex = pAttacker.getIndex();
		int pd = Grid.dir(approacherIndex, closestBomb);

		int[] destTmp2 = genDestTmp(GUARDED_OPEN, color, closestBomb+pd);

		// Thwart the approach of the closest unknown or eight piece.
		// Note the use of DEST_PRIORITY_ATTACK_FLAG, because
		// the attacker could be either the AI or the opponent.

		genPlanA(destTmp2, 1-color, pAttacker.getRank().toInt(), DEST_PRIORITY_ATTACK_FLAG);

		int destTmp3[] = genDestTmp(GUARDED_OPEN, color, closestBomb);
		int rank = getDefenderRank(color, destTmp3, 8, stepsAttacker);

		if (rank != 0)
			genNeededPlanA(0, destTmp3, color, rank, DEST_PRIORITY_DEFEND_FLAG_BOMBS);

		// Try to push the protector, if any, out of the way

		if (stepsProtector < stepsAttacker) {
			rank = getDefenderRank(color, destTmp3, pProtector.getRank().toInt(), stepsProtector);
			if (rank != 0) {
				int[] destTmp4 = genDestTmp(GUARDED_OPEN, color, pProtector.getIndex());
				genNeededPlanA(0, destTmp4, color, rank, DEST_PRIORITY_DEFEND_FLAG_AREA);
			}
		}
	}

	// depends on possibleFlag() which can set the ai flag
	// isSuspectedRank to true
	// if it detects an obvious bomb structure

	// AI FLAG:
	// If the ai assumes opponent has guessed its flag
	// and sets the flag to be known,
	// then the ai will leave pieces hanging if the opponent
	// can take the flag, because the ai assumes the opponent
	// is going to take the flag rather than the ai pieces.
	// If the opponent does not exactly know, it
	// may be better to allow the opponent to take the
	// flag in the search tree, moving the ai pieces
	// as if nothing is amiss (bluffing).
	// If the opponent HAS guessed it correctly, then
	// the flag needs to be set known so the ai pieces
	// respond.

	// But the ai also assumes
	// that unknown pieces take bombs and transform
	// into eights.  So this makes the ai vigilant in
	// protecting the bombed area from unknown pieces.

	private void aiFlagSafety()
	{
		Piece pflag = flag[Settings.topColor];

		assert pflag.getRank() == Rank.FLAG : "aiFlag not ai flag?";
		assert pflag.getColor() == Settings.topColor : "flag routines only for ai flag";
		// initially all bombs are worthless (0)
		// value remaining bombs around ai flag

		boolean bombed = true;
		for (int d : dir) {
			int j = pflag.getIndex() + d;
				
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p == null) {
				bombed = false;

		// If flag area had a bomb removed, the flag is known

				if (setup[j] == Rank.BOMB)
					makeFlagKnown(pflag);
				continue;
			}

		// If the opponent is adjacent to flag
		// or flag has a known bomb
		// then the ai guesses that the flag is known

			if (p.getColor() != pflag.getColor()
				|| (p.getRank() == Rank.BOMB && p.isKnown())) {
				makeFlagKnown(pflag);
			}

			if (p.getRank() == Rank.BOMB)
				p.setAiValue(aiBombValue(p.getColor()));
			else
				bombed = false;
		}

		// Target the flag if not bombed,
		// otherwise target the bombs (if there still are eights)

		if (!bombed) {

		// A non-bombed flag is worth about a known Four.
		// So this is like having a known Four that cannot
		// move, so the ai can only act defensively.
		// But it should not risk a Three to save the flag
		// because loss of superior rank during the endgame
		// would mean eventual loss anyway, and remember,
		// the flag really is not known.

			setFlagValue(pflag);
		// opponent color eights are now expendable

			expendableEights(Settings.bottomColor);

		// Setting the flag to always known is questionable
		// because its location is almost never
		// known for sure by the opponent.  If the flag is set
		// to known, the AI will make every effort to prevent
		// the flag from attack.  Therein lies the problem.
		// If the flag is known and can be attacked successfully,
		// the AI is prone to leaving its pieces hanging, because it
		// assumes that the opponents best move is to take the
		// flag rather than its pieces.  This is a horizon effect,
		// and ideally qs() should return a worse result
		// and the AI should attempt to minimize material loss
		// even if the flag can be taken.  However, if the flag
		// attacker reaches the flag at ply N and the AI does not
		// evaluate beyond ply N, the AI will leave its pieces
		// hanging because for the opponent to take an AI piece,
		// it will delay reaching the flag.
		//
		// I do not see a way around this problem.
		// The issue is correctly
		// ascertaining whether the flag is indeed known.  Even
		// if it is not known, the attacker could clumsily attack
		// it and win, so the flag always needs to be known
		// at some point.  Perhaps the ai should assume the flag
		// unknown until an attacker is within some distance?
		// This will hamper its effort to protect the flag if the
		// flag is indeed known but if the distance is less than
		// the maximum search ply, it prevents the horizon effect.
			makeFlagKnown(pflag);

			flagTarget(flag[Settings.topColor]);
		}

		else if ((pflag.isKnown() || pflag.isSuspectedRank())
			&& rankAtLarge(1-pflag.getColor(), Rank.EIGHT) != 0) {
		// Flag value is worth more than an Eight.
		// (because we always want an eight to take the
		// flag rather than the protecting bombs.)

			setFlagValue(pflag);
			flagBombTarget(pflag);
		}
	}

	// Establish location "i" as a flag destination.
	void genDestFlag(int i)
	{
		Piece flagp = getPiece(i);
		int color = flagp.getColor();
		if (color == Settings.bottomColor
			|| flagp.getRank() == Rank.FLAG) {
			setFlagValue(flagp);

		//
		// This eliminates the flag
		// from the piece move lists, which means that
		// it offers no protective value to pieces that
		// bluff against lower ranked pieces.  Hence, a side
		// effect is that a low ranked AI piece in the area
		// will attack the flag, which could lose the game
		// For example,
		// R8 R1
		// BB B7
		// BB BB -- BF
		// R8xBB, B7xR8, R1 attacks the corner bomb which it
		// thinks is the flag, but the flag is actually elsewhere.
		//
			makeFlagKnown(flagp);
		}

		// Send in a lowly piece to find out
		// Note: if the flag is still bombed, the
		// destination matrix will not extend past the bombs.
		// So the lowly piece will only be activated IF
		// there is an open path to the flag.
		int destTmp[] = genDestTmp(GUARDED_OPEN, flagp.getColor(), i);
		for (int k = 10; k > 0; k--) {
			if (k == 10 && rankAtLarge(flagp.getColor(), Rank.ONE) != 0)
				continue;
			if (rankAtLarge(1-flagp.getColor(),k) != 0) {
				genNeededPlanA(0, destTmp, 1-flagp.getColor(), k, DEST_PRIORITY_ATTACK_FLAG);
				break;
			}
		}
	}

	private void genDestBombedFlag(int b[], int maybe_count, int open_count)
	{
		Piece flagp = getPiece(b[0]);
		int color = flagp.getColor();
		int eightsAtLarge = rankAtLarge(1-color, Rank.EIGHT);

		// The ai only uses its eights to attack flag structures
		// if it has enough remaining.

		if (maybe_count - open_count > eightsAtLarge)
			return;

		// Set any remaining pieces in the pattern to Rank.BOMB.
		// It doesn't matter if the piece really is a bomb or not.
		boolean found = true;
		int layer = 1;
		for (int j = 1; b[j] != 0; j++) {
			if (j == 5 && found == false) {
				// First layer of double layer pattern
				// is penetrated.  Check second layer.
				found = true;
				layer = j;
			}
			Piece p = getPiece(b[j]);
			if (p == null
				|| (p.isKnown() && p.getRank() != Rank.BOMB)
				|| p.hasMoved()) {
				found = false;
				continue;
			}

		//
		// If there is only 1 pattern left, the AI goes out
		// on a limb and decides that the pieces in the
		// bomb pattern are known.  This eliminates them
		// from the piece move lists, which means that
		// they offer no protective value to pieces that
		// bluff against lower ranked pieces.  This code
		// works for both AI and opponent bomb patterns.
		//
			if (flagp.getColor() == Settings.bottomColor) {
				p.setSuspectedRank(Rank.BOMB);
				if (maybe_count == 1)
					makeKnown(p);
			} else if (p.getRank() == Rank.BOMB
				&& maybe_count == 1)
					makeKnown(p);
		}

		if (maybe_count == 1) {
			setFlagValue(flagp);
			makeFlagKnown(flagp);
		}

		// Remove bombs that surround a possible flag
		// this code is only for opponent bombs
		if (flagp.getColor() == Settings.bottomColor
			&& rankAtLarge(1-flagp.getColor(), Rank.EIGHT) != 0
			&& found)
			for (int j = layer; b[j] != 0; j++)
				destBomb(b[j], found);
	}

	public int yside(int color, int y)
	{
		if (color == Settings.topColor)
			return y;
		else
			return 9-y;
	}


	// Scan the board setup for suspected flag pieces.
	//
	// This code is designed primarily to detect the opponent flag,
	// but by also scanning the AI's own pieces, it can second
	// guess the opponent where the opponent may think
	// the AI flag may be located.  This tells the AI whether
	// to step up protection on its own flag, and possibly to
	// step up protection on a alternate location in an effort
	// to create a realistic deception that the flag is at
	// the alternate location, but really isn't.

	private void possibleFlag()
	{
		for (int c = RED; c <= BLUE; c++) {
		int [][] maybe = new int[30][];
		int maybe_count = 0;
		int open_count = 0;

		// generate bomb patterns
		int[][] bombPattern = new int[30][8];
		for (int y = 0; y <= 2; y++)
		for (int x = 0; x <= 9; x++) {
			int flag = Grid.getIndex(x,y);
			int bpi = 0;
			bombPattern[y*10+x][bpi++] = flag;
			for (int d : dir) {
				int bi = flag + d;
				if (!isValid(bi))
					continue;
				bombPattern[y*10+x][bpi++] = bi;
			}
			bombPattern[y*10+x][bpi] = 0;	// end of pattern
		}

                for ( int[] bp : bombPattern ) {
			int[] b = new int[8];
			for ( int i = 0; bp[i] != 0; i++) {
				if (c == Settings.topColor)
					b[i] = bp[i];
				else
					b[i] = 132 - bp[i];
			}
			Piece flagp = getPiece(b[0]);
			if (flagp != null
				&& (!flagp.isKnown()
				 	|| flagp.isSuspectedRank()
				 	|| flagp.getRank() == Rank.FLAG)
				&& !flagp.hasMoved()) {
				boolean open = false;
				int k;
				for ( k = 1; b[k] != 0; k++ ) {
					if (setup[b[k]] == Rank.BOMB) {
						open = true;
						continue;
					}
					Piece p = getPiece(b[k]);
					if (p == null
						|| (p.isKnown() && p.getRank() != Rank.BOMB)
						|| p.hasMoved())
						break;
				} // k

				// b[k] == 0 means possible flag structure

				if (b[k] == 0) {
					maybe[maybe_count++]=b;
					if (open) {
					// if a structure had a bomb removed,
					// guess that it holds the flag
						genDestFlag(b[0]);
						open_count++;
					}
				}

			} // possible flag
		} // bombPattern

		// revalue eights
		// (Note: maybe_count == 0 calls expendableEights even
		// if there are not any left.  This is necessary because
		// Flag value > aiBombValue > Eight value.)
		if (maybe_count == 0 || maybe_count < rankAtLarge(1-c, Rank.EIGHT))
			// at least 1 opponent color eight is expendable
			expendableEights(1-c);

		if (maybe_count >= 1) {

			for (int i = 0; i < maybe_count; i++)
				genDestBombedFlag(maybe[i], maybe_count, open_count);

			// eights become more valuable now
			int eightsAtLarge = rankAtLarge(1-c, Rank.EIGHT);
			if (maybe_count <= 3 && maybe_count > eightsAtLarge)
				values[1-c][Rank.EIGHT.toInt()]
					+= (maybe_count - eightsAtLarge) * 30;

		// Low rank piece discovery is much more important
		// than finding the structure that contains the flag.
		// The structure will become evident
		// as the opponent moves pieces and the game progresses,
		// without any need to identify the likely bombs
		// in the structure.
		//
		// The AI waits for the number of possible structures
		// to be reduced and then sends in an Eight to investigate.
		//
		// However, a strong opponent will move as few pieces
		// as possible to thwart structure discovery.  This is
		// especially true if the opponent has an unfavorable
		// position and is satisfied with a draw.
		//
		// This is often the case when the AI plays bots, because
		// most bots (and some players) aimlessly fire off 
		// expendable pieces at the start to probe the enemy's
		// position.  The AI tries to retain its expendable
		// pieces until the middle game and use them to
		// identify low ranked pieces.  So often the AI reaches
		// the middle game with expendable pieces but no clue
		// where the opponent low ranked pieces are located.
		//
		// So if the AI has a favorable mid-game position, it has
		// to use its expendable pieces to probe the opponents
		// unmoved pieces.  This is a last ditch effort to
		// avoid a draw, but could result in discovery of an
		// opponent low ranked piece or reduce the number of
		// possible structures to a point where an Eight
		// can be sent.

		// If the player is winning, opponent should
		// preserve its pieces and not randomly attack,
		// so skip this code.

		// Conversely, if the player is losing, there is value
		// in keeping pieces in structures unmoved to
		// confuse the opponent which structure holds the flag,
		// forcing the opponent to randomly attack.

			if (isWinning(c) > 0)
				continue;

			if (eightsAtLarge >= maybe_count)
				continue;

		// The value of attacking unknown structures increases
		// with the move count and the number
		// of remaining structures.   So when the move count
		// is low, the value of attacking unknown structures is low.
		// When the number of structures decreases,
		// the value decreases.

			long value = undoList.size() * (1 + maybe_count) / 200;

		// But not too high, otherwise the AI will sacrifice
		// two pieces just for one discovery.

			value = Math.min(value, VALUE_SIX);
 
		// Add value to pieces in structure to encourage discovery.
		// Structures are worth more on the back ranks
		// because the flag is more likely in the rear.
		// note: *3 to outgain -depth late capture penalty

			for (int i = 0; i < maybe_count; i++)
			for ( int j = 1; maybe[i][j] != 0; j++ ) {
				int k = maybe[i][j];
				if (k >= 56)
					unmovedValue[k] = (k/11-7) * 3 + (int)value;
				else
					unmovedValue[k] = (4-k/11) * 3 + (int)value;
			}

		// Encourage movement of front line pieces
		// to clear a path so that pieces can move easily
		// from side to side.

			for (int x = 0; x < 9; x++)
				unmovedValue[Grid.getIndex(x, yside(1-c,3))] = -(int)value;

		} else if (c == Settings.bottomColor) {

		// Player color c did not surround his flags with
		// adjacent bombs.  That does not mean the player did
		// not bomb the flag, but perhaps surrounded the flag
		// along with some lowly piece(s) like a Seven.
		// Common setups are:
		// Setup 1:
		// B - -
		// 7 B -
		// F 7 B
		//
		// Setup 2:
		// - B B -
		// B F 7 B
		//
		// These setups are attacked using the standard
		// bomb patterns.  Setup 1 contain 3 patterns.
		// This is the toughest for the AI to crack, because once
		// it removes one bomb and one Seven takes its Eight,
		// the AI will think that the remaining Seven is the Flag,
		// which is certainly possible.
		// Setup 3:
		// B - -
		// 7 B -
		// 7 F B
		//
		// Setup 2 contain 2 patterns.  If the bomb above the
		// Seven is removed and the Seven takes the Eight, the
		// AI will not recognize any bomb pattern .
		//
		// But the AI will choose which of the pieces to target
		// as a flag on the back row based on where any bombs
		// on the second to back row were removed.  The AI
		// will choose the piece directly behind the bomb or
		// next to that piece, if that piece is against the side
		// or has an unmoved or bomb piece next to it.
		// For example, in Setup 1, if the middle bomb is removed,
		// and a Seven takes an Eight, there is still
		// one matching pattern, so the AI will target the unmoved
		// Seven.
		// B - -
		// - 7 -
		// F 7 B
		//
		// But if that Seven moves, then the Flag is targeted
		// (rather than the Bomb or any other back row piece)
		// because the piece is against the side of the board.
		//
		// In Setup 2, if the bomb above the Seven is removed,
		// and the Seven takes the Eight, there are no matching
		// patterns.
		// - B 7 -
		// B F - B
		//
		// The next piece to be targeted is the Flag, because it
		// is next to another unmoved piece.
		//
		// Once this rule is exhausted,
		// go for any remaining unmoved pieces.

			Piece flagp = null;
			for (int x=1; x <= 8; x++) {
				int i = Grid.getIndex(x, yside(c,1));
				if (setup[i] == Rank.BOMB) {
					int flagi = Grid.getIndex(x, yside(c,0));
					Piece flag = getPiece(flagi);
					if (flag != null
						&& (!flag.isKnown() || flag.isSuspectedRank())
						&& !flag.hasMoved()) {
						flagp = flag;
						break;
					}
					flag = getPiece(flagi-1);
					if (flag != null
						&& (!flag.isKnown() || flag.isSuspectedRank())
						&& !flag.hasMoved()) {
						if (x == 1) {
							flagp = flag;
							break;
						}
						Piece p = getPiece(flagi-2);
						if (p != null && (!p.isKnown() || p.getRank() == Rank.BOMB) && !p.hasMoved()) {
							flagp = flag;
							break;
						}
					}

					flag = getPiece(flagi+1);
					if (flag != null
						&& (!flag.isKnown() || flag.isSuspectedRank())
						&& !flag.hasMoved()) {
						if (x == 8) {
							flagp = flag;
							break;
						}
						Piece p = getPiece(flagi+2);
						if (p != null && (!p.isKnown() || p.getRank() == Rank.BOMB) && !p.hasMoved()) {
							flagp = flag;
							break;
						}
					}
				}
			}


		// Try the back rows first.
		// Choose the piece that has the most remaining
		// surrounding pieces.
		// (water counts, so corners and edges are preferred)

			for (int y=0; y <= 3 && flagp == null; y++)  {
			int flagprot = 0;
			for (int x=0; x <= 9; x++) {
				int i = Grid.getIndex(x, yside(c,y));
				Piece p = getPiece(i); 
				if (p != null
					&& (!p.isKnown() || p.isSuspectedRank())
					&& !p.hasMoved()) {
					int count = 0;
					for (int d : dir) {
						Piece bp = getPiece(i+d);
						if (bp == null
							|| bp.getColor() != c)
							continue;
						if (bp.hasMoved())
							count++;
						else
							count+=3;
					}
					if (count > flagprot) {
						flagp = p;
						flagprot = count;
					}
				}
			}
			}

			assert flagp != null : "Well, where IS the flag?";

			setFlagValue(flagp);
			flagTarget(flagp);

		} // maybe == 0 (opponent flag only)

		} // color c
	}

	// Scan the board for isolated unmoved pieces (possible bombs).
	// If the piece is Unknown and unmoved
	// (and if the AI does not already suspect
	// the piece to be something else, usually a Flag),
	// reset the piece rank to Bomb so that the AI pieces
	// will not want to attack it.
	private void possibleBomb()
	{
		for (int i = 78; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece tp = getPiece(i);
			if (tp != null
				&& tp.getRank() == Rank.UNKNOWN
				&& !tp.hasMoved()) {
				boolean found = false;
				for ( int d : dir ) {
					int j = i + d;
					if (!isValid(j))
						continue;
					Piece p = getPiece(j);
					if (p != null && !p.hasMoved()) {
						found = true;
						break;
					}
				}
				if (!found) {
		// transform the piece into a suspected bomb.
					tp.setSuspectedRank(Rank.BOMB);
				}
			}
		}
	}

	// Set a destination for a bomb surrounding a possible flag
	// this code is only for opponent bombs

	// The ai can only guess how to launch an attack
	// on a bomb that it thinks may protect the flag.
	// It sends an invincible low ranked piece and an eight,
	// so it cannot be easily blocked by an unknown opponent piece.
	// The idea is once the low ranked piece reaches the bomb area
	// with the eight trailing behind, the search tree will
	// discover a way for the eight to win the bomb.
	private void destBomb(int j, boolean intact)
	{
		Piece p = getPiece(j);
		if (p == null
			|| (p.isKnown() && p.getRank() != Rank.BOMB)
			|| p.hasMoved())
			return;

		// If the structure is intact, encourage the Eight
		// to sacrifice itself to open the flag to attack
		// by other pieces.  Otherwise, the bomb is worthless
		// to the Eight unless it can get at the flag.  By
		// continuing to remove more bombs around the structure,
		// it opens it up to combined attack by other pieces.
		if (intact)
			p.setAiValue(aiBombValue(p.getColor()));

		// stay near but don't get in the way
		int near;
		if (p.getColor() == Settings.bottomColor) {
			near =  j - 10;
			if (!isValid(near))
				near = j - 12;
			assert isValid(near) : "near is not valid?";
		} else
			near = j;

		// Send a lower ranked piece along to protect the Eight(s)
		// and possibly confuse the opponent about which is which.
		int r;
		for (r = 5; r >= 1; r--)
			if (isActiveRank(1-p.getColor(),r)) {
				int destTmp[] = genDestTmpGuarded(p.getColor(), near, Rank.toRank(r));
				genPlanA(0, destTmp, 1-p.getColor(), r, DEST_PRIORITY_LOW);
				break;
		}

		if (r == 0) {
			for (r = invincibleRank[1-p.getColor()]; r >= 1; r--)
				if (rankAtLarge(1-p.getColor(), r) != 0) {
					int destTmp[] = genDestTmpGuarded(p.getColor(), near, Rank.toRank(r));
					genNeededPlanA(0, destTmp, 1-p.getColor(), r, DEST_PRIORITY_LOW);
					break;
				}
		}

		// Send the miner(s)
		// multiple structures can be investigated in parallel
		int destTmp[] = genDestTmpGuarded(p.getColor(), j, Rank.EIGHT);
		genNeededPlanA(0, destTmp, 1-p.getColor(), 8, DEST_PRIORITY_LOW);
		genPlanB(destTmp, 1-p.getColor(), 8, DEST_PRIORITY_LOW);

		// Send along expendable pieces as a subterfuge measure
		// If the protectors of the bomb structure are outnumbered,
		// they often can be drawn towards the extra unknowns
		// allowing the eight to attack the bomb.
		chaseWithUnknownExpendable(p, destTmp);
	}

	// some bombs are worth removing and others we can ignore.
	// initially all bombs are worthless (0)
	private void valueLaneBombs()
	{
		// remove bombs that block the lanes
		int [][] frontPattern = { { 78, 79 }, {90, 79}, {82, 83}, {86, 87}, {86, 98} };
		for (int i = 0; i < 5; i++) {
			Piece p1 = getPiece(frontPattern[i][0]);
			Piece p2 = getPiece(frontPattern[i][1]);
			if (p1 != null && p1.getRank() == Rank.BOMB
				&& p2 != null && p2.getRank() == Rank.BOMB) {
				int destTmp[] = genDestTmpGuarded(p1.getColor(), frontPattern[i][0], Rank.EIGHT);
				p1.setAiValue(aiBombValue(p1.getColor()));
				genNeededPlanA(0, destTmp, 1-p1.getColor(), 8, DEST_PRIORITY_LOW);

				int destTmp2[] = genDestTmpGuarded(p2.getColor(), frontPattern[i][1], Rank.EIGHT);
				p2.setAiValue(aiBombValue(p2.getColor()));
				genNeededPlanA(0, destTmp2, 1-p2.getColor(), 8, DEST_PRIORITY_LOW);
			}
		}
	}

	// Generate a matrix of consecutive values with the highest
	// value at the destination "to". (Lee's algorithm).
	//
	// Pieces of "color" or opposing bombs
	// block the destination from discovery.  It is the opposite
	// color of the piece seeking the destination.
	//
	// Pieces of the opposite "color" do not
	// block the destination.
	//
	// Seed the matrix with "n" at "to".
	//
	// If guarded is true, then the matrix will avoid passing
	// any moved piece of unknown or superior rank.
	// This is used when sending eights.
	//
	// This matrix is used to lead pieces to desired
	// destinations.
	private int[] genDestTmpCommon(int guarded, int color, int to, Rank guard)
	{
		int[] destTmp = new int[121];
		for (int j = 0; j <= 120; j++)
			destTmp[j] = DEST_VALUE_NIL;

		destTmp[to] = 1;
		ArrayList<Integer> queue = new ArrayList<Integer>();
		queue.add(to);
		int count = 0;
		while (count < queue.size()) {
			int j = queue.get(count++);
			if (!isValid(j))
				continue;
			int n = destTmp[j];

			Piece p = getPiece(j);

		// Any piece blocks the way
		// (I had tried to allow a path through 
		// pieces of opposite "color" to have a 1 move penalty,
		// because the attacker can move these out of the way,
		// but this causes bunching up of attackers).

			if (p != null && j != to)
				continue;

		// check for guarded squares
			if ((guarded == GUARDED_MOVED || guarded == GUARDED_UNKNOWN) && j != to) {
				boolean isGuarded = false;
				for (int d : dir) {
					int i = j + d;
					if (!isValid(i))
						continue;
					if (i == to)
						continue;
					Piece gp = getPiece(i);
					if (gp == null 
						|| gp.getColor() != color)
						continue;

					if (guarded == GUARDED_MOVED) {
						if (!gp.hasMoved())
							continue;
					
						int result = (gp.getRank()).winFight(guard);
						if (result == Rank.WINS
							|| result == Rank.UNK
								&& !((guard == Rank.ONE && !hasSpy(color))
									|| guard != Rank.ONE && guard.toInt() <= invincibleRank[1-color]))
							isGuarded = true;
				if (j == 1 && hasSpy(p.getColor()))
					guarded = GUARDED_UNKNOWN;
				else
					guarded = GUARDED_OPEN;
					} else if (!gp.isKnown())
						isGuarded = true;
				}
				if (isGuarded) {
					destTmp[j] = DEST_VALUE_NIL;
					continue;
				}
			}

			// set the neighbors
			for (int d : dir) {
				int i = j + d;
				if (!isValid(i) || destTmp[i] != DEST_VALUE_NIL)
					continue;

				destTmp[i] = n + 1;
				queue.add(i);
			} // d
		} // while
		return destTmp;
	}

	private int[] genDestTmp(int guarded, int color, int to)
	{
		return genDestTmpCommon(guarded, color, to, Rank.NIL);
	}

	private int[] genDestTmpGuarded(int color, int to, Rank guard)
	{
		return genDestTmpCommon(GUARDED_MOVED, color, to, guard);
	}

	private void genNeededPlanA(int neededNear, int [] desttmp, int color, int rank, int priority)
	{
		genPlanA(neededNear, desttmp, color, rank, priority);
		if (!isActiveRank(color, rank))
			setNeededRank(color, rank);
	}

	private void genNeededPlanB(int [] desttmp, int color, int rank, int priority)
	{
		genPlanB(desttmp, color, rank, priority);
		setNeededRank(color, rank);
	}

	private void genPlanA(int [] desttmp, int color, int rank, int priority)
	{
		setPlan(planA[color][rank-1], desttmp, priority);
	}

	// The value of the destination trails off with distance.
	// This encourages the piece to prefer closer destinations.

	public static int log2nlz( int bits )
	{
	    if( bits == 0 )
		return 0; // or throw exception
	    return 31 - Integer.numberOfLeadingZeros( bits );
	}

	private static int trailing(int n)
	{
		if (n == DEST_VALUE_NIL)
		 	return n;
		return log2nlz(n);
	}

	private void setPlan(int[][] plan, int[] tmp, int priority)
	{
		assert tmp[0] == DEST_VALUE_NIL : "call genDestTmp before setPlan";
		for (int j = 12; j <= 120; j++)
			if (plan[1][j] > priority) {
				if (plan[0][j] == DEST_VALUE_NIL) {
					plan[0][j] = tmp[j];
					plan[1][j] = priority;
				}
			} else if (plan[1][j] < priority) {
				if (tmp[j] != DEST_VALUE_NIL) {
					plan[0][j] = tmp[j];
					plan[1][j] = priority;
				}
			} else if (plan[0][j] > tmp[j])
				plan[0][j] = tmp[j];
	}

	private void setPlan(int neededNear, int[][] plan, int[] desttmp, int color, int rank, int priority)
	{
		if (neededNear == 1) {
			int[]tmp = new int[121];
			if (!isActiveRank(color, rank))
				setNeededRank(color, rank);

			// deter aimless chasing of the target piece, because
			// otherwise it will guess the chaser's rank

			for (int j = 0; j <= 120; j++) {
				if (desttmp[j] == 2)
					tmp[j] = 5;
				else tmp[j] = desttmp[j];
			}

			setPlan(plan, tmp, priority);
		} else
			setPlan(plan, desttmp, priority);
	}

	private void setFleePlan(int[][] plan, int[] tmp, int priority)
	{
		assert tmp[0] == DEST_VALUE_NIL : "call genDestTmp before setPlan";
		for (int j = 12; j <= 120; j++)
			if (plan[1][j] > priority) {
				if (plan[0][j] == DEST_VALUE_NIL) {
					plan[0][j] = tmp[j];
					plan[1][j] = priority;
				}
			} else if (plan[1][j] < priority) {
				if (tmp[j] != DEST_VALUE_NIL) {
					plan[0][j] = tmp[j];
					plan[1][j] = priority;
				}
			} else if (plan[0][j] < tmp[j])
				plan[0][j]= tmp[j];
	}

	private void genPlanB(int [] desttmp, int color, int rank, int priority)
	{
		setPlan(planB[color][rank-1], desttmp, priority);
	}

	private void genPlanB(int neededNear, int [] desttmp, int color, int rank, int priority)
	{
		setPlan(neededNear, planB[color][rank-1], desttmp, color, rank, priority);
	}

	private void genPlanA(int neededNear, int [] desttmp, int color, int rank, int priority)
	{
		setPlan(neededNear, planA[color][rank-1], desttmp, color, rank, priority);
	}

	// The usual Stratego attack strategy is one rank lower.

	Rank getChaseRank(Piece p, int r, boolean rankLess)
	{
		int color = p.getColor();
		int j = r;
		Rank newRank = Rank.UNKNOWN;

		// See if the unknown rank is still on the board.
		if (r <= 5) {
			if (!rankLess)
				j--;
			for (int i = j; i > 0; i--)
				if (unknownRankAtLarge(color, i) != 0) {
					newRank = Rank.toRank(i);
					break;
				}

		// Desired unknown rank not found, try the same rank.
			if (newRank == Rank.UNKNOWN)
				if (unknownRankAtLarge(color, r) != 0)
					newRank = Rank.toRank(r);
		} else {
			if (r == 6)
				j = 5; 	// chaser is probably a Five

			else {

		// Sevens, Eights and Nines are chased
		// by Fives and higher ranks, as long as the chaser rank
		// is lower.
		//
		// This needs to be consistent with winFight.  For example,
		// R9 B? R6
		// All pieces are unknown and Red has the move.  Blue
		// has a chase rank of unknown.
		// R6xB? is LOSES, because winFight assumes that an
		// unknown that chases an unknown is a Five.  So R9xB?
		// must create a suspected rank of Five.  If it created
		// a higher suspected rank, then the AI would play R9xB?
		// just to create a Six so that it can attack with its Six.
		//
		// Note: if a chase rank of Nine results in an Eight,
		// this could cause the AI to randomly attack pieces
		// with Nines hoping they turn out to be Eights
		// that it can attack and win.  So the AI assumes
		// that the chaser is not an Eight, unless all other
		// unknown lower ranked pieces are gone.
		//
				if (lowestUnknownExpendableRank < r)
					j = lowestUnknownExpendableRank;
				else
					j = r - 1;
			}

			for (int i = j; i <= r; i++)
				if (unknownRankAtLarge(color, i) != 0) {
					newRank = Rank.toRank(i);
					break;
				}

		// Desired unknown rank not found.
		// Chaser must be ranked even lower.

			if (newRank == Rank.UNKNOWN)
				for (int i = j-1; i > 0; i--)
					if (unknownRankAtLarge(color, i) != 0) {
						newRank = Rank.toRank(i);
						break;
					}
		}

		// If the piece hasn't moved, then maybe its a bomb
		if (p.moves == 0)
			return Rank.BOMB;

		return newRank;
	}

	void setSuspectedRank(Piece p, Rank rank)
	{
		p.setSuspectedRank(rank);

		// The AI needs time to confirm whether a suspected
		// rank is bluffing.  The more the suspected rank moves
		// without being discovered, the more the AI believes it.

		if (p.moves > 15)
			suspectedRank[p.getColor()][rank.toInt()-1]++;
	}


	//
	// suspectedRank is based on ActingRankChase.
	// If the piece has chased another piece,
	// the ai guesses that the chaser is a lower rank
	// If there are no lower ranks, then
	// the chaser may be of the same rank (or perhaps higher, if
	// bluffing)
	//
	public void genSuspectedRank()
	{
		// Knowing the lowest unknown expendable rank is
		// useful in an encounter with an opponent piece that
		// has approached an AI unknown.  The AI assumes that
		// these piece are expendable, because an opponent
		// usually tries to avoid discovery of its lower ranks.

		lowestUnknownExpendableRank = 0;
		for (int r = 1; r <= 9; r++)
			if (unknownNotSuspectedRankAtLarge(Settings.bottomColor, r) > 0) {
				lowestUnknownExpendableRank = r;
				if (r >= 5)
					break;
			}

		if (lowestUnknownExpendableRank < 5
			&& rankAtLarge(Settings.topColor, Rank.ONE) == 0
			&& unknownNotSuspectedRankAtLarge(Settings.bottomColor, Rank.SPY) > 0)
			lowestUnknownExpendableRank = 10;

		for (int i = 12; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			if (p.getRank() != Rank.UNKNOWN)
				continue;

		// If the opponent still has any unknown Eights
		// assume that the unknown can be an Eight.
		// (This flag can be cleared during the search
		// tree if a Seven or lower ranked piece attacks the unknown)
			if (unknownRankAtLarge(Settings.bottomColor, Rank.EIGHT) != 0)
				p.setMaybeEight(true);

			Rank rank = p.getActingRankChase();
			if (rank == Rank.NIL || rank == Rank.UNKNOWN)
				continue;

			// if (rank == Rank.ONE) {
			//	if (unknownRankAtLarge(p.getColor(), Rank.ONE) != 0)
			//		setSuspectedRank(p, Rank.ONE);

			//} else 
			if (rank == Rank.SPY) {
				if (hasSpy(p.getColor()))
					setSuspectedRank(p, Rank.SPY);

			} else
				setSuspectedRank(p, getChaseRank(p, rank.toInt(), p.isRankLess()));

		} // for

	// chase rank is permanently set only if a chase piece
	// is unprotected (see Board).  However, if a piece with
	// protection approaches an AI piece, the piece should
	// still be considered a chaser.
	// This encourages the AI to move its
	// chased piece away.  If the chaser continues the chase,
	// eventually chase rank will be permanently set.  If not,
	// the AI may approach the chaser like any other unknown piece.
	// For example:
	// xx -- -- xx
	// xx R5 -- xx
	// -- -- B? B?
	// -- B? B? B?
	// If either unknown Blue approaches known Red Five, it will
	// not have a chase rank because it has protection.  But the
	// piece is likely a lower ranked piece.  So Red should move
	// away.  If unknown Blue continues the chase by moving
	// away from its protection, it inherits a permanent chase rank.
	// If not, Red Five may approach
	// the unknown Blue regardless of its previous chase behavior.

		{
		int r = 10;
		UndoMove prev = getLastMove(1);
		if (prev != null) {
			int i = prev.getTo();
			Piece p = getPiece(i);
			if (p != null && p.getRank() == Rank.UNKNOWN)
				for (int d : dir) {
					Piece tp = getPiece(i+d);
					if (tp == null)
						continue;
					if (tp.getColor() == Settings.topColor
						&& tp.isKnown()
						&& tp.getRank().toInt() < r) 
						r = tp.getRank().toInt();
				}
				if (r != 10)
					setSuspectedRank(p, getChaseRank(p, r, false));
		}
		}

		// lowestUnknownRank is used in an encounter
		// with an invincible piece
		lowestUnknownRank = Rank.UNKNOWN.toInt();
		for (int r = 10; r >= 1; r--)
			if (unknownRankAtLarge(Settings.bottomColor, r) != 0
				&& suspectedRankAtLarge(Settings.bottomColor, r) == 0) {
				lowestUnknownRank = r;
			}

		// Another useful count is the number of opponent pieces with
		// lower rank than an invincible rank.  If an invincible rank
		// has only 1 opponent piece of lower rank
		// remaining on the board and that piece is known
		// or suspected (which makes the rank invincible),
		// then it safe for the rank to venture out,
		// because it takes two pieces of lower
		// rank to corner another piece.
		//
		// TBD: Even so, because AI's look ahead is limited,
		// it can be trapped in a dead-end or by the Two Squares
		// rule or forked with another one of its pieces.
		// This can be solved by increasing look-ahead.

		for (int c = RED; c <= BLUE; c++) {
			int count = 0;
			for (int r = 1; r <= 10; r++) {
				if (unknownRankAtLarge(c, r) != 0)
					break;
				count += rankAtLarge(c, r);
				lowerRankCount[c][r-1] = count;
			}
		}
	}

	//
	// TBD: this should be assigned in constructor statically
	//
	public boolean isInvincible(Piece p) 
	{
		Rank rank = p.getRank();
		return (rank.toInt() <= invincibleRank[p.getColor()]);
	}

	//
	// TBD: this should be assigned in constructor statically
	//
	public boolean isInvincibleWin(Piece p) 
	{
		Rank rank = p.getRank();
		if (rank == Rank.ONE && hasSpy(1-p.getColor()))
			return false;
		return (rank.toInt() <= invincibleWinRank[p.getColor()]);
	}

	public boolean isActiveRank(int color, int r)
	{
		return activeRank[color][r-1] != null;
	}

	public int isWinning(int color)
	{
		return sumValues[color] - sumValues[1-color];
	}

	public boolean isExpendable(Rank rank)
	{
		int r = rank.toInt();
		if (r >= 6 && r <= 9)
			return true;
		return false;
	}

	public void setNeededRank(int color, int rank)
	{
		// If the opponent has a known invincible rank,
		// it will be hellbent on obliterating all
		// moved pieces,
		// so movement of additional pieces is discouraged.

		if (rank > dangerousKnownRank)
			return;

		neededRank[color][rank-1] = true;
	}

	public int planv(int [][] plan, int from, int to)
	{
		int vto = plan[0][to];
		int vfrom = plan[0][from];
		int priority = plan[1][from];
		if (vto != DEST_VALUE_NIL
			&& vfrom != DEST_VALUE_NIL
			&& plan[1][to] == priority) {
				if (vfrom > vto)
					return priority;
				else
					return -priority;
			}
		return 0;
	}

	// To be consistent, the remaining opponent piece after an attack is
	// assigned a suspected rank less than the attacker rank.
	//
	// This can help the ai protect another ai piece from an
	// unknown attacker, because if the attacker takes the first
	// ai piece, the ai has a good shot at recapture if the
	// attacker stays on the board with a known rank.
	// Of course, the attacker could be much stronger than predicted,
	// so the ai could make a serious error as well.
	//
	// setSuspectedRank() is called instead of setRank() because
	// the new rank is only a guess.  Once setKnown(true) is called,
	// the code needs to handle the case of a known Unknown.
	// A known Unknown (isKnown() is true) has a suspected rank
	// just like an unknown Unknown, except a unknown Unknown
	// can be any unknown rank still on the board
	// (because it is yet unchallenged)
	// but a known Unknown cannot exceed its suspected rank + 1.
	//
	// One case where this is of particular importance
	// is an encounter where the AI piece was an eight or less:
	// the suspected rank of the unknown becomes a known lower value so
	// that the piece cannot be an eight and therefore
	// cannot take a bomb.  This encourages
	// an ai piece to attack an unknown piece that
	// is within striking distance of a flag bomb.
	//
	// Note: rankWon can be the Flag in which case the newRank
	// will be based on lowestUnknownExpendableRank.
	//
	public void makeWinner(Piece p, Rank rankWon)
	{
		// If the piece is an AI piece
		// or a known opponent piece
		// or a Nine (only can win a Spy)
		// or suspected Bomb or Flag (can win any piece),
		// the piece rank does not change.
		if (p.getColor() == Settings.topColor
			|| (p.isKnown() && !p.isSuspectedRank())
			|| p.getRank() == Rank.NINE
			|| p.getRank() == Rank.BOMB
			|| p.getRank() == Rank.FLAG)
			return;

		// The only piece that wins a bomb is an Eight.
		if (rankWon == Rank.BOMB) {
			p.setSuspectedRank(Rank.EIGHT);
			return;
		}

		// If the piece already has a suspected rank, keep it.
		// For example, if a Nine attacks a suspected One,
		// makeWinner(One, Nine) is a One rather than some
		// rank lower than Nine.
		if (!p.isSuspectedRank()) {
			assert p.getRank() == Rank.UNKNOWN : "Rank is " + p.getRank() + " but expected UNKNOWN to win " + rankWon;

			Rank newRank = Rank.UNKNOWN;
			if (rankWon == Rank.FLAG)
				newRank = Rank.toRank(lowestUnknownExpendableRank);
			else if (rankWon == Rank.ONE) {
				newRank = Rank.SPY;
				// demote SPY value
				p.setAiValue(values[p.getColor()][Rank.UNKNOWN.toInt()]);
			} else
				newRank = getChaseRank(p, rankWon.toInt(), false);

			assert newRank != Rank.UNKNOWN : "Piece " + p.getRank() + " " + p.isSuspectedRank() + " " + p.getActingRankChase() + " " + p.isKnown() + " should not have won (invincible)" + rankWon + " at " + p.getIndex() + " because lowestUnknownRank is " + lowestUnknownRank;
			p.setSuspectedRank(newRank);
		}

		if (rankWon.toInt() <= 8)
			p.setMaybeEight(false);
	}

	public void move(BMove m, int depth, boolean unknownScoutFarMove)
	{
		Piece fp = getPiece(m.getFrom());
		Piece tp = getPiece(m.getTo());
		moveHistory(fp, tp, m);

		if (depth == 0)
			assert hash == hashTest : "bug: hash changed before move "  + m.getFrom() + " to " + m.getTo();

		setPiece(null, m.getFrom());
		int vm = 0;

		// Moving an unknown scout reveals its rank.

		if (unknownScoutFarMove)
			fp.setRank(Rank.NINE);

		Rank fprank = fp.getRank();
		int fpcolor = fp.getColor();

		int r = fprank.toInt()-1;

		if (!fp.isKnown() && fp.moves == 0) {
			if (!neededRank[fpcolor][r])

		// Moving an unmoved piece needlessly is bad play
		// because these pieces can become targets for
		// invincible opponent pieces.  The greater the piece
		// value, the greater the risk.  If the piece
		// is part of a structure that could be construed
		// as a bomb structure, it is best to leave the structure
		// intact to prevent the opponent from guessing the
		// real structure.

				vm += -VALUE_MOVED -values[fpcolor][r]/100 -unmovedValue[m.getFrom()];
		}

		if (tp == null) { // move to open square

		// Give Plan A value to one piece of a rank
		// and Plan B value to the rest of the pieces
		// if the piece has moved or is known

			int v = 0;
			if (activeRank[fpcolor][r] == fp
				|| activeRank[fpcolor][r] == null && neededRank[fpcolor][r])
				v =  planv(planA[fpcolor][r], m.getFrom(), m.getTo());
			else if (fp.hasMoved()
					|| fp.isKnown()
					|| neededRank[fpcolor][r])
				v = planv(planB[fpcolor][r], m.getFrom(), m.getTo());
		// If a nine makes a far move,
		// add planA for if the nine is already known
		// or only negative planA is the nine is not known.
		// This is because nines reach their destination
		// more quickly, and we do not want an unknown nine
		// becoming known just to obtain higher planA value.

			if (unknownScoutFarMove) {
				if (v < 0)
					vm += v;
				makeKnown(fp);
				vm -= stealthValue(fp);
			} else {
				vm += v;
			}

			fp.setIndex(m.getTo());
			fp.moves++;
			setPiece(fp, m.getTo());

		} else { // attack

		// Remove target piece and update hash.
		// (Target will be restored later, if necessary,
		// perhaps with different hash because of
		// change in "known" status and perhaps rank).

			setPiece(null, m.getTo());

			Rank tprank = tp.getRank();

		// Suspected bombs can move, but if they do, they become
		// unknowns.  The AI guesses that certain unknowns are
		// bombs.  This deters pieces other than Eights from
		// attacking them.  But the AI could be wrong, and so
		// allows the suspected bomb piece to move or attack.
		// An unknown AI bomb can move as well, and this is
		// handled in LOSES.  But an unknown opponent bomb
		// could be any piece, so this is handled in UNK.
		//
		// Note that only worthless bombs become unknowns.
		// This allows an Eight to approach them and attack.
		// But for other ranks, this is a bit aggressive, because
		// because the suspected bomb structure could contain a
		// lower ranked piece.

		if ((fprank == Rank.BOMB || fprank == Rank.FLAG)
			&& fpcolor == Settings.bottomColor
			&& (fp.aiValue() == 0 || tprank != Rank.EIGHT)) {
			fp.setRank(Rank.UNKNOWN);
			fprank = Rank.UNKNOWN;
		}

		// note: use actual value for ai attacker
		// because unknown ai attacker knows its own value
		// but opponent attacker is just guessing

			int fpvalue = actualValue(fp);

		// An attack on an unmoved (unknown) piece
		// gains its unmoved value to help avoid draws.
		// This encourages mindless slamming into
		// suspected bomb structures with the likely result of
		// loss of the piece, so limit this to expendable rank.
		// The idea is that if it isn't a bomb, then the
		// number of possible structures is reduced to better
		// target the Eights.  The AI only does this if it
		// thinks it is winning and wants to speed up the game.

			if (tp.moves == 0 && isExpendable(fprank))
				vm += unmovedValue[m.getTo()];

		// The ai assumes that any unknown piece
		// will take a known or flag bomb
		// because of flag safety.  All other attacks
		// on bombs are considered lost.
		//
		// If the unknown has a (suspected) non-eight rank,
		// winFight() would return LOSES.
		// But the opponent piece could be a bluffing eight,
		// so the threat needs to be taken seriously.
		// If the piece is a known Unknown, it is a piece
		// that was attacked in the search tree, so its rank
		// *must* be less than the attacker.
		//
		// RF RB B? -- -- R9
		// RB -- R7 -- -- --
		// -- -- -- -- -- --
		//
		// Red has the move. If R9xB? or R7xB?,
		// the result is a known Unknown.  But R9xB? results
		// in a piece (known Unknown Nine) which still can take the bomb
		// and R7xB? results in a known Unknown Six
		// that cannot take the bomb.
		//
		// Another example:
		// RF RB
		// RB --
		// -- --
		// B? R8 R2
		// -- B3
		//
		// Red has the move.  Unknown Blue has a chase rank of
		// Eight because it approached Red 8.  Red has no choice
		// but to attack Unknown Blue or risk losing the game.
		//
			int result;
			if (tprank == Rank.BOMB && fp.getMaybeEight()) {
				if (tp.isKnown() || tp.aiValue() != 0) {
					fprank = Rank.EIGHT;
					fp.setRank(fprank);
					result = Rank.WINS;	// maybe not
				} else
					result = Rank.LOSES;	// for sure
			} else
				result = winFight(fp, tp);

			switch (result) {
			case Rank.EVEN :
				// assert fprank != Rank.UNKNOWN : "fprank is unknown?";
		// If the attacker is invincible, then the attacker knows
		// that it cannot lose the exchange, but because this
		// exchange is even, tprank must also be the same rank,
		// so this is a known evenly valued exchange,
		// unless the AI piece is unknown (because of stealth).
		// However, if the AI is winning, the AI must neutralize 
		// the attacker's invincible pieces, so the AI deems this
		// as an even exchange, even if it still has stealth.

				if (tp.hasMoved()
					&& fpcolor == Settings.bottomColor
					&& fp.isKnown()
					&& isInvincible(fp)) {
					if  (!tp.isKnown() && isWinning(Settings.topColor) > VALUE_FIVE)
						vm -= 10;
					else
						vm += actualValue(tp) - fpvalue;
				} else if (fpcolor == Settings.topColor
					&& tp.isKnown()
					&& isInvincible(tp)) {
					if  (!fp.isKnown() && isWinning(Settings.topColor) > VALUE_FIVE)
						vm += 10;
					else
						vm += actualValue(tp) - fpvalue;
				}

		// If the defender is known, and either the attacker is known
		// or the AI is the attacker (AI knows its own pieces)
		// then, this is a known even exchange based on actual values.

		// Note that although the exchange is even, the value
		// may not be.  If the AI is winning, its ranks are
		// worth less than opponent ranks, encouraging it to
		// make an even exchange.

				else if (tp.isKnown()
					&& (fp.isKnown() || fpcolor == Settings.topColor))
					vm += actualValue(tp) - fpvalue;

		// If the defender is an AI piece and the
		// attacker is unknown OR the defender is an unknown
		// opponent piece, the AI is just guessing the opponent's
		// rank, and it is guessing that it could be even.
		// However, the AI usually overstates the strength of
		// the opponent to be safe, and the opponent piece is more
		// likely a less strong piece.
		//
		// Thus, the result should be positive for the AI.  Yet if the 
		// AI attacker is unknown (the opponent piece is
		// also unknown at this point in the code), and
		// if the opponent piece turned out to be a bluffing
		// high ranked piece, the AI would lose stealth
		// and gain very little, which could cause the result
		// to be negative.
		//
		// The AI stands to gain more from an attack
		// the lower the suspected rank.  This is because multiple
		// ranks map into a lower suspected rank, so an
		// EVEN exchange is more likely to WIN.
		//
		// The AI stands to lose more the higher the suspected rank
		// because higher ranks tend to LOSE.  The worst
		// case is the Spy.  The AI should not play SxB?
		// nor allow B?xS,
		// even if unknown Blue is a suspected Spy.
		// 
		// A known Unknown is also usually overstated in rank.
		// For example:
		// -- RB --
		// xx R9 R5
		// xx B? --
		// All pieces are unknown.  Red has the move.
		// Red errs by moving Red Five away.  Why?  The AI
		// assumes that unknown Blue is the lowest expendable
		// piece, or a Five.  So it sees the loss of Red Nine
		// as inevitable.  But if Blue turns out to be a Six or
		// Seven, then Red Five would regain the exchange.

				else if (tp.getColor() == Settings.topColor) {

		// If the defender is an unknown AI piece,
		// then an attacker (not a known invincible attacker)
		// doesn't really know that the exchange is even.
		// The attacker sees only an unknown gain
		// but the potential loss of its piece.
		// Thus the result should be negative for the opponent.
		//
		// TBD.  If the target piece has not moved, it is even
		// more negative.

					if (!tp.isKnown())
						vm += apparentWinValue(fp, 
							unknownScoutFarMove,
							tp,
							actualValue(tp),
							apparentValue(tp)) - fpvalue;
		// If the AI piece is known and the opponent is unknown,
		// it may mean that the AI has guessed wrong,
		// if the opponent has allowed its piece to contact
		// the known AI piece.  The exchange is too close to call,
		// so the AI piece loses its value.

					else
						vm += riskOfLoss(tp, fp);

		// But it has to be better than a known exchange.

					if (!fp.isKnown())
						vm -= (9 - tprank.toInt());

				} else {	// AI is attacker

		// The One risks stealth.
		// All other pieces are at risk of total loss.
					if (tp.isSuspectedRank()) {
						if (tp.moves == 0
							&& tprank != Rank.FLAG
							&& fprank != Rank.EIGHT)
							vm -= fpvalue;
						else if (fprank == Rank.ONE) {
							if (!fp.isKnown())
								vm -= stealthValue(fp);
						} else
							vm -= riskOfLoss(fp, tp);
					}

					if (!tp.isKnown() || tp.isSuspectedRank()) 
						vm += (9 - fprank.toInt());
				}

		// Unknown AI pieces also have bluffing value
				if (depth != 0
					&& tp.getColor() == Settings.topColor
					&& !tp.isKnown()
					&& !isInvincible(fp)
					&& fprank.toInt() <= 4
					&& tp.getActingRankFlee() != fprank
					&& isEffectiveBluff(m))
					vm += valueBluff(fp, tprank);

				if (depth != 0
					&& fpcolor == Settings.topColor
					&& !fp.isKnown()
					&& (!isInvincible(tp)
						|| (tprank == Rank.ONE
							&& hasSpy(Settings.topColor)))
					&& tprank.toInt() <= 4
					&& fp.getActingRankFlee() != tprank 
					&& !unknownScoutFarMove
					&& isEffectiveBluff(m))
					vm -= valueBluff(tp, fprank);

		// Consider the following example.
		// -- R1 --
		// BS -- B2
		//
		// Blue Spy and Two are unknown and Red One is known.  Blue Two
		// has a acting rank chase of Two and therefore a suspected rank
		// of One.  Blue Two moves towards Blue Spy.  R1xB2 removes both
		// pieces from the board because the AI thinks that Blue Two
		// is Blue One.  Therefore it does not see BSXR1 and thus
		// loses the game.
		//
		// It is unlikely that unknown Blue One would have moved to
		// attack known Red One because the value of an unknown One
		// is much higher than a known One.  Although B2 is suspected
		// to be a One elsewhere on the board, if it moves to attack
		// Red One, Red must assume that it is a lower ranked piece
		// protected by a Spy.
		//
		// The same example with different ranks:
		// -- R3 --
		// B2 -- B4
		//
		// Blue Four has a acting rank chase of Four and a suspected
		// rank of Three.  R3xB4 would remove the pieces from the board
		// and the AI would not see B2xR3.
		//
		// So to generalize, the AI piece must stay on the board
		// if it attacks a suspected rank in an EVEN exchange.
		//
				if (tp.getColor() == Settings.bottomColor
					&& !tp.isKnown()) {
					fp.moves++;
					makeKnown(fp);
					setPiece(fp, m.getTo());
					fp.setIndex(m.getTo());
				}

		// If the opponent attacker is invincible and is
		// attacking an unknown AI piece where the outcome
		// happens to be even, the opponent actually does not know
		// the outcome will be even, and will expect that
		// its invincible piece will survive.  For example,
		// R? R? B1
		// 
		// Blue One will not attack unknown Red if the Spy is
		// still on the board, regardless of whether unknown
		// Red happens to be a One, because it expects that
		// the Blue One will survive.

				if (!tp.isKnown()
					&& tp.hasMoved()
					&& fpcolor == Settings.bottomColor
					&& isInvincible(fp)) {
					makeKnown(fp);
					setPiece(fp, m.getTo());
					fp.setIndex(m.getTo());
				}
				break;

			case Rank.LOSES :

		// call apparentWinValue() and makeWinner() before makeKnown()
				if (!tp.isKnown()) {
					if (fpcolor == Settings.topColor)
						vm += stealthValue(tp);
					else {
						vm += apparentWinValue(fp, unknownScoutFarMove, tp, stealthValue(tp), valueStealth[tp.getColor()][Rank.UNKNOWN.toInt()-1]);
						vm += riskOfLoss(tp, fp);
					}
				}
		
		// TBD: If the target piece has a suspected rank, then
		// the ai is just guessing that it loses, so
		// there is a small chance that it could win.
		// The upside is maybe 10% of its value.  Larger than this,
		// the more likely the AI is to make bad exchanges.
		//
		// TBD: If the attacker has a suspected rank, then
		// the ai is just guessing that the attacker loses, and
		// there is some chance that it could win.
		//
		// To be consistent, an attack
		// on a suspected rank that takes an AI piece
		// needs to be more value.  For example,
		// -- R? R?
		// B? R4 R?
		// -- xx xx
		//
		// Red Four is cornered by a piece chasing it.  R4xB?
		// needs to be worse than staying pat.  After B?xR4,
		// Blue is a known Unknown, with a rank of Three.
		// R?xThree still needs to be a deterrent.

		// So fp loses its entire value if known, but
		// nothing if the defender may think that it is
		// a lower ranked piece.  The value
		// of an unknown protector piece is irrelevant.
		//
				if (depth != 0
					&& fp.getColor() == Settings.topColor
					&& !fp.isKnown()
					&& (!isInvincible(tp)
						|| (tprank == Rank.ONE
							&& hasSpy(fpcolor)))
					&& tprank.toInt() <= 4
					&& fp.getActingRankFlee() != tprank
					&& !unknownScoutFarMove
					&& isEffectiveBluff(m)) {
					vm += valueBluff(m, fp, tp);
					vm -= valueBluff(tp, fprank);

		// What should happen to the piece?
		// If the bluff is effective, the opponent
		// would have to consider that its piece would
		// be removed from the board, and a stronger known
		// AI piece would replace it.
		// TBD: for now, both pieces disappear

					// makeWinner(fp, tprank);
					// makeKnown(fp);
					// setPiece(fp, m.getTo());
				} else {
					makeWinner(tp, fprank);
					makeKnown(tp);
					vm -= fpvalue;
					setPiece(tp, m.getTo());
				}

				break;

			case Rank.WINS:

		// A win of a suspected piece is much less valuable than
		// a win of a known piece.
		//
		// Example:
		// R8 R8 R1 RB
		// R4 -- -- --
		// RS R3 B?
		//
		// All pieces are unknown except for Red Three.
		// Unknown Blue has been chasing Red Three, so
		// now the AI thinks that it might be a One (but it
		// could also be a Two, or any piece if Blue is bluffing).
		// 
		// If Red Three stays put, the result is very
		// negative because a suspected One does not have much value,
		// so SpyxOne? (WINS) is much less than B?xThree (WINS).
		//
		// Consider if Red Three moves up towards
		// Red Eight.  If unknown Blue moves up towards unknown
		// Red One (or left towards Red Spy),
		// unknown Blue loses its chase rank which becomes
		// Unknown, due to its reckless behavior, leading one to
		// believe that it is a bluffing high rank piece.
		//
		// Thus the next move should be R3xB?, because the Red
		// Three will try to protect the stealth of Red One
		// (or Red Spy).
		// This would be a bad move if unknown Blue turned out
		// to be Blue One or Blue Two, but the AI assumes that
		// the opponent plays consistently, and if the opponent
		// is reckless, then it will ultimately lose anyway.
		//
				if (fpcolor == Settings.topColor) {
					vm += apparentValue(tp);

		// If a piece has a suspected rank but has not yet moved,
		// and an AI piece (except Eight) attacks it,
		// assume a loss (because it could still be a bomb).
		// This often happens when an opponent bluffs with
		// a protector piece that hasn't moved.

					if (tp.isSuspectedRank()
						&& tp.moves == 0
						&& tprank != Rank.FLAG
						&& fprank != Rank.EIGHT)
						vm -= fpvalue;
					else if (fprank != Rank.ONE)
						vm -= riskOfLoss(fp, tp);
				}

		// If the target is not moved nor known and the attacker
		// is not an Eight, the attacker loses some % of its value.
		// This is related to the risk that the attacker
		// is willing to accept by blindly slamming its piece
		// into an unknown unmoved defender, which could be a bomb.
		// If the attacking rank is low valued, such as a Six
		// Seven or Nine, the attacker is willing to accept complete
		// loss.  But the attacker is less willing to lose its
		// more valuable ranks on such attacks.
		//
		// This entices the ai to not move a piece subject to attack,
		// even though it knows the attacker would win (which
		// is why the move landed in WINS).
		// For example,
		//  R? R? R? R?
		// -- -- R8 R?
		// -- B7 R9 R8
		// All pieces are unknown except for Blue Seven.  It
		// it quite likely that Blue Seven will attack unknown
		// Red Nine.  But this is not a given.  B7xR9 is not
		// worth a full unknown piece (which is equal to the 
		// minimum piece value, which is a Nine), because
		// Blue Seven has risk of hitting a bomb.  Blue Seven
		// might also head for some other target.
		//
		// The example becomes more complex if Blue Seven
		// is a Blue Eight instead.  Eights have no reluctance
		// to attack an unmoved piece (that does not attack them,
		// eliminating any bluffing possibility).  So B8xR9 is
		// worth the full unknown piece.  But the counter move
		// R8xB8 is what Blue Eight is worried about, because
		// unknown Red Eight might be a lower rank, thus making
		// for a negative evaluation after B8xR9 (WINS), R8xR8
		// (EVEN, but high negative value due to bluffing).

		// Note: usually the tp is AI, unless any of the following
		// are true:
		// - AI fp is an invincible 8
		// - all bombs are known (this sets tp.moves)
		// - AI is attacking an unknown unmoved piece
		//	with a suspected rank (i.e. flag or bomb)

		// Note: The factor has to be more than 50% because
		// near the end game when the AI is looking for a flag
		// on the back row, the flag is worth the lowest rank.
		// So if this rank is a Four (100), the AI will risk its
		// Three (200).

				if (!tp.isKnown()
					&& isPossibleBomb(tp)
					&& fprank != Rank.EIGHT) {
					vm -= fpvalue * (10 - apparentRisk(fp, unknownScoutFarMove, tp)) / 10;
					if (fpcolor == Settings.bottomColor)
						vm += apparentWinValue(fp,
							unknownScoutFarMove,
							tp,
							actualValue(tp),
							apparentValue(tp));

		// If the bluff is effective, the AI does not lose
		// its apparent piece value, but is discouraged from
		// bluffing using its valuable pieces to bluff

				} else if (depth != 0
					&& tp.getColor() == Settings.topColor
					&& !tp.isKnown()
					&& !isInvincible(fp)
					&& fprank.toInt() <= 4
					&& tp.getActingRankFlee() != fprank
					&& isEffectiveBluff(m))
					vm += valueBluff(fp, tprank);

				else if (fpcolor == Settings.bottomColor)
					vm += apparentWinValue(fp,
						unknownScoutFarMove,
						tp,
						actualValue(tp),
						apparentValue(tp));

				if (!fp.isKnown()) {
					makeWinner(fp, tprank);

		// call makeWinner() before makeKnown()

					vm -= stealthValue(fp);
					makeKnown(fp);
				} // fp not known

				fp.moves++;
				setPiece(fp, m.getTo()); // won
				fp.setIndex(m.getTo());
				break;

			case Rank.UNK:
				int tpvalue = actualValue(tp);
		// note: fpvalue is actualValue

		// fp or tp is unknown

				if (fpcolor == Settings.topColor) {

		// AI IS ATTACKER (fp)

		// The lower the AI piece rank, the more likely it will win
		// against an unknown.  But lower ranked pieces have
		// higher value and therefore more risk.  The risk is not
		// as high as the actual piece value, if the opponent
		// has not guessed the piece rank, because the opponent
		// will not likely risk its superior pieces against unknowns.
		//
		// But there are situations where the opponent will weigh
		// the risk of taking an unknown piece.  In the diagram below,
		// all pieces are unknown.  Red 3 moves to the empty square
		// above Blue 2. Blue guesses that the Red 3 is probably not
		// not an unknown Red One, because Blue has already guessed
		// that Red One is some other piece.  Blue has also guessed
		// that Red 3 is acting like a low ranked piece.
		// So Blue Two takes Red 3 and wins.
		//
		// -- R3
		// B2 B8
		// 
		// This could also turn out to be loss if Red 3 turned out
		// to be a Red 9, because Blue 2 loses stealth.
		// Or if Red 3 really was Red 1.
		//
		// So the situation can be difficult to assess.  Human players
		// may be better able to assess the risk, so it is best
		// for the ai to avoid unknown exchanges.
		//
		// When the AI piece is an unknown defender,
		// apparent piece value is the apparent win value.
		//
		// However, if the AI piece is a known low ranked piece,
		// it has high value.  Both piece values are lost in
		// the exchange, and the AI loses much more than the
		// opponent when valued based on the unknown piece value.
		// The AI assumes that the unknown opponent piece
		// has the stealth value of a piece two ranks lower
		// than the AI piece.  If this stealth value is greater
		// than the unknown piece value, this is the value
		// that the AI gains in the unknown exchange.
		//
		// Probability based strictly on remaining piece ranks
		// and number is of little use because piece encounters
		// are rarely random.  A common example is:
		// R2
		// B3
		// B? B? B? B?
		// B? B? B? B?
		//
		// Red has the move.  Should it take B3?  Based on strict
		// random probability, it should, because there is only
		// one One and eight other unknowns in the field, so there
		// is only a small random chance that the unknown blue
		// adjacent to Blue Three is Blue One.  But because Blue
		// has left its Blue Three subject to attack, it is likely
		// that Blue Three IS protected by Blue One.
		//
		// So in an unknown exchange, the AI always loses
		// its actual piece value but gains the greater of
		// the stealth value of the piece that attacks it and the
		// unknown piece value, multiplied by
		// a factor that compares the AI piece rank to
		// the lowest expendable piece rank, which is the
		// rank the AI suspects because the opponent
		// has *allowed* this unknown to come into contact with
		// the AI piece.  (Again, note that probability based
		// on a random encounter with remaining unknown pieces
		// is not relevant).
		// 
		// For example,
		// xxxxxxx
		// x R4 --
		// x B3 B?
		// Red Four and Blue Three are known.  Red Four will move
		// towards Unknown Blue because at least it will gain
		// the stealth value of Unknown Blue.
		//
		// But if Blue Three was also unknown, the choice is
		// a toss-up between unknowns.  If Red Four stays, B?(3)xR4
		// is a WIN and all Red gains is the stealth value of
		// a Three.  So Red Four will move towards
		// the other unknown because it has the chance to
		// gain a greater stealth value, because the unknown piece
		// could be a One or Two.
		//
		// Red Two should not take a Blue Three if it is protected
		// and Blue One is unknown.  Blue Three is worth 200 points.
		// So B?xR2 must be less than -200.
		// The stealth value of a One (60) plus 10 is 70,
		// so B?xR2 is 70 - 400,
		// or -330 so Red Two will not take Blue Three
		// if it is protected and Blue One is unknown.
		//
		// Another common example is a unknown piece chasing a Two.
		// B? R2 -- --
		// -- -- B? B?
		// B? B? B? B?
		//
		// Governed by the same rule, should Red Two
		// assume that the chaser is Blue One and flee past the
		// unknowns?   If Red Two stays, it loses 400 - 60 but
		// if moves, it loses 400 - 70.  This is a mere 10 points.
		// If Red can capture a piece elsewhere on the board,
		// it will and let its Two be attacked.
		//
		// However, because unknown Blue has chased Red Two,
		// Red now suspects that unknown Blue IS Blue One, making
		// Red Two invincible.
		// This could allow Red Two to flee past the other unknowns
		// with impunity.
		//
		// TBD: there needs to be some risk that a suspected
		// chase piece is not a One.
		//
		// Another borderline example is 4x5 when 5 is protected
		// by two unknowns, so the protectors do not have any
		// suspected rank.  This gains 50 points and the
		// stealth value of a Two (40).

					assert tprank == Rank.UNKNOWN: "Known ranks are handled in WINS/LOSES/EVEN";

		// One exception to the rule that the AI piece loses its value
		// in an unknown exchange is at the start of the game when
		// few of the pieces can move and most of the expendable
		// pieces are still unknown.  This condition is slightly
		// favorable to a kamikaze foray by a Five or Six, because 
		// winning a random encounter is greater than 50%
		// even against an unmoved piece.
		//
		// A Five has to win two such encounters and a Six
		// only one encounter.  Because the opponent often places
		// its higher ranks in the front line and bombs in the
		// rear row, odds are improved for a foray into the front row.
		//
		// The probability is hard to prove theoretically
		// (because it depends on opponent setup) so
		// one must run a series of games to prove that this
		// is a valid rule.  One can also reason this intuitively
		// as follows.
		//
		// A Five loses against 9 pieces (Bombs and 4s), but
		// assume zero or one bombs in the front row.
		// The Fours may or may not be near the front, so
		// assume 3 spaces of protection on the front row.
		// 3.5 spaces out of 10 is 35% risk of loss after the
		// first attack.
		// The probability of winning two pieces is maybe 80% * 65%
		// or about 52%.
		//
		// A Six loses against 14 pieces (Bombs, 4s and 5s).
		// Note that even if it only wins one piece and loses the
		// next, it will also have won the stealth value of the
		// attacker.  The probability of winning one piece is
		// about 60%.

		if (npieces[Settings.bottomColor] >= 37
			&& (fprank == Rank.FIVE || fprank == Rank.SIX)) {
			int div = 2;
			if (tp.moves == 0)
				div += (9 - Grid.getY(tp.getIndex()));
			fpvalue = fpvalue / div;
		}

		tpvalue = unknownValue(fp, tp, fprank);

		// If the AI attacks an unknown unmoved piece, reduce
		// the value by the number of pieces remaining, because
		// the likelyhood that the piece is a bomb increases
		// as the pieces become fewer.  This fixes the bug
		// where lowestUnknownExpendableRank is the Spy (the
		// last unknown piece on the board), so the value of
		// a moved Unknown would be the Spy.  This caused the AI
		// to slam into unknown pieces thinking that they were
		// the spy, but were just bombs.

		if (tp.moves == 0)
			tpvalue = tpvalue * npieces[Settings.bottomColor]/40;
		vm += tpvalue - fpvalue;

		// What should happen to the ai piece
		// after an attack on an unknown piece?

		// In a field of unknowns, if an ai piece survives
		// and retains its value, it would never enter
		// the field, because each successive ply
		// would become more negative.  In the position below,
		// it would allow itself to be captured by Blue One
		// because attacking an unknown leads to another
		// position with more unknowns, and it would lose
		// twice its value.

		//       B1 
		// ?? ?? R4 ?? ??
		// ?? ?? ?? ?? ??

		// But often attacking one unknown is better
		// than attacking another, and this evaluation
		// depends on the ai piece survival.

		// In the position below, it is better for Red Four
		// to attack the unknown on the right because
		// the left unknown is a certain to recapture
		// by Blue One.  An addition bonus on the right
		// is that there is only one more possible unknown 
		// attack before freedom is assured.

		//    B1
		// ?? ?? R4 ?? ??
		// ?? ?? B2    ??

		// One solution is to allow the ai piece
		// to survive, but greatly reduce its value.

		// Another idea is to allow the ai piece
		// to survive if the value of the move is positive.
		// (this needs to be checked)

		// If the ai piece does not survive, it is prone
		// to the following blunder.  In the position below,
		// Red Four may attack the unknown piece because
		// if Red Four does not survive, then the ai
		// cannot see the easy recapture.

		// R4
		// ??
		// B3

		// Note: tpvalue and fpvalue contains stealth

			if (vm < 0) {

		// Note that an attack on an unknown
		// creates a known Unknown.
					if (isPossibleBomb(tp))
						tp.setSuspectedRank(Rank.BOMB);
					else 
						makeWinner(tp, fprank);
					makeKnown(tp);
					setPiece(tp, m.getTo());

			} else {
				makeKnown(fp);
				fp.moves++;
				setPiece(fp, m.getTo()); // won
				fp.setIndex(m.getTo());
			}


				} else {

		// AI IS DEFENDER (tp)

		// What is the likely outcome if a known AI piece
		// is defending an unknown attacker?
		//
		// If the attacker approaches the AI piece, it
		// acquires an ActingRankChase of the AI piece.
		// This causes winFight() to return WINS/LOSES.
		// (This case needs to be symmetric in
		// unknownValue() when the AI is the attacker.)
		//
		// Here is the example:
		//
		// -- R5
		// -- R3
		// B? --
		// -- B?
		//
		// Upper unknown Blue moves up to attack Red 3, acquiring
		// an ActingRankChase of Three.  Known Red
		// has a choice between Blue WINS by staying
		// put or take its chances with by approaching
		// the lower unknown Blue.  This is preferable result,
		// although one could argue the outcome could
		// be identical if Blue is bluffing. 
		//
		// But certainly, approaching the lower unknown
		// should not be worse.  The problem is that
		// WINS/LOSES makes the unknown Blue attacker a Two
		// after the attack, and so WINS/LOSES returns a
		// not so negative result because of the stealth
		// value of a Two.  unknownValue() currently
		// does not make that prediction because the
		// outcome is unknown, and certainly a Three should
		// have a high probability of winning when it
		// approaches a random unknown piece.
		//
		// Here is a more complex example:
		// B4 R3 -- -- --
		// -- -- -- B? --
		// -- -- -- R2 --
		// -- -- B? -- B?
		//
		// It is Red's move.  Unknown Blue has just approached
		// Red Two, acquiring a chase rank of Two.  This makes its
		// suspected Rank a One.  So Red Two should move away
		// from that unknown and take its chances with the other
		// unknowns.  But it could also stay pat and instead
		// take Blue Four, assuming that the approaching unknown
		// is bluffing and perhaps one of the other two unknowns
		// is the actual One. What would you do?
		//
		// The formula to determine value based on the differing
		// ranks is not used for an unmoved unknown piece,
		// because the target could be thought a bomb.
		//
		// Note: If a piece approaches a known AI piece, then
		// it usually has a suspected rank, and is not handled here.
		// But if a known AI piece approaches an opponent unknown,
		// then the opponent attack is handled by the formula
		// below because tp.moves != 0.
		//
		// If an unknown opponent piece approaches an unknown AI
		// piece, what is the probability that it will attack the
		// AI piece?  The opponent piece only sees the apparent value
		// of the AI piece.

					tpvalue = apparentWinValue(fp, false, tp, tpvalue, apparentValue(tp));

		// Outcome is the negation as if ai
		// were the attacker.
		//
		// But note that the resulting value
		// of AI PIECE X OPP PIECE !=
		// OPP PIECE X AI PIECE,
		// because the former uses the
		// actual AI piece value and the
		// latter uses the apparent piece value.

		// TBD
		// if !fp.hasMoved()
		// adjust fpvalue based on the
		// probability that fp is a bomb
		// and cannot move

					fpvalue = unknownValue(tp, fp, tprank);
		// The probability that an unknown is of lower rank
		// increases linearly with the number adjacent unknowns.
		// While we don't know *which* unknown is the lower rank,
		// several adjacent unknowns is worse than just one.

					int index = tp.getIndex();
					int count = 0;
					for (int d : dir) {
						Piece p = getPiece(index + d);
						if (p == null || p == fp)
							continue;
						if (p.getRank() == Rank.UNKNOWN)
							count++;
					}
					fpvalue = fpvalue * 5 / (5 + count);

					vm += tpvalue - fpvalue;
		// If the AI appears to make an obviously bad move,
		// often it is because it did not guess correctly
		// what happened to the pieces after an unknown attack.
		// Any outcome is possible.
		//
		// However, if the AI piece has moved
		// or if the attacker has an chase rank of Unknown
		// (proving that it is hellbent on attacking),
		// assume worst case for AI: AI loses.
		// Otherwise the AI guesses that the defender
		// will remain and the attacker
		// loses its piece.  This closely matches
		// what the attacker is likely
		// thinking, because the unmoved piece could be a bomb.

					if (!isPossibleBomb(tp) || fp.getActingRankChase() == Rank.UNKNOWN) {
						makeWinner(fp, tprank);
		// do not add in makeKnown() because fpvalue
		// contains stealth and was already added
		// in unknownValue()
						makeKnown(fp);

						fp.moves++;
						setPiece(fp, m.getTo()); // won
						fp.setIndex(m.getTo());
					} else
						setPiece(tp, m.getTo());
				}
				break;
			} // switch

			// prefer early successful attacks
			// for both ai and opponent
			if (vm > 0)
				vm -= depth;
			else
			// otherwise delay it
				vm += depth;

		} // else attack

		if (fpcolor == Settings.topColor)
			value += vm;
		else
			value -= vm;
	}

	// The AI always assumes that it loses in an unknown encounter.
	// It receives only stealth value of the rank that
	// removes the AI piece.  Because the opponent piece is unknown, the
	// AI assumes that it is two ranks lower.

	// Recall that if an opponent piece chases an AI piece,
	// it acquires a suspected rank of one rank lower
	// than the AI piece.  So the AI gains only the stealth of
	// one rank lower in LOSES.  But in an unknown exchange,
	// the rank is assumed to be two ranks lower, so an unknown
	// exchange is always more favorable to the AI than LOSES.

	// For example, a Five approaches an unknown.
	// The stealth value of the unknown is (30), assuming
	// that the Five loses to a Three.
	// The Five loses its value (50), so the result is -20.

	// Piece ranks greater than the lowest unknown expendable
	// receive stealth of a Four, or 15 points.

	// So you can see that the AI always loses something
	// but not everthing in an unknown encounter.

	// Odds improve for higher ranked pieces
	// once *all* the opponents lower ranked
	// expendable pieces become known.  Then the AI knows
	// that it will gain more stealth value because
	// the remaining pieces must be lower ranked.

	public int unknownValue(Piece fp, Piece tp, Rank fprank)
	{
		assert tp.getRank() == Rank.UNKNOWN : "target piece is known? (" + tp.getRank() + ")";
		assert lowestUnknownExpendableRank != 0 : "unknownValue should be known";

		int r = Math.min(fprank.toInt(), Math.min(lowestUnknownExpendableRank+1, 6));
		int tpvalue = 0;
		if (r == 1)
			tpvalue = valueStealth[tp.getColor()][0];
		else if (r == 2)
			tpvalue = valueStealth[tp.getColor()][0]+10;
		else
			tpvalue = valueStealth[tp.getColor()][r-3];

		// Note: Acting Rank Chase is an
		// unreliable predictor of actual rank.
		// It is easy to get suckered into thinking
		// that a bluffing piece is a low rank and
		// then sacrificing material to avoid it.

		// If a Spy has a shot at a unknown piece acting like a One,
		// it must be careful, because the piece could be bluffing,
		// or it could be a Two that chased the ai Two.
		// if ((tp.getActingRankChase() == Rank.ONE
		// 	|| tp.getActingRankChase() == Rank.TWO)
		// 	&& fp.getRank() == Rank.SPY)
		// 	tpvalue =?

		// If an unknown piece flees from an opponent unknown piece,
		// it is a good indication that the opponent piece
		// is either a high ranked piece or an unknown low ranked
		// piece trying to maintain stealth.  Either way, this
		// makes the piece a more attractive target.
		//
		// So extra value is added to the target piece.
		// (Note: this rule had been handled in winFight() as
		// a WIN, but then the opponent piece is removed from
		// the board, so it is handled here, because the
		// opponent piece remains, as would be expected if
		// the target is a lower ranked piece.)
		//
		// Note: If a piece has a chase rank, then the piece
		// has a suspected rank and is not handled here.  But
		// if the chase rank is UNKNOWN, it does not have
		// a suspected rank.  Because the piece has chased
		// an unknown piece, it is not a low ranked piece.

		Rank chaseRank = tp.getActingRankChase();
		Rank fleeRank = tp.getActingRankFlee();
		if (!isPossibleBomb(tp)
			&& chaseRank != Rank.UNKNOWN
			&& fleeRank == Rank.UNKNOWN)
			tpvalue += 15;

		// In an effort to prevent draws,
		// unknowns are worth more the more they move.
		// Sixes, Sevens and Nines probably don't last very
		// long, so this creates a bonus for attacking more
		// valuable pieces.
		if (isWinning(Settings.topColor) > 0)
			tpvalue += Math.min(tp.moves / 2, 20);

		return tpvalue;
	}

        protected void moveHistory(Piece fp, Piece tp, BMove m)
        {
                undoList.add(new UndoMove(fp, tp, m.getFrom(), m.getTo(), hash, value));
	}

	public void undo()
	{
		UndoMove um = getLastMove();
		value = um.value;
		Piece fp = um.getPiece();

		// remove piece at target to update hash
		setPiece(null, um.getTo());

		// place original piece and restore hash
		fp.copy(um.fpcopy);
		setPiece(fp, um.getFrom());

		// place target piece and restore hash
		if (um.tp != null) {
			um.tp.copy(um.tpcopy);
			setPiece(um.tp, um.getTo());
		}

		popMove();
	}

	public void pushNullMove()
	{
                undoList.add(null);
	}

	public void popMove()
	{
		undoList.remove(undoList.size()-1);
	}
	
	// If the prior move was to the target square,
	// then the opponent must consider whether the ai is bluffing.
	// This could occur if the opponent moves a piece next to an
	// unknown ai piece or if the ai moved its unknown piece next to an
	// opponent piece.
	//
	// However, this gets stale after the next move, because if the ai
	// does not attack, it probably means the ai is less strong,
	// and the opponent will know it.
	//
	// A move becomes stale when the player
	// makes any other move rather than using the bluffing
	// piece to attack.  For example,
	// xx R? R? xx
	// xx -- -- xx
	// -- -- B1 B?
	// -- R2 B3 --
	// Unknown Red moves towards Blue One.  This is a very
	// good move because Blue One is protecting Blue Three
	// and must either move (losing the Three) or stand pat,
	// risking loss of Blue One if unknown Red is the Spy.
	// R?xB1 (LOSES) is very positive for Red in the move tree,
	// and must remain as the main line in the tree
	// even if Blue calls the bluff later in the tree
	// by moving some other piece.
	//
	// Because R?xB1 would not be played in practice,
	// if Blue actually does stand pat by moving some other
	// piece, Red will have to choose some other move than
	// R?xB1.  If it moves some other piece in the tree, bluffing will
	// become stale because the bluffing square no longer appears
	// as the prior move in the move tree.
	//
	// If Red does move some other piece, Unknown Red also
	// acquire an actingRankFlee which permanently marks it as a weaker
	// piece.

	public boolean isEffectiveBluff(BMove m)
	{
		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack when isEffectiveBluff() is called)
		UndoMove prev = getLastMove(2);
		if (prev != null && m.getTo() == prev.getTo())
		 	return true;

		prev = getLastMove(3);
		if (prev != null && m.getFrom() == prev.getTo())
		 	return true;

		return false;
	}

	// Unknown moved and unmoved AI pieces bluff.
	//
	// If the AI calculates that the bluff is effective
	// (see isEffectiveBluff()), the AI does not assign any
	// value to the loss of its piece in LOSES.  This makes
	// the AI able to bluff with any piece, no matter how valuable.
	//
	// An important case is an attack by the Spy, because 
	// an attack on a One by a protected Spy should be positive,
	// to support the following example:
	// -- R?
	// R? --
	// -- B1
	// Either unknown Red moves towards known Blue One.
	// Unknown Red *could* be the Spy.   If Red always
	// bluffed in this situation, then Blue could ignore
	// the attack.  But the AI wants Blue to respond to
	// a protected unknown by moving away, because this
	// can result in material gain.  For example,
	// xx -- R? xx
	// xx R? -- xx
	// -- -- B1 --
	// -- R2 B3 --
	// Unknown Red moves towards Blue One.  This is a very
	// good move because Blue One is protecting Blue Three
	// and must either move (losing the Three) or stand pat,
	// risking loss of Blue One if unknown Red is the Spy.
	// R?xB1 (LOSES) is very positive for Red in the move tree,
	//
	// This is why the AI does not currently assign value to the
	// the loss of its piece in LOSES during an effective bluff.
	// Any unknown piece would work.
	//
	// (TBD: But this causes the AI to make many high stakes and unnecessary
	// bluffs, so perhaps it should assign a small negative value,
	// based on the value of its piece, perhaps randomized).
	//
	// The AI must also risk its Spy occcasionally
	// if it wants to convince the
	// opponent that the unknown piece might be the Spy.
	// So B1xRS (WINS) followed by R?xB1 (LOSES) must at least sometimes
	// be positive.  WINS loses the Spy value.  So LOSES must
	// check the prior move.  If the prior move was a capture
	// to the target square, the captured piece value must be
	// negated.
	//
	// The AI doesn't have any clue *why* it is bluffing.  Its
	// only plan is to persuade the opponent into making a bad decision,
	// like moving into the arms of one of its lower ranked pieces
	// or attacking one of its unmoved pieces that could be
	// a bomb.  But this often backfires, if none of its pieces
	// are bombs, and the bluffing AI piece chases the opponent
	// piece through a field of its unmoved pieces, obliterating them.
	//
	// (TBD: can the search tree be used to increase the value of
	// a bluff if it leads to more possibilities of capture?
	// So a bluff that pushes the opponent piece towards a waiting
	// lower ranked piece or a bomb has more bluffing value?)
	//
	// The bluffing value must be less than the lowest value of
	// one of its pieces.  Otherwise the AI will sacrifice
	// bluffing pieces on the front line to repeat the bluff again.
	// For example:
	// xx R? R? xx
	// xx R? R? xx
	// B? -- B1 B?
	// B? B? B? B?
	// Red has the move.  R?xB1 loses its piece.  But if the
	// bluffing value for the approach of a red Unknown further back is 
	// is greater than the piece value, it will play R?xB1 just
	// to be able to move its other unknown piece towards Blue One.
	//
	// Another interesting case is the same setup, but Blue One
	// is suspected, but not known.  If the bluffing value is based
	// completely on the value of the target piece, it would be
	// much higher for a known piece rather than a suspected piece.
	// This could cause R?xB? just to make unknown Blue known so
	// that it can get more bluffing value by moving its further
	// back piece towards now known Blue One.
	//
	// In other cases, one can argue that the bluffing value should
	// be much higher.  In the example below, Unknown Blue
	// is about to fork two known Red Fives.  Red anticipates the
	// possible fork, but sees that the loss is inevitable.
	// (After Red Five moves up, Blue Four moves up, Red Five moves
	// up, Blue can take either Five, because the Five is only
	// protected by an unknown Four.  Because the bluffing value
	// is low, the AI sees that it will lose the Five anyway.
	// R? -- R4 R?
	// xx R5 -- xx
	// xx -- R5 xx
	// -- B? -- B?
	//
	// Another example suggesting a higher bluffing value:
	// B? B? B? B?
	// B? R4 -- B?
	// xx B3 -- xx
	// xx R7 -- xx
	// B? B? -- --
	// Blue Three has forked Red Four and Red Seven.
	// Red has the move.  Will Blue Three
	// actually take Red Four?  Probably not, but because bluffing
	// value is low, Red Four moves sideways allowing Blue Three to
	// take Red Seven.

	// How it works.
	// Example:
	// R? R5
	// -- B3
	// Blue Three takes Red Five which is protected by
	// unknown Red.  Unknown Red is a Six, so 6x3 is a loss.
	// prev1 is the current move, R6xB3.
	// prev2 is B3xR5.
	// prev1.value is the value of the board after B3xR5.
	// prev2.value is the value of the board before B3xR5.
	// prev1.value - prev2.value is the value(-) of B3xR5.
	// 
	// This works no matter how deep in the move tree.
	// For example,
	// R? -- R5
	// -- B3 --
	// Red Five moves towards unknown Red.  The board is
	// then evaluated as in the above example.

	protected int valueBluff(BMove m, Piece fp, Piece tp)
	{
		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack when isEffectiveBluff() is called)
		UndoMove prev2 = getLastMove(2);
		if (prev2 != null
			&& prev2.tp != null) {

		// Because a One will win any attack on a lesser piece,
		// regardless of whether the lesser piece is known,
		// the bluffing counter-attack
		// restores the full value of the lost piece.  This
		// encourages an unknown AI piece to approach an
		// opponent One if the AI piece has protection.  Thus
		// the One has to consider if the approaching piece is
		// a Spy, or the protecting piece is a Spy, with dire
		// consequence if the One guesses incorrectly.
		// For example:
		// -- -- R?
		// -- -- -- R?
		// R? R2 B1 --
		// -- xx xx --
		// Red Two and Blue One are known and the other pieces
		// are unknown. If the piece above Blue One moves down,
		// what should Blue do?  Most bots will play B1xR2 because
		// Blue has no move that does not result in a possible
		// loss if the unknown Red piece is actually the Spy.
		// But this is *exactly* what Red is hoping for, because
		// the Spy has been planted in usual fashion next to
		// Red Two.  (Humans would likely call the bluff
		// and play B1xR?, assuming that the Two is protected
		// by the Spy, but this is of course a risk that could
		// lose the game).
		// 
				UndoMove prev1 = getLastMove(1);
				if (tp.getRank() == Rank.ONE
					&& tp.isKnown()
					&& !tp.isSuspectedRank()
					&& !fp.isKnown()
					&& !prev2.tp.isKnown())
					return -(prev1.value - prev2.value);

		// In other cases the AI only receives half of the
		// value of the bluffing piece.  This discourages the
		// AI from risking known pieces. There is no
		// penalty in risking an unknown piece, given that the
		// the opponent piece is not invincible.

				else
					return -(prev1.value - prev2.value) / 2;
			}
		return 0;
	}

	protected int valueBluff(Piece oppPiece, Rank airank)
	{
		// If the defender is near the flag,
		// the defender has to call the bluff
		// so bluffing isn't very effective

		if (flag[Settings.bottomColor] != null
			&& Grid.steps(oppPiece.getIndex(), flag[Settings.bottomColor].getIndex()) <= 2 )
			return values[Settings.topColor][airank.toInt()]/2;

		// Bluffing using valuable pieces is (slightly) discouraged.

		Rank rank = oppPiece.getRank();
		if (airank.toInt() <= 4
			|| (airank == Rank.SPY && rank != Rank.ONE))
			return values[Settings.topColor][airank.toInt()]/10;

		// A suspected Four (that chased a Five) could well be a Five.
		// If so, the piece might attack and the AI would
		// lose its piece.

		if (rank == Rank.FOUR
			&& oppPiece.isSuspectedRank()
			&& !oppPiece.isRankLess())
			return values[Settings.topColor][airank.toInt()]/2;

		return 0;
	}

	public int getValue()
	{
		return value;
	}

	public void addValue(int v)
	{
		value += v;
	}

	public void setValue(int v)
	{
		value = v;
	}


	private int stealthValue(Piece p)
	{
		if (p.aiValue() != 0)
			return p.aiValue() / 9;

		Rank rank = p.getRank();
		int r;
		if (rank == Rank.UNKNOWN)
			r = lowestUnknownExpendableRank;
		else
			r = rank.toInt();
		return valueStealth[p.getColor()][r-1];
	}

	private void makeKnown(Piece p)
	{
		if (!p.isKnown())
			p.setKnown(true);
	}

	// If the opponent has an invincible win rank,
	// then it is impossible to defend the flag,
	// so do not try, because it just leads to successive piece loss
	private void makeFlagKnown(Piece pflag)
	{
		int c = pflag.getColor();
		if (c == Settings.bottomColor
			|| invincibleWinRank[c] <= invincibleRank[1-c])
			pflag.setKnown(true);
	}

	//
	// BOMB VALUE
	//
	// How much is a bomb worth that surrounds a flag?
	// Quite a bit, if the removal allows another piece to access the flag.
	// But therein lies the catch.  If the search tree can discover
	// the flag after a bomb is removed by the eight, then
	// the bomb itself is still actually worthless.
	// Still there must be an incentive to remove them
	// because the search tree depth is limited and eventually
	// it is hoped that an exposed flag can be attacked.
	//
	// So a flag bomb is worth more than an unknown eight.
	// The value of an eight increases with the number remaining.
	//
	// The ai is always willing to exchange an eight for a flag bomb.
	// With several eights remaining, it is not willing to take a bomb
	// if it loses a low ranked piece (1-4) with a higher value
	// trying to get at the bomb, unless the search tree exposes
	// the flag (which then garners a higher value).
	//
	// An ai flag bomb is worth considerably more than an opponent
	// flag bomb.  This is because the ai never really knows if an
	// opponent flag bomb really surrounds the flag.

	private int aiBombValue(int color)
	{
		// A worthwhile bomb of "color" is worth the value
		// of the opposite 8, its stealth value, and more.
		// This makes taking a worthwhile bomb always worth sacrificing
		// an eight if necessary.

		// Note that a known miner taking a worthless bomb,
		// the value is zero, but an unknown miner,
		// the value is -stealthvalue.  This deters an
		// unknown miner from taking a worthless bomb.
		// This is calculated in WINS.

		// The ai is willing to sacrifice a Four in order to
		// protect its own flag bombs.
		int more = 10;
		if (color == Settings.topColor)
			more = values[color][Rank.FOUR.toInt()];

		return values[1-color][8]
			+ valueStealth[1-color][Rank.EIGHT.toInt()-1]
			+ more;
	}

	// FLAG VALUE.
	//
	// In a game-playing algorithm that uses a search tree
	// to discover the end result of the game (in this case, the flag),
	// piece values should be viewed as simply a gimmick
	// to move the game forward towards the desired end result.

	// If flag value is set ridiculously high, the AI
	// will leave pieces hanging if the search tree discovers that
	// it can be taken.  But the flag is always unknown
	// by definition and therefore is comparable to the value of
	// other unknown pieces, depending on the intuition of the
	// player at guessing the flag location from among the
	// remaining unknown pieces.

	// When the flag value is set higher than other unknown pieces,
	// the opponent may be able to discern the protective
	// behavior of the AI and accurately guess the location of the
	// flag, which would put the AI at a further disadvantage.

	// Only after all other unknown pieces have been discovered
	// and there is only one remaining unknown piece of a given color,
	// (which then must by default be the flag)
	// can the value of the flag be set to infinity.

	private void setFlagValue(Piece pflag)
	{
		int color = pflag.getColor();

	// Set the flag value higher than a worthwhile bomb
	// so that an unknown miner that removes a bomb
	// to get at the flag
	// will attack the flag rather than another bomb.

		int v = aiBombValue(color);

	// Flag value needs to be higher than the lowest value piece
	// so that this piece will also attack a possible flag.
	// (A possible flag is an EVEN exchange, so the only pieces
	// that will attack it are those with lessor value).
	//
	// For example, if R2 and R6 are on the board, Blue Flag value is
	// slightly more than R6.  This allows R6 to attack the Blue Flag.
	// But if R1 and R8 are on the board,
	// Blue Flag value is more than the R8.
	// R8 will attack the Flag and not R1, because we do not want
	// R1 to attack the Flag, which would lose if it turned out to be
	// a Bomb.
	//
	// TBD: This can be improved by considering all the
	// pieces remaining on the board, and then deeming which pieces
	// are expendable for attacking a possible flag.
	//
		int min = 9999;
		for (int r = 1; r <= 10; r++)
			if (rankAtLarge(1-color, r) != 0)
				if (values[1-color][r] < min)
					min = values[1-color][r];

		if (min + 10 > v)
			v =  min + 10;

		pflag.setSuspectedRank(Rank.FLAG);
		values[color][Rank.FLAG.toInt()] = v;
		if (color == Settings.bottomColor)
			flag[color] = pflag;
	}

	// risk of attack (0 is none,  10 is certain)
	int apparentRisk(Piece fp, boolean unknownScoutFarMove, Piece tp)
	{
		int rank = fp.getRank().toInt();

		if (!isPossibleBomb(tp)) {

		// if the attacker is invincible, attack is almost certain

			if (isInvincible(fp))
				return 9;

		// if an opponent piece approaches a moved AI piece
		// and the AI piece does not attack
		// attack is almost certain
		// TBD: unless the opponent piece is protected

		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack

			UndoMove prev = getLastMove(2);
			if (prev != null && prev.getPiece() != tp)
				return 9;
		}

		if (rank <= 4) {
			if (isPossibleBomb(tp))

		// tp is not known and has not moved
		// so it could be a bomb which deters low ranked
		// pieces from attacking an unknown unmoved piece

				return 1; // 10% chance of attack
			else
				return rank;
		}

		if (rank == 10 && rankAtLarge(1-fp.getColor(), Rank.ONE) != 0) {
			// suspected spy probably won't attack
			// unless it really isn't the spy
			return 1;
		}

		// Known Fives are unpredictable.  Suspected Fives
		// even more so.
		// For example,
		// R? R8 R?
		// R1 -- R?
		// xx B? R?
		// All pieces are unknown.  Because unknown Blue
		// has approached an unknown Red, it has a chase rank
		// of unknown.  When the AI evaluates the possibility
		// of Blue moving further up the board,
		// the evaluation of R8xB? may create a suspected rank
		// of Blue Five.  This leads to a false sense of security
		// because the stealth of its Red One is safer,
		// because a Five is less likely to sacrifice itself
		// on a discovery mission than a Six, Seven or Nine.
		//
		if (rank == 5 && !fp.isSuspectedRank())
			return 5;	// 50% chance of attack

		// Risk of an unknown scout attack decreases with
		// with the number of scouts remaining.
		//
		// An important case is an attack on the Spy, because
		// 9xS wins the Spy and its 300 point value.  For example,
		// R? RS R?
		// -- -- --
		// R3 -- --
		// -- B? --
		// Red has the move.  Red moves R3 to the left which is
		// highly negative because it subjects its known Three
		// to an approaching unknown Blue piece (which happens to
		// be Red One).  But if risk is high (say 50%), B?xRS is
		// -150 points, greater than B?xR3.

		if (unknownScoutFarMove)
			return 3 + unknownRankAtLarge(fp.getColor(), Rank.NINE)/2;
		// The unknown defender has moved or the attacker is
		// an unknown or a high ranking piece that may actually
		// intend on attacking unmoved pieces.
		// This makes it much more likely for an unknown defender
		// to be attacked, and so the risk is much higher.

		return 8;	// 80% chance of attack

	}

	int apparentWinValue(Piece fp, boolean unknownScoutFarMove, Piece tp, int actualV, int apparentV)
	{
		assert fp.getColor() == Settings.bottomColor : "apparentWinValue only for opponent attacker";

		// if the target is known, attacker
		// sees the actual value of the piece
		if (tp.isKnown())
			return actualV;

		// tp is unknown

		// attacker sees the apparent value of the piece,
		// and the defender sees the risk of loss,
		// which does not exceed the actual value of the piece.
		// Both need to be considered in assigning a value to an
		// attack on an unknown piece.
		//
		// In the example below, AI Blue Spy and Blue Four
		// and unmoved and unknown.  Red Three is known.
		// If the actual value of the target piece were assigned
		// to move, then Blue Four would attack Red Three
		// because the ai would guess that Red Three is going to
		// move left and attack Blue Spy, which is more valuable
		// than Red Three.  By attacking Red Three, Blue creates
		// a square for the Spy to move to.
		// xxxxxxxxxx
		// x BS B4
		// x -- R3
		//
		// But Red Three does not *see* the actual value of Blue Spy.
		// It only sees the apparent value but the ai has to assign
		// some risk (low) that Red Three will attack
		// its unknown unmoved Blue Spy.
		//
		// In the following example, AI Blue Spy is unknown,
		// Red Three and Blue Two are known.   Blue Two should not
		// approach Red Three because it will flee into Blue Spy.
		// R3 BS ?B
		// -- ?B
		// B2 
		//
		// If the apparent value was assigned to Blue Spy
		// Blue Two would approach Red Three, because it would
		// gain a Three minus the apparent value of Blue Spy
		// (small).
		//
		// These two examples further suggest that the assignment
		// value is somewhere between actual and apparent value.
		//
		// Now consider the following example.
		// -- B? B? B?
		// -- B? R3 B?
		// B? R2 B7
		// All pieces are unknown except for Blue Seven.
		// Red has the move.  Should Red play R3xB7 to
		// protect the stealth of the Two?  R3xB7 loses
		// the stealth of the Three (40) but gains the
		// value of the Seven, resulting in -15.
		// B7xR2 is not a certainty, so the value is
		// the stealth of Red Two (-80) * 70% + 25 = -31.
		// So clearly Red should play R3xB7.  However, the value
		// is close enough that a situation elsewhere on
		// the board could allow Red to play some other move
		// and allow B7xR2.  But then Red is calculating that
		// Blue may play B7xR3 instead of B7xR2, since
		// neither piece is known.

		int risk = apparentRisk(fp, unknownScoutFarMove, tp);
		return (apparentV * (10 - risk) + actualV * risk) / 10;
	}

	public int apparentValue(Piece p)
	{
		return aiValue(p, true);
	}

	public int actualValue(Piece p)
	{
		return aiValue(p, false);
	}

	private int aiValue(Piece p, boolean apparent)
	{
		// Piece value (bomb, flag) overrides calculated value.
		// Piece value does not depend on being known
		// because the ai sets it when the piece value is obvious.
		int v = p.aiValue();

		if (v == 0) {
			Rank actualRank = p.getRank();
			Rank apparentRank;

			// apparent rank is the known rank of a piece or
			// if unknown, its suspected rank, if any,
			// and otherwise RANK.UNKNOWN.
			if (p.isKnown()
				|| p.isSuspectedRank()
				|| !apparent)
				apparentRank = actualRank;
			else
				apparentRank = Rank.UNKNOWN;

			v = values[p.getColor()][apparentRank.toInt()];

			if (!p.isKnown() && v != 0) {

		// The addition of stealthValue based on actual
		// value to unknown value makes AI unknowns
		// vary in value.
		//
		// The desired behavior is for the unmoved
		// unknown to remain unmoved, even it if can win
		// an attack against a known piece at or below
		// the UNKNOWN value, because to do so, it loses
		// its stealth.
		// 
		// Another desired behavior is that the AI will
		// protect its unmoved unknown pieces from attack
		// by opponent unknowns, short of moving the unmoved
		// piece, with one notable exception:
		//
		// If the value of the unmoved unknown is very
		// high, such as the SPY or the last EIGHT,
		// the AI may need to move the piece as a last
		// resort.  It should consider moving the piece
		// if the attacker (known or unknown)
		// could win the piece.
		//
		// If an unmoved piece is made too valuable,
		// it will move away from a potential attacker,
		// making itself even less safe,
		// because then the attacker knows
		// it is not a bomb.  A high value is also fodder
		// for the hanging piece horizon effect,
		// making for a surprising sacrifice of material
		// when the opponent had no idea that
		// it could take a valuable unknown.

				v += valueStealth[p.getColor()][actualRank.toInt()-1];

		// Suspected ranks have much less value
		// than known ranks because of uncertainty.
		// The value of a suspected rank is the value
		// of an unknown (minimum rank value) plus
		// the the rank value divided by 5.  This represents
		// the high risk that a suspected rank is bluffing
		// or not moving optimally.
		//
		// For example:
		// RS R1
		// -- R3
		// B? --
		//
		// If Unknown Blue has been chasing R3,
		// it might be a One or a Two.  If its suspected
		// Rank is One, Red three could move between
		// unknown RS and unknown B3.  But if Blue
		// is actually a Two, it loses the Three.
		// So if the suspected One value (800) is divided by 5,
		// it would see the loss of the Three (200)
		// and a gain of 160, deterring the Three
		// from moving.
		//
		// Another example:
		// R? R2 R?
		// -- B1 --
		// 
		// All pieces are unknown, except that Blue One
		// chased a Red 5, so its suspected rank is a Three.
		// R2xB?(3) is a WIN, so the value is the loss of
		// stealth of Two plus the suspected value of Three.
		// -80 + (25+40), so unknown R2xB?(3) is a loss.
		//
		// However, if Red Two were known, it would take Blue 1.
		// For this to happen,
		// Red Two would have to approach Blue 1, because if Blue 1
		// approached Red Two, then it would have a chase rank
		// of One.  If it does happen, it still is not a blunder,
		// because an opponent who uses an unknown One to chase
		// a Five isn't playing well anyway.
		//
		// Note that the value of a suspected rank must always
		// be higher than the minimum rank value.  If the piece
		// is a suspected (but not known) Flag, the AI wants
		// to risk its minimum valued ranks on attacking the Flag
		// (WIN) even if the flag has adjacent pieces that can
		// win the AI piece.
		//

				if (p.isSuspectedRank())
					v = values[p.getColor()][Rank.UNKNOWN.toInt()] + v / 5;

				if (p.isBlocker())
					v += 10;

			} // piece not known
		} // v == 0

		else {
			// The piece is a known Unknown.
			// A known Unknown perhaps is more certain, but
			// still is only a guess.
			// Best guess is the value is the same as a known.
		}

		return v;
	}

	int winFight(Piece fp, Piece tp)
	{
		Rank fprank = fp.getRank();
		Rank tprank = tp.getRank();

		int result = winRank[fprank.toInt()][tprank.toInt()];

		if (result == Rank.UNK) {

		if (tp.getColor() == Settings.topColor) {

			// AI IS DEFENDER (tp)

			assert fprank == Rank.UNKNOWN : "opponent piece is known? (" + fprank + ")";

		// by definition, attack on invincible rank loses or is even

			if (isInvincible(tp)) {
				if (tprank.toInt() <= lowestUnknownRank
					&& !isPossibleSpy(tp, fp))
				return Rank.LOSES; // could be EVEN
			}

		// If the attacker has chased an unknown piece,
		// it usually indicates that the piece is weak (5-9)
		// However, be wary of this rule in the opponent flag area,
		// because unknown pieces are often approached
		// in the opponent flag area to fend off attack.
		// Unknown eights trying to get at the bombs are
		// especially vulnerable.
			else if (fp.getActingRankChase() == Rank.UNKNOWN
				&& (flag[Settings.bottomColor] == null
					|| Grid.steps(fp.getIndex(), flag[Settings.bottomColor].getIndex()) > 2)) {
					if (tprank.toInt() < lowestUnknownExpendableRank)
						return Rank.WINS;	// maybe not
					else if (tprank.toInt() == lowestUnknownExpendableRank)
						return Rank.EVEN;	// maybe not
			}

			else if (isFleeing(tp, fp))
				return Rank.LOSES;	// maybe not

		// As a last ditch effort to avoid a draw,
		// and the AI is winning, the AI encourages its
		// expendable pieces to randomly attack unknowns,
		// especially on the back rank.
		// To get to the back rank, the AI will risk these pieces
		// passing unmoved unknowns.
		// Note that simply passing an unmoved unknown that does
		// not attack results in a flee rank for the piece, so
		// if the piece subsequently moves, it becomes a target.
		// This is a non-symmetric rule.

			else if ((isExpendable(tprank))
				&& !fp.hasMoved()
				&& isWinning(Settings.topColor) >= VALUE_FIVE)
				return Rank.LOSES;	// maybe not

		// If the opponent no longer has any unknown expendable
		// pieces nor a dangerous unknown rank,
		// then it is unlikely that an unknown opponent
		// piece will randomly attack an unknown AI piece.
		// This is a non-symmetric rule.

			else if (lowestUnknownExpendableRank < 5
				&& tprank.toInt() < dangerousUnknownRank
				&& !tp.isKnown())
				return Rank.LOSES;

		// And unknown Fives chase unknown pieces with impunity

			else if (tprank == Rank.FIVE
				&& !tp.isKnown()
				&& isWinning(Settings.topColor) >= VALUE_FIVE)
				return Rank.LOSES;	// maybe not

		// Any piece will take a SPY.

			else if (tprank == Rank.SPY)
				return Rank.WINS;

		} else {

		// AI IS ATTACKER (fp)

		assert tp.getRank() == Rank.UNKNOWN : "opponent piece is known? (" + fp.getRank() + "X" + tp.getRank() + ")";

		// if tp is a bomb and fp is not an Eight,
		// the result is handled in UNK
		if (fprank != Rank.EIGHT && isPossibleBomb(tp))
			return result;

		// by definition, invincible rank wins or is even
		// on attack of unknown moved pieces.
		if (isInvincible(fp))
			return Rank.WINS;	// maybe not, could be EVEN

		else if (tp.getActingRankChase() == Rank.UNKNOWN
			&& (flag[Settings.bottomColor] == null
				|| Grid.steps(tp.getIndex(), flag[Settings.bottomColor].getIndex()) > 2)) {
				if (fprank.toInt() < lowestUnknownExpendableRank)
					return Rank.WINS;	// maybe not
				else if (fprank.toInt() == lowestUnknownExpendableRank)
					return Rank.EVEN;	// maybe not
		}
		// A spy almost always loses when attacking an unknown
		else if (fprank == Rank.SPY)
			return Rank.LOSES;	// maybe not

		// Test ActingRankFlee after ActingRankChase
		// in case piece has both.
		else if (isFleeing(fp, tp))
			return Rank.WINS; // maybe not, but who cares?

		} // ai attacker
		} // result UNK
		
		return result;
	}

	// Note: Acting Rank is an unreliable predictor
	// of actual rank.
	//
	// Often an unknown opponent piece (such as a 1-3)
	// will eschew taking a known ai piece (such as a 4-7)
	// to avoid becoming known.  So to determine whether the
	// piece fled because it is a low ranked piece avoiding
	// discovery or a high ranked piece avoiding loss,
	// check the stealth value of the lowest unknown rank
	// against the value of the rank that fleed.  Add 25%
	// as a margin of error.  If the stealth value is less
	// than this value, the unknown should have taken the piece 
	// instead of fleeing, so the AI decides that the unknown
	// fled because it is a higher ranked piece, and is
	// no threat to the AI.   However, if the stealth value
	// is greater than this value, the unknown could be the
	// lowest ranked piece and fled to avoid discovery, so
	// the AI assumes that the unknown is a threat.
	//
	// If the fp actingRankFlee is >= tp rank
	// it is probably only an even exchange and
	// likely worse, so the move is a loss.
	// However, it is only a loss for low actingRankFlee
	// or for high tp rank.
	protected boolean isFleeing(Piece fp, Piece tp)
	{
		Rank fprank = fp.getRank();
		Rank fleeRank = tp.getActingRankFlee();
		if (fleeRank != Rank.NIL
			&& fleeRank != Rank.UNKNOWN
			&& fleeRank.toInt() >= fprank.toInt()
			&& (valueStealth[tp.getColor()][lowestUnknownRank-1] * 5 / 4 < values[fp.getColor()][fleeRank.toInt()]
				|| fprank.toInt() >= 5))
			return true;

		else if (fleeRank != Rank.NIL
			&& fleeRank != Rank.UNKNOWN
			&& (fleeRank.toInt() == fprank.toInt()
				|| fleeRank.toInt() + 1 == fprank.toInt())
			&& fleeRank.toInt() >= 5)
			return true;
		return false;
	}

	boolean hasSpy(int color)
	{
		return rankAtLarge(color, Rank.SPY) != 0;
	}

	// An expendable Eight is an Eight that is not needed to
	// remove flag bombs, because either there are excess Eights
	// or fewer structures that can contain bombs.  (However, if
	// the opponent still has many unknown bombs, an Eight could
	// still be decisive in a game with few pieces remaining.)
	//
	// An expendable Eight is worth less than a Seven.
	// but probably more than a Nine (cannot think of an example
	// where a Nine could be worth more?)
	//
	// TBD: need examples
	// - loss of Seven v. Eight => loss of game
	// - few remaining pieces + Eight
	// - multiple square movement v. single square movement

	void expendableEights(int color)
	{
		values[color][Rank.EIGHT.toInt()] = values[color][Rank.SEVEN.toInt()] - 5;
		values[color][Rank.NINE.toInt()] = values[color][Rank.EIGHT.toInt()] - 5;
	}

	boolean isValuable(Piece p)
	{
		return values[p.getColor()][p.getRank().toInt()] > VALUE_FIVE;
	}

	// If all bombs have been accounted for,
	// the rest must be pieces (or the flag).
	// In this case, the piece is susceptible to
	// attack by an invincible piece.
	boolean isPossibleBomb(Piece p)
	{
		return (p.moves == 0 && unknownRankAtLarge(p.getColor(), Rank.BOMB) != 0);
	}

	// How to guess whether the marshal will win or lose
	// when attacked by an unknown piece.
	//
	// The most conservative approach is to assume that
	// any unknown piece could be a Spy, if the Spy is
	// still on the board.
	//
	// A modest aggressive approach is if the AI has already
	// guessed the location of the Spy, it will assume
	// that any other piece is not a Spy.
	//
	// An more aggressive approach is to check
	// ActingRankChase and Piece.isRankLess().  These
	// determine if the piece approached another piece
	// in the past, so one could assume that a non-reckless
	// player would not have moved its spy into a position
	// where it could be attacked.  So the piece is
	// probably not a Spy.  For example,
	// R1
	// B4 B?
	//
	// Unknown red One approached known Blue Four and it did not
	// move on its turn, so unknown Blue now has a chase
	// rank of Two.  isRankLess() is true, so this is a case
	// where unknown Blue could still be a Spy.
	//
	// Also, a suspected Bomb or Flag might actually be
	// a Spy.  So the One could be lost if a bomb pattern
	// or suspected Flag actually turned out to be a Spy.
	// (NOTE: The One will avoid moving next to a suspected
	// bomb because of bluffing value of Bomb X Piece in LOSES).
	// R1
	// --
	// BS
	// BF BB
	//
	// If a piece gains an chase rank of Spy, the One will
	// also avoid it.  For example:
	// B1 -- R1
	// -- BS --
	//
	// Blue Spy moves between Blue One and Red One.  It does
	// this only because Blue is winning and it wants to
	// remove Red One from the board in an exchange, even if
	// it has to sacrifice its Spy (which is worthless anyway
	// with the Ones gone).  Red One must either take the
	// piece or move away.
	//
	// The AI usually opts for the mid aggressive approach because
	// the most aggressive approach can be learned
	// and then used to defeat the AI (i.e., if the player
	// learns to bluff with its Spy, it can cause the AI
	// to lose its One).
	//
	// However, if the AI is losing and there is a dangerous
	// known opponent piece running amok (this normally results
	// in a sure loss unless the AI can stall and draw),
	// the AI takes the most aggressive approach.

	boolean isPossibleSpy(Piece fp, Piece tp)
	{
		Rank fprank = fp.getRank();
		if (fprank == Rank.ONE
			&& fp.isKnown()
			&& hasSpy(Settings.bottomColor)
			&& suspectedRankAtLarge(Settings.bottomColor, Rank.SPY) == 0
			&& (isWinning(Settings.topColor) > 0
				|| !(dangerousKnownRank != 99
					&& tp.getActingRankChase() != Rank.NIL
					&& !tp.isRankLess())))
			return true;
		return false;
	}

	// If an opponent piece has a suspected rank,
	// it is quite possible that the AI has guessed wrong
	// and made a wrong assumption about whether an attack
	// results in a win, loss or even exchange.
	//
	// The risk of loss increases the narrower the difference
	// between the ranks as well as the value of the AI piece at risk.
	int riskOfLoss(Piece fp, Piece tp)
	{
		assert fp.getColor() == Settings.topColor : "fp must be top color";
		Rank fprank = fp.getRank();
		Rank tprank = tp.getRank();

		if (fprank == Rank.BOMB)
			return 0;

		assert fprank == Rank.SPY || fprank.toInt() <= tprank.toInt()
			: fprank + " loses to " + tprank;

		if (tp.isKnown() || !tp.isSuspectedRank())
			return 0;

		// The AI believes the opponent rank is weaker if it fled
		// from a higher rank, but only if it
		// was not trying to remain cloaked.
		if (isFleeing(fp, tp))
			return 0;

		// The Spy risks total loss regardless of the opposing rank
		if (fprank == Rank.SPY)
			return values[fp.getColor()][fprank.toInt()];

		if (isInvincible(fp)) {
			if (isPossibleSpy(fp, tp))
				return values[fp.getColor()][fprank.toInt()]*7/10;
			return 0;
		}

		// If the opponent has a dangerous unknown rank,
		// it may approach other ranks to get at a low ranked
		// AI piece.
		if (fprank.toInt() <= 4
			&& fprank.toInt() > dangerousUnknownRank)
			return values[fp.getColor()][fprank.toInt()]*7/10;

		// The risk of loss depends on the piece value
		// and the difference between the ranks.

		// Example
		// 4x4 : -100
		// 3x4 : -50
		// 2x4 : -44
		// Thus, if the AI must capture the suspected Four
		// it will use the lowest known rank (Two).
		// However, if the AI ranks are unknown, stealth
		// dominates, making a capture with the Three
		// the most palatable.
		//
		// Example
		// 5x5 : -50
		// 4x5 : -25
		// 3x5 : -22

		// TBD: this needs further tuning.
		// e.g. a suspected rank of Four
		// that actually chased a piece (rather than protecting)
		// rarely turns out to be a lower rank, because a suspected
		// rank of Four means that the piece chased a known Five,
		// which are unpredictable, and could easily have
		// counter-attacked and exposed the lower rank.

		int diff = 1 + tprank.toInt() - fprank.toInt();
		diff = diff * diff;

		return values[fp.getColor()][fprank.toInt()] / diff;
	}
}


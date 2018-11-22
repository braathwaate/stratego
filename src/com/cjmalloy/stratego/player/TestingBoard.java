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

import java.util.ArrayList;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.UndoMove;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.TestPiece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Settings;
import java.util.Random;



public class TestingBoard extends Board
{
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
	// • Each of the lowest ranked pieces (1-5) is worth two of
	//	the next lowest rank.  (While this may not be accurate
	//	for competitive human play, it seems to hold true
	//	for AI play, because the loss of any of its low ranking
	//	pieces means almost certain loss once the remaining
	//	pieces are traded off.)
	// • If the opponent has the Spy in play,
	//	the value of the Marshal is multiplied by 0.9.
	// • An unknown piece is worth more than a known piece (stealth).
	// • If a player no longer has any pieces of rank N
	//	and rank N-1, the value of the opponent's rank N-1 becomes
	//	equal to Math.min(value(N), value(N-1)), because pieces of
	//	both rank N and N-1 have the same strength.
	// • Known bombs are worthless, except if they surround a flag.
	//	These bombs are worth a little more than a Miner.
	// • An unsuspected Spy has a value between a Colonel and a
	//	Major.  It is obviously worth more than a Major, which
	//	can be lost and still the game can be won.  But
	//	losing a Three makes it much harder for the AI to win.
	//	However, once the Spy is suspected,
	//	it has limited value except in certain reduced piece
	//	endgames with Ones.
	// • The Nine is the lowest valued piece on the board.  However,
	//	its stealth value is higher than ranks Six and Seven.
	//	High stealth value encourages the Nine
	//	to maintain its stealth by moving only square at a time.
	//	An unmoved Nine should attack a suspected One, Two or
	//	or Three, so its combined value (value + unmoved state
	//	+ stealth) must be less than the stealth of the attacked
	//	low ranked piece.  A known Nine should attack a suspected
	//	Four.
	// • The value of a Miner varies depending on the number
	//	of possible opponent bombed structures remaining.
	//	When there are less Miners than structures,
	//	a known Miner is equal to a known Six
	//	but an unknown Miner has more stealth than a Six,
	//	making it considerably more valuable, but not as
	//	valuable as a Five.
	//	Otherwise the Miner is expendable and is worth less than
	//	a Seven.
	// • If the AI is winning, an AI piece is worth less than
	//	the opponents piece of the same rank.  If the AI is losing,
	//	its pieces are worth more.
	// • An unbombed flag is worth about a known Three.  (It is not
	//	the highest value because of the opponent never really
	//	knows for sure the location of the flag.  See the code
	//	for more information).
	// 
	// Note that known Sevens and Nines have very little value,
	// and the AI should sacrifice these pieces by approaching
	// unknown opponent pieces, because the possibility of gaining
	// almost any discovery exceeds their value.  If the AI is
	// winning (and therefore its pieces are reduced in value)
	// a known Seven (or Nine) should sacrifice itself
	// to reveal a suspected Four but not a suspected Five.

	private static final int VALUE_ONE = 6400;
	private static final int VALUE_TWO = VALUE_ONE/2;
	private static final int VALUE_THREE = VALUE_TWO/2;
	private static final int VALUE_FOUR = VALUE_THREE/2;
	private static final int VALUE_FIVE = VALUE_FOUR/2;
	private static final int VALUE_SIX = VALUE_FIVE/2;
	private static final int VALUE_SEVEN = VALUE_SIX * 60/100;
	private static final int VALUE_EIGHT = VALUE_SIX;

    // Opponent Nine value must be more than lowest AI stealth
    // (usually six or seven)
    // or it will never be attacked, leaving it to roam forever ...
	private static final int VALUE_NINE = VALUE_SIX * 40/100;

	private static final int [] startValues = {
		0,
		VALUE_ONE,	// 1 Marshal
		VALUE_TWO, 	// 2 General
		VALUE_THREE, 	// 3 Colonel
		VALUE_FOUR,	// 4 Major
		VALUE_FIVE,	// 5 Captain
		VALUE_SIX,	// 6 Lieutenant
		VALUE_SEVEN,	// 7 Sergeant
		VALUE_EIGHT,	// 8 Miner
		VALUE_NINE,	// 9 Scout
		0,	// Spy valued by code
		0,	// Bomb valued by code
		1000,	// Flag (valued by code)
		0,	// Unknown valued by code, minimum piece value
		0	// Nil
	};
	public int [][] values = new int[2][15];

	private static final int DEST_PRIORITY_DEFEND_FLAG = VALUE_NINE;
	private static final int DEST_PRIORITY_DEFEND_FLAG_AREA = 7;

	// Note: DEST_PRIORITY_LANE must have unique priority
	// because it is a special case in planv.  It is limited to
	// the lanes and has priority over CHASE, CHASE_ATTACK
	// and CHASE_DEFEND.  This is because if the AI has weak pieces
	// in the lanes, they can block chases.   So if the chase piece
	// is blocked by pieces in the lanes, it will just move back
	// and forth ad infinitum, if the chase had higher priority.
	//
	// Note: The pruning scheme selects only the best move based
	// on these priorities for pieces outside the active area.   So
	// pieces that block higher priority pieces are never moved.
	// So blocking is a problem not only in the lanes, but anywhere
	// on the board.  But usually if the lanes are not blocked, the
	// chase piece can find a way to its target.

	private static final int DEST_PRIORITY_LANE = 6;

	// Note: DEST_PRIORITY_ATTACK_FLAG should be higher than
	// DEST_PRIORITY_CHASE, because an Eight could be
	// an expendable piece and can chase other opponent pieces,
	// but it is better for the eight to attack the flag structure.

	private static final int DEST_PRIORITY_ATTACK_FLAG = 3;

    // If there is a way of attacking a flag with a scout,
    // this is very high priority, because it can reach the flag
    // quickly
	private static final int DEST_PRIORITY_ATTACK_FLAG_WITH_SCOUT = 5;

	private static final int DEST_PRIORITY_CHASE_ATTACK = 4;
	private static final int DEST_PRIORITY_CHASE_DEFEND = 3;

    // Flag bias is 1, so chase priority must be 2 or higher,
    // so that it will chase a piece one step away.
	private static final int DEST_PRIORITY_CHASE = 2;
	private static final int DEST_PRIORITY_LOW = 1;

	private static final int DEST_VALUE_NIL = 9999;
	private static final int GUARDED_OPEN = 0;
	private static final int GUARDED_UNKNOWN = 1;
	private static final int GUARDED_OPEN_CAUTIOUS = 2;
	private static final int GUARDED_CAUTIOUS = 3;
	private static final int VALUE_BLUFF = 2;	// negative discourages bluffing

	protected Piece[][] planAPiece = new Piece[2][15];	// moved rank Piece
	protected Piece[][] planBPiece = new Piece[2][15];	// unmoved rank Piece
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[][][][] planA = new int[2][15][2][121];	// plan A
	protected int[][][][] planB = new int[2][15][2][121];	// plan B
	protected int[] sumValues = new int[2];
	protected int value;	// value of board
	protected int[] unmovedValue = new int[121];    // unmoved value
	protected int[][] valueStealth = new int[2][15];
	protected static final int attacklanes[][] = { { 89, 90}, { 93, 94}, {97, 98} };
	protected long[] hashTest = new long [2];
	protected int lowestUnknownNotSuspectedRank;
	protected int lowestUnknownExpendableRank;
	protected int dangerousKnownRank;
	protected int dangerousUnknownRank;
	protected int[] unknownRank = new int[2];
	public int depth = -1;
	protected int needExpendableRank;
        protected ArrayList<Piece> ghostPieceStack = new ArrayList<Piece>();
        protected Piece ghostPiece = null;

// Silly Java warning:
// Java won't let you declare a typed list array like
// public ArrayList<Piece>[] scouts = new ArrayList<Piece>()[2];
// and then it warns if you created a non-typed list array.
@SuppressWarnings("unchecked")
	public ArrayList<Piece>[] scouts = (ArrayList<Piece>[])new ArrayList[2];

	protected boolean randomForay;
	private static final int VALUE_MOVED = VALUE_NINE/3;

	private static final int UNK = 0;
	private static final int WINS = 1;
	private static final int LOSES = 2;
	private static final int EVEN = 3;

	// cache the winFight values for faster evaluation
	protected static int[][] winRank = new int[15][15]; // winfight cache
	static {
		for (Rank frank : Rank.values())
		for (Rank trank : Rank.values())
			switch (frank.winFight(trank)) {
				case Rank.WINS :
					winRank[frank.ordinal()][trank.ordinal()] = WINS;
					break;
				case Rank.LOSES :
					winRank[frank.ordinal()][trank.ordinal()] = LOSES;
					break;
				case Rank.EVEN :
					winRank[frank.ordinal()][trank.ordinal()] = EVEN;
					break;
			}
	}

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

        // super(t) makes a copy of the board info
        // but not the suspected rank analysis
        // so it is called again

        genSuspectedRank();

		// mark weak pieces before copying below
        // because the weak attribute is persistent

		// markWeakPieces() depends on hasFewWeakRanks()
		// which depends on genSuspectedRank
		markWeakPieces();

		value = 0;
		hashTest[0] = boardHistory[0].hash;	// for debugging (see move)
		hashTest[1] = boardHistory[1].hash;	// for debugging (see move)

		needExpendableRank = 0;
		for (int c = RED; c <= BLUE; c++) {
			for (int j=0;j<15;j++) {
				planAPiece[c][j] = null;
				planBPiece[c][j] = null;
				neededRank[c][j] = false;
				values[c][j] = startValues[j];
				valueStealth[c][j] = 0;
			}
		} // color c

		for (int i=12;i<=120;i++) {
			if (!Grid.isValid(i))
				continue;
			Piece bp = getPiece(i);
			if (bp != null) {

		// Make a copy of the piece because the graphics
		// thread is also using board/piece information for display

				TestPiece p = new TestPiece(bp);
				grid.setPiece(i, p);

				Rank rank = p.getRank();
				int r = rank.ordinal();

		// only one piece of a rank is assigned plan A
		// and preferably a piece that has moved or is known and as
		// far forward on the board as possible

				if (!hasPlan(planAPiece, p.getColor(), r))
					planAPiece[p.getColor()][r-1]=p;

				if (p.isKnown()
					|| (p.hasMoved() && !planAPiece[p.getColor()][r-1].isKnown())) {
					planBPiece[p.getColor()][r-1]=planAPiece[p.getColor()][r-1];
					planAPiece[p.getColor()][r-1]=p;
				} else
					planBPiece[p.getColor()][r-1]=p;
			}
		}

		adjustPieceValues();
		genDangerousRanks();
		genForay();	// depends on sumValues, dangerousUnknownRank

		// Destination Value Matrices
		//
		// Note: at depths >8 moves (16 ply),
		// these matrices may not necessary
		// because these pieces will find their targets in the 
		// move tree.  However, they would still be useful in pruning
		// the move tree.
		for (int c = RED; c <= BLUE; c++)
		for (int rank = 0; rank < 15; rank++) {
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

		valuePieces();
		genValueStealth();	// depends on valuePieces
		genInvincibleRank();	// depends on stealth
		genDestFlag();
		aiFlagSafety(); // depends on genDestFlag, valueStealth, values

		// The following code depends on values[] and valueStealth[]
		// (and values[Miner] which depends on genDestFlag()))
		genUnknownRank();

		// depends on unknown rank
		adjustInvincibleRanks();
		genFleeRankandWeak();

		scouts[0] = new ArrayList<Piece>();
		scouts[1] = new ArrayList<Piece>();
		for (int i=12;i<=120;i++) {
			if (!Grid.isValid(i))
				continue;
			TestPiece p = (TestPiece)getPiece(i);
			if (p == null)
				continue;
			Rank rank = p.getRank();
			if (rank == Rank.FLAG)
				continue;

			else if (rank == Rank.BOMB) {

		// If the bomb is deemed valuable, launch an effort
        // to remove it.

				if (p.isFlagBomb() || isForaySquare(1-p.getColor(), p.getIndex())) {
					attackBomb(p);
                    if (p.targetValue == 0)
                        p.targetValue = pieceValue(Settings.topColor, 8);
                }
				continue;
			}
		
			else if (rank == Rank.NINE
				|| (unknownRankAtLarge(p.getColor(), Rank.NINE) != 0
					&& rank == Rank.UNKNOWN))
				scouts[p.getColor()].add(p);

		// Encourage lower ranked pieces to find pieces
		// of higher ranks.
		//
		// Note: this is a questionable heuristic
		// because the ai doesn't know what to do once
		// the piece reaches its destination.
		// However, the position can evolve into one
		// where material can be gained.

			chase(p);

		}
		attackLanes();

		// keep a piece of a high rank in motion,
		// preferably a 6, 7 or 9 to discover unknown pieces.
		// 5s are also drawn towards unknown pieces, but they
		// are more skittish and therefore blocked by moving
		// unknown pieces.
		if (needExpendableRank != 0)
			setNeedExpendableRank(Settings.topColor);

		// setunmovedValues depends on neededRank:
		// chase(), setNeedExpendableRank()
		setUnmovedValues();

		// Call genUnknownRank again because setUnmovedValues()
		// could change pieceValue()

		genUnknownRank();
		assert flag[Settings.topColor] != 0 : "AI flag unknown";
	}

	int missingValue(int c, int r)
	{
		if (r == 10 || rankAtLarge(c, r) != 0)
			return values[1-c][r-1];
		return Math.min(values[1-c][r-1], missingValue(c, r+1));
	}

	void adjustPieceValues()
	{
		for (int c = RED; c <= BLUE; c++) {

		// If a player no longer has any pieces of rank N
		// and rank N-1, the value of the opponent's rank N-1 becomes
		// equal to Math.min(value(N), value(N-1)), because pieces of
		// both rank N and N-1 have the same strength.
		//
		// The rank value is set to the lowest value of any of the ranks.
		//
		// Example 1. Blue has a 2, 5, 8, 9.  Red has a 2, 4, 9, 9.
		// Red 3 value is the same as Red 4 value.
		// Blue 5,6,7 value is the same as Blue 7 value.
		//
		// Example 2.  Blue has 5, 8, 9.  Red has 4 4 5 6.
		// Red 5,6 value is the same as Red 7.
		//
		// Example 3.  Blue has 1, S, 6, 7.  Red has 1, S, 2, 5, 8.
		// The value of Red One is 3/4 of Red One original value.
		// Blue 6 value is the same as Blue 7.
		//
		// Example 4.  Blue has S, 6, 7.  Red has 1, 4, 8.
		// Red 2,3,4 value is the same as Red 5.
		// The value of Red One is 3/4 of the value of
		// Red Five.  This makes
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

			for (int r = 1; r <= 7; r++)
				if (rankAtLarge(c, r) == 0)
					values[1-c][r] = missingValue(c, r+1);

		// The value of the Spy is worth somewhere between
		// a Three and a Four with the opponent One still on the board.
        // As those pieces are removed, the Spy falls in value.
        // (A high Spy value results in the AI dropping pieces).

			values[c][10] = Math.min(
                            values[1-c][3] + values[1-c][4],
                            values[c][3] + values[c][4])/2;

		// The value of a One is worth somewhat less if the opponent
		// still has the Spy.  This encourages the One to trade
		// with an opponent One if the player does not have the Spy,
		// making the opponent One more valuable.  However, the
		// difference in value must not be more than the stealth.
		// Otherwise the AI would exchange its unknown One for
		// a known One, which could easily lead to the loss of
		// the rest of its pieces, if the loss of its One made
		// some of the opponent's ranks invincible.
		// By reducing the value to 90%, the One loses 80 points,
		// which is less than the stealth value.
		// The AI will sacrifice a Five but not a Four to
		// exchange known Ones, where the opponent has a Spy but
		// the AI doesn't.
		//
		// TBD: If the opponent Spy is suspected, then the value
		// of the player One is hardly affected, as the Spy
		// is easily avoided.

			if (hasSpy(1-c))
				values[c][1] = values[c][1]*9/10;

		// If the opponent Marshal is off on the board,
		// the Spy value is reduced.

			if (rankAtLarge(1-c, Rank.ONE) == 0)
				values[c][10] = values[c][7]/2;

		// The opponent spy, once suspected, is worth maybe a Five, but
		// this depends on the remaining pieces, because in an endgame with
		// Ones on the board, the Spy can be the critical piece to win the game.

			else if (c == Settings.bottomColor)
				values[c][10] = VALUE_FIVE; // TBD: depends on remaining pieces

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
		// that count.  Yet these pieces have some value
		// in the endgame and in their ability to pose as other pieces.
		//
		// TBD: This formula fails towards the end of the game,
		// when the number of pieces remaining dominates.
		// Still, it is not clear which combination of
		// pieces and known/unknown status create winning endgames.
		// As such, this would have general
		// applicability to the game of stratego, so someone should
		// analyze the endgames and publish a FAQ.
		//
		// Note: genDestFlag() depends on sumValues.
		// Note: genDestFlag() revalues Eights.
		// 
			sumValues[c] = 0;
			for (int rank = 1; rank <= 10; rank++) {
				if (rank >= 8) {
					int n = unknownRankAtLarge(c, rank);
					sumValues[c] += (11 - rank) * 10 * n;
					n = knownRankAtLarge(c, rank);
					sumValues[c] += 10 * n;
				} else {
					int n = rankAtLarge(c, rank);
					sumValues[c] += values[c][rank] * n;
				}
			}

		} // color
	}

	void genDangerousRanks()
	{
		for (lowestUnknownNotSuspectedRank = 1;
			lowestUnknownNotSuspectedRank != Rank.UNKNOWN.ordinal()
                && unknownNotSuspectedRankAtLarge(Settings.bottomColor, lowestUnknownNotSuspectedRank) == 0;
			lowestUnknownNotSuspectedRank++);

		// dangerousUnknownRank is set when an opponent
		// has an invincible unknown rank.  This increases
		// the risk of loss (see riskOfLoss()) when opponent
		// pieces approach AI pieces.
		// Normally, the AI assumes that the opponent
		// will not subject its unknown low ranked pieces to attack.
		// But if the opponent has an invincible unknown rank,
		// an unknown opponent piece might approach an unknown AI piece
		// because it only risks discovery rather than complete
		// loss of its piece.  

		dangerousUnknownRank = 99;
		dangerousKnownRank = 99;
		for (int rank = 1; rank <= 9; rank++) {

			if ((rank == 1 && !hasSpy(Settings.topColor))
				|| rank != 1) {
				if (unknownRankAtLarge(Settings.bottomColor, rank) != 0
					&& dangerousUnknownRank == 99)
					dangerousUnknownRank = rank;
				else if (knownRankAtLarge(Settings.bottomColor, rank) != 0
					&& dangerousKnownRank == 99)
					dangerousKnownRank = rank;

			}
			if (unknownRankAtLarge(Settings.topColor, rank) != 0)
				break;
		}
	}

	int minPieceValue(int color)
	{
		int min = VALUE_ONE * 2;
		for (int r = 1; r <= 10; r++)
			if (rankAtLarge(color, r) != 0)
				if (pieceValue(color, r) < min)
					min = pieceValue(color, r);
		return min;
	}

	int maxExpendableValue(int color)
	{
		int max = 0;
		for (int r = 1; r <= 10; r++)
			if (rankAtLarge(color, r) != 0
				&& isExpendable(color, r)
				&& pieceValue(color, r) > max)
					max = pieceValue(color, r);
		return max;
	}

	void setNeedExpendableRank(int c)
	{
		// if player is not winning, it is best to let
		// opponent attack and try for a draw

		if (isWinning(c) < 0)
			return;

		int count = 0;  // number of unknown moved expendable ranks
		for (int i=12;i<=120;i++) {
			Piece p = getPiece(i);
			if (p == null || p.getColor() != c)
				continue;
			if (!isExpendable(p)
				|| p.isKnown())
				continue;
			if (p.hasMoved()) {
				if (p.getRank().ordinal() <= needExpendableRank)
                    return;
                count++;
            }
		}

		for (int r = 2; r <= 9; r++) {

        // The AI tries to keep its expendable pieces
        // unmoved except when needed, because it makes it
        // harder for the opponent to guess the flag and bombs
        // and a moved piece is fair game to attack.
        // A rank equal or lower than needExpendableRank
        // has been requested.   But if those ranks are buried
        // (perhaps behind bombs), then the request can never
        // be serviced.   So the AI always tries to have
        // at least one unknown moved expendable rank in play,
        // even if it isn't the desired rank, because these
        // pieces are useful for bluffing.

            if (r > needExpendableRank
                && count != 0)
                return;

		// Set the neededRank.  The search tree will
		// move the rank that can make the most progress.

			if (isExpendable(c,r))
				setNeededRank(c,r);
        }
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
	// I have seesawed on whether an unknown Marshal
	// should take a Three.  If it takes a Three,
	// it makes the opponent Two invincible.
	// But if won't take a Three, then the opponent Threes
	// become defacto invincible once the Two is known.
	//
	// The decision on whether to take the Three
	// rests on how vulnerable the AI's position is to attack
	// by an invincible opponent Two.  If the AI has few moved pieces,
	// its Threes are unknown, and the AI Two is unknown and well
	// located for defense, taking a Three is advisable.
	//
	// However, an unknown AI One will always attack a Three
	// once any of the Twos or any of the Threes
	// are gone from the board.
	//
	// An unknown General will attack a Four only if a couple of
	// Threes are gone from the board.  (If the opponent makes a foray
	// with a Four, the AI relies on the fact that it has 2 Threes
	// and 3 Fours to repulse the attack).
	//
	// Stealth value for pieces (1-4) is slightly less than
	// the sum of next six higher ranked pieces still on the board
	// times a risk factor if stealth is maintained.
	//
	// The risk factor is 10%, which is the probability
	// of capture of each of these pieces.
	//
	// If the piece is the only player piece that prevents an opponent
	// piece from becoming a dangerous rank, the value is increased
	// by 1/3.  If there are more than one player pieces, the value is
	// decreased by 1/3.
	//
	// Stealth of a low ranked piece decreases the less chance
	// it has for capture a less valuable piece.
	// For example, if the Ones are still on the board, the
	// Twos have been removed, the stealth of the One
	// drops in half.
	//
	// A One stealth value is equal to 6400 *.1 (640 +- 213 points)
	// if both Twos and all the Threes are still on the board.
	//
	// A Two stealth value is equal to 4000 *.1 (400 +- 133 points)
	// if all Threes and two of the Fours are still on the board.
	//
	// A Three stealth value is equal to 2400 *.1 (240 +- 80 points)
	// if six Fours are still on the board.
	//
	// A Four stealth value is equal to 1200 * .1 (120 +- 40 points)
	// if six Fives are still on the board.
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
	// Stealth value for 5-9 is:
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
	//
	// For example,
	// B? B5 R4
	// The AI should not play R4XB5 if B? is a suspected Two,
	// (This is the same if B? is unknown, see unknownValue())
	//
	// For example,
	// B? B6 R5
	// The AI should not play R5XB6 if B? is a suspected Four.
	//
	// (In the same example, if B? is unknown, the AI would
	// not play R5XR6.)

	// (This situation can happen if B6 is protected
	// by more than one piece,
	// so the protectors do not obtain a suspected rank.)

	// Higher ranked pieces (known 6 and unknown 7,9) will be eager
	// to sacrifice themselves to discover ranks (1-2) and the
	// ranks (known 7 and unknown 9) are eager to discover a Three.

	// Note that this changes based on blufferRisk.  As blufferRisk
	// increases, the AI is more willing to attack suspected
	// rank pieces to confirm their identify.  At low blufferRisk,
	// why bother?

	// Another area where stealth values are important is in
	// unknownValue().  If an AI Four has a choice between
	// an exchange with a suspected rank of Three or an Unknown,
	// the difference is the Three stealth or Two stealth, so
	// the Four will be inclined to take its chances with the Unknown.

	private void genValueStealth()
	{
		for (int c = RED; c <= BLUE; c++) {
		for (int r = 1; r <= 9; r++) {
		int v = 0;

		// Five stealth should be lower than the value of a Six (100)
		// minus a Seven (72).  Otherwise, a Six will attack
		// a Seven protected by a suspected Five.
		// This is basically an even exchange.
		// (A Six should always attack a Seven protected by an
		// unknown piece).

		if (r >= 5 && r <= 9) {

			final int stealthRatio[] = {0, 0, 0, 0, 0, 8, 5, 2, 2, 1};
		// Adjust stealth for missing pieces
			int i = r;
			while (i != 9 && values[c][i] == values[c][i+1])
				i++;

			v = values[c][r]/stealthRatio[i];

		} else {
			int n = 0;
			int unknownDefenders = 0;
			int count=6;
			boolean found = false;
			int vulnerable = 0;
			for (int rs = 1; rs<8; rs++) {
				if (rs > r) {
					n = rankAtLarge(1-c, rs);
					if (n != 0) {
						n = Math.min(n, count);
						v += values[1-c][rs] * n;
						count -= n;
						if (count == 0)
							break;
						found = true;
					}

					n = knownRankAtLarge(c, rs);
					if (n != 0) {
						n = Math.min(n, count);
						v += values[c][rs] * n;
						count -= n;
						if (count == 0)
							break;
					}

                // Known ranks are more vulnerable than unknown ranks,
                // especially if they have not moved.

					n = unknownRankAtLarge(c, rs);
					if (n != 0) {
						n = Math.min(n, count);
						v += values[c][rs] / 2 * n;
						count -= n;
						if (count == 0)
							break;
					}
					vulnerable += knownRankAtLarge(c, rs) * values[c][rs];
				}
				if (!found)
					unknownDefenders += unknownRankAtLarge(c, rs);
			}

		// note: if n is zero, then either there are no higher
		// ranks (v = 0) and stealth will be zero
		// or just one higher rank (v = higher rank value).

			if (unknownDefenders <= 1) {
				if (vulnerable > values[c][r]/4)
					v += v/3;
			}

		// If there is more than one equal or lower ranked unknown
		// defender, then it is safe for the invincible piece to
		// attack, because the other lower ranked unknown
		// invincibles still discourage a counter-attack
		// by the enemy.

		// If the rank is the lowest invincible, keep it cloaked.
		// For example, Red has an unknown One but Blue
		// only has a known Two.  Both sides have two known Threes
		// and an unknown Four.

		// The unknown One should remain cloaked to
		// negate the power of the opponent Two, but the unknown
		// Four can safely attack an unknown piece.

		// Yet in this particular example, it is better for
		// the Four to stay cloaked, leaving the known Threes
		// to run amok.  Otherwise, once the Four became known,
		// it could be cornered by Blue's superior pieces.
		// Thus it is only safe to reveal the lowest ranked
		// invincible piece.

		// Set the stealth value of an invincible piece
		// to the minimum opponent piece value.  Because this
		// does not include stealth, it will encourage an
		// invincible piece to slam into unknown pieces,
		// but having some stealth should make it target
		// a lower ranked known opponent piece first.

			else if (isInvincible(c, r)
				&& lowerRankCount[c][r-1] <= 1) {
				valueStealth[c][r-1] = values[1-c][9];
				continue;
			}
			else if (unknownDefenders > 3)
				v -= v/3;
			v = v / 8;

			if (c == Settings.bottomColor) {

		// If the opponent has a dangerous unknown rank,
		// and the AI suspects which piece it is,
		// it is best to keep it unknown, because once it becomes
		// discovered, it will certainly go on a rampage.

				if (dangerousUnknownRank != 99
					&& r <= dangerousUnknownRank
					&& r != 1)
					v = 0;

        // If the AI thinks it has guessed the rank,
        // then the rank has less stealth
        // (and perhaps negative stealth, if the opponent
        // does not realize the piece has been guessed)

                if (unknownNotSuspectedRankAtLarge(c, r) == 0)
                    v /= 2;

		// Prior to version 9.9, opponent stealth remained constant
		// during the game; it did not depend on remaining ranks
		// at large like player stealth does.  This lead to the AI
		// attacking the opponent suspected One with an invincible
		// piece such as a Four or Five, when it would have been much
		// better for the invincible piece to attack other unknowns
		// rather than confirm the identity of the One.  blufferRisk
		// helps to reduce this problem, but it does not eliminate it.
		// So opponent stealth needs to reduced like player stealth
		// as the game wears on and it becomes obvious which pieces
		// are strong and which are weak.

        // Version 12 increased opponent stealth because it
        // also increased the minimum AI piece stealth to Four
        // for pieces that could be construed as strong pieces.
        // See stealthValue().

		// Initial values (blufferRisk 3)
		// 1: 175
		// 2: 140
		// 3: 84
		// 4: 56

		// Note: Stealth values are modified based on movable piece
		// count (see below)

				v = (int)Math.sqrt(v);
				v *= blufferRisk * 4;
			}
		}

		valueStealth[c][r-1] = v;

		} // rank

		// Spy stealth is equal to about a Seven stealth.

		// Thus Spy X One is always a positive WIN, because
		// the Spy loses very little stealth in this attack.
		// And if the One is off the board, the value of the
		// Spy is less than a Seven.

		valueStealth[c][Rank.SPY.ordinal()-1] = valueStealth[c][6];

		// Bombs not surrounding the flag have no value,
		// but they do have stealth value, proportional to
		// the number of unknown bombs remaining.
		// Because if the player discovers all the bombs,
		// then all the remaining pieces are at risk.
		//
		// Note that even if the opponent has all 6 bombs,
		// the suspected bomb still has stealth,
		// because the AI may have guessed wrong, and the
		// bomb is really an unknown piece.   This is
		// very important when the AI piece is trapped
		// in an opponent bomb structure; it should try
		// attacking the suspected bomb as a last resort.

		valueStealth[c][Rank.BOMB.ordinal()-1] =
			(8 - unknownRankAtLarge(c, Rank.BOMB))*4;

		} // color

		// Flag stealth is about the same as an opponent Nine stealth,
		// so Nines will attack a suspected flag.
		// (see isNineTarget()).
		valueStealth[0][Rank.FLAG.ordinal()-1] = valueStealth[1][8];
		valueStealth[1][Rank.FLAG.ordinal()-1] = valueStealth[0][8];

		// If a player has a movable piece count majority, excess
		// expendable pieces just get in the way.
		// The player has the luxury of attack
		// for the sake of random discovery.
        // Version 11 proportionally modified the stealth value
        // of all pieces to encourage discovery.
        // But this skewed superior piece exchanges.
        // (e.g. an unknown One might not attack a Three,
        // because it had more stealth due to a lacking piece count).
        // So Version 12 adds a fixed bonus to the stealth values.

		int u = grid.pieceCount(Settings.topColor)
            - rankAtLarge(Settings.topColor, Rank.BOMB)
			- (grid.pieceCount(Settings.bottomColor)
            - rankAtLarge(Settings.bottomColor, Rank.BOMB));

		int c;
		if (u > 0)
			c = Settings.bottomColor;
		else
			c = Settings.topColor;
		u = Math.min(Math.abs(u), 10);
	
		for (int r = 1; r < 10; r++)	
			valueStealth[c][r-1] = valueStealth[c][r-1] + u * VALUE_NINE / 20;

		// Handle special cases

		// Prior to version 10.4, the AI always set
		// the stealth of the opponent One higher than
		// the AI's lowest piece value.  Otherwise, the AI would
		// not attack the suspected One and make it known,
		// and consequently, the AI Spy would not attack,
		// because the One must be known for the Spy to attack it,
		// because the Spy risks total loss if it is wrong
		// (see riskOfLoss()).
		//
		// While this makes sense in an endgame where the AI has a
		// spare expendable piece, the loss of a piece can
		// cause loss of the game in other situations.
		// And valueStealth is adjusted (see above) in situations
		// where the AI has an excess piece count majority.
		//
		// So to be consistent with the 
		// the strategy of leaving suspected pieces unknown
		// (see prior comments, "rampage" and
		// "opponent stealth remained constant"), version 10.4 also
		// leaves the suspected One unknown.   So in an endgame
		// where the opponent has two unknown pieces (the suspected
		// One and say a Seven buried in a bomb structure),
		// the AI Spy must be bold enough to approach the suspected One
		// and attack it if possible; othewise the game would
		// end in a draw.
		//
		// For the AI Spy to attack the suspected One, the
		// suspected One value must be greater than the AI Spy
		// value (see WINS).   This happens in two ways.
		// (1) The value of AI Spy is tied to the value of the opponent
		// Three and Four (see adjustPieceValues(), and so
		// drops as AI Threes and Fours are removed
		// from the board.   (2) The suspected value of the opponent One
		// increases with moves (see actualValue()).
		//
		// TBD: These kind of endgames may need to be handled
		// in a more specific way, such as a database of endings.
	}

	// Is the AI winning? If so, its pieces are worth less
	// to encourage it to exchange ranks
	// if not, its pieces are worth slightly more
	// to encourage it to avoid exchanging ranks.
	//
	// And the more the AI is winning, the more risk
	// it must be willing to accept to win the game.
	// Otherwise, the opponent can just run some piece around
	// in circles, so that the AI cannot guess the identity
	// of the remaining pieces or the location of the flag.
	//
	// Thus the wining AI has to be encouraged to enter into
	// questionable exchanges, where it knows it will likely
	// lose material.  The hope is that it has sufficient
	// material advantage that discovery will eventually
	// lead to victory.
	//
	// But not too much more so it allows lower ranks
	// to be captured for higher ranks.  For example,
	// if a suspected opponent Two is protecting a
	// known Four, the AI should not play 3x4.   But this is
	// a close exchange, because the AI gets the value of
	// the Four (200) and the stealth value of the Two
	// in exchange for its Three (400).
	//
	// So it is important that the stealth value of the
	// opponent suspected pieces diminish as pieces are
	// removed from the board.  But we still want to encourage
	// the AI to attack unknowns so that it might be able to
	// discover opponent pieces that would allow its pieces
	// to become invincible.
	//
	// TBD: Completely unknown pieces need to be targeted,
	// such as unmoved pieces or those that appear to be
	// avoiding discovery.
	//
	// if (sumValues[Settings.topColor] == 0) and
	// its flag is bombed and the opponent has an 8
	// the ai should surrender
	//
	void valuePieces()
	{
		// Not sure how this can happen anymore
		if (sumValues[Settings.topColor] == 0)
			return;

		// int vb = sumValues[Settings.bottomColor] * grid.movablePieceCount(Settings.bottomColor);
		// int vt = sumValues[Settings.topColor] * grid.movablePieceCount(Settings.topColor);
		int vb = sumValues[Settings.bottomColor];
		int vt = sumValues[Settings.topColor];

		for (int rank = 1; rank <= 10; rank++) {
			int v1 = values[Settings.topColor][rank]/2;
			long v2 = v1;
			v2 *= vb;
			v2 /= vt;
			v2 = Math.min(v2, v1);
			values[Settings.topColor][rank] = v1 + (int)v2;
		}
	}

	// Certain higher ranked pieces may also have the prospect
	// of a positive result from attacking unknown pieces,
	// if the stealth value of the unknown low ranked
	// opponent piece is higher than the value of the player
	// piece.  For example, the opponent One is unknown, but
	// the opponent Two and Threes are known.  So the player
	// Four is "invincible" (able to attack unknown pieces
	// with good prospect), because if the opponent One
	// is the unknown defender, the player still "wins",
	// because then its other pieces become invincible.
	//
	// Note that the AI stealth values are used because the
	// opponent stealth values are lower (explained elsewhere).
	// They are reduced to 80% because the AI really doesn't want
	// to trade its Three (400 pts) just to discover the opponent One 
	// (426 pts stealth), but it certainly would trade the Three
	// to discover the opponent One and gain a minor piece (or more) in
	// the trade.  The important point is that the trade makes
	// the unknown AI Two invincible, giving it a good shot at
	// gaining back a Three.

	void genInvincibleRank()
	{
		for (int c = RED; c <= BLUE; c++) {
            int lowUnknownRank = 1;
            for (int rank = 2; rank<9;rank++) {
                if (valueStealth[Settings.topColor][lowUnknownRank-1] * 8 / 10 > values[1-c][rank])
                    invincibleRank[1-c][rank-1] = true;
                    
                if (unknownNotSuspectedRankAtLarge(c, rank) > 0)
                    lowUnknownRank = rank;
            }
		}
	}

	// Until version 12.0, code in EVEN made an exchange
	// positive if an opponent piece was invincible, even if
	// the AI piece was unknown.  This is because it is necessary
	// to neutralize invincible pieces because they can attack
	// any piece with impunity.

	// But it should also be necessary to make an EVEN exchange
	// negative if an AI piece is invincible, so the AI piece
	// can continue as long as possible in attacking lesser pieces.

	// A simple way to accomplish both objectives is to
	// increase the value of the invincible piece.  Then there
	// is no need to check for this special case in the EVEN code.

	// TBD: If the AI is winning, it should also try for even
	// exchanges.  Yet it could be better to avoid even exchanges
	// and try to increase its advantage.

	// If the AI is losing, it may be better for the AI to flee
	// and hope for a tie by time expiration.  But this makes
	// for boring games, and the AI still has confidence that
	// it could win, in spite of that it may be losing.  And the
	// isWinning() function isn't perfect, because it only looks
	// at material value.

	// Note that suspected invincible pieces (except the One,
	// if the AI has the Spy) must also
	// be neutralized, although this can lead to loss
	// of stealth if the opponent is bluffing, or even
	// loss of the AI piece, if the opponent rank is
	// lower than suspected.

	// If the player has multiple invincible ranks, only the lowest
	// rank is set more valuable.  That is because the player only
	// needs one; the others may be happily traded off if the player
	// is winning to expedite the end of game.  If the player is losing,
	// his ranks will be more valuable, and he won't want to trade
	// them off.

	void adjustInvincibleRanks()
	{
		for (int c = RED; c <= BLUE; c++)
		for (int rank = 1; rank<9;rank++)
			if (rankAtLarge(c, rank) != 0
			    && rankAtLarge(1-c, rank) != 0
                && isInvincibleDefender(c, rank)
			    && !isInvincibleDefender(1-c, rank)) {
				values[c][rank] = values[1-c][rank] + stealthValue(1-c, rank) + values[1-c][unknownRank[1-c]]/2;
				break;
			}
	}

	// Because of shallow search depth, the AI is encouraged to
	// guard each lane with a sufficiently low rank to deter higher ranked
	// pieces from entering AI territory.

	void attackLanes()
	{
		for (int lane = 0; lane < 3; lane++) {

			int[][] fleetmp = new int[2][121];
			for (int c = RED; c <= BLUE; c++) {
			for (int j = 0; j <= 120; j++) {
				fleetmp[c][j] = DEST_VALUE_NIL;
			}
			for (int i : attacklanes[lane])
				for (int y = 0; y < 5; y++) {
					fleetmp[c][Grid.side(c, i - y*11)] = 6 - y;
				}
			}

		// All pieces (except eights) flee the lane
		// if front opponent pieces are likely bombs.

            if (isBombedLane(lane)) {
                    for (int r = 1; r <= 10; r++) {
                            if (r == 8)
                                    continue;
                            genPlanA(fleetmp[Settings.topColor], Settings.topColor, r, DEST_PRIORITY_LANE);
                            genPlanB(fleetmp[Settings.topColor], Settings.topColor, r, DEST_PRIORITY_LANE);
                    }
                    continue;
                }

		// If there is no known or suspected opponent rank in the lane,
		// guard the lane with a rank equal to or lower than
		// the lowest unknown opponent expendable rank.
		// Thus, if an unknown opponent rank should approach,
		// an attack will be zero or positive for the AI.
		
		// If there is a known or suspected opponent rank in the lane,
		// guard the lane with a lower rank.
		// (An equal rank could be insufficient because the AI
		// often misguesses suspected ranks and because known
		// ranks are often more aggressive)

			int[] lowRank = new int[2];
			lowRank[Settings.bottomColor] = lowestUnknownExpendableRank + 1;
			lowRank[Settings.topColor] = 99;
			Piece aiPiece = null;


			for (int i : attacklanes[lane])
			for (int y = 0; y < 5; y++) {
                int j = i - y * 11;
				Piece p = getPiece(j);
				if (p == null)
					continue;
                Rank rank = p.getRank();
				if (rank.ordinal() < lowRank[p.getColor()])
					lowRank[p.getColor()] = rank.ordinal();

		// Try to keep more than one non-invincible piece of the
		// same rank out of the lanes.
		// This reduces the risk of forks.
		// It should also help to spread the defenders into
		// other lanes.

		// The goal of fleeing in attacklanes()
		// is to keep the higher ranked piece out of the lane.
		// Until version 10.0, fleeing was awarded to both
		// the AI and the opponent.  But the proximity pruning
		// mechanism then causes chases to be thwarted in the lane.
		// For example:
		// -- -- --
		// R2 -- --
		// -- -- xx
		// -- B3 xx
		// -- -- --
		// If R2 moves down, B3 may be close enough to R2 to allow
		// B3 to be considered, and if fleeing is awarded more than
		// chasing, R2 will not chase.  Even worse, R2 may move
		// away, because perhaps B3 would no longer be close enough for the
		// higher value B3 fleeing move to be considered.
		//
		// TBD: this effect likely occurs with other priority differences
		// as well.


				if (p.getColor() == Settings.bottomColor)
					continue;

				if (!isInvincible(p)
					&& getPlanA(p) != null)
					genPlanB(fleetmp[p.getColor()], p.getColor(), p.getRank().ordinal(), DEST_PRIORITY_LANE);
				else if (!isInvincible(p)
					&& getPlanB(p) != null)
					genPlanA(fleetmp[p.getColor()], p.getColor(), p.getRank().ordinal(), DEST_PRIORITY_LANE);
				else if (aiPiece == null
					|| rank.ordinal() < aiPiece.getRank().ordinal())
					aiPiece = p;

		// Actively chased higher ranked pieces flee the lane rearward
		// (unless there is only one lower opponent rank).
        // to discourage the piece from moving
        // forward in the lane, thus allowing two opponent pieces
        // to easily trap it.

        // (Until Version 12, the AI piece preemptively evacuated.
        // This was a waste of moves and more importantly
        // removed a deterrent to higher ranked opponent pieces.)

        // TBD: this, like the rest of attackLanes, needs to be
        // replaced by localized minimax, which can predict what
            // could happen in the lanes.

                    if (y < 3
                            && isChased(j)
                            && lowerRankCount[Settings.bottomColor][rank.ordinal()-1] > 1) {
                    genPlanA(fleetmp[Settings.topColor], Settings.topColor, rank.ordinal(), DEST_PRIORITY_LANE);
                    genPlanB(fleetmp[Settings.topColor], Settings.topColor, rank.ordinal(), DEST_PRIORITY_LANE);
				}

			} // y

		// Stealthy pieces preemptively evacuate the lane because
        // of the ease of cornering by an opponent expendable piece,
        // should one decide to enter the lane.

        // This is a sissy heuristic; deeper minimax can predict
        // whether a piece can becomes cornered, so this needs to
        // be handled by deep search.

        // As of Version 12, fleeing the lane is now a default plan.
        // If the piece does not have a plan, then it should
        // not wander into the lanes, clogging them up, and
        // potentially losing stealth.  A piece only enters
        // the lane if it has a plan.  This is primarily directed
        // at unknown Miners, which are not called into duty
        // until a path to the flag has been identified.

			if (aiPiece != null
				&& isStealthy(aiPiece, lowRank[Settings.bottomColor])) {
				int planA[][] = getPlanA(aiPiece);
				int planB[][] = getPlanB(aiPiece);
                                if (planA != null && planA[0][0] == 0)
					genPlanA(fleetmp[aiPiece.getColor()], aiPiece.getColor(), aiPiece.getRank().ordinal(), DEST_PRIORITY_LANE);
                                if (planB != null && planB[0][0] == 0)
					genPlanB(fleetmp[aiPiece.getColor()], aiPiece.getColor(), aiPiece.getRank().ordinal(), DEST_PRIORITY_LANE);
			}

		// It is tempting to make guarding the lanes high priority,
		// because the AI often cannot see an attack on its weak flanks
		// until too late.  But chasing is just as important,
		// especially if an opponent piece has made it past the lanes.
		// Guarding the lanes does no good in this case.
		// Worse, the AI pieces would be glued to the lanes allowing
		// the errant opponent piece to wreak havoc uncontested.

			for (int c = RED; c <= BLUE; c++) {

		// Once the player has three or less remaining pieces
		// of Rank Eight or less, guarding the lanes becomes pointless,
		// and other goals take precedence, like attacking or
		// directly guarding the flag.
		// TBD: it is also pointless if the opponent has superior
		// ranks.

			if (lowerRankCount[c][8] <= 3
				|| (c == Settings.topColor
					&& dangerousKnownRank != 99))
				continue;

			int oppRank = lowRank[1-c];
			int thisRank = lowRank[c];
			if (oppRank < thisRank)
				for (int i : attacklanes[lane]) {
					int ranksNeeded = 1;
					for (int r = oppRank; ranksNeeded <= 3 && r > 1; r--) {
						if (rankAtLarge(c, r) != 0) {
							int tmp2[] = genDestTmpGuarded(1-c, i - 11, Rank.toRank(r));
							if (!isStealthy(planAPiece[c][r-1], oppRank))
								genPlanA(false, tmp2, c, r, DEST_PRIORITY_CHASE);
							if (!isStealthy(planBPiece[c][r-1], oppRank))
								genPlanB(tmp2, c, r, DEST_PRIORITY_CHASE);
							ranksNeeded++;
						}
					}
				}

			} // c

		// Spy flees the lane if the opponent one is not known

			if (knownRankAtLarge(Settings.bottomColor, Rank.ONE) == 0
				&& rankAtLarge(Settings.bottomColor, Rank.ONE) != 0) {
				genPlanA(fleetmp[Settings.topColor], Settings.topColor, 10, DEST_PRIORITY_LANE);
				genPlanB(fleetmp[Settings.topColor], Settings.topColor, 10, DEST_PRIORITY_LANE);
			}

		} // lane
	}


        // Is the piece buried by unmoved pieces?
        boolean isBuried(Piece p)
        {
            int i = p.getIndex();
            for (int d : dir) {
                int n = i + d;
                if (!Grid.isValid(n))
                        continue;
                Piece np = getPiece(n);
                if (np == null
                    || np.hasMoved())
                    return false;
            }
            return true;
        }

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
	void setUnmovedValues()
	{
        int atRisk=0;
		for (int i=12;i<=120;i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;

            if (p.hasMoved()
				|| p.isKnown()) {
                if (p.getRank() != Rank.BOMB
                    && p.getRank() != Rank.FLAG
                    && p.getRank().ordinal() > dangerousKnownRank
                    && p.getColor() != Settings.topColor)
                    atRisk++;
                continue;
            }

            if (p.getColor() != Settings.topColor) {
                unmovedValue[i] += Math.min(
                    2*valueStealth[Settings.bottomColor][Rank.BOMB.ordinal()-1],
                    stealthValue(Settings.bottomColor, unknownRank[Settings.bottomColor]));
                if (isForaySquare(Settings.topColor, i))
                    unmovedValue[i] = Math.max(unmovedValue[i], pieceValue(Settings.topColor, Rank.NINE));
                continue;
            }

		// genDestFlag() tries to keep structures intact by
		// setting unmovedValue[].  Yet if the piece is a non-expendable
		// piece and it is needed, break up structure.

			if (isNeededRank(p)
				&& !isExpendable(p))
				unmovedValue[i]=0;

			else if (!isNeededRank(p)) {

		// Moving an unmoved piece needlessly is bad play
		// because these pieces can become targets for
		// invincible opponent pieces.  The greater the piece
		// value, the greater the risk.  If the piece
		// is part of a structure that could be construed
		// as a bomb structure, it is best to leave the structure
		// intact to prevent the opponent from guessing the
		// real structure.

				int rank = p.getRank().ordinal();
				int color = p.getColor();
				unmovedValue[i] += VALUE_MOVED +values[color][rank]/(VALUE_MOVED * 10);
			}

            // Encourage the unmoved pieces in the foray lane to move
            // Especially if they are valuable pieces (e.g. Spy, One)
            // at the back that could become targets.  This also
            // helps clear the pieces out so that others outside
            // the foray area can more easily assist (less blockage).

            if (isForayLane(p.getColor(), i))
                unmovedValue[i]/=2;
		} // i


		// If the opponent has a known invincible rank,
		// it will be hellbent on obliterating all moved pieces,
		// so movement of additional pieces is heavily discouraged
                // because the pieces instantly become additional targets.
                // The risk of loss increases with the number of
		// opponent lower ranks and the number of weak moved AI pieces.
		//
		// TBD: However, this can lead to draws, so eventually the AI
		// must sparingly move additional pieces, such as Eights
		// to attack flag structures or mid ranked pieces needed
		// to attack other opponent pieces.  But only if
		// the AI has a fighting chance.

		for (int i=12;i<=120;i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null
				|| p.getColor() != Settings.topColor
                                || p.hasMoved())
				continue;
			if (p.getRank().ordinal() > dangerousKnownRank
				&& p.getRank() != Rank.BOMB
				&& p.getRank() != Rank.FLAG
				&& (lowerKnownOrSuspectedRankCount[1-p.getColor()][p.getRank().ordinal()-1] >= 2
                || atRisk >= 1))
				unmovedValue[i] += VALUE_MOVED*2;

		} // i
	}

	protected void chaseWithScout(Piece p, int i, int priority)
	{
		int tmp[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.NINE);
		genPlanA(tmp, 1-p.getColor(), 9, priority);
		genPlanB(tmp, 1-p.getColor(), 9, priority);
	}

	// Launch an attack on a bomb.
	//
	// The active miner heads directly for the flag,
	// regardless of any protection, so GUARDED_OPEN is used.
	// Another miner, if available, pursues the flag
	// through an unguarded path.
	// (At least one miner has to head directly for
	// the flag regardless of protection;
	// otherwise, the miner would simply retrace
	// its steps to an alternative path, the protection would move,
	// and the miner would retrace again).

	// Note: Before Version 11.1, planA was not activated
	// until there were few weak opponent ranks.
	// The thinking was to keep the valuable Miners unmoved
	// until they were less likely to encounter resistance.
	// Version 11.1 deviates from this conservative strategy, hoping to
	// make an early flank strike and attack the flag structure
	// while most of the opponent pieces are locked down on the
	// other side of the board.

    // Before version 9.5, an active piece was sent to protect
    // the Miner.  But this is redundant because opponent
    // pieces are already chased in chase(), so if an opponent
    // piece is guarding the flag, the AI will target it anyway.
    // Moreover, if the flag is guarded by a low ranked piece
    // (usually the case), sending a known piece of
    // higher rank is pointless and counterproductive.

    // Before version 11.1, an unknown expendable piece
    // was sent along as a subterfuge measure.
    // If the protectors of the bomb structure are outnumbered,
    // they often can be drawn towards the extra unknowns
    // allowing the eight to attack the bomb.

    // Yet this too is somewhat redundant because since version 11.1
    // a slight bias is awarded to all unknown pieces to approach
    // the bombed flag area.  Because the bias is not a plan,
    // it does not overload any plans that the unknown piece might have.

    // However, because the Miners chase with high priority,
    // they often arrive first, and then crash into other
    // opponent pieces guarding the flag.   The high priority
    // dash towards the flag can make the Miners pretty obvious.

    // So experimentally, Version 12 reverts to sending one
    // Miner and one Unknown. The Unknown  is usually expendable, but could
    // be any unknown piece, including a Miner.  The Unknown is
    // sent along the open path and the Miner along the guarded path.

	protected void attackBomb(Piece bomb)
	{
        int bombColor = bomb.getColor();
        int j = bomb.getIndex();
        int destTmp[] = genDestTmp(GUARDED_OPEN, bombColor, j);

        // Find the weakest guard near the flag
        // and chase it off with an Unknown.
        // If there is no guard, then just send the one Miner on a mad dash
        // towards the flag.

        Piece tp = null;
        for (int i=12;i<=120;i++) {
            if (Grid.steps(i, j) > 3)
                continue;
            Piece p = getPiece(i);
            if (p == null
                || p.getColor() != bombColor
                || !p.hasMoved())
                continue;
            if (tp == null
                || p.getRank().ordinal() > tp.getRank().ordinal())
                tp = p;
        }

        if (tp != null) {
            if (isInvincibleDefender(tp))
                tp = bomb;
            chaseWithUnknown(planAPiece, tp, destTmp, DEST_PRIORITY_ATTACK_FLAG);
            chaseWithUnknown(planBPiece, tp, destTmp, DEST_PRIORITY_ATTACK_FLAG);
        }

        int destTmp2[] = genDestTmpGuardedOpen(bombColor, j, Rank.EIGHT);
        genNeededPlanA(false, destTmp2, 1-bombColor, 8, DEST_PRIORITY_ATTACK_FLAG);
        genPlanB(false, destTmp2, 1-bombColor, 8, DEST_PRIORITY_ATTACK_FLAG);
	}

	protected void chaseWithExpendable(Piece p, int i, int priority)
	{
        if (!grid.hasMove(p))
            return;
		for ( int r = 1; r <= 10; r++)
			if (isExpendable(1-p.getColor(), r)) {
				int tmp[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(r));
				genNeededPlanA(false, tmp, 1-p.getColor(), r, priority);
				genNeededPlanB(tmp, 1-p.getColor(), r, priority);
			}
	}

	protected void chaseWithUnknown(Piece[][] plan, Piece p, int tmp[], int priority)
	{
		assert !isInvincibleDefender(p) : "Do not chase an invincible rank " + p.getRank() + " with unknowns!";

		boolean found = false;
		int valuableRank = 0;
		int valuableRankValue = 0;
		for ( int r = 1; r <= 10; r++) {
                        if (unknownRankAtLarge(1-p.getColor(), r) == 0)
                            continue;
			Piece a = plan[1-p.getColor()][r-1];
			if (a != null) {
				if (a.isKnown())
					continue;
				

		// If the expendable has fled from this rank before,
		// it is no longer a convincing bluffer.
		// Note that it could have fled from a higher ranked
		// piece, and still be a convincing bluffer, because
		// it may have fled to avoid detection (or capture,
		// in the case of a Spy).

				if (a.isFleeing(p.getRank()))
					continue;
			}

			if (isExpendable(1-p.getColor(), r)) {
				if (plan == planAPiece)
					genNeededPlanA(rnd.nextInt(2) != 0, tmp, 1-p.getColor(), r, priority);
				else
					genNeededPlanB(tmp, 1-p.getColor(), r, priority);
				found = true;
			} else if (valuableRank == 0 || valuableRankValue > pieceValue(1-p.getColor(), r)) {
				valuableRank = r;
				valuableRankValue = pieceValue(1-p.getColor(), r);
			}
		}
				

		// If unknown expendables are exhausted, bluff with the least valuable piece

		if (!found && valuableRank != 0) {
			if (plan == planAPiece)
				genPlanA(rnd.nextInt(2) != 0, tmp, 1-p.getColor(), valuableRank, priority);
			else
				genPlanB(rnd.nextInt(2) != 0, tmp, 1-p.getColor(), valuableRank, priority);
		}
	}

	public int[][] getPlanA(Piece p)
	{
		int c = p.getColor();
		int r = p.getRank().ordinal() -1;
		if (planAPiece[c][r] == p)
			return planA[c][r];
		return null;
	}

	public int[][] getPlanB(Piece p)
	{
		int c = p.getColor();
		int r = p.getRank().ordinal() -1;
		if (planBPiece[c][r] == p)
			return planB[c][r];
		return null;
	}

    int lowRankChasePriority(Piece p)
    {
        int priority = DEST_PRIORITY_CHASE;
            if (grid.hasAttack(p))
                priority = DEST_PRIORITY_CHASE_DEFEND;

            // If the low piece is defending its flag, the AI
            // has to be more aggressive in chasing it away

            else if (isNearOpponentFlag(p))
                priority = DEST_PRIORITY_CHASE_ATTACK;

        else if ((p.getRank() == Rank.TWO
                || p.getRank() == Rank.THREE)
                && hasUnsuspectedSpy(p.getColor())
                && hasUnsuspectedOne(p.getColor()))
                priority = DEST_PRIORITY_CHASE_ATTACK;
            return priority;
    }

	protected void chaseWithUnknown(Piece p, int tmp[])
	{
		// Chase the opponent Two and Three
		// at high priority if the opponent has not yet divulged
		// the location of its Spy or One.  This may also
		// convince the opponent that the unknown chaser
		// is actually a lower rank, making the opponent think
		// that his rank is locally invincible,
		// then wham! the player's lowest rank can make the capture.

		int priority = lowRankChasePriority(p);
		chaseWithUnknown(planAPiece, p, tmp, priority);
		chaseWithUnknown(planBPiece, p, tmp, priority);
	}

	// Chase the piece "p"
	protected void chase(Piece p)
	{
		int i = p.getIndex();
		int chasedRank = p.getRank().ordinal();
		int[][] destTmp = new int[2][];	// encourage forward motion
		for (int guarded = GUARDED_OPEN ; guarded <= GUARDED_UNKNOWN; guarded++) {
			destTmp[guarded] = genDestTmp(guarded, p.getColor(), i);
			// this nulls out incentive for chase sequences
			destTmp[guarded][i] = DEST_VALUE_NIL;
		}

		if (p.isKnown() || p.isSuspectedRank()) {
assert p.getRank() != Rank.UNKNOWN : "Unknown cannot be known or suspected " + p.isKnown() + " " + p.isSuspectedRank();

		// no point in chasing a Nine because
		// it can easily get away

			if (p.getRank() == Rank.NINE)
				return;

		// Only the closest non-invincible known rank is assigned
		// to chase an opponent piece.
		// This prevents a pile-up of ai pieces
		// chasing one opponent piece that is protected
		// by an unknown and possibly lower piece.

			int found = 0;
			int minsteps = 99;
			for (int j = chasedRank - 1; j > 0; j--)
				if (isInvincible(1-p.getColor(),j))
					continue;
				else if (knownRankAtLarge(1-p.getColor(),j) != 0
					&& Grid.steps(planAPiece[1-p.getColor()][j-1].getIndex(), i) < minsteps) {
					minsteps = Grid.steps(planAPiece[1-p.getColor()][j-1].getIndex(), i);
					found = j;
				}

		// Generate planA *and* planB.
		// If there are multiple moved pieces of the rank,
		// they will all chase the piece.

			if (found != 0) {

		// Most pieces chase at low priority, except if the
        // target is in the foray lane.
		//
		// Three chasing a Four, when the opponent Two
		// is known or suspected.  This makes the Three invincible
		// IF it wins the Four, i.e. 3x4 followed by
		// ... 1?x3 is positive, because the value of the Four
		// plus the value of the One stealth
		// is greater than the value of the Three.  (This is similar
		// to where the opponent Two and Threes are known or suspected,
		// making the player Four invincible, because 1?x4 is positive).

				int priority = DEST_PRIORITY_CHASE;
				if ((found == 3
					&& unknownNotSuspectedRankAtLarge(p.getColor(), Rank.TWO) == 0)
                    || isForayLane(1-p.getColor(), p.getIndex()))
					priority = DEST_PRIORITY_CHASE_ATTACK;
				
				int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(found));
				genNeededPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), found, priority);
                genNeededPlanB(destTmp2, 1-p.getColor(), found, priority);
            }

        // Chase a known One with a Spy.
        // The chase is limited to the AI side of the board
        // until there are no opponent expendable pieces remaining
        // as a gross effort to limit the Spy's vulernability.
        //
        // Note the use of GUARDED_CAUTIOUS.  GUARDED_CAUTIOUS is used
        // for Eights and invincible pieces, because unmoved enemy ranks
        // are no detriment to the goal.  Here, GUARDED_CAUTIOUS
        // is not quite right, because unmoved ranks do pose
        // a threat to the Spy.  But this is better than
        // any of the other options.
        //
        // Once the opponent One is known and approaches
        // the AI side of the board, the Spy must be called up,
        // because the One is a dangerous piece, and could obliterate
        // the AIs moved pieces.  Because of the low probability
        // of encountering a Spy amongst many pieces, opponents
        // often play the odds of capturing just enough pieces to get
        // ahead and then play conservatively to a winning finish.

            else if (chasedRank == 1
                && p.isKnown()
                && hasSpy(1-p.getColor())
                && (p.getColor() == Settings.topColor
                        || hasFewWeakRanks(p.getColor(), 4)
                        || p.getIndex() <= 65)) {
                int destTmp2[] = genDestTmpGuarded(p.getColor(), i, Rank.SPY);
                genNeededPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), 10, DEST_PRIORITY_CHASE);
                chaseWithUnknown(p, destTmp[GUARDED_UNKNOWN]);
            }

		// Chase an opponent piece with an unknown piece.
		//
		// A non-active rank is occasionally summoned
		// to avoid a draw when the lack of known lower ranked
		// pieces causes both sides to stand pat.
		// This also helps to make the AI somewhat less predictable.
		//
		// The unknown chaser should not always directly chase the
		// opponent piece because this divulges its rank.  So
		// expendable pieces are sent instead, while keeping the
		// chaser near the chase.  In a situation where the
		// opponent is forced to make a decision between standing
		// pat when approached or moving next to an unknown piece,
		// the opponent almost always chooses to move.  At least
		// this is true for opponent bots.
		//
		// Yet the chaser must also directly chase at least some
		// of the time, because otherwise the opponent will
		// learn the AI bluffing strategy.
		//
		// Version 10.1 chases with both the same rank and a
		// lower rank.  This confuses the opponent about which
		// piece is the lower ranked piece.  Additional chasers
		// can cause the opponent piece to seek protection,
		// and the discovery can lead to further gain.

		//
		// Note the use of GUARDED_OPEN.  See note below.
		//
			else {
			for (int j = chasedRank; j > 0; j--)
				if (isInvincible(1-p.getColor(), j))
					continue;
				else if (rankAtLarge(1-p.getColor(),j) != 0) {
					Piece chaser = planAPiece[1-p.getColor()][j-1];
					if (chaser != null
                                            && (p.getMoves() > 15
						|| rnd.nextInt(15) == 0)) {
                                                genNeededPlanA(rnd.nextInt(2) != 0, destTmp[GUARDED_OPEN], 1-p.getColor(), j, DEST_PRIORITY_CHASE);
						if (j != chasedRank)
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

				if (chasedRank <= 4 && !isInvincible(p))
					chaseWithUnknown(p, destTmp[GUARDED_UNKNOWN]);

			} // found = 0

		// Chase suspected low ranked pieces with known
		// expendables to confirm their identity

			if (p.isSuspectedRank()
				&& (chasedRank <= 3
					|| chasedRank == Rank.SPY.ordinal())) {
				chaseWithExpendable(p, i, DEST_PRIORITY_CHASE);
				chaseWithScout(p, i, DEST_PRIORITY_CHASE);
			}

		// Chase the piece with the same rank IF an EVEN exchange
		// is positive.  An EVEN exchange is positive based
		// on values of the pieces (see EVEN).

		Piece opp = planAPiece[1-p.getColor()][chasedRank];
		if (opp != null
			&& pieceValue(p) > pieceValue(opp)) {

			// go for an even exchange
			int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, p.getRank());
			genNeededPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), chasedRank, DEST_PRIORITY_CHASE);
		}

        } else { // not known or suspected

                if (p.hasMoved()) {

		// chase unknown fleeing pieces as well

                    if (p.getActingRankFleeLow() != Rank.NIL
                        && p.getActingRankFleeLow().ordinal() <= 4) {
                        for (int j = p.getActingRankFleeLow().ordinal(); j > 0; j--)
                            if (!isInvincible(1-p.getColor(),j)) {
                                int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(j));
                                genPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), j, DEST_PRIORITY_CHASE);
                            }

                    } else if (p.getActingRankFleeLow() == Rank.UNKNOWN) {
                        for ( int j = 2; j < 10; j++)
                            if (isExpendable(1-p.getColor(), j)) {
                                int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(j));
                                genPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), j, DEST_PRIORITY_CHASE);
                                genPlanB(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), j, DEST_PRIORITY_CHASE);
                            }

                    } else if (p.getActingRankFleeLow() != Rank.NIL
                        && p.getActingRankFleeLow().ordinal() >= 5) {
                            int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, p.getActingRankFleeLow());
                            genPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), p.getActingRankFleeLow().ordinal(), DEST_PRIORITY_CHASE);
                            int destTmp3[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(p.getActingRankFleeLow().ordinal()+1));
                            genPlanA(rnd.nextInt(2) != 0, destTmp3, 1-p.getColor(), p.getActingRankFleeLow().ordinal()+1, DEST_PRIORITY_CHASE);
                    }

        // Also use Fours and Fives to chase unknown pieces.
		// This is done as bait to find Threes and maybe a Two.
		// And their stealth is about the same as the value of
		// an expendable, so if they are discovered, its a wash.

                    for ( int j = 5; j >= 4; j--) {
                        if (isInvincible(1-p.getColor(), j))
                            continue;
                        int destTmp2[] = genDestTmpGuardedOpen(p.getColor(), i, Rank.toRank(j));
                        genPlanA(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), j, DEST_PRIORITY_CHASE);
                        genPlanB(rnd.nextInt(2) != 0, destTmp2, 1-p.getColor(), j, DEST_PRIORITY_CHASE);
                    }
                } // has moved

		// Before version 10.3, the AI chased
		// completely unknown pieces with known AI pieces
		// and expendable unknown piece.

		// This was a baiting plan.  The idea was if the AI could
		// entice the unknown piece to chase back, then
		// it would get a chase rank.  This is how the AI tries
        // to assess the rank of unknown piece without actually
        // attacking it.

		// Yet if the piece never chased back, it tied up the AI
		// piece from other more pressing duties,
        // such as chasing higher
		// ranked pieces or protecting an open lane.   Furthermore,
		// attackLanes() pushes pieces down the lane to bait the
		// opponent into attacking as well.

		// Therefore, chasing unknown pieces is now limited
        // to expendables chasing non-weak pieces and pieces
        // in the foray lane.
		
            if (!p.isWeak()
                || isForaySquare(1-p.getColor(), p.getIndex()))
                    chaseWithExpendable(p, i, DEST_PRIORITY_LOW);

            if (!p.hasMoved())
                return;	// do not comment this out!
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

		for (int j = 1; j <= chasedRank; j++) {

			if (!isInvincible(1-p.getColor(), j))
				continue;

			if (rankAtLarge(1-p.getColor(),j) == 0)
				continue;

		// If the opponent has an invincible piece,
		// the AI must chase it to attempt an even exchange.
		// While this just leads to another opponent rank becoming
		// invincible, it is the only way to end the game,
		// because otherwise the invincible piece just runs amok.
		// Eventually, if the AI is indeed winning, the
		// opponent will run out of invincible pieces.
		// (See EVEN).

		// Only go for an even exchange of invincible pieces
		// if the player is winning.

			if (j == chasedRank
				&& (isWinning(1-p.getColor()) < 0
					|| knownRankAtLarge(1-p.getColor(),j) == 0
					|| chasedRank == 1 && hasSpy(1-p.getColor())))
				continue;

                // Prior to Version 12, the AI waited for a unguarded open
                // path to send the piece, and now it looks for any
                // unguarded path, so that the AI invincible piece will move
                // towards its goal, even if blocked by its own pieces.
                // This seems to get the AIs invincible pieces quicker
                // to the rescue or attack, although it can result in
                // stacking problems when the AI has multiple invincible
                // pieces.

			int destTmp2[] = genDestTmpGuarded(p.getColor(), i, Rank.toRank(j));

			if (knownRankAtLarge(1-p.getColor(),j) != 0
				|| stealthValue(1-p.getColor(), j) <= values[p.getColor()][unknownRank[p.getColor()]]) {

		// Prior to version 9.6 it was thought that
		// invincible pieces cannot always chase at high priority,
		// otherwise the AI will use these pieces in an
		// ad infinitum chase.  So half of the time it chased
		// at lower priority.
		//
		// The sole exception was when the *chased* rank is invincible,
		// because until the opponent invincible rank is captured or
		// cornered, it will just likely
		// chase the players pieces around the board ad inifinitim
		// until they are captured.  So the AI must continually chase
		// the invincible rank with invincible pieces until it is
		// cornered in order for the game to continue.
		//
		// If a lone chased piece is always chased at high priority,
		// fleeing creates a pointless chase.
		// However, if the chased piece is nearby another possible
		// target, then perhaps both pieces might not be
		// able to flee at once, so it is suitable to chase
		// the clump at high priority.  And it is
		// important to try to chase a clump at high priority because
		// if the AI plays some other moves, it gives the opponent
		// time to react, such as bringing in a defensive
		// invincible piece.
		//
		// This reliance on the oracle is simply a guess and may or
		// may not lead to material gain, because the targets
		// could flee before the chaser arrives or a superior
		// protector could come into play.  The important
		// condition is to stop chasing at high priority
		// once the clump dissolves in order to avoid continuous
		// chase sequences.
		//
		// The goal is to corner the chased piece
		// rather than approach it and push it
		// around randomly.  This is especially true if the
		// chased piece is invincible.  The idea is to bring another
		// chase piece towards the chased piece and get the search
		// tree to discover the proper moves to capture it.
		// This also avoids mindless chases around the board.
		// (Mindless chases do often result in material gain,
		// but the goal of this programmer is to avoid them).
		//
		// The One chases at three different priorities.  If the
		// opponent Spy is gone, it can chase at the highest priority,
		// just like other invincible pieces.  But if the opponent
		// Spy is still lurking, it chases opponent invincible
		// pieces as a lower priority as a defense measure.  Other
		// opponent pieces are chased at low priority, because
		// the One probably will not be able to capture the piece
		// if it is protected by an unknown.
		//
		// The chase is also generated for opponent invincible
		// pieces, because blocking these chases by the AI
		// is an important goal.

			if (isInvincibleDefender(1-p.getColor(), j)
				|| p.isKnown()
				|| (p.getColor() == Settings.bottomColor
					&& !isPossibleUnknownSpyXOne(planAPiece[Settings.topColor][j-1], p))) {
				int priority = DEST_PRIORITY_CHASE;
				boolean neededNear = false;
				if (isInvincible(p)
					|| rnd.nextInt(2) == 0) {
					priority = DEST_PRIORITY_CHASE_DEFEND;
					neededNear = true;
				}
				else if (grid.movablePieceCount(p.getColor(), i, 1) >= 2)
					priority = DEST_PRIORITY_CHASE_ATTACK;

				genPlanA(neededNear, destTmp2, 1-p.getColor(), j, priority);
				genPlanB(neededNear, destTmp2, 1-p.getColor(), j, priority);
			}

		// The AI could be cautious in sending its unknown low
        // ranked pieces to chase an opponent pieces
        // because of the risk of discovery of its piece
        // and loss of stealth.

        // But this resulted in slow and dull games,
        // so Version 12 chases with its unknown low pieces
        // and expendables equally, hoping to corner the opponent
        // piece where the AI would have at least a 50/50
        // chance by approaching it with either the low ranked
        // piece or an expendable piece (as a bluff, which
        // often works better).

		// The unknown low ranked piece must also be careful
		// to rarely approach the chased piece,
		// or opponent will guess its rank.  So the piece
		// must mostly keep one square away to leave the opponent
		// guessing.

			} else if (j <= invincibleWinRank[1-p.getColor()]
				|| (lowerKnownOrSuspectedRankCount[p.getColor()][j-1] < 2
					&& stealthValue(1-p.getColor(), j) <= values[p.getColor()][chasedRank])
                                        && p.isKnown()) {
            
				int priority = lowRankChasePriority(p);
                                genNeededPlanA(true, destTmp2, 1-p.getColor(), j, priority);

				// tbd: PlanB as well
				if (!isInvincible(p))
					chaseWithUnknown(p, destTmp[GUARDED_UNKNOWN]);

			}
		} // for

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

	// First defense is to control the lanes to prevent
	// penetration by a miner (see attackLanes()).  Defense in
	// this manner does not disclose the location of the flag.
	//
	// Secondly, the AI does open path analysis and tries to keeps
	// a defender of appropriate rank one step closer to the
	// square adjacent to the flag on the opponent approach path.
	// Yet without search tree analysis, protection is impossible
	// to guarantee.
	//
	// (Note: it may well be possible to use a limited search tree
	// to accomplish a better result, albeit with much more
	// computation.)
	//

	private void defendFlag(int flagi)
	{
		Piece flagp = getPiece(flagi);
		int color = flagp.getColor();

		if (color == Settings.topColor
			&& flagi != flag[color])
			return;

		// Even when the flag is not completely bombed,
		// the approach could be guarded by an invincible rank
		// thwarting access.  So Eights are still needed
		// to clear flag structure bombs.
		for (int d : dir) {
			int bi = flagi + d;
			Piece bp = getPiece(bi);
			if (bp != null && bp.getRank() == Rank.BOMB)
				attackBomb(bp);
		}

		// destTmp[] is created with GUARDED_OPEN.
		// This means that the flag defender will
		// protect its flag only if there is an open unblocked
		// path.
		//
		// destTmpAttacker[] is created with GUARDED_CAUTIOUS.
		// This means that the attacker sees
		// the flag through the defender's moved pieces.
		// This is because an attacker may be superior to
		// those moved pieces and perhaps they pose no obstacle.
		// TBD: No attempt is made to determine if this is
		// the case.  Unless, of course, they have an open
		// path and therefore are a flag defender.

		for (int d: dir) {
			int side = flagi + d;
			if (!Grid.isValid(side))
				continue;
			Piece pside = getPiece(side);

			int destTmp[] = genDestTmp(GUARDED_OPEN, color, side);
			int destTmpAttacker[] = genDestTmpGuarded(color, side, Rank.NIL);
			int stepsAttacker = 99;
			Piece pAttacker = null;
			for (int i = 12; i < 120; i++) {
				Piece p = getPiece(i);
				if (p == null || p.getColor() != 1 - color)
					continue;
				if (destTmpAttacker[i] < stepsAttacker) {
					stepsAttacker = destTmp[i];
					pAttacker = p;
				}
			}
			if (pAttacker == null)
				continue; // no open path

		// If the flag is partially bombed, the bomb
		// must be protected from an unknown or Eight

			Rank rankAttacker = pAttacker.getRank();
			int attackerValue = pieceValue(pAttacker);

			if (pside != null
				&& pside.getRank() == Rank.BOMB) {
				if (!(rankAttacker == Rank.UNKNOWN
					|| rankAttacker == Rank.EIGHT))
					continue;

		// If the approaching piece is unknown, assume that it
		// could be a low ranked piece on a kamikaze attack to discover the flag,
		// so the AI needs an equal or even lower piece.

			} else if (rankAttacker == Rank.UNKNOWN) {
				rankAttacker = Rank.toRank(lowestUnknownNotSuspectedRank);
				attackerValue = pieceValue(1-color, rankAttacker);
			}

			Piece defender = getDefender(color, destTmp, rankAttacker, attackerValue, stepsAttacker);
			if (defender == null)
				continue;	// no imminent danger

		// Even if the unbombed flag is not known, call up
		// the closest defender, even if it means moving
		// another unmoved piece.
		// The opponent (especially bots) may be
		// throwing pieces at any unmoved piece hoping that
		// it may be the flag and the active piece too far
		// away.
		// TBD: If the active piece is able to defend, then
		// use it.  Fix this in getDefender().

			setDefender(defender, destTmp, color == Settings.topColor ? DEST_PRIORITY_DEFEND_FLAG : DEST_PRIORITY_DEFEND_FLAG_AREA);
		} // dir
	}

	// Determine if piece at "index" is at risk of attack
	// and if so, what rank should be moved to protect it?

	// Of course, this algorithm isn't perfect anyway.  If
	// the attacker has an open path, but the player does not,
	// the AI fails to defend.  This code cannot take the place
	// of the search tree which discovers how to move the players
	// pieces optimally.  This code is a pale effort to improve defensive
	// measures past the search horizon.

	// If stepsTarget != 0, find the closest piece capable
	// of defending.

	// If stepsTarget == 0, just find the highest ranked piece capable
	// of defending.

	private Piece getDefender(int color, int destTmp[], Rank attacker, int attackerValue, int stepsTarget)
	{
		int attackerRank = attacker.ordinal();

		int stepsDefender = 99;
		Piece pDefender = null;
		int stepsAttacker = 99;

		for (int i = 12; i < 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;

			if (p.getColor() == 1 - color) {
				if (destTmp[i] < stepsAttacker) {
					stepsAttacker = destTmp[i];
				}
				continue;
			}

		// The Spy is the better piece to protect the flag area
		// against a One.  (A One can be used, but after 1x1,
		// the flag area is unprotected).

			if (attacker == Rank.ONE
				&& hasSpy(color)) {
				if (p.getRank() == Rank.SPY) {
					stepsDefender = destTmp[i];
					pDefender = p;
				}
				continue;
			}

			if (p.getRank().ordinal() > attackerRank
				|| (!p.isKnown()
					&& stealthValue(p) > attackerValue))
				continue;

			if (stepsTarget != 0 && destTmp[i] < stepsDefender
				|| (stepsTarget == 0
					&& destTmp[i] != DEST_VALUE_NIL
					&& (pDefender == null
					|| p.getRank().ordinal() > pDefender.getRank().ordinal()))) {
				stepsDefender = destTmp[i];
				pDefender = p;
			}
		}

		// Prior to version 9.4, null was returned only when:
		// stepsDefender < stepsTarget // no imminent danger
		// So even if the defender had no chance of beating
		// the attacker (stepsDefender > stepsTarget), it
		// was rewarded if it approached its flag.
		// It was argued that because the attacker does not
		// really know where the flag is, this might get the
		// defender close enough to defend before the attacker
		// decided to attack.
		//
		// But this backfires.  Consider the example:
		// B2 R6 -- -- -- -- -- -- |
		// -- -- -- -- BB BF BB B8 |
		//--------------------------
		// The AI is Red and has the move.  But for every move
		// it makes to approach the flag, Blue Two follows.
		// Every step that Blue takes towards its flag is rewarded.
		// But if Red moved away from Blue Flag, Blue would not
		// be rewarded because its flag is no longer in danger.
		// 
		// When the AI was protecting its own flag, it would
		// see a small reward of getting ahead of the attacker,
		// but a larger extended reward if it allowed the attacker
		// to go past and then chase it.
		//
		// This is exactly opposite the desired result.
		// So in Version 9.4, the reward was increased and
		// limited to only when absolutely necessary:
		// (stepsTarget != 0 && stepsDefender != stepsTarget - 1)) // no imminent danger
		//
		// Yet this introduced another bug because the
		// defender does not realize that it is the piece
		// defending the flag and could wander off.
		// So in version 9.5, the pre-9.4 comparison was
		// restored, but cropped the reward to only
		// the adjacent squares.
		//
		// TBD: when the flag is no danger, and then the
		// defender wanders away, it can cause loss of the
		// game, or back-and-forth moves.  For example,
		// -- -- RF
		// -- -- --
		// -- -- --
		// -- -- --
		// -- -- xx
		// -- R7 xx
		// B? -- --
		// The flag is in no danger, so R7 moves down and
		// unknown Blue moves up.  Now the flag is in danger,
		// so R7 moves up, and unknown Blue moves down.

		if (pDefender == null  	// attacker not stopable
			|| (stepsTarget != 0 && stepsDefender < stepsTarget)
			|| (stepsTarget == 0 && stepsDefender < stepsAttacker)) // no imminent danger
			return null;

		return pDefender;
	}

	void setDefender(Piece defender, int tmp[], int priority)
	{
		int r = defender.getRank().ordinal();
		int color = defender.getColor();
		planAPiece[color][r-1] = defender;
		genNeededPlanA(false, tmp, color, r, priority);
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
		assert color == Settings.topColor : "flagBombTarget only for AI";

		// Determine if any bomb is subject to attack
		// and take defensive measures.
		// TBD: if there are two unknown attackers,
		// but if one has a suspected rank, focus on the other one.

		for (int d : dir) {

			int bi = flagi + d;
			if (!Grid.isValid(bi))
				continue;
			Piece bp = getPiece(bi);
			if (bp == null || bp.getRank() != Rank.BOMB)
				continue;

			//
			// check all possible approaches to bomb
			//

			for (int dbomb : dir ) {
				int bd = bi + dbomb;
				if (!Grid.isValid(bd))
					continue;
				if (getPiece(bd) != null)
					continue;

				int stepsAttacker = 99;
				int stepsProtector = 99;
				Piece pAttacker = null;
				Piece pProtector = null;
				int destTmp[] = genDestTmp(GUARDED_OPEN, color, bd);
				for (int i = 12; i < 120; i++) {
					Piece p = getPiece(i);

		// If the closest enemy piece has not moved,
		// it probably is a bomb.

					if (p == null
						|| p.getColor() != 1 - color
						|| !p.hasMoved())
						continue;
					if (p.getRank().ordinal() <= 8) {
						if (destTmp[i] < stepsProtector) {
							stepsProtector = destTmp[i];
							pProtector = p;
						}
						continue;
					} else if ((p.getRank() == Rank.UNKNOWN
						|| p.getRank() == Rank.EIGHT)
						&& destTmp[i] < stepsAttacker) {
						stepsAttacker = destTmp[i];
						pAttacker = p;
					}
				}
				if (pAttacker == null)
					continue; // no open path

		// Thwart the approach of the closest unknown or eight piece.
		// (Note: DEFEND_FLAG must be high enough so that if
		// the defender is an unmoved piece part of a possible
		// bomb structure, it will still be the best pruned move).

		// TBD: Note that if the attacker is unknown
		// the AI relies on a Eight or less to defend.
		// Obviously, that would be no deterrent
		// to a lower-ranked unknown attacker.

				Piece defender = getDefender(color, destTmp, Rank.EIGHT, pieceValue(1-color, Rank.EIGHT), stepsAttacker);
				if (defender != null) {
					setDefender(defender, destTmp, DEST_PRIORITY_DEFEND_FLAG);
				} else {
					// TBD: try looking for an Eight
				}

		// If the flag is known, try to keep the protector, if any,
		// away from the flag, because otherwise the protector
		// may attack the defender, giving the attacker a clear
		// shot at the flag.  (If the flag is not known, it is
		// best not to react to a known opponent piece in the
		// area, because overreaction is a dead giveaway).

				if (pProtector != null && flagp.isKnown()) {
					defender = getDefender(color, destTmp, pProtector.getRank(), pieceValue(pProtector), stepsProtector);
					if (defender != null) {
						int[] destTmp4 = genDestTmpGuarded(1-color, pProtector.getIndex(), pProtector.getRank());
						setDefender(defender, destTmp4, DEST_PRIORITY_DEFEND_FLAG_AREA);
					}
				}


			} // dbomb
		} // d
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
		int flagi = flag[Settings.topColor];
		Piece pflag = getPiece(flagi);
		int color = pflag.getColor();

		assert pflag.getRank() == Rank.FLAG : "aiFlag is " + pflag.getRank() + " at " + flagi +"?";
		assert color == Settings.topColor : "flag routines only for ai flag";
		// initially all bombs are worthless (0)
		// value remaining bombs around ai flag

		boolean bombed = true;
		for (int d : dir) {
			int j = flagi + d;
				
			if (!Grid.isValid(j)
				|| Grid.getY(j) > 3)
				continue;
			TestPiece p = (TestPiece)getPiece(j);
			if (p == null) {
				bombed = false;
				continue;
			}

			if (p.getRank() == Rank.BOMB) {
                p.setFlagBomb(true);
				p.targetValue = aiBombValue(p);
			} else
				bombed = false;
		}

		// Target the flag if not bombed,
		// otherwise target the bombs (if there still are eights)

		if (!bombed) {

		// opponent color eights are now expendable

			setExpendableEights(Settings.bottomColor);

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

			defendFlag(flag[Settings.topColor]);
		}

		// Always protect any remaining bombs in the
		// the flag bomb structure, even if the flag
		// is not known (i.e multiple structures remaining)
		// because the opponent can make a lucky guess,
		// especially if the opponent has expendable eights
		// that it can sacrifice by throwing them at multiple
		// structures (just like the AI does).

		if (rankAtLarge(1-color, Rank.EIGHT) != 0)
			flagBombTarget(pflag);
	}

	void genDestFlag()
	{
		for (int c = RED; c <= BLUE; c++) {

            int flagi = flag[c];
            if (flagi == 0)
                continue;
            Piece pflag = getPiece(flagi);

            int opponentEightsAtLarge = rankAtLarge(1-c, Rank.EIGHT);
            int bombedStructuresRemaining = maybe_count[c] - open_count[c];

            if (!isBombedFlag[c]
                || bombedStructuresRemaining == 0
                || bombedStructuresRemaining + 1 < opponentEightsAtLarge)

            // At least 1 opponent color Miner is expendable
            // (The AI assumes that the player
            // keeps one more Miner around than
            // necessary to insure for an unforeseen mortality).

                setExpendableEights(1-c);

            else {

		// When the number of possible structures is greater
		// than the supply of Miners, the Miner retains
		// the value set in values[] and stealth value
		// at the start of the game.

		// Yet if both players have non-expendable Eights,
		// should they exchange Miners?  If the player is
		// losing, its only chance of winning may be to attack
		// the opponent flag, so its Miners are more valuable,
		// and this is somewhat addressed by adjustPieceValues().

		// But even if the player is winning, retaining
		// a Miner superiority could be critical in winning
		// the game, because if it has to protect its flag,
		// it may not be able to use its material advantage
		// to win the game.   Furthermore, some of its
		// material advantage could be inside of bombed
		// structures!

		// So a Miner superiority decreases the value
		// of a player's Miner while inferiority increases the value.
		// This encourages or deters an exchange of Eights
		// when each player has non-expendable Eights.

		// For example, Red and Blue both have two possible
		// bombed structures.  Red has 3 unknown Miners (and
		// a Seven inside a bombed structure).  Blue has 1 known
		// Miner (and a Seven inside a bombed structure).
		// Thus both sides have Miners that are not expendable.

		// A human would instantly recognize that Red has
		// an easy win if it simply exchanges Miners which
		// eliminates all of Blue's movable pieces.  Attacking an
		// opponent flag structure is less desirable because
		// if the attacker chooses wrong, it unleashes a Seven
		// and the player would lose the game.

		// But the AI as Red is reluctant to exchange an equivalently
		// valued unknown piece for a known piece.
		// So the equation below makes Blue's known Miner more
		// valuable than Red's unknown Miner.

			if (rankAtLarge(c, Rank.EIGHT)
				> rankAtLarge(1-c, Rank.EIGHT))
				values[1-c][Rank.EIGHT.ordinal()] =
				(values[c][Rank.EIGHT.ordinal()] + stealthValue(c, Rank.EIGHT)) * 10 / 9;
		}

		// Making the flag known eliminates the flag
		// from the piece move lists, which means that
		// it offers no protective value to pieces that
		// bluff against lower ranked pieces.

		// Setting the opponent flag known is a gamble.
		// If the flag is known, a low ranked AI piece in the area
		// will attack the flag, which could lose the game
		// For example,
		// | R8 R1
		// | BB B7
		// | BB BB -- BF
		// ----------
		// R8xBB, B7xR8, R1 attacks the corner bomb which it
		// thinks is the flag, but the flag is actually elsewhere.

		// So the only case when the AI makes the flag known
		// is when there is only one potential bomb structure
		// remaining.   And the AI makes its flag known as well,
		// if it is indeed the piece surrounded by the bomb
		// structure.

		// Note: earlier code tried to identify which of the
		// remaining opponent pieces was actually the flag
		// and made it known,
		// which caused the AI to hammer all of its pieces
		// into the remaining unknowns, resulting in loss of
		// the game.  So now the code does not make the flag
		// known, but assigns a value to the suspected flag that
		// is just larger than its expendable pieces, which
		// the AI still uses to hammer into the remaining unknowns.

		if (isBombedFlag[c]) {
			if (maybe_count[c] == 1)
				makeFlagKnown(pflag);

			if (opponentEightsAtLarge == 0)
				continue;
		}

		// Note:Flag value depends on opponent Eight value above
		setFlagValue(pflag);

		// The ai only uses its eights to attack flag structures
                // if it has enough remaining.  However, it still approaches
                // flag structures with unknown expendable pieces.
                // This subterfuge often can cause the opponent into
                // making a mistake, attacking an unknown piece that
                // approaches the flag, perhaps resulting in material
                // gain for the AI.

                // this code is only for opponent bombs

		// Miners begin to attack bomb structures as the
		// number of possible structures diminish.
		// So if the AI has a surplus of Miners, it will
		// start to attack likely looking bomb structures
		// immediately.  If it has a deficit of Miners,
		// it waits until the number of structures diminish.

		boolean sendMiner = (maybe_count[c] - open_count[c] <= opponentEightsAtLarge)
			|| (maybe_count[c] <= 3 && 
				(maybe_count[c] - open_count[c] - 1 <= opponentEightsAtLarge));

                if (c == Settings.bottomColor
                        && isBombedFlag[c]
                        && sendMiner)
				for (int d : dir) {
					int j = flag[c] + d;
					if (!Grid.isValid(j))
						continue;
					TestPiece p = (TestPiece)getPiece(j);
                    p.setFlagBomb(true);
					p.targetValue = aiBombValue(p);
				}

		defendFlag(flagi);
		genDestFlag(flagi);

		// Unmoved pieces that remain in structures that could
		// contain the flag should remain unmoved if possible,
		// to confuse the opponent about the flag location,
		// unless the player is winning by a large edge.

		// unmovedValue[] distorts unknown exchanges, so this
		// needs to be as low as necessary to keep the
		// pieces unmoved.  This distortion shows up in
		// close exchanges.  For example,
		// | R4 --
		// | B5 B?
		// | B? B? B?
		// -------
		// R4xB5 and B?xR4 is 50 - 100 + 40 (Two stealth) = -10.
		// But if B? is on the back rank, the unmovedValue is 6,
		// plus VALUE_MOVED (5), which leads Red to attack.

		for (int x = 0; x < 10; x++) {
			int i = Grid.getIndex(x, Grid.yside(c, 0));
			Piece p = getPiece(i);
			if (p == null || p.getRank().ordinal() <= 3)
				continue;
			boolean altFlag = true;
			for (int d : dir) {
				int j = i + d;
				if (!Grid.isValid(j))
					continue;
				p = getPiece(j);
				if (p == null
					|| (p.isKnown() && p.getRank() != Rank.BOMB)
					|| p.hasMoved()
					|| p.getRank().ordinal() <= 3) {
					altFlag = false;
					break;
				}
			}
			if (altFlag
				&& isWinning(c) < VALUE_THREE
				&& (isWinning(c) < 0 || rnd.nextInt(20) != 0))
				for (int d : dir) {
					int bi = i + d;
					if (!Grid.isValid(bi))
						continue;
					p = getPiece(bi);
					unmovedValue[bi] += 4;
				}
		} // x
		} // c
	}

	// Establish location "i" as a flag destination.
	void genDestFlag(int i)
	{
		Piece flagp = getPiece(i);

                // If the flag is not exposed, don't bother
                if (!grid.hasMove(flagp))
                    return;

		int color = flagp.getColor();

		// Send in an expendable to attack the suspected flag.

		// Note: if the flag is still bombed, the
		// destination matrix will not extend past the bombs.
		// So the expendable will only be activated IF
		// there is an open path to the flag.

		chaseWithExpendable(flagp, i, DEST_PRIORITY_ATTACK_FLAG);

		// Note: planA and planB keep only 2 pieces
		// occupied.  Thus it can happen that the AI has several
		// Scouts remaining, and one of the Scouts has an obvious
		// path to the flag, but that Scout isn't picked to move.
		// This is a necessary tradeoff to keep most pieces unmoved
		// and reduce the size of the search tree.

		chaseWithScout(flagp, i, DEST_PRIORITY_ATTACK_FLAG_WITH_SCOUT);
	}

	// Generate a matrix of consecutive values with the highest
	// value at the destination "to". (Lee's algorithm).
	//
	// For attackers, "color" is the color of the defender
	// (usually at destination "to" or nearby).
	// A piece of any color blocks the destination from discovery,
	// unless GUARDED_CAUTIOUS is set, in which case
	// the maze continues through moved pieces.
	//
	// If the destination "to" contains a piece of "color",
	// the maze continues.  If the destination "to" contains
	// a piece of the opposite "color", this function returns immediately.
	//
	// Seed the matrix with "n" at "to".
	//
	// If GUARDED_CAUTIOUS or GUARDED_OPEN_CAUTIOUS is set,
	// then the matrix will avoid passing
	// any moved piece of unknown or superior rank.
	// This is used when sending eights.
	//
	// This matrix is used to lead pieces to desired
	// destinations.
	private int[] genDestTmpCommon(int guarded, int color, int to, Rank attackRank)
	{

	// TBD: The destination matrices were originally designed for
	// ranks, not pieces.  The idea was that all ranks were equivalent
	// in moving around the board.  But this idea is not quite correct.
	// Unknown ranks are safer to move around than known ranks.
	// Also, winFight(Piece, Piece) requires piece inputs to determine
	// the winner of a fight, because unknown pieces may have
	// an associated flee rank that can be meaningful in determining
	// the winner of the fight.
	// 
	// So the hack is to use rank to index planAPiece and planBPiece
	// to get the piece.  But genDestTmpCommon is used for both
	// planA and planB, so it often uses the wrong piece for the wrong
	// plan.  Still, it is better to sometimes use the wrong piece
	// than to base win/loss assumptions just on rank.

		Piece attackPiece = null;
		if (attackRank != Rank.NIL) {
			assert guarded == GUARDED_OPEN_CAUTIOUS
				|| guarded == GUARDED_CAUTIOUS :
				"genDestTmpCommon called with incompatible arguments";
			attackPiece = planAPiece[1-color][attackRank.ordinal()-1];
			if (attackPiece == null)
				attackPiece = planBPiece[1-color][attackRank.ordinal()-1];
			if (attackPiece == null) 
				return null;
		}
			
		int[] destTmp = new int[121];
		for (int j = 0; j <= 120; j++)
			destTmp[j] = DEST_VALUE_NIL;

		destTmp[to] = 1;
		ArrayList<Integer> queue = new ArrayList<Integer>();
		queue.add(to);
		int count = 0;
		while (count < queue.size()) {
			int j = queue.get(count++);
			if (!Grid.isValid(j))
				continue;
			int n = destTmp[j];

			Piece p = getPiece(j);

		// Any piece blocks the way
		// (except for the destination piece, if any).
		// (I had tried to allow a path through 
		// pieces of opposite "color" to have a 1 move penalty,
		// because the attacker can move these out of the way,
		// but this causes bunching up of attackers).

			boolean blocked = false;
			if (p != null
				&& !(j == to && p.getColor() == color)
                                && !(attackRank == Rank.EIGHT
                                    && p.getColor() == color
                                    && p.getRank() == Rank.BOMB)) {

		// If GUARDED_CAUTIOUS is set, then the maze can continue
		// through non-adjacent moved pieces of the same color.
		// The hope is that these moved pieces will eventually move,
		// clearing the way.  This should not cause
		// stacking problems if this is used sparingly.

		// This tries to address the situation where
		// an opponent invincible piece is known and 
		// chases the players moved pieces, which it is
		// certain to win.  The player must target the
		// opponents invincible piece with an invincible
		// piece of its own, but it needs to see through
		// its moved pieces to get there.

				if (guarded != GUARDED_CAUTIOUS
					|| !p.hasMoved())
					continue;

				blocked = true;
			}

		// check for guarded squares
			if ((guarded == GUARDED_CAUTIOUS
				|| guarded == GUARDED_UNKNOWN
				|| guarded == GUARDED_OPEN_CAUTIOUS)
                                && j != to) {
				boolean isGuarded = false;
				for (int d : dir) {
					int i = j + d;
					if (!Grid.isValid(i))
						continue;
					if (i == to)
						continue;
					Piece gp = getPiece(i);
					if (gp == null 
						|| gp.getColor() != color)
						continue;

            // Scouts can zip past opponent pieces on their first move,
            // but on a multileg path, the scout can be blocked.
            // The AI prunes off most Scout moves to save time, but
            // uses the best path plan for Scout moves if the Scout is not
            // easily blocked.  For example,
            // -- -- -- R9
            // -- -- b? --
            // BF -- -- --
            // Unknown moved Blue blocks every path except the
            // leftmost.  Note that it does not block Red Scout
            // from going left or down, because the Blue move
            // goes to the same Y or X as Red Scout.

                        if (attackRank == Rank.NINE) {
                            int ap = attackPiece.getIndex();
                            if (Grid.getY(ap) != Grid.getY(i)
                                && Grid.getX(ap) != Grid.getX(i)
                                && gp.hasMoved())
                                isGuarded = true;
					} else {
                                            if (attackRank != Rank.NIL) {
						int result = winFight(gp, attackPiece);
						if (result == WINS
							|| result == UNK)
							isGuarded = true;
                                            } else if (!gp.isKnown())
						isGuarded = true;
                                        }
				}
				if (isGuarded) {
					destTmp[j] = DEST_VALUE_NIL;
					continue;
				}
			}

			// set the neighbors
			for (int d : dir) {
				int i = j + d;
				if (!Grid.isValid(i) || destTmp[i] != DEST_VALUE_NIL)
					continue;

				Piece bp = getPiece(i);
				if (blocked
					&& bp != null
					&& bp.getColor() != color)
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

	private int[] genDestTmpGuardedOpen(int color, int to, Rank guard)
	{
		return genDestTmpCommon(GUARDED_OPEN_CAUTIOUS, color, to, guard);
	}

	// genDestTmpGuarded() generates a path to the destination
	// even if the path is blocked by pieces of their own color
	// This is used by pieces that are needed to guard lanes.

	private int[] genDestTmpGuarded(int color, int to, Rank guard)
	{
		return genDestTmpCommon(GUARDED_CAUTIOUS, color, to, guard);
	}

	// To reduce the number of moved weak pieces,
	// expendable ranks are needed as required by setNeedExpendableRank(),
	// except for expendable Eights, which may be needed
	// for its ability to attack a bomb.

	private void setNeededRank(Piece[][] plan, int color, int rank)
	{
		if (rank != 8
			&& isExpendable(color, rank)) {

			if (needExpendableRank == 0
				|| rank < needExpendableRank)
				needExpendableRank = rank;

		} else if (plan[color][rank-1] != null
			&& !plan[color][rank-1].hasMoved())
			setNeededRank(color, rank);
	}

	private void genNeededPlanA(boolean neededNear, int [] desttmp, int color, int rank, int priority)
	{
		genPlanA(neededNear, desttmp, color, rank, priority);
		setNeededRank(planAPiece, color, rank);
	}

	private void genNeededPlanB(int [] desttmp, int color, int rank, int priority)
	{
		genPlanB(desttmp, color, rank, priority);
		setNeededRank(planBPiece, color, rank);
	}

	private void genPlanA(int [] desttmp, int color, int rank, int priority)
	{
		setPlan(planA[color][rank-1], desttmp, priority);
	}

	private void setPlan(int[][] plan, int[] tmp, int priority)
	{
		if (tmp == null)
			return;

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

                // flag that the rank has a plan
                if (priority > plan[0][0])
                    plan[0][0] = priority;
	}

	private void setPlan(boolean neededNear, int[][] plan, int[] desttmp, int color, int rank, int priority)
	{
		if (desttmp == null)
			return;

		if (neededNear) {
			int[]tmp = new int[121];
			if (!hasPlan(planAPiece, color, rank))
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

	private void genPlanB(int [] desttmp, int color, int rank, int priority)
	{
		setPlan(planB[color][rank-1], desttmp, priority);
	}

	private void genPlanB(boolean neededNear, int [] desttmp, int color, int rank, int priority)
	{
		setPlan(neededNear, planB[color][rank-1], desttmp, color, rank, priority);
	}

	private void genPlanA(boolean neededNear, int [] desttmp, int color, int rank, int priority)
	{
		setPlan(neededNear, planA[color][rank-1], desttmp, color, rank, priority);
	}

	protected Rank getLowestAdjacentEnemyRank(int color, int to)
	{
		if (!grid.hasAttack(color, to))
			return Rank.NIL;

		Rank minRank = Rank.NIL;
		for (int d : dir) {
			Piece tp = getPiece(to + d);
			if (tp == null
				|| tp.getColor() != 1 - color)
				continue;

		// If an unknown opponent piece chases a known AI piece
		// during the search, the opponent could be a lower
		// piece or it could be bluffing, so nothing
		// can be determined about its rank during the search.

			Rank tprank = tp.getRank();
			if (color == Settings.bottomColor) { // tp is topColor
				if (tp.isKnown())
					continue;
				tprank = Rank.UNKNOWN;
			}

		// Note that tprank could be suspected rank.  Thus
		// any AI piece can approach a suspected bomb or flag
		// and an expendable AI piece can chase a suspected low
		// rank without aquiring a chase rank of Unknown.

		// But if a low ranked AI piece chases an (obvious)
		// suspected rank, it does make the AI rank obvious
		// as well.  For example, an opponent piece has been
		// chasing a Four.   Then if the AI chases it with an unknown
		// Two or Three, the opponent can easily guess its rank.

			if (tprank.ordinal() < minRank.ordinal())
				minRank = tprank;
		}

		return minRank;
	}

	// An expedition by a non-invincible piece into the unknown area of the
	// opponents ranks is called a foray.  
	// The idea behind a foray is to discover low ranked pieces
	// and win higher ranked pieces while avoiding Bombs.
	//
	// Statistically, random forays result in a loss of material,
	// if the opponent has strategically placed its pieces to rebuff them.
	// So a foray must be a directed attack in only one of the lanes,
	// preferably the side lanes, so that the opponent cannot
	// easily refortify the area.  Indeed, it is counter-productive
	// to attack multiple lanes at once, because the more the
	// opponents pieces move, the more mobility the opponent
	// has to refortify the other lanes.
	//
	// Humans are often able to guess the ranks of unmoved pieces
	// and direct forays accordingly, resulting in material gain.
	// This ability needs to distilled into an algorithm giving
	// the AI the same advantage.

	protected void genForay()
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

		if (lowestUnknownExpendableRank == 0
			|| (lowestUnknownExpendableRank < 5
			&& rankAtLarge(Settings.topColor, Rank.ONE) == 0)
			&& unknownNotSuspectedRankAtLarge(Settings.bottomColor, Rank.SPY) > 0)
			lowestUnknownExpendableRank = 10;


		// Random forays to accelerate the game
		// do not begin until the AI
		// has a substantial material advantage.
		// (If the AI only has a slight advantage, then the
		// opponent can easily reverse the advantage
		// by lying in wait.  For example, a known AI Four
		// may approach an unknown piece on a foray which
		// could be a Three).
		randomForay = isWinning(Settings.topColor) >= VALUE_THREE;
	}

	public boolean hasPlan(Piece plan[][], int color, int r)
	{
		return plan[color][r-1] != null;
	}

	public int isWinning(int color)
	{
		return sumValues[color] - sumValues[1-color];
	}

    // The AI thinks an aggressive piece is probably weak if:
    //	- it chases an unknown
    // 	- it chases an expendable piece
    public boolean isWeakAggressive(Piece p)
    {
        return (p.isChasing(Rank.UNKNOWN)
            || (p.getActingRankChase() != Rank.NIL
                    && isExpendable(1-p.getColor(), p.getActingRankChase().ordinal())));
    }

	public boolean hasLowValue(Piece p)
	{
		return pieceValue(p) <= pieceValue(p.getColor(),4);
	}

	// Sixes, Sevens and Nines (and excess known Eights) are expendable
	//
	// pieceValue() must be used instead of values[].
	// If values[] is used, then Eights (value 30) would be expendable.
	// But is important that values[] is tuned so that
	// as values[] decreases in end game situations,
	// (and stealthValue[] does not change)
	// that Nines (with high stealth) remain expendable.

	public boolean isExpendable(int c, int r)
	{
		return !isInvincible(c,r)
			&& pieceValue(c, r) <= pieceValue(c, 6);
	}

	public boolean isExpendable(Piece p)
	{
		return !isInvincible(p)
			&& pieceValue(p) <= pieceValue(p.getColor(),6);
	}

	// Note that an excess known Eight is expendable.   Call isStrongExpendable()
	// to prevent questionable moves, such as an Eight or a Nine attacking
	// unknown pieces just to discover their rank.

	public boolean isStrongExpendable(Piece p)
	{
		if (p.getRank().ordinal() >= 8)
			return false;
		
		return isExpendable(p);
	}

	public boolean isNeededRank(Piece p)
	{
		return neededRank[p.getColor()][p.getRank().ordinal()-1];
	}

	public void setNeededRank(int color, int rank)
	{
		neededRank[color][rank-1] = true;
	}

	protected int planv(int [][] plan, int from, int to)
	{
		int pto = plan[1][to];
		int pfrom = plan[1][from];
		if (pto == 0 && pfrom == 0)
			return 0;

		int vto = plan[0][to];
		int vfrom = plan[0][from];

		if (pto == pfrom) {

		// Award the high value DEST_PRIORITY_DEFEND_FLAG
		// for only the first move.

        if (pto >= DEST_PRIORITY_DEFEND_FLAG) {
            if (depth <= 1) {
		// prevent transposition table equivalency.
                hashDepth(depth);
                return Math.max(Math.min(vfrom - vto, 1), -1) * pto;
            }
            pto = DEST_PRIORITY_DEFEND_FLAG_AREA;
        }

		// The difference in chase plan values for adjacent squares
		// should always be 1, except if the plan was modified by
		// neededNear, which makes the squares
		// adjacent to the target (value 2) to be value 5.
		//
		// 4 3 4 5	4 3 4 5
		// 3 2 3 4	3 5 3 4
		// 2 T 2 3	5 T 5 3
		// 3 2 3 4	3 5 3 2
		// chase	neededNear
		//
		// Yet if a blocking piece can be reached via two different
		// paths, and the blocking piece moves allowing the chase
		// piece to move across the two paths,
		// the difference can be large.  A large difference
		// could allow the path value to exceed material value,
		// and cause the AI to sacrifice material for the shortcut.
		// So the value is limited to the maximum plan step value.

			return Math.max(Math.min(vfrom - vto, 2), -2) * pto;

		} // to-priority == from-priority

		else if (pfrom == DEST_PRIORITY_LANE)
			return DEST_PRIORITY_LANE;
		else if (pto == DEST_PRIORITY_LANE)
			return -DEST_PRIORITY_LANE;
		else if (pfrom == 0)
			return 1;
		else if (pto == 0)
			return -1;

		return 0;
	}

	protected void addPlanValue(Piece fp, int from, int to, boolean scoutFarMove)
	{
		int [][] a = getPlanA(fp);
		int [][] b = getPlanB(fp);

		// If a piece is both a plan A and a plan B piece
		// (that is, it is the only piece of that rank on the board)
		// it gets plan A or plan B

		int vm;
		if (a != null && b != null ) {
			vm = planv(a, from, to);
			if (vm == 0)
				vm = planv(b, from, to);
		} else if (a != null)
			vm = planv(a, from, to);

		else if (b != null)
			vm = planv(b, from, to);
		else
			return;

		// Scouts go too fast, so limit the points to one

		if (scoutFarMove)
			vm = Math.min(vm, 1);

		value += vm;
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
	// If the AI attacks an unknown piece, it creates a known Unknown.
	// A known Unknown (isKnown() and isSuspectedRank() are both true)
	// has a suspected rank, so its value is diminished in
	// actualValue(), and it is also known, so a further attack on
	// the opponent piece does not garner its stealth value.
	//
	// Note: rankWon can be the Flag in which case the newRank
	// will be based on lowestUnknownExpendableRank.
	//
	public void makeWinner(Piece p, Rank rankWon, boolean aiAttack)
	{
		boolean isSuspected = p.isSuspectedRank();
		boolean isMaybeEight = p.isMaybeEight();
		Rank newRank = p.getRank();

		// The winner must always become known.
		// Otherwise the AI will plan to attack the same
		// piece multiple times to accumulate its stealth value.
		//
		// Note: makeKnown clears SUSPECTED_RANK and MAYBE_EIGHT
		// so these are reset below as needed
		p.makeKnown();

		// If the piece is an AI piece
		// or a known opponent piece (not suspected)
		// or a Nine (only can win a Spy)
		// or suspected Bomb or Flag (can win any piece),
		// the piece rank does not change.

		if (p.getColor() == Settings.topColor
			|| (newRank != Rank.UNKNOWN && !isSuspected)
			|| newRank == Rank.NINE
			|| newRank == Rank.BOMB
			|| newRank == Rank.FLAG)
			return;

		// The only piece that wins a bomb is an Eight,
		// and then it is no longer suspected
		if (rankWon == Rank.BOMB) {
			p.setRank(Rank.EIGHT);
			return;
		}

		// If the piece already has a suspected rank, keep it.
		// For example, if a Nine attacks a suspected One,
		// makeWinner(One, Nine) is a One rather than some
		// rank lower than Nine.

		if (!isSuspected) {
			assert newRank == Rank.UNKNOWN : "Rank is " + newRank + " but expected UNKNOWN to win " + rankWon;

			if (rankWon == Rank.FLAG)
				newRank = Rank.toRank(lowestUnknownExpendableRank);
			else if (rankWon == Rank.ONE) {
				newRank = Rank.SPY;
			} else
				newRank = getChaseRank(rankWon);

			if (newRank == Rank.NIL)
				newRank = Rank.toRank(lowestUnknownExpendableRank);

			assert newRank != Rank.UNKNOWN : "Piece " + p.getRank() + " " + isSuspected + " should not have won (invincible)" + rankWon + " at " + p.getIndex() + " because lowestUnknownNotSuspectedRank is " + lowestUnknownNotSuspectedRank;
		}

		// Suspected ranks have much less value (see actualValue()).
		// The AI cannot attack a trapped low ranked opponent
		// piece with a losing piece just to increase its value.
		// So when the AI attacks an unknown opponent piece
		// that remains on the board, the opponent piece becomes
		// known, but remains a suspected rank.
		//
		// In addition, if an unknown opponent piece wins
		// an AI piece Six or greater, the assumption
		// is that the attacker is a Five.  But it might also
		// be a lesser valued piece, such as a Four, so prior code
        // retained the suspected rank.   However, the AI can lose
        // by guessing wrong in any case, so now the AI sticks with its guess.

		if (aiAttack /* || rankWon.ordinal() >= 6 */)
			p.setSuspectedRank(newRank);	// known Unknown
		else
			p.setRank(newRank);

		// Attacks on or by Nines do not change MAYBE_EIGHT
		if (rankWon.ordinal() >= 9)
			p.setMaybeEight(isMaybeEight);
	}

	// morph() is the same concept as makeWinner(), but for AI pieces.
	// It is used when a bluff is effective,
	// because the opponent would have to consider that its piece would
	// be removed from the board, and a superior known AI piece
	// would replace it.
	//
	// It is also necessary to avoid transposition cache equivalency
	// because the disappearance of a bluffing AI piece is not equivalent
	// to its disappearance in other situations (e.g. EVEN).
	//
	// To address this issue prior to Version 10.3,
	// the bluffing AI piece was kept on the board
	// and was made known.   The problem was that the AI piece could
	// keep going and rack up more points.   One situation was an unknown
	// AI piece bluffing as an Eight.  It attacks the opponent flag
	// structure and LOSES, but the flag bomb was removed and the
	// AI piece was retained.  Then it goes on to win the flag.
	//
	// As of Version 10.3, the surviving piece morphs into a BOMB
	// to prevent it from moving and any other piece from moving
	// through it.  It remains unknown so that it can bluff again,
	// to address the case where the opponent attacks the piece
	// to transform it into a bomb and then another opponent piece
	// moves past it to attack some other valuable AI piece.
	//
	// However, by moving the bluffing piece that morphed
	// into a bomb, qs returned an incorrect result for this example:
	// -- R? R? --
	// -- B4 BB --
	// -- BB BF BB
	// If R?xB4 (LOSES) is a good bluff, and then unknown Red morphs
	// into a unknown bomb at the Blue Four position, unknown
	// Red is still unknown so R?xBB (EVEN) is a good bluff as well,
	// even though Red is now a bomb.  Eventually it bluffs its way
	// to the flag.
	//
	// Version 11 fixes this bug by not moving a bluffing piece.

	protected void morph(Piece p)
	{
		p.setRank(Rank.BOMB);

	// A morphed piece cannot bluff again as a Spy.
	// For example,
	// -- RS --
	// B4 -- R?
	// -- B1 --
	// Unknown Red Spy moves down and forks Blue Four and Blue One.
	// B4xRS is WINS and morphs the Spy into an unknown Bomb.
	// B?xB1 is not a threat, because the Spy loses any encounter.

		p.setActingRankFlee(Rank.ONE);
		setPiece(p, p.getIndex());
	}

	public void move(int m)
	{
		move(m, Grid.isAdjacent(m));
	}

	public void move(int m, boolean adjacent)
	{
		int from = Move.unpackFrom(m);
		int to = Move.unpackTo(m);
		Piece fp = getPiece(from);

		addMoveValue(m, adjacent);
		addPlanValue(fp, from, to, !adjacent);
	}

	public boolean chaseMove(int m)
	{
		boolean adjacent = Grid.isAdjacent(m);
		int from = Move.unpackFrom(m);
		int to = Move.unpackTo(m);
		Piece fp = getPiece(from);
		addMoveValue(m, adjacent);

		// If the chase can lead to a two squares result,
		// repetitive moves are OK
        // (until Two Squares Rule kicks in)

		if (isTwoSquaresChase())
			value++;
		else if (isRepeatedPosition())
			return true;

		// addPlanValue() can change hash,
		// so call isRepeatedPosition() before addPlanValue()

		addPlanValue(fp, from, to, !adjacent);
		return false;
	}

	public void addMoveValue(int m, boolean adjacent)
	{
		int from = Move.unpackFrom(m);
		int to = Move.unpackTo(m);
		TestPiece fp = (TestPiece)getPiece(from);
		TestPiece tp = (TestPiece)getPiece(to);
		boolean scoutFarMove = !adjacent;
		boolean unknownScoutFarMove = scoutFarMove && !fp.isKnown();
		boolean wasChased = isChased(from);
		moveHistory(fp, tp, m);

		if (depth == 0) {
			assert boardHistory[0].hash == hashTest[0] : "bug: hash changed before move "  + from + " to " + to;
			assert boardHistory[1].hash == hashTest[1] : "bug: hash changed before move "  + from + " to " + to;
		}

		clearPiece(from);

		// setDirectActingRankFlee(fp, tp);

		int vm = 0;

        if (!fp.isKnown()
            && !fp.hasMoved())
            vm -= unmovedValue[from];

        // Moving an unknown scout reveals its rank.
        // Note: if this is an attack move, fpvalue will not include the stealth
        // because the scout has been made known and vm contains the loss of stealth

        
        if (unknownScoutFarMove) {
            fp.setRank(Rank.NINE);
            fp.setMaybeEight(false);
            vm -= stealthValue(fp);
            fp.makeKnown();
        }

		Rank fprank = fp.getRank();
		int fpcolor = fp.getColor();
		boolean fpknown = fp.isKnown();
		boolean fpsuspected = fp.isSuspectedRank();

		if (tp == null) { // move to open square

		// When a non-expendable piece is needed somewhere
		// on the board, unmoved pieces can impede its progress,
		// because movement of these pieces incur a penalty.
		// So if an unmoved piece moves to make way
		// for a needed piece (because "to" has unmovedValue),
		// add back the movement penalty.
		// (If the non-expendable piece has already moved twice,
		// it could be moving back-and-forth to the penalty
		// square, so don't reward this behavior.)

			if (!isExpendable(fp)
				&& isNeededRank(fp)
				&& fp.getMoves() - fp.boardPiece().getMoves() <= 1)
				vm += unmovedValue[to];

		// Give a slight bias to all pieces to move towards
		// an open opponent flag and
		// towards a bombed opponent flag, if the player still
		// has any Miners.   This replaces
		// the code that used a plan A/B chase to move
		// pieces in the lanes down the lanes, because that
		// code overloaded chases, often stranding low rank
		// pieces in the lanes while the opponent pieces
		// ran amok.

		// Note that if the opponent chooses to defend its
		// flag area, this will attract more player pieces
		// towards the flag in the chase() code.

        // Note that plan A/B chase offers more points, so
        // pieces should follow plans first.   But having a default
        // direction helps prevent pieces from wandering
        // aimlessly around the board.

        // TBD: This causes stacking and needs to be dealt with

			assert flag[1-fpcolor] != 0;
            if ((!isBombedFlag[1-fpcolor]
                || rankAtLarge(fpcolor, Rank.EIGHT) != 0)
                && fp.getMoves() - fp.boardPiece().getMoves() == 0) {
				if (Grid.steps(to, flag[1-fpcolor])
					- Grid.steps(from, flag[1-fpcolor]) < 0)
					vm++;
				else
					vm--;
			}

		// If an unknown player piece approaches an unknown
		// opponent piece during the search,
		// the player piece is probably
		// weak, so it gains a temporary chase rank so that deeper
		// evaluation is consistent.  For example,
		// rS --
		// r3 --
		// -- xx
		// b? xx
		// All pieces are unknown.  When the Red considers the
		// move sequence beginning with b? moving up towards
		// unknown Red Three, unknown Blue gains a chase rank
		// of unknown, so Red can plan to play r3xb? with the
		// expectation of a WIN.
		//
		// Note that without this code, unknown Blue would be
		// a threat to Red Three, so the move sequence would
		// be very negative after Red Three flees and unknown
		// Blue forks Red Spy and Red Three.
		//
		// Note that if a *known* AI piece is approached during
		// the search, the opponent piece is still unknown
		// and does not gain a suspected rank, because suspected
		// ranks are carefully assigned based on actual opponent
		// play and position analysis.
		//
		// Thus in the example above, if Red Three was known,
		// unknown Blue is a significant threat.  If unknown
		// Blue approaches, the AI does not know if unknown Blue
		// is a Two or just a bluffer.  So Red Three must move
		// aside and Blue then forks Red Spy and Red Three.
		// Red Three must retreat again, which would allow the
		// bluffer to capture the Spy.
		//
		// TBD: This is why it is important that is is best to
		// assign suspected ranks to unknown pieces 
		// before rather than during the search.  The AI
		// must guess the opponent unknown ranks and then live 
		// or die by its analysis, even if incorrect.  Otherwise
		// a bluffing opponent can quickly control the game.
		//
		// Thus in the example above, if unknown Blue had been
		// assigned a suspected Rank of Two, then Red would not
		// be imperiled, because unknown Blue would likely not
		// approach the unknown Spy, because it could be the
		// unknown One.

			if (!fp.isKnown()) {

				Rank oppRank = getLowestAdjacentEnemyRank(fpcolor, to);
				if (oppRank != Rank.NIL
					&& oppRank != fp.getActingRankChase()
					&& oppRank != Rank.BOMB
					&& oppRank != Rank.FLAG) {
					fp.setActingRankChaseEqual(oppRank);

		// By approaching any enemy piece for the first time,
		// a player piece loses part of its stealth,
		// if the opponent is playing attention,
		// because most players make at least some kind
		// of assumption that their opponent is making
		// rational moves.

		// So this is a bad idea unless the player piece
		// has a material reason to chase,
		// or isn't a piece that the opponent might suspect.

        // For example, if a unknown Two chases a known Five,
        // the opponent may guess that the chaser is a Four,
        // and then might chase the chaser with a Three and
        // lose it.  Thus it is even OK for an unknown Two
        // to chase a known Two, because it might make the
        // known Two think it is invincible (because it thinks
        // it knows where the AI One is) and then approach
        // the actual AI One.  The biggest risk is that the opponent
        // may try to attack it with an expendable piece
        // to disclose its identity, but misleading the opponent
        // is worth that risk.

		// Because the AI does not know if the opponent is
		// bluffing, this code applies only to AI pieces
		// and is especially important when the AI plays itself,
		// because then both sides are trying desperately
		// to determine piece ranks through these kinds of movements.

					if (fpcolor == Settings.topColor
                        && !isForay(to)) {
                        if (oppRank == Rank.UNKNOWN) {
                            if (hasLowValue(fp))
                                vm -= stealthValue(fp)/3;
                        } else if (fprank.ordinal() <= oppRank.ordinal())
                            vm -= stealthValue(fp)/3;
					}
				}
			}

			UndoMove m2 = getLastMove(2);

			if (fpcolor == Settings.topColor) {

		// If the AI moved an unknown piece on its prior move
		// adjacent to an opponent known piece of the same rank,
		// and the opponent attacked the AI piece, it was an EVEN
		// exchange and the opponent normally wins the AI piece stealth.
		// However if the opponent piece was not invincible, the AI
		// did not award the stealth, because the opponent may
		// not realize that the AI is bluffing.
		//
		// But if the opponent piece is an invincible One,
		// (and the AI approached it with its unknown One,
		// then the opponent played 1x1, creating an EVEN exchange),
		// an adjacent unknown AI piece *could* be the Spy,
		// so an unknown AI piece moving to this "ghost" square
		// is credited with restoring the lost stealth
		// and adding the bluffing value.
		// (very rare occurrence).
		//
		// Version 10.4 extended this concept to other EVEN exchanges.

			if (m2 != UndoMove.NullMove
				&& m2.getTo() == to
				&& depth != 0
				&& !unknownScoutFarMove) {
				Piece m2fp = m2.getPiece();
				Rank m2fprank = m2fp.getRank();
				if (m2fprank != Rank.UNKNOWN
					&& !(m2.fpcopy.isKnown()
						&& m2.tpcopy.isKnown())
					&& (!isInvincible(m2fp)
					|| (m2fprank == Rank.ONE
						&& hasSpy(fpcolor)))
					&& isEffectiveBluffWins(fp, m2fp, m))
					vm += valueBluff(m, fp, m2fp) - valueBluff(m2fp, fp);
			}

			} // fp is AI

/*
		// If an AI piece disappeared from the board on the last move
		// by attacking an unknown (or suspected) opponent piece,
		// it is possible that the AI piece could have actually won
		// the attack.  So if an opponent piece moves to the square
		// where the AI piece disappeared (the "ghost" square),
		// the opponent is rewarded if it is of lower rank or unknown.
		
			else if (m2 != null
				&& m2.getTo() == to
				&& !m2.tpcopy.isKnown()
				&& (!fp.isKnown()
					|| m2.getPiece().getRank().ordinal() > fprank.ordinal()))
					vm += pieceValue(m2.getPiece())/2;
*/

		// Take the wind out of the sails of alternating moves

			if (alternateSquares[to][0] == fp.boardPiece())
				vm -= DEST_PRIORITY_DEFEND_FLAG / 2;

            fp.setMoved();
			setPiece(fp, to);

		} else { // attack

           assert fp.getColor() != tp.getColor() : "invalid move";

		// Remove target piece and update hash.
		// (Target will be restored later, if necessary,
		// perhaps with different hash because of
		// change in "known" status and perhaps rank).

			clearPiece(to);

			Rank tprank = tp.getRank();

            assert !(fp.isKnown() && fprank == Rank.UNKNOWN
                || tp.isKnown() && tprank == Rank.UNKNOWN)
                : "Entry " + fprank + "X" + tprank;

			int fpvalue = actualValue(fp);
			int tpvalue = actualValue(tp);

			int result = winFight(fp, tp);

            if (!tp.isKnown()
                && !tp.hasMoved())
                vm += unmovedValue[to];

			switch (result) {
			case EVEN :
				// assert fprank != Rank.UNKNOWN : "fprank is unknown?";
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

				if (fpcolor == Settings.bottomColor) {

		// If the opponent piece is unknown,
		// the AI mave have guessed wrong,
		// and the exchange could be a loss.  This case
		// should only happen at depth, because if
		// the opponent actually allows its piece to contact
		// the known AI piece, the opponent would gain
		// a lower chase rank and thus the exchange would not be EVEN.

		// Yet the AI piece should try to avoid this situation,
		// because of the possibility that it has guessed wrong,
		// but it cannot overreact and lose material to an idle threat.
		// For example:
		// -- -- -- --
		// xx -- R3 xx
		// xx -- R4 xx
		// -- -- B? --
		// Unknown Blue approaches known R3.  This makes Blue
		// a suspected rank of 3.  Red Four should move,
		// because a suspected Blue Three approaching known Red Three 
		// (this EVEN case) is negative for Blue because it
		// would lose its stealth in an even exchange
		// (note that only stealth is on the line; the values
		// of suspected and known differ greatly so they are not used).

		// Unknown Blue *could* be a Two, but this is not a foregone
		// conclusion; Blue might not approach Red Three,
		// although that would be a very good move, because
		// then Red Three would have to move away (because
		// Blue is now a suspected Two), leaving Blue to
		// capture Red Four.

		// If Red Three is unknown, then the stealth exchange is a wash,
		// but it is unlikely that Blue would approach unknown Red
		// and so Blue would get a negative value from valueBluff().

					if (!fp.isKnown()) {
						vm += stealthValue(tp) - stealthValue(Settings.bottomColor, tprank.ordinal());

		// While the AI always gains the stealth value of the
		// unknown opponent piece, it may have guessed wrong.
		//
		// Note: the bluffing factor (br) changes the outcome
		// because it affects both stealth and riskOfLoss.
		// With lower bluffing factors (i.e. opponent not bluffing),
		// the result becomes more positive, because although the AI
		// has less knowledge to gain from an even exchange,
		// it has less chance of complete loss of its piece.
		//
		// Capture, Opp. Stealth (br=4), Risk Of Loss Factor, Result
		// 4x4?, 12, 16, -4 
		// 3x3?, 28, 33, -5
		// 2x2?, 32, 66, -34

						if (!fp.isFleeing(tprank))
							vm += riskOfLoss(tp, fp)/6;
					} // fp is unknown

		// If the defender is known, and either the attacker is known
		// or the AI is the attacker (AI knows its own pieces)
		// then, this is a known even exchange based on actual values.

		// Note that although the exchange is even, the value
		// may not be.  If the AI is winning, its ranks are
		// worth less than opponent ranks, encouraging it to
		// make an even exchange.

                // An exchange with a known Unknown is zero because of
                // situations like this example:
                // b? -- --
                // R3 R6 b?
                // -------
                // All of Blue's Fives and Fours are gone.   b?xR6
                // results in a known Unknown which could be Blue Three.
                // B3xR3 is even. A known Unknown has very little
                // value so Red would lose a lot in an event exchange,
                // which appears to be forced because Red Three
                // cannot flee.  So Red plays R3xB? which happens to
                // be a bomb.

					// fp is known

					else if (!fp.isSuspectedRank())
						vm += tpvalue - fpvalue;

		// Unknown AI pieces also have bluffing value

		// If the defender (tp) is an unknown AI piece,
                // then an attacker (not an invincible attacker, see above)
                // doesn't really know that the exchange is even.
                // The attacker sees only an unknown gain
                // but the potential loss of its piece.
		//
		// So first check for an effective bluff to see
		// if the attack is rational.  If the bluff is
		// effective, the attack is unlikely, and vm is reduced
		// to the bluffing value.
                //
					if (depth != 0
						&& !maybeIsInvincible(fp)
						&& isEffectiveBluffWins(tp, fp, m)) {
						vm = Math.min(vm, valueBluff(fp, tp));
						morph(tp);
					} else

                // RISK REDUCTION BY DISTANCE
		// If the attack is not an effective bluff,
		// reduce vm by the distance, because a known opponent
		// piece attack on an unknown AI piece to gain its
		// stealth is less likely.

		// Note that R?xB should equal BxR?, because both pieces
                // are removed from the board, so the transposition
                // table will return an identical result regardless
		// of the direction of attack.

		// (If it is an effective bluff, morph() changes the board
		// and thus the transposition table returns a correct result).

						vm /= distanceFactor(tp, fp, scoutFarMove);

		// AI is attacker

				} else {

		// If the defender is known,
		// this is a known even exchange based on actual values
		// (AI knows its own pieces).

		// Note that although the exchange is even, the value
		// may not be.  If the AI is winning, its ranks are
		// worth less than opponent ranks, encouraging it to
		// make an even exchange.

				if (tp.isKnown())
					vm += tpvalue - fpvalue;
				else {

					assert !tp.isKnown() : "defender is known?"; 
					vm += stealthValue(tp) - stealthValue(fp);

		// Often an unmoved piece acquires a chase rank
		// because it protected a piece.  However, the piece
		// could have been cornered, and the protector is a bomb.
		// So if the piece could be a bomb,
		// then the AI loses its value, even if it thinks it
		// might be an even exchange.

					if (isPossibleBomb(tp)
						&& fprank != Rank.EIGHT)
						vm -= values[fpcolor][fprank.ordinal()];

		// The AI One risks only its stealth value in
		// an even exchange, if the opponent piece turns out
		// not be a one. If the One is known, the exchange
		// is a wash.

		// But other pieces risk total loss, if the AI has
		// has incorrectly guessed the defender rank.
		// While probability favors the AI, it does not
		// want to risk its lower ranked pieces on its guesses.

					else {
						if (fprank != Rank.ONE
							&& fprank.ordinal() <= 5
							&& !tp.isFleeing(fprank))
							vm -= riskOfLoss(fp, tp);

		// Yet the AI usually is conservative,
		// so even exchanges usually turn out to be positive,
		// depending on the difference in values[]
		// (e.g. an even exchange of Eights could vary
		// wildly depending on the value of the Eights)

						vm += values[1-fpcolor][fprank.ordinal()] - values[fpcolor][fprank.ordinal()];

						vm += (9 - fprank.ordinal());
					}

		// Consider the following example.
		// -- R1 --
		// BS -- B2
		//
		// Blue Spy and Two are unknown and Red One is known.  Blue Two
		// has a suspected rank of One (because it chased Red Two).
		// Blue Two moves towards Blue Spy.  R1xB2 removes both
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
		// Blue Four has a suspected rank of Three.
		// R3xB4 would remove the pieces from the board
		// and the AI would not see B2xR3.
		//
		// By leaving the AI piece on the board, it can make
		// the following error:
		// R8
		// B? BB
		// BB BF BB
		// unknown Blue is suspected to be an Eight.  R8xB? would
		// leave the Red Eight on the board in position to attack
		// the flag.

		// In versions 9.3, the AI piece (other than an Eight)
		// stayed on the board if it attacks a suspected rank
		// in an EVEN exchange, because the suspected rank might
		// actually be higher.
		//
		// But this created the following undesired transposition
		// cache equivalency:
		// R1 RB
		// R3 --
		// -- B3
		// Red Three and Red Bomb are known and Blue Three
		// is suspected. 
		// ...    B3-a6
		// R3xB3  (null)
		// is very negative because perhaps B3 is a Two.
		//
		// ....   B3-b5
		// R3-a6  R3xRB
		// results in the same position and the cache returns
		// the negative number stored, rather than a highly
		// positive number because R3 just rammed into a known bomb!
		//
		// (Note: this issue also exists in a much reduced
		// form even in WINS. If the pieces are known, there
		// is no bug, but removing a suspected
		// opponent piece to a known bomb is not the same as removing
		// a suspected opponent piece in an exchange with a
		// known AI piece, but at least both are positive.)
		//
		// As of Version 10.4, this issue is handled by the "ghost"
		// square code when tp is null.
        //
        // As of Version 12, this issue is handled by the ghostPiece
        // code, leaving the AI piece on the board for 1 ply.
                    if (fp.getColor() == 1- bturn) {
                        fp.makeKnown();
                        setPiece(fp, to);
                        ghostPiece=getPiece(to);
                    }

				} // tp is not known
		// Unknown AI pieces also have bluffing value

				if (depth != 0
					&& !unknownScoutFarMove
					&& isEffectiveBluffLoses(fp, tp)) {
						vm = Math.max(vm,  valueBluff(m, fp, tp) - valueBluffLoses(tp, fp));
						morph(fp);
				} else

		// because of transposition table
		// see RISK REDUCTION above
					vm /= distanceFactor(fp, tp, scoutFarMove);

				} // AI is attacker

				//vm = 0; // fubar
				break;

			case LOSES :

				if (fpcolor == Settings.topColor) {

		// To be consistent, an attack
		// on a suspected rank that loses an AI piece
		// needs to be more value.  For example,
		// -- R? R?
		// B? R4 R?
		// -- xx xx
		//
		// Red Four is cornered by a piece chasing it.  R4xB?(3)
		// needs to be worse than staying pat.  After B?(3)xR4,
		// Blue becomes a known Three.
		// R?x3 still needs to be a deterrent.

		// So fp loses its entire value if known, but
		// nothing if the defender may think that it is
		// a lower ranked piece.  The value
		// of an unknown protector piece is irrelevant.

					if (depth != 0
						&& !unknownScoutFarMove
                        && ((fprank == Rank.BOMB || fprank == Rank.FLAG)
						    || isEffectiveBluffLoses(fp, tp))) {
						vm += Math.max(stealthValue(tp), valueBluff(m, fp, tp) - valueBluffLoses(tp, fp));
						morph(fp);
						break;
					}

		// The opponent knows that the AI is bluffing, so
		// the opponent expects that whatever the AI piece rank,
		// it is lost.
					if (!tp.isKnown()) {

		// If an opponent piece flees from an AI piece,
		// the opponent is signaling that the exchange
		// would be in the favor of the AI.  So it might
		// seem intuitive that the AI should pursue the exchange.

		// But the opponent and the AI may differ on what
		// they believe is a good exchange, so while
		// a fleeing piece is a good sign of a decent exchange,
		// it may or may not be in the best interest of the AI.

		// For example, known Red Eight has approached
		// suspected Blue Three.   Blue Three does not attack
		// and so obtains a flee rank of 8.  Blue does
		// not think attacking Red Eight is worthwhile,
		// because it does not want to reveal its rank.

		// But the AI has already guessed that the piece is
		// a Three.  So to the AI, it is not worthwhile to
		// attack the Three and confirm its stealth.

						if (tp.hasMoved()
                            && tp.isFleeing(fprank))
                            vm += stealthValue(tp) - fpvalue/2;

		// The opponent is an unknown rank and the AI
        // piece thinks it loses, but it could
		// be any rank, perhaps weaker.
		// The AI loses its piece value but gains at least
        // the stealth of the opponent piece or riskOfWin(),
        // whichever is greater.

						else {
                            vm += Math.max(
                                    stealthValue(tp),
                                    riskOfWin(fpvalue, fp, tp, !tp.hasMoved() && fprank != Rank.EIGHT))
                                - fpvalue;
						}
                        if (tp.isSuspectedRank())
                            tp.setKnown(true);
						if (fprank.ordinal() < 9)
							tp.setMaybeEight(false);
					} else

        // Opponent is a known piece (or known Unknown)
        // and the AI knows that it loses.

        // TBD: The AI has a better chance against a known Unknown,
        // but it already calculated that an AI piece lost against
        // it earlier.

                            vm -= fpvalue;

					setPiece(tp, to);
				} else {

		// AI is defender

		// The AI guesses that certain opponent pieces are bombs.
		// A suspected opponent bomb attacking an AI piece
		// always LOSES, because winFight return LOSES when
		// both pieces have rank and a bomb is always higher in rank.
		// (An unknown AI bomb can move as well, and this is
		// also handled sent to LOSES. See winFight()).
		
		// But the AI could be wrong, and so
		// allows the suspected bomb piece to move or attack.
		// An Eight is allowed to approach suspected bombs and attack.
		// But for valuable ranks, this is a bit aggressive, because
		// because the suspected bomb could be a lower ranked piece,
		// and then the AI could unwittingly lose a valuable rank
		// and consequently the game.

		// Should the Eight be allowed to pass or attack a
		// suspected worthless bomb?  The prior code
		// made any suspected worthless bomb an unknown.
		// This prevents the Eight from passing it to get at the
		// bomb structure.  The new code puts the Eight
		// at risk, perhaps unnecessarily if there is some other
		// path to the bomb structure.  Thus the AI needs to
		// be relatively confident that the suspected bomb
		// really is a bomb before marking it suspected.
		// The AI also tries to retain one more Eight than
		// the number of bomb structures to insure against loss.
		// A player cannot waste pieces on trying to identify
		// worthless bombs.

		// Unknown pieces are also allowed to pass suspected bombs.
		// This may be necessary to allow the piece to attack
		// other pieces.  Passing a bomb entails a tiny penalty
		// to encourage the piece to pick a safer path if at
		// all possible.

		// For known pieces, the AI has to guess whether its piece
		// stays on the board.  Prior to Version 11, it awarded a variable
		// value based on projected risk and left the AI piece on the board.
		// But if the AI is chasing
		// a valuable piece past a suspected bomb, then the value
		// is irrelevant.  For example:
		// -- R1 --
		// BB -- --
		// -- B2 --
		// Blue Spy is unknown and the AI has incorrectly guessed it
		// to be a bomb.  If Red One moves towards Blue Two, and
		// then BBxR1.  The negative value of the board is irrelevant if it
		// is less than the value of Blue Two and if Red One remains on
		// the board, because after R1xB2, the value becomes positive again,
		// allowing Red One to chase Blue Two past the suspected bomb,
		// regardless of whether Blue Two is trapped.

					if ((fprank == Rank.BOMB || fprank == Rank.FLAG)
						&& !isInvincibleDefender(tp)
                        && !isForay(to)) {

		// When an opponent (suspected) flag bomb attacks an AI Miner
		// or an opponent (suspected) flag attacks any AI piece,
		// and the attack is favorable for the AI, remove both pieces,
		// and the opponent loses its piece value.

		// Otherwise, if the AI Miner were left on
		// the board, it could continue to obtain points
		// in the search tree by attacking other flag bombs (WINS),
		// which could allow it to leave material hanging.
		// (Removing both pieces is also correct if the bomb
		// turns out to be an opponent of equivalent rank.
		// If the opponent piece turns out to be a superior rank,
		// at least the AI makes a half-right guess).

		
						if (tprank == Rank.EIGHT
							|| fprank == Rank.FLAG) {
							if (fpvalue > tpvalue) {
								vm -= fpvalue;
								break; // both pieces removed from board
							} else
								vm += 3;
						}

						if (!tp.isKnown()
							|| isExpendable(tp)
							|| (fp.isWeak()
								&& tprank.ordinal() <= 4))
							vm += 1;

						else {
							vm += pieceValue(tp)/7;

							// piece is removed from board
							break;
						}

					} else { // not bomb or flag

		// fp is opponent, so opponent loses but gains the stealth
		// value of the AI piece and the AI piece becomes known.

						vm += apparentWinValue(fp, fprank, unknownScoutFarMove, tp, stealthValue(tp));
						if (!fp.isFleeing(tprank))
							vm += riskOfLoss(tp, fp);
                        vm = vm / distanceFactor(tp, fp, scoutFarMove);
						vm -= fpvalue;
					}

					tp.makeKnown();
					setPiece(tp, to);
				}

				break;

			case WINS :

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

		// If a piece is a possible bomb (or flag)
		// and an AI piece (except Eight) attacks it and wins,
                // the AI piece gains the value of the opponent but
		// loses its own value (because it could still be a bomb).
		// This often happens when an opponent bluffs with
		// a protector piece that hasn't moved so it has
		// a suspected rank, thus avoiding the check for
		// isPossibleBomb() in winFight().  This check
		// is also coded in EVEN.
		//
		// TBD: Currently the attacker survives but perhaps
		// it shouldn't.

					if (isPossibleBomb(tp)
						&& fprank != Rank.EIGHT) {

						assert tp.isSuspectedRank() : "AI " + fprank + " does not WIN against " + tprank + " unsuspected possible bomb";
						vm += tpvalue / 2 - fpvalue;
					} else {
                        vm -= stealthValue(fp);

                        if (fprank != Rank.ONE
                            && !tp.isFleeing(fprank))
                            vm -= riskOfLoss(fp, tp);


		// If unknown fp was protecting a known piece that tp won
		// on the prior move, the AI does not receive the value
		// of the won piece, but instead receives back the known
		// piece value and loses the stealth of the protector.
		// In other words, the value of the board is reset to
		// the prior move minus the stealth of the protector.
		//
		// This discourages the AI from protecting the piece,
		// because when a known attacked piece ceases to move, the
		// opponent will guess that the protector is a lower ranked
		// piece, thus losing its stealth without any gain.
		//
		// Often it is better for the attacked piece to flee.  Only
		// when the fleeing piece is cornered should it stop
		// at the protector if the value of the known piece is worth
		// more than the stealth of the protector.  Thus, the
		// opponent has to guess whether the cornered piece
		// is protected or not.
		//
		// Note that there are three ways for the position to
		// evolve to activate this code:
		//   1. the attacker approaches and the known
		//	piece does not move
		//   2. the known piece approaches the
		// 	attacker
		//   3. the protector approaches the known piece
		//
		// Note that after this position is reached and a move
		// is played on the board (rather than during tree search),
		// the protector will obtain an indirect chase rank.
		// So if the protector already has an indirect chase rank,
		// (i.e. the opponent should already have guessed its
		// rank), then it is awarded full value.
		//
		// Note if an unknown piece defended by another unknown
		// piece approaches the attacker,
		// the attacker cannot be sure which unknown piece
		// is stronger.  For example,
		// RS -- B1
		// -- R8 --
		// Red Spy and Red Eight are unknown.  Red Eight
		// moves up to approach Blue One.  If 1x8?, then Red is
		// awarded the full value of RSxB1, because Blue
		// cannot be sure which piece is Red Spy.
		//
		// Conversely, if Blue One moves down and
		// approaches unknown Red Eight, Red Spy must
		// not move towards Red Eight to protect it
		// (Red Eight earns a flee rank of One because it
		// ceased to attack).
		// 
		// So the AI only receives the value for the win
		// only if the lost piece was itself an effective bluffer.

					UndoMove m2 = getLastMove(2);
					if (depth != 0
						&& !fp.isKnown()
						&& fp.getActingRankChase() != tprank
						&& m2 != UndoMove.NullMove
						&& m2.tpcopy != null
						&& m2.tpcopy.isKnown()
						&& !isEffectiveBluffWins(m2.tpcopy, tp, m2.getMove())
						&& tp == m2.getPiece()) {
						vm += Math.min(tpvalue, m2.value - value);
					} else
						vm += tpvalue + tp.targetValue;

		// Because of the loss of stealth, an AI win is often
		// negative.  Yet the opponent does not know this,
		// so the value must at least be the bluffing value.

                        if (depth != 0
                            && isEffectiveBluffWins(fp, tp, m))
                            vm = Math.max(vm, VALUE_BLUFF);

                        fp.makeKnown();
                    }
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
		// R? R? R? R?
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

		// Note: The factor has to be more than 50% because
		// near the end game when the AI is looking for a flag
		// on the back row, the flag is worth the lowest rank.
		// So if this rank is a Four (100), the AI will risk its
		// Three (200).

		// If a suspected opponent piece *could* be an Eight,
		// the assumption is that might *not* be an Eight.
		// winFight() returns WINS if the piece could be an Eight,
		// but here the piece loses some percentage of its value.
		// This is a risky assumption, but necessary to
		// prevent the AI from leaving moved pieces hanging
		// if it assumes that the suspected piece could be
		// an Eight and capture the Spy or Flag bombs.  Thus
		// a bluffing Eight is very dangerous, because it
		// may be allowed to approach the AI flag bombs with
		// impunity, if it acquires a low suspected rank,
		// because these ranks are generally not Eights.
		//
		// This behavior was deliberately added
		// in version 9.6.  The AI needs to abide by its
		// assignment of suspected rank and must not allow
		// the opponent to bluff the AI into thinking otherwise,
		// even at the cost of its flag.  AI flag defense must
		// be improved in other ways, and the assignment of
		// suspected rank must be improved.

				else {	// fp is bottomcolor

		// could be bomb

					if (isPossibleBomb(tp)
						&& !tp.isKnown()
						&& (fp.isKnown() || fp.isSuspectedRank())
						&& fprank != Rank.EIGHT
						&& !wasChased) {

		// If risk is low, an AI piece is usually safe from attack.

		// But if the attacker is pinned (facing a sure loss),
		// it will likely attack. 
		// For example,
		// R3 B2 --
		// -- R1 --
		// Red Three is unknown and unmoved.  Blue Two cannot
		// go right because of Two Squares.  It is forced to
		// take Red Three.

		// If the opponent is losing, perhaps the superior
		// piece WILL attack the AI unknown pieces.
		// Perhaps the superior piece is an expendable
		// but invincible piece because all of the lower ranks
		// are known. And some opponents make an offensive strategy
		// of attacking unmoved pieces with superior pieces.

		// So vm must always be positive.  But not too positive,
		// because if the AI moves its piece, it becomes more
		// vulnerable, especially because the opponent may
		// guess that it is weak. Thus it is usually best
		// to keep unknown unmoved pieces unmoved,
		// even when they are susceptible to attack.

						int risk = apparentRisk(fp, fprank, unknownScoutFarMove, tp);
						vm -= fpvalue * (100 - risk) / 100;
						vm += tpvalue * risk / 100;

		  				vm = vm / distanceFactor(tp, fp, scoutFarMove);
						vm = makePositive(vm, values[Settings.topColor][unknownRank[Settings.topColor]]/2);

						if (risk == 10) {

		// Because a positive value is returned, if a
		// a known superior attacker is near a slew of
		// unmoved pieces, qs() would accumulate greatly and so the
		// AI would assume that the opponent would attack
		// the slew of unmoved pieces,
		// and the AI would leave moved pieces hanging as well.

		// To address the slew problem (and speed qs()),
		// if the attacker is a superior piece,
		// the AI piece morphs into a bomb and the attacker
		// disappears.  The attacker must disappear
		// to prevent qs() from accumulating points when
		// the attacker attacks an unmoved piece and the adjacent
		// pieces have moved.

		// (If the attacker is a high risk, then qs()
		// does evaluate the slew.)

							morph(tp);
							break;
						}

					} else { // not a possible bomb

        // If the AI piece is a ghost piece,
        // AI thinks this piece has a chance of surviving, which also
        // means that if the target is guarded, it could also lose twice:
        // once when it attacked an unknown and then again when
        // a guard WINS it.
        //
        // For example,
        // | R2 B4 -- 
        // | B5 r? r?
        // ----------
        // B5xr? is negative, but it has a chance of survival.
        // B4xB5 gains the entire Five value.  In this case,
        // B5xr? should be the correct move, so the end result
        // must be better than losing its piece.  Indeed, the unknown
        // Red could be a Bomb, or a Five or superior piece,
        // thus AI loses its piece and B4xB5 is zilch.

                        if (fp == ghostPiece
                            && priorMoveValue() < 0)
                            vm = vm;

                // If the attacker fled from this rank before,
                // it means that the opponent thinks that
                // fp X tp is positive for the AI even though fp WINS.
                // (See Deciphering isFleeing() in winFight())

                        else if (fp.isFleeing(tprank)) {
                            vm -= stealthValue(fp)/2;

                        } else {
                // opponent gains AI piece value
                            vm += apparentWinValue(fp,
                                fprank,
                                unknownScoutFarMove,
                                tp,
                                tpvalue + tp.targetValue);
                            vm = vm / distanceFactor(tp, fp, scoutFarMove);

                // AI gains opponent stealth or riskOfWin
                            if (fp.isSuspectedRank()
                                && !fp.isKnown())
                                vm -= Math.max(stealthValue(fp), riskOfWin(tpvalue, tp, fp, false));

		// Chase bluffs. Allow an expendable unknown AI piece to 
		// chase a low ranked opponent piece.  For example, unknown
		// Red Seven approaches Blue Two.  B2xB7? is a WIN, but 
		// valueBluff() makes it a small loss.

                            if (depth != 0
                                && !maybeIsInvincible(fp)
                                && isEffectiveBluffWins(tp, fp, m)) {
                                vm = Math.min(vm, valueBluffWins(fp, tp));
                                morph(tp);
                                break;
                            }
                        }
					}

					makeWinner(fp, tprank, false);
				}

		// Capture of the flag is the rare case when it is safe
		// to reward earlier capture, because unwanted transpositions are
		// avoided because the search ends (see endOfSearch()).

				if (tprank == Rank.FLAG)
					vm += vm * Math.max(10 - depth, 0)/30;

				fp.setMoved();
				setPiece(fp, to); // won

				break;

			case UNK :
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
		// its actual piece value but gains the
		// the stealth value of the piece that attacks it
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

		// When the AI piece is an unknown defender,
		// apparent piece value is the apparent win value.
		//

					assert tprank == Rank.UNKNOWN: "Known ranks are handled in WINS/LOSES/EVEN";


		// In an unknown attack, the AI usually loses its piece
		// but gains the stealth value of the opposing piece.
		// But if there are not any opposing pieces of lower rank,
		// the AI piece must be invincible, so the outcome
		// is UNK only if it attacks an unmoved piece
		// which could be a bomb (see winFight()).
		//
		// As defined, an invincible piece WINS against any moved
		// opponent piece.  If the opponent is unmoved, the
		// risk is that it completely loses by hitting a bomb,
		// gaining only in the discovery of a bomb.
		//
		// But by correctly guessing the setup locations of the
		// bombs and marking them suspected, the remaining unknowns
		// must be WINS.  So when an invincible piece attacks
		// an unknown unmoved piece, it gains the value
		// of an unknown and loses only 50% of its value.
		//
		// An important example is when an AI invincible piece gets
		// trapped in a sea of unmoved unknowns.  It is far
		// better rewarded to slam into an unmoved unknown than
		// face (almost) certain capture by the chaser.
		//
		// Should the invincible AI piece survive?  If it survives,
		// then it could chose an unknown not adjacent to
		// a superior opponent piece.  But then it could
		// continue to rack up points until it hits the
		// flag (which has happened in play), not
		// a bad thing if all the unknown pieces were actually
		// not bombs, but the possibility is high.  Survival also
		// slows qs().  It is best for an invincible piece
		// not to waste the time evaluating unmoved pieces
		// and avoid them altogether unless chased.

                    if (fprank.ordinal() <= lowestUnknownNotSuspectedRank) {
                        assert !tp.hasMoved() : "AI " + isInvincible(fp) + " " + fprank + " WINS or is EVEN " + " against " + tprank + " " + lowestUnknownNotSuspectedRank + " (see winFight())";
                        vm += Math.min(-fpvalue/4, values[1-fpcolor][unknownRank[1-fpcolor]] - fpvalue/2);

        // Although the AI has tried to guess the position of
        // the bombs, an unmoved unknown piece can still be a bomb.
        // So we know that either the invincible AI piece
        // will survive or the opponent piece is a bomb.
        // For example,
        // R9 -- xx
        // -- -- xx
        // b? R5 --
        // -- -- --
        // -- -- B1
        // bf -- --
        // Invincible Red Five cannot attack Blue Flag because
        // it is defended by Blue One.  So it attacks unknown unmoved
        // Blue.  If the pieces disappear from the board,
        // Red would think it would win on the next move
        // with R9xbf.  So an attacked unknown unmoved piece
        // becomes a bomb.
                        tp.setRank(Rank.BOMB);
                        tp.makeKnown();
                        setPiece(tp, to);
                        break;
                    }

                    if (tp.hasMoved()
                        || fprank == Rank.EIGHT) {
                        if (tp.isFleeing(fprank))
                            fpvalue = stealthValue(fp);
                        else if (fprank != Rank.EIGHT) {
                            if (fprank.ordinal() <= 5
                                && tp.isWeak())
                                fpvalue /= 2;
                            if (isForayLane(fpcolor, to) && hasLowValue(fp))
                                fpvalue /= 4;
                        }
                        vm += unknownValue(fpvalue, fp, tp) - fpvalue;
                    } else {

		// Prior to version 9.7, if the AI attacked an unknown
		// unmoved piece, the tpvalue was reduced
		// by the number of pieces remaining, because it was thought
		// that the likelyhood that the piece is a bomb increases
		// as the pieces become fewer.  This fixed the bug
		// where lowestUnknownExpendableRank is the Spy (the
		// last unknown piece on the board), so the value of
		// a moved Unknown would be the Spy.  This caused the AI
		// to slam into unknown pieces thinking that they were
		// the spy, but were just bombs.
		//
		// However, this violated the rule that UNK must return
		// a more favorable value than LOSES.  Thus if an AI
		// piece was forced between a sure loss and slamming into
		// an unknown piece, it chose the sure loss.
		//
		// Furthermore, it is not true that the risk of attacking
		// an unknown unmoved piece increases as the number of pieces
		// are removed from the board, because as the game progresses,
		// the AI identifies the suspected rank of bomb pieces, so
		// the risk actually remains constant.
		//
		// So the aforementioned bug must be fixed in a different
		// manner.
		//
		// But what value should the AI receive for attacking
        // an unmoved piece?
		// At minimum, the AI should expect to get only the minimum
		// piece stealth value and lose its piece.

		// The problem is a guessing game.
		// Perhaps the unknown piece is not a bomb.
		// Perhaps the AI has correctly guessed all the bomb locations,
		// so the remaining unmoved pieces are fair game?

                            if (isForay(to) && hasLowValue(fp))
                                fpvalue /= 4;

                            vm += riskOfWin(fpvalue, fp, tp, true) - fpvalue;
                    }

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

		// If the AI piece does not survive, it is prone
		// to the following blunder.  In the position below,
		// Red Four may attack the unknown piece because
		// if Red Four does not survive, then the ai
		// cannot see the easy recapture.
		// R4
		// B?
		// B2

		// A more complex example is
		// R4 -- --
		// B? -- --
		// B2 B4 R3
		// Now R4xB? is not a blunder, because after B2xR4
		// Red plays R3xB4.

		// Thus, the issue cannot be handled by simply checking
		// all adjacent squares to the "to" square
		// for known enemy pieces and adjusting vm appropriately.

        // After realizing that qs also needs to check
        // for the ghost square, Version 12 
        // leaves the ghost piece on the ghost square for
        // 1 ply, allowing it to be attacked on the very next
        // move, but then disappearing before ply 2, so that
        // the tree search cannot move the ghost piece.

        // Some AI pieces have little chance of winning.
        // But even weak pieces have some chance of winning,
        // so they should prefer attacking an unguarded piece
        // (especially if the guard is known) to a guarded piece,
        // although this may discourage them from attacking
        // meaty targets if they are guarded, due to the ghostpiece
        // logic.  The other issue is that the AI could be counting
        // on the opponent piece to disappear from the board
        // to stymie an attack on some other valuable piece.

                    if (fprank.ordinal() >= 9
                        || (fprank.ordinal() > lowestUnknownExpendableRank
                            && grid.defenderCount(1 - fpcolor, to) == 0)) {

                        makeWinner(tp, fprank, true);
                        if (tp.getRank() != fprank) {	// could be even
                                setPiece(tp, to);
                        }
                    } else {
                        assert (fpcolor == 1- bturn);
                        fp.makeKnown();
                        setPiece(fp, to);
                        ghostPiece = getPiece(to);
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
		// should not be worse. 
		// WINS/LOSES makes the unknown Blue attacker a Two
		// after the attack, and so WINS/LOSES returns a
		// not so negative result because of the stealth
		// value of a Two.  So unknownValue() must always
		// a more favorable result.
		//
		// To this end, unknownValue() returns the stealth
		// value of a piece two ranks lower, if one exists.
		// This still is not very much advantage (<20 points).
		// TBD: if there is only one unknown lower rank this
		// would be zero points, but this is not coded.

		// Note that an opponent piece cannot be protected by
		// a single unknown piece because the protector always gains
		// a chase rank and therefore has a suspected rank.

		// But if the piece is protected by two unknowns, neither
		// unknown gains a suspected rank.  So unknownValue() must
		// never return a result that would cause the AI to take
		// a piece protected by two unknowns.

		// TBD:  To correctly calculate the unknown value,
		// the AI would have to predict
		// all the ranks of the remaining unknown pieces.
		// All remaining pieces should not be considered equal.
		// Indeed, many of the remaining pieces are bombs and
		// the AI has little to fear from them.

		// So this is often where the AI errs.
		// xx R5 -- xx xx -- --
		// xx -- R4 xx xx -- --
		// -- B? -- R3 -- -- B?
		// -- -- -- -- -- B? --
		// -- -- B? B? B? B? B?
		// Left-most unknown Blue moves towards Red Three.
		// Red sees that even if it moves the Three to the right, an
		// unknown Blue piece can force it to be attacked.
		// Thus, Red allows its Three to be captured.
		// Even worse, Red may leave both its Three and Four unmoved,
		// because it assumes that unknown Blue will take
		// Red Three.

		// If Red Three becomes invincible when unknown
		// Blue approaches (Two is gone or known), then Red Three
		// can freely flee.

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
		// Note: If a piece approaches a known or unknown AI piece,
		// then it usually has a suspected rank, and is not handled
		// here.  But if a known or unknown AI piece approaches
		// an opponent unknown, then the opponent attack is
		// handled by the formula below because tp.moves != 0.
		// This is also heavily used when the AI evaluates
		// the risk of an unknown opponent piece approaching
		// its pieces.

					assert fprank == Rank.UNKNOWN: "Known ranks are handled in WINS/LOSES/EVEN";
                    tpvalue = apparentWinValue(fp, getChaseRank(tprank), false, tp, tpvalue);

		// But note that the resulting value
		// of AI PIECE X OPP PIECE !=
		// OPP PIECE X AI PIECE,
		// because the former uses the
		// actual AI piece value and the
		// latter uses the apparent piece value.

                    if (isForay(to) && hasLowValue(tp))
                        tpvalue /= 4;

                    if (fp.isFleeing(tprank))
                        vm -= unknownValue(tpvalue, tp, fp) + stealthValue(tp);
                    else if (isStrongExpendable(tp) && !fp.isWeak()
                        || tprank.ordinal() <= 5  && fp.isWeak()

		// If fp fled from some unknown AI piece before,
        // will it neglect to attack this unknown AI piece?
		// We don't know for sure, because the opponent may
        // be guessing the ranks of the AI unknowns.
		// But the AI thinks it is a better bet
        // than approaching an opponent piece for the first time.

                        || !tp.isKnown() && fp.isFleeing(Rank.UNKNOWN)) {
                        tpvalue /= 2;
                        fpvalue = unknownValue(tpvalue, tp, fp);

                        vm += tpvalue - fpvalue;
                    }

		// Outcome is the negation as if ai were the attacker.
		// Note: prior to version 9.8, the number of
		// adjacent unknowns were counted and used to decrease
		// fpvalue to encourage the piece to pass the fewest
		// number of unknowns.  But actually, passing multiple
		// unknowns may establish flee ranks, and possibly creates
		// a forking opportunity, so passing multiple unknowns is
		// reasonable aggression.

                    else {
                        fpvalue = unknownValue(tpvalue, tp, fp);
                        vm += tpvalue - fpvalue;
                        vm = vm / distanceFactor(tp, fp, scoutFarMove);

		// vm is usually quite positive.  But in an endgame when
		// the opponent has no expendable pieces, it becomes
		// negative, encouraging AI pieces to approach opponent
		// pieces because the AI believes that the opponent
		// will not risk losing its remaining pieces in unknown
		// encounters.

		// But the AI still should not risk its valuable pieces
		// in this manner.  For example, the last AI Eight should
		// be discouraged from approaching an unknown opponent piece.

                        vm = Math.max(vm, valueBluff(fp, tp));

		// If the AI appears to make an obviously bad move,
		// often it is because it did not guess correctly
		// what happened to the pieces after an unknown attack.
		// Any outcome is possible.
		//
		// However, if the AI piece has moved
		// or if the attacker has an chase rank of Unknown
		// (proving that it is hellbent on attacking),
		// assume worst case for AI: AI loses (or is even)
		// Otherwise the AI guesses that the defender
		// will remain and the attacker loses its piece.
		// This closely matches what the attacker is likely
		// thinking, because the unmoved piece could be a bomb.
                    }

                    if (!isPossibleBomb(tp) || fp.isChasing(Rank.UNKNOWN)) {
                        makeWinner(fp, tprank, false);
                        if (fp.getRank() != tprank) {
                            fp.setMoved();
                            setPiece(fp, to); // won
                        }
                    } else {
                        tp.makeKnown();
                        setPiece(tp, to);
                        }
                    }
				break;
			} // switch

		assert !(fp.isKnown() && fp.getRank() == Rank.UNKNOWN
			|| tp.isKnown() && tp.getRank() == Rank.UNKNOWN)
			: "Exit known:" + fp.isKnown() + " " + fp.getRank() + "X known:" + tp.isKnown() + tp.getRank();

		// Prefer early attacks
		//
		// Consider the following example:
		// RB -- RB -- --
		// -- R5 -- R3 --
		// -- B2 -- -- --
		//
		// Blue Two has moved towards Red Five.
		// Red Five can move back, allowing Blue Two
		// to trap it.  Red Five can move left, but
		// the Two Squares rule will eventually push
		// it right next to Red Three (in 8 ply), allowing Blue Two
		// to fork Red Five and Blue Three.
		//
		// So Red knows that it can lose its Five.
		// It this situation, Red should play out the sequence
		// and hope that Blue Two misplays.
		//
		// This particularly affects deep chase, where
		// the depth is very deep.  Few opponents will see
		// this deep.  Often the AI will leave material
		// hanging because of a potential deep threat
		// involving unknown pieces that do not have the
		// ranks that the AI is worried about.
		//
		// This is important in tournament play with substandard
		// bots.  Stratego is a game of logic, but chance plays
		// an important part.  By forcing the opponent to play
		// out a losing sequence, the AI can win more games.
		//
		// Winning sequences are also important, because
		// of the limited search depth.  If the AI delays a
		// capture, it is possible that the piece can be rescued
		// or the AI may need to reliquish its capture.
		// For example,
		// ----------------------
		// | -- RF -- RB B4 RB --
		// | -- -- -- R1 -- -- --
		// | -- -- -- -- -- -- --
		// | -- -- -- -- -- -- R?
		// | -- -- xx xx -- -- xx
		// | -- -- xx xx -- -- xx
		// | -- B? -- -- -- -- --
		// Red has the move.
		// Red One knows it has Blue Four trapped.  The search
		// tree rewards Red for the piece because all lines
		// of play allow Red One to capture Blue Four.  So
		// moving unknown Red has the same value as approaching
		// Blue Four.  But if 10 ply were considered, Red
		// would realize that Red One is needed to protect Red Flag.
		// If the search is not this deep, then Red play some
		// other move, allowing Blue to force the AI to relinquish
		// its capture of Blue Four.
		//
		// Consider the following example:
		// | -- -- -- -- -- -- --
		// | BB R3 -- -- -- -- B1
		// | B4 -- BB -- -- -- --
		// -----------------------
		// Red Three must move to attack Blue Four now because
		// if it plays some other move, it cannot win Blue Four
		// without losing its Three to Blue One in 12 ply.
		//
		// These examples have occurred in play.
		//
		// Up to Version 10.4, the AI would add a credit for
		// positive values and debit negative values the earlier
		// they occurred in the search tree.
		//
		// This credit/debit failed to work properly, because
		// the transposition table makes all positions equivalent,
		// no matter when the moves occur.   The only way to make these positions
		// not equivalent is to change the hash.
		//
		// This is done correctly in the bluffing code,
		// by changing which pieces survive during an attack,
		// so the bluffing code can condition based on depth.
		//
		// But if the hash doesn't change, then it is pointless to
		// bonus or credit the move based on depth or other factors.
		//
		// For example, OPP X unknown AI piece is not the same
		// as unknown AI piece X OPP.   This is a bug if the pieces
		// retained on the board after the attack do not differ.
		// So when the AI piece is an unknown One,
		// the AI piece can be retained in both cases, leading to
		// a cache transparency bug.
		//
		// TBD: This problem will need to be addressed, perhaps by
		// adding or reusing a piece flag that is used by the hash.
		//
		// Version 11 credits positive earlier attacks
		// and modifies the hash for all attacks.
		//
		// Modifying the hash was necessary to enable this
		// credit and because distanceFactor()
		// reduces risk for attacks based on distance.
		// For example:
		// xxxxxxxx
		// -- -- --
		// -- -- -- 
		// -- R? --
		// R1 B9 --
		// Unknown Red One could be attacked by Blue Nine
		// and lose its stealth.  distanceFactor() is 1.
		// But if Red One moves away and Blue Nine chases
		// during the tree search, distanceFactor increases
		// because the probability of attack decreases
		// (will Blue Nine continually chase unknown Red
		// or will it attack some other unknown?).
		//
		// Note: it is impossible to determine what moves
		// the opponent actually considers, so the AI cannot
		// second-guess the opponent and play a suboptimal move,
		// hoping the opponent will miss a good move.

			vm += vm / ( 10 * (1 + depth / 2));

		// prevent transposition table equivalency.
			hashDepth(depth);

		} // else attack

		if (fpcolor == Settings.topColor)
			value += vm;
		else
			value -= vm;

	}

	// The AI always assumes that it loses in an unknown encounter.
	// It receives only the stealth value of one rank lower plus
	// a nominal value based on rank so that this function always
	// returns slightly more than LOSES.

	// Odds improve for higher ranked pieces
	// once *all* the opponents lower ranked
	// expendable pieces become known.  Then the AI knows
	// that it will gain more stealth value because
	// the remaining pieces must be lower ranked.

	public int unknownValue(int fpvalue, Piece fp, Piece tp)
	{
		assert tp.getRank() == Rank.UNKNOWN : "target piece is known? (" + tp.getRank() + ")";
		assert lowestUnknownExpendableRank != 0 : "unknownValue: unknown rank should be known.";

		int tpvalue = 0;
		Rank aiRank = fp.getRank();
		int fprank = aiRank.ordinal();

		// Once all the expendable ranks are gone,
		// the AI can expect to receive more stealth value
		// in an unknown exchange.

		Rank oppRank = getChaseRank(aiRank);
		assert oppRank != Rank.UNKNOWN : aiRank + "(" + isInvincible(fp) + ") should be invincible against UNKNOWN (" + tp.hasMoved() + ")";

		int r = oppRank.ordinal();

		if (r == 0)
			tpvalue = stealthValue(tp.getColor(), 1);
		else if (lowestUnknownExpendableRank < 5)
			tpvalue = stealthValue(tp.getColor(), 
				Math.min(r, lowestUnknownExpendableRank));

		// As the opponent uses up its expendable ranks,
		// it becomes more likely that the unknown piece
		// has higher stealth value.  If all the
		// opponent Fives are gone, then an AI Six is no
		// different from an AI Five, and hence should receive
		// the same stealth value as an AI Five.

		else if (fprank >= 5 && lowestUnknownExpendableRank >= r)
			tpvalue = stealthValue(tp.getColor(), 4);

		else if (fprank >= 6)

		// If a high ranked AI piece (6-9) has a choice of
		// an exchange with a suspected Five and an
                // unknown piece, the AI will prefer the
                // unknown piece, because stealthValue(Unknown) returns
                // the stealth value of a Three, Four or Five.
                // riskOfWin() awards twice the bonus for UNK than LOSES.

			tpvalue = stealthValue(tp);
		else 
			tpvalue = stealthValue(tp.getColor(), r);

		// unknownValue() should be more positive than LOSES.

		// Version 9.7 introduced blufferRisk, which causes
		// the stealth value of suspected pieces to be reduced
		// if the opponent does not bluff, because the AI does
		// not need to attack the suspected pieces to find out
		// their true rank.  So to keep the value of UNK
		// significantly greater
		// than LOSES, version 9.8 adds a value based on AI rank.

                // Version 12 replaced this with riskOfWin(),
                // which is based on AI rank and awards twice the
                // bonus for UNK than for LOSES.

		// Yet the expected gain from an unknown encounter
		// should the AI piece actually win is likely low.
		// So this value cannot be higher than 1/2 the value of
		// the AI piece; otherwise if an opponent piece is guarded
		// by an unknown, the AI will take the opponent piece.
		// This usually happens with 4x5, because a this is
		// 200-100, only 100 points.  So the stealth of a Three (48)
		// plus the value based on rank must be less than 100 points.
		//
		// Note: this value decreases with the value of AI pieces
		// because then then AI loses less in an unknown encounter

		return Math.max(tpvalue, riskOfWin(fpvalue, fp, tp, false));
	}

    int priorMoveValue()
    {
        UndoMove m2 = getLastMove(2);
        return value - m2.value;
    }

    protected void moveHistory(Piece fp, Piece tp, int m)
    {
        if (ghostPiece != null
            && ghostPiece != tp)
        clearPiece(ghostPiece.getIndex());
        ghostPieceStack.add(ghostPiece);
        ghostPiece = null;

        undoList.add(new UndoMove(fp, tp, m, boardHistory[bturn].hash, boardHistory[1-bturn].hash, value));
        bturn = 1 - bturn;
        depth++;
    }

	public void undo()
	{
		depth--;
		bturn = 1 - bturn;

		UndoMove um = undoList.remove(undoList.size()-1);
        Piece tp = null;
		if (um != UndoMove.NullMove) {
            Piece fp = um.getPiece();
            value = um.value;

			// remove piece at target
			grid.clearPiece(um.getTo());

			// place original piece
			fp.copy(um.fpcopy);
			grid.setPiece(um.getFrom(), fp);

			// place target piece
            tp = um.tp;
			if (tp != null) {
				um.tp.copy(um.tpcopy);
				grid.setPiece(um.getTo(), tp);
			}

			boardHistory[bturn].hash = um.hash;
			boardHistory[1-bturn].hash = um.hash2;
		}

        // place ghost piece
        ghostPiece = ghostPieceStack.remove(ghostPieceStack.size()-1);
        if (ghostPiece != null
            && ghostPiece != tp)
            setPiece(ghostPiece, ghostPiece.getIndex());
	}

	public void pushNullMove()
	{
        if (ghostPiece != null)
            clearPiece(ghostPiece.getIndex());
        ghostPieceStack.add(ghostPiece);
        ghostPiece = null;

        undoList.add(UndoMove.NullMove);
        bturn = 1 - bturn;
		depth++;
	}

	public void pushFleeMove(Piece fp)
	{
        if (ghostPiece != null)
            clearPiece(ghostPiece.getIndex());
        ghostPieceStack.add(ghostPiece);
        ghostPiece = null;

		int i = fp.getIndex();
        undoList.add(new UndoMove(fp, null, Move.packMove(i, i), boardHistory[bturn].hash, boardHistory[1-bturn].hash, value));
		grid.clearPiece(i);
		bturn = 1 - bturn;
		depth++;
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

	// Bluffing doesn't work:
	//	- if the AI piece is known
	//	- if the opponent piece is trapped and cannot retreat
    //      (only has one move, which is to attack bluffer,
	//      so it may be forced to call the bluff
    //      if it happens to lack protection)
	//		(TBD: or if the piece is forced towards a dead-end)
	// 	- if the AI piece has fled from this same piece rank before
	//		(or neglected to attack)
	// 	- if the AI piece has fled from some other piece rank before
	//		(or neglected to attack)
	// 		and the value of that piece was much greater
	//		than the stealth value of the AI piece that
	//		could win the target piece of the current bluff.
	//		For example, if a Five passed by an AI piece,
	//		the AI piece can no longer bluff against a
	//		Four, because had the AI piece been an
	//		actual Three, it should have taken the Five.
	//	- if the piece hasn't moved and the flag is at risk
	//	- if the piece is the last movable opponent piece
	//	  	on the board (a bluff cannot remove the piece,
	//		or the AI will think the game has ended when
	//		the opponent has to move and call the bluff)
    //     (note: clearPiece(tp) was called earlier, so
    //     movablePieceCount is tested for 0).

	public boolean isEffectiveBluff(Piece fp, Piece tp)
	{
		assert fp.getColor() == Settings.topColor : "AI only";

		if (fp.isKnown()
            || isExpendable(tp)
			|| fp.isFleeing(tp.getRank())
			|| grid.moveCount(tp.getColor(), tp.getIndex()) == 1
			|| (fp.getActingRankFleeLow() != Rank.NIL
				&& fp.getActingRankFleeLow() != Rank.UNKNOWN
				&& tp.getRank() != Rank.ONE	// Spy flees from any other piece
				&& tp.getRank() != Rank.UNKNOWN
				&& pieceValue(Settings.bottomColor, fp.getActingRankFleeLow()) > stealthValue(Settings.topColor, tp.getRank().ordinal()-1) * 4 / 3)
			|| (!fp.hasMoved()
				&& isFlagBombAtRisk(tp))
			|| grid.movablePieceCount(Settings.bottomColor) == 0)
			return false;

		return true;
	}

	public boolean isEffectiveBombBluff(Piece fp, Piece tp)
	{
        if (isInvincibleDefender(tp)
            || fp.isKnown()
            || hasLowValue(tp)
            || fp.isFleeing(tp.getRank()))
            return false;

		UndoMove prev2 = getLastMove();
		return (prev2 != UndoMove.NullMove
			&& prev2.getPiece() == tp);
    }

	public boolean isEffectiveBluffLoses(Piece fp, Piece tp)
	{
        if (isInvincibleDefender(tp)
            || !isEffectiveBluff(fp, tp))
            return false;

        // Bluffing is effective only if the opponent piece approached
        // on the last move.  If the opponent lingers, then
        // it is obvious that the opponent called the bluff

		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack when isEffectiveBluffLoses() is called)

		UndoMove prev2 = getLastMove(2);
		return (prev2 != UndoMove.NullMove
			&& prev2.getPiece() == tp);
    }

	// It is always risky to bluff by pushing an unknown piece
	// towards an opponent piece of superior rank.
	// It is especially risky to bluff with a piece from a weak area
	// or approach the opponent flag.
	// Thus an effective bluff for WINS must not be a weak piece
        // (unless the opponent is an idiot bot)
	// or near the opponent flag.

	// Yet it is a good bet to make moves that force
	// the opponent to approach the unknown piece, because
	// then the AI may be able to move away and retain its piece.
	// Thus an effective bluff or LOSES and EVEN
	// can with a weak piece or near the flag.

	public boolean isEffectiveBluffWins(Piece fp, Piece tp, int m)
	{
		return isEffectiveBluff(fp, tp)
			&& !(blufferRisk >= 3
                && (fp.isWeak() || isWeakAggressive(fp)))
			&& !isNearOpponentFlag(Move.unpackTo(m));
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
	// On the other hand, a low bluffing value often results in
	// no bluff at all if the opponent has a chase move at its
	// disposal.  For example:
	// -- -- -- -- -- R?
	// R4 -- xx xx B3 --
	// -- -- xx xx -- R?
	// B? -- -- -- -- --
	// The lower unknown Red is a Two.  Red should move the upper
	// unknown left to bluff an attack on Blue Three, which often
	// will move away (and then be captured by unknown Red Two.
	// But if Red only gets a small value for R?xB3, unknown
	// Blue can move upwards towards Red Four.  Then R?xB3 is dwarfed
	// by B?xR4.  So R4 must move away.  After that, the bluff is no
	// longer effective, so B3xR? wins the bluffing piece.
	//
	// To counteract, if the opponent move is a chase move,
	// the bluff must retain its effectiveness.
	// 
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

	protected int valueBluff(int m, Piece fp, Piece tp)
	{
		assert fp.getColor() == Settings.topColor : "valueBluff only for AI";

		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack when isEffectiveBluff() is called)
		UndoMove prev2 = getLastMove(2);
		if (prev2 != UndoMove.NullMove
			&& prev2.tp != null
			&& prev2.getPiece() == tp) {

			UndoMove prev1 = getLastMove(1);
			int v = -(prev1.value - prev2.value);

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
		// are unknown. If the unknown Red piece above Blue One moves
                // down what should Blue do?  Most bots will play B1xR2 because
		// Blue has no move that does not result in a possible
		// loss if the unknown Red piece is actually the Spy.
		// But this is *exactly* what Red is hoping for, because
		// the Spy has been planted in usual fashion next to
		// Red Two.  (Humans would likely call the bluff
		// and play B1xR?, assuming that the Two is protected
		// by the Spy, but this is of course a risk that could
		// lose the game).

        // But if the One is trapped, approaching the One with
        // is a bad bluff (unless the adjacent unknown Red piece
        // IS the Spy), because the One will likely attack.
        // For example:
		// -- -- R? --
		// -- -- -- R?
		// B? B? B1 B? 
		// xx xx xx xx
		// If the unknown Red piece above Blue One moves
        // down what should Blue do?  Most bots will play
        // B1xR?, because then at least Blue One wins a piece before
        // a possible Spy attack.  Note that if the adjacent
        // unknown Red piece is actually the Spy, the loss of
        // the first unknown Red piece will not deter the approach.

        // TBD: Of course, if the opponent was aware that the AI
        // does not bluff in a trapped situation, then Blue One
        // would stand pat, knowing that the approaching piece
        // is not the Spy and the adjacent piece must the Spy.
        // I doubt that anyone will ever realize this.
            
			if (tp.getRank() == Rank.ONE
				&& tp.isKnown()
				&& !tp.isSuspectedRank()
				&& !fp.isKnown()
				&& !prev2.tp.isKnown()
                                && grid.moveCount(tp.getColor(), prev2.getFrom()) == 0)
				return v;

		// If the attacker is suspected, the AI is only guessing
		// that the attacker is stronger.  This is a situation
		// that should be avoided, but the AI awards only a small
		// stipend; otherwise if the situation was highly negative,
		// the AI could lose material in trying to rectify the
		// situation.

			if (tp.isSuspectedRank())
				return v - valueBluff(tp, fp);

		// In other cases the AI receives less than the full
		// value of the bluffing piece.  This discourages the
		// AI from unnecessarily risking its pieces (known or
		// unknown fleers), encouraging it to find authentic protectors
		// for them.  But if the AI piece is truly unknown, the
		// the AI piece did not lose its apparent piece value
		// (see WINS), so prev1.value - prev2.value is small
		// (just the value from valueBluff() below).

		// One can argue that the bluffing should be high.
		// In the example below, Unknown Blue
		// is about to fork two known Red Fives.  Red anticipates the
		// possible fork, but sees that the loss is inevitable.
		// (After Red Five moves up, Blue Four moves up, Red Five moves
		// up, Blue can take either Five, because the Five is only
		// protected by an unknown Four.)  If the bluffing value
		// is low, the AI sees that it will lose a Five anyway
		// and not move to protect them.
		// R? -- R? R?
		// xx R5 -- xx
		// xx -- R5 xx
		// -- B? -- B?

		// Another example suggesting a high bluffing value:
		// B? B? B? B?
		// B? R4 -- B?
		// xx B3 -- xx
		// xx R7 -- xx
		// B? B? -- --
		// Blue Three has forked Red Four and Red Seven.
		// Red has the move.  Will Blue Three
		// actually take Red Four?  Possibly not, but with a low
		// bluffing value, Red Four moves sideways allowing
		// Blue Three to take Red Seven.

		// Yet a high bluffing value causes the AI to
		// sacrifice material to avoid the possibility
		// of capture of a known piece, even when the piece
		// appears to be protected.

			if (fp.isWeak())
				return v / 3;
			else
				return v * 2 / 3;

		} // prev2.tp != null

		return 0;
	}

	// Return the bluffing value for the approach of an
	// unknown AI piece towards a lower ranked opponent piece.
	// For reasons described above, this value must be low
	// (lower than the value of the lowest piece).
	// It should be greater than zero because a bluff can
	// be useful as a deterrent.
	// For example,
	// -- R? R? R? R?
	// B2 -- R6 -- R?
	// -- -- xx xx --
	// If all the unknown Red pieces are high ranked pieces,
	// and bluffing value is zero, R?xB2 (LOSES) is zero, so
	// Blue Two will be undeterred from cornering and
	// winning Red Six.
	//
	// If the bluffing value is non-zero, R?xB2 (LOSES)
	// is a deterrent, and if Red Six moves left,
	// there are four bluffs that Red Two must consider.
	// (In addition, the attack value is diminishes with depth).
	//
	// TBD: In reality, it is very unlikely that Blue Two
	// would chase Red Six past one unknown, let alone four.
	// The issue is how to separate worthwhile bluffs from
	// bluffing just for the sake of bluffing.

	protected int valueBluff(Piece oppPiece, Piece aiPiece)
	{
		assert aiPiece.getColor() == Settings.topColor : "valueBluff only for AI";
		// An important bluff is entrapment of an opponent piece by an unknown AI low
		// ranked piece and an unknown expendable AI piece.
		// For example,
		// | R1 -- R7
		// | -- B2 --
		// |xxxxxxxxxx
		// Red One and Red Seven are unknown, and Blue Two is known.
		//
		// Red One may approach Blue Two, forcing Blue Two to either remain
		// or chose to move towards unknown Red Seven.  R7?xB2 is LOSES but
		// returns "valueBluff".  An approach by Red One is slightly negative
		// because the AI is discouraged from using its low ranked pieces to
		// directly chase, because that is how the opponent can discover
		// its ranks.  So the net value is slightly below "valueBluff".
		//
		// So usually a better move is to approach Blue Two with Red Seven.
		// B2xR7? (WINS) is "-valueBluff".  And if Blue plays some other move,
		// R7?xB2 (LOSES) is "valueBluff".  So the net value is "valueBluff",
		// and therefore the AI should normally use its expendable pieces
		// to push the opponent pieces towards low ranked pieces.
		//
		// TBD: Yet if Red One was known (or Blue Two had no other moves)
		// the approach by unknown Blue Seven
		// would simply lose a Seven, because Blue Two has no other
		// option but to attack Red Seven.
		//
		// TBD: And Blue Two was trapped by unknown two expendable pieces,
		// an approach by either expendable piece would also be counterproductive.

		int valueBluff = values[Settings.topColor][unknownRank[Settings.topColor]]/2;

        // Consider this example:
        // r4 r9 r9
        // rB r7 r6
        // -- -- R5
        // -- rS --
        // xx -- B4
        // xx -- b?
        // -- -- B2
        // bB -- b?
        // -- b? --
        // b? -- --
        // Red Spy is worried about b?xRS because unknown blue may
        // be a Scout (it actually was).   It discards the move
        // right to approach Blue Four, because hasLowValue
        // qualifies isEffectiveBluff.  But the approach would be
        // worth a shot. (It did not go left because of
        // possible two squares, another bug).

		// A suspected Four (that chased a Five) could well be a Five.
		// If so, the piece might attack and the AI would
		// lose its piece.

        // Version 12 allows bluffs on weaker pieces, but
        // returns a positive value, discouraging the AI from doing it.

		if (hasLowValue(oppPiece))
            return valueBluff;

		return -valueBluff;
	}

	// The AI is discouraged from placing an unknown
	// valuable piece at risk by approaching a stronger
	// opponent piece
	// (unless some further goal is highly positive, such as a win
	// of a valuable piece, the flag, or the protection
	// of its flag.)
	//
	// Yet in the search tree, the unknown AI piece has
	// deterrent value, per the usual assumption
	// that a valuable opponent piece
	// will not risk approaching an unknown piece.
	//
	// When an opponent piece WINS, it calls valueBluffWins
	// and the result is positive (but not as positive as winning
	// a known piece).  If the AI piece LOSES, it calls
	// valueBluff() directly, and the result is the negative
	// bluffing value.   This deters the opponent piece from
	// approaching any AI unknown, valuable or not, in the search tree.
        // And it deters a valuable AI piece from approaching
        // the opponent.

	protected int valueBluffWins(Piece oppPiece, Piece aiPiece)
	{
		if (!isExpendable(aiPiece)) {

		// Bluffing with the Spy against the opponent One
		// is encouraged.  Because the One is invincible,
		// the AI does this only when it has an unknown protector.
		// So the opponent One can become confused about which
		// of the unknown AI pieces is actually the Spy.

			Rank oppRank = oppPiece.getRank();
			Rank aiRank = aiPiece.getRank();
			if (aiRank == Rank.SPY && oppRank == Rank.ONE)
				return -VALUE_BLUFF;

			int valueBluff = values[Settings.topColor][unknownRank[Settings.topColor]]/2;
			return valueBluff;
		}

		return valueBluff(oppPiece, aiPiece);
	}

    // The AI relies too much on bluffing with any handy piece.
    // While this works against feeble-minded bots, it might
    // not work against aggressive bots or humans.
    // For example,
    // | r6 r4 rb r8
    // | -- R2 -- r9
    // | B1 -- xx xx
    // Blue One moves right to attack Red Two.  Red Two should
    // move to the left to nullify the attack by the Two Squares rule.
    // But instead the AI moves right.  Why?   It thinks that
    // Blue One will not move up to attack Red Two because r4xB1
    // is an effective bluff because Red Spy is still unknown.
    // But Blue doesn't care and wins Red Two.

    // The following check deters bluffing if the opponent can
    // immediately attack another AI piece.
	protected int valueBluffLoses(Piece oppPiece, Piece aiPiece)
	{
        if (grid.hasAttack(oppPiece)) {
			int valueBluff = values[Settings.topColor][unknownRank[Settings.topColor]]/2;
			return valueBluff;
        }
		return valueBluff(oppPiece, aiPiece);
    }

	public int getValue()
	{
		return value;
	}

	public void setValue(int v)
	{
		value = v;
	}

	private int stealthValue(int c, int r)
	{
		return valueStealth[c][r-1];
	}

	private int stealthValue(int c, Rank rank)
	{
		return stealthValue(c, rank.ordinal());
	}

	private int stealthValue(Piece p)
	{
		if (p.isKnown())
			return 0;

		Rank rank = p.getRank();

        if (p.getColor() == Settings.topColor) {

            int actualStealth = stealthValue(Settings.topColor, rank);

            if (isWeakAggressive(p)
                || p.isWeak()
                || rank != Rank.NINE)
                return actualStealth;

        // A human opponent (or advanced bot such as the AI)
        // will notice that the AI shelters its unknown
        // superior ranks from Scout attacks and unknowns.
        // Thus for effective bluffing,
        // inferior pieces need to behave similarly.
        // Expendable AI pieces that have not chased unknowns or
        // allowed themselves to be exposed to scout attacks
        // should continue to eschew them.  The AI should check
        // for Scout Far moves and avoid them.  See isNineTarget().

        // However, it is nearly impossible to shelter all pieces
        // from possible attack, so the AI chooses to shelter just
        // its Scouts, because these are so weak that they are lost
        // in any attack.  Because of their higher stealth,
        // these inferior pieces might only attack a suspected One or Two,
        // but not a Three, whereas a known or weak piece would.

            return Math.max(actualStealth, pieceValue(1-p.getColor(), 9));
        }

		// Unknowns that chase unknowns have low stealth.

        if (rank == Rank.UNKNOWN) {

            int v = stealthValue(p.getColor(), Rank.THREE);

            if (isWeakAggressive(p)
                || p.isWeak())
                v /= 2;

		// If an unknown piece flees from an opponent unknown piece,
		// it is an indication that the opponent piece
		// is either a high ranked piece (7-9, Spy) avoiding capture
        // or an unknown low ranked piece trying to maintain stealth.
        // Although the high ranked piece would not have very much stealth,
        // this is more information than a completely unknown piece,
        // making it a more desirable target

            if (rank == Rank.UNKNOWN) {
                if ((p.getActingRankFleeLow() == Rank.UNKNOWN
                    && !isPossibleBomb(p))
                    || p.isLikelySpy()) {
                    return Math.min(v/2 + v/2 * p.getMoves() / 25, v);
                } else
                    return v/3;
            }
        }

        return stealthValue(p.getColor(), rank);
	}

	// If the opponent has an invincible win rank,
	// then it is impossible to defend the flag,
	// so do not try, because it just leads to successive piece loss
	private void makeFlagKnown(Piece pflag)
	{
		int c = pflag.getColor();
		if (c == Settings.bottomColor
			|| invincibleWinRank[1-c] >= invincibleWinRank[c]) {
			pflag.makeKnown();
		}
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
	// A flag bomb must always be worth more than an unknown Miner,
	// so that the Miner can sacrifice itself against a defended
	// bomb structure.
	//
	// But the value of an Miner fluctuates depending on the
	// the number of bomb structures remaining (expendable eights).
	// So if the opponent has expendable eights, is the flag
	// bomb worth less?
	//
	// The question comes down to how much the player is willing
	// to spend for defending a flag bomb structure.  This is
	// a difficult question to answer outright, because it depends
	// on the position.  And the player should not defend
	// the flag bomb structure any more actively than another
	// structure; otherwise, the opponent will readily guess
	// the real flag structure.
	//
	// A worthwhile bomb of "color" is worth the value
	// of the AI Miner, its stealth value, and more.
	// This makes attacking or defending a worthwhile bomb always
        // worth sacrificing an AI eight if necessary.  Thus, even
        // if the opponent has expendable Miners, the AI will still
        // sacrifice its miner to protect its bomb structure.
	//
	// (But a flag bomb should not be too valuable; otherwise
	// the opponent will be able to "sense" the flag
	// bomb location by the AI's reaction.)

	// Note that a known miner taking a worthless bomb,
	// the value is zero, but an unknown miner,
	// the value is -stealthvalue.  This deters an
	// unknown miner from taking a worthless bomb.
	// This is calculated in WINS.

	// A small bonus is added if the bomb is surrounded
	// by other pieces.   The idea is to attack bombs that
	// could be part of multiple structures.   For example,
	// B F B -	
	// 7 B - -
	// B - - -
	// Thus the middle bomb is more valuable than either of the end bombs.
	// TBD:  More suspected rank analysis is needed to
	// determine if approaching pieces are Eights.  Most
	// opponents will send expendable pieces or Eights
	// to areas with a suspected bomb structure, rather than
	// low ranked pieces.  The AI needs to examine
	// its known pieces, and track the movement of approaching
	// pieces.  If an approacher is headed towards a Bomb,
	// it is probably an Eight.

	private int aiBombValue(Piece p)
	{
            int color = p.getColor();
            int value = pieceValue(Settings.topColor, 8) + grid.defenderCount(color, p.getIndex())*VALUE_MOVED;

        // If the AI flag is known, the AI is willing to sacrifice more
        // to defend an intact flag structure.
        // This is significant when the opponent still has an unknown
        // rank less than the defender.

            if (color == Settings.topColor
                && getPiece(flag[color]).isKnown())
                return Math.max(value, isWinning(color)*3/2);
       
            return value; 
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
		int v;

	// If the flag is known
	// (because it is the last piece on the board or is it heavily
	// suspected because it is in the only bomb structure)
	// it has a high value and must be protected or
	// attacked at all costs.
	// (But this value must be less than alpha/beta).

		if (pflag.isKnown()
			&& (color == Settings.topColor
				|| unknownBombs[Settings.bottomColor] == 0))
			v = VALUE_ONE * 2;
		else {

	// Set the flag value higher than a worthwhile bomb
	// so that an unknown miner that removes a bomb
	// to get at the flag
	// will attack the flag rather than another bomb.

		v = aiBombValue(pflag);

	// Flag value needs to be higher than twice the lowest value piece
	// and the highest expendable piece value,
	// so that these piece(s) will attack a possible flag.
	// An attack on a possible flag by a piece other than a Miner
        // is WINS, but because the target
	// is unmoved and suspected, the attacker loses its complete value
        // and gains only half the value of the flag.
	// So the only pieces that will attack a suspected flag are
	// Eights and those with lessor value.
	//
	// For example, if R2 and R6 are on the board, Blue Flag value is
	// slightly more than R6.  This allows R6 to attack the Blue Flag.
	// But if R1 and R8 are on the board,
	// Blue Flag value is more than the R8.
	// R8 will attack the Flag and not R1, because we do not want
	// R1 to attack the Flag, which would lose if it turned out to be
	// a Bomb.

		v = Math.max(v, (minPieceValue(1-color) + VALUE_NINE)*2);
		v = Math.max(v, (maxExpendableValue(1-color) + VALUE_NINE)*2);
		}

		values[color][Rank.FLAG.ordinal()] = v;
	}

	// risk of attack (0 is none,  100 is certain)
	int apparentRisk(Piece fp, Rank fprank, boolean unknownScoutFarMove, Piece tp)
	{
		assert !tp.isKnown() : "tp " + tp.getRank() + " should be unknown";

		// Risk of an unknown scout attack increases if
        // the target has chased superior pieces, if the opponent
        // has been paying attention.  Most bots carelessly
        // fire off their Scouts quickly, so risk
        // decreases with the number of moves that the piece makes.

		// An important case is an attack on the Spy, because
		// 9xS wins the Spy (value between a Three and Four).
		// Thus the AI will sacrifice a Four to save the Spy
		// if the risk is 66% or greater.  Even if there is
		// only one opponent Scout still remaining, there is still
		// sustantial risk to the Spy (and the undiscovered One).

		if (unknownScoutFarMove) {
            int risk = Math.max(10, 80 - fp.getMoves() * 3);
			risk = Math.max( risk, (9 - tp.getActingRankChase().ordinal()*10));

			return risk;
		}
		// High ranking pieces

		int r = fprank.ordinal();
		if (fp.isSuspectedRank())
			r += blufferRisk - 2;

		// If tp is not known and has not moved,
		// it could be a bomb which can deter valuable
		// pieces from attacking an unknown unmoved piece.
		// 

		if (isPossibleBomb(tp)
			&& fp.isKnown()
			&& fprank != Rank.EIGHT
			&& (r <= 4 
				|| isInvincible(fp)))
			return 10; // 10% chance of attack

		// if the attacker is invincible, attack is almost certain

		if (isInvincible(fp)) {
			if (fp.isKnown())
				return 90;
			else
				return 50;
		}

		// An attack on a fleeing piece is almost certain.
		// (But you never know, maybe the opponent forgot).
		if (tp.isFleeing(fprank))
			return 90;	// 90% chance of attack

		if (r <= 4)
			return r*10;

		if (fprank == Rank.SPY && rankAtLarge(1-fp.getColor(), Rank.ONE) != 0) {
			// suspected spy probably won't attack
			// unless it really isn't the spy
			return 10;
		}

		// Known Fives are unpredictable.  Suspected Fives
		// even more so (because they could be a 6, 7 or 9).
		if (r == 5 && !fp.isSuspectedRank())
			return 70;	// 70% chance of attack

		// The unknown defender has moved or the attacker is
		// an unknown or a high ranking piece that may actually
		// intend on attacking unmoved pieces.
		// This makes it much more likely for an unknown defender
		// to be attacked, and so the risk is much higher.

		return 90;	// 90% chance of attack

	}

	// This program has a big unsolved problem with
	// the horizon effect, particularly when the valuable
	// and vulnerable Spy could be attacked.  The horizon
	// effect is addressed by extended search,
	// but if the attacker is far from the Spy, then
	// it is possible that the attacker barely reaches
	// the Spy and extended search is insufficient
	// in depth to to prevent the AI from leaving pieces hanging.
	//
	// Note: If the opponent piece is unknown,
	// it is handled in UNK, but because move
	// generation of unknown pieces is limited (pruned off)
	// to 1 space of separation in AI.java,
	// the horizon effect is limited as well.
	// Yet it is still important.
	//
	// Note that the closer the opponent moves towards the AI piece
	// during tree evaluation, the danger does not increase,
	// prediction of moves by unknown pieces is hypothetical.
	// But the closer an AI piece is to an opponent piece at the
	// start of tree evaluation, the higher the probability of attack.
	//
	// Prior to Version 10.4, this was handled by using opponent "moves"
	// (the number of times the opponent piece moved during
	// tree evalution), and not the distance between the two pieces.
	// But this was an error because if the opponent piece moved
	// back and forth during tree evaluation, it increased its number
	// of moves and was deemed less of a threat.
	//
	// Version 10.4 used the distance
	// between the current location of the AI piece and the original
	// location of the opponent piece.
	//
    // But this suffered from the problem of AI pieces fleeing from
    // potential attackers, giving the attacker a clear signal that the
    // piece was a meaty target.  For example:
    // |----------
    // | -- b1 bb
    // | -- R5 --
    // Unknown Blue One is unmoved.  R5xb1 is probable (see apparentRisk)
    // Unknown Blue One moves left because the 10.4 algorithm decreases
    // risk based on its distance to the attacker.  Now Blue has moved
    // and has fled from Red Five, so Red Five is certain it is a
    // worthwhile target.  Furthermore, in this case, Blue One is trapped,
    // making the attack inevitable.
    //
    // So Version 12.1 uses the distance between the original location
    // of the opponent piece and the original OR current location
	// of the AI piece, whichever is less. Now an AI piece cannot flee to
    // avoid the danger; it must call on another piece to defend or just
    // hope that it isn't hit.  Nor can it approach another piece and gain
    // its value without suffering full consequences (such as a One deciding
    // to approach a trapped Two from far away, but seeing only a fraction
    // of the possibility of being attacked the the opponent Spy).

	// TBD: This isn't perfect, because
	// it does not take into account the Lakes, so the AI will sense
	// danger across the lakes although none exists.
	//
	// TBD: As search depth is increased, the distance
	// factor should be reduced.  The distance factor
	// causes the AI not to react the more moves that
	// approaching attacker makes during tree evaluation;
	// the idea is that the further the attacker,
	// the more likely it has some other target in mind

	protected int distanceFactor(TestPiece aiPiece, TestPiece oppPiece, boolean scoutFarMove)
	{
		// If the opponent Piece is invincible, then the AI
		// believes the outcome is inevitable.

		// If the AI piece is known and the opponent piece
                // is known or suspected, then the AI believes
		// the outcome is inevitable.

		if (scoutFarMove
			|| isInvincible(oppPiece)
			|| (aiPiece.isKnown()
                && oppPiece.getRank() != Rank.UNKNOWN))
			return 1;

		// But if either piece is unknown, then the outcome
		// is less certain, because the AI piece
        // might not be the intended attack target
		// by the opponent piece.

		int d = Math.min(
            Grid.steps(aiPiece.getIndex(), oppPiece.boardPiece().getIndex()),
            Grid.steps(aiPiece.boardPiece().getIndex(), oppPiece.boardPiece().getIndex())) - 1;

		// double the distance

		return Math.max(1, d+d);
	}

	protected int apparentWinValue(Piece fp, Rank fprank, boolean unknownScoutFarMove, Piece tp, int v)
	{
		assert fp.getColor() == Settings.bottomColor : "apparentWinValue only for opponent attacker";

		// if the target is known, attacker
		// sees the actual value of the piece

		if (tp.isKnown())
			return v;

		// tp is unknown

		// Opponent does not know the actual value of the piece.
		// So what is the chance that the opponent will actually
		// attack?  This risk times the actual value of the
		// piece is the win value.
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
		// But there is still some risk (very low) that Red Three
		// will attack its unknown unmoved Blue Spy.
		//
		// In the following example, AI Blue Spy is unknown,
		// Red Three and Blue Two are known.   Blue Two should not
		// approach Red Three because it will flee into Blue Spy.
		// R3 BS ?B
		// -- ?B
		// B2 
		//
		// TBD: how is this handled?
		//
		// These two examples further suggest that the assignment
		// value is somewhere between actual and apparent value.
		//
		// Now consider the following example.
		// -- R? R? R?
		// -- R? R3 R?
		// R? R2 B7
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
		//
		// The risk of attack on an unknown piece is largely
		// based on the rank of the attacker.  High ranked
		// pieces are much more likely to attack unknown pieces,
		// except perhaps if the opponent is losing, when
		// the opponent may make a last ditch effort to
		// bolster a lost position.
		//
		// But the true risk depends assessing the actual target
		// of the attacker.  Factors that could be useful in
		// determining the most likely target are:
		// 1. distance to the target
		// 2. direction of the attacker
		// 3. multiple targets
		//
		// It is tempting to use depth rather than distance.
		// But that fails because it entices the AI
		// to leave pieces hanging by allowing the attacker to first
		// take the free material and then continue the
		// former attack later (at a higher depth and thus
		// lower value).
		//
		// The other problem with using depth is that the
		// transposition table would have to index by depth, because
		// it would cause the same position to be evaluated
		// differently at different depths.

		int risk = apparentRisk(fp, fprank, unknownScoutFarMove, tp);
		v = v * risk / 100;
		return v;
	}

	// TBD: combine with pieceValue()

	public int actualValue(Piece p)
	{
		// Note: if all the movable pieces have been moved,
		// the rest must be bombs or the flag, so the
		// AI sets the rank of the unmoved pieces to Bomb,
		// and makes them known and suspected.  So suspected
		// Bombs and the Flag have the same value as known ranks.
		//
		// Note that a suspected Spy also is currently assigned
		// a fixed value (i.e. it does not increase in value
		// with aging).  This is because the AI is confident
		// in its assignment of suspected rank to the Spy,
		// because it is unlikely (although possible) that
		// the opponent would bluff with protecting its Two.
		// This makes the discovery of a suspected Spy immediate
		// fodder for chase and attack.

		int color = p.getColor();
		Rank rank = p.getRank();
		if (color == Settings.topColor
			|| (p.isKnown() && !p.isSuspectedRank())
			|| rank == Rank.SPY
			|| rank == Rank.BOMB
			|| rank == Rank.FLAG)
			return pieceValue(p);

		// Note: the value of an unknown is the minimum value
		// of the remaining unknowns.  Hence, if the Spy and One
		// are the last two pieces remaining, it is the value
		// of the Spy.

		if (rank == Rank.UNKNOWN)
			return pieceValue(color, unknownRank[color]);

		// Suspected ranks have much less value than known ranks
		// because of uncertainty.  
		//
		// Note that the value of a suspected rank must always
		// be higher than the minimum rank value.  If the piece
		// is a suspected (but not known) Flag, the AI wants
		// to risk its minimum valued ranks on attacking the Flag
		// (WIN) even if the flag has adjacent pieces that can
		// win the AI piece.
		//
		// Until version 9.6, a losing AI attack on a suspected
		// rank created a known rank.  This caused
		// the AI to make a piece known before attacking it with
		// the lower piece to gain the higher known value rather
		// than the reduced suspected value. This is a waste of
		// material, and occurred often when an Eight was going to
		// take a flag bomb, because by another piece slamming
		// into the flag bomb first, it became more valuable.
		//
		// Yet the value of an opponent piece that successfully
		// attacked an AI piece must be that value.
		// For example,
		// B? R5 R3
		// B?(4)xR5 gains the value of the Five, or 50 points
		// (minus the stealth value of a Four).  R3xB?(4)
		// must be worth 100 points, not the suspected value
		// of 20 points.
		//
		// The dilemma is solved by clearing IS_SUSPECTED only
		// if an AI piece is attacked by an opponent piece
		// but leaving it IS_SUSPECTED when an opponent piece
		// is attacked by an AI piece.
		//
		// Thus it is possible to have a known but suspected
		// opponent piece (but only in the search tree, not
		// on the board).  The known status just means that
		// the stealth value of the opponent piece has already
		// been collected, so if the AI attacks it again,
		// it does not collect it twice.
		//
		// TBD: the confidence in suspected rank increases as
		// expendable pieces are reduced.
		// And in general, suspected rank assignment needs to
		// be improved, and afterwards this code needs to
		// be revisited.

		// vs - suspected piece value
		// vb - minimum unknown piece value
		// For example, rank is Five and blufferRisk is 3.
		// vs = 200
		// vb = 30
		// So the piece value is at least 30 but not more than 200.
		// The more the piece moves, the closer to 200.
		// And the lower the blufferRisk, the faster it closes
		// in on 200.

		int vs = pieceValue(color, rank);
		int vb = pieceValue(color, unknownRank[color]);

		assert vb <= vs : "vb for " + unknownRank[color] + " must be less than vs for rank " + rank;

		return Math.min(vs, vb + (vs - vb) * p.getMoves() / (SUSPECTED_RANK_AGING_DELAY * blufferRisk));
	}

	public int winFight(Rank fprank, Rank tprank)
	{
		return winRank[fprank.ordinal()][tprank.ordinal()];
	}

	public int winFight(Piece fp, Piece tp)
	{
		Rank fprank = fp.getRank();
		Rank tprank = tp.getRank();

		// The AI assumes that any unknown piece
		// will take a known or flag bomb
		// because of flag safety.  All other attacks
		// on bombs are considered lost.
		//
		// If the unknown has a (suspected) non-eight rank,
		// winFight() would return LOSES.
		// But the opponent piece could be a bluffing eight,
		// so the threat needs to be taken seriously.
		//
		// maybeEight determines whether a known Unknown
		// can take a bomb.  For example,
		// RF RB B? -- -- R9
		// RB -- R7 -- -- --
		// -- -- -- -- -- --
		//
		// Red has the move. If R9xB? or R7xB?,
		// the result is a known Unknown.  But R9xB? does not
		// clear maybeEight, so the piece can still take the bomb.
		// R7xB? clears maybeEight, so the piece
		// cannot take the bomb.
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
		// The flag must be known OR the attacker does not
		// have a suspected rank to allow the attack on the
		// bomb to succeed.  The AI assumes that an attacker
		// can succeed in attacking the flag position even
		// if the opponent makes a lucky guess.

		// Yet the AI still needs to make an intelligent guess
		// whether the oncoming attacker is after the flag
		// or after a valuable piece.  So if the chase rank
		// has matured (that is, the chase rank was acquired
		// long ago), the AI believes the original chase rank.
		// So in the following example,
		// RF RB
		// RB --
		// -- RB 
		// -- R3
		// B? --
		// Unknown Blue has a chase rank of Two.  It moves forward
		// towards the flag, which also attacks Red Three.
		// Because the chase rank has matured, Red Three
		// moves away.
		//
		// Note: maybeEight is cleared for matured suspected ranks
		// in Board.java
		//
		// Note: Prior to version 10.1, this was qualified by
		//
		// && (flag[Settings.topColor].isKnown()
		//	|| fprank == Rank.UNKNOWN)) {
		//
		// But if the attacker becomes a known unknown during
		// the search, then the attacker lost by attacking
		// a bomb.  For example,
		// xxxxxxxx|
		// -- RB RF|
		// R9 -- RB|
		// -- B? R7|
		// The AI thinks that it is no danger because if
		// unknown Blue moves up, R9xB? creates a known
		// but suspected Five (i.e. known unknown) and
		// although maybeEight was still true, the AI thought the
		// attacker would lose if it attacked the bomb structure.
		//
		// So now the AI depends only on maybeEight.  This means
		// that unmatured suspected ranks can successfully win
		// an attack on an unknown AI bomb structure.

		// AI IS DEFENDER (tp)

		if (!fp.isKnown()
			&& tp.getColor() == Settings.topColor) {

            if (tprank == Rank.BOMB) {
                if (fp.isMaybeEight()
                    && (tp.isKnown() || tp.isFlagBomb()))
                    return WINS;	// maybe not
                return LOSES;	// most likely
            }

            if (fprank == Rank.UNKNOWN) {

		// Any piece will take a SPY or FLAG

			if (tprank == Rank.SPY
				|| tprank == Rank.FLAG)
				return WINS;

		// If an unknown opponent piece attacks an AI piece
		// that was previously unknown, it is less likely
		// that the unknown opponent attacker is strong
		// because the opponent could not know for sure that
		// the AI piece was strong.  For example,
		// | r4 --
		// | B5 b?
		// | b? b?
		// |------
		// r4?xB5 gains the value of Blue Five.
		// The countermove b?xR4 LOSES because r4xB5
		// could have been a surprise.

		// Thus R4 remains on the board.
		// Even if R4 was attacked earlier and became known,
		// R4xB5 is still a surprise.  For example,
		// | r4 --
		// | -- b?
		// | B5 b?
		// | b? b?
		// |------
		// Unknown Red Four moves down towards Blue Five.
		// b?xr4 LOSES.  After R4xR5, b?xR4 should still
		// be LOSES.

		// This risky assumption makes the AI aggressive in
		// cornering and attacking pieces.
		// But note that if there is only one protector,
		// the AI usually assigns the protector an indirect chase
		// rank, deterring the AI from attacking.   Yet if the
		// opponent is a zealous bluffer, the AI does not
		// assign indirect chase ranks, so the AI will attack anyway.
		// Note that the loss of stealth for ranks (1-3) will
		// deter them from approaching unknown pieces unless there
		// is sizable material advantage in doing so.  Ranks 4 & 5
		// will approach any unknown piece, because the unknown
		// value exceeds the value of ranks 4 & 5.

		// This replaces the less aggressive version 9.9 rule,
		// where ranks with stealth values less
		// than a unknown piece (i.e. Fours and Fives) were allowed
		// to approach *moved* unknown pieces.  This dovetails
		// with chase() because unknown moved pieces do not deter
		// the chaser from its target.

        // While an AI piece is safe, all opponent attacks lose,
        // except the One is not safe it if the opponent
        // is a LIKELY_SPY

			else if (tp.isSafe()
                && !(tprank == Rank.ONE && fp.isLikelySpy())
			    && tprank.ordinal() <= lowestUnknownExpendableRank + 1)
                    return LOSES;	// maybe not

		// By definition, attack on invincible rank LOSES
		// But invincible eights should be used for attacking
		// bombs rather than other eights.
		//
		// Version 10.4 removed:
		//	|| tprank.ordinal() == lowestUnknownNotSuspectedRank
		// that made the exchange EVEN when the opponent
		// invincible piece of the same rank is unknown.
		// The bug is that the ONE should not disappear, because
		// of a possible SpyxOne if the piece was protected.
		//
		// This bug could have been fixed
		// by making the One WINS and leaving the code in
		// for other ranks.   But an invincible piece should
		// expect to win an unknown piece in most cases, yet it
		// is important that winning suspected ranks is more
		// positive to avoid the possibility of an even exchange
		// with an unknown.
		//
		// Of course, there are situations where the AI will
		// guess wrong causing loss of the game.  For example:
		// rF rB
		// rB --
		// R1 --
		// -- b?
		// B8 --
		// The AI might approach Unknown Blue, because it thinks
		// it will win, but the exchange is even because unknown
		// Blue is actually Blue One. So Blue Eight
		// marches right to the flag bomb structure and wins the game.

			if (isInvincible(tp)) {
				if (isPossibleUnknownSpyXOne(tp, fp))
					return WINS;

				if (tprank.ordinal() >= 8)
					return EVEN;	// could be LOSES
				else
					return LOSES; // could be EVEN

        // Deciphering isFleeing():

		// Version 10.4 added the isFleeing() check to
		// the move value assessment (LOSES, EVEN, WINS)
		// instead of using it to determine the move result
		// if the AI piece is expendable and the opponent piece
		// is thought to be strong.

		// A fleeing piece could be of superior or inferior rank,
		// so in this case,
		// the AI assumes that it will lose its piece in
		// an encounter, but that the encounter will be
		// net positive for the AI.

		// In addition, the isFleeing() check in the move
		// value assignment applies to suspected ranks as well.
		// So a suspected lower ranked opponent piece  that fled
		// from an AI piece is a positive encounter but not a WIN.

		// In addition, not all suspected ranks are equal,
		// because a suspected rank that fled from an AI
		// rank poses no threat to it.  For example, a suspected
		// bomb with no flee rank could be a dangerous piece,
		// but a suspected bomb that neglected to attack
		// an AI rank poses no threat to that rank.

		// Ideally, suspected ranks should be assigned so
		// that they are not lower than a given rank that
		// they are fleeing from and where a capture
		// would appear to be positive.  But it is usual that
		// unknown low ranks flee from high ranks or unknowns
		// because they value their stealth, so capture
		// is negative.

		// But what about an unknown piece that chases a Five and then
		// flees from a Six?  The AI thinks that the piece
		// is a Four because it chased the Five, but apparently
		// it poses no risk to the Six.   Indeed, the surety is
		// that it poses no risk to the Six and it less likely
		// that the piece is actually a Four, and could
		// be almost anything.  Perhaps it is a Three that
		// was willing to attack a Five but is unwilling to
		// be discovered in exchange for a Six?  Perhaps it
		// is a bluffing high ranked piece, like a Seven or
		// Nine?

		// This deep analysis is lacking in the assignment
		// of suspected ranks.  Hence, the isFleeing() check
		// is used to eliminate riskOfLoss() during the move
		// value assignment.  Thus in the above example,
		// the result is LOSES, but the value is positive
		// (because the AI gains the opponent stealth and
		// does not lose the value of its piece).   This is
		// similar to WINS, but the AI piece is removed from
		// the board.

		// Yet if the AI piece is strong, it must assume that
		// it will win against any fleeing or weak pieces.
		// Otherwise, it may attack these pieces, even if they
		// are protected, because it would assume it will lose
		// the attack and therefore not see the counter capture
		// by the the protector.

			} else if (fp.isFleeing(tprank)
				&& (tprank.ordinal() <= 4
					|| fp.isWeak()))
					return LOSES;	// maybe not

		// If the opponent no longer has any unknown expendable
		// pieces nor a dangerous unknown rank,
		// then it is unlikely that an unknown opponent
		// piece will randomly attack an unknown AI piece.
		// Thus, unknown opponent pieces can no longer serve
		// as blockers, preventing the AI piece from its goal.
		// This is a non-symmetric rule.
		// TBD: this might be coded to use hasFewWeakRanks,

			else if (lowestUnknownExpendableRank < 5
				&& tprank.ordinal() < dangerousUnknownRank
				&& !tp.isKnown())
				return LOSES;

		// In version 9.2, the AI could be up by a Five
		// at the beginning of the game,
		// then push its known Five along opponent
		// ranks without concern.  The inevitable result was
		// losing its known Five to a Four.
		// 
		// What about an unknown Five?  The opponent can
		// assume that the unknown piece is weak (5-9)
		// and possibly attack it with a Four, resulting in
		// an even greater loss, because an unknown Five is
		// worth even more than a known Five.
		//
		// But it still seems worthwhile to approach unmoved opponent
		// pieces with expendable pieces, as a way to gain
		// information about the opponent pieces.  If a piece
		// flees, then the AI can assume it is a target.
		// The risk is that it will lose the expendable piece
		// to a Four or Five, but this is a way to keep the
		// game moving.  So if the AI is winning, the AI encourages its
		// expendable pieces to approach unmoved unknowns,
		//
		// Note that simply passing an unmoved unknown that does
		// not attack results in a flee rank for the piece, so
		// if the piece subsequently moves, it becomes a target.
		// This is a non-symmetric rule.

		// Version 9.3: any unknown expendable piece is not
		// afraid of unmoved pieces.

		// This is a further step to keep the game going.

			boolean riskExpendable =
				(isExpendable(tp)
				&& !tp.isKnown()
				&& isWinning(Settings.topColor) >= VALUE_FIVE);

		// If the attacker does not have a suspected rank,
		// but it does has a chase rank, it must have
		// chased a weak or unknown piece, which usually means
		// that the chaser is weak.
		//
		// Note that it is important to guess correctly if the AI
		// piece stays on the board (WINS) or disappears (UNK).
		// For example,
		// R5 --
		// B? --
		// B4 --
		// If B? is weak, R5xB? is positive, if R5 is removed from
		// the board, because then B4xR5 is not possible.
		// R5 --
		// B? R6
		// -- --
		// If B? is weak, R5xB? is positive, but if B? is actually
		// Red Four, then R5 loses and Blue wins both Red Five
		// and Red Six.
		//
		// Be wary of this rule in the opponent flag area,
		// because unknown pieces are often approached
		// in the opponent flag area to fend off attack.
		// Unknown Miners trying to get at the bombs are
		// especially vulnerable.  So prior to Version 10.4,
		// this was qualified by isNearOpponentFlag().  So then
		// the result was UNK.  UNK removes the AI piece.
		// But if the AI piece actually won and the
		// the opponent piece was protected, the opponent
		// takes the AI piece.
		//
		// As per the previous comment, if it looks like
		// the AI piece wins, it needs to stay on the board.
		// So in Version 10.4, isNearOpponentFlag() was
		// removed and hasLowValue() was used to qualify,
		// which should eliminate high value Miners.

			if (tprank.ordinal() <= lowestUnknownExpendableRank + 1) {

				int result = (tprank.ordinal() <= lowestUnknownExpendableRank ? LOSES : EVEN);
				if ((isWeakAggressive(fp)
					&& tprank.ordinal() <= 7)
					|| riskExpendable
					|| ((randomForay || tp.isSafe())
						&& fp.isWeak()
						&& hasLowValue(tp)))
						return result;	// maybe not
			}

			return UNK;

                    } // fp UNKNOWN rank

		} else if (!tp.isKnown()
			&& fp.getColor() == Settings.topColor) {

		// AI IS ATTACKER (fp)

		if (tprank == Rank.UNKNOWN) {

			if (fprank == Rank.BOMB
				|| fprank == Rank.FLAG
				|| fprank == Rank.SPY)	// almost always
				return LOSES;	// but has bluffing value

		// If tp could be a bomb and fp is not an Eight,
		// the result is UNK.
		// Note: an attack on a suspected bomb is handled in LOSES

			else if (fprank != Rank.EIGHT && isPossibleBomb(tp))
				return UNK;

		// By definition, invincible rank WINS (or is even)
		// on attack of unknown moved pieces.
		// But invincible eights should be used for attacking
		// bombs rather than other eights.
			else if (isInvincible(fp)) {
				if (fprank.ordinal() >= 8)
					return EVEN;	// maybe not, could be WINS
				else
					return WINS;	// maybe not, could be EVEN
			} else if (tp.isFleeing(fprank)
				&& (fprank.ordinal() <= 4
					|| tp.isWeak()))
					return WINS;	// maybe not

			else if (fprank.ordinal() <= lowestUnknownExpendableRank + 1) {

                // if the AI piece is lower than the lowest opponent expendable
                // rank remaining, and the AI thinks that the opponent piece
                // is expendable, then this is a WIN for the AI

				int result = (fprank.ordinal() <= lowestUnknownExpendableRank ? WINS : EVEN);
				

				if (isWeakAggressive(tp)
					&& fprank.ordinal() <= 7) 
					return result;	// maybe not

				if (randomForay
					&& tp.isWeak()
					&& hasLowValue(fp))
					return result;	// maybe not
			}

			return UNK;

		} // tp rank UNKNOWN

		} // ai attacker (fp)

		return winFight(fprank, tprank);
	}


	// Find the unknown rank with the minimum piece value
	// (value + stealth)
	// Obviously depends on values[] and valueStealth[].
	protected void genUnknownRank()
	{
		for (int c = RED; c <= BLUE; c++) {
			unknownRank[c] = Rank.UNKNOWN.ordinal();
			assert values[c][unknownRank[c]] == 0 : "Unknown has value?";
			for (int rank = 1; rank <= 10; rank++)
				if (unknownRankAtLarge(c, rank) != 0
					&& (unknownRank[c] == Rank.UNKNOWN.ordinal()
						|| pieceValue(c,rank) < pieceValue(c,unknownRank[c])))
					unknownRank[c] = rank;
		}
	}

	// Note: Acting Rank is an unreliable predictor
	// of actual rank.

	protected void genFleeRankandWeak()
	{
		for (int i=12;i<=120;i++) {
			if (!Grid.isValid(i))
				continue;
			Piece unk = getPiece(i);
			if (unk == null
			    || unk.isKnown())
				continue;
            int color = unk.getColor();

            if (isWeakAggressive(unk))
				unk.setWeak(true);

            Rank lowfleeRank = unk.getActingRankFleeLow();
            Rank highfleeRank = unk.getActingRankFleeHigh();

            if (highfleeRank != Rank.NIL)
            for (int rank = 1; rank <= 10; rank++) {

		// If an unknown piece approaches another unknown
		// piece, it probably means the approacher is weak,
		// because usually a player does not want to risk
		// discovery or loss of a low rank in an unknown encounter.

		// If then the approached unknown piece flees
		// from the approacher, it obtains a fleeRankHigh of Unknown.
		// This could mean that the fleeing piece is strong and wants
		// to avoid discovery (because the approacher was probably
		// weak).  Or perhaps the fleeing piece could be a weak
		// (7, 9) piece that does not want to enter an unknown
		// exchange with another weak piece and in which
		// it probably would lose.

		// The AI looks at the setup position to guess which of
		// the reasons the piece fled.  If the fleeing piece
		// came from a strong position, then the AI guesses
		// the the piece is strong.  Therefore, the AI deems
		// that the piece would flee from any expendable piece,
		// known or unknown.

			if (highfleeRank == Rank.UNKNOWN) {
				if (!unk.isWeak()
					&& isExpendable(1-color, rank))
					unk.setActingRankFlee(Rank.toRank(rank));
				continue;
			}

		// If a piece flees from two different ranks,
		// will it also flee from intervening ranks?
		// Not necessarily.  If a piece flees from a Two and Nine,
		// it might be a Two or Three and be happy to attack
		// a Four.

		// But if a piece flees from two expendable ranks,
		// it is likely that it will also flee from intervening
		// expendable ranks.  For example, if a piece fled
		// from a Six and a Nine, it will likely also flee
		// from a Seven, because if the fleer was a Five, Six
		// or Seven, it should have attacked.  Thus it is likely
		// that the piece is low ranked (1-3).

			if (isExpendable(1-color, rank)
				&& isExpendable(1-color, lowfleeRank.ordinal())
				&& rank > lowfleeRank.ordinal()
				&& rank < highfleeRank.ordinal())
				unk.setActingRankFlee(Rank.toRank(rank));
		}

		// If the unknown fled from some rank, can we predict
		// if the piece will flee from an even lower rank?  Usually this
		// is true, but not always.

		// For example, the opponent One is unknown.
		// An unknown piece has a flee rank of Three.  If the One
		// should have taken the Three, then the unknown is probably
		// not the One, so the unknown is considered to have fled from
		// a Two as well.  Thus a Two could approach a piece that fled
		// from a Three.
		//
		// But if the unknown piece has a flee rank of Four, perhaps
		// the unknown is the opponent One and so the player Two
		// (and Three) are not safe.

		// So to determine whether a lower rank is safe,
		// check the stealth value of the lower unknown ranks
		// against the value of the rank that fleed.  Add 20%
		// as a margin of error.

		// If the unknown were this rank
		// and its stealth value is less than this value,
		// the unknown *should* have taken the piece 
		// instead of fleeing, so the AI decides that the piece
		// is not this lower rank. Thus the piece poses
		// no threat to an equal rank and the AI assigns it
		// a flee rank.

		// However, if the stealth value is greater than this value,
		// the unknown could be the lowest ranked piece
		// and fled to avoid discovery, so
		// the AI assumes that the unknown could be a threat,
		// and does not set any additional flee ranks.

		// Examples:
		// A piece has a low flee rank of 9
		// and a high flee rank of unknown.  What ranks are safe
		// to approach the piece? Just 9.  The piece is probably
		// 1-4 (high stealth) and probably not a 5-8 (should
		// have attacked).  If stealth(4) > value(7), then
		// a 7 might be safe as well.     But a 5 or 6 might
		// not be safe if the piece is a Four.
		// TBD: This case is not handled.

		// A piece has a low flee rank of 3.
		// If stealth(1) < value(3), then a 2 should be safe.

		// A piece has a low flee rank of 4.
		// if stealth(2) < value(4), and the opponent one is
		// known or off the board, then a 3 should be safe.

		// A piece has a low flee rank of 5.
		// if stealth(3) < value(5), and the opponent one
		// and two are known or off the board, then a four
		// should be safe.

		// Note: topColor stealthValues are used, because these are
		// actual stealth. bottomColor stealthValues are suspected stealth.

		if (lowfleeRank != Rank.NIL
			&& lowfleeRank != Rank.UNKNOWN)
            for (int rank = 1; rank < lowfleeRank.ordinal(); rank++) {

            // unk cannot be rank because rank known or off board
                if (unknownRankAtLarge(color, rank) == 0)
                    continue;

            // unk could be rank because rank would have attacked
                if (stealthValue(Settings.topColor, rank) * 5 / 4 > values[Settings.topColor][lowfleeRank.ordinal()])
                    break;

            // unk should not be rank because rank did not attack
                if (rank != 1)
                    unk.setActingRankFlee(Rank.toRank(rank));

            } // rank


        // Simplistic, the Spy is thought to be adjacent to the Two
        // TBD: its not so simple
        // TBD: if all adjacent pieces are known, then where?

        if (setupRank[color][2] != 0
            && unknownRankAtLarge(color, Rank.SPY) != 0
            && Grid.isAdjacent(setupRank[color][2], unk.getIndex()))
                unk.setLikelySpy(true);

		} // i
	}

	// An expendable Eight is an Eight that is not needed to
	// remove flag bombs, because either there are excess Eights
	// or fewer structures that can contain bombs.  (However, if
	// the opponent still has many unknown bombs, an Eight could
	// still be decisive in a game with few pieces remaining.)
	//
	// An expendable Eight is usually worth less than a Seven.
	// The question is how much less.  It depends on the endgame.
	// If bombs are still very important, expendable Eights may be worth
	// more than a Seven.  But usually an endgame is all about
	// lower ranks winning, so Sevens are worth more.

	// Note: If an expendable Eight is worth less than Seven stealth,
	// then an unknown Seven will not attack an Eight
	// and lose its stealth.

	// Note: A known Seven will not attack a protected expendable Eight,
	// because the value gained is less.

	// TBD: need examples
	// - loss of Seven v. Eight => loss of game
	// - few remaining pieces + Eight
	// - multiple square movement v. single square movement

	void setExpendableEights(int color)
	{
            if (rankAtLarge(color, Rank.EIGHT) == 0)
                return;

            // An expendable Eight is worth more than a Nine.
            // This is because it can attack or defend its flag
            // against an opposing Nine.

            // TBD: However, a Nine can force a draw against
            // two opponent pieces and
            // win if it could get to an unguarded flag.

            values[color][Rank.EIGHT.ordinal()] =
                Math.max(
                    values[color][Rank.SEVEN.ordinal()] * 4 / 5,
                    values[color][Rank.NINE.ordinal()]);

            valueStealth[color][Rank.EIGHT.ordinal()-1] = valueStealth[color][Rank.SEVEN.ordinal()-1];
	}

    // Prior to Version 11, the AI attempted to forward
    // prune off Scout moves based on how juicy the target looked.
    // Yet it is difficult to make absolute predictions about
    // which unknown pieces are Scout targets, because
    // the AI is willing to sacrifice its Scout
    // if a following move combination wins the opponent
    // piece which is now known.  For example,
    // -- R9 --
    // -- -- --
    // -- -- R3
    // b? b? b?
    // Blue is losing and refuses to move any of its unknown
    // unmoved pieces.  R9xb? is perhaps a poor move in the
    // short term, but if it allows R3 to take the now known
    // blue piece, it could be a good move.

    // Yet, this is a necessary check to prune moves generated
    // by AI genSafe().  Examining all of the
    // AI Scout moves and opponent fleeing and blocking moves
    // would be very time consuming.
    // So the code was restored in Version 12.

    // TBD: Yes, this needs some more work to handle
    // attacks such as the example.  Perhaps increase
    // the stealth value of pieces near invincible pieces?

	boolean isNineTarget(Piece p)
	{
		Rank rank = p.getRank();
		if (rank == Rank.FLAG
			|| rank == Rank.SPY

		// Known opponent pieces are obviously not targets
		// except in some cases the AI may want to target
		// an opponent Scout, for example:
		// RB -- -- -- --
		// RS -- B9 -- R9
		// Red should play R9xB9.  Yes, this case is unusual
		// but it does happen.

            || (rank == Rank.NINE
                && p.getColor() == Settings.bottomColor)
            || isForaySquare(1-p.getColor(), p.getIndex()))
            return true;

        return stealthValue(p) >=  pieceValue(1-p.getColor(), 9);
	}

	// If all bombs have been accounted for,
	// the rest must be pieces (or the flag).
	// In this case, the piece is susceptible to
	// attack by an invincible piece.
	boolean isPossibleBomb(Piece p)
	{
		if (p.hasMoved())
			return false;

		if (p.getRank() == Rank.BOMB)
			return true;

		// The piece could also be a known suspected flag
		// (which could turn out to be a bomb)
		// if all other pieces have moved.
		if (p.getRank() == Rank.FLAG
			&& p.isSuspectedRank())
			return true;

		return (!p.isKnown()
			&& unknownBombs[p.getColor()] != 0);
	}

	// How to guess whether the Marshal will win or lose
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
	// An more aggressive approach is to assume that if
	// the unknown piece has a chase rank other than Spy
	// that the piece is not the Spy.
	//
	// But if the chase rank was awarded by protection
	// rather than actual chase, the chase rank is even less
	// reliable, so if the piece isRankLess() is true, then
	// the AI does not rely on the chase rank.
	// For example,
	// R3 --
	// B4 B?
	//
	// Unknown Red Three approached known Blue Four and it did not
	// move on its turn, so unknown Blue now has a chase
	// rank of Two.  isRankLess() is true, so this is a case
	// where unknown Blue could still be a Spy.
	//
	// Also, a suspected Bomb or Flag might actually be
	// a Spy.  So the One could be lost if a bomb pattern
	// or suspected Flag actually turned out to be a Spy.
	// (NOTE: The One will avoid moving next to a suspected
	// bomb because of bluffing value of Bomb X Piece in LOSES).
	// R1 --
	// -- --
	// BS --
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
	// Finally, the most aggressive approach is to rely on
	// setup information.  This is outright guessing, but some
	// bots and players alike assume that the Spy
	// was placed near the Two from the beginning.  If a player
	// always relies on this assumption, then the opponent
	// will learn this strategy in successive games, and then
	// pretty much any neighboring piece to the Two becomes
	// an effective Spy.  So when the AI relies on setup information
	// to determine the Spy location, it only assumes that
	// the Spy is not on the back row.
	//
	// The AI usually opts for the mid aggressive approach because
	// the most aggressive approaches can be learned
	// and then used to defeat the AI (i.e., if the player
	// learns to bluff with its Spy, it can cause the AI
	// to lose its One).
	//
	// Yet if the opponent has a dangerous known or unknown rank,
	// the AI is sure to lose additional material once its
	// One becomes known, so the AI becomes more aggressive.
	//
	// Finally, the AI is aggressive in trying to get the
	// opponent to divulge the location of the Spy.   Thus, once
	// the opponent Two is discovered, the AI chases it with
	// unknown pieces until it either traps the Two between
	// unknown pieces (one of which could the One) or the
	// Two stops with an unknown protector, which the AI then
	// assumes is the Spy and tries to attack it.

	boolean isPossibleUnknownSpyXOne(Piece fp, Piece tp)
	{
		assert !tp.isKnown() : "Opponent piece must be unknown";

		if (fp.getRank() != Rank.ONE)
			return false;

	// If the AI One is unknown, then it is highly unlikely that the
	// opponent will play SpyxUnknown, because it could lose it's Spy.
	// This is important because winFight() awards a WIN to the unknown
	// attacker, which would make any unknown attack overly negative
	// if the One is trapped when it really risks only its stealth.
	// 
	// Note that if the AI One is suspected, an opponent might play
	// SpyxUnknown, which is exactly what the AI does, if it
	// heavily suspects the opponent One.   So this check could be
	// conditioned on chase rank; if the AI has chased an opponent
	// Two for instance, then it is fair game even if it is unknown.
	//
	// But this gets into the Princess Bride scenario of
	// the poisoned drink "you were thinking that I was thinking
	// that you were thinking ... ", so it is best not to overthink
	// this too much.

		if (!fp.isKnown())
			return false;

	// TBD: Playing with a known One is challenging, and this code
	// has seesawed to and from being aggressive and conservative.
	// A possible "improvement" is to analyze the opponent pieces
	// after the discovery of the One to try to identify pieces most
	// likely to be the Spy.  For example, if the opponent Two is
	// known or suspected, then mark the pieces near the Two.

		if (tp.getRank() == Rank.SPY)
			return true;

	// If the AI has guessed the location of the Spy, the AI One is safe
	// from any unknown piece.

		if (!hasUnsuspectedSpy(Settings.bottomColor))
			return false;

	// A piece that fled from the One is not the Spy

		if (tp.isFleeing(Rank.ONE))
			return false;

        // If the opponent is not winning by much and does not have
        // any dangerous ranks, then the AI One remains conservative.

		if (isWinning(Settings.bottomColor) < values[Settings.topColor][3]) {
			if (dangerousKnownRank == 99 && dangerousUnknownRank == 99)
				return true;
                }

	// The opponent is either winning big or has a dangerous rank.
	// The AI will likely lose more material, but how much?
        // If the opponent has an unknown Two,
	// the AI will probably lose a Three, if it still has one.
	// If the opponent has a known Two, perhaps the AI can keep
	// the opponent Two at bay.

	//  In this scenario, the AI also assumes that the AI One is safe from:
	// - any piece with a direct chase rank
        //      or an indirect chase rank that moved.

		if ( tp.getActingRankChase() != Rank.NIL
			&& !(tp.isRankLess() && !tp.hasMoved()) )
                    return false;

        // If the opponent is not winning by much, the AI remains conservative

		if (isWinning(Settings.bottomColor) < values[Settings.topColor][3])
                        return true;

	// The AI is losing badly and must become even more aggressive,
	// so it now assumes that the AI One is safe from:
	// - any piece from a weak area
	// - a suspected bomb with a high flee rank of Five or more
	//	(the AI assumes the Spy will flee if approached by
	//	an expendable piece but perhaps not if approached
	// 	by a valuable rank).
	//	(Note: unknown piece will always have an actingRankFleeHigh
	//	of at least One, because it was approached by the One).
	//
                if (tp.isWeak()
			|| (tp.getRank() == Rank.BOMB
				&& tp.getActingRankFleeHigh().ordinal() >= 5))
			return false;

		return true;
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
		assert fp.getColor() == Settings.topColor : "fp must be top color.  Rank " + fp.getRank() + " color " + fp.getColor() + " at " + fp.getIndex() + " tpRank " + tp.getRank() + " color " + tp.getColor() + " at " + tp.getIndex();
		Rank fprank = fp.getRank();
		Rank tprank = tp.getRank();

		if (fprank == Rank.BOMB)
			return 0;

		assert fprank == Rank.SPY || fprank.ordinal() <= tprank.ordinal()
			: fprank + " loses to " + tprank;

		if (tp.isKnown())
			return 0;

		if (isInvincible(fp)) {

        // Note: b?xR1 or N?xR1 have some risk of loss if
        // the opponent piece turns out to be the Spy.  However, winFight makes
        // b?xR1 WINS, so riskOfLoss is not called.  Here, isLikelySpy()
        // is called to be consistent with isSafe() in winFight().  Thus,
        // a successful Spy would have to be not adjacent to the opponent Two,
        // then chase a known piece, while not bluffing with other pieces to keep bluffer risk
        // low to maintain its suspected rank. This is very unlikely so
        // the AI ignores the risk.  It is also unlikely if the One is unknown,
        // unless the opponent thinks it has guessed the unknown to be the One,
        // but that is very gutsy (although the AI does it on occasion
        // if the unknown has been obvious chaser of the Two).

            if (fp.isKnown()
                && fprank == Rank.ONE
                && tp.isLikelySpy())
                return values[fp.getColor()][fprank.ordinal()]*7/10;
			return 0;
        }

		// The Spy risks total loss regardless of the opposing rank
		if (fprank == Rank.SPY)
			return pieceValue(fp);

		// If the opponent has a dangerous unknown rank,
		// it may approach any AI piece, and therefore may
		// have a suspected or unknown rank.  This increases
		// the risk of loss.   But the opponent may also decide to
		// keep the dangerous unknown rank hidden, so the
		// AI needs to assume some risk until the dangerous
		// unknown rank is discovered.

		int minRisk = 0;
		if (fprank.ordinal() <= dangerousUnknownRank + 2
			&& fprank.ordinal() > dangerousUnknownRank)
			minRisk = pieceValue(fp)/3;

		if (!tp.isSuspectedRank())
			return minRisk;

		// The risk of loss depends on the piece value
		// and the difference between the ranks.

		// Example
		// 4x4 : -100
		// 3x4 : -25
		// 2x4 : -11
		// Thus, if the AI must capture the suspected Four
		// it will use the lowest known rank (Two).
		// However, if the AI ranks are unknown, stealth
		// dominates, making a capture with the Three
		// the most palatable.
		//
		// Example
		// 5x5 : -50
		// 4x5 : -12
		// 3x5 : -5

		// Note: This fails if the opponent uses low ranks to bluff.
		// For example, if the unknown opponent One chases a Five,
		// it would make the AI think that the opponent was a Four
		// and so might attack it with a Three.  Another case is
		// a One bluffing as a Spy, protecting a Two from
		// the AI One. Because of the substantial difference in rank
		// of a Spy and almost any other piece, there is
		// little risk of loss in the AI attacking the suspected Spy,
		// which actually turns out to be the One.
		//
		// These situations do occur in play, but infrequently.
		// Incorporating the intelligence to discern this type
		// of bluffing is beyond the scope of this program
		// (and perhaps artificial intelligence).

		// riskOfLoss is reduced by low bluffer risk
		// once the piece has had time to attack AI pieces
		// and refine its suspected rank.

		int diff;
		if (tp.getMoves() >= 10)
			diff = blufferRisk;
		else
			diff = 4;

		// protection rank is less reliable than chase rank
		if (tp.isRankLess())
			diff++;

		diff = 5 - Math.min(diff, 4);
		diff += tprank.ordinal() - fprank.ordinal();
		diff = diff * diff;

		int risk = pieceValue(tp) / diff;

		// TBD: e.g. a suspected rank of Four
		// that actually chased a piece (rather than protecting)
		// rarely turns out to be a lower rank, because a suspected
		// rank of Four means that the piece chased a known Five,
		// which are unpredictable, and could easily have
		// counter-attacked and exposed the lower rank.
		// Yet 3?x5 is about even, so Threes do chase Fives.

		// A suspected rank of Five (it chased a Six) could
		// be a Four or a Five, but usually it is a Five and
		// so 5x5? should not be so negative.
		if (tprank == Rank.FIVE)
			risk /= 2;
				
		return Math.max(minRisk, risk);
	}

    // In an unknown encounter when the AI predicts the loss of its piece,
    // it loses its entire piece value.
    // But there is always a chance that the AI might not lose it piece.
    // riskOfWin() tries to calculate those odds.
    // This code is always in flux trying to make a better guess.

    // Note that this function passes in fpvalue and generally
    // returns a percentage of it, not to exceed 1/2.  
    // fpvalue is not necessarily pieceValue(fp), because
    // of apparentValue().

	int riskOfWin(int fpvalue, Piece fp, Piece tp, boolean maybeBomb)
	{
        assert fp.getColor() == Settings.topColor : "riskOfWin only for AI";
        assert !tp.isKnown() : "riskOfWin: opponent piece must be unknown";
        Rank fprank = fp.getRank();
        assert !(maybeBomb && fprank == Rank.EIGHT);

        int v = fpvalue/ 5;

        if (!maybeBomb
            && !(tp.isChasing(Rank.UNKNOWN)
                && fprank.ordinal() >= 7))
                v += fpvalue/ 5;
        else {
            if (tp.getRank() != Rank.BOMB)
                v += fpvalue/ 10;
            if (tp.isFleeing(fp.getRank()))
                v += fpvalue/ 5;
        }

    // If the AI has guessed the rank, then the risk of win is about nil

        if (tp.isSuspectedRank())
            v /= 3;

		return v;
	}

	int pieceValue(int c, int r)
	{
		return values[c][r] + stealthValue(c, r);
	}

	int pieceValue(int c, Rank rank)
	{
		return pieceValue(c, rank.ordinal());
	}

	int pieceValue(Piece p)
	{
		int v = values[p.getColor()][p.getRank().ordinal()] + stealthValue(p);
		return v;
	}

	boolean isNearOpponentFlag(int to)
	{
		return flag[Settings.bottomColor] != 0 &&
			Grid.steps(to, flag[Settings.bottomColor]) <= 4;
	}

	boolean isNearOpponentFlag(Piece p)
	{
		return isNearOpponentFlag(p.getIndex());
	}

	public boolean isFlagBombAtRisk(Piece p)
	{
		int flagi = flag[1-p.getColor()];
		assert flagi != 0 : "flag zero for color " + (1-p.getColor());

		return p.isMaybeEight()
			&& Grid.steps(p.getIndex(), flagi) <= 4;
	}

	// If the opponent has an unsuspected Spy, it is dangerous for
	// for the One to approach unknown pieces.  But if the Spy is gone
	// or the AI thinks it knows where it is, then the AI goes on
	// the rampage with its One.

	boolean hasUnsuspectedSpy(int c)
	{
		return hasSpy(c)
			&& suspectedRankAtLarge(c, Rank.SPY) == 0;
	}

	boolean hasUnsuspectedOne(int c)
	{
		return rankAtLarge(c, Rank.ONE) != 0
			&& suspectedRankAtLarge(c, Rank.ONE) == 0;
	}

	// Will an unknown AI piece attack a known or suspected
	// opponent rank?  Not if its stealth exceeds the value
	// of the opponent rank or if the piece is the same rank.
	boolean isStealthy(Piece p, int r)
	{
		return (p != null
			&& !p.isKnown()
			&& (stealthValue(p) > pieceValue(1-p.getColor(), r)
				|| p.getRank().ordinal() == r));
	}

	boolean isStealthy(Piece p)
	{
		return isStealthy(p, unknownRank[1-p.getColor()]);
	}

	// For an AI bluff to be effective, the defending opponent piece
	// must not be invincible.  If the AI has incorrectly guessed
	// the opponent piece to be non-invincible, and it turns out to be
	// invincible, the AI loses big.
	// For example,
	// -- R9 -- --
	// xx -- B3 xx
	// xx R2 -- xx
	// -- -- B2 --
	// All pieces are unknown except for Blue Three.  Red has the move.
	// It moves its Red Two to the right.  Red still has its unknown
	// One, so it is bluffing that suspected Blue Two will move away,
	// allowing it to capture Blue Three.  But if Blue Two turns out
	// to be Blue One, then BAM! Red loses its Two.
	//
	// So until Version 11.1, maybeIsInvincible() returned true
	// for this example.  But this was deemed a cop-out, because if Red
	// had correctly guessed Blue Two, Blue Two would likely move
	// away, because unknown Red could be Red One or some bluffing
	// piece, allowing unknown Red Two to win Blue Three.
	//
	// Version 11.1 raises the ante; the AI must correctly guess
	// the opponent ranks to play aggressive moves such as this.
	// But how can the AI guess the opponent One when its own Two is
	// unknown?   Good question, but this is the essence of
	// advanced play in Stratego.
	//
	// Note the similarily with the following position:
	// -------
	// | B3 BB
	// | -- B?
	// | R2 --
	// Blue Three is known, Red Two is unknown and unknown Blue One
	// is still at large.  Should Red Two advance and attack
	// the cornered Blue Three?  Yes, according to the AI.
	// So if unknown Blue is a suspected Two, what is the difference
	// between this example and the former example?

	boolean maybeIsInvincible(Piece p)
	{
		return isInvincible(p);
	}


	void markExposedPiece(Piece oppPiece)
	{
		// If the piece already has a rank or a chase rank,
		// the chase rank is retained

		if (oppPiece.isKnown()
			|| oppPiece.getActingRankChase() != Rank.NIL)
			return;

		// c is current player color, so determines
		// the direction of the players field and
		// is the color of the possible Scout piece
		int c = 1 - oppPiece.getColor();

		int up;
		if (c == Settings.topColor)
			up = -11;
		else
			up = 11;

		for (int i = oppPiece.getIndex()+up; ; i += up) {
			Piece p = getPiece(i);
			if (p != null) {
				if (p.getColor() != c)
					break;
				if (p.isKnown()
					&& p.getRank() != Rank.NINE)
					break;

                // The opponent is allowing oppPiece to be attacked
                // by a Scout.  The AI believes then that the piece
                // is weak and no danger to a One or Two, because
                // strong players usually shelter their superior pieces
                // to retain the element of surprise.
                // This is just a wild guess, but in stratego
                // you live or die by your guesses.

				oppPiece.setWeak(true);

                // Prior to Version 12, the oppPiece was set
                // with a flee rank of One and Two, because
                // the piece was guessed to be no danger to a One or Two.
                // One problem with that was the indirect flee rank
                // code did not assign a flee rank (of Spy) when the unknown
                // One approached a known Two with an unknown protector.
                // The other problem is that this flee rank assignment is
                // too simplistic; the AI should be encouraged to attack
                // pieces that actually flee rather than wild guesses.
                // isPossibleUnknownSpyXOne() is more intelligent.
                // TBD: and should be extended to other pieces,
                // which should accept more risk when the AI is losing.

				break;
			}
		}
	}

	void markExposedPieces(int mc)
	{
		// If the opponent has few Scouts remaining,
		// it is marginly safer to expose low ranked pieces.
		// However, often an opponent does not care about
		// exposure, or has no choice in complex situations,
		// so this rule is used only during the opening.

		Move m1 = getLastMove(mc);
		if (m1 == UndoMove.NullMove)
			return;

		int c = m1.getPiece().getColor();

		if (rankAtLarge(1-c, Rank.NINE) < 4
			|| hasFewWeakRanks(c, 4))
			return;

		// If the piece is still on the board
		// and it moved vertically,
		// it could be a superior piece whose blocking
		// piece was removed, and now it is trying to
		// escape detection.

		if (getPiece(m1.getTo()) == m1.getPiece()) {

			if (m1.getToX() - m1.getFromX() == 0)
				return;

		// It is difficult to guess if the opponent
		// was forced to expose a piece or deliberately
		// exposed a piece for some opportunity.
		// And some opponents do not care about exposing
		// their strong pieces.   So the following logic
		// should not be too restrictive or complex.
		// Marking weak pieces based on piece movements
		// is just a (big) risk that the AI takes.

		// If an opponent piece is nearby, the piece
		// may have been chased, or the piece may be
		// beginning a chase.

			Move m2 = getLastMove(mc+1);
			if (m2 == UndoMove.NullMove)
				return;

			if (Grid.isAdjacent(m1.getFrom(), m2.getTo())
				|| grid.hasAttack(m1.getPiece()))
				return;

		// There isn't an obvious reason why the piece moved,
		// so if it exposed itself or another piece to a
		// potential Scout attack, assume the exposed piece is weak.

			markExposedPiece(m1.getPiece());
		}


		// Did the piece expose another piece?
		int down;
		if (c == Settings.topColor)
			down = -11;
		else
			down = 11;

		for (int i = m1.getFrom()+down; Grid.isValid(i); i += down) {
			Piece p = getPiece(i);
			if (p != null
				&& p.getColor() == c) {
				markExposedPiece(p);
				break;
			}
		}
		
	}


	// Unknown pieces at the front of the lanes at the start
	// of the game or that freely move into a lane across
	// from an opponent unknown piece while the opponent
	// still has a healthy supply of Scouts are thought to be weak
	// because the player has deliberately
	// exposed these pieces to attack by Scouts.
	//
	// While this heuristic is frequently violated by aggressive
	// players, the AI's route to victory hinges on discovering
	// strong opponent ranks, particularly the opponent One,
	// and for defensive opponents, the AI has to assume that
	// the opponent is sheltering its One; otherwise, if the AI
	// is unsuccessful at discovering the opponent One, it
	// will likely lose a game of attrition against a skilled opponent.
	//
	// Note: this heuristic is also necessary for effective bluffing,
	// because once the opponent realizes that the AI plays
	// defensively and shelters its One, then the opponent also
	// will realize that an AI piece that was voluntarily exposed to
	// the front line is not a strong piece, and therefore
	// is unsuitable to bluff against an opponent strong piece.
	//
	// Suspected Piece Layout (W = Weak)
	// W W W W W W W W W W
	// ? ? ? ? ? ? ? ? ? ?
	// ? ? ? ? ? ? ? ? ? ?
	// W W ? ? W W ? ? W W
	// - - x x - - x x - -
	// - - x x - - x x - -

	void markWeakPieces()
	{
		for (int c = RED; c <= BLUE; c++) {

		// AI assumes that front row pieces are weak
		// and not Ones, Spys or Miners.

		for (int lane = 0; lane < 3; lane++)
		for (int x = 0; x < 2; x++) {
			Piece p = getSetupPiece(Grid.getIndex(lane*4+x, Grid.yside(1-c, 3)));
			p.setWeak(true);
			p.setMaybeEight(false);
		}

		// AI assumes that back row pieces are weak,
		// guessing that most opponents do not bury strong pieces

		for (int x = 0; x < 10; x++)
			getSetupPiece(Grid.getIndex(x, Grid.yside(1-c, 0))).setWeak(true);

		} // c

		// AI assumes that the opponent does not advance its Spy
		// while the AI One is unknown and the opponent Two
		// is unknown.

		if (unknownRankAtLarge(Settings.topColor, Rank.ONE) == 0
			&& unknownRankAtLarge(Settings.bottomColor, Rank.TWO) == 0
			&& hasFewWeakRanks(Settings.topColor, 4)
			&& blufferRisk != 5) {
			UndoMove um = getLastMove(1);
			if (um != UndoMove.NullMove
				&& Grid.getY(um.getTo()) < 5
				&& !um.getPiece().isKnown())
				um.getPiece().setActingRankFlee(Rank.ONE);
		}

		markExposedPieces(1);
		markExposedPieces(2);

		// TBD: Unknown moved opponent pieces left adjacent to
		// known player weak pieces could be marked weak as well,
		// if opponent had a fleeing route.  Yet a bluffing opponent
		// negates this rule.  Note that the opponent will
		// acquire a flee rank, but fleeing could also mean the
		// opponent is a strong piece fleeing to avoid discovery.

	}

    boolean isForayLane(int color, int i)
    {
        return Math.abs(forayLane[color] - Grid.getX(i)) < 2;
    }

    // Foray Squares (FO):
    // -- xx xx -- FO |
    // -- xx xx -- FO |
    // -- -- -- -- FO |
    // -- -- -- -- FO |
    // -- -- -- -- FO |
    // FO FO FO FO FO |
    // ----------------

    boolean isForaySquare(int color, int i)
    {
        return forayLane[color] == Grid.getX(i)
            || (Math.abs(forayLane[color] - Grid.getX(i)) < 5
                && Grid.yside(color, 9) == Grid.getY(i));
    }

	// Aggressively attack pieces in the foray lane to try
	// to get to the back row, where it should encounter weaker pieces.
    // Any bomb encountered should be removed.
    // If it is lucky, the flag will be
	// in a corner structure in the foray lane.
	// This should make for fireworks, with the AI throwing
	// its pieces towards the foray lane and attacking all the defenders that
	// come in its way.

	boolean isForay(int i)
	{
            return randomForay || isForaySquare(Settings.topColor, i);
	}

	// Return a result between 0 and vm, depending on the value vm.
	// The more negative vm is, the closer to zero.
	// The more positive vm is, the closer to vm.

	static int makePositive(int vm, int v)
	{
		if (vm > v)
			return vm;
		else if (vm > 0)
			return (v + vm)/2;
		else if (vm > -v * 10)
			return -vm/20;
		else
			return 0;
	}

	String getDebugInfo()
	{
		String s = "";
		for (int c = RED; c <= BLUE; c++) {
			s += "values[" + c + "][] = ";
			for (int i : values[c])
				s += i + ",";
			s += "\n";

			s += "valueStealth[" + c + "][] = ";
			for (int i : valueStealth[c])
				s += i + ",";
			s += "\n";

			s += "unmovedValue[] = ";
			for (int i : unmovedValue)
				s += i + ",";
			s += "\n";
		}

            s += "Foray lane: " + forayLane[Settings.topColor] + "\n";
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    Piece p = getPiece(x, y);
                    if (p == null) {
                        s += "-- ";
                        continue;   
                    }
                    if (p.getColor() == 0) {
                        if (p.isKnown())
                            s += "R";
                        else
                            s += "r";
                    } else if (p.getColor() == 1) {
                        if (p.isKnown())
                            s += "B";
                        else
                            s += "b";
                    } else
                        s += "x";

                    Rank rank = p.getRank();
                    s += rank.value;
                    s += " ";
                }
                s += "\n";
            }
            return s;
	}
}

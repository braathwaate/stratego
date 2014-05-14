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

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.BMove;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Settings;
import java.util.Random;



public class TestingBoard extends Board
{
	private static final int DEST_PRIORITY_DEFEND_FLAG = 20;
	private static final int DEST_PRIORITY_DEFEND_FLAG_BOMBS = 15;
	private static final int DEST_PRIORITY_ATTACK_FLAG = 10;
	private static final int DEST_PRIORITY_ATTACK_FLAG_BOMBS = 5;
	private static final int DEST_PRIORITY_BLOCKER = 5;
	private static final int DEST_PRIORITY_LOW = -1;
	private static final int DEST_VALUE_NIL = -99;
	private static final int AI_BLUFFING_VALUE = 30;

	protected Piece[][] pieces = new Piece[2][41];	// piece arrays
	protected int[] npieces = new int[2];		// piece counts
	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected Piece[][] activeRank = new Piece[2][15];	// moved rank Piece
	protected int[][] trayRank = new int[2][15];	// ranks in trays
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[] destTmp = new int[121];	// encourage forward motion
	protected int[][][] planA = new int[2][15][121];	// plan A
	protected int[][][] planB = new int[2][15][121];	// plan B
	protected int[][] winRank = new int[15][15];	// winfight cache
	protected int[] piecesInTray = new int[2];
	protected int[] piecesNotBomb = new int[2];
	protected int avgUnkRank;	// average unknown
	protected int[] sumValues = new int[2];
	protected int value;	// value of board
	protected Piece[] flag = new Piece[2];	// flags
	protected int[] unmovedValue = new int[121];	// unmoved value

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
	// • An unmoved piece is worth more than a moved piece.
	// • An unknown piece is worth more than a known piece (stealth).
	// • An invincible piece is worth more than a non-invincible
	//	opponent piece of the same rank.
	// • Bombs are worthless, except if they surround a flag or
	//	block lanes.  These bombs are worth a little more than a Miner.
	// • The value of the Spy becomes equal to a Seven once the
	//	opponent Marshal is removed.
	// • The value of the Miner depends on the number of structures
	//	left than could surround a flag.
	//	- When the opposing player has more structures left than Miners
	//	  the value of the Miners is increased.
	//	- When the opposing player has less structures left than Miners
	//	  the value of the Miners becomes equal to a Seven.
	// • If the AI is winning, an AI piece is worth less than
	//	the opponents piece of the same rank.  If the AI is losing,
	//	its pieces are worth more.
	// • An unbombed flag is worth about a known Four.  (It is not
	//	the highest value because of the opponent never really
	//	knows for sure the location of the flag.  See the code
	//	for more information).
	// 
	static final int [] startValues = {
		0,
		800,	// 1 Marshal
		400, 	// 2 General
		200, 	// 3 Colonel
		100,	// 4 Major
		50,	// 5 Captain
		30,	// 6 Lieutenant
		25,	// 7 Sergeant
		60,	// 8 Miner
		25,	// 9 Scout
		600,	// Spy
		0,	// Bomb (valued by code)
		0,	// Flag (valued by code)
		35,	// Unknown
		0	// Nil
	};
	int [][] values = new int[2][15];

	static final int[][] lanes = {
		{ 45, 46, 56, 57, 67, 68, 78, 79 },
		{ 49, 50, 60, 61, 71, 72, 82, 83 },
		{ 53, 54, 64, 65, 75, 76, 86, 87 }
	};
		

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

		value = 0;

		for (int c = RED; c <= BLUE; c++) {
			flag[c]=null;
			npieces[c] = 0;
			piecesNotBomb[c] = 0;
			piecesInTray[c] = 0;
			for (int j=0;j<15;j++) {
				knownRank[c][j] = 0;
				activeRank[c][j] = null;
				trayRank[c][j] = 0;
				neededRank[c][j] = false;
				values[c][j] = startValues[j];
			}
		}
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

		// avgUnkRank is the average rank of the remaining
		// unknown opponent pieces, excluding the bombs and flag.
		// avgUnkRank is used when nothing else
		// is guessed about unknown opponent piece ranks.
		// This occurs during an encounter
		// when both opponent and ai pieces are unknown.
		avgUnkRank = 0;
		int npieces = 0;
		for (int r=1; r <= 10; r++) {
			int n = unknownRankAtLarge(Settings.bottomColor, r);
			npieces += n;
			avgUnkRank += n * r; 
		}
		if (npieces != 0)
			avgUnkRank /= npieces;

		// If all pieces have been accounted for,
		// the rest must be bombs (or the flag)
		for (int c = RED; c <= BLUE; c++) {
		if (40 - piecesInTray[c] - piecesNotBomb[c]
			== 1 + Rank.getRanks(Rank.BOMB.toInt()-1) - 
				trayRank[c][Rank.BOMB.toInt()-1]) {
			for (int i=12;i<=120;i++) {
				if (!isValid(i))
					continue;
				Piece p = getPiece(i);
				if (p == null)
					continue;
				if (p.getColor() != c)
					continue;
				if (!p.isKnown() && !p.hasMoved()) {
				Rank rank = p.getRank();
				if (c == Settings.topColor)
					assert (rank == Rank.FLAG || rank == Rank.BOMB) : "remaining ai pieces not bomb or flag?";
				else
					p.setActingRankChase(Rank.BOMB);
				}
			}
		}
		}

		// A rank becomes invincible when all lower ranking pieces
		// are gone or *known*.
		//
		// Invincibility means that a rank can attack unknown pieces
		// with the prospect of a win or even exchange.
		//
		for (int c = RED; c <= BLUE; c++) {
			int rank = 1;
			for (;rank<10;rank++) {
				if (unknownRankAtLarge(c, rank) != 0)
					break;
			}
			invincibleRank[1-c] = rank;

			// demote the value of the spy if there is
			// no opponent marshal left at large
			if (rankAtLarge(1-c,1) == 0)
				values[c][Rank.SPY.toInt()]
					= values[c][Rank.SEVEN.toInt()];

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
			for (int j=12; j <= 120; j++) {
				planA[c][rank][j] = DEST_VALUE_NIL;
				planB[c][rank][j] = DEST_VALUE_NIL;
			}
		}

		// valuePieces should be called after all individual
		// piece values have been determined.
		// Destination Value Matrices depends on piece values
		// so that needs to be called later.

		possibleFlag();
		aiFlagSafety(); // depends on possibleFlag
		valuePieces();

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
			chase(p, i);
		
			// Encourage lesser valued pieces to attack
			// unknown unmoved pieces of higher value.
			// Back rows may be further, but are of
			// equal destination value.
			//
			// Once possibleFlag() identifies a possible flag,
			// then this code is skipped, as it is better
			// to target one flag location before trying to identify
			// another.  This keeps the ai pieces
			// lesser valued unknown pieces unmoved, which helps to
			// confuse the opponent about the whereabouts of
			// its own flag.
			//
			if (!p.isKnown()
				&& !p.hasMoved()
				&& p.getActingRankChase() != Rank.BOMB
				&& (p.getColor() == Settings.bottomColor && flag[p.getColor()] == null
					|| p.getColor() == Settings.topColor && !flag[p.getColor()].isKnown())) {
				int y = Grid.getY(i);
				if (y <= 3)
					y = -y;
				genDestTmp(false, p.getColor(), i, DEST_PRIORITY_LOW + y - 9);
				for (int r=1; r<=10; r++)
					if (values[1-p.getColor()][r] <= apparentValue(p))
						genPlanA(1-p.getColor(), r);
			}
		}

		// keep a piece of a high rank in motion,
		// preferably a 6, 7 or 9 to discover unknown pieces.
		// 5s are also drawn towards unknown pieces, but they
		// are more skittish and therefore blocked by moving
		// unknown pieces.
		for (int c = RED; c <= BLUE; c++) {
			int rank[] = { 6, 7, 9 };
			boolean found = false;
			for ( int r : rank )
				if (activeRank[c][r-1] != null) {
					found = true;
					break;
				}

			// if there are not any high rank pieces in motion
			// set the neededRank.  The search tree will
			// move the rank that can make the most progress
			// towards a opponent unknown unmoved piece.
			if (!found) {
				for (int r : rank)
					neededRank[c][r-1] = true;
			}
		}

		// The ai considers all isolated unmoved pieces to be bombs
		// if the opponent spy is gone or the AI One is gone.
		//
		// bug: So one way to fool the ai is to make the flag 
		// isolated and then the ai will not attack it.
		//
		if (!hasSpy(Settings.bottomColor)
			|| rankAtLarge(Settings.topColor, 1) == 0)
			possibleBomb();

		valueLaneBombs();
		genWinRank();
		targetUnknownBlockers();
		genPieceLists();
	}

	//
	// Generate piece lists for move generation.  This is the
	// list of all pieces except for Bombs and Flags.
	// genPieceLists depends on the routines that guess the location
	// of Bombs and Flags, because these guesses are not added
	// to the list.
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
			if (rank == Rank.BOMB || rank == Rank.FLAG)
				continue;

			pieces[p.getColor()][npieces[p.getColor()]++]=p;
		}

		pieces[0][npieces[0]++]=null;	 // null terminate list
		pieces[1][npieces[1]++]=null;	 // null terminate list
	}

	void valuePieces()
	{
		for (int c = RED; c <= BLUE; c++) {
			// invincible pieces have higher value
			for (int r = 2; r <= invincibleRank[c]; r++)
				values[c][r] += 30;

			// a One is worth more (at least a Five) if the opponent
			// has lost its spy
			if (!hasSpy(c))
				values[1-c][1] += 50;

			// Sum the values of the remaining pieces
			// ranks 1 - 7.  Omit the 8's because higher
			// values actually indicate loss, omit the 9's
			// and spy because these have no strength
			// in an endgame where one player has superior
			// pieces.
			sumValues[c] = 0;
			for (int rank = 1; rank <= 7; rank++) {
				int n = Rank.getRanks(rank-1) - trayRank[c][rank-1];
				sumValues[c] += values[c][rank] * n;
			}

		}

		// is the AI winning?
		// if so, its pieces are worth less
		// to encourage it to exchange ranks
		// if not, its pieces are worth slightly more
		// to encourage it to avoid exchanging ranks
		//
		// But not too much more so it allows lower ranks
		// to be captured for higher ranks.  For this to work,
		// the value difference between ranks must be greater than
		// 25%.
		//
		// if (sumValues[Settings.topColor] == 0) and
		// its flag is bombed and the opponent has an 8
		// the ai should surrender
		//
		if (sumValues[Settings.topColor] != 0)
		for (int rank = 1; rank <= 10; rank++) {
			long v = values[Settings.topColor][rank]/4;
			v *= sumValues[Settings.bottomColor];
			v /= sumValues[Settings.topColor];
			values[Settings.topColor][rank] =
				values[Settings.topColor][rank]*3/4 + (int)v;
		}
	}

	// An unknown piece blocking the destination
	// of a low ranked piece is worth discovering,
	// because it might be bluffing.
	// Perhaps it is blocking access to the flag?
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
			for (int lowr = 1; lowr <= 8; lowr++) {
				Piece fp = activeRank[1-tp.getColor()][lowr-1];
				if (fp != null
					&& planva(fp.getColor(), lowr-1, fp.getIndex(), i) > 0) {
					tp.setBlocker(true);

					// Unknown piece blocking confirmed:
					// move towards blocker.
					// TBD:
					// There is a tradeoff with creating
					// a new plan to discover a blocker,
					// because the ai doesn't really
					// know how to attack the blocker,
					// so I think it is best to keep to
					// existing plans and rely on the
					// search tree to discover the blocker's
					// increased value.
					//
					// if (r <= 4)
					// for (int r=lowr+1; r<=9; r++) {
					// 	if (values[1-tp.getColor()][r] <= apparentValue(fp))
					// 		genNeededPlanA(1-tp.getColor(), r);
					// }
				}
			}
		}
	}


	// Chase the piece at position i using pieces of "chaseRank" or lower.
	//
	protected void chase(Piece p, int i)
	{
		int chaseRank = 0;
		if (p.isKnown()) {
			// no point in chasing a Nine because
			// it can easily get away
			if (p.getRank() == Rank.NINE)
				return;

			chaseRank = p.getRank().toInt();

		} else if (p.hasMoved()) {
			// chase unknown fleeing pieces as well
			if (p.getActingRankFlee() != Rank.NIL
				&& p.getActingRankFlee().toInt() <= 4)
				chaseRank = p.getActingRankFlee().toInt()+1;
			else
				// chase unknown pieces with invincible rank
				chaseRank = invincibleRank[1-p.getColor()]+1;
		} else
			// unknown and unmoved
			return;

		genDestTmp(false, p.getColor(), i, DEST_PRIORITY_LOW);
		// this nulls out incentive for chase sequences
		destTmp[i] = DEST_VALUE_NIL;

		boolean found = false;
		for (int j = chaseRank - 1; j >= 1; j--)

			// Multiple lower invincible ranks
			// and all the pieces of the rank
			// will chase an opponent piece.
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
			// But if its low ranked piece is known or
			// the opponent no longer has a piece as low,
			// the piece can be called up for the chase, so
			// getNeededPlanA or B are invoked.
			//
			// Ideally, the ai would predict if a chase
			// would be successful, and the only way I know
			// of doing that is to use the search tree.
			// So this code is the best that the ai can do.
			//
			if (j < invincibleRank[p.getColor()]) {
				genNeededPlanA(1-p.getColor(), j);
				genNeededPlanB(1-p.getColor(), j);
			} else if (j <= invincibleRank[1-p.getColor()]) {
			// If an opponent piece is chasing a low rank piece,
			// go ahead and move the piece if necessary
			// to combat the chase.
			// E.g. a Two chasing a Three has a chase rank of Three.
			// Chase the Two with a One: 3 - 2 = 1.
				if (p.getActingRankChase() != Rank.NIL
					&& j >= p.getActingRankChase().toInt() - 2) {
					genNeededPlanA(1-p.getColor(), j);
					genNeededPlanB(1-p.getColor(), j);
				} else {
					genPlanA(1-p.getColor(), j);
					genPlanB(1-p.getColor(), j);
				}

			// Only 1 non-invincible known rank is assigned
			// to chase an opponent piece.
			// This prevents a pile-up of ai pieces
			// chasing one opponent piece that is protected
			// by an unknown and possibly lower piece.
			//
			} else if (!found && knownRankAtLarge(1-p.getColor(),j)) {
				genNeededPlanA(1-p.getColor(), j);
				found = true;
			}

		// If lower ranks are not available, chase the piece
		// with the same rank IF both ranks are known
		// and the value of the ai rank is less, (implying
		// that the ai is winning).
		//
		// (Even if the ai is winning, it must keep its low
		// ranked pieces concealed for further advantage,
		// so that is why it will only chase a known piece
		// with another known piece of the same rank).
		if (!found 
			&& p.isKnown()
			&& knownRankAtLarge(1-p.getColor(),chaseRank)
			&& values[1-p.getColor()][chaseRank] <=
				values[p.getColor()][chaseRank])
			// go for an even exchange
			genNeededPlanA(1-p.getColor(), chaseRank);
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
		return Rank.getRanks(r-1)
			- trayRank[color][r-1]
			- knownRank[color][r-1];
	}

	private boolean knownRankAtLarge(int color, int r)
	{
		return (knownRank[color][r-1] != 0);
	}

	private int rankAtLarge(int color, int rank)
	{
		return (Rank.getRanks(rank-1) - trayRank[color][rank-1]);
	}


	// Target ai or opponent flag
	private void flagTarget(Piece flagp)
	{
		int color = flagp.getColor();
		int flagi = flagp.getIndex();

		genDestTmp(false, color, flagi, DEST_PRIORITY_ATTACK_FLAG);
		int r;
		// Assume attacker will target known flag with all ranks
		// and only Six through Nine if not known.
		// (Note: call genNeededPlanA to call up any remaining
		// high ranked pieces to discover flag)
		if (flagp.isKnown())
			r = 1;
		else
			r = 6;
		for (; r <= 9; r++)
			genNeededPlanA(1-color, r);
		genPlanA(1-color, Rank.UNKNOWN.toInt());

		// TBD:
		// How to bluff the opponent into thinking some other
		// piece is the flag while still guarding the flag?
		if (color == Settings.topColor
			&& flagp != flag[color])
			return;

		// Assume defender will try
		// to protect the flag with the strongest active rank(s)
		//
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
		int pd = grid.closestPieceDir(flagp, false);
		for (r = 1; r < 8; r++)
			if (activeRank[color][r-1] != null) {
				genDestTmp(false, 1 - color, flagi + pd, DEST_PRIORITY_DEFEND_FLAG);
				genPlanA(color, r);
				break;
			}
	}

	private void flagBombTarget(Piece flagp)
	{
		int color = flagp.getColor();
		int flagi = flagp.getIndex();
		int pd = grid.closestPieceDir(flagp, true);

		// assume opponent will target
		// with eights and unknown ranks
		genDestTmp(false, color, flagi+pd, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
		genPlanA(1-color, Rank.UNKNOWN.toInt());
		genPlanA(1-color, Rank.EIGHT.toInt());

		// If one of the bombs is known,
		// then the opponent probably has guessed
		// the location of the flag and all the other bombs
		// Try to protect the bombs with the closest rank that can
		// take an eight.
		//
		int rank = 0;
		int closest = DEST_VALUE_NIL;

		// protect the square in next to the bomb
		Piece p = getPiece(flagi+pd);
		if (p != null && p.getRank() == Rank.BOMB)
			pd *= 2;
		if (!isValid(flagi + pd))
			return;		// not sure where to protect

		for (int r = 7; r >= 1; r--)
			if (activeRank[color][r-1] != null) {
				genDestTmp(false, 1 - color, flagi + pd, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
				if (destTmp[activeRank[color][r-1].getIndex()] > closest)
					rank = r;
			}

		if (rank != 0)
			genNeededPlanA(color, rank);
	}

	// depends on possibleFlag() which can set the ai flag to known
	// if it detects an obvious bomb structure

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
				continue;
			}

			// If flag area had a bomb removed
			// or the opponent is adjacent to flag
			// or flag has a known bomb
			// then the ai guesses that the flag is known
			if (setup[j] == Rank.BOMB
				|| p.getColor() != pflag.getColor()
				|| (p.getRank() == Rank.BOMB && p.isKnown())) {
				pflag.setKnown(true);
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
			pflag.setAiValue(values[Settings.topColor][Rank.FOUR.toInt()]);
			flagTarget(flag[Settings.topColor]);

			// opponent color eights are now expendable
			values[Settings.bottomColor][Rank.EIGHT.toInt()] = values[Settings.bottomColor][Rank.SEVEN.toInt()];
			// Setting the flag to always known is questionable
			// because its location is almost never
			// known for sure by the opponent.  If the flag is set
			// to known, the AI will make every effort to prevent
			// the flag from attack.  Therein lies the problem.
			// If the flag is known and can be attacked successfully,
			// the AI is prone to leaving its pieces hanging, because it
			// assumes that the opponents best move is to take the
			// flag rather than its pieces.  This is a horizon effect,
			// and ideally the ebqs should return a worse result
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
			pflag.setKnown(true);
		}

		else if (pflag.isKnown()
			&& rankAtLarge(1-pflag.getColor(), Rank.EIGHT.toInt()) != 0) {
			// Flag value is worth more than an Eight.
			// (because we always want an eight to take the
			// flag rather than the protecting bombs.)
			pflag.setAiValue(aiFlagValue(pflag.getColor()));

			flagBombTarget(pflag);
		}
	}

	// Establish location it as a flag destination.

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

	// The ai sets the flag to be known on a flag that
	// was totally bombed.  That way, the bombs must be
	// removed in order for non-eights to find the flag.
	// But the ai also assumes
	// that unknown pieces take bombs and transform
	// into eights.  So this makes the ai vigilant in
	// protecting the bombed area from unknown pieces.

	private void genDestFlag(int b[])
	{
		int i = b[0];
		Piece flagp = getPiece(i);
		if (flagp.getColor() == Settings.bottomColor
			|| flagp.getRank() == Rank.FLAG) {
			flagp.setRank(Rank.FLAG);
			flagp.setKnown(true);
			flagp.setAiValue(aiFlagValue(flagp.getColor()));
			flag[flagp.getColor()] = flagp;
		}
		// else
		// hmmm, currently we are not able to change
		// rank or value for ai pieces

		// Send in a lowly piece to find out
		// Note: if the flag is still bombed, the
		// destination matrix will not extend past the bombs.
		// So the lowly piece will only be activated IF
		// there is an open path to the flag.
		genDestTmp(false, flagp.getColor(), i, DEST_PRIORITY_ATTACK_FLAG);
		for (int k = 9; k > 0; k--) {
			if (rankAtLarge(1-flagp.getColor(),k) != 0) {
				genNeededPlanA(1-flagp.getColor(), k);
				break;
			}
		}

		// Set any remaining pieces in the pattern
		// to actingRankChase(Rank.BOMB).
		// It doesn't matter if the piece really is a bomb or not.
		// This causes outcomeUnknown() to reduce the value
		// of the unknown piece, making it an undesirable target
		// for non-eights.
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

			p.setActingRankChase(Rank.BOMB);
		}

		// Remove bombs that surround a possible flag
		// this code is only for opponent bombs
		if (flagp.getColor() == Settings.bottomColor
			&& rankAtLarge(1-flagp.getColor(), Rank.EIGHT.toInt()) != 0
			&& found)
			for (int j = layer; b[j] != 0; j++)
				destBomb(b[j]);
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
				&& (!flagp.isKnown() || flagp.getRank() == Rank.FLAG)
				&& !flagp.hasMoved()) {
				boolean found = false;
				int k;
				for ( k = 1; b[k] != 0; k++ ) {
					Piece p = getPiece(b[k]);
					if (setup[b[k]] == Rank.BOMB
						|| (p != null && p.getRank() == Rank.BOMB)) {
						found = true;
						continue;
					}
					if (p == null
						|| p.isKnown()
						|| p.hasMoved()) {
						found = false;
						break;
					}

					
				} // k

				if (found) {
					maybe[maybe_count++]=b;

				// no bombs found, but it looks suspicious
				} else if (b[k] == 0) {
					maybe[maybe_count++]=b;

	// Add value to pieces in structure to encourage discovery.
	// Structures are worth more on the back ranks
	// because the flag is more likely in the rear.
	// note: *3 to outgain -depth late capture penalty

					for ( k = 1; b[k] != 0; k++ ) {
						int i = b[k];
						if (i >= 56)
							unmovedValue[i] = (i/11-7) * 3;
						else
							unmovedValue[i] = (4-i/11) * 3;
					}
				}
			} // possible flag
		} // bombPattern

		// revalue eights
		if (maybe_count < rankAtLarge(1-c, 8))
			// at least 1 opponent color eight is expendable
			values[1-c][Rank.EIGHT.toInt()] = values[1-c][Rank.SEVEN.toInt()];
			// try the back row first
		if (maybe_count >= 1 && maybe_count <= 3) {
			// only a few patterns left, so attack it
			for (int i = 0; i < maybe_count; i++)
				genDestFlag(maybe[i]);

			// eights become more valuable now
			if (maybe_count > rankAtLarge(1-c, 8))
				values[1-c][Rank.EIGHT.toInt()]
					+= (maybe_count - rankAtLarge(1-c, 8)) * 20;
		} else if (maybe_count == 0) {
			// player color c did not bomb his flag,
			// go for any remaining unmoved pieces


			for (int i = 12; i < 22; i++) {
				int b;
				if (c == Settings.topColor)
					b = i;
				else
					b = 132 - i;
				Piece p = getPiece(b); 
				if (p != null && !p.isKnown() && !p.hasMoved()) {
					unmovedValue[i] = 15;
					flagTarget(p);
				}
			}
				
		}

		} // colors
	}

	// Scan the board for isolated unmoved pieces (possible bombs).
	// Reset the piece rank to bomb so that the ai
	// move generation will not generate any moves for the piece
	// and the ai will not want to attack it.
	private void possibleBomb()
	{
		for (int i = 78; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece tp = getPiece(i);
			if (tp != null && !tp.isKnown() && !tp.hasMoved()) {
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
					// transform the piece into
					// a bomb.
					tp.setActingRankChase(Rank.BOMB);
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
	private void destBomb(int j)
	{
		Piece p = getPiece(j);
		if (p == null
			|| (p.isKnown() && p.getRank() != Rank.BOMB)
			|| p.hasMoved())
			return;

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

		// Send a lower ranked piece along to protect the eight
		// and possibly confuse the opponent about which is which
		// if both are unknown
		genDestTmp(false, p.getColor(), near, DEST_PRIORITY_LOW);
		int r;
		for (r = 5; r >= 1; r--)
			if (activeRank[1-p.getColor()][r-1] != null) {
				genNeededPlanA(1-p.getColor(), r);
				break;
		}

		if (r == 0) {
			for (r = invincibleRank[1-p.getColor()]; r >= 1; r--)
				if (rankAtLarge(1-p.getColor(), r) != 0) {
					genNeededPlanA(1-p.getColor(), r);
					break;
				}
		}

		// Send the miner 
		genDestTmp(true, p.getColor(), j, DEST_PRIORITY_LOW);
		genNeededPlanA(1-p.getColor(), 8);
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
				genDestTmp(true, p1.getColor(), frontPattern[i][0], DEST_PRIORITY_LOW);
				p1.setAiValue(aiBombValue(p1.getColor()));
				// p1.setActingRankChase(Rank.BOMB);
				genNeededPlanA(1-p1.getColor(), 8);

				genDestTmp(true, p2.getColor(), frontPattern[i][1], DEST_PRIORITY_LOW);
				p2.setAiValue(aiBombValue(p2.getColor()));
				// p2.setActingRankChase(Rank.BOMB);
				genNeededPlanA(1-p2.getColor(), 8);
			}
		}
	}

	private void genNeededPlanA(int color, int rank)
	{
		genPlanA(color, rank);
		if (activeRank[color][rank-1] == null)
			neededRank[color][rank-1] = true;
	}

	private void genNeededPlanB(int color, int rank)
	{
		genPlanB(color, rank);
		neededRank[color][rank-1] = true;
	}

	// Generate a matrix of consecutive values with the highest
	// value at the destination "to".
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
	private void genDestTmp(boolean guarded, int color, int to, int n)
	{
		int mult = 1;
		if (n > DEST_PRIORITY_ATTACK_FLAG)
			mult = 2;

		for (int j = 12; j <= 120; j++)
			destTmp[j] = DEST_VALUE_NIL;
		destTmp[to] = n;
		boolean found = true;
		while (found) {
		found = false;
		for (int j = 12; j <= 120; j++) {
			if (!isValid(j))
				continue;

			if (destTmp[j] != n) {
				if (destTmp[j] == n - mult)	// 1 move penalty
					found = true;
				continue;
			}

			Piece p = getPiece(j);
			if (p != null
				&& j != to
				&& !p.hasMoved()
				&& (p.getColor() == color || p.getRank() == Rank.BOMB))
				continue;

			// check for guarded squares
			if (guarded && j != to) {
				boolean isGuarded = false;
				for (int d : dir) {
					int i = j + d;
					if (!isValid(i))
						continue;
					Piece gp = getPiece(i);
					if (gp == null 
						|| gp.getColor() != color
						|| !gp.hasMoved())
						continue;
					
					int result = (gp.getRank()).winFight(Rank.EIGHT);
					if (result == Rank.UNK || result == Rank.WINS)
						isGuarded = true;
				}
				if (isGuarded)
					continue;
			}

			// set the neighbors
			for (int d : dir) {
				int i = j + d;
				if (!isValid(i) || destTmp[i] != DEST_VALUE_NIL)
					continue;
				destTmp[i] = n - mult;

				// if the piece blocks the way
				// add a 1 move penalty to its neighbors
				if (p != null && j != to) {
					destTmp[i] -=  mult;
					continue;
				}
				found = true;

			}
		}
		n -= mult;
		}
	}

	private void genPlanA(int color, int rank)
	{
		for (int j = 12; j <= 120; j++)
			if (planA[color][rank-1][j] < destTmp[j])
				planA[color][rank-1][j] = destTmp[j];
	}

	private void genPlanB(int color, int rank)
	{
		for (int j = 12; j <= 120; j++)
			if (planB[color][rank-1][j] < destTmp[j])
				planB[color][rank-1][j] = destTmp[j];
	}

	//
	// suspectedRank is based on actingRankChase.
	// if the piece has chased another piece,
	// the ai guesses that the chaser is a lower rank, probably
	// just one rank lower.  If there are no lower ranks, then
	// the chaser may be of the same rank (or perhaps higher, if
	// bluffing)
	//
	// TBD: this should be assigned in constructor statically
	//
	public Rank suspectedRank(Piece p)
	{
		assert p.getRank() == Rank.UNKNOWN : "piece is known?";
		Rank rank = p.getActingRankChase();
		if (rank == Rank.NIL || rank == Rank.UNKNOWN)
			return Rank.UNKNOWN;
		int r = rank.toInt();
		for (int i = r - 1; i > 0; i--)
			if (rankAtLarge(p.getColor(), i) != 0)
				return Rank.toRank(i);

		if (rankAtLarge(p.getColor(), r) != 0)
			return rank;

		// attacker is a bad bluffer
		return Rank.UNKNOWN;
	}

	//
	// TBD: this should be assigned in constructor statically
	//
	public boolean isInvincible(Piece p) 
	{
		Rank rank = p.getRank();
		if (rank == Rank.UNKNOWN && p.getActingRankChase() != Rank.NIL)
			rank = p.getActingRankChase();
		return (rank.toInt() <= invincibleRank[p.getColor()]);
	}

	public int planva(int color, int r, int from, int to)
	{
		int vto = planA[color][r][to];
		int vfrom = planA[color][r][from];
		if (vto != DEST_VALUE_NIL
			&& vfrom != DEST_VALUE_NIL)
			return vto - vfrom;
		return 0;
	}

	public int planvb(int color, int r, int from, int to)
	{
		int vto = planB[color][r][to];
		int vfrom = planB[color][r][from];
		if (vto != DEST_VALUE_NIL
			&& vfrom != DEST_VALUE_NIL)
			return vto - vfrom;
		return 0;
	}

	// To be consistent, the remaining piece after an attack is
	// assigned a known rank less than the attacker rank.
	//
	// One case where this is of particular importance
	// is where the ai piece was an eight or less:
	// the rank of the unknown becomes a low value so
	// that the piece cannot be an eight and therefore
	// cannot take a bomb.  This encourages
	// an ai piece to attack an unknown piece that
	// is within striking distance of a flag bomb
	//
	// This also can help the ai protect another ai piece from an
	// unknown attacker, because if the attacker takes the first
	// ai piece, the ai has a good shot at recapture if the
	// attacker stays on the board with a known rank.
	// Of course, the attacker could be much stronger than predicted,
	// so the ai could make a serious error as well.
	//
	public void makeWinner(Piece p, Rank rankWon)
	{
		if (p.getRank() == Rank.UNKNOWN) {
			int newRank = 0;
			Rank chaseRank = p.getActingRankChase();
			Rank fleeRank = p.getActingRankFlee();
			if (chaseRank != Rank.NIL
				&& chaseRank == Rank.BOMB)
				return;
			else if (avgUnkRank < rankWon.toInt())
				newRank = avgUnkRank;
			else if (rankWon == Rank.ONE)
				newRank = 10;
			else {
				// The usual Stratego attack strategy is one
				// rank lower.

				// If the piece has chased a lower ranked piece,
				// then the new rank is based on the chase rank
				if (chaseRank != Rank.NIL
					&& chaseRank.toInt()-1 < rankWon.toInt())
					newRank = chaseRank.toInt()-1;
				else
					newRank = rankWon.toInt()-1;

				// If the guessed rank is not on the board,
				// try the next lower rank
				while (newRank != 0
					&& unknownRankAtLarge(p.getColor(), newRank) == 0)
					newRank--;

				// Threes are so valuable that it is safest
				// to assume that the attacker is a One.
				if (rankWon == Rank.THREE
					&& newRank == 2
					&& unknownRankAtLarge(p.getColor(), 1) != 0)
					newRank = 1;

				if (newRank == 0) // no unknown ranks found
				for (newRank = rankWon.toInt()-1; newRank != 10 && unknownRankAtLarge(p.getColor(), newRank) == 0; newRank++);
			}
			if (newRank == 8)
				newRank = 7;

			if (newRank != 0)
				p.setRank(Rank.toRank(newRank));
		}
	}

	public void move(BMove m, int depth, boolean unknownScoutFarMove)
	{
		Piece fp = getPiece(m.getFrom());
		Piece tp = getPiece(m.getTo());
		moveHistory(fp, tp, m);

		setPiece(null, m.getFrom());
		Rank fprank = fp.getRank();
		int vm = 0;
		int r = fprank.toInt()-1;
		int color = fp.getColor();

		if (tp == null) { // move to open square
			if (!fp.isKnown() && fp.moves == 0) {
				if (neededRank[fp.getColor()][r])
					vm -= 1;
				else
					vm += -aiMovedValue(fp) -unmovedValue[m.getFrom()];
			}

			// give a given piece planA value
			// or any piece of a given rank if the rank is needed
			if ((activeRank[color][r] == fp
				|| (activeRank[color][r] == null && neededRank[color][r]))
				// only give nines planA for adjacent moves
				// because they can reach their destination
				// more quickly.
				&& !unknownScoutFarMove) {
				vm += planva(color, r, m.getFrom(), m.getTo());
			} else if (neededRank[color][r])
				vm += planvb(color, r, m.getFrom(), m.getTo());

			fp.setIndex(m.getTo());
			setPiece(fp, m.getTo());

			if (unknownScoutFarMove) {
				// Moving an unknown scout
				// more than one position makes it known.
				// That changes its value.
				fp.setRank(Rank.NINE);
				vm -= makeKnown(fp);
			}

		} else { // attack
			Rank tprank = tp.getRank();

			// note: use actual value for ai attacker
			// because unknown ai attacker knows its own value
			// but opponent attacker is just guessing
			int fpvalue = actualValue(fp);

			switch (winFight(fp, tp)) {
			case Rank.EVEN :
				assert fprank != Rank.UNKNOWN : "fprank is unknown?";
				vm += actualValue(tp) - fpvalue;
				setPiece(null, m.getTo());
				break;

			case Rank.LOSES :
				vm -= fpvalue;
				makeWinner(tp, fprank); // call before makeKnown
				vm += makeKnown(tp);
				
				// Unknown moved and unmoved pieces
				// have bluffing value.
				//
				// If the bluffing value is high,
				// unknown Red Eight moves towards Red 4
				// to protect Red 4, because it hopes that
				// known Blue 2 will think that it is actually
				// Red One.  But this is a high stakes
				// and unnecessary bluff.
				//
				// A Four is worth 75 and a Two is worth 250.
				// So if the bluffing value is 30% of the
				// defender (e.g. Two), the ai would still
				// make this high stakes bluff.  So bluffing
				// value is 20% of the defending piece value,
				// which means that the ai will bluff
				// only as a last resort.
				//
				// R8 --
				// -- --
				// R4 --
				// B2 --
				if (depth != 0
					&& !fp.isKnown()
					&& !(isInvincible(tp)
						|| tprank == Rank.ONE
						&& hasSpy(Settings.topColor))
					&& fp.getActingRankFlee() == Rank.NIL
					&& !unknownScoutFarMove)
					vm += aiBluffing(apparentValue(tp)/5, m.getTo());
				break;

			case Rank.WINS:
				// if the target is known, attacker
				// sees the actual value of the piece
				if (tp.isKnown())
					vm += actualValue(tp);
				else {  // tp is unknown

					// attacker sees
					// the apparent value of the piece,
					// and the defender sees the
					// risk of loss, which does not exceed
					// the actual value of the piece.
					// Both need to be considered
					// in assigning a value to an
					// attack on an unknown piece.
					//
					// In the example below, AI Blue Spy
					// an Blue Four and unmoved and
					// unknown.  Red Three is known.
					// If the actual value of the target
					// piece were assigned to move,
					// then Blue Four would attack
					// Red Three because the ai would
					// guess that Red Three is going to
					// move left and attack Blue Spy,
					// which is more valuable than Red Three.
					// BS B4
					// -- R3
					// But Red Three does not *see* the
					// actual value of Blue Spy.  It
					// only sees the apparent value
					// but the ai has to assign some risk
					// (low) that Red Three will attack
					// its unknown unmoved Blue Spy.
					//
					// In the following example, AI Blue Spy
					// is unknown, Red Three and Blue Two
					// are known.   Blue Two should not
					// approach Red Three because it
					// will flee into Blue Spy.
					// R3 BS ?B
					// -- ?B
					// B2 
					//
					// If the apparent value was assigned
					// to the target, Blue Two would
					// approach Red Three.
					//
					// These two examples further suggest
					// that the assignment value is
					// between actual and apparent value.
					
					int apparentV = apparentValue(tp);

					if (tp.moves == 0) {
					// tp is not known and has not moved
					// so it could be a bomb
					// which deters attacking
					// an unknown unmoved piece
						int actualV = actualValue(tp);
						if (apparentV + actualV / 10 < actualV)
							vm += apparentV + actualV / 10;
						else
							vm += actualV;

					// The attacker loses its entire
					// value. This entices
					// the ai to not move
					// a piece subject to attack.
					// or attack an unmoved unknown piece

					// Note: usually the tp is AI,
					// but if a AI fp is an invincible 8,
					// or if there are no more bombs,
					// then this code is also executed.

						if (fprank != Rank.EIGHT)
							vm -= fpvalue;

					} else {
					// the unknown defender has moved
					// this makes it much more likely
					// to be attacked, so the risk
					// is much higher.
						int actualV = actualValue(tp);
						if (apparentV + actualV / 2 < actualV)
							vm += apparentV + actualV / 2;
						else
							vm += actualV;

					}

					// Unknown pieces
					// also have bluffing value
						if (depth != 0
						&& !isInvincible(fp)
						&& tp.getActingRankFlee() == Rank.NIL)
						vm -= aiBluffing(fpvalue/10, m.getTo());

				} // tp unknown

				makeWinner(fp, tprank); // call before makeKnown()
				vm -= makeKnown(fp);
				setPiece(fp, m.getTo()); // won
				fp.setIndex(m.getTo());
				break;

			case Rank.UNK:
				int tpvalue = apparentValue(tp);
				// note: fpvalue is actualValue

				// fp or tp is unknown

				if (fp.getColor() == Settings.topColor) {
				// AI is attacker (fp)

				// The lower the AI piece rank,
				// the more likely it will win
				// against a moved unknown.  But lower
				// ranked pieces have higher value
				// and therefore more risk.  The risk
				// is not as high as the actual piece value,
				// if the opponent has not guessed the
				// piece rank, because the opponent
				// will not likely risk its superior pieces
				// against unknowns.
				//
				// But there are situations where the
				// opponent will weigh the risk of taking
				// an unknown piece.  In the diagram below,
				// all pieces are unknown.  Red 3 moves
				// to the empty square above Blue 2. Blue
				// guesses that the Red 3 is probably not
				// not an unknown Red One, because Blue
				// has already guessed that Red One
				// is some other piece.  Blue has also guessed
				// that Red 3 is acting like a low ranked
				// piece.  So Blue Two takes Red 3 and wins.
				//
				// -- R3
				// B2 B8
				// 
				// This could also turn out to be loss
				// if Red 3 turned out to be a Red 9, because
				// Blue 2 loses stealth.  Or if Red 3 really
				// was Red 1.
				//
				// So the situation can be difficult to assess.
				// Human players may be better able to assess
				// the risk, so it is best for the ai to
				// avoid unknown exchanges.
				//
				// The risk is set to the apparent piece value
				// plus a factor related to the actual
				// piece value which deters high valued
				// pieces from entering into unknown
				// exchanges.
				//
				// (Note: when both pieces are unknown,
				// apparent piece value is the unknown
				// piece value.  So if fprank is equal
				// to avgUnkRank, the value will be
				// a small loss (the actualValue factor).
			
					if (tp.hasMoved()) {
						// tp is unknown
						int diff = fprank.toInt() - avgUnkRank;
						tpvalue = tpvalue * (20 - diff) / 20;
						fpvalue = apparentValue(fp) + actualValue(fp)/10;
					}

					vm = outcomeUnknown(fp, tp, fprank, tprank, fpvalue, tpvalue);
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

			// Another solution is to allow the ai piece
			// to survive if the value of the move is positive.

			// If the ai piece does not survive, it is prone
			// to the following blunder.  In the position below,
			// Red Four may attack the unknown piece because
			// if Red Four does not survive, then the ai
			// cannot see the easy recapture.

			// R4
			// ??
			// B3

			// TBD
			// Currently ai piece does not survive

			// do not add in makeKnown() because
			// tpvalue and fpvalue already
			// contains stealth and was already added
			// in outcomeUnknown()

			//	if (vm < 0) {

				// the ai guesses that an attack on an unknown
				// unmoved piece is a bomb.  It gets the
				// stealth value of an unknown.
				if (tp.moves == 0) {
					makeKnown(tp);
					tp.setRank(Rank.BOMB);
				} else {
					makeWinner(tp, fprank);
					makeKnown(tp);
				}


			//	} else {
			//		makeKnown(fp);
			//		setPiece(fp, m.getTo()); // won
			//		fp.setIndex(m.getTo());
			//	}


				} else {

			// AI is defender (tp)

					if (!tp.isKnown() && tp.hasMoved()) {
						int diff = tprank.toInt() - avgUnkRank;
						tpvalue += actualValue(tp)/10;
						fpvalue = fpvalue * (20 - diff) / 20;
					}

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

					vm = -outcomeUnknown(tp, fp, tprank, fprank, tpvalue, fpvalue);
			// If the AI appears to make
			// an obviously bad move, often it
			// is because it did not guess correctly
			// what happened to the pieces
			// after an unknown attack.
			// Any outcome is possible.
			//
			// If its defending piece has not moved,
			// the AI guesses that the defender
			// will remain and the attacker
			// loses its piece.  This matches
			// what the attacker is likely
			// thinking, because the piece
			// could be a bomb.

			// However, if the AI piece has moved,
			// assume worst case for AI: AI loses.
				if (tp.moves == 0) {
					makeWinner(fp, tprank);
			// do not add in makeKnown() because fpvalue
			// contains stealth and was already added
			// in outcomeUnknown()
					makeKnown(fp);

					setPiece(fp, m.getTo()); // won
					fp.setIndex(m.getTo());
				}
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

		if (fp.getColor() == Settings.topColor)
			value += vm;
		else
			value -= vm;
		fp.moves++;
	}

	public int outcomeUnknown(Piece fp, Piece tp, Rank fprank, Rank tprank, int fpvalue, int tpvalue)
	{
		int vm = 0;
		assert tp.getRank() == Rank.UNKNOWN : "target piece is known?";

		// unknown but suspected BOMBs are handled in WINS or LOSES
		assert (tp.getActingRankChase() != Rank.BOMB) : " tp is bomb?";

		// fp or tp is unknown

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

		// If the unknown has chased an unknown opponent piece,
		// the unknown is probably trying to discover an unknown
		// opponent piece for some reason, so it is best to
		// thwart its objective.  Avoid the piece if possible..
		if (tp.getActingRankChase() == Rank.UNKNOWN)
			tpvalue -= 4;

		// If an opponent piece fled from (or neglected to attack)
		// an ai piece, then it is a more attractive target
		// than other unknowns
		if (tp.getActingRankChase() == Rank.NIL
			&& tp.getActingRankFlee() != Rank.NIL
			&& tp.getActingRankFlee().toInt() >= fprank.toInt())
			tpvalue += 4;

		vm += tpvalue - fpvalue;

		return vm;
	}

        protected void moveHistory(Piece fp, Piece tp, BMove m)
        {
                undoList.add(new UndoMove(fp, tp, m.getFrom(), m.getTo(), hash));
	}


	public void undo(int valueB)
	{
		value = valueB;
		UndoMove um = getLastMove();
		Piece fp = um.getPiece();
		fp.copy(um.fpcopy);
		if (um.tp != null)
			um.tp.copy(um.tpcopy);

		setPiece(um.tp, um.getTo());
		setPiece(fp, um.getFrom());
		undoList.remove(undoList.size()-1);
	}
	
	// If the prior move was to the target square,
	// then the opponent must consider whether the ai is bluffing.
	// This could occur if the opponent moves a piece next to an
	// unknown ai piece or if the ai moved its unknown piece next to an
	// opponent piece.
	//
	// Either way,add bluffing value.
	//
	// However, this gets stale after the next move, because if the ai
	// does not attack, it probably means the ai is less strong,
	// and the opponent will know it.
	public int aiBluffing(int value, int to)
	{
		// (note that getLastMove(2) is called to get the prior
		// move, because the current move is already on the
		// stack when aiBluffing is called)
		UndoMove prev = getLastMove(2);
		if (to == prev.getTo())
			return value;
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


	// Stealth value is expressed as a percentage of the piece's
	// actual value.  An unknown Marshal should not take a Six
	// or even a Five, but certainly a Four is worthwhile.
	// An unknown General should not take a Six, but a Five
	// would be tempting.
	//
	// If a piece has moved, then much is probably guessed about
	// the piece already, so it has half of its stealth value.
	//
	private int stealthValue(int color, Rank r)
	{
		// stealth is worth a Four to a Marshal
		// (to other pieces, a percentage equal to Four / Marshal)
		long v = values[color][r.toInt()];
		v *= values[color][4];	// Four
		v /= values[color][1];	// Marshal
		return (int)v;
	}

	private int stealthValue(Piece p)
	{
		if (p.aiValue() != 0)
			return p.aiValue() / 9;
		else
			return stealthValue(p.getColor(), p.getRank());
	}

	private int makeKnown(Piece p)
	{
		if (!p.isKnown()) {
			p.setKnown(true);
			return stealthValue(p);
		}
		return 0;
	}

	// If a piece has already moved, then
	// the piece cannot be a flag or a bomb,
	// so the attacker doesn't gain as much info
	// by discovering it.  (The ai scans for
	// for unmoved piece patterns as a part
	// of its flag discovery routine).

	// If the rank is needed, the penalty is reduced
	// so that the ai will move the piece if it can make progress
	// towards its destination.

	// The difference in value between a moved piece and
	// an unmoved unknown piece is what drives the ai to attack
	// unmoved unknown pieces.

	private int aiMovedValue(Piece p)
	{
		return values[p.getColor()][Rank.UNKNOWN.toInt()] / 5;
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
			+ stealthValue(1-color, Rank.EIGHT)
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

	// Set the flag value higher than a worthwhile bomb
	// so that an unknown miner that removes a bomb to get at the flag
	// will attack the flag rather than another bomb.

	private int aiFlagValue(int color)
	{
		return aiBombValue(color) + 10;
	}

	private int apparentValue(Piece p)
	{
		return aiValue(p, Rank.UNKNOWN);
	}

	private int actualValue(Piece p)
	{
		return aiValue(p, p.getRank());
	}

	private int aiValue(Piece p, Rank rank)
	{
		// Piece value (bomb, flag) overrides calculated value.
		// Piece value does not depend on being known
		// because the ai sets it when the piece value is obvious.
		int v = p.aiValue();

		if (v == 0) {
			Rank r = p.getRank();

			if (p.isKnown()) {
				v = values[p.getColor()][r.toInt()];
			} else {
			// The addition of stealthValue to unknown value
			// makes AI unknowns vary in value.
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

				Random rnd = new Random();
				v = values[p.getColor()][rank.toInt()]
					+ stealthValue(p.getColor(), rank)

				// discourage stalling
				// TBD: stalling factor should only
				// apply in repetitive cases,
				// not generally.  Otherwise, ai
				// makes bad decisions when it
				// is actually making progress.
				// + recentMoves.size()/10

				// we add a random 1 point to the value so that
				// if the ai has a choice of attacking two or more unknown
				// pieces during a series of attacks,
				// it will alternate direction.
				+ rnd.nextInt(2);

				if (p.moves == 0)
					v += unmovedValue[p.getIndex()];
				else
					v -= aiMovedValue(p);

				// An unknown chase piece could be a highly
				// valued piece, but maybe not.
				// So add a percentage (25%) of the alleged value.
				// This makes it a more desirable target
				// for discovery (or possibly attack).
				if (p.getActingRankChase() != Rank.NIL
					&& p.getActingRankChase().toInt() <= 5)
					v += values[p.getColor()][p.getActingRankChase().toInt()] / 4;

				if (p.isBlocker())
					v += 10;

			} // piece not known
		} // v == 0

		return v;
	}

	int winFight(Piece fp, Piece tp)
	{
		Rank fprank = fp.getRank();
		Rank tprank = tp.getRank();
		int result = winRank[fprank.toInt()][tprank.toInt()];
		if (result == Rank.UNK) {
		if (tp.getColor() == Settings.topColor) {
			// ai is defender (tp)
			assert fp.getRank() == Rank.UNKNOWN : "opponent piece is known?";

			// How to guess whether the marshal will win or lose
			// when attacked by an unknown piece.
			//
			// The most conservative approach is to assume that
			// any unknown piece could be a spy, if the spy is
			// still on the board.
			//
			// The approach taken is that if a piece
			// has an actingRankChase, then it is not a spy.
			// That is because it is unlikely (although possible) that
			// that the opponent would have moved its spy into a position
			// where it could be attacked.

			if (tprank == Rank.ONE
				&& hasSpy(Settings.bottomColor)
				&& (fp.getActingRankChase() == Rank.NIL
					|| fp.getActingRankChase() == Rank.BOMB))
				return Rank.WINS; // maybe not, see above

			else if (isInvincible(tp))
				return Rank.LOSES;	// for sure

			else if (fp.getActingRankChase() == Rank.BOMB)
				return Rank.LOSES;	// maybe not

			else if (fp.getActingRankChase() != Rank.NIL
				&& fp.getActingRankChase().toInt() <= tp.getRank().toInt())
				return Rank.WINS;	// maybe not

			// Note: Acting Rank is an unreliable predictor
			// of actual rank.
			//
			// High tp actingRankFlee is very unreliable because
			// often an unknown opponent piece (such as a 1-3)
			// will eschew taking a known ai piece (such as a 5-7)
			// to avoid becoming known.
			//
			// If the fp actingRankFlee is >= tp rank
			// it is probably only an even exchange and
			// likely worse, so the move is a loss.
			// However, it is only a loss for low actingRankFlee
			// or for high tp rank.

			else if (fp.getActingRankFlee() != Rank.NIL
				&& fp.getActingRankFlee() != Rank.UNKNOWN
				&& fp.getActingRankFlee().toInt() >= tprank.toInt()
				&& (fp.getActingRankFlee().toInt() <= 4
					|| tprank.toInt() >= 5))
				return Rank.LOSES;	// maybe not

			// pieces that fled from unknowns can be worthwhile
			// attacks because (1) either they are low ranks
			// evading discovery or (2) they are high ranks
			// that realize the risk of loss is high
			else if (tp.moves != 0
			 	&& fp.getActingRankFlee() == Rank.UNKNOWN
			 	&& tprank.toInt() >= 5
			 	&& tprank != Rank.EIGHT)
			 	return Rank.LOSES; // maybe not, but who cares?

			// The ai assumes that any unknown piece
			// will take a non-worthless bomb
			// because of flag safety.  All other attacks
			// on bombs are considered lost.
			//
			// However, if the unknown has a low chase rank,
			// the ai assumes that it is not an eight.
			// Of course, the opponent could be bluffing,
			// but this would be a very skillful bluff.
			//
			else if (tprank == Rank.BOMB) {
				if (tp.aiValue() != 0
					&& rankAtLarge(Settings.bottomColor, 8) != 0
					&& (fp.getActingRankChase() == Rank.NIL
						|| fp.getActingRankChase().toInt() >= 8)) {
					fp.setRank(Rank.EIGHT);
					return Rank.WINS;	// maybe not
				} else
					return Rank.LOSES;	// maybe not

			// Any piece will take a SPY.
			// However, the spy is always unknown so
			// the opponent may not know which piece is the spy.
			// So if the spy has not moved, it is considered
			// safe, but once it has moved, it is considered
			// a known target.
			} else if (tp.moves != 0
				&& tprank == Rank.SPY)
				return Rank.LOSES;

			// TBD: see below
			// don't let any unknown get too close
			// else if (planA[fp.getColor()][Rank.UNKNOWN.toInt()-1][fp.getIndex()] >= DEST_PRIORITY_DEFEND_FLAG_BOMBS - 2)
			//	return Rank.LOSES;

		} else {
			// ai is attacker (fp)
			assert tp.getRank() == Rank.UNKNOWN : "opponent piece is known?";
		// by definition, invincible rank wins or is even
		// with unknown moved pieces.
		if (tp.moves != 0
			&& isInvincible(fp)) {
			// if a piece fled from a 1-4
			// and we can take it, take it.
			if (suspectedRank(tp) == fp.getRank())
				return Rank.EVEN;	// maybe not
			else
				return Rank.WINS;	// maybe not

		// an invincible eight wins unknown unmoved pieces as well
		} else if (tp.moves == 0
			&& fp.getRank() == Rank.EIGHT
			&& isInvincible(fp))
			return Rank.WINS;

		else if (tp.getActingRankChase() == Rank.BOMB) {
			if (fp.getRank() == Rank.EIGHT)
				return Rank.WINS;	// maybe not
			else
				return Rank.LOSES;	// maybe not
		} else if (tp.getActingRankChase() != Rank.NIL
			&& tp.getActingRankChase().toInt() <= fp.getRank().toInt())
			return Rank.LOSES;	// maybe not

		// A spy almost always loses when attacking an unknown
		else if (fprank == Rank.SPY)
			return Rank.LOSES;	// maybe not

		// test actingRankFlee after actingRankChase
		// in case piece has both
		else if (tp.moves != 0
			&& tp.getActingRankFlee() != Rank.NIL
			&& tp.getActingRankFlee() != Rank.UNKNOWN
			&& tp.getActingRankFlee().toInt() >= fprank.toInt()
			&& (tp.getActingRankFlee().toInt() <= 4
				|| fprank.toInt() >= 5))
			return Rank.WINS;

		else if (tp.moves != 0
			&& tp.getActingRankFlee() == Rank.UNKNOWN
			&& fprank.toInt() >= 5
			&& fprank != Rank.EIGHT)
			return Rank.WINS; // maybe not, but who cares?

		// TBD: this code is a nice idea, but because the
		// flag really isn't known, it leads to surprisingly
		// blunders.
		// don't let any unknown get too close
		// else if (planA[tp.getColor()][Rank.UNKNOWN.toInt()-1][fp.getIndex()] >= DEST_PRIORITY_DEFEND_FLAG_BOMBS - 2)
		// 	return Rank.WINS;

		} // ai attacker
		} // result UNK
		
		return result;
	}


	boolean hasSpy(int color)
	{
		return rankAtLarge(color, Rank.SPY.toInt()) != 0;
	}

}


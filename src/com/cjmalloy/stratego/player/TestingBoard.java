/*.
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
	private static final int DEST_PRIORITY_LOW = -1;
	private static final int DEST_VALUE_NIL = -99;

	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected int[][] activeRank = new int[2][15];	// moved rank index
	protected int[][] activeRankID = new int[2][15];	// moved rank ID
	protected int[][] trayRank = new int[2][15];	// ranks in trays
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[][][] destValue = new int[2][15][121];	// encourage forward motion
	protected int[] destTmp = new int[121];	// encourage forward motion
	protected int[][] winRank = new int[15][15];	// winfight cache
	protected int[] piecesInTray = new int[2];
	protected int[] piecesNotBomb = new int[2];
	protected boolean looping;
	protected boolean possibleSpy = false;
	protected int value;	// value of board

	static final int [] startValues = {
		0,
		400,	// 1 Marshal
		200, 	// 2 General
		100, 	// 3 Colonel
		75,	// 4 Major
		45,	// 5 Captain
		30,	// 6 Lieutenant
		25,	// 7 Sergeant
		60,	// 8 Miner
		25,	// 9 Scout
		200,	// Spy
		0,	// Bomb (valued by code)
		0,	// Flag (valued by code)
		45,	// Unknown
		0	// Nil
	};
	int [][] values = new int[2][15];

	static final int [][] bombPattern = {
		{ 12, 13, 23, 23 },	// corner
		{ 14, 13, 25, 15 },	// back row
		{ 15, 14, 26, 16 },	// back row
		{ 16, 15, 27, 17 },	// back row
		{ 17, 16, 28, 18 },	// back row
		{ 18, 17, 29, 19 },	// back row
		{ 19, 18, 30, 20 },	// back row
		{ 21, 20, 32, 32 },	// corner
		{ 12, 35, 45, 25 },	// corner double layer
		{ 21, 42, 54, 30 }	// corner double layer
	};

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
			piecesNotBomb[c] = 0;
			piecesInTray[c] = 0;
			for (int j=0;j<15;j++) {
				knownRank[c][j] = 0;
				activeRank[c][j] = 0;
				activeRankID[c][j] = 0;
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
			Piece p = getPiece(i);
			if (p != null) {
				// copy pieces to be threadsafe
				// because piece data is updated during ai process
				Piece np = new Piece(p);
				grid.setPiece(i, np);
				np.setAiValue(0);
				if (!p.isKnown()) {
					if (p.getColor() == Settings.bottomColor)
						np.setRank(Rank.UNKNOWN);
			 	} else {
					int r = p.getRank().toInt();
					knownRank[p.getColor()][r-1]++;
				}

				if (p.hasMoved() || p.isKnown()) {
					// count the number of moved pieces
					// to determine further movement penalties
					Rank rank = p.getRank();
					int r = rank.toInt();
					activeRank[p.getColor()][r-1] = i;
					activeRankID[p.getColor()][r-1]=p.getID();
					if (rank != Rank.BOMB)
						piecesNotBomb[p.getColor()]++;
				}
			}
		}

		// add in the tray pieces to knownRank and to trayRank
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			knownRank[p.getColor()][r-1]++;
			trayRank[p.getColor()][r-1]++;
			piecesInTray[p.getColor()]++;
		}

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
					p.setRank(Rank.BOMB);
				}
			}
		}
		}

		// How to guess whether the marshal can be attacked by a spy.
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
		possibleSpy = rankAtLarge(Settings.bottomColor, Rank.SPY.toInt());

		// A rank becomes invincible when all lower ranking pieces
		// are gone or *known*.
		//
		// Invincibility means that a rank can attack unknown pieces
		// with the prospect of a win or even exchange.
		//
		for (int c = RED; c <= BLUE; c++) {
			int rank = 0;
			for (;rank<9;rank++) {
				if (knownRank[c][rank] != Rank.getRanks(rank))
					break;
			}
			invincibleRank[1-c] = rank + 1;

			// revalue eights
			if (trayRank[c][7] >= 3)
				values[c][Rank.EIGHT.toInt()]
					*= (4 - (5 - trayRank[c][7]));

			// demote the value of the spy if there is
			// no opponent marshal left at large
			if (!rankAtLarge(1-c, 1))
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
			for (int j=12; j <= 120; j++)
				destValue[c][rank][j] = DEST_VALUE_NIL;
		}

		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			Rank rank = p.getRank();
			if (rank == Rank.BOMB)
				continue;

 			if (rank == Rank.FLAG) {
				// ai flag discovery
				flagSafety(i);
				continue;
			}

			// Encourage lower ranked pieces to find pieces
			// of higher ranks.
			//
			// Note: this is a questionable heuristic
			// because the ai doesn't know what to do once
			// the piece reaches its destination.
			// However, the position can evolve into one
			// where material can be gained.
			if (p.isKnown())
				chase(rank.toInt(), i);
			else if (p.hasMoved()) {
				// chase unknown fleeing pieces as well
				if (p.getActingRankFlee() != Rank.NIL
					&& p.getActingRankFlee().toInt() <= 4)
					chase(p.getActingRankFlee().toInt(), i);
		
				// Chase unknown moved with pieces of invincible rank.
				chase(invincibleRank[1-p.getColor()], i);
			} else {
				// Encourage lower ranked pieces to discover
				// unknown and unmoved pieces
				// Back rows may be further, but are of
				// equal destination value.
				int y = Grid.getY(i);
				if (y <= 3)
					y = -y;
				genDestTmp(false, p.getColor(), i, DEST_PRIORITY_LOW + y - 9);
				genDestValue(1-p.getColor(), 5);
				genDestValue(1-p.getColor(), 6);
				genDestValue(1-p.getColor(), 7);
				genDestValue(1-p.getColor(), 9);
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
				if (activeRank[c][r-1] != 0) {
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
		// if the opponent spy is gone or the ai one is gone.
		//
		// bug: So one way to fool the ai is to make the flag 
		// isolated and then the ai will not attack it.
		//
		if (!possibleSpy || !rankAtLarge(Settings.topColor, 1))
			possibleBomb();

		possibleFlag();
		valueLaneBombs();
		genWinRank();
		looping = dejavu();
	}

	// Chase the piece at position i using pieces of "rank" or lower.
	//
	// Only 1 rank is assigned to chase an opponent piece
	// to prevent a pile-up of ai pieces
	// chasing one opponent piece that is protected
	// by an unknown and possibly lower piece.
	protected void chase(int chaseRank, int i)
	{
		Piece p = getPiece(i);

		genDestTmp(false, p.getColor(), i, DEST_PRIORITY_LOW);
		// this nulls out incentive for chase sequences
		destTmp[i] = DEST_VALUE_NIL;

		int protector;
		for (protector = 1; protector < chaseRank; protector++)
			if (rankAtLarge(p.getColor(),protector))
				break;

		boolean found = false;
		for (int j = chaseRank - 1; j >= 1; j--)
		if (rankAtLarge(1-p.getColor(),j)) {
			genNeededDest(1-p.getColor(), j);
			found = true;
			if (j > protector)
				break;
		}
		if (!found) {
			// go for an even exchange
			genNeededDest(1-p.getColor(), chaseRank);
		}
	}

	// cache the winFight values for faster evaluation
	private void genWinRank()
	{
		for (Rank frank : Rank.values())
		for (Rank trank : Rank.values()) {
			int v;
			if (frank == Rank.UNKNOWN) {
				// ai must be defender

				// The following results are unknown
				// but good guesses based simply on rank
				// and some board information.
				// The ai assumes that any unknown piece
				// will take a bomb because of flag safety.
				// Any piece will take a SPY
				if (trank == Rank.BOMB && rankAtLarge(Settings.bottomColor, 8)
					|| trank == Rank.SPY)
					v = Rank.WINS;
				else
					v = frank.winFight(trank);
			} else
				v = frank.winFight(trank);
			winRank[frank.toInt()][trank.toInt()] = v;
		}
	}


	private boolean rankAtLarge(int color, int rank)
	{
		return (trayRank[color][rank-1] != Rank.getRanks(rank-1));
	}


	private void flagTarget(int j)
	{
		Piece p = getPiece(j);

		// assume opponent will target
		// with eights and unknown ranks
		genDestTmp(false, p.getColor(), j, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
		genDestValue(1-p.getColor(), Rank.UNKNOWN.toInt());
		genDestValue(1-p.getColor(), Rank.EIGHT.toInt());

		// If one of the bombs is known,
		// then the opponent probably has guessed
		// the location of the flag
		// and all the other bombs
		// Try to protect the bombs with the lowest rank(s)
		int pd = grid.closestPieceDir(j);
		for (int r = 1; r < 8; r++)
			if (rankAtLarge(p.getColor(), r)) {
				genDestTmp(false, 1 - p.getColor(), j + pd, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
				// genDestTmp(false, 1-p.getColor(), indexb, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
				genNeededDest(p.getColor(), r);
				break;
			}
	}

	private void flagSafety(int flag)
	{
		Piece pflag = getPiece(flag);

		// ai only code
		if (pflag.getColor() != Settings.topColor)
			return;

		assert pflag.getColor() == Settings.topColor : "flag routines only for ai";
		pflag.setAiValue(aiFlagValue(pflag.getColor()));

		// Setting the flag to known is questionable.
		// The issue is that its location is almost never
		// known for sure by the opponent.  If the flag is set
		// to known, the AI will make every effort to prevent
		// the flag from attack.  Therein lies the problem.
		// If the flag is known and can be attacked successfully,
		// the AI is prone to leaving its pieces hanging, because it
		// assumes that the opponents best move is to take the
		// flag rather than its pieces.  This is a horizon effect,
		// and ideally the ebqs should return a worse result
		// and the AI should attempt to minimize material loss
		// even if the flag can be taken.
		pflag.setKnown(true);

		// initially all bombs are worthless (0)
		// value remaining bombs around ai flag
		boolean bombed = true;
		for (int d : dir) {
			int j = flag + d;
				
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p == null) {
				bombed = false;
				continue;
			}

			// if flag area had a bomb removed
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
		if (!bombed)
			flagTarget(flag);

		else if (pflag.isKnown()
			&& rankAtLarge(1-pflag.getColor(), Rank.EIGHT.toInt())) {
			for (int d : dir) {
				int j = flag + d;
				if (!isValid(j))
					continue;
				Piece p = getPiece(j);
				if (p == null)
					continue;

				if (p.getRank() == Rank.BOMB)
					flagTarget(j);
			} // d
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
	// is better to allow the opponent to take the
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
			if (rankAtLarge(1-flagp.getColor(),k)) {
				genNeededDest(1-flagp.getColor(), k);
				break;
			}
		}

		// Set any remaining pieces in the pattern to "bomb".
		// It doesn't matter if the piece really is a bomb or not.
		// The known flag remains false, so the piece becomes
		// an "unknown bomb".  For an ai attacker an EIGHT
		// WINS and it survives but loses its own value.
		// Non-eight LOSES.
		// For an opponent attacker, it evaluates like other
		// unknown ai bombs.
		boolean found = true;
		for (int j = 1; j < 4; j++) {
			Piece p = getPiece(b[j]);
			if (p == null
				|| (p.isKnown() && p.getRank() != Rank.BOMB)
				|| p.hasMoved()) {
				found = false;
				continue;
			}
		}

		// Remove bombs that surround a possible flag
		// this code is only for opponent bombs
		if (flagp.getColor() == Settings.bottomColor
			&& rankAtLarge(1-flagp.getColor(), Rank.EIGHT.toInt())
			&& found)
			for (int k = 1; k < 4; k++)
				destBomb(b[k]);
	}

	// Scan the board setup for suspected flag pieces,
	private void possibleFlag()
	{
		for (int c = RED; c <= BLUE; c++) {
		int [] maybe = null;
		int maybe_count = 0;
                for ( int[] bp : bombPattern ) {
			int[] b = new int[4];
			for ( int i = 0; i < 4; i++) {
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
				for ( k = 1; k < 4; k++ ) {
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
					genDestFlag(b);
					maybe_count=-1;
					break;	// only one pattern at a time
				}
				if (k == 4) {
					// no bombs found, but it looks suspicious
					maybe_count++;
					maybe = b;
				}
			} // possible flag
		} // bombPattern

		if (maybe_count == 1) {
			// didn't find any bombs, but there is only one
			// pattern left, so go for it
			genDestFlag(maybe);
		} else if (maybe_count == 0) {
			// player color c did not bomb his flag,
			// go for any remaining unmoved pieces

			// opponent color eights are now expendable
			values[1-c][Rank.EIGHT.toInt()] = values[1-c][Rank.SEVEN.toInt()];
			// try the back row first

			for (int i = 12; i < 22; i++) {
				int b;
				if (c == Settings.topColor)
					b = i;
				else
					b = 132 - i;
				destBomb(b);
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
					// an "unknown bomb".
					tp.setRank(Rank.BOMB);
					tp.setAiValue(aiBombValue(tp.getColor()));
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

		// hmmm, currently we are not able to change
		// rank or value for ai pieces
		if (p.getRank() == Rank.UNKNOWN
			|| p.getRank() == Rank.BOMB) {
			p.setAiValue(aiBombValue(p.getColor()));
			p.setRank(Rank.BOMB);
		}

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
			if (activeRank[1-p.getColor()][r-1] != 0) {
				genNeededDest(1-p.getColor(), r);
				break;
		}

		// Send the miner 
		genDestTmp(true, p.getColor(), j, DEST_PRIORITY_LOW);
		genNeededDest(1-p.getColor(), 8);
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
				if (p1.getRank() == Rank.UNKNOWN)
					p1.setRank(Rank.BOMB);
				genNeededDest(1-p1.getColor(), 8);

				genDestTmp(true, p2.getColor(), frontPattern[i][1], DEST_PRIORITY_LOW);
				p2.setAiValue(aiBombValue(p2.getColor()));
				if (p2.getRank() == Rank.UNKNOWN)
					p2.setRank(Rank.BOMB);
				genNeededDest(1-p2.getColor(), 8);
			}
		}
	}

	private void genNeededDest(int color, int rank)
	{
		genDestValue(color, rank);
		if (activeRank[color][rank-1] == 0)
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
	// any guards in the lanes.  This is used when sending eights.
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
			if (!isValid(j) || destTmp[j] != n)
				continue;
			Piece p = getPiece(j);
			if (p != null
				&& j != to
				&& !p.hasMoved()
				&& (p.getColor() == color || p.getRank() == Rank.BOMB))
				continue;

			// check for guarded lanes
			if (guarded) {
			for ( int[] lp : lanes ) {
				boolean inlane = false;
				boolean isGuarded = false;
				for (int lane : lp) {
					if (lane == j)
						inlane = true;
					Piece gp = getPiece(lane);
					if (gp != null 
						&& gp.getColor() == color
						&& (!gp.isKnown()
							|| isInvincible(gp)))
						isGuarded = true;
				}
				if (isGuarded && inlane)
					continue;
			}
			}

			// set the neighbors
			for (int d : dir) {
				int i = j + d;
				if (!isValid(i) || destTmp[i] != DEST_VALUE_NIL)
					continue;
				destTmp[i] = n - mult;
				found = true;
			}
		}
		n -= mult;
		}
	}

	private void genDestValue(int color, int rank)
	{
		for (int j = 12; j <= 120; j++)
			if (destValue[color][rank-1][j] < destTmp[j])
				destValue[color][rank-1][j] = destTmp[j];
	}

	public boolean isInvincible(Piece p) 
	{
		return (p.getRank().toInt() <= invincibleRank[p.getColor()]);
	}

	public void move(BMove m, int depth, boolean unknownScoutFarMove)
	{
		Piece fp = getPiece(m.getFrom());
		Rank fprank = fp.getRank();
		Piece tp = getPiece(m.getTo());
		setPiece(null, m.getFrom());
		moveHistory(fp, tp, m);
		fp.setIndex(m.getTo());
		int vm = 0;
		int r = fprank.toInt()-1;
		int color = fp.getColor();

		if (tp == null) { // move to open square
			if (!fp.isKnown() && fp.moves == 0)
				vm -= aiMovedValue(fp);

			// give a given piece destValue
			// or any piece of a given rank if the rank is needed
			if ((activeRankID[color][r] == fp.getID() || neededRank[color][r])
				// only give nines destValue for adjacent moves
				// because they can reach their destination
				// more quickly.
				&& !unknownScoutFarMove) {
				int dvto = destValue[color][r][m.getTo()];
				int dvfrom = destValue[color][r][m.getFrom()];
				if (dvto != DEST_VALUE_NIL
					&& dvfrom != DEST_VALUE_NIL)
					vm += dvto - dvfrom;
			}

			setPiece(fp, m.getTo());
			if (unknownScoutFarMove) {
				// Moving an unknown scout
				// more than one position makes it known.
				// That changes its value.
				vm += makeKnown(fp);
				fp.setRank(Rank.NINE);
			}

		} else { // attack
			Rank tprank = tp.getRank();

			// note: use values[] (not aiValue) for ai attacker
			// because unknown ai attacker knows its own value
			// but opponent attacker is just guessing
			int fpvalue;
			if (color == Settings.topColor)
				fpvalue = values[color][r+1];
			else
				fpvalue = aiValue(fp, m.getFrom());

			// An unknown attacker blocking a low ranked piece
			// is worth discovering; perhaps it is blocking
			// access to the flag.
			// if (!tp.isKnown() && r <= 4
			// 	&& destValue[color][r][m.getTo()] != DEST_VALUE_NIL)
			// 	vm += 30;

			switch (winFight(fp, tp)) {
			case Rank.EVEN :
				assert fprank != Rank.UNKNOWN : "fprank is unknown?";
				vm += aiValue(tp, m.getTo()) - fpvalue;
				setPiece(null, m.getTo());
				break;

			case Rank.LOSES :
				// note: use values not aiValue for
				// attacker because attacker knows its value
				// even if attacker is unknown
				vm -= fpvalue;
				vm += makeKnown(tp);

				break;

			case Rank.WINS:
				vm += aiValue(tp, m.getTo());

				// Because of flag safety,
				// the ai assumes that unknown pieces win bombs.
				if (tprank == Rank.BOMB
					&& fprank == Rank.UNKNOWN)
						fp.setRank(Rank.EIGHT);

				else if (!tp.isKnown()) {
					// tp is not known,
					// so it could be a bomb
					// which deters attacking
					// an unknown unmoved piece

					// encourage ai to not move
					// a piece subject to attack.
					// or attack an unmoved piece
					if (tp.moves == 0)
						vm -= fpvalue;

					// unknown moved pieces
					// have bluffing value
					else if (!isInvincible(fp))
						vm -= aiBluffingValue(m.getTo());
				}

				vm += makeKnown(fp);
				setPiece(fp, m.getTo()); // won
				break;

			case Rank.UNK:
				vm = outcomeUnknown(m, fp, tp, fprank, tprank, fpvalue);
				break;
			} // switch

			// prefer early attacks for both ai and opponent
			vm -= depth;

		} // else attack

		if (fp.getColor() == Settings.topColor)
			value += vm;
		else
			value -= vm;
		fp.moves++;
	}

	public int outcomeUnknown(BMove m, Piece fp, Piece tp, Rank fprank, Rank tprank, int fpvalue)
	{
		int vm = 0;
		// fp or tp is unknown
		if (tp.getColor() == Settings.topColor) {
			// ai is defender (tp)
			assert fp.getRank() == Rank.UNKNOWN : "opponent piece is known?";
			vm += aiValue(tp, m.getTo()) - fpvalue;

			// Note: Acting Rank Chase is an
			// unreliable predictor of actual rank.
			// It is easy to get suckered into thinking
			// that a bluffing piece is a low rank and
			// then sacrificing material to avoid it.
			if (fp.getActingRankChase() != Rank.NIL
				&& fp.getActingRankChase().toInt() <= tp.getRank().toInt())
				vm -= aiBluffingValue(m.getFrom());
			// this encourages the ai to keep
			// its attacked pieces next to its unknown
			// pieces, because if attacker wins
			// then it is subject to attack
			// by unknown ai pieces.
			vm += makeKnown(fp);

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
			//
			// Because some unknown AI pieces
			// (8's and SPY) are valued at actual value,
			// the AI would likely overestimate
			// the damage that a kamikaze opponent
			// piece could cause, because the search
			// tree discovers the optimal path for the
			// attacker.  But how likely is the attacker
			// going to take the optimal path?
			//	setPiece(null, to);	// maybe even

			// defender piece has moved
			// assume worst case for ai
			// defender loses
			if (tp.hasMoved())
				setPiece(fp, m.getTo());	// attacker wins

		} else {
			// ai is attacker (fp)
			// it may or may not be known
			// defender may or may not be known

			assert fp.getColor() == Settings.topColor : "fp is opponent?";

			if (tp.getActingRankChase() != Rank.NIL
				&& tp.getActingRankChase().toInt() <= fp.getRank().toInt())
				vm -= aiBluffingValue(m.getTo());
			vm += aiValue(tp, m.getTo()) - fpvalue; 
			vm += makeKnown(fp);
			// assume worst case for ai
			// attacker loses
		}
		return vm;
	}

        protected void moveHistory(Piece fp, Piece tp, BMove m)
        {
                undoList.add(new UndoMove(fp, tp, m.getFrom(), m.getTo(), 0L));
	}


	public void undo(int valueB)
	{
		value = valueB;
		UndoMove um = getLastMove();
		Piece fp = um.getPiece();
		fp.copy(um.fpcopy);
		if (um.tp != null)
			um.tp.copy(um.tpcopy);
		setPiece(fp, um.getFrom());
		setPiece(um.tp, um.getTo());
		undoList.remove(undoList.size()-1);
	}
	
	// if the prior move was to the target square,
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
	public int aiBluffingValue(int to)
	{
		UndoMove prev = getLastMove();
		if (to == prev.getTo())
			return 30;
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
	// So maybe stealth value is about 11%.
	//
	// If a piece has moved, then much is probably guessed about
	// the piece already, so it has half of its stealth value.
	//
	private int stealthValue(int color, Rank r)
	{
		return values[color][r.toInt()] / 9;
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
			return -stealthValue(p);
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
		if (looping)
			return 0;

		Rank r = p.getRank();
		if (neededRank[p.getColor()][r.toInt()-1])
			return 1;
		else
			return values[p.getColor()][Rank.UNKNOWN.toInt()] / 4;
	}

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
	// The following implementation is similar, but differs:
	// • Bombs are worthless, except if they surround a flag or
	//	block lanes.  These bombs are worth a little more than a eight.
	// • The value of the Spy becomes equal to a Seven once the
	//	opponent Marshal is removed.

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
	// So a flag bomb is worth slightly more than an unknown eight.
	// The value of an eight increases with the number remaining.
	//
	// The ai is always willing to exchange an eight for a flag bomb.
	// With several eights remaining, it is not willing to take a bomb
	// if it loses a low ranked piece (1-4) with a higher value
	// trying to get at the bomb, unless the search tree exposes
	// the flag (which then garners a higher value).

	private int aiBombValue(int color)
	{
		// A worthwhile bomb of "color" is worth the value
		// of the opposite 8, its stealth value, and a little more.
		// This makes taking a worthwhile bomb always worth sacrificing
		// an eight if necessary.

		// Note that a known miner taking a worthless bomb,
		// the value is zero, but an unknown miner,
		// the value is -stealthvalue.  This deters an
		// unknown miner from taking a worthless bomb.
		// This is calculated in WINS.
		return values[1-color][8]
			+ stealthValue(1-color, Rank.EIGHT)
			+ 10;
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

	private int aiValue(Piece p, int i)
	{
		// piece value (bomb, flag) overrides calculated value
		int v = p.aiValue();

		if (v == 0) {
			Rank r = p.getRank();

			// eights and spies are always valued based on rank
			// even if they are unknown, because they
			// are too valuable to lose in an unknown
			// exchange.
			if (p.isKnown()
				|| r == Rank.EIGHT
				|| r == Rank.SPY) {
				v = values[p.getColor()][r.toInt()];
			} else {
				Random rnd = new Random();
				v = values[p.getColor()][Rank.UNKNOWN.toInt()]
					+ stealthValue(p)

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

				// unknown pieces are worth more on the back ranks
				// because we need to find bombs around the flag
				// note: *3 to outgain -depth late capture penalty

				if (p.getColor() == Settings.bottomColor) {
					if (!p.hasMoved())
						v += (i/11-7) * 3;
				} else {
					if (!p.hasMoved())
						v += (4-i/11) * 3;
				}

				if (p.moves != 0)
					v -= aiMovedValue(p);

				// If a moved piece is blocking an
				// ai piece because the ai thinks it could
				// be a high piece, the ai needs to call the
				// bluff.  So pieces that are acting as low
				// ranking pieces (1 - 4) need to be discovered.
				if (p.getActingRankChase().toInt() <= 4)
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

			// Note: Acting Rank is an unreliable predictor
			// of actual rank.
			//
			// High tp actingRankFlee is very unreliable because
			// often an unknown opponent piece (such as a 1-3)
			// will eschew taking a known ai piece (such as a 5-7)
			// to avoid becoming known.
			//
			// If the tp actingRankFlee is >= fp rank
			// it is probably at least an even exchange and
			// perhaps better, so we add the entire tp value
			// and do not subtract the fp value (which we do in
			// the case of an unknown exchange).

			if (isInvincible(tp)
			&& !(tprank == Rank.ONE
				&& possibleSpy
				&& (fp.getActingRankChase() == Rank.NIL || fp.getActingRankChase() != Rank.ONE))
			|| (fp.getActingRankFlee() != Rank.NIL
				&& fp.getActingRankFlee().toInt() >= fp.getRank().toInt()
				&& fp.getActingRankFlee().toInt() <= 4))
			return Rank.LOSES;
		} else {
			if (isInvincible(fp) && tp.moves != 0
				// if a piece fled from a 1-4
				// and we can take it, take it.
				|| (tp.getActingRankFlee() != Rank.NIL
					&& tp.getActingRankFlee().toInt() >= fp.getRank().toInt()
					&& tp.getActingRankFlee().toInt() <= 4)) 
			return Rank.WINS;
		}
		}
		return result;
	}

}


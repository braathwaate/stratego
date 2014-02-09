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
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Settings;
import java.util.Random;



public class TestingBoard extends Board
{
	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected int[][] movedRank = new int[2][15];	// moved ranks
	protected int[][][] destValue = new int[2][15][121];	// encourage forward motion
	protected int[] destTmp = new int[121];	// encourage forward motion
	private static int[] dir = { -11, -1,  1, 11 };


	protected boolean possibleSpy = false;
	protected int value;	// value of board

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

		value = 0;

		for (int c = RED; c <= BLUE; c++)
		for (int j=0;j<15;j++) {
			knownRank[c][j] = 0;
			movedRank[c][j] = 0;
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
				Piece np = new Piece(UniqueID.get(), p);
				grid.setPiece(i, np);
				if (p.getColor() == Settings.bottomColor && !p.isKnown()) {
					np.setUnknownRank();
					// unknown pieces are worth more on the back ranks
					// because we need to find bombs around the flag
					// we add a random 1 point to the value so that
					// if the ai has a choice of attacking two or more unknown
					// pieces during a series of attacks,
					// it will alternate direction.
					Random rnd = new Random();
					np.setAiValue(aiValue(Rank.UNKNOWN)+(i/11-7) +rnd.nextInt(2));
			 	} else {
					if (p.getColor() == Settings.bottomColor)
						np.setAiValue(aiValue(p.getRank()));
					else
						np.setAiValue(-aiValue(p.getRank()));
					int r = p.getRank().toInt();
					if (p.isKnown())
						knownRank[p.getColor()][r-1]++;
				}

				if (p.hasMoved()) {
					// count the number of moved pieces
					// to determine further movement penalties
					Rank rank = p.getRank();
					int r = rank.toInt();
					movedRank[p.getColor()][r-1]++;
				}
			}
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
				destValue[c][rank][j] = -99;
		}

		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p != null) {
			// Encourage lower ranked pieces to find pieces
			// of higher ranks and eights to find bombs.
			if (p.isKnown()) {
				Rank rank = p.getRank();
				int r = rank.toInt();
				if (rank == Rank.BOMB) {
					genDestTmp(p.getColor(), i, -1);
					genDestValue(1-p.getColor(), Rank.EIGHT.toInt());
				} else if (r > 1 && r <= 8)  {
					genDestTmp(p.getColor(), i, -1);
					genDestValue(1-p.getColor(), r-1);
					if (r > 2)
						genDestValue(1-p.getColor(), r-2);
					if (r > 3)
						genDestValue(1-p.getColor(), r-3);
				}
			} else if (!p.hasMoved()) {
				// Encourage lower ranked pieces to discover
				// unknown and unmoved pieces
				genDestTmp(p.getColor(), i, -1);
				genDestValue(1-p.getColor(), 6);
				genDestValue(1-p.getColor(), 7);
				// nines don't need any encouragement
				// flag discovery
				// TBD: list of squares subject to change
				if (p.getRank() == Rank.FLAG
					|| i == 111
					|| i == 112
					|| i == 119
					|| i == 120) {
					genDestTmp(p.getColor(), i, 4);
					for (int k = 5; k <= 10; k++)
						genDestValue(1-p.getColor(), k);
				}
			}
			}
		}

		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			knownRank[p.getColor()][r-1]++;
		}

		possibleSpy = (knownRank[Settings.bottomColor][9] == 0);

		// a rank becomes invincible when all higher ranking pieces
		// are gone or known
		for (int c = RED; c <= BLUE; c++) {
			int rank = 0;
			for (;rank<10;rank++) {
				if (knownRank[c][rank] != Rank.getRanks(rank))
					break;
			}
			invincibleRank[1-c] = rank + 1;
		}

	}

	private void genDestTmp(int color, int to, int n)
	{
		for (int j = 12; j <= 120; j++)
			destTmp[j] = -99;
		destTmp[to] = n;
		boolean found = true;
		while (found) {
		found = false;
		for (int j = 12; j <= 120; j++) {
			if (!isValid(j) || destTmp[j] != n)
				continue;
			for (int k = 0; k < 4; k++) {
				int i = j + dir[k];
				if (!isValid(i) || destTmp[i] != -99)
					continue;
				// opposing pieces and bombs block the destination but
				// the move tree allows pieces of the same color
				// to move out of the way, although this costs an
				// additional move.
				Piece p = getPiece(i);
				if (p != null) {
					if (p.getColor() == color || p.getRank() == Rank.BOMB) {
						destTmp[i] = n;
						continue;
					}
				}
				destTmp[i] = n - 1;
				found = true;
			}
		}
		n--;
		}

/*
		// nullify for depth
		for (int j = 12; j <= 120; j++) {
			if (destTmp[j]  > -Settings.aiLevel/2)
				destTmp[j] = -Settings.aiLevel/2;
*/
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

	// TRUE if move is valid
	public boolean move(BMove m, int depth, boolean scoutFarMove)
	{
		Piece fp = getPiece(m.getFrom());
		Piece tp = getPiece(m.getTo());
		setPiece(null, m.getFrom());
		fp.moves++;

		if (tp == null) { // move to open square
			Rank rank = fp.getRank();
			int r = rank.toInt()-1;
			value += (destValue[fp.getColor()][r][m.getTo()]
				- destValue[fp.getColor()][r][m.getFrom()]);

			int color = fp.getColor();
			if (!fp.isKnown() && fp.moves == 1)
				// moving a unknown piece makes it vulnerable
				value += aiMovedValue(fp);
			setPiece(fp, m.getTo());
			if (scoutFarMove) {
				fp.setKnown(true);
			}
			recentMoves.add(m);
		} else { // attack
			if ((fp.isKnown() || fp.getColor() == Settings.topColor)
				&& (tp.isKnown() || tp.getColor() == Settings.topColor)) {
				// known attacker and defender
				int result = fp.getRank().winFight(tp.getRank());
				if (result == -1) {	// even
					setPiece(null, m.getTo());
					value += fp.aiValue() + tp.aiValue();
				} else if (result == 0) { // lost
					value += fp.aiValue();
					// unknown pieces have bluffing value
					if (depth != 0 && !fp.isKnown()) {
						assert fp.getColor() == Settings.topColor : "if fp is unknown then fp must be ai";
						aiBluffingValue(m.getTo());
					}
				} else {	// won
					// The defending piece value is
					// gained only if it is known or
					// if it moved.  This encourages the
					// the ai to not move its unknown
					// pieces that are subject to loss
					// by attack.
					if (tp.isKnown() || tp.moves != 0)
						value += tp.aiValue();
					setPiece(fp, m.getTo()); // won
				}
			} else {
				// unknown attacker or defender
				// remove attacker or defender or both
				if (tp.getRank() == Rank.BOMB && tp.getColor() == Settings.topColor) {
					// ai is defender and piece is bomb
					if (fp.isKnown() && fp.getRank() == Rank.EIGHT) {
						// miner wins if it hits a bomb
						setPiece(fp, m.getTo()); // won
						value += tp.aiValue();
					} else {
						// non-miner loses if it hits a bomb
						// bomb stays on board
						value += fp.aiValue();
					}
				} else if (tp.getColor() == Settings.topColor) {
					// ai is defender (tp)
					if (isInvincible(tp) && !(tp.getRank() == Rank.ONE && possibleSpy)) {
						// tp stays on board
						value += fp.aiValue();
					} else {
						aiUnknownValue(fp, tp, m.getTo(), depth);
					}
				} else if (fp.isKnown() && tp.hasMoved()) {
					// attacker (ai) is known and defender has moved
					assert fp.getColor() == Settings.topColor : "fp is opponent?";

					if (isInvincible(fp)) {
						value += tp.aiValue();
						setPiece(fp, m.getTo()); // won
					} else {
						aiUnknownValue(fp, tp, m.getTo(), depth);
					}
				} else {
					// nothing is known about target.
					// attacker is just guessing.
					assert fp.getColor() == Settings.topColor : "fp is opponent?";
					aiUnknownValue(fp, tp, m.getTo(), depth);
				}
			} // unknown attacker or defender

			// prefer early attacks for both ai and opponent
			if (fp.getColor() == Settings.topColor)
				value -= depth;
			else
				// this helps keep ai's attacked pieces
				// next to unknown defending pieces
				value += depth;

			return true;

		} // else attack
		return false;
	}

	public void undo(Piece fp, int f, Piece tp, int t, int valueB)
	{
		setPiece(fp, f);
		setPiece(tp, t);
		value = valueB;
		fp.moves--;
		if (tp == null)
			recentMoves.remove(recentMoves.size()-1);
	}
	
	// Value given to discovering an unknown piece.
	// Discovery of these pieces is the primary driver of the heuristic,
	// as it leads to discovery of the flag.
	private void aiUnknownValue(Piece fp, Piece tp, int to, int depth)
	{
/*
		if (fp.getRank() != Rank.EIGHT) {
			value += fp.aiValue();
			setPiece(null, m.getTo()); // maybe even
		}
*/
		// If the defending piece has not yet moved
		// but we are evaluating moves for it,
		// assume that the defending piece loses.
		// This deters new movement of pieces near opp. pieces,	
		// which makes them easy to capture.

		if (tp.getColor() == Settings.bottomColor) {
			// ai is attacker (fp)
			assert !tp.isKnown() : "tp is known"; // defender (tp) is unknown
			value += tp.aiValue() + fp.aiValue();
			if (!tp.hasMoved()) {
				// Add in fp.moves to prevent stalling
				value += fp.moves;
				// assume lost
				// "from" square already cleared on board
			} else if (depth != 0 && fp.moves == 1 && !fp.isKnown()) {
				// "from" square already cleared on board
				aiBluffingValue(to);
				setPiece(null, to);	// assume even
			}
				// else assume lost
				// "from" square already cleared on board
		} else {
			// ai is defender (tp)
			assert !fp.isKnown() : "fp is known"; // defender (fp) is unknown

			// encourage ai to not move an unmoved piece
			// that is subject to attack.
			if (!tp.hasMoved() && tp.moves == 0 && !tp.isKnown())
				value += fp.aiValue();
			else
				value += tp.aiValue() + fp.aiValue();

			if (!fp.hasMoved()) {
				// Add in tp.moves to prevent stalling
				value += tp.moves;
				// attacker loses
				// "from" square already cleared on board
			// unknown but moved ai pieces have some bluffing value
			} else if (tp.hasMoved() && !tp.isKnown()) {
				// bluffing value
				aiBluffingValue(to);
			} else
				// setPiece(null, to);	// even
				// this encourages the ai to keep
				// its attacked pieces next to its unknown
				// pieces, because if attacker wins
				// then it is subject to attack
				// by unknown ai pieces.
				setPiece(fp, to);	// attacker wins
		}
	}

	// This penalty value deters movement of an unmoved piece
	// to an open square.
	// destValue[] encourages a piece to move.
	// But moving a piece makes it more subject to attack because
	// an attacker knows it cannot be a bomb.
	// So we only want an unmoved piece to move if it can make substantial
	// progress towards its destination.
	// Each time a piece moves a square towards its destination
	// it gains a point.  So in a N move depth search, it can gain
	// N points.  So the penalty needs to be 1 < penalty < N + 1.

	public int aiMovedValue(Piece fp)
	{
		int color = fp.getColor();
		Rank rank = fp.getRank();
		// keep a 6 or 7 in motion to discover unknown pieces
		if ((rank == Rank.SIX || rank == Rank.SEVEN)
			&& movedRank[color][5] == 0	// six
			&& movedRank[color][6] == 0)	// seven
			return 0;

		int v = Settings.aiLevel / 3 + 1;
		if (color == Settings.bottomColor) {
			return v;
		} else {
			return -v;
		}
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
	public void aiBluffingValue(int to)
	{
		BMove m = recentMoves.get(recentMoves.size()-1);
		if (m.getTo() == to)
			value += 30;
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

	public static int aiValue(Rank r)
	{
		switch (r)
		{
		case FLAG:
			return 6000;
		case BOMB:
			return 100;//6
		case SPY:
			return 360;
		case ONE:
			return 420;
		case TWO:
			return 360;
		case THREE:
			return 150;//2
		case FOUR:
			return 80;//3
		case FIVE:
			return 45;//4
		case UNKNOWN:
		// TBD: unknown value changes during game
			return 35;
		case SIX:
			return 30;//4
		case SEVEN:
			return 15;//4
		case EIGHT:
			return 60;//5
		case NINE:
			return 7;//8
		default:
			return 0;
		}
	}

	@Override
	protected void setShown(Piece p, boolean b){}
}

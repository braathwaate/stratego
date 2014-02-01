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


public class TestingBoard extends Board
{
	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][10];	// discovered ranks
	protected int[][] destValue = new int[2][131];	// encourage forward motion

	protected boolean possibleSpy = false;
	protected int value;	// value of board

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

		value = 0;

		for (int c = RED; c <= BLUE; c++)
		for (int j=0;j<10;j++)
			knownRank[c][j] = 0;

		// ai only knows about known opponent pieces
		// so update the grid to only what is known
		for (int i=13;i<=131;i++) {
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
					np.setAiValue(aiValue(Rank.UNKNOWN));
				} else {
					if (p.getColor() == Settings.bottomColor)
						np.setAiValue(aiValue(p.getRank()));
					else
						np.setAiValue(-aiValue(p.getRank()));
					int r = p.getRank().toInt();
					if (r == 0 || r > 10)
						continue;
					if (p.isKnown())
						knownRank[p.getColor()][r-1]++;
				}
			}
		}

		for (int i = 0; i < 10; i++)
		for (int j = 0; j < 10; j++) {
			destValue[Settings.bottomColor][grid.getIndex(i,j)] = 9 - j;
			destValue[Settings.topColor][grid.getIndex(i,j)] =  j;
		}

		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			if (r == 0 || r > 10)
				continue;
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
		
		// Encourage forward motion of lowly pieces
		// to aid in piece discovery.
		// Higher ranked pieces are kept back for protection.
		// Eights are a special case and need to be handled
		// separately.
		//
		// Note: at depths >8 moves (16 ply), this rule is not necessary
		// because these pieces will find their targets in the 
		// move tree.
		if (Math.abs(fp.aiValue()) <= aiValue(Rank.UNKNOWN))
			value += (destValue[fp.getColor()][m.getTo()]
				- destValue[fp.getColor()][m.getFrom()]);

		if (tp == null) { // move to open square
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
				} else {	// won
					if (tp.isKnown() || tp.hasMoved())
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
				} else if (tp.isKnown()) {
					// ai is defender (tp)
					if (isInvincible(tp) && !(tp.getRank() == Rank.ONE && possibleSpy)) {
						// tp stays on board
						value += fp.aiValue();
					} else {
						aiUnknownValue(fp, tp, m.getTo(), depth);
					}
				} else if ((fp.isKnown() || fp.getColor() == Settings.topColor) && tp.hasMoved()) {
					// attacker is known and defender has moved
					if (isInvincible(fp)) {
						value += tp.aiValue();
						setPiece(fp, m.getTo()); // won
					} else {
						aiUnknownValue(fp, tp, m.getTo(), depth);
					}
				} else {
					// nothing is known about target.
					// attacker is just guessing.
					aiUnknownValue(fp, tp, m.getTo(), depth);
				}
			} // unknown attacker or defender

			if (fp.getColor() == Settings.topColor)
				value -= depth;	// prefer early attacks
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
			value += (tp.aiValue() + fp.aiValue()) / 3;
			if (!tp.hasMoved()) {
				// Add in fp.moves to prevent stalling
				value += fp.moves;
				// assume lost
				// "from" square already cleared on board
			} else if (depth != 0 && fp.moves == 1 && !fp.isKnown()) {
				// "from" square already cleared on board
				value += 10;	// bluffing value
				setPiece(null, to);	// assume even
			}
				// else assume lost
				// "from" square already cleared on board
		} else {
			// ai is defender (tp)
			assert !fp.isKnown() : "fp is known"; // defender (fp) is unknown

			if (!tp.hasMoved() && tp.moves == 0 && !tp.isKnown())
				value += (fp.aiValue()) / 3;
			else
				value += (tp.aiValue() + fp.aiValue()) / 3;

			if (!fp.hasMoved() && !fp.isKnown()) {
				// Add in tp.moves to prevent stalling
				value += tp.moves;
				// attacker loses
				// "from" square already cleared on board
			} else if ((tp.hasMoved() || tp.moves != 0) && !tp.isKnown()) {
				// bluffing value
				value += 10;
			} else
				setPiece(null, to);	// even
		}
	}

	// this penalty value deters movement of an unmoved piece
	// to an open square.
	// the value negates the destValue[] so that a unmoved piece
	// will move if it can make undeterred forward motion on the board.

	private int aiMovedValue(Piece p)
	{
		if (p.getColor() == Settings.bottomColor) {
			return (Settings.aiLevel / 2 + 1);
		} else {
			return -(Settings.aiLevel / 2 + 1);
		}
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
		case UNKNOWN:	// TBD: unknown value changes during game
			return 35;//4
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

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
				grid.setPiece(i, new Piece(UniqueID.get(), p));
				if (p.getColor() == Settings.bottomColor && !p.isKnown()) {
					grid.getPiece(i).setUnknownRank();
				} else {
					int r = p.getRank().toInt();
					if (r == 0)
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
			if (r == 0)
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
		
		if (tp == null) { // move to open square
			// encourage forward motion
			value += (destValue[fp.getColor()][m.getTo()]
				- destValue[fp.getColor()][m.getFrom()]);
			if (!fp.isKnown() && fp.moves == 1)
				// moving a unknown piece makes it vulnerable
				value += aiMovedValue(fp);
			setPiece(fp, m.getTo());
			if (scoutFarMove) {
				fp.setKnown(true);
			}
			recentMoves.add(m);
		} else { // attack
			if (!fp.isKnown() && fp.getColor() == Settings.topColor
				&& tp.isKnown() && depth != 0) {
				// unknown ai piece at depth has bluffing value
				int result = winFight(fp, tp);
				if (result == -1) {	// even
					setPiece(null, m.getTo());
					value += aiValue(fp) + aiValue(tp);
				} else if (result == 0) // lost
					value += Rank.UNKNOWN.aiValue() / 3;
				else {	// won
					setPiece(fp, m.getTo()); // won
					value += aiValue(tp);
				}
			} else if ((fp.isKnown() || fp.getColor() == Settings.topColor)
				&& tp.isKnown()) {
				// known attacker and defender
				int result = winFight(fp, tp);
				if (result == -1) {	// even
					setPiece(null, m.getTo());
					value += aiValue(fp) + aiValue(tp);
				} else if (result == 0) // lost
					value += aiValue(fp);
				else {	// won
					setPiece(fp, m.getTo()); // won
					value += aiValue(tp);
				}
			} else {
				// unknown attacker or defender
				// remove attacker or defender or both
				if (tp.getRank() == Rank.BOMB && tp.getColor() == Settings.topColor) {
					// ai is defender and piece is bomb
					if (fp.isKnown() && fp.getRank() == Rank.EIGHT) {
						// miner wins if it hits a bomb
						setPiece(fp, m.getTo()); // won
						value += aiValue(tp);
					} else {
						// non-miner loses if it hits a bomb
						value += aiValue(fp);
					}
				} else if (tp.isKnown()) {
					// ai is defender
					if (isInvincible(tp) && !(tp.getRank() == Rank.ONE && possibleSpy)) {
						value += aiValue(fp);
					} else if (!fp.hasMoved()) {
					// be cautious but not afraid of unmoved pieces
					// add in fp.moves to keep the game from stalling
					// TBD: need probability calc here
						value += (fp.moves + Rank.UNKNOWN.aiValue() - tp.getRank().aiValue()) / 3;	
						setPiece(fp, m.getTo()); // maybe won
					} else {
					// moving attacker is probably chasing ai piece
					// for a reason, but could be bluffing
					// TBD: need probability calc here
					// and past move analysis
						setPiece(fp, m.getTo()); // maybe won
						// very negative, maybe spy
						// value += aiValue(tp) / 3;
						value += aiWinValue(tp);
					}
				} else if ((fp.isKnown() || fp.getColor() == Settings.topColor) && tp.hasMoved()) {
					// attacker is known and defender has moved
					if (isInvincible(fp)) {
						value += aiValue(tp);
						setPiece(fp, m.getTo()); // won
					} else {
						// TBD: need probability
						// the higher the attacker rank
						// the more likely a win
						value += aiWinValue(fp);
						setPiece(fp, m.getTo()); // maybe won
					}
				} else if (tp.moves != 0) {
					// target is unknown but moving
					// target is likely strong
					// but we need to know what it is
					if (isInvincible(fp)) {
						value += aiValue(tp);
						setPiece(fp, m.getTo()); // won
					} else {
						value += aiUnknownValue(tp) / 3;
						setPiece(fp, m.getTo()); // maybe win
					}
				} else {
					// nothing is known about unmoved target.
					// attacker is just guessing.
					// assume even exchange
					// remove both pieces.
					// unmoved piece may be bomb and miner might win 
					// TBD: need probability
					value += aiUnknownValue(tp) / 3;
					if (fp.getRank() != Rank.EIGHT) {
						value += aiValue(fp) / 3;
						setPiece(null, m.getTo()); // maybe even
					}
				}
				value -= depth;	// prefer early attacks
				return true;
			}
		}
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
	
	private int aiValue(Piece p)
	{
		if (p.getColor() == Settings.bottomColor) {
			return p.getRank().aiValue();	
		} else {
			return -p.getRank().aiValue();	
		}
	}

	// this value is used when only one of the two pieces in a collision
	// is unknown but has moved.
	private int aiWinValue(Piece known)
	{
/*
		if (attacker.getColor() == Settings.bottomColor) {
			return (7 - attacker.getRank().toInt()) * 3;	
		} else {
			return (7 - attacker.getRank().toInt()) * 3;	
		}
*/

		if (known.getColor() == Settings.bottomColor) {
			return (known.getRank().aiValue() - Rank.UNKNOWN.aiValue());	
		} else {
			return -(known.getRank().aiValue() - Rank.UNKNOWN.aiValue());	
		}
	}

	private int aiUnknownValue(Piece p)
	{
		if (p.getColor() == Settings.bottomColor) {
			return Rank.UNKNOWN.aiValue();	
		} else {
			return -Rank.UNKNOWN.aiValue();	
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

	@Override
	protected void setShown(Piece p, boolean b){}
}

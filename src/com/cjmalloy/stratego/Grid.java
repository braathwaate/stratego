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

package com.cjmalloy.stratego;


public class Grid 
{
	// The actual stratego board is 10x10 but
	// an old performance enhancing trick is to surround the
	// board with illegal squares
	// so that illegal moves are easily discarded

	private Piece[] grid = new Piece[133];
	private static Piece water = new Piece(UniqueID.get(), -1, Rank.WATER);

	// It is useful to answer the following grid questions quickly:
	// 1. Does a piece have any legal moves (is it trapped)?
	// 2. Does a piece have any possible attacks?
	// 3. Are there any nearby enemy pieces?
	//
	// By keeping two 64-bit variables up-to-date on the locations
	// of all the pieces, masks can be used to answer these questions.

	protected BitGrid pieceBitGrid[] = new BitGrid[2];
	static private boolean[] isWater = new boolean[133];
	static public final int NEIGHBORS = 5;
	static protected BitGrid neighbor[][] = new BitGrid[NEIGHBORS][121];

	static {
		setWater(2,4);
		setWater(3,4);
		setWater(2,5);
		setWater(3,5);
		setWater(6,4);
		setWater(7,4);
		setWater(6,5);
		setWater(7,5);

		for (int i = 0; i < 11; i++)
			setWater(i);
		for (int i = 0; i < 132; i+=11)
			setWater(i);
		for (int i = 121; i < 133; i++)
			setWater(i);

		for (int n = 0; n < NEIGHBORS; n++)
		for (int f = 12; f <= 120; f++) {
			if (!isValid(f))
				continue;
			neighbor[n][f] = new BitGrid();
			for (int t = 12; t <= 120; t++) {
				if (!isValid(t) || f == t)
					continue;
				if (steps(f,t) > n + 1)
					continue;
				neighbor[n][f].setBit(t);
			}
		}
	}

	public static class UniqueID
        {
                private static int id = 0;

                static public int get()
                {
                        id++;
                        return id;
                }
        }

	public Grid() 
	{
		for (int i = 0; i < 2; i++)
			pieceBitGrid[i] = new BitGrid();

		for (int i = 0; i < grid.length; i++)
			if (isWater[i])
				grid[i] = water;
	}

	public Grid(Grid g)
	{
		grid = g.grid.clone();
		for (int i = 0; i < 2; i++) {
			pieceBitGrid[i] = new BitGrid(g.pieceBitGrid[i]);
		}

	}

	static public boolean isValid(int i)
	{
		return !isWater[i];
	}

	public Piece getPiece(int i) 
	{
		return grid[i];
	}

	static public int getX(int i)
	{
		return i % 11 - 1;
	}

	static public int getY(int i)
	{
		return i / 11 - 1;
	}

	public Piece getPiece(int x, int y) 
	{
		return getPiece(getIndex(x, y));
	}

	static public int getIndex(int x, int y)
	{
		return x + 1 + (y+1) * 11;
	}

	public void setPiece(int i, Piece p) 
	{
		grid[i] = p;
		pieceBitGrid[p.getColor()].setBit(i);
	}

	static private void setWater(int i) 
	{
		isWater[i] = true;
	}

	static private void setWater(int x, int y) 
	{
		setWater(getIndex(x, y));
	}

	public void clearPiece(int i) 
	{
		grid[i] = null;
		pieceBitGrid[0].clearBit(i);
		pieceBitGrid[1].clearBit(i);
	}

	public void setPiece(int x, int y, Piece p) 
	{
		setPiece(getIndex(x, y), p);
	}

	public void clear()
	{
		for (int i=12;i<=120;i++) {
			if (isValid(i))
				clearPiece(i);
		}
	}

	public boolean hasMove(Piece p)
	{
		int i = p.getIndex();
		return pieceBitGrid[p.getColor()].xorMask(neighbor[0][i]);
	}

	public boolean hasAdjacentPiece(int i)
	{
		return pieceBitGrid[0].andMask(neighbor[0][i])
			|| pieceBitGrid[1].andMask(neighbor[0][i]) ;
	}

	// same is isCloseToEnemy(0);
	public boolean hasAttack(Piece p)
	{
		int i = p.getIndex();
		return pieceBitGrid[1-p.getColor()].andMask(neighbor[0][i]);
	}

	public boolean isCloseToEnemy(int turn, int i, int n)
	{
		return pieceBitGrid[1-turn].andMask(neighbor[n][i]);
	}

	public void getNeighbors(int turn, long [] bg)
	{
		pieceBitGrid[turn].getNeighbors(pieceBitGrid[1-turn], bg);
	}

	// isAdjacent is the same as steps() == 1 but perhaps faster
	static public boolean isAdjacent(int f, int t)
	{
		int d = f - t;
		return d == 11
			|| d == -11
			|| d == 1
			|| d == -1;
	}

	static public boolean isAdjacent(int m)
	{
		return isAdjacent(Move.unpackFrom(m), Move.unpackTo(m));
	}

	// number of steps between indicies
	static public int steps(int f, int t)
	{
		int ty = t/11;
		int fy = f/11;
		return Math.abs(ty-fy) + Math.abs((t-ty*11)-(f-fy*11));
	}

	static public int dir(int f, int t)
	{
		int x = getX(f);
		int y = getY(f);
		int tx = getX(t);
		int ty = getY(t);
		int dir = 0;

		if (y > ty) {
			if (x > tx)
				dir = 12;
			else if (x == tx)
				dir = 11;
			else
				dir = 10;
		} else if (y == ty) {
			if (x > tx)
				dir = 1;
			else
				dir = -1;
		} else {
			if (x > tx)
				dir = -10;
			else if (x == tx)
				dir = -11;
			else
				dir = -12;
		}
		return dir;
	}

	// search for closest relevant piece to Piece p
	public Piece closestPiece(Piece p, int offset, boolean isBombed)
	{
		int tx = Grid.getX(p.getIndex() + offset);
		int ty = Grid.getY(p.getIndex() + offset);
	
		int minsteps = 99;
		Piece found = null;
		for (int y = 0; y < 10; y++)
		for (int x = 0; x < 10; x++) {
			Piece fp = getPiece(x,y);
			if (fp == null
				|| !fp.hasMoved()
				||  fp.getColor() == p.getColor())
				continue;

			// when looking for the closest relevant piece
			// to a bomb, only look for eights and unknowns
			if (isBombed
				&& fp.getApparentRank() != Rank.UNKNOWN
				&& fp.getApparentRank() != Rank.EIGHT)
				continue;

			int steps = Math.abs(ty - y) + Math.abs(tx - x);
			if (steps < minsteps) {
				minsteps = steps;
				found = fp;
			}
		}

		return found;
	}
}

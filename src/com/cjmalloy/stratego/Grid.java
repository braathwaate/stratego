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
	public static Piece water = new Piece(-1, Rank.WATER);

	// It is useful to answer the following grid questions quickly:
	// 1. Does a piece have any legal moves (is it trapped)?
	// 2. Does a piece have any possible attacks?
	// 3. Are there any nearby enemy pieces?
	//
	// By keeping two 64-bit variables up-to-date on the locations
	// of all the pieces, masks can be used to answer these questions.

	protected BitGrid pieceBitGrid[] = new BitGrid[2];
	protected BitGrid movablePieceBitGrid[] = new BitGrid[2];
	static private boolean[] isWater = new boolean[133];
	static public final int NEIGHBORS = 5;
	static protected BitGrid neighbor[][] = new BitGrid[NEIGHBORS][121];
	static protected BitGrid waterGrid = new BitGrid();

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
			assert id <= 81 : "Only 81 unique pieces including water";
                        return id;
                }
        }

	public Grid() 
	{
		for (int i = 0; i < 2; i++) {
			pieceBitGrid[i] = new BitGrid();
			movablePieceBitGrid[i] = new BitGrid();
		}

		for (int i = 0; i < grid.length; i++)
			if (isWater[i])
				grid[i] = water;
	}

	public Grid(Grid g)
	{
		grid = g.grid.clone();
		for (int i = 0; i < 2; i++) {
			pieceBitGrid[i] = new BitGrid(g.pieceBitGrid[i]);
			movablePieceBitGrid[i] = new BitGrid(g.movablePieceBitGrid[i]);
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
                setMovable(p);
	}

	public void setMovable(Piece p) 
	{
                int i = p.getIndex();
                Rank rank = p.getRank();
		if (rank == Rank.BOMB
                    || rank == Rank.FLAG)
                    movablePieceBitGrid[p.getColor()].clearBit(i);
                else
                    movablePieceBitGrid[p.getColor()].setBit(i);
	}

	public void clearMovable(Piece p) 
	{
                int i = p.getIndex();
                movablePieceBitGrid[p.getColor()].clearBit(i);
        }

	static private void setWater(int i) 
	{
		isWater[i] = true;
		if (i >= 2 && i <= 129)
			waterGrid.setBit(i);
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
		movablePieceBitGrid[0].clearBit(i);
		movablePieceBitGrid[1].clearBit(i);
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

	public int moveCount(int color, int i)
	{
		return pieceBitGrid[color].xorBitCount(neighbor[0][i]);
	}

	public boolean hasAttack(int turn, int i)
	{
		return isCloseToEnemy(turn, i, 0);
	}

	public boolean hasAttack(Piece p)
	{
		return hasAttack(p.getColor(), p.getIndex());
	}

	public boolean isCloseToEnemy(int turn, int i, int n)
	{
		return pieceBitGrid[1-turn].andMask(neighbor[n][i]);
	}

	public int enemyCount(Piece p)
	{
		int i = p.getIndex();
		return pieceBitGrid[1-p.getColor()].andBitCount(neighbor[0][i]);
	}

	public int defenderCount(int color, int i)
	{
		return pieceBitGrid[color].andBitCount(neighbor[0][i]);
	}

	public int movablePieceCount(int color, int i, int n)
	{
		return movablePieceBitGrid[color].andBitCount(neighbor[n][i]);
	}

	public void getNeighboringBombsAndFlag(int turn, BitGrid out)
	{
		long low = pieceBitGrid[turn].get(0);
		long high = pieceBitGrid[turn].get(1);
		long bf_low = pieceBitGrid[1-turn].get(0)
		    & ~movablePieceBitGrid[1-turn].get(0) ;
		long bf_high = pieceBitGrid[1-turn].get(1)
		    & ~movablePieceBitGrid[1-turn].get(1) ;
		BitGrid.getNeighbors(low, high, bf_low, bf_high, out);
	}

	public void getNeighbors(int turn, BitGrid out)
	{
                pieceBitGrid[turn].getNeighbors(pieceBitGrid[1-turn], out);
	}

	public void getMovableNeighbors(int turn, BitGrid out)
	{
		pieceBitGrid[turn].getNeighbors(movablePieceBitGrid[1-turn], out);
	}

	public void getMovablePieces(int turn, BitGrid out)
	{

	// Note: this is a workaround the silly Java restriction that all
	// classes are created with new.  Here we just want to create
	// a temporary BitGrid composed of open squares and call
	// BitGrid.getNeighbors to find the movable pieces.  
	// In C, we would create the structure on the stack, and
	// the code would look obvious.  But to avoid heap allocation
	// in this heavily used function,
	// the members of BitGrid (2 longs) are created on the stack
	// individually.

		long low = ~(pieceBitGrid[turn].low | waterGrid.low);
		long high = ~(pieceBitGrid[turn].high | waterGrid.high);
		BitGrid.getNeighbors(low, high,
			movablePieceBitGrid[turn].low,
			movablePieceBitGrid[turn].high, out);
	}

	public void getMovablePieces(int turn, int n, BitGrid unpruned, BitGrid out, BitGrid outpruned)
	{
	// get the enemy piece bit grid

		long elow = pieceBitGrid[1-turn].low;
		long ehigh = pieceBitGrid[1-turn].high;

	// grow it

        // Prior to Version 12, the code was:
	//	for (int i = 0; i <= (n+1)/2; i++) {
        // For a more accurate qs, Version 12 adds pieces outside
        // the active area which could block an attack.
        // For example,
        // -- -- R9
        // -- R5 R6 |
        // xx -- -- |
        // xx -- B5 |
        // At n = 2, R5 was outside the active area, so R5 down
        // (or R9 left) was not considered.   But then at n = 1,
        // B5 up traps R6 and qs awards the win.
        
		for (int i = 0; i <= (n+2)/2; i++) {
			BitGrid.grow(elow, ehigh, out);
			elow = out.low & ~waterGrid.low;
			ehigh = out.high & ~waterGrid.high;
		}

	// get the possible move spaces

		long low = ~(pieceBitGrid[turn].low | waterGrid.low);
		long high = ~(pieceBitGrid[turn].high | waterGrid.high);

        // get the neighbors of the possible move spaces
        // this is the set of movable pieces

		BitGrid.getNeighbors(low, high,
			movablePieceBitGrid[turn].low,
			movablePieceBitGrid[turn].high, outpruned);

	// Intersect it with the movable pieces
	// so pieces outside of the enemy area are cleared

		elow &= outpruned.low;
		ehigh &= outpruned.high;

        // Unpruned squares are open squares that
	// are considered at all depths.
	// Add in the neighbors of the unpruned squares.

		BitGrid.getNeighbors(unpruned.low, unpruned.high,
			movablePieceBitGrid[turn].low,
			movablePieceBitGrid[turn].high, out);

		out.low |= elow;
		out.high |= ehigh;

            outpruned.low = outpruned.low & ~out.low;
            outpruned.high = outpruned.high & ~out.high;
	}

	public int movablePieceCount(int turn)
	{
		return Long.bitCount(movablePieceBitGrid[turn].get(0))
			+ Long.bitCount(movablePieceBitGrid[turn].get(1));
	}

	public int pieceCount(int turn)
	{
		return Long.bitCount(pieceBitGrid[turn].get(0))
			+ Long.bitCount(pieceBitGrid[turn].get(1));
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

	static public boolean isAlternatingMove(int m1, Move m2)
	{
		int from1 = Move.unpackFrom(m1);
		int to1 = Move.unpackTo(m1);

                // Opponent and player pieces not alternating
                // in the same column or row?
                int xdiff = Grid.getX(to1) - Grid.getX(from1);
                int ydiff = Grid.getY(to1) - Grid.getY(from1);
                if (xdiff != 0) {
                        if (m2.getFromX() != Grid.getX(to1)
                                || m2.getToX() != Grid.getX(from1))
                                return false;
                }
                if (ydiff != 0) {
                        if (m2.getFromY() != Grid.getY(to1)
                                || m2.getToY() != Grid.getY(from1))
                                return false;
                }

		return true;
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

	static public int yside(int color, int y)
    {
        if (color == Settings.topColor)
                return y;
        else
                return 9-y;
    }

	static public int side(int color, int i)
    {
		if (color == Settings.topColor)
			return i;
		else
			return getIndex(getX(i), 9-getY(i));
	}

	static public boolean isRetreat(int color, int m)
	{
        int from = Move.unpackFrom(m);
        int to = Move.unpackTo(m);

		int d = to - from;
		return (d == 11 && color == Settings.bottomColor)
			|| (d == -11 && color == Settings.topColor);
	}
}

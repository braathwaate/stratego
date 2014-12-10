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

public class Move
{
	// Prior to version 9.2, Move was extended from BMove.
	// BMove was a simple class containing from and to.
	// When the transposition table was introduced in version 9.2,
	// garbage collection became very slow, because the heap
	// became fragmented as the move objects were retained in
	// the transposition table.  This reveals one of the failings
	// in java, that all objects have to be allocated from the
	// heap.  The allocation is not slow, but if the heap becomes
	// fragmented, garbage collection becomes an issue.
	// As a result, when 9.2 played older versions
	// with both using the same timeclock setting,
	// version 9.2 used much more CPU time.
	//
	// The only objects in Java that are not allocated from
	// the heap are primitive objects.  So the solution (hack)
	// was to make BMove an int, packing the from and to into
	// a single int.  This is not to save memory, but to avoid
	// heap allocation during move generation and to avoid
	// objects in the transposition table.

	private int move;
	private Piece piece = null;

	public Move(Piece p, int f, int t)
	{
		move = packMove(f, t);
		piece = p;
	}

	public Move(Piece p, Spot f, Spot t)
	{
		move = packMove(f, t);
		piece = p;
	}

	public Move(Piece p, int m)
	{
		move = m;
		piece = p;
	}

	public Piece getPiece()
	{
		return piece;
	}

	static public int packMove(int f, int t)
	{
		return (f << 7) + t;
	}
	static public int packMove(Spot f, Spot t)
	{
        	int from = f.getX() + 1 + (f.getY() + 1) * 11 ;
        	int to = t.getX() + 1 + (t.getY() + 1) * 11 ;

		return packMove(from, to);
	}

	static public int unpackFrom(int m)
	{
		return (m >> 7);
	}

	static public int unpackTo(int m)
	{
		return m & 0x7F;
	}

        static public int unpackFromX(int m)
        {
                return unpackFrom(m) % 11 - 1;
        }

        static public int unpackFromY(int m)
        {
                return unpackFrom(m) / 11 - 1;
        }

        static public int unpackToX(int m)
        {
                return unpackTo(m) % 11 - 1;
        }

        static public int unpackToY(int m)
        {
                return unpackTo(m) / 11 - 1;
        }

	public int getMove()
	{
		return move;
	}

	public void setMove(int m)
	{
		move = m;
		piece = null;
	}

	public int getFrom()
	{
		return unpackFrom(move);
	}

	public int getTo()
	{
		return unpackTo(move);
	}

	public int getFromX()
        {
                return unpackFromX(move);
        }

        public int getFromY()
        {
                return unpackFromY(move);
        }

        public int getToX()
        {
                return unpackToX(move);
        }

        public int getToY()
        {
                return unpackToY(move);
        }

	public boolean equals(Object m)
	{
		return move == ((Move)m).move && piece.equals(((Move)m).piece);
	}
}

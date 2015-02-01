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

import java.util.EnumSet;

public class Piece implements Comparable<Piece>
{
	private int uniqueID = 0;
	private int color = 0;

	private Rank rank = null;

	private int value = 0;
	private Rank actingRankFleeLow = Rank.NIL;
	private Rank actingRankFleeHigh = Rank.NIL;
	private Rank actingRankChase = Rank.NIL;
	private int index = 0;
	
	public int moves = 0;	// times piece has moved

	// a known piece can be not shown
	// a shown piece can be unknown to the computer
	static private final int IS_SUSPECTED = 1 << 0;
	static private final int MAYBE_EIGHT = 1 << 1;	// unknown piece could be an eight
	static private final int IS_LESS = 1 << 2;
	static private final int IS_BLOCKER = 1 << 3;
	static private final int IS_KNOWN = 1 << 4;	// known to players
	static private final int IS_SHOWN = 1 << 5;	// visible on screen
	static Piece[] lastKill = new Piece[2];

	private int flags = 0;

	public Piece(int c, Rank r) 
	{
		uniqueID = Grid.UniqueID.get();
		color = c;
		rank = r;
	}

	public Piece(Piece p) 
	{
		copy(p);
	}

	public void copy(Piece p) 
	{
		uniqueID = p.uniqueID;
		color = p.color;
		moves = p.moves;
		rank = p.rank;
		actingRankFleeLow = p.actingRankFleeLow;
		actingRankFleeHigh = p.actingRankFleeHigh;
		actingRankChase = p.actingRankChase;
		flags = p.flags;
		value = p.value;
		index = p.index;
	}

	public void clear()
	{
		moves = 0;
		actingRankChase = Rank.NIL;
		actingRankFleeLow = Rank.NIL;
		actingRankFleeHigh = Rank.NIL;
		flags = 0;
		value = 0;
		index = 0;
	}

	public void setRank(Rank r)
	{
		rank = r;
	}

	public void makeKnown()
	{
		flags |= IS_KNOWN;
	}

	public int getColor() 
	{
		return color;
	}

	public Rank getApparentRank() 
	{
		if (!isKnown())
			return Rank.UNKNOWN;
		else
			return rank;
	}
	
	public Rank getRank() 
	{
		return rank;
	}
	
	public int getID() 
	{
		return uniqueID;
	}
	
	public boolean isShown()
	{
		return (flags & IS_SHOWN) != 0;
	}

	public void kill()
	{
		setShown(true);
		if (color >= 0)
			lastKill[color] = this;
	}

	public void setShown(boolean b)
	{
		if (b)
			flags |= IS_SHOWN;
		else
			flags &= ~IS_SHOWN;
	}	
	
	public boolean isKnown()
	{
		return (flags & IS_KNOWN) != 0;
	}

	public boolean isHighLight()
	{
		return isKnown() && !(color >= 0 && lastKill[color] == this);
	}

	public void setKnown(boolean b)
	{
		if (b)
			flags |= IS_KNOWN;
		else
			flags &= ~IS_KNOWN;
	}

	public boolean hasMoved()
	{
		return moves != 0;
	}
	
	public void setMoved()
	{
		moves++;
	}

	public void setAiValue(int v)
	{
		value = v;
	}

	public int aiValue()
	{
		return value;
	}

	public Rank getActingRankChase()
	{
		if (isKnown())
			return rank;
		return actingRankChase;
	}

	public void setActingRankChaseEqual(Rank r)
	{
		actingRankChase = r;
		flags &= ~IS_LESS;
	}

	public void setActingRankChaseLess(Rank r)
	{
		actingRankChase = r;
		flags |= IS_LESS;
	}

	public boolean isRankLess()
	{
		return (flags & IS_LESS) != 0;
	}

	public Rank getActingRankFleeLow()
	{
		if (isKnown())
			return rank;
		return actingRankFleeLow;
	}

	public Rank getActingRankFleeHigh()
	{
		if (isKnown())
			return rank;
		return actingRankFleeHigh;
	}

	public void setActingRankFlee(Rank r)
	{
		if (actingRankFleeLow == Rank.NIL
			|| actingRankFleeLow.toInt() > r.toInt())
			actingRankFleeLow = r;

		if (actingRankFleeHigh == Rank.NIL
			|| actingRankFleeHigh.toInt() < r.toInt())
			actingRankFleeHigh = r;
	}

	public void clearActingRankFlee()
	{
		actingRankFleeLow = Rank.NIL;
		actingRankFleeHigh = Rank.NIL;
	}

	public boolean isSuspectedRank()
	{
		return (flags & IS_SUSPECTED) != 0;
	}

	public void setSuspectedRank(Rank r)
	{
		setRank(r);
		flags |= IS_SUSPECTED;
	}

	public void setBlocker(boolean b)
	{
		if (b)
			flags |= IS_BLOCKER;
		else
			flags &= ~IS_BLOCKER;
	}

	public boolean isBlocker()
	{
		return (flags & IS_BLOCKER) != 0;
	}

	public void setMaybeEight(boolean b)
	{
		if (b)
			flags |= MAYBE_EIGHT;
		else
			flags &= ~MAYBE_EIGHT;
	}

	public boolean getMaybeEight()
	{
		return (flags & MAYBE_EIGHT) != 0;
	}

	public int getIndex()
	{
		return index;
	}

	public void setIndex(int i)
	{
		index = i;
	}

	public int compareTo(Piece p)
	{
		return uniqueID - p.uniqueID;
	}

	// returns all flags that affect the board position
	public int getStateFlags()
	{
		return flags & ~IS_SHOWN;
	}
	
	public boolean equals(Object p)
	{
		return (uniqueID == ((Piece)p).uniqueID);
	}

	public int winFight(Piece defender)
	{
		return rank.winFight(defender.rank);
	}
}

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
	    public enum Flags {
		IS_SUSPECTED,
		MAYBE_EIGHT,	// unknown piece could be an eight
		IS_LESS,
		IS_BLOCKER,
		IS_SHOWN,	// visible on screen
		IS_KNOWN	// known to players
	    }

	private EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);

	public Piece(int id, int c, Rank r) 
	{
		uniqueID = id;
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
		flags = p.flags.clone();
		value = p.value;
		index = p.index;
	}

	public void clear()
	{
		moves = 0;
		actingRankChase = Rank.NIL;
		actingRankFleeLow = Rank.NIL;
		actingRankFleeHigh = Rank.NIL;
		flags = EnumSet.noneOf(Flags.class);
		value = 0;
		index = 0;
	}

	public void setRank(Rank r)
	{
		rank = r;
	}

	public void makeKnown()
	{
		flags.add(Flags.IS_KNOWN);
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
		return flags.contains(Flags.IS_SHOWN);
	}
	
	public void setShown(boolean b)
	{
		if (b)
			flags.add(Flags.IS_SHOWN);
		else
			flags.remove(Flags.IS_SHOWN);
	}	
	
	public boolean isKnown()
	{
		return flags.contains(Flags.IS_KNOWN);
	}
	
	public void setKnown(boolean b)
	{
		if (b)
			flags.add(Flags.IS_KNOWN);
		else
			flags.remove(Flags.IS_KNOWN);
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
		flags.remove(Flags.IS_LESS);
	}

	public void setActingRankChaseLess(Rank r)
	{
		actingRankChase = r;
		flags.add(Flags.IS_LESS);
	}

	public boolean isRankLess()
	{
		return flags.contains(Flags.IS_LESS);
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
		return flags.contains(Flags.IS_SUSPECTED);
	}

	public void setSuspectedRank(Rank r)
	{
		setRank(r);
		flags.add(Flags.IS_SUSPECTED);
	}

	public void setBlocker(boolean b)
	{
		if (b)
			flags.add(Flags.IS_BLOCKER);
		else
			flags.remove(Flags.IS_BLOCKER);
	}

	public boolean isBlocker()
	{
		return flags.contains(Flags.IS_BLOCKER);
	}

	public void setMaybeEight(boolean b)
	{
		if (b)
			flags.add(Flags.MAYBE_EIGHT);
		else
			flags.remove(Flags.MAYBE_EIGHT);
	}

	public boolean getMaybeEight()
	{
		return flags.contains(Flags.MAYBE_EIGHT);
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
	
	public boolean equals(Object p)
	{
		return (uniqueID == ((Piece)p).uniqueID);
	}

	public int winFight(Piece defender)
	{
		return rank.winFight(defender.rank);
	}
}

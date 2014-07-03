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

public class Piece implements Comparable<Piece>
{
	private int uniqueID = 0;
	private int color = 0;

	private Rank rank = null;

	private boolean shown = false;	// visible on screen
	private boolean known = false;	// known to players
	// a known piece can be not shown
	// a shown piece can be unknown to the computer
	private int value = 0;
	private Rank actingRankFlee = Rank.NIL;
	private Rank actingRankChase = Rank.NIL;
	private boolean suspectedRank = false;
	private int index = 0;
	
	public int moves = 0;	// times piece has moved
	private boolean blocker = false;

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
		known = p.known;
		shown = p.shown;
		actingRankFlee = p.actingRankFlee;
		actingRankChase = p.actingRankChase;
		suspectedRank = p.suspectedRank;
		value = p.value;
		index = p.index;
		blocker = p.blocker;
	}

	public void clear()
	{
		moves = 0;
		known = false;
		shown = false;
		actingRankChase = Rank.NIL;
		actingRankFlee = Rank.NIL;
		suspectedRank = false;
		value = 0;
		index = 0;
		blocker = false;
	}

	public void setRank(Rank r)
	{
		rank = r;
	}

	public void makeKnown()
	{
		known = true;
	}

	public int getColor() 
	{
		return color;
	}

	public Rank getApparentRank() 
	{
		if (!known)
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
		return shown;
	}
	
	public void setShown(boolean b)
	{
		shown = b;
	}	
	
	public boolean isKnown()
	{
		return known;
	}
	
	public void setKnown(boolean b)
	{
		known = b;
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
		if (known)
			return rank;
		return actingRankChase;
	}

	public void setActingRankChase(Rank r)
	{
		actingRankChase = r;
	}

	public Rank getActingRankFlee()
	{
		if (known)
			return rank;
		return actingRankFlee;
	}

	public void setActingRankFlee(Rank r)
	{
		actingRankFlee = r;
	}

	public boolean isSuspectedRank()
	{
		return suspectedRank;
	}

	public void setSuspectedRank(Rank r)
	{
		setRank(r);
		suspectedRank = true;
	}

	public void setBlocker(boolean b)
	{
		blocker = b;
	}

	public boolean isBlocker()
	{
		return blocker;
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

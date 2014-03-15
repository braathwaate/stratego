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
	private boolean moved = false;	// used by screen view thread so
					// do not update by ai
	private int value = 0;
	private Rank actingRankFlee = Rank.NIL;
	private Rank actingRankChase = Rank.NIL;
	private int index = 0;
	
	public int moves = 0;	// times piece has moved

	public Piece(int id, int c, Rank r) 
	{
		uniqueID = id;
		color = c;
		rank = r;
	}

	public Piece(Piece p) 
	{
		uniqueID = p.uniqueID;
		color = p.color;
		moved = p.moved;
		moves = p.moves;
		rank = p.rank;
		known = p.known;
		shown = p.shown;
		actingRankFlee = p.actingRankFlee;
		actingRankChase = p.actingRankChase;
		value = p.value;
		index = p.index;
	}

	public void copy(Piece p) 
	{
		uniqueID = p.uniqueID;
		color = p.color;
		moved = p.moved;
		moves = p.moves;
		rank = p.rank;
		known = p.known;
		shown = p.shown;
		actingRankFlee = p.actingRankFlee;
		actingRankChase = p.actingRankChase;
		value = p.value;
		index = p.index;
	}

	public void clear()
	{
		moved = false;
		moves = 0;
		known = false;
		shown = false;
		actingRankChase = Rank.NIL;
		actingRankFlee = Rank.NIL;
		value = 0;
		index = 0;
	}

	public void setRank(Rank r)
	{
		rank = r;
	}

	public int getColor() 
	{
		return color;
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
		return moved;
	}
	
	public void setMoved(boolean m)
	{
		moved = m;
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
		return actingRankChase;
	}

	public void setActingRankChase(Rank r)
	{
		actingRankChase = r;
	}

	public Rank getActingRankFlee()
	{
		return actingRankFlee;
	}

	public void setActingRankFlee(Rank r)
	{
		actingRankFlee = r;
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

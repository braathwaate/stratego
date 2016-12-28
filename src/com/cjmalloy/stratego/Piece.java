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

	private Rank actualRank = null;
	private Rank rank = null;

	private int value = 0;
	private int actingRankFlee = 0;
	private Rank actingRankChase = Rank.NIL;
	private int index = 0;
	
	public int moves = 0;	// times piece has moved

	// a known piece can be not shown
	// a shown piece can be unknown to the computer
	static private final int MAYBE_EIGHT = 1 << 0;	// unknown piece could be an eight
	static private final int IS_WEAK = 1 << 1;
	static private final int IS_KNOWN = 1 << 2;	// known to players
	static private final int IS_LESS = 1 << 3;
	static private final int IS_SUSPECTED = 1 << 4;
	static private final int IS_SHOWN = 1 << 5;	// visible on screen
	static Piece[] lastKill = new Piece[2];

	private int flags = 0;

	public Piece(int c, Rank r) 
	{
		uniqueID = Grid.UniqueID.get();
		color = c;
		actualRank = r;
		rank = r;
		clearActingRankFlee();
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
		actualRank = p.actualRank;
		actingRankFlee = p.actingRankFlee;
		actingRankChase = p.actingRankChase;
		flags = p.flags;
		value = p.value;
		index = p.index;
	}

	public void clear()
	{
		moves = 0;
		actingRankChase = Rank.NIL;
		clearActingRankFlee();
		flags = 0;
		value = 0;
		index = 0;
	}

	public void setRank(Rank r)
	{
		rank = r;
		flags &= ~IS_SUSPECTED;
	}

	// When the AI is playing an external agent, opponent rank in
	// the Piece object must be UNKNOWN, so that the rank can be
	// updated with suspected rank.  saveActualRank() saves the setup
	// rank and revealRank() restores it when the piece becomes
	// known.

	public void saveActualRank()
	{
		actualRank = rank;
	}

	public boolean isRevealed()
	{
		return actualRank != Rank.UNKNOWN;
	}

	public void revealRank()
	{
		if (actualRank != Rank.UNKNOWN)
			rank = actualRank;
		makeKnown();
	}

	// actualRank is the actual piece rank (if available)
	public Rank getActualRank()
	{
		return actualRank;
	}

	// rank is the piece rank as seen or suspected by the AI
	public Rank getRank()
	{
		return rank;
	}

	// display rank is the rank displayed on the gui.  If a human
	// opponent is playing, it is the actual rank of the opponent
	// pieces.  But if the AI is playing remotely, and does not
	// know the opponent pieces, it is the seen or suspected rank.
	public Rank getDisplayRank()
	{
		if (actualRank == Rank.UNKNOWN)
			return rank;
		else
			return actualRank;
	}

	// getApparentRank() returns unknown if the ai piece is unknown,
	// and otherwise the suspected rank.  This is the rank that
	// each side sees.
	public Rank getApparentRank()
	{
		if (!isKnown() && color == Settings.topColor)
			return Rank.UNKNOWN;
		else
			return rank;
	}

	public void revealRank(Rank r)
	{
		actualRank = r;
	}

	public void makeKnown()
	{
		flags |= IS_KNOWN;
		flags &= ~(IS_SUSPECTED | IS_LESS | MAYBE_EIGHT);
		clearActingRankFlee();
	}

	public int getColor() 
	{
		return color;
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
		assert !isKnown() : "actingRankChase: piece must be unknown";
		return actingRankChase;
	}

	public void setActingRankChase(Rank r)
	{
		// Reset moves.  Chase rank matures after a certain
		// number of subsequent moves to give the AI time
		// to confirm (attack) the suspected rank.
		//
		// Note: that if a prior rank was already set, the move
		// count is not reset.  For example,
		// (1) an opponent piece chased a Three,
		//	earning it chase rank of Three.
		//	so the AI suspects that the piece is Two.
		// (2) Both of the opponent Threes are known or gone.
		// (3) So the AI Four thinks that it is invincible,
		//	because the only lower piece is a One.
		//	But Then the AI Four is attacked by the real Two.
		// 	This makes the piece in (1) a suspected One.
		// (4) Next, the suspected One chases the AI Two.
		//	This earns it a chase rank of Two.
		//  It would be a mistake to reset moves after (4),
		//	because the AI Two was invincible and then not.
		// 	This could cause the AI Two to become cornered.

		if (actingRankChase == Rank.NIL && moves != 0)
			moves = 1;

		// actingRankChase of unknown takes precedence over
		// actingRankFlee (see setActingRankFlee)

		if (r == Rank.UNKNOWN)
			actingRankFlee &= ~(1 << Rank.UNKNOWN.ordinal());

		actingRankChase = r;
	}

	public void setActingRankChaseEqual(Rank r)
	{
		setActingRankChase(r);
		flags &= ~IS_LESS;
	}

	public void setActingRankChaseLess(Rank r)
	{
		setActingRankChase(r);
		flags |= IS_LESS;
	}

	public boolean isRankLess()
	{
		return (flags & IS_LESS) != 0;
	}

	public boolean isFleeing(Rank rank)
	{
		return (actingRankFlee & (1 << rank.ordinal())) != 0;
	}

	public Rank getActingRankFleeLow()
	{
		assert !isKnown() : "actingRankFleeLow: piece must be unknown";
		if (actingRankFlee == 0)
			return Rank.NIL;
		return Rank.toRank(Integer.numberOfTrailingZeros(actingRankFlee));
	}

	public Rank getActingRankFleeHigh()
	{
		assert !isKnown() : "actingRankFleeHigh: piece must be unknown";
		if (actingRankFlee == 0)
			return Rank.NIL;
		return Rank.toRank(31 - Integer.numberOfLeadingZeros(actingRankFlee));
	}

	public void setActingRankFlee(Rank r)
	{
		// If a piece both chases and flees from an unknown,
		// the chase rank has precedence, because an unknown
		// chase rank suggests that the piece is weak, whereas
		// a flee rank of unknown suggests that the piece is
		// strong (fleeing to prevent discovery).

		if (r == Rank.UNKNOWN
			&& actingRankChase == Rank.UNKNOWN)
			return;

		actingRankFlee |= (1 << r.ordinal());
	}

	public void clearActingRankFlee()
	{
		actingRankFlee = 0;
	}

	public boolean isSuspectedRank()
	{
		return (flags & IS_SUSPECTED) != 0;
	}

	public void setSuspectedRank(Rank r)
	{
		rank = r;
		flags |= IS_SUSPECTED;
	}

	public void setWeak(boolean b)
	{
		if (b)
			flags |= IS_WEAK;
		else
			flags &= ~IS_WEAK;
	}

	public boolean isWeak()
	{
		return (flags & IS_WEAK) != 0;
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

	// Returns all piece flags that affect the board hash.

	// For example, equivalent pieces are:
	// all known pieces of the same rank
	// all unknown pieces of the same rank (suspected)
	// all unknown unranked pieces that are maybe an eight
	// all unknown unranked pieces that are marked weak
	//
	// TBD: not quite true, because unknown pieces can have
	// chase rank that makes them differ, so this could result
	// in undesired transposition cache equivalency

	public int getStateFlags()
	{
		return flags & (MAYBE_EIGHT | IS_WEAK | IS_KNOWN);
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

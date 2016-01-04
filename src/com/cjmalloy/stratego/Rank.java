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


public enum Rank
{
	WATER("W"),
	ONE("1"),
	TWO("2"),
	THREE("3"),
	FOUR("4"),
	FIVE("5"),
	SIX("6"),
	SEVEN("7"),
	EIGHT("8"),
	NINE("9"),
	SPY("S"),
	BOMB("B"),
	FLAG("F"),
	UNKNOWN("?"),
	NIL("-");

	public String value;
	Rank(String s) {
		value = s;
	}

	static public final int WINS = 1;
	static public final int EVEN = -1;
	static public final int UNK = -2;
	static public final int LOSES = 0;

	static public Rank toRank(int rank)
	{
		final Rank ranks[] = {WATER, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, SPY, BOMB, FLAG, UNKNOWN, NIL};
		return ranks[rank];
	}
	
	static public int nRanks()
	{
		return Rank.NIL.ordinal();
	}
	
	static public int getRanks(Rank rank)
	{	
		final int[] ranks = {0, 1, 1, 2, 3, 4, 4, 4, 5, 8, 1, 6, 1};

		return ranks[rank.ordinal()];
	}


	// return 1 attacker wins
	// return -1 equal
	// return 0 attacker loses
	public int winFight(Rank defend)
	{
		if (defend == Rank.FLAG)
			return WINS;

		if (this == Rank.UNKNOWN || defend == Rank.UNKNOWN)
			return UNK;

		if (this == defend)
		{
			if (Settings.bDefendAdvantage)
				return LOSES;
			else
				return EVEN;
		}
		if (defend == Rank.BOMB)
		{
			if (this == Rank.EIGHT)
				return WINS;
			if (Settings.bOneTimeBombs)
				return EVEN;
			else
				return LOSES;
		}
		if (this == Rank.SPY
			&& defend == Rank.ONE)
			return WINS;
		
		if (ordinal() < defend.ordinal())
			return WINS;
		return LOSES;
	}
}



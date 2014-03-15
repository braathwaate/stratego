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
	WATER,
	ONE,
	TWO,
	THREE,
	FOUR,
	FIVE,
	SIX,
	SEVEN,
	EIGHT,
	NINE,
	SPY,
	BOMB,
	FLAG,
	UNKNOWN,
	NIL;

	static public final int WINS = 1;
	static public final int EVEN = -1;
	static public final int UNK = -2;
	static public final int LOSES = 0;
	public int toInt()
	{
		return ordinal();
	}
	
	static public int nRanks()
	{
		return Rank.NIL.ordinal();
	}
	
	public static int getRanks(int i)
	{	
		int[] ranks = {1, 1, 2, 3, 4, 4, 4, 5, 8, 1, 6, 1};

		return ranks[i];
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
		if (this == Rank.SPY & defend == Rank.ONE)
				return WINS;
		
		if (toInt() < defend.toInt())
			return WINS;
		return LOSES;
	}
}


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

	public int toInt()
	{
		switch (this)
		{
		case ONE:
			return 1;
		case TWO:
			return 2;
		case THREE:
			return 3;
		case FOUR:
			return 4;
		case FIVE:
			return 5;
		case SIX:
			return 6;
		case SEVEN:
			return 7;
		case EIGHT:
			return 8;
		case NINE:
			return 9;
		case SPY:
			return 10;
		default:
			return 0;
		}
	}
	
	public static int getRanks(int i)
	{	
		int[] ranks = {1, 1, 2, 3, 4, 4, 4, 5, 8, 1};

		return ranks[i];
	}


	// return 1 attacker wins
	// return -1 equal
	// return 0 attacker loses
	public int winFight(Rank defend)
	{
		if (this == defend)
		{
			if (Settings.bDefendAdvantage)
				return 0;
			else
				return -1;
		}
		if (defend == Rank.FLAG)
			return 1;
		if (defend == Rank.BOMB)
		{
			if (this == Rank.EIGHT)
				return 1;
			if (Settings.bOneTimeBombs)
				return -1;
			else
				return 0;
		}
		if (this == Rank.SPY & defend == Rank.ONE)
				return 1;
		
		if (toInt() < defend.toInt())
			return 1;
		return 0;
	}
}


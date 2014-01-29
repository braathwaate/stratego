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
	
	public int aiValue()
	{
		switch (this)
		{
		case FLAG:
			return 6000;
		case BOMB:
			return 100;//6
		case SPY:
			return 360;
		case ONE:
			return 420;
		case TWO:
			return 360;
		case THREE:
			return 150;//2
		case FOUR:
			return 80;//3
		case FIVE:
			return 45;//4
		case UNKNOWN:	// TBD: unknown value changes during game
			return 35;//4
		case SIX:
			return 30;//4
		case SEVEN:
			return 15;//4
		case EIGHT:
			return 60;//5
		case NINE:
			return 7;//8
		default:
			return 0;
		}
	}

	public static int getRanks(int i)
	{	
		int[] ranks = {1, 1, 2, 3, 4, 4, 4, 5, 8, 1};

		return ranks[i];
	}

	public static int aiTotalValue()
	{
		return 9000-4;
	}
}

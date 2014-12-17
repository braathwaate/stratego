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


public class BitGrid 
{
	private long low;
	private long high;

	BitGrid() { low = 0; high = 0; }
	BitGrid(BitGrid b) { low = b.low; high = b.high; }

	public void setBit(int i) 
	{
		if (i < 63)
			low |= (1L << i);
		else
			high |= (1L << (i - 63));
	}

	public void clearBit(int i) 
	{
		if (i < 63)
			low &= ~(1L << i);
		else
			high &= ~(1L << (i - 63));
	}

	public boolean testBit(int i)
	{
		if (i < 63)
			return (low & (1L << i)) != 0;
		else
			return (high & (1L << (i - 63))) != 0;
	}

	public boolean andMask(BitGrid b)
	{
		return (high & b.high) != 0
			|| (low & b.low) != 0;
	}

	public boolean xorMask(BitGrid b)
	{
		return ((high & b.high) ^ b.high) != 0
			|| ((low & b.low) ^ b.low) != 0;
	}
}

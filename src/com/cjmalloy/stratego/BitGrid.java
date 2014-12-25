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

// BitGrid (aka Bitboard)
// 128-bit grid


// low
// rows at bit positions 10-19, 21-30, 32-41, 43-52, 54-63
// (low to high bits)
// -- -- 0  0  0  0  0  0  0  0  0
// 0  12 13 14 15 16 17 18 19 20 21
// 0 
// 0 
// 0 
// 0  56 57 xx xx 60 61 xx xx 64 65

// high
// rows at bit positions 1-10, 12-21, 23-32, 34-43, 45-54
// (low to high bits)
// 0   67  68  xx  xx  71  72  xx  xx  75  76
// 0   78
// 0   89
// 0   100
// 110 111 112 113 114 115 116 117 118 119 120
// 0   0   0   0   0   0   0   0   0   --  --

public class BitGrid 
{
	public long low;
	public long high;

	public BitGrid() { low = 0; high = 0; }
	public BitGrid(BitGrid b) { low = b.low; high = b.high; }

	public void setBit(int i) 
	{
		if (i <= 65)
			low |= (1L << (i-2));
		else
			high |= (1L << (i - 66));
	}

	public void clearBit(int i) 
	{
		if (i <= 65)
			low &= ~(1L << (i-2));
		else
			high &= ~(1L << (i - 66));
	}

	public boolean testBit(int i)
	{
		if (i <= 65)
			return (low & (1L << (i-2))) != 0;
		else
			return (high & (1L << (i - 66))) != 0;
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

	// This returns the neighboring bits
	// For example,
	// 1 1 0 0	0 0 0 0
	// 0 0 1 0	0 0 0 1
	// 0 0 0 0	0 0 1 0
	// The bits in the lower right corner of each map are neighbors.  
	//
	// This function returns 0 if there are no neighboring bits.

	public void getNeighbors(BitGrid in, long [] out)
	{
		out[0] = ((low << 1) & in.low)
			| ((low >>> 1) & in.low)
			| ((low << 11) & in.low)
			| (((low >>> 11) | ((in.high & 0x7fe) << 44)) & in.low);

		out[1] = ((high << 1) & in.high)
			| ((high >>> 1) & in.high)
			| ((high << 11) & in.high)
			| (((high >>> 11) | ((in.low & 0x7fe) << 44)) & in.high);
	}
}

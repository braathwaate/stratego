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
	public BitGrid(long l, long h) { low = l; high = h; }

	public long get(int i)
	{
		if (i == 0)
			return low;
		else
			return high;
	}

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

	public int andBitCount(BitGrid b)
	{
		return Long.bitCount(high & b.high)
			+ Long.bitCount(low & b.low);
	}

	public boolean xorMask(BitGrid b)
	{
		return ((high & b.high) ^ b.high) != 0
			|| ((low & b.low) ^ b.low) != 0;
	}

	// This grows the bits in the bit map
	// For example,
	// in:		out:
	// 1 1 0 0	1 1 1 0
	// 0 0 1 0	1 1 1 1
	// 0 0 0 0	0 0 1 0

	static public void grow(long low, long high, BitGrid out)
	{
		out.low =  low
			| (low << 1) 
			| (low >>> 1)
			| (low << 11)
			| (low >>> 11)
			| ((high & 0x7fe) << 53);

		out.high = high
			| (high << 1)
			| (high >>> 1)
			| (high << 11)
			| (high >>> 11)
			| (low >>> 53);
	}

	// This returns the neighboring bits
	// For example,
	// 1 1 0 0	0 0 0 0
	// 0 0 1 0	0 0 0 1
	// 0 0 0 0	0 0 1 0
	// The bits in the lower right corner of each map are neighbors.  
	//
	// This function returns 0 if there are no neighboring bits.

	static public void getNeighbors(long low, long high, long inlow, long inhigh, BitGrid out)
	{
		grow(low, high, out);
		out.low &= inlow;
		out.high &= inhigh;
	}

	public void getNeighbors(BitGrid in, BitGrid out)
	{
		getNeighbors(low, high, in.low, in.high, out);
	}
}

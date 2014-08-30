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

import com.cjmalloy.stratego.BMove;

// the transposition table entry
class TTEntry {
  public long key; // should be at least 64-bit to be safe
  public BMove move;
  public int score;
  public int flag;
  public int depth;
  public int distance;

	public TTEntry(long hash, int from, int to) {
		  key = hash;
		  move = new BMove(from, to);
	}

	public void clear()
	{
		key = 0;
	}
}


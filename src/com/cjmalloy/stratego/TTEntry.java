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


// the transposition table entry
public class TTEntry {
  public int turn;	// for debugging
  public long hash; // should be at least 64-bit to be safe
  public int value;
  public int depth;

           public enum Flags {
                EXACT,
                LOWERBOUND,
                UPPERBOUND
            }

        public EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);

	public TTEntry(int t, long h, int v, int d, Flags f) {
		turn = t;
		hash = h;
		value = v;
		depth = d;
		flags.add(f);
	}
}


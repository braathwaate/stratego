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

	// A deep search is a forward pruned search to reach some goal.
	// A broad search is the standard search.
	// If an entry was created during a deep search, it can
	// only be reused if a deep search is running.
	// A broad search entry can be reused by either search.

	public enum SearchType {
		BROAD,
		DEEP
	}
	public SearchType type;	// deep or broad search
	public int moveRoot;

	public long hash; // should be at least 64-bit to be safe
	public int bestMove;
	public int bestValue;
	public int depth;
	public int turn;

	public enum Flags {
		EXACT,
		LOWERBOUND,
		UPPERBOUND
	}

        public Flags flags;

	public TTEntry()
	{
	}
}


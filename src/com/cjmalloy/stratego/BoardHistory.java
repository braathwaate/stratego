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
import java.util.HashSet;

// import gnu.trove.*;

public class BoardHistory
{
	public long hash;
	public long hash1;
	public long hash2;
	protected HashSet<Long>  hashset = new HashSet<Long>();
	public void clear() { hashset.clear(); hash = 0; }
	public void add() { hash2=hash1; hash1=hash; hashset.add(hash); }
	public boolean get() { return hashset.contains(hash); }
	public void remove() { hashset.remove(hash); }
}


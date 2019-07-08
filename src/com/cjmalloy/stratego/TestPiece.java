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
import com.cjmalloy.stratego.Piece;

public class TestPiece extends Piece
{
        protected Piece orig;
        public int targetValue;
        public int[][] plan;
        public boolean neededPiece;

	public TestPiece(Piece p) 
	{
		super(p);
		orig = p;
	}

	public Piece boardPiece()
	{
		return orig;
	}
}

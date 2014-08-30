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

public class UndoMove extends Move
{
	public Piece tp = null;
	public Piece fpcopy = null;
	public Piece tpcopy = null;
	public long hash = 0;

	public UndoMove(Piece fpin, Piece tpin, int f, int t, long h)
	{
		super(fpin, f, t);
		tp = tpin;
		fpcopy = new Piece(fpin);
		if (tp != null)
			tpcopy = new Piece(tp);
		else
			tpcopy = null;
		hash = h;
	}
}


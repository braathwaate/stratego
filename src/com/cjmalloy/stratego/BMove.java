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

public class BMove
{

	protected int from;
	protected int to;

	public BMove()
	{
		from = 0;
		to = 0;
	}

	public BMove(BMove m)
	{
		from = m.from;
		to = m.to;
	}

	public BMove(int f, int t)
	{
		from = f;
		to = t;
	}

	public BMove(Spot f, Spot t)
	{
		from = f.getX() + 1 + (f.getY() + 1) * 11 ;
		to = t.getX() + 1 + (t.getY() + 1) * 11 ;
	}

	public void set(BMove m)
	{
	    from = m.from;
	    to = m.to;
	}

	public void clear()
	{
	    from = 0;
	    to = 0;
	}

	public int getFrom()
	{
		return from;
	}

	public int getTo()
	{
		return to;
	}

	public int getFromX()
	{
		return from % 11 - 1;
	}

	public int getFromY()
	{
		return from / 11 - 1;
	}
	
	public int getToX()
	{
		return to % 11 - 1;
	}

	public int getToY()
	{
		return to / 11 - 1;
	}

	@Override public boolean equals(Object m)
        {
                return from==((BMove)m).from && to==((BMove)m).to;
	}

}



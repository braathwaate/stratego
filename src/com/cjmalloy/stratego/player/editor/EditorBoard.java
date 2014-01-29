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

package com.cjmalloy.stratego.player.editor;

import java.util.Collections;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;



public class EditorBoard extends Board implements EditorControls
{
	public EditorBoard()
	{
		for (int i=0;i<40;i++)
		for (Piece p: tray)
			if (p.getColor() == 1) 
			{
				tray.remove(p);
				break;
			}
	}
	
	public boolean move(Move m)
	{		
		//remember, tray pieces are at -1, -1 so this works
		if (m.getFrom().getY()>3)
			return false;
		if (m.getTo().getY()>3)
			m = new Move(m.getPiece(), m.getFrom(), IN_TRAY);
		
		if (m.getTo().equals(IN_TRAY))
			return remove(m.getFrom());
		else if (getPiece(m.getTo()) != null)
			return false;
		else if (m.getFrom().equals(IN_TRAY))
			return add(m.getPiece(), m.getTo());
		else 
		{
			setPiece(m.getPiece(), m.getTo());
			setPiece(null, m.getFrom());
			return true;
		}
	}
	
	public boolean add(Piece p, Spot s)
	{
		if (getPiece(s) == null)
		{
			setPiece(p, s);
			tray.remove(p);
			Collections.sort(tray);
			return true;
		}
		return false;
	}
	
	public boolean remove(Spot s)
	{
		if (s.equals(IN_TRAY))
			return false;
		else
		{
			tray.add(getPiece(s));
			setPiece(null, s);
			Collections.sort(tray);
			return true;
		}
	}
	
	public void clear()
	{
		super.clear();
		Settings.topColor = 0;
	}
}

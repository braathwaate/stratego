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

package com.cjmalloy.stratego.server;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Engine;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Status;

public class ServerEngine extends Engine
{
	private Game game = null;
	
	public ServerEngine(Game g)
	{
		game = g;
		board = new Board();
		Settings.topColor = 0;
		Settings.bottomColor = 1;
	}
	
	public void play(int color)
	{
		if (status != Status.SETUP)
			return;
		
		if (board.getTraySize() != 0)
		{
			int l_limit, u_limit;
			if (color == 0)
			{
				l_limit = 0;
				u_limit = 4;
			}
			else
			{
				l_limit = 6;
				u_limit = 10;
			}
			for (int i=0;i<10;i++)
			for (int j=l_limit;j<u_limit;j++)
				if (board.getPiece(i, j) == null)
					for (int k=0;k<board.getTraySize();k++)
						if (getTrayPiece(k).getColor() == color)
							setupPlacePiece(getTrayPiece(k), new Spot(i, j));
		}
	
		if (board.getTraySize() == 0)
			status = Status.PLAYING;
	}
	

	public void requestMove(Move m, int color)
	{
		if (m == null) return;
		
		if (status == Status.PLAYING)
		{
			if (board.getPiece(m.getFrom()) == null) return;
			if (board.getPiece(m.getFrom()).getColor() != color) return;
			
			if (color == turn)
			{
				board.hideAll();
				requestMove(m);
			}
		}
		else if (status == Status.SETUP)
		{
			if (m.getFrom() == 0) // Board.IN_TRAY
			{
				for (int i=0;i<board.getTraySize();i++)
					if (board.getTrayPiece(i).getRank().equals(m.getPiece().getRank()))
						setupPlacePiece(board.getTrayPiece(i), new Spot(m.getToX(), m.getToY()));
			}
			else if (m.getTo() == 0) // Board.IN_TRAY
			{
				setupRemovePiece(new Spot(m.getFromX(), m.getFromY()));
				board.hideAll();
			}
			else
			{
				if (setupRemovePiece(new Spot(m.getFromX(), m.getFromY())))
					setupPlacePiece(m.getPiece(), new Spot(m.getToX(), m.getToY()));
			}
		}
	}
	
	@Override
	protected void gameOver(int winner)
	{
		game.gameOver(winner);
	}

	@Override
	protected void update()
	{
		game.update();
	}
}

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

package com.cjmalloy.stratego.player;

import java.io.DataInputStream;
import java.io.IOException;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.server.shared.ClientConnection;
import com.cjmalloy.stratego.server.shared.Connection;
import com.cjmalloy.stratego.server.shared.Message;
import com.cjmalloy.stratego.server.shared.MessageHandler;


public class Client implements MessageHandler
{
	private WView view = null;
	private Connection conn = ClientConnection.getInstance();
	
	public Client(WView v)
	{
		view = v;
		attach();
	}
	
	public boolean read(int type, DataInputStream in)
	{
		try
		{
			int x, y, color, rank;

			switch (Message.values()[type])
			{
			case SETUP:
				x = in.readInt();
				view.setColor(x);
				return true;
			case GRID:
				x = in.readInt();
				y = in.readInt();
				color = in.readInt();
				rank = in.readInt();
				view.update(new Spot(x, y), new Piece(0, color, Rank.values()[rank]));
				return true;
			case TRAY:
				x = in.readInt();
				color = in.readInt();
				rank = in.readInt();
				view.update(Board.IN_TRAY, new Piece(x, color, Rank.values()[rank]));
				return true;
			case GAMEOVER:
				x = in.readInt();
				view.gameOver(x);
				return true;
			case DISC:
				bye();
				return true;
			default:
				return false;
			}
		}
		catch (IOException e)
		{
			bye();
			return true; // stop message propagation
		}
	}
	
	public void attach()
	{
		conn.register(this);
	}
	
	public void detach()
	{
		conn.unregister(this);
	}
	
	public void bye()
	{
		view.gameOver(-1);
	}
	
	public void newGame()
	{
		attach();
		play();
	}

	public void move(Move m)
	{
		conn.startSending();
		try
		{
			conn.writeInt(Message.MOVE.ordinal());
			conn.writeInt(m.getFromX());
			conn.writeInt(m.getFromY());
			conn.writeInt(m.getToX());
			conn.writeInt(m.getToY());
			conn.writeInt(m.getPiece().getRank().ordinal());
		}
		finally
		{
			conn.stopSending();
		}
	}

	public void play()
	{
		conn.startSending();
		try
		{
			conn.writeInt(Message.PLAY.ordinal());
		}
		finally
		{
			conn.stopSending();
		}
	}

}

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

import java.io.DataInputStream;
import java.io.IOException;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.server.shared.Message;
import com.cjmalloy.stratego.server.shared.MessageHandler;


public class Game
{
	private class Mailbox implements MessageHandler
	{
		private int player;
		private Game game;

		public Mailbox(Game g, int p)
		{
			player = p;
			game = g;
		}
		
		public void disconnect()
		{
			game.player[player].unregister(this);
		}

		public void bye()
		{
			game.player[(player+1)%2].startSending();
			try
			{
				game.player[(player+1)%2].writeInt(Message.DISC.ordinal());
			}
			finally
			{
				game.player[(player+1)%2].stopSending();
			}
			gameServer.gameOver(game);
		}

		public boolean read(int type, DataInputStream in)
		{
			try
			{
				switch (Message.values()[type])
				{
				case MOVE:
					int x1, x2, y1, y2, rank;
					Spot from, to;
					Piece p;
					x1 = in.readInt();
					y1 = in.readInt();
					x2 = in.readInt();
					y2 = in.readInt();
					rank = in.readInt();
					if (x1 < 0)
					{
						from = Board.IN_TRAY;
						// p = new Piece(0, 0, Rank.values()[rank]);
						p = new Piece(0, Rank.values()[rank]);
					}
					else
					{
						from = new Spot(x1, y1);
						p = engine.getBoardPiece(x1, y1);
					}
					if (x2 < 0)
						to = Board.IN_TRAY;
					else
						to = new Spot(x2, y2);

					game.engine.requestMove(new Move(p, from, to), player);
					break;
				case PLAY:
					game.engine.play(player);
					break;
				default:
					return false;
				}
				game.update();
				return true;
			}
			catch (IOException e)
			{
				return true;
			}
		}
	}

	private ServerEngine engine = null;
	public ServerConnection player[] = new ServerConnection[2];
	private Mailbox mailboxes[] = new Mailbox[2];
	private GameServer gameServer = null;

	public Game(GameServer gs, ServerConnection p1, ServerConnection p2)
	{
		gameServer = gs;
		
		player[0] = p1;
		player[1] = p2;
		mailboxes[0] = new Mailbox(this, 0);
		mailboxes[1] = new Mailbox(this, 1);
		player[0].register(mailboxes[0]);
		player[1].register(mailboxes[1]);
	}

	public void start()
	{
		for (int i=0;i<2;i++)
		{
			player[i].startSending();
			try
			{
				player[i].writeInt(Message.SETUP.ordinal());
				player[i].writeInt(i);
			}
			finally
			{
				player[i].stopSending();
			}
		}
		
		engine = new ServerEngine(this);
		engine.newGame();
		update();
	}

	public void gameOver(int c)
	{
		update();
		for (int i=0;i<2;i++)
		{
			player[i].writeInt(Message.GAMEOVER.ordinal());
			player[i].writeInt(c);
		}
	}
	
	public void disc()
	{
		for (int i=0;i<2;i++)
			mailboxes[i].disconnect();

		gameServer.gameOver(this);
	}

	public void update()
	{
		for (int i=0;i<2;i++)
		{
			player[i].startSending();
			try
			{
				for (int j=0;j<10;j++)
				for (int k=0;k<10;k++)
				{
					player[i].writeInt(Message.GRID.ordinal());

					player[i].writeInt(j);
					player[i].writeInt(k);
					if (engine.getBoardPiece(j, k) == null || engine.getBoardPiece(j, k).getColor() < 0)
					{
						player[i].writeInt(-1);
						player[i].writeInt(Rank.NIL.ordinal());
					}
					else
					{
						if (engine.getBoardPiece(j, k).isShown())
						{
							player[i].writeInt(engine.getBoardPiece(j, k).getColor()+2);
							player[i].writeInt(engine.getBoardPiece(j, k).getRank().ordinal());
						}
						else
						{
							player[i].writeInt(engine.getBoardPiece(j, k).getColor());
							if (engine.getBoardPiece(j, k).getColor() == i)
								player[i].writeInt(engine.getBoardPiece(j, k).getRank().ordinal());
							else
								player[i].writeInt(Rank.UNKNOWN.ordinal());
						}
					}
				}

				int p = 0,
					q = 0;
				for (int j=0;j<engine.getTraySize();j++)
				{
					player[i].writeInt(Message.TRAY.ordinal());

					if (engine.getTrayPiece(j).getColor() == 0)
					{
						player[i].writeInt(p);
						p++;
					}
					else
					{
						player[i].writeInt(q);
						q++;
					}
					if (engine.getTrayPiece(j).isShown())
					{
						player[i].writeInt(engine.getTrayPiece(j).getColor()+2);
						player[i].writeInt(engine.getTrayPiece(j).getRank().ordinal());
					}
					else
					{
						player[i].writeInt(engine.getTrayPiece(j).getColor());
						if (engine.getTrayPiece(j).getColor() == i)
							player[i].writeInt(engine.getTrayPiece(j).getRank().ordinal());
						else
							player[i].writeInt(Rank.UNKNOWN.ordinal());
					}
				}

				for (;p<40;p++)
				{
					player[i].writeInt(Message.TRAY.ordinal());
					player[i].writeInt(p);
					player[i].writeInt(0);
					player[i].writeInt(Rank.NIL.ordinal());
				}

				for (;q<40;q++)
				{
					player[i].writeInt(Message.TRAY.ordinal());
					player[i].writeInt(q);
					player[i].writeInt(1);
					player[i].writeInt(Rank.NIL.ordinal());
				}
			}
			finally
			{
				player[i].stopSending();
			}

		}
	}
	
	public String status()
	{
		return "Player 1: " + player[0].status() + "\nPlayer 2: " + player[1].status();
	}
}

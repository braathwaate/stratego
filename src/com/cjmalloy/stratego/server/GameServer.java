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

import java.util.ArrayList;

import com.cjmalloy.stratego.server.shared.Message;

public class GameServer extends Server
{
	private ArrayList<Game> games = new ArrayList<Game>();
	private boolean closing = false;
	private GameServer me = null;
	private boolean userListChanged = false;
	private Thread sendUserList = new Thread()
	{
		public void run()
		{
			while (!closing)
			{
				try
				{
					sleep(2000);
					synchronized (sendUserList)
					{
						if (!userListChanged)  wait();
					}
				}
				catch (InterruptedException e) {}
				userListChanged = false;

				String s = "u " + (connections.size() + 2*games.size());
				for (ServerConnection c: connections)
					s += " " + Message.IDLE_PREFIX + c.alias;

				for (Game g: games)
					s += " " + Message.GAME_PREFIX + g.player[0].alias
					   + " " + Message.GAME_PREFIX + g.player[1].alias;

				synchronized (me)
				{
					for (ServerConnection c: connections)
					{
						c.startSending();
						try
						{
							c.cmd(s);
						}
						finally
						{
							c.stopSending();
						}
					}
	
					for (int i=0;i<2;i++)
					for (Game g: games)
					{
						g.player[i].startSending();
						try
						{
							g.player[i].cmd(s);
						}
						finally
						{
							g.player[i].stopSending();
						}
					}
				}
			}
		}
	};
	
	public GameServer(ServerController sc, int p)
	{
		super(sc, p);
		
		me = this;
		closing = false;
		sendUserList.start();
	}
	
	public synchronized boolean newGame(ServerConnection c, ArrayList<String> users)
	{
		for (int i=0;i<connections.size();i++)
		{
			if (!connections.get(i).ignore &&
				 connections.get(i) != c)
			{
				if (users == null)
				{
					startGame(c, connections.get(i));
					return true;
				}
				else
				{
					for (String s: users)
						if (connections.get(i).alias.equals(s))
						{
							startGame(c, connections.get(i));
							return true;
						}
				}
			}
		}
		return false;
	}
	
	private synchronized void startGame(ServerConnection a, ServerConnection b)
	{
		Game g = new Game(this, a, b);
		games.add(g);
		connections.remove(a);
		connections.remove(b);
		g.start();
		sendUserList();
	}

	public synchronized void stopGame(ServerConnection u)
	{
		for (Game g: games)
		{
			if (g.player[0] == u || g.player[1] == u)
			{
				stopGame(g);
				return;
			}
		}
	}
	public synchronized void stopGame(Game g)
	{
		for (int i=0;i<2;i++)
		{
			if (g.player[i].connected())
			{
				g.player[i].startSending();
				try
				{
					g.player[i].writeInt(Message.DISC.ordinal());
				}
				finally
				{
					g.player[i].stopSending();
				}
			}
		}
		g.disc();
	}
	
	public synchronized void gameOver(Game g)
	{
		games.remove(g);
		if (g.player[0].connected())
		{
			connections.add(g.player[0]);
		}
		if (g.player[1].connected())
		{
			connections.add(g.player[1]);
		}
		
		if (closing && games.size() <= 1)
		{
			close();
			System.exit(0);
		}
		
		sendUserList();
	}
	
	public void closeWhenEmpty(boolean b)
	{
		closing = b;

		if (closing && (games.size() <= 1))
		{
			close();
			System.exit(0);
		}
	}
	
	public boolean isClosingWhenEmpty()
	{
		return closing;
	}
	
	protected synchronized void add(ServerConnection sc)
	{
		super.add(sc);
		sendUserList();
	}
	
	public synchronized boolean addAlias(ServerConnection conn, String s)
	{
		for (ServerConnection c: connections)
			if (c.alias.equals(s))
				return false;

		for (Game g: games)
		{
			if (g.player[0].alias.equals(s))
				return false;
			if (g.player[1].alias.equals(s))
				return false;
		}
		
		conn.alias = s;
		sendUserList();
		return true;
	}
	
	public synchronized void sendUserList()
	{
		userListChanged = true;
		synchronized (sendUserList)
		{
			sendUserList.notify();
		}
	}

	public synchronized boolean isIdle(ServerConnection u)
	{
		for (ServerConnection c: connections)
		{
			if (c == u)
				return true;
		}
		return false;
	}
	
	public synchronized boolean isInGame(ServerConnection u)
	{
		for (Game g: games)
		{
			if (g.player[0] == u)
				return true;
			if (g.player[1] == u)
				return true;
		}
		return false;
	}
	
	public synchronized boolean isIgnoring(String user)
	{
		if (user == null)
		{
			for (ServerConnection c: connections)
			{
				if (!c.ignore)
					return false;
			}
			return true;
		}
		
		for (ServerConnection c: connections)
		{
			if (c.alias.equals(user))
			{
				if (c.ignore)
					return true;
				return false;
			}
		}
		return true;
	}
	
	public synchronized void setPort(int p)
	{
		if (!isIgnoring())
			serverIDs.remove(new Integer(port));
		
		if (serverIDs.contains(new Integer(p)))
		{
			ignore(true);
		}
		else
		{
			serverIDs.add(new Integer(p));
		}
		port = p;
	}
	

	public void bye()
	{
		super.bye();
		sendUserList();
	}
	
	public synchronized String games()
	{
		String ret = "";

		for (int i=0;i<games.size();i++)
		{
			ret += "Game " + (i+1) + "\n";
			ret += games.get(i).status() + "\n\n";
		}

		return ret;
	}
	
	public synchronized String idle()
	{
		return connections.size() + " idle players";
	}
	
	public String status()
	{
		return games.size() + " games active";
	}
}

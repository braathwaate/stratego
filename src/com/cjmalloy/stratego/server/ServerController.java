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

public class ServerController
{
	private ArrayList<Controller> controllers = new ArrayList<Controller>();
	private ArrayList<RemoteControllerServer> remotes = new ArrayList<RemoteControllerServer>();
	private Controller localController = null;
	private GameServer gs = null;
	private int port;
	private Thread logThread = new Thread()
	{
		public void run()
		{
			while (true)
			{
				try {
					//every hour
					sleep(3600000);
				} catch (InterruptedException e) {}
				try
				{
					Log.println("*************");
					Log.println(getGameServer().status());
					Log.println(getGameServer().idle());
					Log.println("Port " + getGameServer().getPort());
					if (getGameServer().isIgnoring())
						Log.println("Ignoring new connections.");
					else
						Log.println("Accepting new connections.");
					if (getGameServer().isClosingWhenEmpty())
						Log.println("Will close when no games are being played.");
					Log.println("*************");
					Log.println(getGameServer().status());
					Log.println(getGameServer().games());
					Log.println("*************");
					Log.println(getRemotes());
				}
				catch (Exception e) {}
			}
		}
	};
	
	public ServerController(int p)
	{
		port = p;
		logThread.start();
	}
	
	public GameServer getGameServer()
	{
		return gs;
	}
	
	public void setGameServer(GameServer srv)
	{
		if (gs != null)
			gs.close();
		gs = srv;
	}
	
	public void start()
	{
		gs = new GameServer(this, port);
		localController = new Controller(this, null, 0);
		controllers.add(localController);
	}
	
	public void newGame(ServerConnection c, ArrayList<String> users)
	{
		gs.stopGame(c);
		if (!gs.newGame(c, users))
		{
			c.startSending();
			try
			{
				if (users.size() == 1)
					c.println(users.get(0) + " is unavailable.");
				else
					c.println("All users are unavailable.");
			}
			finally
			{
				c.stopSending();
			}
		}
	}
	
	public int isIgnoring(ArrayList<String> users)
	{
		synchronized (gs)
		{
			if (users == null)
			{
				if (gs.isIgnoring(null))
					return -1;
				return 0;
			}
			
			for (int i=0;i<users.size();i++)
			{
				if (!gs.isIgnoring(users.get(i)))
					return i;
			}
		}
		
		return -1;
	}
	
	public void add(ServerConnection c, int privilege)
	{
		synchronized (controllers)
		{
			Controller con = new Controller(this, c, privilege);
			controllers.add(con);
		}
	}	
	
	public void remove(Controller r)
	{
		synchronized (controllers)
		{
			controllers.remove(r);
		}
	}
	
	public void add(RemoteControllerServer rcs)
	{
		synchronized (remotes)
		{
			remotes.add(rcs);
		}
	}
	
	public void disconnectAll()
	{
		print("Disconnecting all connections...");
		synchronized (remotes)
		{
			while (remotes.size() > 0)
			{
				remotes.get(0).close();
				remotes.remove(0);
			}
		}
	}
	
	public void exit()
	{
		gs.close();
		disconnectAll();
		System.exit(0);
	}
	
	public String getRemotes()
	{
		String s = "";
		synchronized (remotes)
		{
			for (RemoteControllerServer rcs : remotes)
			{
				s += rcs.status();
			}
		}
		if (s.equals(""))
		{
			s = "No remote controller servers.";
		}
		return s;
	}

	public void println(String e)
	{
		print(e+"\n");
		Log.println(e);
	}
	
	public void print(String e)
	{
		synchronized (controllers)
		{
			for (Controller c : controllers)
				c.print(e);
		}
		Log.print(e);
	}
}

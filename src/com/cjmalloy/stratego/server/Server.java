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
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.cjmalloy.stratego.server.shared.MessageHandler;


public abstract class Server implements MessageHandler
{
	protected ArrayList<ServerConnection> connections = new ArrayList<ServerConnection>();
	protected static ArrayList<Integer> serverIDs = new ArrayList<Integer>();
	private Semaphore ignore = new Semaphore(1);
	protected ServerController owner = null;
	protected ServerConnection connecting = null;
	protected boolean running;
	protected String password = "";
	protected int privilege = 5;
	protected int port;
	private ServerSocket srv = null;
	
	protected Thread main = new Thread()
	{
		public void run()
		{
			while(running)
			{
				try
				{
					ignore.acquireUninterruptibly();
					if (!running) break;
					ignore.release();
					
					if (srv == null)
						srv = new ServerSocket(port);
					
					if (connecting == null)
					{
						connecting = new ServerConnection();
					}
					if (connecting.connect(srv, password))
					{
						add(connecting);
						owner.add(connecting, privilege);
						connecting = null;
					}
				}
				catch (IOException e)
				{
					if (running && !isIgnoring())
					{
						owner.println("Port unavailable, please select a different port.");
						owner.println("Ignoring new connections.");
						ignore(true);
					}
				}
			}
		}
	};
	
	protected Server(ServerController sc, int p)
	{
		port = p;
		owner = sc;
		running = true;
		serverIDs.add(new Integer(port));
		main.start();
	}

	public synchronized void ignore(boolean b)
	{
		if (b == (0 == ignore.availablePermits()))
			return;
		
		if (0 == ignore.availablePermits())
		{
			ignore.release();
			serverIDs.add(new Integer(port));
		}
		else
		{
			ignore.acquireUninterruptibly();
			
			if (srv != null)
			{
				try {
					srv.close();
				} catch (IOException e) {}
				srv = null;
			}
			if (connecting != null)
				connecting.disconnect();
			
			serverIDs.remove(new Integer(port));
		}
	}
	
	public boolean isIgnoring()
	{
		return 0 == ignore.availablePermits();
	}
	
	public int getPort()
	{
		return port;
	}
	
	public void close()
	{
		running = false;
		if (connecting != null)
			connecting.disconnect();
		
		if (isIgnoring())
			ignore.release();
		else
			serverIDs.remove(new Integer(port));
			
		while (connections.size() > 0)
		{
			connections.get(0).unregister(this);
			connections.get(0).bye();
			connections.remove(0);
		}
		
		try
		{
			main.join();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
	
	protected synchronized void add(ServerConnection sc)
	{
		sc.register(this);
		connections.add(sc);
	}
	
	public void bye()
	{
		for (int i=0;i<connections.size();)
		{
			if (connections.get(i).connected())
			{
				i++;
			}
			else
			{
				connections.remove(i);
			}
		}
	}

	public boolean read(int type, DataInputStream in)
	{
		return false; //ignore all messages
	}
}

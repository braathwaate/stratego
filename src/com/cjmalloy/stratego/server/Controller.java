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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import com.cjmalloy.stratego.server.shared.Connection;
import com.cjmalloy.stratego.server.shared.Message;
import com.cjmalloy.stratego.server.shared.MessageHandler;


public class Controller implements MessageHandler
{
	private static ReentrantLock serverLock = new ReentrantLock();
	private ServerController owner = null;
	private ServerConnection conn = null;
	private int privilege;

	public Controller(ServerController sc, ServerConnection c, int p)
	{
		owner = sc;
		conn = c;
		privilege = p;

		if (conn != null)
			conn.register(this);
		else
		{
			conn = new LocalConnection();
			new Thread()
			{
				public void run()
				{
					Scanner kbd = new Scanner(System.in);
					while (true)
					{
						parse(kbd.nextLine());
					}
				}
			}.start();
		}
	}

	public boolean read(int type, DataInputStream in)
	{
		switch(Message.values()[type])
		{
		case CMD:
			try
			{
				int len = in.readInt();
				parse(Connection.decode(len, in));
				return true;
			}
			catch (IOException e)
			{
				return true;
			}
		case MSG:
			try
			{
				int len = in.readInt();
				owner.print(conn.alias + ": " + Connection.decode(len, in));
				return true;
			}
			catch (IOException e)
			{
				return true;
			}
		default:
			return false;
		}
	}

	public void parse(String in)
	{
		Scanner kbd = new Scanner(in);

		try
		{
			while (kbd.hasNext())
			{
				String input = kbd.next();
				switch (input.charAt(0))
				{
				case 'q': //quit
				case 'Q':
					if (privilege > 0) break;
					owner.exit();
					return;
				case 'u': //user name
				case 'U':
					if (input.endsWith("?"))
					{
						println("Username is " + conn.alias);
						break;
					}
					
					Scanner cleaner = new Scanner(kbd.next());
					cleaner.useDelimiter("[\",.`':;/\\\\]"); // no ",.`':;/\ symbols allowed
					String user = cleaner.next();
					if (user.equals(""))
						break;
					if (owner.getGameServer().addAlias(conn, user))
					{
						println("Username is now " + conn.alias);
					}
					else
					{
						println("Username " + user + " is in use.");
					}
					break;
				case 'n': //new game
				case 'N':
					int n = kbd.nextInt();
					
					if (n == 0)
					{
						if (input.endsWith("?"))
						{
							if (owner.isIgnoring(null) < 0)
							{
								println("Nobody is available for a game.");
							}
							else
							{
								println("Some players are waiting for a game.");
							}
						}
						else
						{
							owner.newGame(conn, null);
						}
					}
					else
					{
						
						ArrayList<String> users = new ArrayList<String>();
						for (int i=0;i<n;i++)
						{
							//TODO check for prefix code instead of just ignoring it
							users.add(kbd.next().substring(1));
						}
						
						if (input.endsWith("?"))
						{
							int p;
							if ((p = owner.isIgnoring(users)) < 0)
							{
								println("Nobody is available for a game.");
							}
							else
							{
								println(users.get(p) + " is waiting for a game.");
							}
						}
						else
						{
							if (n != 1 || !users.get(0).equals(conn.alias))
								owner.newGame(conn, users);
						}
					}
					break;
				case 's': //status
				case 'S':
					conn.startSending();
					try
					{
						conn.println(owner.getGameServer().status());
						conn.println(owner.getGameServer().idle());
						conn.println("Port " + owner.getGameServer().getPort());
						conn.println("IP   " + getIP());
						if (owner.getGameServer().isIgnoring())
							conn.println("Ignoring new connections.");
						else
							conn.println("Accepting new connections.");
						if (owner.getGameServer().isClosingWhenEmpty())
							conn.println("Will close when no games are being played.");
					}
					finally
					{
						conn.stopSending();
					}
					break;
				case 'g': //list games
				case 'G':
					println(owner.getGameServer().status());
					println(owner.getGameServer().games());
					break;
				case 'c': // list remote controllers
				case 'C':
					if (privilege > 1) break;
					
					println(owner.getRemotes());
					break;
				case 'r': //restart
				case 'R':
					if (privilege > 1) break;

//					print("Are you sure? This will disconnect all games. (y/n) ");
//					String yn = kbd.next();
//					if (yn.length() != 0 && yn.charAt(0) == 'y')
					{
						int port = owner.getGameServer().getPort();
						if (!owner.getGameServer().isIgnoring())
							owner.getGameServer().ignore(true);
						serverLock.lock();
						try
						{
							owner.setGameServer(new GameServer(owner, port));
						}
						finally
						{
							serverLock.unlock();
						}
						println("Restarted.");
					}
					break;
				case 'p': //port #####
				case 'P':
					if (privilege > 1) break;
	
					int port = readPort(kbd);
					owner.getGameServer().setPort(port);
					println("Port set: " + port);
					break;
				case 'i': //ignore (toggle)
				case 'I':
					if (privilege > 1)
					{
						conn.ignore = !conn.ignore;
					}
					else
					{
						owner.getGameServer().ignore(!owner.getGameServer().isIgnoring());
						if (owner.getGameServer().isIgnoring())
							println("Ignore on");
						else
							println("Ignore off");
						break;
					}
				case 'x': //close when no games are being played (toggle)
				case 'X':
					if (privilege > 1)
					{
						owner.getGameServer().stopGame(conn);
					}
					else
					{
						owner.getGameServer().closeWhenEmpty(!owner.getGameServer().isClosingWhenEmpty());
						if (owner.getGameServer().isClosingWhenEmpty())
							println("ClosingWhenEmpty on");
						else
							println("ClosingWhenEmpty off");
						break;
					}
				case 'a': //activate remote input
				case 'A':
					if (privilege > 1)
					{
						//TODO: let someone login in and gain privileges
					}
	
					int priv;
					String p = kbd.next();
					int remotePort = readPort(kbd);
					String password = kbd.next();
					switch (p.charAt(0))
					{
					case 'a':
					case 'A':
					case '1':
						priv = 1;
						p = "Admin";
						break;
					case 'm':
					case 'M':
					case '2':
						priv = 2;
						p = "Moderator";
						break;
					case '3':
						priv = 3;
						p = "level 3";
						break;
					case '4':
						priv = 4;
						p = "level 4";
						break;
					case 'g':
					case 'G':
					case '5':
					default:
						priv = 5;
						p = "Guest";
						break;
					}
					RemoteControllerServer rcs = RemoteControllerServer.getServer(owner, remotePort, password, priv);
					if (rcs != null)
					{
						owner.print("Adding new " + p 
									+ " remote controller on port " + remotePort 
									+ "\nPassword: " + password + "\n");
						owner.add(rcs);
					}
					else
					{
						println("Could not create server, port in use.");
					}
					break;
				case 'd': //disconnects remote connection(s)
				case 'D':
					if (privilege > 0) break;
					owner.disconnectAll();
					break;
				case 'l': //logout (remote only)
				case 'L':
					if (privilege > 0)
					{
						println("Logging out...");
						logout();
						return;
					}
					break;
				case 'h':
				case 'H':
					conn.startSending();
					try
					{
						//TODO: update this
						conn.println("h - help");
						conn.println("n - new game");
						conn.println("\tusage: n #users user1 user2 ... user#" +
								"\n\texample: n 2 mike kim (play with either mike or kim)" +
								"\n\texample: n 0 (play with anyone available)");
						conn.println("n? - anybody available for a new game?");
						conn.println("\tusage: n? #users user1 user2 ... user#" +
								"\n\texample: n? 2 mike kim (is either mike or kim available?)" +
								"\n\texample: n? 0 (is anyone available?)");
						conn.println("u - change username");
						conn.println("\tusage: u username");
						conn.println("u? - what's my username?");
						conn.println("s - status");
						conn.println("g - list games");
						conn.println("c - (admin) list controllers");
						conn.println("r - (admin) restart (kills all games)");
						conn.println("p - (admin) change port");
						conn.println("\tusage: p port" +
								"\n\texample: p 12345");
						conn.println("i - (admin) (toggle) ignore new connections (kills games that haven't started)");
						conn.println("x - (admin) (toggle) close when there are no games being played");
						conn.println("a - (admin) activate remote input");
						conn.println("\tusage: a [guest|admin|mod] port password" +
								"\n\texample: a guest 12345 yxQ!7%a");
						conn.println("d - (local only) deactivate all remote input");
						conn.println("q - (local only) quit");
						conn.println("l - (remote only) logout");
						conn.println("See documentation for more details.");
					}
					finally
					{
						conn.stopSending();
					}
					break;
				default:
					conn.startSending();
					try
					{
						conn.println("Type help for instructions.");
					}
					finally
					{
						conn.stopSending();
					}
					break;
				}
			}
		}
		catch (NoSuchElementException e)
		{					
			conn.startSending();
			try
			{
				conn.println("Parse error.\nType help for instructions.");
			}
			finally
			{
				conn.stopSending();
			}
		}
	}

	public void bye()
	{
		owner.remove(this);
	}

	public void logout()
	{
		owner.remove(this);
		conn.unregister(this);
		conn.bye();
	}

	public void println(String s)
	{
		print(s + "\n");
	}
	
	public void print(String s)
	{
		conn.startSending();
		try
		{
			conn.print(s);
		}
		finally
		{
			conn.stopSending();
		}
	}

	public int getPrivilege()
	{
		return privilege;
	}

	public String getIP()
	{
		try
		{
			return ""+InetAddress.getLocalHost();
		}
		catch (UnknownHostException e)
		{
			return "unknown";
		}
	}

	private int readPort(Scanner kbd)
	{
		int p;
		while (true)
		{
			try
			{
				p = kbd.nextInt();
			}
			catch (InputMismatchException e)
			{
				kbd.nextLine();
				conn.print("Enter port: ");
				continue;
			}
			break;
		}

		return p;
	}
}
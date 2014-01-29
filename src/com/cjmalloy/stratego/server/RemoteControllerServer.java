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


public class RemoteControllerServer extends Server
{
	public static RemoteControllerServer getServer(ServerController sc, int port, String pass, int priv)
	{
		if (serverIDs.contains(new Integer(port)))
		{
			return null;
		}
		
		return new RemoteControllerServer(sc, port, pass, priv);
	}
	
	private RemoteControllerServer(ServerController sc, int port, String pass, int priv)	
	{
		super(sc, port);
		password = pass;
		privilege = priv;
	}

	public String getPass()
	{
		return password;
	}
	
	public void setPass(String p)
	{
		password = p;
	}
	
	public String status()
	{
		String s =  "Level " + privilege
			+ " connections accepted on port " + port
			+ "\n" + connections.size() + " connections:\n";
		for (ServerConnection sc : connections)
			s += sc.status() + "\n";
		
		return s;
	}
}

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

import java.io.DataOutputStream;

public class LocalConnection extends ServerConnection
{
	public LocalConnection()
	{
		out = new DataOutputStream(System.out);
	}
	
	public boolean connected()
	{
		return  true;
	}
	
	public void startSending(){}
	
	public void stopSending(){}
	
	public synchronized void writeInt(int i)
	{
		print(""+i);
	}

	public synchronized void println(String s)
	{
		System.out.println(s);
	}
	
	public synchronized void print(String s)
	{
		System.out.print(s);
	}

	public boolean connect(int port, String password)
	{
		return false;
	}
	
	public void disconnect(){}
	public void bye(){}
	
	public String status()
	{
		return "Local Connection";
	}
}

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
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;

import com.cjmalloy.stratego.server.shared.Connection;
import com.cjmalloy.stratego.server.shared.Hash;
import com.cjmalloy.stratego.server.shared.Message;

public class ServerConnection extends Connection
{
	public String alias;
	public boolean ignore = false;;
	private ServerSocket srv = null;
	private static final long m = System.currentTimeMillis();
	
	public synchronized boolean connect(ServerSocket ss, String password) throws IOException
	{
		if (sock != null)
			disconnect();
		try
		{
			srv = ss;
			sock = srv.accept();
		}
		finally
		{
			srv = null;
		}
		
		try
		{
			out = new DataOutputStream(sock.getOutputStream());
			in = new DataInputStream(sock.getInputStream());
			//get their version number
			if (in.readInt() != Message.PROTOCOL_MAJOR_VERSION ||
				in.readInt()  < Message.PROTOCOL_MINOR_VERSION)
			{
				out.writeInt(Message.UPDATE.ordinal());
				startSending();
				try
				{
					println(Message.DOWNLOAD_URL);
				}
				finally
				{
					stopSending();
				}	
				disconnect();
				return false;
			}
			else
			{
				//write anything except UPDATE
				out.writeInt(Message.UPDATE.ordinal()+1);
			}	

			if (!password.equals(""))
			{
				String salt = Hash.Sha1((""+System.currentTimeMillis())+m);
				out.write(encode(salt));
				out.flush();
				String pass = Connection.decode(40, in);
				if (!Hash.Sha1(salt+password).equals(pass))
				{
					startSending();
					try
					{
						println("Invalid password");
					}
					finally
					{
						stopSending();
					}	
					disconnect();
					return false;
				}
			}
		}
		catch (IOException e)
		{
			//authentication error
			return false;
		}

		alias = sock.getInetAddress().getHostAddress()
				+ ":" + sock.getPort();
		startReading();
		startSending();
		try
		{
			print(Message.WELCOME_MSG);
		}
		finally
		{
			stopSending();
		}
		return true;
	}
	
	protected void read()
	{
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {}

		super.read();
	}
	
	protected boolean recieve(int type, DataInputStream packet)
	{
		return sendMessage(type, packet);
	}
	
	public void bye()
	{
		if (!closing.tryAcquire()) return;
		
		super.bye();
		clients = null;
	}
	
	protected void disconnect()
	{
		try
		{
			if (srv != null)
				srv.close();
		} catch (IOException e) {}
		finally
		{
			srv = null;
		}
		
		super.disconnect();
	}
}

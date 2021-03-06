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

package com.cjmalloy.stratego.server.remote;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Scanner;

import com.cjmalloy.stratego.server.shared.ClientConnection;
import com.cjmalloy.stratego.server.shared.Connection;
import com.cjmalloy.stratego.server.shared.Message;
import com.cjmalloy.stratego.server.shared.MessageHandler;


public class RemoteControllerClient implements MessageHandler
{
	ClientConnection conn = ClientConnection.getInstance();
	
	public RemoteControllerClient()
	{
		conn.register(this);
	}
	
	public void start()
	{
		new Thread()
		{
			public void run()
			{
				Scanner kbd = new Scanner(System.in);
				String input;
				while (conn.connected())
				{
					input = kbd.nextLine();
					conn.startSending();
					try
					{
						conn.cmd(input);
					}
					finally
					{
						conn.stopSending();
					}
				}
			}
		}.start();
	}

	public void bye()
	{
		System.out.println("Disconnected.");
		System.exit(0);
	}

	public boolean read(int type, DataInputStream in)
	{
		if (type != Message.MSG.ordinal()) return false;
		
		try
		{
			int len = in.readInt();
			System.out.print(Connection.decode(len, in));
			return true;
		}
		catch (IOException e)
		{
			return true;
		}
	}
}

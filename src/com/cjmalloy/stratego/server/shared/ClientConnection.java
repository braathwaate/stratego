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

package com.cjmalloy.stratego.server.shared;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

import javax.swing.JOptionPane;


public class ClientConnection extends Connection
{
	private static ClientConnection me = null;
	
	private ClientConnection() {}
	
	public static ClientConnection getInstance()
	{
		if (me == null)
			me = new ClientConnection();
		return me;
	}
	
	public synchronized boolean connect(Socket s, String password)
	{
		if (closing.availablePermits() == 0)
			closing.release();
		if (sock != null)
			bye();
		
		sock = s;

		if (sock == null) return false;
		
		try
		{
			in = new DataInputStream(sock.getInputStream());
			out = new DataOutputStream(sock.getOutputStream());

			out.writeInt(Message.PROTOCOL_MAJOR_VERSION);
			out.writeInt(Message.PROTOCOL_MINOR_VERSION);
			out.flush();
			
			int update = in.readInt();
			if (update == Message.UPDATE.ordinal())
			{
				in.readInt();in.readInt(); //ignore packet length and type
				int len = in.readInt();
				String downloadURL = decode(len, in);
				JOptionPane.showMessageDialog(null, "Your version of Stratego is incompatable with this server.\n" +
						"Please download the latest version at " + downloadURL, "Stratego", JOptionPane.ERROR_MESSAGE);
				return false;
			}

			if (password!="")
			{
				String salt = Connection.decode(40, in);
				out.write(encode(Hash.Sha1(salt+password)));
				out.flush();
			}
			
			startReading();
			return true;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
	}
	
	protected boolean recieve(int type, DataInputStream packet)
	{
		switch (Message.values()[type])
		{
		case UPDATE:
			String downloadURL = new Scanner(packet).next();
			JOptionPane.showMessageDialog(null, "Your version of Stratego is incompatable with this server.\n" +
					"Please download the latest version at " + downloadURL, "Stratego", JOptionPane.ERROR_MESSAGE);
			bye();
			return true;
		default:
			return sendMessage(type, packet);
		}
	}
}

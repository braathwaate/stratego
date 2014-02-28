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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;


public abstract class Connection
{
	public static final String CHAR_ENCODING = "ASCII";
	
	protected Socket sock = null;
	protected DataInputStream in = null;
	protected DataOutputStream out = null;
	protected ArrayList<MessageHandler> clients = new ArrayList<MessageHandler>();
	private Semaphore sending = new Semaphore(1);
	private DataOutputStream buffer = null;
	private ByteArrayOutputStream byteBuffer = null;
	protected Semaphore closing = new Semaphore(1);
	
	protected Connection()
	{
		byteBuffer = new ByteArrayOutputStream();
		buffer = new DataOutputStream(byteBuffer);
	}
	
	public synchronized void register(MessageHandler mh)
	{
		clients.add(mh);
	}
	
	public synchronized void unregister(MessageHandler mh)
	{
		clients.remove(mh);
	}
	
	public boolean connected()
	{
		return sock != null;
	}
	
	protected void startReading()
	{
		new Thread() 
		{
			public void run()
			{
				while (sock != null && sock.isConnected())
				{
					read();
				}
			}
		}.start();
	}
	
	protected void read()
	{
		try
		{
			int len = in.readInt();
			byte[] bs = readBytes(len);
			DataInputStream packet = new DataInputStream( new ByteArrayInputStream(bs));
			try
			{
				while (packet.available() > 0)
				{
					int type = packet.readInt();
					switch (Message.values()[type])
					{
					case BYE:
						bye();
						return;
					default:
						if (!recieve(type, packet))
							return;
					}
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return;
			}
		}
		catch (IOException e)
		{
			if (clients != null)
				bye();
			return;
		}
	}
	
	// if the receiving end does not accept a message,
	// all messages following in the same packet will be ignored
	// therefore it is not good to group unrelated messages in the same packet
	public void startSending()
	{
		// start packet
		sending.acquireUninterruptibly();
		byteBuffer.reset();
	}
	
	public void stopSending()
	{
		// send packet
		if (sending.availablePermits() != 0)
		{
			System.out.println("sync error");
		}
		if (sock == null)
		{
			sending.release();
			return;
		}
		
		try
		{
			buffer.flush();
			writeBytes(byteBuffer.toByteArray());
			out.flush();
		}
		catch (IOException e)
		{
			System.err.println("stopSending error");
			bye();
		}
		sending.release();
	}
	
	public synchronized void writeInt(int i)
	{
		try
		{
			buffer.writeInt(i);
		}
		catch (IOException e)
		{
			bye();
		}
	}

	public void println(String s)
	{
		print(s+"\n");
	}
	
	public void print(String s)
	{
		try
		{
			buffer.writeInt(Message.MSG.ordinal());
			writeString(s);
		}
		catch (IOException e)
		{
			bye();
		}
	}
	
	public void cmd(String s)
	{
		try
		{
			buffer.writeInt(Message.CMD.ordinal());
			writeString(s);
		}
		catch (IOException e)
		{
			bye();
		}
	}
	
	public void writeString(String s)
	{
		try
		{
			byte[] bs = encode(s);
			buffer.writeInt(bs.length);
			buffer.write(bs);
		}
		catch (IOException e)
		{
			bye();
		}
	}
	
	private void writeBytes(byte[] bs)
	{
		try
		{
			out.writeInt(bs.length);
			out.write(bs);
		}
		catch (IOException e)
		{
			bye();
		}
	}
	
	private byte[] readBytes(int len)
	{
		byte[] bs = null;
		try
		{
			bs = new byte[len];
			for (int i=0;i<len;)
				i += in.read(bs, i, len-i);
		}
		catch (IOException e)
		{
			bye();
		}
		return bs;
	}
	
	protected boolean sendMessage(int type, DataInputStream packet)
	{
		for (MessageHandler mh : clients)
			if (mh.read(type, packet)) return true;
		return false;
	}
	
	public void bye()
	{
		if (!closing.tryAcquire()) return;
		
		disconnect();
		for (MessageHandler mh : clients)
			mh.bye();
	}
	
	protected void disconnect()
	{
		startSending();
		try
		{
			buffer.writeInt(Message.BYE.ordinal());
		}
		catch (IOException e) {}
		finally
		{
			stopSending();
			try
			{
				sock.close();
			} catch (Exception e) {}
			finally
			{
				sock = null;
			}
		}
	}
	
	public static byte[] encode(String s)
	{
		try
		{
			return s.getBytes(CHAR_ENCODING);
		}
		catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public static String decode(int len, DataInputStream input) throws IOException
	{
		byte[] bs = new byte[len];
		try
		{
		for (int i=0;i<len;)
			i += input.read(bs, i, len-i);
		}
		catch (IndexOutOfBoundsException e)
		{
			//bad input
		}
		return new String(bs, CHAR_ENCODING);
	}
	
	public String status()
	{
		if (sock != null && sock.isConnected())
			return sock.getInetAddress().getHostAddress();
		else
			return "Not connected";
	}
	

	protected abstract boolean recieve(int type, DataInputStream packet);
}

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

import java.security.MessageDigest;

//http://www.anyexample.com/programming/java/java_simple_class_to_compute_sha_1_hash.xml
public class Hash
{
	public static String Sha1(String s) 
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = new byte[40];
			md.update(s.getBytes("iso-8859-1"), 0, s.length());
			hash = md.digest();
			return toHex(hash);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	private static String toHex(byte[] data)
	{
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++)
		{
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do
			{
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while(two_halfs++ < 1);
		}
		return buf.toString();
	}
}


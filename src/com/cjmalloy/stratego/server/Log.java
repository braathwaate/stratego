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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class Log
{
	private PrintStream log = null;
	private static Log me = new Log("stratego_server04_log.txt");
	
	private Log(String fn)
	{
		try {
			log = new PrintStream(new File(fn));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void print(String s)
	{
		me.log.print(s);
		me.log.flush();
	}
	
	public static void println(String s)
	{
		me.log.println(s);
		me.log.flush();
	}
}

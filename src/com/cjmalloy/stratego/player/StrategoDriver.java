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

package com.cjmalloy.stratego.player;
import com.cjmalloy.stratego.Settings;



public class StrategoDriver
{
	//main entry point
	public static void main(String[] args)
	{
		boolean graphics = false;
		for(String arg:args)
		    if (arg.equals("-g"))
			graphics = true;
		    else if (arg.equals("-1"))
			Settings.twoSquares = false;
		    else if (arg.equals("-2"))
			Settings.twoSquares = true;
		    else if (arg.equals("-v"))
			Settings.debugLevel = 1;
		    else if (arg.equals("-vv"))
			Settings.debugLevel = 2;
		    else if (arg.substring(0,2).equals("-l"))
			Settings.aiLevel = Integer.parseInt(arg.substring(2));
		    else if (arg.equals("-t")) {
			new AITest(graphics);
			return;
		    }
		
		new WView();
	}

}

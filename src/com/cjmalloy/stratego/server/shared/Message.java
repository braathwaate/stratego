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

public enum Message
{
	UPDATE, // update must not move
	SETUP,
	MOVE,
	PLAY,
	GRID,
	TRAY,
	BYE,
	GAMEOVER,
	DISC,
	CMD,
	MSG;
	

	public static final char ADMIN_PREFIX = '.';
	public static final char IDLE_PREFIX = ':';
	public static final char GAME_PREFIX = ';';
	public static final char IGRNORE_PREFIX = ',';

	public static final int PROTOCOL_MAJOR_VERSION = 2;
	public static final int PROTOCOL_MINOR_VERSION = 0;
	public static final String DOWNLOAD_URL = "http://java-stratego.sourceforge.net";
	public static final String WELCOME_MSG  = "Welcome to Stratego @cs.smu.ca.\nType :h for help or :<command>.\n";
}

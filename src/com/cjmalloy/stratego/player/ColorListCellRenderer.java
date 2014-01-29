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

import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import com.cjmalloy.stratego.server.shared.Message;

public class ColorListCellRenderer extends DefaultListCellRenderer
{
	private static final long serialVersionUID = 1L;

	public ColorListCellRenderer()
    {
		super();
        setOpaque(true);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
    	super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    	
    	String user = value.toString();
    	setText(user.substring(1));
    	switch (user.charAt(0))
    	{
    	case Message.IDLE_PREFIX:
    		setForeground(Color.blue);
    		break;
    	case Message.GAME_PREFIX:
    		setForeground(Color.green);
    		break;
    	case Message.ADMIN_PREFIX:
    		setForeground(Color.red);
    		break;
    	}

        return this;
    }

}

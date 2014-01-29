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

import java.awt.BorderLayout;
import java.awt.Event;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.cjmalloy.stratego.server.shared.ClientConnection;
import com.cjmalloy.stratego.server.shared.Connection;
import com.cjmalloy.stratego.server.shared.Message;
import com.cjmalloy.stratego.server.shared.MessageHandler;

public class WLobby implements MessageHandler
{
	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,54"
	private JPanel jContentPane = null;
	private JMenuBar jJMenuBar = null;
	private JMenu fileMenu = null;
	private JMenuItem closeMenuItem = null;
	private JMenuItem logoutMenuItem = null;
	private JMenuItem newServerMenuItem;
	private JLabel lblTitle = null;
	private JScrollPane scUsers = null;
	private JTextField txtCmd = null;
	private JScrollPane scMain = null;
	private JTextArea txtMain = null;
	private JList lstUsers = null;
	private DefaultListModel listModel = null;
	private JSplitPane jSplitPane = null;
	private PopupMenu popUser = null;
	private MenuItem mStartGame = null;
	private KeyAdapter setFocus = new KeyAdapter()
	{
		public void keyPressed(KeyEvent e)
		{
			txtCmd.requestFocusInWindow();  //  @jve:decl-index=0:
			if (e.getKeyCode() > 31 && e.getKeyCode() < 127)
				txtCmd.setText(txtCmd.getText()+e.getKeyChar());
		}
	};
	
	private static WLobby me = null;  //  @jve:decl-index=0:
	
	private ClientConnection conn = ClientConnection.getInstance();  //  @jve:decl-index=0:
	
	private WLobby()
	{
		listModel = new DefaultListModel();
		conn.register(this);
	}
	
	public static WLobby getInstance()
	{
		if (me == null)
			me = new WLobby();
		return me;
	}
	
	public void newGame()
	{
		if (conn.connected())
		{
			conn.startSending();
			try
			{
				
			conn.writeInt(Message.CMD.ordinal());
			}
			finally
			{
				conn.stopSending();
			}
		}
		else
		{
			connect();
		}
	}
	
	public void connect()
	{
		getLblTitle().setText("Connecting...");
		if (conn.connect(WConnect.getServer(), ""))
		{
			getJFrame().setVisible(true);
			getLblTitle().setText("Connected");
		}
		else
		{
			WConnect.getInstance().getJFrame().setVisible(true);
		}
	}
	
	public void bye()
	{
		getLblTitle().setText("Not Connected");
		listModel.removeAllElements();
	}

	public boolean read(int type, DataInputStream in)
	{
		try
		{
			switch (Message.values()[type])
			{
			case MSG:
				print(Connection.decode(in.readInt(), in));
				return true;
			case CMD:
				Scanner cmd = new Scanner(Connection.decode(in.readInt(), in));
				while (cmd.hasNext())
				{
					switch (cmd.next().charAt(0))
					{
					case 'u':
						int len = cmd.nextInt();
						ArrayList<String> sorter = new ArrayList<String>();
						for (int i=0;i<len;i++)
						{
							sorter.add(cmd.next());
						}
						Collections.sort(sorter);
						int i;
						for (i=0;i<sorter.size();i++)
						{
							if (listModel.size() <= i)
							{
								listModel.addElement(sorter.get(i));
							}
							else if (listModel.get(i).equals(sorter.get(i)))
							{
								continue;
							}
							else if (sorter.get(i).compareTo(listModel.get(i).toString()) < 0)
							{
								listModel.add(i, sorter.get(i));
							}
							else
							{
								listModel.remove(i);
								i--;
							}
						}
						while (i<listModel.size())
						{
							listModel.remove(i);
						}
						break;
					}
				}
				return true;
			default:
				return false;
			}
		}
		catch (IOException e)
		{
			bye();
			return true; // stop message propagation
		}
	}
	
	private void print(String s)
	{
		//TODO: fix it so that you can scroll with out it harassing you
//		boolean scroll = true;
//
//		System.out.println(scMain.getVerticalScrollBar().getValue()
//				+  scMain.getVerticalScrollBar().getVisibleAmount());
//		System.out.println(scMain.getVerticalScrollBar().getMaximum());
//
//		if(scMain.getVerticalScrollBar().getValue()
//				+  scMain.getVerticalScrollBar().getVisibleAmount()
//				!= scMain.getVerticalScrollBar().getMaximum())
//			scroll = false;

		getTxtMain().append(s);
//		if (scroll)
		getTxtMain().setCaretPosition(getTxtMain().getDocument().getLength());
	}
	
	public JFrame getJFrame()
	{
		if (jFrame == null)
		{
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			jFrame.setJMenuBar(getJMenuBar());
			jFrame.setSize(425, 300);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("Lobby");
			jFrame.setIconImage(Skin.getInstance().noicon);
			
			mStartGame = new MenuItem();
			mStartGame.setLabel("Start Game");
			mStartGame.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					conn.startSending();
					try
					{
						conn.cmd("n 1 " + listModel.get(lstUsers.getSelectedIndex()));
					}
					finally
					{
						conn.stopSending();
					}
				}
			});
			popUser = new PopupMenu();
			popUser.add(mStartGame);
			jFrame.add(popUser);
		}
		return jFrame;
	}
	
	private JLabel getLblTitle()
	{
		if (lblTitle == null)
		{
			lblTitle = new JLabel();
			lblTitle.setText("Not Connected");
		}
		
		return lblTitle;
	}

	private JPanel getJContentPane()
	{
		if (jContentPane == null)
		{
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.add(getLblTitle(), BorderLayout.NORTH);
			jContentPane.add(getTxtCmd(), BorderLayout.SOUTH);
			jContentPane.add(getJSplitPane(), BorderLayout.CENTER);
		}
		return jContentPane;
	}

	private JMenuBar getJMenuBar()
	{
		if (jJMenuBar == null)
		{
			jJMenuBar = new JMenuBar();
			jJMenuBar.add(getFileMenu());
		}
		return jJMenuBar;
	}

	private JMenu getFileMenu()
	{
		if (fileMenu == null)
		{
			fileMenu = new JMenu();
			fileMenu.setText("File");
			fileMenu.add(getNewServerMenuItem());
			fileMenu.add(getLogoutMenuItem());
			fileMenu.add(getCloseMenuItem());
		}
		return fileMenu;
	}

	private JMenuItem getNewServerMenuItem()
	{
		if (newServerMenuItem == null)
		{
			newServerMenuItem = new JMenuItem();
			newServerMenuItem.setText("New Server");
			newServerMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
					Event.CTRL_MASK, true));
			newServerMenuItem.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					WConnect.getInstance().getJFrame().setVisible(true);
				}
			});
		}
		return newServerMenuItem;
	}
	
	private JMenuItem getCloseMenuItem()
	{
		if (closeMenuItem == null)
		{
			closeMenuItem = new JMenuItem();
			closeMenuItem.setText("Close");
			closeMenuItem.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					getJFrame().setVisible(false);
				}
			});
		}
		return closeMenuItem;
	}

	private JMenuItem getLogoutMenuItem()
	{
		if (logoutMenuItem == null)
		{
			logoutMenuItem = new JMenuItem();
			logoutMenuItem.setText("Logout");
			logoutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,
					Event.CTRL_MASK, true));
		}
		return logoutMenuItem;
	}

	private JScrollPane getJScUsers()
	{
		if (scUsers == null)
		{
			scUsers = new JScrollPane();
			scUsers.setViewportView(getLstUsers());
		}
		return scUsers;
	}

	private JTextField getTxtCmd()
	{
		if (txtCmd == null)
		{
			txtCmd = new JTextField();
			txtCmd.addKeyListener(new KeyAdapter()
			{
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ENTER)
					{
						conn.startSending();
						try
						{
							if (txtCmd.getText().charAt(0) == ':')
								conn.cmd(txtCmd.getText().substring(1));
							else
								conn.println(txtCmd.getText());
						}
						finally
						{
							conn.stopSending();
							txtCmd.setText("");
						}
					}
				}
			});
		}
		return txtCmd;
	}

	private JScrollPane getJScMain()
	{
		if (scMain == null)
		{
			scMain = new JScrollPane();
			scMain.setViewportView(getTxtMain());
		}
		return scMain;
	}

	private JTextArea getTxtMain()
	{
		if (txtMain == null)
		{
			txtMain = new JTextArea();
			txtMain.setEditable(false);
			txtMain.addKeyListener(setFocus);
		}
		return txtMain;
	}

	private JList getLstUsers()
	{
		if (lstUsers == null)
		{
			lstUsers = new JList(listModel);
			lstUsers.setCellRenderer(new ColorListCellRenderer());
			lstUsers.addKeyListener(setFocus);
			lstUsers.addMouseListener(new MouseAdapter()
			{
				public void mouseReleased(MouseEvent e)
				{
					if (lstUsers.getSelectedIndex() != -1)
						popUser.show(lstUsers, e.getX(), e.getY());
				}
				
			});
		}
		return lstUsers;
	}

	private JSplitPane getJSplitPane()
	{
		if (jSplitPane == null)
		{
			jSplitPane = new JSplitPane();
			jSplitPane.setDividerLocation(300);
			jSplitPane.setOneTouchExpandable(true);
			jSplitPane.setResizeWeight(1.0);
			jSplitPane.setLeftComponent(getJScMain());
			jSplitPane.setRightComponent(getJScUsers());
		}
		return jSplitPane;
	}
}

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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class WConnect implements DocumentListener
{
	private static class Server
	{
		public String name = null;
		public String ip = null;
		public int port;
		
		public Server(String n, String i, int p)
		{
			name = n;
			ip = i;
			port = p;
		}
	}
	
	private JFrame jFrame = null;
	private JPanel jContentPane = null;
	private JPanel Settings = null;
	private JPanel Buttons = null;
	private JList lstServers = null;
	private JButton bNew = null;
	private JButton bConnect = null;
	private JPanel jIPPanel = null;
	private JPanel jPortPanel = null;
	private JLabel lblIP = null;
	private JLabel lblPort = null;
	private JTextField txtIP = null;
	private JTextField txtPort = null;
	private JScrollPane jScrollPane = null;
	
	private ArrayList<Server> servers = new ArrayList<Server>();  //  @jve:decl-index=0:
	private DefaultListModel listModel = null;
	private Server selected = null;
	
	private static WConnect me = null;  //  @jve:decl-index=0:
	private JPanel jNamePanel = null;
	private JLabel lblName = null;
	private JTextField txtName = null;
	private JButton bRemove;
	
	private WConnect() throws IOException
	{
		File f = new File("servers.cfg");
	    if (!f.exists()) f.createNewFile();
		BufferedReader in = new BufferedReader(new FileReader(f));
    	Scanner cfg = new Scanner(in).useDelimiter(";");
		    
    	try
	    { 	
	    	getLstServers();
	    	while (cfg.hasNext())
	    	{
	    		Scanner value = new Scanner(cfg.next()).useDelimiter("=");
	    		if (!value.hasNext())
	    			continue;
		    	String name = value.next().trim();
	    		if (!value.hasNext())
	    			continue;
		    	value = new Scanner(value.next()).useDelimiter(":");
		    	String ip = value.next();
	    		if (!value.hasNext())
	    			continue;
		    	int port = Integer.parseInt(value.next());
		    	if (name.equals("") || ip.equals(""))
		    		continue;
		    	
		    	servers.add(new Server(name, ip, port));
		    	listModel.addElement(name);
	    	}
	    }
    	finally
    	{
	    	cfg.close();
    	}
	    
	    if (servers.size() > 0)
	    {
	    	select(0);
	    	getLstServers().setSelectedIndex(0);
	    }
	}
	
	public static WConnect getInstance()
	{
		if (me == null)
		{
			try
			{
				me = new WConnect();
			} catch (IOException e) {}
		}
		return me;
	}

	public static Socket getServer()
	{
		Socket s = null;
		if ((s = getInstance().connect()) == null)
			getInstance().getJFrame().setVisible(true);

		return s;
	}
	
	private Socket connect()
	{
		if (selected == null)
			return null;
		
		try
		{
			return new Socket(selected.ip, selected.port);
		}
		catch (UnknownHostException e)
		{
			JOptionPane.showMessageDialog(null, "Could not find Server: " + selected.ip,
					"Connection Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null, e.getMessage(),
					"Connection Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	private void addServer()
	{
    	servers.add(new Server("New Server", "", 0));
    	listModel.addElement("New Server");
    	lstServers.setSelectedIndex(listModel.size()-1);
	}
	
	private void removeServer()
	{
		servers.remove(lstServers.getSelectedIndex());
		listModel.remove(lstServers.getSelectedIndex());
	}
	
	private void select(int i)
	{
        selected = null;
        
        if (i == -1)
        {
        	getTxtName().setText("");
        	getTxtIP().setText("");
        	getTxtPort().setText("");
        	getBRemove().setEnabled(false);
        	getBConnect().setEnabled(false);
        }
        else
        {
        	getTxtName().setText(servers.get(i).name);
        	getTxtIP().setText(servers.get(i).ip);
        	getTxtPort().setText(""+servers.get(i).port);
        	selected = servers.get(i);
        	getBRemove().setEnabled(true);
        	getBConnect().setEnabled(true);
        }
	}

	private void saveServers()
	{
		if (lstServers.getSelectedIndex() >= 0)
		{
			listModel.set(lstServers.getSelectedIndex(), ""+txtName.getText());
			try
			{
				selected.name = txtName.getText();
				selected.ip = txtIP.getText();
				selected.port = Integer.parseInt("0"+txtPort.getText());
			}
			catch (Exception e){}
		}
		
	    try
	    {
			File f = new File("servers.cfg");
		    if (f.exists()) f.delete();
		    f.createNewFile();
		    BufferedWriter out = new BufferedWriter(new FileWriter(f));
	    	
		   for (int i=0;i<servers.size();i++)
	    	{
		    	out.write(servers.get(i).name+"=");
		    	out.write(servers.get(i).ip+":");
		    	out.write(servers.get(i).port+";\n");
	    	}
		   out.close();
	    }
	    catch (IOException e){}
	}
	
	public JFrame getJFrame()
	{
		if (jFrame == null)
		{
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			jFrame.setSize(300, 300);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("Servers");
			jFrame.setIconImage(Skin.getInstance().noicon);
			jFrame.addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent e)
				{
					saveServers();
				}
			});
		}
		return jFrame;
	}

	private JPanel getJContentPane()
	{
		if (jContentPane == null)
		{
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.add(getJScrollPane(), BorderLayout.WEST);
			jContentPane.add(getSettings(), BorderLayout.CENTER);
		}
		return jContentPane;
	}

	private JPanel getSettings()
	{
		if (Settings == null)
		{
			Settings = new JPanel();
			Settings.setLayout(new GridLayout(4, 1, 5, 5));
			Settings.add(getJNamePanel(), getJNamePanel().getName());
			Settings.add(getJIPPanel());
			Settings.add(getJPortPanel());
			Settings.add(getButtons());
		}
		return Settings;
	}

	private JPanel getButtons()
	{
		if (Buttons == null)
		{
			Buttons = new JPanel();
			Buttons.setLayout(new GridLayout(3, 1, 5, 0));
			Buttons.add(getBNew());
			Buttons.add(getBRemove());
			Buttons.add(getBConnect());
		}
		return Buttons;
	}

	private JScrollPane getJScrollPane()
	{
		if (jScrollPane == null)
		{
			jScrollPane = new JScrollPane(getLstServers());
			jScrollPane.setPreferredSize(new Dimension(jFrame.getWidth()/2, jFrame.getHeight()));
			jScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		}
		return jScrollPane;
	}
	
	private JList getLstServers()
	{
		if (lstServers == null)
		{
			listModel = new DefaultListModel();
			lstServers = new JList(listModel);
			lstServers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			lstServers.setLayoutOrientation(JList.VERTICAL);
			lstServers.setVisibleRowCount(-1);
			lstServers.addListSelectionListener(new ListSelectionListener()
			{
				public void valueChanged(ListSelectionEvent e)
				{
				    if (e.getValueIsAdjusting() == false)
				    {
				        select(lstServers.getSelectedIndex());
				    }

				}
			});
		}
		return lstServers;
	}

	private JButton getBNew()
	{
		if (bNew == null)
		{
			bNew = new JButton();
			bNew.setText("New Server");
			bNew.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					addServer();
				}
			});
		}
		return bNew;
	}

	private JButton getBRemove()
	{
		if (bRemove == null)
		{
			bRemove = new JButton();
			bRemove.setText("Remove Server");
			bRemove.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					removeServer();
				}
			});
		}
		return bRemove;
	}

	private JButton getBConnect()
	{
		if (bConnect == null)
		{
			bConnect = new JButton();
			bConnect.setText("Connect");
			bConnect.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					getJFrame().setVisible(false);
					WLobby.getInstance().connect();
				}
			});
		}
		return bConnect;
	}

	private JPanel getJNamePanel()
	{
		if (jNamePanel == null)
		{
			lblName = new JLabel();
			lblName.setText("Name:");
			jNamePanel = new JPanel();
			jNamePanel.setLayout(new GridLayout(2, 1, 2, 0));
			jNamePanel.add(lblName, null);
			jNamePanel.add(getTxtName(), null);
		}
		return jNamePanel;
	}
	
	private JPanel getJIPPanel()
	{
		if (jIPPanel == null)
		{
			lblIP = new JLabel();
			lblIP.setText("IP:");
			jIPPanel = new JPanel();
			jIPPanel.setLayout(new GridLayout(2, 1, 2, 0));
			jIPPanel.add(lblIP, null);
			jIPPanel.add(getTxtIP(), null);
		}
		return jIPPanel;
	}

	private JPanel getJPortPanel()
	{
		if (jPortPanel == null)
		{
			lblPort = new JLabel();
			lblPort.setText("Port:");
			jPortPanel = new JPanel();
			jPortPanel.setLayout(new GridLayout(2, 1, 2, 0));
			jPortPanel.add(lblPort, null);
			jPortPanel.add(getTxtPort(), null);
		}
		return jPortPanel;
	}
	
	private JTextField getTxtName()
	{
		if (txtName == null)
		{
			txtName = new JTextField();
			txtName.getDocument().addDocumentListener(this);
		}
		return txtName;
	}

	private JTextField getTxtIP()
	{
		if (txtIP == null)
		{
			txtIP = new JTextField();
			txtIP.getDocument().addDocumentListener(this);
		}
		return txtIP;
	}

	private JTextField getTxtPort()
	{
		if (txtPort == null)
		{
			txtPort = new JTextField();
			txtPort.getDocument().addDocumentListener(this);
		}
		return txtPort;
	}
	
	public void changedUpdate(DocumentEvent e)
	{
		saveServers();
	}

	public void insertUpdate(DocumentEvent e)
	{
		saveServers();
	}

	public void removeUpdate(DocumentEvent e)
	{
		saveServers();
	}
}

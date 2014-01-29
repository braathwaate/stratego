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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

public class WSetup
{
	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,390"
	private JPanel jContentPane = null;
	private JMenuBar jJMenuBar = null;
	private JMenu fileMenu = null;
	private JMenuItem exitMenuItem = null;
	private JMenuItem saveMenuItem = null;
	private JScrollPane jScrollPane = null;
	private JTextArea jTextArea = null;
	private JMenuItem openMenuItem;
	
	private static WSetup me = null;

	private WSetup() throws IOException
	{
		getJFrame().setVisible(true);
		
		File f = new File("ai.cfg");
		BufferedReader cfg;
	    if(!f.exists()) {
                        // f.createNewFile();
                        InputStream is = Class.class.getResourceAsStream("/com/cjmalloy/stratego/resource/ai.cfg");
                        InputStreamReader isr = new InputStreamReader(is);
                        cfg = new BufferedReader(isr);
		} else 
			cfg = new BufferedReader(new FileReader("ai.cfg"));

		String fn;
		while ((fn = cfg.readLine()) != null)
			if (jTextArea.getText().equals(""))
				jTextArea.setText(fn);
			else
				jTextArea.setText(jTextArea.getText() + "\n" + fn);
	}
	
	public static WSetup getInstance() throws IOException
	{
		if (me == null)
			me = new WSetup();
		return me;
	}
	
	private void open() throws IOException
	{
	    JFileChooser chooser = new JFileChooser();
	    int returnVal = chooser.showOpenDialog(this.getJFrame());
	    if(returnVal != JFileChooser.APPROVE_OPTION)
	    	return;

		if (jTextArea.getText().equals(""))
			jTextArea.setText(chooser.getSelectedFile().getCanonicalPath());
		else
			jTextArea.setText(jTextArea.getText() + "\n" + chooser.getSelectedFile().getCanonicalPath());
	}
	
	private void save() throws IOException
	{
        BufferedWriter out = new BufferedWriter(new FileWriter("ai.cfg"));
        out.write(jTextArea.getText());
        out.close();
	}
	
	public JFrame getJFrame()
	{
		if (jFrame == null)
		{
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			jFrame.setJMenuBar(getJJMenuBar());
			jFrame.setSize(481, 280);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("AI Setup Files");
			jFrame.setIconImage(Skin.getInstance().noicon);
		}
		return jFrame;
	}

	private JPanel getJContentPane()
	{
		if (jContentPane == null)
		{
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.add(getJScrollPane(), BorderLayout.CENTER);
		}
		return jContentPane;
	}

	private JMenuBar getJJMenuBar()
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
			fileMenu.add(getOpenMenuItem());
			fileMenu.add(getSaveMenuItem());
			fileMenu.add(getExitMenuItem());
		}
		return fileMenu;
	}

	private JMenuItem getExitMenuItem()
	{
		if (exitMenuItem == null)
		{
			exitMenuItem = new JMenuItem();
			exitMenuItem.setText("Close");
			exitMenuItem.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					getJFrame().setVisible(false);
				}
			});
		}
		return exitMenuItem;
	}

	private JMenuItem getSaveMenuItem()
	{
		if (saveMenuItem == null) {
			saveMenuItem = new JMenuItem();
			saveMenuItem.setText("Save");
			saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
					Event.CTRL_MASK, true));			
			saveMenuItem.addActionListener(new ActionListener()
					{
						public void actionPerformed(ActionEvent e)
						{
								try
								{
									save();
								}
								catch (IOException e1)
								{
									e1.printStackTrace();
								}
						}
					});
		}
		return saveMenuItem;
	}

	private JMenuItem getOpenMenuItem()
	{
		if (openMenuItem == null)
		{
			openMenuItem = new JMenuItem();
			openMenuItem.setText("Open");
			openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
					Event.CTRL_MASK, true));
			openMenuItem.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
						try
						{
							open();
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
						}
				}
			});
		}
		return openMenuItem;
	}
	
	private JScrollPane getJScrollPane()
	{
		if (jScrollPane == null) {
			jScrollPane = new JScrollPane();
			jScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			jScrollPane.setViewportView(getJTextArea());
		}
		return jScrollPane;
	}

	private JTextArea getJTextArea()
	{
		if (jTextArea == null) {
			jTextArea = new JTextArea();
			jTextArea.setLineWrap(true);
		}
		return jTextArea;
	}
}

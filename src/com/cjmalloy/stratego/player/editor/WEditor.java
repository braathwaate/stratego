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

package com.cjmalloy.stratego.player.editor;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;

import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.player.PieceButton;
import com.cjmalloy.stratego.player.WView;


public class WEditor extends WView
{
	private EditorControls board = null;  //  @jve:decl-index=0:
	private JButton openButton = null;
	private JButton saveButton = null;
	private JButton saveAsButton = null;
	
	private String fn = "";
	
	public WEditor(String f)
	{
		fn = f;
		board = new EditorBoard();
		
		getJFrame().setTitle("Stratego Map Editor");
		unSplash();
		update();
		
		if (!fn.equals(""))try
		{
			open();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void open() throws IOException
	{
	    if (fn.equals(""))
	    {
	    	openAs();
	    	return;
	    }
	    
		BufferedReader in = new BufferedReader(new FileReader(fn));
		
		board.clear();
		
		try
		{
			for (int j=0;j<40;j++)
			{
				int x = in.read(),
					y = in.read();
				
				if (x<0||x>9||y<0||y>3)
					throw new Exception();
				
				board.move(new Move(board.getTrayPiece(0), EditorBoard.IN_TRAY, new Spot(x, y)));
			}
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(getJFrame(), "File Format Error: Unexpected end of file.", 
					"Stratego Map Editor", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(getJFrame(), "File Format Error: Invalid File Structure.", 
					"Stratego Map Editor", JOptionPane.INFORMATION_MESSAGE);
		}
		finally
		{
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			update();
		}
	}
	
	private void openAs() throws IOException
	{
	    JFileChooser chooser = new JFileChooser();
	    int returnVal = chooser.showOpenDialog(this.getJFrame());
	    if(returnVal != JFileChooser.APPROVE_OPTION)
	    	return;

	    fn = chooser.getSelectedFile().getCanonicalPath();
	    
	    open();
	}	
	
	private void save() throws IOException
	{
		if (fn.equals(""))
		{
			saveAs();
			return;
		}
		
		for (int j=0;j<4;j++)
		for (int i=0;i<10;i++)
			if (board.getPiece(i, j) == null) 
			{

				JOptionPane.showMessageDialog(getJFrame(), "Board Setup Incomplete.",
						"Stratego Map Editor", JOptionPane.INFORMATION_MESSAGE);
				return;
			}

		ArrayList<PieceButton> setup = new ArrayList<PieceButton>();
		
		for (int j=0;j<4;j++)
		for (int i=0;i<10;i++)
			setup.add(grid[i][j]);
		Collections.sort(setup);
		

		BufferedWriter out = new BufferedWriter(new FileWriter(fn));
		
		for (PieceButton p: setup)
		{
			out.write(p.getSpot().getX());
			out.write(p.getSpot().getY());
		}
		
		out.close();
	}
	
	private void saveAs() throws IOException
	{
	    JFileChooser chooser = new JFileChooser();
	    int returnVal = chooser.showSaveDialog(this.getJFrame());
	    if(returnVal != JFileChooser.APPROVE_OPTION)
	    	return;

	    fn = chooser.getSelectedFile().getCanonicalPath();
	    
	    save();
	}

	public void update()
	{		
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			grid[i][j].setPiece(board.getPiece(i, j));

		for (int i=0;i<40;i++)
			trayComp[i].setPiece(null);
		
		for (int i=0;i<board.getTraySize();i++)
			{
				trayComp[i].setPiece(board.getTrayPiece(i));
			}
	}
	
	public void moveAction(Move m)
	{
		board.move(m);
		update();
	}

	protected void skinToolBar()
	{
		getOpenButton().setIcon(skin.scaledOpenIcon);
		getSaveButton().setIcon(skin.scaledSaveIcon);
		getSaveAsButton().setIcon(skin.scaledSaveAsIcon);
	}
	
	protected JMenuBar getJMenuBar()
	{
		if (jMenuBar == null) {
			jMenuBar = new JMenuBar();
			jMenuBar.add(getOpenButton());
			jMenuBar.add(getSaveButton());
			jMenuBar.add(getSaveAsButton());
		}
		return jMenuBar;
	}

	private JButton getOpenButton()
	{
		if (openButton == null)
		{
			openButton = new JButton();
			openButton.setText("Open");
			openButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				openButton.setBackground(skin.bgColor);
			openButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
						try {
							openAs();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
				}
			});
		}
		return openButton;
	}

	private JButton getSaveButton()
	{
		if (saveButton == null)
		{
			saveButton = new JButton();
			saveButton.setText("Save");
			saveButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				saveButton.setBackground(skin.bgColor);
			saveButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					try {
						save();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			});
		}
		return saveButton;
	}

	private JButton getSaveAsButton()
	{
		if (saveAsButton == null)
		{
			saveAsButton = new JButton();
			saveAsButton.setText("Save As");
			saveAsButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				saveAsButton.setBackground(skin.bgColor);
			saveAsButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					try {
						saveAs();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			});
		}
		return saveAsButton;
	}
}

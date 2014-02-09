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
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;


public class WView implements MoveListener
{
	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private JPanel jContentPane = null;
	private JPanel board = null;
	private JPanel comp = null;
	private JPanel user = null;
	protected JMenuBar jMenuBar = null;
	private JButton newGameButton = null;
	private JButton loadSkinButton = null;
	private JButton multiplayerButton = null;
	private JButton settingsButton = null;
	private JButton helpButton = null;
	private JButton playButton = null;
	private JButton undoButton = null;

	protected PieceButton grid[][] = new PieceButton[10][10];
	protected PieceButton trayComp[] = new PieceButton[40];
	protected PieceButton trayUser[] = new PieceButton[40];
	
	protected Skin skin = null;
	private UserControls engine = null;
	private Client client = new Client(this);  //  @jve:decl-index=0:
	private boolean warn = false;
	private boolean clientMode = false;
	protected boolean splashed;
	
	public WView()
	{
		skin = Skin.getInstance();
		try
		{
			splash();
			skin.loadSkin();
			skin.loadIcon();
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(getJFrame(),
					"Error loading default skin.", "Load Skin", JOptionPane.ERROR_MESSAGE);
			//e.printStackTrace();
			//System.exit(0);
		}

		getJFrame().setVisible(true);
		//resize();
	}
	
	public void update(Spot s, Piece p)
	{
		if (s == Board.IN_TRAY)
		{
			if (p.getRank().equals(Rank.NIL))
			{
				if (p.getColor()%2 == 1)
					trayUser[p.getID()].setPiece(null);
				else
					trayComp[p.getID()].setPiece(null);
			}
			else
			{
				if (p.getColor()%2 == 1)
					trayUser[p.getID()].setPiece(p);
				else
					trayComp[p.getID()].setPiece(p);
			}
		}
		else
		{
			if (p.getColor() < 0)
				grid[s.getX()][s.getY()].setPiece(null);
			else
				grid[s.getX()][s.getY()].setPiece(p);
		}
	}

	
	public void setColor(int c)
	{
		Settings.bottomColor = c;
		Settings.topColor = (c+1)%2;
	}
	
	public void update()
	{		
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			grid[i][j].setPiece(engine.getBoardPiece(i, j));
		updateTray();
		
		getJFrame().repaint();
	}

	public void setPlayMode()
	{
		jMenuBar.remove(5);
		jMenuBar.add(getPlayButton());
		jMenuBar.revalidate();
	}

	public void setUndoMode()
	{
		jMenuBar.remove(5);
		jMenuBar.add(getUndoButton());
		jMenuBar.revalidate();
	}

	private void updateTray()
	{
		for (int j=0;j<40;j++)
			trayUser[j].setPiece(null);
		for (int k=0;k<40;k++)
			trayComp[k].setPiece(null);
		
		int j = 0, k = 0;
		
		for (int i=0;i<engine.getTraySize();i++)
			if (engine.getTrayPiece(i).getColor() == Settings.bottomColor)
			{
				trayUser[j].setPiece(engine.getTrayPiece(i));
				j++;
			}
			else
			{
				trayComp[k].setPiece(engine.getTrayPiece(i));
				k++;
			}
	}
	
	public void gameOver(int winner)
	{
		warn = false;
		String win;
		if (winner == Settings.bottomColor)
			win = "won!";
		else if (winner == (Settings.bottomColor+1)%2)
			win = "lost.";
		else
		{
			if (clientMode)
			{
				win = "were disconnected from the game.";
			}
			else
			{
				//it's actually impossibly to tie in this game
				//this line should never run
				win = "tied.";
			}
		}
		JOptionPane.showMessageDialog(getJFrame(),
				"You " + win, "Game Over", JOptionPane.INFORMATION_MESSAGE);
	}
	
	public void moveAction(Move m)
	{
		if (clientMode)
		{
			if (client != null)
				client.move(m);
		}
		else
		{
			if (m.getPiece().getColor() == Settings.bottomColor)
			{
				if (m.getFrom() == 0) // Board.IN_TRAY
					engine.setupPlacePiece(m.getPiece(), new Spot(m.getToX(), m.getToY()));
				else if (m.getTo() ==  0) // Board.IN_TRAY
					engine.setupRemovePiece(new Spot(m.getFromX(), m.getFromY()));
				else
					engine.requestUserMove(m);
			}		
			update();
		}
	}
	
	public void dragAction()
	{
		getJFrame().repaint();
	}
	
	private void resize()
	{
		int x = getJContentPane().getWidth() - 15;
		int y = getJContentPane().getHeight() - 10;
		
		board.setBounds(new Rectangle(5, 5, (int)(5.0/9.0 * x) - ((int)(5.0/9.0 * x))%10, y - y%10));
		comp.setBounds(new Rectangle((int)(5.0/9.0 * x) + 10, 0, (int)(4.0/9.0 * x) - ((int)(4.0/9.0 * x))%8, y/2 - (y/2)%5));
		user.setBounds(new Rectangle((int)(5.0/9.0 * x) + 10, (y/2) + 10, (int)(4.0/9.0 * x) - ((int)(4.0/9.0 * x))%8, y/2 - (y/2)%5));
		getJContentPane().revalidate();
		
		skin.resize(x, y);

		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
		{
			grid[i][j].refreshIcon();
		}
		for(int i=0;i<40;i++)
		{
			trayComp[i].refreshIcon();
			trayUser[i].refreshIcon();
		}
		
		skinToolBar();
		getJFrame().repaint();
	}
	
	protected void skinToolBar()
	{
		if (clientMode)
			multiplayerButton.setIcon(skin.scaledOnePlayerIcon);
		else
			multiplayerButton.setIcon(skin.scaledTwoPlayerIcon);
		
		settingsButton.setIcon(skin.scaledSettingsIcon);
		loadSkinButton.setIcon(skin.scaledLoadSkinIcon);
		newGameButton.setIcon(skin.scaledNewGameIcon);
		helpButton.setIcon(skin.scaledHelpIcon);
		playButton.setIcon(skin.scaledPlayIcon);
		getUndoButton().setIcon(skin.scaledUndoIcon);
	}
	
	private void paintBack(Graphics g)
	{
		if (skin.bg != null)
			g.drawImage(skin.bg, 0, 0, jFrame.getWidth(), jFrame.getHeight(), null);
	}

	private void  paintTop(Graphics g)
	{
		if (splashed)
		{
			int x = (jFrame.getWidth() - skin.scaledSplash.getWidth(null)) / 2;
			int y =	(jFrame.getHeight() - skin.scaledSplash.getHeight(null)) / 2;
			
			if (y<55)
				y = 55;
			
			g.drawImage(skin.scaledSplash, x, y, null);
		}
		else if (PieceButton.dragIcon >= 0)
		{
		g.drawImage(skin.scaledSkins[PieceButton.dragIcon].getImage(), 
				MouseInfo.getPointerInfo().getLocation().x
					- skin.scaledSkins[PieceButton.dragIcon].getIconWidth()/2
					- getJFrame().getLocation().x, 
				MouseInfo.getPointerInfo().getLocation().y
					- skin.scaledSkins[PieceButton.dragIcon].getIconHeight()/2
					- getJFrame().getLocation().y,
				null);
		}
	}
	
	private void loadSkin() throws Exception
	{
	    JFileChooser chooser = new JFileChooser();
	    int returnVal = chooser.showOpenDialog(this.getJFrame());
	    if(returnVal != JFileChooser.APPROVE_OPTION)
	    	return;

        BufferedWriter out = new BufferedWriter(new FileWriter("skin.cfg"));
        out.write(chooser.getSelectedFile().getCanonicalPath() + "\n");
        out.close();
	    
	    skin.loadSkin(chooser.getSelectedFile().getCanonicalPath());
    	resize();

		if (!skin.bgColor.equals(Color.white))
			getJMenuBar().setBackground(skin.bgColor);
	}
	
	protected void splash() throws IOException
	{
		splashed = true;
		skin.loadSplash();
		getJContentPane().setVisible(false);
	}
	
	protected void  unSplash()
	{
		splashed = false;
		skin.unloadSplash();
		resize();
		getJContentPane().setVisible(true);
	}
	
	private void newGame() 
	{
		if (splashed)
		{
			unSplash();
		}

		if (warn()) return;

		setPlayMode();
		
		if (clientMode)
		{
			WLobby.getInstance().newGame();
		}
		else
		{
			Object[] options = {"Red", "Blue"};
			if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getJFrame(),
					"Which color would you like to be?", "New Game",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
					null, options, null))
				setColor(0);
			else
				setColor(1);
		
			if (engine == null)
				engine = new AIEngine(this);
			engine.newGame();
			update();
		}
	}

	private void play() 
	{
		if (splashed)
		{
			splashed = false;
			resize();
			getJContentPane().setVisible(true);
		}
		
		if (clientMode)
		{
			if (client == null)
			{
				newGame();
			}
			else
			{
				warn = true;
				client.play();
			}
		}
		else
		{
			if (engine == null)
			{
				newGame();
			}
			else
			{
				warn = true;
				engine.play();
			}
		}
	}
	
	private void stopClient()
	{
		if (warn()) return;
		clientMode = false;
		WLobby.getInstance().getJFrame().setVisible(false);
		WConnect.getInstance().getJFrame().setVisible(false);
		getMultiplayerButton().setIcon(skin.scaledTwoPlayerIcon);
		getMultiplayerButton().setText("Two Player");
		newGame();
	}
	
	private void startClient()
	{
		if (warn()) return;
		clientMode = true;
		if (engine != null)
		{
			engine = null;
		}
		WSettings.getInstance().getJFrame().setVisible(false);
		getMultiplayerButton().setIcon(skin.scaledOnePlayerIcon);
		getMultiplayerButton().setText("One Player");
		Settings.bNoHideAll = true;
		Settings.bShowAll = false;
		Settings.bDefendAdvantage = false;
		Settings.bNoMoveDefender = false;
		Settings.bNoShowDefender = false;
		Settings.bOneTimeBombs = false;
		newGame();
	}
	
	private boolean warn()
	{
		if (warn)
		{
			if (! (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(getJFrame(),
					"Discard current game?", "New Game", JOptionPane.YES_NO_OPTION)))
				return true;
		}
		warn = false;
		return false;
	}
	
	protected JFrame getJFrame()
	{
		if (jFrame == null)
		{
			jFrame = new JFrame()
			{
				public void paint(Graphics g)
				{
					super.paint(g);
					paintTop(g);
				}
				private static final long serialVersionUID = 1L;
			};
			jFrame.setIconImage(skin.icon);
			if (!skin.bgColor.equals(Color.white))
				jFrame.setBackground(skin.bgColor);
			jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			jFrame.setJMenuBar(getJMenuBar());
			jFrame.setSize(930, 575);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("Stratego");
			jFrame.addComponentListener(new ComponentListener()
			{
				public void componentResized(ComponentEvent e)
				{
					resize();
				}
				public void componentMoved(ComponentEvent e) {}
				public void componentShown(ComponentEvent e) {}
				public void componentHidden(ComponentEvent e) {}
			});
			jFrame.addWindowListener(new WindowListener()
			{
				public void windowClosing(WindowEvent e)
				{
					//System.exit(0);
					//TODO: check this
				}

				public void windowDeactivated(WindowEvent e) {}
				public void windowDeiconified(WindowEvent e) {}
				public void windowIconified(WindowEvent e) {}
				public void windowActivated(WindowEvent e) {}
				public void windowOpened(WindowEvent e) {}
				public void windowClosed(WindowEvent e) {}
				
			});
		}
		return jFrame;
	}

	protected JPanel getJContentPane()
	{
		if (jContentPane == null)
		{
			jContentPane = new JPanel()
			{
				public void paintComponent(Graphics g)
				{
					super.paintComponent(g);
					paintBack(g);
				}
				private static final long serialVersionUID = 1L;
					
			};
			jContentPane.setLayout(null);
			jContentPane.add(getBoard(), null);
			jContentPane.add(getComp(), null);
			jContentPane.add(getUser(), null);
		}
		return jContentPane;
	}

	private JPanel getBoard()
	{
		if (board == null)
		{
			board = new JPanel();
			board.setOpaque(false);
			board.setLayout(new GridLayout(10, 10 , 0, 0));
			board.setBounds(new Rectangle(5, 5, 500, 500));
			for (int j=0;j<10;j++)
			for (int i=0;i<10;i++)
			{
				grid[i][j] = new PieceButton(this, new Spot(i, j));
				board.add(grid[i][j]);
			}
		}
		return board;
	}

	private JPanel getComp()
	{
		if (comp == null)
		{
			GridLayout gridLayout = new GridLayout(5, 8, 0, 0);
			comp = new JPanel();
			comp.setOpaque(false);
			comp.setBounds(new Rectangle(510, 0, 400, 250));
			comp.setLayout(gridLayout);
			for (int i=0;i<40;i++)
			{
				trayComp[i] = new PieceButton(this, Board.IN_TRAY);
				comp.add(trayComp[i]);
			}
		}
		return comp;
	}

	private JPanel getUser()
	{
		if (user == null)
		{
			GridLayout gridLayout1 = new GridLayout(5, 8, 0, 0);
			user = new JPanel();
			user.setOpaque(false);
			user.setBounds(new Rectangle(510, 260, 400, 250));
			user.setLayout(gridLayout1);
			for (int i=0;i<40;i++)
			{
				trayUser[i] = new PieceButton(this, Board.IN_TRAY);
				user.add(trayUser[i]);
			}
		}
		return user;
	}

	protected JMenuBar getJMenuBar()
	{
		if (jMenuBar == null) {
			jMenuBar = new JMenuBar();
			jMenuBar.setLayout(new GridLayout(1, 6));
			if (!skin.bgColor.equals(Color.white))
				jMenuBar.setBackground(skin.bgColor);
			jMenuBar.add(getNewGameButton());
			jMenuBar.add(getLoadSkinButton());
			jMenuBar.add(getSettingsButton());
			jMenuBar.add(getHelpButton());
			jMenuBar.add(getMultiplayerButton());
			jMenuBar.add(getPlayButton());
		}
		return jMenuBar;
	}

	private JButton getNewGameButton()
	{
		if (newGameButton == null)
		{
			newGameButton = new JButton();
			newGameButton.setText("New Game");
			newGameButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				newGameButton.setBackground(skin.bgColor);
			newGameButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					newGame();
				}
			});
		}
		return newGameButton;
	}
	
	private JButton getLoadSkinButton()
	{
		if (loadSkinButton == null)
		{
			loadSkinButton = new JButton();
			loadSkinButton.setText("Load Skin");
			loadSkinButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				loadSkinButton.setBackground(skin.bgColor);
			loadSkinButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					try
					{
						loadSkin();
					}
					catch (Exception e1)
					{
						JOptionPane.showMessageDialog(getJFrame(),
								"Error loading skin.", "Load Skin", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
		}
		return loadSkinButton;
	}
	
	private JButton getMultiplayerButton()
	{
		if (multiplayerButton == null)
		{
			multiplayerButton = new JButton();
			multiplayerButton.setText("Two Player");
			multiplayerButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				multiplayerButton.setBackground(skin.bgColor);
			multiplayerButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (clientMode)
						stopClient();
					else
						startClient();
				}
			});
		}
		return multiplayerButton;
	}	
	
	private JButton getSettingsButton()
	{
		if (settingsButton == null)
		{
			settingsButton = new JButton();
			settingsButton.setText("Settings");
			settingsButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				settingsButton.setBackground(skin.bgColor);
			settingsButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (clientMode)
						WLobby.getInstance().getJFrame().setVisible(true);
					else
						WSettings.getInstance().getJFrame().setVisible(true);
				}
			});
		}
		return settingsButton;
	}
	
	private JButton getHelpButton()
	{
		if (helpButton == null)
		{
			helpButton = new JButton();
			helpButton.setText("Help");
			helpButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				helpButton.setBackground(skin.bgColor);
			helpButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					WHelp.getInstance().getJFrame().setVisible(true);
				}
			});
		}
		return helpButton;
	}
	
	private JButton getPlayButton()
	{
		if (playButton == null)
		{
			playButton = new JButton();
			playButton.setText("Play");
			playButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				playButton.setBackground(skin.bgColor);
			playButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					play();
				}
			});
		}
		return playButton;
	}

	private JButton getUndoButton()
	{
		if (undoButton == null)
		{
			undoButton = new JButton();
			undoButton.setText("Undo");
			undoButton.setFocusable(false);
			if (!skin.bgColor.equals(Color.white))
				undoButton.setBackground(skin.bgColor);
			undoButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					play();
				}
			});
		}
		return undoButton;
	}
}


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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;


public class PieceButton extends JButton implements MouseListener,
													MouseMotionListener,
													ComponentListener,
													Comparable<PieceButton>
{
	private static boolean mouseDown = false;
	private static Spot mouseFrom = null;
	private static Spot mouseTo = null;
	private static Piece mousePiece = null;
	public static int dragIcon = -1;
	
	private Piece piece = null;
	private Spot spot = null;
	private MoveListener listener = null;
	private Skin skin = null;
	private boolean resized = false;
	
	public PieceButton(MoveListener l, Spot s)
	{
		spot = s;
		listener = l;
		skin = Skin.getInstance();
		
		if (spot == Board.IN_TRAY || skin.hasBG())
			setOpaque(false);
		
	    setFocusPainted(false);
		addMouseListener(this);
		addComponentListener(this);
		addMouseMotionListener(this);
	    
		if (spot.equals(Board.IN_TRAY))
			setBackground(skin.bgColor);
		else
			setBackground(skin.mapColor);
	}
	
	public void setPiece(Piece p)
	{
		piece = p;
		if (piece != null && piece.getColor() > 1) {
			piece.setShown(true);
		}
		
		if (piece != null && piece.getColor() == -1)
			setBackground(skin.waterColor);
		
		refreshIcon();
	}
	
	public void refreshIcon()
	{		
		if (spot.equals(Board.IN_TRAY) ||
				skin.gridBG[spot.getX()][spot.getY()] == null)
		{
			if (piece == null || piece.getColor() == -1) 
			{
				setIcon(null);
				return;
			}
			
			if (piece.isShown()==false && 
				piece.getColor()%2!=Settings.bottomColor && 
				!Settings.bShowAll)
			{
				if (piece.getColor()%2 == 0) {
					if (piece.hasMoved())
						setIcon(skin.redABack);
					else
						setIcon(skin.redBack);
				} else {
					if (piece.hasMoved())
						setIcon(skin.blueABack);
					else
						setIcon(skin.blueBack);
				}
			}
			else
			{
				if (piece.getColor()%2 == 0)
				{
					if (piece.isKnown() && !Settings.bShowAll)
						setIcon(skin.redASkins[piece.getRank().ordinal()]);
					else
						setIcon(skin.redSkins[piece.getRank().ordinal()]);
				}
				else
				{
					if (piece.isKnown() && !Settings.bShowAll)
						setIcon(skin.blueASkins[piece.getRank().ordinal()]);
					else
						setIcon(skin.blueSkins[piece.getRank().ordinal()]);
				}
			}
		}
		else 
		{
		
			if (piece == null || piece.getColor() == -1)
			{
				setIcon(skin.gridBG[spot.getX()][spot.getY()]);
				return;
			}

			Image pc;
			if (piece.isShown()==false && 
				piece.getColor()%2!=Settings.bottomColor && 
				!Settings.bShowAll)
			{
				if (piece.getColor()%2 == 0) {
					if (piece.hasMoved())
						pc = skin.redABack.getImage();
					else
						pc = skin.redBack.getImage();
				} else {
					if (piece.hasMoved()) {
						pc = skin.blueABack.getImage();
					} else
						pc = skin.blueBack.getImage();
				}
			}
			else
			{
				if (piece.getColor()%2 == 0)
				{
					if (piece.isKnown() && !Settings.bShowAll)
						pc = skin.redASkins[piece.getRank().ordinal()].getImage();
					else if (piece.hasMoved() && !Settings.bShowAll)
						pc = skin.redAASkins[piece.getRank().ordinal()].getImage();
					else
						pc = skin.redSkins[piece.getRank().ordinal()].getImage();
				}
				else
				{
					if (piece.isKnown() && !Settings.bShowAll)
						pc = skin.blueASkins[piece.getRank().ordinal()].getImage();
					else if (piece.hasMoved() && !Settings.bShowAll)
						pc = skin.blueAASkins[piece.getRank().ordinal()].getImage();
					else

						pc = skin.blueSkins[piece.getRank().ordinal()].getImage();
				}
			}

			Image bg = skin.gridBG[spot.getX()][spot.getY()].getImage();
			BufferedImage bi = new BufferedImage(bg.getWidth(null), bg.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = bi.createGraphics();
			g.drawImage(bg, 0, 0, bg.getWidth(null), bg.getHeight(null),  null);
			g.drawImage(pc, (int)(0.1*bg.getWidth(null)), (int)(0.1*bg.getHeight(null)),
					(int)(0.9*bg.getWidth(null)), (int)(0.9*bg.getHeight(null)),
					0, 0, pc.getWidth(null), pc.getHeight(null), null);
			setIcon(new ImageIcon(bi));
			g.dispose();
		}
	}
	
	public Piece getPiece()
	{
		return piece;
	}
	
	public Spot getSpot()
	{
		return spot;
	}
	
	public void paint(Graphics g)
	{
		if (resized)
		{
			resized = false;
			
			if (piece != null &&
					skin.scaledSkins[piece.getRank().ordinal()] != null)
			{
				refreshIcon();
			}
		}

		super.paint(g);
	}

	public void mousePressed(MouseEvent e)
	{
		if (piece!=null &&
			piece.getColor()%2 == Settings.bottomColor)
		{
			mouseDown = true;
			mouseFrom = spot;
			mousePiece = piece;
			if (piece != null && skin.scaledSkins[piece.getRank().ordinal()] != null)
			{
				dragIcon = piece.getRank().ordinal();
			}
		}
	}

	public void mouseReleased(MouseEvent e)
	{
		dragIcon = -1;
		listener.dragAction();
		
		if (mouseDown)
		if (mousePiece!=null)
		if (mouseFrom!=null)
		if (mouseTo!=null)
		if (!mouseTo.equals(mouseFrom))
			listener.moveAction(new Move(mousePiece, mouseFrom, mouseTo));
		
		mouseDown = false;
	}
	
	public void mouseDragged(MouseEvent e)
	{
		listener.dragAction();
	}

	public void mouseEntered(MouseEvent arg0)
	{

		listener.dragAction();
		if (mouseDown)
			mouseTo = spot;
	}

	public void mouseExited(MouseEvent e)
	{
		mouseTo = null;
	}

	public void componentResized(ComponentEvent e)
	{
		resized = true;
		repaint();
	}
	
	
	public int compareTo(PieceButton p)
	{
		return piece.compareTo(p.piece);
	}

	private static final long serialVersionUID = 1L;


	public void componentHidden(ComponentEvent e) {}
	public void componentMoved(ComponentEvent e) {}
	public void componentShown(ComponentEvent e) {}
	public void mouseClicked(MouseEvent e){}
	public void mouseMoved(MouseEvent e) {}
}

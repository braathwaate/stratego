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

package com.cjmalloy.stratego;

import java.util.ArrayList;
import java.util.Collections;


public class Board
{
	protected static class UniqueID
	{
		private static int id = 0;
		
		static public int get()
		{
			id++;
			return id;
		}
	}
	
	public static final int RED  = 0;
	public static final int BLUE = 1;
	public static final Spot IN_TRAY = new Spot(-1, -1);
	
	protected Grid grid = new Grid();
	protected ArrayList<Piece> tray = new ArrayList<Piece>();
	protected ArrayList<Piece> red = new ArrayList<Piece>();
	protected ArrayList<Piece> blue = new ArrayList<Piece>();
	protected ArrayList<BMove> recentMoves = new ArrayList<BMove>();
	
	public Board()
	{

		//create pieces
		red.add(new Piece(UniqueID.get(), RED, Rank.FLAG));
		red.add(new Piece(UniqueID.get(), RED, Rank.SPY));
		red.add(new Piece(UniqueID.get(), RED, Rank.ONE));
		red.add(new Piece(UniqueID.get(), RED, Rank.TWO));
		for (int j=0;j<2;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.THREE));
		for (int j=0;j<3;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.FOUR));
		for (int j=0;j<4;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.FIVE));
		for (int j=0;j<4;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.SIX));
		for (int j=0;j<4;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.SEVEN));
		for (int j=0;j<5;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.EIGHT));
		for (int j=0;j<8;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.NINE));
		for (int j=0;j<6;j++)
			red.add(new Piece(UniqueID.get(), RED, Rank.BOMB));

		//create pieces
		blue.add(new Piece(UniqueID.get(), BLUE, Rank.FLAG));
		blue.add(new Piece(UniqueID.get(), BLUE, Rank.SPY));
		blue.add(new Piece(UniqueID.get(), BLUE, Rank.ONE));
		blue.add(new Piece(UniqueID.get(), BLUE, Rank.TWO));
		for (int j=0;j<2;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.THREE));
		for (int j=0;j<3;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.FOUR));
		for (int j=0;j<4;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.FIVE));
		for (int j=0;j<4;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.SIX));
		for (int j=0;j<4;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.SEVEN));
		for (int j=0;j<5;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.EIGHT));
		for (int j=0;j<8;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.NINE));
		for (int j=0;j<6;j++)
			blue.add(new Piece(UniqueID.get(), BLUE, Rank.BOMB));

		tray.addAll(red);
		tray.addAll(blue);

		Collections.sort(tray);
	}

	public Board(Board b)
	{
                for (int i=0;i<10;i++)
                for (int j=0;j<10;j++)
                        grid.setPiece(i, j, b.getPiece(i, j));
				
		tray.addAll(b.tray);
		recentMoves.addAll(b.recentMoves);
	}

	public boolean add(Piece p, Spot s)
	{
		if (p.getColor() == Settings.topColor)
		{
			if(s.getY() > 3)
				return false;
		}
		else
		{
			if (s.getY() < 6)
				return false;
		}
			
		if (getPiece(s) == null)
		{
			grid.setPiece(s.getX(), s.getY(), p);
			tray.remove(p);
			return true;
		}
		return false;
	}

	// TRUE if valid attack
	public boolean attack(BMove m)
	{
		if (validAttack(m))
		{
			Piece fp = getPiece(m.getFrom());
			Piece tp = getPiece(m.getTo());
			setShown(fp, true);
			fp.setKnown(true);
			tp.setKnown(true);
			if (!Settings.bNoShowDefender || fp.getRank() == Rank.NINE) {
				setShown(tp, true);
			}
			
			int result = fp.getRank().winFight(tp.getRank());
			if (result == 1)
			{
				remove(m.getTo());
				setPiece(fp, m.getTo());
				setPiece(null, m.getFrom());
			}
			else if (result == -1)
			{
				remove(m.getFrom());
				remove(m.getTo());
			}
			else 
			{
				if (Settings.bNoMoveDefender ||
						tp.getRank() == Rank.BOMB)
					remove(m.getFrom());
				else if (fp.getRank() == Rank.NINE)
					scoutLose(m);
				else
				{
					remove(m.getFrom());
					setPiece(tp, m.getFrom());
					setPiece(null, m.getTo());
				}
			}
			return true;
		}
		return false;
	}

	public int checkWin()
	{
		int flags = 0;
		int flagColor = -1;
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			if (getPiece(i, j) != null)
			if (getPiece(i, j).getRank().equals(Rank.FLAG))
			{
				flagColor = grid.getPiece(i,j).getColor();
				flags++;
			}
		if (flags!=2)
			return flagColor;
		
		for (int k=0;k<2;k++)
		{
			int movable = 0;
			for (int i=0;i<10;i++)
			for (int j=0;j<10;j++)
			{
				if (getPiece(i, j) != null)
				if (getPiece(i, j).getColor() == k)
				if (!getPiece(i, j).getRank().equals(Rank.FLAG))
				if (!getPiece(i, j).getRank().equals(Rank.BOMB))
				if (getPiece(i+1, j) == null ||
					getPiece(i+1, j).getColor() == (k+1)%2||
					getPiece(i, j+1) == null ||
					getPiece(i, j+1).getColor() == (k+1)%2||
					getPiece(i-1, j) == null ||
					getPiece(i-1, j).getColor() == (k+1)%2||
					getPiece(i, j-1) == null ||
					getPiece(i, j-1).getColor() == (k+1)%2)
					
					movable++;
			}
			
			if (movable==0)
				return (k+1)%2;
		}
		
		return -1;
	}
	
	public void clear()
	{
		grid.clear();

		tray.clear();
		tray.addAll(red);
		tray.addAll(blue);

		Collections.sort(tray);
		
		for (Piece p: tray) {
			p.setShown(false);
			p.setKnown(false);
			p.setMoved(false);
		}
	}
	
	public Piece getPiece(int x, int y)
	{
		return grid.getPiece(x, y);
	}
	
	public boolean isValid(int i)
	{
		return grid.isValid(i);
	}
	
	public Piece getPiece(int i)
	{
		return grid.getPiece(i);
	}
	
	public Piece getPiece(Spot s)
	{
		return grid.getPiece(s.getX(),s.getY());
	}

	
	public Piece getTrayPiece(int i)
	{
		return tray.get(i);
	}
	
	public int getTraySize()
	{
		return tray.size();
	}
	
	public void showAll()
	{
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			if (grid.getPiece(i, j) != null)
				setShown(grid.getPiece(i,j), true);
		for (Piece p: tray)
			setShown(p, true);
	}
	
	public void hideAll()
	{
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			if (grid.getPiece(i, j) != null)
				setShown(grid.getPiece(i,j), false);

		hideTray();
	}
	
	public void hideTray()
	{
		for (Piece p: tray)
			setShown(p, false);
	}
	
	protected void setPiece(Piece p, Spot s)
	{
		grid.setPiece(s.getX(),s.getY(),p);
	}
	
	protected void setPiece(Piece p, int i)
	{
		grid.setPiece(i, p);
	}
	
	protected void setShown(Piece p, boolean b)
	{
		p.setShown(b);	// make piece visible
	}
	
	public boolean move(BMove m)
	{
		if (validMove(m))
		{
			Piece fp = getPiece(m.getFrom());
			recentMoves.add(m);
			
			setPiece(fp, m.getTo());
			setPiece(null, m.getFrom());
			fp.setMoved(true);
			fp.moves++;
			if (Math.abs(m.getToX() - m.getFromX()) > 1 || 
				Math.abs(m.getToY() - m.getFromY()) > 1)
				setShown(fp, true);

			return true;
		}
		
		return false;
	}
	
	public boolean remove(int i)
	{
		if (getPiece(i) == null)
			return false;
		
		getPiece(i).setShown(true);
		tray.add(getPiece(i));
		setPiece(null, i);
		Collections.sort(tray);
		return true;
	}

	public boolean remove(Spot s)
	{
		return remove(grid.getIndex(s.getX(), s.getY()));
	}

	public boolean isRecentMove(BMove m)
	{
		int size = recentMoves.size();
		if (size < 4)
			return false;
		return m.equals(recentMoves.get(size-4));
	}
	
	private void scoutLose(BMove m)
	{
		Spot tmp = getScoutLooseFrom(m);

		remove(m.getFrom());
		setPiece(getPiece(m.getTo()), tmp);
		setPiece(null, m.getTo());
	}
	
	private Spot getScoutLooseFrom(BMove m)
	{
		if (m.getFromX() == m.getToX())
		{
			if (m.getFromY() < m.getToY())
				return new Spot(m.getToX(), m.getToY() - 1);
			else
				return new Spot(m.getToX(), m.getToY() + 1);
		}
		else //if (m.getFromY() == m.getToY())
		{
			if (m.getFromX() < m.getToX())
				return new Spot(m.getToX() - 1, m.getToY());
			else
				return new Spot(m.getToX() + 1, m.getToY());
		}
	}
	
	protected boolean validAttack(BMove m)
	{
		if (m.getToX()<0||m.getToX()>9||
			m.getToY()<0||m.getToY()>9)
			return false;
		if (getPiece(m.getTo()) == null)
			return false;
		if (getPiece(m.getFrom()) == null)
			return false;
		if (getPiece(m.getTo()).getRank().equals(Rank.WATER))
			return false;
		

		Piece tmp = getPiece(m.getTo());
		setPiece(null, m.getTo());
		
		boolean valid = validMove(m);
		setPiece(tmp, m.getTo());
		
		return valid;

	}

	// TRUE if piece moves to legal and unoccupied square
	public boolean validMove(BMove m)
	{
		if (m.getToX()<0||m.getToX()>9||
			m.getToY()<0||m.getToY()>9)
			return false;
		if (getPiece(m.getTo()) != null)
			return false;
		Piece fp = getPiece(m.getFrom());
		if (fp == null)
			return false;
		
		//check for rule: "a player may not move their piece back and fourth.." or something
		if (isRecentMove(new Move(fp, m)))
			return false;

		switch (fp.getRank())
		{
		case FLAG:
		case BOMB:
			return false;
		}

		if (m.getFromX() == m.getToX())
			if (Math.abs(m.getFromY() - m.getToY()) == 1)
				return true;
		if (m.getFromY() == m.getToY())
			if (Math.abs(m.getFromX() - m.getToX()) == 1)
				return true;

		if (fp.getRank().equals(Rank.NINE))
			return validScoutMove(m.getFromX(), m.getFromY(), m.getToX(), m.getToY(), fp);

		return false;
	}
	
	private boolean validScoutMove(int x, int y, int tox, int toy, Piece p)
	{
		if ( !(grid.getPiece(x,y) == null || p.equals(grid.getPiece(x,y))) )
			return false;

		if (x == tox)
		{
			if (Math.abs(y - toy) == 1)
			{
				p.setKnown(true); //scouts reveal themselves by moving more than one piece
				return true;
			}
			if (y - toy > 0)
				return validScoutMove(x, y - 1, tox, toy, p);
			if (y - toy < 0)
				return validScoutMove(x, y + 1, tox, toy, p);
		}
		else if (y == toy)
		{
			if (Math.abs(x - tox) == 1)
			{
				p.setKnown(true);
				return true;
			}
			if (x - tox > 0)
				return validScoutMove(x - 1, y, tox, toy, p);
			if (x - tox < 0)
				return validScoutMove(x + 1, y, tox, toy, p);
		}

		return false;
	}
}


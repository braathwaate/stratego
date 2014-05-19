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
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;


//
// The board class contains all the factual information
// about the current and historical position
//
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

        public class UndoMove extends Move
        {
                public Piece tp = null;
		public Piece fpcopy = null;
		public Piece tpcopy = null;
		public long hash = 0;

		public UndoMove(Piece fpin, Piece tpin, int f, int t, long h)
		{
			super(fpin, f, t);
			tp = tpin;
			fpcopy = new Piece(fpin);
			if (tp != null)
				tpcopy = new Piece(tp);
			else
				tpcopy = null;
			hash = h;
		}
        }

        public ArrayList<UndoMove> undoList = new ArrayList<UndoMove>();

	
	public static final int RED  = 0;
	public static final int BLUE = 1;
	public static final Spot IN_TRAY = new Spot(-1, -1);
	
	protected Grid grid = new Grid();
	protected ArrayList<Piece> tray = new ArrayList<Piece>();
	protected static ArrayList<Piece> red = new ArrayList<Piece>();
	protected static ArrayList<Piece> blue = new ArrayList<Piece>();
	// setup contains the original locations of Pieces
	// as they are discovered.
	// this is used to guess flags and other bombs
	// TBD: try to guess the opponents entire setup by
	// correllating against all setups in the database
	protected Rank[] setup = new Rank[121];
	protected static int[] dir = { -11, -1,  1, 11 };
	protected static int[] dir2 = { -10, -11, -12, -1,  1, 10, 11, 12 };
	protected static long[][][][][] boardHash = new long[2][2][2][15][121];
	protected long hash = 0;
	
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

		Random rnd = new Random();
		for ( int k = 0; k < 2; k++)
		for ( int m = 0; m < 2; m++)
		for ( int c = RED; c <= BLUE; c++)
		for ( int r = 0; r < 15; r++)
		for ( int i = 12; i <= 120; i++)
			boardHash[k][m][c][r][i] = rnd.nextLong();
						
	}

	public Board(Board b)
	{
		grid = new Grid(b.grid);
				
		tray.addAll(b.tray);
		undoList.addAll(b.undoList);
		setup = b.setup.clone();
		hash = b.hash;
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
			setPiece(p, s);
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
			moveHistory(fp, tp, m.getFrom(), m.getTo());

			fp.setShown(true);
			fp.makeKnown();
			tp.makeKnown();
			fp.setMoved(true);
			fp.moves++;
			if (!Settings.bNoShowDefender || fp.getRank() == Rank.NINE) {
				tp.setShown(true);
			}

			// TBD: store original setup location
			// after each discovery
			if (tp.getRank() == Rank.BOMB)
				setup[m.getTo()] = Rank.BOMB;
			
			int result = fp.getRank().winFight(tp.getRank());
			if (result == 1)
			{
				moveToTray(m.getTo());
				setPiece(fp, m.getTo());
				setPiece(null, m.getFrom());
			}
			else if (result == -1)
			{
				moveToTray(m.getFrom());
				moveToTray(m.getTo());
			}
			else 
			{
				if (Settings.bNoMoveDefender ||
						tp.getRank() == Rank.BOMB)
					moveToTray(m.getFrom());
				else if (fp.getRank() == Rank.NINE)
					scoutLose(m);
				else
				{
					moveToTray(m.getFrom());
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
		// put all the pieces in the tray
		tray.addAll(red);
		tray.addAll(blue);

		Collections.sort(tray);

		// clear all the dynamic piece data
		for (Piece p: tray)
			p.clear();

		undoList.clear();

		for (int i = 12; i <=120; i++)
			setup[i] = Rank.UNKNOWN;

		hash = 0;
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
				grid.getPiece(i,j).setShown(true);
		for (Piece p: tray)
			p.setShown(true);
	}
	
	public void hideAll()
	{
		for (int i=0;i<10;i++)
		for (int j=0;j<10;j++)
			if (grid.getPiece(i, j) != null)
				grid.getPiece(i,j).setShown(false);

		hideTray();
	}
	
	public void hideTray()
	{
		for (Piece p: tray)
			p.setShown(false);
	}
	
	public void setPiece(Piece p, Spot s)
	{
		setPiece(p, Grid.getIndex(s.getX(),s.getY()));
	}

	// Zobrist hashing.
	// Used to determine redundant board positions.
	//
	// TBD: Unknown opponent pieces have UNKNOWN rank.  Hence, if two
	// UNKNOWN pieces swap positions, the board is considered
	// to be the same.  However, the AI does not consider all
	// UNKNOWN pieces to be the same, because the AI bases rank
	// rank predictions based on setup and the movement of the piece.
	// So for UNKNOWN pieces, the rank should instead be the
	// predicted rank.
	public void rehash(Piece p, int i)
	{
		Rank rank;
		if (p.getColor() == Settings.topColor)
			rank = p.getRank();
		else
			rank = p.getApparentRank();
		hash ^= boardHash
			[p.isKnown() ? 1 : 0]
			[p.hasMoved() ? 1 : 0]
			[p.getColor()]
			[rank.toInt()-1]
			[i];
	}
	
	public void setPiece(Piece p, int i)
	{
		Piece bp = getPiece(i);
		if (bp != null)
			rehash(bp, i);

		grid.setPiece(i, p);
		if (p != null)
			rehash(p, i);
	}

	// return TRUE if the current board position
	// has been seen before
	public boolean dejavu()
	{
		int size = undoList.size();
		if (size <= 2)
			return false;
		for ( int k = size-2; k >= 0; k -= 2) {
			UndoMove u = undoList.get(k);
			if (u.hash == hash)
				return true;
		}
		return false;
	}

	public UndoMove getLastMove()
	{
		return undoList.get(undoList.size()-1);
	}

	public UndoMove getLastMove(int i)
	{
		int size = undoList.size();
		if (size < i)
			return null;
		return undoList.get(size-i);
	}

	// Return true if the chase piece is obviously protected.
	//
	// Examples:
	// If unknown Blue moves towards Red 3 and Blue 8,
	// the piece is not protected.
	// R3 -- B?
	// -- B8 --
	//
	// If unknown Blue moves towards unknown Red and Blue 8,
	// the piece is not protected.
	// R? -- B?
	// -- B8 --
	//
	// If unknown Blue moves towards Red 3 and Blue 2,
	// the piece is protected.
	// R3 -- B?
	// -- B2 --
	//
	// If unknown Blue moves towards Red 3 and unknown Blue,
	// the piece is unprotected.
	// R3 -- B?
	// -- B? --
	//
	public boolean isProtectedChase(Piece fp, Piece tp, int i)
	{
		for (int d : dir) {
			int j = i + d;
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p != null
				&& p != fp
				&& p.getColor() == fp.getColor()
				&& tp.isKnown()
				&& p.getApparentRank().toInt() < tp.getApparentRank().toInt()) {
				return true;
			}
		}
		return false;
	}

	// return if fleeing piece could be protected
	public boolean isProtectedFlee(Piece fp, Piece tp, int i)
	{
		for (int d : dir) {
			int j = i + d;
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p != null
				&& p != fp
				&& p.getColor() == fp.getColor()
				&& (p.getApparentRank() == Rank.UNKNOWN
					|| p.getApparentRank().toInt() < tp.getApparentRank().toInt())) {
				return true;
			}
		}
		return false;
	}

	protected void moveHistory(Piece fp, Piece tp, int from, int to)
	{
		UndoMove um = new UndoMove(fp, tp, from, to, hash);

		// Acting rank is a historical property of the known
		// opponent piece ranks that a moved piece was adjacent to.
		//
		// Acting rank is calculated factually, but is of
		// questionable use in the heuristic because of bluffing.
		//
		// If an unprotected attacker moves next to a opponent piece, 
		// it inherits a chase acting rank
		// of a equal to or lower than the piece.
		//
		// If a piece moves away from an unprotected
		// opponent attacker, it inherits a flee acting rank
		// of the lowest rank that it fled from.
		// High flee acting ranks are probably of
		// little use because unknown low ranks may flee
		// to prevent discovery.
		//
		// However, the lower the rank it flees from,
		// the more likely that the piece rank
		// really is equal to or greater than the flee acting rank,
		// because low ranks are irresistable captures.
		// 

		// movement towards
		for (int d : dir) {
			int chased = to + d;
			if (!isValid(chased))
				continue;
			Piece chasedPiece = getPiece(chased);
			if (chasedPiece != null
				&& chasedPiece.getColor() != fp.getColor()) {
				// chase confirmed
					
				Rank rank = chasedPiece.getApparentRank();
				if (rank == Rank.BOMB)
					continue;

				// if the chase piece is protected,
				// it is less likely (although possible)
				// that the piece is a lower ranked piece.
				if (isProtectedChase(fp, chasedPiece, to))
					continue;

				Rank arank = fp.getActingRankChase();
				if (arank == Rank.NIL
				|| arank.toInt() > rank.toInt())
					fp.setActingRankChase(rank);
			}
		}

		// movement aways
		for (int d : dir) {
			int chaser = from + d;
			if (!isValid(chaser))
				continue;
			Piece chasePiece = getPiece(chaser);
			if (chasePiece != null
				&& chasePiece.getColor() != fp.getColor()) {
				// chase confirmed
				Rank rank = chasePiece.getApparentRank();
				if (rank == Rank.BOMB)
					continue;

				// if the chase piece is protected,
				// nothing can be guessed about the rank
				// of the fleeing piece.  It could be
				// fleeing because it is a high ranked
				// piece but it could also be
				// a low ranked piece that wants to attack
				// the chase piece, but does not because
				// of the protection.
				if (isProtectedFlee(chasePiece, fp, chaser))
					continue;

				Rank arank = fp.getActingRankFlee();
				if (arank == Rank.NIL
				|| arank.toInt() > rank.toInt())
					fp.setActingRankFlee(rank);
			}
		}

		// Flee acting rank is also set implicitly if
		// the piece neglects to capture an adjacent opponent
		// piece and the immediate player move was not a capture
		// (NOT IMPLEMENTED: or a capture of a less valuable piece).  
		for ( int i = 12; i <= 120; i++) {
			if (i == from)
				continue;
			Piece fleeTp = getPiece(i);
			if (fleeTp == null
				|| fleeTp.isKnown()
				|| fleeTp.getColor() != fp.getColor())
				continue;
			
			for (int d : dir) {
				int j = i + d;
				if (!isValid(j))
					continue;
				Piece op = getPiece(j);
				if (op != null
					&& op.getColor() != fp.getColor()
					&& !isProtectedFlee(op, fleeTp, j)
					&& tp == null) {
					Rank rank = op.getApparentRank();
					if (rank == Rank.BOMB)
						continue;
					Rank arank = fleeTp.getActingRankFlee();
					if (arank == Rank.NIL
					|| arank.toInt() > rank.toInt())
						fleeTp.setActingRankFlee(rank);
				}
			}
		}

		undoList.add(um);
	}
	
	public boolean move(BMove m)
	{
		if (getPiece(m.getTo()) != null)
			return false;

		if (validMove(m))
		{
			Piece fp = getPiece(m.getFrom());
			moveHistory(fp, null, m.getFrom(), m.getTo());

			setPiece(null, m.getFrom());
			setPiece(fp, m.getTo());
			fp.setMoved(true);
			fp.moves++;
			if (Math.abs(m.getToX() - m.getFromX()) > 1 || 
				Math.abs(m.getToY() - m.getFromY()) > 1) {
				//scouts reveal themselves by moving more than one place
				fp.setShown(true);
				fp.setRank(Rank.NINE);
				fp.makeKnown();
			}

			return true;
		}
		
		return false;
	}

	public void moveToTray(int i)
	{
		assert getPiece(i) != null : "getPiece(i) null?";

		getPiece(i).setShown(true);
		getPiece(i).setKnown(false);

		for (Piece p : tray) 
			p.setKnown(true);

		remove(i);
	}
	
	public boolean remove(int i)
	{
		if (getPiece(i) == null)
			return false;
		
		tray.add(getPiece(i));
		setPiece(null, i);
		Collections.sort(tray);
		return true;
	}

	public boolean remove(Spot s)
	{
		return remove(grid.getIndex(s.getX(), s.getY()));
	}

	// Three Moves on Two Squares: Two Square Rule.
	//
	// It is not allowed to move a piece more than 3
	// times non-stop between the same two
	// squares, regardless of what the opponent is doing.
	//
	public boolean isTwoSquares(BMove m)
	{
		int size = undoList.size();
		if (size < 6)
			return false;

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).
		if (Settings.twoSquares
			|| getPiece(m.getFrom()).getColor() == Settings.topColor) {
			BMove prev = undoList.get(size-2);
			return (undoList.get(size-6)).equals(prev)
				&& m.getTo() == prev.getFrom();
		} else
			// let opponent get away with it
			return false;
	}

	// Repetition of Threatening Moves: More-Squares Rule
	// It is not allowed to continuously chase one or
	// more pieces of the opponent endlessly. The
	// continuous chaser may not play a chasing
	// move which would lead to a position on the
	// board which has already taken place.

	// return TRUE if cyclic move
	public boolean isMoreSquares()
	{
		int size = undoList.size();
		if (size < 2)
			return false;
		BMove aimove = undoList.get(size-1);
		BMove oppmove = undoList.get(size-2);

		for (int d : dir) {
			int i = oppmove.getTo() + d;
			if (!isValid(i))
				continue;
			if (i == aimove.getTo()) {
				// chase confirmed
				return dejavu();
			}
			// we are being chased, so repetitive moves OK
			if (i == aimove.getFrom())
				return false;
		}
		// no chases
		// return false;
		// prevent looping
		return dejavu();
	}

	// This is a stricter version of the Two Squares rule.
	// It is used during move generation to forward prune
	// repeated moves.
	public boolean isRepeatedMove(BMove m)
	{
		int size = undoList.size();
		if (size < 4)
			return false;

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).
		if (Settings.twoSquares
			|| getPiece(m.getFrom()).getColor() == Settings.topColor) {
			BMove prev = undoList.get(size-4);
			return m.equals(prev);
		} else
			// let opponent get away with it
			return false;
	}

	public void undoLastMove()
	{
		int size = undoList.size();
		if (size < 2)
			return;
		for (int j = 0; j < 2; j++, size--) {
			UndoMove undo = undoList.get(size-1);
			Piece fp = undo.getPiece();
			Piece tp = getPiece(undo.to);
			fp.copy(undo.fpcopy);
			setPiece(undo.getPiece(), undo.from);
			setPiece(undo.tp, undo.to);
			if (undo.tp != null) {
				undo.tp.copy(undo.tpcopy);
				if (tp == null) {
					tray.remove(tray.indexOf(undo.tp));
					tray.remove(tray.indexOf(undo.getPiece()));
				} else if (tp.equals(undo.tp))
					tray.remove(tray.indexOf(undo.getPiece()));
				else
					tray.remove(tray.indexOf(undo.tp));
			}
			undoList.remove(size - 1);
		}
		Collections.sort(tray);
	}


	private void scoutLose(BMove m)
	{
		Spot tmp = getScoutLooseFrom(m);

		moveToTray(m.getFrom());
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
		if (!isValid(m.getTo()) || !isValid(m.getFrom()))
			return false;
		if (getPiece(m.getTo()) == null)
			return false;
		if (getPiece(m.getFrom()) == null)
			return false;
		if (getPiece(m.getFrom()).getColor() ==
			getPiece(m.getTo()).getColor())
			return false;

		return validMove(m);
	}

	// TRUE if piece moves to legal square
	public boolean validMove(BMove m)
	{
		if (!isValid(m.getTo()) || !isValid(m.getFrom()))
			return false;
		Piece fp = getPiece(m.getFrom());
		if (fp == null)
			return false;
		
		//check for rule: "a player may not move their piece back and fourth.." or something
		if (isTwoSquares(m))
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

		if (fp.getRank().equals(Rank.NINE)
			|| fp.getRank().equals(Rank.UNKNOWN))
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
				return true;
			if (y - toy > 0)
				return validScoutMove(x, y - 1, tox, toy, p);
			if (y - toy < 0)
				return validScoutMove(x, y + 1, tox, toy, p);
		}
		else if (y == toy)
		{
			if (Math.abs(x - tox) == 1)
				return true;
			if (x - tox > 0)
				return validScoutMove(x - 1, y, tox, toy, p);
			if (x - tox < 0)
				return validScoutMove(x + 1, y, tox, toy, p);
		}

		return false;
	}
}


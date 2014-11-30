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
import java.util.Comparator;
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

        public ArrayList<UndoMove> undoList = new ArrayList<UndoMove>();
	public static final int RED  = 0;
	public static final int BLUE = 1;
	public static final Spot IN_TRAY = new Spot(-1, -1);
	
	protected Grid grid = new Grid();
	protected ArrayList<Piece> tray = new ArrayList<Piece>();
	protected ArrayList<Piece> red = new ArrayList<Piece>();
	protected ArrayList<Piece> blue = new ArrayList<Piece>();
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

	protected static TTEntry[] ttable = new TTEntry[2<<22];

        protected int[][] knownRank = new int[2][15];   // discovered ranks
        protected int[][] trayRank = new int[2][15];    // ranks in trays
	protected int[] piecesInTray = new int[2];

	static {
		Random rnd = new Random();
		for ( int k = 0; k < 2; k++)
		for ( int m = 0; m < 2; m++)
		for ( int c = RED; c <= BLUE; c++)
		for ( int r = 0; r < 15; r++)
		for ( int i = 12; i <= 120; i++) {
			long n = rnd.nextLong();

		// It is really silly that java does not have unsigned
		// so we lose a bit of precision.  hash has to
		// be positive because we use it to index ttable.

			if (n < 0)
				boardHash[k][m][c][r][i] = -n;
			else
				boardHash[k][m][c][r][i] = n;
		}
	}
	
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
		grid = new Grid(b.grid);
				
		tray.addAll(b.tray);
		undoList.addAll(b.undoList);
		setup = b.setup.clone();
		hash = b.hash;
		trayRank = b.trayRank.clone();
		knownRank = b.knownRank.clone();
		piecesInTray = b.piecesInTray.clone();
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
			fp.setMoved();
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
			genChaseRank(fp.getColor());
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

		for (TTEntry t : ttable)
			if (t != null)
				t.clear();
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
	//
	// Should "known" be included in the hash?  At first glance,
	// a known piece creates a different board position from
	// one with an unknown piece.  However, for a piece to become known,
	// (1) another piece must have attacked it and lost
	// (2) a nine moved more than one square.
	// If (1), the board position is different because of the missing
	// piece.  If (2) and an opponent nine moved more than the one
	// square, its apparent rank would also change.  The only case
	// worth distinguishing is an unknown AI Nine moving more than one
	// square.
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

	// stores the state prior to the move
	// the hash is the position prior to the move
	protected void moveHistory(Piece fp, Piece tp, int from, int to)
	{
		UndoMove um = new UndoMove(fp, tp, from, to, hash, 0);
		undoList.add(um);

		int index = (int)(hash % ttable.length);
		ttable[index] = new TTEntry(hash, from, to);

		// Acting rank is a historical property of the known
		// opponent piece ranks that a moved piece was adjacent to.
		//
		// Acting rank is calculated factually, but is of
		// questionable use in the heuristic because of bluffing.
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

		// Flee acting rank can be set implicitly if
		// the piece neglects to capture an adjacent opponent
		// piece and the immediate player move was not a capture,
		// a chase or the response to a chase.
		// However, it is difficult to determine whether the
		// opponent merely played some other move that requires
		// immediate response and planned to move the fleeing piece
		// later.  It is better to leave the flee rank at
		// NIL until the piece actually flees.
		// So this code was removed in version 9.1.
	}

	// Return true if the chase piece is obviously protected.
	//
	// Examples:
	// If unknown Blue moves towards Red 3 and Blue 8,
	// the piece is not protected.  Unknown Blue gains a chase
	// rank of Three.
	// R3 -- B?
	// -- B8 --
	//
	// If unknown Blue moves towards unknown Red and Blue 8,
	// the piece is not protected.  Unknown Blue gains a chase
	// rank of Unknown.
	// R? -- B?
	// -- B8 --
	//
	// If unknown Blue moves towards Red 3 and Blue 2,
	// the piece is protected.  No chase rank assigned.
	// R3 -- B?
	// -- B2 --
	//
	// If unknown Blue moves towards Red 3 and unknown Blue,
	// the piece could be protected, or it could be a lower
	// ranked piece.  This is a difficult case to evaluate.
	// R3 -- B?
	// -- B? --
	//
	// Below is the same case. Both the Blue Seven and Blue Spy
	// are unknown.  If one of the unknown Blue pieces moves
	// towards Red 1, was it the Spy or was it the Seven?  If
	// B7 moves towards Red 1, and if this code assigned a chase rank,
	// the chase rank would be One.  R1xB1 removes both pieces from
	// the board.  But because B7 is not B1, Blue Spy takes Red 1.
	// R1 -- BS
	// -- B7 --
	//
	// So this code considers the attacker to be protected.  This prevents
	// the piece from obtaining an erroneous chase rank.
	//
	// In the following example, all the pieces are unknown.  One of the
	// blue pieces moves towards Unknown Red.  It gains a chase
	// rank of Unknown.
	// R? -- B?
	// -- B? --
	//
	public void isProtectedChase(Piece chaser, Piece chased, int i)
	{
		// Either the chaser or the chased must be known
		// in order for protection to be determined.

		if (!chaser.isKnown() && !chased.isKnown())
			return;

		assert getPiece(i) == chased : "chased not at i?";
		Piece knownProtector = null;
		Piece unknownProtector = null;
		int open = 0;
		for (int d : dir) {
			int j = i + d;
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p == null) {
				open++;
				continue;
			}

			if (p.getColor() == chaser.getColor())
				continue;

			if (p.isKnown()
				&& p.getRank() != Rank.BOMB
				&& (knownProtector == null
					|| knownProtector.getRank().toInt() > p.getRank().toInt()))
				knownProtector = p;
			else if (unknownProtector != null) {
				// more than one unknown protector
				return;
			} else
				unknownProtector= p;
		}

		// if the chased piece is trapped, nothing can be
		// determined about the protection

		if (open == 0)
			return;

		if (unknownProtector != null) {

		// If both the chased piece and the protector are unknown,
		// and the chased piece approached the chaser
		// (recall that the chased piece is actually the chaser, so
		// an unknown piece is attacking a known piece.)
		// then it cannot be certain which piece is stronger.
		//
		// For example, if an unknown Blue approaches known Red Two,
		// it is not possible to tell which piece is stronger.
		// B? -- R2
		// -- B? --
		//
		// Furthermore, prior chase and flee acting rank
		// of unknown Blue becomes unreliable.  For example, if
		// unknown Blue had an acting chase rank of Three,
		// and then approaches Red Two in the example,
		// Red cannot assume that unknown Blue is a Three
		// and sit idly by.
		//
		// So set the acting rank chase to NIL and caveat emptor.
		// Also set the flee acting rank to NIL, because if
		// flee acting rank was Unknown, and stayed Unknown while
		// acting rank chase was set to NIL, the AI would assume
		// that the piece was a desirable target, because it
		// fled and did not approach any piece.
		//
		// However, if the chased piece neglects to attack
		// a known piece (thereby acquiring an acting rank flee),
		// it can be certain that the unknown protector
		// is the stronger piece.
		// For example, known Red Five approaches unknown Blue
		// (because it has a chase rank of Unknown from chasing
		// unknown pieces, suggesting that it is a high ranked
		// piece).  The unknown blue protector moves to protect
		// the unknown blue under attack.
		// -- B? R5
		// B? -- -- 

			if (!chased.isKnown()) {
				assert chaser.isKnown() : "unknown chaser?";
				Rank fleeRank = chased.getActingRankFlee();
				Rank chaserRank = chaser.getApparentRank();
				if (fleeRank != chaserRank) {

		// If the chased piece already has an acting rank chase,
		// and that rank is greater than the chaser piece rank,
		// reset the acting rank chase to NIL.

					Rank chasedRank = chased.getActingRankChase();
					if (chasedRank.toInt() > chaserRank.toInt()) {
						chased.setActingRankChaseEqual(Rank.NIL);
						chased.setActingRankFlee(Rank.NIL);
					}
					return;
				}
			}

		// strong unknown protector confirmed

			int r = chased.getRank().toInt();

		// One cannot be protected.
			if (r == 1)
				return;

			int attackerRank = 0;

			if (chaser.isKnown()) {
				attackerRank = chaser.getApparentRank().toInt();
				assert attackerRank < r : "known chaser must chase unknown or lower rank";
				r = attackerRank - 1;
			} else {

		// Guess that the attacker rank is one less than
		// the chased rank (or possibly less, if not unknown)

				attackerRank = r - 1;
				while (attackerRank > 0 && unknownRankAtLarge(chaser.getColor(), attackerRank) == 0)
					attackerRank--;

				if (attackerRank == 0)
					return;

		// Guess that the protector rank is one less than
		// the attacker rank

				r = attackerRank - 1;
			}
			while (r > 0 && unknownRankAtLarge(chased.getColor(), r) == 0)
				r--;

		// known piece is protector

			if (knownProtector != null
				&& knownProtector.getRank().toInt() <= r)
				return;

		// Only a Spy can protect a piece attacked by a One.

			Rank rank;
			if (r == 0) {
				if (attackerRank != 1)
					return;
				rank = Rank.SPY;
			} else
				rank = Rank.toRank(r);

		// Once the AI has indirectly identified the opponent Spy
		// (by attacking an opponent Two that had a protector)
		// the AI sticks with the guess, even if the suspected
		// Spy later tries to bluff.  This is because the Two is
		// so valuable, that it is unlikely, although possible,
		// that the opponent would risk the Two in a high stakes
		// bluff, protected by some other piece.
		// 
			Rank arank = unknownProtector.getActingRankChase();
			if (arank == Rank.SPY)
				rank = Rank.SPY;

			if (arank == Rank.NIL || arank.toInt() > r)
				unknownProtector.setActingRankChaseLess(rank);
		}

		// Set direct chase rank

		// Set direct chase rank to the piece that just moved.
		// Do not set direct chase rank to any other piece.
		//
		// For example:
		// B? -- R3
		// Red Three approaches Unknown Blue.  Blue makes some
		// other move.  So after Blue makes some other move,
		// Unknown Blue should not acquire a chase rank because
		// Unknown Blue did not approach Red Three.
		//
		// Why would Blue make some other move?  It would appear
		// that Blue should take Red Three if Blue is lower ranked,
		// flee if Blue is higher ranked, or stay put, attack, or
		// flee if even ranked?
		//
		// But there are reasons why Blue may not move.
		// 1. Unknown Blue is cornered.
		//    (this case is handled above when open == 0)
		// 2. Unknown Blue will lose the piece anyway because fleeing
		//    would lead Red Three to fork a more valuable piece.
		// 3. Unknown Blue is currently forked with another piece.
		// 4. Blue is a human player and just missed the fact
		//    that its piece is under attack.
		// 5. It is protected.
		//
		// Case #3 occurs frequently.  For example,
		// B? --
		// -- R3
		// B4
		// If Red Three forks unknown Blue and Blue Four, Blue Four
		// flees, unknown Blue should not acquire a chase rank.
		//
		// TBD: An opponent piece approaches a player piece
		// that is protected by a low piece.
		// The opponent piece is also protected, so it does not gain
		// a chase rank.  But what if the opponent protector then moves?
		// In this case, the opponent piece should acquire a chase
		// rank, because it will likely attack the player
		// piece if it become unprotected.
		// To solve this, we need to add an actingRankApproach
		// and rely on this instead of last move.

		Move m = getLastMove();
		if (m.getPiece() != chased)
			return;

		if (chased.isKnown()
			|| unknownProtector != null
			|| (knownProtector != null
				&& knownProtector.getApparentRank().toInt() < chaser.getApparentRank().toInt()))
			return;

		// If an unknown piece has been fleeing,
		// and gets trapped in some way, it may just give up.
		// So do not assign a chase rank equal to the flee rank.

		if (chased.getActingRankFlee() == chaser.getApparentRank())
			return;

		// Chasing an unknown sets the chase rank to UNKNOWN.
		// Once set, the chase rank
		// is never changed again because if a piece chases an Unknown,
		// it could be a bluffing high rank that will chase anything or
		// less likely, but possible, it could be an invincible
		// piece.  Its real identity can be determined only
		// through attack.

		Rank arank = chased.getActingRankChase();
		if (arank == Rank.UNKNOWN)
			return;

		if (chaser.getApparentRank() == Rank.UNKNOWN
			|| arank == Rank.NIL 
			|| arank.toInt() > chaser.getApparentRank().toInt())
			chased.setActingRankChaseEqual(chaser.getApparentRank());
	}

	boolean forked(int i)
	{
		int color = getPiece(i).getColor();
		for (int d : dir) {
			int j = i + d;
			Piece p = getPiece(j);
			if (p == null)
				continue;
			if (p.isKnown() && p.getColor() != color)
				return true;
		}
		return false;
	}

	// Chase acting rank is set implicitly if
	// a known piece under attack does not move
	// and it has only one possible unknown defender.
	//
	// (This is how the ai can guess the spy: if the
	// a known Two is attacked by an ai piece, and it
	// does not move or another piece moves to protect it)
	//
	// Note that chase rank is set after the player move
	// but flee rank is set before the player move.

	void genChaseRank(int turn)
	{
               for (int c = RED; c <= BLUE; c++) {
                        piecesInTray[c] = 0;
                        for (int j=0;j<15;j++) {
                                trayRank[c][j] = 0;
                                knownRank[c][j] = 0;
			}
		}

		// add in the tray pieces to trayRank
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			trayRank[p.getColor()][r-1]++;
			piecesInTray[p.getColor()]++;
		}

		for ( int i = 12; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			if (p.isKnown())
				knownRank[p.getColor()][p.getRank().toInt()-1]++;
		}

		for ( int i = 12; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece chased = getPiece(i);

		// Start out by finding a chased piece of the opposite color
		// (This can even be the One, because if a chaser piece
		// approaches the One and has a protector, then we know
		// the protector is a Spy (or acting like a Spy).
	
			if (chased == null
				|| chased.getColor() == turn
				|| chased.getApparentRank() == Rank.FLAG
				|| chased.getApparentRank() == Rank.BOMB)
				continue;

		// Then find a chaser piece of the same color
		// adjacent to the chaser (rank does not matter
		// at this point).

			for (int d : dir) {
				int j = i + d;
				if (!isValid(j))
					continue;
				Piece chaser = getPiece(j);
				if (chaser == null
					|| chaser.getColor() != turn
					|| !chaser.hasMoved())
					continue;

		// If the chased piece (moved or unmoved) is not known,
		// and the chaser had no prior chase rank,
		// the piece gains a permanent chase rank of Unknown.
		// This is because a piece that willingly moves next
		// to an unknown is willing to sacrifice itself
		// (and so the AI guesses that the piece is probably
		// a high-ranked piece).
		//
		// The difficulty is determining if the piece willingly
		// moved next to an unknown or was cornered.  So if
		// the piece already has a chase rank, it is retained
		// if it approaches an unknown.
		//
		// If a protected unknown chaser attacking an unknown
		// chased piece also forks a known piece, it could
		// be the unknown attacker is targeting the known piece
		// and not the unknown, so no rank is assigned.  For example,
		// -- R3 --
		// B? -- R?
		// B? B? --
		// Unknown Blue (a One or Two) moves towards Red Three.
		// Blue doesn't care that R?xB? reveals its rank
		// because B?xR3 wins.
		//
		// TBD: However, if the unknown chaser is not protected,
		// then what can happen?
		// -- R3 --
		// B? -- R?
		// Unknown Blue forks Red Three and Unknown Red.
		// Is Unknown Blue a Two, risking R?xB? if R? is a One?
		// Is Blue hoping that Red thinks that Unknown Blue is
		// a high ranked piece, so that Red plays R3xB?, when
		// in reality Unknown Blue is a Two, and then Blue
		// moves its Two away, winning the Red Three?
		// I am thinking of coding Blue as an Unknown chaser, but
		// this is risky.

				if (!chased.isKnown()) {
					if (!chaser.isKnown()) {
						if (!forked(j)) {
							Rank chaserRank = chaser.getActingRankChase();
							if (chaserRank == Rank.NIL)
								chaser.setActingRankChaseEqual(Rank.UNKNOWN);
						}
						continue;
					}
				}

		// If the chaser is a lower or equal rank to the chased piece,
		// there is nothing more that can be determined.
		//
		// However, if the chased piece is Unknown, 
		// then there is more that can be determined in this case
		// if the chaser is not invincible, because if the chaser
		// believes that the Unknown is a low ranked piece, then
		// the protector must even be lower.  For example,
		// R? -- B?
		// -- B2 --
		// Blue Two moves between Unknown Red and Unknown Blue.
		// If Blue thinks that Unknown Red could be Red One
		// and Unknown Blue is Blue Spy, then Red could guess that
		// Unknown Blue *is* Blue Spy.  But if Blue does not think
		// that Unknown Red is Red One, this is simply an attacking
		// move and nothing can be determined about Unknown Blue.
		// 
		// One way to determine whether Blue believes Unknown Red
		// is a superior piece is if Blue neglects to attack
		// Unknown Red.  For example,
		// R? B2 -- B?
		// If Blue Two does not attack Unknown Red, and instead
		// moves unknown Blue towards Blue Two, then Red is signaling
		// that it believes Unknown Red is a superior piece.
		//
		// So if the last move was not the chaser, then it can
		// be assumed that the player believes the unknown chased piece
		// is a superior piece, and so the AI assumes that its
		// protector is even more superior.

				Rank chasedRank = chased.getApparentRank();
				Rank chaserRank = chaser.getApparentRank();

				if (chasedRank == Rank.UNKNOWN) {
					Move m = getLastMove();
					if (m.getPiece() == chaser)
						continue;
				} else if (chaserRank.toInt() <= chasedRank.toInt()
					|| chaserRank == Rank.FLAG
					|| chaserRank == Rank.BOMB)
					continue;

		// Now this is the tricky part.
		// The roles of chaser and chased are swapped
		// in isProtectedChase(), becaused the chased piece
		// tries to determine if it can win the chaser piece
		// based on its protection.  This can result in actingRankChase
		// assignment to a piece that is protecting the actual
		// attacker rather than the attacker.
		// For example:
		// B3 R4 -- R2
		//
		// Unknown Red Two moves towards known Red Four.
		// Red is the chaser because it had the move.
		// The same result occurs in the example below, where
		// Red Four moves between Blue Three and unknown Red Two:
		// B3 -- R2
		// -- R4 --
		// 
		// The usual case is usually simpler, such when unknown Red
		// approaches Blue Three in the example below.  Then
		// unknown Red gets an actingRankChase because it has
		// no protection.
		// B3 -- R?
		// 
		// In the following example, Red Four is known but all
		// other pieces are unknown.  Unknown chase rank is set because
		// it is unclear whether the unknown Blue piece is chasing
		// the Four or just bluffing, trying to get past to attack
		// an unknown piece.
		// RB RS
		// R4 --
		// -- B?
		//
		// This also can happen when an Eight bluffs to get at a flag
		// bomb.  The example below is a common bluff because Blue knows
		// that Red Five has to attack its unknown Blue piece
		// (if Blue has any unknown Eights), even if Blue has
		// unknown lower ranks (e.g. Blue Four).
		// RB RF
		// -- RB
		// R5 --
		// -- B?
		//

				isProtectedChase(chased, chaser, j);
			} // d
		} // i
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
			fp.setMoved();
			if (Math.abs(m.getToX() - m.getFromX()) > 1 || 
				Math.abs(m.getToY() - m.getFromY()) > 1) {
				//scouts reveal themselves by moving more than one place
				fp.setShown(true);
				fp.setRank(Rank.NINE);
				fp.makeKnown();
			}
			genChaseRank(fp.getColor());
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
		Collections.sort(tray,new Comparator<Piece>(){
				     public int compare(Piece p1,Piece p2){
					return p1.getRank().toInt() - p2.getRank().toInt();
				     }});
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
			if (prev == null)
				return false;
			BMove prevprev = undoList.get(size-6);
			if (prevprev == null)
				return false;
			return prevprev.equals(prev)
				&& m.getFrom() == prev.getTo()
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

	// Because of limited search depth, the completion of
	// a two squares ending may well be beyond the horizon of the search.
	// However, a two squares ending can be predicted earlier if the
	// opponent piece chased the defender from a non-adjacent
	// square.  For example,
	// xx xx xx	xx xx xx	xx xx xx
	// -- R3 R?	-- R3 R?	-- R3 R?
	// -- -- --	-- -- B2	B2 -- --
	// -- B2 --	-- -- --	-- -- --
	//
	// Blue Two has the move and approaches Red Three
	// Positions 1 & 2 can lead to the two squares rule but
	// position 3 does not.  Thus, move generation can eliminate
	// the move for R3 back to its original spot if the
	// from-square of its chaser did not begin adjacent to the
	// current from-square of the chased piece.
	//
	// Hence, the effect of the two squares rule can be felt
	// at ply 3 rather than played out to its entirety at ply 7.
	//
	// If a two squares ending is predicted, the AI will see
	// the inevitable loss of its piece, and may play some other 
	// move, because it sees that it is pointless to move its
	// chased piece away.
	//
	// This rule also works for attacks by the AI on opponent
	// pieces, allowing it to see the effect of the two squares
	// rule much earlier in the search tree.
	//
	// If there are three open squares, the code must allow
	// the move.  For example,
	// xx xx xx xx
	// -- R3 -- R?
	// -- -- -- --
	// -- B2 -- --
	//
	// Blue Two has the move and approaches Red Three.
	// Position 1 could be seen as a false Two Squares ending.

	public boolean isPossibleTwoSquares(BMove m)
	{
		UndoMove m2 = getLastMove(2);
		if (m2 == null)
			return false;

		// not back to the same square?
		if (m2.getFrom() != m.getTo())
			return false;

		// is a capture?
		if (getPiece(m.getTo()) != null)
			return false;

		// not the same piece?
		if (m2.getPiece() != getPiece(m.getFrom()))
			return false;

		// not an adjacent move (i.e., nine far move)
		if (!Grid.isAdjacent(m.getFrom(), m.getTo()))
			return false;

		// test for three squares (which is legal)
		Piece p = getPiece(m.getTo() + (m.getTo() - m.getFrom()));
		if (p == null || p.getColor() != m2.getPiece().getColor())
			return false;

		UndoMove oppmove3 = getLastMove(3);
		if (oppmove3 == null)
			return false;

		// Did chase piece start non-adjacent to chaser?
		// If so, this is a possible two squares ending.
		return !Grid.isAdjacent(oppmove3.getFrom(), m.getFrom());
	}

	public boolean isChased(BMove m)
	{
		UndoMove oppmove = getLastMove(1);
		if (oppmove == null)
			return false;
		return Grid.isAdjacent(oppmove.getTo(),  m.getFrom());
	}

	// chasing moves back to the square where the chasing piece
	// came from in the directly preceding turn are always allowed
	// as long as this does not violate the
	// Two-Squares Rule / Five-Moves-on-Two-Squares Rule.
	public boolean isTwoSquaresChase(BMove m)
	{
		BMove m2 = getLastMove(2);
		if (m2 == null)
			return false;

		// not back to square where the chasing piece came from?
		if (m2.getFrom() != m.getTo())
			return false;

		// not a chasing move?
		BMove oppmove = getLastMove(1);
		if (oppmove == null)
			return false;

		if (!Grid.isAdjacent(oppmove.getTo(),  m.getTo()))
			return false;

		// Will the AI eventually be blocked by Two Squares?
		//
		// B3 --	B3 --		-- B3		-- B3
		// -- R2	R2 --		R2 --		-- R2
		// (A)		  (1)		  (B)		  (2)
		// (C)		  (3)		  (D)		  (4)
		//
		// (A) is the starting position.
		// The AI is allowed to make moves 1 and 2.
		// However, if position C is reached, the AI is not
		// not allowed to make move 3, although this is
		// a legal move.  This reduces 
		// pointless 3 move sequences to pointless 2 move
		// sequences.
		//
		// If A was indeed the starting position,
		// this function will return false when position C is reached.
		// isRepeatedPosition() will prevent the AI from
		// considering the move.
		// If position (A) was not the starting position,
		// this function returns true
		// because the moves (1) and (3) are not equal.
		// The AI is allowed to proceed, because
		// it will be the opponent whose will repeat first.
		//
		// Hence, position D can only be reached
		// if the opponent is the repeater.  So if moves 1 and 3
		// are equal, as well as the proposed move 4 equal to move 2,
		// this function returns true, which
		// then AI allows to proceed,
		// by avoiding the isRepeatedPosition() check in the AI.

		BMove m4 = getLastMove(4);
		if (m4 == null)
			return false;

		BMove m6 = getLastMove(6);
		if (m6 == null)
			return false;

		if (m.equals(m4) && !m2.equals(m6))
			return false;

		return true;
	}

	// This implements the More-Squares Rule during tree search,
	// but is more restrictive because it also eliminates
	// all repetitive moves,
	// non-chase moves as well as chase moves.
	// It is used during move generation to forward prune
	// repeated moves.
	//
	// Note that a player chasing an opponent piece
	// can reach a previous position
	// and still abide by More-Squares Rule, as long as the
	// moves are not continuous.
	//
	public boolean isRepeatedPosition()
	{
		// Because isRepeatedPosition() is more restrictive
		// than More Squares, the AI
		// does not expect the opponent to abide
		// by this rule as coded.

		int index = (int)(hash % ttable.length);
		TTEntry entry = ttable[index];
		if (entry == null || entry.key != hash)
			return false;

		// Position has been reached before

		return true;
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

		int index = (int)(hash % ttable.length);
		TTEntry entry = ttable[index];
		if (entry != null)
			entry.clear();
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

	public int unknownRankAtLarge(int color, int r)
	{
		return Rank.getRanks(Rank.toRank(r))
			- trayRank[color][r-1]
			- knownRank[color][r-1];
	}

	public int unknownRankAtLarge(int color, Rank rank)
	{
		return unknownRankAtLarge(color, rank.toInt());
	}

	public int knownRankAtLarge(int color, int r)
	{
		return knownRank[color][r-1];
	}

	public int rankAtLarge(int color, int rank)
	{
		return (Rank.getRanks(Rank.toRank(rank)) - trayRank[color][rank-1]);
	}

	public int rankAtLarge(int color, Rank rank)
	{
		return rankAtLarge(color, rank.toInt());
	}
}



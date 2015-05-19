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
import java.util.HashMap;
import java.lang.Long;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;


//
// The board class contains all the factual information
// about the current and historical position
//
public class Board
{
        public ArrayList<UndoMove> undoList = new ArrayList<UndoMove>();
	public static final int RED  = 0;
	public static final int BLUE = 1;
	public static int bturn = RED;
	public static final Spot IN_TRAY = new Spot(-1, -1);
	
	public Grid grid = new Grid();
	protected ArrayList<Piece> tray = new ArrayList<Piece>();
	protected ArrayList<Piece> red = new ArrayList<Piece>();
	protected ArrayList<Piece> blue = new ArrayList<Piece>();
	// setup contains the original locations of Pieces
	// as they are discovered.
	// this is used to guess flags and other bombs
	// TBD: try to guess the opponents entire setup by
	// correllating against all setups in the database
	protected Piece[] setup = new Piece[121];
	protected static int[] dir = { -11, -1,  1, 11 };
	protected static long[][][][][] boardHash = new long[2][32][2][15][121];

	protected class BoardHistory {
		public long hash;
		protected HashMap<Long, UndoMove>  hashmap = new HashMap<Long, UndoMove>();
		public void clear() { hashmap.clear(); hash = 0; }
		public void put(UndoMove um) { hashmap.put(hash, um); }
		public UndoMove get() { return hashmap.get(hash); }
		public void remove() { hashmap.remove(hash); }
	}
	protected static BoardHistory[] boardHistory = new BoardHistory[2];

        protected int[][] knownRank = new int[2][15];   // discovered ranks
        protected int[][] trayRank = new int[2][15];    // ranks in trays
	protected int[][] suspectedRank = new int[2][15];	// guessed ranks
	protected int[] piecesInTray = new int[2];
	protected int lowestUnknownExpendableRank;

	static {
		Random rnd = new Random();

	// An identical position differs depending on whose turn it is.
	// For example,
	// R8 R9	-- B5
	// -- B5	R8 --
	// (A)		 (B)
	//
	// Red has the move and can reach position B
	// by R9xB5, B5 moves up, R8 moves down.  Blue now has the move.
	//
	// (B) can also be reached if Red plays R8 down, Blue plays B5xR9.
	// Red now has the move.
	//

		for ( int c = RED; c <= BLUE; c++)
		for ( int k = 0; k < 32; k++)
		for ( int m = 0; m < 2; m++)
		for ( int r = 0; r < 15; r++)
		for ( int i = 12; i <= 120; i++) {
			long n = rnd.nextLong();

		// It is really silly that java does not have unsigned
		// so we lose a bit of precision.  hash has to
		// be positive because we use it to index ttable.

			if (n < 0)
				boardHash[c][k][m][r][i] = -n;
			else
				boardHash[c][k][m][r][i] = n;
		}
	}
	
	public Board()
	{
		//create pieces
		red.add(new Piece(RED, Rank.FLAG));
		red.add(new Piece(RED, Rank.SPY));
		red.add(new Piece(RED, Rank.ONE));
		red.add(new Piece(RED, Rank.TWO));
		for (int j=0;j<2;j++)
			red.add(new Piece(RED, Rank.THREE));
		for (int j=0;j<3;j++)
			red.add(new Piece(RED, Rank.FOUR));
		for (int j=0;j<4;j++)
			red.add(new Piece(RED, Rank.FIVE));
		for (int j=0;j<4;j++)
			red.add(new Piece(RED, Rank.SIX));
		for (int j=0;j<4;j++)
			red.add(new Piece(RED, Rank.SEVEN));
		for (int j=0;j<5;j++)
			red.add(new Piece(RED, Rank.EIGHT));
		for (int j=0;j<8;j++)
			red.add(new Piece(RED, Rank.NINE));
		for (int j=0;j<6;j++)
			red.add(new Piece(RED, Rank.BOMB));

		//create pieces
		blue.add(new Piece(BLUE, Rank.FLAG));
		blue.add(new Piece(BLUE, Rank.SPY));
		blue.add(new Piece(BLUE, Rank.ONE));
		blue.add(new Piece(BLUE, Rank.TWO));
		for (int j=0;j<2;j++)
			blue.add(new Piece(BLUE, Rank.THREE));
		for (int j=0;j<3;j++)
			blue.add(new Piece(BLUE, Rank.FOUR));
		for (int j=0;j<4;j++)
			blue.add(new Piece(BLUE, Rank.FIVE));
		for (int j=0;j<4;j++)
			blue.add(new Piece(BLUE, Rank.SIX));
		for (int j=0;j<4;j++)
			blue.add(new Piece(BLUE, Rank.SEVEN));
		for (int j=0;j<5;j++)
			blue.add(new Piece(BLUE, Rank.EIGHT));
		for (int j=0;j<8;j++)
			blue.add(new Piece(BLUE, Rank.NINE));
		for (int j=0;j<6;j++)
			blue.add(new Piece(BLUE, Rank.BOMB));

		tray.addAll(red);
		tray.addAll(blue);

		Collections.sort(tray);
		boardHistory[0] = new BoardHistory();
		boardHistory[1] = new BoardHistory();
	}

	public Board(Board b)
	{
		grid = new Grid(b.grid);
				
		tray.addAll(b.tray);
		undoList.addAll(b.undoList);
		setup = b.setup.clone();
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
			setup[Grid.getIndex(s.getX(), s.getY())] = p;
			return true;
		}
		return false;
	}

	// TRUE if valid attack
	public boolean attack(Move m)
	{
		if (validAttack(m))
		{
			Piece fp = getPiece(m.getFrom());
			Piece tp = getPiece(m.getTo());
			moveHistory(fp, tp, m.getMove());

			fp.setShown(true);
			fp.makeKnown();
			tp.makeKnown();
			fp.setMoved();
			if (!Settings.bNoShowDefender || fp.getRank() == Rank.NINE) {
				tp.setShown(true);
			}

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
			setup[i] = null;

		bturn = 0;
		boardHistory[0].clear();
		boardHistory[1].clear();
	}
	
	public Piece getPiece(int x, int y)
	{
		return grid.getPiece(x, y);
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
	// Unknown pieces (ai or opponent) are not all identical.
	// Hence, if two unknown pieces swap positions, the board is different,
	// because the AI bases rank predictions based on setup
	// and the history of the piece interactions.
	// Presently the AI does not consolidate all its information
	// (i.e. actingranks) into a single predicted rank, so the
	// hash cannot be relied on for unknown ranks.
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
/*
	public void rehash(long v)
	{
		if (v < 0)
			v = -v;
		hash ^= v;
	}
*/

	// A board position (and hash) reflects all that each player
	// knows.  The AI knows its piece ranks and has information
	// it keeps about the opponent ranks.  The opponent sees
	// only what is known and what information can be gathered.
	//
	// Note that the hash is unreliable for unknown pieces
	// (see above).

	static public long hashPiece(int turn, Piece p, int i)
	{
		if (turn == Settings.topColor) {
			return boardHash
				[p.getColor()]
				[p.getStateFlags()]
				[p.hasMoved() ? 1 : 0]
				[p.getRank().toInt()-1]
				[i];
		} else {
			Rank rank = p.getRank();
			if (p.getColor() == Settings.topColor
				&& !p.isKnown())
				rank = Rank.UNKNOWN;
			return boardHash
				[p.getColor()]
				[p.getStateFlags()]
				[p.hasMoved() ? 1 : 0]
				[rank.toInt()-1]
				[i];
		}
	}

	public void rehash(Piece p, int i)
	{
		boardHistory[Settings.topColor].hash ^= hashPiece(Settings.topColor, p, i);
		boardHistory[Settings.bottomColor].hash ^= hashPiece(Settings.bottomColor, p, i);
	}
	
	public void setPiece(Piece p, int i)
	{
		Piece bp = getPiece(i);
		if (bp != null) {
			rehash(bp, i);
		}

		if (p != null) {
			grid.setPiece(i, p);
			rehash(p, i);
		} else
			grid.clearPiece(i);
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
	public boolean isProtectedFlee(Piece chasePiece, Piece chasedPiece, int chaser)
	{
		for (int d : dir) {
			int j = chaser + d;
			if (!Grid.isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p != null
				&& p.getColor() == chasePiece.getColor()
				&& (p.getApparentRank() == Rank.UNKNOWN
					|| p.getApparentRank().toInt() < chasedPiece.getApparentRank().toInt())) {
				return true;
			}
		}
		return false;
	}

	// stores the state prior to the move
	// the hash is the position prior to the move
	protected void moveHistory(Piece fp, Piece tp, int m)
	{
		UndoMove um = new UndoMove(fp, tp, m, boardHistory[bturn].hash, 0);
		undoList.add(um);

		boardHistory[bturn].put(um);
		bturn = 1 - bturn;

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
		int from = Move.unpackFrom(m);
		int chaserank = 99;
		for (int d : dir) {
			int chaser = from + d;
			if (!Grid.isValid(chaser))
				continue;
			Piece chasePiece = getPiece(chaser);
			if (chasePiece != null
				&& chasePiece.getColor() != fp.getColor()) {
				// chase confirmed
				Rank rank = chasePiece.getApparentRank();
				if (rank == Rank.BOMB)
					continue;

		// New in version 9.5
		// If the opponent piece flees, it can delay the setting
		// of implicit flee rank if the piece
		// is unknown or of equal or lower rank than a piece
		// under attack elsewhere on the board.

				chaserank = fp.getApparentRank().toInt();

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

				fp.setActingRankFlee(rank);
			}
		}

		// Flee acting rank can be set implicitly if a player
		// piece neglects to capture an adjacent opponent
		// piece and the immediate player move was not a capture,
		// a chase or the response to a chase.
		//
		// However, it is difficult to determine whether the
		// player merely played some other move that requires
		// immediate response and planned to move the fleeing piece
		// later. So in version 9.1, this code was entirely removed,
		// with the thought that was better to leave the flee rank at
		// NIL until the piece actually flees.
		//
		// But the removal of the code meant that if an opponent piece
		// passes by an unknown player piece, and the player
		// piece neglects to attack, the flee rank would not be set,
		// and so the player would still
		// think that the piece would still be useful for
		// chasing the opponent piece away.  But of course
		// the opponent would call the bluff, and attack the
		// moved piece, because now the opponent knows it is
		// a weaker piece.
		//
		// So the code was restored for version 9.5, adding
		// additional checks to guess if the last move played
		// should delay the setting of flee rank.  This is definitely
		// tricky, and undoubtably does not handle all cases,
		// but this code is essential to prevent an opponent from using
		// low ranked pieces to probe the player's ranks and
		// baiting them to move.

		// New code in Version 9.5.
		// Check for a possible delay before setting the flee rank.
		// - if the player chases an unknown or an equal or
		// lower ranked piece
		int to = Move.unpackTo(m);
		for (int d : dir) {
			int j = to + d;
			if (!Grid.isValid(j))
				continue;
			Piece op = getPiece(j);
			if (op == null
				|| op.getColor() == fp.getColor())
				continue;
			Rank rank = op.getApparentRank();
			if (rank.toInt() <  chaserank)
				chaserank = rank.toInt();
		}

		// New in version 9.5
		// If an unknown piece flees or is chased,
		// delay the setting of implicit flee rank.
		if (chaserank == Rank.UNKNOWN.toInt())
			return;

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
				if (!Grid.isValid(j))
					continue;
				Piece op = getPiece(j);
				if (op == null
					|| op.getColor() == fp.getColor()
					|| isProtectedFlee(op, fleeTp, j)
					|| tp != null)	// TBD: test rank
					continue;

				Rank rank = op.getApparentRank();
				if (rank == Rank.BOMB
					|| rank.toInt() >= chaserank)	// new in version 9.5
					continue;

				fleeTp.setActingRankFlee(rank);
			}
		}
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
			if (!Grid.isValid(j))
				continue;
			Piece p = getPiece(j);

		// If an adjacent square is open, assume that the chased piece
		// is not cornered.  This assumption is usually valid,
		// unless the move to the open square is prevented by
		// the Two Squares rule or guarded by another enemy piece.

			if (p == null) {
				UndoMove um3 = getLastMove(3);
				UndoMove um7 = getLastMove(7);

		// oversimplication of a Two Squares check

				if (um3 != null
					&& um7 != null
					&& um3.getMove() == um7.getMove())
					continue;

		// TBD: check if the open square is guarded

		// assume the move to the open square is a decent flee move

				open++;
				continue;
			}

			if (p.getColor() == chaser.getColor())
				continue;

			if (p.isKnown()
				&& p.getApparentRank() != Rank.BOMB
				&& (knownProtector == null
					|| knownProtector.getRank().toInt() > p.getRank().toInt()))
				knownProtector = p;
			else if (unknownProtector != null) {
				// more than one unknown protector
				return;
			} else
				unknownProtector= p;
		}

		if (unknownProtector != null) {

		// If the chased piece is cornered, nothing should be
		// determined about the protection.  This is where
		// the AI deviates from worst case analysis, because
		// it guesses that cornered pieces are not protected.
		//
		// Of course, the piece could have a protector as
		// well as being cornered, but there is a good chance
		// that the piece does not have a protector and
		// this is a chance that the AI must take if it wants
		// to win material.
		//
		// Even if the last move was a move to adjacent to
		// the chased piece and the piece is cornered, the
		// approaching protector is probably bluffing.  Or
		// at least the AI thinks so.

			if (open == 0)
				return;

		// If a chased piece was forked, the player had to choose
		// a piece to be left subject to attack.  Thus if the piece
		// happens to have an adjacent unknown piece which could
		// be construed as a protector, do not set the chase rank.
		// For example,
		// xx xx -- --
		// -- B4 R3 --
		// -- B? B5 --
		// -- -- -- --
		// Red Three has forked Blue Four and Blue Five.  Unknown
		// Blue is a suspected isolated bomb.  Blue Four moves
		// left, leaving Blue Five subject to attack.  Do not
		// set a chase rank on unknown Blue.
		//
		// TBD: Note that the following check fails if Blue did not
		// move one of the forked pieces.
		// For example,
		// xx xx -- --
		// -- B4 R3 --
		// -- B? B4 --
		// -- -- -- --
		// Blue knows it will lose a Four, so neither flees.
		// The AI assigns a suspected chase rank to unknown Blue,
		// which happens to be an isolated bomb.

			Move m = getLastMove();
			if (Grid.isAdjacent(m.getFrom(), chaser.getIndex()))
				return;

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
				Rank fleeRank = chased.getActingRankFleeLow();
				Rank chaserRank = chaser.getApparentRank();
				if (fleeRank != chaserRank) {

		// If the chased piece already has an acting rank chase,
		// and that rank is greater than the chaser piece rank,
		// reset the acting rank chase to NIL.

					Rank chasedRank = chased.getActingRankChase();
					if (chasedRank.toInt() > chaserRank.toInt()) {
						chased.setActingRankChaseEqual(Rank.NIL);
						chased.clearActingRankFlee();
					}
					return;
				}
			}

		// If the chaser is high ranked (or unknown) and the chased
		// is unknown, the chased piece may have neglected to attack
		// the chaser to retain stealth, and the chaser did not
		// flee to protect other pieces.  For example,
		// -- R7 -- B6
		// -- B2 BS --
		// B? -- -- B?
		// All pieces except for Red Seven are unknown.
		// Red Seven has approached unknown Blue Two.
		// The neighboring Blue unknown is Blue Spy.  Rather than
		// flee with Blue Two, unknown Blue Six to the right of
		// Red Seven moves left.  Blue is hoping that Red will
		// either flee or attack unknown Blue Six to the right rather
		// attacking its Blue Two.  Had Blue Two fled, it would
		// signal to Red that the piece was a desirable target
		// and Red Seven would chase, thus forcing Blue Two to
		// attack to protect Blue Spy.  It would then be obvious
		// to Red than the neighboring piece was also a valuable
		// target.  (This is definitely advanced game play).

			if (chaser.getApparentRank().toInt() >= 5)
				return;

		// strong unknown protector confirmed

			int r = chased.getApparentRank().toInt();

		// One cannot be protected.
			if (r == 1)
				return;

			int attackerRank = 0;

			if (chaser.isKnown()) {
				attackerRank = chaser.getApparentRank().toInt();
				assert attackerRank < r : "known chaser " + attackerRank + "  must chase unknown or lower rank, not " + chased.getApparentRank();
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

		// Note: Note that prior assignment of chase rank
		// consumes unassigned ranks, so when there
		// are no more unassigned unknown lower ranks, the AI
		// assumes that the protector is bluffing.  Otherwise,
		// the opponent could continually protect its pieces
		// through bluffing with unknown protectors.
		// So now the AI initially believes the opponent, but
		// after all plausible protectors have been consumed,
		// the AI knows that the opponent is a bluffing opponent
		// so the AI stops assigning chase rank in this manner.
		// This can lead to material gain when the AI has a
		// locally invincible piece based on a prior assignment
		// of chase rank, and then attacks opponent unknowns
		// protected by other unknowns.

			while (r > 0 && unknownNotSuspectedRankAtLarge(chased.getColor(), r) == 0)
				r--;

		// known piece is protector

			if (knownProtector != null
				&& knownProtector.getApparentRank().toInt() <= r)
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
		// Spy later tries to bluff by protecting some other piece.
		// This is because the Two is so valuable,
		// that it is unlikely, although possible,
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

		if (chased.getActingRankFleeLow() == chaser.getApparentRank())
			return;

		Rank arank = chased.getActingRankChase();

		// An unknown chase rank, once set, is never changed again.
		// This prevents being duped by random bluffing, where a high
		// rank piece chases all pieces, low ranks
		// as well as unknowns.  The AI believes
		// that unknown pieces that chase AI unknowns are probably
		// high ranked pieces and can be attacked by a Five or less.
		//
		// Prior to version 9.3, chasing an unknown always
		// set the chase rank to UNKNOWN.
		// The problem was that once the AI successfully
		// trapped a low suspected rank that was forced to move
		// pass AI unknowns, it lost its suspected rank
		// and acquired the UNKNOWN rank.
		// This led the AI to approach the now unknown opponent
		// piece and thus lose its piece.
		//
		// In version 9.3, if a piece has a chase rank
		// of 4 or lower, the low rank is retained if the piece
		// chases an unknown.  The side-effect is that the AI is duped
		// by an opponent piece that chases a low ranked AI piece
		// and then chases an unknown.  This bluff results in
		// the AI wasting a piece trying to discover the true rank
		// of the bluffing piece.  If the AI piece is not able
		// to attack the bluffing piece, it can also lead to the
		// AI miscalculation of other suspected ranks on the board,
		// leading to a loss elsewhere.
		//
		// Note that if a piece has a chase rank of Five
		// (suspected rank of Four) and it then approaches an
		// unknown, the chase rank changes to UNKNOWN, which
		// usually results in a suspected rank of Five.
		// Thus, if the piece is actually a trapped Four,
		// the AI could approach it with a Five and lose
		// (but it probably won't approach, because 5x5 is even).
		// But much more often the piece is a Five, so that
		// chasing an Unknown is a sure sign that the piece
		// is not a Four but a Five, so the chance of a small loss
		// (4x5) is a necessary evil.
		//
		// There is no way around this.  Bluffing makes assignment
		// of suspected ranks a challenge.

		if (arank == Rank.UNKNOWN)
		 	return;

		if (arank == Rank.NIL 
			|| (chaser.getApparentRank() == Rank.UNKNOWN
				&& arank.toInt() >= 5)
			|| arank.toInt() > chaser.getApparentRank().toInt())
			chased.setActingRankChaseEqual(chaser.getApparentRank());
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
				suspectedRank[c][j] = 0;
			}
		}

		// Indirect chase rank now depends on unassigned
		// suspected ranks because of bluffing.
		genSuspectedRank();

		// add in the tray pieces to trayRank
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			trayRank[p.getColor()][r-1]++;
			piecesInTray[p.getColor()]++;
		}

		for ( int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			if (p.isKnown())
				knownRank[p.getColor()][p.getRank().toInt()-1]++;
		}

		for ( int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
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
		// adjacent to the chased piece (rank does not matter
		// at this point).

			for (int d : dir) {
				int j = i + d;
				if (!Grid.isValid(j))
					continue;
				Piece chaser = getPiece(j);
				if (chaser == null
					|| chaser.getColor() != turn
					|| !chaser.hasMoved())
					continue;

		// If an unprotected unknown chaser forks a known and unknown
		// piece, assume that the chaser is after the known piece,
		// unless the known piece is a known bomb.
		// -- R3 --
		// B? -- R?
		// This assigns the chaser a rank of Two.
		//
		// -- RB --
		// B? -- R?
		// This assigns the chaser a rank of Unknown.

				Piece chased2 = null;
				for (int d2 : dir) {
					int k = j + d2;
					if (!Grid.isValid(k))
						continue;
					chased2 = getPiece(k);
					if (chased2 == null
						|| chased2.getColor() == turn
						|| chased2 == chased) {
						chased2 = null;
						continue;
					}

					if (!chased.isKnown()
						&& chased2.isKnown()
						&& chased2.getRank() != Rank.BOMB)
						break;

		// Unknown chaser can be assigned a chase rank.

					chased2 = null;
				} // d2
				if (chased2 != null)
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

				if (!chased.isKnown()) {
					if (!chaser.isKnown()) {
						Rank chaserRank = chaser.getActingRankChase();
						if (chaserRank == Rank.NIL)
							chaser.setActingRankChaseEqual(Rank.UNKNOWN);
						continue;
					}

		// Because ranks 5-9 are often hellbent on discovery,
		// an adjacent unknown piece next to the chaser should
		// not be misinterpreted as a protector.
		// Example 1:
		// R? B6 -- B?
		//
		// Example 2: 
		// R? -- B6
		// -- B? --
		// Unknown Blue moves towards Blue Six (in Example 1) or
		// Blue Six moves towards unknown Red in Example 2.

		// If Unknown Blue is viewed as a protector, it would acquire
		// a chase rank of 4.  But Unknown Blue might not
		// care about protecting Blue 6, if Blue intends
		// to attack unknown Red anyway.

					if (chaser.getApparentRank().toInt() >= 5)
						continue;
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

	// ********* start suspected ranks

	// > 0 : the rank is still at large, location unknown
	// = 0 : the rank is gone or if large, the location is guessed
	// < 0 : the rank is still at large, multiple locations are guessed
	protected int unknownNotSuspectedRankAtLarge(int color, int r)
	{
		return unknownRankAtLarge(color, r) - suspectedRankAtLarge(color, r);
	}

	protected int unknownNotSuspectedRankAtLarge(int color, Rank rank)
	{
		return unknownNotSuspectedRankAtLarge(color, rank.toInt());
	}

	protected int suspectedRankAtLarge(int color, int r)
	{
		return suspectedRank[color][r-1];
	}

	protected int suspectedRankAtLarge(int color, Rank rank)
	{
		return suspectedRankAtLarge(color, rank.toInt());
	}

	// The usual Stratego attack strategy is one rank lower.

	protected Rank getChaseRank(Piece p, int r, boolean rankLess)
	{
		int color = p.getColor();
		assert color == Settings.bottomColor : "getChaseRank() only for opponent pieces";
		int j = r;
		Rank newRank = Rank.UNKNOWN;

		// See if the unknown rank is still on the board.
		if (r <= 5) {
			if (!rankLess)
				j--;
			for (int i = j; i > 0; i--)
				if (unknownRankAtLarge(color, i) != 0) {
					newRank = Rank.toRank(i);
					break;
				}

		// Desired unknown rank not found, try the same rank.
			if (newRank == Rank.UNKNOWN)
				if (unknownRankAtLarge(color, r) != 0)
					newRank = Rank.toRank(r);
		} else {
			if (r == 6) {

		// Sixes are usually chased by Fives, but because 4?x6
		// is also positive, the chaser could be a Four.
		// Fours are usually reserved to chase Fives, but if there
		// are more opponent Fours than AI Fives,
		// then chasing Sixes becomes worthwhile.

				if (rankAtLarge(1-color, r-1) >= rankAtLarge(color, r-2))

					j = 5; 	// chaser is probably a Five
				else
					j = 4; 	// chaser is probably a Four
			} else {

		// Sevens, Eights and Nines are chased
		// by Fives and higher ranks, as long as the chaser rank
		// is lower.
		//
		// This needs to be consistent with winFight.  For example,
		// R9 B? R6
		// All pieces are unknown and Red has the move.  Blue
		// has a chase rank of unknown.
		// R6xB? is LOSES, because winFight assumes that an
		// unknown that chases an unknown is a Five.  So R9xB?
		// must create a suspected rank of Five.  If it created
		// a higher suspected rank, then the AI would play R9xB?
		// just to create a Six so that it can attack with its Six.
		//
		// Note: if a chase rank of Nine results in an Eight,
		// this could cause the AI to randomly attack pieces
		// with Nines hoping they turn out to be Eights
		// that it can attack and win.  So the AI assumes
		// that the chaser is not an Eight, unless all other
		// unknown lower ranked pieces are gone.
		//
				if (lowestUnknownExpendableRank < r)
					j = lowestUnknownExpendableRank;
				else
					j = r - 1;
			}

			for (int i = j; i <= r; i++)
				if (unknownRankAtLarge(color, i) != 0) {
					newRank = Rank.toRank(i);
					break;
				}

		// Desired unknown rank not found.
		// Chaser must be ranked even lower.

			if (newRank == Rank.UNKNOWN)
				for (int i = j-1; i > 0; i--)
					if (unknownRankAtLarge(color, i) != 0) {
						newRank = Rank.toRank(i);
						break;
					}
		}

		// If the piece hasn't moved, then maybe its a bomb
		// COMMENTED OUT: Version 9.3
		// The AI just cannot willy nilly suspect any unmoved
		// piece to be a bomb!  Suspected bombs must be chosen
		// very carefully because the AI pieces generally do
		// not fear suspected bombs.  Not sure what this code
		// was trying to accomplish.
		// if (p.moves == 0)
		//	return Rank.BOMB;

		return newRank;
	}

	protected void setSuspectedRank(Piece p, Rank rank, boolean maybeBluffing)
	{
		if (p.getRank() == Rank.UNKNOWN || p.isSuspectedRank())
			p.setSuspectedRank(rank);

		if (!maybeBluffing)
			suspectedRank[p.getColor()][rank.toInt()-1]++;
	}

	//
	// suspectedRank is based on ActingRankChase.
	// If the piece has chased another piece,
	// the ai guesses that the chaser is a lower rank
	// If there are no lower ranks, then
	// the chaser may be of the same rank (or perhaps higher, if
	// bluffing)
	//
	public void genSuspectedRank()
	{
		// Knowing the lowest unknown expendable rank is
		// useful in an encounter with an opponent piece that
		// has approached an AI unknown.  The AI assumes that
		// these piece are expendable, because an opponent
		// usually tries to avoid discovery of its lower ranks.

		lowestUnknownExpendableRank = 0;
		for (int r = 1; r <= 9; r++)
			if (unknownNotSuspectedRankAtLarge(Settings.bottomColor, r) > 0) {
				lowestUnknownExpendableRank = r;
				if (r >= 5)
					break;
			}

		if (lowestUnknownExpendableRank == 0
			|| (lowestUnknownExpendableRank < 5
			&& rankAtLarge(Settings.topColor, Rank.ONE) == 0)
			&& unknownNotSuspectedRankAtLarge(Settings.bottomColor, Rank.SPY) > 0)
			lowestUnknownExpendableRank = 10;

		for (int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;
			if (p.getRank() != Rank.UNKNOWN)
				continue;

		// If the opponent still has any unknown Eights,
		// assume that the suspected rank can also be an Eight,
		// due to the possibility of bluffing to get at the flag.
		// (The maybeEight status can be cleared during the search
		// tree if a Seven or lower ranked piece attacks the unknown)
			if (unknownRankAtLarge(Settings.bottomColor, Rank.EIGHT) != 0)
				p.setMaybeEight(true);

			Rank rank = p.getActingRankChase();
			if (rank == Rank.NIL || rank == Rank.UNKNOWN)
				continue;

		// If the piece both chased and fled from the same rank,
		// it means that the piece is not dangerous to the
		// same rank, so creating a lower suspected rank
		// would be in error.  Perhaps it should acquire a
		// suspected rank of the same rank?  But because this is
		// unusual behavior, the piece stays Unknown.

			if (rank == p.getActingRankFleeLow()
				|| rank == p.getActingRankFleeHigh())
				continue;

		// The AI needs time to confirm whether a suspected
		// rank is bluffing.  The more the suspected rank moves
		// without being discovered, the more the AI believes it.

			if (rank == Rank.SPY) {
				if (hasSpy(p.getColor()))
					setSuspectedRank(p, Rank.SPY, p.moves < 15);

			} else
				setSuspectedRank(p, getChaseRank(p, rank.toInt(), p.isRankLess()), p.moves < 15);

		} // for
	}

	// ********* end of suspected ranks

	public boolean move(Move m)
	{
		if (getPiece(m.getTo()) != null)
			return false;

		if (validMove(m))
		{
			Piece fp = getPiece(m.getFrom());
			moveHistory(fp, null, m.getMove());

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

		getPiece(i).kill();

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
	public boolean isTwoSquares(int m)
	{
		int size = undoList.size();
		if (size < 6)
			return false;

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).
		if (Settings.twoSquares
			|| getPiece(Move.unpackFrom(m)).getColor() == Settings.topColor) {
			UndoMove prev = undoList.get(size-2);
			if (prev == null)
				return false;
			UndoMove prevprev = undoList.get(size-6);
			if (prevprev == null)
				return false;
			return prevprev.equals(prev)
				&& Move.unpackFrom(m) == prev.getTo()
				&& Move.unpackTo(m) == prev.getFrom();
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
		UndoMove aimove = undoList.get(size-1);
		UndoMove oppmove = undoList.get(size-2);

		for (int d : dir) {
			int i = oppmove.getTo() + d;
			if (!Grid.isValid(i))
				continue;
			if (i == aimove.getTo()) {
				// chase confirmed
				return isRepeatedPosition();
			}
			// we are being chased, so repetitive moves OK
			if (i == aimove.getFrom())
				return false;
		}
		// no chases
		// return false;
		// prevent looping
		return isRepeatedPosition();
	}

	// Because of limited search depth, the completion of
	// a two squares ending may well be beyond the horizon of the search.
	// However, a two squares ending can be predicted earlier if the
	// player begins a possible two squares ending before the defender.
	// For example,
	// xx xx xx	xx xx xx	xx xx xx
	// -- R3 R?	-- R3 R?	-- R3 R?
	// -- -- --	-- -- B2	B2 -- --
	// -- B2 --	-- -- --	-- -- --
	//
	// Blue Two has the move and approaches Red Three
	// Positions 1 & 2 can lead to the two squares rule but
	// position 3 does not.  Thus, move generation can eliminate
	// the move for R3 back to its original spot if the player
	// was the first to move its piece between two adjacent squares.
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
	// Note that this rule works even if chaser and chased pieces
	// are far away.  For example,
	// xx xx xx
	// -- R3 R?
	// -- -- --
	// B2 -- --
	// Blue has the move. This is not a possible two squares ending
	// because if Blue Two moves right, Red Three moves left,
	// Blue Two moves left, Red Three can still move right,
	// because Blue began the two squares first.
	// ...    2a3-b3	// m3
	// 3b1-a1		// m2
	// ...    2b3-a3	// m1
	// 3a1-b1	// allowed because m1 and m3 are swapped

	// ...    2a3-b3	// m5
	// 3b1-a1		// m4
	// ...    2b3-a3	// m3
	// 3a1-b1		// m2
	// ...    2a3-b3	// m1
	// 3b1-a1	// allowed because m1 and m3 are swapped

	// ...    <some other move>	// m3
	// 3b1-a1		// m2
	// ...    2b3-a3	// m1
	// 3a1-b1	// disallowed because m1 and m3 are not swapped

	public boolean isPossibleTwoSquares(int m)
	{
		UndoMove m2 = getLastMove(2);
		if (m2 == null)
			return false;

		int from = Move.unpackFrom(m);
		int to = Move.unpackTo(m);

		// not back to the same square?
		if (m2.getFrom() != to)
			return false;

		// is a capture?
		if (getPiece(to) != null)
			return false;

		// not the same piece?
		if (!m2.getPiece().equals(getPiece(from)))
			return false;

		// not an adjacent move (i.e., nine far move)
		if (!Grid.isAdjacent(m))
			return false;

		// If the target square has an opposite escape square,
		// the code must allow the move.  For example,
		// xx xx xx xx
		// -- R3 -- R?
		// -- -- -- --
		// -- B2 -- --
		//
		// Blue Two has the move and approaches Red Three.
		// If R3 moves to the left, it must be allowed to return
		// back to its original position, because the target
		// square has an escape route.
		//
		// Version 9.5 added code to handle the following case.
		// R? -- -- --
		// -- R3 R? --
		// -- -- -- --
		// -- B2 -- --
		//
		// The idea was to allow R3 to move back right after
		// it moved left.  But this is a pointless move, and
		// the transposition table erroneously returns the value
		// from the stored position when B2 tries to move back
		// to the right to chase R3.  But that stored value is
		// not valid, because R3 began the two squares sequence
		// and will be forced to use the escape route next.
		// For example,
		// R? -- R? --
		// -- R3 R? --
		// -- -- -- --
		// -- B2 -- --
		// Because R3 will be forced to use the escape square above
		// it, it will soon become trapped, with a very negative value.
		// So Version 9.6 removed the 9.5 code and replaced it
		// with the 9.3 test for three squares.

		// test for three squares (which is allowed)
		Piece p = getPiece(to + to - from);
		if (p == null || p.getColor() == 1 - m2.getPiece().getColor())
			return false;

		// fleeing is OK, even if back to the same square
		// for example,
		// -------
		// R? R5 |
		// R? -- |
		// B4 -- |
		// Red Five moves down and Blue Four moves right.
		// It is OK for Red Five to return to its original
		// square.
		Piece op = getPiece(from - (to - from));
		if (op != null && op.getColor() ==
			1 - getPiece(from).getColor())
			return false;

		UndoMove oppmove1 = getLastMove(1);
		if (oppmove1 == null)
			return false;
		int oppFrom1 = oppmove1.getFrom();

		// This rule checks if the player move is forced.  If the
		// player is returning to the same square and the
		// opponent was doing something else on the board,
		// it could still lead to a two squares result, but
		// no two squares prediction can be made because the move
		// was not forced.  For example,
		// -- R9 xx
		// B4 -- B1
		// -- -- --
		// -- -- B? 
		// Unknown but suspected Blue One moves down.  Red Nine
		// moves down two squares to begin a two squares sequence
		// to be able to attack Blue One and confirm its identity.
		// When the move back to its original square is considered,
		// isPossibleTwoSquares() returns false because Blue's
		// first move was not forced, and in order to force
		// Blue to move back down, Red Nine would have to move
		// up one square, subjecting it to attack by Blue Four.

		// Determine if the player move is forced.
		// Opponent and player pieces not alternating
		// in the same column or row?
		if ((oppmove1.getToX() != Grid.getX(from)
			&& oppmove1.getToY() != Grid.getY(from))
			|| (oppmove1.getFromX() != Grid.getX(to)
			&& oppmove1.getFromY() != Grid.getY(to)))
			return false;

		UndoMove oppmove3 = getLastMove(3);
		if (oppmove3 == null)
			return false;

		// player began two moves before opponent?

		if (!(oppmove3.getFrom() == oppmove1.getTo()
			&& oppmove3.getTo() == oppmove1.getFrom()))
			return true;

		UndoMove m4 = getLastMove(4);
		if (m4 == null)
			return false;

		if (!(m4.getFrom() == m2.getTo()
			&& m4.getTo() == m2.getFrom()))
			return false;

		UndoMove oppmove5 = getLastMove(5);
		if (oppmove5 == null)
			return false;

		return !(oppmove5.getFrom() == oppmove3.getTo()
			&& oppmove5.getTo() == oppmove3.getFrom());

	}

	public boolean isChased(int m)
	{
		UndoMove oppmove = getLastMove(1);
		if (oppmove == null)
			return false;
		return Grid.isAdjacent(oppmove.getTo(),  Move.unpackFrom(m));
	}

	// A chasing move back to the square where the chasing piece
	// came from in the directly preceding turn is allowed
	// if this can force the opponent piece to hit the
	// Two-Squares Rule / Five-Moves-on-Two-Squares Rule first.
	// If not, then the chase move should be discarded if it
	// results in a repetitive position, because there is no point
	// in chasing further.  (When this function returns false,
	// the calling code can reduce the number of
	// pointless back-and-forth moves from three to two, because
	// the third move results in the same position.)
	// 
	public boolean isTwoSquaresChase(int m)
	{
		UndoMove m2 = getLastMove(2);
		if (m2 == null)
			return false;

		// not back to square where the chasing piece came from?
		if (m2.getFrom() != Move.unpackTo(m))
			return false;

		// If opponent piece does not move between same two squares,
		// then this move cannot result in a two squares victory.
		UndoMove m1 = getLastMove(1);
		if (m1 == null)
		 	return false;

		UndoMove m3 = getLastMove(3);
		if (m3 == null)
		 	return false;

		if (m1.getTo() != m3.getFrom()
			|| m1.getFrom() != m3.getTo())
			return false;

		// Commented out in 9.3 because
		// piece does not need to be adjacent for a chase.
		// For example,
		// -- -- R9
		// -- -- xx
		// BS -- xx
		// -- -- --
		// Red Nine moves left two squares, Blue Spy moves right,
		// Red Nine moves right, Blue Spy moves left.
		// Red Nine should be allowed to move right again,
		// even though this is a repeated position, because
		// it does not violate the two squares rule and
		// Red Nine will capture Blue Spy.
		// if (!Grid.isAdjacent(oppmove.getTo(),  Move.unpackTo(m)))
		// 	return false;

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

		UndoMove m4 = getLastMove(4);
		if (m4 == null)
			return false;

		UndoMove m6 = getLastMove(6);
		if (m6 == null)
			return false;

		// (1) If the proposed move and the move four plies ago
		// are equal,
		// (2) and the current position and the position four plies ago
		// are equal,
		// (3) and the current square and to-square six plies ago
		// are not equal,
		// (if they are equal, it  means we are at position D
		// rather than at position C)
		// -- then this is a repetitive move
		if (m == m4.getMove()
			&& boardHistory[bturn].hash == m4.hash
			&& Move.unpackFrom(m) != m6.getTo())
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

		// Note that the hash is XORed with 1 - undoList.size()%2
		// This tests if the opposing player
		// has seen the position that the
		// current player has just created.
		// 
		UndoMove entry = boardHistory[bturn].get();
		if (entry == null)
			return false;

		// Position has been reached before

		return true;
	}

	public void undoLastMove()
	{
		int size = undoList.size();
		if (size < 2)
			return;

		boardHistory[0].remove();
		boardHistory[1].remove();

		for (int j = 0; j < 2; j++, size--) {
			UndoMove undo = undoList.get(size-1);
			Piece fp = undo.getPiece();
			Piece tp = getPiece(undo.getTo());
			fp.copy(undo.fpcopy);
			setPiece(undo.getPiece(), undo.getFrom());
			setPiece(undo.tp, undo.getTo());
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


	private void scoutLose(Move m)
	{
		Spot tmp = getScoutLooseFrom(m);

		moveToTray(m.getFrom());
		setPiece(getPiece(m.getTo()), tmp);
		setPiece(null, m.getTo());
	}
	
	private Spot getScoutLooseFrom(Move m)
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
	
	protected boolean validAttack(Move m)
	{
		if (!Grid.isValid(m.getTo()) || !Grid.isValid(m.getFrom()))
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
	public boolean validMove(Move m)
	{
		if (!Grid.isValid(m.getTo()) || !Grid.isValid(m.getFrom()))
			return false;
		Piece fp = getPiece(m.getFrom());
		if (fp == null)
			return false;
		
		//check for rule: "a player may not move their piece back and fourth.." or something
		if (isTwoSquares(m.getMove()))
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

        public boolean hasSpy(int color)
        {
                return rankAtLarge(color, Rank.SPY) != 0;
        }


	public long getHash()
	{
		return boardHistory[bturn].hash;
	}

	protected Piece getSetupPiece(int i)
	{
		return setup[i];
	}

	protected Rank getSetupRank(int i)
	{
		Piece p = setup[i];
		if (!p.isKnown())
			return Rank.UNKNOWN;
		else
			return p.getRank();
	}
}



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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.lang.Long;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import com.cjmalloy.stratego.BoardHistory;

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

	// number of moves unknown piece must make before
	// AI begins to believe suspected rank is actual rank

	public static final int SUSPECTED_RANK_AGING_DELAY = 15;
	
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
	protected static final int[] dir = { -11, -1,  1, 11 };
    protected static long[][][][][] boardHash = new long[15][8][2][82][121];
	protected static long[] depthHash = new long[40];	// MAX_DEPTH + QSMAX
	protected static BoardHistory boardHistory = new BoardHistory();
    protected int[][] knownRank = new int[2][12];   // discovered ranks
    protected int[][] allRank = new int[2][12];    // ranks in trays
	protected int[][] suspectedRank = new int[2][12];	// guessed ranks
	protected Rank[] chaseRank = new Rank[15];	// usual chase rank
	protected boolean[][] invincibleRank = new boolean[2][15];// rank that always wins or is even
	protected int[] invincibleWinRank = new int[2];	// rank that always wins
	protected int[] piecesInTray = new int[2];
	protected int[] remainingUnmovedUnknownPieces = new int[2];
	protected int[] nUnknownWeakRankAtLarge = new int[2];
	protected Piece[] flag = new Piece[2];  // flags
	protected static final int expendableRank[] = { 6, 7, 9 };
	protected static final int BLUFFER_RANK_MIN = 2;
	protected static final int BLUFFER_RANK_MAX = 5;
	protected static final int BLUFFER_RANK_INIT = 3;
	public int blufferRisk = BLUFFER_RANK_INIT;
	protected int guessedRankCorrect = 0;
	protected int guessedRankWrong = 0;
	protected int[] maybe_count = new int[2];
	protected int[] open_count = new int[2];
	protected int[][] lowerRankCount = new int[2][10];
	protected int[][] lowerKnownOrSuspectedRankCount = new int[2][10];
	protected boolean[] isBombedFlag = new boolean[2];
	protected int unknownBombs[] = new int[2];
    protected Random rnd = new Random();
    protected static int forayLane[] = { 0, 0 };
    protected boolean[][] foraySquare =  new boolean[2][121];
    public ReentrantLock lock = new ReentrantLock();  // graphics lock

	// generate bomb patterns
	static final int[][] bombPattern = new int[30][6];
	static {
	for (int y = 0; y <= 2; y++)
	for (int x = 0; x <= 9; x++) {
		int flag = Grid.getIndex(x,y);
		int bpi = 0;
		bombPattern[y*10+x][bpi++] = flag;
		for (int d : dir) {
			int bi = flag + d;
			if (!Grid.isValid(bi))
				continue;
			bombPattern[y*10+x][bpi++] = bi;
		}
		bombPattern[y*10+x][bpi] = 0;	// end of pattern
	}
	}

	static {

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

		Random rnd = new Random();

		for ( int r = 0; r < 15; r++)
		for ( int k = 0; k < 8; k++)
		for ( int m = 0; m < 2; m++)
		for ( int id = 0; id < 82; id++)
		for ( int i = 12; i <= 120; i++)
			boardHash[r][k][m][id][i] = Math.abs(rnd.nextLong());

		for ( int i = 0; i < depthHash.length; i++)
			depthHash[i] = Math.abs(rnd.nextLong());
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
		boardHistory = new BoardHistory();
	}

	public Board(Board b)
	{
		grid = new Grid(b.grid);
				
		tray.addAll(b.tray);
		undoList.addAll(b.undoList);
		setup = b.setup.clone();
		blufferRisk = b.blufferRisk;
		guessedRankCorrect = b.guessedRankCorrect;
		guessedRankWrong = b.guessedRankWrong;
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
			if (p.getColor() == Settings.bottomColor)
			 	p.setRank(Rank.UNKNOWN);
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
            lock.lock();
			Piece fp = getPiece(m.getFrom());
			Piece tp = getPiece(m.getTo());
			moveHistory(fp, tp, m.getMove());

			clearPiece(m.getFrom());
			clearPiece(m.getTo());

			fp.setShown(true);
			revealRank(fp);
			revealRank(tp);
			fp.setMoved();
			if (!Settings.bNoShowDefender || fp.getRank() == Rank.NINE) {
				tp.setShown(true);
			}

			int result = fp.getRank().winFight(tp.getRank());

		// Attacker wins:

			if (result == 1) {
				moveToTray(tp);
				setPiece(fp, m.getTo());
			}

		// Tie:

			else if (result == -1) {
				moveToTray(fp);
				moveToTray(tp);

		// Defender wins:

			} else {
				moveToTray(fp);
				if (Settings.bNoMoveDefender ||
						tp.getRank() == Rank.BOMB)
					setPiece(tp, m.getTo());

		// Setting: Defender moves to attacker's square

				else
					setPiece(tp, m.getFrom());
			}
			genChaseRank(fp.getColor());
			genFleeRank(fp, tp);	// depends on suspected rank
            lock.unlock();
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
			if (getPiece(i, j).getActualRank().equals(Rank.FLAG))
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
				if (!getPiece(i, j).getActualRank().equals(Rank.FLAG))
				if (!getPiece(i, j).getActualRank().equals(Rank.BOMB))
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
		boardHistory.clear();
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

    public Rank getRank(Piece p)
    {
        if (p == null)
            return Rank.NIL;
        else
            return p.getRank();
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
	
	public void clearPiece(int i)
	{
		setPiece(null, i);
	}

	public void setPiece(Piece p, Spot s)
	{
		setPiece(p, Grid.getIndex(s.getX(),s.getY()));
	}

	public void setPiece(Piece p, int i)
	{
		Piece bp = getPiece(i);
		if (bp != null) {
			rehash(bp, i);
		}

		if (p != null) {
			p.setIndex(i);
			grid.setPiece(i, p);
			rehash(p, i);
		} else
			grid.clearPiece(i);
	}

	// Zobrist hashing.
	// Used to determine redundant board positions.
	//
	// Unknown pieces (ai or opponent) are not all identical.
	// If two unknown pieces swap positions, the board is different,
	// because the AI bases rank predictions based on setup
	// and the history of the piece interactions.
    // The opponent may also be tracking behavior of AI pieces.
    // Therefore, each piece is considered unique and results
    // in differing hash regardless if the ranks are the same.
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

	static public long hashPiece(Piece p, int i)
	{
        return boardHash
            [p.isKnown() ? Rank.NIL.ordinal() : p.getActingRankChase().ordinal()]
            [p.getStateFlags()]
            [p.hasMoved() ? 1 : 0]
            [p.getID()]
            [i];
	}

	public void rehash(Piece p, int i)
	{
		boardHistory.hash ^= hashPiece(p, i);
	}

	public void hashDepth(int depth)
	{
		boardHistory.hash ^= depthHash[depth];
	}	

	public UndoMove getLastMove()
	{
            return undoList.get(undoList.size()-1);
	}

	public UndoMove getLastMove(int i)
	{
            int size = undoList.size();
            if (size < i)
                    return UndoMove.NullMove;
            return undoList.get(size-i);
	}

    boolean isThreat(Piece fp, Piece tp)
    {
        if (!fp.isKnown()
            && !fp.isSuspectedRank()) {
            if (isInvincibleDefender(tp))
                return false;
            return true;
        }

        // fp is known or suspected

        Rank fprank = fp.getRank();
		if ((fprank == Rank.BOMB
			|| fprank == Rank.FLAG)
			&& fp.isKnown())
			return false;

        int fpo = fprank.ordinal();
		int tpo = tp.getRank().ordinal();

        return fpo < tpo

		// A known expendable piece could be a threat
		// to a suspected One, Two or Three if it does
		// not want to reveal its stealth.   This code
		// is hit when the AI wants to attack these
		// pieces to reveal their true rank (called from
		// isPossibleTwoSquares())

			|| (!tp.isKnown()
				&& tpo <= 3
				&& fpo >= 6);
    }

	// return true if target piece could be protected
	public boolean isProtected(Piece targetPiece, Piece attackPiece)
	{
		int target = targetPiece.getIndex();
		for (int d : dir) {
			int j = target + d;
			if (!Grid.isValid(j))
				continue;
			Piece prot = getPiece(j);
			if (prot == null
				|| prot.getColor() != targetPiece.getColor())
				continue;

	// If both targetPiece and protector are known,
	// then the protector has to be at least two ranks lower
    // that the target piece to be a protector.  For example,
	// R3 R4 b?
	// Red Three is not a protector.  If unknown Blue does not
	// attack Red Four and moves some other piece, unknown Blue
	// is not lower than Red Four and flee rank will be set.

			if (prot.isKnown()
				&& targetPiece.isKnown()
				&& prot.getRank().ordinal()
					>= targetPiece.getRank().ordinal()-2)
				continue;

			 if (isThreat(prot, attackPiece))
				return true;
		}
		return false;
	}

	protected Piece getLowestRankedOpponent(int color, int i, Piece delayPiece)
	{
		for (int d : dir) {
			int j = i + d;
			if (!Grid.isValid(j))
				continue;
			Piece op = getPiece(j);
			if (op == null
				|| op.getColor() == color)
				continue;
			Rank rank = op.getApparentRank();

			if (delayPiece == null
				|| rank.ordinal() < delayPiece.getApparentRank().ordinal())
				delayPiece = op;

		}
		return delayPiece;
	}

	// Acting rank is a historical property of the known
	// opponent piece ranks that a player piece was adjacent to.
    // If a piece flees from another piece, it gains a flee acting rank,
    // which is informational in that it becomes known that that
    // piece neglected to attack another.
	//
	// High flee acting ranks may indicate low ranks may flee
	// to prevent discovery.  The lower the rank it flees from,
	// the more likely that the piece rank
	// is equal to or greater than the flee acting rank,
	// because low ranks are irresistable captures.
	// 
	// Note: genFleeRank() calls isProtected(), which depends on
	// invincibleRank[] and suspected ranks.  Thus if an unknown flees
	// from a suspected piece or a piece protected by a suspected bomb,
	// it will acquire a flee rank.  Although the fleer could have
	// fled because it does not want to attack because the suspected piece
	// might in fact be incorrect, the opponent may
	// at least suspect that the fleer would flee again from a
	// piece of the same rank.
	//
	// When a piece forks two or more opponent pieces, setting the
	// flee rank is just a guess if subsequently one of the
	// opponent pieces attacks,
	// because the neighboring pieces *could* be as strong
	// as the opponent piece that attacked.   Statistically,
	// however, the neighboring pieces are less likely to attack,
	// so the AI assigns the flee rank to all of them.

	// Flee acting rank is set on all adjacent unknown pieces.
	// Thus, if the adjacent piece neglects to attack
	// an adjacent opponent (and become known), it retains
	// its unknown status, but because it fled, it can no
	// longer bluff by chasing the same rank again.
	//
	// However, it is difficult to determine whether the
	// player played some other move that requires
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
	// - if the player chases an equal or lower ranked piece.
	// - if the player flees from a equal or lower attacker
	//
	// For example,
	// xx xx B3 -- xx 
	// -- R4 -- r? -- 
	// -- b? -- -- --
	// Unknown Blue approaches Red Four, so unknown Blue
	// now is a suspected Three.  Unknown Red moves up
	// and attacks Blue Three.  Blue Three moves away.  Do not set
	// a flee rank on unknown Blue, because a lower ranked
	// Blue piece was moved instead.
	//
	// Contrast with this example:
	// | R7 --
	// | -- b?
	// | b? --
	// |xxxxxxx
	// Red Seven moves down and forks two Unknowns.
	// Flee rank is set on both unknowns.  So if neither
	// attacks Red Seven, Red Seven is safe to attack either
	// one.

	void genFleeRank(Piece fp, Piece tp)
	{
		// delayRank is lowest of:
		// 1. the rank just captured
		// 2. a rank chased by the last move
        // 3. if the piece chased an unknown, then use the rank that just moved
		// 4. a rank fled from by the last move
        // 5. if the piece fled from an unknown, then use the rank that just moved

		Piece delayPieceChase = getLowestRankedOpponent(fp.getColor(), getLastMove().getTo(), tp);
		Piece delayPieceFled = getLowestRankedOpponent(fp.getColor(), getLastMove().getFrom(), null);
        Rank delayRank = Rank.NIL;
        if (delayPieceFled != null) {
            delayRank = delayPieceFled.getApparentRank();
            fp.setActingRankFlee(delayRank);    // actually fled
        }
        if (delayRank == Rank.UNKNOWN)
            delayRank = fp.getApparentRank();

        Rank delayRankChase = Rank.NIL;
        if (delayPieceChase != null)
            delayRankChase = delayPieceChase.getApparentRank();
        if (delayRankChase == Rank.UNKNOWN)
            delayRankChase = fp.getApparentRank();

        if (delayRankChase.ordinal() < delayRank.ordinal())
            delayRank = delayRankChase;

        // If the opponent calls an AI bluff,
        // the AI needs to curtail bluffing, or it will quickly lose
        // many of its pieces through its aggressive bluffing.

        // For now, just stop bluffing against Fives
        // TBD: this code needs to be fleshed out
        // perhaps choosing only a few pieces to bluff with

        boolean noFiveBluffs = false;
        if (fp.getColor() == Settings.bottomColor
            && tp != null
            && fp.getRank() == Rank.FIVE) {
            UndoMove um = getLastMove(2);
            if (um != null
                && um.getPiece() == tp
                && !um.fpcopy.isKnown())
                noFiveBluffs = true;
        }

		for ( int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece fleeTp = getPiece(i);
			if (fleeTp == null
				|| fleeTp == fp
				|| fleeTp.isKnown())
				continue;

        // Setting flee rank to Five on all the unknown pieces
        // makes the pieces ineffective for bluffing against Fives

            if (noFiveBluffs
                && fleeTp.getColor() == Settings.topColor)
                fleeTp.setActingRankFlee(Rank.FIVE);
			
			for (int d : dir) {
				int j = i + d;
				Piece op = getPiece(j);

				if (op == null
					|| op.getColor() != 1 - fleeTp.getColor()

        // If the opponent piece is protected, then it may be that the
        // the fleeTp does not attack because of the protection.
        // Yet if the fleeTp also has chased an unknown, it proves
        // that the fleeTp does not care about a possible unknown protector,
        // so it is more likely that fleeTp is bluffing.  The bluff must
        // be called; otherwise, inferior pieces can corner superior pieces
        // with impunity.

					|| (isProtected(op, fleeTp) && !fleeTp.isChasing(Rank.UNKNOWN)))
					continue;

		// Handle this case:
		// | -- r? --
		// | B3 b? R5
		// | -- -- --
		// After R5xb? and B3xR5, unknown Red gains an immediate
		// flee rank so that Red will realize that it must move
		// away.

				Rank rank = op.getApparentRank();
				if (op == fp && fleeTp.getColor() == Settings.topColor) {
					fleeTp.setActingRankFlee(rank);
					continue;
				}

				if (rank == Rank.BOMB
					|| rank.ordinal() >= delayRank.ordinal())	// new in version 9.5
					continue;

				fleeTp.setActingRankFlee(rank);

		// Should the piece also be set "weak"?

		// A piece that was left open to attack is probably weak
		// as well.  For example,
		// xx -- R? xx
		// xx B3 B? xx
		// -- -- -- --
		// Unknown Blue has been fleeing unknown Red.   Then
		// Blue played some other move.   This probably means
		// that unknown Blue is weak; otherwise unknown Blue
		// would have continued fleeing to avoid discovery.
		// But instead unknown Blue stopped fleeing with
		// Blue Three as protection, thinking that unknown Red
		// is also weak, because it has been chasing an unknown.
		// By setting fleeTp weak, it means that R?xB? will be
		// WINS and so the AI will see the countermove B3xR?.
		//
		// But if multiple pieces are forked, only one of them
		// can move.  And if a piece is trapped, it cannot move.
		// So a piece really cannot be set to weak without
		// further positional analysis.
		//		fleeTp.setWeak(true);
			}
		}
	}

    // Consider the following example:
    // -- -- -- --
    // xx -- -- xx
    // xx r2 -- xx
    // b? B3 b? --
    // b? b? b? b?

    // Unknown Red Two has trapped Blue Three and Red wants
    // to play r2xB3 because it believes it has the element of
    // surprise (so statistically, r2xB3 wins at least 2/3 of
    // the time because unknown Blue One could be in any of three
    // lanes.

    // But if unknown Blue to the right of Red Two moves up
    // after r2xB3, then
    // Red Two becomes trapped if it thinks that any unknown
    // piece could be Blue One after the moment of surprise
    // (i.e. r2xB3).

    // There are perhaps various ways of solving this,
    // but the logical approach used by the AI is to assign
    // indirect flee rank to pieces that fail to attack.
    // But assigning indirect flee rank on the fly
    // immediately after a move would cause the AI to attack all pieces
    // because it will think that their neighbors are no threat.

    // Alternatively, assigning indirect flee rank after the opponent
    // makes its move is complicated because the opponent may have
    // moved one of the neighboring pieces that should be assigned
    // a flee rank.  Assignment is also complicated because
    // R2 could be protected and by delaying moves.  This is
    // handled in Board, but is time-consuming to do every move.
    // Finally, moveHistory/undo handles only the piece that moves, and
    // not any other pieces.

    // Prior to Version 12, acting rank flee was assigned to
    // pieces that directly flee during tree search.
    // In the above example,
    // the unknown Blue moved piece is assigned a flee rank of Two, so
    // that Two can fearlessly backpedal because it is in no danger
    // of attack from a piece that fled from it.  This allows the
    // to play R2xR3 in this situation.

    // Yet that code suffered from this bug:
    // -- R4 RB
    // R4 b3 xx
    // -- -- xx
    // Unknown Blue Three has forked two known Fours.  But
    // the Red AI thinks that it is safe, because b3 would gain
    // a temporary flee rank of Four, regardless of which
    // Four it attacked.  So setDirectActingRankFlee() needs
    // to be called after move processing, not before.

    // Consider the following trap:
    // xx -- -- xx
    // b? r3 b? b?
    // b? B4 b? b?
    // After r3xB4 it becomes known and not safe.
    // Hence, it would not try to exit
    // because b?xR3 on the upper row of Blue pieces.

    // Version 12 experimented with simply extending
    // the time a piece is safe from attack during tree
    // processing.  Safeness terminates only when the safe
    // piece actually moved to an open square (depth = 0).
    // Qs speed is an issue and this strategy is much faster.

    // The problem with this is that a safe piece will continue
    // attacking pieces or move to the left or right and expose itself
    // to attack.  So Version 12.1 sets flee rank on neighboring
    // pieces (see guessRanks()) and clears safe after an attack.

    protected void updateSafe(Piece p)
    {
        //  Clear safe on *prior* piece if player moved some other piece

        UndoMove m3 = getLastMove(3);
        if (m3 != UndoMove.NullMove) {
            Piece priorPiece = m3.getPiece();
            if (priorPiece != p
                && priorPiece.isKnown())
                priorPiece.clear(Piece.SAFE);
        }

        // Unknown pieces are safe until they become known
        // because it is unlikely that an opponent will use its
        // unknown superior pieces on random attacks that would
        // reveal their identity in exchange for possibly an low
        // valued piece.

        if (!p.isKnown())
            return;

        // Known piece might not be safe anymore

        // clear safe once the AI captures a valuable piece
        // - one rank lower (don't push your luck)

        if (m3 != UndoMove.NullMove
            && m3.tp != null
            && p.getRank().ordinal() - 1 != m3.tp.getRank().ordinal())
            p.clear(Piece.SAFE);

        UndoMove m2 = getLastMove(2);

        if (isPossibleTwoSquaresChase()

        // A known piece is still safe
        // if it was just attacked on the last move
        // For example,
        // xx r3 -- xx
        // xx B6 -- xx
        // b? B4 -- b?
        // b? b? b? b?
        // Unknown Red Three should approach Blue Four.
        // B6xr3 (or r3xB6) does not change the safe Two Squares result.

            || m2.getTo() == p.getIndex())
            return;

        p.clear(Piece.SAFE);
    }

	// stores the state prior to the move
	// the hash is the position prior to the move
	protected void moveHistory(Piece fp, Piece tp, int m)
	{
		UndoMove um = new UndoMove(fp, tp, m, boardHistory.hash,  0);
		undoList.add(um);

		// save the hash to detect board repetitions
		boardHistory.add();
		bturn = 1 - bturn;

	}

	// check if the piece was trapped
	boolean wasTrapped(Piece trapPiece, int j)
	{
		for (int d : dir) {
			int i = j + d;
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
// TBD: check rank
			if  (p != null
				&& p != trapPiece)
				continue;
			if (!isGuarded(trapPiece, i))
				return false;
		}
		return true;
	}

	// check if the square is guarded
	public boolean isGuarded(Piece trapped, int j)
	{
        int color = trapped.getColor();
        Rank rank = trapped.getRank();
		for (int d : dir) {
			int i = j + d;
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if  (p == null
				|| p.getColor() == trapped.getColor()
                || (p.getApparentRank() != Rank.UNKNOWN
                    && p.getApparentRank().winFight(rank) != Rank.WINS))
				continue;
			return true;
		}
		return false;
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
	// b? --
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

	void setDirectChaseRank(Piece chaser, Piece chased, int i)
	{
		if (chased.isKnown()
            || chased.isFleeing(chaser.getRank()))        // ha ha
		    return;

		Move m = getLastMove();
		if (m.getPiece() != chased)
			return;

        Rank arank = chased.getActingRankChase();
		Rank chaserRank = chaser.getApparentRank();

        if (chased.isChasing(chaserRank))  // new in 13.1
            return;

		// Do not reset chase rank on a trapped piece
		// that already has a chase rank
		// even if it wasn't chased, because it may try
		// to make a dash for it.
		// (Prior to version 12, chase rank would have
		// been set unless the piece was directly threatened)

		// Example 1		Example 2
		// R3 -- --		// R? -- --
		// xx b4 --		// xx b4 --
		// xx -- R4		// xx -- r?
		// -- -- --		// -- -- --
		// 
		// In example 1, suspected Blue Four anticipates
        // that Red Three may approach and retreats
        // to a square next to known Red Four.
		// Blue Four does not acquire a chase rank of Three,
		// but remains a Four. Example 2 is also a nervous
		// situation for Blue Four and it may not wait to be
		// chased, or it may have already guessed that one of
		// the red pieces is weaker, or perhaps its other pieces
		// are trapped as well.

		Move m2 = getLastMove(2);
		if (Grid.isAdjacent(m2.getTo(), m.getFrom()) // was chased
            || (arank != Rank.NIL && wasTrapped(chased, m.getFrom())))
			return;

		// Prior to version 9.6, if a chased piece had a
		// protector, it did not acquire a chase rank, because
		// the strong piece could be either the chased piece or
		// or the protector.
		//
		// After version 9.6, this code was removed, because
		// the chaser could very well be a strong piece and
		// the AI should attack it to confirm its suspicion.
		//
		// In version 9.10, the AI tries to determine if
		// the protector is strong.
		// The idea was that if a neighboring piece has exactly
		// the same chase rank (or a known rank of one less),
		// then no chase rank should be assigned.   For example,
		// R2 --
		// b? b1
		// Unknown Blue has just approached known Red Two, but it is
		// protected by Blue One.  Unknown Blue should not
		// earn a chase rank of Two.
		//
		// Version 11.0 clarifies this concept by restating it:
		// A chase rank is always assigned to the chased piece
		// unless the protector is stronger than the chaser.

		if (chaser.isKnown()) {

        // Version 12 does not assign a chase rank if the chaser
        // is invincible, because it is rare that the opponent
        // will approach a known rank with the same rank, due to
        // loss of stealth.  This fixes the bug where the
        // AI thinks that every piece approaching a known One is
        // an unknown One.  This happens when the opponent is
        // using two unknown pieces to approach the known AI One
        // when the opponent Spy is still on the board.
        // R1 --
        // b? b?

            if (isInvincible(chaser))   // caveat: excludes defacto invincible ranks
                return;

            for (int d : dir) {
                int j =  i + d;
                Piece p = getPiece(j);
                if (p == null
                    || p.getColor() != chased.getColor())
                    continue;

                Rank prank = p.getRank();   // known or suspected

            // If the only protector is truly unknown, then the AI
            // will assign a chase rank to the chased piece.
            // (Although the protector *could* be strong, the AI
            // assumes that the chased piece is the strong piece.)

			if (prank == Rank.UNKNOWN)
				continue;

            // Both chaser and defender have known or suspected ranks.
            // If the defender would win the chaser,
            // then the AI does not assign a chase rank to the chased piece.

			if (prank.winFight(chaserRank) == Rank.WINS)
				return;
            }
        } // chaser is known

        // Update chase rank
		
		// Recall that direct and indirect chase ranks
		// are assigned differently: If a piece protects
		// another piece from a Three, it gains an indirect
		// chase rank of One.   (The LESS flag is set).
        // If a piece chases a Three, it gains a chase rank of Three.

        // One could argue that it would be simpler to have
        // only one kind of chase rank.  The AI
        // keeps these separate although uses just
        // one variable to store the chase rank.  This is because
        // information discovered by indirect and direct chases
        // differs, although both are still difficult to use
        // in the heuristic because of bluffing.

        // Because direct chasing is voluntary, the chaser is obvious:
        // usually one rank below the chased piece.  If the player is
        // a bluffer, the chaser is usually an expendable piece.

        // An indirect chase rank is bestowed when a piece is protected, and
        // the player may have to resort to using a more superior
        // piece than desired to protect the piece.   Often
        // the player realizes that there is no way to protect
        // the piece, forcing the player into an undesirable
        // bluffing situation.

        // Thus indirect chase rank is less reliable than
        // direct rank.  However, there is one situation where
        // it can be more reliable: a player who protects an unknown
        // expendable piece with a stronger unknown protector.  This is because
        // stronger players know how obvious it is to chase
        // or protect known pieces, but think that the double
        // unknown piece ruse is safer.   A skilled player will
        // detect this playing style, not take the bait, but remember
        // the ranking in subsequent attack plans.

		// Prior to Version 10.4, if an unknown piece had LESS set
		// (because it protected a piece) and then chased an AI piece,
		// it always cleared LESS
		// (actual chases are better indicators of actual rank
		// than protectors because of bluffing when an opponent
		// piece is trapped.

		// However, if the player wasn't bluffing, then information
		// about a potential stronger piece is lost.  For example,
		// An unknown Two protected a Four from attack.   Then
		// the Two chased a Four.  Prior to Version 10.4, the AI
		// would guess that the Two was a Three.   So Version 10.4
		// retains the rank from LESS if the rank is plausible.

        // The LESS flag always has to be cleared when setting a
        // direct chase rank.  But the chase rank needs to be
        // converted into a comparable direct chase, because the
        // chase rank variable is not cleared.

        arank = chased.convertActingRankChaseLess();

		// The AI believes that unknown pieces that chase AI
        // unknowns are probably high ranked pieces
        // and can be attacked by a Five or less.

		// Prior to version 9.3, chasing an unknown always
		// set the chase rank to UNKNOWN.
		// The problem was that once the AI successfully
		// trapped a low suspected rank that was forced to move
		// pass AI unknowns, it lost its suspected rank
		// and acquired the UNKNOWN rank.
		// This led the AI to approach the now unknown opponent
		// piece and thus lose its piece.

		// From version 9.3 to 10.0, if a piece has a chase rank
		// of 4 or lower, the low rank is retained if the piece
		// chases an unknown.  The side-effect is that the AI is duped
		// by an opponent piece that chases a low ranked AI piece
		// and then chases an unknown.  This bluff results in
		// the AI wasting a piece trying to discover the true rank
		// of the bluffing piece.  If the AI piece is not able
		// to attack the bluffing piece, it can also lead to the
		// AI miscalculation of other suspected ranks on the board,
		// leading to a loss elsewhere.

		// The key change in 10.1 is that all chase ranks
		// other than Four or Five are retained if the piece approaches
		// an Unknown.  This is because chasing a known piece
		// of any rank always gives more information than chasing
		// an Unknown.  For example, if a piece chases a Six,
		// it is probably a Five.  Then if it chases an Unknown,
		// it still is probably a Five.

		// In Version 10.1, if a piece has a Four or Five chase rank,
		// (suspected rank of Four) and it then approaches an
		// Unknown, the chase rank changes to Six which
		// usually results in a suspected rank of Five.
		// Thus, if the piece is actually a trapped Four,
		// the AI could approach it with a Five and lose
		// (but it probably won't approach, because 5x5 is even).
		// But much more often the piece is a Five, so that
		// chasing an Unknown is a sure sign that the piece
		// is not a Four but a Five, so the chance of a small loss
		// (4x5) is a necessary evil.

        // Bluffing makes any strategy indeterminate, so
        // Version 12 simply declined to assign a suspected
        // rank to any piece chasing an Unknown.   So if a piece
        // that chased a Three then chased an Unknown, it would
        // no longer garner a suspected rank.  This is the same
        // when blufferRisk is 5 and the AI believes that
        // the opponent is a bluffer.

        // Version 12.1 then experimentally
        // checked blufferRisk (in genSuspectedRank())
        // and if the opponent blufferRisk is 3-4, then it used
        // the 12.0 strategy.  However, if opponent is a dumb bot
        // (not bluffing), then genSuspectedRank() used
        // strategy similar to 10.1 and retains all chase ranks.

        // There were problems with this strategy.  If a piece chases a 6 or 7
        // and then chases an unknown, the chase is consistent, and the piece
        // is probably a Five or Six.   If the piece chases a superior piece
        // and then chases an unknown, it is an indication of bluffing,
        // but if no rank is assigned, then revealRank will not update
        // blufferRisk when the piece finally attacks.

        // Therefore, in Version 12.1 if the opponent
        // chases both an unknown or inferior and also a superior piece,
        // blufferRisk is increased and the chase rank is reset to the
        // newly chased piece.  If the opponent continues in this fashion,
        // blufferRisk will continue to increase to the point where the AI knows it
        // is playing more than a dumb botand can adjust its tactics accordingly.

        // Finally, if the piece corners a superior AI piece by chasing
        // it past unknowns, blufferRisk will quickly increase as
        // it passes each unknown to the point that the AI will realize the opponent
        // is bluffing, allowing the superior piece to strike back.

        // Why is this important to know?  So that the AI can clobber
        // the bot faster.  Humans also invariably adjust their
        // play when they know they are playing a bot, and take advantage
        // of their predictable behavior.  So when a human plays the AI,
        // the AI begins predictably, but soon becomes unpredictable
        // itself in response to the opponent unpredictable behavior.

        // Note that chasing a Five tells us nothing about what it will
        // do next.  The chaser could be a Three, Four, or Five, and
        // if a Four or Five, may chase an unknown.

        // If the opponent forks a known strong AI piece and an unknown
        // piece (say a Spy or an unknown One), what should the player do?
        // If the strong AI piece flees, the opponent might only be
        // a weak piece and then attack the unknown piece.  But it could
        // be a strong piece chasing the known strong AI piece, especially
        // if it is invincible.

        // Version 13.1 reverts to the 9.3/10.1 strategy.  The problem with
        // 12.1 is that reset erases opponent piece chase history which is
        // useful to establish that the piece is erratic.
        // The rank miscalculation problem can then be solved
        // by simply discarding the erratic pieces (see, maybeBluffing()).
        // Also, once the AI establishes that the opponent is a bluffer,
        // the AI will then guess erratic pieces could be weak as well.

        // In 12.1, the AI was duped into assuming that the last rank that
        // the opponent piece chased was significant and prior chases were
        // not.  This assumption cannot be made.  The AI now assumes that the lowest
        // rank is significant until proven otherwise, which is more conservative.

        // The prior comments about the deficiencies of 9.3/10.1 remain correct.
        // The AI will waste pieces attacking these erratic pieces.
        // But since 9.3/10.1, reaching blufferRisk of 5 requires only 2 surprises,
        // which is a cost that the AI can pay to determine if it is playing a dumb
        // bot or a human.

        chased.setActingRankChaseEqual(chaserRank);
	}


    public int getOpenSquares(Piece chased, int i)
    {
		UndoMove um1 = getLastMove(1);
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

		// If moving to the open square is prevented by
		// Two Squares OR could possibly lead to Two Squares
		// then the open square is not really an option.
		// So if the chased player made some other move (um1)
		// that left its chased piece open to attack,
		// check the chased player prior move (um3).   If
		// the chased piece just came from the open square, then
		// perhaps it won't move back because it leads to a
		// Two Squares ending.
		//
		// Note that if the chased piece sees that it is trapped
		// in an area with no protection, the best bluff
		// is to stop early, leaving an obvious open square.
		// This indicates to the AI that it has protection.
		// But if the piece, alternates between two open squares,
		// the AI believes that the chased piece
		// is trapped without any protection.

				if (um3 != UndoMove.NullMove
					&& j == um3.getFrom())
					continue;

		// check if the open square was occupied by some other
		// piece that just moved to make a getaway square for
		// the chased piece

				if (j == um1.getFrom()
					&& um1.getPiece() != chased)
					continue;

		// assume the move to the open square is a decent flee move

				if (!isGuarded(chased, j))
					open++;
				continue;
			} // open square
        } // dir
        return open;
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
		assert getPiece(i) == chased : "chased not at i?";

		// In version 9.6, direct chase rank no longer
		// depends on protecting pieces

		setDirectChaseRank(chaser, chased, i);

		Rank chasedRank = chased.getApparentRank();
		Rank chaserRank = chaser.getApparentRank();

		if (!chaser.isKnown()) {

		// Because ranks 5-9 are often hellbent on discovery,
		// an adjacent unknown piece next to the chased should
		// not be misinterpreted as a protector.
		// Example 1:
		// r? B6 -- b?
		//
		// Example 2: 
		// r? -- B6
		// -- b? --
		// Unknown Blue moves towards Blue Six (in Example 1) or
		// Blue Six moves towards unknown Red in Example 2.
		// If Unknown Blue is viewed as a protector, it would
		// acquire a chase rank of 4.  But Unknown Blue might not
		// care about protecting Blue 6, if Blue intends
		// to attack unknown Red anyway.
        //
        // (However, if the chaser is known, the protector is
        // probably stronger.)

			if (chasedRank.ordinal() >= 5	// or UNKNOWN
				|| isInvincible(chased)
				|| chaser.isFleeing(chasedRank)
				|| (chaser.is(Piece.WEAK) && blufferRisk >= 3))
				return;

		// If the chased piece is a strong but not invincible piece,
		// then there can be more that can be determined in this
		// case because if the chased believes that the Unknown
		// is a low ranked piece, then the protector
		// must even be lower.
		//
		// For example,
		// r? -- b?
		// -- B2 --
		// Blue Two moves between Unknown Red and Unknown Blue.
		// If Blue thinks that Unknown Red could be Red One
		// and Unknown Blue is Blue Spy, then Red could guess that
		// Unknown Blue *is* Blue Spy.  But if Blue does not think
		// that Unknown Red is Red One, this is simply an attacking
		// move and nothing can be determined about Unknown Blue.
		//
		// Yet, if Unknown Red is actually a lower rank,
		// and its cover has been blown, then it follows
		// that Unknown Blue is probably Blue Spy, or otherwise
		// Blue has greatly miscalculated.
		// 
		// A surer way to determine whether Blue believes Unknown
		// Red is a superior piece is if Blue neglects to attack
		// Unknown Red.  For example,
		// r? B2 -- b?
		// If Blue Two does not attack Unknown Red, and instead
		// moves unknown Blue towards Blue Two, then Blue is
		// signaling that it believes Unknown Red is Red One.
		//
		// So if the last move was not the chased, then it can
		// be assumed that the player believes the unknown chaser
		// piece is a superior piece, and so the AI assumes that its
		// protector is even more superior.
		//
		// Another example,
		// r? B3 -- b?
		// If Blue Three does not attack Unknown Red, and instead
		// moves unknown Blue towards Blue Two, then Blue is
		// signaling that it thinks unknown Red is either Red One,
		// and therefore unknown Blue is Blue Spy
		// OR it thinks that unknown Red is Red Two,
		// and therefore unknown Blue is Blue One.
		// So Red does not know if unknown Blue is Blue Spy
		// or Blue One, but both pieces are worthy targets
		// to discover its identity.
		// 
		// The AI makes the assumption that unknown Blue is probably
		// Blue One in this case, although obviously it could be
		// wrong.
		//
		// In version 10.1, the AI always believes that whenever the
		// opponent leaves a valuable piece vulnerable to attack
		// by an unknown AI piece, but protected,
		// then the protection must be strong.

		// If the chaser is known and the chased is unknown,
        // nothing can be determined.  For example,
        // R4 -- b? b?
        // Red Four approaches unknown Blue, and Blue does not
        // move.   Unknown Blue could be another Four and
        // and delines to move.
        // r? R4 -- b?
        // -- -- b? --
        // Unknown Blue approaches Red Four.  It aquires a
        // suspected rank.  The other unknown Blue could
        // be any piece.

        // Version 8.2 adds assignment of chase rank
        // to protectors of fleeing unknowns (including suspected):
        // R3 b? -- b?
        // Red Three has been chasing unknown Blue. Then
        // the right unknown Blue moves left to protect.
        // However, is left unknown Blue is a suspected Three,
        // then right unknown blue is not a protector.
       
        // chaser is known 
		} else if (!(chased.isKnown()
                || (chased.isFleeing(chaserRank)
                    && chaserRank != chasedRank))
            || chaserRank.ordinal() >= chasedRank.ordinal())
                return;

        //------------------------------------------------
		// chaser piece is either an unknown piece chasing
        // a known strong chased piece OR
        // both chaser and chased are known and chaser is
        // stronger than the chased piece
        // If the chased piece does not flee and has
        // protection, the protection is assigned a chase rank.
        //------------------------------------------------

		Piece knownProtector = null;
		Piece unknownProtector = null;
		boolean activeProtector = false;
		int open = 0;
		UndoMove um1 = getLastMove(1);

		// If the opponent attacked the chaser on the prior move
		// (and obviously lost), the chaser was probably unknown,
		// (unless the opponent is an idiot), so its rank
		// cannot possibly determine the protector until the opponent
		// declines to move the chased piece.

		if (um1.tp == chaser)
			return;

		// If a piece moved adjacent to the chased piece,
		// and the chased piece has more than one adjacent
		// unknown piece, it is more likely that the piece
		// that actively moved adjacent to the chase piece
		// is the protector.

		if (Grid.isAdjacent(um1.getTo(), i)) {
			open++;	// adjacent square was open
			Piece p = um1.getPiece();
			if (p.getApparentRank() == Rank.UNKNOWN
				&& p.getActingRankFleeLow() != chaser.getRank()) {
				unknownProtector = p;
				activeProtector = true;
			}
		}

        // If the chased piece moved, then check for open squares
        // from where it moved  For example,
        // xx -- -- xx xx -- --
        // r? r? -- R3 B2 -- --
        // r? RB B1 -- rB -- b9
        // Red Three is chased by Blue Two and moves left.  Do
        // not assign a chase rank to unknown Red because the
        // move was forced.

        if (um1.getTo() == i
            && getOpenSquares(chased, um1.getFrom()) == 0)
            return;

		for (int d : dir) {
			int j = i + d;
			if (!Grid.isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p == null
                || p.getColor() == chaser.getColor())
				continue;

			if (p.isKnown()
				&& chaser.isKnown()
				&& (p.getRank().ordinal() < chaserRank.ordinal()
					|| p.getRank() == Rank.SPY && chaserRank == Rank.ONE))
					return;

			else if (p.getApparentRank().ordinal() < chasedRank.ordinal()
				&& !chaser.isKnown()) {
				assert chased.isKnown() : "chasedRank must be ranked";
				if (knownProtector == null
					|| p.getApparentRank().ordinal() < knownProtector.getApparentRank().ordinal())
					knownProtector = p;

			} else if (p.isKnown())
				continue;

			else if (activeProtector)
				continue;

		// If the protector fled from the chaser,
		// it isn't a protector.  For example,
		// -- -- R4 --
		// xx B5 -- xx
		// xx -- B? xx
		// Red Four moves down and forks Blue Five and unknown Blue.
		// Unknown Blue moves left.  Unknown Blue is not a protector.
		//
		// And if the AI thinks that the protector could be weak,
		// it isn't a protector either.   This especially applies
		// to unmoved front row pieces in the lanes, which the AI
		// thinks are weak or bombs, and certainly unlikely solitary
		// protectors!

			else if (!p.isFleeing(chaserRank) && !p.is(Piece.WEAK)) {

		// more than one unknown protector?

				if (unknownProtector != null)
					return;
				
				unknownProtector = p;
			}
		} // dir

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
		//
		// One exception is if the unknown protector has the
		// same suspected rank as the attacker.   In this case the AI
		// believes that the protector may actually be stronger.

			if (open == 0 && getOpenSquares(chased, i) == 0
                && (chaserRank == Rank.UNKNOWN
                    || unknownProtector.getApparentRank() != chaserRank))
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
		// Note that the following check fails if Blue did not
		// move one of the forked pieces.
		// For example,
		// xx xx -- --
		// -- B4 R3 --
		// -- B? B4 --
		// -- -- -- --
		// Blue knows it will lose a Four, so neither flees.
		// The AI assigns a suspected chase rank to unknown Blue,
		// which happens to be an isolated bomb.

		// Thus it can be hard to determine whether a protector
		// is involved or not.  But if the opponent did move a piece
		// away from the chaser, do not set the chase rank on
		// any adjacent piece.

			if (Grid.steps(um1.getFrom(), chaser.getIndex()) == 1)
				return;

		// If both the chased piece and the protector are unknown,
		// and the chased piece approached the chaser
		// (recall that the chased piece is actually the chaser, so
		// an unknown piece is attacking a known piece.)
		// then it cannot be certain which piece is stronger.
		//
		// For example, if an unknown Blue approaches known Red Two,
		// it is not possible to tell which piece is stronger.
		// b? -- R2
		// -- b? --
		//
		// The approaching unknown will acquire a chase rank of Two,
		// and no chase rank is assigned to the protector.
		//
		// However, if the unknown chased piece neglects to attack,
		// it can be certain that the unknown protector
		// is the stronger piece.
		//
		// For example, known Red Five approaches unknown Blue
		// (because it has a chase rank of Unknown from chasing
		// unknown pieces, suggesting that it is a high ranked
		// piece).  The unknown blue protector moves to protect
		// the unknown blue under attack.
		// -- B? R5
		// B? -- -- 

			if (um1.getPiece() == chased) {
				if (!chased.isKnown())
					return;

			} else {

		// Opponent moved some piece other than the chased,
		// and neglected to capture the chaser,
		// likely implying that the chased piece protection
		// is strong.  But the opponent could also
		// be attacking some other player piece
		// that requires more immediate attention.
		//
		// Prior to version 10.1, flee rank was checked:
		//		Rank fleeRank = chased.getActingRankFleeLow();
		//		if (fleeRank != chaserRank)
		//			return;
		//
		// But this was a poor substitute for several reasons.
		// 1. genFleeRank is called after genChaseRank.
		//	This delayed the setting of the protector rank.
		// 2. the piece could have fled from multiple pieces,
		//	such as a Three fled from a Two, then
		//	was attacked by an unknown, and then did
		// 	not flee, implying that the protector was a Spy.
		//	But then the protector rank was not set.
		// 3. flee rank could have been set prior, and then
		//	the opponent chased a stronger piece.
		//	And then the protector rank was set in error.

			Piece defender = getLowestRankedOpponent(um1.getPiece().getColor(), um1.getTo(), chased);
			if (defender != chased)
				return;
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
		//
		// Yet if the opponent moved a piece to protect,
		// a chase rank should be set.  For example,
		// -- -- |
		// -- R5 |
		// -- B? |
		// B? -- |
		// Red Five has approached an unknown.  The lower unknown
		// Blue moves up to protect the upper unknown, which
		// is probably a Seven or a Nine.  The lower unknown
		// should gain a chase rank of Five.

			if (um1.getPiece() != unknownProtector
				&& chaserRank.ordinal() >= 5
				&& !chased.isKnown())
				return;


		// If the chased piece is high ranked and just
		// captured an AI piece, it did so because it
		// wanted the AI piece regardless of protection.
		// For example,
		// xx xx -- -- |
		// B? B? R? R4 |
		// -- -- B? -- |
		//
		// Either Blue Unknown attacks unknown Red and wins.
		// Unknown Blue is a high ranked piece (5 to 8).
		// Unknown Blue may or may not be protected, because
		// its mission was to  attack unknown Red, regardless
		// of the consequence.

			if (chasedRank.ordinal() >= 5
				&& chased == um1.getPiece()
				&& um1.tp != null)
				return;

		// TBD: If the unknown protector is part of a potential
		// bomb structure, then the chased piece may just be
		// willing to sacrifice itself to protect the bomb structure.

		// strong unknown protector confirmed

			int r = chasedRank.ordinal();

		// One cannot be protected.
			if (r == 1)
				return;

			int attackerRank = 0;

			if (chaser.isKnown()) {
				attackerRank = chaser.getRank().ordinal();
				assert !chased.isKnown() || attackerRank < r : "known chaser " + attackerRank + "  must chase unknown or lower rank, not " + chased.isKnown() + " " + chased.getRank();
				r = attackerRank - 1;
			} else {

		// Guess that the attacker rank is one less than
		// the chased rank (or possibly less, if not unknown)

				attackerRank = r - 1;
				while (attackerRank > 0 && unknownRankAtLarge(chaser.getColor(), attackerRank) == 0)
					attackerRank--;

		// Guess that the protector rank is one less than
		// the attacker rank or the Spy.

				if (attackerRank != 0)
					r = attackerRank - 1;
			}

		// Prior to version 12.3, the AI relied on its guessing
        // of suspected ranks in assignment of new suspected ranks.
		// This was to prevent the opponent from continually
        // protecting its pieces through bluffing with unknown protectors,
        // because at some point, the limited pool of superior
        // protectors would be exhausted, and the AI would know
        // that the opponent is bluffing (which was never implemented).

        // The AI now relies on blufferRisk to change strategy
        // when bluffing is excessive. So this code no longer looks
        // at suspected rank in assigning new rank and thus prevents compounded errors
        // in guessing suspected ranks.

        // This means that the newly assignments are now less superior
        // than the earlier code.  For example, if the opponent has a suspected Two
        // and then an AI Three attacks an opponent Four and a protector steps in,
        // the AI makes the protector a suspected Two instead of a One.
        // Obviously both pieces cannot be Twos, and this creates less risk for the
        // AI when one of them matures; there was more risk with the earlier
        // code if the suspected One matured, making the AI think that its
        // Two was invincible.

			while (r > 0 && unknownRankAtLarge(chased.getColor(), r) == 0)
				r--;

		// known piece is protector

			if (knownProtector != null
				&& (knownProtector.getRank().ordinal() <= r
					|| (knownProtector.getRank() == Rank.SPY && r == 0)))
				return;

		// Only a Spy can protect a piece attacked by a One.

			Rank arank = unknownProtector.getActingRankChase();
			Rank rank;
			if (r == 0) {
				if (attackerRank != 1)
					return;

		// If the protector already has a chase rank,
		// don't let it bluff as a Spy.  This is evidence
		// of a bluffing opponent.

				if (arank != Rank.NIL)
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
			if (arank == Rank.SPY)
				rank = Rank.SPY;

			if (arank == Rank.NIL || arank.ordinal() > r)
				unknownProtector.setActingRankChaseLess(rank);

		} // unknown protector

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

	protected void genChaseRank(int turn)
	{
		// Indirect chase rank now depends on unassigned
		// suspected ranks because of bluffing.
		genSuspectedRank();

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
		// piece, assume that the chaser is after the known piece.
		// -- R3 --
		// -- b? r?
		// This assigns the chaser a rank of Two.
		//
		// If an unprotected unknown chaser forks two known pieces
		// assume that the chaser is after the stronger known piece.
		// -- R3 --
		// -- b? R6
		// This assigns the chaser a rank of Two.
		//
		// If the known piece is expendable (5-9) or a Bomb,
		// assume the chaser is after either the known piece
		// or the unknown, so set an unknown chase rank.
		// For example,
		// -- R5 --
		// -- b? r?
		// The chaser may be a Four or a Five, but it is more often
		// a Five.  This check is consistent with setDirectChaseRank()
		// which sets the chase rank to Unknown if a piece chases
		// a known Five, and then an unknown, or vice versa.
        //
        // TBD: these assumptions make the AI vulnerable to
        // the following attack:
		// -- R3 --
		// -- b? s?
        // Blue has forked known Red Three and unknown Spy.  If
        // Red Three flees, unknown Blue could be bluffing like
        // a Scout and then attack Red Spy.
        //
        // Consider blufferRisk and whether Blue has dangerous
        // invincible pieces.

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

		// If the chased piece is unknown, but is forked with
		// a known low rank piece (1-4), do not set an unknown
		// chase rank.

					if (!chased.isKnown()
						&& chased2.isKnown()
						&& chased2.getRank().ordinal() <= 4)
						break;

		// If the chased piece is forked with a superior
        // piece, do not set chase rank based on the inferior piece
        // (chase rank will be set based on the superior piece).

					if (chased.isKnown()
						&& chased2.isKnown()
						&& chased2.getRank().ordinal() < chased.getRank().ordinal())
						break;

		// Unknown chaser can be assigned a chase rank.

					chased2 = null;
				} // d2
				if (chased2 != null)
                    continue;

		// Now this is the tricky part.
		// The roles of chaser and chased are swapped
		// in isProtectedChase(), becaused the chased piece
		// tries to determine if it can win the chaser piece
		// based on its protection.  This can result in actingRankChase
		// assignment to a piece that is protecting the actual
		// attacker rather than the attacker.
		// For example:
		// B3 R4 -- r2
		//
		// Unknown Red Two moves towards known Red Four.
		// Red is the chaser because it had the move.
		// The same result occurs in the example below, where
		// Red Four moves between Blue Three and unknown Red Two:
		// B3 -- r2
		// -- R4 --
		// 
		// The usual case is simpler, such when unknown Red
		// approaches Blue Three in the example below.  Then
		// unknown Red gets an actingRankChase because it has
		// no protection.
		// B3 -- r?

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
		return unknownNotSuspectedRankAtLarge(color, rank.ordinal());
	}

	protected int suspectedRankAtLarge(int color, int r)
	{
		return suspectedRank[color][r-1];
	}

	protected int suspectedRankAtLarge(int color, Rank rank)
	{
		return suspectedRankAtLarge(color, rank.ordinal());
	}

	// The number of unknown weak pieces still at large
    // greatly determines the relative safety of a valuable rank
    // from discovery.  Note that weak ranks include Eights.
    // Because some weak ranks may remained buried
    // in bomb structures or simply are not being moved,
    // nUnknownWeakRankAtLarge is rarely zero.
    // But this should not deter valuable ranks from attack.
    // So the AI subtracts a percentage (1/3) of
    // remainingUnmovedUnknownPieces.  The lower the value
    // returned by weakRanks(), the less chance an unknown piece
    // will be randomly attacked, and if it is, the more likely
    // it will escape.
    protected int weakRanks(int color)
    {
            return nUnknownWeakRankAtLarge[color] - (remainingUnmovedUnknownPieces[color] / 3);
    }

	// The usual Stratego attack strategy is one rank lower.

	Rank getChaseRank(int r)
	{
		Rank newRank = Rank.UNKNOWN;

		// If a piece approaches an unknown piece, the approacher is
		// usually a Five, Six, Seven or Nine.  No suspected rank
		// is set, and the evaluation function checks the chase rank
		// for Rank.UNKNOWN and lowestUnknownExpendableRank to determine
		// the result of an attack, which must be more positive
		// than if the lowest unknown expendable rank were assigned as
		// the suspected rank of the piece, because an unknown
		// *could* be a higher ranked piece.
		//
		// Yet in the very special case
		// when there are no expendable opponent ranks remaining
		// except for unknown Fives, the AI assumes that the approacher
		// *is* a Five.

		if (r == Rank.UNKNOWN.ordinal()) {
			for (int e: expendableRank)
				if (unknownRankAtLarge(Settings.bottomColor, e) != 0)
					return Rank.NIL;
			if (unknownRankAtLarge(Settings.bottomColor, 5) == 0)
				return Rank.NIL;

			r = 6; 	// chaser is probably a Five
		}

		// See if the unknown rank is still on the board.

		// Note: Sixes are usually chased by Fives, but because 4?x6
		// is also positive, the chaser could be a Four.
		// So if an AI Five encounters a suspected Five
		// that has a Six chase rank, the result is EVEN,
		// but the risk of loss is higher than if the chase
		// rank is Unknown.
		//
		// Note: Sevens are usually chased by Sixes, but 5?x7
		// is also positive.

		if (r <= 7) {
			for (int i = r; i > 0; i--)
				if (unknownRankAtLarge(Settings.bottomColor, i) != 0) {
					newRank = Rank.toRank(i);
					break;
				}

		// Lower unknown rank not found.  It is not likely that
		// the attacker will chase a known rank with the
		// same unknown rank, unless the attacker is winning
		// and wants to reduce the piece count.
		// It is more likely that the attacker is bluffing.

		} else {

		// Eights and Nines could be chased by
		// almost any expendable piece, so no suspected rank is set.
		//
		// This needs to be consistent with winFight.  For example,
		// r9 b? r6
		// All pieces are unknown and Red has the move.  Blue
		// has a chase rank of unknown.
		// r6xb? is LOSES, because winFight assumes that an
		// unknown that chases an unknown is a Five.  So r9xb?
		// must create a suspected rank of Five.  If it created
		// a higher suspected rank, then the AI would play r9xb?
		// just to create a Six so that it can attack with its Six.
		//
		// Note: if a chase rank of Nine results in an Eight,
		// this could cause the AI to randomly attack pieces
		// with Nines hoping they turn out to be Eights
		// that it can attack and win.  So the AI assumes
		// that the chaser is not an Eight, unless all other
		// unknown lower ranked pieces are gone.
		//
			return Rank.NIL;
		}

		return newRank;
	}

	protected Rank getChaseRank(Rank rank)
	{
		int r = rank.ordinal();
		if (r <= 7) {
			Rank chaser = chaseRank[r-1];

		// While it is less probable that the same rank
		// chased the same rank, it certainly is possible
		// and is a better guess than Unknown.

			if (chaser != Rank.UNKNOWN)
				return chaser;
		}

		return chaseRank[r];
	}

	// Until version 10.1, bluffing determination relied on a simple count
	// of moves before the AI believed the piece was the rank
	// indicated by its chasing behavior.  But this causes the
	// AI to lose material when suspected ranked opponent pieces
	// that have not matured are near the flag.  It also results
	// in a long delay before the AI takes decisive action based
	// on the ranks that it has discovered.
	//
	// Version 10.1 relies on a 3 move successive chase sequence
	// (either chasing or fleeing, it doesn't matter)
	// to accelerate the rank maturation in addition to
	// the move count.

	boolean maybeBluffing(Piece p)
	{
		if (p.isErraticChaser()
            || p.getMoves() >= SUSPECTED_RANK_AGING_DELAY)
			return false;

		for (int i = 1; i <= 5; i+=2) {
			Move oppm = getLastMove(i);
			Move aim = getLastMove(i+1);
			if (aim == UndoMove.NullMove
				|| oppm.getPiece() != p)
				return true;
			if (!Grid.isAdjacent(oppm.getTo(), aim.getTo())
				&& !Grid.isAdjacent(oppm.getFrom(), aim.getTo()))
				return true;
		}
		p.setMoves(SUSPECTED_RANK_AGING_DELAY);
		return false;
	}

    protected void genUnknownWeakRankAtLarge()
    {
        // The number of expendable ranks still at large
        // determines the risk of discovery of low ranked pieces.
        // If there are few expendable pieces remaining,
        // the AI can be more aggressive with its unknown low ranked
        // pieces.
        for (int c = RED; c <= BLUE; c++) {
                nUnknownWeakRankAtLarge[c] = 0;
                for (int r = 5; r <= 9; r++)
                        nUnknownWeakRankAtLarge[c] += unknownRankAtLarge(c, r);
        }
    }

    // Morph a piece into a suspected bomb

    // (Note: If AI allowed all the requested pieces to be bombs,
    // then AI move generation could mistakenly allow the game to end
    // if it calculated that there were no more movable pieces).

    private boolean suspectedBomb(Piece p)
    {
        assert p.getColor() == Settings.bottomColor;
        if (unknownNotSuspectedRankAtLarge(p.getColor(), Rank.BOMB) == 0)
            return false;

        p.setSuspectedRank(Rank.BOMB);
        suspectedRank[p.getColor()][Rank.BOMB.ordinal()-1]++;
        grid.clearMovable(p);
        return true;
    }

	private void setSuspectedRank(Piece p, Rank rank)
	{
		if (rank == Rank.NIL || rank == Rank.UNKNOWN)
			return;

		if (p.getRank() == Rank.UNKNOWN || p.isSuspectedRank())
			p.setSuspectedRank(rank);

		if (!maybeBluffing(p)) {
			suspectedRank[p.getColor()][rank.ordinal()-1]++;

		// If the AI believes the suspected rank is not an Eight,
		// then clear maybeEight so that that the suspected
		// rank does not win a flag bomb during tree search.
		// While risky, the AI has to make this assumption
		// to avoid losing material by a direct attack on the suspected piece.

			if (p.getRank() != Rank.EIGHT)
				p.setMaybeEight(false);
		}
	}

	// suspectedRank is based on ActingRankChase.
	// If the piece has chased another piece,
	// the ai guesses that the chaser is a lower rank
	// If there are no lower ranks, then
	// the chaser may be of the same rank (or perhaps higher, if
	// bluffing)

	protected void genSuspectedRank()
	{
		int piecesMovableOrKnown[] = new int[2];
		int unknownRank[] = new int[2];
		for (int c = RED; c <= BLUE; c++) {
            piecesInTray[c] = 0;
            piecesMovableOrKnown[c] = 0;
			flag[c] = null;
            for (int j=0;j<12;j++) {
                allRank[c][j] = Rank.getRanks(Rank.toRank(j+1));
                knownRank[c][j] = 0;
				suspectedRank[c][j] = 0;
			}

		} // c

		// subtract the tray pieces from allRank[]
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().ordinal();
			allRank[p.getColor()][r-1]--;
			piecesInTray[p.getColor()]++;
		}

		for ( int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null)
				continue;

		// reset suspected ranks to unknown
		// because these are recalculated each time

			if (p.getColor() == Settings.bottomColor
				&& p.isSuspectedRank()) {
				p.setKnown(false);
				p.setRank(Rank.UNKNOWN);
                grid.setMovable(p);
			}

			if (p.isKnown())
				knownRank[p.getColor()][p.getRank().ordinal()-1]++;
			if (p.getRank() == Rank.FLAG)
				flag[p.getColor()] = p;
			else if (p.hasMoved() || p.isKnown())
				piecesMovableOrKnown[p.getColor()]++;
		}

		genUnknownWeakRankAtLarge();

		// If all movable pieces are moved or known
		// and there is only one unknown rank, then
		// the remaining unknown moved pieces must be that rank.

		for (int c = RED; c <= BLUE; c++) {
			unknownRank[c] = 0;
			for (int r = 1; r <= 10; r++)
				if (unknownRankAtLarge(c, r) != 0) {
					if (unknownRank[c] != 0) {
						unknownRank[c] = 0;
						break;
					}
					unknownRank[c] = r;
				}

            unknownBombs[c] = unknownRankAtLarge(c, Rank.BOMB);
            remainingUnmovedUnknownPieces[c] =
                40 - piecesInTray[c] - piecesMovableOrKnown[c]; // used by weakRanks()
        }

		for (int r = 1; r < 15; r++)
			chaseRank[r] = getChaseRank(r);

		// The only piece that chases a One is a One
		chaseRank[0] = getChaseRank(1);

		for (int i = 12; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p == null
				|| p.isKnown())
				continue;

    // Initially, a surpise attack by an unknown piece is a good
    // move because the captured rank and the discovery of a strong
    // rank that recaptures (if any) exceeds the value of the unknown piece.

    // However, this becomes a poorer move as play continues
    // as weak pieces are removed from the board because
    // 1. the remaining pieces are stronger and are more likely to recapture
    // 2. players have begun to guess the rank of unknown pieces (reduced stealth)
    // 3. mobility is increased, increasing the possibility of recapture
    // 4. the ability to capitalize on rank discovery decreases

    // TBD: In reality, the decision is more complex.  Humans base
    // the risk of recapture based on guessing the setup, which indirectly
    // factors in the statistics of prior captures.  The AI does this to a limited
    // extent when blufferRisk is high.

            if (isInvincible(p) // caveat: excludes defacto invincible ranks 
                || nUnknownWeakRankAtLarge[1-p.getColor()] > 12)
                p.set(Piece.SAFE);
            else
                p.clear(Piece.SAFE);

            if (p.getColor() != Settings.bottomColor)
                continue;

			if (p.hasMoved()
				&& !p.isKnown()
				&& unknownRank[p.getColor()] != 0) {
				p.setRank(Rank.toRank(unknownRank[p.getColor()]));
				p.makeKnown();
                continue;
			}

			p.setMaybeEight(unknownRankAtLarge(Settings.bottomColor, Rank.EIGHT) != 0);

        // If the opponent is a bluffer, then the AI does not assign any suspected ranks
        // Otherwise, a bluffer could use any piece to thwart an AI attack.
        // When a bluffer is identified, the AI needs to rely on guessing piece setups
        // and not assume chasers or protectors are what they portend to be.

        // Prior to version 13, the AI believed the bluffer in the case of the Spy.
        // But this is silly because the opponent could use any handy unknown piece
        // as protection to thwart the AI.  It also can lead to horizon impacts when
        // a Spy/unknown piece combo corners the AI One.

            Rank rank = p.getActingRankChase();
			if (blufferRisk == 5
                || rank == Rank.NIL)
                continue;

			if (rank == Rank.SPY) {
				if (hasSpy(p.getColor()))
					setSuspectedRank(p, Rank.SPY);
            } else {
                Rank suspRank;
                if (!p.isRankLess())  {

        // This is the version 12.3 equivalent of the 10.1 strategy
        // where a piece that chased both a Five and an unknown
        // is probably a Five rather than a Four.
        // (see setDirectChaseRank())

                    if (rank == Rank.FIVE && p.isChasing(Rank.UNKNOWN))
                        rank = Rank.SIX;

                    suspRank = getChaseRank(rank);
                } else
                    suspRank = chaseRank[rank.ordinal()];

		// If the piece both chased and fled from the same rank,
		// it means that the piece is not dangerous to the
		// same rank, so creating a lower suspected rank
		// would be in error.  Perhaps it should acquire a
		// suspected rank of the same rank?  But because this is
		// unusual behavior, the piece stays Unknown.

                if (rank != suspRank
                    && (rank == p.getActingRankFleeLow()
                        || rank == p.getActingRankFleeHigh()))
                    continue;

		// The AI needs time to confirm whether a suspected
		// rank is bluffing.  The more the suspected rank moves
		// without being discovered, the more the AI believes it.

				setSuspectedRank(p, suspRank);
            }
		} // for

		for (int c = RED; c <= BLUE; c++) {

		// If all movable pieces have been accounted for,
		// the rest must be bombs (or the flag)

		if (remainingUnmovedUnknownPieces[c] == unknownBombs[c] + 1) {
			for (int i=12;i<=120;i++) {
				if (!Grid.isValid(i))
					continue;
				Piece p = getPiece(i);
				if (p == null
					|| p.getColor() != c
					|| p.isKnown()
					|| p.hasMoved())
					continue;

		// The remaining unmoved pieces must be bombs (or the flag)
                // Make the piece a bomb for now and immobile.
                // possibleFlag() will morph one of them into a flag.

				Rank rank = p.getRank();

				if (c == Settings.topColor)
					assert (rank == Rank.FLAG || rank == Rank.BOMB) : "remaining ai piece " + rank + " should be bomb or flag.  UnknownBombs = " + unknownBombs[c];
				else if (unknownBombs[c] != 0) {
                    p.setSuspectedRank(Rank.BOMB);
                                        grid.clearMovable(p);
				} else {
					p.setRank(Rank.FLAG);
                    p.makeKnown();
					flag[c] = p;
                    grid.clearMovable(p);
				}

			} // for

		} // all pieces accounted for

		int lowerRanks = 0;
		int lowerKnownOrSuspectedRanks = 0;
		for (int r = 1; r <= 10; r++) {

		// Another useful count is the number of opponent pieces with
		// lower rank.  If a rank has only 1 opponent piece
		// of lower rank remaining on the board,
		// then it safe for the rank to venture out,
		// because it takes two pieces of lower
		// rank to corner another piece.
		//
		// TBD: Even so, because AI's look ahead is limited,
		// it can be trapped in a dead-end or by the Two Squares
		// rule or forked with another one of its pieces.
		// This can be solved by increasing look-ahead.

			lowerRankCount[c][r-1] = lowerRanks;
			lowerRanks += rankAtLarge(c, r);

			lowerKnownOrSuspectedRankCount[c][r-1] = lowerKnownOrSuspectedRanks;
			lowerKnownOrSuspectedRanks += suspectedRankAtLarge(c, r) + knownRankAtLarge(c, r);
		}

		} // c

		// A rank becomes invincible when all lower ranking pieces
		// are gone or *known*.
		//
		// Invincibility means that a rank can attack unknown pieces
		// with the prospect of a win or even exchange.
		//
		// Even the Spy can be invincible, if other opponent
		// pieces are suspected but not known.  (If all the
		// other opponent pieces were known, then the Spy
		// would be known by default).
		//
		// TBD: if there is one unknown piece remaining
		// and the others are suspected, then the unknown piece
		// should be suspected as well, but currently is is
		// unknown.
		//
		// Invincible AI Eights, Nines or the Spy in an
		// unknown exchange are EVEN (see winFight())

		for (int c = RED; c <= BLUE; c++) {
			int rank;
			for (rank = 1;rank<15;rank++)
				invincibleRank[1-c][rank-1] = false;

			for (rank = 1;rank <= 10;rank++) {
				if (lowerKnownOrSuspectedRankCount[c][rank-1] < lowerRankCount[c][rank-1])
					continue;
				invincibleRank[1-c][rank-1] = true;
			}

			for (rank = 1;rank<9;rank++)
				if (rankAtLarge(1-c, rank) != 0)
					break;
			invincibleWinRank[c] = rank-1;
		}

        // possibleFlag() must be called before possibleBomb()
        // because a flag is removed from the movable grid
        // and possibleBomb() uses the movable count
        // to determine how many bombs to suspect

		possibleFlag();
        if (unknownNotSuspectedRankAtLarge(Settings.bottomColor, Rank.BOMB) > 0)
            possibleBomb(Settings.bottomColor);

	}

	// Scan the board for isolated unmoved pieces (possible bombs).
	// If the piece is Unknown and unmoved
	// (and if the AI does not already suspect
	// the piece to be something else, usually a Flag),
	// reset the piece rank to Bomb so that the AI pieces
	// will not want to attack it.

	// The AI considers isolated unmoved pieces to be bombs.
    // A piece is isolated if all of its forward and side neighbors
    // are known or have moved.   Thus an isolated piece may
    // still have an adjacent unknown piece directly below it.
	//
	// So one way to fool the AI is to make the flag
	// isolated on the front rank (by moving all pieces that
	// surround it) and then the AI will not attack it until
	// it is the last piece remaining.  I doubt that few
	// opponents will ever realize this without reading this code.

	private void possibleBomb(int c)
	{
        // Front row pieces in lanes that do not move
        // may be bombs, or at least unlikely to move,
        // so should be attacked with 7-9 to confirm.

        final int lanes[] = { 78, 79, 82, 83, 86, 87 };
        Piece tp1 = null;
        Piece tp2 = null;
        int frontMoved = 0;
        for (int lane : lanes) {
            Piece tp = getPiece(lane);
            if (tp == null
                || tp.getRank() != Rank.UNKNOWN
                || tp.hasMoved())
                frontMoved++;
            else {
                tp2 = tp1;
                tp1 = tp;
            }
        }

        if (frontMoved == 4) {
                suspectedBomb(tp1);
                suspectedBomb(tp2);
        } else if (frontMoved == 5) {
                suspectedBomb(tp1);
        }

		for (int i = 78; i <= 120; i++) {
			if (!Grid.isValid(i))
				continue;

        // If the square is below the lakes,
        // wait until the One or Two are discovered or suspected
        // because often these low ranks hide below the lakes
        // and do not move unless needed.

            if ((i == 80 || i == 81 || i == 84 || i == 85
                || i == 91 || i == 92 || i == 95 || i == 96)
				&& lowerRankCount[c][2] - lowerKnownOrSuspectedRankCount[c][2] == 2)
                            continue;

			Piece tp = getPiece(i);
			if (tp != null
				&& tp.getRank() == Rank.UNKNOWN
				&& !tp.hasMoved()) {
				int found = 0;
				for ( int d : dir ) {
					int j = i + d;
					if (!Grid.isValid(j))
						continue;
					Piece p = getPiece(j);

					if (p != null
						&& !(p.hasMoved()
							|| p.isKnown()))
						found++;
				}

        // transform the piece into a suspected bomb.

				if (found == 0)
                    suspectedBomb(tp);
			}
		}
	}

	// Usual flag locations are on the back row, and usually
	// in the corners or beneath the lakes.
	// F -- F F -- -- F F -- F

	protected boolean usualFlagLocation(int color, int i)
	{
		int x = Grid.getX(i);
		if (Grid.getY(i) != Grid.yside(color, 0))
			return false;
		return (x == 0
            || x == 2
            || x == 3
            || x == 6
            || x == 7
            || x == 9);
	}

    protected int bombedLane(int color, int lane)
    {
        int i = Grid.getIndex(lane*4, Grid.yside(color, 3));
        Piece p1 = getPiece(i);
        Piece p2 = getPiece(i+1);
        return (
            (p1 != null && p1.getRank() == Rank.BOMB ? 1 : 0) +
            (p2 != null && p2.getRank() == Rank.BOMB ? 1 : 0));
    }

	// Favor the foray lane with the most powerful unimpeded piece and lack
    // of own bombs.  This makes it difficult for the opponent to determine
    // if the AI is attacking because it has its One in the area or if
    // it is just avoiding its own bombs.

	void selectForayLane(int color)
	{
        final int attacklanes[][] = { { 0, 1, 2}, { 3, 4, 5, 6}, {7, 8, 9} };
        int [] lowrank = new int[2];

        if (weakRanks(1-color) <= 6) {
            forayLane[color] = 0;
            return;
        }

		int maxPower = -99;
        int newlane = 0;
		for (int lane = 0; lane < 3; lane++) {
            if (bombedLane(color, lane) == 2
                || bombedLane(1-color, lane) == 2)
                continue;
            int power = 0;
            lowrank[0] = lowrank[1] = 99;
            
            for (int y = 0; y < 10; y++) {
                for (int x : attacklanes[lane]) {
                    int i = Grid.getIndex(x, y);
                    if (!Grid.isValid(i))
                        continue;
                    Piece p = getPiece(i);
                    if (p == null
                        || p.isSuspectedRank()) // could be bluffing
                        continue;

                    lowrank[p.getColor()] = Math.min(
                        lowrank[p.getColor()], p.getRank().ordinal());
                    if (p.getColor() != color)
                        continue;

		// Avoid pushing pieces and leaving unknown bombs behind
		// because then the bombs become obvious.
        // The Flag and Spy also become vulnerable with the loss
        // of other pieces compromise the defense.

                    if (!p.isKnown()
                        && (p.getRank() == Rank.BOMB
                            || p.getRank() == Rank.SPY
                            || p.getRank() == Rank.FLAG))
                        power-= Grid.yside(color,y);

        // Some intermediate pieces are needed

                    if (p.getRank() == Rank.FOUR
                        || p.getRank() == Rank.FIVE)
                        power++;

                }  // x
			} // y

        // Once a stronger opponent piece appears, its time to change
        // the foray lane (tbd: although this could be an indication of
        // a bomb structure)

            if (lowrank[1-color] < lowrank[color]) {
                if (forayLane[color] == lane + 1)
                    forayLane[color] = 0;
                continue;
            }
            power -= lowrank[color]*2;

        // Discourage forays in the center lane because they are much more
        // easily defended, but allow them rarely just to keep the
        // opponent guessing

            if (lane == 1)
                power-=2;

			if (power < maxPower)
				continue;
            newlane = lane+1;
            setForaySquares();
			maxPower = power;

		} // lane

        if (forayLane[color] == 0)
            forayLane[color] = newlane;
	}

    protected boolean isForayAttack(int color, int i)
    {
        final int[] attack = {0, 0, 4+rnd.nextInt(2), 9};
        final int[] attackalt = {0, 1, 4+rnd.nextInt(2), 8};
        return (attack[forayLane[color]] == Grid.getX(i)
            || (getRank(getPiece(attack[forayLane[color]], Grid.getY(i))) == Rank.BOMB
                && attackalt[forayLane[color]] == Grid.getX(i)));
    }

    protected boolean isForayLane(int color, int i)
    {
        final boolean[][] defend = {
            { false, false, false, false, false, false, false, false, false, false },
            { true, true, false, false, false, false, false, false, false, false },
            { false, false, false, false, true, true, false, false, false, false },
            { false, false, false, false, false, false, false, false, true, true }
        };
        return defend[forayLane[color]][Grid.getX(i)];
    }

    // Foray Squares (FO):
    // -- xx xx -- FO |
    // -- xx xx -- FO |
    // -- -- -- -- FO |
    // -- -- -- -- FO |
    // -- -- -- -- FO |
    // FO FO FO FO FO |
    // ----------------

    void setForaySquares()
    {
        final boolean[][] goal = {
            { false, false, false, false, false, false, false, false, false, false },
            { true, true, true, true, true, false, false, false, false, false },
            { false, false, true, true, true, true, true, true, false, false },
            { false, false, false, false, false, true, true, true, true, true }
        };

        for (int c = RED; c <= BLUE; c++)
		for (int i = 12; i <=120; i++) {
            if (!Grid.isValid(i))
                continue;
            if (isForayAttack(c, i)
                || (goal[forayLane[c]][Grid.getX(i)]
                    && (Grid.yside(c, 9) == Grid.getY(i)
                        || (Grid.yside(c, 8) == Grid.getY(i)
                            && getRank(getPiece(Grid.getX(i), Grid.yside(c, 9))) == Rank.BOMB))))
                foraySquare[c][i] = true;
            else
                foraySquare[c][i] = false;
        }
    }

    protected boolean isForaySquare(int color, int i)
    {
        return (forayLane[color] != 0) && foraySquare[color][i];
    }

	// Scan the board setup for suspected flag pieces.
	//
	// This code is designed primarily to detect the opponent flag,
	// but by also scanning the AI's own pieces, it can second
	// guess the opponent where the opponent may think
	// the AI flag may be located.  This tells the AI whether
	// to step up protection on its own flag, and possibly to
	// step up protection on a alternate location in an effort
	// to create a realistic deception that the flag is at
	// the alternate location, but really isn't.

	protected void possibleFlag()
	{
		for (int c = RED; c <= BLUE; c++) {

	// If the flag is already known (perhaps it is the last piece
	// on the board), skip this code.

            Piece flagp = flag[c];
            if (flagp != null
                && flagp.isKnown()
                && !flagp.isSuspectedRank())
                continue;

            int [][] maybe = new int[31][];
            maybe_count[c] = 0;
            open_count[c] = 0;

            int lastbp = 0;
            for ( int[] bp : bombPattern ) {
                int[] b = new int[6];
                for ( int i = 0; bp[i] != 0; i++)
                    b[i] = Grid.side(c, bp[i]);
                flagp = getPiece(b[0]);
                if (flagp != null
                    && (!flagp.isKnown()
                        || flagp.getRank() == Rank.FLAG)
                    && !flagp.hasMoved()) {
                    boolean open = false;
                    int k;
                    for ( k = 1; b[k] != 0; k++ ) {
                        Piece p = getPiece(b[k]);
                        if (p == null
                            || (p.isKnown() && p.getRank() != Rank.BOMB)
                            || p.hasMoved()) {
                            if (getSetupRank(b[k]) == Rank.BOMB) {
                                open = true;
                                continue;
                            }
                            break;
                        }
                    } // k

                    // b[k] == 0 means possible flag structure

                    if (b[k] == 0) {
                        if (open)
                            open_count[c]++;
                        maybe[maybe_count[c]++]=b;

		// Note: Adjacent bomb structures are considered to be
		// only one structure, because it only takes one Miner
		// to penetrate.  For example:
		// -- BB BB --
		// BB B7 BF BB
		// appears as two substructures, although only one Miner
		// is necessary.  (Yet, two Miners could be required
		// if any of the outside bombs were actually pieces.)

		// Note: The four level bomb structure
		// -- -- -- BB
		// -- -- BB B7
		// -- BB B7 BB
		// BB B7 BB BF
		// would never be attacked by the AI, even if it has
		// its full complement of five Miners.  That is
		// because there are a total of six substructures.
		// Yet an experienced player knows that it really
		// only requires two Miners to penetrate, if the
		// structure is actually constructed as above.

		// But early in the game these large structures
		// may turn out to be pieces rather than bombs.
		// So the adjacent structure rule must also examine
		// the number of remaining unmoved movable pieces.

		// For example, say that the three Sevens in the
		// four level bomb structure were Blue's last three unmoved
		// movable pieces.  So remainingUnmovedUnknownPieces
		// is ten, six of which are bombs and one is the flag,
		// leaving three unknown unmoved pieces.  This allows
		// open_count to increment to three,
		// so the four level bomb structure
		// can be considered to be only three substructures,
		// and the AI will attack it if it has three or more Miners.

					if (bp[0] == lastbp + 1
						&& remainingUnmovedUnknownPieces[c] - rankAtLarge(c, Rank.BOMB) - 1 < 7 - open_count[c])
						open_count[c]++;
					lastbp = bp[0];
				}
			} // possible flag
		} // bombPattern

		if (maybe_count[c] >= 1) {

		// Pick the structure that looks most likely and
		// mark it as containing the flag.

			int bestGuess = getBestGuess(c, maybe, maybe_count[c]);
			if (c == Settings.bottomColor) {
				flag[c] = getPiece(maybe[bestGuess][0]);
				flag[c].setSuspectedRank(Rank.FLAG);
				grid.clearMovable(flag[c]);
			}

		// Mark surrounding pieces in all usual flag
		// structures as suspected bombs.

			markBombedFlag(maybe, maybe_count[c], open_count[c], bestGuess);
			for (int i = 0; i < maybe_count[c]; i++)
				if (usualFlagLocation(c, maybe[i][0]))
					markBombedFlag(maybe, maybe_count[c], open_count[c], i);
                // Call markBombedFlag yet again on bestGuess
                // to set isBombedFlag
                // (it was called first because suspected bomb allocation
                // is limited, and we want to give priority to the
                // best guess flag structure.)

			markBombedFlag(maybe, maybe_count[c], open_count[c], bestGuess);

		} else if (c == Settings.bottomColor) {

		// Player color c did not surround his flags with
		// adjacent bombs.  That does not mean the player did
		// not bomb the flag, but perhaps surrounded the flag
		// along with some lowly piece(s) like a Seven.
		// Common setups are:
		// Setup 1:
		// B - -
		// 7 B -
		// F 7 B
		//
		// Setup 2:
		// - B B -
		// B F 7 B
		//
		// These setups are attacked using the standard
		// bomb patterns.  Setup 1 contain 3 patterns.
		// This is the toughest for the AI to crack, because once
		// it removes one bomb and one Seven takes its Eight,
		// the AI will think that the remaining Seven is the Flag,
		// which is certainly possible.
		// Setup 3:
		// B - -
		// 7 B -
		// 7 F B
		//
		// Setup 2 contain 2 patterns.  If the bomb above the
		// Seven is removed and the Seven takes the Eight, the
		// AI will not recognize any bomb pattern .
		//
		// But the AI will choose which of the pieces to target
		// as a flag on the back row based on where any bombs
		// on the second to back row were removed.  The AI
		// will choose the piece directly behind the bomb or
		// next to that piece, if that piece is against the side
		// or has an unmoved or bomb piece next to it.
		// For example, in Setup 1, if the middle bomb is removed,
		// and a Seven takes an Eight, there is still
		// one matching pattern, so the AI will target the unmoved
		// Seven.
		// B - -
		// - 7 -
		// F 7 B
		//
		// But if that Seven moves, then the Flag is targeted
		// (rather than the Bomb or any other back row piece)
		// because the piece is against the side of the board.
		//
		// In Setup 2, if the bomb above the Seven is removed,
		// and the Seven takes the Eight, there are no matching
		// patterns.
		// - B 7 -
		// B F - B
		//
		// The next piece to be targeted is the Flag, because it
		// is next to another unmoved piece.
		//
		// Once this rule is exhausted,
		// go for any remaining unmoved pieces.

			flagp = null;
			for (int x=1; x <= 8; x++) {
				int i = Grid.getIndex(x, Grid.yside(c,1));
				if (getSetupRank(i) == Rank.BOMB) {
					int flagi = Grid.getIndex(x, Grid.yside(c,0));
					Piece flag = getPiece(flagi);
					if (flag != null
						&& !flag.isKnown()
						&& !flag.hasMoved()) {
						flagp = flag;
						break;
					}
					flag = getPiece(flagi-1);
					if (flag != null
						&& !flag.isKnown()
						&& !flag.hasMoved()) {
						if (x == 1) {
							flagp = flag;
							break;
						}
						Piece p = getPiece(flagi-2);
						if (p != null && (!p.isKnown() || p.getRank() == Rank.BOMB) && !p.hasMoved()) {
							flagp = flag;
							break;
						}
					}

					flag = getPiece(flagi+1);
					if (flag != null
						&& !flag.isKnown()
						&& !flag.hasMoved()) {
						if (x == 8) {
							flagp = flag;
							break;
						}
						Piece p = getPiece(flagi+2);
						if (p != null && (!p.isKnown() || p.getRank() == Rank.BOMB) && !p.hasMoved()) {
							flagp = flag;
							break;
						}
					}
				}
			}


		// Try the back rows first.
		// Choose the piece that has the most remaining
		// surrounding pieces.
		// (water counts, so corners and edges are preferred)

			for (int y=0; y <= 3 && flagp == null; y++)  {
			int flagprot = 0;
			for (int x=0; x <= 9; x++) {
				int i = Grid.getIndex(x, Grid.yside(c,y));
				Piece p = getPiece(i); 
				if (p != null
					&& !p.isKnown()
					&& !p.hasMoved()) {
					int count = 0;
					for (int d : dir) {
						Piece bp = getPiece(i+d);
						if (bp == null
							|| bp.getColor() == 1 - c)
							continue;
						if (!Grid.isValid(i+d))
							count++;
						else if (bp.hasMoved())
							count+=2;
						else
							count+=3;
					}
					if (flagprot == 0 || count > flagprot) {
						flagp = p;
						flagprot = count;
					}
				}
			}
        }

        // flagp is null if the AI has just captured the flag
        // with the last move.
        // assert flagp != null : "Well, where IS the flag?";

			if (flagp != null) {
				flag[c] = flagp;
				flagp.setSuspectedRank(Rank.FLAG);
				grid.clearMovable(flagp);
                for (int d : dir) {
                    Piece p = getPiece(flagp.getIndex()+d);
                    if (p != null
                        && p.getColor() == c
                        && !p.isKnown()
                        && !p.hasMoved())
                        suspectedBomb(p);
                }
			}

		} // maybe == 0 (opponent flag only)

		} // color c
	}

	int getBestGuess(int color, int[][] maybe, int maybe_count)
	{
		int bestProb = 0;
		int bestGuess = 99;

        // For starters, the flag is guessed to be in the foray lane
        // because thats where the action is headed, and we don't
        // want to be mislead elsewhere until we know its not there.

        selectForayLane(1-color);

		for (int i = 0; i < maybe_count; i++) {

            int prob = 1;

        // ensure isBombedFlag is set correctly for AI

            if (color == Settings.topColor
                && maybe[i][0] == flag[Settings.topColor].getIndex())
                return i;

		// compute the number of bombs in the structure

			for (int nb = 1; maybe[i][nb] != 0; nb++) {

                int j = maybe[i][nb];
				Piece p = getPiece(j);

		// A back row flag structure that had a bomb removed
        // is the best guess.

				if (p == null
                    || p.hasMoved()) {
                    if (Grid.yside(color, Grid.getY(maybe[i][0])) == 0)
                        return i;
                    continue;
                }

		// If the one of the possible bombs has a chase rank
		// (meaning that it protected some piece under attack),
		// it is much less likely that the piece is actually
		// a bomb, unless the player is a bluffer.

				if (!p.isKnown()
					&& p.getActingRankChase() != Rank.NIL) {
					prob--;
					break;
				}

                if (isForayAttack(1-color,j))
                    prob++;
			}

		// The AI examines the opponent setup for possible sentries
		// left near the structures.  If the opponent had guarded
		// the structure with low ranked pieces, the AI
		// believes that it is more likely to be the flag structure.

		// Note: If there is a corner bomb structure and
		// a 3 bomb structure, the 3 bomb structure normally
		// contains the flag because often the corner bomb
		// structure is a ruse because it is difficult to defend.
		// However, the corner bomb structure could be inside
		// a layered corner bomb structure.  In this case,
		// the corner bomb structure probably has the flag.
		// So the AI looks for a combination of small size and
		// large number of defenders.

		// Note: Until Version 11, the AI looked at
		// piece movements around the structures.  But this
		// often led to the flag structure alternating as
		// pieces moved on the board.  Thus the AI has to pick
		// a structure and not be led off by piece movements,
		// no matter how convincing, because bluffers can
		// be very convincing.

		// Although the flag is most likely on the back row,
        // the AI attacks the front row patterns first,
        // which may be necessary to get at the back row pattern.

            if (usualFlagLocation(color, maybe[i][0])) {
                int guards = 1;
                for (int j = 12; j <= 120; j++)
                    if (Grid.steps(maybe[i][0], j) == 2) {
                        Piece p = getSetupPiece(j);
                        if (p != null
                            && p.getColor() == color
                            && p.getRank() != Rank.BOMB) {
                            if (p.getRank().ordinal() <= 3)
                                guards += 2;
                            else if (p.getRank() != Rank.NINE)
                                guards++;
                        }
                    }

                prob = prob * guards;
            }

			if (bestGuess == 99
				|| prob > bestProb) {
				bestGuess = i;
				bestProb = prob;
			}

		} // i
		return bestGuess;
	}

    // Search for the most accessible possible bomb in a compound bomb structure

    protected boolean isPieceLocked(Piece lp)
    {
        for (int d : dir) {
            int i = lp.getIndex() + d;
			if (!Grid.isValid(i))
				continue;
            Piece p = getPiece(i);
            if (p == null
                || p.hasMoved()
                || p.getColor() != lp.getColor()
                || p.isKnown() && p.getRank() != Rank.BOMB)
                return false;
        }
        return true;
    }

    protected Piece getFlagBomb(Piece p)
    {
        int color = p.getColor();
        int index = p.getIndex();
        int dir = (color == Settings.topColor ? 11 : -11);
        if (Grid.getY(index) < Grid.yside(color, 4) && isPieceLocked(p))
            return getFlagBomb(getPiece(index + dir));
        return p;
    }

	private void markBombedFlag(int[][] maybe, int maybe_count, int open_count, int bestGuess)
	{
		int flagi = maybe[bestGuess][0];
		Piece flagp = getPiece(flagi);
		int color = flagp.getColor();

        assert maybe_count >= 1 : "markBombedFlag invalid maybe_count " + maybe_count;

		int eightsAtLarge = rankAtLarge(1-color, Rank.EIGHT);

		// Eights begin to attack bomb structures as the
		// number of possible structures diminish
		boolean eightAttack = (maybe_count <= 3
			|| maybe_count - open_count <= eightsAtLarge);

		// It doesn't matter if the piece really is a bomb or not.
		isBombedFlag[color] = true;
		for (int j = 1; maybe[bestGuess][j] != 0; j++) {
			assert Grid.isValid(maybe[bestGuess][j]) : maybe[bestGuess][j] + " is not valid ";
			Piece p = getPiece(maybe[bestGuess][j]);
			if (p == null
				|| !(p.getApparentRank() == Rank.BOMB
					|| p.getApparentRank() == Rank.FLAG
					|| p.getApparentRank() == Rank.UNKNOWN)
				|| p.hasMoved()) {
				isBombedFlag[color] = false;
				continue;
			}

			if (color == Settings.bottomColor) {

		// Note: the AI marks the pieces surrounding
		// the suspected flag as suspected bombs.  The AI
		// needs to be relatively confident that these
		// pieces are bombs, because it will risk approaching
		// these pieces if the reward is large enough.
		// (See riskOfLoss()).

				if (eightAttack
					&& !p.isKnown()) {

		// If there is only 1 pattern left, the AI goes out
		// on a limb and decides that the pieces in the
		// bomb pattern are known bombs.  This means
		// they offer no protective value to pieces that
		// bluff against lower ranked pieces. 

                    Piece b = getFlagBomb(p);

					if (maybe_count == 1) {
						if (suspectedBomb(b))
                            b.setKnown(true);

		// If the piece already has a suspected rank, keep it,
		// otherwise make it a suspected bomb

					} else if (b.getRank() == Rank.UNKNOWN) {
						suspectedBomb(b);
                    }
				}
			} else {

		// color is AI
        // Note that the remaining structure may not have
        // the flag in the expected position or even in
        // the structure at all, yet the opponent will still
        // assume it does.  For example,
        // |---------
        // | b6 bb bf
        // | -- b8 --
        // contains the flag, but in an unexpected position,
        // so the flag is relatively safe from attack, at least from
        // non-Miners.  Yet it any bombs are in the expected
        // position, they are assumed known.

				if (p.getRank() == Rank.BOMB
					&& maybe_count == 1) {
					p.makeKnown();
				}
			} // color is AI
		} // j

		// If the AI setup is a ruse where the flag is outside
		// of the last potential bomb structure, clear isBombedFlag.

        if (color == Settings.topColor
            && flagi != flag[color].getIndex())
            isBombedFlag[color] = false;
	}
	// ********* end of suspected ranks

	public boolean move(Move m)
	{
		if (getPiece(m.getTo()) != null)
			return false;

		if (validMove(m.getMove()))
		{
            lock.lock();
			Piece fp = getPiece(m.getFrom());
			moveHistory(fp, null, m.getMove());

			clearPiece(m.getFrom());
			setPiece(fp, m.getTo());
			fp.setMoved();
			if (Math.abs(m.getToX() - m.getFromX()) > 1 || 
				Math.abs(m.getToY() - m.getFromY()) > 1) {
				//scouts reveal themselves by moving more than one place
				fp.setShown(true);
				if (fp.getRank().ordinal() <= 4)
					guess(false);
				fp.setRank(Rank.NINE);
				fp.makeKnown();
			}
			genChaseRank(fp.getColor());
			genFleeRank(fp, null);	// depends on suspected rank
            updateSafe(fp);
            lock.unlock();
			return true;
		}
		
		return false;
	}

	public void moveToTray(Piece p)
	{
		p.kill();

		remove(p);
	}
	
	public boolean remove(int i)
	{
		Piece p = getPiece(i);
		if (p == null)
			return false;
		remove(p);
		clearPiece(i);
		return true;
	}

	public void remove(Piece p)
	{
		tray.add(p);
		Collections.sort(tray,new Comparator<Piece>(){
				     public int compare(Piece p1,Piece p2){
					return p1.getRank().ordinal() - p2.getRank().ordinal();
				     }});
	}

	public boolean remove(Spot s)
	{
		return remove(grid.getIndex(s.getX(), s.getY()));
	}

	// Three Moves on Two Squares: Two Square Rule.
	//
	// (ISF 10.1)
	// It is not allowed to move a piece more than 3
	// times non-stop between the same two
	// squares, regardless of what the opponent is doing.
	// It does not matter whether a piece is
	// moving and thereby attacking an opponent‟s
	// piece, or just moving to an empty square.
	//
	// Thus, even if the to square is becomes occupied
	// by an opponent piece, the player is not allowed
	// to attack it, if it would cause this rule to be violated.
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
			if (prev == UndoMove.NullMove)
				return false;
			UndoMove prevprev = undoList.get(size-6);
			if (prevprev == UndoMove.NullMove)
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
	// xx xx xx    xx xx xx     xx xx xx
	// -- R3 R?    -- R3 R?     -- R3 R?
	// -- -- --    -- -- B2     B2 -- --
	// -- B2 --    -- -- --     -- -- --
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
		if (m2 == UndoMove.NullMove)
			return false;

		int from = Move.unpackFrom(m);
		int to = Move.unpackTo(m);

		// not back to the same square?
		if (m2.getFrom() != to)
			return false;

		// was a capture?
		// For example,
		// -- R6 -- --
		// xx B5 R4 xx
		// xx -- -- xx
		// -- -- B3 --
		// Blue Three moves up to attack Red Four.  R4xB5.
		// Blue Three moves left.  Return false to allow
		// Red Four to return to its initial square.
		// (Otherwise Red Four would not take Blue Five
		// and flee upwards instead).
		if (m2.tp != null)
			return false;

		Piece fp = getPiece(from);
		// not the same piece?
		if (!m2.getPiece().equals(fp))
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

		// Test for three squares (which is allowed)

		// Version 9.6 allowed the move if the third square
		// was occupied by an opponent piece, because then
		// the chased piece can avoid a two squares ending
		// by attacking the opponent piece.
		//
		// Version 9.7 commented that line of code out:
		//	|| p.getColor() == 1 - m2.getPiece().getColor()
		// because the opponent piece *could* be a bomb.
		// Alternatively, the code could be qualified by
		//	&& p.getRank() != Rank.BOMB))

		Piece p = getPiece(to + to - from);
		if (p == null)
			return false;


		UndoMove oppmove1 = getLastMove(1);
		if (oppmove1 == UndoMove.NullMove)
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
		if (from - (to - from) == oppmove1.getTo())
			return false;

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
		//
		// Compare with the following two squares attack.
		//
		// |-- R3 --
		// |-- -- --
		// |-- B4 B?
		// |-- -- B?
		// |xxxxxxxxxxx
		// Blue Four moves to the left.
		// Now if Red Three moves to the left, and
		// then Blue Four tries to move back right,
		// isPossibleTwoSquares should return true.
		// (Thus Blue Four is seen to be trapped in very few ply).

		if (!Grid.isAlternatingMove(m, oppmove1))
			return false;

		// A move is forced only if the subject piece could be in any danger.
		// For example,
		// |-- -- -- -- --
		// |-- -- -- -- --
		// |-- B4 -- -- R6
		// |BF BB R8 -- --
		// |xxxxxxxxxxxxxx
		// Blue Four moves up.  Red Six moves up.  Now if Blue Four
		// tries to move back down, its move was not forced, and
		// isPossibleTwoSquares should return false.
		//
		// Although the obvious move for Red is to play 8XB when Blue Four
		// moves up, if it thought Blue would not move back,
		// it might try to delay the capture.   This has happened in play.
		//
		// Note that the following check does not handle all cases.

		if (!isThreat(oppmove1.getPiece(), fp))
			return false;

		UndoMove oppmove3 = getLastMove(3);
		if (oppmove3 == UndoMove.NullMove)
			return false;

		// player began two moves before opponent?

		if (!(oppmove3.getFrom() == oppmove1.getTo()
			&& oppmove3.getTo() == oppmove1.getFrom()))
			return true;

		UndoMove m4 = getLastMove(4);
		if (m4 == UndoMove.NullMove)
			return false;

		if (!(m4.getFrom() == m2.getTo()
			&& m4.getTo() == m2.getFrom()))
			return false;

		UndoMove oppmove5 = getLastMove(5);
		if (oppmove5 == UndoMove.NullMove)
			return false;

		return !(oppmove5.getFrom() == oppmove3.getTo()
			&& oppmove5.getTo() == oppmove3.getFrom());

	}

	public boolean isChased(int i)
	{
		UndoMove oppmove = getLastMove(1);
		if (oppmove == UndoMove.NullMove)
			return false;
		return Grid.isAdjacent(oppmove.getTo(), i);
	}

	// Returns true if both moves are between the same row
    // or column.
    // For example,
    //   (a)        (b)
    // -- -- --    B1 -- 
    // B1 R2 --    R2 --
    //             -- --
    // In (a), If Red Two moves up and then Blue One moves up, this
    // function returns true.  It also returns true if R2 moves right
    // and B1 chases.  This is useful in examining
    // the potential for a Two Squares ending.
    public boolean isPossibleTwoSquaresChase()
    {
        UndoMove m1 = getLastMove(1);
		UndoMove m2 = getLastMove(2);
		if (m1 == UndoMove.NullMove
            || m2 == UndoMove.NullMove)
			return false;

        // If the chase piece is attacked enroute, it remains "safe".
        // For example,
        // -- -- b2 -- -- --
        // -- r? xx xx -- --
        // -- R3 xx xx -- --
        // r? r? --
        // Unknown Blue Two should approach unknown Red.  After r?xb2
        // Blue Two is known, but it is still safe, and can continue
        // a two squares attach on Red Three.

        if (m2.tp == m1.getPiece())
            return true;

		return (m1.getFromX() == m2.getFromX()
			&& m1.getToX() == m2.getToX()
			&& (m1.getFromX() != m1.getToX()    // alternates
                || m1.getFromY() -  m2.getFromY() == m1.getToY() - m2.getToY()))  // OR chases
			|| (m1.getFromY() == m2.getFromY()
			&& m1.getToY() == m2.getToY()
			&& (m1.getFromY() != m1.getFromY()  // alternates
                || m1.getFromX() -  m2.getFromX() == m1.getToX() - m2.getToX()));  // OR chases
    }

    // Returns true if a piece moves back and forth between two squares,
    // and the opponent move is alternating.

    // For example,
    //   (a)     (b)    (c)     (d)
    // -- r2    r2 --   -- B3   B3 --
    // B3 --    -- B3   r2 --   -- r2
    // In position (a), if unknown Red Two moves left and Blue Three moves right,
    // it results in position b.  Next, if Red Two moves right, it would
    // allow Blue Three to reach its original square.  This is a pointless chase,
    // detectable in just 3 ply.

    // Note that if Red Two moves down in example (b) and reaches position (c),
    // the chase might not be pointless if the original Red Two square is guarded
    // or Red Two could fork another piece.
    // These kinds of worthwhile situations are difficult to predict positionally.
    // For example,
    // -- R1 --
    // -- r2 xx
    // B3 -- xx
    // B4 -- --

    // Note that if Red Two moves to the right in position (c), it would create
    // position (d).   The circular sequence of moves (a,b,c,d) is pointless because
    // (d) can be reached directly from (a).  This is detectable in 5 ply.

    // Pointless chases must be eliminated because of the horizon effect.
    // For example, there may be an eventual AI loss of a piece on the board, but
    // the AI will use successive chase moves to push it out past the horizon.
    // often to its detriment.  In the example above, the chase results in
    // the loss of stealth by unknown Red Two.

    // Note that QS eliminates the horizon effect from an *imminent* loss of a
    // piece, because QS evaluates all adjacent captures.  But QS does not evaluate
    // these chase sequences (TBD: although it could).

    // Note that isRepeatedPosition() also detect a pointless chase, but at
    // 5 ply.  It detects any repeated position, including non-chase moves.

    // Note that isAlternatingMove() ignores intervening pieces or lakes for speed,
    // which does not seem to matter, because almost all back and forth moves are pointless
    // anyway.   (Yet there are a some non-alternating back and forth moves
    // that are not pointless but it is difficult to positionally determine these.)
    // For example,
    // -- -- R1 --
    // -- xx xx --
    // -- xx xx --
    // -- B2 -- --
    // Red One moves left and Blue Two moves right, it is pointless for Red One to move
    // back because Blue Two can move back.  Note that if Red does not want to
    // be blocked from moving back, it should not make the left move in the first place!

    // So while isPointlessChase() reduces the number of ply to detect pointless moves,
    // it still requires several ply to detect a circular chase and only has impact
    // given adequate search depth.

    public boolean isPointlessChase(int m)
    {
        Move m1 = getLastMove(1);   // opp
		Move m2 = getLastMove(2);   // ai
		if (m1 == UndoMove.NullMove
            || m2 == UndoMove.NullMove)
			return false;

        // if a piece moves back and forth between two squares
        // and the opponent move is alternating,
        // the move is pointless

        if (Move.unpackTo(m) == m2.getFrom()
            && Move.unpackFrom(m) == m2.getTo()
            && Grid.isAlternatingMove(m1.getMove(), m2))
            return true;

        // if the same piece chases after alternating twice,
        // the move is pointless

        if (getPiece(Move.unpackFrom(m)) == m2.getPiece()
            && Move.unpackTo(m) == m1.getFrom()) {   // chase
            Move m3 = getLastMove(3);   // opp
            if (m3 == UndoMove.NullMove)
                return false;
            if (m1.getPiece() == m3.getPiece()
                && Grid.isAlternatingMove(m3.getMove(), m2)) {
                Move m4 = getLastMove(4);   // ai
                if (m4 == UndoMove.NullMove)
                    return false;
                if (m2.getPiece() == m4.getPiece()
                    && Grid.isAlternatingMove(m4.getMove(), m3))
                    return true;
            }
        }

        return false;
    }

    // Reoturns the number of sequential chase moves
    // (for use in the transposition table)
    public int twoSquaresChases()
    {
        Move m1 = null;
        for (int i = 0; i < 4; i++) {
            Move m2 = getLastMove(i+1);
            if (m2 == UndoMove.NullMove)
                return 0;
            if (i != 0
                 && (!(m1.getFromX() == m2.getFromX()
                    && m1.getToX() == m2.getToX())
                    || (m1.getFromY() == m2.getFromY()
                    && m1.getToY() == m2.getToY())))
                    return i-1;
            m1 = m2;
        }
        return 3;
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
	// Note: Piece does not need to be adjacent for a chase.
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
	//
	// Note: A chase can begin without moving the chase piece.
	// For example,
	// -- R9 --
	// -- R4 --
	// -- -- --
	// -- -- xx
	// -- BS xx
	// Red Four moves right.  Blue Spy moves left.  Red Nine
	// can eventually capture Blue Spy by moving to the left.

	public boolean isTwoSquaresChase()
	{
		if (!isPossibleTwoSquaresChase())
			return false;

		int m = getLastMove(1).getMove();
		Move m2 = getLastMove(2);
		if (m2 == UndoMove.NullMove)
		 	return false;

		Move m3 = getLastMove(3);
		if (m3 == UndoMove.NullMove)
			return false;

		// If opponent piece does not move between same two squares,
		// then this move cannot result in a two squares victory.
		UndoMove m4 = getLastMove(4);
		if (m4 == UndoMove.NullMove)
		 	return false;

		if (m2.getTo() != m4.getFrom()
			|| m2.getFrom() != m4.getTo())
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

		UndoMove m5 = getLastMove(5);
		if (m5 == UndoMove.NullMove)
			return false;

		Move m6 = getLastMove(6);
		if (m6 == UndoMove.NullMove)
			return false;

		// (1) If the proposed move and the move four plies ago
		// are equal,
		// (2) and the current position and the position four plies ago
		// are equal,
		// (3) and the prior opponent move and move five plies ago
		// are not equal,
		// (if they are equal, it means we are at position D
		// rather than at position C)
		// -- then this is a repetitive move

        // Note: versions 11-12 wrongly compared boardHistory.hash to m5.hash,
        // boardHistory.hash is like m0.  Comparisons only make sense 4 moves
        // back, m0 and m4, m1 and m5, and m2 and m6.  I am guessing that the
        // change was to fix a bug where the AI did not see a two squares combo,
        // so that bug is back again.

		if (m == m5.getMove()
			&& boardHistory.hash == m4.hash
			&& !m6.equals(m2))	// compare Move, not UndoMove
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
		return boardHistory.get();
	}

	public void undoLastMove()
	{
		int size = undoList.size();
		if (size < 2)
			return;

		boardHistory.remove();

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

		return validMove(m.getMove());
	}

	// TRUE if piece moves to legal square
	public boolean validMove(int m)
	{
		if (!Grid.isValid(Move.unpackTo(m)) || !Grid.isValid(Move.unpackFrom(m)))
			return false;
		Piece fp = getPiece(Move.unpackFrom(m));
		if (fp == null)
			return false;
		
		//check for rule: "a player may not move their piece back and fourth.." or something
		if (isTwoSquares(m))
			return false;

		switch (fp.getActualRank())
		{
		case FLAG:
		case BOMB:
			return false;
		}

		if (Move.unpackFromX(m) == Move.unpackToX(m))
			if (Math.abs(Move.unpackFromY(m) - Move.unpackToY(m)) == 1)
				return true;
		if (Move.unpackFromY(m) == Move.unpackToY(m))
			if (Math.abs(Move.unpackFromX(m) - Move.unpackToX(m)) == 1)
				return true;

		if (fp.getActualRank().equals(Rank.NINE)
			|| fp.getActualRank().equals(Rank.UNKNOWN))
			return validScoutMove(Move.unpackFromX(m), Move.unpackFromY(m), Move.unpackToX(m), Move.unpackToY(m), fp);

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
		return allRank[color][r-1]
			- knownRank[color][r-1];
	}

	public int unknownRankAtLarge(int color, Rank rank)
	{
		return unknownRankAtLarge(color, rank.ordinal());
	}

	public int knownRankAtLarge(int color, int r)
	{
		return knownRank[color][r-1];
	}

	public int knownRankAtLarge(int color, Rank rank)
	{
		return knownRankAtLarge(color, rank.ordinal());
	}

	public int rankAtLarge(int color, int rank)
	{
		return (allRank[color][rank-1]);
	}

	public int rankAtLarge(int color, Rank rank)
	{
		return rankAtLarge(color, rank.ordinal());
	}

    public boolean hasSpy(int color)
    {
            return rankAtLarge(color, Rank.SPY) != 0;
    }

	public boolean isInvincible(int color, int r) 
	{
		return invincibleRank[color][r-1];
	}

	public boolean isInvincible(Piece p) 
	{
		Rank rank = p.getRank();
		return isInvincible(p.getColor(), rank.ordinal());
	}

	public boolean isInvincibleDefender(int color, int r) 
	{
		if (r == 1 && hasSpy(1-color))
			return false;

		return isInvincible(color, r);
	}

	public boolean isInvincibleDefender(Piece p) 
	{
		Rank rank = p.getRank();
		return isInvincibleDefender(p.getColor(), rank.ordinal());
	}

    public boolean isNearOpponentFlag(int to)
    {
        return flag[Settings.bottomColor] != null &&
            Grid.steps(to, flag[Settings.bottomColor].getIndex()) <= 4;
    }

    public boolean isNearOpponentFlag(Piece p)
    {
        return isNearOpponentFlag(p.getIndex());
    }

	public long getHash()
	{
		return boardHistory.hash;
	}

	protected Piece getSetupPiece(int i)
	{
		return setup[i];
	}

	protected Rank getSetupRank(int i)
	{
		return setup[i].getApparentRank();
	}

    protected int getSetupIndex(Piece p)
    {
        for (int i = 12; i <=120; i++)
            if (setup[i] == p)
                return i;
        assert false : "piece not found in setup";
        return 0;
    }

    protected void guess(boolean guessedRight)
	{
		if (guessedRight)
			guessedRankCorrect++;
		else
			guessedRankWrong++;

		blufferRisk = BLUFFER_RANK_INIT - guessedRankCorrect/2 + guessedRankWrong;
		blufferRisk = Math.max(blufferRisk, BLUFFER_RANK_MIN);
		blufferRisk = Math.min(blufferRisk, BLUFFER_RANK_MAX);
	}

	protected void revealRank(Piece p)
	{
        boolean known = p.isKnown();

        if (p.getColor() == Settings.bottomColor
                && !p.isKnown()) {

                if (p.getRank() == Rank.SPY)
                        guess(p.getActualRank() == Rank.SPY);

        // Its hard to distinguish equal or lower ranks.
        // If the opponent uses equal or far superior unknown ranks to attack,
        // it is not bluffing, but rather suboptimal play.

        // A Five may chase a AI Five, and the AI will suspect it is a Four,
        // but it could be a Five or any lower rank.
        // A Four may chase a AI Four, and the AI will suspect it is a Three,
        // but it could be a Four or any lower rank.
        // A Three may chase an AI Three, and the AI will suspect it is a Two,
        // but it could be a Three or any lower rank.

        // On the other hand, a Three is bluffing as a One by chasing a Two.

                else if (p.getRank().ordinal() <= 4)
                        guess(p.getActualRank().ordinal() <= p.getRank().ordinal() + 1
                                || p.getActualRank() == Rank.SPY
                                        && p.getActingRankChase() == Rank.NIL);

        }
        p.revealRank();
        if (!known)
            guessRanks(p);
    }

    protected void guessRanks(Piece reveal)
    {
        // When a player's superior piece is revealed, there is
        // more to be learned than just the rank of that piece.
        // This information can be used in guessing the
        // approximate position of the remaining superior pieces and the Spy.

        // In predictable setups, the opponent separates the One
        // from the Two and the Threes from each other.

        // Simplistic, the Spy is thought to be adjacent to the Two
        // TBD: its not so simple
        // TBD: if all adjacent pieces are known, then where?

        Rank revealRank = reveal.getRank();
        if (reveal.getColor() == Settings.bottomColor)
        switch (revealRank) {
            case ONE :
            case TWO :
            case THREE :
                 {
                int i = getSetupIndex(reveal);
                for (int d : dir) {
                    int j = i + d;
                    if (!Grid.isValid(j))
                        continue;
                    Piece unk = getSetupPiece(j);
                    if (unk == null
                        || unk.getColor() != Settings.bottomColor
                        || unk.isKnown())
                        continue;
                    if (revealRank == Rank.ONE)
                        unk.setActingRankFlee(Rank.TWO);    // safe for a Two to approach
                    else if (revealRank == Rank.TWO) {
                        unk.set(Piece.LIKELY_SPY);
                        unk.setActingRankFlee(Rank.TWO);    // safe for a Two to approach
                    } else
            // safe for a Four, but maybe not a Three (could be a One)
                        unk.setActingRankFlee(Rank.FOUR);
                }
                }
                break;

            case SPY :
                for (int i=12;i<=120;i++) {
                    if (!Grid.isValid(i))
                        continue;
                    Piece p = getSetupPiece(i);
                    if (p != null
                        && p.getColor() == Settings.bottomColor)
                        p.clear(Piece.LIKELY_SPY);
                }
                break;
        }

        // The AI guesses that the opponent will be surprised by
        // the discovery of an AI superior piece and will not have
        // an adjacent defender

        else {
            boolean surprise = (revealRank.ordinal() <= 3
                && weakRanks(Settings.bottomColor) > 4);
        switch (revealRank) {
            case ONE :
            case TWO :
            case THREE :
            case FOUR :
            case FIVE :
                int i = reveal.getIndex();
                for (int d1 : dir) {
                    int j = i + d1;
                    if (!Grid.isValid(j))
                        continue;
                    Piece p = getPiece(j);
                    if (p == null
                        || p.getColor() != Settings.bottomColor
                        || p.isKnown())
                        continue;
                    p.setActingRankFlee(revealRank);

                    for (int d2 : dir) {
                        int k = j + d2;
                        if (!Grid.isValid(k))
                            continue;
                        p = getPiece(k);
                        if (p == null
                            || p.getColor() != Settings.bottomColor
                            || p.isKnown()
                            || !p.is(Piece.WEAK))
                            continue;
                        p.setActingRankFlee(revealRank);
                    }
                }

                break;
            } // switch
        } // top color
    } // end of function

} // end of file

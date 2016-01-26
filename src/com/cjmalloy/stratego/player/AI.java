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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Collections;


import javax.swing.JOptionPane;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.BitGrid;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.UndoMove;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.TTEntry;



public class AI implements Runnable
{
	public static ReentrantLock aiLock = new ReentrantLock();
	static final int MAX_PLY = 30;
	private Board board = null;
	private TestingBoard b = null;
	private CompControls engine = null;
	private PrintWriter log;
	static final int PV = 1;
	static final int DETAIL = 2;
	private int unknownNinesAtLarge;	// if the opponent still has Nines
	private ArrayList<Integer>[] rootMoveList = null;
	private Piece lastMovedPiece;

	// Static move ordering takes precedence over dynamic
	// move ordering.  All active moves (pieces adjacent to
	// opponent pieces) are considered before inactive
	// moves and scout far moves are considered last.
	// The idea is that attacks and flees will cause
	// alpha-beta cutoffs.  These are almost always among the
	// best moves because non-forced losing attacks are
	// discarded.  All moves are dynamically ordered as well.

	static final int ACTIVE = 0;
	static final int INACTIVE = 1;
	static final int FAR = 2;

	private static int[] dir = { -11, -1,  1, 11 };
	private int[] hh = new int[2<<14];	// move history heuristic
	private TTEntry[][] ttable = new TTEntry[2][2<<18]; // 262144
	private final int QSMAX = 6;	// maximum qs search depth
	int bestMove = 0;
	long stopTime = 0;
	int moveRoot = 0;
	int completedDepth = 0;
	int deepSearch = 0;

	enum MoveResult {
		TWO_SQUARES,
		POSS_TWO_SQUARES,
		CHASER,
		REPEATED,
		IMMOBILE,
		NEG,	// pointless attack (negative value)
		OK
	}

	enum MoveType {
		KM,	// killer move
		TE,	// transposition table entry
		NU,	// null move
		SGE,	// singular extension
		PR,	// pruned
		GE	// generated move
	};

	static private long twoSquaresHash;
	static {
		Random rnd = new Random();
		long n = rnd.nextLong();

                // It is really silly that java does not have unsigned
                // so we lose a bit of precision.  hash has to
                // be positive because we use it to index ttable.

		if (n < 0)
			twoSquaresHash = -n;
		else
			twoSquaresHash = n;
	}


	public AI(Board b, CompControls u) 
	{
		board = b;
		engine = u;
	}
	
	public void getMove() 
	{
		new Thread(this).start();
	}
	
	public void getBoardSetup() throws IOException
	{
		if (Settings.debugLevel != 0)
			log = new PrintWriter("ai.out", "UTF-8");
	
		File f = new File("ai.cfg");
		BufferedReader cfg;
		if(!f.exists()) {
			// f.createNewFile();
			InputStream is = Class.class.getResourceAsStream("/com/cjmalloy/stratego/resource/ai.cfg");
			InputStreamReader isr = new InputStreamReader(is);
			cfg = new BufferedReader(isr);
		} else
			cfg = new BufferedReader(new FileReader(f));
		ArrayList<String> setup = new ArrayList<String>();

		String fn;
		while ((fn = cfg.readLine()) != null)
			if (!fn.equals("")) setup.add(fn);
		
		while (setup.size() != 0)
		{
			Random rnd = new Random();
			String line = setup.get(rnd.nextInt(setup.size()));
			String[] opts = line.split(",");
			long skip = 0;
			if (opts.length > 1)
				skip = (Integer.parseInt(opts[1]) - 1) * 80;
			rnd = null;
			
			BufferedReader in;
			try
			{
				if(!f.exists()) {
					InputStream is = Class.class.getResourceAsStream(opts[0]);
					InputStreamReader isr = new InputStreamReader(is);
					in = new BufferedReader(isr);
				} else 
					in = new BufferedReader(new FileReader(opts[0]));
				in.skip(skip);
			}
			catch (Exception e)
			{
				setup.remove(line);
				continue;
			}
			
			try
			{
				for (int j=0;j<40;j++)
				{
					int x = in.read(),
						y = in.read();

					if (x<0||x>9||y<0||y>3)
						throw new Exception();
					
					for (int k=0;k<board.getTraySize();k++)
						if (board.getTrayPiece(k).getColor() == Settings.topColor)
						{
							engine.aiReturnPlace(board.getTrayPiece(k), new Spot(x, y));
							break;
						}
				}
				log(line);
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(null, "File Format Error: Unexpected end of file.", 
						"AI", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (Exception e)
			{
				JOptionPane.showMessageDialog(null, "File Format Error: Invalid File Structure.", 
						"AI", JOptionPane.INFORMATION_MESSAGE);
			}
			finally
			{
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			break;
		}
		
		//double check the ai setup
		for (int i=0;i<10;i++)
		for (int j=0;j<4; j++)
		{
			Piece p = null;
			for (int k=0;k<board.getTraySize();k++)
				if (board.getTrayPiece(k).getColor() == Settings.topColor)
				{
					p = board.getTrayPiece(k);
					break;
				}

			if (p==null)
				break;
				
			engine.aiReturnPlace(p, new Spot(i, j));
		}
		
		//if the user didn't finish placing pieces just put them on
		for (int i=0;i<10;i++)
		for (int j=6;j<10;j++)
		{
			Random rnd = new Random();
			int s = board.getTraySize();
			if (s == 0)
				break;
			Piece p = board.getTrayPiece(rnd.nextInt(s));
			assert p != null : "getBoardSetup";
				
			engine.aiReturnPlace(p, new Spot(i, j));
		}
	
		// engine.play();
	}

	public void run() 
	{
		long startTime = System.currentTimeMillis();
		aiLock.lock();
		log("Settings.aiLevel:" + Settings.aiLevel);
		log("Settings.twoSquares:" + Settings.twoSquares);
		log("blufferRisk:" + board.blufferRisk);
		stopTime = startTime
			+ Settings.aiLevel * Settings.aiLevel * 100;

		b = new TestingBoard(board);
                try
                {
		// Settings tick marks:
		// 1: .1 sec
		// 2: .4 sec
		// 3: .9 sec
		// 4: 1.6 sec
		// 5: 2.5 sec
		// etc, etc
			long t = System.currentTimeMillis() - startTime;
			long trem = stopTime - System.currentTimeMillis();
			log("Call getBestMove() at " + t + "ms: time remaining:" + trem + "ms");
			getBestMove();
		} catch (InterruptedException e) {
			log("time aborted");
                } catch (Exception e) {
			log("exception aborted");
			e.printStackTrace();
		}
		finally
		{
			long t = System.currentTimeMillis() - startTime;
			t = System.currentTimeMillis() - startTime;
			log("getBestMove() returned at " + t + "ms");
			System.runFinalization();

		// note: no assertions here, because they overwrite
		// earlier assertions

			if (bestMove == 0)
				engine.aiReturnMove(null);
			else if (bestMove == 0)
				log("Null move");
			else if (board.getPiece(Move.unpackFrom(bestMove)) == null)
 				log("bestMove from " + Move.unpackFrom(bestMove) + " to " + Move.unpackTo(bestMove) + " but from piece is null?");
			else {
				logFlush("----");
				log(PV, logMove(board, 0, bestMove));
				// return the actual board move
				engine.aiReturnMove(new Move(board.getPiece(Move.unpackFrom(bestMove)), Move.unpackFrom(bestMove), Move.unpackTo(bestMove)));
			}

			logFlush("\n----");

			long t2 = System.currentTimeMillis() - startTime;
			t2 = System.currentTimeMillis() - startTime;
			log("exit getBestMove() at " + t2 + "ms");
			aiLock.unlock();
		}
	}

	private void addMove(ArrayList<Integer> moveList, int m)
	{
		moveList.add(m);
	}

	private void addMove(ArrayList<Integer> moveList, int f, int t)
	{
		addMove(moveList, Move.packMove(f, t));
	}

	void getScoutFarMoves(int n, ArrayList<Integer> moveList, int i) {
		Piece fp = b.getPiece(i);
		for (int d : dir ) {
			int t = i + d ;
			Piece p = b.getPiece(t);
			if (p != null)
				continue;

			t += d;
			p = b.getPiece(t);

		// if next-to-adjacent square is invalid or contains
		// the same color piece, a far move is not possible

			if (p != null
				&& p.getColor() != 1 - b.bturn)
				continue;

			int vbest = -1;
			int tbest = -1;
			while (p == null) {

		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks
		// or maximum plan score.  In the following example,
		// the AI should generate the 3 move sequence
		// for Red to capture the Blue flag, but no other far moves:
		// -- RB RF RB R9
		// -- -- RB -- --
		// -- -- R7 -- --
		// -- -- -- -- --
		// -- -- xx xx --
		// -- -- xx xx --
		// -- -- -- BB --
		// -- -- -- B3 --
		// -- -- -- B7 --
		// BF BB -- -- --

				int v = b.planValue(fp, i, t);
				if (v > 0 && v > vbest) {
					tbest = t;
					vbest = v;
				}
				t += d;
				p = b.getPiece(t);
			};

		// If n is 1 and the move is not an attack,
		// then the scout move cannot be the best move
		// because it has run out of moves to reach its target.

			if (tbest > 0
				&& n > 1)
				addMove(moveList, i, tbest);

			if (p.getColor() == 1 - b.bturn
				&& b.isNineTarget(p))
				addMove(moveList, i, t);
		} // dir
	}

	// TBD: this should probably be sped up
	// - It may only be important to search two directions (up and down)
	// rather than across the board.  Almost all attacks
	// on valuable unknown AI pieces occur during the opening blitzkreig.
	// - Search from the valuable AI piece rather than from the
	// unknown opponent piece, since they are much fewer.
	// - Use a rotated bitgrid to bitscan for an attack.
	//
	// It is tempting to forward prune off these moves except at
	// depth 0, but this causes the AI to blunder material if the AI Spy
	// is unavoidably exposed.  Because the opponent
	// could only take the Spy at depth 0 and if this is the opponent
	// best move, then the AI will leave material hanging because it thinks
	// that the opponent can only take the Spy immediately rather than
	// first taking the material and then taking the Spy later.

	void getAttackingScoutFarMoves(ArrayList<Integer> moveList, int i)
	{
		for (int d : dir ) {
			int t = i + d ;
			Piece p = b.getPiece(t);
			if (p != null)
				continue;

			do {
				t += d;
				p = b.getPiece(t);
			} while (p == null);

			if (p.getColor() == 1 - b.bturn
				&& b.isNineTarget(p))
				addMove(moveList, i, t);
		}
	}

	public void getAllMoves(ArrayList<Integer> moveList, int i)
	{
		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);
			if (tp == null
				|| tp.getColor() == 1 - b.bturn)
				addMove(moveList, i, t);
		} // d
	}

	public void getAttackMoves(ArrayList<Integer> moveList, int i)
	{
		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);

			if (tp != null
				&& tp.getColor() == 1 - b.bturn)
				addMove(moveList, i, t);
		} // d
	}

	// n > 0: prune off inactive moves
	// n = 0; no pruning
	// n < 0: prune off active moves

	public boolean getMoves(int n, ArrayList<Integer>[] moveList, int i)
	{
		Piece fp = b.getPiece(i);
		Rank fprank = fp.getRank();

		if (fprank == Rank.BOMB || fprank == Rank.FLAG) {

		// Known bombs or flags are not movable pieces (see Grid.java)

			assert !fp.isKnown() : "Known " + logPiece(fp) + " " + logFlags(fp) + " at " + fp.getIndex() + " ?";

		// We need to generate moves for suspected
		// bombs because the bomb might actually be
		// some other piece, but once the bomb becomes
		// known, it ceases to move.

		// Yet an unknown bomb or suspected bomb is so unlikely to have
		// any impact (except for attack on first move),
		// even if the bomb is within the active area.
		// that it is worthwhile to omit move
		// generation for it except for attack on first move.
		// This can increase the ply during the endgame,
		// when unknown bombs dominate the remaining pieces.
		//
		// In addition, return false, to prevent the option
		// of a null move.
		//
                // Note that AI bombs are treated identically,
                // thus limiting their scope of travel to immediate
                // attack.

			if (b.grid.hasAttack(b.bturn, i))
				getAttackMoves(moveList[ACTIVE], i);
			return false;
		}

		// FORWARD PRUNING:
                // Unmoved unknown AI pieces really aren't very
                // scary to the opponent, unless they can attack
                // on their first move.  The AI has to generate
                // moves for its unmoved unknown pieces because
                // lower ranks may be needed for defense.  But it
                // is unlikely (but possible) that the piece
                // will be part of a successful attack.
                // TBD: So it may be possible to prune off unmoved
                // high rank piece moves in the active area.
                //
                // Unknown unmoved opponent pieces are also not
                // very scary to the AI.
                // Prior to version 9.3, the AI didn't generate moves
                // for any unknown unmoved opponent piece unless
                // it could attack on its first move.  This effective
                // forward pruning was generalized in 9.3 to include
                // all inactive pieces in version 9.4.
                //
                // But version 9.4 suffers from a horizon effect
                // when these pieces are in the active area, but far
                // away from potential AI targets.  If the extended
                // search is not deep enough, the AI may mistakely
                // assume an attack by an unknown piece is the best
                // move, and allow some lesser attack on its pieces,
                // thus losing material.
                //
                // So in version 9.5, the AI prunes off moves by
                // any unknown opponent piece more than 1 space away, unless
                // the unknown piece was the last piece moved by
                // the opponent.
		//
		// (Note: if n <= 3, those moves are already pruned off)

		if (n > 3
			&& fprank == Rank.UNKNOWN
			&& fp != lastMovedPiece
			&& !b.grid.isCloseToEnemy(b.bturn, i, 1))
			return true;

		if (b.grid.hasAttack(b.bturn, i))
			getAllMoves(moveList[ACTIVE], i);
		else
			getAllMoves(moveList[INACTIVE], i);

		return false;
	}

	boolean genSafe(int i, boolean unsafe, BitGrid unsafeGrid)
	{
		Piece p = b.getPiece(i);
		if (p != null) {
			if (p.getColor() != Settings.bottomColor) {
				if (unsafe
					&& p.getColor() == Settings.topColor
					&& b.isNineTarget(p))
					return true;
				return false;
			} else if (p.getRank() != Rank.UNKNOWN
				&& p.getRank() != Rank.NINE)
				genSafe(i - 11, false, unsafeGrid);
			else
				genSafe(i - 11, true, unsafeGrid);
		} else if (genSafe(i - 11, unsafe, unsafeGrid)) {
			unsafeGrid.setBit(i);
			return true;
		}
		return false;
	}

	void genSafe(BitGrid unsafeGrid)
	{
		final int[] lanes = { 111, 112, 115, 116, 119, 120 };
		for (int lane : lanes)
			genSafe(lane, false, unsafeGrid);
	}

	private boolean getMovablePieces(int n, BitGrid out)
	{
		// FORWARD PRUNING
		// deep chase search
		// Only examine moves where player and opponent
		// are separated by up to MAX_STEPS.

		// What often happens in a extended chase,
		// is that the chased piece approaches one of its other
		// pieces, allowing the chaser to fork the two pieces.
		// For example,
		// -- -- R3 -- -- --
		// -- -- B2 -- -- R5
		// -- -- -- -- -- R4
		// Red Three has been fleeing Blue Two.
		// If Red Three moves right, Blue Two will be
		// able to fork Red Five and Red Four.
		// But as the search continues, the AI will evaluate
		// Blue Two moving even further right, because only
		// one open square separates Blue Two from Red Five.
		// Thus, Red will move left.

		// If the Red pieces were more than one open square
		// away, Blue two could still try to approach them,
		// but Red has an extra move to respond, and usually
		// it is able to separate the pieces eliminating
		// the fork.

		// By pruning off all other moves on the board
		// that are more than one open square away from
		// an opponent piece, the AI search depth doubles,
		// making it far less likely for the AI to lose
		// material in a random chase.
		// (More often what happens is that the opponent
		// piece ends the chase, and then
		// the AI makes a bad move using the broad search,
		// such as heading into a trap beyond the search
		// horizon, and then the chase resumes.)

		int ns = Math.abs(n);
		if (deepSearch != 0)
			ns = Math.min(ns, deepSearch);

		// NOTE: FORWARD TREE PRUNING
		// If a piece is too far to reach the enemy,
		// there is no point in generating moves for it,
		// because the value is determined only by pre-processing,
		// (unless an unknown valuable piece can be attacked
		// from far away by a Nine or the player piece is absolutely
		// necessary for defense of an immobile piece or a clump
		// of high ranked pieces. Otherwise the enemy is too far
		// away to result in material change for a one-on-one
		// attack.  (It almost always takes more than one attacker
		// or a clump of defenders to affect material balance).
		// For example,
		// RF RB -- RB
		// RB -- -- R7
		// -- -- -- --
		// -- -- -- --
		// -- -- xx xx
		// B8 -- xx xx
		//
		// Red has the move.  But no moves will be generated
		// for it because it is too far from Blue Eight
		// to result in a material change, i.e. R7xR8 is
		// avoidable.
		//
		// But Blue Eight is threatening to attack the bomb
		// structure. And obviously the bombs and flags cannot
		// simply run away.
		//
		// Pre-processing determined that R7 moving left
		// is essential to protect the bomb structure, so
		// assigned the highest pre-processing value.  This will
		// mean that the move will be picked as the best
		// pruned off move and then added to the root move list.

		// If a valuable piece can be attacked by a far scout move,
		// it should be allowed to flee (or another
		// piece should be allowed to block).  The difficulty
		// is how to determine if the valuable piece is vulnerable.
		//
		// Prior to version 9.6, all moves by valuable pieces
		// were allowed:
		// 	allowAll = allowAll || (fpcolor == Settings.topColor
		//			&& unknownNinesAtLarge > 0
		//			&& b.isNineTarget(fp)));
		//
		// This is a significant waste of time if the piece
		// isn't vulnerable, for instance, a valuable piece
		// on the back row with other pieces in front.
		// This code also did not consider blocking moves.  And
		// it pointless to react to a future scout far move
		// when n=1, because qs() does not consider scout far moves.

		// In version 9.6, a BitGrid of the squares that can
		// be attacked by opponent scouts is created when n >= 2.
		// The algorithm was slightly modified for speed in 9.10.
		// Now all moves for any piece adjacent to these squares are
		// considered.  This includes a valuable piece
		// moving to a safe square or a non-valuable piece moves
		// to a non-safe square (blocking move).

		BitGrid unsafeGrid = new BitGrid();
		if (b.bturn == Settings.topColor
			&& unknownNinesAtLarge > 0
			&& ns >= 2)
			genSafe(unsafeGrid);

		// Scan the board for movable pieces inside the active area

		if (n == 0)
			b.grid.getMovablePieces(b.bturn, out);
		else if (n > 0) {
			b.grid.getMovablePieces(b.bturn, ns, unsafeGrid, out);
			BitGrid bgp = new BitGrid();
			b.grid.getPrunedMovablePieces(b.bturn, ns, unsafeGrid, bgp);
			if (bgp.get(0) != 0 || bgp.get(1) != 0)
				return true;
		} else {
			b.grid.getPrunedMovablePieces(b.bturn, ns, unsafeGrid, out);
		}
		return false;
	}

	private boolean getMoves(BitGrid bg, ArrayList<Integer>[] moveList, int n)
	{
		for (int i = 0; i <= FAR; i++)
			moveList[i] = new ArrayList<Integer>();

		boolean isPruned = false;
		for (int bi = 0; bi < 2; bi++) {
			int k;
			if (bi == 0)
				k = 2;
			else
				k = 66;
			long data = bg.get(bi);
			while (data != 0) {
				int ntz = Long.numberOfTrailingZeros(data);
				int i = k + ntz;
				data ^= (1l << ntz);

				if (getMoves(n, moveList, i))
					isPruned = true;
			} // data
		} // bi

		return isPruned;
	}

	private boolean getMoves(ArrayList<Integer>[] moveList, int n)
	{
		BitGrid bg = new BitGrid();
		boolean isPruned = getMovablePieces(n, bg);
		return getMoves(bg, moveList, n) || isPruned;
	}

	private void getScoutMoves(ArrayList<Integer> moveList, int n, int turn)
	{
		// TBD: check for a valuable AI suspected rank;
		// if there is no suspected AI rank remaining,
		// then skip AI Scout far moves

		// Prior to Version 9.10, the AI pruned off all AI Scout
		// far moves during a deep search.  The idea was to
		// focus the search on the superior pieces to prevent
		// a large loss of material.

		// However, the reason to consider far moves during deep search
		// is if the opponent flag is in danger, because the
		// opponent deep search attacker might be blocking access
		// to the flag.  For example:
		// -- -- xx xx R9 --
		// -- -- xx xx -- --
		// -- -- -- -- -- --
		// -- -- R8 -- -- --
		// -- -- -- -- B4 --
		// -- -- -- BB BF BB
		// The proximity of Red Eight to Blue Four invokes the
		// deep search code, because increased search depth
		// is often needed to respond to potential attacks.
		// But Blue Four is pinned in this case to Blue Flag
		// and cannot attack.
		//
		// Version 9.10 sped move generation by moving Scout moves
		// after all other moves and limited far moves
		// to attacks and the optimal pre-processor squares.
		// So the decision was made to allow AI Far moves during
		// deep search if this costs only a small time penalty.

		for (Piece fp : b.scouts[turn]) {

		// if the piece is gone from the board, continue
			int i = fp.getIndex();

			if (b.getPiece(i) != fp)
				continue;

			Rank fprank = fp.getRank();
			if (fprank == Rank.NINE)
				getScoutFarMoves(n, moveList, i);

			else if (fprank == Rank.UNKNOWN)
				getAttackingScoutFarMoves(moveList, i);
		}
	}

// Silly Java warning:
// Java won't let you declare a typed list array like
// public ArrayList<Piece>[] scouts = new ArrayList<Piece>()[2];
// and then it warns if you created a non-typed list array.
@SuppressWarnings("unchecked")
	private void getBestMove() throws InterruptedException
	{
		int tmpM = 0;
		bestMove = 0;
		int bestMoveValue = 0;
		int ncount = 0;

		// Because of substantial pre-processing before each move,
		// the entries in the transposition table
		// should be cleared to prevent anomolies.
		// But this is a tradeoff, because retaining the
		// the entries leads to increased search depth.
		//ttable = new TTEntry[2<<22]; // 4194304, clear transposition table

		moveRoot = b.undoList.size();
		deepSearch = 0;

		// chase variables
		int lastMoveTo = 0;
		Move lastMove = b.getLastMove(1);
		if (lastMove != null) {
			lastMoveTo = lastMove.getTo();
			Piece p = b.getPiece(lastMoveTo);
		// make sure last moved piece is still on the board
			if (p != null && p.equals(lastMove.getPiece()))
				lastMovedPiece = p;
		}

		// move history heuristic (hh)
		for (int j=0; j < hh.length; j++)
			hh[j] = 0;

		unknownNinesAtLarge = b.unknownRankAtLarge(Settings.bottomColor, Rank.NINE);

		completedDepth = 0;

		genDeepSearch();

		for (int n = 1; n < MAX_PLY; n++) {

			Move killerMove = new Move(null, -1);
			Move returnMove = new Move(null, -1);

			rootMoveList = (ArrayList<Integer>[])new ArrayList[FAR+1];
			boolean isPruned = getMoves(rootMoveList, n);
			if (isPruned) {

			log(DETAIL, "\n>>> pick best pruned move");

		// If any moves were pruned off, choose the best looking one
		// and then evaluate it along with the non-pruned moves
		// that could affect material balance.
		//
		// Why do this forward pruning?  If the AI
		// were to consider all moves together, then the best move
		// would be found without the rigmarole.
		// The problem is that there are just too many moves
		// to consider, resulting in shallow search depth.
		// By pruning off inactive moves, and then
		// evaluating only active moves, depth is significantly
		// increased (by 2-3 ply), making sure that the AI
		// does not easily blunder material away.
		//
		// TBD: Search depth needs to be increased further.
		// There is currently a grid bitmap for each color.
		// This could be improved to indexed on rank, where
		// each color rank bitmap contains the locations of
		// all lower ranks.  This would allow fleeing moves
		// to be generated for only pieces that should flee
		// (when the indexed rank bitmap mask is non-zero)
		// and attack/approach moves for pieces that can attack
		// (when the indexed rank bitmap mask is zero).
		//
		// Alternatively, look at the planA/B matrices to
		// determine direction.  Or use windowing.

			ArrayList<Integer>[] moveList = (ArrayList<Integer>[])new ArrayList[FAR+1];
			BitGrid bg = new BitGrid();
			getMovablePieces(-n, bg);
			getMoves(bg, moveList, -n);
			int bestPrunedMoveValue = -9999;
			int bestPrunedMove = -1;
			for (int mo = 0; mo <= INACTIVE; mo++)
			for (int move : moveList[mo]) {
				logMove(2, move, 0, MoveType.PR);
				MoveResult mt = makeMove(n, move);
				if (mt == MoveResult.OK) {

		// Note: negamax(0) isn't deep enough because scout far moves
		// can cause a move outside the active area to be very bad.
		// For example, a Spy might be far away from the opponent
		// pieces and could be selected as the best pruned move.
		// But moving it could lose the Spy to a far scout move.
		// So negamax(1) is called to allow the opponent scout
		// moves to be considered (because QS does not currently
		// consider scout moves).
		// 
					int vm = -negamax(1, -9999, 9999, killerMove, returnMove, depthValueReduction(1)); 
					if (vm > bestPrunedMoveValue) {
						bestPrunedMoveValue = vm;
						bestPrunedMove = move;
					}
					b.undo();
					log(DETAIL, " " + negQS(vm));
				} else
					log(DETAIL, " " + mt);
			}
			if (bestPrunedMove != -1) {
				addMove(rootMoveList[INACTIVE], bestPrunedMove);
				log(PV, "\nPPV:" + n + " " + bestPrunedMoveValue);
				log(PV, "\n" + logMove(b, n, bestPrunedMove));
				log(DETAIL, "\n<< pick best pruned move\n");
			}
			} // movelist

		boolean hasMove = false;
		for (int mo = 0; mo <= INACTIVE; mo++)
			if (rootMoveList[mo].size() != 0) {
				hasMove = true;
				break;
			}

		if (!hasMove) {
			log("Empty move list");
			return;		// ai trapped
		}

		log(DETAIL, "\n>>> pick best move");
		int vm = negamax(n, -9999, 9999, killerMove, returnMove, 0); 
		completedDepth = n;

		// To negate the horizon effect where the ai
		// plays a losing move to delay a negative result
		// discovered deeper in the tree, the search is
		// extended two plies deeper.
		// If the extended search cannot be completed in time,
		// the AI sticks with the result of the last acceptable ply.
		// For example,
		// B5 R4
		// B3 --
		//
		// If the ai discovers a winning sequence of moves for Blue
		// deeper in the tree, the ai would play R4xB5
		// because it assumes
		// Blue will play the winning sequence of moves.  But Blue will
		// simply play B3xR4 and play the winning sequence on its next
		// move.
		//
		// By searching two plies deeper, the ai may see that R4xB5
		// is a loss, even if R4xB4 delays the Blue winning sequence
		// past the horizon.  If the search cannot be completed
		// in time, the AI will play some other move that does not
		// lose material immediately, oblivious that Blue has a
		// winning sequence coming.  (This can only be solved by
		// increasing search depth).
		//
		// This seems to work most of the time.  But not if
		// the loss is more than two ply deeper.  For example,
		// R? R? -- R? RF R? -- -- -- --
		// -- -- -- -- -- -- -- B4 R5 --
		//
		// If Red Flag is known (the code may mark the flag
		// as known if it is vulnerable so that the search tree
		// moves AI pieces accordingly in defense), Red moves
		// one of its unknown pieces because Blue Four x Red Five
		// moves Blue Four *away* from Red Flag, causing a delay
		// of 4 ply.  Red will continue to do this until all of
		// its pieces have been lost.
		//
		// This happens not just with Red Flag, but in any instance
		// where the AI sees a loss of a more valuable piece
		// and can lose a lessor piece by drawing the attacker
		// away.  Fortunately, this does not occur in play often.

		int bestMovePly = returnMove.getMove();
		int bestMovePlyValue = vm;

		log("\n<<< pick best move");

		if (n == 1
			|| deepSearch != 0
			|| n == MAX_PLY - 1) {

		// no horizon effect possible until ply 2

			bestMove = bestMovePly;
			bestMoveValue = vm;

		} else {

		// Singular Extension.
		// The AI accepts a new best move only
		// the value of the new best move searched 2 plies deeper.
		// is better (or just slightly worse) than the current
		// best move or new best move.

			log(">>> singular extension");

			logMove(n+2, bestMovePly, b.getValue(), MoveType.SGE);
			MoveResult mt = makeMove(n, bestMovePly);
			vm = -negamax(n+1, -9999, 9999, killerMove, returnMove, depthValueReduction(1)); 
			b.undo();
			log(DETAIL, " " + negQS(vm));


		// The new move is kept until the ply deepens beyond the depth
		// of the singular extension, because at that point, the
		// value is no longer valid, because it was calculated
		// below the current ply.  Because ply 1 is not searched
		// with a singular extension, that means that ply 2 always
		// selects a new move.

			if (bestMove == bestMovePly
				|| vm >= bestMoveValue - 5
				|| vm >= bestMovePlyValue - 5
				|| ncount-- == 0) {
				bestMove = bestMovePly;
				bestMoveValue = vm;
				ncount = 2;
			} else {
				log(PV, "\nPV:" + n + " " + vm + " < " + bestMoveValue + "," + bestMovePlyValue + ": best move discarded.\n");
				log("<<< singular extension");
				continue;
			}

		}

		hh[bestMove]+=n;
		log("\n-+++-");

		log(PV, "PV:" + n + " " + vm + "\n");
		logPV(Settings.topColor, n);
		} // iterative deepening
	}

	// Quiescence Search (qs)
	// Deepening the tree to evaluate worthwhile captures
	// and flee moves.  The search ends when the position becomes
	// quiescent (no more worthwhile captures) or limited by
	// QSMAX ply.
	// 
	// I had tried to use Evaluation Based Quiescence Search
	// as suggested by Schaad, but found that it often returned
	// inaccurate results, even when recaptures were considered.
	// Ebqs returns a negative qs for the example board position
	// because it sums B3xR4 and B3xR7 and Red has no positive
	// attacks.
	//
	// I tested the two versions of qs against each other in many
	// games. Although ebqs was indeed faster and resulted in
	// comparable (but lesser) strength for a 5-ply tree, its inaccuracy
	// makes tuning the evaluation function difficult, because
	// of widely varying results as the tree deepens.  I suspect
	// that as the tree is deepened further by coding improvements,
	// the difference in strength will become far more noticeable,
	// because accuracy is necessary for optimal alpha-beta pruning.
	//
	// This version of quiescent search limits the
	// horizon effect because captures often are the best
	// moves.  This allows the AI to see the material effect
	// of future positions without having to fully expand the
	// search, effectively extending the search by QSMAX ply.
	//
	// So for example if an opponent piece is approaching
	// a clump of AI pieces, and the search detects the arrival
	// of the opponent piece adjacent to the clump, qs effectively
	// evaluates the subsequent captures at lower ply.
	//
	// But qs does not eliminate the horizon effect, because
	// the ai is still prone to make bad moves 
	// that waste more material in a position where
	// the opponent has a sure favorable attack in the future,
	// but the ai search does not reach the adjacent pieces.
	//
	// qs does not eliminate a horizon effect
	// where the ai places another (lesser) piece subject
	// to attack when the loss of an existing piece is inevitable.
	// When the AI does this, it does so because it assumes
	// that the opponent will play the moves to take the more
	// valuable piece.  This happens because the capture of
	// the lesser pieces pushes the attack on the more valuable
	// piece past the horizon.  This horizon effect can only be
	// addressed by extended search.
	// 
	// qs does not (and should not) prevent a horizon effect
	// caused by repetitive chase moves.  Thus, if a lesser
	// piece is subject to loss, the AI can try to delay the loss
	// by chasing a more valuable piece.  However, the Two Squares
	// rule should eventually kick in and the AI should see the
	// loss is inevitable.
	//
	// Chase moves can be erroneously associated with the
	// horizon effect if a chasing AI piece is also drawn towards
	// protecting its flag.  In this case, the AI is rewarded
	// greatly for keeping its chase piece near the flag and
	// thwarting movement of opponent pieces towards the flag.
	// Thus it may leave material at risk to keep its chase piece
	// near the flag.  
	//
	// TBD: Should QSMAX be even?  Should it be limited at all?
	//
	// The reason why QSMAX is even is that it superficially gives
	// the opponent a chance to respond in kind.  But this fails
	// when the player does not have a successful capture, because
	// then the player loses its turn by pushing a null move.
	// This allows the opponent to move first,
	// and then the player can be limited by QSMAX.
	//
	// For example, the player's Two is guarded by a Spy,
	// and adjacent to an opponent One.  When qs() is called,
	// the player has no winning attacks, so
	// a null move is pushed.  Then the
	// opponent plays some other losing attacking move, and then
	// another losing attacking move, and so on, until QSMAX-1 is hit;
	// then it plays 1X2 and the player is limited out by QSMAX
	// and cannot play SX1.  This results in an erroneous qs.
	//
	// The issue still exists, and needs a resolution.
	// QSMAX exists because otherwise a rogue piece
	// in a field of forty pieces could take an enormous amount of
	// CPU time.  So perhaps the number of moves a *piece* makes should
	// be limited, but the number of moves a *player* makes should
	// be unlimited.
	//
	// Another idea: allow the player to push a null move without
	// decrementing n.

	private int qs(int n, int alpha, int beta, int dvr)
	{
		int bvalue = negQS(b.getValue() + dvr);
		if (n < 1)
			return bvalue;

		BitGrid bg = new BitGrid();

		// Find the neighbors of opponent pieces (1 - b.turn)
		// (In other words, find the player's movable pieces
		// that are under direct attack).

		b.grid.getMovableNeighbors(1-b.bturn, bg);

		// As qs is defined, if there are no neighbors,
		// qs is the current value of the board

		if (bg.get(0) == 0 && bg.get(1) == 0)
			return bvalue;

		// If the opponent move was null and the player move
		// is null, then the result is the same as if neither
		// player move was null.  This can be thought of as
		// a simple transposition cache.  It also prevents
		// the search continuing ad infinitem.

		int best;
		if (b.getLastMove() == null)
			best = bvalue;
		else {
			b.pushNullMove();
			best = -qs( n-1, -beta, -alpha, dvr);
			b.undo();
		}
		alpha = Math.max(alpha, best);

		if (alpha >= beta)
			return best;

		for (int bi = 0; bi < 2; bi++) {
			int k;
			if (bi == 0)
				k = 2;
			else
				k = 66;
			long data = bg.get(bi);
			while (data != 0) {
				int ntz = Long.numberOfTrailingZeros(data);
				int i = k + ntz;
				data ^= (1l << ntz);

				Piece fp = b.getPiece(i);
				Rank fprank = fp.getRank();
				int enemies = b.grid.enemyCount(fp);

		// TBD: Far attacks by nines or unknown pieces
		// are not handled.  This would improve the
		// accuracy of qs.
		//
		// TBD: Another reason to handle far attacks is
		// the deep chase code.  It relies on qs, so
		// a known AI piece can be chased out of the
		// way to permit an attack on an unknown AI piece
		// without the AI realizing it.
		//
		// Version 9.2 cached the qs from the prior move
		// so that it could reused if the new move
		// is not adjacent to any enemy or protecting pieces,
		// thus not affecting the qs result.
		// (nines were not handled, because it would be impossible to
		// to reuse the result in subsequent moves)
		//
		// Version 9.3 removed the cache entirely because the addition
		// of forward pruning of inactive moves means that
		// all end nodes are now active moves and always affect
		// the qs result from move to move.
		//
			for (int d : dir ) {
				int t = i + d;	

				Piece tp = b.getPiece(t); // defender
				if (tp != null
					&& tp.getColor() != 1 - b.bturn)
					continue;

				if (tp == null
				 	&& (fprank == Rank.BOMB || fprank == Rank.FLAG))
					continue;

				b.move(Move.packMove(i, t));
				int v = -negQS(b.getValue() + dvr) - bvalue;

				if (tp != null && v < 0) {

		// It is tempting to skip losing captures to save time
		// (such as attacking a known lower ranked piece).
		// But it had to evaluate them because of the rare
		// case when a valuable piece is cornered and has to
		// sacrifice a lesser piece to clear an escape.
		//
		// So beginning with version 9.7,
		// all losing moves were considered in qs, because sometimes
		// an oncoming opponent piece forks two low ranked unknown
		// player pieces.  So the lesser piece must attack the
		// oncoming opponent piece to protect the stealth of
		// the superior piece.  This could even be the Two to
		// protect the One, and because the loss of the stealth
		// of the Two is substantial, the magnitude of the loss
		// cannot be used to determine whether the move would
		// be worthwhile.
		//
		// This resulted in a more accurate qs,
		// but with a heavy time penalty.  It is better to save
		// time by pruning off useless moves so that qs is never
		// even reached.
		//
		// Version 9.9 revisited this code with the
		// theory that a losing move in qs
		// is never worthwhile unless the opponent piece has
		// more than one adjacent player piece.
		//
		// This logic may actually improve qs.  For example:
		// R4 RS |
		// R7 R6 |
		// -- B5 |
		// -- -- |
		// Red has the move.  Before version 9.9, Red would play R6xB5,
		// because then Blue has no attacking moves, and qs
		// is terminated with minimal loss.  In version 9.9,
		// R6xB5 is discarded,  allowing Blue to play B5xR6,
		// then B5xRS, giving qs a very negative value.
		//
		// Note that a forked piece can also be a nonmovable
		// piece such as a bomb or flag which may require
		// a losing move to protect it.
		//
		// Version 10.0 has a new theory.  If the attacker does not
		// survive and the result is negative, then the move
		// is pointless.  This should make no difference in qs
		// from 9.9, but now the theory is used in makeMove()
		// as well, which allows it to permit attacks such as:
		// xxxxxxxx
		// | RF RB
		// | RB --
		// | -- RB
		// | B? R2
		// Red Two is unknown.  R2?xB? is a losing move because Red Two
		// does not want to lose stealth.  But if it does not capture
		// Unknown Blue, it risks losing the game.
		//
		// Also, if the attacker has multiple enemies, perhaps
		// it is trapped, and is forced into a losing move.
		//
		// Version 10.1 also qualified this logic by isFlagBombAtRisk
		// because of this example:
		// xxxxxxxxx
		// -- RB RF|
		// -- -- RB|
		// -- B? R7|
		//
		// R7xB? is a losing move, but is necessary because
		// unknown Blue is maybe an Eight and is close to the flag
		// bomb structure.

					if (tp == b.getPiece(t)	// lost the attack
						&& enemies < 2) {
						b.undo();
						continue;
					}
				}
		
				int vm = -qs(n-1, -beta, -alpha, dvr + depthValueReduction(b.depth+1));

				b.undo();
				// log(DETAIL, "   qs(" + n + "x.):" + logMove(b, n, tmpM, MoveResult.OK) + " " + b.getValue() + " " + negQS(vm));

		// Save worthwhile attack (vm > best)
		// (if vm < best, the player will play
		// some other move)

				if (vm > best)
					best = vm;

				alpha = Math.max(alpha, vm);

				if (alpha >= beta)
					return vm;
			} // dir
		} // data
		} // bi

		return best;
	}

	private int negQS(int qs)
	{
		if (b.bturn == Settings.topColor)
			return qs;
		else
			return -qs;
	}

	boolean endOfSearch()
	{
		if (b.depth == -1)
			return false;

		UndoMove um1 = b.getLastMove(1);
		UndoMove um2 = b.getLastMove(2);

		// If the opponent move was null and the player move
		// is null, then the result is the same as if neither
		// player move was null.  This can be thought of as
		// a simple transposition cache.

		if (um1 == null && um2 == null)
			return true;

		if (um1 != null
			&& um1.tp != null
			&& um1.tp.getRank() == Rank.FLAG)
			return true;

		return false;
	}

	void saveTTEntry(TTEntry entry, long hashOrig, int index, int n, TTEntry.SearchType searchType, TTEntry.Flags entryFlags, int vm, int dvr, int bestmove)
	{
		// Replacement scheme.
		//
		// In the event of a collision,
		// retain the entry if deeper and current
		// (deeper entries have more time invested in them)

		if ((entry.depth > n || bestmove == -1)
			&& moveRoot == entry.moveRoot
			&& entry.bestMove != -1) {
			log(DETAIL, " collision " + index);
			return;
		}

		// Yet we want to retain an exact entry as well.
		// Otherwise, a deeper search might overwrite the entry with
		// a lower bound or upper bound entry.  So we keep both.
		//
		// (Note: This prevents the pruned moves from losing their exact
		// transposition table values when the move is considered
		// in the best move search, which uses null moves, and thus
		// overwrites the lower depth entries).

		// Clear the exact entry when the entry is reused.

		if (moveRoot != entry.moveRoot
			|| hashOrig != entry.hash) {
			entry.exactDepth = -1;
			entry.exactValue = -9999;
		}

		// The transposition table cannot be used if the current position
		// results from moves leading to a possible Two Squares ending.
		// That is because it is the order of the moves that is important,
		// and therefore the value of the position depends on whether the
		// moves were issued in the proper order to result in a
		// possible Two Squares ending.  For example,
		// BB -- BB -- |
		// -- -- -- BB |
		// R3 -- B4 -- |
		// -------------
		// Red has the move.  Two Squares is possible after R3 moves right,
		// B4 moves up, R3 moves up. B4 moving down is then disallowed by
		// isPossibleTwoSquares(), so the value of the position to Red is
		// the win of Blue Four.
		//
		// But this is the same position as R3 moves up, B4 moves up,
		// R3 moves right.  B4 moving down is allowed.  So if this latter
		// position is stored in the transposition table, and is retrieved for
		// the value of the first position, Red would not be awarded
		// the win of Blue Four.
		//
		// Vice versa, if the first position were stored, and the value
		// retrieved for the latter position, the AI would erroneously
		// believe it had a Two Squares ending when it really didn't.
		//
		// The solution is store positions separately that can or cannot
		// lead immediately to a two squares result.  Prior to version 10.0,
		// the fix was not to store *any* chases, but this was
		// a performance hit.  Version 10.0 checks if
		// player and opponent piece are alternating in the same
		// row or column, and if so, assumes that a two squares
		// result might be in the offing.  It hashes the two positions
		// separately in the transposition table.

		entry.type = searchType;
		entry.flags = entryFlags;
		entry.moveRoot = moveRoot;
		entry.bestValue = vm - dvr;
		entry.bestMove = bestmove;
		entry.hash = hashOrig;
		entry.depth = n;
		if (entryFlags == TTEntry.Flags.EXACT) {
			entry.exactDepth = n;
			entry.exactValue = vm - dvr;
		}

		log(DETAIL, " " + entryFlags.toString().substring(0,1) + " " + index + " " + negQS(vm) + " " + negQS(vm - dvr));
	}

	// Note: negamax is split into two parts
	// Part 1: check transposition table and qs
	// Part 2: check killer move and if necessary, iterate through movelist

	private int negamax(int n, int alpha, int beta, Move killerMove, Move returnMove, int dvr) throws InterruptedException
	{
		if (bestMove != 0
			&& stopTime != 0
			&& System.currentTimeMillis( ) > stopTime) {

		// reset the board back to the original
		// so that logPV() works

			int depth = b.depth;
			for (int i = 0; i <= depth; i++)
				b.undo();

			log(String.format("abort at %d", depth));
			throw new InterruptedException();
		}

		int alphaOrig = alpha;
		long hashOrig = getHash();
		int index = (int)(hashOrig % ttable[b.bturn].length);
		TTEntry entry = ttable[b.bturn][index];
		int ttmove = -1;
		TTEntry.SearchType searchType;
		if (deepSearch != 0)
			searchType = TTEntry.SearchType.DEEP;
		else
			searchType = TTEntry.SearchType.BROAD;

		if (entry == null) {
			entry = new TTEntry();
			entry.moveRoot = -1;
			ttable[b.bturn][index] = entry;
		} else if (entry.hash == hashOrig) {
			if (entry.depth >= n) {

		// Note that the same position from prior moves
		// (moveRoot != entry.moveRoot)
		// does not have the same score,
		// because the AI assigns less value to attacks
		// at greater depths.  However, the best move 
		// is still useful and often will generate the best score.

			if (moveRoot == entry.moveRoot
				&& (searchType == entry.type
					|| entry.type == TTEntry.SearchType.BROAD)) {
				if (entry.exactDepth >= n) {
					returnMove.setMove(entry.bestMove);
					if (entry.bestMove != 0)
						killerMove.setMove(entry.bestMove);
					log(DETAIL, " exact " + index + " " + negQS(entry.exactValue) + " " + negQS(entry.exactValue + dvr));
					return entry.bestValue + dvr;
				} else if (entry.flags == TTEntry.Flags.LOWERBOUND)
					alpha = Math.max(alpha, entry.bestValue + dvr);
				else if (entry.flags== TTEntry.Flags.UPPERBOUND)
					beta = Math.min(beta, entry.bestValue + dvr);
				if (alpha >= beta) {
					returnMove.setMove(entry.bestMove);
					if (entry.bestMove != 0)
						killerMove.setMove(entry.bestMove);
					log(DETAIL, " cutoff " + index + " " + negQS(entry.bestValue) + " " +  negQS(entry.bestValue + dvr));
					return entry.bestValue + dvr;
				}
			} // same moveroot
			} // entry.depth > n

			ttmove = entry.bestMove;

		} // entry has same hash

		int vm;
		if (n < 1 || endOfSearch()) {
			vm = qs(QSMAX, alpha, beta, dvr);
			// save value of position at hash 0 (see saveTTEntry())
			saveTTEntry(entry, hashOrig, index, n, searchType, TTEntry.Flags.EXACT, vm, dvr, -1);
			return vm;
		}

		// Prevent null move on root level
		// because a null move is unplayable in this game.
		// (not sure how this happened, but it did)
		if (b.depth == -1 && ttmove == 0)
			ttmove = -1;

		vm = negamax2(n, alpha, beta, killerMove, ttmove, returnMove, dvr);

		assert hashOrig == getHash() : "hash changed";

		TTEntry.Flags entryFlags;
		if (vm <= alphaOrig)
			entryFlags = TTEntry.Flags.UPPERBOUND;
		else if (vm >= beta)
			entryFlags = TTEntry.Flags.LOWERBOUND;
		else
			entryFlags = TTEntry.Flags.EXACT;

		// save value of position at hash 0 (see saveTTEntry())
		saveTTEntry(entry, hashOrig, index, n, searchType, entryFlags, vm, dvr, returnMove.getMove());

		return vm;
	}

	int sortMove(ArrayList<Integer> ml, int i)
	{
		int mvp = ml.get(i);
		int max = mvp;
		int tj = i;
		for (int j = i + 1; j < ml.size(); j++) {
			int tmvp = ml.get(j);
			if (hh[tmvp] > hh[max]) {
				max = tmvp;
				tj = j;
			}
		}
		ml.set(tj, mvp);
		ml.set(i, max);
		return max;
	}

	boolean isValidMove(int move)
	{
		int from = Move.unpackFrom(move);
		int to = Move.unpackTo(move);
		Piece fp = b.getPiece(from);
		Piece tp = b.getPiece(to);
		if (fp == null
			|| fp.getColor() != b.bturn
			|| (tp != null && tp.getColor() == b.bturn)
			|| (fp.isKnown()
				&& (fp.getRank() == Rank.BOMB
					|| fp.getRank() == Rank.FLAG)))
			return false;
		return true;
	}

	boolean isValidScoutMove(int move)
	{
		int from = Move.unpackFrom(move);
		int to = Move.unpackTo(move);
		int dir = Grid.dir(to, from);
		from += dir;
		Piece p = b.getPiece(from);
		while (p == null && to != from) {
			from += dir;
			p = b.getPiece(from);
		}
		return (to == from);
	}

// Silly Java warning:
// Java won't let you declare a typed list array like
// public ArrayList<Piece>[] scouts = new ArrayList<Piece>()[2];
// and then it warns if you created a non-typed list array.
@SuppressWarnings("unchecked")
	private int negamax2(int n, int alpha, int beta, Move killerMove, int ttMove, Move returnMove, int dvr) throws InterruptedException
	{
		// The player with the last movable piece on the board wins

		boolean playerMove = (b.grid.movablePieceCount(b.bturn) != 0);
		boolean oppMove = (b.grid.movablePieceCount(1-b.bturn) != 0);

		if (playerMove == false && oppMove == false) {
			returnMove.setMove(-1);
			return 0;	// tie
		}
		else if (playerMove == false) {
			returnMove.setMove(-1);
			return -9999;	// win
		}
		else if (oppMove == false) {
			returnMove.setMove(-1);
			return 9999;	// loss
		}

		int bestValue = -9999;
		Move kmove = new Move(null, -1);
		int bestmove = -1;

		if (ttMove != -1) {

		// use best move from transposition table for move ordering
		// best move entries in the table are not tried
		// if a duplicate of the killer move

			if (ttMove != 0 && !isValidMove(ttMove))
				log(PV, n + ":" + ttMove + " bad tt entry");
			else {

			logMove(n, ttMove, b.getValue(), MoveType.TE);
			MoveResult mt = makeMove(n, ttMove);
			if (mt == MoveResult.OK) {

				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove, dvr + depthValueReduction(b.depth+1));

				b.undo();

				log(DETAIL, " " + negQS(vm));

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[ttMove]+=n;
					returnMove.setMove(ttMove);
					if (ttMove != 0)
						killerMove.setMove(ttMove);
					return vm;
				}

				bestValue = vm;
				bestmove = ttMove;
			} else
				log(DETAIL, " " + mt);
			}
		}

		// Try the killer move before move generation
		// to save time if the killer move causes ab pruning
		// and skips the expensive move generation operation.

		int km = killerMove.getMove();
		assert km != 0 : "Killer move cannot be null move";
		if (km != -1
			&& km != ttMove
			&& isValidMove(km) 
			&& (Grid.isAdjacent(km)
				|| isValidScoutMove(km))) {
			logMove(n, km, b.getValue(), MoveType.KM);
			MoveResult mt = makeMove(n, km);
			if (mt == MoveResult.OK) {
				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove, dvr + depthValueReduction(b.depth+1));
				b.undo();
				log(DETAIL, " " + negQS(vm));
				
				if (vm > bestValue) {
					bestValue = vm;
					bestmove = km;
				}

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[km]+=n;
					returnMove.setMove(km);
					return bestValue;
				}
			} else
				log(DETAIL, " " + mt);
		}

		// Sort the move list.
		// Because alpha-beta can prunes off most of the list,
		// most game playing programs use a selection sort.
		//
		// concept:
		// Collections.sort(moveList);
		// for (MoveValuePair mvp : moveList) {
		//
		// implementation: selection sort

		ArrayList<Integer>[] moveList = null;
		if (b.depth == -1)
			moveList = rootMoveList;
		else {
			BitGrid bg = new BitGrid();
			boolean isPruned = getMovablePieces(n, bg);

		// If legal moves were pruned before move
		// generation, then try the null move before move
		// generation.  If the null move causes alpha-beta
		// pruning, then the move generation is skipped,
		// saving a heap of time.

			if (isPruned && ttMove != 0) {

			logMove(n, 0, b.getValue(), MoveType.NU);
			MoveResult mt = makeMove(n, 0);
			if (mt == MoveResult.OK) {

				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove, dvr + depthValueReduction(b.depth+1));

				b.undo();

				log(DETAIL, " " + negQS(vm));

				if (vm > bestValue) {
					bestValue = vm;
					bestmove = 0;
				}
				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[0]+=n;
					returnMove.setMove(0);
					return vm;
				}
			} else
				log(DETAIL, " " + mt);

			} // isPruned

			moveList = (ArrayList<Integer>[])new ArrayList[FAR+1];

			// FORWARD PRUNING
			// Add null move if not processed already
			if (getMoves(bg, moveList, n)
				&& !isPruned
				&& ttMove != 0)
				addMove(moveList[INACTIVE], 0);
		}

		outerloop:
		for (int mo = 0; mo <= FAR; mo++) {

		// Scout far moves are expensive to generate and
		// because of the loss of stealth are often
		// bad moves, so these are generated last, hoping
		// that they will get pruned off by alpha-beta.
		// They are generated separately because they are
		// not forward pruned based on enemy distance like other moves.

			if (mo == FAR)
				getScoutMoves(moveList[mo], n, b.bturn);

			ArrayList<Integer> ml = moveList[mo];
			for (int i = 0; i < ml.size(); i++) {
				int max = sortMove(ml, i);

		// skip ttMove and killerMove

				if (max == ttMove
					|| (max != 0 && max == km))
					continue;

				logMove(n, max, b.getValue(), MoveType.GE);
				MoveResult mt = makeMove(n, max);
				if (!(mt == MoveResult.OK)) {
					log(DETAIL, " " + mt);
					continue;
				}

				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove, dvr + depthValueReduction(b.depth+1));

				b.undo();

				log(DETAIL, " " + negQS(vm));

				if (vm > bestValue) {
					bestValue = vm;
					bestmove = max;
				}

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					assert bestValue == vm : "bestvalue not vm?";
					break outerloop;
				}
			} // moveList
		} // move order

		// It is possible bestmove can equal -1 if the player
		// has a piece that can move, but moving it is illegal,
		// such in the case of two squares, or was eliminated
		// by the NEG capture heuristic.
		// TBD: if a NEG capture is the only move available,
		// then it should be allowed.

		if (bestmove != -1)
			hh[bestmove]+=n;

		returnMove.setMove(bestmove);

		// A null move cannot be a killer move, because
		// a null move is valid only if the movelist is pruned,
		// and that cannot be determined until a new movelist
		// is generated.  (killer move bypasses move generation).

		if (bestmove != 0)
			killerMove.setMove(bestmove);

		return bestValue;
	}

	private MoveResult makeMove(int n, int tryMove)
	{
		// NOTE: FORWARD TREE PRUNING (minor)
		// isRepeatedPosition() discards repetitive moves.
		// This is done now rather than during move
		// generation because most moves
		// are pruned off by alpha-beta,
		// so calls to isRepeatedPosition() are also pruned off,
		// saving a heap of time.

		if (tryMove == 0) {

		// A null move does not change the board
		// so it doesn't change the hash.  But the hash
		// could have been saved (in ttable or possibly boardHistory)
		// for the opponent.  Even if the board is the same,
		// the outcome is different if the player is different.
		// So set a new hash and reset it after the move.
			b.pushNullMove();
			return MoveResult.OK;
		}

		// Immobile Pieces
		// Bombs and the Flag are not legal moves.  However,
		// the AI generates moves for unknown bombs because
		// the apparent rank is unknown to the opponent, so
		// these pieces can protect another piece as a bluff.
		if (b.depth == -1) {
			Piece p = b.getPiece(Move.unpackFrom(tryMove));
			if (p.getRank() == Rank.BOMB
				|| p.getRank() == Rank.FLAG) {
				return MoveResult.IMMOBILE;
			}
		}


		if (b.isTwoSquares(tryMove))
			return MoveResult.TWO_SQUARES;

		int enemies = b.grid.enemyCount(b.getPiece(Move.unpackFrom(tryMove)));
		int bvalue = b.getValue();

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).

		if (Settings.twoSquares
			|| b.bturn == Settings.topColor) {

		// Note that a possible two squares result can occur
		// even if the piece does not have an adjacent attacker.
		// This can occur if a Nine is trying to attack from afar,
		// or simply if the piece is moving back and forth
		// aimlessly.

			if (b.isPossibleTwoSquares(tryMove))
				return MoveResult.POSS_TWO_SQUARES;

		// Piece is being chased, so repetitive moves OK

			if (b.isChased(tryMove)) {
				b.move(tryMove);
			} else if (b.bturn == Settings.topColor) {

				if (b.isTwoSquaresChase(tryMove)) {

		// Piece is chasing, so repetitive moves OK
		// (until Two Squares Rule kicks in)

					b.move(tryMove);
				} else {

	// Because isRepeatedPosition() is more restrictive
	// than More Squares, the AI does not expect
	// the opponent to abide by this rule as coded.

					b.move(tryMove);
					if (b.isRepeatedPosition()) {
						b.undo();
						return MoveResult.REPEATED;
					}
				}
			} else
				b.move(tryMove);
		} else
			b.move(tryMove);

	// Losing captures are discarded (see qs())

		if (enemies < 2
			&& -negQS(b.getValue() - bvalue) < 0) {
			
			UndoMove m = b.getLastMove(1);
			if (m.tp != null
				&& (!b.isFlagBombAtRisk(m.tp)
					|| m.getPiece().getRank() == Rank.NINE)) {
				Piece tp = b.getPiece(Move.unpackTo(tryMove));
				if (tp == m.tp) {	// lost the attack
					b.undo();
					return MoveResult.NEG;
				}
			}
		}

		return MoveResult.OK;
	}

	private long getHash()
	{
		if (Grid.isPossibleTwoSquaresChase(b.getLastMove(1), b.getLastMove(2)))
			return b.getHash() ^ twoSquaresHash;
		else
			return b.getHash();
	}

	// Prefer early successful attacks
	//
	// Note: it is impossible to determine what moves
	// the opponent actually considers, so the AI cannot
	// second-guess the opponent and play a suboptimal move,
	// hoping the opponent will miss a good move.
	//
	// The depth adjustment here is intended to
	// delay losing sequences.
	// By delaying losing sequences, the opponent may misplay,
	// but this cannot be predicted.  Generally, the AI
	// will play the best move.
	//
	// However, consider the following example:
	// RB -- RB -- --
	// -- R5 -- R3 --
	// -- B2 -- -- --
	//
	// Blue Two has moved towards Red Five.
	// Red Five can move back, allowing Blue Two
	// to trap it.  Red Five can move left, but
	// the Two Squares rule will eventually push
	// it right next to Red Three (in 8 ply), allowing Blue Two
	// to fork Red Five and Blue Three.
	//
	// So Red knows that it can lose its Five.
	// It this situation, Red should play out the sequence
	// and hope that Blue Two misplays.
	//
	// This particularly affects deep chase, where
	// the depth is very deep.  Few opponents will see
	// this deep.  Often the AI will leave material
	// hanging because of a potential deep threat
	// involving unknown pieces that do not have the
	// ranks that the AI is worried about.
	//
	// This is important in tournament play with substandard
	// bots.  Stratego is a game of logic, but chance plays
	// an important part.  By forcing the opponent to play
	// out a losing sequence, the AI can win more games.
	//
	// Winning sequences are also important, because
	// of the limited search depth.  If the AI delays a
	// capture, it is possible that the piece can be rescued
	// or the AI may need to reliquish its capture.
	// For example,
	// ----------------------
	// | -- RF -- RB B4 RB --
	// | -- -- -- R1 -- -- --
	// | -- -- -- -- -- -- --
	// | -- -- -- -- -- -- R?
	// | -- -- xx xx -- -- xx
	// | -- -- xx xx -- -- xx
	// | -- B? -- -- -- -- --
	// Red has the move.
	// Red One knows it has Blue Four trapped.  The search
	// tree rewards Red for the piece because all lines
	// of play allow Red One to capture Blue Four.  So
	// moving unknown Red has the same value as approaching
	// Blue Four.  But if 10 ply were considered, Red
	// would realize that Red One is needed to protect Red Flag.
	// If the search is not this deep, then Red play some
	// other move, allowing Blue to force the AI to relinquish
	// its capture of Blue Four.
	//
	// Consider the following example:
	// | -- -- -- -- -- -- --
	// | BB R3 -- -- -- -- B1
	// | B4 -- BB -- -- -- --
	// -----------------------
	// Red Three must move to attack Blue Four now because
	// if it plays some other move, it cannot win Blue Four
	// without losing its Three to Blue One in 12 ply.
	//
	// These examples have occurred in play.

	//
	//
	// Depth value reduction
	//   known
	// 2 : 10%
	// 4 : 20%
	// 6 : 30%
	// 8 : 40%
	// 10+ : 50%
	//
	// The depth value reduction must be small so that
	// the AI does not leave pieces hanging to delay
	// a possible future attack.  Because of the 
	// high value of the Spy, this is important when
	// the Spy is susceptible to attack in the
	// proximity of unknown or high ranked opponent pieces.

	int depthValueReduction(int depth)
	{
		UndoMove um = b.getLastMove();
		if (um == null)
			return 0;

		return -(b.getValue() - um.value) * Math.min(depth, 10) / 20;
	}

	// DEEP SEARCH
	//
	// Some opponent AI bots like to chase pieces around
	// the board relentlessly hoping for material gain.
	// The opponent is able to chase the AI piece because
	// the AI always abides by the Two Squares Rule, otherwise
	// the AI piece could go back and forth between the same
	// two squares.  If the Settings Two Squares Rule box
	// is left unchecked, this program does not enforce the
	// rule for opponents. This is a huge advantage for the
	// opponent, but is a good beginner setting.
	//
	// So an unskilled human or bot can chase an AI piece
	// pretty easily, but even a skilled opponent abiding
	// by the two squares rule can still chase an AI piece around
	// using the proper moves.
	//
	// Hence, chase sequences need to be examined in more depth.
	// So if moving a chased piece
	// looks like the best move, then do a deep
	// search of those pieces and any interacting pieces.
	//
	// The goal is that the chased piece will find a
	// protector or at least avoid a trap or dead-end
	// and simply outlast the chaser's patience.  If not,
	// the opponent should be disqualified anyway.
	// 
	// This requires FORWARD PRUNING which is tricky
	// to get just right.  The AI may discover many moves
	// deep that it will lose the chase, but unless the
	// opponent is highly skilled, the opponent will likely
	// not realize it.

	void genDeepSearch()
	{
		final int MAX_STEPS = 2;
		final int MAX_STEPS2 = 4;

		BitGrid bg = new BitGrid();
		b.grid.getMovablePieces(Settings.topColor, bg);

		for (int bi = 0; bi < 2; bi++) {
			int k;
			if (bi == 0)
				k = 2;
			else
				k = 66;
			long data = bg.get(bi);

		while (data != 0) {
			int ntz = Long.numberOfTrailingZeros(data);
			int i = k + ntz;
			data ^= (1l << ntz);

			Piece fp = b.getPiece(i);

		// Unknown unmoved pieces are not chased
		// (use broad search).

			if (!fp.isKnown()
				&& !fp.hasMoved())
				continue;

		// Nines are not chased.
		// 1. They can run away fast
		// 2. They are not worth very much
		// 3. They may be chasing opponent pieces
		//	(such as a Spy or unknown One)
		//	and deep search eliminates those moves

			if (fp.getRank() == Rank.NINE)
				continue;

			if (!b.grid.isCloseToEnemy(Settings.topColor, fp.getIndex(), MAX_STEPS))
				continue;

			int attackers = 0;
			int maxsteps = 0;

			BitGrid tbg = new BitGrid();
			b.grid.getMovablePieces(Settings.bottomColor, tbg);

			for (int tbi = 0; tbi < 2; tbi++) {
				int tk;
				if (tbi == 0)
					tk = 2;
				else
					tk = 66;
				long tdata = tbg.get(tbi);
				while (tdata != 0) {
					int tntz = Long.numberOfTrailingZeros(tdata);
					int ti = tk + tntz;
					tdata ^= (1l << tntz);

					Piece tp = b.getPiece(ti);

				if (tp.getRank() == Rank.UNKNOWN
					|| tp.getRank() == Rank.BOMB
					|| tp.getRank() == Rank.FLAG)
					continue;

		// If any attacker is near the flag, then the target might be
		// the AI flag rather than the chased piece,
		// so a broad search is used.

		// For example,
		// B8 -- -- -- RB RF
		// -- RB -- R9 -- RB
		// -- -- R8 RS -- --
		// Broad search is needed to determine that Red Eight
		// can protect the Red Flag bomb from Blue Eight.  So
		// even if there is a hot chase going on elsewhere on the board,
		// broad search must still be used.

				if (b.isFlagBombAtRisk(tp)) {
					deepSearch = 0;
					return;
				}

				int steps = Grid.steps(fp.getIndex(), tp.getIndex());

		// TBD: MAX_STEPS2 is only an approximation of the steps to the attacker
		// because the attacker could be blocked by the lakes, bombs, or its
		// own piece.

				if (steps > MAX_STEPS2)
					continue;

		// TBD: If Two Squares is in effect and the chased and chaser pieces
		// are not on the same row or column, and the chased piece has
		// an open square, the chased piece is in no danger, 
		// because the chase piece can move back and forth
		// until Two Squares prevents the chaser from moving.
		// But if it has a choice of another open square, deep search
		// is still needed to examine the flee route.

				if (b.isProtected(fp, tp, fp.getIndex()))
					continue;

		// Unknown AI pieces have little to fear from the opponent One
		// if the AI still has its Spy

				if (tp.getRank() == Rank.ONE
					&& b.hasSpy(Settings.topColor)
					&& !fp.isKnown())
					continue;

		// If chased X chaser isn't a bad move (at depth), the AI
		// isn't concerned about a chase, so continue deep search.
		// (Note: When depth != 0, then bluffing comes into play ... )

				int vm = b.getValue();
				b.depth++;
				b.move(Move.packMove(fp.getIndex(), tp.getIndex()), true);
				vm = b.getValue() - vm;
				b.undo();
				b.depth--;
				if (vm >= -5)
					continue;

		// If chaser X chased isn't a good move, the chaser
		// isn't likely to chase.  For example, 8?x3 (LOSES) is a bad move
		// for the AI because bluffing using non-expendable pieces
		// is discouraged.  So the check above falls through to here.
		// But 3x8? (WINS) is also a bad move for the opponent,
		// losing 1/2 of the value of an unknown piece (see valueBluff).

				vm = b.getValue();
				b.move(Move.packMove(tp.getIndex(), fp.getIndex()) , true);
				vm = b.getValue() - vm;
				b.undo();
				if (vm > 0)
					continue;

				log("Deep search (" + steps + "):" + tp.getRank() + " chasing " + fp.getRank());

				attackers++;
				if (steps > MAX_STEPS)
					continue;


				if (steps > maxsteps)
					maxsteps = steps;

			} //tp data
			} //tp bi

		// If there are two attackers nearby, then broad search
		// is often preferable because of potential
		// entrapment by the two attackers.

			if (attackers >= 2)
				continue;

		// If multiple AI pieces are under attack,
		// use the narrowest search.  (Better: use
		// the search level of the most valuable
		// endangered piece).

			if (maxsteps != 0
				&& (deepSearch == 0
					|| maxsteps < deepSearch) )
				deepSearch = maxsteps;

		} // bp data
		} // fp bi

		if (deepSearch != 0) {
			deepSearch *= 2;
			log("Deep search (" + deepSearch + ") in effect");
		}
	}

	String logPiece(Piece p)
	{
		if (p == null)
			return "ILLEGAL PIECE";
		Rank rank = p.getRank();
		if (!p.isKnown()
			&& (p.getActingRankFleeLow() != Rank.NIL
				|| p.getActingRankFleeHigh() != Rank.NIL
				|| p.getActingRankChase() != Rank.NIL))
			return rank.value + "["
				+ p.getActingRankChase().value
				+ "," + p.getActingRankFleeLow().value
				+ "," + p.getActingRankFleeHigh().value + "]";

		return "" + rank.value;
	}

	String logFlags(Piece p)
	{
		if (p == null)
			return "ILLEGAL PIECE";
		String s = "";
		if (p.hasMoved())
			s += 'M';
		else
			s += '.';
		if (p.isKnown())
			s += 'K';
		else
			s += '.';
		if (p.isSuspectedRank())
			s += 'S';
		else
			s += '.';
		if (p.isRankLess())
			s += 'L';
		else
			s += '.';
		if (p.getMaybeEight())
			s += '8';
		else
			s += '.';
		return s;
	}


	String logMove(Board b, int n, int move)
	{

	String s = "";
	if (b.bturn == 1)
		s += "... ";
	if (move == 0)
		return s + "(null)";

	Piece fp = b.getPiece(Move.unpackFrom(move));
	s += logPiece(fp);
	s += (char)(Move.unpackFromX(move)+97);
	s += (Move.unpackFromY(move)+1);

	Piece tp = b.getPiece(Move.unpackTo(move));
	if (tp == null) {
		s += "-";
		s += (char)(Move.unpackToX(move)+97);
		s += (Move.unpackToY(move)+1);
		s += " " + logFlags(fp);
	} else {
		char X = 'x';
		if (n == 0)
			X = 'X';
		s += X;
		s += logPiece(tp);
		s += (char)(Move.unpackToX(move)+97);
		s += (Move.unpackToY(move)+1);
		s += " " + logFlags(fp);
		s += " " + logFlags(tp);
	}
	return s;
	}

	void logMove(int n, int move, int valueB, MoveType mt)
	{
		if (Settings.debugLevel >= DETAIL)
			log.print( "\n" + n + ":" + logMove(b, n, move) + " " + valueB + " " + mt);
	}

	public void logMove(Move m)
	{
		log(PV, logMove(board, 0, m.getMove()) + "\n");
	}

	private void log(int level, String s)
	{
		if (Settings.debugLevel >= level)
			log.print(s);
	}

	private void log(String s)
	{
		log(DETAIL, s + "\n");
	}

	public void logFlush(String s)
	{
		if (Settings.debugLevel != 0) {
			log.println(s);
			log.flush();
		}
	}

	private void logPV(int turn, int n)
	{
		if (n == 0)
			return;
		long hash = getHash();
		int index = (int)(hash % ttable[turn].length);
		TTEntry entry = ttable[turn][index];
		if (entry == null
			|| hash != entry.hash)
			return;

		int bestmove = entry.bestMove;
		if (bestmove == 0) {
			log(PV,  index + ":   (null)\n");
			b.pushNullMove();
		} else if (bestmove == -1) {
			log(PV,  index + ":   (end of game)\n");
			return;
		} else {
			log(PV, index + ":   " + logMove(b, n, bestmove) + "\n");
			b.move(bestmove);
		}
		logPV(1-turn, --n);
		b.undo();
	}
}

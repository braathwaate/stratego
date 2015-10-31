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
	private ArrayList<ArrayList<Integer>> rootMoveList = null;
	// static move ordering
	static final int WINS = 0;
	static final int ATTACK = 1;
	static final int APPROACH = 2;
	static final int FLEE = 3;
	static final int LOSES = 4;

	private static int[] dir = { -11, -1,  1, 11 };
	private int[] hh = new int[2<<14];	// move history heuristic
	private TTEntry[] ttable = new TTEntry[2<<22]; // 4194304
	private final int QSMAX = 6;	// maximum qs search depth
	int bestMove = 0;
	long stopTime = 0;
	int moveRoot = 0;
	int completedDepth = 0;
	int deepSearch = 0;
	private Piece lastMovedPiece;

	enum MoveType {
		TWO_SQUARES,
		POSS_TWO_SQUARES,
		CHASED,
		CHASER,
		REPEATED,
		KM,	// killer move
		TE,	// transposition table entry
		IMMOBILE,
		OK
	}

	// QS cache
	// The QS calculation is time-consuming.
	// Fortunately, only attacking and protecting moves affect the QS value
	// so that often the QS value does not change from move to move.
	// Once a QS value has been determined, subsequent moves that
	// do not affect the QS can use the previous QS value.
	class QSEntry {
		long hash;
		int value;
		int depth;

		QSEntry(int d, long h) {
			depth = d;
			hash = h;
		}

		void setValue(int v)
		{
			value = v;
		}

		int getValue()
		{
			return value;
		}
	}
	private QSEntry[][] QSCtable;

	static private long [][] depthHash = new long [2][MAX_PLY];
	static {
		Random rnd = new Random();
               for ( int t = 0; t < 2; t++)
                for ( int d = 0; d < MAX_PLY; d++) {
                        long n = rnd.nextLong();

                // It is really silly that java does not have unsigned
                // so we lose a bit of precision.  hash has to
                // be positive because we use it to index ttable.

                        if (n < 0)
                                depthHash[t][d] = -n;
                        else
                                depthHash[t][d] = n;
                }
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

	void getScoutFarMoves(int depth, ArrayList<ArrayList<Integer>> moveList, Piece fp) {
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

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
				&& p.getColor() != 1 - fpcolor)
				continue;
			while (p == null) {

		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks when depth > 1

				if (depth <= 1)
				 	addMove(moveList.get(FLEE), i, t);
				t += d;
				p = b.getPiece(t);
			};
			if (p.getColor() == 1 - fpcolor) {
				int mo = LOSES;
				if (b.isNineTarget(p))
					mo = ATTACK;
				addMove(moveList.get(mo), i, t);
			}
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

	void getAttackingScoutFarMoves(int depth, ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

		for (int d : dir ) {
			int t = i + d ;
			Piece p = b.getPiece(t);
			if (p != null)
				continue;

			do {
				t += d;
				p = b.getPiece(t);
				// addMove(moveList.get(FLEE), i, t);
			} while (p == null);
			if (p.getColor() == 1 - fpcolor
				&& b.isNineTarget(p)) {
				addMove(moveList.get(WINS), i, t);
			} // attack
		}
	}

	public void getAllMoves(ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		int i = fp.getIndex();
		int fpcolor = fp.getColor();
		Rank fprank = fp.getRank();

		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);

			if (tp == null) {
				addMove(moveList.get(FLEE), i, t);
			} else if (tp != Grid.water && tp.getColor() != fpcolor) {
				if (b.isLosingMove(fp, tp))
					addMove(moveList.get(LOSES), i, t);
				else
					addMove(moveList.get(ATTACK), i, t);

					
			}
		} // d
	}

	public void getAttackMoves(ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		if (!b.grid.hasAttack(fp))
			return;

		int i = fp.getIndex();
		int fpcolor = fp.getColor();
		Rank fprank = fp.getRank();

		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);

			if (tp == null || tp == Grid.water)
				continue;

			if (tp.getColor() != fpcolor) {
				if (b.isLosingMove(fp, tp))
					addMove(moveList.get(LOSES), i, t);
				else
					addMove(moveList.get(ATTACK), i, t);
			}
		} // d
	}

	public boolean getApproachMoves(int n, ArrayList<ArrayList<Integer>> moveList, Piece fp, BitGrid unsafeGrid)
	{
		boolean isPruned = false;
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

		// if the piece is close enough, the move is allowed
		// 
		boolean allowAll =
			!(Math.abs(n/2) < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, i, Math.abs(n/2)));

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
		//
		// In version 9.6, a BitGrid of the squares that can
		// be attacked by opponent scouts is created when n >= 2.
		// A move is considered only if it is a valuable piece
		// moving to a safe square or a non-valuable piece moves
		// to a non-safe square (blocking move).

		boolean isValuable =
			fpcolor == Settings.topColor
			&& b.isNineTarget(fp)
			&& unsafeGrid.testBit(i+11);

		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);
			if (tp != null)
				continue;

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

			if (n > 0) {
				if (n >= 2
					&& !allowAll
					&& ((isValuable && !unsafeGrid.testBit(t))
						|| (!isValuable && unsafeGrid.testBit(t))))
					addMove(moveList.get(APPROACH), i, t);
				else if (!allowAll
					&& (n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, n/2)))
					isPruned = true;
				else
					addMove(moveList.get(APPROACH), i, t);
			} else if (n < 0) {
				if (!allowAll
					&& (-n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, -n/2)))
					addMove(moveList.get(APPROACH), i, t);
				else
					isPruned = true;
			}

		} // d

		return isPruned;
	}

	// n > 0: prune off inactive moves
	// n = 0; no pruning
	// n < 0: prune off active moves

	public boolean getMoves(int n, ArrayList<ArrayList<Integer>> moveList, Piece fp, BitGrid unsafeGrid)
	{
		boolean isPruned = false;
		int i = fp.getIndex();

		Rank fprank = fp.getRank();

		if (fprank == Rank.BOMB || fprank == Rank.FLAG) {

		// Known bombs are removed from pieces[] but
		// a bomb or flag could become known in the search
		// tree.  We need to generate moves for suspected
		// bombs because the bomb might actually be
		// some other piece, but once the bomb becomes
		// known, it ceases to move.

			if (fp.isKnown())
				return false;

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

			getAttackMoves(moveList, fp);
			return false;
		}

		if (n > 0) {

			if (b.grid.hasAttack(fp)) {
				getAllMoves(moveList, fp);
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

			if (fprank == Rank.UNKNOWN
                                && fp != lastMovedPiece) {

		// Deep search reduces an the ability for an AI piece
		// to flee before an unknown opponent reaches it.
		// So the AI discards moves by unknowns unless they
		// can directly attack.  This introduces a problem where
		// an AI piece is under attack in one area of the board,
		// and then the AI piece moves its other pieces into
		// an area where they can be forked by an unknown piece.

				if (deepSearch != 0
					&& n >= deepSearch)
					return true;

				if (n > 2)
					n = 2;
			}

			return getApproachMoves(n, moveList, fp, unsafeGrid);

		} else if (n < 0) {

		// always return false to avoid null move

			int fpcolor = fp.getColor();
			assert fpcolor == Settings.topColor : "n<0 code only for AI";
			getApproachMoves(n, moveList, fp, unsafeGrid);
			return false;
		} else {
			getAllMoves(moveList, fp);
		}

		return false;
	}

	void genSafe(BitGrid unsafeGrid)
	{
		final int[] lanes = { 111, 112, 115, 116, 119, 120 };
		for (int lane : lanes) {
			boolean unsafe = false;
			int i = lane;
			for (i = lane; ; i -= 11) {
				Piece p = b.getPiece(i);
				if (p != null) {
					if (p.getColor() != Settings.bottomColor)
						break;
					if (p.getRank() != Rank.UNKNOWN
						&& p.getRank() != Rank.NINE) {
						unsafe = false;
						continue;
					}
					unsafe = true;
					continue;
				}
				if (unsafe)
					unsafeGrid.setBit(i);
			}
		}
	}

	private boolean getMoves(ArrayList<ArrayList<Integer>> moveList, int n, int depth, int turn)
	{
		for (int i = 0; i <= LOSES; i++)
			moveList.add(new ArrayList<Integer>());

		boolean isPruned = false;
		BitGrid unsafeGrid = new BitGrid();
		if (turn == Settings.topColor
			&& unknownNinesAtLarge > 0
			&& n >= 2)
			genSafe(unsafeGrid);

		for (Piece fp : b.pieces[turn]) {
			if (fp == null)	// end of list
				break;

		// if the piece is no longer on the board, ignore it

			if (b.getPiece(fp.getIndex()) != fp)
				continue;

			if (!b.grid.hasMove(fp))
				continue;

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

			int ns = n;
			if (deepSearch != 0) {
				if (n >= 0)
					ns = Math.min(n, deepSearch);
				else
					ns = Math.max(n, -deepSearch);
			}

		// TBD: check for suspected rank; if no suspected rank,
		// then skip AI Nine far moves

			if (n >= 0
				&& !(deepSearch != 0 && turn == Settings.topColor)) {
				Rank fprank = fp.getRank();
				if (fprank == Rank.NINE)
					getScoutFarMoves(depth, moveList, fp);

				else if (unknownNinesAtLarge > 0 && fprank == Rank.UNKNOWN)
					getAttackingScoutFarMoves(depth, moveList, fp);
			}

			if (getMoves(ns, moveList, fp, unsafeGrid))
					isPruned = true;
		}

		return isPruned;
	}

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

		// clear the QS cache
		QSCtable = new QSEntry [2][10007];

		moveRoot = b.undoList.size();
		deepSearch = 0;

		// chase variables
		Piece lastMovedPiece = null;
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

			rootMoveList = new ArrayList<ArrayList<Integer>>();
			boolean isPruned = getMoves(rootMoveList, n, 0, Settings.topColor);
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

			ArrayList<ArrayList<Integer>> moveList = new ArrayList<ArrayList<Integer>>();
			getMoves(moveList, -n, 0, Settings.topColor);
			int bestPrunedMoveValue = -9999;
			int bestPrunedMove = 0;
			for (int mo = 0; mo <= LOSES; mo++)
			for (int move : moveList.get(mo)) {
				logMove(2, move, 0);
				MoveType mt = makeMove(n, 0, move);
				if (mt == MoveType.OK
					|| mt == MoveType.CHASER
					|| mt == MoveType.CHASED) {

		// Note: negamax(0) isn't deep enough because scout far moves
		// can cause a move outside the active area to be very bad.
		// For example, a Spy might be far away from the opponent
		// pieces and could be selected as the best pruned move.
		// But moving it could lose the Spy to a far scout move.
		// So negamax(1) is called to allow the opponent scout
		// moves to be considered (because QS does not currently
		// consider scout moves).
		// 
					int vm = -negamax(1, -9999, 9999, 1, killerMove, depthValueReduction(1)); 
					if (vm > bestPrunedMoveValue) {
						bestPrunedMoveValue = vm;
						bestPrunedMove = move;
					}
					b.undo();
					log(DETAIL, " " + negQS(vm));
				}
			}
			addMove(rootMoveList.get(APPROACH), bestPrunedMove);
			log(PV, "\nPPV:" + n + " " + bestPrunedMoveValue);
			log(PV, "\n" + logMove(b, n, bestPrunedMove));
			log(DETAIL, "\n<< pick best pruned move\n");
			}

		boolean hasMove = false;
		for (int mo = 0; mo <= LOSES; mo++)
			if (rootMoveList.get(mo).size() != 0) {
				hasMove = true;
				break;
			}

		if (!hasMove) {
			log("Empty move list");
			return;		// ai trapped
		}

		log(DETAIL, "\n>>> pick best move");
		int vm = negamax(n, -9999, 9999, 0, killerMove, 0); 
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

		int bestMovePly = killerMove.getMove();
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

			logMove(n+2, bestMovePly, b.getValue());
			MoveType mt = makeMove(n, 0, bestMovePly);
			vm = -negamax(n+1, -9999, 9999, 1, killerMove, depthValueReduction(1)); 
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
		logPV(n, 0);
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

	private int qs(int depth, int n, int alpha, int beta, int dvr)
	{
		int bvalue = negQS(b.getValue() + dvr);
		if (n < 1)
			return bvalue;

		int best = -9999;

		// Note: the following code snippet is faster,
		// especially for dense bitmaps:
		// http://lemire.me/blog/archives/2013/12/23/even-faster-bitmap-decoding/
// int pos = 0;
// for(int k = 0; k < bitmaps.length; ++k) {
//    long bitset = bitmaps[k];
//    while (bitset != 0) {
//      long t = bitset & -bitset;
//      output[pos++] = k * 64 +  Long.bitCount(t-1);
//      bitset ^= t;
//    }
// }
		BitGrid bg = new BitGrid();
		b.grid.getNeighbors(1-b.bturn, bg);
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

		// Known bombs are removed from pieces[] but
		// a bomb could become known in the search
		// tree.  We need to generate moves for suspected
		// bombs because the bomb might actually be
		// some other piece, but once the bomb becomes
		// known, it ceases to move.

			Rank fprank = fp.getRank();
			if ((fprank == Rank.BOMB || fprank == Rank.FLAG)
				&& fp.isKnown())
			 	continue;

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
			boolean tryFlee = false;
			for (int d : dir ) {
				int t = i + d;	

				Piece tp = b.getPiece(t); // defender
				if (tp == null)
					continue;

				if (tp.getColor() != 1 - b.bturn)
					continue;

				int tmpM = Move.packMove(i, t);
				b.move(tmpM, depth);

		// If there is a negative capture, try fleeing

				if (-negQS(b.getValue() + dvr) < bvalue
					&& !(fprank == Rank.BOMB || fprank == Rank.FLAG)) {
					tryFlee = true;

		// It is tempting to skip losing captures to save time
		// (such as attacking a known lower ranked piece).
		// The main search throws these into the LOSES
		// bucket, hoping that they will get pruned off.
		// But it has to evaluate them because of the rare
		// case when a valuable piece is cornered and has to
		// sacrifice a lesser piece to clear an escape.
		//
		// This code was commented out in version 9.7, because
		// sometimes a low ranked piece like a Three or Four
		// must protect the stealth of a One, Two or Three
		// in the face of an oncoming known Seven.  But 3x7
		// and 4x7 is a losing move.  There are likely other
		// scenarios as well.  This results in a more accurate qs,
		// but with a heavy time penalty.  It is better to save
		// time by pruning off useless moves so that qs is never
		// even reached.
		//
		//			b.undo();
		//			continue;
				}
		
				int vm = -qs(depth+1, n-1, -beta, -alpha, dvr + depthValueReduction(depth+1));

				b.undo();
				// log(DETAIL, "   qs(" + n + "x.):" + logMove(b, n, tmpM, MoveType.OK) + " " + b.getValue() + " " + negQS(vm));

		// Save worthwhile attack (vm > best)
		// (if vm < best, the player will play
		// some other move)

				if (vm > best)
					best = vm;

				alpha = Math.max(alpha, vm);

				if (alpha >= beta)
					return vm;
			} // dir

			if (tryFlee)
			for (int d : dir ) {
				int t = i + d;	

				Piece tp = b.getPiece(t); // defender
				if (tp != null)
					continue;

				int tmpM = Move.packMove(i, t);
				b.move(tmpM, depth);
				int vm = -qs(depth+1, n-1, -beta, -alpha, dvr + depthValueReduction(depth+1));
				b.undo();
				// log(DETAIL, "   qs(" + n + "x.):" + logMove(b, n, tmpM, MoveType.OK) + " " + b.getValue() + " " + negQS(vm));

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

		// If the opponent move was null and the player move
		// is null, then the result is the same as if neither
		// player move was null.  This can be thought of as
		// a simple transposition cache.  It also prevents
		// the search continuing ad infinitem.

		if (b.getLastMove() == null)
			return Math.max(best, bvalue);

		b.pushNullMove();
		int vm = -qs(depth+1, n-1, -beta, -alpha, dvr);
		b.undo();

		return	Math.max(vm, best);
	}

	private int qscache(int depth, int alpha, int beta, int dvr)
	{

		BitGrid bg = new BitGrid();
		b.grid.getNeighbors(bg);
		int valueB = b.getValue();

		// As qs is defined, if there are no neighbors,
		// qs is valueB

		if (bg.get(0) == 0 && bg.get(1) == 0)
			return negQS(valueB + dvr);

		// valueBluff() checks for a prior move capture,
		// so qscache is invalid in this case

		return qs(depth, QSMAX, alpha, beta, dvr);
/*

		UndoMove um = b.getLastMove();
		if (um != null && um.tp != null)
			return qs(depth, QSMAX, false);

		// calculate qshash to be sure
		long qshash = 0;
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
				Piece p = b.getPiece(i);

		// If any of the pieces have an unknown rank
		// along with chase rank and flee rank,
		// the hash is invalid, because the AI uses
		// this in determining the move value.
		// TBD: fix this

				Rank rank = p.getRank();
				if ((bturn == Settings.topColor
					&& rank == Rank.UNKNOWN)
					&& (p.getActingRankChase() != Rank.NIL
					|| p.getActingRankFleeLow() != Rank.NIL
					|| p.getActingRankFleeHigh() != Rank.NIL))
					return qs(depth, QSMAX, false);

				qshash ^= b.hashPiece(bturn, p, i);
			}
		}

		int qsindex = (int)(qshash % 10007);
		QSEntry entry = QSCtable[b.bturn][qsindex];

		if (entry != null
			&& entry.hash == qshash
			&& entry.depth == depth) {
			// return valueB + entry.getValue();
			int v = valueB + entry.getValue();
			int v2 = qs(turn, depth, QSMAX, false);
			if (v != v2)
				log (PV, String.format("bad qsentry %d", v));
			else
				log (PV, String.format("qscache"));
			return v2;
		}

		if (entry == null)
			entry = new QSEntry(depth, qshash);
		else {

		// always overwrite existing table entry because qs
		// changes with depth (because captures are less valuable)

			entry.depth = depth;
			entry.hash = qshash;
		}

		int v = qs(depth, QSMAX, false);

		// save the delta qs value after a quiescent move
		// for possible reuse

		entry.setValue(v-valueB);
		QSCtable[turn][qsindex] = entry;
		return v;
*/
	}

	private int negQS(int qs)
	{
		if (b.bturn == Settings.topColor)
			return qs;
		else
			return -qs;
	}

	void saveTTEntry(int hashN, int n, TTEntry.SearchType searchType, TTEntry.Flags entryType, int vm, int dvr, int bestmove)
	{
		long hashOrig;
		if (hashN == 0)
			hashOrig = b.getHash();
		else
			hashOrig = getHash(hashN);
		int index = (int)(hashOrig % ttable.length);
		TTEntry entry = ttable[index];
		if (entry == null) {
			entry = new TTEntry();
			ttable[index] = entry;

		// Replacement scheme.
		// Because the hash includes depth, shallower entries
		// are automatically retained.  This is because a identical
		// board position is evaluated differently at different
		// depths, so the score is valid only at exactly the
		// same depth.  So these entries can be helpful even
		// at deeper searches.  They are also needed for PV
		// because of repetitive moves.
		//
		// As such, replacement rarely
		// happens until the table is mostly full.
		// In the event of a collision,
		// retain the entry if deeper and current
		// (deeper entries have more time invested in them)
		} else if ((entry.depth > n || bestmove == -1)
			&& moveRoot == entry.moveRoot
			&& entry.bestMove != -1) {
			log(DETAIL, " collision " + index);
			return;
		}

		entry.type = searchType;
		entry.flags = entryType;
		entry.moveRoot = moveRoot;
		entry.hash = hashOrig;
		entry.bestValue = vm - dvr;
		entry.bestMove = bestmove;
		entry.depth = n;
		entry.turn = b.bturn;	//debug

		if (hashN == 0)
			log(DETAIL, " " + entryType.toString().substring(0,1) + " " + index + " " + negQS(vm) + " " + negQS(vm - dvr));
	}

	// Note: negamax is split into two parts
	// Part 1: check transposition table and qs
	// Part 2: check killer move and if necessary, iterate through movelist

	private int negamax(int n, int alpha, int beta, int depth, Move killerMove, int dvr) throws InterruptedException
	{
		if (bestMove != 0
			&& stopTime != 0
			&& System.currentTimeMillis( ) > stopTime) {

		// reset the board back to the original
		// so that logPV() works

			for (int i = 0; i < depth; i++)
				b.undo();

			log(String.format("abort at %d", depth));
			throw new InterruptedException();
		}

		int alphaOrig = alpha;
		long hashOrig = b.getHash();
		int index = (int)(hashOrig % ttable.length);
		TTEntry entry = ttable[index];
		int ttmove = -1;
		TTEntry.SearchType searchType;
		int bestmove = -1;
		if (deepSearch != 0)
			searchType = TTEntry.SearchType.DEEP;
		else
			searchType = TTEntry.SearchType.BROAD;

		if (entry != null
			&& entry.hash == hashOrig
			&& entry.turn == b.bturn
			&& entry.depth >= n) {

		// Note that the same position from prior moves
		// does not have the same score,
		// because the AI assigns less value to attacks
		// at greater depths.  However, the best move 
		// is still useful and often will generate the best score.

			if (moveRoot == entry.moveRoot
				&& (searchType == entry.type
					|| entry.type == TTEntry.SearchType.BROAD)) {
				if (entry.flags == TTEntry.Flags.EXACT) {
					killerMove.setMove(entry.bestMove);
					log(DETAIL, " exact " + index + " " + negQS(entry.bestValue) + " " + negQS(entry.bestValue + dvr));
					return entry.bestValue + dvr;
				}
				else if (entry.flags == TTEntry.Flags.LOWERBOUND)
					alpha = Math.max(alpha, entry.bestValue + dvr);
				else if (entry.flags== TTEntry.Flags.UPPERBOUND)
					beta = Math.min(beta, entry.bestValue + dvr);
				if (alpha >= beta) {
					killerMove.setMove(entry.bestMove);
					log(DETAIL, " cutoff " + index + " " + negQS(entry.bestValue) + " " +  negQS(entry.bestValue + dvr));
					return entry.bestValue + dvr;
				}
			}
		}

		if (n > 1) {
			long ttMoveHash = getHash(n-1);
		
			TTEntry ttMoveEntry = ttable[(int)(ttMoveHash % ttable.length)];
			if (ttMoveEntry != null
				&& ttMoveEntry.hash == ttMoveHash
				&& ttMoveEntry.turn == b.bturn)
				ttmove = ttMoveEntry.bestMove;
		}

		int vm;
		if (n < 1
			|| (depth != 0
				&& b.getLastMove() != null
				&& b.getLastMove().tp != null
				&& b.getLastMove().tp.getRank() == Rank.FLAG)) {
			vm = qscache(depth, alpha, beta, dvr);
			// save value of position at hash 0 (see saveTTEntry())
			saveTTEntry(0, n, searchType, TTEntry.Flags.EXACT, vm, dvr, -1);
			return vm;
		}

		// Prevent null move on root level
		// because a null move is unplayable in this game.
		// (not sure how this happened, but it did)
		if (depth == 0 && ttmove == 0)
			ttmove = -1;

		vm = negamax2(n, alpha, beta, depth, killerMove, ttmove, dvr);

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
		// The solution is to not store any positions resulting from
		// chase moves into the transposition table.

		UndoMove m = b.getLastMove(1);
		if (m != null && b.grid.hasAttack(m.getPiece()))
			return vm;

		assert hashOrig == b.getHash() : "hash changed";

		TTEntry.Flags entryType;
		if (vm <= alphaOrig)
			entryType = TTEntry.Flags.UPPERBOUND;
		else if (vm >= beta)
			entryType = TTEntry.Flags.LOWERBOUND;
		else
			entryType = TTEntry.Flags.EXACT;

		// save each move at each ply for PV
		saveTTEntry(n, n, searchType, entryType, vm, dvr, killerMove.getMove());
		// save value of position at hash 0 (see saveTTEntry())
		saveTTEntry(0, n, searchType, entryType, vm, dvr, killerMove.getMove());

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

	private int negamax2(int n, int alpha, int beta, int depth, Move killerMove, int ttMove, int dvr) throws InterruptedException
	{
		int bestValue = -9999;
		Move kmove = new Move(null, -1);
		int bestmove = 0;

		if (ttMove != -1) {

		// use best move from transposition table for move ordering
		// best move entries in the table are not tried
		// if a duplicate of the killer move

			if (ttMove != 0 && !isValidMove(ttMove))
				log(PV, n + ":" + ttMove + " bad tt entry");
			else {

			logMove(n, ttMove, b.getValue());
			MoveType mt = makeMove(n, depth, ttMove);
			if (mt == MoveType.OK
				|| mt == MoveType.CHASER
				|| mt == MoveType.CHASED) {

				int vm = -negamax(n-1, -beta, -alpha, depth + 1, kmove, dvr + depthValueReduction(depth+1));

				long h = b.getHash();
				b.undo();

				log(DETAIL, " " + negQS(vm) + " " + MoveType.TE);

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[ttMove]+=n;
					killerMove.setMove(ttMove);
					return vm;
				}

				bestValue = vm;
				bestmove = ttMove;
			}
			}
		}

		// Try the killer move before move generation
		// to save time if the killer move causes ab pruning.
		int km = killerMove.getMove();
		if (km != -1
			&& km != 0 // null move legal only if pruned
			&& km != ttMove
			&& isValidMove(km) 
			&& (Grid.isAdjacent(km)
				|| isValidScoutMove(km))) {
			logMove(n, km, b.getValue());
			MoveType mt = makeMove(n, depth, km);
			if (mt == MoveType.OK
				|| mt == MoveType.CHASER
				|| mt == MoveType.CHASED) {
				int vm = -negamax(n-1, -beta, -alpha, depth + 1, kmove, dvr + depthValueReduction(depth+1));
				long h = b.getHash();
				b.undo();
				log(DETAIL, " " + negQS(vm) + " " + MoveType.KM);
				
				if (vm > bestValue) {
					bestValue = vm;
					bestmove = km;
				}

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[km]+=n;
					return bestValue;
				}
			}
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

		ArrayList<ArrayList<Integer>> moveList = null;

		// Use rootMoveList for a chase because it
		// is heavily pruned.
		if (depth == 0)
			moveList = rootMoveList;
		else {
			moveList = new ArrayList<ArrayList<Integer>>();
			boolean isPruned = getMoves(moveList, n, depth, b.bturn);
			// FORWARD PRUNING
			// Add null move
			if (isPruned)
				addMove(moveList.get(APPROACH), 0);
		}

		for (int mo = 0; mo <= LOSES; mo++) {

			// It is tempting to discard moves in LOSES.
			// This works if LOSES is always a loss.
			// But there could be some cases where LOSES is
			// actually a win.  TBD: fix this
			// Here is an example:
			// R4 -- R? --
			// R? B? -- --
			// -- xx xx --
			// Unknown Blue was chasing Red Four and had
			// a suspected rank of Three.  But then it
			// got trapped by unknown Red pieces.  This
			// gave it a chase rank of Unknown.  That
			// makes it fair game to attack with Red Four.
			// B?xR4 is LOSES, but then Blue actually plays
			// B?xR4.
			// if (mo == LOSES && depth != 0)
			//	continue;

			ArrayList<Integer> ml = moveList.get(mo);
			for (int i = 0; i < ml.size(); i++) {
				int max = sortMove(ml, i);

		// skip ttMove and killerMove

				if (max == ttMove
					|| (max != 0 && max == km))
					continue;

				logMove(n, max, b.getValue());
				MoveType mt = makeMove(n, depth, max);
				if (!(mt == MoveType.OK
					|| mt == MoveType.CHASER
					|| mt == MoveType.CHASED))
					continue;

				int vm = -negamax(n-1, -beta, -alpha, depth + 1, kmove, dvr + depthValueReduction(depth+1));

				long h = b.getHash();

				b.undo();

				log(DETAIL, " " + negQS(vm));

				if (vm > bestValue) {
					bestValue = vm;
					bestmove = max;
				}

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[bestmove]+=n;
					killerMove.setMove(bestmove);
					return vm;
				}
			} // moveList
		} // move order

		hh[bestmove]+=n;
		killerMove.setMove(bestmove);
		return bestValue;
	}

	private MoveType makeMove(int n, int depth, int tryMove)
	{
		// NOTE: FORWARD TREE PRUNING (minor)
		// isRepeatedPosition() discards repetitive moves.
		// This is done now rather than during move
		// generation because most moves
		// are pruned off by alpha-beta,
		// so calls to isRepeatedPosition() are also pruned off,
		// saving a heap of time.

		MoveType mt = MoveType.OK;

		if (tryMove == 0) {

		// A null move does not change the board
		// so it doesn't change the hash.  But the hash
		// could have been saved (in ttable or possibly boardHistory)
		// for the opponent.  Even if the board is the same,
		// the outcome is different if the player is different.
		// So set a new hash and reset it after the move.
			b.pushNullMove();
			return mt;
		}

		// Immobile Pieces
		// Bombs and the Flag are not legal moves.  However,
		// the AI generates moves for unknown bombs because
		// the apparent rank is unknown to the opponent, so
		// these pieces can protect another piece as a bluff.
		if (depth == 0) {
			Piece p = b.getPiece(Move.unpackFrom(tryMove));
			if (p.getRank() == Rank.BOMB
				|| p.getRank() == Rank.FLAG) {
				return MoveType.IMMOBILE;
			}
		}


		if (b.isTwoSquares(tryMove))
			return MoveType.TWO_SQUARES;

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).

		if (Settings.twoSquares
			|| b.bturn == Settings.topColor) {

			if (b.isChased(tryMove)) {

	// Piece is being chased, so repetitive moves OK
	// but can it lead to a two squares result?

				if (b.isPossibleTwoSquares(tryMove))
					return MoveType.POSS_TWO_SQUARES;

				b.move(tryMove, depth);
				mt = MoveType.CHASED;
			} else if (b.bturn == Settings.topColor) {

				if (b.isTwoSquaresChase(tryMove)) {

		// Piece is chasing, so repetitive moves OK
		// (until Two Squares Rule kicks in)

					b.move(tryMove, depth);
					mt = MoveType.CHASER;
				} else {

	// Because isRepeatedPosition() is more restrictive
	// than More Squares, the AI does not expect
	// the opponent to abide by this rule as coded.

					b.move(tryMove, depth);
					if (b.isRepeatedPosition()) {
						b.undo();
						return MoveType.REPEATED;
					}
				}
			} else
				b.move(tryMove, depth);
		} else
			b.move(tryMove, depth);

		return mt;
	}

	private long getHash(int d)
	{
		return b.getHash() ^ depthHash[b.bturn][d];
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
		for (Piece fp : b.pieces[Settings.topColor]) {
			if (fp == null)	// end of list
				break;

			if (!fp.isKnown())
				continue;

		// If the piece is near the flag, the target might be
		// the AI flag rather than the chased piece,
		// so a broad search is used.

			if (b.isNearAIFlag(fp.getIndex()))
				continue;

			if (!b.grid.isCloseToEnemy(Settings.topColor, fp.getIndex(), MAX_STEPS))
				continue;

			int attackers = 0;
			for (Piece tp : b.pieces[Settings.bottomColor]) {
				if (tp == null)	// end of list
					break;

				if (tp.getRank() == Rank.UNKNOWN
					|| tp.getRank() == Rank.BOMB
					|| tp.getRank() == Rank.FLAG)
					continue;

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

	// If chased X chaser isn't a bad move, the AI
	// isn't concerned about a chase, so continue deep search

				int tmpM = Move.packMove(fp.getIndex(), tp.getIndex());
				int vm = b.getValue();
				b.move(tmpM, 0);
				vm = b.getValue() - vm;
				b.undo();
				if (vm >= -5)
					continue;

				attackers++;
				if (steps > MAX_STEPS)
					continue;

				log("Deep search (" + steps + "):" + tp.getRank() + " chasing " + fp.getRank());

				if (deepSearch == 0
					|| steps > deepSearch) {
					deepSearch = 2*steps-1;

				}
			} //tp

	// If there are two attackers nearby, then use broad search
	// to try to avoid becoming trapped by the two attackers.

			if (attackers >= 2) {
				deepSearch = 0;
				return;
			}
		} //fp

		if (deepSearch != 0)
			log("Deep search (" + deepSearch + ") in effect");
	}

	String logPiece(Piece p)
	{
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

	s += logPiece(b.getPiece(Move.unpackFrom(move)));
	s += (char)(Move.unpackFromX(move)+97);
	s += (Move.unpackFromY(move)+1);
	
	if (b.getPiece(Move.unpackTo(move)) == null) {
		s += "-";
		s += (char)(Move.unpackToX(move)+97);
		s += (Move.unpackToY(move)+1);
		s += " " + logFlags(b.getPiece(Move.unpackFrom(move)));
	} else {
		char X = 'x';
		if (n == 0)
			X = 'X';
		s += X;
		s += logPiece(b.getPiece(Move.unpackTo(move)));
		s += (char)(Move.unpackToX(move)+97);
		s += (Move.unpackToY(move)+1);
		s += " " + logFlags(b.getPiece(Move.unpackFrom(move)));
		s += " " + logFlags(b.getPiece(Move.unpackTo(move)));
	}
	return s;
	}

	void logMove(int n, int move, int valueB)
	{
		if (Settings.debugLevel >= DETAIL)
			log.print( "\n" + n + ":" + logMove(b, n, move) + " " + valueB);
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

	private void logPV(int n, int depth)
	{
		if (n == 0)
			return;
		long hash = getHash(n);
		int index = (int)(hash % ttable.length);
		TTEntry entry = ttable[index];
		if (entry == null
			|| hash != entry.hash)
			return;
		if (entry.bestMove == 0) {
			log(PV,  "   (null)\n");
			b.pushNullMove();
		} else if (entry.bestMove == -1) {
			log(PV,  "   (end of game)\n");
			return;
		} else {
			log(PV, "   " + logMove(b, n, entry.bestMove) + "\n");
			b.move(entry.bestMove, depth);
		}
		logPV(--n, ++depth);
		b.undo();
	}
}

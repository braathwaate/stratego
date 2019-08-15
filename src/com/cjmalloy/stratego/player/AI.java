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
	private Piece lastMovedPiece;

	// Static move ordering takes precedence over dynamic
	// move ordering.  All active moves (pieces adjacent to
	// opponent pieces) are considered before inactive
	// moves and scout far moves are considered last.
	// The idea is that attacks and flees will cause
	// alpha-beta cutoffs.  These are almost always among the
	// best moves because non-forced losing attacks are
	// discarded.  All moves are dynamically ordered as well.

	static final int NULL = 0;
	static final int ACTIVE = 1;
	static final int INACTIVE = 2;
	static final int IMMOBILE = 3;
	static final int FAR = 4;

	private static int[] dir = { -11, -1,  1, 11 };
	private int[] hh = new int[2<<14];	// move history heuristic
	private TTEntry[][] ttable = new TTEntry[2][2<<18]; // 262144
	private final int QSMAX = 4;	// maximum qs search depth
	int bestMove = 0;
	long stopTime = 0;
	int moveRoot = 0;
	int completedDepth = 0;
	int deepSearch = 0;

	enum MoveResult {
		TWO_SQUARES,
		POSS_TWO_SQUARES,
		POINTLESS_CHASE,
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

	static private long[] twoSquaresHash = new long[3];
	static {
		Random rnd = new Random();
        for (int i = 0; i < 3; i++)
            twoSquaresHash[i] = Math.abs(rnd.nextLong());
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
			+ Settings.aiLevel * Settings.aiLevel * 10;

		b = new TestingBoard(board);
		log(b.getDebugInfo());
                try
                {
		// Settings tick marks:
		// 1: .01 sec
		// 2: .04 sec
		// 3: .09 sec
		// 4: .16 sec
		// 5: .25 sec
		// 6: .36 sec
		// 7: .49 sec
		// 8: .64 sec
		// 9: .81 sec
		// 10: 1.0 sec
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
			else if (bestMove == -1)
				log("bestMove is erroneously -1");
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
		int [][] plan = b.getPlan(fp);

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

			int vbest = 99;
			int tbest = 99;
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

				int v = 99;
				if (plan != null && plan[1][t] != 0)
					v = plan[0][t];
				if (v < vbest) {
					tbest = t;
					vbest = v;
				}
				t += d;
				p = b.getPiece(t);
			};

		// If n is 1 and the move is not an attack,
		// then the scout move cannot be the best move
		// because it has run out of moves to reach its target.

			if (tbest != 99 
				&& n > 1)
				addMove(moveList, i, tbest);

			if (p.getColor() == 1 - b.bturn)
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

	// n > 0: prune off inactive moves
	// n = 0; no pruning
	// n < 0: prune off active moves

	public boolean getMoves(int n, ArrayList<Integer>[] moveList, int i)
	{
		Piece fp = b.getPiece(i);
		Rank fprank = fp.getRank();
                assert !(fprank == Rank.BOMB || fprank == Rank.FLAG) : "getMoves only for movable pieces, not " + fp.isKnown() + " " + fprank + " at " + i;

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

        // Unknown unmoved opponent pieces are also not
        // very scary to the AI.
        // Prior to version 9.3, the AI didn't generate moves
        // for any unknown unmoved opponent piece unless
        // it could attack on its first move.  This effective
        // forward pruning was generalized in 9.3 to include
        // all inactive pieces in version 9.4.

        // But version 9.4 suffers from a horizon effect
        // when these pieces are in the active area, but just far
        // enough away from potential AI targets to cause a horizon
        // effect if the unknown piece can reach an advantageous target.
        // The AI would then assume that is the best move and
        // allow some lesser attack on its pieces,
        // thus losing material.  (Mitigated by distanceFactor()).

        // So in version 9.5, the AI prunes off moves by
        // any unknown opponent piece more than 1 space away, unless
        // the unknown piece was the last piece moved by
        // the opponent.

		// (Note: if n <= 3, those moves are already pruned off)

        // Pruning in this fashion often works, but consider this
        // example:
        // -- r? xx xx R1 -- xx xx -- --
        // -- -- -- r? -- -- -- -- -- --
        // -- -- -- r? -- -- r? B8 -- --
        // r? -- r? -- -- r? RB -- r? --
        // r? -- r? r? -- RB RF RB -- rB
        // Unknown Red on g8 flees from Blue Eight to g7, giving
        // Blue Eight clear access to the flag structure after
        // 8h8-g8.  But the unknown Red on f9 gets pruned off,
        // so Red plays some other move allowing the opponent to play f9-f8,
        // blocking access to the flag structure.

        // Version 13 excludes pruning of pieces near the flag structure

		if (n > 3
			&& fprank == Rank.UNKNOWN
			&& fp != lastMovedPiece
			&& !b.grid.isCloseToEnemy(b.bturn, i, 1)
            && !b.isNearOpponentFlag(i))
			return true;

		if (b.grid.hasAttack(b.bturn, i))
			getAllMoves(moveList[ACTIVE], i);
		else
			getAllMoves(moveList[INACTIVE], i);

		return false;
	}

	boolean genSafe(int i, boolean unsafe, BitGrid unprunedGrid)
	{
		int dir = -11;
		if (b.bturn == Settings.bottomColor)
			dir = 11;
			
		Piece p = b.getPiece(i);
		if (p != null) {
			if (p.getColor() != 1 - b.bturn) {
				if (unsafe
					&& p.getColor() == b.bturn 
					&& b.isNineTarget(p))
					return true;
				return false;
			} else if (p.getRank() != Rank.UNKNOWN
				&& p.getRank() != Rank.NINE)
				genSafe(i + dir, false, unprunedGrid);
			else
				genSafe(i + dir, true, unprunedGrid);
		} else if (genSafe(i + dir, unsafe, unprunedGrid)) {
			unprunedGrid.setBit(i);
			return true;
		}
		return false;
	}

	void genSafe(BitGrid unprunedGrid)
	{
		final int[] lanes = { 111, 112, 115, 116, 119, 120 };
		for (int lane : lanes) {
			if (b.bturn == Settings.bottomColor)
				lane -= 99;
			genSafe(lane, false, unprunedGrid);
		}
	}

        void addLastMovedPiece(BitGrid unpruned, BitGrid pruned)
        {
            // Make certain that the last piece that moved will
            // not be pruned off, which happens with the best
            // pruned move because it is outside the pruning area.
            // This is particularly important to protect the
            // bomb structure from an incoming attacker.

            Move m2 = b.getLastMove(2);
            if (m2 != UndoMove.NullMove) {
                Piece p = b.getPiece(m2.getTo());
                if (p == m2.getPiece()
                    && p.getRank() != Rank.BOMB
                    && p.getRank() != Rank.FLAG) {
                    unpruned.setBit(m2.getTo());
                    pruned.clearBit(m2.getTo());
                }
            }
        }

	private boolean getMovablePieces(int n, BitGrid unpruned, BitGrid pruned)
	{
		if (n == 0) {
			b.grid.getMovablePieces(b.bturn, unpruned);
                        return false;
                }

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

		// In version 12.0, the BitGrid is enabled for opponent
		// suspected low ranked pieces as well, so that the
		// opponent piece may flee or some minor intervening
		// piece can block the AI Nine (or unknown).

		BitGrid unprunedGrid = new BitGrid();
		if (b.unknownRankAtLarge(1-b.bturn, Rank.NINE) != 0
			&& ns >= 2)
			genSafe(unprunedGrid);
/*
		// Never prune off a move to the last square, because
		// it may be an unoccupied "ghost" square. See TestingBoard.
		Move m = b.getLastMove(1);
		if (m != UndoMove.NullMove)
			unprunedGrid.setBit(m.getTo());
*/

		// Scan the board for movable pieces inside the active area
                b.grid.getMovablePieces(b.bturn, ns, unprunedGrid, unpruned, pruned);
                addLastMovedPiece(unpruned, pruned);
                return (pruned.get(0) != 0 || pruned.get(1) != 0);
	}

	public void getBombFlagMoves(ArrayList<Integer> moveList, int i)
	{
		Piece fp = b.getPiece(i);
		Rank fprank = fp.getRank();

		assert (fprank == Rank.BOMB || fprank == Rank.FLAG);
                assert (b.grid.hasAttack(b.bturn, i));

                if (fp.isKnown())
                    return;

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

                for (int d : dir ) {
                        int t = i + d ;
                        Piece tp = b.getPiece(t);

                        if (tp != null
                                && tp.getColor() == 1 - b.bturn
                                && b.isEffectiveBombBluff(fp, tp)) {
                                addMove(moveList, i, t);
                        }
                } // d
        }

	private void getBombFlagMoves(ArrayList<Integer> moveList)
	{
                if (b.grid.pieceCount(b.bturn) - (b.rankAtLarge(b.bturn, Rank.BOMB) + 1) == b.grid.movablePieceCount(b.bturn))
                    return;

		BitGrid bg = new BitGrid();

		// Find the neighboring bombs and flags
                // of opponent pieces (1 - b.turn)
		// (In other words, find the player's bombs and flags
		// that are under direct attack).

		b.grid.getNeighboringBombsAndFlag(1-b.bturn, bg);
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

				getBombFlagMoves(moveList, i);
			} // data
		} // bi
	}

	private boolean getMoves(BitGrid bg, ArrayList<Integer>[] moveList, int n)
	{
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
		BitGrid unpruned = new BitGrid();
		BitGrid pruned = new BitGrid();
		boolean isPruned = getMovablePieces(n, unpruned, pruned);
		return getMoves(unpruned, moveList, n) || isPruned;
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

			if (!b.grid.hasMove(fp))
				continue;

			Rank fprank = fp.getRank();
			if (fprank == Rank.NINE)
				getScoutFarMoves(n, moveList, i);

		// Any unknown piece could be a Scout.
        // But if the piece has a suspected rank
		// or if it chased an Unknown, it is much less
		// likely that the piece is a Scout. 

			else if (!fp.isKnown()
                && !fp.isSuspectedRank()
				&& fp.getActingRankChase() != Rank.UNKNOWN)
				getAttackingScoutFarMoves(moveList, i);
		}
	}

    // If the opponent's last move provided any new info
    boolean lastMoveInfo()
    {
        UndoMove lastMove = b.getLastMove(1);
        return (lastMove != UndoMove.NullMove
            && lastMove.tp != null);
    }

// Silly Java warning:
// Java won't let you declare a typed list array like
// public ArrayList<Piece>[] scouts = new ArrayList<Piece>()[2];
// and then it warns if you created a non-typed list array.
@SuppressWarnings("unchecked")
	private void getBestMove() throws InterruptedException
	{
		int tmpM = 0;
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
		if (lastMove != UndoMove.NullMove) {
			lastMoveTo = lastMove.getTo();
			Piece p = b.getPiece(lastMoveTo);
		// make sure last moved piece is still on the board
			if (p != null && p.equals(lastMove.getPiece()))
				lastMovedPiece = p;
		}

		// move history heuristic (hh)
		for (int j=0; j < hh.length; j++)
			hh[j] = 0;

		completedDepth = 0;

		genDeepSearch();

        // On non-dedicated computers, the amount of resource
        // available to the AI will vary from move to move
        // due to other consumptive tasks running at the same time.
        // The difference in ply can be quite dissimilar.
        // One move might get 8 ply and the next only 2 ply.
        // Thus the analysis from the prior is often more accurate,
        // subject to the lastMoveInfo() constraints.
        // So the AI begins the search starting from
        // the basis of the prior move.

        int nstart=1;
		long hashOrig = getHash();
		int index = (int)(hashOrig % ttable[b.bturn].length);
		TTEntry entry = ttable[b.bturn][index];

        bestMove = 0;
		if (entry != null
            && entry.hash == hashOrig
            && !lastMoveInfo()
            && entry.bestMove != -1
            && b.validMove(entry.bestMove)) {
                nstart = Math.max(1, entry.depth - 2);
                log("\n<<< Reusing prior move state starting at " + nstart);
                bestMove = entry.bestMove;
        }

		// Iterative Deepening

		for (int n = nstart; n < MAX_PLY; n++) {

		Move killerMove = new Move(null, -1);
		Move returnMove = new Move(null, -1);

		log(DETAIL, "\n>>> pick best move");
		int vm = negamax(n, -22222, 22222, killerMove, returnMove); 

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

		if (n == nstart
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
			MoveResult mt = makeMove(bestMovePly);
			vm = -negamax(n+1, -22222, 22222, killerMove, returnMove); 
			b.undo();
			log(DETAIL, " " + b.boardValue(vm));


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

		if (bestMove != -1)
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
	// the player a chance to respond in kind.  But this fails
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
	// decrementing n.  This makes the problem much less likely
	// to occur, because the opponent needs to have QSMAX attacks
	// rather than QSMAX/2 attacks. Implemented by Version 11.

	private int qs(int n, int alpha, int beta)
	{
		int bvalue = b.boardValue(b.getValue());
        
		if (n < 1)
			return bvalue;

		// qs is the better of a null move or its attacks,
		// in case the attacks worsen the position

		b.pushMove(UndoMove.FleeMove);
		int best = -qsbest( n, -beta, -alpha, -bvalue);
		b.undo();
		best = qsbest(n, alpha, beta, best);
        return best;
	}


	private int qsbest(int n, int alpha, int beta, int best)
	{
		alpha = Math.max(alpha, best);

		if (alpha >= beta)
			return best;

		BitGrid bg = new BitGrid();

		// Find the neighbors of opponent pieces (1 - b.turn)
		// (In other words, find the player's pieces
		// that are under direct attack).

		b.grid.getNeighbors(1-b.bturn, bg);

		// Note: Version 10.3 checks this AFTER null move, because
		// player might not have any movable pieces but opponent
		// eight could be next to player flag! (Version 10.4
		// actually fixed the bug by returning "best" instead
		// of "bvalue").
		//
		// As qs is defined, if there are no neighbors,
		// qs is the value of the board if the player makes
		// a null move, allowing the opponent's movable pieces
		// to continue racking up points.

		if (bg.get(0) == 0 && bg.get(1) == 0)
			return best;

        int maxvm = best;   // attacks are optional
        int maxvm1 = best;
        int maxvm2 = best;
        Move lastmove = b.getLastMove();

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

                boolean isBombOrFlag = (fprank == Rank.BOMB
                    || fprank == Rank.FLAG);
                if (isBombOrFlag
                    && fp.isKnown())
                    continue;

                int enemies = b.grid.enemyCount(fp);
                for (int d : dir ) {
                    int t = i + d;	

                    Piece tp = b.getPiece(t); // defender
                    if (tp == null
                        || tp.getColor() != 1 - b.bturn
                        || (isBombOrFlag
                                && !b.isEffectiveBombBluff(fp,tp)))
                        continue;

                    int move = Move.packMove(i, t);

        // Version 13 redefines qs so that a player is not rewarded for chasing
        // a opponent piece when another player piece is under immediate attack.  This eliminates
        // chases that are delaying moves for imminent loss.

        // For example,
        // -- -- xx xx -- -- xx xx r? --
        // -- -- -- -- -- -- -- -- -- b8
        // -- -- r? -- -- -- -- rB -- r?
        // -- RB -- B4 -- -- -- -- -- --
        // Blue Eight is under attack and there is nothing that Blue can do about it.
        // If Blue Four chases unknown Red on the left side of the board,
        // Version 12 qs rewarded Blue, because qs would
        // evaluate r?xb8 and then allow B4xr?.  But Blue will simply move unknown Red after
        // each chase until the cows come home and then attack Blue Eight.

        // If there is only one attack and the defender can move,
        // opponent will move it, so qs does not change.  This was the same in Version 12.

        // However, in Version 13, if there are multiple pieces attacked that can move,
        // then qs is minimum of the attacks, because opponent
        // will move the most valuable piece.  In the example, Blue qs is the minimum of B4xr?
        // and B8xr?.  B8xr? is negative, so Blue would choose the null move option instead.
        // Red qs will then be r?xb8, because Blue chose the null move option.

        // Note that this still does nothing to stop chases that delay eventual loss.

                    boolean canflee = false;

        // When n == QSMAX, it is the first time qs is called after the player
        // makes his move.  The opponent is *always* awarded any attack, even
        // if the last move was null.  A null move is just like forfeiting a turn
        // and the opponent can play any move.

        // When n < QSMAX, the first move in qs has already been played.  If the prior
        // move is a flee move or the opponent attacks a piece other than the
        // one the player just used to make a capture,
        // then the opponent is awarded only those attacks
        // where the defender cannot flee or has a choice of multiple attacks.
        // Note that here a flee move is like forfeiting a turn *except* if
        // the opponent wants to attack a piece that can flee.

        // This avoids a reward to a pointless chase when another player piece
        // is under direct attack.

        // Note: the reason why the null and flee move meanings differ is because of the
        // transposition table.  qs does not use the transposition table whereas
        // the search tree does.  A search tree position must return
        // a specific result for a specific position to prevent incorrect results
        // for different positions.

                    if (lastmove == UndoMove.FleeMove
                        || (n < QSMAX
                            && lastmove.getPiece() != tp))
                        for (int fleedir : dir ) {
                            int fleeto = t + fleedir;	// flee square
                            if (!Grid.isValid(fleeto)
                                || fleeto == i)
                                continue;
                            Piece fleetp = b.getPiece(fleeto);

            // Is the piece able to flee?
            // Note: although the piece may be able to flee, the flee move may
            // not be a good one, but that is not possible to determine in qs.
            // Only if the piece is trapped without legal moves is the move
            // value always computed.

                            if ((fleetp == null
                                || fleetp.getColor() != tp.getColor())
                                && !b.isPossibleTwoSquares(Move.packMove(t, fleeto))) {
                                canflee = true;
                                break;
                            }
                        } // for flee dir

                    boolean wasKnown = tp.isKnown();
                    int bvalue = b.boardValue(b.getValue());
                    // log(DETAIL, "\n   qs(" + n + "x.):" + logMove(b, n, move) + " " + b.getValue());
                    b.move(move);
                    int v = -b.boardValue(b.getValue()) - bvalue;

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
		// Version 10.0 has a new theory.  If the target
		// survives and the result is negative, then the move
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
		//
		// Version 11 allows negative moves if the target piece
		// was unknown and unmoved to support foray mining where an expendable
		// piece attacks while a power piece sits in wait.

                    if ((wasKnown || tp.hasMoved())
                        && v < 0
                        && tp == b.getPiece(t)	// lost the attack
                        && enemies < 2) {
                            b.undo();
                            continue;   // do not bother to evaluate move
                    }

                    int vm = -qs(n-1, -beta, -alpha);
                    b.undo();

                    if (!canflee)
                        maxvm = Math.max(maxvm, vm);
                    else if (vm > maxvm1) {
                        maxvm2 = maxvm1;
                        maxvm1 = vm;
                    } else if (vm > maxvm2) {
                        maxvm2 = vm;
                    }

                }   // dir
            } // data
        } // bi

        // if the piece cannot flee or there are 2 or more attacks
        // to consider, the attack move is rewarded

        int vm = Math.max(maxvm, maxvm2);

        // Save worthwhile attack (vm > best)
        // (if vm < best, the player will play
        // some other move)

        if (vm > best)
            best = vm;

        alpha = Math.max(alpha, vm);

        if (alpha >= beta)
            return vm;

		return best;
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

		if (um1 == UndoMove.NullMove && um2 == UndoMove.NullMove)
			return true;

		if (um1 != null
			&& um1.tp != null
			&& um1.tp.getRank() == Rank.FLAG)
			return true;

		return false;
	}

	void saveTTEntry(TTEntry entry, long hashOrig, int index, int n, TTEntry.SearchType searchType, TTEntry.Flags entryFlags, int vm, int bestmove)
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
			entry.exactValue = -22222;
		}

		entry.type = searchType;
		entry.flags = entryFlags;
		entry.moveRoot = moveRoot;
		entry.bestValue = vm;
		entry.bestMove = bestmove;
		entry.hash = hashOrig;
		entry.depth = n;
		if (entryFlags == TTEntry.Flags.EXACT) {
			entry.exactDepth = n;
			entry.exactValue = vm;
		}

		log(DETAIL, " " + entryFlags.toString().substring(0,1) + " " + index);
	}

	// Note: negamax is split into two parts
	// Part 1: check transposition table and qs
	// Part 2: check killer move and if necessary, iterate through movelist

	private int negamax(int n, int alpha, int beta, Move killerMove, Move returnMove) throws InterruptedException
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

		// Note that the same position from prior moves
		// (moveRoot != entry.moveRoot)
		// does not have the same score,
		// because the AI assigns less value to attacks
		// at greater depths.
		//
		// However, until version 11, the best move was used from the prior move.
		// This was normally worthwhile.  But if the opponent piece became
		// suspected after the prior move, the best move might now
		// be pruned off.  For example,
		// -- RS --
		// -- -- xx
		// -- -- xx
		// -- -- B?
		// R4 BB --
		// Red has the move and is worried about B?xRS if unknown Blue
		// turns out to be a Scout.  But if Red Four moves up, then
		// unknown Blue approaches Red Four, unknown Blue is thought to
		// be a Three, and so the AI no longer believes that unknown Blue
		// could be a Scout.  Yet the (old) transposition table still could
		// contain B?xRS.
		//
		// TBD: This problem stems from not updating rank during
		// the tree search, but then updating rank after the player really
		// makes the move.   But chase rank is updated, so maybe the solution
		// is to use chase rank to index the hash table?  Still, the
		// scouts array is updated only after each physical move, so
		// this may not work.

		} else if (entry.hash == hashOrig
			&& moveRoot == entry.moveRoot) {
			if (entry.depth >= n) {
				if (entry.exactDepth >= n) {
					returnMove.setMove(entry.bestMove);
					if (entry.bestMove != 0)
						killerMove.setMove(entry.bestMove);
					log(DETAIL, " exact " + index + " " + b.boardValue(entry.exactValue));
					return entry.exactValue;
				} else {
				if (entry.flags == TTEntry.Flags.LOWERBOUND)
					alpha = Math.max(alpha, entry.bestValue);
				else if (entry.flags== TTEntry.Flags.UPPERBOUND)
					beta = Math.min(beta, entry.bestValue);
				if (alpha >= beta) {
					returnMove.setMove(entry.bestMove);
					if (entry.bestMove != 0)
						killerMove.setMove(entry.bestMove);
					log(DETAIL, " cutoff " + index + " " + b.boardValue(entry.bestValue));
					return entry.bestValue;
				}
				}
			} // entry.depth > n

			ttmove = entry.bestMove;

		} // entry has same hash and root

		int vm;
		if (n < 1 || endOfSearch()) {
			vm = qs(QSMAX, alpha, beta);
			// save value of position at hash 0 (see saveTTEntry())
			saveTTEntry(entry, hashOrig, index, n, searchType, TTEntry.Flags.EXACT, vm, -1);
			return vm;
		}

		// Prevent null move on root level
		// because a null move is unplayable in this game.
		// (not sure how this happened, but it did)
		if (b.depth == -1 && ttmove == 0)
			ttmove = -1;

		vm = negamax2(n, alpha, beta, killerMove, ttmove, returnMove);

		assert hashOrig == getHash() : "hash changed";

		// Note: this is the same as Marsland
		// (A Review of Game Tree Pruning, p. 15)
		// with a slight improvement by qualifying UPPERBOUND
		// by n > 1.  This is because the called negamax
		// function is always exact when n == 0.

		TTEntry.Flags entryFlags;
		if (vm <= alpha && n > 1)
			entryFlags = TTEntry.Flags.UPPERBOUND;
		else if (vm >= beta)
			entryFlags = TTEntry.Flags.LOWERBOUND;
		else
			entryFlags = TTEntry.Flags.EXACT;

		// save value of position at hash 0 (see saveTTEntry())
		saveTTEntry(entry, hashOrig, index, n, searchType, entryFlags, vm, returnMove.getMove());

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

	boolean isValidMove(int from, int to)
	{
		Piece tp = b.getPiece(to);
		if (tp != null) {
                    if (tp.getColor() == b.bturn)
			return false;
                    Piece fp = b.getPiece(from);
                    Rank rank = fp.getRank();
		    if ((rank == Rank.BOMB
                         || rank == Rank.FLAG)
                               && !b.isEffectiveBombBluff(fp, tp))
                        return false;
                }
		return true;
	}

	boolean isValidMove(BitGrid bg, int move)
	{
		int from = Move.unpackFrom(move);
		int to = Move.unpackTo(move);

		if (Grid.isAdjacent(from, to))
			return bg.testBit(from)
                            && isValidMove(from, to);

		// Test for a valid scout move
		// (Note: Scout moves need not be inside the bg pruning area).

		Piece p = b.getPiece(from);
		if (p == null
			|| p.getColor() != b.bturn)
			return false;

		int dir = Grid.dir(to, from);
		while (to != from) {
			from += dir;
			p = b.getPiece(from);
			if (p != null) {
				if (p.getColor() != 1 - b.bturn
					|| from != to)
					return false;
				return true;
			}
		}
		return true;
	}

// Silly Java warning:
// Java won't let you declare a typed list array like
// public ArrayList<Piece>[] scouts = new ArrayList<Piece>()[2];
// and then it warns if you created a non-typed list array.
@SuppressWarnings("unchecked")
	private int negamax2(int n, int alpha, int beta, Move killerMove, int ttMove, Move returnMove) throws InterruptedException
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
			return -22222;	// win
		}
		else if (oppMove == false) {
			returnMove.setMove(-1);
			return 22222;	// loss
		}

		int bestValue = -22222;
		Move kmove = new Move(null, -1);
		int bestmove = -1;

		// Version 10.3 fixes a bug by skipping the TE and KM
		// if they fall outside the pruning area.   This caused moves
		// to be evaluated on an unequal basis.  This was first noticed
		// in the selection of the best pruned move, because often only
		// one of the first moves in the list was selected because
		// the remaining moves picked up a TE or KM outside
		// the pruning area, making them look bad.   The same occurred
		// with the selection of the best move, particularly when
		// the AI flag could be attacked successfully,
		// and only some of the moves considered the attack.

		if (ttMove != -1
			&& ttMove != 0) {

		// Use best move from transposition table for move ordering.

		// Note: if the move is not valid, it can only mean that
		// two different positions had the same hash, which we
		// hope is statistically near impossible.

			// assert isValidMove(ttMove) : n + ":" + ttMove + " bad tt entry";

			logMove(n, ttMove, b.getValue(), MoveType.TE);
			MoveResult mt = makeMove(ttMove);
			if (mt == MoveResult.OK) {

				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove);

				b.undo();

				log(DETAIL, " " + b.boardValue(vm));

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

		} // ttmove

		// Try the killer move before move generation
		// to save time if the killer move causes ab pruning
		// and skips the expensive move generation operation.

		int km = killerMove.getMove();
		assert km != 0 : "Killer move cannot be null move";

		BitGrid unpruned = new BitGrid();
		BitGrid pruned = new BitGrid();
		boolean isPruned = getMovablePieces(n, unpruned, pruned);

		if (km != -1
			&& km != ttMove
			&& isValidMove(unpruned, km)) {
			logMove(n, km, b.getValue(), MoveType.KM);
			MoveResult mt = makeMove(km);
			if (mt == MoveResult.OK) {
				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove);
				b.undo();
				log(DETAIL, " " + b.boardValue(vm));
				
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
		} // killer move

		if (b.depth == -1) {
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
		//
		// TBD: Localized minimax
		// Pieces in the game of stratego have limited mobility,
		// and this fact is used to advantage in the AI in
		// the forward pruning mechanism.  Yet further improvement
		// is possible by grouping pieces into independent sets
		// where pieces in those sets cannot reach the enemy pieces
		// in the other sets given the amount of search depth.
		// Then the minimax function is applied to each set twice,
		// allowing either player to move first.   The best move
		// is then the maximum of the players moves minus the
		// maximum of the opponents moves in the other sets.
		//
		// This grouping would greatly reduce the number of moves
		// that have to be considered together, resulting in
		// an exponential increase in speed with no loss
		// in accuracy.  Furthermore, it makes headway into
		// solving the pointless chase problem, where the opponent
		// has a good move in one area of the board, but because
		// the opponent has a pointless chase sequence in
		// some other area of the board, it pushes the ability
		// to see the good move beyond the horizon.
		// An additional benefit is the ability to run the sets
		// in parallel (multithreading).


			ArrayList<Integer>[] moveList = (ArrayList<Integer>[])new ArrayList[FAR+1];
			for (int i = 0; i <= FAR; i++)
				moveList[i] = new ArrayList<Integer>();

			getMoves(pruned, moveList, -n);
			int bestPrunedMoveValue = -22222;
			int bestPrunedMove = -1;
			for (int mo = 0; mo <= INACTIVE; mo++)
			for (int move : moveList[mo]) {
				logMove(2, move, 0, MoveType.PR);
				MoveResult mt = makeMove(move);
				if (mt == MoveResult.OK) {

		// Note: negamax(0) isn't deep enough because scout far moves
		// can cause a move outside the active area to be very bad.
		// For example, a Spy might be far away from the opponent
		// pieces and could be selected as the best pruned move.
		// But moving it could lose the Spy to a far scout move.
		// So negamax(1) is called to allow the opponent scout
		// moves to be considered (because QS does not currently
		// consider scout moves).

					int vm = -negamax(1, -22222, 22222, killerMove, returnMove); 
					if (vm > bestPrunedMoveValue) {
						bestPrunedMoveValue = vm;
						bestPrunedMove = move;
					}
					b.undo();
					log(DETAIL, " " + b.boardValue(vm));
				} else
					log(DETAIL, " " + mt);
			} // moves
			log(DETAIL, "\n<< pick best pruned move\n");

			if (bestPrunedMove != -1) {

			logMove(n, bestPrunedMove, b.getValue(), MoveType.PR);
			MoveResult mt = makeMove(bestPrunedMove);
			assert mt == MoveResult.OK : "Pruned move tested OK above?";

			int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove);

			b.undo();

			log(DETAIL, " " + b.boardValue(vm));

			if (vm > bestValue) {
				bestValue = vm;
				bestmove = bestPrunedMove;
			}
			alpha = Math.max(alpha, vm);

			if (alpha >= beta) {
				hh[bestmove]+=n;
				returnMove.setMove(bestmove);
				return bestValue;
			}

			} // pruned move found
			} // isPruned

		}

		ArrayList<Integer>[] moveList = (ArrayList<Integer>[])new ArrayList[FAR+1];
		for (int i = 0; i <= FAR; i++)
			moveList[i] = new ArrayList<Integer>();

		outerloop:
		for (int mo = NULL; mo <= FAR; mo++) {

		// If legal moves were pruned before move
		// generation, then try the null move before move
		// generation.  If the null move causes alpha-beta
		// pruning, then the move generation is skipped,
		// saving a heap of time.
		//
		// Note: Prior to Version 10.4, if ttmove was null,
		// it was tried in the ttmove code.  But we don't
		// know if a null move is valid until after we
		// check isPruned.  So version 10.4 now always
		// tries the null move after killer move and
		// before move generation.

			if (mo == NULL) {
				if (!isPruned
					|| b.depth == -1)
					continue;
				addMove(moveList[NULL], 0);

		// FORWARD PRUNING
		// Add null move if not processed already

			} else if (mo == ACTIVE) {
				if (getMoves(unpruned, moveList, n)
					&& b.depth != -1
					&& !isPruned)
					addMove(moveList[INACTIVE], 0);

		// Immobile Pieces
		// Bombs and the Flag are not legal moves.  However,
		// the AI generates moves for unknown bombs because
		// the apparent rank is unknown to the opponent, so
		// these pieces can protect another piece as a bluff.

			} else if (b.depth != -1 && mo == IMMOBILE)
				getBombFlagMoves(moveList[mo]);

		// Scout far moves are expensive to generate and
		// because of the loss of stealth are often
		// bad moves, so these are generated last, hoping
		// that they will get pruned off by alpha-beta.
		// They are generated separately because they are
		// not forward pruned based on enemy distance like other moves.

			else if (mo == FAR)
				getScoutMoves(moveList[mo], n, b.bturn);

		// Sort the move list.
		// Because alpha-beta prunes off most of the list,
		// most game playing programs use a selection sort.

			ArrayList<Integer> ml = moveList[mo];
			for (int i = 0; i < ml.size(); i++) {
				int max = sortMove(ml, i);

		// skip ttMove and killerMove

				if (max != 0
					&& (max == ttMove
						|| max == km))
					continue;

				logMove(n, max, b.getValue(), MoveType.GE);
				MoveResult mt = makeMove(max);
				if (!(mt == MoveResult.OK)) {
					log(DETAIL, " " + mt);
					continue;
				}

				int vm = -negamax(n-1, -beta, -alpha, kmove, returnMove);

				b.undo();

				log(DETAIL, " " + b.boardValue(vm));

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

		// An attack on the piece that just moved is not
		// a legal move for other nodes, so ignore it
		// if it happens to be the best move.


		Move um = b.getLastMove(1);
		if (bestmove != 0
			&& (um == UndoMove.NullMove
				|| b.getLastMove(1).getTo() != Move.unpackTo(bestmove)))
			killerMove.setMove(bestmove);

		return bestValue;
	}

	private MoveResult makeMove(int tryMove)
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
			b.pushMove(UndoMove.NullMove);
			return MoveResult.OK;
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

		// If piece is being chased, repetitive moves OK

			if (b.isChased(Move.unpackFrom(tryMove)))
				b.move(tryMove);
			else {
                if (b.depth > 1 && b.isPointlessChase(tryMove))
                    return MoveResult.POINTLESS_CHASE;
				if (b.bturn == Settings.topColor) {

	// Because isRepeatedPosition() is more restrictive
	// than More Squares, the AI does not expect
	// the opponent to abide by this rule as coded.

					if (b.chaseMove(tryMove)) {
						b.undo();
						return MoveResult.REPEATED;
					}
				} else
					b.move(tryMove);

				if (b.isPossibleTwoSquaresChase())
					b.hashDepth(b.depth);
			}
		} else
			b.move(tryMove);

	// Losing captures are discarded (see qs())

		if (enemies < 2
			&& -b.boardValue(b.getValue() - bvalue) < 0) {
			
			UndoMove m = b.getLastMove(1);
			if (m.tp != null
				&& (m.tpcopy.isKnown() || m.tpcopy.hasMoved())
				&& (!b.isFlagBombAtRisk(m.tpcopy)
					|| m.getPiece().getRank() == Rank.NINE)) {
				Piece tp = b.getPiece(Move.unpackTo(tryMove));
				if (tp == m.tp) {	// lost the attack
                    log(DETAIL, " " + b.getValue());
					b.undo();
					return MoveResult.NEG;
				}
			}
		}

		return MoveResult.OK;
	}

	// An important complication with using a position table (TT)
	// in Stratego is that it is not just the position but how
	// the position was reached that governs the result
	// if the move sequence can lead to a Two Squares ending.
	// For example,
	// | -- -- R6 -- --
	// | -- R2 -- -- B5 
	// | -- B3 BB -- --
	// xxxxxxxxxx

	// Blue Five moves left, Red Six moves left, Blue Three moves left,
	// Red Two moves left.
	// In this case, Red Two still will win Blue Three.

	// But this is not the same position if:
	// Blue Three moves left, Red Six moves left, Blue Five moves left
	// Red Two moves left.

	// So to keep the two positions from being confused,
	// the two squares chase sequence must be retrieved and stored
	// using a different hash table entry.

    // The TT cannot be used if the current position
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
    // position is stored in the TT, and is retrieved for
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
    // separately in the TT.
    //
    // Warning: Moves *must* be stored in the TT and must not be omitted
    // because the TT is used to store the best move from the search,
    // so if the best move is omitted, the principal variation does
    // not work, and perhaps the wrong move is selected as the best move.
    //
    // TBD: also need to check for actual two squares (3 repeating moves)
    // because this is not equivalent to b.isPossibleTwoSquaresChase
    // (2 repeating moves).

	private long getHash()
	{
		int n = b.twoSquaresChases();
        if (n != 0)
			return b.getHash() ^ twoSquaresHash[n-1];

		return b.getHash();
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

				if ((tp.getRank() == Rank.UNKNOWN
                    && !tp.hasMoved())
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

				if (b.isProtected(fp, tp))
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

		// Prior to Version 11, deepSearch was exactly equal to
		// the distance to the attacker.  But if the target AI piece
		// moved away from the attacker, it no longer saw the attacker,
		// and could easily become trapped.  For example:
		// |BB -- B1
		// |-- R2 B3
		// |xxxxxxxx
		// Blue One is two steps away and deepSearch is set.  But Red Two
		// might move to the left, trapping itself because deepSearch would
		// prevent it from seeing Blue One at that distance.
		//
		// So Version 11 adds one more square to the search depth.
		// This means that deep search goes broader and shallower.

		if (deepSearch != 0) {
			deepSearch = (deepSearch + 1) * 2;
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
		if (p.isMaybeEight())
			s += '8';
		else
			s += '.';
		if (p.is(Piece.WEAK))
			s += 'W';
		else
			s += '.';
		if (p.is(Piece.SAFE))
			s += 's';
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
			b.pushMove(UndoMove.NullMove);
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

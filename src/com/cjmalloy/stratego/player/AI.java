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
	static final int NULLMOVE = 2;
	static final int APPROACH = 3;
	static final int FLEE = 4;
	static final int LOSES = 5;

	private static int[] dir = { -11, -1,  1, 11 };
	private int[] hh = new int[2<<14];	// move history heuristic
	private TTEntry[] ttable = new TTEntry[2<<22]; // 4194304
	private final int QSMAX = 3;	// maximum qs search depth
	int bestMove = 0;
	long stopTime = 0;
	int moveRoot = 0;
	int completedDepth = 0;

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
				log(PV, "\n" + logMove(board, 0, bestMove, MoveType.OK));
				// return the actual board move
				engine.aiReturnMove(new Move(board.getPiece(Move.unpackFrom(bestMove)), Move.unpackFrom(bestMove), Move.unpackTo(bestMove)));
			}

			logFlush("----");

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

		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks and far rank

			t += d;
			p = b.getPiece(t);

		// if next-to-adjacent square is invalid or contains
		// the same color piece, a far move is not possible

			if (p != null
				&& p.getColor() != 1 - fpcolor)
				continue;
			while (p == null) {
				if (depth <= 1)
				 	addMove(moveList.get(FLEE), i, t);
				t += d;
				p = b.getPiece(t);
			};
			if (p.getColor() != 1 - fpcolor) {
				if (depth <= 1)
					addMove(moveList.get(FLEE), i, t-d);
			} else {
				int mo = LOSES;
				if (!p.isKnown())
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
	void getPossibleScoutFarMoves(int depth, ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		if (depth > 1)
			return;

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
				int result = b.winFight(fp, tp);
				int mo = LOSES;
				if (result == Rank.WINS ||
					result == Rank.EVEN)
					mo = WINS;
				else if (result == Rank.LOSES
					&& (fprank.toInt() <= 4
						|| tp.isKnown()))
					mo = LOSES;
				else
					mo = ATTACK;
					
				addMove(moveList.get(mo), i, t);
			}
		} // d
	}

	public void getAttackMoves(ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		int i = fp.getIndex();
		int fpcolor = fp.getColor();
		Rank fprank = fp.getRank();

		for (int d : dir ) {
			int t = i + d ;
			Piece tp = b.getPiece(t);

			if (tp == null || tp == Grid.water)
				continue;

			if (tp.getColor() != fpcolor) {
				int result = b.winFight(fp, tp);
				int mo = LOSES;
				if (result == Rank.WINS ||
					result == Rank.EVEN)
					mo = WINS;
				else if (result == Rank.LOSES
					&& (fprank.toInt() <= 4
						|| tp.isKnown()))
					mo = LOSES;
				else
					mo = ATTACK;
					
				addMove(moveList.get(mo), i, t);
			}
		} // d
	}

	public boolean getApproachMoves(int n, ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		boolean hasMove = false;
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

		// if the piece is close enough, the move is allowed
		// 
		boolean allowAll =
			(!(Math.abs(n/2) < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, i, Math.abs(n/2)))
				|| (fpcolor == Settings.topColor
					&& unknownNinesAtLarge > 0
					&& b.isNineTarget(fp)));

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
		// avoidable, so the AI will look to its other pieces on
		// board or resort to a null move and examine the
		// pre-processing values.
		//
		// But Blue Eight is threatening to attack the bomb
		// structure. And obviously the bombs and flags cannot
		// simply run away.
		//
		// So the AI must consider defensive moves outside of
		// the pruning area as well.  Rather than doubling the
		// pruning area, defensive moves are determined
		// by pre-processing, and so the search examines
		// moves towards these goals,
		// even if the moves are outside of the pruning area.

			if (n > 0) {
				if (!allowAll
					&& (n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, n/2)
					&& b.planValue(fp, i, t) <= 1))
					hasMove = true;
				else
					addMove(moveList.get(APPROACH), i, t);
			} else if (n < 0) {
				if (!allowAll
					&& (-n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, -n/2)
					&& b.planValue(fp, i, t) <= 1))
					addMove(moveList.get(APPROACH), i, t);
				else
					hasMove = true;
			}

		} // d

		return hasMove;
	}

	// n > 0: prune off inactive moves
	// n = 0; no pruning
	// n < 0: prune off active moves

	public boolean getMoves(int n, ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		boolean hasMove = false;
		int i = fp.getIndex();

		Rank fprank = fp.getRank();

		if (fprank == Rank.BOMB) {

		// Known bombs are removed from pieces[] but
		// a bomb could become known in the search
		// tree.  We need to generate moves for suspected
		// bombs because the bomb might actually be
		// some other piece, but once the bomb becomes
		// known, it ceases to move.

			if (fp.isKnown())
				return false;

		// Unmoved unknown pieces really aren't very
		// scary to the opponent, unless they can attack
		// on their first move.  The AI has to generate
		// moves for its unmoved unknown pieces because
		// lower ranks may be needed for defense.  But it
		// is unlikely (but possible) that the piece
		// will be part of a successful attack.
		//
		// Prior to version 9.3, the AI didn't generate moves
		// for any unknown unmoved opponent piece unless
		// it could attack on its first move.  This effective
		// forward pruning was generalized in 9.3 to include
		// all inactive pieces.
		//
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

			if (!b.grid.hasAttack(fp))
				return false;

			getAttackMoves(moveList, fp);
			return false;
		}

		if (n > 0) {
			int fpcolor = fp.getColor();

			if (b.grid.hasAttack(fp)) {
				getAllMoves(moveList, fp);
				return false;
			} else
				return getApproachMoves(n, moveList, fp);

		} else if (n < 0) {

		// always return false to avoid null move

			int fpcolor = fp.getColor();
			assert fpcolor == Settings.topColor : "n<0 code only for AI";
			if (b.grid.hasAttack(fp)) {
				return false;
			} else {
				getApproachMoves(n, moveList, fp);
				return false;
			}
		} else {
			getAllMoves(moveList, fp);
		}

		return false;
	}


	private boolean getMoves(ArrayList<ArrayList<Integer>> moveList, int n, int depth, int turn, Piece chasePiece, Piece chasedPiece)
	{
		for (int i = 0; i <= LOSES; i++)
			moveList.add(new ArrayList<Integer>());

		boolean hasMove = false;
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
			// are separated by up to 1 open square.

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

			if (chasePiece != null) {
				if (getMoves(1, moveList, fp))
					hasMove = true;
			} else {
				Rank fprank = fp.getRank();
				if (fprank == Rank.NINE)
					getScoutFarMoves(depth, moveList, fp);

				// NOTE: FORWARD PRUNING
				// generate scout far moves only for attacks on unknown
				// valuable pieces.
				// if there are no nines left, then skip this code
				else if (unknownNinesAtLarge > 0 && fprank == Rank.UNKNOWN)
					getPossibleScoutFarMoves(depth, moveList, fp);

				if (getMoves(n, moveList, fp))
					hasMove = true;
			}
		}

		return hasMove;
	}

	private void getBestMove() throws InterruptedException
	{
		int tmpM = 0;
		bestMove = 0;
		int bestMoveValue = 0;

		// Because of substantial pre-processing before each move,
		// the entries in the transposition table
		// should be cleared to prevent anomolies.
		// But this is a tradeoff, because retaining the
		// the entries leads to increased search depth.
		//ttable = new TTEntry[2<<22]; // 4194304, clear transposition table

		// clear the QS cache
		QSCtable = new QSEntry [2][10007];

		moveRoot = b.undoList.size();

		// chase variables
		Piece chasedPiece = null;
		Piece chasePiece = null;
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

		for (int n = 1; n < MAX_PLY; n++) {

		Move killerMove = new Move(null, -1);

		if (chasePiece == null) {
			rootMoveList = new ArrayList<ArrayList<Integer>>();
			boolean hasMove = getMoves(rootMoveList, n, 0, Settings.topColor, null, null);
			if (hasMove) {

			log("-++-");

		// If any moves were pruned off, choose the best looking one
		// and then evaluate it along with the non-pruned moves
		// that could affect material balance.
		//
		// Why do this forward pruning?  If the AI
		// were to consider all moves, then the best move
		// would be found without all this rigmarole.
		// The problem is that there are just too many moves
		// to consider, resulting in shallow search depth.
		// By pruning off inactive moves, depth is significantly
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
			getMoves(moveList, -n+1, 0, Settings.topColor, null, null);
			int bestPrunedMoveValue = -9999;
			int bestPrunedMove = 0;
			for (int mo = 0; mo <= LOSES; mo++)
			for (int move : moveList.get(mo)) {
				MoveType mt = makeMove(n, 0, move);
				if (mt == MoveType.OK
					|| mt == MoveType.CHASER
					|| mt == MoveType.CHASED) {

		// Note: negamax(0) isn't deep enough because scout far moves
		// can cause a move outside the active area to be very bad.
		// For example, a Spy might be far away from the opponent
		// pieces and could be selected as a null move.  But moving
		// it could lose the Spy to a far scout move.
		// So negamax(1) is called to allow the opponent scout
		// moves to be considered (because QS does not currently
		// consider scout moves).
		// 
					int vm = -negamax(1, -9999, 9999, 1, null, null, killerMove); 
					if (vm > bestPrunedMoveValue) {
						bestPrunedMoveValue = vm;
						bestPrunedMove = move;
					}
					b.undo();
					log(DETAIL, "   " + logMove(b, n, move, mt));
				}
			}
			addMove(rootMoveList.get(NULLMOVE), bestPrunedMove);
			log(PV, "\nPPV:" + n + " " + bestPrunedMoveValue);
			log(PV, logMove(b, n, bestPrunedMove, MoveType.OK));
			log("-++-");

			}
		}

		// DEEP CHASE
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
		// not realize it.  So the deep chase pruning is only
		// used when there are a choice of squares to flee to
		// and it should never attack a chaser if it loses,
		// even if it determines that it will lose the chase
		// anyway and the board at the end the chase is less favorable
		// than the current one.
		//
		// If the chaser is unknown, and the search determines
		// that it will get cornered into a loss situation, then
		// it should still flee until the loss is imminent.
		//
		// To implement this, the deep chase is skipped if
		// the best move from the broad chase is to attack the
		// chaser (which could be bluffing),
		// but otherwise remove the move that attacks
		// the chaser from consideration.

		if (chasePiece == null
			&& lastMovedPiece != null

		// Begin deep chase after xx iterations of broad search
			&& n >= 3
			&& bestMove != 0

		// Deep chase is skipped if best move from broad search
		// is to attack a piece (perhaps the chaser)
			&& b.getPiece(Move.unpackTo(bestMove)) == null

		// Deep chase is skipped if best move value is negative.
		// This indicates that the piece is trapped
		// already or there is something else on the board
		// going on.  So broad search is preferred.
			&& bestMoveValue > -30

		// Limit deep chase to superior pieces.
		// Using deep chase can be risky if the
		// objective of the chaser is not be the chased
		// piece, but some other piece, like a flag or
		// flag bomb.
			&& b.getPiece(Move.unpackFrom(bestMove)).getRank().toInt() <= 4

		// If Two Squares is in effect,
		// deep search only if the best move is not adjacent
		// to the chase piece from-square.
		// Otherwise, the chased piece is in no danger,
		// because the chase piece can move back and forth
		// until Two Squares prevents the chaser from moving.
		//
		// (Note that bestMove cannot lead to a possible two squares
		// result for the AI, because makeMove() prevents it).

			&& !(Settings.twoSquares
				&& Grid.isAdjacent(Move.unpackTo(bestMove), lastMove.getFrom()))) {

		// check for possible chase
			for (int d : dir) {
				int from = lastMoveTo + d;
				if (from != Move.unpackFrom(bestMove))
					continue;

		// chase confirmed:
		// bestMove is to move chased piece 

				int count = 0;
				for (int mo = 0; mo <= LOSES; mo++)
				for (int k = rootMoveList.get(mo).size()-1; k >= 0; k--)
				if (from == Move.unpackFrom(rootMoveList.get(mo).get(k))) {
					int to = Move.unpackTo(rootMoveList.get(mo).get(k));
					Piece tp = b.getPiece(to);
					if (tp != null) {
					
					int result = b.winFight(b.getPiece(from), tp);
		// If chased piece wins or is even
		// continue broad search.
					if (result == Rank.WINS
						|| result == Rank.EVEN) {
						count = 0;
						break;
					}
					else 
						continue;
					}
					count++;
				}

		// Note: if the chased piece has a choice of
		// 1 open square and 1 opponent piece,
		// broad search is continued.  Deep search
		// is not used because the AI may discover
		// many moves later that the open square is
		// a bad move, leading it to attack the
		// opponent piece. This may appear to
		// be a bad choice, because usually the opponent
		// is not highly skilled at pushing the
		// chased piece for so many moves
		// in the optimal direction.  But because
		// the broad search is shallow, the AI can
		// be lured into a trap by taking material.
		// So perhaps this can be solved by doing
		// a half-deep search?
				if (count >= 2) {
		// Chased piece has at least 2 moves
		// to open squares.
		// Pick the better path.
					chasePiece = lastMovedPiece;
					chasePiece.setIndex(lastMoveTo);
					chasedPiece = b.getPiece(from);
					chasedPiece.setIndex(from);

					log("Deep chase:" + chasePiece.getRank() + " chasing " + chasedPiece.getRank());
					for (int mo = 0; mo <= LOSES; mo++)
					for (int k = rootMoveList.get(mo).size()-1; k >= 0; k--)
						if (from != Move.unpackFrom(rootMoveList.get(mo).get(k))
							|| b.getPiece(Move.unpackTo(rootMoveList.get(mo).get(k))) != null)

							rootMoveList.get(mo).remove(k);

					break;
				}
			}
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

		int vm = negamax(n, -9999, 9999, 0, chasePiece, chasedPiece, killerMove); 
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

		if (n == 1
			|| chasePiece != null
			|| n == MAX_PLY - 1) {

		// no horizon effect possible until ply 2

			bestMove = bestMovePly;
			bestMoveValue = vm;

		} else {

		// Singular Extension.
		// The AI accepts the best move only
		// if the value is better (or just slightly worse) than
		// the value of the best move searched 2 plies deeper.
		
			MoveType mt = makeMove(n, 0, bestMovePly);
			vm = -negamax(n+1, -9999, 9999, 1, chasedPiece, chasePiece, killerMove); 
			b.undo();
			logMove(n+2, bestMovePly, b.getValue(), vm, 0, mt);
			
			if (vm > bestMovePlyValue - 10) {
				bestMove = bestMovePly;
				bestMoveValue = vm;
			} else {
				log(PV, "\nPV:" + n + " " + bestMoveValue + " >" + vm + ": best move discarded.");
				log("-+++-");
				continue;
			}
		}

		hh[bestMove]+=n;
		log("-+++-");

		log(PV, "\nPV:" + n + " " + vm);
		logPV(n, 0);
		} // iterative deepening
	}


	// return true if a piece is safely movable.
	// Safely movable means it has an open space
	// or can attack a known piece of lesser rank.
	private boolean isMovable(int i)
	{
		Piece fp = b.getPiece(i);
		Rank rank = fp.getRank();
		if (rank == Rank.FLAG || rank == Rank.BOMB)
			return false;
		for (int d: dir) {
			int j = i + d;
			Piece tp = b.getPiece(j);
			if (tp == null)
				return true;
			if (tp == Grid.water || tp.getColor() == fp.getColor())
				continue;
			int result = b.winFight(fp, tp);
			if (result == Rank.WINS
				|| result == Rank.EVEN)
				return true;
		}
		return false;
	}
			

	// Quiescence Search (qs)
	// Deepening the tree to evaluate captures for qs.
	// 
	// This version gives no credit for the "best attack" on a movable
	// piece on the board because it is likely the opponent will move
	// the defender the very next move.  This prevents the ai
	// from thinking it has won or lost at the end of
	// a chase sequence, because otherwise the qs would give value when
	// (usually, unless cornered) the chase sequence can be extended
	// indefinitely without any material loss or gain.
	//
	// If credit were given for the "best attack", it would mean that
	// the ai would allow one of its pieces to be captured in exchange
	// for a potential attack on a more valuable piece.  But it is
	// likely that the the opponent would just move the valuable piece
	// away until the Two Squares or More Squares rule kicks in.
	//
	// qs can handle complicated evaluations:
	// -- -- --
	// -- R7 --
	// R4 B3 R3
	//
	// Red is AI and has the move.
	//
	// 1. Fleeing is considered first.  Fleeing will return a negative
	// board position (B3xR7) because the best that Red can do is to move
	// R4.
	//
	// 2. The captures R4xB3, R3xB3 and R7xB3 are then evaluated,
	// along with the Blue responses.  
	//
	// Because R3xB3 removes B3 from the board, the board position
	// after blue response will be zero.
	//
	// Hence, the capture board position (0) is greater than the flee board
	// position (-), so qs will return zero.
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
	// Quiescent search with only attack moves limits the
	// horizon effect but does not eliminate it, because
	// the ai is still prone to make bad moves 
	// that waste more material in a position where
	// the opponent has a sure favorable attack in the future,
	// and the ai depth does not reach the position of exchange.
	//
	// Test Example.
	// RB
	// R4 R2
	// B3
	// Blue has the move:
	// 2a. Red flee, best = b.value
	// 2b. R4xB3 is negative, so best is not changed
	// return b.value
	// 1a. Blue flee
	// 2a. Red flee, best = b.value(-100)
	// 2b. R2xB3, best = b.value(+100)
	// return best(+100)
	// 1b. B3xR4
	//
	// If Red has the move:
	// 2a. Blue flee, best = b.value
	// 2b. B3xR4, best = -100
	// return -100 (because Red Four cannot flee)
	// 1a. Red flee
	// 2a. Blue flee, best = b.value(-100) (*)
	// 2b. No attacks remaining
	// return best (-100)
	// 1b. R4xB3
	//
	// (*) this is why QSMAX must be at least 3.
	// This also prevents a single level horizon effect
	// where the ai places another (lesser) piece subject
	// to attack when the loss of an existing piece is inevitable.
	// QSMAX 3 is deep enough to see both captures.
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

	private int qs(int depth, int n, boolean flee)
	{
		if (n < 1)
			return b.getValue();

		boolean bestFlee = false;
		Piece bestTp = null;

		// try fleeing
		b.pushNullMove();
		int best = qs(depth+1, n-1, true);
		b.undo();
		int nextBest = best;

		// "best" is b.getValue() after
		// opponent's best attack if player can flee.
		// So usually qs is b.getValue(), unless the
		// opponent has two good attacks or the player
		// piece under attack is cornered.

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
			if (fprank == Rank.BOMB && fp.isKnown())
			 	continue;

		// TBD: Far attacks by nines or unknown pieces
		// are not handled.  This would improve the
		// accuracy of qs.  isMovable() would have to
		// check the direction of the attack.
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
				boolean canFlee = false;
				int t = i + d;	

				Piece tp = b.getPiece(t); // defender
				if (tp == null
					|| tp == Grid.water
					|| b.bturn == tp.getColor())
					continue;

				if (flee && isMovable(t))
					canFlee = true;

				int tmpM = Move.packMove(i, t);
				b.move(tmpM, depth);

				int vm = qs(depth+1, n-1, false);

				b.undo();

		// Save worthwhile attack (vm > best)
		// (if vm < best, the player will play
		// some other move)

		// bestTp: do not give extra credit for two attacks
		// on the same piece, because if it flees,
		// it nullifies both attacks.

				if (b.bturn == Settings.topColor) {
					if (vm > best) {
						if (tp != bestTp)
							nextBest = best;
						best = vm;
						bestTp = tp;
						bestFlee = canFlee;
					} else if (vm > nextBest && tp != bestTp)
						nextBest = vm;
				} else {
					if (vm < best) {
						if (tp != bestTp)
							nextBest = best;
						best = vm;
						bestTp = tp;
						bestFlee = canFlee;
					} else if (vm < nextBest && tp != bestTp)
						nextBest = vm;
				}
			} // dir
		} // pieces
		} // k

		if (bestFlee)
			best = nextBest;

		return best;
	}

	private int qscache(int depth)
	{

		BitGrid bg = new BitGrid();
		b.grid.getNeighbors(bg);
		int valueB = b.getValue();

		// As qs is defined, if there are no neighbors,
		// qs is valueB

		if (bg.get(0) == 0 && bg.get(1) == 0)
			return valueB;

		// valueBluff() checks for a prior move capture,
		// so qscache is invalid in this case

		return qs(depth, QSMAX, false);
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

	void saveTTEntry(int n, TTEntry.SearchType searchType, TTEntry.Flags entryType, int vm, int bestmove)
	{
		long hashOrig;
		if (bestmove == -1)
			hashOrig = b.getHash();
		else
			hashOrig = getHash(n);

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
			&& entry.bestMove != -1)
			return;

		entry.type = searchType;
		entry.flags = entryType;
		entry.moveRoot = moveRoot;
		entry.hash = hashOrig;
		entry.bestValue = vm;
		entry.bestMove = bestmove;
		entry.depth = n;
		entry.turn = b.bturn;	//debug
	}

	// Note: negamax is split into two parts
	// Part 1: check transposition table and qs
	// Part 2: check killer move and if necessary, iterate through movelist

	private int negamax(int n, int alpha, int beta, int depth, Piece chasePiece, Piece chasedPiece, Move killerMove) throws InterruptedException
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
		if (chasePiece != null)
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
					log("exact " + moveRoot + " " + entry.moveRoot );
					return entry.bestValue;
				}
				else if (entry.flags == TTEntry.Flags.LOWERBOUND)
					alpha = Math.max(alpha, entry.bestValue);
				else if (entry.flags== TTEntry.Flags.UPPERBOUND)
					beta = Math.min(beta, entry.bestValue);
				if (alpha >= beta) {
					killerMove.setMove(entry.bestMove);
					log("cutoff " + moveRoot + " " + entry.moveRoot );
					return entry.bestValue;
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
			vm = negQS(qscache(depth));
			// save value of position at hash 0 (see saveTTEntry())
			saveTTEntry(n, searchType, TTEntry.Flags.EXACT, vm, -1);
			return vm;
		}

		// Prevent null move on root level
		// because a null move is unplayable in this game.
		// (not sure how this happened, but it did)
		if (depth == 0 && ttmove == 0)
			ttmove = -1;

		vm = negamax2(n, alpha, beta, depth, chasePiece, chasedPiece, killerMove, ttmove);

		assert hashOrig == b.getHash() : "hash changed";

		// reuse existing entry and avoid garbage collection
		TTEntry.Flags entryType;
		if (vm <= alphaOrig)
			entryType = TTEntry.Flags.UPPERBOUND;
		else if (vm >= beta)
			entryType = TTEntry.Flags.LOWERBOUND;
		else
			entryType = TTEntry.Flags.EXACT;

		// save each move at each ply for PV
		saveTTEntry(n, searchType, entryType, vm, killerMove.getMove());
		// save value of position at hash 0 (see saveTTEntry())
		saveTTEntry(n, searchType, entryType, vm, -1);

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
			|| (tp != null && tp.getColor() == b.bturn))
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

	private int negamax2(int n, int alpha, int beta, int depth, Piece chasePiece, Piece chasedPiece, Move killerMove, int ttMove) throws InterruptedException
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

			// logMove(n, b, ttMove, b.getValue(), 0, 0, "");
			MoveType mt = makeMove(n, depth, ttMove);
			if (mt == MoveType.OK
				|| mt == MoveType.CHASER
				|| mt == MoveType.CHASED) {

				int vm = -negamax(n-1, -beta, -alpha, depth + 1, chasedPiece, chasePiece, kmove);

				long h = b.getHash();
				b.undo();

				logMove(n, ttMove, b.getValue(), negQS(vm), h, MoveType.TE);

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
		// TBD: killer move can be multi-hop, but then
		// checking for a legal move requires checking all squares
		int km = killerMove.getMove();
		if (km != -1
			&& km != 0 // null move legal only if pruned
			&& km != ttMove
			&& isValidMove(km) 
			&& (Grid.isAdjacent(km)
				|| isValidScoutMove(km))) {
			MoveType mt = makeMove(n, depth, km);
			if (mt == MoveType.OK
				|| mt == MoveType.CHASER
				|| mt == MoveType.CHASED) {
				int vm = -negamax(n-1, -beta, -alpha, depth + 1, chasedPiece, chasePiece, kmove);
				long h = b.getHash();
				b.undo();
				logMove(n, km, b.getValue(), negQS(vm), h, MoveType.KM);
				
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
			boolean hasMove = getMoves(moveList, n, depth, b.bturn, chasePiece, chasedPiece);

			// FORWARD PRUNING
			// Add null move
			// (NULLMOVE evaluates the move before other APPROACH moves,
			// so that if the null move is equal in value,
			// it will be the one selected as the best move.
			// This allows the AI to subsequently chose an inactive move
			// as the best move.
			if (hasMove)
				addMove(moveList.get(NULLMOVE), 0);
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

				MoveType mt = makeMove(n, depth, max);
				if (!(mt == MoveType.OK
					|| mt == MoveType.CHASER
					|| mt == MoveType.CHASED))
					continue;

				int vm = -negamax(n-1, -beta, -alpha, depth + 1, chasedPiece, chasePiece, kmove);

				long h = b.getHash();

				b.undo();

				logMove(n, max, b.getValue(), negQS(vm), h, mt);

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


		if (b.isTwoSquares(tryMove)) {
			logMove(n, tryMove, b.getValue(), 0, 0, MoveType.TWO_SQUARES);
			return MoveType.TWO_SQUARES;
		}

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).

		if (Settings.twoSquares
			|| b.bturn == Settings.topColor) {

			if (b.isChased(tryMove)) {

	// Piece is being chased, so repetitive moves OK
	// but can it lead to a two squares result?

				if (b.isPossibleTwoSquares(tryMove)) {
					logMove(n, tryMove, b.getValue(), 0, 0, MoveType.POSS_TWO_SQUARES);
					return MoveType.POSS_TWO_SQUARES;
				}

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
						logMove(n, tryMove, b.getValue(), 0, 0, MoveType.REPEATED);
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
			s += ' ';
		if (p.isKnown())
			s += 'K';
		else
			s += ' ';
		if (p.isSuspectedRank())
			s += 'S';
		else
			s += ' ';
		if (p.isRankLess())
			s += 'L';
		else
			s += ' ';
		if (p.isBlocker())
			s += 'B';
		else
			s += ' ';
		return s;
	}


	String logMove(Board b, int n, int move, MoveType mt)
	{

	if (move == 0)
		return "(null move) " + mt;

	int color = b.getPiece(Move.unpackFrom(move)).getColor();
	String s = "";
	if (color == 1)
		s += "... ";
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
	if (mt != MoveType.OK)
		s += " " + mt;
	return s;
	}

	void logMove(int n, int move, int valueB, int value, long hash, MoveType mt)
	{
		if (Settings.debugLevel != 0)
			log(DETAIL, n + ":" + logMove(b, n, move, mt) + " " + valueB + " " + value);
	}

	public void logMove(Move m)
	{
		log(PV, logMove(board, 0, m.getMove(), MoveType.OK) + "\n");
	}

	private void log(int level, String s)
	{
		if (Settings.debugLevel >= level)
			log.println(s);
	}

	private void log(String s)
	{
		log(DETAIL, s);
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
			log(PV,  "   (null move)");
			b.pushNullMove();
		} else if (entry.bestMove == -1) {
			log(PV,  "   (end of game)");
			return;
		} else {
			log(PV, "   " + logMove(b, n, entry.bestMove, MoveType.OK));
			b.move(entry.bestMove, depth);
		}
		logPV(--n, ++depth);
		b.undo();
	}
}

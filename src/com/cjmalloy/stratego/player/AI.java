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
	private final int QSMAX = 2;	// maximum qs search depth
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
                } catch (Exception e) {
			e.printStackTrace();
		}
		finally
		{
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
				log(PV, "\n" + logMove(b, 0, bestMove, MoveType.OK));
				long t = System.currentTimeMillis() - startTime;
				t = System.currentTimeMillis() - startTime;
				log("aiReturnMove() at " + t + "ms");
				// return the actual board move
				engine.aiReturnMove(new Move(board.getPiece(Move.unpackFrom(bestMove)), Move.unpackFrom(bestMove), Move.unpackTo(bestMove)));
			}

			logFlush("----");

			long t = System.currentTimeMillis() - startTime;
			t = System.currentTimeMillis() - startTime;
			log("exit getBestMove() at " + t + "ms");
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

	void getScoutFarMoves(ArrayList<ArrayList<Integer>> moveList, Piece fp) {
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

		for (int d : dir ) {
			int t = i + d ;
			if (!Grid.isValid(t))
				continue;
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
				t += d;
				p = b.getPiece(t);
			};
			if (p.getColor() != 1 - fpcolor) {
				t -= d;
				addMove(moveList.get(FLEE), i, t);
			} else {
				int mo = LOSES;
				if (!p.isKnown())
					mo = ATTACK;
				addMove(moveList.get(mo), i, t);
			}
		} // dir
	}

	void getPossibleScoutFarMoves(ArrayList<ArrayList<Integer>> moveList, Piece fp)
	{
		int i = fp.getIndex();
		int fpcolor = fp.getColor();

		for (int d : dir ) {
			int t = i + d ;
			if (!Grid.isValid(t))
				continue;
			Piece p = b.getPiece(t);
			if (p != null)
				continue;

			do {
				t += d;
				p = b.getPiece(t);
			} while (p == null);
			if (!p.isKnown()
				&& p.getColor() == 1 - fpcolor
				&& b.isValuable(p)) {
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
			if (!Grid.isValid(t))
				continue;
			Piece tp = b.getPiece(t);

			if (tp == null) {
				addMove(moveList.get(FLEE), i, t);
			} else if (tp.getColor() != fpcolor) {
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
			if (!Grid.isValid(t))
				continue;
			Piece tp = b.getPiece(t);

			if (tp == null)
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

		for (int d : dir ) {
			int t = i + d ;
			if (!Grid.isValid(t))
				continue;
			Piece tp = b.getPiece(t);
			if (tp != null)
				continue;

			// NOTE: FORWARD TREE PRUNING
			// If a piece moves too far from the enemy,
			// there is no point in generating moves for it,
			// because the value is determined only by pre-processing.
			if (n > 0) {
				if (n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, n/2))
					hasMove = true;
				else
					addMove(moveList.get(APPROACH), i, t);
			} else if (n < 0) {
				 if (-n/2 < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, t, -n/2))
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

		// if the piece is no longer on the board, ignore it
		if (b.getPiece(i) != fp)
			return false;

		if (!b.grid.hasMove(fp))
			return false;

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

		if (fprank == Rank.NINE)
 			getScoutFarMoves(moveList, fp);

		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks on unknown
		// valuable pieces.
		// if there are no nines left, then skip this code
		else if (unknownNinesAtLarge > 0 && fprank == Rank.UNKNOWN)
 			getPossibleScoutFarMoves(moveList, fp);

		// If a piece is too far to reach the enemy,
		// there is no point in generating moves for it,
		// because the value is determined only by pre-processing.
		if (n > 0) {
			int fpcolor = fp.getColor();
			int m = n / 2 + 1;
			if (m < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, i, m))
				return true;

			if (b.grid.hasAttack(fp)) {
				getAllMoves(moveList, fp);
				return false;
			} else
				return getApproachMoves(n, moveList, fp);

		} else if (n < 0) {

		// always return false to avoid null move

			int fpcolor = fp.getColor();
			int m = -n / 2 + 1;
			if (m < Grid.NEIGHBORS && !b.grid.isCloseToEnemy(fpcolor, i, m)) {
				getAllMoves(moveList, fp);
				return false;
			} else if (b.grid.hasAttack(fp)) {
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


	private ArrayList<ArrayList<Integer>> getMoves(int n, int turn, Piece chasePiece, Piece chasedPiece)
	{
		ArrayList<ArrayList<Integer>> moveList = new ArrayList<ArrayList<Integer>>();
		for (int i = 0; i <= LOSES; i++)
			moveList.add(new ArrayList<Integer>());

		// FORWARD PRUNING
		// chase deep search
		// only examine moves adjacent to chase and chased pieces
		// as they chase around the board
		if (chasePiece != null) {
			getMoves(n, moveList, chasedPiece);
			for (int d : dir ) {
				int i = chasePiece.getIndex() + d;
				if (i != chasedPiece.getIndex() && Grid.isValid(i)) {
					Piece p = b.getPiece(i);
					if (p != null && p.getColor() == turn)
						getMoves(n, moveList, p );
				}
			}

			// AI can end the chase by moving some other piece,
			// allowing its chased piece to be attacked.  If it
			// has found protection, this could be a good
			// way to end the chase.
			// Add null move
			addMove(moveList.get(NULLMOVE), 0);

		} else {
		boolean hasMove = false;
		for (Piece np : b.pieces[turn]) {
			if (np == null)	// end of list
				break;
			if (getMoves(n, moveList, np))
				hasMove = true;
		}

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

		return moveList;
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

		rootMoveList = getMoves(0, Settings.topColor, null, null);

		boolean discardPly = false;	// horizon effect
		completedDepth = 0;

		for (int n = 1; n < MAX_PLY; n++) {

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
			&& b.getPiece(Move.unpackFrom(bestMove)).getRank().toInt() <= 4 ) {

		// check for possible chase
			for (int d : dir) {
				int from = lastMoveTo + d;
				if (from != Move.unpackFrom(bestMove))
					continue;

		// chase confirmed:
		// bestMove is to move chased piece 

		// If Two Squares is in effect,
		// deep search only if the best move is not adjacent
		// to the chase piece from-square.
		// Otherwise, the chased piece is in no danger,
		// because the chase piece can move back and forth
		// until Two Squares prevents the chaser from moving.

				if (Settings.twoSquares
					&& Grid.isAdjacent(Move.unpackTo(bestMove), lastMove.getFrom()))
					continue;

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

		Move killerMove = new Move(null, 0);
		int vm = negamax(n, -9999, 9999, 0, chasePiece, chasedPiece, killerMove); 
		completedDepth = n;

		log("-+-");

		// A null move may be returned on iterations other
		// than the first iteration because of forward pruning.
		// A null move is inserted in the tree when a move
		// (usually several moves) are pruned off.
		// If a null move is the best move, it means that
		// a pruned move could be the best move.
		//
		// The dilemma is which move is really the best move?
		// Moves are pruned off when they cannot create a
		// result that can affect material balance within
		// the search depth. (They are just too far from
		// the enemy pieces.)
		// Thus, a null move result indicates that the AI wants to
		// keep its active pieces in their current positions.
		//
		// The best move must therefore be selected from
		// the moves that were pruned off.  While this move may
		// not be the best move (nor perhaps even a good move)
		// it is the only way these pieces ever get activated,
		// unless an opponent piece happens to come visiting,
		// causing the moves to be considered.
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

		if (killerMove.getMove() == 0) {
			assert n != 1 : "null move on iteration 1";
			log("Best move is null");
			ArrayList<ArrayList<Integer>> saveRootMoveList = rootMoveList;
			rootMoveList = getMoves(-n+1, Settings.topColor, null, null);
			vm = negamax(1, -9999, 9999, 0, chasePiece, chasedPiece, killerMove); 
			rootMoveList = saveRootMoveList;
		}

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

		if (n == 1 || chasePiece != null) {

		// no horizon effect possible until ply 2

			bestMove = bestMovePly;
			bestMoveValue = vm;

		} else {

		// The AI accepts the best move of a deeper search only
		// if the value is better (or just slightly worse) than
		// the value of the best move of a shallow search 
		// OR
		// if the deeper search is 2 plies deeper than
		// the ply of the currently selected best move.
		
			if (bestMove == bestMovePly
				|| vm > bestMoveValue - 15
				|| discardPly) {
				bestMove = bestMovePly;
				bestMoveValue = vm;
				discardPly = false;
			} else {
				discardPly = true;
				log(1, "\nPV:" + n + " " + vm + ": best move discarded.");
				n += 1;
				continue;
			}
		}

		hh[bestMove]+=n;
		log("-+++-");

		log(1, "\nPV:" + n + " " + vm);
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
			if (!Grid.isValid(j))
				continue;
			Piece tp = b.getPiece(j);
			if (tp == null)
				return true;
			if (tp.getColor() == fp.getColor())
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
	// This prevents a single level horizon effect
	// where the ai places another
	// (lesser) piece subject to attack when the loss of an existing piece
	// is inevitable.
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
	
	private int qs(int depth, int n, boolean flee)
	{
		if (n < 1)
			return b.getValue();

		boolean bestFlee = false;
		int nextBest = -9999;	// default value not significant

		// try fleeing
		b.pushNullMove();
		int best = qs(depth+1, n-1, true);
		b.undo();

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
				if (!Grid.isValid(t))
					continue;

				Piece tp = b.getPiece(t); // defender
				if (tp == null || b.bturn == tp.getColor())
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

				if (b.bturn == Settings.topColor) {
					if (vm > best) {
						nextBest = best;
						best = vm;
						bestFlee = canFlee;
					} else if (vm > nextBest)
						nextBest = vm;
				} else {
					if (vm < best) {
						nextBest = best;
						best = vm;
						bestFlee = canFlee;
					} else if (vm < nextBest)
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
		long hashOrig = getHash(n);
		int index = (int)(hashOrig % ttable.length);
		TTEntry entry = ttable[index];
		TTEntry ttBestMove = null;

		if (entry != null
			&& entry.hash == hashOrig
			&& entry.turn == b.bturn
			&& entry.depth == n) {

		// Note that the same position at different depths
		// or from prior moves does not have the same score,
		// because the AI assigns less value to attacks
		// at greater depths.  However, the best move 
		// is still useful and often will generate the best score.

			if (moveRoot == entry.moveRoot
				&& (chasePiece != null || entry.type == TTEntry.SearchType.BROAD )) {
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
			ttBestMove = entry;

		} else if (n > 0) {
			long ttMoveHash = getHash(n-1);
			TTEntry ttMoveEntry = ttable[(int)(ttMoveHash % ttable.length)];
			if (ttMoveEntry != null
				&& ttMoveEntry.hash == ttMoveHash
				&& ttMoveEntry.turn == b.bturn)
				ttBestMove = ttMoveEntry;
		}


		if (n < 1
			|| (depth != 0
				&& b.getLastMove() != null
				&& b.getLastMove().tp != null
				&& b.getLastMove().tp.getRank() == Rank.FLAG)) {
			return negQS(qscache(depth));
		}


		// If the chaser is N or more squares away, then
		// the chase is considered over.
		// What often happens in a extended random chase,
		// is that the chased piece approaches one of its other
		// pieces, allowing the chaser to fork the two pieces.
		// N = 3 means chaser has 1 move to become adjacent
		// to the chased piece, so the forked piece must be
		// adjacent to the the chaser. 
		// N = 4 gives the chaser a broader area to attack
		// after a chase.  For example,
		// -- -- --
		// B2 -- R5
		// -- R7 R4
		// R3
		// Red Three has been fleeing Blue Two.  If Blue Two
		// moves right, Red Five will move and
		// the chase will be over (N=3), but
		// QS will award Blue with Red Seven.
		// But if N=4, Blue has one more move, so after Red Five
		// flees, Blue Two moves right again, forking Red Five
		// and Red Four.

		if (chasePiece != null && b.bturn == Settings.bottomColor) {
			if (Grid.steps(chasePiece.getIndex(), chasedPiece.getIndex()) >= 4)
				return negQS(qscache(depth));
		}

		int vm = negamax2(n, alpha, beta, depth, chasePiece, chasedPiece, killerMove, ttBestMove);

		assert hashOrig == getHash(n) : "hash changed";

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
		} else if (entry.depth > n && moveRoot == entry.moveRoot)
			return vm;

		// reuse existing entry and avoid garbage collection
		if (vm <= alphaOrig)
			entry.flags = TTEntry.Flags.UPPERBOUND;
		else if (vm >= beta)
			entry.flags = TTEntry.Flags.LOWERBOUND;
		else
			entry.flags = TTEntry.Flags.EXACT;

		if (chasePiece != null)
			entry.type = TTEntry.SearchType.DEEP;
		else
			entry.type = TTEntry.SearchType.BROAD;
		entry.moveRoot = moveRoot;
		entry.hash = hashOrig;
		entry.bestValue = vm;
		entry.bestMove = killerMove.getMove();
		entry.depth = n;
		entry.turn = b.bturn;	//debug

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
		if (move == 0)
			return true;

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

private int negamax2(int n, int alpha, int beta, int depth, Piece chasePiece, Piece chasedPiece, Move killerMove, TTEntry ttBestMove) throws InterruptedException
	{
		int bestValue = -9999;
		Move kmove = new Move(null, 0);
		int bestmove = 0;

		if (ttBestMove != null
			&& ttBestMove.bestMove != killerMove.getMove()) {

		// use best move from transposition table for move ordering
		// best move entries in the table are not tried
		// if a duplicate of the killer move

			int ttMove = ttBestMove.bestMove;
			if (!isValidMove(ttMove))
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
		int kfrom = killerMove.getFrom();
		int kto = killerMove.getTo();
		Piece fp = b.getPiece(kfrom);
		Piece tp = b.getPiece(kto);
		if (fp != null
			&& fp.getColor() == b.bturn
			&& Grid.isAdjacent(kfrom, kto)
			&& (tp == null || tp.getColor() != b.bturn)) {
			MoveType mt = makeMove(n, depth, killerMove.getMove());
			if (mt == MoveType.OK
				|| mt == MoveType.CHASER
				|| mt == MoveType.CHASED) {
				int vm = -negamax(n-1, -beta, -alpha, depth + 1, chasedPiece, chasePiece, kmove);
				long h = b.getHash();
				b.undo();
				logMove(n, killerMove.getMove(), b.getValue(), negQS(vm), h, MoveType.KM);
				
				if (vm > bestValue) {
					bestValue = vm;
					bestmove = killerMove.getMove();
				}

				alpha = Math.max(alpha, vm);

				if (alpha >= beta) {
					hh[killerMove.getMove()]+=n;
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
		if (depth == 0
			&& (n == 1 || chasePiece != null))
			moveList = rootMoveList;
		else
			moveList = getMoves(n, b.bturn, chasePiece, chasedPiece);

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

				if (max != 0
					&& ((killerMove != null
						&& max == killerMove.getMove())
						|| (ttBestMove != null
						&& max == ttBestMove.bestMove)))
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
		return "(null move)";

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
		} else {
			log(PV, "   " + logMove(b, n, entry.bestMove, MoveType.OK));
			b.move(entry.bestMove, depth);
		}
		logPV(--n, ++depth);
		b.undo();
	}
}

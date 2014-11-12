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
import com.cjmalloy.stratego.BMove;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.UndoMove;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;



public class AI implements Runnable
{
	public static ReentrantLock aiLock = new ReentrantLock();
	private Board board = null;
	private CompControls engine = null;
	private PrintWriter log;
	private int ninesAtLarge;	// if the opponent still has Nines

	private static int[] dir = { -11, -1,  1, 11 };
	private int[][] hh = new int[121][121];	// move history heuristic
	private final int QSMAX = 3;	// maximum qs search depth
	MoveValuePair bestMove = null;
	long stopTime = 0;

	public class MoveValuePair implements Comparable<MoveValuePair> {
		BMove move = null;
		protected int value;
		boolean unknownScoutFarMove = false;
		MoveValuePair(BMove m, boolean s) {
			move = m;
			unknownScoutFarMove = s;
		}
		public int getValue() {
			return value;
		}
		public void setValue(int v) {
			value = v;
		}
		public int compareTo(MoveValuePair mvp) {
			return mvp.value - value;
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
		if (Settings.debug)
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
		aiLock.lock();
                try
                {
		// Settings tick marks:
		// 1: .1 sec
		// 2: .4 sec
		// 3: .9 sec
		// 4: 1.6 sec
		// 5: 2.5 sec
		// etc, etc
			log("Settings.aiLevel:" + Settings.aiLevel);
			log("Settings.twoSquares:" + Settings.twoSquares);
			long startTime = System.currentTimeMillis();
			stopTime = startTime
				+ Settings.aiLevel * Settings.aiLevel * 100;

			TestingBoard b = new TestingBoard(board);
			long t = System.currentTimeMillis() - startTime;
			long trem = stopTime - System.currentTimeMillis();
			log("Call getBestMove() at " + t + "ms: time remaining:" + trem + "ms");
			getBestMove(b);
		} catch (InterruptedException e) {
                } catch (Exception e) {
			e.printStackTrace();
		}
		finally
		{
			System.runFinalization();
			System.gc(); 

		// note: no assertions here, because they overwrite
		// earlier assertions

			if (bestMove == null)
				engine.aiReturnMove(null);
			else if (bestMove.move == null)
				log("Null move");
			else if (board.getPiece(bestMove.move.getFrom()) == null)
 				log("bestMove from " + bestMove.move.getFrom() + " to " + bestMove.move.getTo() + " but from piece is null?");
			else {
				logMove(0, board, bestMove.move, 0, bestMove.value, "");
				log.flush();
				// return the actual board move
				engine.aiReturnMove(new Move(board.getPiece(bestMove.move.getFrom()), bestMove.move));
			}

			logFlush("----");

			aiLock.unlock();
		}
	}

	private BMove getMove(TestingBoard b, Piece fp, int f, int t)
	{
		if (!b.isValid(t))
			return null;
		Piece tp = b.getPiece(t);
		if (tp != null && tp.getColor() == fp.getColor())
			return null;
		
		BMove tmpM = new BMove(f, t);

		return tmpM;
	}


	public boolean getMoves(ArrayList<MoveValuePair> moveList, TestingBoard b, Piece fp)
	{
		boolean hasMove = false;
		int i = fp.getIndex();

		// if the piece is no longer on the board, ignore it
		if (b.getPiece(i) != fp)
			return false;

		Rank fprank = fp.getRank();
		int fpcolor = fp.getColor();

		// Known bombs are removed from pieces[] but
		// a bomb could become known in the search
		// tree.  We need to generate moves for suspected
		// bombs because the bomb might actually be
		// some other piece, but once the bomb becomes
		// known, it ceases to move.
		if (fprank == Rank.BOMB && fp.isKnown())
			return false;

		for (int d : dir ) {
			int t = i + d ;

		// NOTE: FORWARD TREE PRUNING
		// We don't actually know if an unknown unmoved piece
		// can or will move, but usually we don't care
		// unless it can attack an AI piece during the search.
		//
		// TBD: determine in advance which opponent pieces
		// are able to attack an AI piece within the search window.
		// For now, we just discard all unmoved unknown piece moves
		// to an open square.
			if (b.getPiece(t) == null) {
				if ((fprank == Rank.UNKNOWN || fprank == Rank.BOMB || fprank == Rank.FLAG)
					&& !fp.hasMoved()) {
		// ai bombs or flags cannot move
					if (fpcolor == Settings.bottomColor)
						hasMove = true;
				} else {

		// move to open square
					BMove tmpM = new BMove(i, t);
					moveList.add(new MoveValuePair(tmpM, false));
				}


		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks on unknown
		// valuable pieces.
		// if there are no nines left, then skip this code
				if (ninesAtLarge > 0 && fprank == Rank.UNKNOWN) {
					Piece p;
					do {
						t += d;
						p = b.getPiece(t);
					} while (p == null);
					if (!p.isKnown()
						&& p.getColor() == 1 - fpcolor
						&& b.isValuable(p)) {
						BMove tmpM = getMove(b, fp, i, t);
						if (tmpM != null)
							moveList.add(new MoveValuePair(tmpM, !fp.isKnown()));
					} // attack

		// NOTE: FORWARD PRUNING
		// generate scout far moves only for attacks and far rank

				} else if (fprank == Rank.NINE) {
					t += d;
					Piece p = b.getPiece(t);

		// if next-to-adjacent square is invalid or contains
		// the same color piece, a far move is not possible

					if (p != null
						&& p.getColor() != 1 - fpcolor)
						continue;
					while (p == null) {
						t += d;
						p = b.getPiece(t);
					};
					if (p.getColor() != 1 - fpcolor)
						t -= d;
					BMove tmpM = getMove(b, fp, i, t);
					if (tmpM != null)
						moveList.add(new MoveValuePair(tmpM, !fp.isKnown()));
				} // nine
			} else {

		// attack
				BMove tmpM = getMove(b, fp, i, t);
				if (tmpM != null)
					moveList.add(new MoveValuePair(tmpM, false));
			}
		} // d

		return hasMove;
	}


	// Quiescent search with only attack moves limits the
	// horizon effect but does not eliminate it, because
	// the ai is still prone to make bad moves 
	// that waste more material in a position where
	// the opponent has a sure favorable attack in the future,
	// and the ai depth does not reach the position of exchange.
	private ArrayList<MoveValuePair> getMoves(TestingBoard b, int turn, Piece chasePiece, Piece chasedPiece)
	{
		ArrayList<MoveValuePair> moveList = new ArrayList<MoveValuePair>();
		// FORWARD PRUNING
		// chase deep search
		// only examine moves adjacent to chase and chased pieces
		// as they chase around the board
		if (chasePiece != null) {
			getMoves(moveList, b, chasedPiece);
			for (int d : dir ) {
				int i = chasePiece.getIndex() + d;
				if (i != chasedPiece.getIndex() && b.isValid(i)) {
					Piece p = b.getPiece(i);
					if (p != null && p.getColor() == turn)
						getMoves(moveList, b, p );
				}
			}

			// AI can end the chase by moving some other piece,
			// allowing its chased piece to be attacked.  If it
			// has found protection, this could be a good
			// way to end the chase.
			// Add null move
			moveList.add(new MoveValuePair(null, false));

		} else {
		boolean hasMove = false;
		for (Piece np : b.pieces[turn]) {
			if (np == null)	// end of list
				break;
			if (getMoves(moveList, b, np))
				hasMove = true;
		}

		// FORWARD PRUNING
		// Add null move
		if (hasMove)
			moveList.add(new MoveValuePair(null, false));
		}

		return moveList;
	}

	private void getBestMove(TestingBoard b) throws InterruptedException
	{
		BMove tmpM = null;
		bestMove = null;
		int bestMoveValue = 0;

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
		for (int j=12; j<=120; j++)
		for (int k=12; k<=120; k++)
			hh[j][k] = 0;

		ninesAtLarge = Rank.getRanks(Rank.NINE)
			- b.rankInTray(Settings.bottomColor, Rank.NINE);

		for (Piece p : b.pieces[Settings.bottomColor])
			if (p == null)	// end of list
				break;
			else if (p.getRank() == Rank.NINE)
				ninesAtLarge--;
			
		ArrayList<MoveValuePair> moveList = getMoves(b, Settings.topColor, null, null);
		// To speed move generation, the search tree does not check the
		// Two Squares rule for each move, so we remove
		// the move now.
		for (int k = moveList.size()-1; k >= 0; k--) {

			MoveValuePair mvp = moveList.get(k);
			tmpM = mvp.move;

		// Two Squares Rule
			if (b.isTwoSquares(tmpM)) {
				moveList.remove(k);
				continue;
			}


		// Immobile Pieces
		// Bombs and the Flag are not legal moves.  However,
		// the AI generates moves for unknown bombs because
		// the apparent rank is unknown to the opponent, so
		// these pieces can protect another piece as a bluff.
			Piece p = b.getPiece(tmpM.getFrom());
			if (p.getRank() == Rank.BOMB
				|| p.getRank() == Rank.FLAG) {
				moveList.remove(k);
				continue;
			}

		// Restrict moves to those that do not result
		// in repetitive positions unless the piece is
		// being chased.  This is more restrictive
		// than the More Squares Rule, but better for
		// the game and easy to calculate (for computers).

			if (!b.isChased(tmpM) && !b.isTwoSquaresChase(tmpM)) {
				b.move(tmpM, 0, mvp.unknownScoutFarMove);

				if (b.isRepeatedPosition())
					moveList.remove(k);

				b.undo();
			}

			assert b.getValue() == 0 : "Board value not zero?";
		}

		boolean discardPly = false;	// horizon effect

		for (int n = 1; n < 20; n++) {

		int alpha = -9999;

		// FORWARD PRUNING:
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
			&& bestMove != null

		// Deep chase is skipped if best move from broad search
		// is to attack a piece (perhaps the chaser)
			&& b.getPiece(bestMove.move.getTo()) == null

		// Deep chase is skipped if best move value is negative.
		// This indicates that the piece is trapped
		// already or there is something else on the board
		// going on.  So broad search is preferred.
			&& bestMove.getValue() > -30

		// Limit deep chase to superior pieces.
		// Using deep chase can be risky if the
		// objective of the chaser is not be the chased
		// piece, but some other piece, like a flag or
		// flag bomb.
			&& b.getPiece(bestMove.move.getFrom()).getRank().toInt() <= 4 ) {

		// check for possible chase
			for (int d : dir) {
				int from = lastMoveTo + d;
				if (from != bestMove.move.getFrom())
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
					&& Grid.isAdjacent(bestMove.move.getTo(), lastMove.getFrom()))
					continue;

				int count = 0;
				for (int k = moveList.size()-1; k >= 0; k--)
				if (from == moveList.get(k).move.getFrom()) {
					int to = moveList.get(k).move.getTo();
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
					for (int k = moveList.size()-1; k >= 0; k--)
						if (from != moveList.get(k).move.getFrom()
							|| b.getPiece(moveList.get(k).move.getTo()) != null)

							moveList.remove(k);

					break;
				}
			}
		}

		if (moveList.size() == 0) {
			log("Empty move list");
			return;		// ai trapped
		}

		for (MoveValuePair mvp : moveList) {
			tmpM = mvp.move;

			b.move(tmpM, 0, mvp.unknownScoutFarMove);

			int vm = valueNMoves(b, n-1, alpha, 9999, Settings.bottomColor, 1, chasedPiece, chasePiece, new BMove(0,0)); 

			mvp.setValue(vm);

			b.undo();
			assert b.getValue() == 0 : "Board value not zero?";
			int v = 0;
			Piece tp = b.getPiece(mvp.move.getTo());
			if (tp != null)
				v = b.actualValue(tp);
			logMove(n, b, tmpM, v, vm, "");

			if (vm > alpha)
			{
				alpha = vm;
			}
		}

		log("-+-");
		Collections.sort(moveList);

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

		MoveValuePair bestMovePly = moveList.get(0);

		if (n == 1 || chasePiece != null) {

		// no horizon effect possible until ply 2

			bestMove = bestMovePly;
			bestMoveValue = bestMove.getValue();

		} else {

		// The AI accepts the best move of a deeper search only
		// if the value is better (or just slightly worse) than
		// the value of the best move of a shallow search 
		// OR
		// if the deeper search is 2 plies deeper than
		// the ply of the currently selected best move.
		
			if (bestMove == bestMovePly
				|| bestMovePly.getValue() > bestMoveValue - 15
				|| discardPly) {
				bestMove = bestMovePly;
				bestMoveValue = bestMove.getValue();
				discardPly = false;
			} else {
				discardPly = true;
				n += 1;
				log("ply " + n + ": best move discarded.");
				continue;
			}
		}

		hh[bestMove.move.getFrom()][bestMove.move.getTo()]+=n;
		log("-+++-");

		} // iterative deepening
	}


	// return true if a piece is safely movable.
	// Safely movable means it has an open space
	// or can attack a known piece of lesser rank.
	private boolean isMovable(TestingBoard b, int i)
	{
		Piece fp = b.getPiece(i);
		Rank rank = fp.getRank();
		if (rank == Rank.FLAG || rank == Rank.BOMB)
			return false;
		for (int d: dir) {
			int j = i + d;
			if (!b.isValid(j))
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
	
	private int qs(TestingBoard b, int turn, int depth, int n, boolean flee)
	{
		int valueB = b.getValue();
		if (n < 1)
			return valueB;

		int best = valueB;

		boolean bestFlee = false;
		int nextBest = best;

		if (!flee) {
		// try fleeing
			int vm = qs(b, 1-turn, depth+1, n-1, true);

		// "best" is board value after
		// opponent's best attack if player can flee.
		// So usually this is valueB, unless the
		// opponent has two good attacks or the player
		// piece under attack is cornered.
			best = vm;
		}

		for (Piece fp : b.pieces[turn]) {
			if (fp == null)	// end of list
				break;
			int i = fp.getIndex();
			if (fp != b.getPiece(i))
				continue;

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
		// check the direction of the attack.  If nines
		// are handled, then it would be impossible to
		// to reuse the result in subsequent moves
		// (tbd: use qs from prior move (or null move)
		// if new move is not adjacent to any pieces involved
		// in attack)
		//
		// TBD: Another reason to handle far attacks is
		// the deep chase code.  It relies on qs, so
		// a known AI piece can be chased out of the
		// way to permit an attack on an unknown AI piece
		// without the AI realizing it.

			for (int d : dir ) {
				boolean canFlee = false;
				int t = i + d;	
				if (!b.isValid(t))
					continue;
				Piece tp = b.getPiece(t); // defender
				if (tp == null || turn == tp.getColor())
					continue;

				if (flee && isMovable(b, t))
					canFlee = true;

				BMove tmpM = new BMove(i, t);
				b.move(tmpM, depth, false);

				int vm = qs(b, 1-turn, depth+1, n-1, false);

				b.undo();

		// Save worthwhile attack (vm > best)
		// (if vm < best, the player will play
		// some other move)

				if (turn == Settings.topColor) {
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

		if (bestFlee)
			best = nextBest;

		return best;
	}

	private int valueNMoves(TestingBoard b, int n, int alpha, int beta, int turn, int depth, Piece chasePiece, Piece chasedPiece, BMove killerMove) throws InterruptedException
	{
		BMove bestmove = null;
		int valueB = b.getValue();
		int v;
		UndoMove lastmove = b.getLastMove();
		if (n < 1 || (lastmove != null && lastmove.tp != null && lastmove.tp.getRank() == Rank.FLAG))
			return qs(b, turn, depth, QSMAX, false);

		if (bestMove != null
			&& stopTime != 0
			&& System.currentTimeMillis( ) > stopTime)
			throw new InterruptedException();


		if (turn == Settings.topColor)
			v = alpha;
		else
			v = beta;

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

		if (chasePiece != null && turn == Settings.bottomColor) {
			if (Grid.steps(chasePiece.getIndex(), chasedPiece.getIndex()) >= 4)
				return qs(b, turn, depth, QSMAX, false);
		}

		// Try the killer move before move generation
		// to save time if the killer move causes ab pruning.
		// TBD: killer move can be multi-hop, but then
		// checking for a legal move requires checking all squares
		int kfrom = killerMove.getFrom();
		int kto = killerMove.getTo();
		Piece fp = b.getPiece(kfrom);
		Piece tp = b.getPiece(kto);
		int d = kto - kfrom;
		if (fp != null
			&& fp.getColor() == turn
			&& (d == 1 || d == -1 || d == -11 || d == 11)
			&& (tp == null || tp.getColor() != turn)) {
			BMove tmpM = new BMove(kfrom, kto);
			b.move(tmpM, depth, false);

			int vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1, chasedPiece, chasePiece, killerMove);

			b.undo();

			logMove(n, b, tmpM, valueB, vm, "");

			if (turn == Settings.topColor) {
				if (vm > alpha) {
					v = alpha = vm;
					bestmove = tmpM;
				}
			} else {
				if (vm < beta) {
					v = beta = vm;
					bestmove = tmpM;
				}
			}

			if (beta <= alpha) {
				hh[kfrom][kto]+=n;
				return vm;
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

		ArrayList<MoveValuePair> moveList = getMoves(b, turn, chasePiece, chasedPiece);
		for (MoveValuePair mvp : moveList) {
			if (mvp.move != null)
				mvp.setValue(hh[mvp.move.getFrom()][mvp.move.getTo()]);
		}

		for (int i = 0; i < moveList.size(); i++) {
			MoveValuePair max;
			{
				MoveValuePair mvp = moveList.get(i);

			// skip killer move
				if (mvp.move != null
					&& mvp.move.getFrom() == kfrom
					&& mvp.move.getTo() == kto)
					continue;

				max = mvp;
				int tj = i;
				for (int j = i + 1; j < moveList.size(); j++) {
					MoveValuePair tmvp = moveList.get(j);
					if (tmvp.getValue() > max.getValue()) {
						max = tmvp;
						tj = j;
					}
				}
				moveList.set(tj, mvp);
			}

			int vm = 0;
			if (max.move == null) {
			b.pushNullMove();	// because of isRepeatedPosition()
			vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1, chasedPiece, chasePiece, killerMove);
			b.popMove();
			log(n + ": (null move) " + valueB + " " + vm);
			} else {

		// NOTE: FORWARD TREE PRUNING (minor)
		// isRepeatedPosition() discards repetitive moves.
		// This is done now rather than during move
		// generation because most moves
		// are pruned off by alpha-beta,
		// so calls to isRepeatedPosition() are also pruned off,
		// saving a heap of time.

			if (b.isTwoSquares(max.move)) {
				logMove(n, b, max.move, valueB, 0, "two squares");
				continue;
			}

			String note = "";

		// AI always abides by Two Squares rule
		// even if box is not checked (AI plays nice).

			if (Settings.twoSquares
				|| turn == Settings.topColor) {

				if (b.isChased(max.move)) {

		// Piece is being chased, so repetitive moves OK
		// but can it lead to a two squares result?

					if (b.isPossibleTwoSquares(max.move)) {
						logMove(n, b, max.move, valueB, 0, "poss two squares");
						continue;
					}

					b.move(max.move, depth, max.unknownScoutFarMove);
					note = "chased";
				} else if (b.isTwoSquaresChase(max.move)) {

		// Piece is chasing, so repetitive moves OK
		// (until Two Squares Rule kicks in)

					b.move(max.move, depth, max.unknownScoutFarMove);
					note = "chaser";
				} else if (turn == Settings.topColor) {

		// Because isRepeatedPosition() is more restrictive
		// than More Squares, the AI does not expect
		// the opponent to abide by this rule as coded.

					b.move(max.move, depth, max.unknownScoutFarMove);
					if (b.isRepeatedPosition()) {
						b.undo();
						logMove(n, b, max.move, valueB, 0, "repeated");
						continue;
					}
				}
				else
					b.move(max.move, depth, max.unknownScoutFarMove);
			} else
				b.move(max.move, depth, max.unknownScoutFarMove);
			vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1, chasedPiece, chasePiece, killerMove);

			b.undo();

			logMove(n, b, max.move, valueB, vm, note);
			}

			if (turn == Settings.topColor) {
				if (vm > alpha) {
					v = alpha = vm;
					bestmove = max.move;
				}
			} else {
				if (vm < beta) {
					v = beta = vm;
					bestmove = max.move;
				}
			}

			if (beta <= alpha)
				break;
		} // moveList

		if (bestmove != null) {
			hh[bestmove.getFrom()][bestmove.getTo()]+=n;
			killerMove.set(bestmove);
		}
		return v;
	}

	String logPiece(Piece p)
	{
		Rank rank = p.getRank();
		if (!p.isKnown()
			&& (p.getActingRankFlee() != Rank.NIL
				|| p.getActingRankChase() != Rank.NIL))
			return p.getRank() + "["
				+ p.getActingRankChase()
				+ "," + p.getActingRankFlee() + "]";

		return "" + p.getRank();
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


	void logMove(int n, Board b, BMove move, int valueB, int value, String note)
	{
	int color = b.getPiece(move.getFrom()).getColor();
	if (b.getPiece(move.getTo()) == null) {
	log(n + ":" + color
+ " " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " (" + logPiece(b.getPiece(move.getFrom())) + ")"
	+ logFlags(b.getPiece(move.getFrom())) + " "
	+ valueB + " " + value + " " + note);
	} else {
		char X = 'x';
		if (n == 0)
			X = 'X';
	log(n + ":" + color
+ " " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " (" + logPiece(b.getPiece(move.getFrom())) + X + logPiece(b.getPiece(move.getTo())) + ")"
	+ logFlags(b.getPiece(move.getFrom())) + " " + logFlags(b.getPiece(move.getTo()))
	+ " " + valueB + " " + value + " " + note);
	}
	}

	public void logMove(Move m)
	{
		logMove(0, board, m, 0, 0, "");
	}

	private void log(String s)
	{
		if (Settings.debug)
			log.println(s);
	}

	public void logFlush(String s)
	{
		if (Settings.debug) {
			log.println(s);
			log.flush();
		}
	}
}

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

	private int mmax;
	private static int[] dir = { -11, -1,  1, 11 };
	private int[][] hh = new int[121][121];	// move history heuristic

	public class MoveValuePair implements Comparable<MoveValuePair> {
		BMove move = null;
		Integer value = 0;
		boolean unknownScoutFarMove = false;
		MoveValuePair(BMove m, Integer v, boolean s) {
			move = m;
			value = v;
			unknownScoutFarMove = s;
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
	
		engine.play();
	}

	public void run() 
	{
		BMove bestMove = null;
		aiLock.lock();
                try
                {
	
			bestMove = getBestMove(new TestingBoard(board));
			System.runFinalization();
			System.gc(); 

			// return the actual board move
			if (bestMove == null)
				engine.aiReturnMove(null);
			else
				engine.aiReturnMove(new Move(board.getPiece(bestMove.getFrom()), bestMove));
		}
		finally
		{
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


	public void getMoves(ArrayList<MoveValuePair> moveList, TestingBoard b, int turn, int i)
	{
		Piece fp = b.getPiece(i);
		if (fp == null)
			return;
		if (fp.getColor() != turn)
			return;

		Rank fprank = fp.getRank();

		// note: we need to make this check each time
		// because the index and perhaps the rank
		// could change.
		if (fprank == Rank.BOMB
			|| fprank == Rank.FLAG)
			return;

		for (int d : dir ) {
			int t = i + d ;

			BMove tmpM = getMove(b, fp, i, t);
			if (tmpM != null) {
				moveList.add(new MoveValuePair(tmpM, 0, false));
				// NOTE: FORWARD PRUNING
				// generate scout far moves
				// only for captures and far rank
				if (fprank == Rank.NINE) {
					while (b.getPiece(t) == null)
						t += d;
					if (t != i + d) {
						if (!b.isValid(t))
							t -= d;
						tmpM = getMove(b, fp, i, t);
						if (tmpM != null)
							moveList.add(new MoveValuePair(tmpM, 0, !fp.isKnown()));
					}
				} // scout far moves
			} // valid move
		} // d
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
			getMoves(moveList, b, turn, chasedPiece.getIndex());
			for (int d : dir ) {
				int i = chasePiece.getIndex() + d;
				if (i != chasedPiece.getIndex())
					getMoves(moveList, b, turn, i );
			}
		} else
		for (int j=0;j<92;j++)
		{
			// order the move evaluation to consider
			// the pieces furthest down the board first because
			// already moved pieces have the highest scores.
			// This results in the most alpha-beta pruning.
			int i;
			if (turn == Settings.topColor)
				i = 132 - Grid.getValidIndex(j);
			else
				i = Grid.getValidIndex(j);

			getMoves(moveList, b, turn, i);
		}

		return moveList;
	}

	boolean hasMove(TestingBoard b, int turn)
	{
		for (int j=0;j<92;j++) {
			int i = Grid.getValidIndex(j);
			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			if (fp.getColor() != turn)
				continue;
			for (int d : dir ) {
				int t = i + d ;
				Piece tp = b.getPiece(t);
				if (tp == null)
					continue;
				if (fp.getColor() == tp.getColor())
					continue;
				return true;
			}
		}
		return false;
	}


	private BMove getBestMove(TestingBoard b)
	{
		BMove tmpM = null;
		MoveValuePair bestmove = null;
		final int CHASE_RANK_NIL = 99;

		// chase variables
		Piece chasedPiece = null;
		Piece chasePiece = null;
		Piece lastMovedPiece = null;
		int lastMoveTo = 0;
		Move lastMove = b.getLastMove(1);
		if (lastMove != null) {
			lastMoveTo = lastMove.getTo();
			lastMovedPiece = b.getPiece(lastMoveTo);
		}

		// move history heuristic (hh)
		for (int j=12; j<=120; j++)
		for (int k=12; k<=120; k++)
			hh[j][k] = 0;


		ArrayList<MoveValuePair> moveList = getMoves(b, Settings.topColor, null, null);

		// To speed move generation, the search tree does not check the
		// Two Squares rule for each move, so we remove
		// the move now.
		for (int k = moveList.size()-1; k >= 0; k--) {
			if (b.isTwoSquares(moveList.get(k).move))
				moveList.remove(k);
			else {
				MoveValuePair mvp = moveList.get(k);
				tmpM = mvp.move;
				if (b.isRepeatedMove(tmpM))
					continue;
				b.move(tmpM, 0, mvp.unknownScoutFarMove);
				// More Squares Rule
				if (b.isMoreSquares())
				// To prevent looping, the AI always
				// avoids repeated positions.
				// This is stricter than the More Squares Rule
				// if (b.dejavu())
					moveList.remove(k);
				b.undo(0);
			}
		}

		long t = System.currentTimeMillis( );
		boolean timeout = false;
 		long dur = Settings.aiLevel * Settings.aiLevel * 100;

		for (int n = 1; !timeout && n < 20; n++) {

		int alpha = -9999;
		int beta = 9999;

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
			&& lastMovedPiece.equals(lastMove.getPiece())
			&& System.currentTimeMillis( ) > t + dur/100 
			&& bestmove != null
			&& bestmove.move.getTo() != lastMoveTo) {
			// check for possible chase

			for (int d : dir) {
				int from = lastMoveTo + d;
				if (from != bestmove.move.getFrom())
					continue;

				// chase confirmed:
				// bestmove is to move chased piece 
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

		if (moveList.size() == 0)
			return null;	// ai trapped

		for (MoveValuePair mvp : moveList) {
			if (bestmove != null
				&& System.currentTimeMillis( ) > t + dur) {
				timeout = true;
				break;
			}

			tmpM = mvp.move;

			int valueB = b.getValue();
			Piece fp = b.getPiece(mvp.move.getFrom());

			Piece tp = b.getPiece(mvp.move.getTo());
			b.move(tmpM, 0, mvp.unknownScoutFarMove);


			int vm = valueNMoves(b, n-1, alpha, beta, Settings.bottomColor, 1, chasedPiece, chasePiece); 
			// int vm = valueNMoves(b, n-1, -9999, 9999, Settings.bottomColor, 1); 

			mvp.value = vm;
			b.undo(valueB);
			logMove(n, b, tmpM, valueB, vm);

			if (vm > alpha)
			{
				alpha = vm;
			}

		}

		if (!timeout) {
			log.println("-+-");
			Collections.sort(moveList);
			bestmove = moveList.get(0);
			hh[bestmove.move.getFrom()][bestmove.move.getTo()]+=n;
			log.println("-+++-");
		}

		} // iterative deepening

		logMove(0, b, bestmove.move, 0, bestmove.value);
		log.println("----");
		log.flush();

		return bestmove.move;
	}

	// Evaluation-Based Quiescence Search (qs)
	// Quiescence Search for Stratego
	//	Maarten P.D. Schadd Mark H.M. Winands
	// A faster and more effective method than
	// deepening the tree to evaluate captures for qs.
	// 
	// The reason why ebqs is more effective is that it sums
	// all the pieces under attack.  This prevents a
	// single level horizon effect where the ai places another
	// (lesser) piece subject to attack when the loss of an existing piece
	// is inevitable.
	// 
	// This version gives no credit for the best attack on a *moved*
	// piece on the board because it is likely the opponent will move
	// the defender the very next move.  This prevents the ai
	// from thinking it has won or lost at the end of
	// a chase sequence, because otherwise the ebqs would give value when
	// (usually, unless cornered) the chase sequence can be extended
	// indefinitely without any material loss or gain.
	//
	// Unmoved pieces are likely bombs or flags.
	//
	// bug: is that if two pieces can attack the same piece,
	// the piece is added twice.
	//
	// possible bug: if a piece is cornered, it does not get valued
	// until the opponents turn.  Is this just a 1 ply horizon effect?
	//
	private int ebqs(TestingBoard b, int turn, int depth)
	{
		int qs = b.getValue();
		b.setValue(0);
		int maxbest = 0;
		int minbest = 0;
		for (int j=0;j<92;j++) {
			int i = Grid.getValidIndex(j);
			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			Rank fprank = fp.getRank();
			// note: we need to make this check each time
			// because the index and perhaps the rank
			// could change.
			if (fprank == Rank.BOMB
				|| fprank == Rank.FLAG)
				continue;

			int min = 0;
			int max = 0;
			boolean minmoved = false;
			boolean maxmoved = false;
			for (int d : dir ) {
				int t = i + d;
				if (!b.isValid(t))
					continue;
				Piece tp = b.getPiece(t);
				if (tp == null || tp.getColor() == fp.getColor())
					continue;
				b.move(new BMove(i, t), depth, false);
				int vm = b.getValue();

				if (vm > max) {
					vm = recapture(b, t, depth, true);
					if (vm > max) {
						max = vm;
						maxmoved = tp.hasMoved();
					}
				}
				if (vm < min) {
					vm = recapture(b, t, depth, false);
					if (vm < min) {
						min = vm;
						minmoved = tp.hasMoved();
					}
				}
				b.undo(0);
			}
			if (fp.getColor() == Settings.topColor) {
				qs += max;
				if (max > maxbest && maxmoved)
					maxbest = max;
			} else {
				qs += min;
				if (min < minbest && minmoved)
					minbest = min;
			}
		}

		if (turn == Settings.topColor)
			return qs - minbest;
		else
			return qs - maxbest;
	}

	// ebqs recaptures
	// this gives a better ebqs when a piece can be won
	// but the attacking piece can be recaptured by a lower
	// piece. e.g.:
	// R2 B3
	//    B1
	// Red 2 can take Blue 3, but Blue 1 will take Red 2.
	//
	int recapture(TestingBoard b, int t, int depth, boolean ismax)
	{
		int qs = b.getValue();
		b.setValue(0);
		Piece tp = b.getPiece(t);
		if (tp == null)
			return qs;
		int min = 0;
		int max = 0;
		for (int d : dir ) {
			int f = t + d;
			if (!b.isValid(f))
				continue;
			Piece fp = b.getPiece(f);
			if (fp == null
				|| tp.getColor() == fp.getColor()
				|| fp.getRank() == Rank.BOMB
				|| fp.getRank() == Rank.FLAG)
				continue;
			b.move(new BMove(f, t), depth, false);
			int vm = b.getValue();
			b.undo(0);

			if (vm > max)
				max = vm;
			if (vm < min)
				min = vm;
		}
		if (ismax)
			return qs + min;
		else
			return qs + max;
	}


	private int valueNMoves(TestingBoard b, int n, int alpha, int beta, int turn, int depth, Piece chasePiece, Piece chasedPiece)
	{
		BMove bestMove = null;
		int valueB = b.getValue();
		int v;
		if (n < 1) {
			return ebqs(b, turn, depth);
		}

		if (turn == Settings.topColor)
			v = alpha;
		else
			v = beta;

		if (chasePiece != null && turn == Settings.topColor) {
			boolean chaseOver = true;
			for (int d : dir) {
				if (chasePiece.getIndex() == chasedPiece.getIndex() + d) {
					chaseOver = false;
					break;
				}
			}
			if (chaseOver)
				return ebqs(b, turn, depth);

			// AI can end the chase by moving some other piece,
			// allowing its chased piece to be attacked.  If it
			// has found protection, this could be a good
			// way to end the chase.  So the AI checks the
			// value if it does not move the chased piece.
			int vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1, chasedPiece, chasePiece);
			for (int ii=8; ii >= n; ii--)
				log.print("  ");
			log.println(n + ": (end chase) " + valueB + " " + vm);

			if (vm > alpha) {
				v = alpha = vm;
			}
		}

		ArrayList<MoveValuePair> moveList = getMoves(b, turn, chasePiece, chasedPiece);

		// because of forward pruning, this may be a terminal
		// node but yet there still are moves, so just return
		// the board position
		if (moveList.size() == 0)
			if (hasMove(b, turn))
				return ebqs(b, turn, depth);
			else
				return v;
		
		for (MoveValuePair mvp : moveList) {
			mvp.value = hh[mvp.move.getFrom()][mvp.move.getTo()];
		}
		Collections.sort(moveList);

		for (MoveValuePair mvp : moveList) {
			int vm = 0;
			BMove tmpM = mvp.move;
			// NOTE: FORWARD TREE PRUNING (minor)
			// isRepeatedMove() discards back-and-forth moves.
			// This is done now rather than during move
			// generation because most moves
			// are pruned off by alpha-beta,
			// so calls to isRepeatedMove() are also pruned off,
			// saving a heap of time.
			if (b.isRepeatedMove(tmpM))
				continue;

			Piece fp = b.getPiece(tmpM.getFrom());
			Piece tp = b.getPiece(tmpM.getTo());

			// NOTE: FORWARD TREE PRUNING
			// We don't actually know if an unknown unmoved piece
			// can or will move, but usually we don't care
			// unless they attack on their first or
			// second move.
			// In other words, don't evaluate moves to an
			// open square for an unknown unmoved piece
			// past its initial move.
			if (tp == null && fp.getRank() == Rank.UNKNOWN && !fp.hasMoved() && fp.moves == 1)
				vm = beta;
			else {
				b.move(tmpM, depth, false);

				vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1, chasedPiece, chasePiece);
				// vm = valueNMoves(b, n-1, -9999, 9999, 1 - turn, depth + 1);

				b.undo(valueB);

				for (int ii=8; ii >= n; ii--)
					log.print("  ");
				logMove(n, b, tmpM, valueB, vm);

			} // else

			if (turn == Settings.topColor) {
				if (vm > alpha) {
					v = alpha = vm;
					bestMove = tmpM;
				}
			} else {
				if (vm < beta) {
					v = beta = vm;
					bestMove = tmpM;
				}
			}

			if (beta <= alpha)
				break;
		} // moveList
		if (bestMove != null) {
			hh[bestMove.getFrom()][bestMove.getTo()]+=n;
		}
		return v;
	}

	void logMove(int n, Board b, BMove move, int valueB, int value)
	{
	int color = b.getPiece(move.getFrom()).getColor();
	char hasMoved = ' ';
	if (b.getPiece(move.getFrom()).hasMoved())
		hasMoved = 'M';
	char isKnown = ' ';
	if (b.getPiece(move.getFrom()).isKnown())
		isKnown = 'K';
	if (b.getPiece(move.getTo()) == null) {
	log.println(n + ":" + color
+ " " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " (" + b.getPiece(move.getFrom()).getRank() + ")"
	+ hasMoved + isKnown + " "
	+ valueB + " " + value);
	} else {
		char tohasMoved = ' ';
		if (b.getPiece(move.getTo()).hasMoved())
			tohasMoved = 'M';
		char toisKnown = ' ';
		if (b.getPiece(move.getTo()).isKnown())
			toisKnown = 'K';
		char X = 'x';
		if (n == 0)
			X = 'X';
	log.println(n + ":" + color
+ " " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " (" + b.getPiece(move.getFrom()).getRank() + X + b.getPiece(move.getTo()).getRank() + ")"
	+ hasMoved + isKnown + " " + tohasMoved + toisKnown
	+ " " + valueB + " " + value);
	}
	}

	public void logMove(Move m)
	{
		logMove(0, board, m, 0, 0);
	}

	public void log(String s)
	{
		log.println(s);
		log.flush();
	}
}

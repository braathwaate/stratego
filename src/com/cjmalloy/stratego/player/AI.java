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
	private int foo;
	private static int[] dir = { -11, -1,  1, 11 };
	private int[] movable = new int[92];
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
	
		log = new PrintWriter("ai.out", "UTF-8");
	
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

		if (b.isRecentMove(tmpM))
			return null;

		return tmpM;
	}

	// Quiescent search with only attack moves limits the
	// horizon effect but does not eliminate it, because
	// the ai is still prone to make bad moves 
	// that waste more material in a position where
	// the opponent has a sure favorable attack in the future,
	// and the ai depth does not reach the position of exchange.
	private ArrayList<MoveValuePair> getMoves(TestingBoard b, int turn)
	{
		ArrayList<MoveValuePair> moveList = new ArrayList<MoveValuePair>();
		for (int j=0;j<foo;j++)
		{
			// order the move evaluation to consider
			// the pieces furthest down the board first because
			// already moved pieces have the highest scores.
			// This results in the most alpha-beta pruning.
			int i;
			if (turn == Settings.topColor)
				i = movable[foo - j - 1];
			else
				i = movable[j];

			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			if (fp.getColor() != turn)
				continue;
			for (int d : dir ) {
				int t = i + d ;
				BMove tmpM = getMove(b, fp, i, t);
				if (tmpM != null) {
					moveList.add(new MoveValuePair(tmpM, 0, false));
					// NOTE: FORWARD PRUNING
					// generate scout far moves
					// only for captures and far rank
					if (fp.getRank() == Rank.NINE) {
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

		return moveList;
	}


	private BMove getBestMove(TestingBoard b)
	{
		BMove tmpM = null;

		// generate array of movable indices to speed move generation
		// by removing unmovable pieces (bombs + flag)
		int mmax = 0;
		for (int j=0;j<92;j++)
		{
			int i = Grid.getValidIndex(j);
			Piece fp = b.getPiece(i);
			if (fp != null) {
				Rank rank = fp.getRank();
				if (rank == Rank.BOMB || rank == Rank.FLAG)
					continue;
			}
			movable[mmax++] = i;
		}
		foo = mmax; //java bug

		// move history heuristic (hh)
		for (int j=12; j<=120; j++)
		for (int k=12; k<=120; k++)
			hh[j][k] = 0;
			
		ArrayList<MoveValuePair> moveList = getMoves(b, Settings.topColor);
		
		long t = System.currentTimeMillis( );

		for (int n = 1;
			System.currentTimeMillis( ) < t + Settings.aiLevel * Settings.aiLevel * 100;
			n++) {

		int alpha = -9999;
		int beta = 9999;

		for (MoveValuePair mvp : moveList) {
			tmpM = mvp.move;

			int valueB = b.getValue();
			Piece fp = b.getPiece(mvp.move.getFrom());
			Piece tp = b.getPiece(mvp.move.getTo());
			b.move(tmpM, 0, mvp.unknownScoutFarMove);


			int vm = valueNMoves(b, n-1, alpha, beta, Settings.bottomColor, 1); 
			// int vm = valueNMoves(b, n-1, -9999, 9999, Settings.bottomColor, 1); 
			log.println(n + ": (" + fp.getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY() + " " + vm);

			mvp.value = vm;
			b.undo(valueB);

			if (vm > alpha)
			{
				alpha = vm;
			}

		}
		log.println("-+-");
		Collections.sort(moveList);
		MoveValuePair mvp = moveList.get(0);
		hh[mvp.move.getFrom()][mvp.move.getTo()]+=n;
		log.println("-+++-");


		} // iterative deepening

		if (moveList.size() == 0)
			return null;	// ai trapped

		MoveValuePair mvp = moveList.get(0);
		logMove(b, mvp.move, mvp.value);
		log.println("----");
		log.flush();

		return mvp.move;
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
	// This version gives no credit for the best attack on a moved
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
		for (int j=0;j<foo;j++) {
			int i = movable[j];
			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			int min = 0;
			int max = 0;
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
					if (vm > max)
						max = vm;
					if (max > maxbest && tp.hasMoved())
						maxbest = max;
				}
				if (vm < min) {
					vm = recapture(b, t, depth, false);
					if (vm < min)
						min = vm;
					if (min < minbest && tp.hasMoved())
						minbest = min;
				}
				b.undo(0);
			}
			if (fp.getColor() == Settings.topColor) {
				qs += max;
			} else {
				qs += min;
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


	private int valueNMoves(TestingBoard b, int n, int alpha, int beta, int turn, int depth)
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

		ArrayList<MoveValuePair> moveList = getMoves(b, turn);

		for (MoveValuePair mvp : moveList) {
			mvp.value = hh[mvp.move.getFrom()][mvp.move.getTo()];
		}
		Collections.sort(moveList);

		for (MoveValuePair mvp : moveList) {
			int vm = 0;
			Piece fp = b.getPiece(mvp.move.getFrom());
			Piece tp = b.getPiece(mvp.move.getTo());

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
				b.move(mvp.move, depth, false);

/*
int tpvalue = 0;
if (tp != null)
	tpvalue = tp.aiValue();
for (int ii=5; ii >= n; ii--)
	System.out.print("  ");
System.out.println(n + " (" + fp.getRank() + ") " + mvp.move.getFromX() + " " + mvp.move.getFromY() + " " + mvp.move.getToX() + " " + mvp.move.getToY()
+ " " + fp.aiValue() + " " + tpvalue + " " + valueB + " " + b.getValue());
*/


				vm = valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1);
				// vm = valueNMoves(b, n-1, -9999, 9999, 1 - turn, depth + 1);

				b.undo(valueB);

for (int ii=5; ii >= n; ii--)
	log.print("  ");
if (b.getPiece(mvp.move.getTo()) == null)
log.println(n + " (" + fp.getRank() + ") " + mvp.move.getFromX() + " " + mvp.move.getFromY() + " " + mvp.move.getToX() + " " + mvp.move.getToY()
+ " aiV:" + b.getPiece(mvp.move.getFrom()).aiValue()
+ " " + vm);
else
log.println(n + " (" + fp.getRank() + ") " + mvp.move.getFromX() + " " + mvp.move.getFromY() + " " + mvp.move.getToX() + " " + mvp.move.getToY()
+ " tprank:" + b.getPiece(mvp.move.getTo()).getRank()
+ " aiV:" + b.getPiece(mvp.move.getTo()).aiValue()
+ " " + vm);




			} // else

			if (turn == Settings.topColor) {
				if (vm > alpha) {
					v = alpha = vm;
					bestMove = mvp.move;
				}
			} else {
				if (vm < beta) {
					v = beta = vm;
					bestMove = mvp.move;
				}
			}

			if (beta <= alpha)
				break;
		} // moveList
		if (bestMove != null)
			hh[bestMove.getFrom()][bestMove.getTo()]+=n;
		return v;
	}

	void logMove(Board b, BMove move, int value)
	{
	int color = b.getPiece(move.getFrom()).getColor();
	if (b.getPiece(move.getTo()) == null)
	log.println(color + "(" + b.getPiece(move.getFrom()).getRank() + ") " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
	+ " hasMoved:" + b.getPiece(move.getFrom()).hasMoved()
	+ " isKnown:" + b.getPiece(move.getFrom()).isKnown()
	+ " moves:" + b.getPiece(move.getFrom()).moves
	+ " " + value);
	else
	log.println(color + "(" + b.getPiece(move.getFrom()).getRank() + "x" + b.getPiece(move.getTo()).getRank() + ")" + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
	+ " hasMoved:" + b.getPiece(move.getFrom()).hasMoved()
	+ " isKnown:" + b.getPiece(move.getFrom()).isKnown()
	+ " moves:" + b.getPiece(move.getFrom()).moves
	+ " hasMoved:" + b.getPiece(move.getTo()).hasMoved()
	+ " isKnown:" + b.getPiece(move.getTo()).isKnown()
	+ " moves:" + b.getPiece(move.getTo()).moves
	+ " " + value);
	}

	public void logMove(Move m)
	{
		logMove(board, m, 0);
	}

	public void log(String s)
	{
		log.println(s);
		log.flush();
	}
}

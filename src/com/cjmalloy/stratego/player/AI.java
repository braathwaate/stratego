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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JOptionPane;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.BMove;
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

	private ReentrantLock tLock = new ReentrantLock();
	private Semaphore bLock = new Semaphore(1);
	private static TestingBoard tmpB = null;
	private int turnF = 0;
	private static int[] dir = { -12, -1,  1, 12 };

	private Thread threads[] = new Thread[160];
	private int threadc = 0;
		

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
			fn = setup.get(rnd.nextInt(setup.size()));
			rnd = null;
			
			BufferedReader in;
			try
			{
				if(!f.exists()) {
					InputStream is = Class.class.getResourceAsStream(fn);
					InputStreamReader isr = new InputStreamReader(is);
					in = new BufferedReader(isr);
				} else
					in = new BufferedReader(new FileReader(fn));
			}
			catch (Exception e)
			{
				setup.remove(fn);
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
		
		engine.play();
	}

	public void run() 
	{
		Move bestMove = null;
		
		aiLock.lock();
		try
		{
			bestMove = getBestMove(board, Settings.aiLevel);
		}
		finally
		{
			aiLock.unlock();
		}
		
		tmpB = null;
		System.runFinalization();
		System.gc(); 
		
		engine.aiReturnMove(bestMove);
	}

/*	
	The AI algorithm is the usual minimax with alpha-beta pruning.
	The search depth is adjustable with the Settings menu.

	At the default depth (5 ticks = 6 plys = 3 ai + 3 opponent moves),
	it completes within a second on a modern desktop.  It completes within
	a couple of seconds at 7 ticks.

	Evaluation heuristic is based on material gained versus lost.
	Collisions between unknown pieces usually result in both pieces removed
	from the move tree, unless it sure that one or the other will win because
	of invincible rank.  Unknown unmoved pieces that are removed have a specified
	value, which is about equal to a Six.  This results in an eagerness
	to use pieces of ranks Seven through Nine to discover opponent's pieces.

	This simple algorithm results in a basic level of play.  

	Areas for improvements are:
	1. Regression testing.  Design an automatic way to allow further
		improvements to be tested, such as allowing ai v. ai
		play.
	2. Improving the search tree.  Extreme pruning will be required to
		get to deeper levels.
	3. Adding probability to collisions with unknown pieces.  This would
		lead to the computer choosing which pieces to target,
		and ultimately the flag and its defences.
*/

	private Move getBestMove(Board b, int n)
	{
		BMove move = null;
		BMove tmpM = null;
		ArrayList<BMove> moveList = new ArrayList<BMove>();
		ArrayList<TestingBoard> boardList = new ArrayList<TestingBoard>();
		
		if (n%2==0)
			turnF = 0;
		else
			turnF = 1;

		int value = -9999;
		int alpha = -9999;
		int beta = 9999;

		for (int j=13;j<=131;j++)
		{
			// order the move evaluation to consider
			// the pieces furthest down the board first because
			// already moved pieces have the highest scores.
			// This results in the most alpha-beta pruning.
			int i = 144 - j;

			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			if (fp.getColor() != Settings.topColor)
				continue;
			if (fp.getRank() == Rank.BOMB ||
				fp.getRank() == Rank.FLAG)
				continue;
			for (int k=0;k<4;k++)
			{
				int d = dir[k];
				int t = i + d;
				boolean scoutFarMove = false;
				for ( ;b.isValid(t); t+=d, scoutFarMove = true) {
					Piece tp = b.getPiece(t);
					if (tp != null && tp.getColor() == fp.getColor())
						break;
					
					tmpM = new BMove(i, t);
					if (b.isRecentMove(tmpM))
						break;

					// a new TestingBoard is required for each move
					// because evaluation changes piece data
					tmpB = new TestingBoard(b);
					tmpB.move(tmpM, 0, scoutFarMove);

					moveList.add(tmpM);
					// store the board pointer rather
					// than the value because we might
					// be using threads to eval values.
					boardList.add(tmpB);

					// do not access tmpB after this point
					// if we are using threads

					// threaded
					// threadValueNMoves(tmpB, n-1, alpha, beta); 
					// end of threaded

					// not threaded
					valueNMoves(tmpB, n-1, alpha, beta); 
					// valueNMoves(tmpB, n-1, -9999, 9999); 
System.out.println(n + " (" + b.getPiece(tmpM.getFrom()).getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY() + " " + tmpB.getValue());
					if (tmpB.getValue() > alpha)
					{
						alpha = tmpB.getValue();
					}
					// end of not threaded

					if (fp.getRank() != Rank.NINE || tp != null)
						break;
				} // for
			} // k
		}

		for (int i=0;i<160;i++)
			if (threads[i] != null)
				try
				{
					threads[i].join();
					threads[i] = null;
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}


		for (int i = 0; i < moveList.size(); i++) {
			tmpM = moveList.get(i);
			boardList.get(i).getValue();
			int vm = boardList.get(i).getValue();

			if (vm > value)
			{
				value = vm;
				move = tmpM;
			}

		}
System.out.println(n + " (" + b.getPiece(move.getFrom()).getRank() + ") " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY() + " " + value);
System.out.println("----");

		
		return new Move(b.getPiece(move.getFrom()), move);
	}


	// Evaluation of the first ply of moves using threads
	// could be useful on parallel hardware, but it may be
	// faster to use a single thread so that each successive
	// move evaluation can rely on alpha-beta updates of the prior move.
	private void threadValueNMoves(final TestingBoard b, final int n, final int alpha, final int beta)
	{
		threads[threadc] = new Thread()
		{
			public void run()
			{
				valueNMoves(b, n, alpha, beta);
			}
		};

		bLock.acquireUninterruptibly();
		threads[threadc].start();
		threadc++;
		bLock.release();
	}

	private void valueNMoves(TestingBoard b, int n, int alpha, int beta)
	{
		if (n<1)
			return;

		int turn;
		int v;
		if ((turnF + n) % 2 == 0) {
			v = alpha;
			turn = Settings.topColor;
		} else {
			v = beta;
			turn = Settings.bottomColor;
		}

		int depth = Settings.aiLevel - n;
		
		for (int j=13;j<=131;j++)
		{
			// order the move evaluation to consider
			// the pieces furthest down the board first because
			// already moved pieces have the highest scores.
			// This results in the most alpha-beta pruning.
			int i;
			if (turn == Settings.topColor)
				i = 144 - j;
			else
				i = j;

			Piece fp = b.getPiece(i);
			if (fp == null)
				continue;
			if (fp.getColor() != turn)
				continue;
			if (fp.getRank() == Rank.BOMB ||
				fp.getRank() == Rank.FLAG)
				continue;
				
			for (int k=0;k<4;k++)
			{
				int t = i + dir[k];
				if  (!b.isValid(t))
					continue;
				Piece tp = b.getPiece(t);
				if (tp != null && tp.getColor() == fp.getColor())
					continue;

				BMove tmpM = new BMove(i, t);
				if (b.isRecentMove(tmpM))
					continue;

				int valueB = b.getValue();
				b.move(tmpM, depth, false);
				valueNMoves(b, n-1, alpha, beta);

				int vm = b.getValue();

				b.undo(fp, i, tp, t, valueB);
/*
for (int ii=5; ii >= n; ii--)
	System.out.print("  ");
System.out.println(n + " (" + fp.getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY() + " " + vm);
*/


				if (vm > alpha && turn == Settings.topColor)
				{
					v = alpha = vm;
				}
				if (vm < beta && turn == Settings.bottomColor)
				{
					v = beta = vm;
				}

				if (beta <= alpha) {
					b.setValue(v);
					return;
				}
			} // k
		} // 
		b.setValue(v);
		return;
	}
}

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

	private int mmax;
	private int foo;	//java mmax bug
	private static int[] dir = { -11, -1,  1, 11 };
	private int[] movable = new int[92];

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
				setup.remove(opts[0]);
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
		BMove bestMove = null;
		aiLock.lock();
                try
                {
	
			bestMove = getBestMove(new TestingBoard(board), Settings.aiLevel);
		}
		finally
		{
			aiLock.unlock();
		}
		
		System.runFinalization();
		System.gc(); 

		// return the actual board move
		engine.aiReturnMove(new Move(board.getPiece(bestMove.getFrom()), bestMove));

	}
/*	
	The AI algorithm is the usual minimax with alpha-beta pruning.
	The search depth is adjustable with the Settings menu.

	At the default depth (5 ticks = 6 plys = 3 ai + 3 opponent moves),
	it completes within a second on a modern desktop.  It completes within
	a few seconds at 7 ticks.

	Evaluation heuristic is based on material gained versus lost.
	An ai piece will avoid unknown pieces unless it is invincible.
	Unknown unmoved pieces that are removed have a specified
	value, which is about equal to a Six.  This results in an eagerness
	to use pieces of ranks Six, Seven and Nine to discover opponent's pieces.

	This simple algorithm results in a basic level of play.  

	Areas for improvements are:
	1. Regression testing.  Design an automatic way to allow further
		improvements to be tested, such as allowing ai v. ai
		play.
	2. Improving the search tree.  Extreme pruning will be required to
		get to deeper levels.  A transposition table is needed.
	3. Adding probability to collisions with unknown pieces.  This would
		lead to the computer choosing which pieces to target,
		and ultimately the flag and its defences.
*/

	private ArrayList<MoveValuePair> getMoves(TestingBoard b, int n, int turn, int depth)
	{
		BMove move = null;
		BMove tmpM = null;
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
			Rank rank = fp.getRank();
			for (int k=0;k<4;k++)
			{
				int d = dir[k];
				int t = i + d;
				boolean unknownScoutFarMove = false;
				for ( ;b.isValid(t); t+=d) {
					Piece tp = b.getPiece(t);
					if (tp != null && (tp.getColor() == fp.getColor() || rank != Rank.EIGHT && tp.getRank() == Rank.BOMB && tp.isKnown()))
						break;
					
					tmpM = new BMove(i, t);
					if (b.isRecentMove(tmpM))
						break;
					moveList.add(new MoveValuePair(tmpM, 0, unknownScoutFarMove));
					// NOTE: FORWARD PRUNING
					// generate scout far moves only at depth 0
					if (depth != 0 || (fp.getRank() != Rank.NINE || tp != null))
						break;
					if (!fp.isKnown())
						unknownScoutFarMove = true;
				} // for
			} // k
		}

		return moveList;
	}


	private BMove getBestMove(TestingBoard b, int n)
	{
		BMove move = null;
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
		foo = mmax; // java bug
			
		ArrayList<MoveValuePair> moveList = getMoves(b, n, Settings.topColor, 0);
		
		long t = System.currentTimeMillis( );

		int n1 = 1;
		while (true) {

		int alpha = -9999;
		int beta = 9999;

		for (MoveValuePair mvp : moveList) {
			tmpM = mvp.move;

			int valueB = b.getValue();
			Piece fp = b.getPiece(mvp.move.getFrom());
			Piece tp = b.getPiece(mvp.move.getTo());
			b.move(tmpM, 0, mvp.unknownScoutFarMove);


			valueNMoves(b, n1-1, alpha, beta, Settings.bottomColor, 1); 
			// valueNMoves(b, n1-1, -9999, 9999, Settings.bottomColor, 1); 
System.out.println(n1 + ": " + n + " (" + fp.getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY() + " " + b.getValue());

			int vm = b.getValue();
			mvp.value = vm;
			b.undo(fp, mvp.move.getFrom(), tp, mvp.move.getTo(), valueB, mvp.unknownScoutFarMove);

			if (vm > alpha)
			{
				alpha = vm;
			}

			if (t != 0 && System.currentTimeMillis( ) > t + Settings.aiLevel * Settings.aiLevel * 100 ) {
				if (moveList.size() == 0)
					return null;	// ai trapped

				mvp = moveList.get(0);
				move = mvp.move;
				int value = mvp.value;
if (b.getPiece(move.getTo()) == null)
System.out.println(n + " (" + b.getPiece(move.getFrom()).getRank() + ") " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " hasMoved:" + b.getPiece(move.getFrom()).hasMoved()
+ " isKnown:" + b.getPiece(move.getFrom()).isKnown()
+ " moves:" + b.getPiece(move.getFrom()).moves
+ " " + value);
else
System.out.println(n + " (" + b.getPiece(move.getFrom()).getRank() + ") " + move.getFromX() + " " + move.getFromY() + " " + move.getToX() + " " + move.getToY()
+ " hasMoved:" + b.getPiece(move.getFrom()).hasMoved()
+ " isKnown:" + b.getPiece(move.getFrom()).isKnown()
+ " moves:" + b.getPiece(move.getFrom()).moves
+ " hasMoved:" + b.getPiece(move.getTo()).hasMoved()
+ " isKnown:" + b.getPiece(move.getTo()).isKnown()
+ " moves:" + b.getPiece(move.getTo()).moves
+ " " + value);
System.out.println("----");

				return move;
			}
		}
		System.out.println("-+-");
		Collections.sort(moveList);
		System.out.println("-+++-");

		n1++;

		} // iterative deepening
	}


	private void valueNMoves(TestingBoard b, int n, int alpha, int beta, int turn, int depth)
	{
		if (n<1)
			return;

		int v;
		if (turn == Settings.topColor)
			v = alpha;
		else
			v = beta;

		ArrayList<MoveValuePair> moveList = getMoves(b, n, turn, depth);
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
				int valueB = b.getValue();

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

				valueNMoves(b, n-1, alpha, beta, 1 - turn, depth + 1);

				vm = b.getValue();
				b.undo(fp, mvp.move.getFrom(), tp, mvp.move.getTo(), valueB, false);
/*
for (int ii=5; ii >= n; ii--)
	System.out.print("  ");
if (b.getPiece(tmpM.getTo()) == null)
System.out.println(n + " (" + fp.getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY()
+ " hasMoved:" + b.getPiece(tmpM.getFrom()).hasMoved()
+ " isKnown:" + b.getPiece(tmpM.getFrom()).isKnown()
+ " moves:" + b.getPiece(tmpM.getFrom()).moves
+ " aiV:" + b.getPiece(tmpM.getFrom()).aiValue()
+ " " + vm);
else
System.out.println(n + " (" + fp.getRank() + ") " + tmpM.getFromX() + " " + tmpM.getFromY() + " " + tmpM.getToX() + " " + tmpM.getToY()
+ " hasMoved:" + b.getPiece(tmpM.getFrom()).hasMoved()
+ " isKnown:" + b.getPiece(tmpM.getFrom()).isKnown()
+ " moves:" + b.getPiece(tmpM.getFrom()).moves
+ " hasMoved:" + b.getPiece(tmpM.getTo()).hasMoved()
+ " isKnown:" + b.getPiece(tmpM.getTo()).isKnown()
+ " moves:" + b.getPiece(tmpM.getTo()).moves
+ " aiV:" + b.getPiece(tmpM.getTo()).aiValue()
+ " " + vm);
*/

			}

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
		} // moveList
		b.setValue(v);
		return;
	}
}

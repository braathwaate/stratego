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
import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Engine;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Status;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.View;

import java.util.Scanner;
import java.lang.Exception;
import java.util.Vector;
import java.util.concurrent.Semaphore;




public class AITest extends View
{
	//main entry point
	private AIEngine engine;
	private Scanner scan;
	private Semaphore aimove = new Semaphore(0);
	private boolean active = false;
	private WView wview = null;

        private String colour; //Colour of the AI
        private String opponentName; //Name of the AI's opponent
        private int width; //Width of the board (NOTE: Should always be 10)
        private int height; //Height of the board (NOTE: Should always be 10)

	private static String directions[] = {"UP", "DOWN", "LEFT", "RIGHT"}; //All available directions

	private final char rankchar[] = {'?', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 's', 'B', 'F' };
	private final Rank rank[] = {
		Rank.WATER,
		Rank.ONE,
		Rank.TWO, 
		Rank.THREE,
		Rank.FOUR,
		Rank.FIVE,
		Rank.SIX,
		Rank.SEVEN,
		Rank.EIGHT,
		Rank.NINE,
		Rank.SPY,
		Rank.BOMB,
		Rank.FLAG
	};


	public AITest(boolean graphics) 
	{
		engine = new AIEngine(this);
		if (graphics) {
			wview = new WView();
			wview.showBoard(engine.getBoard());
		}
                scan = new Scanner(System.in);

	try
	{
	    setup();
	    if (Settings.topColor == Board.RED) {
		// flush START and board
		for (int i = 0; i < 11; i++)
			scan.nextLine();
		engine.play();
		aimove.acquire();
	    } else {
		engine.play();
	    }

            while (true) {
		MoveCycle();
		aimove.acquire();
            }
	}
	catch (Exception e)
                {
			e.printStackTrace();
                }

 	}

	/**
         * Cycles a move
         */
        public void MoveCycle() throws Exception
        {
                Move move = InterpretResult();	// wait for opponent move
		// board
		for (int i = 0; i < 10; i++)
			scan.nextLine();
		engine.requestUserMove(move);	// make the user move on board
        }

	/**
	 * Implements Setup phase of protocol described in manager program man page
	 */
	public void setup() throws Exception
	{
		String input = scan.nextLine();	// SETUP line
		Vector<String> setup = readTokens(input); //Wierd java way of doing input from stdin, see Reader.java
		if (setup.size() != 4)
		{
			throw new Exception("BasicAI.Setup - Expected 4 tokens, got " + setup.size());
		}	
		colour = setup.elementAt(0);
		opponentName = setup.elementAt(1);
		width = Integer.parseInt(setup.elementAt(2));
		height = Integer.parseInt(setup.elementAt(3));

		if (width != 10 || height != 10)
			throw new Exception("BasicAI.Setup - Expected width and height of 10, got " + width + " and " + height);

	        if (colour.compareTo("RED") == 0) {
			Settings.topColor = Board.RED;
			Settings.bottomColor = Board.BLUE;
		} else {
			Settings.topColor = Board.BLUE;
			Settings.bottomColor = Board.RED;
		}
		active = true;
		engine.newGame();

		engine.play();	 // this runs ai setup

		for (int y = 6; y < 10; y++) 
		for (int x = 0; x < 10; x++)  {
			Piece p = engine.getBoardPiece(x, y);
			p.setRank(Rank.UNKNOWN);	// clear ai setup rank
			p.saveActualRank();
			p.setShown(false);
		}
		printBoard();
	}

	public void printBoard()
	{
		for (int y = 0; y < 4; y++) {
			int yy = y;
			if (Settings.topColor == Board.BLUE)
				yy = 3 - y;
				
			for (int x = 0; x < 10; x++) 
				System.out.print(rankchar[engine.getBoardPiece(x,yy).getRank().ordinal()]);
			System.out.println();
		}
	}


	/**
 	 * Moves a point in a direction, returns new point
	 * @param x x coord
	 * @param y y coord
	 * @param direction Indicates direction. Must be "LEFT", "RIGHT", "UP", "DOWN"
	 * @param multiplier Spaces to move
	 * @returns An array of length 2, containing the new x and y coords
	 * @throws Exception on unrecognised direction
	 */
	public static int[] Move(int x, int y, String direction, int multiplier) throws Exception
	{
		//NOTE: The board is indexed so that the top left corner is x = 0, y = 0
		//	Does not check that coordiantes would be valid in the board.

		if (direction.compareTo("DOWN") == 0)
			y += multiplier; //Moving down increases y
		else if (direction.compareTo("UP") == 0)
			y -= multiplier; //Moving up decreases y
		else if (direction.compareTo("LEFT") == 0)
			x -= multiplier; //Moving left decreases x
		else if (direction.compareTo("RIGHT") == 0)
			x += multiplier;
		else
		{
			throw new Exception("BasicAI.Move - Unrecognised direction " + direction);
		}

		int result[] = new int[2];
		result[0] = x; result[1] = y;
		return result;
	}

	/**
	 * Tests if a value is an integer
	 * I cry at using exceptions for this
	 */
	public static boolean IsInteger(String str)
	{
		try
		{
			Integer.parseInt(str);
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		return true;
	}
	/**
	 * Interprets the result of a move, updates all relevant variables
	 */
	public Move InterpretResult() throws Exception
	{
                String input = "";
                input = scan.nextLine();
		Vector<String> result = readTokens(input);

		if (result.elementAt(0).compareTo("QUIT") == 0)
			System.exit(0);
		if (result.elementAt(0).compareTo("NO_MOVE") == 0)
			return null;

		if (result.size() < 4)
		{
			throw new Exception("BasicAI.InterpretResult - Expect at least 4 tokens, got " + result.size() + ":" + input);
		}

		int x = Integer.parseInt(result.elementAt(0));
		int y = Integer.parseInt(result.elementAt(1));
		String direction = result.elementAt(2);
		if (Settings.topColor == Board.BLUE) {
			y = 9 - y;
			if (direction.compareTo("UP") == 0)
				direction = "DOWN";
			else if (direction.compareTo("DOWN") == 0)
				direction = "UP";
		}

		int multiplier = 1;
		String outcome = result.elementAt(3);
		int outIndex = 3;
		if (IsInteger(outcome))
		{
			multiplier = Integer.parseInt(outcome);
			outcome = result.elementAt(4);
			outIndex = 4;
		}
		int p[] = Move(x,y,direction, multiplier);

		Piece attacker = engine.getBoardPiece(x,y);
		if (attacker == null)
			throw new Exception("BasicAI.InterpretResult - Couldn't find a piece to move at (" + x +"," + y+")");

		Piece defender = engine.getBoardPiece(p[0],p[1]);
		
		if (result.size() >= outIndex + 3)
		{
			if (defender == null)
				throw new Exception("BasicAI.InterpretResult - Result suggests a defender at ("+p[0]+","+p[1]+"), but none found");
			char attackerrank = result.elementAt(outIndex+1).charAt(0); //ranks are 1 char long
			int i;
			for ( i = 0; i < rank.length; i++)
				if (rankchar[i] == attackerrank)
					break;
			assert !attacker.isRevealed()
				|| attacker.getRank() == rank[i]
					: "Rank " + attacker.getRank() + " != " + rank[i] + " " + x + " " + y + " " + attackerrank;
			if (!attacker.isRevealed())
				attacker.revealRank(rank[i]);

			char defenderrank = result.elementAt(outIndex+2).charAt(0); //ranks are 1 char long
			for ( i = 0; i < rank.length; i++)
				if (rankchar[i] == defenderrank)
					break;
			assert !defender.isRevealed()
				|| defender.getRank() == rank[i]
				: "Rank " + defender.getRank() + " != " + rank[i] + " " + x + " " + y + " " + defenderrank;
			if (!defender.isRevealed())
				defender.revealRank(rank[i]);
			
		}

		// System.out.println(attacker.getRank() + " to " + p[0] + " " + p[1]);
		return new Move(attacker, new Spot(x, y), new Spot(p[0], p[1]));
	}

	public void moveInit(Move m)
	{
		int fromy = m.getFromY();
		int toy =  m.getToY();
		if (Settings.topColor == Board.BLUE) {
			// ai pieces always start at 0 (top)
			// but aitest has RED pieces at top
			fromy = 9 - fromy;
			toy = 9 - toy;
		}

		int dirIndex = 0;
		if (fromy > toy)
			dirIndex = 0;
		if (fromy < toy)
			dirIndex = 1;
		if (m.getFromX() > m.getToX())
			dirIndex = 2;
		if (m.getFromX() < m.getToX())
			dirIndex = 3;
		System.out.print(""+m.getFromX() + " " + fromy + " " + directions[dirIndex]);
		int ydiff = Math.abs(fromy - toy);
		int xdiff = Math.abs(m.getFromX() - m.getToX());
		if (xdiff > 1)
			System.out.print(" " + xdiff);
		if (ydiff > 1)
			System.out.print(" " + ydiff);
		System.out.println();

		try {
		InterpretResult();
		}
		catch (Exception e)
			{
			e.printStackTrace();
			}
	}

	public synchronized void moveComplete(Move m)
	{
		update();
		aimove.release();
	}

	public boolean isActive() { return active; }

	public static Vector<String> readTokens(String r)
	{
		// String r = readLine();
		String token = "";
		Vector<String> result = new Vector<String>();
		for (int ii=0; ii < r.length(); ++ii)
		{
			if (r.charAt(ii) == ' ' || r.charAt(ii) == '\n')
			{
				result.add(new String(token));	
				//System.out.println("Token " + token);
				token = "";
			}
			else
				token += r.charAt(ii);
		}
		result.add(new String(token));
		return result;
	}

	public void update()
	{
		if (wview != null)
			wview.update();
	}
}

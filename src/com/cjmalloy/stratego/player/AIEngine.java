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

import java.io.IOException;

import javax.swing.JOptionPane;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.Engine;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Settings;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Status;
import com.cjmalloy.stratego.View;



public class AIEngine extends Engine implements CompControls, UserControls
{
	private View view = null;
	private AI ai = null;
	
	public AIEngine(View v)
	{
		view = v;
		board = new Board();
		ai = new AI(board, this);
	}
	
	public void play()
	{
                if (status == Status.PLAYING) {
                        board.undoLastMove();
                        update();
			return;
                }

		if (status != Status.SETUP)
			return;

		if (board.getTraySize() == 0)
		{
			// this is how we get started
			view.setUndoMode();
			status = Status.PLAYING;
			if (turn!=Settings.bottomColor)
				requestCompMove();
		}
		else 
		{
			try {
				ai.getBoardSetup();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if (Settings.bShowAll)
			board.showAll();
	}

	private void requestCompMove()
	{
		assert (status == Status.PLAYING) : "requestCompMove but not playing?";
		ai.getMove();
	}
	
	public void requestUserMove(Move m)
	{
		if (m == null) return;
		
		if (status == Status.SETUP)
		{
			if (setupRemovePiece(new Spot(m.getFromX(), m.getFromY())))
					setupPlacePiece(m.getPiece(), new Spot(m.getToX(), m.getToY()));
		}
		else
		{
			if (Settings.bShowAll)
				board.showAll();
			else if (!Settings.bNoHideAll)
				board.hideAll();

			// wait for the ai to finish
			ai.aiLock.lock();
			ai.aiLock.unlock();

			// perhaps the ai move finished the game
			if (status != Status.PLAYING)
				return;

			ai.logMove(m);
			if (requestMove(m, view.isActive())) {
				// perhaps the move finished the game
				if (status != Status.PLAYING)
					return;
				requestCompMove();
			} else
				ai.log("ILLEGAL MOVE");
		}
	}

	public void aiReturnMove(Move m)
	{
		if (m==null || m.getPiece()==null)
		{
			ai.log("AI trapped");
			status = Status.STOPPED;
			board.showAll();
			view.setPlayMode();
			view.gameOver(Settings.bottomColor);
			return;
		}

		assert m.getPiece().getColor() != Settings.bottomColor
			: "piece is bottom color?";
		
		view.moveInit(m);
		if (!requestMove(m, view.isActive())) {
			ai.log("ILLEGAL MOVE-->");
			ai.logMove(m);
			ai.log("<--ILLEGAL MOVE");
		}

		view.moveComplete(m);
	}
	
	public void aiReturnPlace(Piece p, Spot s)
	{
		if (!setupPlacePiece(p, s))
			return;
		view.update();
	}

	@Override
	protected void gameOver(int winner)
	{
		view.setPlayMode();
		view.gameOver(winner);
	}

	@Override
	protected void update()
	{
		view.update();
	}
}

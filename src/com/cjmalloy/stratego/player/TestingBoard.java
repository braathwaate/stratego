/*.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.cjmalloy.stratego.Board;
import com.cjmalloy.stratego.BMove;
import com.cjmalloy.stratego.Grid;
import com.cjmalloy.stratego.Move;
import com.cjmalloy.stratego.Piece;
import com.cjmalloy.stratego.Rank;
import com.cjmalloy.stratego.Spot;
import com.cjmalloy.stratego.Settings;
import java.util.Random;



public class TestingBoard extends Board
{
	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected int[][] movedRank = new int[2][15];	// moved ranks
	protected int[][] trayRank = new int[2][15];	// ranks in trays
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[][][] destValue = new int[2][15][121];	// encourage forward motion
	protected int[] destTmp = new int[121];	// encourage forward motion


	protected boolean possibleSpy = false;
	protected int value;	// value of board

	public TestingBoard() {}
	
	public TestingBoard(Board t)
	{
		super(t);

		value = 0;

		for (int c = RED; c <= BLUE; c++)
		for (int j=0;j<15;j++) {
			knownRank[c][j] = 0;
			movedRank[c][j] = 0;
			trayRank[c][j] = 0;
			neededRank[c][j] = false;
		}

		// ai only knows about known opponent pieces
		// so update the grid to only what is known
		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p != null) {
				// copy pieces to be threadsafe
				// because piece data is updated during ai process
				Piece np = new Piece(p);
				grid.setPiece(i, np);
				if (p.getColor() == Settings.bottomColor && !p.isKnown()) {
					np.setUnknownRank();
					Random rnd = new Random();
					int v = aiValue(p.getColor(), Rank.UNKNOWN)
						// discourage stalling
						+ recentMoves.size()/10
						// we add a random 1 point to the value so that
						// if the ai has a choice of attacking two or more unknown
						// pieces during a series of attacks,
						// it will alternate direction.
						+ rnd.nextInt(2);
					// if a piece has already moved, then
					// we already know something about it,
					// especially if it has Acting Rank,
					// so we don't gain as much
					// by discovering it.

					// unknown pieces are worth more on the back ranks
					// because we need to find bombs around the flag
					// note: *3 to outgain
					// -depth early capture rule
					if (!p.hasMoved())
						v += (i/11-7) * 3;
					else
						v /= 2;
					np.setAiValue(v);
			 	} else {
					np.setAiValue(aiValue(p.getColor(), p.getRank()));
					int r = p.getRank().toInt();
					if (p.isKnown())
						knownRank[p.getColor()][r-1]++;
				}

				if (p.hasMoved()) {
					// count the number of moved pieces
					// to determine further movement penalties
					Rank rank = p.getRank();
					int r = rank.toInt();
					movedRank[p.getColor()][r-1]++;
				}
			}
		}

		// add in the tray pieces to knownRank and to trayRank
		for (int i=0;i<getTraySize();i++) {
			Piece p = getTrayPiece(i);
			int r = p.getRank().toInt();
			knownRank[p.getColor()][r-1]++;
			trayRank[p.getColor()][r-1]++;
		}

		// Destination Value Matrices
		//
		// Note: at depths >8 moves (16 ply),
		// these matrices may not necessary
		// because these pieces will find their targets in the 
		// move tree.  However, they would still be useful in pruning
		// the move tree.
		for (int c = RED; c <= BLUE; c++)
		for (int rank = 0; rank < 15; rank++) {
			for (int j=12; j <= 120; j++)
				destValue[c][rank][j] = -99;
		}

		for (int i=12;i<=120;i++) {
			if (!isValid(i))
				continue;
			Piece p = getPiece(i);
			if (p != null) {
			Rank rank = p.getRank();

			// demote the value of the spy if there is
			// no opponent marshal left at large
			if (rank == Rank.SPY && !rankAtLarge(1-p.getColor(), 1))
				p.setAiValue(aiValue(p.getColor(), Rank.SEVEN));
				
			// Encourage lower ranked pieces to find pieces
			// of higher ranks.
			//
			// Note: this is a questionable heuristic
			// because the ai doesn't know what to do once
			// the piece reaches its destination.
			// Only 1 rank is assigned to chase an opponent piece
			// to prevent a pile-up of ai pieces
			// chasing one opponent piece that is protected
			// by an unknown and possibly lower piece.
			else if (p.isKnown()) {
				int r = rank.toInt();
				if (r >= 2 && r <= 7)  {
					genDestTmp(p.getColor(), i, -1);
					for (int j = r - 1; j >= 1; j--)
					if (rankAtLarge(1-p.getColor(),j)) {
						genDestValue(1-p.getColor(), j);
						break;
					}
				}
			} else if (!p.hasMoved()) {
				// Encourage lower ranked pieces to discover
				// unknown and unmoved pieces
				// Back rows may be further, but are of
				// equal destination value.
				int y = Grid.getY(i);
				if (y <= 3)
					y = -y;
				genDestTmp(p.getColor(), i, y - 9);
				genDestValue(1-p.getColor(), 5);
				genDestValue(1-p.getColor(), 6);
				genDestValue(1-p.getColor(), 7);
				// nines don't need any encouragement

				// ai flag discovery
				if (p.getRank() == Rank.FLAG) {
					assert p.getColor() == Settings.topColor : "flag routines only for ai";
					// assume opponent has guessed it
					p.setKnown(true);
					// assume opponent will try to get to it
					genDestTmp(p.getColor(), i, 4);
					for (int r = 5; r <= 10; r++)
						genDestValue(1-p.getColor(), r);
					// defend it by lowest ranked pieces
					for (int r = 1; r <= 7; r++)
						if (rankAtLarge(p.getColor(),r)) {
							genDestValue(p.getColor(), r);
							break;
						}
				}
			}
			}
		}

		// How to guess whether the marshal can be attacked by a spy.
		//
		// The most conservative approach is to assume that
		// any unknown piece could be a spy, if the spy is
		// still on the board.
		//
		// The approach taken is that if a piece
		// has an actingRankLow, then it is not a spy.
		// That is because it is unlikely (although possible) that
		// that the opponent would have moved its spy into a position
		// where it could be attacked.
		possibleSpy = (knownRank[Settings.bottomColor][9] == 0);

		// a rank becomes invincible when all higher ranking pieces
		// are gone or known
		for (int c = RED; c <= BLUE; c++) {
			int rank = 0;
			for (;rank<10;rank++) {
				if (knownRank[c][rank] != Rank.getRanks(rank))
					break;
			}
			invincibleRank[1-c] = rank + 1;
		}

		// keep a piece of the lowest remaining rank in motion,
		// preferably a 6 or 7, to discover unknown pieces.
		// 5s are also drawn towards unknown pieces, but they
		// are more skittish and therefore blocked by moving
		// unknown pieces.
		for (int c = RED; c <= BLUE; c++) {
			for (int r = 7; r >= 1; r--)
				if (movedRank[c][r-1] != 0) {
					if (r >= 6)
						break;
					for (int nr = 7; nr > r; nr--)
						neededRank[c][nr-1] = true;
					break;
				}
				
		}


		possibleFlag();
		possibleBomb();
		valueBombs();
	}

	private boolean rankAtLarge(int color, int rank)
	{
		return (trayRank[color][rank-1] != Rank.getRanks(rank-1));
	}

	// set the flag value higher than a bomb
	// so that a miner that removes a bomb to get at the flag
	// will attack the flag rather than another bomb
	private void genDestFlag(int i)
	{
		Piece p = getPiece(i);
		if (p != null && !p.isKnown() && !p.hasMoved()) {
			if (p.getColor() == Settings.bottomColor)
				p.setAiValue(120);
			// hmmm, currently we are not able to change
			// unknown values for ai pieces
			// else
			//	p.setAiValue(p.aiValue() - 4);
			genDestTmp(p.getColor(), i, 4);
			for (int k = 5; k <= 10; k++)
				genDestValue(1-p.getColor(), k);
		}
	}

	// analyze the board setup for possible flag positions
	// for suspected flag pieces,
	// add value to make the piece more desirable to attack
	// and also establish it as a preferable destination.
	private void possibleFlag()
	{
		for (int i = 12; i < 22; i++)
			if (setup[i+11] == Rank.BOMB)
				genDestFlag(i);
		for (int i = 111; i <= 120; i++)
			if (setup[i-11] == Rank.BOMB)
				genDestFlag(i);
	}

	// assess the board for isolated unmoved pieces (possible bombs)
	// we reset the piece rank to bomb and value so that the ai
	// move generation will not generate any moves for the piece
	// and the ai will not want to attack it.
	private void possibleBomb()
	{
		for (int i = 78; i <= 120; i++) {
			if (!isValid(i))
				continue;
			Piece tp = getPiece(i);
			if (tp != null && !tp.isKnown() && !tp.hasMoved()) {
				boolean found = false;
				for (int k =0; k < 4; k++) {
					int j = i + dir[k];
					if (!isValid(j))
						continue;
					Piece p = getPiece(j);
					if (p != null && !p.hasMoved()) {
						found = true;
						break;
					}
				}
				if (!found)
					tp.setPossibleBomb();
			}
		}
	}

	// some bombs are worth removing and others we can ignore.
	// initially all bombs are worthless (-99)
	private void valueBombs()
	{
		// remove bombs that block the lanes
		int [][] frontPattern = { { 78, 79 }, {90, 79}, {82, 83}, {86, 87}, {86, 98} };
		for (int i = 0; i < 5; i++) {
			Piece p1 = getPiece(frontPattern[i][0]);
			Piece p2 = getPiece(frontPattern[i][1]);
			if (p1 != null && p1.getRank() == Rank.BOMB
				&& p2 != null && p2.getRank() == Rank.BOMB) {
				genDestTmp(p1.getColor(), frontPattern[i][0], -1);
				genDestValue(1-p1.getColor(), 8);
				p1.setAiValue(100);
				genDestTmp(p2.getColor(), frontPattern[i][1], -1);
				genDestValue(1-p2.getColor(), 8);
				p2.setAiValue(100);
				if (movedRank[1-p2.getColor()][7] == 0)
					neededRank[1-p2.getColor()][7] = true;
			}
		}
		
		// remove bombs that surround a possible back row flag
		for (int i = 111; i<= 120; i++) {
			Piece tp = getPiece(i);
			if (tp != null && !tp.isKnown()) {
				boolean found = true;
				for (int k =0; k < 3; k++) {
					int j = i + dir[k];
					if (!isValid(j))
						continue;
					Piece p = getPiece(j);
					if (p == null) {
						found = false;
						break;
					}
					if (p.getRank() == Rank.BOMB)
						found = true;
				}
				if (found)
				for (int k =0; k < 3; k++) {
					int j = i + dir[k];
					if (!isValid(j))
						continue;
					Piece p = getPiece(j);
					if (p.getRank() == Rank.BOMB) {
						p.setAiValue(100);
						genDestTmp(p.getColor(), j, -1);
						// send a miner
						genDestValue(1-p.getColor(), 8);
						if (movedRank[1-p.getColor()][7] == 0)
							neededRank[1-p.getColor()][7] = true;
						// send a low ranked piece for protection
						for (int r = 1; r < 8; r++)
							if (rankAtLarge(1-p.getColor(), r)) {
								genDestValue(1-p.getColor(), r);
								break;
							}
					}
				}
		  	}
		}
	}


	private void genDestTmp(int color, int to, int n)
	{
		for (int j = 12; j <= 120; j++)
			destTmp[j] = -99;
		destTmp[to] = n;
		boolean found = true;
		while (found) {
		found = false;
		for (int j = 12; j <= 120; j++) {
			if (!isValid(j) || destTmp[j] != n)
				continue;
			for (int k = 0; k < 4; k++) {
				int i = j + dir[k];
				if (!isValid(i) || destTmp[i] != -99)
					continue;
				// opposing pieces and bombs block the destination but
				// the move tree allows pieces of the same color
				// to move out of the way, although this costs an
				// additional move.
				Piece p = getPiece(i);
				if (p != null) {
					if (p.getColor() == color || p.getRank() == Rank.BOMB) {
						destTmp[i] = n+1;
						continue;
					}
				}
				destTmp[i] = n - 1;
				found = true;
			}
		}
		n--;
		}
	}

	private void genDestValue(int color, int rank)
	{
		for (int j = 12; j <= 120; j++)
			if (destValue[color][rank-1][j] < destTmp[j])
				destValue[color][rank-1][j] = destTmp[j];
	}

	public boolean isInvincible(Piece p) 
	{
		return (p.getRank().toInt() <= invincibleRank[p.getColor()]);
	}

	// TRUE if move is valid
	public boolean move(BMove m, int depth, boolean unknownScoutFarMove)
	{
		Piece fp = getPiece(m.getFrom());
		Piece tp = getPiece(m.getTo());
		setPiece(null, m.getFrom());
		fp.moves++;

		if (tp == null) { // move to open square
			Rank rank = fp.getRank();
			int r = rank.toInt()-1;
			int color = fp.getColor();
			if (color == Settings.topColor)
				value += (destValue[color][r][m.getTo()]
					- destValue[color][r][m.getFrom()]);
			else
				value -= (destValue[color][r][m.getTo()]
					- destValue[color][r][m.getFrom()]);

			if (!fp.isKnown() && !fp.hasMoved())
				// moving a unknown piece makes it vulnerable
				value += aiMovedValue(fp, fp.moves % 3);
			setPiece(fp, m.getTo());
			if (unknownScoutFarMove) {
				// moving an unknown scout
				// more than one position makes it known
				// penalize so that scout should only 
				// make itself known for a long voyage
				// towards a destination.
				value += aiMovedValue(fp, 5);
				fp.setKnown(true);
			}
			recentMoves.add(m);
		} else { // attack
			if ((fp.isKnown() || fp.getColor() == Settings.topColor)
				&& (tp.isKnown() || tp.getColor() == Settings.topColor)) {
				// known attacker and defender
				int result = fp.getRank().winFight(tp.getRank());
				if (result == -1) {	// even
					setPiece(null, m.getTo());
					value += fp.aiValue() + tp.aiValue();
				} else if (result == 0) { // lost
					value += fp.aiValue();
					// unknown pieces have bluffing value
					if (depth != 0 && !fp.isKnown() && !isInvincible(tp)) {
						assert fp.getColor() == Settings.topColor : "if fp is unknown then fp must be ai";
						aiBluffingValue(m.getTo());
					}
				} else {	// won
					// The defending piece value is
					// gained only if it is known or
					// if it moved.  This encourages the
					// the ai to not move its unknown
					// pieces that are subject to loss
					// by attack.
					if (tp.isKnown() || tp.moves != 0)
						value += tp.aiValue();
					// fp loses stealth if it is not known
					aiStealthValue(fp);
					setPiece(fp, m.getTo()); // won
				}
			} else if (tp.getColor() == Settings.topColor) {
				// ai is defender (tp)

				if (tp.getRank() == Rank.BOMB) {
					// ai is defender and piece is bomb
					if (fp.getRank() == Rank.EIGHT) {
						// miner wins if it hits a bomb
						setPiece(fp, m.getTo()); // won
						value += tp.aiValue();
					} else {
						// non-miner loses if it hits a bomb
						// bomb stays on board
						value += fp.aiValue();
					}
				} else if (isInvincible(tp) && !(tp.getRank() == Rank.ONE && possibleSpy && fp.getActingRankLow() == Rank.NIL)) {
					// tp stays on board
					value += fp.aiValue();
					// even if the piece is invincible
					// it loses stealth if it is not known
					aiStealthValue(tp);
				} else {
					aiUnknownValue(fp, tp, m.getTo(), depth);
				}
			} else {
				// ai is attacker (fp)
				// it may or may not be known
				// defender may or may not be known

				assert fp.getColor() == Settings.topColor : "fp is opponent?";

				if (isInvincible(fp) && tp.moves != 0) {
					value += tp.aiValue();
					// even if the piece is invincible
					// it loses stealth if it is not known
					aiStealthValue(fp);
					setPiece(fp, m.getTo()); // won
				} else {
					aiUnknownValue(fp, tp, m.getTo(), depth);
				}
			}

			// prefer early attacks for both ai and opponent
			if (fp.getColor() == Settings.topColor)
				value -= depth;
			else
				// this helps keep ai's attacked pieces
				// next to unknown defending pieces
				value += depth;

			return true;

		} // else attack
		return false;
	}

	public void undo(Piece fp, int f, Piece tp, int t, int valueB, boolean unknownScoutFarMove)
	{
		setPiece(fp, f);
		setPiece(tp, t);
		value = valueB;
		fp.moves--;
		if (tp == null)
			recentMoves.remove(recentMoves.size()-1);
		if (unknownScoutFarMove)
			fp.setKnown(false);
	}
	
	// Value given to discovering an unknown piece.
	// Discovery of these pieces is the primary driver of the heuristic,
	// as it leads to discovery of the flag.
	private void aiUnknownValue(Piece fp, Piece tp, int to, int depth)
	{
		// If the defending piece has not yet moved
		// but we are evaluating moves for it,
		// assume that the defending piece loses.
		// This deters new movement of pieces near opp. pieces,	
		// which makes them easy to capture.

		if (tp.getColor() == Settings.bottomColor) {
			// ai is attacker (fp)
			assert !tp.isKnown() : "tp is known"; // defender (tp) is unknown

			// Note: Acting Rank is an unreliable predictor
			// of actual rank.
			//
			// tp actingRankHigh is very unreliable because
			// often an unknown opponent piece (such as a 1-3)
			// will eschew taking a known ai piece (such as a 5-7)
			// to avoid becoming known.
			//
			// If the tp actingRankHigh is >= fp rank
			// it is probably at least an even exchange and
			// perhaps better, so we add the entire tp value
			// and do not subtract the tp value (which we do in
			// the case of an even exchange).
			// if (tp.getActingRankHigh() != Rank.NIL
			//	&& tp.getActingRankHigh().toInt() >= fp.getRank().toInt())
			//	value += tp.aiValue();
			// else
			if (tp.getActingRankLow() != Rank.NIL
				&& tp.getActingRankLow().toInt() <= fp.getRank().toInt())
				value += fp.aiValue();
			else
				value += tp.aiValue() + fp.aiValue();
			if (!tp.hasMoved()) {
				// assume lost
				// "from" square already cleared on board
			} else if (depth != 0 && fp.moves == 1 && !fp.isKnown() && !isInvincible(tp)) {
				// "from" square already cleared on board
				aiBluffingValue(to);
				setPiece(null, to);	// assume even
			}
				// else assume lost
				// "from" square already cleared on board
		} else {
			// ai is defender (tp)
			assert !fp.isKnown() : "fp is known"; // defender (fp) is unknown
			// encourage ai to not move an unmoved piece
			// that is subject to attack.
			if (!tp.hasMoved() && tp.moves == 0 && !tp.isKnown())
				value += fp.aiValue();
			else
				value += tp.aiValue() + fp.aiValue();

			if (!fp.hasMoved()) {
				// attacker loses
				// "from" square already cleared on board
			// unknown but moved ai pieces have some bluffing value
			} else if (tp.hasMoved() && !tp.isKnown() && !isInvincible(fp)) {
				// bluffing value
				aiBluffingValue(to);
			} else
				// setPiece(null, to);	// even
				// this encourages the ai to keep
				// its attacked pieces next to its unknown
				// pieces, because if attacker wins
				// then it is subject to attack
				// by unknown ai pieces.
				setPiece(fp, to);	// attacker wins
		}
	}

	// This penalty value deters movement of an unmoved piece
	// to an open square.
	// destValue[] encourages a piece to move.
	// But moving a piece makes it more subject to attack because
	// an attacker knows it cannot be a bomb.
	// So we only want an unmoved piece to move if it can make substantial
	// progress towards its destination.
	// Each time a piece moves a square towards its destination
	// it gains a point.  So in a N move depth search, it can gain
	// N points.  So the penalty needs to be 1 < penalty < N + 1.

	public int aiMovedValue(Piece fp, int v)
	{
		int color = fp.getColor();
		Rank rank = fp.getRank();
		if (neededRank[color][rank.toInt()-1])
			return 0;

		if (color == Settings.bottomColor) {
			return v;
		} else {
			return -v;
		}
	}

	// if the prior move was to the target square,
	// then the opponent must consider whether the ai is bluffing.
	// This could occur if the opponent moves a piece next to an
	// unknown ai piece or if the ai moved its unknown piece next to an
	// opponent piece.
	//
	// Either way,add bluffing value.
	//
	// However, this gets stale after the next move, because if the ai
	// does not attack, it probably means the ai is less strong,
	// and the opponent will know it.
	public void aiBluffingValue(int to)
	{
		BMove m = recentMoves.get(recentMoves.size()-1);
		if (m.getTo() == to)
			value += 30;
	}

	// Stealth value is expressed as a percentage of the piece's
	// actual value.  An unknown Marshal should not take a Six
	// or even a Five, but certainly a Four is worthwhile.
	// An unknown General should not take a Six, but a Five
	// would be tempting.
	//
	// So maybe stealth value is about 11%.
	//
	// If a piece has moved, then much is probably guessed about
	// the piece already, so it has half of its stealth value.
	public void aiStealthValue(Piece p)
	{
		if (!p.isKnown()) {
			int v = aiValue(p.getColor(), p.getRank()) / 9;
			if (p.hasMoved())
				v /= 2;
			value += v;
		}
	}

	public int getValue()
	{
		return value;
	}

	public void addValue(int v)
	{
		value += v;
	}

	public void setValue(int v)
	{
		value = v;
	}

	// initial values subject to change
	private static int aiValue(int color, Rank r)
	{
		int [] values = {
			0,
			420,	// 1 Marshal
			360, 	// 2 General
			150, 	// 3 Colonel
			80,	// 4 Major
			45,	// 5 Captain
			30,	// 6 Lieutenant
			25,	// 7 Sergeant
			60,	// 8 Miner
			25,	// 9 Scout
			360,	// Spy
			-99,	// Bomb
			6000,	// Flag
			45,	// Unknown
			0	// Nil
		};

		int v = values[r.toInt()];
		if (color == Settings.bottomColor)
			return v;
		else
			return -v;
	}
}

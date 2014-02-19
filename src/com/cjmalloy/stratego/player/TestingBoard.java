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
	private static final int DEST_PRIORITY_DEFEND_FLAG = 20;
	private static final int DEST_PRIORITY_DEFEND_FLAG_BOMBS = 10;
	private static final int DEST_PRIORITY_ATTACK_FLAG = 10;
	private static final int DEST_PRIORITY_ATTACK_FLAG_BOMBS = 5;
	private static final int DEST_PRIORITY_LOW = -1;

	protected int[] invincibleRank = new int[2];	// rank that always wins attack
	protected int[][] knownRank = new int[2][15];	// discovered ranks
	protected boolean[][] movedRank = new boolean[2][15];	// moved ranks
	protected int[][] movedRankID = new int[2][15];	// moved rank index
	protected int[][] trayRank = new int[2][15];	// ranks in trays
	protected boolean[][] neededRank = new boolean[2][15];	// needed ranks
	protected int[][][] destValue = new int[2][15][121];	// encourage forward motion
	protected int[] destTmp = new int[121];	// encourage forward motion
	protected int[][] winRank = new int[15][15];	// winfight cache


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
			movedRank[c][j] = false;
			movedRankID[c][j] = 0;
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
					np.setRank(Rank.UNKNOWN);
					Random rnd = new Random();
					int v = aiValue(p.getColor(), Rank.UNKNOWN)
						// discourage stalling
						// TBD: stalling factor should only
						// apply in repetitive cases,
						// not generally.  Otherwise, ai
						// makes bad decisions when it
						// is actually making progress.
						// + recentMoves.size()/10
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
					movedRank[p.getColor()][r-1] = true;
					movedRankID[p.getColor()][r-1]=i;
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
					genDestTmp(p.getColor(), i, DEST_PRIORITY_LOW);
					boolean found = false;
					for (int j = r - 1; j >= 1; j--)
					if (rankAtLarge(1-p.getColor(),j)) {
						genNeededDest(1-p.getColor(), j);
						found = true;
						break;
					}
					if (!found) {
						// go for an even exchange
						genNeededDest(1-p.getColor(), r);
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
				genDestTmp(p.getColor(), i, DEST_PRIORITY_LOW + y - 9);
				genDestValue(1-p.getColor(), 5);
				genDestValue(1-p.getColor(), 6);
				genDestValue(1-p.getColor(), 7);
				genDestValue(1-p.getColor(), 9);

				// ai flag discovery
				if (p.getRank() == Rank.FLAG)
					flagSafety(i);
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
		// preferably a 6, 7 or 9 to discover unknown pieces.
		// 5s are also drawn towards unknown pieces, but they
		// are more skittish and therefore blocked by moving
		// unknown pieces.
		for (int c = RED; c <= BLUE; c++) {
			int rank[] = { 6, 7, 9 };
			boolean found = false;
			for ( int r : rank )
				if (movedRank[c][r-1]) {
					found = true;
					break;
				}
			if (!found) {
				Random rnd = new Random();
				int i = rnd.nextInt(3);
				neededRank[c][rank[i]-1] = true;
				break;
			}
		}

		possibleBomb();
		possibleFlag();
		valueLaneBombs();
		genWinRank();
	}

	// cache the winFight values for faster evaluation
	private void genWinRank()
	{
		for (Rank frank : Rank.values())
		for (Rank trank : Rank.values()) {
			int v;
			if (frank == Rank.UNKNOWN) {
				// The following results are unknown
				// but good guesses based simply on rank
				// and some board information.
				// The ai assumes that any unknown piece
				// will take a bomb because of flag safety.
				// Almost any piece will take a SPY or NINE.
				if (trank == Rank.BOMB && rankAtLarge(Settings.bottomColor, 8)
					|| trank == Rank.SPY
					|| trank == Rank.NINE)
					v = Rank.WINS;
				else
					v = frank.winFight(trank);
			} else
				v = frank.winFight(trank);
			winRank[frank.toInt()][trank.toInt()] = v;
		}
	}


	private boolean rankAtLarge(int color, int rank)
	{
		return (trayRank[color][rank-1] != Rank.getRanks(rank-1));
	}

	private void flagSafety(int flag)
	{
		Piece pflag = getPiece(flag);

		assert pflag.getColor() == Settings.topColor : "flag routines only for ai";
		// If the ai assumes opponent has guessed the flag
		// by setting it known
		// then the ai with leave pieces hanging if the opponent
		// can take the flag.  But the opponent may or may know.
		// pflag.setKnown(true);

		// assume opponent will try to get to it
		// with all known and unknown ranks
		genDestTmp(pflag.getColor(), flag, DEST_PRIORITY_DEFEND_FLAG);
		for (int r = 1; r <= Rank.UNKNOWN.toInt(); r++)
			genDestValue(1-pflag.getColor(), r);

		// defend it by lowest ranked pieces
		for (int r = 1; r <= 7; r++)
			if (rankAtLarge(pflag.getColor(),r)) {
				genDestValue(pflag.getColor(), r);
				break;
			}

		// initially all bombs are worthless
		// value bombs around ai flag
		for (int d : dir) {
			int j = flag + d;
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p == null)
				continue;
			if (p.getRank() == Rank.BOMB) {
				p.setAiValue(-aiValue(p.getColor(), Rank.BOMB));

				// assume opponent will try to remove
				// with eights and unknown ranks
				genDestTmp(p.getColor(), j, DEST_PRIORITY_DEFEND_FLAG_BOMBS);
				genDestValue(1-p.getColor(), Rank.UNKNOWN.toInt());
				genDestValue(1-p.getColor(), Rank.EIGHT.toInt());

				// If one of the bombs is known,
				// then the opponent probably has guessed
				// the location of the flag.
				// Try to protect the bombs with the lowest rank(s)
				if (p.isKnown())
					for (int r = 1; r < 8; r++)
						if (rankAtLarge(p.getColor(), r)) {
							genDestValue(p.getColor(), r);
							break;
						}
			}
		}
	}

	// establish location it as a flag destination.
	private void genDestFlag(int i)
	{
		Piece flagp = getPiece(i);
		if (flagp.getColor() == Settings.bottomColor) {
			flagp.setRank(Rank.FLAG);
			flagp.setKnown(true);
			// set the flag value higher than a bomb
			// so that a miner that removes a bomb to get at the flag
			// will attack the flag rather than another bomb
			flagp.setAiValue(-aiValue(flagp.getColor(), Rank.BOMB)+10);
		}
		// else
		// hmmm, currently we are not able to change
		// rank or value for ai pieces

		// send in a lowly piece to find out
		genDestTmp(flagp.getColor(), i, DEST_PRIORITY_ATTACK_FLAG);
		for (int k = 9; k > 0; k--) {
			genDestValue(1-flagp.getColor(), k);
			break;
		}

		// remove bombs that surround a possible flag
		// this code is only for opponent bombs
		if (flagp.getColor() == Settings.bottomColor) {
			boolean found = false;
			for (int d : dir) {
				int j = i + d;
				if (!isValid(j))
					continue;
				Piece p = getPiece(j);
				if (p == null || p.hasMoved()) {
					found = false;
					break;
				}
				if (p.getRank() == Rank.BOMB)
					found = true;
			}
			if (found)
				destBomb(i);
		}
	}

	// analyze the board setup for possible flag positions
	// for suspected flag pieces,
	private void possibleFlag()
	{
                int [][] bombPattern = {
                        { 12, 13, 23, 23 },
                        { 14, 13, 25, 15 },
                        { 15, 14, 26, 16 },
                        { 16, 15, 27, 17 },
                        { 17, 16, 28, 18 },
                        { 18, 17, 29, 19 },
                        { 19, 18, 30, 20 },
                        { 21, 20, 32, 32 }
		};

		for (int c = RED; c <= BLUE; c++) {
                for ( int[] b : bombPattern ) {
			for ( int i = 0; i < 4; i++) {
				b[i] = 132 - b[i];
			Piece flagp = getPiece(b[0]);
			if (flagp != null && !flagp.isKnown() && !flagp.hasMoved())
			for ( int j = 1; j < 4; j++)
				if (setup[b[j]] == Rank.BOMB) {
					boolean found = true;
					for ( int k = 1; k < 4; k++ ) {
						if (setup[b[k]] == Rank.BOMB)
							continue;
						Piece p = getPiece(b[k]);
						if (p == null || p.isKnown() || p.hasMoved()) {
							found = false;
							break;
						}
						
					}
					if (found) {
						genDestFlag(b[0]);
					}
					break;
				}
			}
		}
		}
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
				if (!found) {
					tp.setRank(Rank.BOMB);
					tp.setAiValue(-100);
					tp.setKnown(true);
				}
			}
		}
	}

	// set a destination for a bomb surrounding a possible flag
	// this code is only for opponent bombs
	private void destBomb(int flag)
	{
		for (int d : dir) {
			int j = flag + d;
			if (!isValid(j))
				continue;
			Piece p = getPiece(j);
			if (p.getRank() == Rank.BOMB) {
				p.setAiValue(-aiValue(p.getColor(), Rank.BOMB));

				// send a miner
				genDestTmp(p.getColor(), j, DEST_PRIORITY_LOW);
				genNeededDest(1-p.getColor(), 8);
				// send a low ranked piece ahead
				// to protect the eight
				int id = movedRankID[1-p.getColor()][7];
				if (id != 0 && isValid(id + 22))
				for (int r = 1; r < 8; r++)
					if (rankAtLarge(1-p.getColor(), r)) {
						genDestTmp(p.getColor(), id + 22, DEST_PRIORITY_LOW);
						genNeededDest(1-p.getColor(), r);
						break;
					}
			}
		}
	}

	// some bombs are worth removing and others we can ignore.
	// initially all bombs are worthless (-100)
	private void valueLaneBombs()
	{
		// remove bombs that block the lanes
		int [][] frontPattern = { { 78, 79 }, {90, 79}, {82, 83}, {86, 87}, {86, 98} };
		for (int i = 0; i < 5; i++) {
			Piece p1 = getPiece(frontPattern[i][0]);
			Piece p2 = getPiece(frontPattern[i][1]);
			if (p1 != null && p1.getRank() == Rank.BOMB
				&& p2 != null && p2.getRank() == Rank.BOMB) {
				genDestTmp(p1.getColor(), frontPattern[i][0], DEST_PRIORITY_LOW);
				p1.setAiValue(-aiValue(p1.getColor(), Rank.BOMB));
				genNeededDest(1-p1.getColor(), 8);

				genDestTmp(p2.getColor(), frontPattern[i][1], DEST_PRIORITY_LOW);
				p2.setAiValue(-aiValue(p2.getColor(), Rank.BOMB));
				genNeededDest(1-p2.getColor(), 8);
			}
		}
	}

	private void genNeededDest(int color, int rank)
	{
		genDestValue(color, rank);
		if (!movedRank[color][rank-1])
			neededRank[color][rank-1] = true;
	}

	// Generate a matrix of consecutive values with the highest
	// value at the destination "to".
	//
	// Pieces of "color" or opposing bombs
	// block the destination from discovery.
	//
	// Pieces of the opposite "color" do not
	// block the destination.
	//
	// Seed the matrix with "n" at "to".
	//
	// This matrix is used to lead pieces to desired
	// destinations.
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
			for (int d : dir) {
				int i = j + d;
				if (!isValid(i) || destTmp[i] != -99)
					continue;
				Piece p = getPiece(i);
				if (p != null) {
					if (p.getColor() == color || p.getRank() == Rank.BOMB) {
						destTmp[i] = -98;
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
		Rank fprank = fp.getRank();
		Piece tp = getPiece(m.getTo());
		setPiece(null, m.getFrom());
		fp.moves++;

		if (tp == null) { // move to open square
			int r = fprank.toInt()-1;
			int color = fp.getColor();

			if ((movedRank[color][r] || neededRank[color][r])
				// only give nines destValue for adjacent moves
				// because they can reach their destination
				// more quickly.
				&& !unknownScoutFarMove) {
			if (color == Settings.topColor)
				value += (destValue[color][r][m.getTo()]
					- destValue[color][r][m.getFrom()]);
			else
				value -= (destValue[color][r][m.getTo()]
					- destValue[color][r][m.getFrom()]);
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

			if (!fp.isKnown() && !fp.hasMoved() )
				aiMovedValue(fp, fp.moves % 2);
			setPiece(fp, m.getTo());
			if (unknownScoutFarMove) {
				// Moving an unknown scout
				// more than one position makes it known.
				// Penalize so that a scout should only 
				// make itself known for a long voyage
				// towards a destination.
				aiMovedValue(fp, 5);
				fp.setKnown(true);
			}
			recentMoves.add(m);
		} else { // attack
			Rank tprank = tp.getRank();
			switch (winRank[fprank.toInt()][tprank.toInt()]) {
			case Rank.EVEN :
				setPiece(null, m.getTo());
				value += fp.aiValue() + tp.aiValue();
				break;

			case Rank.LOSES :
				value += fp.aiValue();
				// unknown pieces have bluffing value
				if (depth != 0 && !fp.isKnown() && !isInvincible(tp)) {
					assert fp.getColor() == Settings.topColor : "if fp is unknown then fp must be ai";
					aiBluffingValue(m.getTo());
				}
				break;

			case Rank.WINS:
				// The defending piece value is
				// gained if it is known or if it moved.
				// or if it is a bomb.
				// This encourages the
				// the ai to not move its unknown
				// pieces that are subject to loss
				// by attack.
				// Pieces that win a bomb (eights or
				// possibly unknown) always gain the value
				// even if the bomb is unknown.
				if (tp.isKnown() || tp.moves != 0 || tprank == Rank.BOMB)
					value += tp.aiValue();
				// fp loses stealth if it is not known
				aiStealthValue(fp);
				setPiece(fp, m.getTo()); // won
				break;

			case Rank.UNK:
			// fp or tp is unknown
			if (tp.getColor() == Settings.topColor) {
				// ai is defender (tp)
				assert !fp.isKnown() : "opponent piece is known?";

				if (isInvincible(tp) && !(tprank == Rank.ONE && possibleSpy && fp.getActingRankLow() == Rank.NIL)) {
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
			break;
			} // switch

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
			// Unknown but moved ai pieces have some bluffing value.
			// Unknown Eights and Spys are usually so obviously
			// trying to get at bombs and Marshals
			// that they have no bluffing value.
			} else if (tp.hasMoved() && !tp.isKnown() && !isInvincible(fp) && tp.getRank() != Rank.EIGHT) {
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

	public void aiMovedValue(Piece fp, int v)
	{
		int color = fp.getColor();
		Rank rank = fp.getRank();
		if (neededRank[color][rank.toInt()-1])
			return;

		if (color == Settings.bottomColor) {
			value += v;
		} else {
			value -= v;
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

	// De Boer (2007) suggested a formula
	// for the relative values of pieces in Stratego:
	// • The Flag has the highest value of all
	// • The Spy’s value is half of the value of the Marshal
	// • If the opponent has the Spy in play,
	//	the value of the Marshal is multiplied with 0.8
	// • When the player has less than three Miners,
	//	the value of the Miners is multiplied with 4 − #left.
	// • The same holds for the Scouts
	// • Bombs have half the value of the strongest piece
	//	of the opponent on the board
	// • The value of every piece is incremented with 1/#left
	//	to increase their importance when only a few are left
	//
	// A.F.C Arts (2010) used the following value relations:
	// • First feature is multiplying the value of the Marshal
	//	(both player and opponent) with 0.8 if the
	//	opponent has a Spy on the game board.
	// • Second feature multiplies the value of the Miners with 4 − #left
	//	if the number of Miners is less than three.
	// • Third feature sets the value of the Bomb
	//	to half the value of the piece with the highest value.
	// • Fourth feature sets divides the value of the Marshal by two
	//	if the opponent has a Spy on the board.
	// • Fifth feature gives a penalty to pieces
	//	that are known to the opponent
	// • Sixth feature increases the value of a piece
	//	when the player has a more pieces of the same type
	//	than the opponent.
	//
	// Eventually the value of a piece is multiplied
	//	with the number of times that the piece is on the board,
	//	and summated over all the pieces.
	// Values are based upon the M.Sc. thesis by Stengard (2006).
	//
	// The following implementation is similar, but differs:
	// • Bombs are worthless, except if they surround a flag or
	//	block lanes.
	// • The value of the Spy becomes equal to a Seven once the
	//	opponent Marshal is removed.
	
	private static int aiValue(int color, Rank r)
	{
		int [] values = {
			0,
			400,	// 1 Marshal
			200, 	// 2 General
			100, 	// 3 Colonel
			75,	// 4 Major
			45,	// 5 Captain
			30,	// 6 Lieutenant
			25,	// 7 Sergeant
			60,	// 8 Miner
			25,	// 9 Scout
			200,	// Spy
			-100,	// Bomb
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

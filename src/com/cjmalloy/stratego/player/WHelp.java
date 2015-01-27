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

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class WHelp {

	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private JPanel jContentPane = null;
	private JScrollPane jScrollPane = null;
	private JTextArea jTextArea = null;
	
	private static WHelp me = null;
	
	private WHelp()
	{		
		//scroll to top
		getJTextArea().setSelectionStart(0);
		getJTextArea().setSelectionEnd(0);

	}
	
	public static WHelp getInstance()
	{
		if (me == null)
			me = new WHelp();
		return me;
	}

	public JFrame getJFrame() {
		if (jFrame == null) {
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			jFrame.setSize(600, 400);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("Stratego Help");
			jFrame.setIconImage(Skin.getInstance().scaledHelpIcon.getImage());
		}
		return jFrame;
	}

	private JPanel getJContentPane() {
		if (jContentPane == null) {
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.add(getJScrollPane(), BorderLayout.CENTER);
		}
		return jContentPane;
	}

	private JScrollPane getJScrollPane() {
		if (jScrollPane == null) {
			jScrollPane = new JScrollPane();
			jScrollPane.setViewportView(getJTextArea());
		}
		return jScrollPane;
	}

	private JTextArea getJTextArea() {
		if (jTextArea == null) {
			jTextArea = new JTextArea();
			jTextArea.setText("Stratego\n\n" +
					"(Pronounced Stra-Tee-Go)\n\n" +
					"A Game of Skill and Strategy for Two Players.\n\n" +
					"Stratego is the American version of the game now popular on the Continent. While the pieces have military designs and are maneuvered across the playing board, it is not a war game.\n\n" +
					"Stratego is a fast moving game, which is easy to learn, delightful to play, and which provides a never-ending variety of ways to outwit your opponent. The colorful playing pieces are marked according to military rank which are kept hidden from the opponent as they are placed and moved across the board to capture your opponent\'s \"Flag\". The rank of the piece is revealed only when an opposing piece is \"struck\" or attacked. The higher ranked piece removes the lesser rank. There are \"Bombs\" which \"blow-up\" and remove any attacking piece except the \"Miner\" who can dismantle and remove the \"Bomb\". Even the \"Marshal\", the highest ranking piece, has a weakness, in that the lowly \"Spy\" can remove him from the game.\n\n" +
					"The exciting elements of skillful planning, clever deception, memory and suspense make Stratego a thoroughly delightful game.\n\n" +
					"Rules for Stratego\n\n" +
					"The object of the game is to capture the opponent\'s flag.\n\n" +
					"To start the game:\n\n" +
					"   1. Place the board between the players so that the name Stratego is facing each contestant.\n" +
					"   2. One player takes the Red and the other the Blue playing pieces. Red starts first.\n" +
					"   3. Each player gets an army of 40 pieces, in order of rank from high to low, consisting of:\n\n" +
					"        1 Marshal\n" +
					"        1 General\n" +
					"        2 Colonels\n" +
					"        3 Majors\n" +
					"        4 Captains\n" +
					"        4 Lieutenants\n" +
					"        4 Sergeants\n" +
					"        5 Miners\n" +
					"        8 Scouts\n" +
					"        1 Spy\n\n" +
					"These are all movable pieces.\n\n" +
					"There are also 6 Bombs and 1 Flag, which are not moveable. Note that the moveable pieces have a number in the upper right corner to designate the order of rank. Thus, the Marshal is ranked 1 (highest), the General 2, the Colonels 3, and so on to the Spy who is marked with an \"S\".\n\n" +
					"   1. The players place one piece in each square of their half of the board. All squares are to be filled from each end. That is, 10 per row, 4 rows deep. The two middle rows are to be left unoccupied at the start of the game.\n" +
					"   2. The pieces are placed with the notched ends up and the printed emblem facing the player in such a way that the opponent does not know the arrangement of the pieces.\n" +
					"   3. Read the rules of \"Movement\" and \"Striking\" so that an idea of how to plan the placement of the pieces will be obtained.\n\n" +
					"Rules for Movement\n\n" +
					"   1. Turns alternate, first Red then Blue.\n" +
					"   2. A piece moves from square to square, one square at a time. (Exception: Scout, see rule 8). A piece may be moved forward, backward, or sideward but not diagonally.\n" +
					"   3. Note that there are two lakes in the center of the board, which contain no squares. Pieces must move around lakes and cannot move where there is no square.\n" +
					"   4. Two pieces may not occupy the same square at the same time.\n" +
					"   5. A piece may not move through a square occupied by a piece nor jump over a piece.\n" +
					"   6. Only one piece may be moved in each turn.\n" +
					"   7. The \"Flag\" and the \"Bomb\" pieces cannot be moved. Once these pieces are placed at the start of the game, they must remain in that square.\n" +
					"   8. The \"Scout\" may move any number of open squares forward, backward, or sideward in a straight line if the player desires. This movement, of course, then reveals to the opponent the value of that piece. Therefore, the player may choose to move the Scout only one square in his turn, so as to keep the Scout\'s identity hidden. The Scout is valuable for probing the opponent\'s positions.\n" +
					"   9. Once a piece has been moved to a square and the hand removed, it cannot be moved back to its original position in that turn.\n" +
					"  10. Pieces cannot be moved back and forth between the same 2 squares in 3 consecutive turns.\n" +
					"  11. A player must either \"move\" or \"strike\" in his turn.\n\n" +
					"Rules for \"Strike\" or Attack\n\n" +
					"   1. When a red and a blue piece occupy adjoining squares either back to back, side to side, or face to face, they are in a position to attack or \"strike\". No diagonal strikes can be made.\n" +
					"   2. A player may move in his turn or strike in his turn. He cannot do both. (Exception: Scout, see rule 13). The \"strike\" ends the turn. After pieces have finished the \"strike\" move, the player who was struck has his turn to move or strike.\n" +
					"   3. It is not required to \"strike\" when two opposing pieces are in position. A player may decide to strike, whenever he desires.\n" +
					"   4. Either player may strike (in his turn), not only the one who moves his piece into position.\n" +
					"   5. To strike (or attack), the player, whose turn it is, takes up his piece and lightly \"strikes\" the opponent\'s piece while at the same time declaring his piece\'s rank. The opponent answers by naming the rank of his piece.\n" +
					"   6. The piece with the lower rank is lost and removed from the board. The winning higher ranking piece is then moved immediately into the empty square formerly occupied by the losing piece.\n" +
					"   7. When equal ranks are struck, then both pieces are lost and removed from the board.\n" +
					"   8. A Marshal removes a General, a General removes a Colonel, and a Colonel removes a Major, and so on down to the Spy, which is the lowest ranking piece.\n" +
					"   9. The Spy, however, has the special privilege of being able to remove only the Marshal provided he strikes first. That is, if the Spy \"strikes\" the Marshal in his turn, the Marshal is removed. However, if the Marshal \"strikes\" first, the Spy is removed. All other pieces remove the Spy regardless of who strikes first.\n" +
					"  10. When any piece (except a Miner) strikes a Bomb (Bang!) that piece is lost and is removed from the board. The Bomb does not move into the empty square, but remains in its original position at all times. When a Miner strikes a Bomb, the Bomb is lost and the miner moves into the unoccupied square.\n" +
					"  11. A Bomb cannot strike, but rather must wait until a moveable piece strikes it.\n" +
					"  12. Remember, the Flag also can never be moved.\n" +
					"  13. The scout may move and attack in the same turn. It is the only piece than can do this.\n\n" +
					"To End the Game\n\n" +
					"Whenever a player \"strikes\" his opponent\'s Flag, the game ends and he is the winner. If a player cannot move a piece or \"strike\" in his turn, he must give up and declare his opponent the winner.\n\n" +
					"Some Suggestions For Strategy\n\n" +
					"From the above it is clear that the original placement of the pieces can determine the outcome. It is therefore good defensive tactics to surround the Flag with a few Bombs. However, to mislead the opponent, it is recommended to place a few Bombs at some distance from the Flag.\n\n" +
					"A few high-ranking pieces in the front lines is a good play, but the player who rapidly loses his high officers stands in a weak position.\n\n" +
					"Scouts in the front line are useful to probe the strength of the opposing pieces.\n\n" +
					"Miners are very important near the end of the game so it is good strategy to place some in the rear ranks.\n");
					jTextArea.setWrapStyleWord(true);
					jTextArea.setLineWrap(true);
		}
		return jTextArea;
	}
}

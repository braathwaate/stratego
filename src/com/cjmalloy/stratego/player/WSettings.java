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


import java.awt.Rectangle;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import com.cjmalloy.stratego.Settings;


public class WSettings {

	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private JPanel jContentPane = null;
	private JButton bClose = null;
	private JLabel lblAI = null;
	private JSlider sAILevel = null;
	private JButton bSetup = null;
	private JLabel lblMode = null;
	private JCheckBox cNoHideAll = null;
	private JLabel lblNoHideAll = null;
	private JCheckBox cShowAll = null;
	private JLabel lblShowAll = null;
	private JCheckBox cDefendAdvantage = null;
	private JLabel lblDefendAdvantage = null;
	private JCheckBox cNoMoveDefender = null;
	private JLabel lblNoMoveDefender = null;
	private JCheckBox cNoShowDefender = null;
	private JLabel lblNoShowDefender = null;
	private JCheckBox cOneTimeBombs = null;
	private JLabel lblOneTimeBombs = null;
	
	private static WSettings me = null;
	
	private WSettings(){}
	
	public static WSettings getInstance()
	{
		if (me == null)
			me = new WSettings();
		return me;
	}

	public JFrame getJFrame() {
		if (jFrame == null) {
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			jFrame.setSize(380, 440);
			jFrame.setContentPane(getJContentPane());
			jFrame.setTitle("Settings");
			jFrame.setIconImage(Skin.getInstance().noicon);
			jFrame.setResizable(false);
		}
		return jFrame;
	}

	private JPanel getJContentPane() {
		if (jContentPane == null) {
			lblOneTimeBombs = new JLabel();
			lblOneTimeBombs.setBounds(new Rectangle(45, 318, 400, 16));
			lblOneTimeBombs.setText("Bombs removed after exploding");
			lblNoShowDefender = new JLabel();
			lblNoShowDefender.setBounds(new Rectangle(70, 228, 400, 16));
			lblNoShowDefender.setText("Don't reveal the defender (unless scouted)");
			lblNoMoveDefender = new JLabel();
			lblNoMoveDefender.setBounds(new Rectangle(45, 258, 400, 16));
			lblNoMoveDefender.setText("Defender doesn't not move");
			lblDefendAdvantage = new JLabel();
			lblDefendAdvantage.setBounds(new Rectangle(45, 288, 400, 16));
			lblDefendAdvantage.setText("Defender's advantage");
			lblShowAll = new JLabel();
			lblShowAll.setBounds(new Rectangle(45, 168, 400, 16));
			lblShowAll.setText("Show all pieces");
			lblNoHideAll = new JLabel();
			lblNoHideAll.setBounds(new Rectangle(70, 198, 400, 16));
			lblNoHideAll.setText("Always show revealed pieces");
			lblMode = new JLabel();
			lblMode.setBounds(new Rectangle(18, 126, 400, 23));
			lblMode.setText("Game Options:");
			lblAI = new JLabel();
			lblAI.setBounds(new Rectangle(15, 23, 400, 27));
			lblAI.setText(" AI Difficulty:");
			jContentPane = new JPanel();
			jContentPane.setLayout(null);
			jContentPane.add(lblAI, null);
			jContentPane.add(getSAILevel(), null);
			jContentPane.add(getBClose(), null);
			jContentPane.add(lblMode, null);
			jContentPane.add(getCNoHideAll(), null);
			jContentPane.add(lblNoHideAll, null);
			jContentPane.add(getCShowAll(), null);
			jContentPane.add(lblShowAll, null);
			jContentPane.add(getCDefendAdvantage(), null);
			jContentPane.add(lblDefendAdvantage, null);
			jContentPane.add(getCNoMoveDefender(), null);
			jContentPane.add(lblNoMoveDefender, null);
			jContentPane.add(getCNoShowDefender(), null);
			jContentPane.add(lblNoShowDefender, null);
			jContentPane.add(getCOneTimeBombs(), null);
			jContentPane.add(lblOneTimeBombs, null);
			jContentPane.add(getBSetup(), null);
		}
		return jContentPane;
	}

	private JSlider getSAILevel() {
		if (sAILevel == null) {
			sAILevel = new JSlider();
			sAILevel.setBounds(new Rectangle(15, 62, 332, 30));
			sAILevel.setMinorTickSpacing(1);
			sAILevel.setMajorTickSpacing(5);
			sAILevel.setPaintTicks(true);
			sAILevel.setSnapToTicks(true);
			sAILevel.setValue(1);
			sAILevel.setMinimum(1);
			sAILevel.setMaximum(21);
			sAILevel.addChangeListener(new javax.swing.event.ChangeListener()
			{
				public void stateChanged(javax.swing.event.ChangeEvent e)
				{
					Settings.aiLevel = sAILevel.getValue();
				}
			});
		}
		return sAILevel;
	}

	private JButton getBClose() {
		if (bClose == null) {
			bClose = new JButton();
			bClose.setBounds(new Rectangle(266, 360, 78, 28));
			bClose.setText("Close");
			bClose.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					getJFrame().setVisible(false);
				}
			});
		}
		return bClose;
	}

	private JCheckBox getCNoHideAll() {
		if (cNoHideAll == null) {
			cNoHideAll = new JCheckBox();
			cNoHideAll.setBounds(new Rectangle(45, 195, 21, 21));
			cNoHideAll.doClick();
			cNoHideAll.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bNoHideAll = cNoHideAll.isSelected();
				}
			});
		}
		return cNoHideAll;
	}

	private JCheckBox getCShowAll() {
		if (cShowAll == null) {
			cShowAll = new JCheckBox();
			cShowAll.setBounds(new Rectangle(15, 165, 21, 21));
			cShowAll.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bShowAll = cShowAll.isSelected();
					cNoHideAll.setEnabled(!Settings.bShowAll);
					cNoShowDefender.setEnabled(!Settings.bShowAll);
				}
			});
		}
		return cShowAll;
	}

	private JCheckBox getCDefendAdvantage() {
		if (cDefendAdvantage == null) {
			cDefendAdvantage = new JCheckBox();
			cDefendAdvantage.setBounds(new Rectangle(15, 285, 21, 21));
			cDefendAdvantage.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bDefendAdvantage = cDefendAdvantage.isSelected();
				}
			});
		}
		return cDefendAdvantage;
	}

	private JCheckBox getCNoMoveDefender() {
		if (cNoMoveDefender == null) {
			cNoMoveDefender = new JCheckBox();
			cNoMoveDefender.setBounds(new Rectangle(15, 255, 21, 21));
			cNoMoveDefender.doClick();
			cNoMoveDefender.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bNoMoveDefender = cNoMoveDefender.isSelected();
				}
			});
		}
		return cNoMoveDefender;
	}

	private JCheckBox getCNoShowDefender() {
		if (cNoShowDefender == null) {
			cNoShowDefender = new JCheckBox();
			cNoShowDefender.setBounds(new Rectangle(45, 225, 21, 21));
			cNoShowDefender.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bNoShowDefender = cNoShowDefender.isSelected();
				}
			});
		}
		return cNoShowDefender;
	}

	private JCheckBox getCOneTimeBombs() {
		if (cOneTimeBombs == null) {
			cOneTimeBombs = new JCheckBox();
			cOneTimeBombs.setBounds(new Rectangle(15, 315, 21, 21));
			cOneTimeBombs.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					Settings.bOneTimeBombs = cOneTimeBombs.isSelected();
				}
			});
		}
		return cOneTimeBombs;
	}

	private JButton getBSetup() {
		if (bSetup == null) {
			bSetup = new JButton();
			bSetup.setText("Setup");
			bSetup.setBounds(new Rectangle(259, 98, 85, 25));
			bSetup.addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					try
					{
						WSetup.getInstance().getJFrame().setVisible(true);
					}
					catch (IOException e1)
					{
						e1.printStackTrace();
					}
				}
			});
		}
		return bSetup;
	}
}

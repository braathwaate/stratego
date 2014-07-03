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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Scanner;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class Skin
{
	private static Skin me = new Skin();
	
	private static final String SKIN_BG = "#FFFFFF";
	private static final String SKIN_G  = "#00FF00";
	private static final String SKIN_W  = "#00FFFF";
	private static final String SKIN_R  = "#FF0000";
	private static final String SKIN_B  = "#6060FF";
	private static final String SKIN_RA = "#FF8080";
	private static final String SKIN_BA = "#A0A0FF";
	private static final String SKIN_RAA = "#BB7070";
	private static final String SKIN_BAA = "#9090BB";
	public Color bgColor    = Color.decode(SKIN_BG);
	public Color mapColor   = Color.decode(SKIN_G);
	public Color waterColor = Color.decode(SKIN_W);
	public Color redColor   = Color.decode(SKIN_R);
	public Color blueColor  = Color.decode(SKIN_B);
	public Color redAColor  = Color.decode(SKIN_RA);
	public Color blueAColor = Color.decode(SKIN_BA);
	public Color redAAColor  = Color.decode(SKIN_RAA);
	public Color blueAAColor = Color.decode(SKIN_BAA);
	
	private Image splash = null;
	public Image scaledSplash = null;
	public Image icon = null;
	public Image noicon = null;
	
	private ImageIcon skins[] = new ImageIcon[13];
	public ImageIcon scaledSkins[] = new ImageIcon[13];
	public ImageIcon gridBG[][] = new ImageIcon[10][10];
	public ImageIcon redASkins[] = new ImageIcon[13];
	public ImageIcon blueASkins[] = new ImageIcon[13];
	public ImageIcon redAASkins[] = new ImageIcon[13];
	public ImageIcon blueAASkins[] = new ImageIcon[13];
	public ImageIcon redSkins[] = new ImageIcon[13];
	public ImageIcon blueSkins[] = new ImageIcon[13];
	public ImageIcon redBack = null;
	public ImageIcon blueBack = null;
	public ImageIcon redABack = null;
	public ImageIcon blueABack = null;
	public Image bg = null;
	
	private Image newGameIcon = null;
	private Image loadSkinIcon = null;
	private Image onePlayerIcon = null;
	private Image twoPlayerIcon = null;
	private Image settingsIcon = null;
	private Image helpIcon = null;
	private Image playIcon = null;
	private Image undoIcon = null;
	private Image openIcon = null;
	private Image saveIcon = null;
	private Image saveAsIcon = null;
	public ImageIcon scaledNewGameIcon = null;
	public ImageIcon scaledLoadSkinIcon = null;
	public ImageIcon scaledOnePlayerIcon = null;
	public ImageIcon scaledTwoPlayerIcon = null;
	public ImageIcon scaledSettingsIcon = null;
	public ImageIcon scaledHelpIcon = null;
	public ImageIcon scaledPlayIcon = null;
	public ImageIcon scaledUndoIcon = null;
	public ImageIcon scaledOpenIcon = null;
	public ImageIcon scaledSaveIcon = null;
	public ImageIcon scaledSaveAsIcon = null;
	
	private Skin(){}
	
	public static Skin getInstance()
	{
		return me;
	}
	
	public void loadIcon() throws IOException
	{
		icon = ImageIO.read(this.getClass().getResource("/images/icon.png"));
		noicon = ImageIO.read(this.getClass().getResource("/images/noicon.png"));
	}
	
	public void loadSplash() throws IOException
	{
		splash = ImageIO.read(this.getClass().getResource("/images/splash.jpg"));
	}
	
	public void unloadSplash()
	{
		splash = null;
	}
	
	public boolean hasBG()
	{
		return skins[0] != null;
	}
	
	public void resize(int x, int y)
	{
		//splash screen
		if (splash != null)
		{
			BufferedImage bi = new BufferedImage(splash.getWidth(null), splash.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			bi.createGraphics().drawImage(splash, 0, 0, splash.getWidth(null), splash.getHeight(null), null);
			
			AffineTransform tx = new AffineTransform();
			tx.scale((y*0.75)/splash.getHeight(null), (y*0.75)/splash.getHeight(null));
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
			scaledSplash = op.filter(bi, null);
		}
		
		//toolbar
		{
			int w = 32;
			int h = 32;
			scaledOnePlayerIcon = scale(onePlayerIcon, w, h);
			scaledTwoPlayerIcon = scale(twoPlayerIcon, w, h);
			scaledNewGameIcon = scale(newGameIcon, w, h);
			scaledLoadSkinIcon = scale(loadSkinIcon, w, h);
			scaledSettingsIcon = scale(settingsIcon, w, h);
			scaledHelpIcon = scale(helpIcon, w, h);
			scaledPlayIcon = scale(playIcon, w, h);
			scaledUndoIcon = scale(undoIcon, w, h);
			scaledOpenIcon = scale(openIcon, w, h);
			scaledSaveIcon = scale(saveIcon, w, h);
			scaledSaveAsIcon = scale(saveAsIcon, w, h);
		}
		
		//background
		if (skins[0] != null)
		{
			Image img = skins[0].getImage();
			int w = img.getWidth(null) / 10;
			int h = img.getHeight(null) / 10;

			AffineTransform tx = new AffineTransform();
			tx.scale((x/18.0)/w, (y/10.0)/h);
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
			
			for (int i=0;i<10;i++)
			for (int j=0;j<10;j++)
			{
				BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = bi.createGraphics();
				g.drawImage(img, 0, 0, w, w, i*w, j*h, (i+1)*w, (j+1)*h, null);
				gridBG[i][j] = new ImageIcon(op.filter(bi, null));
				g.dispose();
			}
		}
		else
		{
			for (int i=0;i<10;i++)
			for (int j=0;j<10;j++)
			{
				gridBG[i][j] = null;
			}
		}
		
		//backs
		for (int i=1;i<13;i++)
		{
			if (skins[i] != null)
			{			
				Image img = skins[i].getImage();
				BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = bi.createGraphics();
	
				AffineTransform tx = new AffineTransform();
				tx.scale(0.8*(x/18.0)/img.getWidth(null), 0.8*(y/10.0)/img.getHeight(null));
				AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
				
				g.setBackground(redColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				redBack = new ImageIcon(op.filter(bi, null));
				
				g.setBackground(blueColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				blueBack = new ImageIcon(op.filter(bi, null));

				g.setBackground(redAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				redABack = new ImageIcon(op.filter(bi, null));
				
				g.setBackground(blueAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				blueABack = new ImageIcon(op.filter(bi, null));

				g.dispose();
				break;
			}
			else
			{
				redBack = null;
				blueBack = null;
			}
		}
		
		//pieces
		for (int i=1;i<13;i++)
		{
			if (skins[i] != null)
			{


				Image img = skins[i].getImage();
				BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = bi.createGraphics();

				AffineTransform tx = new AffineTransform();
				tx.scale((x/18.0)/img.getWidth(null), (y/10.0)/img.getHeight(null));
				AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);

				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				scaledSkins[i] = new ImageIcon(op.filter(bi, null));


				tx = new AffineTransform();
				tx.scale(0.8*(x/18.0)/img.getWidth(null), 0.8*(y/10.0)/img.getHeight(null));
				op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);

				g.setBackground(redColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				redSkins[i] = new ImageIcon(op.filter(bi, null));

				g.setBackground(blueColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				blueSkins[i] = new ImageIcon(op.filter(bi, null));

				g.setBackground(redAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				redASkins[i] = new ImageIcon(op.filter(bi, null));

				g.setBackground(blueAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				blueASkins[i] = new ImageIcon(op.filter(bi, null));

				g.setBackground(redAAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				redAASkins[i] = new ImageIcon(op.filter(bi, null));

				g.setBackground(blueAAColor);
				g.clearRect(0, 0, img.getWidth(null), img.getHeight(null));
				g.drawImage(img, 0, 0, img.getWidth(null), img.getHeight(null), null);
				blueAASkins[i] = new ImageIcon(op.filter(bi, null));

				g.dispose();
			}
			else
			{
				scaledSkins[i] = null;
				redSkins[i] = null;
				blueSkins[i] = null;
				redASkins[i] = null;
				blueASkins[i] = null;
				redAASkins[i] = null;
				blueAASkins[i] = null;
			}
		}
	}
	

	public void loadSkin() throws Exception
	{
		File f = new File("skin.cfg");
	    if(!f.exists()) f.createNewFile();

		BufferedReader cfg = new BufferedReader(new FileReader(f));

		String fn = null;
		try
		{
			while ((fn = cfg.readLine()) != null)
			{
				if (!fn.trim().equals(""))
				{
					loadSkin(fn);
					break;
				}
			}
		}
		finally
		{			
			cfg.close();
		}
		
		if (fn == null || fn.trim().equals(""))
		{
			defaultPieces();
			
			URL in;
			in = this.getClass().getResource("/images/bg.jpg"); 
			bg = ImageIO.read(in);
			in = this.getClass().getResource("/images/grid.jpg"); 
			skins[0] = new ImageIcon(ImageIO.read(in));
		}
	}
	
	public void loadSkin(String skin) throws Exception
	{
		bg = null;
    	for (int i=0;i<13;i++)
    		skins[i] = null;
    	
		
	    ZipFile zf = new ZipFile(skin);
	    try
	    {
	    	String s = "";
	    	BufferedReader buf = new BufferedReader(new InputStreamReader(zf.getInputStream(zf.getEntry("cfg"))));
	    	Scanner cfg = new Scanner(buf).useDelimiter(";");
	    	
	    	while (cfg.hasNext())
	    	{
	    		Scanner value = new Scanner(cfg.next()).useDelimiter("=");
		    	s = value.next().trim();
		    	
				if (s.equals("1"))
					skins[1] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("2"))
					skins[2] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("3"))
					skins[3] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("4"))
					skins[4] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("5"))
					skins[5] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("6"))
					skins[6] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("7"))
					skins[7] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("8"))
					skins[8] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equals("9"))
					skins[9] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equalsIgnoreCase("spy"))
					skins[10] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equalsIgnoreCase("bomb"))
					skins[11] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equalsIgnoreCase("flag"))
					skins[12] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equalsIgnoreCase("map"))
					skins[0] = new ImageIcon(ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim()))));
				else if (s.equalsIgnoreCase("background"))
					bg = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("NewGame"))
					newGameIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("LoadSkin"))
					loadSkinIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Settings"))
					settingsIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Help"))
					helpIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("OnePlayer"))
					onePlayerIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("TwoPlayer"))
					twoPlayerIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Play"))
					playIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Undo"))
					undoIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Open"))
					openIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("Save"))
					saveIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("SaveAs"))
					saveAsIcon = ImageIO.read(zf.getInputStream(zf.getEntry(value.next().trim())));
				else if (s.equalsIgnoreCase("BackgroundColor"))
					try
					{
						bgColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						bgColor = Color.decode(SKIN_BG);
					}
				else if (s.equalsIgnoreCase("MapColor"))
					try
					{
						mapColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						mapColor = Color.decode(SKIN_G);
					}
				else if (s.equalsIgnoreCase("WaterColor"))
					try
					{
						waterColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						waterColor = Color.decode(SKIN_W);
					}
				else if (s.equalsIgnoreCase("RedColor"))
					try
					{
						redColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						redColor = Color.decode(SKIN_R);
					}
				else if (s.equalsIgnoreCase("ActiveRedColor"))
					try
					{
						redAColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						redAColor = Color.decode(SKIN_RA);
					}
				else if (s.equalsIgnoreCase("BlueColor"))
					try
					{
						blueColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						blueColor = Color.decode(SKIN_B);
					}
				else if (s.equalsIgnoreCase("ActiveBlueColor"))
					try
					{
						blueAColor = Color.decode(value.next().trim());
					}
					catch (Exception e)
					{
						blueAColor = Color.decode(SKIN_BA);
					}
	    	}
	    	
	    	buf.close();
	    }
	    finally
	    {
	    	zf.close();
	    }
	    
	    defaultPieces();
	}
	
	private void defaultPieces() throws Exception
	{
		//load defualt skin				
		URL in;
		if (skins[1] == null)
		{
			in = this.getClass().getResource("/images/1.png"); 
			skins[1] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[2] == null)
		{
			in = this.getClass().getResource("/images/2.png"); 
			skins[2] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[3] == null)
		{
			in = this.getClass().getResource("/images/3.png"); 
			skins[3] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[4] == null)
		{
			in = this.getClass().getResource("/images/4.png"); 
			skins[4] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[5] == null)
		{
			in = this.getClass().getResource("/images/5.png"); 
			skins[5] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[6] == null)
		{
			in = this.getClass().getResource("/images/6.png"); 
			skins[6] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[7] == null)
		{
			in = this.getClass().getResource("/images/7.png"); 
			skins[7] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[8] == null)
		{
			in = this.getClass().getResource("/images/8.png"); 
			skins[8] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[9] == null)
		{
			in = this.getClass().getResource("/images/9.png"); 
			skins[9] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[10] == null)
		{
			in = this.getClass().getResource("/images/s.png"); 
			skins[10] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[11] == null)
		{
			in = this.getClass().getResource("/images/b.png"); 
			skins[11] = new ImageIcon(ImageIO.read(in));
		}
		if (skins[12] == null)
		{
			in = this.getClass().getResource("/images/f.png"); 
			skins[12] = new ImageIcon(ImageIO.read(in));
		}
		if (newGameIcon == null)
		{
			in = this.getClass().getResource("/images/newgame.png"); 
			newGameIcon = ImageIO.read(in);
		}
		if (loadSkinIcon == null)
		{
			in = this.getClass().getResource("/images/skin.png"); 
			loadSkinIcon = ImageIO.read(in);
		}
		if (settingsIcon == null)
		{
			in = this.getClass().getResource("/images/settings.png"); 
			settingsIcon = ImageIO.read(in);
		}
		if (helpIcon == null)
		{
			in = this.getClass().getResource("/images/help.png"); 
			helpIcon = ImageIO.read(in);
		}
		if (onePlayerIcon == null)
		{
			in = this.getClass().getResource("/images/oneplayer.png"); 
			onePlayerIcon = ImageIO.read(in);
		}
		if (twoPlayerIcon == null)
		{
			in = this.getClass().getResource("/images/twoplayer.png"); 
			twoPlayerIcon = ImageIO.read(in);
		}
		if (playIcon == null)
		{
			in = this.getClass().getResource("/images/play.png"); 
			playIcon = ImageIO.read(in);
		}
		if (undoIcon == null)
		{
			in = this.getClass().getResource("/images/undo.png"); 
			undoIcon = ImageIO.read(in);
		}
		if (openIcon == null)
		{
			in = this.getClass().getResource("/images/open.png"); 
			openIcon = ImageIO.read(in);
		}
		if (saveIcon == null)
		{
			in = this.getClass().getResource("/images/save.png"); 
			saveIcon = ImageIO.read(in);
		}
		if (saveAsIcon == null)
		{
			in = this.getClass().getResource("/images/saveas.png"); 
			saveAsIcon = ImageIO.read(in);
		}
	}
	
	private static ImageIcon scale(Image img, int w, int h)
	{
		AffineTransform tx = new AffineTransform();
		tx.scale(w/(double)img.getWidth(null), h/(double)img.getWidth(null));
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);

		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return new ImageIcon(op.filter(bi, null));
	}
}

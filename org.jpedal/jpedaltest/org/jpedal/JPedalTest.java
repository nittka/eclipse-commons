package org.jpedal;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import javafx.embed.swing.JFXPanel;

public class JPedalTest {

	public static void main(String[] args) {
		try {
			new JFXPanel();
			PdfDecoderFX decoder = new PdfDecoderFX();
			decoder.openPdfFile("file.pdf");
			BufferedImage image = decoder.getPageAsImage(1);
			File output=new File("test.jpg");
			ImageIO.write(image, "jpg", output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

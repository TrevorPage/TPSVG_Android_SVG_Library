package co.uk.hardfault.dragonsvg.effectsnonstandard;

import android.graphics.Bitmap;
import android.graphics.Color;

public class BitmapEffects {

	public static void addNoise(Bitmap source) {
	    int width = source.getWidth();
	    int height = source.getHeight();
		for(int y = 0; y < height; ++y) {
			for(int x = 0; x < width; ++x) {
				int colour = source.getPixel(x, y);
				source.setPixel(x, y, colour + 0xfff);
			}
		}
	}

	public static void addTelevisionLineEffect(Bitmap source) {
	    int width = source.getWidth();
	    int height = source.getHeight();
		for(int y = 0; y < height; y += 2) {
			for(int x = 0; x < width; ++x) {
				int colour = source.getPixel(x, y);			
				float[] hsbVals = new float[3];
				Color.RGBToHSV(colour >> 24, 0xff & (colour >> 16), 0xff & (colour >> 8), hsbVals);			
				if (hsbVals[2] > 10) {
					hsbVals[2] -= 10;	
				}							
				colour = Color.HSVToColor(hsbVals);
				source.setPixel(x, y, colour);			
			}
		}
	}
	
}

package co.uk.hardfault.dragonsvg.internal;

import java.util.ArrayList;

import android.graphics.Matrix;
import android.graphics.Shader;

/**
 * Class to encapsulate a gradient.
 * A Gradient object is made up in several stages: initially a Gradient is created
 * and used to store any information from attributes within the <linearGradient> or
 * <radialGradient> start tag. We may then have child elements such as <stop> which add
 * further information like stop colours to the current gradient. 
 */
public class Gradient {

	public boolean isRadial = false;
	public Shader shader;
	public Matrix matrix;
	public String id;
	public float x1;
	public float y1;
	public float x2;
	public float y2;
	public float cx;
	public float cy;
	public float radius;
	public float fx;
	public float fy;
	public String href = null;

	public ArrayList<StopColor> stopColors = new ArrayList<StopColor>(); 
		
	public void setCoordinates(float x1, float y1, float x2, float y2){
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}
	
	public static class StopColor {
		public int color;
		public float offset;
		
		public StopColor(int color, float offset) {
			this.color = color;
			this.offset = offset;
		}
	}
}
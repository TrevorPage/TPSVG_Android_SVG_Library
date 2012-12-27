package com.trevorpage.tpsvg;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Shader;

public class SVGPatternShader extends BitmapShader {
	
	public SVGPatternShader(SVGParserRenderer image, String subtreeId, 
							float viewBoxX, float viewBoxY, float viewBoxW, float viewBoxH) {
		super(createBitmap(image, subtreeId, viewBoxX, viewBoxY, viewBoxW, viewBoxH), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
	}
	
	private static Bitmap createBitmap(SVGParserRenderer image, String subtreeId, 
			float viewBoxX, float viewBoxY, float viewBoxW, float viewBoxH) {
		Bitmap bitmap = Bitmap.createBitmap((int)viewBoxW, (int)viewBoxH, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.save();
		canvas.translate(-viewBoxX, -viewBoxY);
		image.paintImage(canvas, subtreeId, 1, 1, 0, 0, null, false, true);
		canvas.restore();
		return bitmap;
	}
}

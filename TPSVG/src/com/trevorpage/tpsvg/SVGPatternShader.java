/*******************************************************************************
 * Copyright 2013 Trevor Page
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

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
		image.paintImage(canvas, subtreeId, 0, 0, 0, null, true);
		canvas.restore();
		return bitmap;
	}
}

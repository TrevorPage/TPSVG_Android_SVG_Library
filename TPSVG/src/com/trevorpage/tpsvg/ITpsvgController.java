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

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.trevorpage.tpsvg.SVGParserRenderer.Textstring;

public interface ITpsvgController {

	/**
	 * 
	 * @param id
	 * @param matrix
	 * @param style
	 * @param iteration
	 *            This parameter is zero the first time this method is called
	 *            for a given SVG element to animate. If the return value is
	 *            true to tell the parser to do a repeat iteration on this path,
	 *            this number is incremented each time.
	 * 
	 * @return Returning true indicates that the callback method should be
	 *         immediately called back again. Doing this will cause another
	 *         iteration of the same path to be drawn. This can be done as many
	 *         times as required and it allows a single path to be drawn
	 *         multiple times, with different styling and / or transformation on
	 *         each iteration. A good example use is where the SVG file defines
	 *         the graphics for a gauge, which includes just a single tick mark.
	 *         At run-time, the tick mark can be replicated and placed at the
	 *         required degree angles programmatically, thus allowing the scale
	 *         of the gauge to be manipulated at run-time.
	 */
	boolean animElement(String id, int iteration, Path path, Matrix matrix,
			Paint sroke, Paint fill);

	/**
	 * Inform the controller of remainder width or height.
	 * 
	 * @param remainderWidth
	 * @param remainderHeight
	 */
	void setRemainderWidthOrHeight(float remainderWidth, float remainderHeight);

	/**
	 * 
	 * @param id
	 * @param iteration
	 * @param matrix
	 * @param style
	 * @param text
	 * @return
	 */

	boolean animTextElement(String id, int iteration, Matrix matrix,
			Paint strokePaint, Paint fillPaint, Textstring text, float x,
			float y);

	boolean arcParams(String id, Path path, float startAngle, float sweepAngle,
			RectF bounds);

	void setSourceDocumentWidth(int width);

	void setSourceDocumentHeight(int height);

	void onSVGPrivateData(String key, String value);

}

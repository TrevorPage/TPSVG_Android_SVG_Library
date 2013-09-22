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
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class SVGDrawable extends Drawable {
	private SVGParserRenderer mRenderer;
	private final float mIntrinsicHeight;
	private final float mIntrinsicWidth;
	private Bitmap mCacheBitmap = null;

	/**
	 * Create a new SVGDrawable using the supplied renderer. This drawable's intrinsic width
	 * and height will be the width and height specified in the SVG image document. 
	 * @param renderer
	 */
	public SVGDrawable(SVGParserRenderer renderer) {
		mRenderer = renderer;
		mIntrinsicWidth = mRenderer.getDocumentWidth();
		mIntrinsicHeight = mRenderer.getDocumentHeight();
	}

	/**
	 * Create a new SVGDrawable using the supplied renderer. This drawable's intrinsic width
	 * and height will be according to the values supplied, rather than taken from the 
	 * SVG image document.  
	 * @param renderer
	 * @param intrinsicWidth
	 * @param intrinsicHeight
	 */
	public SVGDrawable(SVGParserRenderer renderer, int intrinsicWidth, int intrinsicHeight) {
		mRenderer = renderer;
		mIntrinsicWidth = intrinsicWidth;
		mIntrinsicHeight = intrinsicHeight;
	}
	
	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		int height = bounds.height();
		int width = bounds.width();

		float scaleX = (float) width / mRenderer.getDocumentWidth();
		float scaleY = (float) height / mRenderer.getDocumentHeight();
		
		if (mCacheBitmap == null) {
			mCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas cacheCanvas = new Canvas(mCacheBitmap);
			cacheCanvas.scale(scaleX, scaleY);
			mRenderer.paintImage(cacheCanvas, null, 0, 0, 0, null, false);
		}
			
		canvas.drawBitmap(mCacheBitmap, 0, 0, null);		
	}

	@Override
	public void setBounds (int left, int top, int right, int bottom) {
		super.setBounds(left, top, right, bottom);
		mCacheBitmap = null;
	}
	
	@Override
	public void setBounds (Rect bounds) {
		super.setBounds(bounds);
		mCacheBitmap = null;
	}
	
	@Override
	public int getIntrinsicHeight() {
		return (int)mIntrinsicHeight;
	}

	@Override
	public int getIntrinsicWidth() {
		return (int)mIntrinsicWidth;
	}

	@Override
	public int getOpacity() {
		return 255;
	}

	@Override
	public void setAlpha(int alpha) {
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
	}

}

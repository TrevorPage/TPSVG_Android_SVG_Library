package com.trevorpage.tpsvg;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class SVGDrawable extends Drawable {
	public SVGParserRenderer mRenderer;

	public SVGDrawable(SVGParserRenderer renderer) {
		mRenderer = renderer;
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();

		int height = bounds.height();
		int width = bounds.width();

		float documentHeight = mRenderer.getDocumentHeight();
		float documentWidth = mRenderer.getDocumentWidth();

		float scaleX = width / documentWidth;
		float scaleY = height / documentHeight;

		canvas.save();
		canvas.scale(scaleX, scaleY);
		mRenderer.paintImage(canvas, null, 0, 0, 0, null, false);
		canvas.restore();

	}

	@Override
	public int getIntrinsicHeight() {
		return mRenderer.getDocumentHeight();
	}

	@Override
	public int getIntrinsicWidth() {
		return mRenderer.getDocumentWidth();
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

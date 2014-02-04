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
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;

public class SVGDrawable extends Drawable {
	private static final int DEFAULT_PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG
			| Paint.DITHER_FLAG;
	private SVGParserRenderer mRenderer;
	// private final float mIntrinsicHeight;
	// private final float mIntrinsicWidth;
	// private Bitmap mCacheBitmap = null;

	private Bitmap mBitmap;

	private int mGravity = Gravity.FILL;
	private Paint mPaint = new Paint(DEFAULT_PAINT_FLAGS);

	// These are scaled to match the target density.
	private int mWidth = -1;
	private int mHeight = -1;
	private float mOriginalWidth = -1;
	private float mOriginalHeight = -1;
	private double mOriginalAspectRatio = 1.0;
	private final Rect mDstRect = new Rect(); // Gravity.apply() sets this
	private boolean mApplyGravity;
	private ImageView.ScaleType mScaleType = ImageView.ScaleType.FIT_XY;

	/**
	 * Create a new SVGDrawable using the supplied renderer. This drawable's
	 * intrinsic width and height will be the width and height specified in the
	 * SVG image document.
	 * 
	 * @param renderer
	 */
	public SVGDrawable(SVGParserRenderer renderer) {
		mRenderer = renderer;
		mOriginalWidth = mWidth = mRenderer.getDocumentWidth();
		mOriginalHeight = mHeight = mRenderer.getDocumentHeight();
		mOriginalAspectRatio = ((double) mOriginalWidth) / ((double) mOriginalHeight);
	}

	/**
	 * Create a new SVGDrawable using the supplied renderer. This drawable's
	 * intrinsic width and height will be according to the values supplied,
	 * rather than taken from the SVG image document.
	 * 
	 * @param renderer
	 * @param intrinsicWidth
	 * @param intrinsicHeight
	 */
	public SVGDrawable(SVGParserRenderer renderer, int intrinsicWidth,
			int intrinsicHeight) {
		mRenderer = renderer;
		// mIntrinsicWidth = intrinsicWidth;
		// mIntrinsicHeight = intrinsicHeight;
		mOriginalWidth = mWidth = intrinsicWidth;
		mOriginalHeight = mHeight = intrinsicHeight;
		mOriginalAspectRatio = ((double) mOriginalWidth) / ((double) mOriginalHeight);
	}

	public final Paint getPaint() {
		return mPaint;
	}

	public final Bitmap getBitmap() {
		return mBitmap;
	}

	/**
	 * Get the gravity used to position/stretch the bitmap within its bounds.
	 * See android.view.Gravity
	 * 
	 * @return the gravity applied to the bitmap
	 */
	public int getGravity() {
		return mGravity;
	}

	/**
	 * Set the gravity used to position/stretch the bitmap within its bounds.
	 * See android.view.Gravity
	 * 
	 * @param gravity
	 *            the gravity
	 */
	public void setGravity(int gravity) {
		mApplyGravity = (mGravity != gravity);
		mGravity = gravity;
	}

	public void setAntiAlias(boolean aa) {
	}

	@Override
	public void setFilterBitmap(boolean filter) {
		mPaint.setFilterBitmap(filter);
	}

	@Override
	public void setDither(boolean dither) {
		mPaint.setDither(dither);
	}

	public void setScaleType(ImageView.ScaleType scaleType) {
		mScaleType = scaleType;
		invalidateSelf();
	}

	/*
	 * This is essential for correct transformation of SVG image It is called
	 * from SvgImageView.onSizeChanged , .setImageDrawable, .setScaleType Actual
	 * processing of scaleType is in private void ImageView.configureBounds()
	 * But this procedure is called before that. So we can adopt our width and
	 * height respectively to scaleType
	 */

	public void adjustToParentSize(int vWidth, int vHeight) {
		if (vWidth <= 0 || vHeight <= 0) {
			return;
		}
		// Reload original width and height
		mWidth = (int) mOriginalWidth;
		mHeight = (int) mOriginalHeight;

		switch (mScaleType) {
		case FIT_XY:
			mWidth = vWidth;
			mHeight = vHeight;
			break;
		case CENTER: // No scaling
			break;
		case CENTER_CROP: // Scale so that both width and height of the image
							// will be equal to or larger than view
			if (mWidth * vHeight > vWidth * mHeight) {
				mHeight = vHeight;
				mWidth = (int) (((double) mHeight) * mOriginalAspectRatio);
			} else {
				mWidth = vWidth;
				mHeight = (int) (((double) mWidth) / mOriginalAspectRatio);
			}
			break;
		case CENTER_INSIDE: // No scale if image fits, otherwise - scale to fit
							// whole image
			if (mWidth <= vWidth && mHeight <= vHeight) {
				break;
			}
			if (vWidth < vHeight) {
				mWidth = vWidth;
				mHeight = (int) (((double) mWidth) / mOriginalAspectRatio);
			} else {
				mHeight = vHeight;
				mWidth = (int) (((double) mHeight) * mOriginalAspectRatio);
			}
			break;
		case FIT_CENTER: // Scale so that both width and height of the image
							// will be equal to or lesser than view
		case FIT_START: // Scale like FIT_CENTER but place bitmap in top left
						// corner
		case FIT_END: // Scale like FIT_CENTER but place bitmap in bottom right
						// corner
		default:
			if (vWidth < vHeight) {
				mWidth = vWidth;
				mHeight = (int) (((double) mWidth) / mOriginalAspectRatio);
			} else {
				mHeight = vHeight;
				mWidth = (int) (((double) mHeight) * mOriginalAspectRatio);
			}
			break;
		}
	}

	/*
	 * When bounds do change we should render the Bitmap from SVG image You
	 * should notice that at this moment we have only two cases for mScaleType:
	 * 1. when we should render image disturbing original aspect ratio 2. when
	 * we should render image with respect of original aspect ration All other
	 * processing of scaleType was done before in private void
	 * ImageView.configureBounds()
	 */

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
			return;
		}
		mApplyGravity = true;

		// Create the bitmap to raster svg to
		Canvas canvas = new Canvas();
		mBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(),
				Bitmap.Config.ARGB_8888);
		canvas.setBitmap(mBitmap);
		switch (mScaleType) {
		case FIT_XY:
			float scaleX = bounds.width() / mOriginalWidth;
			float scaleY = bounds.height() / mOriginalHeight;
			canvas.scale(scaleX, scaleY);
			mRenderer.paintImage(canvas, null, 0, 0, 0, null, false);
			break;
		case CENTER: // No scaling
		case CENTER_INSIDE: // No scale if image fits, otherwise - scale to fit
							// whole image
		case CENTER_CROP: // Scale so that both width and height of the image
							// will be equal to or larger than view
		case FIT_CENTER: // Scale so that both width and height of the image
							// will be equal to or lesser than view
		case FIT_START: // Scale like FIT_CENTER but place bitmap in top left
						// corner
		case FIT_END: // Scale like FIT_CENTER but place bitmap in bottom right
						// corner
		default:
			mRenderer.paintImage(canvas, null, bounds.width(), bounds.height(), null, true);
		
			break;
		}
	}

	@Override
	public void draw(Canvas canvas) {
		if (mApplyGravity) {
			Gravity.apply(mGravity, mWidth, mHeight, getBounds(), mDstRect);
			mApplyGravity = false;
		}
		if (mBitmap == null) {
			return;
		}
		canvas.drawBitmap(mBitmap, null, mDstRect, mPaint);

	}

	@Override
	public void setAlpha(int alpha) {
		mPaint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}

	@Override
	public int getIntrinsicWidth() {
		return mWidth;
	}

	@Override
	public int getIntrinsicHeight() {
		return mHeight;
	}

	@Override
	public int getOpacity() {
		return (mBitmap == null || mBitmap.hasAlpha() || mPaint.getAlpha() < 255) ? PixelFormat.TRANSLUCENT
				: PixelFormat.OPAQUE;
	}

	@Override
	public void finalize() throws Throwable {
		super.finalize();
	}
}

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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SVGView extends View {

	@SuppressWarnings("unused")
	private static final String LOGTAG = SVGView.class.getSimpleName();
	private Paint mDrawPaint = new Paint();
	private SVGParserRenderer mSvgImage;
	private ITpsvgController mController;
	Bitmap mRenderBitmap = null;
	boolean mEntireRedrawNeeded = false;
	String subtree = null;
	private int mRotation = 0;
	Canvas mCanvas;
	private boolean mFill = false;

	// Tried using WeakReference<Bitmap> to avoid View-Bitmap memory leak
	// issues, but this seems
	// to lead to very frequent GC of the bitmaps, leading to terrible
	// performance penalty.
	// WeakReference<Bitmap> bm;

	public SVGView(Context context) {
		super(context);
		init(context);
	}

	public SVGView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public SVGView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	/**
	 * Set fill mode.
	 * 
	 * @param fill
	 *            fill mode
	 */
	public void setFill(boolean fill) {
		mFill = fill;
	}

	/**
	 * Get fill mode.
	 * 
	 * @return fill mode
	 */
	public boolean getFill() {
		return mFill;
	}

	/**
	 * Set the orientation value in degrees to be applied during SVG rendering.
	 * The supplied orientation should be only a multiple of 90 degrees (0, 90,
	 * 180, or 270). This is implemented despite existing View
	 * getRotation/setRotation methods because at present the SVG renderer
	 * automatically sets the rotation pivot point and, importantly, also
	 * correctly manipulates the remainder width / remainder height so that
	 * special anchor and stretch attributes still work, but can only presently
	 * do this at 90 degree multiples. At a later stage it might be adapted to
	 * cope with all rotations and therefore the existing View float rotation
	 * methods can be used.
	 * 
	 * @param rotation
	 *            in degrees
	 */
	public void setOrientation(int rotation) {
		mRotation = rotation;
	}

	/**
	 * Get the orientation value in degrees. Refer to {@link setOrientation}.
	 * 
	 * @return rotation in degrees
	 */
	public int getOrientation() {
		return mRotation;
	}

	// ------------- Initial canvas size setup and scaling ---------------------

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int chosenWidth;
		int chosenHeight;

		if (heightMode == MeasureSpec.AT_MOST
				&& widthMode == MeasureSpec.EXACTLY) {
			// Usually the case if height is MATCH_PARENT and the width is
			// WRAP_CONTENT
			chosenWidth = widthSize;
			chosenHeight = (int) (mSvgImage.getDocumentHeight() * ((float) widthSize / (float) mSvgImage
					.getDocumentWidth()));
		}

		else if (heightMode == MeasureSpec.EXACTLY
				&& widthMode == MeasureSpec.AT_MOST) {
			// Usually the case if width is MATCH_PARENT and the height is
			// WRAP_CONTENT
			chosenHeight = heightSize;
			chosenWidth = (int) (mSvgImage.getDocumentWidth() * ((float) heightSize / (float) mSvgImage
					.getDocumentHeight()));
		}

		else if (heightMode == MeasureSpec.AT_MOST
				&& widthMode == MeasureSpec.AT_MOST) {
			float uniformScaleFactor;
			uniformScaleFactor = Math.min(
					(float) widthSize / (float) mSvgImage.getDocumentWidth(),
					(float) heightSize / (float) mSvgImage.getDocumentHeight());
			chosenHeight = (int) (mSvgImage.getDocumentWidth() * uniformScaleFactor);
			chosenWidth = (int) (mSvgImage.getDocumentHeight() * uniformScaleFactor);
		}

		else if ((heightMode == MeasureSpec.UNSPECIFIED) != (widthMode == MeasureSpec.UNSPECIFIED)) {
			// One of them is UNSPECIFIED and the other is either AT_MOST or
			// EXACTLY
			if (heightMode == MeasureSpec.UNSPECIFIED) {
				chosenWidth = widthSize;
				chosenHeight = (int) (mSvgImage.getDocumentHeight() * ((float) widthSize / (float) mSvgImage
						.getDocumentWidth()));
			} else {
				chosenHeight = heightSize;
				chosenWidth = (int) (mSvgImage.getDocumentWidth() * ((float) heightSize / (float) mSvgImage
						.getDocumentHeight()));
			}
		}

		else {
			chosenWidth = chooseDimension(widthMode, widthSize,
					mSvgImage.getDocumentWidth());
			chosenHeight = chooseDimension(heightMode, heightSize,
					mSvgImage.getDocumentHeight());
		}

		setMeasuredDimension(chosenWidth, chosenHeight);
	}

	private int chooseDimension(int mode, int size, int documentSize) {
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
			return size;
		} else { // (mode == MeasureSpec.UNSPECIFIED)
			return documentSize;
		}
	}

	// in case there is no size specified
	private int getPreferredSize() {
		return 270;
	}

	private void init(Context context) {
		setDrawingCacheEnabled(false);
		mDrawPaint.setAntiAlias(false);
		mDrawPaint.setFilterBitmap(true);
		mDrawPaint.setDither(false);
		mRotation = context.getResources().getConfiguration().orientation;
	}

	public void setSVGRenderer(SVGParserRenderer image, String subtreeTagName) {
		mSvgImage = image;
		setSubtree(subtreeTagName);
	}

	public void bindController(ITpsvgController controller) {
		if (mSvgImage == null) {
			throw new IllegalStateException(
					"The parsed SVG image object needs to be specified first.");
		}
		mController = controller;
		// TODO: This is potentially going to be done multiple times, once for
		// each child SVGView of the
		// widget. I question at the moment if / why the controller should be
		// bound to the individual SVGViews
		// and not directly to the SVGParserRenderer.
		mController.setSourceDocumentHeight(mSvgImage.getDocumentHeight());
		mController.setSourceDocumentWidth(mSvgImage.getDocumentWidth());
		mSvgImage.obtainSVGPrivateData(mController);
	}

	/**
	 * Specify the particular subtree (or 'node') of the original SVG XML file
	 * that this view shall render. The default is null, which results in the
	 * entire SVG image being rendered.
	 * 
	 * @param nodeId
	 */
	public void setSubtree(String subtreeId) {
		subtree = subtreeId;
	}

	@Override
	protected void onDraw(Canvas canvas) {

		if (mRenderBitmap == null) {
			mRenderBitmap = Bitmap.createBitmap(getMeasuredWidth(),
					getMeasuredHeight(), Bitmap.Config.ARGB_8888);
			mEntireRedrawNeeded = true;
			mCanvas = new Canvas(mRenderBitmap);
		}

		if (mEntireRedrawNeeded) {
			mEntireRedrawNeeded = false;
			mRenderBitmap.eraseColor(android.graphics.Color.TRANSPARENT);
			Canvas c = new Canvas(mRenderBitmap);
			mSvgImage.paintImage(c, subtree, getWidth(), getHeight(),
					mController, mFill, mRotation);
		}

		canvas.drawBitmap(mRenderBitmap, 0f, 0f, mDrawPaint);
	}

	/**
	 * This could be called from non-UI thread.
	 */
	public void invalidateBitmap() {
		mEntireRedrawNeeded = true;
		super.postInvalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		mRenderBitmap = null;
		super.onSizeChanged(w, h, oldw, oldh);
	}

}

package com.trevorpage.tpsvg;

//import com.trevp.msDroid.SVGFlyweightFactory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SVGView extends View  {
	
	private static final String LOGTAG = SVGView.class.getSimpleName();

	Paint drawPaint = new Paint();

	// ----------------------------------------------------------------------
		
	Tpsvg svgImage;

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

	
	// ------------- Initial canvas size setup and scaling ---------------------

	@Override
	protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec){

		
		
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		
		int chosenWidth = chooseDimension(widthMode, widthSize);
		int chosenHeight = chooseDimension(heightMode, heightSize);

		// keeps the width equal to the height regardless of the MeasureSpec
		// restrictions. 
		int chosenDimension = Math.min(chosenWidth, chosenHeight);
		
		setMeasuredDimension(chosenDimension, chosenDimension);
	
		
//		fsetMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec) );
	}
	
	
	private int chooseDimension(int mode, int size) {
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
			return size;
		} else { // (mode == MeasureSpec.UNSPECIFIED)
			return getPreferredSize();
		} 
	}
	
	// in case there is no size specified
	private int getPreferredSize() {
		return 300;
	}	

	
	
	SVGFlyweightFactory svgFactory;
	
	// ------------------ Initialisation on construction -----------------------
	
	private void init(Context context){
	

		//this.svgConverter = new Tpsvg();
		//svgConverter.setAnimHandler(this);   // ???? HOW ????	
		//svgConverter.parseImageFile( this.getContext(), R.raw.gaugetest20);		
		
		svgFactory = SVGFlyweightFactory.getInstance();
		
		setDrawingCacheEnabled(false);
		
		//mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		//mGestureDetector = new GestureDetector(context, new CustomOnGestureListener());
	
		
		//appState = (AppState)context.getApplicationContext();
	
		//mHandler.postDelayed(mUpdateTimeTask, 1000);
		
		
		drawPaint.setAntiAlias(false);
		drawPaint.setFilterBitmap(false);
		drawPaint.setDither(false);		
		

	}
		
	
	/**
	 * 
	 * @param id
	 */
	
	ItpsvgAnim animHandler;
	
	public void setImageResource(int id, ItpsvgAnim animHandler){
		svgImage = svgFactory.get(id, getContext(), animHandler);
		this.animHandler = animHandler;
	}
	
	
	
	// --------------------------------------------------------------------------
	// Tried using WeakReference<Bitmap> to avoid View-Bitmap memory leak issues, but this seems
	// to lead to very frequent GC of the bitmaps, leading to terrible performance penalty. 
	
	Bitmap bm = null;
	//WeakReference<Bitmap> bm;
	boolean invalidateBitmap = false;
	String subtree = null;
	
	/**
	 * Specify the particular subtree (or 'node') of the original SVG XML file that this view 
	 * shall render. The default is null, which results in the entire SVG image being rendered.
	 * @param nodeId
	 */
	public void setSubtree(String subtreeId){
		subtree = subtreeId;
	}
	

	@Override
	protected void onDraw(Canvas canvas){

		if(invalidateBitmap){
			bm = null;
			invalidateBitmap = false;
		}
		
	    //canvas.save(Canvas.MATRIX_SAVE_FLAG);
	  		
	    if(bm == null){
	    	/*
	    	// WeakReference way (potentially solves memory leak issues, but Bitmaps are GC'd way
	    	// too often leading to poor performance)
			bm = new WeakReference<Bitmap>(Bitmap.createBitmap(
					getMeasuredWidth(),
					getMeasuredHeight(),
					Bitmap.Config.ARGB_8888));
			svgImage.paintImage(new Canvas(bm.get()), subtree, this, animHandler );
			*/
	    	// Non-WeakReference way
	    	bm = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);  // ARGB_8888
	    	svgImage.paintImage(new Canvas(bm), subtree, this, animHandler );
		}	
	    /*
	    else if(bm.get()==null){
	    	// If .get() gives null then the bitmap has potentially been garbage collected. 
			bm = new WeakReference<Bitmap>(Bitmap.createBitmap(
					getMeasuredWidth(),
					getMeasuredHeight(),
					Bitmap.Config.ARGB_8888));
			svgImage.paintImage(new Canvas(bm.get()), subtree, this, animHandler );
		}		
	    */
	    
	    //if(invalidateBitmap){
	    //	
	    //	svgImage.paintImage(new Canvas(bm.get()), subtree, this, animHandler);
	    //	invalidateBitmap = false;
	    //}
		//Log.d(LOGTAG, "Drawing bitmap for: " + this.toString());
		canvas.drawBitmap(bm, 0f, 0f, drawPaint );
	    //canvas.restore();
		
		
		// test outline border
		/*
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(Color.YELLOW);
		paint.setStrokeWidth(0.5f);
		paint.setAntiAlias(true);
		canvas.drawRect(this.getLeft(), this.getTop(), getLeft()+getWidth(), getTop()+getWidth(), paint);
	*/
	}

	
	@Override
	public void invalidate(){
		//invalidateBitmap = true;
		super.invalidate();
	}
	
	/**
	 * This could be called from non-UI thread.
	 */
	public void invalidateBitmap(){
		invalidateBitmap = true;
		//super.invalidate();
		//proposed change with new wait/notify strategy
		super.postInvalidate();
	}
	

}

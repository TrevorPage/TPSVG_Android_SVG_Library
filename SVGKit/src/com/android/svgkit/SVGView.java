package com.android.svgkit;

//import com.trevp.msDroid.SVGFlyweightFactory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class SVGView extends ImageView {
	private static final String LOGTAG = SVGView.class.getSimpleName();

    SVGFlyweightFactory svgFactory;
	Paint drawPaint = new Paint();
    TPSVG svgImage;

    ITPSVGAnim animHandler;

    Bitmap bm = null;
    boolean invalidateBitmap = false;
    String subtree = null;

	public SVGView(Context context) {
		super(context);
	}

	public SVGView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SVGView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        svgFactory = SVGFlyweightFactory.getInstance();

        setDrawingCacheEnabled(false);

        drawPaint.setAntiAlias(false);
        drawPaint.setFilterBitmap(false);
        drawPaint.setDither(false);
    }

    @Override
	protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec){
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int chosenDimension = Math.min(widthSize, heightSize);

        Log.d(LOGTAG, "onMeasure: " + widthSize + ", " + heightSize);

        if(svgImage != null){
            Log.d(LOGTAG, "Document Size: " + svgImage.getDocumentWidth() + ", " + svgImage.getDocumentHeight());
        }
		
		setMeasuredDimension(widthSize, heightSize);
	}

    @Override
    public void setImageResource(int id){
        setImageResource(id, null);
    }

	public void setImageResource(int id, ITPSVGAnim animHandler){
        //TODO: Use Picture and PictureDrawable
		svgImage = svgFactory.get(id, getContext(), animHandler);
		this.animHandler = animHandler;
	}
	
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
	  		
	    if(bm == null){
	    	bm = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);  // ARGB_8888
	    	svgImage.paintImage(new Canvas(bm), subtree, this, animHandler );
		}

        canvas.drawBitmap(bm, 0f, 0f, drawPaint );
	}
}

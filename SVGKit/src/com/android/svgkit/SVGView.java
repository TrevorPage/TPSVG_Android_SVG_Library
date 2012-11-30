package com.android.svgkit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SVGView extends View {
    SVGKit svgKit;
	Paint drawPaint = new Paint();
    SVG svgImage;

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

        svgKit = SVGKit.getInstance();

        setDrawingCacheEnabled(false);

        drawPaint.setAntiAlias(false);
        drawPaint.setFilterBitmap(false);
        drawPaint.setDither(false);
    }

    @Override
	protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec){
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if(svgImage != null){
            int documentWidth = svgImage.getDocumentWidth();
            int documentHeight = svgImage.getDocumentHeight();

            float widthScale = (float)widthSize/(float)documentWidth;
            float heightScale = (float)heightSize/(float)documentHeight;

            float choosenScale = Math.min(widthScale, heightScale);

            widthSize = (int)(widthSize * choosenScale / widthScale);
            heightSize = (int)(heightSize * choosenScale / heightScale);
        }

        setMeasuredDimension(widthSize, heightSize);
	}

    public void setImageResource(int id){
        setImageResource(id, null);
    }

	public void setImageResource(int id, ITPSVGAnim animHandler){
		svgImage = svgKit.get(id, getContext(), animHandler);
		this.animHandler = animHandler;
	}
	
	/**
	 * Specify the particular subtree (or 'node') of the original SVG XML file that this view 
	 * shall render. The default is null, which results in the entire SVG image being rendered.
	 * @param subtreeId
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

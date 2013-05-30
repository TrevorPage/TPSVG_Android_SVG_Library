package co.uk.hardfault.dragonsvg.dom;

import android.graphics.Canvas;
import android.graphics.Paint;
import co.uk.hardfault.dragonsvg.internal.SVGPath;

public class PathNode extends Node {
	
	// Instead of having the extended Path class, this Node could do
	// the extra bits instead.
	SVGPath mPath;
	
	Paint mStrokePaint;
	Paint mFillPaint;
	
	public PathNode(SVGPath path, Paint strokePaint, Paint fillPaint) {
		mPath = path;
		mStrokePaint = new Paint(strokePaint);
		mFillPaint = new Paint(fillPaint);
	}
	
	@Override
	public void draw(Canvas canvas) {
		if (mStrokePaint != null) {
			canvas.drawPath(mPath, mStrokePaint);
		}
		if (mFillPaint != null) {
			canvas.drawPath(mPath, mStrokePaint);
		}
	}

}

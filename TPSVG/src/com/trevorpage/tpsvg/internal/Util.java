package com.trevorpage.tpsvg.internal;

import android.graphics.Paint;

public class Util {

    public static float bestFitValueTextSize(float width, float height, String string) {
        Paint paint = new Paint();
        paint.setTextSize(height);
        while (paint.measureText(string) > width) {
            height -= 0.5f;
            paint.setTextSize(height);
        }
        return height;
    }	
}

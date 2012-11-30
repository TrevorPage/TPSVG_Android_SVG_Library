package com.android.svgkit.example;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import com.android.svgkit.ITPSVGAnim;
import com.android.svgkit.R;
import com.android.svgkit.SVGView;
import com.android.svgkit.TPSVG.SvgStyle;
import com.android.svgkit.TPSVG.Textstring;

public class MainActivity extends Activity implements ITPSVGAnim {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.main);
        SVGView svgView = (SVGView)this.findViewById(R.id.svgImage);
        svgView.setImageResource(R.raw.anime, this);
        svgView.setBackgroundColor(0xff123456);
    }

    
    
	public boolean animElement(String id, int iteration, Matrix matrix,
			Paint sroke, Paint fill) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean animTextElement(String id, int iteration, Matrix matrix,
			SvgStyle style, Textstring text, float x, float y) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean arcParams(String id, Path path, float startAngle,
			float sweepAngle, RectF bounds) {
		// TODO Auto-generated method stub
		return false;
	}
}
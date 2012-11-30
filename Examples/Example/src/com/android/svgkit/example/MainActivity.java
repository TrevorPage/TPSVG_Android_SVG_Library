package com.android.svgkit.example;

import android.app.Activity;
import android.os.Bundle;
import com.android.svgkit.R;
import com.android.svgkit.SVGView;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.main);
        SVGView svgView = (SVGView)this.findViewById(R.id.svgImage);
        svgView.setImageResource(R.raw.anime);
        svgView.setBackgroundColor(0xff123456);
    }
}
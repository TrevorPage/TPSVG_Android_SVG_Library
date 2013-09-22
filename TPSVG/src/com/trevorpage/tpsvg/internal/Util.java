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

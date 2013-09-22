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

import java.util.ArrayList;

import com.trevorpage.tpsvg.SVGParserRenderer.SvgStyle;

public class ParsedAttributes {
	
	public float x;
	public float y;
	
	public float x1;
	public float y1;
	public float x2;
	public float y2;
	public float cx;
	public float cy;
	public float fx;
	public float fy;
	public float rx;
	public float ry;
	
	public float radius;
	public float width;
	public float height;
	
	public String pathData;
	public String transformData;
	public String styleData;
	public String pointsData;
	public SvgStyle svgStyle;

	public String id;
	public String xlink_href;
	
	public String viewBox;
	
	public boolean anchorRight;
	public boolean anchorBottom;
	
	public boolean stretchToRemainderWidth;
	public boolean stretchToRemainderHeight;
	public String offset;
	
	public ArrayList<Integer> rotations;
	
	public float textLength;
	public boolean textLengthAdjustSize;
	
	public ParsedAttributes() {
    	transformData = null;
    	styleData = null;
    	id = "";
    	anchorRight = false;
    	anchorBottom = false;
    	stretchToRemainderWidth = false;
    	stretchToRemainderHeight = false;
    	rotations = new ArrayList<Integer>();
	}
}
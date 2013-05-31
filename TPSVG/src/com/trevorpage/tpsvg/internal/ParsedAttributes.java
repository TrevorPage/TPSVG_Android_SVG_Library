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
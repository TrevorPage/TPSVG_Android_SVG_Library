package com.android.svgkit;

import android.content.Context;

import java.util.TreeMap;


public class SVGFlyweightFactory {

	private static SVGFlyweightFactory instance = new SVGFlyweightFactory();
	
	public static synchronized SVGFlyweightFactory getInstance(){
		return instance;
	}	

	private SVGFlyweightFactory(){
		
	}
	

	TreeMap<Integer, TPSVG> images = new TreeMap<Integer, TPSVG>();
	

	
	public TPSVG get(int resourceID, Context context, ITPSVGAnim animHandler){
		
		// Autoboxing enables int->Integer
		if(images.containsKey(resourceID)){
			return images.get(resourceID);
		}
		
		TPSVG newImage = new TPSVG(context, resourceID, animHandler);
		images.put(resourceID,newImage);
		return newImage;
	}	
	
}

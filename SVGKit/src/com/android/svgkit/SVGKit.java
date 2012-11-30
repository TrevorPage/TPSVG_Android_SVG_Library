package com.android.svgkit;

import android.content.Context;

import java.util.TreeMap;


public class SVGKit {
    public static final String LOGTAG = "SVGKit";
    private static SVGKit instance = new SVGKit();
	
	public static synchronized SVGKit getInstance(){
		return instance;
	}	

	private SVGKit(){
		
	}
	

	TreeMap<Integer, SVG> images = new TreeMap<Integer, SVG>();

    public SVG get(int resourceID, Context context, ITPSVGAnim animHandler){
		if(images.containsKey(resourceID)){
			return images.get(resourceID);
		}
		
		SVG newImage = new SVG(context, resourceID, animHandler);
		images.put(resourceID,newImage);
		return newImage;
	}
}

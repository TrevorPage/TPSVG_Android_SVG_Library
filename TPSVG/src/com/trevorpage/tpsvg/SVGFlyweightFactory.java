package com.trevorpage.tpsvg;

import java.util.TreeMap;

import android.content.Context;


public class SVGFlyweightFactory {

	private static SVGFlyweightFactory instance = new SVGFlyweightFactory();
	
	public static synchronized SVGFlyweightFactory getInstance(){
		return instance;
	}	

	private SVGFlyweightFactory(){
		
	}
	

	TreeMap<Integer, Tpsvg> images = new TreeMap<Integer, Tpsvg>();	
	

	
	public Tpsvg get(int resourceID, Context context, ItpsvgAnim animHandler){
		
		// Autoboxing enables int->Integer
		if(images.containsKey(resourceID)){
			return images.get(resourceID);
		}
		
		Tpsvg newImage = new Tpsvg(context, resourceID, animHandler);
		images.put(resourceID,newImage);
		return newImage;
	}	
	
}

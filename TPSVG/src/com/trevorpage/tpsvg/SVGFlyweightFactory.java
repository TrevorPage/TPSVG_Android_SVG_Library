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

package com.trevorpage.tpsvg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.TreeMap;

import android.content.Context;

public class SVGFlyweightFactory {

	private static SVGFlyweightFactory instance = new SVGFlyweightFactory();
	
	public static synchronized SVGFlyweightFactory getInstance(){
		return instance;
	}	

	private SVGFlyweightFactory(){
		
	}

	TreeMap<String, SVGParserRenderer> images = new TreeMap<String, SVGParserRenderer>();	
	
	/**
	 * 
	 */
	public SVGParserRenderer get(int resourceID, Context context) {	
		SVGParserRenderer image;
		if (images.containsKey("resource" + resourceID)) {
			image = images.get("resource" + resourceID);
		}
		else {
			image = new SVGParserRenderer(context, resourceID); 
			images.put("resource" + resourceID, image);
		}
		return image;
	}	

	/**
	 * 
	 */
	public SVGParserRenderer get(File sourceFile, Context context) throws FileNotFoundException {
		SVGParserRenderer image;
		if (images.containsKey(sourceFile.getName())) {
			image = images.get(sourceFile.getName());
		}
		else {
			image = new SVGParserRenderer(context, sourceFile);
			images.put(sourceFile.getName(), image);
		}
		return image;		
	}

	/**
	 * 
	 */
	public SVGParserRenderer get(InputStream sourceStream, String name, Context context) throws FileNotFoundException {
		SVGParserRenderer image;
		if (images.containsKey(name)) {
			image = images.get(name);
		}
		else {
			image = new SVGParserRenderer(context, sourceStream);
			images.put(name, image);
		}
		return image;		
	}
	
}

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

import com.trevorpage.tpsvg.SVGParserRenderer;
import com.trevorpage.tpsvg.SVGPatternShader;

import android.graphics.Shader;

/**
 * Encapsulates specifics relating to a pattern fill, which is defined within the SVG file as
 * a &lt;pattern&gt; element. During parsing, this object is constructed for each &lt;pattern&gt;
 * This object is used to retain information about a pattern fill during parsing of the SVG file. 
 * A Shader object can only be generated based on this information once SVG file parsing has finished,
 * because only at that point can the parsed data be used to render the pattern tile. 
 * Similarly, if the pattern uses a xlink:href to another pattern, the href should be resolved
 * to a linked pattern after SVG parsing in case of forward references. 
 */
public class PatternFill {

	private String mSubtreeId;
	private SVGPatternShader mShader;
	private float x;
	private float y;
	private float width;
	private float height;
	private String mXLinkReferenceId;
	private PatternFill mXLinkReferencePatternFill;
	
	/**
	 * Where the pattern is defined as a series of vector paths within the SVG file,
	 * set the ID string of the element that contains all the vector data. This is the 
	 * ID of the &lt;pattern&gt; element itself.
	 * @param subtreeId
	 */
	public void setPatternSubtree(String subtreeId) {
		mSubtreeId = subtreeId;
	}
	
	/**
	 * Set the actual area that represents the valid pattern tile. 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public void setPatternViewBox(float x, float y, float width, float height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	/**
	 * Obtain the Shader representing this pattern that can be directly set to a Paint (using
	 * Paint.setShader()). Internally, an SVGPatternShader will create a bitmap tile based 
	 * on the subtree of the parsed SVG file that represents the pattern tile (assuming 
	 * it's a vector pattern).
	 * This method must be called only once parsing of the SVG file has completely finished. 
	 * @return
	 */
	public Shader createPatternShader(SVGParserRenderer svgParserRenderer) {
		if (mShader == null) {
			if (mSubtreeId != null) {
				mShader = new SVGPatternShader(svgParserRenderer, mSubtreeId, x, y, width, height);	
			}
			else if (mXLinkReferencePatternFill != null) {
				mShader = (SVGPatternShader) mXLinkReferencePatternFill.createPatternShader(svgParserRenderer);
			}
		}
		return mShader;
	}

	public void setXLinkReferenceId(String xLinkReferenceId) {
		mXLinkReferenceId = xLinkReferenceId;
	}
	
	public String getXLinkReferenceId() {
		return mXLinkReferenceId;
	}
	
	public void setXLinkReferencePatternFill(PatternFill xLinkReferencePatternFill) {
		mXLinkReferencePatternFill = xLinkReferencePatternFill;
	}

}
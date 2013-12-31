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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;

import com.trevorpage.tpsvg.internal.Gradient;
import com.trevorpage.tpsvg.internal.ParsedAttributes;
import com.trevorpage.tpsvg.internal.PatternFill;
import com.trevorpage.tpsvg.internal.SVGPath;
import com.trevorpage.tpsvg.internal.Util;

public class SVGParserRenderer extends DefaultHandler {

	private static final String LOGTAG = "SVGParserRenderer";
	private static final String ASSETS_FONTS_ROOT_DIRECTORY = "fonts";

	private static final String CUSTOM_ATTRIBUTE_NAMESPACE = "tpsvg:";
	private String mPrivateDataNamespace = "msdroid:";

	// Bytecode instruction set
	private static final byte INST_END = 0;
	private static final byte INST_PATH = 1;
	private static final byte INST_MATRIX = 2;
	private static final byte INST_BEGINGROUP = 3;
	private static final byte INST_ENDGROUP = 4;
	private static final byte INST_STYLE = 5;
	private static final byte INST_TEXTSTRING = 6;
	private static final byte INST_IDSTRING = 7;
	private static final byte INST_ARC = 8;
	private static final byte INST_BEGIN_PATTERN = 9;
	private static final byte INST_END_PATTERN = 10;

	// XML tags
	private static final String STARTTAG_SVG = "svg";
	private static final String STARTTAG_G = "g";
	private static final String STARTTAG_PATH = "path";
	private static final String STARTTAG_RECT = "rect";
	private static final String STARTTAG_LINE = "line";
	private static final String STARTTAG_POLYGON = "polygon";
	private static final String STARTTAG_CIRCLE = "circle";
	private static final String STARTTAG_TEXT = "text";
	private static final String STARTTAG_TSPAN = "tspan";
	// private static final String STARTTAG_TEXTPATH = "textPath";
	private static final String STARTTAG_RADIALGRADIENT = "radialGradient";
	private static final String STARTTAG_LINEARGRADIENT = "linearGradient";
	private static final String STARTTAG_STOP = "stop";
	private static final String STARTTAG_DEFS = "defs";
	private static final String STARTTAG_PATTERN = "pattern";

	private static final String SPECIAL_ID_PREFIX_ANIM = "_anim";

	// private static final String SPECIAL_ID_PREFIX_META = "_meta";
	// private static final String SPECIAL_ID_PREFIX_ARCPARAMS = "_arcparams";

	/** Supported standard SVG attributes */
	private enum StandardAttributes {
		x, y, x1, y1, x2, y2, cx, cy, fx, fy, r, rx, ry, height, width, d, transform, gradientTransform, style, href, id, opacity, fill, fill_opacity, font_size, font_family, stroke, stroke_fill, stroke_opacity, stroke_width, text_align, text_anchor, offset, points, viewBox, novalue, lengthAdjust, textLength;

		public static StandardAttributes toAttr(String str) {
			try {
				return valueOf(str.replace('-', '_'));
			} catch (Exception e) {
				return novalue;
			}
		}
	}

	/**
	 * Custom attributes that are special to this SVG library. Some elements can
	 * use these to access special functionality that this library provides.
	 * When used they are prefixed with CUSTOM_ATTRIBUTE_NAMESPACE
	 */
	private enum CustomAttributes {
		anchor, stretch_to_remainder, visible_on_rotation, lengthAdjust, novalue;

		public static CustomAttributes toAttr(String str) {
			try {
				return valueOf(str.replace('-', '_'));
			} catch (Exception e) {
				return novalue;
			}
		}
	}

	private ParsedAttributes mParsedAttributes;
	private float mRootSvgHeight = 100;
	private float mRootSvgWidth = 100;

	// Lists and stacks for executable code
	ArrayList<Matrix> matrixList = new ArrayList<Matrix>();
	ArrayList<Path> pathList = new ArrayList<Path>();
	ArrayList<Paint> paintStack = new ArrayList<Paint>();
	ArrayList<SvgStyle> styleList = new ArrayList<SvgStyle>();
	ArrayList<Gradient> gradientList = new ArrayList<Gradient>();
	ArrayList<Textstring> textstringList = new ArrayList<Textstring>();
	HashMap<String, GroupJumpTo> subtreeJumpMap = new HashMap<String, GroupJumpTo>();
	ArrayList<String> idstringList = new ArrayList<String>();
	ArrayList<Arc> arcsList = new ArrayList<Arc>();

	// Data structures used during parsing
	private int tagDepth;
	private String mCurrentElement;
	private Gradient currentGradient = new Gradient();
	private int codePtr;
	private ArrayList<Byte> bytecodeList; // Expandable list used for initial
											// creation of bytecode from
											// parsing.
	private byte[] bytecodeArr; // Holds the complete bytecode for an SVG image
								// once parsed.
	private Stack<Matrix> matrixEvStack = new Stack<Matrix>(); // Used for
																// chaining
																// transformations
																// on nested
																// nodes.
	private boolean[] matrixExistsAtDepth = new boolean[20];
	private Stack<SvgStyle> mStyleParseStack = new Stack<SvgStyle>();

	private float mCurrentX;
	private float mCurrentY;
	private float mLastControlPointX = 0;
	private float mLastControlPointY = 0;

	private Context mContext;
	private HashMap<String, String> mPrivateDataMap;
	private String mPrivateDataCurrentKey;

	private HashMap<String, PatternFill> mPatternMap = new HashMap<String, PatternFill>();

	public SVGParserRenderer() {
		mPrivateDataMap = new HashMap<String, String>();
	}

	public SVGParserRenderer(Context context, int resourceID) {
		mPrivateDataMap = new HashMap<String, String>();
		parseImageFile(context, resourceID);
	}

	public SVGParserRenderer(Context context, File sourceFile)
			throws FileNotFoundException {
		mPrivateDataMap = new HashMap<String, String>();
		parseImageFile(context, sourceFile);
	}

	public SVGParserRenderer(Context context, InputStream sourceStream) {
		mPrivateDataMap = new HashMap<String, String>();
		parseImageFile(context, sourceStream);
	}

	public void setPrivateDataNamespace(String namespace) {
		mPrivateDataNamespace = namespace;
	}

	public String getPrivateDataValue(String key) {
		return mPrivateDataMap.get(key);
	}

	public void obtainSVGPrivateData(ITpsvgController controller) {
		Iterator<Map.Entry<String, String>> it = mPrivateDataMap.entrySet()
				.iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> pairs = (Map.Entry<String, String>) it
					.next();
			controller.onSVGPrivateData(pairs.getKey(), pairs.getValue());
		}
	}

	public void parseImageFile(Context context, File sourceFile)
			throws FileNotFoundException {
		InputStream inStream = new FileInputStream(sourceFile);
		parseImageFile(context, inStream);
	}

	public void parseImageFile(Context context, int resourceID) {
		Resources res = context.getResources();
		InputStream inStream = res.openRawResource(resourceID);
		parseImageFile(context, inStream);
	}

	public void parseImageFile(Context context, InputStream inStream) {
		mContext = context;
		tagDepth = 0;
		codePtr = 0;
		mParsedAttributes = new ParsedAttributes();

		this.gradientList.clear();
		this.matrixList.clear();
		matrixEvStack.clear();
		this.paintStack.clear();
		this.pathList.clear();
		this.styleList.clear();
		this.arcsList.clear();

		bytecodeList = new ArrayList<Byte>();

		SvgStyle s = new SvgStyle();

		mStyleParseStack.add(s);

		SAXParserFactory spf = SAXParserFactory.newInstance();
		try {
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();
			xr.setContentHandler(this);
			xr.parse(new InputSource(inStream));
		} catch (Exception e) {
		}

		addInstruction(INST_END);

		bytecodeArr = new byte[bytecodeList.size()];
		for (int i = 0; i < bytecodeList.size(); i++) {
			bytecodeArr[i] = bytecodeList.get(i);
		}

		for (SvgStyle style : styleList) {
			if (style.mFillPattern != null) {
				if (style.mFillPattern.getXLinkReferenceId() != null) {
					style.mFillPattern.setXLinkReferencePatternFill(mPatternMap
							.get(style.mFillPattern.getXLinkReferenceId()));
				}
				style.fillPaint.setShader(style.mFillPattern
						.createPatternShader(this));
				style.mFillPattern = null;
			}
		}

		mContext = null;
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		super.characters(ch, start, length);

		if (mCurrentElement.equalsIgnoreCase(STARTTAG_TEXT)) {
			text_characters(ch, start, length);
		} else if (mCurrentElement.equalsIgnoreCase(STARTTAG_TSPAN)) {
			tspan_characters(ch, start, length);
		} else if (mPrivateDataCurrentKey != null) {
			mPrivateDataMap.put(mPrivateDataCurrentKey, new String(ch, start,
					length));
		}
	}

	@Override
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		super.endElement(uri, localName, name);

		mPrivateDataCurrentKey = "";
		mCurrentElement = "";

		if (localName.equalsIgnoreCase(STARTTAG_G)) {
			addEndGroup();
		} else if (localName.equalsIgnoreCase(STARTTAG_LINEARGRADIENT)) {
			finaliseLinearGradient();
		} else if (localName.equalsIgnoreCase(STARTTAG_RADIALGRADIENT)) {
			finaliseRadialGradient();
		} else if (localName.equalsIgnoreCase(STARTTAG_DEFS)) {
			completeXLinks();
		} else if (localName.equalsIgnoreCase(STARTTAG_PATTERN)) {
			endPattern();
		}

		tagDepth--;

		if (matrixExistsAtDepth[tagDepth]) {
			this.matrixEvStack.pop();
		}

		mStyleParseStack.pop();
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
	}

	@Override
	public void endDocument() throws SAXException {
		super.endDocument();
	}

	/**
     *  
     */
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {

		super.startElement(uri, localName, qName, attributes);

		matrixExistsAtDepth[tagDepth] = false;
		mCurrentElement = localName;
		mParsedAttributes.svgStyle = new SvgStyle(mStyleParseStack.peek());

		if (mPrivateDataNamespace != null
				&& qName.startsWith(mPrivateDataNamespace)) {
			mPrivateDataCurrentKey = localName;
		} else {
			mPrivateDataCurrentKey = null;
		}

		if (localName.equalsIgnoreCase(STARTTAG_SVG)) {
			parseAttributes(attributes);
			svg();
		} else if (localName.equalsIgnoreCase(STARTTAG_G)) {
			parseAttributes(attributes);
			addBeginGroup(mParsedAttributes.id);
		} else if (localName.equalsIgnoreCase(STARTTAG_PATH)) {
			parseAttributes(attributes);
			path();
		} else if (localName.equalsIgnoreCase(STARTTAG_RECT)) {
			parseAttributes(attributes);
			rect();
		} else if (localName.equalsIgnoreCase(STARTTAG_LINE)) {
			parseAttributes(attributes);
			line();
		} else if (localName.equalsIgnoreCase(STARTTAG_POLYGON)) {
			parseAttributes(attributes);
			polygon();
		} else if (localName.equalsIgnoreCase(STARTTAG_CIRCLE)) {
			parseAttributes(attributes);
			circle();
		} else if (localName.equalsIgnoreCase(STARTTAG_TEXT)) {
			parseAttributes(attributes);
			text_element();
		} else if (localName.equalsIgnoreCase(STARTTAG_TSPAN)) {
			parseAttributes(attributes);
			tspan_element();
		} else if (localName.equalsIgnoreCase(STARTTAG_LINEARGRADIENT)) {
			parseAttributes(attributes);
			linearGradient();
		} else if (localName.equalsIgnoreCase(STARTTAG_RADIALGRADIENT)) {
			parseAttributes(attributes);
			radialGradient();
		} else if (localName.equalsIgnoreCase(STARTTAG_STOP)) {
			parseAttributes(attributes);
			gradientStop();
		} else if (localName.equalsIgnoreCase(STARTTAG_PATTERN)) {
			parseAttributes(attributes);
			startPattern();
		}

		mStyleParseStack.add(mParsedAttributes.svgStyle);
		tagDepth++;
	}

	private void parseAttributes(Attributes attributes) {

		AttributesImpl attrImpl = new AttributesImpl(attributes);

		// Reset attributes that don't inherit.
		mParsedAttributes.transformData = null;
		mParsedAttributes.styleData = null;
		mParsedAttributes.id = "";
		mParsedAttributes.anchorRight = false;
		mParsedAttributes.anchorBottom = false;
		mParsedAttributes.stretchToRemainderWidth = false;
		mParsedAttributes.stretchToRemainderWidth = false;
		mParsedAttributes.rotations = new ArrayList<Integer>();
		// It is important to reset co-ordinates to zero because I've seen some
		// elements (produced by Illustrator) that
		// omit co-ordinates (implying 0,0 or top left) and use a transform to
		// actually place the element.
		mParsedAttributes.x = 0;
		mParsedAttributes.y = 0;
		mParsedAttributes.cx = 0;
		mParsedAttributes.cy = 0;
		mParsedAttributes.x1 = 0;
		mParsedAttributes.x2 = 0;
		mParsedAttributes.y1 = 0;
		mParsedAttributes.y2 = 0;
		mParsedAttributes.rx = 0;
		mParsedAttributes.ry = 0;

		// Not sure if the 'opacity' attribute (as opposed to fill-opacity or
		// stroke-opacity
		// attributes) is supposed to inherit, so for now reset it to 1 each
		// time. Remove this later
		// if it needs to inherit. Also, fill-opacity and stroke-opacity do
		// inherit for time being.
		mParsedAttributes.svgStyle.masterOpacity = 1;
		mParsedAttributes.svgStyle.fillOpacity = 1;
		mParsedAttributes.svgStyle.strokeOpacity = 1;

		mParsedAttributes.textLength = 0;
		mParsedAttributes.textLengthAdjustSize = false;

		// During execution of the loop, the length of attrImpl will expand if a
		// <style> attribute
		// exists. A <style> tag's value itself contains a list of
		// semicolon-separated key/value pairs.
		// The <style>'s key/value pairs are converted into attribute
		// key/values,
		// taking advantage of AttributesImpl class .addAttribute method in
		// doing so. The reason for
		// 'inflating' <style> into attributes is because graphical style
		// attributes
		// can be present in an SVG file as either normal attributes, or
		// contained as key/values
		// in the value of a <style> tag; we need to be able to process both
		// situations.

		for (int n = 0; n < attrImpl.getLength(); n++) {

			String value = attrImpl.getValue(n).trim(); // Value could contain
														// prefix/suffix spaces;
														// remove them.
			switch (StandardAttributes.toAttr(attrImpl.getLocalName(n))) {

			case x:
				mParsedAttributes.x = parseCoOrdinate(value);
				break;

			case y:
				mParsedAttributes.y = parseCoOrdinate(value);
				break;

			case x1:
				mParsedAttributes.x1 = parseCoOrdinate(value);
				break;

			case y1:
				mParsedAttributes.y1 = parseCoOrdinate(value);
				break;

			case x2:
				mParsedAttributes.x2 = parseCoOrdinate(value);
				break;

			case y2:
				mParsedAttributes.y2 = parseCoOrdinate(value);
				break;

			case cx:
				mParsedAttributes.cx = parseCoOrdinate(value);
				break;

			case cy:
				mParsedAttributes.cy = parseCoOrdinate(value);
				break;

			case r:
				mParsedAttributes.radius = parseCoOrdinate(value);
				break;

			case fx:
				mParsedAttributes.fx = parseCoOrdinate(value);
				break;

			case fy:
				mParsedAttributes.fy = parseCoOrdinate(value);
				break;

			case rx:
				mParsedAttributes.rx = parseCoOrdinate(value);
				break;

			case ry:
				mParsedAttributes.ry = parseCoOrdinate(value);
				break;

			case width:
				mParsedAttributes.width = parseCoOrdinate(value);
				break;

			case height:
				mParsedAttributes.height = parseCoOrdinate(value);
				break;

			case d:
				mParsedAttributes.pathData = value;
				break;

			case transform:
				mParsedAttributes.transformData = value;
				break;

			case gradientTransform:
				mParsedAttributes.transformData = value;
				break;

			case id:
				mParsedAttributes.id = value;
				break;

			case href:
				mParsedAttributes.xlink_href = value;
				break;

			case style:
				mParsedAttributes.styleData = value;
				parseAttributeValuePairsIntoSaxAttributesImpl(attrImpl);
				// The number of attribute key/value pairs has now been
				// increased.
				break;

			case font_size:
				mParsedAttributes.svgStyle.strokePaint
						.setTextSize(parseCoOrdinate(value));
				mParsedAttributes.svgStyle.fillPaint
						.setTextSize(parseCoOrdinate(value));
				break;

			case font_family:
				Typeface typeface = getTypeface(value);
				mParsedAttributes.svgStyle.strokePaint.setTypeface(typeface);
				mParsedAttributes.svgStyle.fillPaint.setTypeface(typeface);
				break;

			case fill:
				if (!value.equals("none")) {
					if (value.startsWith("url")) {
						// Assume the form fill:url(#[ID_STRING])
						String idString = value
								.substring(5, value.length() - 1);
						int gradientIdx = this.getGradientByIdString(idString);
						// TODO: This is giving the Paint a *reference* to
						// one
						// of the Shader objects
						// in the gradientList. This is possibly okay, but
						// because we need to
						// apply the current Path's Matrix as the gradient's
						// local Matrix during
						// rendering, we have to be careful not to
						// permanently
						// modify the shader,
						// as otherwise any changes (Matrix transformation
						// etc)
						// will affect
						// subsequent uses of that Shader.

						if (gradientIdx != -1) {
							mParsedAttributes.svgStyle.fillPaint
									.setShader(gradientList.get(gradientIdx).shader);
						} else if (mPatternMap.containsKey(idString)) {
							// s.fillPaint.setShader(mPatternList.get(idString).getShader());
							mParsedAttributes.svgStyle.mFillPattern = mPatternMap
									.get(idString);
						}
					}

					else {
						// Set the colour, while preserving the alpha (in
						// case
						// alpha is to be inherited rather
						// than being explicity set in the element's own
						// style
						// attributes)
						int alpha = mParsedAttributes.svgStyle.fillPaint
								.getAlpha();
						mParsedAttributes.svgStyle.fillPaint
								.setColor(parseColour(value));
						mParsedAttributes.svgStyle.fillPaint.setAlpha(alpha);
					}
					mParsedAttributes.svgStyle.fillPaint
							.setStyle(Paint.Style.FILL);
					// TODO: Should only need do this once in Style
					// constructor!
					mParsedAttributes.svgStyle.hasFill = true;
				} else {
					// The attribute is fill="none".
					mParsedAttributes.svgStyle.hasFill = false;
				}
				break;

			case opacity:
				mParsedAttributes.svgStyle.masterOpacity = parseAttrValueFloat(value);
				mParsedAttributes.svgStyle.fillPaint
						.setAlpha((int) (mParsedAttributes.svgStyle.masterOpacity
								* mParsedAttributes.svgStyle.fillOpacity * 255));
				mParsedAttributes.svgStyle.strokePaint
						.setAlpha((int) (mParsedAttributes.svgStyle.masterOpacity
								* mParsedAttributes.svgStyle.strokeOpacity * 255));
				break;

			case offset:
				mParsedAttributes.offset = value;
				break;

			case fill_opacity: {
				float opacity = parseAttrValueFloat(value);
				mParsedAttributes.svgStyle.fillOpacity = opacity;
				mParsedAttributes.svgStyle.fillPaint
						.setAlpha((int) ((opacity * mParsedAttributes.svgStyle.masterOpacity) * 255));
			}
				break;

			case stroke_opacity: {
				float opacity = parseAttrValueFloat(value);
				mParsedAttributes.svgStyle.strokeOpacity = opacity;
				mParsedAttributes.svgStyle.strokePaint
						.setAlpha((int) ((opacity * mParsedAttributes.svgStyle.masterOpacity) * 255));
			}
				break;

			case stroke:
				if (!value.equals("none")) {
					if (value.startsWith("url")) {
						// Assume the form fill:url(#[ID_STRING])
						int gradientIdx = this.getGradientByIdString(value
								.substring(5, value.length() - 1));
						// TODO: See comments further above (in 'fill')
						// regarding Shader.
						mParsedAttributes.svgStyle.strokePaint
								.setShader(gradientList.get(gradientIdx).shader);
					} else {
						// Set the colour, while preserving the alpha (in
						// case
						// alpha is to be inherited rather
						// than being explicity set in the element's own
						// style
						// attributes)
						int alpha = mParsedAttributes.svgStyle.strokePaint
								.getAlpha();
						mParsedAttributes.svgStyle.strokePaint
								.setColor(parseColour(value));
						mParsedAttributes.svgStyle.strokePaint.setAlpha(alpha);
					}
					mParsedAttributes.svgStyle.strokePaint
							.setStyle(Paint.Style.STROKE); // TODO: Should
															// only
															// have to do
															// once
															// in the
															// constructor!
					mParsedAttributes.svgStyle.hasStroke = true;
				} else { // The attribute is stroke="none".
					mParsedAttributes.svgStyle.hasStroke = false;
				}
				break;

			case stroke_width:
				float width = parseCoOrdinate(value);
				// .setStrokeWidth doesn't seem to deal in px directly;
				// there
				// seems to be scaling of 1.5 applied, and this doesn't seem
				// to depend on screen density. Compensate for it, otherwise
				// wide stroke widths appear much too thick.
				if (width > 0) {
					width /= 1.5;
				}
				mParsedAttributes.svgStyle.strokePaint.setStrokeWidth(width);
				break;

			case points:
				mParsedAttributes.pointsData = value;
				break;

			case text_align:
				break;
			case text_anchor:
				Paint.Align align = Paint.Align.LEFT;
				if (value.startsWith("middle")) {
					align = Paint.Align.CENTER;
				} else if (value.startsWith("end")) {
					align = Paint.Align.RIGHT;
				}

				mParsedAttributes.svgStyle.strokePaint.setTextAlign(align);
				mParsedAttributes.svgStyle.fillPaint.setTextAlign(align);
				break;

			case textLength:
				float length = parseCoOrdinate(value);
				mParsedAttributes.textLength = length;
				break;

			case viewBox:
				mParsedAttributes.viewBox = value;
				break;

			default:
				if (attrImpl.getQName(n).startsWith(CUSTOM_ATTRIBUTE_NAMESPACE)) {
					parseCustomAttribute(attrImpl.getLocalName(n), value);
				}
				break;
			}
		}
	}

	private void parseCustomAttribute(String localName, String value) {

		switch (CustomAttributes.toAttr(localName)) {

		case anchor:
			value = value.toLowerCase();
			mParsedAttributes.anchorRight = value.contains("right") ? true
					: false;
			mParsedAttributes.anchorBottom = value.contains("bottom") ? true
					: false;
			break;

		case stretch_to_remainder:
			value = value.toLowerCase();
			mParsedAttributes.stretchToRemainderHeight = value
					.contains("height") ? true : false;
			mParsedAttributes.stretchToRemainderWidth = value.contains("width") ? true
					: false;
			break;

		case visible_on_rotation:
			PathTokenizer t = new PathTokenizer();
			String valueCopy = value;
			while (t.getToken(valueCopy) == PathTokenizer.LTOK_NUMBER) {
				valueCopy = null;
				mParsedAttributes.rotations.add(Math.round(t.tokenF));
			}
			break;

		case lengthAdjust:
			mParsedAttributes.textLengthAdjustSize = value
					.equalsIgnoreCase("size") ? true : false;
			break;

		default:
			break;
		}
	}

	private void linearGradient() {
		Gradient g = currentGradient; // new Gradient();
		// TODO: xlink stuff here
		/*
		 * g.setLinear( mProperties.x1, mProperties.y1, mProperties.x2,
		 * mProperties.y2,
		 * 
		 * 
		 * );
		 */

		// Note: Cannot assume that the linearGradient element contains the
		// coordinates,
		// or anything for that matter. The spec says that the xlink:href can be
		// used to
		// inherit any properties not defined in this element.

		g.setCoordinates(mParsedAttributes.x1, mParsedAttributes.y1,
				mParsedAttributes.x2, mParsedAttributes.y2);

		g.id = mParsedAttributes.id;

		// Best way to deal with xlink might be to do an entire copy of the
		// referenced
		// Gradient first, and then apply over the top any properties that are
		// explicitly defined for this Gradient.
		if (mParsedAttributes.xlink_href != null) {

			// The hrefs have to be dealt with in a second pass, because forward
			// references are possible!

			// It'll have the form #abcd where abcd is the ID
			// int idx =
			// this.getGradientByIdString(mProperties.xlink_href.substring(1));
			// if(idx!=-1){
			// Gradient xlinkGrad = this.gradientList.get(idx);
			// currentGradient.stopColours = xlinkGrad.stopColours;
			// }
			currentGradient.href = mParsedAttributes.xlink_href;

		} else {
			currentGradient.href = null;
		}

		currentGradient.matrix = transform();
	}

	private void radialGradient() {
		Gradient g = currentGradient;

		// TODO: xlink stuff here
		/*
		 * g.setRadial( // cx, cy and also fx, fy are present in Inkscape
		 * output, but both are same // coordinates usually. Will just use cx
		 * and cy for now. mProperties.cx, mProperties.cy, mProperties.r, ?,
		 * null, Shader.TileMode.CLAMP );
		 */

		/*
		 * g.setCoordinates( mProperties.x1, mProperties.y1, mProperties.x2,
		 * mProperties.y2 );
		 */
		g.cx = mParsedAttributes.cx;
		g.cy = mParsedAttributes.cy;
		g.radius = mParsedAttributes.radius;
		g.fx = mParsedAttributes.fx;
		g.fy = mParsedAttributes.fy;

		// Best way to deal with xlink might be to do an entire copy of the
		// referenced
		// Gradient first, and then apply over the top any properties that are
		// explicitly defined for this Gradient.
		if (mParsedAttributes.xlink_href != null) {
			// It'll have the form #abcd where abcd is the ID
			// int idx =
			// this.getGradientByIdString(mProperties.xlink_href.substring(1));
			// if(idx!=-1){
			// Gradient xlinkGrad = this.gradientList.get(idx);
			// currentGradient.stopColours = xlinkGrad.stopColours;
			// }
			currentGradient.href = mParsedAttributes.xlink_href;
		} else {
			currentGradient.href = null;
		}

		// if(mProperties.transformData!=null){
		currentGradient.matrix = transform();
		// }

		g.id = mParsedAttributes.id;
	}

	/**
	 * Start processing a &lt;pattern&gt; element. Current limitations and
	 * restrictions of pattern support are: 1. The patternUnits attribute isn't
	 * used, and tiles are spaced as if it were patternUnits="userSpaceOnUse".
	 * 2. A viewBox attribute must be provided to specify the bounds of the
	 * pattern tile. 3. Quite a lot more - this is work in progress.
	 */
	private void startPattern() {
		addBeginPattern(mParsedAttributes.id);

		PatternFill patternFill = new PatternFill();

		if (mParsedAttributes.xlink_href != null) {
			// Assume the form xlink:href="#some_id_string"
			patternFill.setXLinkReferenceId(mParsedAttributes.xlink_href
					.replace("#", ""));
		} else {
			patternFill.setPatternSubtree(mParsedAttributes.id);

			PathTokenizer t = new PathTokenizer();
			float x, y, width, height;
			t.getToken(mParsedAttributes.viewBox);
			x = t.tokenF;
			t.getToken(null);
			y = t.tokenF;
			t.getToken(null);
			width = t.tokenF;
			t.getToken(null);
			height = t.tokenF;
			t.getToken(null);

			if (width > 0 && height > 0) {
				patternFill.setPatternViewBox(x, y, width, height);
			} else {
				String id = mParsedAttributes.id != null ? mParsedAttributes.id
						: "(no ID specified)";
				Log.w(LOGTAG,
						"Pattern element "
								+ id
								+ " doesn't have viewBox attribute, or has zero viewBox width or height");
			}
		}
		mPatternMap.put(mParsedAttributes.id, patternFill);
	}

	/**
	 * 
	 */
	private void endPattern() {
		addEndPattern();
	}

	/**
	 * Returns whether any vector data was parsed from the specified SVG file or
	 * file subtree.
	 * 
	 * @return
	 */
	public boolean hasVectorContent() {
		return (pathList.size() > 0 || arcsList.size() > 0);
	}

	/**
	 * Is called when all of the SVG XML file has been parsed. It performs
	 * cross-referencing and copying of data due to xlink:hrefs. For example,
	 * LinearGradient and RadialGradient use xlink:href a lot because gradients
	 * often reference stop colours from another gradient ID. This task has to
	 * be performed after parsing and not during because it's possible to have
	 * both backward and forward references.
	 * 
	 * Actually - this is now called when the 'defs' close tag is encountered.
	 * The gradients need to be all fully set up before we start generating
	 * paths! This assumes for now that there's only one defs block... which is
	 * bound to be a wrong thing to assume. Also, it's probably wrong to assume
	 * that a defs block can't occur again in the document. Something to be
	 * careful of and improve later. We might need to implement pointers for
	 * things like gradientList to keep tabs on which ones were newly added by a
	 * given defs block. Also, xrefs between gradients could be at a greater
	 * depth than 1, which means that we'd need to do multiple passes over the
	 * list.
	 */
	private void completeXLinks() {
		// Process the gradients
		Iterator<Gradient> gradientItr = gradientList.iterator();
		Gradient gradient;
		int idx;
		while (gradientItr.hasNext()) {
			gradient = gradientItr.next();
			if (gradient.href != null) {
				idx = this.getGradientByIdString(gradient.href.substring(1));
				if (idx != -1) {
					Gradient xlinkGrad = this.gradientList.get(idx);
					gradient.stopColors = xlinkGrad.stopColors;

				}

				// TODO: Can't we just insert an object reference to the same
				// shader?

				int[] colors = new int[gradient.stopColors.size()];
				float[] offsets = new float[gradient.stopColors.size()];
				for (int i = 0; i < colors.length; i++) {
					colors[i] = gradient.stopColors.get(i).color;
					offsets[i] = gradient.stopColors.get(i).offset;
				}

				if (gradient.isRadial) {
					gradient.shader = new RadialGradient(gradient.cx,
							gradient.cy, gradient.radius, colors, offsets,
							Shader.TileMode.CLAMP);
				} else { // linear
					gradient.shader = new LinearGradient(gradient.x1,
							gradient.y1, gradient.x2, gradient.y2, colors,
							offsets, Shader.TileMode.CLAMP);
				}

				// The shader needs to have a matrix even if no transform was
				// specified in the attributes
				// for the gradient. This is because the gradient's Matrix, even
				// if 'empty', is needed
				// to concatenate the current cumulative transform to during
				// evaluation/drawing.
				if (gradient.matrix != null) {
					gradient.shader.setLocalMatrix(gradient.matrix);
				} else {
					gradient.shader.setLocalMatrix(new Matrix());
				}
			}
		}
	}

	private void finaliseLinearGradient() {

		// Only apply a shader now if this gradient element itself provided stop
		// colours in its
		// attributes. If it didn't, then hopefully it references another
		// gradient's colours via a href
		// attribute, in which case the cross-referencing will be done later.

		if (currentGradient.stopColors.size() > 0) {
			int[] ia = new int[currentGradient.stopColors.size()];

			for (int i = 0; i < ia.length; i++) {
				ia[i] = currentGradient.stopColors.get(i).color;
			}

			currentGradient.shader = new LinearGradient(currentGradient.x1,
					currentGradient.y1, currentGradient.x2, currentGradient.y2,
					ia, null, Shader.TileMode.CLAMP);

			// The shader needs to have a matrix even if no transform was
			// specified in the attributes
			// for the gradient. This is because the gradient's Matrix, even if
			// 'empty', is needed
			// to concatenate the current cumulative transform to during
			// evaluation/drawing.
			if (currentGradient.matrix != null) {
				currentGradient.shader.setLocalMatrix(currentGradient.matrix);
			} else {
				currentGradient.shader.setLocalMatrix(new Matrix());
			}

		}

		currentGradient.isRadial = false;
		gradientList.add(currentGradient);
		currentGradient = new Gradient();

	}

	private void finaliseRadialGradient() {

		if (currentGradient.stopColors.size() > 0) {

			int[] colors = new int[currentGradient.stopColors.size()];
			float[] offsets = new float[currentGradient.stopColors.size()];
			for (int i = 0; i < colors.length; i++) {
				colors[i] = currentGradient.stopColors.get(i).color;
				offsets[i] = currentGradient.stopColors.get(i).offset;
			}

			currentGradient.shader = new RadialGradient(currentGradient.cx,
					currentGradient.cy, currentGradient.radius, colors,
					offsets, Shader.TileMode.CLAMP);

			// The shader needs to have a matrix even if no transform was
			// specified in the attributes
			// for the gradient. This is because the gradient's Matrix, even if
			// 'empty', is needed
			// to concatenate the current cumulative transform to during
			// evaluation/drawing.
			if (currentGradient.matrix != null) {
				currentGradient.shader.setLocalMatrix(currentGradient.matrix);
			} else {
				currentGradient.shader.setLocalMatrix(new Matrix());
			}
		}
		currentGradient.isRadial = true;
		gradientList.add(currentGradient);
		currentGradient = new Gradient();
	}

	private void gradientStop() {
		gradientStyle();
	}

	/*
	 * Search the gradientList for the Gradient with specified string ID. Index
	 * into the gradientList is returned. If not found, -1 is returned.
	 */
	private int getGradientByIdString(String searchId) {
		for (int idx = 0; idx < gradientList.size(); idx++) {
			if (gradientList.get(idx).id.equals(searchId)) {
				return idx;
			}
		}
		return -1;
	}

	private void svg() {
		mRootSvgHeight = mParsedAttributes.height;
		mRootSvgWidth = mParsedAttributes.width;
	}

	private void rect() {
		SVGPath path = new SVGPath();
		RectF rectangle = new RectF(mParsedAttributes.x, mParsedAttributes.y,
				(mParsedAttributes.x + mParsedAttributes.width),
				(mParsedAttributes.y + mParsedAttributes.height));
		path.addRoundRect(rectangle, mParsedAttributes.rx,
				mParsedAttributes.ry, Path.Direction.CW);
		setCustomPathAttributes(path);
		mCurrentX = mParsedAttributes.x;
		mCurrentY = mParsedAttributes.y;
		addPath(path);
	}

	private void circle() {
		SVGPath path = new SVGPath();
		path.addCircle(mParsedAttributes.cx, mParsedAttributes.cy,
				mParsedAttributes.radius, Direction.CW);
		setCustomPathAttributes(path);
		mCurrentX = mParsedAttributes.cx;
		mCurrentY = mParsedAttributes.cy;
		addPath(path);
	}

	private void line() {
		SVGPath path = new SVGPath();
		path.moveTo(mParsedAttributes.x1, mParsedAttributes.y1);
		path.lineTo(mParsedAttributes.x2, mParsedAttributes.y2);
		setCustomPathAttributes(path);
		mCurrentX = mParsedAttributes.x2;
		mCurrentY = mParsedAttributes.y2;
		addPath(path);
	}

	private void setCustomPathAttributes(SVGPath path) {
		path.setAnchorRight(mParsedAttributes.anchorRight);
		path.setAnchorBottom(mParsedAttributes.anchorBottom);
		path.setStretchToRemainderWidth(mParsedAttributes.stretchToRemainderWidth);
		path.setStretchToRemainderHeight(mParsedAttributes.stretchToRemainderHeight);
		path.addVisibleOnRotations(mParsedAttributes.rotations);
	}

	private void text_element() {
		addStyle();
		addTransform();
	}

	/**
	 * To be called from the XML character data handler when we are inside an
	 * element associated with display of text ('text', 'tspan', 'tref',
	 * 'textPath').
	 */
	private void text_characters(char[] src, int srcPos, int length) {
		// addStyle();
		Textstring textString = new Textstring(mParsedAttributes.x,
				mParsedAttributes.y, src, srcPos, length);
		textString.mAnchorRight = mParsedAttributes.anchorRight;
		textString.mSizeToFitTextLength = mParsedAttributes.textLengthAdjustSize;
		textString.setTextLength((int) mParsedAttributes.textLength);
		textstringList.add(textString);
		addText();
	}

	private void tspan_element() {
		addStyle();
		addTransform();
	}

	private void tspan_characters(char[] src, int srcPos, int length) {
		Textstring textString = new Textstring(mParsedAttributes.x,
				mParsedAttributes.y, src, srcPos, length);
		textString.mAnchorRight = mParsedAttributes.anchorRight;
		textString.mSizeToFitTextLength = mParsedAttributes.textLengthAdjustSize;
		textString.setTextLength((int) mParsedAttributes.textLength);
		textString.addVisibleOnRotations(mParsedAttributes.rotations);
		textstringList.add(textString);
		addText();
	}

	private void path() {
		float rx, ry, x_axis_rotation, x, y, x1, y1, x2, y2;
		boolean firstElement = true, carry = false, large_arc_flag, sweep_flag;
		PathTokenizer t = new PathTokenizer();
		t.getToken(mParsedAttributes.pathData);
		SVGPath path = new SVGPath();
		char currentCommandLetter = '?';
		// The segmentStartX and segmentStartY record the start position of the
		// current segment being drawn in the Path. The segment start position
		// is
		// needed should a 'z' (close path) be immediately followed by a
		// relative
		// command to begin a next segment. (The Path class doesn't seem to
		// provide
		// a way to determine the current position after calling close(). It
		// could
		// be done using PathMeasure, but that seems expensive.)
		float segmentStartX = 0;
		float segmentStartY = 0;
		boolean segmentStart = true;

		/**
		 * Indicates when any segment has been added to the path. Implemented
		 * because path.isEmpty() appears to return true even if it's only had
		 * moveTo() called on it.
		 */
		boolean pathIsEmpty = true;

		do {
			if (t.currentTok == PathTokenizer.LTOK_LETTER) {
				currentCommandLetter = t.tokenChar;
				t.getToken(null);
			}

			// If the current token is not alpha (a letter) but a number, then
			// it's an implied command,
			// i.e. assume last used command letter.

			switch (currentCommandLetter) {

			case 'M':
			case 'm':
				x = t.tokenF;
				t.getToken(null);
				y = t.tokenF;
				// A relative moveto command, 'm', is interpreted as an
				// absolute
				// moveto (M) if it's the first element in the path.
				if (currentCommandLetter == 'm' && firstElement == false) {
					x += mCurrentX;
					y += mCurrentY;
				}
				path.moveTo(x, y);
				mCurrentX = x;
				mCurrentY = y;
				if (currentCommandLetter == 'M') {
					currentCommandLetter = 'L';
				} else {
					currentCommandLetter = 'l';
				}
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				break;

			case 'L':
			case 'l':
				x = t.tokenF;
				t.getToken(null);
				y = t.tokenF;
				if (currentCommandLetter == 'l') {
					x += mCurrentX;
					y += mCurrentY;
				}
				path.lineTo(x, y);
				mCurrentX = x;
				mCurrentY = y;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'H':
			case 'h':
				x = t.tokenF;
				if (currentCommandLetter == 'h') {
					x += mCurrentX;
				}
				path.lineTo(x, mCurrentY);
				mCurrentX = x;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'V':
			case 'v':
				y = t.tokenF;
				if (currentCommandLetter == 'v') {
					y += mCurrentY;
				}
				path.lineTo(mCurrentX, y);
				mCurrentY = y;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'z':
				path.close();
				// The current X, Y need to reflect where this segment
				// started,
				// as that's where the path's position now is. This is
				// important
				// to ensure a following relative command (i.e. another
				// segment
				// begins after a 'z' command) occurs at the right
				// co-ordinates.
				mCurrentX = segmentStartX;
				mCurrentY = segmentStartY;
				segmentStart = true;
				carry = true;
				break;

			case 'A':
			case 'a':
				rx = t.tokenF;
				t.getToken(null);
				ry = t.tokenF;
				t.getToken(null);
				x_axis_rotation = t.tokenF;
				t.getToken(null);
				large_arc_flag = (t.tokenF == 0f) ? false : true;
				t.getToken(null);
				sweep_flag = (t.tokenF == 0f) ? false : true;
				t.getToken(null);
				x = t.tokenF;
				t.getToken(null);
				y = t.tokenF;
				if (currentCommandLetter == 'a') {
					x += mCurrentX;
					y += mCurrentY;
				}

				// If this arc is the only element in the segment then it is
				// stored as an
				// isolated arc object which means it can be programmatically
				// manipulated in
				// terms of start and sweep angle. Otherwise, it forms part of a
				// complex
				// Path object, and the arc can't be specially manipulated.
				if (t.getToken(null) == PathTokenizer.LTOK_END
						&& pathIsEmpty == true) {
					arcTo(null, rx, ry, x_axis_rotation, large_arc_flag,
							sweep_flag, x, y);
				} else {
					arcTo(path, rx, ry, x_axis_rotation, large_arc_flag,
							sweep_flag, x, y);
				}
				carry = true;

				mCurrentX = x;
				mCurrentY = y;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'C':
			case 'c':
				x1 = t.tokenF;
				t.getToken(null);
				y1 = t.tokenF;
				t.getToken(null);
				x2 = t.tokenF;
				t.getToken(null);
				y2 = t.tokenF;
				t.getToken(null);
				x = t.tokenF;
				t.getToken(null);
				y = t.tokenF;
				if (currentCommandLetter == 'c') {
					x += mCurrentX;
					y += mCurrentY;

					x1 += mCurrentX;
					y1 += mCurrentY;
					x2 += mCurrentX;
					y2 += mCurrentY;
				}
				// TODO: Could alternatively make use of rCubicTo if it's to
				// be
				// relative.
				path.cubicTo(x1, y1, x2, y2, x, y);
				mLastControlPointX = x2;
				mLastControlPointY = y2;
				mCurrentX = x;
				mCurrentY = y;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'S':
			case 's':
				x2 = t.tokenF;
				t.getToken(null);
				y2 = t.tokenF;
				t.getToken(null);
				x = t.tokenF;
				t.getToken(null);
				y = t.tokenF;
				if (currentCommandLetter == 's') {
					x += mCurrentX;
					y += mCurrentY;
					x2 += mCurrentX;
					y2 += mCurrentY;
				}
				x1 = 2 * mCurrentX - mLastControlPointX;
				y1 = 2 * mCurrentY - mLastControlPointY;
				// TODO: Could alternatively make use of rCubicTo if it's a
				// relative command.
				path.cubicTo(x1, y1, x2, y2, x, y);
				mLastControlPointX = x2;
				mLastControlPointY = y2;
				mCurrentX = x;
				mCurrentY = y;
				if (segmentStart) {
					segmentStartX = mCurrentX;
					segmentStartY = mCurrentY;
					segmentStart = false;
				}
				pathIsEmpty = false;
				break;

			case 'Q':
			case 'q':
				// TODO: To be completed!
				pathIsEmpty = false;
				break;

			case 'T':
			case 't':
				// TODO: To be completed!
				pathIsEmpty = false;
				break;

			default:
				carry = true;
				// pathIsEmpty = false;
				break;

			}

			firstElement = false;
			if (!carry)
				t.getToken(null);
			carry = false;

		} while (t.currentTok != PathTokenizer.LTOK_END);

		setCustomPathAttributes(path);
		addPath(path);
	}

	private void polygon() {
		float x, y;
		PathTokenizer t = new PathTokenizer();
		t.getToken(mParsedAttributes.pointsData);
		SVGPath path = new SVGPath();

		x = t.tokenF;
		t.getToken(null);
		y = t.tokenF;
		t.getToken(null);
		path.moveTo(x, y);

		do {
			x = t.tokenF;
			t.getToken(null);
			y = t.tokenF;
			t.getToken(null);
			path.lineTo(x, y);

		} while (t.currentTok != PathTokenizer.LTOK_END);

		path.close();
		setCustomPathAttributes(path);
		addPath(path);
	}

	private Matrix transform() {

		float f1, f2;
		Matrix m = new Matrix();
		ValueTokenizer t = new ValueTokenizer();

		if (mParsedAttributes.transformData != null) {
			t.getToken(mParsedAttributes.transformData);
			do {
				if (t.currentTok == ValueTokenizer.LTOK_STRING) {
					if (t.tokenStr.equalsIgnoreCase("scale")) {
						t.getToken(null);
						f1 = t.tokenF;
						t.getToken(null);
						f2 = t.tokenF;
						m.postScale(f1, f2);
					}

					if (t.tokenStr.equalsIgnoreCase("translate")) {
						t.getToken(null);
						f1 = t.tokenF;
						t.getToken(null);
						f2 = t.tokenF;
						// Possibly use .postTranslate to apply over a previous
						// transformation

						m.postTranslate(f1, f2);

					}

					else if (t.tokenStr.equalsIgnoreCase("rotate")) {
						t.getToken(null);
						f1 = t.tokenF;
						// Possibly use .postTranslate to apply over a previous
						// transformation

						m.postRotate(f1);

					}

					else if (t.tokenStr.equalsIgnoreCase("matrix")) {
						float f[] = new float[9];
						t.getToken(null);
						f[0] = t.tokenF;
						t.getToken(null);
						f[3] = t.tokenF;
						t.getToken(null);
						f[1] = t.tokenF;
						t.getToken(null);
						f[4] = t.tokenF;
						t.getToken(null);
						f[2] = t.tokenF;
						t.getToken(null);
						f[5] = t.tokenF;
						f[6] = 0;
						f[7] = 0;
						f[8] = 1;
						Matrix post = new Matrix();
						post.setValues(f);
						m.postConcat(post);

						// m.getValues(f);
						// m.MTRANS_X is 2
						// m.MTRANS_Y is 5

						// m.setValues(f);
					}
				}

				t.getToken(null);

			} while (t.currentTok != ValueTokenizer.LTOK_END);
			mParsedAttributes.transformData = null;
		}
		return m;
	}

	private void gradientStyle() {
		Map<String, String> map = new HashMap<String, String>();
		parseAttributeValuePairsIntoMap(map);
		String value;
		int stopColour = 0;
		float stopOffset = 0;
		boolean haveStopColour = false;

		if (mParsedAttributes.offset != null) {
			stopOffset = parseAttrValueFloat(mParsedAttributes.offset);
		}

		if (null != (value = map.get("stop-opacity"))) {
			float opacity = parseAttrValueFloat(value);
			stopColour |= (((int) (opacity * 255)) << 24);
			haveStopColour = true;
		} else {
			// TODO: If it's possible for gradients, inherit the opacity if it's
			// not explicitly
			// stated in the gradient's attributes.
			stopColour = 0xff000000;
		}

		if (null != (value = map.get("stop-color"))) {
			stopColour |= parseColour(value);
			haveStopColour = true;
		}

		if (haveStopColour) {
			currentGradient.stopColors.add(new Gradient.StopColor(stopColour,
					stopOffset));
		}
	}

	private void parseAttributeValuePairsIntoMap(Map<String, String> map) {
		// Typical format is:
		// font-size:40px;font-style:normal;font-variant:normal; ...
		final Pattern keyValuePattern = Pattern
				.compile("([\\w-#]+\\s*):(\\s*[\\w-#\\.\\(\\)\\s,]*)"); // basically
																		// need
																		// to
																		// change
																		// this
																		// to
																		// match
																		// virtually
																		// anything
																		// on
																		// each
																		// side
																		// of
																		// the :
																		// except
																		// for a
																		// few
																		// symbols
		Matcher keyValueMatcher = keyValuePattern
				.matcher(mParsedAttributes.styleData);
		while (keyValueMatcher.find()) {
			map.put(keyValueMatcher.group(1), keyValueMatcher.group(2));
		}
	}

	private int parseAttributeValuePairsIntoSaxAttributesImpl(
			AttributesImpl attr) {
		int quantityAdded = 0;
		final Pattern keyValuePattern = Pattern
				.compile("([\\w-#]+\\s*):(\\s*[\\w-#\\.\\(\\)\\s,]*)"); // basically
																		// need
																		// to
																		// change
																		// this
																		// to
																		// match
																		// virtually
																		// anything
																		// on
																		// each
																		// side
																		// of
																		// the :
																		// except
																		// for a
																		// few
																		// symbols
		Matcher keyValueMatcher = keyValuePattern
				.matcher(mParsedAttributes.styleData);
		while (keyValueMatcher.find()) {
			attr.addAttribute("", keyValueMatcher.group(1), "", "",
					keyValueMatcher.group(2));
			quantityAdded++;
		}
		return quantityAdded;
	}

	/**
	 * Parse a basic data type of type <coordinate> or <length>.
	 * 
	 * length ::= number ("em" | "ex" | "px" | "in" | "cm" | "mm" | "pt" | "pc"
	 * | "%")?
	 * 
	 * Relevant parts of the SVG specification include:
	 * http://www.w3.org/TR/SVG/types.html#BasicDataTypes
	 * 
	 * This method may be expanded later to handle some of the other basic SVG
	 * data types that use a similar syntax, e.g. <angle>.
	 * 
	 * Limitations of this method: It's assumed for now that values will have
	 * either no units specified and can be assumed 'px', or will otherwise have
	 * 'px' unit identifier explicitly stated. Support for other units such as
	 * pt, pc, etc. will be added later when deemed necessary. Either some
	 * automatic translation could be done into the px space, or alternatively
	 * investigate whether Android's vector graphics functions can be made to
	 * accept units of different systems.
	 * 
	 * For convenience, this method may or may not also handle the stripping of
	 * quotation marks from the value string - this is TBD.
	 */
	private static float parseCoOrdinate(String value) {
		float result = 0f;

		// Quick and dirty way to determine if the value appears to have a units
		// suffix.
		if (value.charAt(value.length() - 1) >= 'a') {
			if (value.endsWith("px")) {
				value = value.substring(0, value.length() - 2);
			} else {
				// TODO: Add support in future for other units here.
				// TODO: Call our error reporting function.
			}
		}
		try {
			result = Float.parseFloat(value);
		} catch (NumberFormatException nfe) {

		}
		return result;
	}

	/**
	 * Parse a basic data type of type &lt;color&gt;. See SVG specification
	 * section: http://www.w3.org/TR/SVG/types.html#BasicDataTypes.
	 * 
	 * TODO: This method needs error checking and reporting.
	 */
	private int parseColour(String value) {

		int result = 0xffffff;

		// Handle colour values that are in the format "rgb(r,g,b)"
		if (value.startsWith("rgb")) {
			int r, g, b;
			ValueTokenizer t = new ValueTokenizer();
			t.getToken(value.substring(3));
			if (t.currentTok == ValueTokenizer.LTOK_NUMBER) {
				r = (int) t.tokenF;
				t.getToken(null);
				if (t.currentTok == ValueTokenizer.LTOK_NUMBER) {
					g = (int) t.tokenF;
					t.getToken(null);
					if (t.currentTok == ValueTokenizer.LTOK_NUMBER) {
						b = (int) t.tokenF;
						result = (r << 16) + (g << 8) + b;
					}
				}
			}
		}

		// Handle colour values that are in the format #123abc. (Assume that's
		// what it is,
		// if the length is seven characters).
		else if (value.length() == 7) {
			try {
				result = Integer.parseInt(value.substring(1, 7), 16);
			} catch (NumberFormatException e) {
				result = 0xff0000;
			}
		}
		return result;
	}

	private float parseAttrValueFloat(String value) {
		float result;
		try {
			result = Float.parseFloat(value);
		} catch (Exception e) {
			result = 0;
		}
		return result;
	}

	/**
	 * This method converts an SVG arc to an Android Canvas arc.
	 * 
	 * Based on an example found on StackOverflow which in turn was based on:
	 * http://www.java2s.com/Code/Java/2D-Graphics-GUI/
	 * AgeometricpathconstructedfromstraightlinesquadraticandcubicBeziercurvesandellipticalarc
	 * .htm
	 * 
	 * The example initially used turned out to have an error with the coef
	 * calculation, so it is a TODO to revisit this.
	 * 
	 * @param path
	 * @param rx
	 * @param ry
	 * @param theta
	 * @param largeArcFlag
	 * @param sweepFlag
	 * @param x
	 * @param y
	 */

	private void arcTo(Path path, float rx, float ry, float theta,
			boolean largeArcFlag, boolean sweepFlag, float x, float y) {
		// Ensure radii are valid
		if (rx != 0 && ry != 0) {

			// Get the current (x, y) coordinates of the path
			// Point2D p2d = path.getCurrentPoint();

			float x0 = mCurrentX; // (float) p2d.getX();
			float y0 = mCurrentY; // (float) p2d.getY();
			// Compute the half distance between the current and the final point
			float dx2 = (x0 - x) / 2.0f;
			float dy2 = (y0 - y) / 2.0f;
			// Convert theta from degrees to radians
			theta = (float) Math.toRadians(theta % 360f);

			//
			// Step 1 : Compute (x1, y1)
			//
			float x1 = (float) (Math.cos(theta) * (double) dx2 + Math
					.sin(theta) * (double) dy2);
			float y1 = (float) (-Math.sin(theta) * (double) dx2 + Math
					.cos(theta) * (double) dy2);
			// Ensure radii are large enough
			rx = Math.abs(rx);
			ry = Math.abs(ry);
			float Prx = rx * rx;
			float Pry = ry * ry;
			float Px1 = x1 * x1;
			float Py1 = y1 * y1;
			double d = Px1 / Prx + Py1 / Pry;
			if (d > 1) {
				rx = Math.abs((float) (Math.sqrt(d) * (double) rx));
				ry = Math.abs((float) (Math.sqrt(d) * (double) ry));
				Prx = rx * rx;
				Pry = ry * ry;
			}

			//
			// Step 2 : Compute (cx1, cy1)
			//
			double sign = (largeArcFlag == sweepFlag) ? -1d : 1d;

			// float coef = (float) (sign * Math
			// .sqrt(((Prx * Pry) - (Prx * Py1) - (Pry * Px1))
			// / ((Prx * Py1) + (Pry * Px1))));

			double sq = (((Prx * Pry) - (Prx * Py1) - (Pry * Px1)) / ((Prx * Py1) + (Pry * Px1)));

			sq = (sq < 0) ? 0 : sq;
			float coef = (float) (sign * Math.sqrt(sq));

			float cx1 = coef * ((rx * y1) / ry);
			float cy1 = coef * -((ry * x1) / rx);

			//
			// Step 3 : Compute (cx, cy) from (cx1, cy1)
			//
			float sx2 = (x0 + x) / 2.0f;
			float sy2 = (y0 + y) / 2.0f;
			float cx = sx2
					+ (float) (Math.cos(theta) * (double) cx1 - Math.sin(theta)
							* (double) cy1);
			float cy = sy2
					+ (float) (Math.sin(theta) * (double) cx1 + Math.cos(theta)
							* (double) cy1);

			//
			// Step 4 : Compute the angleStart (theta1) and the angleExtent
			// (dtheta)
			//
			float ux = (x1 - cx1) / rx;
			float uy = (y1 - cy1) / ry;
			float vx = (-x1 - cx1) / rx;
			float vy = (-y1 - cy1) / ry;
			float p, n;
			// Compute the angle start
			n = (float) Math.sqrt((ux * ux) + (uy * uy));
			p = ux; // (1 * ux) + (0 * uy)
			sign = (uy < 0) ? -1d : 1d;
			float angleStart = (float) Math.toDegrees(sign * Math.acos(p / n));
			// Compute the angle extent
			n = (float) Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
			p = ux * vx + uy * vy;
			sign = (ux * vy - uy * vx < 0) ? -1d : 1d;
			float angleExtent = (float) Math.toDegrees(sign * Math.acos(p / n));
			if (!sweepFlag && angleExtent > 0) {
				angleExtent -= 360f;
			} else if (sweepFlag && angleExtent < 0) {
				angleExtent += 360f;
			}
			angleExtent %= 360f;
			angleStart %= 360f;

			// Arc2D.Float arc = new Arc2D.Float();
			float _x = cx - rx;
			float _y = cy - ry;
			float _width = rx * 2.0f;
			float _height = ry * 2.0f;

			RectF bounds = new RectF(_x, _y, _x + _width, _y + _height);

			if (path != null) {
				path.arcTo(bounds, angleStart, angleExtent);
			} else {
				addArc(new Arc(bounds, angleStart, angleExtent,
						mParsedAttributes.id));
			}
		}
	}

	/**
	 * Obtain a Tyepface from the assets fonts directory for the given name. The
	 * fonts in the assets directory need to be lowercase. The actual font
	 * looked for will be fontFamilyName appended with .ttf. If the font name
	 * contains space characters, the corresponding characters in the filename
	 * of the assets font should be underscores.
	 * 
	 * @param fontFamilyName
	 *            Font name. Will be made lowercase.
	 * @return A Typeface, or null if not found in assets.
	 */
	private Typeface getTypeface(String fontFamilyName) {
		fontFamilyName = fontFamilyName.replace(' ', '_');
		try {
			return Typeface.createFromAsset(
					mContext.getAssets(),
					ASSETS_FONTS_ROOT_DIRECTORY + "/"
							+ fontFamilyName.toLowerCase() + ".ttf");
		} catch (Exception e) {
			return null;
		}

	}

	// The valueTokenizer is used for:
	// - parsing 'transform' attribute string
	// - parsing rgb(r,g,b) colour string

	/**
	 * Is used for tokenizing attribute values that contain a number of strings
	 * or numbers. Typical examples are: 1. Parsing the 'transform' attribute
	 * value. 2. Parsing the 'rgb(r,g,b) colour value.
	 * 
	 * The types of token returned are LTOK_NUMBER and LTOK_STRING. Characters
	 * such as comma and parentheses are considered whitespace for this purpose
	 * and are ignored. For example, the string rgb(10,20,30) would result in an
	 * LTOK_STRING where tokenStr is "abc", followed by three LTOK_NUMBERS where
	 * tokenF is 10, 20 and then 30.
	 */

	private class ValueTokenizer {

		/**
		 * Matches a number with optional exponent. Not perfect as it'd ideally
		 * be [-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?, but the individual regular
		 * expressions can't have groups because the grouping is used as part of
		 * the tokenization. This expression therefore could match a number with
		 * just an 'e' or even a - or + at the end of it, which is hopefully
		 * okay because it isn't being used in a math expression parser.
		 */
		private static final String REGEXP_NUMBER = "([-+]?[0-9]*[\\.]?[0-9]+[eE]?[-+]?[0-9]*)";
		/** Matches a string. */
		private static final String REGEXP_STRING = "([a-zA-Z_]+)";
		/** Matches whitespace. */
		private static final String REGEXP_SPACE = "([\\s+,\\(\\)]+)";

		private static final String REGEXP_TOKENS = REGEXP_NUMBER + "|"
				+ REGEXP_STRING + "|" + REGEXP_SPACE;

		private static final int LTOK_NUMBER = 1;
		private static final int LTOK_STRING = 2;
		private static final int LTOK_SPACE = 3;
		private static final int LTOK_END = 4;

		// TODO: Ensure that this Pattern only has to be compiled once.
		private Pattern tokRegExp = Pattern.compile(REGEXP_TOKENS);
		private Matcher tokMatcher;

		private float tokenF;
		private int currentTok;
		private String tokenStr;

		int getToken(String inputLine) {

			int resultTok = LTOK_END;

			if (inputLine != null) {
				tokMatcher = tokRegExp.matcher(inputLine);
			}

			if (tokMatcher != null) {

				do {
					resultTok = LTOK_END;
					if (tokMatcher.find()) {

						if (tokMatcher.start(LTOK_NUMBER) != -1) {
							resultTok = LTOK_NUMBER;
							try {
								tokenF = Float.parseFloat(tokMatcher
										.group(resultTok));

							} catch (NumberFormatException e_i) {
								// Log.e(LOGTAG, "Number not parsed to float" );
							}
							break;

						} else if (tokMatcher.start(LTOK_STRING) != -1) {
							resultTok = LTOK_STRING;
							tokenStr = tokMatcher.group(resultTok);
						} else if (tokMatcher.start(LTOK_SPACE) != -1) {
							resultTok = LTOK_SPACE;
						}
					}
				} while (resultTok == LTOK_SPACE);
			}
			currentTok = resultTok;
			return resultTok;
		}

	}

	/**
	 * Used for parsing the 'd' attribute in a 'path' element. Is subtly
	 * different to ValueTokenizer in the following ways: 1. Individual letters
	 * (a-zA-Z) are tokenized rather than complete strings. 2. (And possibly
	 * more differences in future)
	 * 
	 * This class could in theory be merged with ValueTokenizer and an argument
	 * flag implemented to control whether alpha characters are treated as
	 * individual tokens or string tokens, or could remain a separate class but
	 * could extend ValueTokenizer.
	 */

	private class PathTokenizer {

		private static final String REGEXP_NUMBER = "([-+]?[0-9]*[\\.]?[0-9]+([eE][-+]?[0-9]+)?)";
		private static final String REGEXP_LETTER = "([a-zA-Z_])"; // Matches
																	// ONE
																	// character.
		private static final String REGEXP_SPACE = "([\\s+,\\(\\)]+)";

		private static final String REGEXP_TOKENS = REGEXP_NUMBER + "|"
				+ REGEXP_LETTER + "|" + REGEXP_SPACE;

		private static final int LTOK_NUMBER = 1;
		private static final int LTOK_LETTER = 3;
		private static final int LTOK_SPACE = 4;
		private static final int LTOK_END = 5;

		// TODO: Ensure that this Pattern only has to be compiled once.
		private Pattern tokRegExp = Pattern.compile(REGEXP_TOKENS);
		private Matcher tokMatcher;

		private float tokenF;
		private int currentTok;
		private char tokenChar;
		private String tokenStr;

		int getToken(String inputLine) {

			int resultTok = LTOK_END;

			if (inputLine != null) {
				tokMatcher = tokRegExp.matcher(inputLine);
			}

			if (tokMatcher != null) {

				do {
					resultTok = LTOK_END;
					if (tokMatcher.find()) {

						if (tokMatcher.start(LTOK_NUMBER) != -1) {
							resultTok = LTOK_NUMBER;
							try {
								tokenF = Float.parseFloat(tokMatcher
										.group(resultTok));

							} catch (NumberFormatException e_i) {
								// Log.e(LOGTAG, "Number not parsed to float" );
							}
							break;

						} else if (tokMatcher.start(LTOK_LETTER) != -1) {
							resultTok = LTOK_LETTER;
							tokenStr = tokMatcher.group(resultTok);
							tokenChar = tokenStr.charAt(0);
						} else if (tokMatcher.start(LTOK_SPACE) != -1) {
							resultTok = LTOK_SPACE;
						}
					}
				} while (resultTok == LTOK_SPACE);
			}
			currentTok = resultTok;
			return resultTok;
		}

	}

	// ----------------------------------------------------------------------------------
	// Scaling and measurement related methods

	private void setCanvasScaleToSVG(Canvas canvas, View view) {
		canvas.scale(view.getWidth() / mRootSvgWidth, view.getHeight()
				/ mRootSvgHeight);
	}

	/**
	 * Obtain the width specified in the SVG image file. It should be specified
	 * in the image's root svg element.
	 * 
	 * @return
	 */
	public int getDocumentWidth() {
		return Math.round(mRootSvgWidth);
	}

	/**
	 * Obtain the height specified in the SVG image file. It should be specified
	 * in the image's root svg element.
	 * 
	 * @return
	 */
	public int getDocumentHeight() {
		return Math.round(mRootSvgHeight);
	}

	// -------------------------------------------------------------------------------------
	// Code-sequence build functions

	private void addPath(Path path) {
		// This may well have a lot more arguments for stuff that's specific to
		// the path
		// i.e. contained within the <path.../> element but can't be expressed
		// in the Path
		// object, e.g. its ID.
		addIdIfContainsSpecialPrefix();
		// TODO: Should we be doing this check? What if the evaluator expects
		// there to be a
		// Style object?
		if (this.mParsedAttributes.svgStyle != null) {
			addStyle();
		}

		// The transform MUST be the item that goes immediately before the path
		// in the instruction
		// code sequence, as the evaluator expects this. So the order is style,
		// transform, path.

		// TODO: Previously I only added a Matrix for a Path if that Path has a
		// transform. However,
		// this means that any transform belonging specifically to a previous
		// path would still
		// be applied. So at the moment I have to add a Matrix for every Path,
		// which might
		// be wasteful as most Paths might have an identity Matrix (no
		// transform). One
		// way of optimising this could be to add an identity tranform ONLY if
		// the previous
		// path had a non-identity transform.
		// (I think what I meant by this is when you have two path elements in
		// parallel, not nested.)
		// (Or maintain a matrix stack in the evaluator.)
		// Note: The parser uses a Matrix stack. The evaluator uses a Matrix
		// list.

		// if(this.mProperties.transformData!=null){
		addTransform();
		// }

		pathList.add(path);
		addInstruction(INST_PATH);
	}

	private void addBeginGroup(String id) {
		addIdIfContainsSpecialPrefix();
		// All groups need to have Matrix added before them, even if empty
		// Matrix
		addTransform();
		addInstruction(INST_BEGINGROUP);
		// Did the attributes for this <g> element include an id= attribute?
		if (!id.equals("")) {
			subtreeJumpMap.put(id,
					new GroupJumpTo(bytecodeList.size() - 2, pathList.size(),
							matrixList.size() - 1, styleList.size(),
							textstringList.size(), idstringList.size(),
							arcsList.size()));
		}
	}

	private void addEndGroup() {
		addInstruction(INST_ENDGROUP);
	}

	private void addBeginPattern(String id) {
		addIdIfContainsSpecialPrefix();
		// All groups need to have Matrix added before them, even if empty
		// Matrix
		addTransform();
		addInstruction(INST_BEGIN_PATTERN);
		// Did the attributes for this <g> element include an id= attribute?
		if (id != null && !id.equals("")) {
			subtreeJumpMap.put(id,
					new GroupJumpTo(bytecodeList.size() - 2, pathList.size(),
							matrixList.size() - 1, styleList.size(),
							textstringList.size(), idstringList.size(),
							arcsList.size()));
		}
	}

	private void addEndPattern() {
		addInstruction(INST_END_PATTERN);
	}

	private void addTransform() {
		// this.matrixList.add(transform());
		Matrix cm = transform(); // matrixList.get(matrixList.size()-1);

		// new:
		if (!matrixEvStack.empty()) {
			cm.postConcat(matrixEvStack.peek());
		}
		matrixEvStack.push(cm);

		this.matrixList.add(matrixEvStack.peek());
		matrixExistsAtDepth[tagDepth] = true;
		addInstruction(INST_MATRIX);
	}

	private void addStyle() {
		this.styleList.add(mParsedAttributes.svgStyle);
		// styleList.add(styleParseStack.peek());
		addInstruction(INST_STYLE);
	}

	private void addText() {
		addIdIfContainsSpecialPrefix();
		addInstruction(INST_TEXTSTRING);
	}

	private void addArc(Arc arc) {
		// addIdIfContainsSpecialPrefix();// At the moment, Arc itself contains
		// the ID
		arcsList.add(arc);
		addInstruction(INST_ARC);
	}

	private void addIdIfContainsSpecialPrefix() {
		if (mParsedAttributes.id.toLowerCase().startsWith(
				SPECIAL_ID_PREFIX_ANIM)) {
			idstringList.add(mParsedAttributes.id);
			addInstruction(INST_IDSTRING);
		}
	}

	private void addInstruction(byte inst) {
		bytecodeList.add(inst);
	}

	// ------------------------------------------------------------------------------
	// Associated with user's handler

	public void ___setAnimHandler(ITpsvgController animHandler) {
		// this.animHandler = animHandler;
		// parseImageFile(context,resourceID);
	}

	// ------------------------------------------------------------------------------

	// Used by code evaluator:
	Iterator<Matrix> matrixListIterator;
	Iterator<Path> pathListIterator;
	Iterator<SvgStyle> styleListIterator;
	Iterator<Textstring> textstringListIterator;
	Iterator<String> idstringListIterator;
	Iterator<Arc> arcsListIterator;

	Paint currentFillPaint = new Paint();
	Paint currentStrokePaint = new Paint();
	Matrix workingMatrix = new Matrix();

	float[] matrixValues = new float[9];

	public void paintImage(Canvas canvas, String subtreeId, int containerWidth,
			int containerHeight, ITpsvgController animHandler) {
		paintImage(canvas, subtreeId, containerWidth, containerHeight,
				animHandler, false);
	}

	public void paintImage(Canvas canvas, String subtreeId, int containerWidth,
			int containerHeight, ITpsvgController animHandler, boolean fill) {
		paintImage(canvas, subtreeId, containerWidth, containerHeight,
				animHandler, fill, 0);
	}

	public void paintImage(Canvas canvas, String subtreeId, int containerWidth,
			int containerHeight, ITpsvgController animHandler, boolean fill,
			int rotation) {
		float uniformScaleFactor;
		canvas.save();

		if (rotation == 90 || rotation == 270) {
			int temp = containerWidth;
			containerWidth = containerHeight;
			containerHeight = temp;
		}

		if (fill) {
			uniformScaleFactor = Math.max(containerWidth / mRootSvgWidth,
					containerHeight / mRootSvgHeight);
		} else {
			uniformScaleFactor = Math.min(containerWidth / mRootSvgWidth,
					containerHeight / mRootSvgHeight);
		}

		float remainderHeight;
		float remainderWidth;
		remainderHeight = (containerHeight / uniformScaleFactor)
				- mRootSvgHeight;
		remainderWidth = (containerWidth / uniformScaleFactor) - mRootSvgWidth;
		canvas.scale(uniformScaleFactor, uniformScaleFactor);

		switch (rotation) {
		case 90:
			canvas.rotate(90, 0, 0);
			canvas.translate(0, -mRootSvgHeight - remainderHeight);
			break;
		case 180:
			canvas.rotate(180, 0, 0);
			canvas.translate(-mRootSvgWidth - remainderWidth, -mRootSvgHeight
					- remainderHeight);
			break;
		case 270:
			canvas.rotate(270, 0, 0);
			canvas.translate(-mRootSvgWidth - remainderWidth, 0);
			break;
		default:
			break;
		}

		paintImage(canvas, subtreeId, remainderWidth, remainderHeight,
				rotation, animHandler, false);
		canvas.restore();
	}

	/**
	 * Render the SVG image to the supplied Canvas. The entire SVG image can be
	 * drawn or, optionally, a specific 'subtree' is only drawn by specifying
	 * the ID of a containing &lt;g&gt; group element in the original SVG
	 * document. The image is drawn to the Canvas in its original width / height
	 * ratio, using dimensions from the original SVG document. The caller is
	 * responsible for applying appropriate scale to the Canvas to ensure proper
	 * fit and / or apply stretch effects. The purpose of the remainderWidth and
	 * remainderHeight arguments is for use with "anchor" attributes that can be
	 * applied to shapes and paths, which is a special attribute provided by
	 * this library and is not part of the SVG standard.
	 * 
	 * @param canvas
	 *            The canvas on which to make the drawing calls.
	 * @param subtreeId
	 *            ID of a subtree in the SVG image to render. The ID needs to be
	 *            the ID of a &lt;g&gt; group element. Pass null to render
	 *            entire image.
	 * @param remainderWidth
	 *            In terms of SVG document width units, the amount of additional
	 *            width available in the container.
	 * @param remainderHeight
	 *            In terms of SVG document width units, the amount of additional
	 *            height available in the container.
	 * @param rotation
	 *            Rotation in degrees. This parameter is passed to the renderer
	 *            (rather than simply rotating the Canvas beforehand) because it
	 *            is used also for paths that are conditionally shown depending
	 *            on rotation.
	 * @param animHandler
	 *            Animation callback handler
	 * @param isDrawingPatternTile
	 *            Normally should be false. Set to true if the call is being
	 *            made specifically for drawing a single pattern tile, usually
	 *            during the creation of an SVGPatternShader. In this case the
	 *            subtreeId would be the ID of the &lt;pattern&gt; element. The
	 *            result is that the vector data inside the &lt;pattern&gt;
	 *            element is drawn to Canvas as if it were regular image data.
	 */
	public synchronized void paintImage(Canvas canvas, String subtreeId,
			float remainderWidth, float remainderHeight, int rotation,
			ITpsvgController animHandler, boolean isDrawingPatternTile) {

		Canvas mCanvas = canvas;
		SVGPath workingPath = new SVGPath();
		Path carryPath = new SVGPath();
		int gDepth = 1;
		boolean mSkipPattern = false;

		codePtr = 0;
		// TBD: I think that previous Matrix is remaining attached to something,
		// so have to instance
		// a new one each time
		// currentMatrix.reset();
		workingMatrix = new Matrix();
		matrixListIterator = matrixList.listIterator();
		pathListIterator = pathList.listIterator();
		styleListIterator = styleList.listIterator();
		textstringListIterator = textstringList.listIterator();
		idstringListIterator = idstringList.listIterator();
		arcsListIterator = arcsList.iterator();
		boolean doSpecialIdCallbackForNextElement = false;
		int animIteration;
		String animId;
		Matrix animMatrix = new Matrix();
		Matrix shaderMatrix = new Matrix();

		if (subtreeId != null) {
			// TODO: It would be better if the GroupJumpTo object did all of
			// this for us, or
			// even better if all the data structures were somehow grouped
			// together into a parent
			// class and this was one of its methods.
			if (subtreeJumpMap.containsKey(subtreeId)) {
				GroupJumpTo jumpTo = subtreeJumpMap.get(subtreeId);
				codePtr = jumpTo.bytecodePosition;// -1;
				matrixListIterator = matrixList
						.listIterator(jumpTo.matrixListPosition);
				pathListIterator = pathList
						.listIterator(jumpTo.pathListPosition);
				styleListIterator = styleList
						.listIterator(jumpTo.styleListPosition);
				textstringListIterator = textstringList
						.listIterator(jumpTo.textstringListPosition);
				idstringListIterator = idstringList
						.listIterator(jumpTo.idstringListPosition);
				arcsListIterator = arcsList
						.listIterator(jumpTo.arcsListPosition);
			}
		}

		// WARNING - currentFillPaint and currentStrokePaint make references to
		// things such as
		// Gradients and their associated shaders.
		// Paint currentFillPaint = new Paint();
		// Paint currentStrokePaint = new Paint();
		// Matrix currentMatrix = new Matrix();
		// SvgStyle currentStyle = null;

		if (bytecodeArr == null) {
			return;
		}

		if (animHandler != null) {
			animHandler.setRemainderWidthOrHeight(remainderWidth,
					remainderHeight);
		}

		while (bytecodeArr[codePtr] != INST_END && gDepth > 0) {

			switch (bytecodeArr[codePtr]) {

			case INST_PATH:
				workingPath.rewind();
				workingPath.addPath(pathListIterator.next());
				workingPath.addPath(carryPath);
				workingPath.transform(workingMatrix);
				carryPath.rewind();

				// p = pathListIterator.next();
				// If we assume a Matrix is included for every path, even if
				// it's an empty matrix,
				// then we always pop the matrix on path creation. On the
				// other
				// hand if
				// Matrix is only inserted for some paths then we somehow
				// need
				// to know whether
				// to pop the matrix or just peek at the tip cumulative
				// matrix.
				// Could have a flag:
				// pathHasMatrix -- set if a path instruction immediately
				// follows matrix inst.

				animId = null;
				animIteration = 0;

				animMatrix.reset();

				if (workingPath.usesRemainderWidthOrHeight()) {
					if (workingPath.getAnchorRight()) {
						animMatrix.postTranslate(remainderWidth, 0);
					}
					if (workingPath.getAnchorBottom()) {
						animMatrix.postTranslate(0, remainderHeight);
					}
					if (workingPath.getStretchToRemainderWidth()) {
						RectF bounds = new RectF();
						workingPath.computeBounds(bounds, false);
						animMatrix.postScale(
								(bounds.right - bounds.left + remainderWidth)
										/ (bounds.right - bounds.left), 1,
								bounds.left, 0);
					}
					if (workingPath.getStretchToRemainderHeight()) {
						RectF bounds = new RectF();
						workingPath.computeBounds(bounds, false);
						animMatrix.postScale(1,
								(bounds.bottom - bounds.top + remainderHeight)
										/ (bounds.bottom - bounds.top), 0,
								bounds.top);
					}
					workingPath.transform(animMatrix);
				}

				do {

					if (doSpecialIdCallbackForNextElement == true) {
						if (animId == null) {
							animId = idstringListIterator.next();
						}
						if (animHandler != null) {
							animMatrix.reset();

							doSpecialIdCallbackForNextElement = animHandler
									.animElement(animId, animIteration++,
											workingPath, animMatrix,
											currentStrokePaint,
											currentFillPaint);
							workingPath.transform(animMatrix);

						} else {
							doSpecialIdCallbackForNextElement = false;
						}
					}

					shaderMatrix = null;
					if (currentFillPaint != null) {

						Matrix copyShaderMatrix = null;
						if (currentFillPaint.getShader() != null) {
							shaderMatrix = new Matrix();
							currentFillPaint.getShader().getLocalMatrix(
									shaderMatrix);
							copyShaderMatrix = new Matrix(shaderMatrix); // Deep
																			// copy.
							copyShaderMatrix.postConcat(workingMatrix);
							copyShaderMatrix.postConcat(animMatrix);
							currentFillPaint.getShader().setLocalMatrix(
									copyShaderMatrix);
						}
						if (!mSkipPattern
								&& workingPath.getVisibleOnRotation(rotation)) {
							mCanvas.drawPath(workingPath, currentFillPaint);
						}
						if (shaderMatrix != null) {
							currentFillPaint.getShader().setLocalMatrix(
									shaderMatrix); // Restore shader's
													// original
													// Matrix
						}
					}

					if (currentStrokePaint != null) {

						workingMatrix.getValues(matrixValues);
						float storedStrokeWidth = currentStrokePaint
								.getStrokeWidth();
						currentStrokePaint
								.setStrokeWidth(storedStrokeWidth
										* (Math.abs(matrixValues[Matrix.MSCALE_Y]) + Math
												.abs(matrixValues[Matrix.MSCALE_X]) / 2));
						// Paint scaledPaint = new
						// Paint(currentStrokePaint);
						// scaledPaint.setStrokeWidth(scaledPaint.getStrokeWidth()
						// * ( ( Math.abs(matrixValues[Matrix.MSCALE_Y]) +
						// Math.abs(matrixValues[Matrix.MSCALE_X]) ) / 2 )
						// );

						// //float curStrkWidth =
						// scaledPaint.getStrokeWidth();
						// //float newStrkWidth = (
						// Math.abs(f[Matrix.MSCALE_Y])
						// + Math.abs(f[Matrix.MSCALE_X]) ) / 2.0f ;
						// //newStrkWidth = curStrkWidth * newStrkWidth;
						// //scaledPaint.setStrokeWidth(newStrkWidth);

						Matrix copyShaderMatrix = null;

						// TODO: Does this block now go after the
						// mCanvas.drawPath?
						if (currentStrokePaint.getShader() != null) {
							shaderMatrix = new Matrix();
							currentStrokePaint.getShader().getLocalMatrix(
									shaderMatrix);
							copyShaderMatrix = new Matrix(shaderMatrix); // Deep
																			// copy.
							copyShaderMatrix.postConcat(workingMatrix);
							copyShaderMatrix.postConcat(animMatrix);
							currentStrokePaint.getShader().setLocalMatrix(
									copyShaderMatrix);
						}

						if (!mSkipPattern
								&& workingPath.getVisibleOnRotation(rotation)) {
							mCanvas.drawPath(workingPath, currentStrokePaint);
						}

						if (shaderMatrix != null) {
							currentStrokePaint.getShader().setLocalMatrix(
									shaderMatrix); // Restore shader's
													// original
													// Matrix
						}

						currentStrokePaint.setStrokeWidth(storedStrokeWidth);
					}

				} while (doSpecialIdCallbackForNextElement == true);
				break;

			case INST_MATRIX:
				workingMatrix = matrixListIterator.next();
				break;

			case INST_BEGINGROUP:
				// Similar notes regarding Matrix for path.
				// If last instruction was a matrix instruction, then set
				// flag:
				// groupHasMatrix
				// Actually, no. Assume a matrix for all groups.
				// Assume that a Matrix instruction went before this one!
				gDepth++;
				break;

			case INST_ENDGROUP:
				gDepth--;
				if (gDepth == 1 && subtreeId != null) {
					// The loop will now terminate and finish rendering,
					// because
					// the specified SVG
					// image fragment has been drawn.
					gDepth = 0;
				}
				break;

			case INST_BEGIN_PATTERN:
				if (!isDrawingPatternTile) {
					mSkipPattern = true;
				}
				gDepth++;
				break;

			case INST_END_PATTERN:
				mSkipPattern = false;
				gDepth--;
				if (isDrawingPatternTile && gDepth == 1 && subtreeId != null) {
					// The loop will now terminate and finish rendering,
					// because
					// the specified SVG
					// image fragment has been drawn.
					gDepth = 0;
				}
				break;

			case INST_STYLE:
				SvgStyle currentStyle = styleListIterator.next();
				if (currentStyle.hasStroke) {
					// IMPORTANT: Making copy as opposed to a reference.
					// This
					// enables
					// currentStrokePaint to be modified without risk of
					// making
					// changes to
					// things that strokePaint references, e.g. Gradients.
					// Same applies to currentFillPaint.
					// currentStrokePaint = new Paint(s.strokePaint);
					currentStrokePaint = currentStyle.strokePaint;
				} else {
					currentStrokePaint = null;
				}
				if (currentStyle.hasFill) {
					// currentFillPaint = new Paint(s.fillPaint);
					currentFillPaint = currentStyle.fillPaint;
				} else {
					currentFillPaint = null;
				}
				break;

			case INST_TEXTSTRING:
				Textstring ts = textstringListIterator.next();
				if (!ts.getVisibleOnRotation(rotation)) {
					// Discard the ID
					if (doSpecialIdCallbackForNextElement == true) {
						idstringListIterator.next();
						doSpecialIdCallbackForNextElement = false;
					}
					break;
				}
				workingMatrix.getValues(matrixValues);
				// We might have already got the values for currentMatrix
				// before, to save
				// on this operation.
				// Paint scaledPaint = new Paint(currentStrokePaint);
				// scaledPaint.setStrokeWidth(scaledPaint.getStrokeWidth() *
				// ( (
				// f[Matrix.MSCALE_Y] + f[Matrix.MSCALE_X] ) / 2 ) );

				float translatePoints[] = new float[2];
				translatePoints[0] = ts.x;
				translatePoints[1] = ts.y;
				workingMatrix.mapPoints(translatePoints);

				animId = null;
				animIteration = 0;
				animMatrix.reset();

				if (ts.mAnchorRight) {
					animMatrix.postTranslate(remainderWidth, 0);
				}

				do {

					if (doSpecialIdCallbackForNextElement == true) {
						if (animId == null) {
							animId = idstringListIterator.next();
						}
						if (animHandler != null) {

							doSpecialIdCallbackForNextElement = animHandler
									.animTextElement(animId, animIteration++,
											animMatrix, currentStrokePaint,
											currentFillPaint, ts,
											translatePoints[0],
											translatePoints[1]);
						
						} else {
							doSpecialIdCallbackForNextElement = false;
						}
					}

					mCanvas.save();
					mCanvas.concat(animMatrix);
					mCanvas.concat(workingMatrix);

					if (currentStrokePaint != null && !mSkipPattern) {

						float savedTextSize = currentStrokePaint.getTextSize();

						if (ts.mTextLength > 0
								&& ts.mSizeToFitTextLength == true) {
							currentStrokePaint
									.setTextSize(Util.bestFitValueTextSize(
											ts.mTextLength, savedTextSize,
											ts.string.toString()));
						}

						mCanvas.drawText(ts.string, 0, ts.string.length(),
								ts.x, ts.y, currentStrokePaint);

						currentStrokePaint.setTextSize(savedTextSize);
					}
					if (currentFillPaint != null && !mSkipPattern) {

						float savedTextSize = currentFillPaint.getTextSize();

						if (ts.mTextLength > 0
								&& ts.mSizeToFitTextLength == true) {
							currentFillPaint
									.setTextSize(Util.bestFitValueTextSize(
											ts.mTextLength, savedTextSize,
											ts.string.toString()));
						}

						mCanvas.drawText(ts.string, 0, ts.string.length(),
								ts.x, ts.y, currentFillPaint);

						currentFillPaint.setTextSize(savedTextSize);

					}

					mCanvas.restore();
				} while (doSpecialIdCallbackForNextElement == true);

				break;

			case INST_IDSTRING:
				doSpecialIdCallbackForNextElement = true;
				break;

			case INST_ARC:
				Arc arc = arcsListIterator.next();
				if (animHandler != null) {
					animHandler.arcParams(arc.animId, carryPath,
							arc.angleStart, arc.angleExtent, arc.bounds);
				} else {
					carryPath.addArc(arc.bounds, arc.angleStart,
							arc.angleExtent);
				}
				break;

			}
			codePtr++;

		}
	}

	/**
	 * Style class holds the stroke and fill Paint objects for each path. It
	 * could later on also hold the Path too. It could also contain the Matrix.
	 * Such Matrix and Paint objects can always be null if not required, so
	 * there shouldn't be any memory penalty. Style class could be a subclass of
	 * e.g. svgPath class.
	 * 
	 */
	public static class SvgStyle {

		public Paint fillPaint;
		public Paint strokePaint;

		boolean hasFill;
		boolean hasStroke;

		public float masterOpacity;
		public float fillOpacity;
		public float strokeOpacity;

		PatternFill mFillPattern;
		PatternFill mStrokePattern;

		/**
		 * Create a new SvgStyle object with all the default initial values
		 * applied in accordance with SVG standards. Useful reference for style
		 * initial properties and inheritance rules:
		 * http://www.w3.org/TR/SVG/painting.html TODO: Still need to ensure
		 * that all properties are to the correct initial values.
		 */
		public SvgStyle() {
			fillPaint = new Paint();
			strokePaint = new Paint();
			fillPaint.setStyle(Paint.Style.FILL);
			strokePaint.setStyle(Paint.Style.STROKE);
			fillPaint.setColor(0xff000000);
			strokePaint.setColor(0xff000000);
			masterOpacity = 1;
			fillOpacity = 1;
			strokeOpacity = 1;
			fillPaint.setAntiAlias(true);
			strokePaint.setAntiAlias(true);
			fillPaint.setStrokeWidth(1f);
			strokePaint.setStrokeWidth(1f);
			fillPaint.setTextAlign(Paint.Align.LEFT);
			strokePaint.setTextAlign(Paint.Align.LEFT);
			fillPaint.setTextSize(0.02f);
			strokePaint.setTextSize(0.02f);
			fillPaint.setTextScaleX(1f);
			strokePaint.setTextScaleX(1f);
			fillPaint.setTypeface(Typeface.DEFAULT);
			strokePaint.setTypeface(Typeface.DEFAULT);
			hasFill = true;
			hasStroke = false;
		}

		/**
		 * Constructor that accepts an existing SvgStyle object to copy. It is
		 * this method that defines what style properties an element inherits
		 * from the parent, or are not inherited and are defaulted to some
		 * value. It is provided so that a stack of SvgStyle can be maintained,
		 * with each child inheriting from the parent initially but with various
		 * properties overridden afterwards whenever a graphical element
		 * explicitly provides style properties. It is this method that
		 * determines what inherits and what doesn't -- if any style must always
		 * default to something rather than inherit, then this method should set
		 * that property to an explicit value.
		 * 
		 * @param s
		 */
		public SvgStyle(SvgStyle s) {
			this.fillPaint = new Paint(s.fillPaint);
			this.strokePaint = new Paint(s.fillPaint);
			this.fillPaint.setStyle(Paint.Style.FILL);
			this.strokePaint.setStyle(Paint.Style.STROKE);
			this.fillPaint.setColor(s.fillPaint.getColor());
			this.strokePaint.setColor(s.strokePaint.getColor());
			this.masterOpacity = s.masterOpacity;
			this.fillOpacity = s.fillOpacity;
			this.strokeOpacity = s.strokeOpacity;
			this.fillPaint.setAntiAlias(true);
			this.strokePaint.setAntiAlias(true);
			this.fillPaint.setStrokeWidth(1f);
			this.strokePaint.setStrokeWidth(1f);
			this.fillPaint.setTextAlign(s.fillPaint.getTextAlign());
			this.strokePaint.setTextAlign(s.strokePaint.getTextAlign());
			this.fillPaint.setTextSize(s.fillPaint.getTextSize());
			this.strokePaint.setTextSize(s.strokePaint.getTextSize());
			this.fillPaint.setTextScaleX(s.fillPaint.getTextScaleX());
			this.strokePaint.setTextScaleX(s.strokePaint.getTextScaleX());
			this.fillPaint.setTypeface(Typeface.DEFAULT);
			this.strokePaint.setTypeface(Typeface.DEFAULT);
			this.hasFill = s.hasFill;
			this.hasStroke = s.hasStroke;
		}
	}

	/**
	 * Class that stores the current positions in all of the list structures
	 * when parsing begins for a given group element in the SVG file.
	 * 
	 * @author trev
	 * 
	 */
	private class GroupJumpTo {
		// TODO: It would be much better I think if this object were to contain
		// all code to
		// perform the 'jump to'. So, it could have a method jumpTo, which could
		// accept the
		// group ID to jump to. Perhaps it could all be static and contain the
		// jumpTo map within?
		int bytecodePosition;
		int pathListPosition;
		int matrixListPosition;
		int styleListPosition;
		int textstringListPosition;
		int idstringListPosition;
		int arcsListPosition;

		public GroupJumpTo(int bytecodePosition, int pathListPosition,
				int matrixListPosition, int styleListPosition,
				int textstringListPosition, int idstringListPosition,
				int arcsListPosition) {
			this.bytecodePosition = bytecodePosition;
			this.pathListPosition = pathListPosition;
			this.matrixListPosition = matrixListPosition;
			this.styleListPosition = styleListPosition;
			this.textstringListPosition = textstringListPosition;
			this.idstringListPosition = idstringListPosition;
			this.arcsListPosition = arcsListPosition;
		}
	}

	/**
	 * Represents a single line of text. Encapsulates data parsed from the SVG
	 * file needed to render the text to Canvas.
	 * 
	 */
	public class Textstring {
		// TODO: Require an index into either a list of Strings or a single
		// large char[] buffer.
		// The char[] buffer could be contained within this object - perhaps
		// that makes most sense.
		public final float x, y;
		public char[] charBuf;
		public int charLength;
		private boolean mAnchorRight;
		private boolean mAnchorBottom;
		private boolean mSizeToFitTextLength = false;
		public int mTextLength = 0;
		public StringBuilder string;
		protected ArrayList<Integer> mVisibleOnRotations;

		public Textstring(float x, float y, char[] src, int srcPos, int length) {
			this.x = x;
			this.y = y;
			this.charLength = length;
			this.charBuf = new char[length + 1];
			System.arraycopy(src, srcPos, this.charBuf, 0, length);
			this.charBuf[length] = 0;
			this.string = new StringBuilder();
			this.string.append(src, srcPos, length);
		}

		public void setTextLength(int textLength) {
			mTextLength = textLength;
		}

		public int getTextLength() {
			return mTextLength;
		}

		public void addVisibleOnRotations(ArrayList<Integer> rotations) {
			if (rotations != null && rotations.size() > 0) {
				if (mVisibleOnRotations == null) {
					mVisibleOnRotations = new ArrayList<Integer>();
				}
				mVisibleOnRotations.addAll(rotations);
			}
		}

		public boolean getVisibleOnRotation(int rotation) {
			return mVisibleOnRotations == null ? true : mVisibleOnRotations
					.contains(rotation);
		}
	}

	private class Arc {
		RectF bounds;
		float angleStart;
		float angleExtent;
		String animId;

		public Arc(RectF bounds, float angleStart, float angleExtent,
				String animId) {
			this.bounds = bounds;
			this.angleStart = angleStart;
			this.angleExtent = angleExtent;
			this.animId = animId;
		}
	}
}

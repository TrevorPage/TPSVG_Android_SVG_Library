package co.uk.hardfault.dragonsvg.dom;

import java.util.ArrayList;

import android.graphics.Canvas;

public class GroupNode extends Node {
	
	private ArrayList<Node> mChildren;

	public GroupNode() {
		mChildren = new ArrayList<Node>();
	}
	
	public void addChildNode(Node node) {
		mChildren.add(node);
	}
	
	@Override
	public void draw(Canvas canvas) {
		for(Node node : mChildren) {
			node.draw(canvas);
		}
	}

}

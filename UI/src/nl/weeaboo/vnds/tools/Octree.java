package nl.weeaboo.vnds.tools;

/**
 * Octree.java -----------
 * 
 * @author Sascha L. Teichmann (sascha@intevation.de)
 * @version 0.1 date 21:31:02 Mi 07-Mrz-2001
 */

public class Octree {
	
	static public class Node {
		Node parent;
		Node children[] = new Node[8];

		int midRed, midGreen, midBlue;

		int totalRed, totalGreen, totalBlue;

		byte id; // which child of parent?
		byte census; // used children
		byte level; // level in tree

		int colorNumber; // index in color table

		int numberUnique;
		int numberColors;

		public Node() {
		}

		public Node(int midRed, int midGreen, int midBlue, Node parent, byte id) {
			this.midRed = midRed;
			this.midGreen = midGreen;
			this.midBlue = midBlue;

			this.parent = parent;
			this.level = (byte) (parent == null ? 0 : parent.level + 1);
			this.id = id;
		}
	}

	Node root;

	public static final int MAX_TREE_DEPTH = 8;
	public static final int MAX_NODES = 266817;
	public static final int MAX_RGB = 255;

	// prepare square tables
	public static final int SQUARES[] = new int[MAX_RGB * 2 + 1];

	public int shift[] = new int[MAX_TREE_DEPTH + 1];

	static {
		for (int i = -MAX_RGB; i <= MAX_RGB; ++i)
			SQUARES[i + MAX_RGB] = i * i;

	}

	int depth; // depth of the tree
	int nodes; // number of nodes in tree
	int colors; // nodes of colors

	int pruningTreshold;
	int nextPruningTreshold;

	int colormap[];

	int cdistance;
	int cred, cgreen, cblue;
	int colorNumber;

	public Octree() {
		root = new Node((MAX_RGB + 1) >> 1, (MAX_RGB + 1) >> 1, (MAX_RGB + 1) >> 1, null, (byte) 0);

		root.parent = root;
		root.numberColors = Integer.MAX_VALUE;
	}

	public void quantize(int pixels[], int width, int maxColors) {

		depth = 1;
		for (int mc = maxColors; mc != 0; mc >>= 2, ++depth)
			;

		int numberPixels = pixels.length;
		int maxShift;

		for (maxShift = 4 * 8; numberPixels != 0; maxShift--)
			numberPixels >>= 1;

		for (int level = 0; level <= depth; level++) {
			shift[level] = maxShift;
			if (maxShift != 0)
				maxShift--;
		}

		sortInImage(pixels);

		reduction(maxColors);
		assignment(pixels, maxColors);
	}

	void sortInImage(int pixels[]) {

		int r, g, b, color;
		for (int i = 0; i < pixels.length; ++i) {
			color = pixels[i];
			r = (color >> 16) & 0xff;
			g = (color >> 8) & 0xff;
			b = color & 0xff;
			sortInRGB(r, g, b);
		}
	}

	void sortInRGB(int red, int green, int blue) {

		// prune one level if tree is too large
		if (nodes > MAX_NODES) {
			pruneLevel(root);
			--depth;
		}

		// descent the tree, start with root
		Node node = root;

		for (int level = 1; level <= depth; ++level) {

			int id = (red > node.midRed ? 1 : 0) | (green > node.midGreen ? 2 : 0)
					| (blue > node.midBlue ? 4 : 0);

			// was this branch visited before ?
			if (node.children[id] == null) {
				int bisect = (1 << (MAX_TREE_DEPTH - level)) >> 1;
				Node n = new Node(node.midRed + ((id & 1) != 0 ? bisect : -bisect), node.midGreen
						+ ((id & 2) != 0 ? bisect : -bisect), node.midBlue
						+ ((id & 4) != 0 ? bisect : -bisect), node, (byte) id);
				++nodes;
				node.census |= 1 << id; // register new child
				node.children[id] = n;

				if (level == depth)
					++colors;
			}

			node = node.children[id]; // descent to next level
			node.numberColors += 1 << shift[level];
		}

		++node.numberUnique;
		node.totalRed += red;
		node.totalGreen += green;
		node.totalBlue += blue;
	}

	void pruneLevel(Node node) {

		if (node.census != 0)
			for (int i = 0; i < node.children.length; ++i)
				if ((node.census & (1 << i)) != 0)
					pruneLevel(node.children[i]);

		if (node.level == depth)
			pruneChild(node);
	}

	void reduction(int numberColors) {

		nextPruningTreshold = 1;

		while (colors > numberColors) {
			pruningTreshold = nextPruningTreshold;
			nextPruningTreshold = root.numberColors;
			colors = 0;
			reduce(root);
		}
	}

	void reduce(Node node) {

		if (node.census != 0)
			for (int i = 0; i < node.children.length; ++i)
				if ((node.census & (1 << i)) != 0)
					reduce(node.children[i]);

		if (node.numberColors <= pruningTreshold)
			pruneChild(node);
		else {
			if (node.numberUnique > 0)
				++colors;
			if (node.numberColors < nextPruningTreshold)
				nextPruningTreshold = node.numberColors;
		}
	}

	void pruneChild(Node node) {

		Node parent = node.parent;

		// parent.children[node.id] = null;
		parent.census &= ~(1 << node.id);
		parent.numberUnique += node.numberUnique;
		parent.totalRed += node.totalRed;
		parent.totalGreen += node.totalGreen;
		parent.totalBlue += node.totalBlue;
		--nodes;
	}

	void assignment(int pixels[], int maxColors) {

		colormap = new int[maxColors];
		colors = 0;
		colorMap(root);

		int r, g, b, color;

		for (int i = 0; i < pixels.length; ++i) {
			color = pixels[i];
			r = (color >> 16) & 0xff;
			g = (color >> 8) & 0xff;
			b = color & 0xff;
			pixels[i] = colormap[rgb2idx(r, g, b)];
		}
	}

	void colorMap(Node node) {

		if (node.census != 0)
			for (int i = 0; i < node.children.length; ++i)
				if ((node.census & (1 << i)) != 0)
					colorMap(node.children[i]);

		if (node.numberUnique != 0) {
			int uh = node.numberUnique >> 1;
			int r = (node.totalRed + uh) / node.numberUnique;
			int g = (node.totalGreen + uh) / node.numberUnique;
			int b = (node.totalBlue + uh) / node.numberUnique;

			node.colorNumber = colors;
			colormap[colors++] = (r << 16) | (g << 8) | b;
		}
	}

	int rgb2idx(int red, int green, int blue) {

		Node node = root;

		for (;;) {
			byte id = (byte) ((red > node.midRed ? 1 : 0) | (green > node.midGreen ? 2 : 0) | (blue > node.midBlue ? 4
					: 0));

			if ((node.census & (1 << id)) == 0)
				break;

			node = node.children[id];
		}

		cdistance = Integer.MAX_VALUE;
		cred = red;
		cgreen = green;
		cblue = blue;

		closestColor(node.parent);

		return colorNumber;
	}

	void closestColor(Node node) {
		if (node.census != 0)
			for (int i = 0; i < node.children.length; i++)
				if ((node.census & (1 << i)) != 0)
					closestColor(node.children[i]);

		if (node.numberUnique != 0) {

			int color = colormap[node.colorNumber];
			int r = (color >> 16) & 0xff;
			int g = (color >> 8) & 0xff;
			int b = color & 0xff;

			int rD = r - cred + MAX_RGB;
			int gD = g - cgreen + MAX_RGB;
			int bD = b - cblue + MAX_RGB;

			int distance = SQUARES[rD] + SQUARES[gD] + SQUARES[bD];

			if (distance < cdistance) {
				cdistance = distance;
				colorNumber = node.colorNumber;
			}
		}
	}
}

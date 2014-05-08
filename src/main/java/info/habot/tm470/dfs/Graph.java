package info.habot.tm470.dfs;
import info.habot.tm470.dao.pojo.NetworkNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class Graph {
	public NetworkNode rootNode;
	public NetworkNode targetNode;
	public ArrayList<NetworkNode> nodes = new ArrayList<NetworkNode>();
	public float[][] adjMatrix;// Edges will be represented as adjacency Matrix
	int size;

	private HashMap<String, Float> distanceBetweenNodes;
	
	private static final int MAX_NODES = 25;
	private static final float MAX_DISTANCE_ALONG = 100;

	private int nodeCount;
	private float distanceAlong;

	public Graph() {
		this.nodeCount = 0;
		this.distanceAlong = 0;
		this.distanceBetweenNodes = new HashMap<String, Float>();
	}

	public void setRootNode(NetworkNode n) {
		this.rootNode = n;
	}

	public void setTargetNode(NetworkNode n) {
		this.targetNode = n;
	}

	public NetworkNode getRootNode() {
		return this.rootNode;
	}

	public void addNode(NetworkNode n) {
		nodes.add(n);
	}

	// This method will be called to make connect two nodes
	public void connectNode(NetworkNode start, NetworkNode end) {
		if (adjMatrix == null) {
			size = nodes.size();
			adjMatrix = new float[size][size];
		}

		int startIndex = nodes.indexOf(start);
		int endIndex = nodes.indexOf(end);
		adjMatrix[startIndex][endIndex] = 1;
		adjMatrix[endIndex][startIndex] = 1;
	}
	
	public void setDistanceBetweenNodes (NetworkNode start, NetworkNode end, float distance) {
		distanceBetweenNodes.put((start.getNodeId() + "/" + end.getNodeId()), distance);
		connectNode(start, end);
	}

	private NetworkNode getUnvisitedChildNode(NetworkNode n) {
		int index = nodes.indexOf(n);
		int j = 0;
		while (j < size) {
			if (adjMatrix[index][j] == 1
					&& ((NetworkNode) nodes.get(j)).visited == false) {
				return (NetworkNode) nodes.get(j);
			}
			j++;
		}
		return null;
	}

	// DFS traversal of a tree is performed by the dfs() function
	public void dfs() {
		// DFS uses Stack data structure
		Stack<NetworkNode> s = new Stack<NetworkNode>();
		s.push(this.rootNode);
		rootNode.visited = true;
		printNode(rootNode);
		while (!s.isEmpty()) {
			NetworkNode n = (NetworkNode) s.peek();
			NetworkNode child = getUnvisitedChildNode(n);
			if (child != null) {
				child.visited = true;
				printNode(child);
				s.push(child);
				if (child == this.targetNode) {
					System.out.println("Target Node '" + child.getNodeId()
							+ "' reached.");
					
					// Save route
				}
				this.nodeCount++;
				
				this.distanceAlong = this.distanceAlong + distanceBetweenNodes.get(n.getNodeId() + "/" + child.getNodeId());
				System.out.println("distanceAlong=" + this.distanceAlong);
				if (this.distanceAlong > MAX_DISTANCE_ALONG) {
					System.out.println("MAX_DISTANCE_ALONG");
					break;
				}

				// Add some heuristics to guide the search
				// Set a limit to the number of nodes traversed.
				if (nodeCount == MAX_NODES) {
					System.out.println("MAX_NODES");
					break;
				}
				
			} else {
				s.pop();
			}
		}
		// Clear visited property of nodes
		clearNodes();
	}

	// Utility methods for clearing visited property of node
	private void clearNodes() {
		int i = 0;
		while (i < size) {
			NetworkNode n = (NetworkNode) nodes.get(i);
			n.visited = false;
			i++;
		}
	}

	// Utility methods for printing the node's label
	private void printNode(NetworkNode n) {
		System.out.print(n.getNodeId() + " ");
	}
}

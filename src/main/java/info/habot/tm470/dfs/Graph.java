package info.habot.tm470.dfs;

import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class Graph {
	public NetworkNode rootNode;
	public NetworkNode targetNode;
	public String targetRoadName;
	private boolean targetRoadFound;
	public ArrayList<NetworkNode> nodes;
	public float[][] adjMatrix;// Edges will be represented as adjacency Matrix
	int size;

	private HashMap<String, NetworkLink> distanceBetweenNodes;
	public HashMap<Integer, NetworkLink> networkLinkMap;
	public NetworkLink currentLink;
	public HashMap<String, NetworkLink> toNetworkLinkMap;

	private static final int MAX_NODES = 15000;
	private static final float MAX_DISTANCE_ALONG = 10000;

	private int nodeCount;
	private double distanceAlong;

	public Graph() {
		this.nodeCount = 0;
		this.distanceAlong = 0;
		this.distanceBetweenNodes = new HashMap<String, NetworkLink>();
		this.nodes = new ArrayList<NetworkNode>();
		this.networkLinkMap = new HashMap<Integer, NetworkLink>();
		this.toNetworkLinkMap = new HashMap<String, NetworkLink>();
		this.targetRoadFound = false;
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
	public void connectNode(NetworkNode start, NetworkNode end, int linkId) {
		if (adjMatrix == null) {
			size = nodes.size();
			adjMatrix = new float[size][size];
		}

		int startIndex = nodes.indexOf(start);
		int endIndex = nodes.indexOf(end);

		adjMatrix[startIndex][endIndex] = 1;
		adjMatrix[endIndex][startIndex] = 1;
	}

	public void setNetworkLink(NetworkNode start, NetworkNode end,
			NetworkLink networkLink) {
		distanceBetweenNodes.put((start.getNodeId() + "/" + end.getNodeId()),
				networkLink);
		connectNode(start, end, networkLink.getLinkId());
	}

	private NetworkNode getUnvisitedChildNode(NetworkNode n) {
		
		NetworkNode networkNode;
		
		int index = nodes.indexOf(n);
		int j = 0;
		while (j < size) {
			currentLink = this.networkLinkMap.get(adjMatrix[index][j]);
		
			if (adjMatrix[index][j] == 1
					&& ((NetworkNode) nodes.get(j)).visited == 0) {
				
				networkNode = (NetworkNode) nodes.get(j);
				return networkNode;
			}
			j++;
		}
		return null;
	}

	// DFS traversal of a tree is performed by the dfs() function
	public void dfs() {
		// DFS uses Stack data structure
		Stack<NetworkNode> s = new Stack<NetworkNode>();
		if (this.rootNode != null) {

			s.push(this.rootNode);

			rootNode.visited = 1;
			printNode(rootNode);

			while (!s.isEmpty()) {
				NetworkNode n = (NetworkNode) s.peek();
				NetworkNode child = getUnvisitedChildNode(n);
				if (child != null) {
					
					child.visited = 1;
					printNode(child);
					s.push(child);
					
					if (child.getNodeId().equals(this.targetNode.getNodeId())) {
						System.out.println("Target Node '" + child.getNodeId()
								+ "' reached. Distance=" + this.distanceAlong);

						// Save route
						break;
					}
					this.nodeCount++;
					
					// Check the maximum distance along route
					this.distanceAlong = this.distanceAlong + child.getLocation().distance(rootNode.getLocation());
//					System.out.println("distanceAlong=" + this.distanceAlong);
					if (this.distanceAlong > MAX_DISTANCE_ALONG) {
						System.out.println("MAX_DISTANCE_ALONG = " + this.distanceAlong);
						break;
					}

//					System.out.println("nodeCount=" + nodeCount );
					
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
		} else {
			System.out.println("root null");
		}

		// Clear visited property of nodes
		clearNodes();
	}

	// Utility methods for clearing visited property of node
	private void clearNodes() {
		int i = 0;
		while (i < size) {
			NetworkNode n = (NetworkNode) nodes.get(i);
			n.visited = 0;
			i++;
		}
	}

	// Utility methods for printing the node's label
	private void printNode(NetworkNode n) {

		String locationName = "";
		try {
			locationName = this.toNetworkLinkMap.get(n.getNodeId()).getLocationName();
			if (locationName.indexOf(targetRoadName) > 0) {
				targetRoadFound = true;
			}
		} catch (Exception ex) {
//			System.out.println("locationName NULL");
		}
		
		if (locationName.indexOf(targetRoadName) > 0) {
			System.out.println(n.getNodeId() + " " + locationName + ", targetRoadFound=" + targetRoadFound);
		}
	}
}

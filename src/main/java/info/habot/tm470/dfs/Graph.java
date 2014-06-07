package info.habot.tm470.dfs;

import info.habot.tm470.dao.drools.RouteState;
import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;
import info.habot.tm470.dao.pojo.StrategicEvent;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.event.KnowledgeRuntimeEventManager;
import org.kie.internal.logger.KnowledgeRuntimeLogger;
import org.kie.internal.logger.KnowledgeRuntimeLoggerFactory;

public class Graph {
	
	static Logger log = Logger.getLogger(Graph.class.getName());
	
	private KieSession kSession;
	private NetworkNode rootNode;
	private NetworkNode targetNode;
	private ArrayList<NetworkNode> nodes;
	private float[][] adjMatrix;// Edges will be represented as adjacency Matrix. These are the links
	private int size;

	private HashMap<String, NetworkLink> distanceBetweenNodes;
	private HashMap<Integer, NetworkLink> networkLinkMap;
	private StrategicEvent strategicEvent;
	private HashMap<String, NetworkLink> toNetworkLinkMap;

	private static final int MAX_NODES = 15;
	private static final float MAX_DISTANCE_ALONG = 15000;
	
//	private KnowledgeRuntimeLogger logger;
	private static final String KIE_LOG_FILENAME = "RouteDeterminationLog";
	
	private ArrayList<Integer> alernativeRoute;
	private String kie_log_file;

	private int nodeCount;
	private Double distanceAlong;

	public Graph() {
		this.nodeCount = 0;
		this.distanceAlong = 0.0;
		this.distanceBetweenNodes = new HashMap<String, NetworkLink>();
		this.nodes = new ArrayList<NetworkNode>();
		this.networkLinkMap = new HashMap<Integer, NetworkLink>();
		this.toNetworkLinkMap = new HashMap<String, NetworkLink>();
		this.alernativeRoute = new ArrayList<Integer>();
		this.strategicEvent = new StrategicEvent();
		
		kSession = null;
		try {
			// load up the knowledge base
			KieServices ks = KieServices.Factory.get();
			KieContainer kContainer = ks.getKieClasspathContainer();
			kSession = kContainer.newKieSession("ksession-route-rules");

			if (kSession == null) {
				log.debug("kSession is null");
			} else {
				kSession.setGlobal( "MAX_NODES",
						MAX_NODES );
				kSession.setGlobal( "MAX_DISTANCE_ALONG",
						MAX_DISTANCE_ALONG );
				
//				logger = KnowledgeRuntimeLoggerFactory.newFileLogger((KnowledgeRuntimeEventManager) kSession, KIE_LOG_FILENAME);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
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
	public void connectNode(NetworkNode start, NetworkNode end, int linkId) throws Exception {
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
			NetworkLink networkLink) throws Exception {
		distanceBetweenNodes.put((start.getNodeId() + "/" + end.getNodeId()),
				networkLink);
		connectNode(start, end, networkLink.getLinkId());
	}

	private NetworkNode getUnvisitedChildNode(NetworkNode n) {
		
		NetworkNode networkNode;
		
		int index = nodes.indexOf(n);
		int j = 0;
		while (j < size) {
//			currentLink = this.networkLinkMap.get(adjMatrix[index][j]);
		
			if (adjMatrix[index][j] == 1
					&& ((NetworkNode) nodes.get(j)).visited == 0) {
				
				networkNode = (NetworkNode) nodes.get(j);
				return networkNode;
			}
			j++;
		}
		return null;
	}
	
	/**
	 * @param NetworkNode - A node that needs to be excluded. Usually as it belongs to the default route and can't be part of an alternative.
	 */
	@SuppressWarnings("unused")
	public void excludeNode(NetworkNode n) {
		
		int index = nodes.indexOf(n);		
		nodes.get(index).visited = 1;
	}

	/**
	 * DFS traversal of a tree is performed by the dfs() function
	 * 
	 * @return alternative route
	 */
	public ArrayList<Integer> dfs() {
		// DFS uses Stack data structure
		Stack<NetworkNode> s = new Stack<NetworkNode>();
		
		kSession.insert( rootNode );
		RouteState routeState = new RouteState();
		FactHandle fhState = kSession.insert( routeState );
		FactHandle fhChild = kSession.insert( new NetworkNode() );
		
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
						log.debug("Target Node '" + child.getNodeId()
								+ "' reached. Distance=" + this.distanceAlong);

						// Save route					
						break;
					}
					this.nodeCount++;
					
					kSession.setGlobal( "nodeCount",
							nodeCount );
					routeState.resetState();
					kSession.update( fhState, routeState );
					kSession.update( fhChild, child );
					
					// Check the maximum distance along route
					this.distanceAlong = this.distanceAlong + child.getLocation().distance(rootNode.getLocation());
					
					log.debug ("distanceAlong=" + this.distanceAlong);
					if (this.distanceAlong > MAX_DISTANCE_ALONG) {
						log.debug ("MAX_DISTANCE_ALONG = " + this.distanceAlong);
						break;
					}

					kSession.setGlobal( "distanceAlong",
							this.distanceAlong );
					
//					log.debug ("nodeCount=" + nodeCount );
					
					// Add some heuristics to guide the search
					// Set a limit to the number of nodes traversed.
					if (nodeCount == MAX_NODES) {
						log.debug ("MAX_NODES");
						break;
					}
					
//					kSession.fireAllRules();
					log.debug("STATE=" + routeState.toString());
					
//					if (routeState.isRoute_complete() == true) {
//						break;
//					}

				} else {
					s.pop();
				}
			}
		} else {
			log.debug ("root null");
		}

		// Clear visited property of nodes
		clearNodes();
		
		return alernativeRoute;
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
			alernativeRoute.add(this.toNetworkLinkMap.get(n.getNodeId()).getLinkId());
		} catch (Exception ex) {
			log.debug ("locationName NULL");
		}
		
		log.debug (n.getNodeId() + " " + locationName);
	}

	/**
	 * @return kie log file
	 */
	public boolean readKieLogFile() {
	   kie_log_file = null;
	   File file = new File(KIE_LOG_FILENAME + ".log"); //for ex foo.txt
	   try {
	       FileReader reader = new FileReader(file);
	       char[] chars = new char[(int) file.length()];
	       reader.read(chars);
	       kie_log_file = new String(chars);
	       reader.close();
	       
	       file.delete();  // Once we have the data dlete the physical file as it is not needed.
	       
	   } catch (IOException e) {
	       e.printStackTrace();
	       return false;
	   }
	   return true;
	}

	public String getExplantion() {
		return kie_log_file;
	}
	
	public HashMap<Integer, NetworkLink> getNetworkLinkMap() {
		return networkLinkMap;
	}

	public void setNetworkLinkMap(HashMap<Integer, NetworkLink> networkLinkMap) {
		this.networkLinkMap = networkLinkMap;
	}

	public HashMap<String, NetworkLink> getToNetworkLinkMap() {
		return toNetworkLinkMap;
	}

	public void setToNetworkLinkMap(HashMap<String, NetworkLink> toNetworkLinkMap) {
		this.toNetworkLinkMap = toNetworkLinkMap;
	}
	public StrategicEvent getStrategicEvent() {
		return strategicEvent;
	}
	public String getExplanation() {
		return kie_log_file;
	}

	public void closeKieSession () {
		kSession.dispose(); // Statefull sessions *must* be properly disposed
		
/*		logger.close();
		
		if (readKieLogFile()) {
			kie_log_file = "<explanation>" + kie_log_file + "</explanation>";
		}
		*/
	}
}

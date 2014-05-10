package info.habot.tm470.dao.drools;

import java.util.HashMap;
import java.util.List;

import info.habot.tm470.dao.EventImpl;
import info.habot.tm470.dao.NetworkLinkImpl;
import info.habot.tm470.dao.NetworkNodeImpl;
import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;
import info.habot.tm470.dao.pojo.StrategicEvent;
import info.habot.tm470.dfs.Graph;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.event.KnowledgeRuntimeEventManager;
import org.kie.internal.logger.KnowledgeRuntimeLogger;
import org.kie.internal.logger.KnowledgeRuntimeLoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
* RouteDeterminationTest
* 
* @author Ian Moffitt
* @version 0.1
* @see www.habot.info
*/
public class RouteDeterminationTest {

	public static void main(String[] args) {

		KieSession kSession = null;
		Graph graph=new Graph();
		
		HashMap<Integer, NetworkLink> networkLinkMap = new HashMap<Integer, NetworkLink>();
		HashMap<String, NetworkLink> toNetworkLinkMap = new HashMap<String, NetworkLink>();
		
		try {
			// load up the knowledge base
			KieServices ks = KieServices.Factory.get();
			KieContainer kContainer = ks.getKieClasspathContainer();
			kSession = kContainer.newKieSession("ksession-route-rules");

			if (kSession == null) {
				System.out.println("kSession is null");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		
//		KnowledgeRuntimeLogger logger = KnowledgeRuntimeLoggerFactory.newFileLogger((KnowledgeRuntimeEventManager) kSession, "RouteDeterminationLog");
		
		// Get all network links and nodes
		@SuppressWarnings("resource")
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:Beans.xml");
		
		NetworkNodeImpl networkNodeImpl = 
			      (NetworkNodeImpl)applicationContext.getBean("networkNodeImpl");
		NetworkLinkImpl networkLinkImpl = 
			      (NetworkLinkImpl)applicationContext.getBean("networkLinkImpl");
		EventImpl eventImpl = 
			      (EventImpl)applicationContext.getBean("eventImpl");
		
		// Get all network nodes
		List<NetworkNode> networkNodeList = networkNodeImpl.listNetworkNode();
		
		// Add to knowledge base
		for (NetworkNode networkNode : networkNodeList) {
			kSession.insert( networkNode );
			graph.addNode(networkNode);
		}
		
//		NetworkNode networkNode1 = new NetworkNode();
//		networkNode1.setNodeId("1030584");
//		graph.addNode(networkNode1);
//		NetworkNode networkNode2 = new NetworkNode();
//		networkNode2.setNodeId("1030584");
//		graph.addNode(networkNode2);
		
		// Get all network links
		List<NetworkLink> networkLinkList = networkLinkImpl.listNetworkLink();
		
		HashMap<String, NetworkNode> networkNodeMap = networkNodeImpl.getNetworkNodes();
		
		// Add to knowledge base
		for (NetworkLink networkLink : networkLinkList) {
			kSession.insert( networkLink );
			// Don't link the link where the event is located
			// 110001601 = exit slip onto M6 southbound
			// 110001701 = M6 northbound exit slip
			// 110005901 = entry slip sb	
//			if ( (networkLink.getLinkId() != 110001601) &&					
//					(networkLink.getLinkId() != 110005901)){
				
				graph.setNetworkLink(networkNodeMap.get(networkLink.getFromNodeIdentifier()),networkNodeMap.get(networkLink.getToNodeIdentifier()), networkLink);
				
				networkLinkMap.put(networkLink.getLinkId(), networkLink);
				toNetworkLinkMap.put(networkLink.getToNodeIdentifier(), networkLink);
//			}
		}
		
		graph.networkLinkMap = networkLinkMap;
		graph.toNetworkLinkMap = toNetworkLinkMap;
		graph.targetRoadName = "M56";
		
//		NetworkLink networkLink = new NetworkLink ();
//		networkLink.setLinkId(103058201);
//		networkLink.setFromNodeIdentifier("1030584");
//		networkLink.setToNodeIdentifier("1030582");
//		networkLink.setLocationName("A23 northbound within the A281 junction");
//		networkLink.setRoadNumber("A23");
//		graph.setNetworkLink(networkNodeMap.get(networkLink.getFromNodeIdentifier()),networkNodeMap.get(networkLink.getToNodeIdentifier()), networkLink);

		NetworkNode rootNetworkNode = new NetworkNode();
		NetworkNode targetNetworkNode = new NetworkNode();
		rootNetworkNode = networkNodeMap.get("1100015");
		targetNetworkNode = networkNodeMap.get("1990894");
		
		if (rootNetworkNode != null) {
			graph.setRootNode(rootNetworkNode);
			graph.setTargetNode(targetNetworkNode);
		
			graph.dfs();
		}
		
		
		System.out.println("- - - END - - -");
		
		/*
		// Get all active events
		List<StrategicEvent> eventList = eventImpl.getActiveEvents();
		
		// Add to knowledge base
		for (StrategicEvent strategicEvent : eventList) {
			kSession.insert( strategicEvent );
		}
		
//		kSession.fireAllRules();
		kSession.dispose();             // Statefull sessions *must* be properly disposed of...
//		logger.close();
 * 
 * */
		/*
		//Lets create nodes as given as an example in the article
		NetworkNode nA=new NetworkNode("001");
		NetworkNode nB=new NetworkNode("002");
		NetworkNode nC=new NetworkNode("003");
		NetworkNode nD=new NetworkNode("004");
		NetworkNode nE=new NetworkNode("005");
		NetworkNode nF=new NetworkNode("006");

		//Create the graph, add nodes, create edges between nodes
//		Graph graph=new Graph();
		graph.addNode(nA);
		graph.addNode(nB);
		graph.addNode(nC);
		graph.addNode(nD);
		graph.addNode(nE);
		graph.addNode(nF);
		
		graph.setRootNode(nA);
		graph.setTargetNode(nF);
		
		// Loop through all the links and get the node connectivity.
		graph.connectNode(nA,nB);
		graph.connectNode(nA,nC);
		graph.connectNode(nA,nD);
		graph.connectNode(nB,nE);
		graph.connectNode(nB,nF);
		graph.connectNode(nC,nF);
		
		//Perform the traversal of the graph
		System.out.println("DFS Traversal of a tree is ------------->");
		
		
		graph.dfs();*/
	}
}

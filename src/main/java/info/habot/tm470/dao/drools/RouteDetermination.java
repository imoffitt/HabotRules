package info.habot.tm470.dao.drools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.habot.tm470.dao.EventImpl;
import info.habot.tm470.dao.FusedSensorDataImpl;
import info.habot.tm470.dao.NetworkLinkImpl;
import info.habot.tm470.dao.NetworkNodeImpl;
import info.habot.tm470.dao.SqlLookupBean;
import info.habot.tm470.dao.pojo.FusedSensorData;
import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;
import info.habot.tm470.dao.pojo.Route;
import info.habot.tm470.dao.pojo.StrategicEvent;
import info.habot.tm470.dfs.Graph;

import org.apache.log4j.Logger;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.event.KnowledgeRuntimeEventManager;
import org.kie.internal.logger.KnowledgeRuntimeLogger;
import org.kie.internal.logger.KnowledgeRuntimeLoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * RouteDetermination. Sets up knowledge base and performs a depth based search to determine alternative routes to avoid the event.
 * 
 * @author Ian Moffitt
 * @version 0.1
 * @see www.habot.info
 */
public class RouteDetermination {

//	private KieSession kSession;
	private Graph graph;
	private HashMap<Integer, NetworkLink> networkLinkMap;
	private HashMap<String, NetworkLink> toNetworkLinkMap;
	private HashMap<String, NetworkNode> networkNodeMap;
	
	private ArrayList<Integer> defaultRoute;  // Route where event is located

	private ApplicationContext applicationContext;
	private SqlLookupBean sqlLookupBean;
	private KnowledgeRuntimeLogger logger;
	
	private static final int MAX_LINK_EXTENT = 20;
	
	private NetworkNodeImpl networkNodeImpl;
	private NetworkLinkImpl networkLinkImpl;
	private EventImpl eventImpl;
	private FusedSensorDataImpl fusedSensorDataImpl;
	
	static Logger log = Logger.getLogger(RouteDetermination.class.getName());

	public RouteDetermination() {

//		kSession = null;
		graph = new Graph();
/*
		try {
			// load up the knowledge base
			KieServices ks = KieServices.Factory.get();
			KieContainer kContainer = ks.getKieClasspathContainer();
			kSession = kContainer.newKieSession("ksession-route-rules");

			if (kSession == null) {
				log.debug("kSession is null");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
*/
		applicationContext = new ClassPathXmlApplicationContext(
				"classpath*:Beans.xml");

		sqlLookupBean = (SqlLookupBean) applicationContext
				.getBean("sqlLookupBean");

		networkLinkMap = new HashMap<Integer, NetworkLink>();
		toNetworkLinkMap = new HashMap<String, NetworkLink>();
		defaultRoute =  new ArrayList<Integer>();
		
		// Get all network links and nodes
		networkNodeImpl = (NetworkNodeImpl) applicationContext
				.getBean("networkNodeImpl");
		networkLinkImpl = (NetworkLinkImpl) applicationContext
				.getBean("networkLinkImpl");
		eventImpl = (EventImpl) applicationContext
				.getBean("eventImpl");
		fusedSensorDataImpl = (FusedSensorDataImpl) applicationContext
				.getBean("fusedSensorImpl");
	}

	/**
	 * Sets up knowledge base and performs a depth based search to determine alternative routes to avoid the event.
	 */
	public void createKnowledgeBase(int eventId) {

//		logger = KnowledgeRuntimeLoggerFactory.newFileLogger((KnowledgeRuntimeEventManager) kSession, "RouteDeterminationLog");

		StrategicEvent strategicEvent = eventImpl.getStrategicEvent(eventId);

		// Get all network nodes
		List<NetworkNode> networkNodeList = networkNodeImpl.listNetworkNode();

		// Add to knowledge base
		for (NetworkNode networkNode : networkNodeList) {
//			kSession.insert(networkNode);
			graph.addNode(networkNode);
		}

		// Get all network links
		List<NetworkLink> networkLinkList = networkLinkImpl.listNetworkLink();
		networkNodeMap = networkNodeImpl.getNetworkNodes();

		// Add to knowledge base
		for (NetworkLink networkLink : networkLinkList) {
//			kSession.insert(networkLink);

			graph.setNetworkLink(
					networkNodeMap.get(networkLink.getFromNodeIdentifier()),
					networkNodeMap.get(networkLink.getToNodeIdentifier()),
					networkLink);

			networkLinkMap.put(networkLink.getLinkId(), networkLink);
			toNetworkLinkMap
					.put(networkLink.getToNodeIdentifier(), networkLink);
		}

		
		NetworkNode affectedNodeStart = getStartOfAffectedLink(strategicEvent.getLink_id());
		NetworkNode affectedNodeEnd = getEndOfAffectedLink(strategicEvent.getLink_id());

		if ((affectedNodeStart != null) && (affectedNodeEnd != null)) {
			graph.setNetworkLinkMap(networkLinkMap);
			graph.setToNetworkLinkMap(toNetworkLinkMap);
			graph.setRootNode(affectedNodeStart);
			graph.setTargetNode(affectedNodeEnd);
			addRouteToStrategicEvent(graph.dfs(), eventId);
			graph.closeKieSession ();
		}

		log.debug("- - - END - - -");
		
//		kSession.fireAllRules();
	}

	public void endSession() {
		
//		kSession.dispose(); // Statefull sessions *must* be properly disposed
//		logger.close();
	}

	/**
	 * @param eventLinkId
	 * @return start node id for the re route
	 */
	private NetworkNode getStartOfAffectedLink(int eventLinkId) {

		NetworkLink networkLink = networkLinkMap.get(eventLinkId);
		String fromNode = networkLink.getFromNodeIdentifier();

		boolean findLink = false;
		int count = 0;
		while (findLink == false) {
			// How many links are the to node connected to?
			ArrayList<Map<String, Object>> lstLinksAttached = sqlLookupBean
					.getValue("select linkId, fromNodeIdentifier, toNodeIdentifier from Network_Links where toNodeIdentifier="
							+ fromNode);

			// Found the end of the route where link affected by event.
			if (lstLinksAttached.size() > 1) {
				findLink = true;
				return networkNodeMap.get(fromNode);
			} else if (lstLinksAttached.size() == 1) {
				Map<String, Object> mapLinksAffected = lstLinksAttached.get(0);
				Integer link = (Integer) mapLinksAffected
						.get("fromNodeIdentifier");
				fromNode = String.valueOf(link);
				defaultRoute.add((Integer) mapLinksAffected
						.get("linkId"));
			} else {
				findLink = false;
			}
//			System.out.println("Cnt=" + count + ", findLink=" + findLink);
			count++;
			// Stop endless looping
			if (count >= MAX_LINK_EXTENT) {
				break;
			}
		}
		return null;
	}

	/**
	 * Find the node at the end of the road section where the event is located.
	 * In theory where the node diverges into another direction is the node that
	 * we want to divert to.
	 * 
	 * @param eventLinkId
	 * @return target node id for the re route
	 */
	private NetworkNode getEndOfAffectedLink(int eventLinkId) {

		NetworkLink networkLink = networkLinkMap.get(eventLinkId);
		String toNode = networkLink.getToNodeIdentifier();

		boolean findLink = false;
		int count = 0;
		while (findLink == false) {
			// How many links are the to node connected to?
			ArrayList<Map<String, Object>> lstLinksAttached = sqlLookupBean
					.getValue("select linkId, fromNodeIdentifier, toNodeIdentifier from Network_Links where fromNodeIdentifier="
							+ toNode);

			// Found the end of the route where link affected by event.
			if (lstLinksAttached.size() > 1) {
				findLink = true;
				return networkNodeMap.get(toNode);
			} else if (lstLinksAttached.size() == 1) {
				Map<String, Object> mapLinksAffected = lstLinksAttached.get(0);
				Integer link = (Integer) mapLinksAffected
						.get("toNodeIdentifier");
				toNode = String.valueOf(link);
				defaultRoute.add((Integer) mapLinksAffected
						.get("linkId"));
			} else {
				findLink = false;
			}
			// System.out.println("Cnt=" + count + ", findLink=" + findLink);
			count++;
			// Stop endless looping
			if (count >= MAX_LINK_EXTENT) {
				break;
			}
		}

		return null;
	}
	
	/**
	 * @param fromDate
	 * @return length in metres of the route
	 */
	public Float getTravelTimeForRoute(ArrayList<Integer> thisRoute, String fromDate) {
		float routeTime = 0;
		
		if (null != defaultRoute) {
			for (Integer linkId : thisRoute) {
				List<FusedSensorData> lstFusedSensorData = fusedSensorDataImpl.getFusedSensorData(linkId, fromDate);
				
				for (FusedSensorData fusedSensorData : lstFusedSensorData) {
					routeTime = routeTime + fusedSensorData.getTravelTime();
				}
			}
		}
		
		return new Float(routeTime);
	}
	

	/**
	 * @param alernativeRoute
	 * @param eventId
	 */
	private void addRouteToStrategicEvent (ArrayList<Integer> alernativeRoute, int eventId) {
		
		log.debug("DefaultTravel Time = " + getTravelTimeForRoute(defaultRoute, "17-JAN-2014 00.00.27"));
		log.debug("Alt Route Travel Time = " + getTravelTimeForRoute(alernativeRoute, "17-JAN-2014 00.00.27"));
		
		Route routeDefault = new Route();
		Route routeAlternative = new Route();
		
		if (alernativeRoute.isEmpty()) {
			routeAlternative.setLink_list(null);
			routeAlternative.setEvent_id(eventId);
			routeAlternative.setIs_valid(0);
		} else {
			routeAlternative.setLink_list(alernativeRoute);
			routeAlternative.setEvent_id(eventId);
			routeAlternative.setIs_valid(1);
		}
		
		routeDefault.setLink_list(this.defaultRoute);
		routeDefault.setEvent_id(eventId);
		routeDefault.setIs_valid(1);	
	}
}

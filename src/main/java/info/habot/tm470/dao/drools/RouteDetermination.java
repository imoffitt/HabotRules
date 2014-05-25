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
import info.habot.tm470.dao.VMSUnitEquipmentImpl;
import info.habot.tm470.dao.pojo.FusedSensorData;
import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;
import info.habot.tm470.dao.pojo.Route;
import info.habot.tm470.dao.pojo.StrategicEvent;
import info.habot.tm470.dao.pojo.VMSUnitEquipment;
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
 * RouteDetermination. Sets up knowledge base and performs a depth based search
 * to determine alternative routes to avoid the event.
 * 
 * @author Ian Moffitt
 * @version 0.1
 * @see www.habot.info
 */
public class RouteDetermination {

	private Graph graph;
	private HashMap<Integer, NetworkLink> networkLinkMap;
	private HashMap<String, NetworkLink> toNetworkLinkMap;
	private HashMap<String, NetworkNode> networkNodeMap;

	private ArrayList<Integer> defaultRoute; // Route where event is located

	private ApplicationContext applicationContext;
	private SqlLookupBean sqlLookupBean;

	private static final int MAX_LINK_EXTENT = 200;

	private NetworkNodeImpl networkNodeImpl;
	private NetworkLinkImpl networkLinkImpl;
	private EventImpl eventImpl;
	private FusedSensorDataImpl fusedSensorDataImpl;
	private VMSUnitEquipmentImpl vMSUnitEquipmentImpl;

	static Logger log = Logger.getLogger(RouteDetermination.class.getName());

	public RouteDetermination() {

		graph = new Graph();

		applicationContext = new ClassPathXmlApplicationContext(
				"classpath*:Beans.xml");

		sqlLookupBean = (SqlLookupBean) applicationContext
				.getBean("sqlLookupBean");

		networkLinkMap = new HashMap<Integer, NetworkLink>();
		toNetworkLinkMap = new HashMap<String, NetworkLink>();
		defaultRoute = new ArrayList<Integer>();

		// Get all network links and nodes
		networkNodeImpl = (NetworkNodeImpl) applicationContext
				.getBean("networkNodeImpl");
		networkLinkImpl = (NetworkLinkImpl) applicationContext
				.getBean("networkLinkImpl");
		eventImpl = (EventImpl) applicationContext.getBean("eventImpl");
		fusedSensorDataImpl = (FusedSensorDataImpl) applicationContext
				.getBean("fusedSensorImpl");
		vMSUnitEquipmentImpl = (VMSUnitEquipmentImpl) applicationContext
				.getBean("vMSUnitEquipmentImpl");
	}

	/**
	 * Sets up knowledge base and performs a depth based search to determine
	 * alternative routes to avoid the event.
	 */
	public StrategicEvent evaluateRoute(StrategicEvent strategicEvent) {

		// Get all network nodes
		List<NetworkNode> networkNodeList = networkNodeImpl.listNetworkNode();

		// Add to knowledge base
		for (NetworkNode networkNode : networkNodeList) {
			// kSession.insert(networkNode);
			graph.addNode(networkNode);
		}

		// Get all network links
		List<NetworkLink> networkLinkList = networkLinkImpl.listNetworkLink();
		networkNodeMap = networkNodeImpl.getNetworkNodes();

		// Add to knowledge base
		for (NetworkLink networkLink : networkLinkList) {
			// kSession.insert(networkLink);

			graph.setNetworkLink(
					networkNodeMap.get(networkLink.getFromNodeIdentifier()),
					networkNodeMap.get(networkLink.getToNodeIdentifier()),
					networkLink);

			networkLinkMap.put(networkLink.getLinkId(), networkLink);
			toNetworkLinkMap
					.put(networkLink.getToNodeIdentifier(), networkLink);
		}

		NetworkNode affectedNodeStart = getStartOfAffectedLink(strategicEvent
				.getLink_id());
		NetworkNode affectedNodeEnd = getEndOfAffectedLink(strategicEvent
				.getLink_id());

		if ((affectedNodeStart != null) && (affectedNodeEnd != null)) {
			graph.setNetworkLinkMap(networkLinkMap);
			graph.setToNetworkLinkMap(toNetworkLinkMap);
			graph.setRootNode(affectedNodeStart);
			graph.setTargetNode(affectedNodeEnd);
			strategicEvent = addRouteToStrategicEvent(graph.dfs(),
					strategicEvent);

			VMSUnitEquipment vMSUnitEquipment = getDecisionPointVMS(strategicEvent, affectedNodeStart);
			if (null != vMSUnitEquipment) {
				strategicEvent.setvMSUnitEquipment(vMSUnitEquipment);
			}

			graph.closeKieSession();
		}

		log.debug("- - - END - - -");

		return strategicEvent;
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
				defaultRoute.add((Integer) mapLinksAffected.get("linkId"));
			} else {
				findLink = false;
			}
			// log.debug("Cnt=" + count + ", findLink=" + findLink);
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
				defaultRoute.add((Integer) mapLinksAffected.get("linkId"));
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
	 * Finds the link that contains a VMS downstream of the start of the decision point.
	 * 
	 * @param strategicEvent
	 * @param affectedNodeStart
	 * @return the VMS equipment ahead of the decision point
	 */
	private VMSUnitEquipment getDecisionPointVMS(StrategicEvent strategicEvent,
			NetworkNode affectedNodeStart) {

		String fromNode = affectedNodeStart.getNodeId();
		VMSUnitEquipment vMSUnitEquipment = null;

		boolean findVMS = false;
		int count = 0;
		while (findVMS == false) {
			// How many links are the to node connected to?
			ArrayList<Map<String, Object>> lstLinksAttached = sqlLookupBean
					.getValue("select linkId, fromNodeIdentifier, toNodeIdentifier from Network_Links where fromNodeIdentifier="
							+ fromNode);
			
//			log.debug("SQL=select linkId, fromNodeIdentifier, toNodeIdentifier from Network_Links where fromNodeIdentifier="
//							+ fromNode);

			Map<String, Object> mapLinksAffected = lstLinksAttached.get(0);
			Integer link = (Integer) mapLinksAffected.get("toNodeIdentifier");
			fromNode = String.valueOf(link);
			Integer linkId = (Integer) mapLinksAffected.get("linkId");

			// If the link is part of the default route then ignore and move on.
			boolean isLinkInAlternativeRoute = false;
			boolean isLinkInDefaultRoute = false;
			for (Integer alternativeLinkId : strategicEvent
					.getAlternativeRoute().getLink_list()) {
				// Link is part of alternative route so is not suitable
				if (alternativeLinkId == linkId) {
					isLinkInAlternativeRoute = true;
					break;
				}
			}

			if (isLinkInAlternativeRoute == false) {
				for (Integer defaultlinkId : strategicEvent.getDefaultRoute()
						.getLink_list()) {
					// Link is part of default route so is not suitable
					if (defaultlinkId == linkId) {
						isLinkInDefaultRoute = true;
						break;
					}
				}
			}

			// If current link is not in default or alternative route then check
			// to see if there is a VMS on it
			if (isLinkInAlternativeRoute == false
					&& isLinkInDefaultRoute == false) {
				List<VMSUnitEquipment> lstVMSUnits = vMSUnitEquipmentImpl
						.getVMSUnitOnLink(linkId);
				
				log.debug("looking for VMS on link : " + linkId);
				if (!lstVMSUnits.isEmpty()) {
					findVMS = true;
					// Just get the first one
					vMSUnitEquipment = lstVMSUnits.get(0);
					break;
				}
			}

			log.debug("Cnt=" + count + ", findVMS=" + findVMS);
			count++;
			// Stop endless looping
			if (count >= MAX_LINK_EXTENT) {
				break;
			}
		}

		return vMSUnitEquipment;
	}

	/**
	 * @param fromDate
	 * @return length in metres of the route
	 */
	public Float getTravelTimeForRoute(ArrayList<Integer> thisRoute,
			String fromDate) {
		float routeTime = 0;

		if (null != defaultRoute) {
			for (Integer linkId : thisRoute) {
				List<FusedSensorData> lstFusedSensorData = fusedSensorDataImpl
						.getFusedSensorData(linkId, fromDate);

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
	private StrategicEvent addRouteToStrategicEvent(
			ArrayList<Integer> alernativeRoute, StrategicEvent strategicEvent) {

		log.debug("DefaultTravel Time = "
				+ getTravelTimeForRoute(defaultRoute, "17-JAN-2014 00.00.27"));
		log.debug("Alt Route Travel Time = "
				+ getTravelTimeForRoute(alernativeRoute, "17-JAN-2014 00.00.27"));

		Route routeDefault = new Route();
		Route routeAlternative = new Route();

		if (alernativeRoute.isEmpty()) {
			routeAlternative.setLink_list(null);
			// routeAlternative.setEvent_id(eventId);
			routeAlternative.setIs_valid(0);
		} else {
			routeAlternative.setLink_list(alernativeRoute);
			// routeAlternative.setEvent_id(eventId);
			routeAlternative.setIs_valid(1);
		}

		routeDefault.setLink_list(this.defaultRoute);
		// routeDefault.setEvent_id(eventId);
		routeDefault.setIs_valid(1);

		strategicEvent.setAlternativeRoute(routeAlternative);
		strategicEvent.setDefaultRoute(routeDefault);

		return strategicEvent;
	}

	/**
	 * @return the explanation on how the rules determined the outcome
	 */
	public String getExplantion() {
		return graph.getExplanation();
	}

	/**
	 * @return stringBuffer containing the alternative route
	 */
	public StringBuffer getAlternativeRouteExplantion(Route alternativeRoute) {

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<AlternativeRouteExplantion>");

		for (Integer LinkId : alternativeRoute.getLink_list()) {

			NetworkLink networkLink = networkLinkMap.get(LinkId);

			if (networkLink != null) {
				stringBuffer.append("<LinkId>" + LinkId + "</LinkId>"
						+ "<locationName>" + networkLink.getLocationName()
						+ "</locationName>" + "<carriageway>"
						+ networkLink.getCarriageway() + "</carriageway>"
						+ "<lane>" + networkLink.getLane() + "</lane>"
						+ "<lengthAffected>" + networkLink.getLengthAffected()
						+ "</lengthAffected>" + "<directionBound>"
						+ networkLink.getDirectionBound() + "</directionBound>"
						+ "<roadNumber>" + networkLink.getRoadNumber()
						+ "</roadNumber>" + "<linearElementNature>"
						+ networkLink.getLinearElementNature()
						+ "</linearElementNature>" + "<fromDistanceAlong>"
						+ networkLink.getFromDistanceAlong()
						+ "</fromDistanceAlong>" + "<fromNodeIdentifier>"
						+ networkLink.getFromNodeIdentifier()
						+ "</fromNodeIdentifier>" + "<fromNodeType>"
						+ networkLink.getFromNodeType() + "</fromNodeType>"
						+ "<toDistanceAlong>"
						+ networkLink.getToDistanceAlong()
						+ "</toDistanceAlong>" + "<toNodeIdentifier>"
						+ networkLink.getToNodeIdentifier()
						+ "</toNodeIdentifier>" + "<toNodeType>"
						+ networkLink.getToNodeType() + "</toNodeType>");
			}
		}
		stringBuffer.append("</AlternativeRouteExplantion>");

		return stringBuffer;
	}

	/**
	 * @return stringBuffer containing the default route
	 */
	public StringBuffer getDefaultRouteExplantion(Route defaultRoute) {

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<DefaultRouteExplantion>");

		for (Integer LinkId : defaultRoute.getLink_list()) {

			NetworkLink networkLink = networkLinkMap.get(LinkId);

			if (networkLink != null) {
				stringBuffer.append("<LinkId>" + LinkId + "</LinkId>"
						+ "<locationName>" + networkLink.getLocationName()
						+ "</locationName>" + "<carriageway>"
						+ networkLink.getCarriageway() + "</carriageway>"
						+ "<lane>" + networkLink.getLane() + "</lane>"
						+ "<lengthAffected>" + networkLink.getLengthAffected()
						+ "</lengthAffected>" + "<directionBound>"
						+ networkLink.getDirectionBound() + "</directionBound>"
						+ "<roadNumber>" + networkLink.getRoadNumber()
						+ "</roadNumber>" + "<linearElementNature>"
						+ networkLink.getLinearElementNature()
						+ "</linearElementNature>" + "<fromDistanceAlong>"
						+ networkLink.getFromDistanceAlong()
						+ "</fromDistanceAlong>" + "<fromNodeIdentifier>"
						+ networkLink.getFromNodeIdentifier()
						+ "</fromNodeIdentifier>" + "<fromNodeType>"
						+ networkLink.getFromNodeType() + "</fromNodeType>"
						+ "<toDistanceAlong>"
						+ networkLink.getToDistanceAlong()
						+ "</toDistanceAlong>" + "<toNodeIdentifier>"
						+ networkLink.getToNodeIdentifier()
						+ "</toNodeIdentifier>" + "<toNodeType>"
						+ networkLink.getToNodeType() + "</toNodeType>");
			}
		}
		stringBuffer.append("</DefaultRouteExplantion>");

		return stringBuffer;
	}
	
	public StringBuffer getVMSEquipmentBeforeDecisionPoint (StrategicEvent strategicEvent) {
		
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<VMSUnit>");
		stringBuffer.append("<equipmentId>" + strategicEvent.getvMSUnitEquipment().getEquipmentId()
				+ "</equipmentId>");
		stringBuffer.append("<vmsUnitIdentifier>" + strategicEvent.getvMSUnitEquipment().getVmsUnitIdentifier()
				+ "</vmsUnitIdentifier>");
		stringBuffer.append("<vmsUnitElectronicAddress>" + strategicEvent.getvMSUnitEquipment().getVmsUnitElectronicAddress()
				+ "</vmsUnitElectronicAddress>");
		stringBuffer.append("<vmsDescription>" + strategicEvent.getvMSUnitEquipment().getVmsDescription()
				+ "</vmsDescription>");
		stringBuffer.append("<vmsType>" + strategicEvent.getvMSUnitEquipment().getVmsType()
				+ "</vmsType>");
		stringBuffer.append("<vmsTypeCode>" + strategicEvent.getvMSUnitEquipment().getVmsTypeCode()
				+ "</vmsTypeCode>");
		stringBuffer.append("<Lattitude>" + strategicEvent.getvMSUnitEquipment().getLocation().getX()
				+ "</Lattitude>");
		stringBuffer.append("<Lontitude>" + strategicEvent.getvMSUnitEquipment().getLocation().getY()
				+ "</Lontitude>");
		stringBuffer.append("<distanceAlong>" + strategicEvent.getvMSUnitEquipment().getDistanceAlong()
				+ "</distanceAlong>");
		stringBuffer.append("</VMSUnit>");
		
    	return stringBuffer;
	}
}

package info.habot.tm470.dao.drools;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		fusedSensorDataImpl = (FusedSensorDataImpl) applicationContext
				.getBean("fusedSensorImpl");
		vMSUnitEquipmentImpl = (VMSUnitEquipmentImpl) applicationContext
				.getBean("vMSUnitEquipmentImpl");
	}

	/**
	 * Sets up knowledge base and performs a depth based search to determine
	 * alternative routes to avoid the event.
	 * 
	 * @throws Exception
	 */
	public StrategicEvent evaluateRoute(StrategicEvent strategicEvent)
			throws Exception {

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

		log.debug("networkLinkList.size()=" + networkLinkList.size());

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

		log.debug("affectedNodeStart="
				+ getStartOfAffectedLink(strategicEvent.getLink_id()));

		NetworkNode affectedNodeEnd = getEndOfAffectedLink(strategicEvent
				.getLink_id());

		log.debug("affectedNodeEnd="
				+ getEndOfAffectedLink(strategicEvent.getLink_id()));

		if ((affectedNodeStart != null) && (affectedNodeEnd != null)) {
			graph.setNetworkLinkMap(networkLinkMap);
			graph.setToNetworkLinkMap(toNetworkLinkMap);
			graph.setRootNode(affectedNodeStart);
			graph.setTargetNode(affectedNodeEnd);

			log.debug("strategicEvent=" + strategicEvent.toString());

			// Exclude nodes from depth based search if they are part of the
			// default route
			for (Integer linkId : this.defaultRoute) {
				graph.excludeNode(networkNodeMap.get(networkLinkMap.get(linkId)
						.getToNodeIdentifier()));
			}

			strategicEvent = addRouteToStrategicEvent(graph.dfs(),
					strategicEvent);

			VMSUnitEquipment vMSUnitEquipment = getDecisionPointVMS(
					strategicEvent, affectedNodeStart);

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

			count++;
			// Stop endless looping
			if (count >= MAX_LINK_EXTENT) {
				break;
			}
		}

		return null;
	}

	/**
	 * Finds the link that contains a VMS downstream of the start of the
	 * decision point.
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

			// log.debug("SQL=select linkId, fromNodeIdentifier, toNodeIdentifier from Network_Links where fromNodeIdentifier="
			// + fromNode);

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

			// log.debug("Cnt=" + count + ", findVMS=" + findVMS);
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

		log.debug("strategicEvent.getDateCreated="
				+ strategicEvent.getDateCreated());

		Route routeDefault = new Route();
		Route routeAlternative = new Route();
		routeAlternative.setDateCreated(strategicEvent.getDateCreated());

		if (alernativeRoute.isEmpty()) {
			routeAlternative.setLink_list(null);
			// routeAlternative.setEvent_id(eventId);
			routeAlternative.setTravelTime(0);
			routeAlternative.setIs_valid(0);
		} else {
			routeAlternative.setLink_list(alernativeRoute);
			// routeAlternative.setEvent_id(eventId);
			routeAlternative.setTravelTime(getTravelTimeForRoute(
					alernativeRoute, strategicEvent.getDateCreated()));
			routeAlternative.setIs_valid(1);
		}

		routeDefault.setLink_list(this.defaultRoute);
		// routeDefault.setEvent_id(eventId);
		routeDefault.setIs_valid(1);
		routeDefault.setTravelTime(getTravelTimeForRoute(defaultRoute,
				strategicEvent.getDateCreated()));
		routeDefault.setDateCreated(strategicEvent.getDateCreated());

		strategicEvent.setAlternativeRoute(routeAlternative);
		strategicEvent.setDefaultRoute(routeDefault);

		log.debug("DefaultTravel Time = " + routeDefault.getTravelTime());
		log.debug("Alt Route Travel Time = " + routeAlternative.getTravelTime());

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
	public StringBuffer getAlternativeRouteExplantion(Route theRoute) {

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<travelTime> " + theRoute.getTravelTime()
				+ "</travelTime>");

		for (Integer LinkId : theRoute.getLink_list()) {

			NetworkLink networkLink = networkLinkMap.get(LinkId);

			if (networkLink != null) {
				stringBuffer.append("<AlternativeRouteExplantion linkId='"
						+ LinkId + "'>");
				stringBuffer.append("<locationName>"
						+ networkLink.getLocationName() + "</locationName>"
						+ "<carriageway>" + networkLink.getCarriageway()
						+ "</carriageway>" + "<lane>" + networkLink.getLane()
						+ "</lane>" + "<lengthAffected>"
						+ networkLink.getLengthAffected() + "</lengthAffected>"
						+ "<directionBound>" + networkLink.getDirectionBound()
						+ "</directionBound>" + "<roadNumber>"
						+ networkLink.getRoadNumber() + "</roadNumber>"
						+ "<linearElementNature>"
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
				stringBuffer.append("</AlternativeRouteExplantion>");
			}
		}

		return stringBuffer;
	}

	/**
	 * @return stringBuffer containing the default route
	 */
	public StringBuffer getDefaultRouteExplantion(Route theRoute) {

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<travelTime> " + theRoute.getTravelTime()
				+ "</travelTime>");

		for (Integer LinkId : theRoute.getLink_list()) {

			NetworkLink networkLink = networkLinkMap.get(LinkId);

			if (networkLink != null) {
				stringBuffer.append("<DefaultRouteExplantion linkId='" + LinkId
						+ "'>");
				stringBuffer.append("<locationName>"
						+ networkLink.getLocationName() + "</locationName>"
						+ "<carriageway>" + networkLink.getCarriageway()
						+ "</carriageway>" + "<lane>" + networkLink.getLane()
						+ "</lane>" + "<lengthAffected>"
						+ networkLink.getLengthAffected() + "</lengthAffected>"
						+ "<directionBound>" + networkLink.getDirectionBound()
						+ "</directionBound>" + "<roadNumber>"
						+ networkLink.getRoadNumber() + "</roadNumber>"
						+ "<linearElementNature>"
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
				stringBuffer.append("</DefaultRouteExplantion>");
			}
		}

		return stringBuffer;
	}

	public StringBuffer getVMSEquipmentBeforeDecisionPoint(
			StrategicEvent strategicEvent) {

		/*
		 * Dummy VMS NTIS_Network_Links 123008901 10414 mainCarriageway
		 * northbound M6 northbound between J18 and J19
		 */
		if (strategicEvent.getvMSUnitEquipment() == null) {
			VMSUnitEquipment vMSEquipment = new VMSUnitEquipment();
			Point2D.Double point = new Point2D.Double();
			point.setLocation(53.2963256835938, -2.39886736869812);

			vMSEquipment.setEquipmentId("CFB613DE81953254E0433CC411ACFD01");
			vMSEquipment.setLinearElementIdentifier("123008901");
			vMSEquipment.setLinearElementReferenceModel("NTIS_Network_Links");
			vMSEquipment.setLocation(point);
			vMSEquipment.setVmsDescription("3x18 VMS MS3");
			vMSEquipment.setVmsType("MONOCHROME_GRAPHIC");
			vMSEquipment.setVmsTypeCode("111");
			vMSEquipment.setVmsUnitElectronicAddress("024/5/217/012");
			vMSEquipment.setVmsUnitIdentifier("M6/6877A");
			vMSEquipment.setDistanceAlong(10414);

			strategicEvent.setvMSUnitEquipment(vMSEquipment);
		}

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<VMSUnit>");

		stringBuffer.append("<equipmentId>"
				+ strategicEvent.getvMSUnitEquipment().getEquipmentId()
				+ "</equipmentId>");
		stringBuffer.append("<vmsUnitIdentifier>"
				+ strategicEvent.getvMSUnitEquipment().getVmsUnitIdentifier()
				+ "</vmsUnitIdentifier>");
		stringBuffer.append("<vmsUnitElectronicAddress>"
				+ strategicEvent.getvMSUnitEquipment()
						.getVmsUnitElectronicAddress()
				+ "</vmsUnitElectronicAddress>");
		stringBuffer.append("<vmsDescription>"
				+ strategicEvent.getvMSUnitEquipment().getVmsDescription()
				+ "</vmsDescription>");

		if (null != strategicEvent.getvMSUnitEquipment().getVmsType()) {
			stringBuffer.append("<vmsType>"
					+ strategicEvent.getvMSUnitEquipment().getVmsType()
					+ "</vmsType>");
		}

		if (null != strategicEvent.getvMSUnitEquipment().getVmsTypeCode()) {
			stringBuffer.append("<vmsTypeCode>"
					+ strategicEvent.getvMSUnitEquipment().getVmsTypeCode()
					+ "</vmsTypeCode>");
		}

		if (null != strategicEvent.getvMSUnitEquipment().getLocation()) {
			stringBuffer.append("<Lattitude>"
					+ strategicEvent.getvMSUnitEquipment().getLocation().getX()
					+ "</Lattitude>");
		}

		if (null != strategicEvent.getvMSUnitEquipment().getLocation()) {
			stringBuffer.append("<Lontitude>"
					+ strategicEvent.getvMSUnitEquipment().getLocation().getY()
					+ "</Lontitude>");
		}

		stringBuffer.append("<distanceAlong>"
				+ strategicEvent.getvMSUnitEquipment().getDistanceAlong()
				+ "</distanceAlong>");

		log.debug("strategicEvent=" + strategicEvent.getLink_id());
		log.debug("networkLinkMap.size()=" + networkLinkMap.size());

		stringBuffer.append("<MessageLine1>"
				+ strategicEvent.getEvent_type().toUpperCase()
				+ "</MessageLine1>");
		try {
			stringBuffer.append("<MessageLine2>"
					+ networkLinkMap.get(strategicEvent.getLink_id())
							.getRoadNumber() + "</MessageLine2>");
		} catch (Exception ex) {
		}

		stringBuffer.append("<MessageLine3>"
				+ strategicEvent.getEvent_sub_type().toUpperCase()
				+ "</MessageLine3>");

		stringBuffer.append("</VMSUnit>");

		return stringBuffer;
	}

	public StringBuffer getDefaultA556XML(StrategicEvent strategicEvent) {

		// Get all network links
		List<NetworkLink> networkLinkList = networkLinkImpl.listNetworkLink();
		networkNodeMap = networkNodeImpl.getNetworkNodes();

		log.debug("networkLinkList.size()=" + networkLinkList.size());

		// Add to knowledge base
		for (NetworkLink networkLink : networkLinkList) {
			networkLinkMap.put(networkLink.getLinkId(), networkLink);
			toNetworkLinkMap
					.put(networkLink.getToNodeIdentifier(), networkLink);
		}

		StringBuffer stringBuffer = new StringBuffer();

		String newLine = System.getProperty("line.separator", "\n");

		InputStream is = getClass().getResourceAsStream(
				"/RouteDetermination.xml");
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;
		try {
			while ((line = br.readLine()) != null) {
				stringBuffer.append(line);
				stringBuffer.append(newLine);
			}

			br.close();
			isr.close();
			is.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return stringBuffer;
	}
}

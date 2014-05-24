package info.habot.tm470.dao.drools;

import info.habot.tm470.dao.pojo.StrategicEvent;

public class RouteDeterminationTest {

	public static void main(String[] args) {

		StrategicEvent strategicEvent = new StrategicEvent();
		strategicEvent.setLink_id(125000501);
		
		RouteDetermination routeDetermination = new RouteDetermination();
		routeDetermination.evaluateRoute(strategicEvent);
	}

}

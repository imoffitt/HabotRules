package info.habot.tm470.dao.drools;

import info.habot.tm470.dao.pojo.StrategicEvent;

public class RouteDeterminationTest {

	public static void main(String[] args) {

		StrategicEvent strategicEvent = new StrategicEvent();
		strategicEvent.setLink_id(125000501);
		strategicEvent.setDateCreated("2014-01-17 00:00:35");

		RouteDetermination routeDetermination = new RouteDetermination();

		try {
			routeDetermination.evaluateRoute(strategicEvent);
		} catch (Exception ex) {
			System.out.println(ex.getStackTrace());
		}
	}

}

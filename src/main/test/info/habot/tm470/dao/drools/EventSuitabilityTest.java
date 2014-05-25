package info.habot.tm470.dao.drools;

import info.habot.tm470.dao.pojo.StrategicEvent;

public class EventSuitabilityTest {

	public static void main(String[] args) {
		
		StrategicEvent strategicEvent = new StrategicEvent();
		strategicEvent.setLink_id(125000501);
		
		EventSuitability eventSuitability = new EventSuitability();
		strategicEvent = eventSuitability.evaluateStrategicEvent(strategicEvent);
		
		System.out.println (eventSuitability.getExplantion());

	}

}

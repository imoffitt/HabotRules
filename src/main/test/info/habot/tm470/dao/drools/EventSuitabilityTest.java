package info.habot.tm470.dao.drools;

import info.habot.tm470.dao.pojo.StrategicEvent;

public class EventSuitabilityTest {

	public static void main(String[] args) {
		
		StrategicEvent strategicEvent = new StrategicEvent();
		strategicEvent.setLink_id(111036202);
		strategicEvent.setCapacity_reduction(70);
		strategicEvent.setEvent_type("Accident");
		strategicEvent.setEvent_id(1);
		
		EventSuitability eventSuitability = new EventSuitability();
		try {
			strategicEvent = eventSuitability.evaluateStrategicEvent(strategicEvent);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println (eventSuitability.getExplantion());
	}

}

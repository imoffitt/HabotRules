/**
 * 
 */
package info.habot.tm470.dao.drools;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.event.KnowledgeRuntimeEventManager;
import org.kie.internal.logger.KnowledgeRuntimeLogger;
import org.kie.internal.logger.KnowledgeRuntimeLoggerFactory;

import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.StrategicEvent;

/**
* EventSuitabilityTest
* 
* @author Ian Moffitt
* @version 0.1
* @see www.habot.info
*/
public class EventSuitability {

	private KieSession kSession;
	private KnowledgeRuntimeLogger logger;
	
	/**
	 * Constructor
	 */
	public EventSuitability() {
		
		try {
			// load up the knowledge base
			KieServices ks = KieServices.Factory.get();
			KieContainer kContainer = ks.getKieClasspathContainer();
			kSession = kContainer.newKieSession("ksession-event-rules");

			if (kSession == null) {
				System.out.println("kSession is null");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public void createKnowledgeBase () {
		
//		logger = KnowledgeRuntimeLoggerFactory.newFileLogger((KnowledgeRuntimeEventManager) kSession, "EventSuitabilityLog");
		
		StrategicEvent strategicEvent = new StrategicEvent();
		
		strategicEvent.setEvent_id(1);
		strategicEvent.setEvent_type("Accident");
		strategicEvent.setLink_id(111036202);
		strategicEvent.setCapacity_reduction(60);
		
		NetworkLink networkLink = new NetworkLink();
		networkLink.setLinkId(strategicEvent.getLink_id());
		networkLink.setLocationName("M6 southbound within J15");
		networkLink.setDirectionBound("southbound");
		
		kSession.insert( strategicEvent );
		kSession.insert( networkLink );

		kSession.fireAllRules();
	}
	
	public void endSession () {
		
		kSession.fireAllRules();
		kSession.dispose();             // Statefull sessions *must* be properly disposed of...
//		logger.close();
	}
}

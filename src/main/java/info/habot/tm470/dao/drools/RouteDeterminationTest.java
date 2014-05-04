package info.habot.tm470.dao.drools;

import java.util.List;

import info.habot.tm470.dao.EventImpl;
import info.habot.tm470.dao.NetworkLinkImpl;
import info.habot.tm470.dao.NetworkNodeImpl;
import info.habot.tm470.dao.pojo.NetworkLink;
import info.habot.tm470.dao.pojo.NetworkNode;
import info.habot.tm470.dao.pojo.StrategicEvent;

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
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext(
				"Beans.xml");
		NetworkNodeImpl networkNodeImpl = 
			      (NetworkNodeImpl)applicationContext.getBean("networkNodeImpl");
		NetworkLinkImpl networkLinkImpl = 
			      (NetworkLinkImpl)applicationContext.getBean("networkLinkImpl");
		EventImpl eventImpl = 
			      (EventImpl)applicationContext.getBean("eventImpl");

		// Get all network links
		List<NetworkLink> networkLintList = networkLinkImpl.listNetworkLink();
		
		// Add to knowledge base
		for (NetworkLink networkLink : networkLintList) {
			kSession.insert( networkLink );
		}
		
		// Get all network nodes
		List<NetworkNode> networkNodeList = networkNodeImpl.listNetworkNode();
		
		// Add to knowledge base
		for (NetworkNode networkNode : networkNodeList) {
			kSession.insert( networkNode );
		}
		
		// Get all active events
		List<StrategicEvent> eventList = eventImpl.getActiveEvents();
		
		// Add to knowledge base
		for (StrategicEvent strategicEvent : eventList) {
			kSession.insert( strategicEvent );
		}
		
//		kSession.fireAllRules();
		kSession.dispose();             // Statefull sessions *must* be properly disposed of...
//		logger.close();
	}
}

/**
 * 
 */
package info.habot.tm470.dao.drools;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
	private static final String KIE_LOG_FILENAME = "EventSuitabilityLog";
	private String kie_log_file;
	
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

	public StrategicEvent evaluateStrategicEvent (StrategicEvent strategicEvent) {
		
		logger = KnowledgeRuntimeLoggerFactory.newFileLogger((KnowledgeRuntimeEventManager) kSession, KIE_LOG_FILENAME);
		
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
		
		endSession ();
		
		return strategicEvent;
	}
	
	/**
	 * @return kie log file
	 */
	private void endSession () {
				
		kSession.dispose();             // Statefull sessions *must* be properly disposed of...
		logger.close();
		
		if (readKieLogFile()) {
			kie_log_file = "<explanation>" + kie_log_file + "</explanation>";
		}
	}
	
	/**
	 * @return kie log file
	 */
	public boolean readKieLogFile()
	{
	   kie_log_file = null;
	   File file = new File(KIE_LOG_FILENAME + ".log"); //for ex foo.txt
	   try {
	       FileReader reader = new FileReader(file);
	       char[] chars = new char[(int) file.length()];
	       reader.read(chars);
	       kie_log_file = new String(chars);
	       reader.close();
	       
	       file.delete();  // Once we have the data dlete the physical file as it is not needed.
	       
	   } catch (IOException e) {
	       e.printStackTrace();
	       return false;
	   }
	   return true;
	}

	public String getExplantion() {
		return kie_log_file;
	}
}

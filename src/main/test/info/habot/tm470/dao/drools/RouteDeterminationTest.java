package info.habot.tm470.dao.drools;

public class RouteDeterminationTest {

	public static void main(String[] args) {


		RouteDetermination routeDetermination = new RouteDetermination();
		routeDetermination.createKnowledgeBase(1);
		routeDetermination.endSession();

	}

}

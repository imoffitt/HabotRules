package info.habot.tm470.dao.drools;

public class RouteState {
    
    private boolean		distance_too_long;
    private boolean		too_many_nodes;
    private boolean		route_complete;
    
    public RouteState() {
    	distance_too_long = false;
        too_many_nodes = false;
        route_complete = false;
    }

    public void resetState () {
        distance_too_long = false;
        too_many_nodes = false;
        route_complete = false;
    }

	public boolean isDistance_too_long() {
		return distance_too_long;
	}

	public void setDistance_too_long(boolean distance_too_long) {
		this.distance_too_long = distance_too_long;
	}

	public boolean isToo_many_nodes() {
		return too_many_nodes;
	}

	public void setToo_many_nodes(boolean too_many_nodes) {
		this.too_many_nodes = too_many_nodes;
	}

	public boolean isRoute_complete() {
		return route_complete;
	}

	public void setRoute_complete(boolean route_complete) {
		this.route_complete = route_complete;
	}

	@Override
	public String toString() {
		return "RouteState [distance_too_long=" + distance_too_long
				+ ", too_many_nodes=" + too_many_nodes + ", route_complete="
				+ route_complete + "]";
	}
	
}

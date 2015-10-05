import GivenTools.*;

public class Tracker {
	public final URL announce_url;
	public String escaped_info_hash;

	public Tracker(URL announce_url) {
		this.announce_url = announce_url;
	}
}
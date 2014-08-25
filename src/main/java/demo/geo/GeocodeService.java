package demo.geo;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jon Brisbin
 */
@Service
public class GeocodeService {

	private static final String GEOCODE_JSON_URL = "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s";

	private final Logger                           log      = LoggerFactory.getLogger(getClass());
	private final ConcurrentHashMap<String, Point> geoCache = new ConcurrentHashMap<>();
	private final RestTemplate                     restTmpl = new RestTemplate();

	private final GoogleApiSettings settings;

	@Autowired
	public GeocodeService(GoogleApiSettings settings) {
		this.settings = settings;
	}

	public Point geocode(String address, String city, String province, String postalCode) throws IOException {
		String key = (city + province).toLowerCase();

		Point geoCoord;
		if (null == (geoCoord = geoCache.get(key))) {
			geoCoord = geoCache.computeIfAbsent(key, s -> {
				String url = String.format(GEOCODE_JSON_URL, (city + "," + province), settings.getApiKey());
				JsonPath coords = JsonPath.compile("$.results[0].geometry.location");

				String response = restTmpl.getForObject(url, String.class);
				log.info("json response: {}", response);
				Object o = coords.read(response);
				log.info("json object: {}", o);

				return new Point(0, 0);
			});
		}

		return geoCoord;
	}

}

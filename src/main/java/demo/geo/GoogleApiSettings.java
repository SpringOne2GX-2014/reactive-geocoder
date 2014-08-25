package demo.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
@ConfigurationProperties(prefix = "google")
public class GoogleApiSettings {

	private String apiKey;

	public String getApiKey() {
		return apiKey;
	}

}

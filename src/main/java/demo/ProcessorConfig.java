package demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class ProcessorConfig {

	private String baseUri         = "http://localhost:5050";
	private int    defaultDistance = 20;

	public String getBaseUri() {
		return baseUri;
	}

	public int getDefaultDistance() {
		return defaultDistance;
	}

	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
	}

	public void setDefaultDistance(int defaultDistance) {
		this.defaultDistance = defaultDistance;
	}

}

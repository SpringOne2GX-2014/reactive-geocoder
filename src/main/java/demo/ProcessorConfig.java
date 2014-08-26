package demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class ProcessorConfig {

	private String baseUri                  = "http://localhost:5050";
	private int    defaultDistance          = 10;
	private int    periodicRetrieverTimeout = 5;
	private int    pageSize                 = 15;

	public String getBaseUri() {
		return baseUri;
	}

	public int getDefaultDistance() {
		return defaultDistance;
	}

	public int getPeriodicRetrieverTimeout() {
		return periodicRetrieverTimeout;
	}

	public int getPageSize() {
		return pageSize;
	}

}

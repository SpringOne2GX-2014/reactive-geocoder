package demo;

import demo.domain.Location;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import reactor.core.Environment;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

public class ProcessorApplicationTests {

	private String baseUri;

	@Before
	public void setup() {
		baseUri = "http://localhost:5050";
	}

	@Test
	public void interaction() throws InterruptedException {
		ExecutorService pool = Executors.newCachedThreadPool();
		RestTemplate rest = new RestTemplate();
		int times = 1000;
		int iterations = times / Environment.PROCESSORS;

		double start = System.currentTimeMillis();
		CountDownLatch latch = new CountDownLatch(times);

		for (int t = 0; t < Environment.PROCESSORS; t++) {
			pool.submit(() -> {
				for (int i = 0; i < iterations; i++) {
					try {
						Location out = new Location()
								.setName("John Doe")
								.setCity("New York")
								.setProvince("NY")
								.setCoordinates(new double[]{-74.00594130000002, 40.7127837});

						URI getUri = rest.postForLocation(baseUri + "/location", out, Location.class);
						assertThat("Location was created", getUri.toString(), startsWith(baseUri + "/location/"));

						LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));

						ResponseEntity<Location> resp = rest.getForEntity(getUri, Location.class);
						assertThat("Response was received", resp.getStatusCode(), is(HttpStatus.OK));

						LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1000));

						ResponseEntity<String> jsonResp = rest.getForEntity(getUri + "/nearby", String.class);
						if (jsonResp.getStatusCode() != HttpStatus.OK) {
							System.out.println("response: " + jsonResp.getBody());
						}
						assertThat("Response was OK", jsonResp.getStatusCode(), is(HttpStatus.OK));
					} catch (RestClientException e) {
						e.printStackTrace();
					} finally {
						System.out.println("latch count: " + latch.getCount());
						latch.countDown();
					}
				}
			});
		}

		latch.await();
		double end = System.currentTimeMillis();
		double elapsed = end - start;
		int throughput = (int) (times / (elapsed / 1000));


		for (int q = 0; q < 100; q++) {
			System.out.println("");
		}
		System.out.println("throughput: " + throughput + "/s");
	}

}

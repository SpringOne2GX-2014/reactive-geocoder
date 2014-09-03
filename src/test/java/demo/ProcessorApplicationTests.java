package demo;

import demo.domain.Location;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.*;

public class ProcessorApplicationTests {

	private String baseUri;

	@Before
	public void setup() {
		baseUri = "http://localhost:5050";
	}

	@Test
	public void interaction() throws InterruptedException {
		RestTemplate rest = new RestTemplate();
		ExecutorService pool = Executors.newCachedThreadPool();
		int times = 256;
		int threads = 32;
		int iterations = times / threads;

		double start = System.currentTimeMillis();
		CountDownLatch latch = new CountDownLatch(times);
		ConcurrentLinkedQueue<String> ids = new ConcurrentLinkedQueue<>();

		for (int t = 0; t < threads; t++) {
			pool.submit(() -> {
				for (int i = 0; i < iterations; i++) {
					try {
						Location out = new Location()
								.setName("John Doe")
								.setCity("New York")
								.setProvince("NY")
								.setCoordinates(new double[]{-74.00594130000002, 40.7127837});

						ResponseEntity<Location> resp = rest.postForEntity(baseUri + "/location", out, Location.class);
						ids.add(resp.getBody().getId());
					} catch (RestClientException e) {
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				}
			});
		}

		latch.await(5, TimeUnit.SECONDS);

		for (String id : ids) {
			String nearbyUrl = baseUri + "/location/" + id + "/nearby";
			ResponseEntity<List> resp = rest.getForEntity(nearbyUrl, List.class);
		}

		double end = System.currentTimeMillis();
		double elapsed = end - start;
		int throughput = (int) (times / (elapsed / 1000));

		System.out.println("throughput: " + throughput + "/s");
	}

}

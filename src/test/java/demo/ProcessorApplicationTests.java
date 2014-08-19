package demo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ratpack.server.RatpackServer;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ProcessorApplication.class)
@IntegrationTest("server.port=0")
public class ProcessorApplicationTests {
	
	@Autowired
	private MongoDbFactory mongo;
	
	@Autowired
	private RatpackServer server;

	@Test
	public void contextLoads() {
		ResponseEntity<String> result = new TestRestTemplate().getForEntity("http://localhost:" + server.getBindPort(), String.class);
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

}

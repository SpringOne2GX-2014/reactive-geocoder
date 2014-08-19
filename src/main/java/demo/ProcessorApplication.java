package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.spring.annotation.EnableRatpack;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableRatpack
public class ProcessorApplication {

	@Bean
	public Action<Chain> handlers() {
		return (chain) -> chain.get((context) -> {
			context.render("Hello World");
		});
	}

	public static void main(String[] args) {
		SpringApplication.run(ProcessorApplication.class, args);
	}

}

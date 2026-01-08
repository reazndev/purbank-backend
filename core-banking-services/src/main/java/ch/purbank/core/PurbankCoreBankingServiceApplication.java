package ch.purbank.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PurbankCoreBankingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PurbankCoreBankingServiceApplication.class, args);
	}

}

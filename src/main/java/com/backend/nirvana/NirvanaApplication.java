package com.backend.nirvana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NirvanaApplication {

	public static void main(String[] args) {
		SpringApplication.run(NirvanaApplication.class, args);
	}

}

package com.sagar.forgeorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForgeorderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ForgeorderApplication.class, args);
	}

}

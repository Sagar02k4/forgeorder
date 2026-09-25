package com.sagar.forgeorder;

import org.springframework.boot.SpringApplication;

public class TestForgeorderApplication {

	public static void main(String[] args) {
		SpringApplication.from(ForgeorderApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package com.renault.fleet;

import org.springframework.boot.SpringApplication;

public class TestFleetApplication {

	public static void main(String[] args) {
		SpringApplication.from(FleetApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

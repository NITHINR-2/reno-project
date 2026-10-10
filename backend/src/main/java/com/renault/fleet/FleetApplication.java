package com.renault.fleet;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FleetApplication {

	public static void main(String[] args) {
		// Run in UTC regardless of host settings; IST (Asia/Kolkata) is applied explicitly where business rules need it.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(FleetApplication.class, args);
	}

}

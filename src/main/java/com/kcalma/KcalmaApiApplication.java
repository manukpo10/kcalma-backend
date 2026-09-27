package com.kcalma;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KcalmaApiApplication {

	public static void main(String[] args) {
		// Pins the JVM default timezone independently of the host/container's own setting so
		// every JVM subsystem that implicitly reads it (notably the Postgres JDBC driver, which
		// sends TimeZone.getDefault() as a startup parameter -- some legacy zone aliases, e.g. the
		// machine-local "America/Buenos_Aires", aren't recognized by the server and fail the
		// connection outright) behaves the same on every machine. The app's own domain logic never
		// relies on this -- see com.kcalma.config.ClockConfig's explicit, app.timezone-zoned Clock.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(KcalmaApiApplication.class, args);
	}

}

package com.worldpinball.leaderboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class LeaderboardSpringBootJavaApplication {

	public static void main(String[] args) {
		if (System.getProperty("app.seeder.enabled") == null) {
			System.setProperty("app.seeder.enabled", "true");
		}
		SpringApplication.run(LeaderboardSpringBootJavaApplication.class, args);
	}
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}

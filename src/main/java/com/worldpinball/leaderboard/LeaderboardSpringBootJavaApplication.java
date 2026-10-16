package com.worldpinball.leaderboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class LeaderboardSpringBootJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaderboardSpringBootJavaApplication.class, args);
	}
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}

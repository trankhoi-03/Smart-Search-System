package com.project.smartsearchsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SmartsearchsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartsearchsystemApplication.class, args);
	}

}

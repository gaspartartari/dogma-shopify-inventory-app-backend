package com.tartaritech.inventory_sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventorySyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventorySyncApplication.class, args);
	}

}

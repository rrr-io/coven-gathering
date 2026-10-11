package org.coven;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CovenApplication {
  public static void main(String[] args) {
    SpringApplication.run(CovenApplication.class, args);
  }
}

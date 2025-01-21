package de.tum.cit.aet.helios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class HeliosApplication {

  public static void main(String[] args) {
    SpringApplication.run(HeliosApplication.class, args);
  }
}

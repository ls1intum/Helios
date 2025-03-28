package de.tum.cit.aet.helios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableCaching
@ComponentScan(basePackages = "de.tum.cit.aet.helios")
public class HeliosApplication {

  public static void main(String[] args) {
    SpringApplication.run(HeliosApplication.class, args);
  }
}

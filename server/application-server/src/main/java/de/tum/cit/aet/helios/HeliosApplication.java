package de.tum.cit.aet.helios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableCaching
@ConfigurationPropertiesScan
public class HeliosApplication {

  public static void main(String[] args) {
    SpringApplication.run(HeliosApplication.class, args);
  }
}

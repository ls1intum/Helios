package de.tum.cit.aet.helios.tests.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "de.tum.cit.aet.helios")
public class TestResultProcessorApp {
  public static void main(String[] args) {
    SpringApplication.run(TestResultProcessorApp.class, args);
  }
}

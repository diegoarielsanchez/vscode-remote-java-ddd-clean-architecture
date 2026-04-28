package com.das.cleanddd.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(
    scanBasePackages = {"com.das.cleanddd.application"},
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class}
)
@RestController
public class DemoApplication {

  @GetMapping("/")
  public String home() {
    return "Auth Service";
  }

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
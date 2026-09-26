package com.diyncrafts.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan("com.diyncrafts.web.app.config")
@EnableJpaRepositories(basePackages = "com.diyncrafts.web.app.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.diyncrafts.web.app.repository.es")
public class WebappApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebappApplication.class, args);
	}

}

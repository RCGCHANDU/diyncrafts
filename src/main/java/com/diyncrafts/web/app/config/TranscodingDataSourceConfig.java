package com.diyncrafts.web.app.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class TranscodingDataSourceConfig {

    @Bean
    public DataSource transcodingDataSource() {
        // Configure the DataSource (make sure to use your own database details)
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/transcoding_tasks");
        dataSource.setUsername("root");
        dataSource.setPassword("dr3@mk1ng");
        return dataSource;
    }
}
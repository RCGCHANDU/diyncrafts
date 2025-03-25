package com.diyncrafts.webapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        // Configure the DataSource (make sure to use your own database details)
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/diycraftshub");
        dataSource.setUsername("ravi");
        dataSource.setPassword("dr3@mk1ng");
        return dataSource;
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager userDetailsService = new JdbcUserDetailsManager();
        userDetailsService.setDataSource(dataSource);

        // Custom query for user details
        userDetailsService.setUsersByUsernameQuery("SELECT username, password, enabled FROM user_account WHERE username = ?");

        // You don't need an authority query anymore since role is directly in user
        userDetailsService.setAuthoritiesByUsernameQuery("SELECT username, role FROM user_account WHERE username = ?");
        return userDetailsService;

    }


}

package com.cugb.quahog;

import com.cugb.quahog.Configuration.AppConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppConfigurationProperties.class)
public class QuahogApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuahogApplication.class, args);
    }

}

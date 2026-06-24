package com.codey.web;

import com.codey.web.config.WebDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 最小 Web 示例应用入口。
 */
@SpringBootApplication(scanBasePackages = {"com.codey"})
@EnableConfigurationProperties(WebDemoProperties.class)
public class WebDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebDemoApplication.class, args);
    }
}
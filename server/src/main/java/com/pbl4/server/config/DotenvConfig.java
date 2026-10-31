package com.pbl4.server.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DotenvConfig {

    public DotenvConfig() {
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null
                    && System.getenv(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }
}
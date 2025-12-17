package com.paperradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaperRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperRadarApplication.class, args);
    }

}

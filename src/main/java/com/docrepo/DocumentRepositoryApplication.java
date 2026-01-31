package com.docrepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumentRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentRepositoryApplication.class, args);
    }
}

package com.yourname.gst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the GST Invoice Processor.
 *
 * To run:
 * 1. Create database: createdb gstdb
 * 2. Set environment variables:
 *    - DB_URL=jdbc:postgresql://localhost:5432/gstdb
 *    - DB_USER=postgres
 *    - DB_PASS=postgres
 *    - ADMIN_USER=admin
 *    - ADMIN_PASS=admin
 * 3. Run: mvn spring-boot:run
 */
@SpringBootApplication
public class GstApplication {

    public static void main(String[] args) {
        SpringApplication.run(GstApplication.class, args);
    }

}
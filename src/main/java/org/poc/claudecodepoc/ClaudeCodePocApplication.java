package org.poc.claudecodepoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.poc.claudecodepoc", "com.smartsensesolutions"})
public class ClaudeCodePocApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeCodePocApplication.class, args);
    }

}

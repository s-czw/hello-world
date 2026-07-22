package app.cairn.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// We authenticate via a custom cookie/JWT chain (D-020), so Spring Boot's default in-memory user
// (and its generated dev password) is unwanted — exclude that auto-config.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CairnApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CairnApiApplication.class, args);
    }
}

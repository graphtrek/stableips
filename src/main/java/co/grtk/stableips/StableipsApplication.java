package co.grtk.stableips;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StableipsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StableipsApplication.class, args);
    }

}

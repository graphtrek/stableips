package co.grtk.stableips.config;

import co.grtk.stableips.repository.UserRepository;
import co.grtk.stableips.service.WalletService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, WalletService walletService) {
        return args -> {
            if (userRepository.findByUsername("stableips1").isEmpty()) {
                walletService.createUserWithWallet("stableips1");
                System.out.println("Default user 'stableips1' created successfully");
            } else {
                System.out.println("Default user 'stableips1' already exists");
            }

            if (userRepository.findByUsername("stableips2").isEmpty()) {
                walletService.createUserWithWallet("stableips2");
                System.out.println("Default user 'stableips2' created successfully");
            } else {
                System.out.println("Default user 'stableips2' already exists");
            }
        };
    }
}

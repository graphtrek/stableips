package co.grtk.stableips.config;

import co.grtk.stableips.repository.UserRepository;
import co.grtk.stableips.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, WalletService walletService) {
        return args -> {
            if (userRepository.findByUsername("stableips1").isEmpty()) {
                walletService.createUserWithWalletAndFunding("stableips1");
                log.info("Default user 'stableips1' created successfully with 1 ETH initial funding");
            } else {
                log.info("Default user 'stableips1' already exists");
            }

            if (userRepository.findByUsername("stableips2").isEmpty()) {
                walletService.createUserWithWalletAndFunding("stableips2");
                log.info("Default user 'stableips2' created successfully with 1 ETH initial funding");
            } else {
                log.info("Default user 'stableips2' already exists");
            }
        };
    }
}

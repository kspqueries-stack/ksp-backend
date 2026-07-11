package com.policescheduler.config;

import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.User;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String rawPassword = "admin123";
            String encoded = passwordEncoder.encode(rawPassword);
            log.info("Generated BCrypt hash for admin123: {}", encoded);
            log.info("Verify matches: {}", passwordEncoder.matches(rawPassword, encoded));

            Optional<User> existing = userRepository.findByUsername("admin");
            if (existing.isPresent()) {
                User admin = existing.get();
                admin.setPasswordHash(encoded);
                userRepository.save(admin);
                log.info("Reset admin password to admin123");
            } else {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(encoded);
                admin.setRole(User.Role.ADMIN);
                admin.setDisplayName("Administrator");
                admin.setLocale("en");
                userRepository.save(admin);
                log.info("Created default admin user: admin / admin123");
            }

            // Create support admin user
            Optional<User> existingSupportAdmin = userRepository.findByUsername("supportadmin");
            if (existingSupportAdmin.isPresent()) {
                User supportAdmin = existingSupportAdmin.get();
                supportAdmin.setPasswordHash(encoded);
                supportAdmin.setRole(User.Role.SUPPORT_ADMIN);
                userRepository.save(supportAdmin);
                log.info("Reset supportadmin password to admin123");
            } else {
                User supportAdmin = new User();
                supportAdmin.setUsername("supportadmin");
                supportAdmin.setPasswordHash(encoded);
                supportAdmin.setRole(User.Role.SUPPORT_ADMIN);
                supportAdmin.setDisplayName("Support Admin");
                supportAdmin.setLocale("en");
                userRepository.save(supportAdmin);
                log.info("Created default support admin user: supportadmin / admin123");
            }
        };
    }

    /**
     * Seeds latitude/longitude coordinates for duty types that have NULL values.
     * Uses varied coordinates around Mangaluru, Karnataka (center: 12.9141, 74.8560).
     * Latitude range: 12.8700 to 12.9500
     * Longitude range: 74.8200 to 74.9000
     */
    @Bean
    CommandLineRunner seedDutyTypeCoordinates(DutyTypeRepository dutyTypeRepository) {
        return args -> {
            List<DutyType> dutyTypes = dutyTypeRepository.findAll();
            if (dutyTypes.isEmpty()) {
                log.info("No duty types found — skipping coordinate seeding.");
                return;
            }

            Random random = new Random(42); // Fixed seed for reproducibility
            int updatedCount = 0;

            // Predefined coordinates around Mangaluru for known duty locations
            double[][] mangaluruCoordinates = {
                {12.9141, 74.8560}, // Commissioner Office area
                {12.8850, 74.8430}, // District Treasury area
                {12.9250, 74.8700}, // Main Gate / Check Post area
                {12.8780, 74.8620}, // Armoury area
                {12.9400, 74.8350}, // VIP Escort area
                {12.9050, 74.8900}, // Striking Force area
                {12.8900, 74.8250}, // Prison escort area
                {12.9350, 74.8800}, // Control Room area
                {12.8720, 74.8550}, // Parade Ground area
                {12.9180, 74.8480}, // Barracks area
                {12.9300, 74.8650}, // Training center area
                {12.8980, 74.8380}, // Quarters area
                {12.9450, 74.8750}, // Highway patrol area
                {12.8830, 74.8920}, // City center area
                {12.9100, 74.8280}, // Outskirts area
            };

            for (DutyType dutyType : dutyTypes) {
                if (dutyType.getLatitude() == null || dutyType.getLongitude() == null) {
                    double lat;
                    double lng;

                    if (updatedCount < mangaluruCoordinates.length) {
                        // Use predefined coordinates for first N duty types
                        lat = mangaluruCoordinates[updatedCount][0];
                        lng = mangaluruCoordinates[updatedCount][1];
                    } else {
                        // Generate random coordinates within Mangaluru range
                        lat = 12.8700 + (random.nextDouble() * (12.9500 - 12.8700));
                        lng = 74.8200 + (random.nextDouble() * (74.9000 - 74.8200));
                    }

                    dutyType.setLatitude(lat);
                    dutyType.setLongitude(lng);
                    dutyTypeRepository.save(dutyType);
                    updatedCount++;
                    log.debug("Set coordinates for duty type '{}': lat={}, lng={}",
                            dutyType.getName(), lat, lng);
                }
            }

            if (updatedCount > 0) {
                log.info("Seeded coordinates for {} duty types with Mangaluru area locations.", updatedCount);
            } else {
                log.info("All duty types already have coordinates — no seeding needed.");
            }
        };
    }
}

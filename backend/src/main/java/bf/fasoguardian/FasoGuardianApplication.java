package bf.fasoguardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "FasoGuardian", sharedModules = "plateforme")
@SpringBootApplication
public class FasoGuardianApplication {

    public static void main(String[] args) {
        SpringApplication.run(FasoGuardianApplication.class, args);
    }
}

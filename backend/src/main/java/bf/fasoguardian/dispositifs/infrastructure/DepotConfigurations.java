package bf.fasoguardian.dispositifs.infrastructure;

import java.util.UUID;

import bf.fasoguardian.dispositifs.domaine.ConfigurationBracelet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotConfigurations extends JpaRepository<ConfigurationBracelet, UUID> {
}

package bo.edu.caseapp.health;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
  private final JdbcTemplate jdbcTemplate;

  public HealthService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

  public HealthResponse check() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return new HealthResponse("UP", "UP");
    } catch (DataAccessException exception) {
      return new HealthResponse("UP", "DOWN");
    }
  }
}


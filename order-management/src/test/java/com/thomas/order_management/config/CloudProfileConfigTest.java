package com.thomas.order_management.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Das Backend wird per Ping wach gehalten. Die Neon-Datenbank muss trotzdem einschlafen können,
 * sonst sind die freien Rechenstunden vor Monatsende verbraucht. Das Profil "test" liegt über "cloud"
 * und ersetzt nur die Datenbank durch H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"cloud", "test"})
@DisplayName("Profil cloud: Datenbank kann einschlafen")
class CloudProfileConfigTest {

    /** Neon legt die Datenbank nach 5 Minuten ohne Abfrage schlafen. */
    private static final Duration NEON_SUSPEND_AFTER = Duration.ofMinutes(5);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Der Pool hält keine Verbindung offen und schickt keine Keepalive-Abfragen")
    void poolShouldLetDatabaseSleep() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);

        assertThat(pool.getMinimumIdle()).isZero();
        assertThat(pool.getKeepaliveTime()).isZero();
        assertThat(pool.getIdleTimeout()).isPositive().isLessThan(NEON_SUSPEND_AFTER.toMillis());
    }

    @Test
    @DisplayName("Der Health-Check fragt die Datenbank nicht ab")
    void healthCheckShouldNotQueryDatabase() throws Exception {
        assertThat(context.containsBean("dbHealthContributor")).isFalse();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link StateLawSeedRunner}.
 *
 * <p>Verifies that the runner loads the seed JSON file from the classpath,
 * parses all 58 records (43 state + 15 federal), and calls
 * {@code stateLawPort.upsert()} for each. Also verifies idempotency
 * (safe to re-run) and correct field mapping for a known record.
 *
 * <p>The real seed file at {@code data/state_health_ai_laws_seed.json} is
 * on the test classpath, so these tests exercise the actual JSON parsing.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-07
 */
class StateLawSeedRunnerTest {

    private StateLawPort stateLawPort;
    private StateLawSeedRunner runner;

    @BeforeEach
    void setUp() {
        stateLawPort = mock(StateLawPort.class);
        runner = new StateLawSeedRunner(stateLawPort);
    }

    @Test
    void run_loadsAndSeedsLaws() throws Exception {
        runner.run(null);

        verify(stateLawPort, times(58)).upsert(any(StateLaw.class));
    }

    @Test
    void run_idempotent_callsUpsert() throws Exception {
        runner.run(null);
        runner.run(null);

        verify(stateLawPort, times(116)).upsert(any(StateLaw.class));
    }

    @Test
    void run_parsesAllFields_firstRecord() throws Exception {
        ArgumentCaptor<StateLaw> captor = ArgumentCaptor.forClass(StateLaw.class);

        runner.run(null);

        verify(stateLawPort, times(58)).upsert(captor.capture());
        List<StateLaw> allLaws = captor.getAllValues();

        // Find the AL SB 63 record
        StateLaw alLaw = allLaws.stream()
                .filter(l -> "al-sb-63".equals(l.id()))
                .findFirst()
                .orElse(null);

        assertThat(alLaw).isNotNull();
        assertThat(alLaw.stateCode()).isEqualTo(StateCode.AL);
        assertThat(alLaw.stateName()).isEqualTo("Alabama");
        assertThat(alLaw.title()).isNotBlank();
        assertThat(alLaw.categories()).isNotEmpty();
        assertThat(alLaw.sources()).isNotEmpty();
        assertThat(alLaw.createdAt()).isNotNull();
        assertThat(alLaw.updatedAt()).isNotNull();
    }

    @Test
    void run_parsesMultipleStates() throws Exception {
        ArgumentCaptor<StateLaw> captor = ArgumentCaptor.forClass(StateLaw.class);

        runner.run(null);

        verify(stateLawPort, times(58)).upsert(captor.capture());
        List<StateLaw> allLaws = captor.getAllValues();

        // Verify multiple states are present in the seed data
        long distinctStates = allLaws.stream()
                .map(StateLaw::stateCode)
                .distinct()
                .count();
        assertThat(distinctStates).isGreaterThanOrEqualTo(20);

        // Verify all records have required fields
        for (StateLaw law : allLaws) {
            assertThat(law.id()).isNotBlank();
            assertThat(law.stateCode()).isNotNull();
            assertThat(law.title()).isNotBlank();
            assertThat(law.status()).isNotNull();
        }
    }
}

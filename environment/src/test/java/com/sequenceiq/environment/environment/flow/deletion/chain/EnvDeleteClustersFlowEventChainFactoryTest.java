package com.sequenceiq.environment.environment.flow.deletion.chain;

import static com.sequenceiq.environment.environment.flow.deletion.chain.FlowChainTriggers.ENV_DELETE_CLUSTERS_TRIGGER_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvClustersDeleteStateSelectors.START_DATAHUB_CLUSTERS_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_FREEIPA_DELETE_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.common.event.AcceptResult;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteEvent;
import com.sequenceiq.environment.environment.service.EnvironmentService;
import com.sequenceiq.flow.core.chain.config.FlowTriggerEventQueue;

import reactor.rx.Promise;

class EnvDeleteClustersFlowEventChainFactoryTest {

    private static final Set<String> FLOW_EVENTS = Set.of(START_DATAHUB_CLUSTERS_DELETE_EVENT.event(), START_FREEIPA_DELETE_EVENT.event());

    private static final VerificationMode ONCE = times(1);

    private static final String USER_CRN = "crn:cdp:iam:us-west-1:1234:user:1";

    private EnvDeleteClustersFlowEventChainFactory underTest;

    @Mock
    private EnvironmentService mockEnvironmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        underTest = new EnvDeleteClustersFlowEventChainFactory(mockEnvironmentService);
    }

    @Test
    void testInitEventShouldReturnTheExpectedValue() {
        assertEquals(ENV_DELETE_CLUSTERS_TRIGGER_EVENT, underTest.initEvent());
    }

    @Test
    void testCreateFlowTriggerEventQueueShouldContainAllNecessaryPassedFields() {
        ThreadBasedUserCrnProvider.doAs(USER_CRN, () -> {
            EnvDeleteEvent event = createEnvDeleteEvent();
            FlowTriggerEventQueue result = underTest.createFlowTriggerEventQueue(event);

            assertNotNull(result);
            assertNotNull(result.getQueue());
            assertEquals(2, result.getQueue().size());

            for (Selectable selectable : result.getQueue()) {
                EnvDeleteEvent actual = (EnvDeleteEvent) selectable;
                assertTrue(FLOW_EVENTS.contains(actual.selector()));
                assertEquals(event.getResourceId(), actual.getResourceId());
                assertEquals(event.getResourceName(), actual.getResourceName());
                assertEquals(event.getResourceCrn(), actual.getResourceCrn());
                assertTrue(actual.isForceDelete());
            }
        });
    }

    @Test
    void testCreateFlowTriggerEventQueueOnlyStartDatahubClustersDeleteEventShouldContainAccepted() {
        ThreadBasedUserCrnProvider.doAs(USER_CRN, () -> {
            EnvDeleteEvent event = createEnvDeleteEvent();
            FlowTriggerEventQueue result = underTest.createFlowTriggerEventQueue(event);

            EnvDeleteEvent startDatahubClustersDeleteEvent = result.getQueue()
                    .stream()
                    .map(selectable -> (EnvDeleteEvent) selectable)
                    .filter(envDeleteEvent -> START_DATAHUB_CLUSTERS_DELETE_EVENT.selector().equals(envDeleteEvent.selector()))
                    .findFirst()
                    .get();

            assertEquals(event.accepted(), startDatahubClustersDeleteEvent.accepted());

            result.getQueue()
                    .stream()
                    .map(selectable -> (EnvDeleteEvent) selectable)
                    .filter(envDeleteEvent -> !START_DATAHUB_CLUSTERS_DELETE_EVENT.selector().equals(envDeleteEvent.selector()))
                    .collect(Collectors.toSet())
                    .forEach(envDeleteEvent -> {
                        assertNull(envDeleteEvent.accepted());
                    });
        });
    }

    @Test
    void testCreateFlowTriggerEventQueueShouldGoForEnvServiceForChildEnvs() {
        EnvDeleteEvent event = createEnvDeleteEvent();
        ThreadBasedUserCrnProvider.doAs(USER_CRN, () -> underTest.createFlowTriggerEventQueue(event));

        verify(mockEnvironmentService, ONCE).findAllByAccountIdAndParentEnvIdAndArchivedIsFalse(anyString(), anyLong());
    }

    private EnvDeleteEvent createEnvDeleteEvent() {
        Promise<AcceptResult> accepted = new Promise<>();
        return new EnvDeleteEvent("eventSelector", 1L, accepted, "resourceName", "resourceCrn", true);
    }

}
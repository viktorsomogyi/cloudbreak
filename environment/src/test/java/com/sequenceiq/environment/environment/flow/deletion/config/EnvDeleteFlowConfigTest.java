package com.sequenceiq.environment.environment.flow.deletion.config;

import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.CLUSTER_DEFINITION_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.ENV_DELETE_FAILED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.ENV_DELETE_FINISHED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.FINAL_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.FREEIPA_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.IDBROKER_MAPPINGS_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.INIT_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.NETWORK_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.PUBLICKEY_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.RDBMS_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.S3GUARD_TABLE_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState.UMS_RESOURCE_DELETE_STARTED_STATE;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.FINALIZE_ENV_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.FINISH_ENV_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.HANDLED_FAILED_ENV_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_CLUSTER_DEFINITION_CLEANUP_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_FREEIPA_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_IDBROKER_MAPPINGS_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_NETWORK_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_PUBLICKEY_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_RDBMS_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_S3GUARD_TABLE_DELETE_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_UMS_RESOURCE_DELETE_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.ReflectionUtils;

import com.sequenceiq.environment.environment.flow.deletion.EnvDeleteState;
import com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors;
import com.sequenceiq.flow.core.config.AbstractFlowConfiguration.FlowEdgeConfig;
import com.sequenceiq.flow.core.config.AbstractFlowConfiguration.Transition;

class EnvDeleteFlowConfigTest {

    private static final EnvDeleteStateSelectors EXPECTED_DEFAULT_FAILURE_EVENT = EnvDeleteStateSelectors.FAILED_ENV_DELETE_EVENT;

    private List<TransitionKeeper> storedTransitions;

    private EnvDeleteFlowConfig underTest;

    public EnvDeleteFlowConfigTest() throws IllegalAccessException {
        underTest = new EnvDeleteFlowConfig();
        List<Transition<EnvDeleteState, EnvDeleteStateSelectors>> transitions = underTest.getTransitions();
        storedTransitions = new ArrayList<>(transitions.size());
        int ordinal = 1;
        for (Transition<EnvDeleteState, EnvDeleteStateSelectors> transition : underTest.getTransitions()) {
            storedTransitions.add(new TransitionKeeper(ordinal, transition.getSource(), transition.getTarget(), transition.getFailureEvent(),
                    extractEventFromAbstractFlowConfigurationTransition(transition)));
            ordinal++;
        }
    }

    @BeforeEach
    void setUp() {
        underTest = new EnvDeleteFlowConfig();
    }

    @Test
    void testInitialEventShouldBeTheStartFreeIpaDeleteEvent() {
        EnvDeleteStateSelectors[] initEvents = underTest.getInitEvents();

        assertNotNull(initEvents);
        assertEquals(1, initEvents.length);
        assertEquals(START_FREEIPA_DELETE_EVENT, initEvents[0]);
    }

    @Test
    void testDeleteDisplayNameShouldBeTheExpected() {
        String expectedDisplayName = "Delete environment";
        assertEquals(expectedDisplayName, underTest.getDisplayName());
    }

    @Test
    void testTheRetryableEventShouldBeTheExpected() {
        assertEquals(HANDLED_FAILED_ENV_DELETE_EVENT, underTest.getRetryableEvent());
    }

    @Test
    void testEdgeConfigInitStateShouldBeTheExpected() {
        FlowEdgeConfig<EnvDeleteState, EnvDeleteStateSelectors> edgeConfigs = underTest.getEdgeConfig();

        assertEquals(INIT_STATE, edgeConfigs.getInitState());
    }

    @Test
    void testEdgeConfigFinalStateShouldBeTheExpected() {
        FlowEdgeConfig<EnvDeleteState, EnvDeleteStateSelectors> edgeConfigs = underTest.getEdgeConfig();

        assertEquals(FINAL_STATE, edgeConfigs.getFinalState());
    }

    @Test
    void testEdgeConfigDefaultFailureStateShouldBeTheExpected() {
        FlowEdgeConfig<EnvDeleteState, EnvDeleteStateSelectors> edgeConfigs = underTest.getEdgeConfig();

        assertEquals(ENV_DELETE_FAILED_STATE, edgeConfigs.getDefaultFailureState());
    }

    @Test
    void testEdgeConfigFailureHandledStateShouldBeTheExpected() {
        FlowEdgeConfig<EnvDeleteState, EnvDeleteStateSelectors> edgeConfigs = underTest.getEdgeConfig();

        assertEquals(HANDLED_FAILED_ENV_DELETE_EVENT, edgeConfigs.getFailureHandled());
    }

    @Test
    void testEventsShouldComeFromEnvDeleteStateSelectorsWithoutFiltering() {
        EnvDeleteStateSelectors[] expectedSelectors = EnvDeleteStateSelectors.values();
        EnvDeleteStateSelectors[] resultSelectors = underTest.getEvents();

        assertEquals(expectedSelectors.length, resultSelectors.length);
    }

    @MethodSource("scenarios")
    @ParameterizedTest(name = "{0}")
    void testTransitions(String testName, int expectedOrdinal, EnvDeleteState expectedFromState, EnvDeleteState expectedToState,
            EnvDeleteStateSelectors expectedInitiatedEvent) {
        storedTransitions.stream()
                .filter(transition -> transition.getFromState() == expectedFromState)
                .forEach(transition -> {
                    assertEquals(expectedInitiatedEvent, transition.getInitiatedEvent());
                    assertEquals(EXPECTED_DEFAULT_FAILURE_EVENT, transition.getDefaultFailureEvent());
                    assertEquals(expectedToState, transition.getToState(), "The target state is not matching with the expected!");
                    assertEquals(expectedOrdinal, transition.getOrdinal(), "The given flow step is not in the expected position in the whole process!");
                });
    }

    // @formatter:off
    // CHECKSTYLE:OFF
    static Object[][] scenarios() {
        return new Object[][] {
                // testName                                                                                                                                         #   source                                   target                                     event
                { "Init state should be right before the FreeIPA deletion state that should start the FreeIPA deletion event",                                      1,  INIT_STATE,                              FREEIPA_DELETE_STARTED_STATE,              START_FREEIPA_DELETE_EVENT},
                { "FreeIPA delete start state should be right before the RDBMS deletion state that should start the RDBMS deletion event",                          2,  FREEIPA_DELETE_STARTED_STATE,            RDBMS_DELETE_STARTED_STATE,                START_RDBMS_DELETE_EVENT},
                { "RDBMS delete state should be right before the public key deletion state that should start the public key deletion event",                        3,  RDBMS_DELETE_STARTED_STATE,              PUBLICKEY_DELETE_STARTED_STATE,            START_PUBLICKEY_DELETE_EVENT},
                { "Public key delete state should be right before the network deletion state that should start the network deletion event",                         4,  PUBLICKEY_DELETE_STARTED_STATE,          NETWORK_DELETE_STARTED_STATE,              START_NETWORK_DELETE_EVENT},
                { "Network delete state should be right before the IDBroker mappings deletion state that should start the IDBroker mappings deletion event",        5,  NETWORK_DELETE_STARTED_STATE,            IDBROKER_MAPPINGS_DELETE_STARTED_STATE,    START_IDBROKER_MAPPINGS_DELETE_EVENT},
                { "IDBroker mappings delete state should be right before the S3Guard table deletion state that should start the S3Guard table deletion event",      6,  IDBROKER_MAPPINGS_DELETE_STARTED_STATE,  S3GUARD_TABLE_DELETE_STARTED_STATE,        START_S3GUARD_TABLE_DELETE_EVENT},
                { "S3Guard table delete state should be right before the cluster definition deletion state that should start the cluster definition cleanup event", 7,  S3GUARD_TABLE_DELETE_STARTED_STATE,      CLUSTER_DEFINITION_DELETE_STARTED_STATE,   START_CLUSTER_DEFINITION_CLEANUP_EVENT},
                { "Cluster definition delete state should be right before the UMS resource deletion state that should start the UMS resource deletion event",       8,  CLUSTER_DEFINITION_DELETE_STARTED_STATE, UMS_RESOURCE_DELETE_STARTED_STATE,         START_UMS_RESOURCE_DELETE_EVENT},
                { "UMS resource deletion state should be right before the environment deletion finished state that should start the finish env delete event",       9,  UMS_RESOURCE_DELETE_STARTED_STATE,       ENV_DELETE_FINISHED_STATE,                 FINISH_ENV_DELETE_EVENT},
                { "Environment deletion finished state should be right before the final state that should start the finalize env delete event",                     10, ENV_DELETE_FINISHED_STATE,               FINAL_STATE,                               FINALIZE_ENV_DELETE_EVENT},
        };
    }
    // CHECKSTYLE:ON
    // @formatter:on

    private static final class TransitionKeeper {

        private final int ordinal;

        private final EnvDeleteState fromState;

        private final EnvDeleteState toState;

        private final EnvDeleteStateSelectors initiatedEvent;

        private final EnvDeleteStateSelectors defaultFailureEvent;

        public TransitionKeeper(int ordinal, EnvDeleteState fromState, EnvDeleteState toState, EnvDeleteStateSelectors defaultFailureEvent,
                EnvDeleteStateSelectors initiatedEvent) {
            this.defaultFailureEvent = defaultFailureEvent;
            this.initiatedEvent = initiatedEvent;
            this.fromState = fromState;
            this.ordinal = ordinal;
            this.toState = toState;
        }

        public int getOrdinal() {
            return ordinal;
        }

        public EnvDeleteState getToState() {
            return toState;
        }

        public EnvDeleteState getFromState() {
            return fromState;
        }

        public EnvDeleteStateSelectors getInitiatedEvent() {
            return initiatedEvent;
        }

        public EnvDeleteStateSelectors getDefaultFailureEvent() {
            return defaultFailureEvent;
        }

    }

    private EnvDeleteStateSelectors extractEventFromAbstractFlowConfigurationTransition(Transition<EnvDeleteState, EnvDeleteStateSelectors> transition)
            throws IllegalAccessException {
        Field field = ReflectionUtils.findField(Transition.class, "event");
        field.setAccessible(true);
        return (EnvDeleteStateSelectors) field.get(transition);
    }

}
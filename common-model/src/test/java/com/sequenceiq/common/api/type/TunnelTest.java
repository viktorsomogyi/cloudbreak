package com.sequenceiq.common.api.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TunnelTest {

    @Test
    void testLatestUpgradeTarget() {
        assertThat(Tunnel.latestUpgradeTarget()).isEqualTo(Tunnel.CCMV2_JUMPGATE);
    }
}

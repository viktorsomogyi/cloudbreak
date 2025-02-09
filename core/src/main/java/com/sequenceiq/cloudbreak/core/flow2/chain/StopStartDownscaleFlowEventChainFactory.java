package com.sequenceiq.cloudbreak.core.flow2.chain;

import static com.sequenceiq.cloudbreak.core.flow2.stack.sync.StackSyncEvent.STACK_SYNC_EVENT;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.core.flow2.cluster.stopstartds.StopStartDownscaleEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterAndStackDownscaleTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.StopStartDownscaleTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.StackSyncTriggerEvent;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.view.ClusterView;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.flow.core.chain.FlowEventChainFactory;
import com.sequenceiq.flow.core.chain.config.FlowTriggerEventQueue;

@Component
public class StopStartDownscaleFlowEventChainFactory implements FlowEventChainFactory<ClusterAndStackDownscaleTriggerEvent> {

    @Inject
    private StackService stackService;

    @Inject
    private HostGroupService hostGroupService;

    @Override
    public String initEvent() {
        return FlowChainTriggers.STOPSTART_DOWNSCALE_CHAIN_TRIGGER_EVENT;
    }

    @Override
    public FlowTriggerEventQueue createFlowTriggerEventQueue(ClusterAndStackDownscaleTriggerEvent event) {

        StackView stackView = stackService.getViewByIdWithoutAuth(event.getResourceId());
        ClusterView clusterView = stackView.getClusterView();
        HostGroup hostGroup = hostGroupService.getByClusterIdAndName(clusterView.getId(), event.getHostGroupName())
                .orElseThrow(NotFoundException.notFound("hostgroup", event.getHostGroupName()));

        StopStartDownscaleTriggerEvent te = new StopStartDownscaleTriggerEvent(
                StopStartDownscaleEvent.STOPSTART_DOWNSCALE_TRIGGER_EVENT.event(),
                stackView.getId(),
                hostGroup.getName(),
                Sets.newHashSet(event.getPrivateIds()),
                event.getClusterManagerType()
        );

        Queue<Selectable> flowEventChain = new ConcurrentLinkedQueue<>();

        // TODO CB-14929: Is a stack sync really required here. What does it do ? (As of now it also serves to accept the event)
        addStackSyncTriggerEvent(event, flowEventChain);

        flowEventChain.add(te);

        return new FlowTriggerEventQueue(getName(), event, flowEventChain);
    }

    private void addStackSyncTriggerEvent(ClusterAndStackDownscaleTriggerEvent event, Queue<Selectable> flowEventChain) {
        flowEventChain.add(new StackSyncTriggerEvent(
                STACK_SYNC_EVENT.event(),
                event.getResourceId(),
                false,
                event.accepted())
        );
    }
}

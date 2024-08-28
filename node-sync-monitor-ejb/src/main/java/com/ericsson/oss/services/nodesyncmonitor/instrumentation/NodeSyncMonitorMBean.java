/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2022
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.nodesyncmonitor.instrumentation;

import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;

@InstrumentedBean(description = "Collects Node Synchronization Monitor metrics for upload to DDC.",
        displayName = "Node Synchronization Monitor Metrics")
@ApplicationScoped
public class NodeSyncMonitorMBean {

    private final AtomicLong totalNoOfCmSyncFailuresBeforeAlarmEventsReceived = new AtomicLong();
    private final AtomicLong totalCmNodeSyncMonitorFeatureEventsReceived = new AtomicLong();
    private final AtomicLong totalFailedSyncsCountEventsReceived = new AtomicLong();
    private final AtomicLong totalCmNodeHeartbeatSupervisionEventsReceived = new AtomicLong();
    private final AtomicLong totalCmUnsyncedAlarmsRaised = new AtomicLong();
    private final AtomicLong totalCmUnsyncedAlarmsCleared = new AtomicLong();

    @MonitoredAttribute(displayName = "Total count of 'noOfCmSyncFailuresBeforeAlarm' configuration change events received.",
            visibility = MonitoredAttribute.Visibility.INTERNAL, collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() {
        return totalNoOfCmSyncFailuresBeforeAlarmEventsReceived.get();
    }

    @MonitoredAttribute(displayName = "Total count of 'cmNodeSyncMonitorFeature' configuration change events received.",
            visibility = MonitoredAttribute.Visibility.INTERNAL, collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalCmNodeSyncMonitorFeatureEventsReceived() {
        return totalCmNodeSyncMonitorFeatureEventsReceived.get();
    }

    @MonitoredAttribute(displayName = "Total count of 'CmFunction.failedSyncsCount' change events received.",
            visibility = MonitoredAttribute.Visibility.INTERNAL, collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalFailedSyncsCountEventsReceived() {
        return totalFailedSyncsCountEventsReceived.get();
    }

    @MonitoredAttribute(displayName = "Total count of 'CmNodeHeartbeatSupervision.active' change events received.",
            visibility = MonitoredAttribute.Visibility.INTERNAL, collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalCmNodeHeartbeatSupervisionEventsReceived() {
        return totalCmNodeHeartbeatSupervisionEventsReceived.get();
    }

    @MonitoredAttribute(displayName = "Total count of CM unsynchronized alarms raised.", visibility = MonitoredAttribute.Visibility.INTERNAL,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalCmUnsyncedAlarmsRaised() {
        return totalCmUnsyncedAlarmsRaised.get();
    }

    @MonitoredAttribute(displayName = "Total count of CM unsynchronized alarms cleared.", visibility = MonitoredAttribute.Visibility.INTERNAL,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTotalCmUnsyncedAlarmsCleared() {
        return totalCmUnsyncedAlarmsCleared.get();
    }

    public void incrementNoOfCmSyncFailuresBeforeAlarmEventReceived() {
        totalNoOfCmSyncFailuresBeforeAlarmEventsReceived.addAndGet(1);
    }

    public void incrementCmNodeSyncMonitorFeatureEventReceived() {
        totalCmNodeSyncMonitorFeatureEventsReceived.addAndGet(1);
    }

    public void incrementFailedSyncsCountEventReceived() {
        totalFailedSyncsCountEventsReceived.addAndGet(1);
    }

    public void incrementCmNodeHeartbeatSupervisionEventReceived() {
        totalCmNodeHeartbeatSupervisionEventsReceived.addAndGet(1);
    }

    public void incrementCmUnsyncedAlarmRaised() {
        totalCmUnsyncedAlarmsRaised.addAndGet(1);
    }

    public void incrementCmUnsyncedAlarmCleared() {
        totalCmUnsyncedAlarmsCleared.addAndGet(1);
    }
}

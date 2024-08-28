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

package com.ericsson.oss.services.nodesyncmonitor.listener;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.nodesyncmonitor.alarm.AlarmSender;
import com.ericsson.oss.services.nodesyncmonitor.cluster.Master;
import com.ericsson.oss.services.nodesyncmonitor.dps.Dps;
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.List;

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_NODE_SYNC_MONITOR_FEATURE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.OFF;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ON;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PIB_CHANGE_EVENT_MESSAGE;

/**
 * Listens for changes to the cmNodeSyncMonitorFeature PIB property.
 * If cmNodeSyncMonitorFeature is turned ON, an alarm is raised for all NetworkElements that meet the following criteria:
 * <ul>
 * <li>CmNodeHeartbeatSupervision.active is TRUE</li>
 * <li>CmFunction.failedSyncsCount is >= noOfCmSyncFailuresBeforeAlarm</li>
 * </p>
 * Note that when the feature is enabled a query will be performed for any existing open alarms with 'specificProblem' set to 'CM unsynchronized'.
 * No such alarms should exist, but it is possible that some might if there was a failure to clear them when the feature was disabled.
 * Any valid alarms found will be kept and no request sent to recreate them. Any invalid alarms found will be cleared.
 * <p>
 * If cmNodeSyncMonitorFeature is turned OFF, all alarms with 'specificProblem' set to 'CM unsynchronized' will be cleared.
 * </p>
 */
@Master
@ApplicationScoped
public class CmNodeSyncMonitorFeatureListener {

    private static final String LISTENER = CmNodeSyncMonitorFeatureListener.class.getSimpleName();

    private static final Logger LOGGER = LoggerFactory.getLogger(CmNodeSyncMonitorFeatureListener.class);

    @Inject
    @Configured(propertyName = "cmNodeSyncMonitorFeature")
    private String cmNodeSyncMonitorFeature;

    @Inject
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarm;

    @Inject
    private Dps dps;

    @Inject
    private AlarmSender alarmSender;

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;

    public void listenForChangeInCmNodeSyncMonitorFeature(
            @Observes @ConfigurationChangeNotification(propertyName = CM_NODE_SYNC_MONITOR_FEATURE) final String newValue) {

        LOGGER.info(PIB_CHANGE_EVENT_MESSAGE,  CM_NODE_SYNC_MONITOR_FEATURE, cmNodeSyncMonitorFeature, newValue);
        nodeSyncMonitorMBean.incrementCmNodeSyncMonitorFeatureEventReceived();
        cmNodeSyncMonitorFeature = newValue;
        processNodeSyncMonitorFeatureEvent();
    }

    public String getCmNodeSyncMonitorFeature() {
        return cmNodeSyncMonitorFeature;
    }

    private void processNodeSyncMonitorFeatureEvent() {
        final List<String> neFdnsWithOpenAlarm = dps.findNetworkElementsWithOpenAlarm();

        if (ON.equalsIgnoreCase(cmNodeSyncMonitorFeature)) {
            final List<String> neFdnsRequiringAlarm = dps.findNetworkElementsThatNeedAlarm(noOfCmSyncFailuresBeforeAlarm.getNoOfCmSyncFailuresBeforeAlarm());
            neFdnsRequiringAlarm.forEach(fdn -> alarmSender.raiseAlarm(fdn, LISTENER));
            neFdnsWithOpenAlarm.removeAll(neFdnsRequiringAlarm);
            neFdnsWithOpenAlarm.forEach(fdn -> alarmSender.clearAlarm(fdn, LISTENER));
        }
        if (OFF.equalsIgnoreCase(cmNodeSyncMonitorFeature)) {
            neFdnsWithOpenAlarm.forEach(fdn -> alarmSender.clearAlarm(fdn, LISTENER));
        }
    }
}

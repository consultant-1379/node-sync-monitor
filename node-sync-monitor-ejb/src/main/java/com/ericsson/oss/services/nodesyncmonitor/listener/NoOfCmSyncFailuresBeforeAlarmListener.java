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

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NO_OF_CM_SYNC_FAILURES_BEFORE_ALARM;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ON;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PIB_CHANGE_EVENT_MESSAGE;

/**
 * Listens for the changes to the 'noOfCmSyncFailuresBeforeAlarm' PIB property.
 * An alarm is raised if the sync failures reached the 'noOfCmSyncFailuresBeforeAlarm' and the alarm doesn't exist, otherwise is cleared. Both are
 * applicable only if the NodeSyncMonitorFeature and the CM supervision is enabled.
 */
@Master
@ApplicationScoped
public class NoOfCmSyncFailuresBeforeAlarmListener {

    private static final String LISTENER = NoOfCmSyncFailuresBeforeAlarmListener.class.getSimpleName();

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOfCmSyncFailuresBeforeAlarmListener.class);

    @Inject
    @Configured(propertyName = NO_OF_CM_SYNC_FAILURES_BEFORE_ALARM)
    private int noOfCmSyncFailuresBeforeAlarm;

    @Inject
    private Dps dps;

    @Inject
    private AlarmSender alarmSender;

    @Inject
    private CmNodeSyncMonitorFeatureListener cmNodeSyncMonitorFeatureListener;

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;


    public void listenForChangeInNoOfCmSyncFailuresBeforeAlarm(@Observes @ConfigurationChangeNotification(
            propertyName = NO_OF_CM_SYNC_FAILURES_BEFORE_ALARM) final int newValue) {

        LOGGER.info(PIB_CHANGE_EVENT_MESSAGE,  NO_OF_CM_SYNC_FAILURES_BEFORE_ALARM, noOfCmSyncFailuresBeforeAlarm, newValue);
        noOfCmSyncFailuresBeforeAlarm = newValue;
        nodeSyncMonitorMBean.incrementNoOfCmSyncFailuresBeforeAlarmEventReceived();
        if (ON.equalsIgnoreCase(cmNodeSyncMonitorFeatureListener.getCmNodeSyncMonitorFeature())) {
            handleAlarm();
        }
    }

    public int getNoOfCmSyncFailuresBeforeAlarm() {
        return noOfCmSyncFailuresBeforeAlarm;
    }

    /**
     * Raises/clears alarms using the following algorithm.
     * <ul>
     * <li>Finds the NetworkElement FDNs that have alarms - they have OpenAlarm.specificProblem set to 'CM unsynchronized'.</li>
     * <li>Finds the NetworkElement FDNs that require alarms - they have CmFunction.failedSyncsCount equal or higher than the value of
     * 'noOfCmSyncFailuresBeforeAlarm'.</li>
     * <li>For any NE that has an open alarm and a failedSyncsCount that is less than the new noOfCmSyncFailuresBeforeAlarm - alarms are cleared.</li>
     * <li>For any NE that has does not have an open alarm and a failedSyncsCount greater than or equal to the new noOfCmSyncFailuresBeforeAlarm -
     * alarms are raised.</li>
     * </ul>
     *
     */
    private void handleAlarm() {
        final List<String> neFdnsWithOpenAlarm = dps.findNetworkElementsWithOpenAlarm();
        final List<String> neFdnsRequiringAlarm = dps.findNetworkElementsThatNeedAlarm(noOfCmSyncFailuresBeforeAlarm);

        neFdnsWithOpenAlarm.forEach(neFdn -> {
            if (!neFdnsRequiringAlarm.contains(neFdn)) {
                alarmSender.clearAlarm(neFdn, LISTENER);
            }
        });

        neFdnsRequiringAlarm.forEach(neFdn -> {
            if (!neFdnsWithOpenAlarm.contains(neFdn)) {
                alarmSender.raiseAlarm(neFdn, LISTENER);
            }
        });
    }
}

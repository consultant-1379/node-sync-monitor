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

import static com.ericsson.oss.itpf.datalayer.dps.notification.DpsNotificationConfiguration.DPS_EVENT_NOTIFICATION_CHANNEL_URI;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ACTIVE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_FUNCTION;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_NODE_HEARTBEAT_SUPERVISION_RDN;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FAILED_SYNCS_COUNT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.DPS_CHANGE_EVENT_MESSAGE;
import static com.ericsson.oss.services.nodesyncmonitor.util.FdnUtil.getParentFdn;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.annotation.Consumes;
import com.ericsson.oss.services.nodesyncmonitor.alarm.AlarmSender;
import com.ericsson.oss.services.nodesyncmonitor.cluster.Master;
import com.ericsson.oss.services.nodesyncmonitor.dps.Dps;
import com.ericsson.oss.services.nodesyncmonitor.feature.NodeSyncMonitorFeatureActive;
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean;
import com.ericsson.oss.services.nodesyncmonitor.recording.SystemRecorderBean;
import com.ericsson.oss.services.nodesyncmonitor.util.FdnUtil;

/**
 * A class that listens for DPS changes to the CmFunction.failedSyncsCount attribute.
 * An alarm is raised when the following conditions are met:-
 * - NodeSyncMonitorFeature is ON
 * - CmNodeHeartbeatSupervision.active is TRUE
 * - New failedSyncsCount value is equal to the alarm threshold PIB value
 * An alarm is cleared when the following conditions are met:-
 * - NodeSyncMonitorFeature is ON
 * - CmNodeHeartbeatSupervision.active is TRUE
 * - New failedSyncsCount is 0 and the old value was equal to or bigger than the alarm threshold PIB value
 */
@Master
@NodeSyncMonitorFeatureActive
@ApplicationScoped
public class FailedSyncsCountListener {

    private static final String LISTENER = FailedSyncsCountListener.class.getSimpleName();
    private static final Logger LOGGER = LoggerFactory.getLogger(FailedSyncsCountListener.class);
    private static final String FAILED_SYNCS_COUNT_EVENT_FILTER = "((type='" + CM_FUNCTION + "') AND " +
            "(attributeName LIKE '%" + FAILED_SYNCS_COUNT + "%'))";

    @Inject
    private SystemRecorderBean systemRecorderBean;
    @Inject
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarmListener;
    @Inject
    private Dps dps;
    @Inject
    private AlarmSender alarmSender;
    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;

    /**
     * DPS event notification listener for the attribute 'failedSyncsCount' on 'CmFunction' MO.
     *
     * @param dpsEvent
     *            The attribute changed event to be processed.
     */
    public void processFailedSyncsCountEvent(@Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI,
            filter = FAILED_SYNCS_COUNT_EVENT_FILTER) final DpsAttributeChangedEvent dpsEvent) {

        nodeSyncMonitorMBean.incrementFailedSyncsCountEventReceived();

        for (final AttributeChangeData attrData : dpsEvent.getChangedAttributes()) {
            if (FAILED_SYNCS_COUNT.equals(attrData.getName())) {
                LOGGER.info(DPS_CHANGE_EVENT_MESSAGE, dpsEvent.getFdn(), FAILED_SYNCS_COUNT, attrData.getOldValue(), attrData.getNewValue());
                recordAlarm((int) attrData.getOldValue(), (int) attrData.getNewValue(), dpsEvent.getFdn());
                break;
            }
        }
    }

    /**
     * Raises an alarm when the failedSyncsCount new value is equal to the alarm threshold or a clears alarm if the
     * new value is 0 after the old value reached the alarm threshold.
     */
    private void recordAlarm(final int oldFailedSyncsCount, final int newFailedSyncsCount, final String cmFunctionFdn) {
        final int noOfCmSyncFailuresBeforeAlarm = noOfCmSyncFailuresBeforeAlarmListener.getNoOfCmSyncFailuresBeforeAlarm();
        final String networkElementFdn = FdnUtil.getParentFdn(cmFunctionFdn);

        if (isAlarmRequired(oldFailedSyncsCount, newFailedSyncsCount, noOfCmSyncFailuresBeforeAlarm) && isCmSupervisionActive(cmFunctionFdn)) {
            if (shouldRaiseAlarm(newFailedSyncsCount, noOfCmSyncFailuresBeforeAlarm)) {
                alarmSender.raiseAlarm(networkElementFdn, LISTENER);
            }
            if (shouldClearAlarm(oldFailedSyncsCount, newFailedSyncsCount, noOfCmSyncFailuresBeforeAlarm)) {
                alarmSender.clearAlarm(networkElementFdn, LISTENER);
            }
        }
    }

    private boolean isAlarmRequired(final int oldFailedSyncsCount, final int newFailedSyncsCount, final int alarmThreshold) {
        return shouldRaiseAlarm(newFailedSyncsCount, alarmThreshold) || shouldClearAlarm(oldFailedSyncsCount, newFailedSyncsCount, alarmThreshold);
    }

    private boolean shouldClearAlarm(final int oldFailedSyncsCount, final int newFailedSyncsCount, final int alarmThreshold) {
        return newFailedSyncsCount == 0 && oldFailedSyncsCount >= alarmThreshold;

    }

    private boolean shouldRaiseAlarm(final int newFailedSyncsCount, final int alarmThreshold) {
        return newFailedSyncsCount == alarmThreshold;
    }

    private boolean isCmSupervisionActive(final String cmFunctionFdn) {
        final String cmNodeHeartbeatSupervisionFdn = getParentFdn(cmFunctionFdn) + "," + CM_NODE_HEARTBEAT_SUPERVISION_RDN;
        return dps.findAttributeValue(cmNodeHeartbeatSupervisionFdn, ACTIVE);
    }
}

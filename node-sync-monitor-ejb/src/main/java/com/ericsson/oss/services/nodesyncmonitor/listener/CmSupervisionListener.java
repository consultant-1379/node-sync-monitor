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
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_FUNCTION_RDN;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_NODE_HEARTBEAT_SUPERVISION;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FAILED_SYNCS_COUNT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.DPS_CHANGE_EVENT_MESSAGE;

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
 * A class that listens for DPS changes to the CmNodeHeartbeatSupervision.active attribute.
 * An alarm is raised when the following conditions are met:-
 * - NodeSyncMonitorFeature is ON
 * - CmFunction.failedSyncsCount is bigger or equal to the alarm threshold PIB value
 * - CmNodeHeartbeatSupervision.active changes to TRUE
 * <p>
 * An alarm is cleared when the following conditions are met:-
 * - NodeSyncMonitorFeature is ON
 * - CmFunction.failedSyncsCount is bigger or equal to the alarm threshold PIB value
 * - CmNodeHeartbeatSupervision.active changes to FALSE
 * <p>
 * When the NodeSyncMonitorFeature is OFF, nothing is done.
 */
@Master
@NodeSyncMonitorFeatureActive
@ApplicationScoped
public class CmSupervisionListener {

    private static final String LISTENER = CmSupervisionListener.class.getSimpleName();
    private static final String CM_SUPERVISION_FILTER = "((type='" + CM_NODE_HEARTBEAT_SUPERVISION + "') AND " +
            "(attributeName LIKE '%" + ACTIVE + "%'))";

    private static final Logger LOGGER = LoggerFactory.getLogger(CmSupervisionListener.class);

    @Inject
    private SystemRecorderBean systemRecorder;
    @Inject
    private Dps dps;
    @Inject
    private AlarmSender alarmSender;
    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;
    @Inject
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarmListener;

    /**
     * Listener method for changes to the CmNodeHeartbeatSupervision.active attribute.
     * Note this will also receive any of the other attributes of the MO that may have changed also.
     *
     * @param dpsAttributeChangedEvent
     */
    public void processCmSupervisionChangeEvent(@Observes @Consumes(endpoint = DPS_EVENT_NOTIFICATION_CHANNEL_URI,
            filter = CM_SUPERVISION_FILTER) final DpsAttributeChangedEvent dpsAttributeChangedEvent) {

        nodeSyncMonitorMBean.incrementCmNodeHeartbeatSupervisionEventReceived();

        for (final AttributeChangeData attrData : dpsAttributeChangedEvent.getChangedAttributes()) {
            if (ACTIVE.equalsIgnoreCase(attrData.getName())) {
                LOGGER.info(DPS_CHANGE_EVENT_MESSAGE, dpsAttributeChangedEvent.getFdn(), ACTIVE, attrData.getOldValue(), attrData.getNewValue());
                checkSupervisionActiveAttributeChange((boolean) attrData.getNewValue(), FdnUtil.getParentFdn(dpsAttributeChangedEvent.getFdn()));
                break;
            }
        }
    }

    /**
     * If the failedSyncsCount is greater or equal to the alarm threshold value and the supervision was set to TRUE
     * then alarm raised is logged, if the supervision was set to FALSE then alarm cleared is logged.
     */
    private void checkSupervisionActiveAttributeChange(final boolean activeAttributeNewValue, final String networkElementFdn) {

        final int noOfCmSyncFailuresBeforeAlarm = noOfCmSyncFailuresBeforeAlarmListener.getNoOfCmSyncFailuresBeforeAlarm();
        final String cmFunctionFdn = networkElementFdn + "," + CM_FUNCTION_RDN;
        final int failedSyncsCount = dps.findAttributeValue(cmFunctionFdn, FAILED_SYNCS_COUNT);

        if (failedSyncsCount >= noOfCmSyncFailuresBeforeAlarm) {
            if (activeAttributeNewValue) {
                alarmSender.raiseAlarm(networkElementFdn, LISTENER);
            } else {
                alarmSender.clearAlarm(networkElementFdn, LISTENER);
            }
        }
    }
}

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
 -----------------------------------------------------------------------------*/

package com.ericsson.oss.services.nodesyncmonitor.constants;

public final class Constants {

    private Constants() {}

    // MO/PO attributes
    public static final String ACTIVE = "active";
    public static final String ALARM_STATE = "alarmState";
    public static final String EVENT_TYPE = "eventType";
    public static final String FAILED_SYNCS_COUNT = "failedSyncsCount";
    public static final String FDN = "fdn";
    public static final String OSS_PREFIX = "ossPrefix";
    public static final String PRESENT_SEVERITY = "presentSeverity";
    public static final String PROBABLE_CAUSE = "probableCause";
    public static final String RECORD_TYPE = "recordType";
    public static final String SPECIFIC_PROBLEM = "specificProblem";

    // MO/PO attributes values
    public static final String ACTIVE_ALARM_STATE = "ACTIVE";
    public static final String BEHALF = "behalf";
    public static final String CM_UNSYNCHRONIZED = "CM unsynchronized";
    public static final String PROCESSING_ERROR = "Processing Error";
    public static final String CONFIGURATION_CUSTOMIZING_ERROR = "Configuration or Customizing Error";
    public static final String MANAGEMENT_SYSTEM_ENM = "ManagementSystem=ENM";
    public static final String NON_SYNCHABLE_ALARM = "NON_SYNCHABLE_ALARM";

    // MO/PO types
    public static final String CM_FUNCTION = "CmFunction";
    public static final String CM_NODE_HEARTBEAT_SUPERVISION = "CmNodeHeartbeatSupervision";
    public static final String MANAGED_ELEMENT = "ManagedElement";
    public static final String ME_CONTEXT = "MeContext";
    public static final String NETWORK_ELEMENT = "NetworkElement";
    public static final String OPEN_ALARM = "OpenAlarm";

    // Namespaces
    public static final String FM = "FM";

    // System Recorder Constants
    public static final String ALARM_CLEARED = "Alarm cleared";
    public static final String ALARM_RAISED = "Alarm raised";
    public static final String LIVE_VIEW = "Live View";
    public static final String NODE_SYNC_MONITOR = "NodeSyncMonitor";

    // RDNs
    public static final String CM_FUNCTION_RDN = CM_FUNCTION + "=1";
    public static final String CM_NODE_HEARTBEAT_SUPERVISION_RDN = CM_NODE_HEARTBEAT_SUPERVISION + "=1";

    // Alarm Request Constants
    public static final String CLEARED = "CLEARED";
    public static final String MINOR = "MINOR";

    // Feature Constants
    public static final String OFF = "off";
    public static final String ON = "on";

    // PIB constants
    public static final String CM_NODE_SYNC_MONITOR_FEATURE = "cmNodeSyncMonitorFeature";
    public static final String NO_OF_CM_SYNC_FAILURES_BEFORE_ALARM = "noOfCmSyncFailuresBeforeAlarm";

    // Exception Messages Constants
    public static final String MO_DOES_NOT_EXIST = "ManagedObject with FDN [%s] does not exist.";

    // Cluster constants
    public static final String NODE_SYNC_MONITOR_CLUSTER = "NodeSyncMonitorCluster";

    // General messages
    public static final String DPS_CHANGE_EVENT_MESSAGE = "Received DPS change event for [{}] attribute [{}] old value [{}] new value [{}]";
    public static final String PIB_CHANGE_EVENT_MESSAGE = "Received PIB change event for property [{}] old value [{}] new value [{}]";

}

package com.ericsson.oss.services.nodesyncmonitor.listener

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.stub.StubbedDataPersistenceService
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder
import com.ericsson.oss.services.nodesyncmonitor.stub.AlarmStub
import com.ericsson.oss.services.nodesyncmonitor.junit.rule.LocalHostRule
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import org.junit.Rule

import static com.ericsson.oss.itpf.sdk.recording.EventLevel.COARSE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ACTIVE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_FUNCTION_RDN
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_NODE_HEARTBEAT_SUPERVISION_RDN
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FAILED_SYNCS_COUNT
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.LIVE_VIEW
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NODE_SYNC_MONITOR
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.OFF
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ON

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean

import spock.lang.Subject
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class CmNodeSyncMonitorFeatureListenerSpec extends CdiSpecification {

    private static final String RNC01 = "RNC01";
    private static final String NETWORK_ELEMENT_FDN_LTE01 = "NetworkElement=LTE01"
    private static final String NETWORK_ELEMENT_FDN_M01B01 = "NetworkElement=M01B01"
    private static final String NETWORK_ELEMENT_FDN_RNC01 = "NetworkElement=RNC01"

    private static final String CM_NODE_HEARTBEAT_SUPERVISION_FDN_LTE01 = NETWORK_ELEMENT_FDN_LTE01 + "," + CM_NODE_HEARTBEAT_SUPERVISION_RDN
    private static final String CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01 = NETWORK_ELEMENT_FDN_M01B01 + "," + CM_NODE_HEARTBEAT_SUPERVISION_RDN
    private static final String CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01 = NETWORK_ELEMENT_FDN_RNC01 + "," + CM_NODE_HEARTBEAT_SUPERVISION_RDN

    private static final String CM_FUNCTION_FDN_LTE01 = NETWORK_ELEMENT_FDN_LTE01 + "," + CM_FUNCTION_RDN
    private static final String CM_FUNCTION_FDN_M01B01 = NETWORK_ELEMENT_FDN_M01B01 + "," + CM_FUNCTION_RDN
    private static final String CM_FUNCTION_FDN_RNC01 = NETWORK_ELEMENT_FDN_RNC01 + "," + CM_FUNCTION_RDN

    private static final String EVENT_LISTENER = NODE_SYNC_MONITOR + ".CmNodeSyncMonitorFeatureListener"

    private RuntimeConfigurableDps runtimeDps

    @Rule
    WireMockClassRule wireMockServer = new WireMockClassRule(options().port(8080).bindAddress("localhost"))

    @Rule
    LocalHostRule hostRule = new LocalHostRule()

    @ImplementationClasses
    private static final Class[] definedImplementations = [DpsReadOnlyQueries.class]

    @Subject
    @ObjectUnderTest
    private CmNodeSyncMonitorFeatureListener featureListener

    @Inject
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarm;

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;

    @Inject
    private SystemRecorder systemRecorder

    @Inject
    private AlarmStub alarmStub

    @Inject
    private DataPersistenceService dataPersistenceService

    def setup() {
        runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)
        noOfCmSyncFailuresBeforeAlarm.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(8)  // Set default threshold value before each test
    }

    def "Test alarm is not raised for network element with supervision not active when feature is turned ON"() {
        given: "supervision is not active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, false)

        and: "failedSyncsCount exceeds the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 9)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "Alarm event not recorded"
        0 * systemRecorder._
    }

    def "Test alarm is raised for network element with failedSyncsCount exceeding threshold when feature is turned ON"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, true)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN_M01B01)

        and: "failedSyncsCount exceeds the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 9)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "raise alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_M01B01, ALARM_RAISED)

        and : "check if DPS is called with write access set to false"
        false == ((StubbedDataPersistenceService) dataPersistenceService).getOrSetAllowWriteAccess()

    }

    def "Test alarm is raised for network element with failedSyncsCount equal to threshold when feature is turned ON"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, true)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN_M01B01)

        and: "failedSyncsCount is equal to the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 8)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "raise alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_M01B01, ALARM_RAISED)
    }

    def "Test alarm is not raised for network element with failedSyncsCount less than threshold when feature is turned ON"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, true)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN_M01B01)

        and: "failedSyncsCount is less than the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 7)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "no alarm events are recorded"
        0 * systemRecorder._
    }

    def "Test multiple nodes with different failedSyncsCount values when feature is turned ON"() {
        given: "supervision is active on three MOs"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, true)
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01, ACTIVE, true)
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_LTE01, ACTIVE, true)

        and: "failedSyncsCount is equal to or exceeds the threshold on two MOs"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 9)  // exceeds threshold
        addMoWithAttributeValue(CM_FUNCTION_FDN_RNC01, FAILED_SYNCS_COUNT, 8)  // equals threshold
        addMoWithAttributeValue(CM_FUNCTION_FDN_LTE01, FAILED_SYNCS_COUNT, 7)  // below threshold

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN_M01B01)
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN_RNC01)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are updated to indicate two raised alarms"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 2
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "two raise alarm events are recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_M01B01, ALARM_RAISED)
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_RNC01, ALARM_RAISED)
    }

    def "Test alarm is not cleared for network element with supervision not active when feature is turned OFF"() {
        given: "supervision is not active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_M01B01, ACTIVE, false)

        and: "failedSyncsCount exceeds the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_M01B01, FAILED_SYNCS_COUNT, 9)

        when: "feature changes from ON to OFF"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(OFF)
        assert featureListener.getCmNodeSyncMonitorFeature() == OFF

        then: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "no alarm events are recorded"
        0 * systemRecorder._
    }
    //"Edge case that might occur if an alarm failed to clear when the feature was disabled"
    def "Test invalid alarm for synchronized node is cleared when feature is turned ON"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01, ACTIVE, true)

        and: "node is synchronized with failedSyncsCount of 0"
        addMoWithAttributeValue(CM_FUNCTION_FDN_RNC01, FAILED_SYNCS_COUNT, 0)  // below threshold

        and: "Invalid unacknowledged active alarm exists"
        addOpenAlarmPo(NETWORK_ELEMENT_FDN_RNC01, "CM unsynchronized", "ACTIVE_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN_RNC01)

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are updated to indicate one clear alarms"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0

        and: "one clear alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_RNC01, ALARM_CLEARED)
    }

    //"Edge case that might occur if an alarm failed to clear when the feature was disabled"
    def "Test no raise alarm request when valid alarm already exists when feature is turned ON"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01, ACTIVE, true)

        and: "failedSyncsCount exceeds the threshold"
        addMoWithAttributeValue(CM_FUNCTION_FDN_RNC01, FAILED_SYNCS_COUNT, 9)  // above threshold

        and: "Valid unacknowledged active alarm exists"
        addOpenAlarmPo(NETWORK_ELEMENT_FDN_RNC01, "CM unsynchronized", "ACTIVE_UNACKNOWLEDGED")

        when: "feature changes from OFF to ON"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(ON)
        assert featureListener.getCmNodeSyncMonitorFeature() == ON

        then: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0

        and: "no event is recorded"
        0 * systemRecorder._
    }

    def "Test valid alarm is cleared when feature is turned OFF"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01, ACTIVE, true)

        and: "node with failedSyncsCount of 9"
        addMoWithAttributeValue(CM_FUNCTION_FDN_RNC01, FAILED_SYNCS_COUNT, 9)  // above threshold

        and: "Valid unacknowledged active alarm exists"
        addOpenAlarmPo(NETWORK_ELEMENT_FDN_RNC01, "CM unsynchronized", "ACTIVE_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN_RNC01)

        when: "feature changes from ON to OFF"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(OFF)
        assert featureListener.getCmNodeSyncMonitorFeature() == OFF

        then: "MBean metrics are updated to indicate one clear alarms"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0

        and: "one clear alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_RNC01, ALARM_CLEARED)
    }
    //"Edge case that might occur if an alarm failed to clear when the node synched."
    def "Test invalid alarm for synchronized node is cleared when feature is turned OFF"() {
        given: "supervision is active"
        addMoWithAttributeValue(CM_NODE_HEARTBEAT_SUPERVISION_FDN_RNC01, ACTIVE, true)

        and: "node is synchronized with failedSyncsCount of 0"
        addMoWithAttributeValue(CM_FUNCTION_FDN_RNC01, FAILED_SYNCS_COUNT, 0)  // below threshold

        and: "Invalid unacknowledged active alarm exists"
        addOpenAlarmPo(NETWORK_ELEMENT_FDN_RNC01, "CM unsynchronized", "ACTIVE_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN_RNC01)

        when: "feature changes from ON to OFF"
        featureListener.listenForChangeInCmNodeSyncMonitorFeature(OFF)
        assert featureListener.getCmNodeSyncMonitorFeature() == OFF

        then: "MBean metrics are updated to indicate one clear alarms"
        nodeSyncMonitorMBean.getTotalCmNodeSyncMonitorFeatureEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0

        and: "one clear alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN_RNC01, ALARM_CLEARED)
    }

    private void addMoWithAttributeValue(String fdn, String attributeName, Object attributeValue) {
        runtimeDps.addManagedObject().withFdn(fdn)
                .addAttribute(attributeName, attributeValue)
                .generateTree()
                .build()
    }
    private addOpenAlarmPo(String name, String specificProblem, String alarmState) {
        Map<String, Object> openAlarmAttrs=  new HashMap();
        openAlarmAttrs.put("specificProblem", specificProblem)
        openAlarmAttrs.put("presentSeverity", "MINOR")
        openAlarmAttrs.put("alarmState", alarmState)
        openAlarmAttrs.put("probableCause", "Configuration or Customizing Error")
        openAlarmAttrs.put("eventType", "Processing Error")
        openAlarmAttrs.put("fdn", name)
        runtimeDps.addPersistenceObject().type("OpenAlarm").namespace("FM").addAttributes(openAlarmAttrs).build()
    }
}

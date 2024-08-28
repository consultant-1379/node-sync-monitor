package com.ericsson.oss.services.nodesyncmonitor.listener

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.stub.StubbedDataPersistenceService
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder
import com.ericsson.oss.services.nodesyncmonitor.junit.rule.LocalHostRule
import com.ericsson.oss.services.nodesyncmonitor.stub.AlarmStub
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import org.junit.Rule

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean

import spock.lang.Subject

import static com.ericsson.oss.itpf.sdk.recording.EventLevel.COARSE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.LIVE_VIEW
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NODE_SYNC_MONITOR
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class NoOfCmSyncFailuresBeforeAlarmListenerSpec extends CdiSpecification {

    private static final String RNC01 = "RNC01";
    private static final String RNC02 = "RNC02";
    private static final String RNC03 = "RNC03";
    private static final String RNC04 = "RNC04";
    private static final String NE_FDN_RNC01 = "NetworkElement=" + RNC01;
    private static final String NE_FDN_RNC02 = "NetworkElement=" + RNC02;
    private static final String NE_FDN_RNC03 = "NetworkElement=" + RNC03;
    private static final String NE_FDN_RNC04 = "NetworkElement=" + RNC04;
    private static final String CM_FUNCTION_RDN = "CmFunction=1";
    private static final String CM_NODE_HEARTBEAT_SUPERVISION_RDN = "CmNodeHeartbeatSupervision=1";
    private static final String EVENT_LISTENER = NODE_SYNC_MONITOR + ".NoOfCmSyncFailuresBeforeAlarmListener"

    @Rule
    WireMockClassRule wireMockServer = new WireMockClassRule(options().port(8080).bindAddress("localhost"))

    @Rule
    LocalHostRule hostRule = new LocalHostRule()

    @ImplementationClasses
    private static final Class[] definedImplementations = [
            DpsReadOnlyQueries.class
    ]

    @Subject
    @ObjectUnderTest
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarmListener

    @Inject
    private CmNodeSyncMonitorFeatureListener cmNodeSyncMonitorFeatureListener;

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;

    @Inject
    private SystemRecorder systemRecorder

    @Inject
    private AlarmStub alarmStub

    @Inject
    private DataPersistenceService dataPersistenceService

    private RuntimeConfigurableDps runtimeDps

    def setup() {
        runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)
        cmNodeSyncMonitorFeatureListener.cmNodeSyncMonitorFeature = "on"
    }

    def "Test alarm is cleared for relevant network elements when threshold is increased"() {

        given: "Supervision is active for NE with failedSyncsCount equal to original threshold"
        setupMoTree(RNC01, true, 10)

        and: "Supervision is active for NE with failedSyncsCount above original threshold but below new threshold"
        setupMoTree(RNC02, true, 11)

        and: "Supervision is active for NE with failedSyncsCount equal to new threshold"
        setupMoTree(RNC03, true, 12)

        and: "Supervision is active for NE with failedSyncsCount below original threshold"
        setupMoTree(RNC04, true, 9)

        and: "Unacknowledged active alarms exist for 3 NEs that equal or exceed original threshold"
        setupOpenUnacknowledgedAlarmPos(RNC01, RNC02, RNC03)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NE_FDN_RNC01)
        alarmStub.createClearAlarm(NE_FDN_RNC02)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then:  "The alarm is cleared for NEs that are below the new threshold"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_CLEARED)
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC02, ALARM_CLEARED)

        and: "The alarm is not cleared for the NE that equals the new threshold"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC03, ALARM_CLEARED)

        and: "No clear alarm is attempted for the NE below original threshold, where the alarm doesn't exist"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_CLEARED)

        and: "MBean metrics are updated to indicate two cleared alarms"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 2
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0

        and : "check if DPS is called with write access set to false"
        false == ((StubbedDataPersistenceService) dataPersistenceService).getOrSetAllowWriteAccess()
    }

    def "Test existing alarm is cleared when threshold is increased and the alarm is not required anymore"() {

        given: "Supervision is active and the number of failed syncs below the new threshold"
        setupMoTree(RNC01, true, 10)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NE_FDN_RNC01)

        and: "Acknowledged active alarms exists"
        addOpenAlarmPo(RNC01, "CM unsynchronized", "ACTIVE_ACKNOWLEDGED")

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then:  "The alarm is cleared, because it is not required anymore"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_CLEARED)

        and: "MBean metrics are updated to indicate a cleared alarm"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
    }

    def "Test existing alarm is not cleared when threshold is increased and the alarm is already cleared, but not acknowledged"() {

        given: "Supervision is active and the number of failed syncs below the new threshold"
        setupMoTree(RNC01, true, 10)

        and: "Unacknowledged cleared alarm exists."
        addOpenAlarmPo(RNC01, "CM unsynchronized", "CLEARED_UNACKNOWLEDGED")

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then:  "The alarm is not cleared, because it was already cleared"
        0 * systemRecorder._

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test existing alarm is not cleared when threshold is increased and the alarm is still required"() {

        given: "Supervision is active and the number of failed syncs equal to the new threshold"
        setupMoTree(RNC01, true, 12)

        and: "Acknowledged active alarms exists"
        addOpenAlarmPo(RNC01, "CM unsynchronized", "ACTIVE_ACKNOWLEDGED")

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then:  "The alarm is not cleared, because it is still valid"
        0 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_CLEARED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not cleared when threshold is increased and the alarm was never required"() {

        given: "Supervision is active and the number of failed syncs below the old or new threshold"
        setupMoTree(RNC01, true, 9)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then:  "The alarm is not cleared, where the alarm doesn't exist and it's not required"
        0 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_CLEARED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is raised for relevant network elements when threshold is decreased"() {

        given: "Supervision is active for NE with failedSyncsCount equal to new threshold"
        setupMoTree(RNC01, true, 10)

        and: "Supervision is active for NE with failedSyncsCount below original threshold but above new threshold"
        setupMoTree(RNC02, true, 11)

        and: "Supervision is active for NE with failedSyncsCount equal to old threshold"
        setupMoTree(RNC03, true, 12)

        and: "Supervision is active for NE with failedSyncsCount below new threshold"
        setupMoTree(RNC04, true, 9)

        and: "Unacknowledged active alarm exists for one NE"
        setupOpenUnacknowledgedAlarmPos(RNC03)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC01)
        alarmStub.createRaiseAlarm(NE_FDN_RNC02)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised for two NEs that are equal to or above the new threshold"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_RAISED)
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC02, ALARM_RAISED)

        and: "The alarm is not raised for one NE, because it already exists"
        0 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC03, ALARM_RAISED)

        and: "No raise alarm is attempted for the NE that still below the new threshold"
        0 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC04, ALARM_RAISED)

        and: "MBean metrics are updated to indicate two raised alarms"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 2
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is raised when threshold is decreased and it is equal to the number of failed syncs, so the alarm is now required"() {

        given: "Supervision is active and the number of failed syncs is equal to the threshold"
        setupMoTree(RNC01, true, 10)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC01)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised because it doesn't exist and it is now required"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_RAISED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is raised when threshold is decreased and it is equal to the number of failed syncs, for an unacknowledged cleared alarm"() {

        given: "Supervision is active and the number of failed syncs is equal to the threshold"
        setupMoTree(RNC01, true, 10)

        and: "Unacknowledged cleared alarm exists"
        addOpenAlarmPo(RNC01, "CM unsynchronized", "CLEARED_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC01)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised because it doesn't exist and it is now required"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_RAISED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is raised when threshold is decreased and it is above the faled syncs, so the alarm is now required"() {

        given: "Supervision is active and the number of failed syncs is above the threshold"
        setupMoTree(RNC01, true, 11)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC01)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised because it doesn't exist and it is now required"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_RAISED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is raised when threshold is decreased and it is above the faled syncs, for an unacknowledged cleared alarm"() {

        given: "Supervision is active and the number of failed syncs is above the threshold"
        setupMoTree(RNC01, true, 11)

        and: "Unacknowledged cleared alarm exists"
        addOpenAlarmPo(RNC01, "CM unsynchronized", "CLEARED_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC01)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised because it doesn't exist and it is now required"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC01, ALARM_RAISED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not raised when threshold is decreased and the active acknowledged alarm exists"() {

        given: "Supervision is active and the number of failed syncs above the new and old threshold"
        setupMoTree(RNC03, true, 10)

        and: "Acknowledged alarm exists"
        addOpenAlarmPo(RNC03, "CM unsynchronized", "ACTIVE_ACKNOWLEDGED")

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is not raised, because the required alarm exists"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC03, ALARM_RAISED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not raised when threshold is decreased and the alarm was never required"() {

        given: "Supervision is active and the number of failed syncs below the threshold"
        setupMoTree(RNC03, true, 9)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is not raised, where the alarm doesn't exist and it was never required"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC03, ALARM_RAISED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Test alarm is not cleared when the threshold is increased and the supervision is not active"() {

        given: "Supervision is not active and the number of failed syncs is lower than the threshold"
        setupMoTree(RNC04, false, 10)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then: "The alarm is not cleared"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_CLEARED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not raised when threshold is decreased and the supervision is not active"() {

        given: "Supervision is not active and the number of failed syncs is equal to the threshold"
        setupMoTree(RNC04, false, 10)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is not raised"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_RAISED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not cleared when the threshold is increased and the feature is off"() {

        given: "The feature is off"
        cmNodeSyncMonitorFeatureListener.cmNodeSyncMonitorFeature = "off"

        and: "Supervision is active and the number of failed syncs is lower than the threshold"
        setupMoTree(RNC04, true, 10)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then: "The alarm is not cleared"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_CLEARED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not raised when threshold is decreased and the feature is off"() {

        given: "The feature is off"
        cmNodeSyncMonitorFeatureListener.cmNodeSyncMonitorFeature = "off"

        and: "Supervision is active and the number of failed syncs is equal to the threshold"
        setupMoTree(RNC04, true, 10)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is not raised"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_RAISED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    def "Test alarm is not cleared when the threshold is increased and an alarm not related to NodeSyncFeature exists"() {

        given: "Supervision is active and the number of failed syncs is lower than the threshold"
        setupMoTree(RNC04, true, 9)

        and: "A NodeSyncFeature unrelated alarm exists with specificProblem Heartbeat Failure"
        addOpenAlarmPo(RNC04, "Heartbeat Failure", "ACTIVE_UNACKNOWLEDGED")

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 10 to 12"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(12);

        then: "The alarm is not cleared"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN_RNC04, ALARM_CLEARED)

        and: "MBean metrics are not updated"
        nodeSyncMonitorMBean.getTotalNoOfCmSyncFailuresBeforeAlarmEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Test alarm is raised when threshold is decreased and an alarm not related to NodeSyncFeature exists"() {

        given: "Supervision is active and the number of failed syncs is equal to the threshold"
        setupMoTree(RNC04, true, 10)

        and: "A NodeSyncFeature unrelated alarm exists with specificProblem Heartbeat Failure"
        addOpenAlarmPo(RNC04, "Heartbeat Failure", "ACTIVE_UNACKNOWLEDGED")

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NE_FDN_RNC04)

        when: "PIB property nofOfCmSyncFailuresBeforeAlarm is updated from 12 to 10"
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(10);

        then: "The alarm is raised"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NE_FDN_RNC04, ALARM_RAISED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

    }

    private void setupOpenUnacknowledgedAlarmPos(String ... neNames) {
        for(String name: neNames) {
            addOpenAlarmPo(name, "CM unsynchronized", "ACTIVE_UNACKNOWLEDGED")
        }
    }

    private addOpenAlarmPo(String name, String specificProblem, String alarmState) {
        Map<String, Object> openAlarmAttrs=  new HashMap();
        openAlarmAttrs.put("specificProblem", specificProblem)
        openAlarmAttrs.put("presentSeverity", "MINOR")
        openAlarmAttrs.put("alarmState", alarmState)
        openAlarmAttrs.put("probableCause", "Configuration or Customizing Error")
        openAlarmAttrs.put("eventType", "Processing Error")
        openAlarmAttrs.put("fdn", "NetworkElement=" + name)
        runtimeDps.addPersistenceObject().type("OpenAlarm").namespace("FM").addAttributes(openAlarmAttrs).build()
    }

    private void setupMoTree(String neName, boolean isActive,  int failedSyncsCount) {
        addMoWithAttributeValue("NetworkElement=" + neName + "," + CM_NODE_HEARTBEAT_SUPERVISION_RDN,  "active", isActive)
        addMoWithAttributeValue("NetworkElement=" + neName + "," + CM_FUNCTION_RDN,  "failedSyncsCount", failedSyncsCount)
    }

    private ManagedObject addMoWithAttributeValue(String fdn, String attributeName, Object attributeValue) {
        return runtimeDps.addManagedObject().withFdn(fdn).addAttribute(attributeName, attributeValue).generateTree().build()
    }
}

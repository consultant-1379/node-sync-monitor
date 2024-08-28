package com.ericsson.oss.services.nodesyncmonitor.listener

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.stub.StubbedDataPersistenceService
import com.ericsson.oss.services.nodesyncmonitor.alarm.AlarmRequestBuilder
import com.ericsson.oss.services.nodesyncmonitor.dps.Dps
import com.ericsson.oss.services.nodesyncmonitor.junit.rule.LocalHostRule
import com.ericsson.oss.services.nodesyncmonitor.stub.AlarmStub
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import org.junit.Rule

import static com.ericsson.oss.itpf.sdk.recording.EventLevel.COARSE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ACTIVE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FAILED_SYNCS_COUNT
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.LIVE_VIEW
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NODE_SYNC_MONITOR

import javax.inject.Inject
import javax.interceptor.InvocationContext

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder
import com.ericsson.oss.services.nodesyncmonitor.alarm.AlarmSender
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean
import spock.lang.Subject
import wiremock.com.google.common.collect.ImmutableSet

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class CmSupervisionListenerSpec extends CdiSpecification {

    private static final String NETWORK_ELEMENT_FDN = "NetworkElement=M01B01"
    private static final String CMFUNCTION_FDN = "NetworkElement=M01B01,CmFunction=1"
    private static final String EVENT_LISTENER = NODE_SYNC_MONITOR + ".CmSupervisionListener"
    private static InvocationContext invocationContext
    private RuntimeConfigurableDps runtimeDps

    @Rule
    WireMockClassRule wireMockServer = new WireMockClassRule(options().port(8080).bindAddress("localhost"))

    @Rule
    LocalHostRule hostRule = new LocalHostRule()

    @ImplementationClasses
    private static final Class[] definedImplementations = [DpsReadOnlyQueries.class]

    @Subject
    @ObjectUnderTest
    private CmSupervisionListener cmSupervisionListener

    @Inject
    private AlarmRequestBuilder alarmRequestBuilder

    @Inject
    private AlarmSender alarmSender

    @Inject
    private SystemRecorder systemRecorder

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean

    @Inject
    private NoOfCmSyncFailuresBeforeAlarmListener noOfCmSyncFailuresBeforeAlarmListener

    @Inject
    private AlarmStub alarmStub

    @Inject
    private DataPersistenceService dataPersistenceService

    def setup() {
        runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(8)
    }

    def "Test when failedSyncsCount exceeds the threshold, and supervision is activated then alarm is raised"() {

        given: "failedSyncsCount exceeds the threshold"
        final ManagedObject networkElementMo = runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN)
                .addAttribute(FAILED_SYNCS_COUNT, 9).generateTree().build()

        and: "supervision is activated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(false, true)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN)

        when: " event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "raise alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_RAISED)

        and : "check if DPS is called with write access set to false"
        false == ((StubbedDataPersistenceService) dataPersistenceService).getOrSetAllowWriteAccess()
    }

    def "Test when failedSyncsCount equals threshold, and supervision is activated then alarm is raised"() {

        given: "failedSyncsCount equals threshold"
        final ManagedObject networkElementMo = runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN)
                .addAttribute(FAILED_SYNCS_COUNT, 8).generateTree().build()

        and: "supervision is activated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(false, true)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN)

        when: " event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "raise alarm event is recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_RAISED)
    }

    def "Test when failedSyncsCount does not exceed threshold, and supervision is activated then no alarm is raised"() {

        given:"failedSyncsCount does not exceeded threshold"
        final ManagedObject networkElementMo = runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN)
                .addAttribute(FAILED_SYNCS_COUNT, 7).generateTree().build()

        and: "supervision is activated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(false, true)

        when: " event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "MBean metrics are unchanged"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and: "Alarm event not recorded"
        0 * systemRecorder._
    }

    def "Test when failedSyncsCount exceeds the threshold, and supervision is deactivated then alarm is cleared"() {

        given: "failedSyncsCount exceeds the threshold"
        final ManagedObject networkElementMo = runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN)
                .addAttribute(FAILED_SYNCS_COUNT, 9).generateTree().build()

        and: "supervision is deactivated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(true, false)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN)

        when: " event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "MBean metrics are updated to indicate a cleared alarm"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1

        and: "clear alarm event recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_CLEARED)
    }

    def "Test when failedSyncsCount equals threshold, and supervision is deactivated then alarm is cleared"() {

        given: "failedSyncsCount equals the threshold"
        final ManagedObject networkElementMo = runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN)
                .addAttribute(FAILED_SYNCS_COUNT, 8).generateTree().build()

        and: "supervision is deactivated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(true, false)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN)

        when: " event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "MBean metrics are updated to indicate a cleared alarm"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1

        then: "Clear alarm event recorded"
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_CLEARED)
    }

    def "Test when failedSyncsCount is less than threshold, and supervision is deactivated then no alarm"() {

        given: "failedSyncsCount is less than the threshold"
        runtimeDps.addManagedObject().withFdn(CMFUNCTION_FDN).addAttribute(FAILED_SYNCS_COUNT, 7).generateTree().build()

        and: "supervision is deactivated"
        final DpsAttributeChangedEvent event = createCmNodeHeartbeatSupervisionChangeEvent(true, false)

        when: "event is received"
        cmSupervisionListener.processCmSupervisionChangeEvent(event)

        then: "Alarm event not recorded and no alarm is sent"
        0 * systemRecorder._

        and: "MBean metrics are unchanged"
        nodeSyncMonitorMBean.getTotalCmNodeHeartbeatSupervisionEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    private DpsAttributeChangedEvent createCmNodeHeartbeatSupervisionChangeEvent(final boolean oldValue, final boolean newValue) {
        final DpsAttributeChangedEvent event = new DpsAttributeChangedEvent()
        final AttributeChangeData active = new AttributeChangeData(ACTIVE, oldValue, newValue, null, null)
        final AttributeChangeData numberOfRetries = new AttributeChangeData("numberOfRetries", 3, 1, null, null)
        event.setChangedAttributes(ImmutableSet.of(active, numberOfRetries))
        event.setFdn("NetworkElement=M01B01,CmNodeHeartbeatSupervision=1")
        return event
    }
}

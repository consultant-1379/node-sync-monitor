package com.ericsson.oss.services.nodesyncmonitor.listener

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.stub.StubbedDataPersistenceService
import com.ericsson.oss.services.nodesyncmonitor.junit.rule.LocalHostRule
import com.ericsson.oss.services.nodesyncmonitor.stub.AlarmStub
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import org.junit.Rule

import static com.ericsson.oss.itpf.sdk.recording.EventLevel.COARSE
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED
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
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean

import spock.lang.Subject

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class FailedSyncsCountListenerSpec extends CdiSpecification {

    private static final String NETWORK_ELEMENT_FDN = "NetworkElement=RNC01"
    private static final String EVENT_LISTENER = NODE_SYNC_MONITOR + ".FailedSyncsCountListener"
    private static ManagedObject enabledCmSupervisionMo
    private static ManagedObject disabledCmSupervisionMo
    private static ManagedObject missingActiveAttrCmSupervisionMo
    private static InvocationContext invocationContext

    @Rule
    WireMockClassRule wireMockServer = new WireMockClassRule(options().port(8080).bindAddress("localhost"))

    @Rule
    LocalHostRule hostRule = new LocalHostRule()

    @ImplementationClasses
    private static final Class[] definedImplementations = [DpsReadOnlyQueries.class]

    @Subject
    @ObjectUnderTest
    private FailedSyncsCountListener failedSyncsCountListener

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

    def setupSpec() {
        invocationContext = Mock { proceed() >> true }
    }

    def setup() {
        RuntimeConfigurableDps runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)
        enabledCmSupervisionMo = runtimeDps.addManagedObject().withFdn("NetworkElement=RNC01,CmNodeHeartbeatSupervision=1").addAttribute("active", true).generateTree().build()
        disabledCmSupervisionMo = runtimeDps.addManagedObject().withFdn("NetworkElement=RNC02,CmNodeHeartbeatSupervision=1").addAttribute("active", false).generateTree().build()
        missingActiveAttrCmSupervisionMo = runtimeDps.addManagedObject().withFdn("NetworkElement=RNC04,CmNodeHeartbeatSupervision=1").generateTree().build()
        noOfCmSyncFailuresBeforeAlarmListener.listenForChangeInNoOfCmSyncFailuresBeforeAlarm(8)
    }

    def "Alarm is raised when failedSyncsCount equal to threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount with old value of 7 and new value 8"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 7, 8)

        and: "alarm service is online"
        alarmStub.createRaiseAlarm(NETWORK_ELEMENT_FDN)

        when: "event is received"
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Raise alarm event recorded'
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_RAISED)
        0 * systemRecorder.recordEvent(_, _, _, NETWORK_ELEMENT_FDN, ALARM_CLEARED)

        and: "MBean metrics are updated to indicate a raised alarm"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0

        and : "check if DPS is called with write access set to false"
        false == ((StubbedDataPersistenceService) dataPersistenceService).getOrSetAllowWriteAccess()
    }

    def "Alarm is not raised when failedSyncsCount higher than threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount with old value of 8 and new value 9"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 8, 9)

        when: 'FailedSyncsCount new value exceeds threshold'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Alarm event not recorded'
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Alarm is not raised when failedSyncsCount lower than threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount with old value of 6 and new value of 7"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 6, 7)

        when: 'FailedSyncsCount new value below the threshold'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Alarm event not recorded'
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Alarm is cleared when the node is synced and old failedSyncsCount value equals the threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount with old value of 8 and new value 0"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 8, 0)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN)

        when: 'event processed and failedSyncsCount old value equal to nofOfCmSyncFailuresBeforeAlarm of 8'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Clear Alarm recorded'
        0 * systemRecorder.recordEvent(_, _, _, NETWORK_ELEMENT_FDN, ALARM_RAISED)
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_CLEARED)

        and: "MBean metrics are updated to indicate a cleared alarm"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
    }

    def "Alarm is cleared when the node is synced and old failedSyncsCount value is above the threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 10, 0)

        and: "alarm service is online"
        alarmStub.createClearAlarm(NETWORK_ELEMENT_FDN)

        when: 'event processed and failedSyncsCount with new value 0 and old value higher than nofOfCmSyncFailuresBeforeAlarm'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Clear Alarm recorded'
        0 * systemRecorder.recordEvent(_, _, _, NETWORK_ELEMENT_FDN, ALARM_RAISED)
        1 * systemRecorder.recordEvent(EVENT_LISTENER, COARSE, LIVE_VIEW, NETWORK_ELEMENT_FDN, ALARM_CLEARED)

        and: "MBean metrics are updated to indicate a cleared alarm"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 1
    }

    def "Alarm is not cleared when node is synced and old failedSyncsCount is lower than threshold"() {
        given: "DPS notification event for CmFunction::failedSyncsCount"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent(NETWORK_ELEMENT_FDN, 4, 0)

        when: 'event processed and failedSyncsCount with new value 0 and old value higher than nofOfCmSyncFailuresBeforeAlarm'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Alarm event not recorded or sent'
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Alarm is not raised when supervision disabled and the failedSyncsCount equal to nofOfCmSyncFailuresBeforeAlarm"() {
        given: "DPS notification event for CmFunction::failedSyncsCount"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent("NetworkElement=RNC02", 7, 8)

        when: 'event processed and failedSyncsCount equal to nofOfCmSyncFailuresBeforeAlarm with value 8'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Alarm event not recorded or sent'
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Alarm is not cleared when supervision disabled and the failedSyncsCount is 0"() {
        given: "DPS notification event for CmFunction::failedSyncsCount"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent("NetworkElement=RNC02", 8, 0)

        when: 'event processed and failedSyncsCount equal to nofOfCmSyncFailuresBeforeAlarm with value 8'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Alarm event not recorded or sent'
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }

    def "Test exception thrown when supervision MO doesn't exist"() {
        given: "DPS notification event for CmFunction::failedSyncsCount"
        final DpsAttributeChangedEvent event = setupFailedSyncsCountDpsEvent("NetworkElement=RNC03", 8, 0)

        when: 'event processed'
        failedSyncsCountListener.processFailedSyncsCountEvent(event)

        then: 'Exception is thrown'
        def exception = thrown(IllegalStateException)
        exception.message == "ManagedObject with FDN [NetworkElement=RNC03,CmNodeHeartbeatSupervision=1] does not exist."

        and: "Alarm event not recorded or sent"
        0 * systemRecorder._

        and: "MBean metrics are updated to no alarm raised or cleared"
        nodeSyncMonitorMBean.getTotalFailedSyncsCountEventsReceived() == 1
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsRaised() == 0
        nodeSyncMonitorMBean.getTotalCmUnsyncedAlarmsCleared() == 0
    }
    private DpsAttributeChangedEvent setupFailedSyncsCountDpsEvent(final String neFdn, final int oldValue, final int newValue) {
        final DpsAttributeChangedEvent event = new DpsAttributeChangedEvent()
        final HashSet<AttributeChangeData> changedAttributes = getChangedAttributes(oldValue, newValue)

        event.setType("CmFunction")
        event.setFdn(neFdn + ",CmFunction=1")
        event.setChangedAttributes(changedAttributes)

        return event
    }

    private HashSet<AttributeChangeData> getChangedAttributes(final int oldValue, final int newValue) {
        final AttributeChangeData failedSyncsCountData = new AttributeChangeData("failedSyncsCount", oldValue, newValue, null, null)
        final AttributeChangeData lastFailedSyncData = new AttributeChangeData("lastFailedSync", oldValue, newValue, null, null)
        final HashSet<AttributeChangeData> changedAttributes = new HashSet<AttributeChangeData>()
        changedAttributes.add(failedSyncsCountData)
        changedAttributes.add(lastFailedSyncData)
        return changedAttributes
    }
}

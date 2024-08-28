package com.ericsson.oss.services.nodesyncmonitor.alarm

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean
import com.ericsson.oss.services.nodesyncmonitor.junit.rule.LocalHostRule
import com.ericsson.oss.services.nodesyncmonitor.stub.AlarmStub
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Subject

import javax.inject.Inject

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class AlarmSenderSpec extends CdiSpecification {

    private static final NE_FDN = "NetworkElement=1"

    @Rule
    WireMockClassRule wireMockServer = new WireMockClassRule(options().port(8080).bindAddress("localhost"))

    @Rule
    LocalHostRule hostRule = new LocalHostRule()

    @Subject
    @ObjectUnderTest
    private AlarmSender alarmSender

    @Inject
    private SystemRecorder systemRecorder

    @Inject
    private AlarmStub alarmStub

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean

    def "Test raising alarm when FM is offline then 503 error is handled"() {

        given: "FM is offline"
        alarmStub.createRaiseAlarmServiceUnavailableError(NE_FDN)

        when: "Alarm is raised"
        def result = alarmSender.raiseAlarm(NE_FDN, "myListener")

        then: "Status code 503 is returned"
        result == 503

        and: "no system event log recorded and metrics are not incremented"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN, ALARM_RAISED)
        0 * nodeSyncMonitorMBean.incrementCmUnsyncedAlarmRaised()
    }

    def "Test clearing alarm when FM is offline then 503 error is handled"() {

        given: "FM is offline"
        alarmStub.createClearAlarmServiceUnavailableError(NE_FDN)

        when: "Alarm is cleared"
        def result = alarmSender.clearAlarm(NE_FDN, "myListener")

        then: "Status code 503 is returned"
        result == 503

        and: "no system event log recorded and metrics are not incremented"
        0 * systemRecorder.recordEvent(_, _, _, NE_FDN, ALARM_CLEARED)
        0 * nodeSyncMonitorMBean.incrementCmUnsyncedAlarmCleared()
    }
}

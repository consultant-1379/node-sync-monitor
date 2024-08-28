package com.ericsson.oss.services.nodesyncmonitor.stub

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.BEHALF
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CLEARED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_UNSYNCHRONIZED
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CONFIGURATION_CUSTOMIZING_ERROR
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FDN
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MANAGED_ELEMENT
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MANAGEMENT_SYSTEM_ENM
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MINOR
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NON_SYNCHABLE_ALARM
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PROCESSING_ERROR
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE

import com.ericsson.oss.services.fm.internalalarm.api.InternalAlarmRequest
import com.ericsson.oss.services.nodesyncmonitor.util.FdnUtil
import com.github.tomakehurst.wiremock.client.WireMock
import com.google.common.collect.ImmutableMap

import wiremock.com.fasterxml.jackson.databind.ObjectMapper

/**
 * Note: this stub deliberately decouples the use of the AlarmRequestBuilder class to create the requests.
 */
class AlarmStub {

    private ObjectMapper objectMapper = new ObjectMapper()

    def createRaiseAlarm(final String networkElementFdn) {
        createRaiseAlarm(networkElementFdn, getManagedElementFdn(networkElementFdn), 200, "")
    }

    def createRaiseAlarmServiceUnavailableError(final String networkElementFdn){
        createRaiseAlarm(networkElementFdn, getManagedElementFdn(networkElementFdn), SERVICE_UNAVAILABLE.getStatusCode(), SERVICE_UNAVAILABLE.getReasonPhrase())
    }

    def createRaiseAlarm(final String networkElementFdn, final String ossPrefix, final int statusCode, final String body) {
        InternalAlarmRequest raiseAlarmRequest = getInternalAlarmRequest(networkElementFdn, ossPrefix, MINOR)
        stubFor(WireMock.post(urlMatching("/internal-alarm-service/internalalarm/internalalarmservice/translate"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Host", equalTo("localhost"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(raiseAlarmRequest)))
                .willReturn(aResponse().withStatus(statusCode).withHeader("Content-Type", "application/json")
                .withBody(body)))
    }

    def createClearAlarm(final String networkElementFdn){
        createClearAlarm(networkElementFdn, getManagedElementFdn(networkElementFdn), 200, "")
    }

    def createClearAlarmServiceUnavailableError(final String networkElementFdn){
        createClearAlarm(networkElementFdn, getManagedElementFdn(networkElementFdn), SERVICE_UNAVAILABLE.getStatusCode(), SERVICE_UNAVAILABLE.getReasonPhrase())
    }

    def createClearAlarm(final String networkElementFdn, final String ossPrefix, final int statusCode, final String body) {
        InternalAlarmRequest clearAlarmRequest = getInternalAlarmRequest(networkElementFdn, ossPrefix, CLEARED)
        stubFor(WireMock.post(urlMatching("/internal-alarm-service/internalalarm/internalalarmservice/translate"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Host", equalTo("localhost"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(clearAlarmRequest)))
                .willReturn(aResponse().withStatus(statusCode).withHeader("Content-Type", "application/json")
                .withBody(body)))
    }



    private InternalAlarmRequest getInternalAlarmRequest(final String networkElementFdn, final String ossPrefix, final String perceivedSeverity) {
        final InternalAlarmRequest internalAlarmRequest = new InternalAlarmRequest();
        internalAlarmRequest.setSpecificProblem(CM_UNSYNCHRONIZED);
        internalAlarmRequest.setProbableCause(CONFIGURATION_CUSTOMIZING_ERROR);
        internalAlarmRequest.setEventType(PROCESSING_ERROR);
        internalAlarmRequest.setManagedObjectInstance(ossPrefix);
        internalAlarmRequest.setPerceivedSeverity(perceivedSeverity);
        internalAlarmRequest.setRecordType(NON_SYNCHABLE_ALARM);
        internalAlarmRequest.setAdditionalAttributes(ImmutableMap.of(
                FDN, networkElementFdn,
                BEHALF, MANAGEMENT_SYSTEM_ENM));
        return internalAlarmRequest;
    }

    private String getManagedElementFdn(final String networkElementFdn){
        return MANAGED_ELEMENT + "=" + FdnUtil.getFdnName(networkElementFdn)
    }
}

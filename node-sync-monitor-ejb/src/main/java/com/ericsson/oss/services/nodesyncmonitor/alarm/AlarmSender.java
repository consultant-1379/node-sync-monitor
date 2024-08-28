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

package com.ericsson.oss.services.nodesyncmonitor.alarm;

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_CLEARED;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_RAISED;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.specimpl.AbstractBuiltResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.fm.internalalarm.api.InternalAlarmRequest;
import com.ericsson.oss.services.nodesyncmonitor.instrumentation.NodeSyncMonitorMBean;
import com.ericsson.oss.services.nodesyncmonitor.recording.SystemRecorderBean;

@ApplicationScoped
public class AlarmSender {


    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmSender.class);
    public static final int INTERNAL_ALARM_SERVICE_PORT = 8080;
    public static final String INTERNAL_ALARM_SERVICE_URL = "http://%s:%s/internal-alarm-service/internalalarm/internalalarmservice/translate";

    private String alarmServiceHost;

    @Inject
    private AlarmRequestBuilder alarmRequestBuilder;

    @Inject
    private SystemRecorderBean systemRecorderBean;

    @Inject
    private NodeSyncMonitorMBean nodeSyncMonitorMBean;

    @PostConstruct
    private void setAlarmServiceHost() {
        alarmServiceHost = System.getProperty("alarm_service_host", "internalalarm-service");
        LOGGER.info("Currently using alarm service host : {}", alarmServiceHost);
    }

    public int raiseAlarm(final String networkElementFdn, final String listener) {
        LOGGER.info("Request was sent to raise alarm for {}", networkElementFdn);
        final InternalAlarmRequest raiseAlarmRequest = alarmRequestBuilder.getRaiseAlarmRequest(networkElementFdn);
        final int result = sendAlarm(raiseAlarmRequest);
        if (result == Response.Status.OK.getStatusCode()) {
            systemRecorderBean.recordEvent(listener, networkElementFdn, ALARM_RAISED);
            nodeSyncMonitorMBean.incrementCmUnsyncedAlarmRaised();
        }
        return result;
    }
    public int clearAlarm(final String networkElementFdn, final String listener) {
        LOGGER.info("Request was sent to clear alarm for {}", networkElementFdn);
        final InternalAlarmRequest clearAlarmRequest = alarmRequestBuilder.getClearAlarmRequest(networkElementFdn);
        final int result = sendAlarm(clearAlarmRequest);
        if (result == Response.Status.OK.getStatusCode()) {
            systemRecorderBean.recordEvent(listener, networkElementFdn, ALARM_CLEARED);
            nodeSyncMonitorMBean.incrementCmUnsyncedAlarmCleared();
        }
        return result;
    }

    private synchronized int sendAlarm(final InternalAlarmRequest internalAlarmRequest) {
        final String url = String.format(INTERNAL_ALARM_SERVICE_URL, alarmServiceHost, INTERNAL_ALARM_SERVICE_PORT);
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Response response = target.request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.HOST, alarmServiceHost)
                .header("X-Tor-UserID", "")
                .post(Entity.entity(internalAlarmRequest, MediaType.APPLICATION_JSON));
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            LOGGER.info("Alarm successfully sent, status [{}]", Response.Status.OK);
        } else {
            LOGGER.error("Alarm failed to send, status [{}] body [{}] ", response.getStatus(), getResponseBody(response));
        }
        return response.getStatus();
    }

    private String getResponseBody(final Response response) {
        if (!((AbstractBuiltResponse)response).isClosed() && response.hasEntity()) {
            return response.readEntity(String.class);
        } else {
            return "";
        }
    }
}

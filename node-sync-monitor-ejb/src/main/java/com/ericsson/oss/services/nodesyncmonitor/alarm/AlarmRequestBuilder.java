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
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.BEHALF;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CLEARED;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_UNSYNCHRONIZED;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PROCESSING_ERROR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FDN;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CONFIGURATION_CUSTOMIZING_ERROR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MANAGED_ELEMENT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MANAGEMENT_SYSTEM_ENM;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ME_CONTEXT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MINOR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NON_SYNCHABLE_ALARM;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.OSS_PREFIX;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ericsson.oss.services.fm.internalalarm.api.InternalAlarmRequest;
import com.ericsson.oss.services.nodesyncmonitor.dps.Dps;
import com.ericsson.oss.services.nodesyncmonitor.util.FdnUtil;
import com.google.common.collect.ImmutableMap;

@ApplicationScoped
public class AlarmRequestBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmRequestBuilder.class);
    
    @Inject
    private Dps dps;
    public InternalAlarmRequest getClearAlarmRequest(final String networkElementFdn) {
        return getAlarmRequest(networkElementFdn, CLEARED);
    }
    public InternalAlarmRequest getRaiseAlarmRequest(final String networkElementFdn) {
        return getAlarmRequest(networkElementFdn, MINOR);
    }
    private InternalAlarmRequest getAlarmRequest(final String networkElementFdn, final String perceivedSeverity) {
        final InternalAlarmRequest internalAlarmRequest = new InternalAlarmRequest();
        internalAlarmRequest.setSpecificProblem(CM_UNSYNCHRONIZED);
        internalAlarmRequest.setProbableCause(CONFIGURATION_CUSTOMIZING_ERROR);
        internalAlarmRequest.setEventType(PROCESSING_ERROR);
        internalAlarmRequest.setManagedObjectInstance(getManagedObjectInstance(networkElementFdn));
        internalAlarmRequest.setPerceivedSeverity(perceivedSeverity);
        internalAlarmRequest.setRecordType(NON_SYNCHABLE_ALARM);
        internalAlarmRequest.setAdditionalAttributes(ImmutableMap.of(
                FDN, networkElementFdn,
                BEHALF, MANAGEMENT_SYSTEM_ENM));
        LOGGER.debug("Generating an internal alarm with details: {}", internalAlarmRequest);
        return internalAlarmRequest;
    }
    @SuppressWarnings("squid:S864")
    private String getManagedObjectInstance(final String networkElementFdn) {
        final String managedElementFdn = MANAGED_ELEMENT + "=" + FdnUtil.getFdnName(networkElementFdn);
        final String ossPrefix = dps.findAttributeValue(networkElementFdn, OSS_PREFIX);
        if (ossPrefix == null || ossPrefix.trim().isEmpty()) {
            return managedElementFdn;
        }
        return ossPrefix.contains(ME_CONTEXT + "=")
                ? ossPrefix
                : ossPrefix + "," + managedElementFdn;
    }
}

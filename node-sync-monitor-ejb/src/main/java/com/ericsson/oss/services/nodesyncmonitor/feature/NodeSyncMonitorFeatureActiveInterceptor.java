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

package com.ericsson.oss.services.nodesyncmonitor.feature;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import com.ericsson.oss.services.nodesyncmonitor.listener.CmNodeSyncMonitorFeatureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NodeSyncMonitorFeatureActive
@Dependent
@Interceptor
public class NodeSyncMonitorFeatureActiveInterceptor {

    @Inject
    private CmNodeSyncMonitorFeatureListener cmNodeSyncMonitorFeatureListener;

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeSyncMonitorFeatureActiveInterceptor.class);

    @AroundInvoke
    public Object isActive(final InvocationContext ctx) throws Exception {
        final String featureActive = cmNodeSyncMonitorFeatureListener.getCmNodeSyncMonitorFeature();
        if (featureActive.equalsIgnoreCase("on")) {
            LOGGER.info("NodeSyncMonitorFeature is on, proceeding.");
            return ctx.proceed();
        }
        LOGGER.info("NodeSyncMonitorFeature is off, exiting.");
        return false;
    }
}

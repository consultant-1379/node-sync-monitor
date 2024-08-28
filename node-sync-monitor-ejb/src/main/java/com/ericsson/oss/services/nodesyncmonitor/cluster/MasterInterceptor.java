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

package com.ericsson.oss.services.nodesyncmonitor.cluster;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

/**
 * Verifies that the current instance is master of the cluster before allowing the
 * execution to proceed.
 **/
@Master
@Dependent
@Interceptor
public class MasterInterceptor {

        @Inject
        private ClusterMembershipObserver cluster;

        @AroundInvoke
        public Object membership(final InvocationContext ctx) throws Exception {
                if (cluster.isMaster()) {
                        return ctx.proceed();
                }
                return null;
        }

}

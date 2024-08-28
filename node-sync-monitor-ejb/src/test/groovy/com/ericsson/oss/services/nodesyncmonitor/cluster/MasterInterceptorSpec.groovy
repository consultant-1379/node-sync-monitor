/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.nodesyncmonitor.cluster

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.nodesyncmonitor.cluster.ClusterMembershipObserver
import com.ericsson.oss.services.nodesyncmonitor.cluster.MasterInterceptor

import javax.interceptor.InvocationContext

class MasterInterceptorSpec extends CdiSpecification {

    @MockedImplementation
    ClusterMembershipObserver cluster

    @ObjectUnderTest
    MasterInterceptor interceptor

    def testMembership_master() {
        given: "The cluster member is a master"
        cluster.isMaster() >> true
        Closure closure = interceptor.&membership
        def InvocationContext ctx = Mock(InvocationContext) {
            getMethod() >> closure.owner.class.getMethod('membership', InvocationContext.class)
        }

        when: "Interceptor receives the context"
        def obj = interceptor.membership(ctx)

        then: "Context is allowed to proceed"
        1 * ctx.proceed() >> "RESULT"
        obj == "RESULT"
    }

    def testMembership_slave()  {
        given: "The cluster member is a standby"
        cluster.isMaster() >> false
        Closure closure = interceptor.&membership
        def InvocationContext ctx = Mock(InvocationContext) {
            getMethod() >> closure.owner.class.getMethod('membership', InvocationContext.class)
        }

        when: "Interceptor receives the context"
        def obj = interceptor.membership(ctx)

        then: "Context is not proceeded"
        0 * ctx.proceed()
        !obj
    }
}


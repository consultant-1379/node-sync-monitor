package com.ericsson.oss.services.nodesyncmonitor.feature

import javax.inject.Inject
import javax.interceptor.InvocationContext

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries
import com.ericsson.oss.services.nodesyncmonitor.listener.CmNodeSyncMonitorFeatureListener

import spock.lang.Subject

class NodeSyncMonitorFeatureInterceptorSpec extends CdiSpecification {

    @ImplementationClasses
    private static final Class[] definedImplementations = [
            DpsReadOnlyQueries.class
    ]

    @Subject
    @ObjectUnderTest
    private NodeSyncMonitorFeatureActiveInterceptor featureInterceptor

    @Inject
    private InvocationContext invocationContext

    @Inject
    private CmNodeSyncMonitorFeatureListener cmNodeSyncMonitorFeatureListener;


    def "Test when feature is off then nothing is done"() {

        given: "feature is OFF"
        cmNodeSyncMonitorFeatureListener.listenForChangeInCmNodeSyncMonitorFeature("off")

        when: "feature is checked"
        def proceed = featureInterceptor.isActive(invocationContext)

        then: "the result is not to proceed any further"
        assert !proceed
    }

    def "Test when feature is on then go further"() {

        given: "feature is ON"
        cmNodeSyncMonitorFeatureListener.listenForChangeInCmNodeSyncMonitorFeature("on")

        when: "feature is checked"
        def proceed = featureInterceptor.isActive(invocationContext)

        then: "the result is to proceed further. NOTE: The intercepted method's return type is void. Therefore, null is returned from this method, and by the interceptor as well."
        assert proceed == null
    }
}

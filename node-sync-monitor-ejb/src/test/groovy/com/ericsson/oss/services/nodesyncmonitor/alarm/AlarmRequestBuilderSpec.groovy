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
package com.ericsson.oss.services.nodesyncmonitor.alarm

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.services.fm.internalalarm.api.InternalAlarmRequest
import com.ericsson.oss.services.nodesyncmonitor.dps.DpsReadOnlyQueries

import spock.lang.Unroll

class AlarmRequestBuilderSpec extends CdiSpecification {

    private RuntimeConfigurableDps runtimeDps

    @ImplementationClasses
    private static final Class[] definedImplementations = [DpsReadOnlyQueries.class]

    @ObjectUnderTest
    AlarmRequestBuilder alarmRequestBuilder

    def setup() {
        runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)
    }

    @Unroll
    def "Test building alarms with ossPrefix '#ossPrefix'"() {

        given: "node with networkElementFdn and ossPrefix"
        runtimeDps.addManagedObject().withFdn(networkElementFdn).addAttribute("ossPrefix", ossPrefix).generateTree().build()

        and: "raise and clear alarm requests are created for networkElementFdn"
        InternalAlarmRequest raiseAlarmRequest = alarmRequestBuilder.getRaiseAlarmRequest(networkElementFdn)
        InternalAlarmRequest clearAlarmRequest = alarmRequestBuilder.getClearAlarmRequest(networkElementFdn)

        expect: "the alarms managedObjectInstance fields are set to managedObjectInstanceFdn"
        raiseAlarmRequest.getManagedObjectInstance() == managedObjectInstanceFdn
        clearAlarmRequest.getManagedObjectInstance() == managedObjectInstanceFdn

        and: "the alarms fdn fields are set to the networkElementFdn"
        raiseAlarmRequest.getAdditionalAttributes().get("fdn") == networkElementFdn
        clearAlarmRequest.getAdditionalAttributes().get("fdn") == networkElementFdn

        and: "the alarms perceivedSeverity fields are set correctly"
        raiseAlarmRequest.getPerceivedSeverity() == "MINOR"
        clearAlarmRequest.getPerceivedSeverity() == "CLEARED"

        where:
        networkElementFdn       | ossPrefix                         || managedObjectInstanceFdn
        "NetworkElement=LTE01"  | "SubNetwork=Cork,MeContext=LTE01" || "SubNetwork=Cork,MeContext=LTE01"
        "NetworkElement=LTE02"  | "SubNetwork=Cork"                 || "SubNetwork=Cork,ManagedElement=LTE02"
        "NetworkElement=LTE03"  | "MeContext=LTE03"                 || "MeContext=LTE03"
        "NetworkElement=LTE04"  | " "                               || "ManagedElement=LTE04"
        "NetworkElement=LTE05"  | null                              || "ManagedElement=LTE05"
    }
}

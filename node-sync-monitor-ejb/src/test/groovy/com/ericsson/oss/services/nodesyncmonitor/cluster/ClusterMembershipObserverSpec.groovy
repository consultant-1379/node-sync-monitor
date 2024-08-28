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

package com.ericsson.oss.services.nodesyncmonitor.cluster

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent
import com.ericsson.oss.services.nodesyncmonitor.cluster.ClusterMembershipObserver


class ClusterMembershipObserverSpec extends CdiSpecification {

    @ObjectUnderTest
    ClusterMembershipObserver cluster

    def test_onMembershipChangeEvent_Stand_by() {
        setup: "Event indicating member is not master"
        def event = Stub(MembershipChangeEvent) {
            isMaster() >> false
        }

        when: "The event is receieved by the observer"
        cluster.onMembershipChangeEvent(event)

        then: "The member is not master"
        !cluster.isMaster()

    }

    def test_onMembershipChangeEvent_Master()  {
        given: "Membership change events for member"
        def eventMaster1 = Stub(MembershipChangeEvent) {
            isMaster() >> true
        }
        def eventStand_by = Stub(MembershipChangeEvent) {
            isMaster() >> false
        }

        when: "The event indicating member as a master is received"
        cluster.onMembershipChangeEvent(eventMaster1)

        then: "The member is master"
        cluster.isMaster()

        when: "The member is a master, receives event to become stand-by"
        cluster.onMembershipChangeEvent(eventStand_by)

        then: "The member is a stand-by"
        !cluster.isMaster()

        when: "The member is stand-by, receives event to become master"
        cluster.onMembershipChangeEvent(eventMaster1)

        then: "The member is a master"
        cluster.isMaster()

    }

    def test_onShutdownEvent_Master() {
        given: "The cluster member is master"
        cluster.master = true

        when: "The member is shut-down"
        cluster.shutdown()

        then: "The member is no longer a master"
        !cluster.isMaster()

    }
}


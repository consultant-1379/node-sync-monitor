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

import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent;
import com.ericsson.oss.itpf.sdk.cluster.annotation.ServiceCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NODE_SYNC_MONITOR_CLUSTER;

/**
 * A class to observe the membership changes for the NodeSyncMonitorCluster
 */
@ApplicationScoped
public class ClusterMembershipObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterMembershipObserver.class);
    private boolean master;
    private final Object lock = new Object();

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            LOGGER.info("Shutting down, was running as {} of {}", master ? "master" : "slave", NODE_SYNC_MONITOR_CLUSTER);
            master = false;
        }
    }

    public boolean isMaster() {
        synchronized (lock) {
            LOGGER.debug("Checking if instance is master {} ", master);
            return master;
        }
    }

    /**
     * Listens for cluster membership change events to determine if this instance is the master or slave.
     * @param event membership change event
     */
    public void onMembershipChangeEvent(@Observes @ServiceCluster(NODE_SYNC_MONITOR_CLUSTER) final MembershipChangeEvent event) {
        synchronized (lock) {
            if (event.isMaster() != master) {
                master = event.isMaster();
            }
            LOGGER.info("Now running as {} of {}", master ? "master" : "slave", NODE_SYNC_MONITOR_CLUSTER);
        }
    }

}

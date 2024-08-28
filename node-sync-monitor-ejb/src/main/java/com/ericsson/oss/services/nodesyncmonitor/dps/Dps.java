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

package com.ericsson.oss.services.nodesyncmonitor.dps;

import java.util.List;

/**
 * Describes the business methods of the DpsQueries class.
 */
public interface Dps {

    /**
     * Finds the attribute value for the FDN.
     * Note the return type can be specified from the invoking class e.g. boolean, String etc.
     */
    <R> R findAttributeValue(final String fdn, final String attributeName);

    /**
     * Finds the NetworkElement FDNs where the number of sync failures equal or greater than the threshold.
     * <ul>
     * <li>Find all the CmNodeHeartbeatSupervision MOs with attribute 'active' set to true.</li>
     * <li>For all of the nodes with CM supervision enabled, find the CmFunction MOs with attribute 'failedSyncsCount' equal or greater than the
     * threshold.</li>
     * </ul>
     * Note that the CmNodeHeartbeatSupervision.active status is checked as disabling supervision for the node will not reset the CmFunction.failedSyncsCount to 0.
     * @param threshold
     *            The threshold for the number of CM sync failures.
     * @return the list of NetworkElement FDNs, otherwise empty list if not found.
     */
    List<String> findNetworkElementsThatNeedAlarm(final int threshold);

    /**
     * Finds the NetworkElement FDNs where the OpenAlarm PO has attribute 'specificProblem' set to 'CM unsynchronized'.
     *
     * @return the list of NetworkElement FDNs, otherwise empty list if not found.
     */
    List<String> findNetworkElementsWithOpenAlarm();
}

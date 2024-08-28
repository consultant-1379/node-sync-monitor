/**
 * ------------------------------------------------------------------------------
 * ******************************************************************************
 * COPYRIGHT Ericsson 2022
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 * ******************************************************************************
 * ------------------------------------------------------------------------------
 */

package com.ericsson.oss.services.nodesyncmonitor.recording;

import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.ericsson.oss.itpf.sdk.recording.EventLevel.COARSE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.LIVE_VIEW;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NODE_SYNC_MONITOR;

/**
 * Class that handles the system level logging.
 */
@ApplicationScoped
public class SystemRecorderBean {

    @Inject
    private SystemRecorder recorder;

    public void recordEvent(final String event, final String resource, final String additionalInfo) {
        recorder.recordEvent(NODE_SYNC_MONITOR + "." + event, COARSE, LIVE_VIEW, resource, additionalInfo);
    }
}

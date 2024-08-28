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

package com.ericsson.oss.services.nodesyncmonitor.models;

import com.ericsson.oss.itpf.modeling.annotation.DefaultValue;
import com.ericsson.oss.itpf.modeling.annotation.EModel;
import com.ericsson.oss.itpf.modeling.annotation.configparam.ConfParamDefinition;
import com.ericsson.oss.itpf.modeling.annotation.configparam.ConfParamDefinitions;
import com.ericsson.oss.itpf.modeling.annotation.configparam.Scope;
import com.ericsson.oss.itpf.modeling.annotation.constraints.Min;
import com.ericsson.oss.itpf.modeling.annotation.constraints.NotNull;
import com.ericsson.oss.itpf.modeling.annotation.constraints.Pattern;

@EModel(namespace = "NodeSyncMonitor",
        description = "Configuration parameters for the Node Synchronization Monitor service.")
@ConfParamDefinitions
public class NodeSyncMonitorConfigurationModel {

    @NotNull
    @Pattern("off|on")
    @DefaultValue("off")
    @ConfParamDefinition(
            description = "Enables or disables the feature that monitors the synchronization state of CM capable nodes",
            scope = Scope.SERVICE)
    public String cmNodeSyncMonitorFeature;

    @NotNull
    @Min(value = 8)
    @DefaultValue("8")
    @ConfParamDefinition(
            description = "Threshold that defines the number of failed CM synchronization attempts before an alarm is raised.",
            scope = Scope.SERVICE)
    public Integer noOfCmSyncFailuresBeforeAlarm;
}

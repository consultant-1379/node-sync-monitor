/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.nodesyncmonitor.rest;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * A class extending {@link Application} and annotated with @ApplicationPath is
 * the Java EE 8 "no XML" approach to activating JAX-RS.
 * 
 * <p>
 * Resources are served relative to the servlet path specified in the
 * {@link ApplicationPath} annotation.
 * </p>
 */
@ApplicationPath("/rest")
public class JaxRsActivator extends Application {

    /* class body intentionally left blank */

}

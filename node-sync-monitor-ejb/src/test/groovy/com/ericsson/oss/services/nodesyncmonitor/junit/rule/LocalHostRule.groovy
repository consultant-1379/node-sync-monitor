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
package com.ericsson.oss.services.nodesyncmonitor.junit.rule

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Intercepts and sets the alarm_service_host to "localhost" before object under test is executed.
 */
class LocalHostRule implements TestRule {

    @Override
    Statement apply(Statement statement, Description description) {
        return new MyHostRule(statement)
    }

    class MyHostRule extends Statement {

        private final Statement base

        MyHostRule(Statement base) {
            this.base = base;
        }
        @Override
        void evaluate() throws Throwable {
            System.setProperty("alarm_service_host", "localhost")
            base.evaluate()
        }
    }
}

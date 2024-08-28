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

import static com.ericsson.oss.itpf.datalayer.dps.query.graph.builder.MainPathBuilder.fromQuery;
import static com.ericsson.oss.itpf.datalayer.dps.query.graph.builder.MainPathBuilder.startWith;
import static com.ericsson.oss.itpf.datalayer.dps.query.graph.builder.RelationshipBuilder.ancestor;
import static com.ericsson.oss.itpf.datalayer.dps.query.graph.builder.RelationshipBuilder.descendant;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ACTIVE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ACTIVE_ALARM_STATE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.ALARM_STATE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_FUNCTION;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_NODE_HEARTBEAT_SUPERVISION;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CM_UNSYNCHRONIZED;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PROCESSING_ERROR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.EVENT_TYPE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FAILED_SYNCS_COUNT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FDN;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.FM;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.CONFIGURATION_CUSTOMIZING_ERROR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MINOR;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.MO_DOES_NOT_EXIST;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.NETWORK_ELEMENT;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.OPEN_ALARM;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PRESENT_SEVERITY;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.PROBABLE_CAUSE;
import static com.ericsson.oss.services.nodesyncmonitor.constants.Constants.SPECIFIC_PROBLEM;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.ericsson.oss.services.nodesyncmonitor.util.FdnUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.exception.model.NotDefinedInModelException;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.query.Query;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryExecutor;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryPathExecutor;
import com.ericsson.oss.itpf.datalayer.dps.query.Restriction;
import com.ericsson.oss.itpf.datalayer.dps.query.StringMatchCondition;
import com.ericsson.oss.itpf.datalayer.dps.query.TargetOptions;
import com.ericsson.oss.itpf.datalayer.dps.query.TypeRestrictionBuilder;
import com.ericsson.oss.itpf.datalayer.dps.query.graph.QueryPath;
import com.ericsson.oss.itpf.datalayer.dps.query.graph.QueryPathRestrictionBuilder;
import com.ericsson.oss.itpf.datalayer.dps.query.projection.ProjectionBuilder;
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;

/**
 * Generic DPS query class.
 * <p>
 * Note as {@code ManagedObjects} are transactional they should not be accessed outside the boundaries of the transaction. Thus, they must not be
 * returned from any public defined here. Instead, return {@code ManagedObjectDto} if needed.
 * <p>
 * This class should only contain public methods that perform read operations on DPS. No write operations should be added as the write access on
 * {@code DataPersistenceService} is set to 'false' as per DPS recommendations {@see #getLiveBucketSuppressMediation()}.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class DpsReadOnlyQueries implements Dps {

    @EServiceRef
    private DataPersistenceService dps;

    private static final Logger LOGGER = LoggerFactory.getLogger(DpsReadOnlyQueries.class);

    @Override
    public <R> R findAttributeValue(final String fdn, final String attributeName) {
        final ManagedObject mo = findMo(fdn);
        Object attributeValue = null;
        try {
            attributeValue = mo.getAttribute(attributeName);
            return (R) attributeValue;
        } catch (final NotDefinedInModelException e) {
            LOGGER.warn("Attribute {} was not found for {} error is {}", attributeName, fdn, e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> findNetworkElementsThatNeedAlarm(final int threshold) {
        final Query<TypeRestrictionBuilder> typeQuery = getCmNodeHeartbeatSupervisionTypeQuery();
        final QueryPathRestrictionBuilder queryPathRestrictionBuilder = getQueryPathExecutor().getRestrictionBuilder();
        final Restriction failedSyncsCountRestriction = queryPathRestrictionBuilder.greaterThan(FAILED_SYNCS_COUNT, threshold - 1);

        final QueryPath cmFunctionQueryPath =
                startWith(fromQuery(typeQuery).withAlias(CM_NODE_HEARTBEAT_SUPERVISION))
                        .withRelationships(ancestor().withAlias(NETWORK_ELEMENT))
                        .withRelationships(descendant()
                                .withAlias(CM_FUNCTION)
                                .withScope(1, 1)
                                .withRestriction(failedSyncsCountRestriction))
                        .build();
        return executeQueryPath(cmFunctionQueryPath).stream()
                .map(FdnUtil::getParentFdn).collect(Collectors.toList());
    }

    @Override
    public List<String> findNetworkElementsWithOpenAlarm() {
        final Query<TypeRestrictionBuilder> typeQuery = dps.getQueryBuilder().createTypeQuery(FM, OPEN_ALARM);
        final Restriction allRestrictions = getOpenAlarmRestrictions(typeQuery);
        typeQuery.setRestriction(allRestrictions);
        final List<String> fdns = getQueryExecutor().executeProjection(typeQuery, ProjectionBuilder.attribute(FDN));
        LOGGER.debug("Result of the FDN projection query : {}", fdns);

        return fdns;
    }

    private ManagedObject findMo(final String fdn) {
        final ManagedObject mo = getLiveBucketSuppressMediation().findMoByFdn(fdn);
        if (mo == null) {
            throw new IllegalStateException(String.format(MO_DOES_NOT_EXIST, fdn));
        }
        return mo;
    }

    private Query<TypeRestrictionBuilder> getCmNodeHeartbeatSupervisionTypeQuery() {
        final Query<TypeRestrictionBuilder> typeQuery = dps.getQueryBuilder().createTypeQuery("*", CM_NODE_HEARTBEAT_SUPERVISION);
        final TypeRestrictionBuilder restrictionBuilder = typeQuery.getRestrictionBuilder();
        final Restriction restriction = restrictionBuilder.equalTo(ACTIVE, true);
        typeQuery.setRestriction(restriction);
        return typeQuery;
    }

    private Restriction getOpenAlarmRestrictions(final Query<TypeRestrictionBuilder> typeQuery) {
        final TypeRestrictionBuilder restrictionBuilder = typeQuery.getRestrictionBuilder();
        final Restriction specificProblemRestriction = restrictionBuilder.equalTo(SPECIFIC_PROBLEM, CM_UNSYNCHRONIZED);
        final Restriction alarmStateRestriction = restrictionBuilder.matchesString(ALARM_STATE, ACTIVE_ALARM_STATE, StringMatchCondition.CONTAINS);
        final Restriction presentSeverityRestriction = restrictionBuilder.equalTo(PRESENT_SEVERITY, MINOR);
        final Restriction probableCauseRestriction = restrictionBuilder.equalTo(PROBABLE_CAUSE, CONFIGURATION_CUSTOMIZING_ERROR);
        final Restriction eventTypeRestriction = restrictionBuilder.equalTo(EVENT_TYPE, PROCESSING_ERROR);

        return restrictionBuilder.allOf(specificProblemRestriction, alarmStateRestriction, presentSeverityRestriction, probableCauseRestriction,
                eventTypeRestriction);
    }

    private List<String> executeQueryPath(final QueryPath queryPath) {
        final Iterable<ManagedObject> queryPathResults = getQueryPathExecutor().getTargets(queryPath, TargetOptions.targetOptions().build());
        final List<ManagedObject> mos = StreamSupport.stream(queryPathResults.spliterator(), false).collect(Collectors.toList());
        final List<String> fdns = mos.stream().map(ManagedObject::getFdn).collect(Collectors.toList());
        LOGGER.debug("Result of the path query: {}", fdns);

        return fdns;
    }

    /**
     * Returns the live data bucket and sets the write access for dps to false. This is recommended by the DPS API to ensure that read only requests
     * are directed to the Neo4J follower nodes only. This avoids unnecessary load on the leader node.
     * <p>
     * The write access is recommended by DPS to be set for every transaction to ensure write access is disabled. Therefore, any new public methods
     * added should ensure that they use only this method to retrieve the live bucket. For more details see the DPS API.
     */
    private DataBucket getLiveBucketSuppressMediation() {
        dps.setWriteAccess(Boolean.FALSE);
        return dps.getDataBucket("live", BucketProperties.SUPPRESS_MEDIATION);
    }

    private QueryPathExecutor getQueryPathExecutor() {
        return getLiveBucketSuppressMediation().getQueryPathExecutor();
    }

    private QueryExecutor getQueryExecutor() {
        return getLiveBucketSuppressMediation().getQueryExecutor();
    }

}

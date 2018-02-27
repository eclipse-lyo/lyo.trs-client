/*
 * Copyright (c) 2016-2018 KTH Royal Institute of Technology.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the Eclipse Distribution
 * License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 * Omar Kacimi         -  Initial implementation
 * Andrew Berezovskyi  -  Lyo contribution updates
 */

package org.eclipse.lyo.oslc4j.trs.consumer.handlers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.oauth.OAuthException;
import org.apache.log4j.Logger;
import org.eclipse.lyo.core.trs.Base;
import org.eclipse.lyo.core.trs.ChangeEvent;
import org.eclipse.lyo.core.trs.ChangeLog;
import org.eclipse.lyo.core.trs.TrackedResourceSet;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.RepresentationRetrievalException;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.ServerRollBackException;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.JenaModelException;
import org.eclipse.lyo.oslc4j.trs.consumer.httpclient.TRSHttpClient;
import org.eclipse.lyo.oslc4j.trs.consumer.util.SparqlUtil;

/**
 * Specialization fothe TRS provider class which supports multithreading when it
 * comes to the processing of the base members and the change events.
 * Additionally, processes the sparql updates as a single transaction. In case
 * the sparql update transaction is not successful, then the indexing of the
 * base members is restarted all over again
 *
 * @author Omar
 */
public class ConcurrentTRSProviderHandler extends TrsProviderHandler {
    private final static Logger log = Logger.getLogger(ConcurrentTRSProviderHandler.class);

    public ConcurrentTRSProviderHandler(String trsUriBase, String sparqlQueryService, String sparqlUpdateService,
            TRSHttpClient trsHttpClient, String userName, String pwd, String sparql_user, String sparql_pwd) {
        super(trsUriBase, sparqlQueryService, sparqlUpdateService, trsHttpClient, userName, pwd, sparql_user,
                sparql_pwd);

    }

    public ConcurrentTRSProviderHandler(String trsUriBase, String sparqlQueryService, String sparqlUpdateService,
            TRSHttpClient trsHttpClient, String userName, String pwd) {
        super(trsUriBase, sparqlQueryService, sparqlUpdateService, trsHttpClient, userName, pwd);

    }

    @Override
    public void pollAndProcessChanges()
            throws URISyntaxException, OAuthException, JenaModelException, IOException, ServerRollBackException,
            RepresentationRetrievalException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date processingDateStart = new Date();
        log.info("started dealing with TRS Provider: " + trsUriBase);

        TrackedResourceSet updatedTrs = extractRemoteTrs();
        boolean indexingStage = false;
        List<URI> baseMembers = new ArrayList<URI>();
        if (lastProcessedChangeEventUri == null) {
            log.debug("Indexing Stage.");
            log.debug("Requesting Base members from remote server");
            List<Base> bases = updateBases(updatedTrs);
            log.debug("Base members retrieved !");
            for (Base base : bases) {
                baseMembers.addAll(base.getMembers());
            }

            lastProcessedChangeEventUri = bases.get(0).getCutoffEvent();
            indexingStage = true;
        }
        log.debug("Requesting changeLogs from Remote Server");
        List<ChangeLog> changeLogs = fetchUpdatedChangeLogs(updatedTrs);
        log.debug("change Logs Retrieved ! ");
        log.debug("Compressing the list of changes ! ");
        List<ChangeEvent> compressedChanges = optimizedChangesList(changeLogs);
        log.debug("Change list compressed ! ");

        log.debug("starting the processing of change events and base members creations");

        log.trace("Creating necessary sparql update queries");

        ExecutorService changeEventsAndBaseProcessingExecutor = null;
        List<String> queries = new ArrayList<>();
        AtomicLong modelSize = new AtomicLong(0);
        changeEventsAndBaseProcessingExecutor = Executors.newCachedThreadPool();

        if (indexingStage) {
            log.debug("optimizing the list of base members against the change events to be processed.");
            baseChangeEventsOptimization(compressedChanges, baseMembers);
            log.debug("finished optimizing the list of base members against the change events to be processed !");
            log.debug(
                    "Indexing stage. Base members creations will be be added to the list of events to be processed.");

            for (URI baseMemberUri : baseMembers) {
                BaseMemberHandler baseMemberHandler = new BaseMemberHandler(oslcClient, sparqlQueryService,
                        sparqlUpdateService, baseAuth_userName, baseAuth_pwd, baseMemberUri.toString(), queries,
                        modelSize);
                changeEventsAndBaseProcessingExecutor.execute(baseMemberHandler);
            }
        }

        for (ChangeEvent compressedChangeEvent : compressedChanges) {
            ChangeEventHandler changeEventHandler = new ChangeEventHandler(oslcClient, sparqlQueryService,
                    sparqlUpdateService, baseAuth_userName, baseAuth_pwd, compressedChangeEvent, queries, modelSize);
            changeEventsAndBaseProcessingExecutor.execute(changeEventHandler);
            lastProcessedChangeEventUri = compressedChangeEvent.getAbout();
        }

        changeEventsAndBaseProcessingExecutor.shutdown();

        while (!changeEventsAndBaseProcessingExecutor.isTerminated()) {

        }

        if (!queries.isEmpty()) {
            queries.contains(null);
            if (queries.contains("")) {
                lastProcessedChangeEventUri = null;
                throw new RepresentationRetrievalException();
            }
            log.debug("number of processed queries: " + queries.size());
            log.debug("finished Creating necessary sparql update queries");
            StringBuilder queriesStringBuilder = new StringBuilder();

            for (String query : queries) {
                queriesStringBuilder.append(query);
                queriesStringBuilder.append("; \n");
            }

            queriesStringBuilder.replace(queriesStringBuilder.lastIndexOf("; \n"),
                    queriesStringBuilder.lastIndexOf("; \n") + 1, "");

            String finalQueryString = queriesStringBuilder.toString();
            log.debug(finalQueryString);
            log.debug("a total of: " + modelSize + " triple . In the sparql update query");
            log.debug("sending Update SPARQL Query to server");

            SparqlUtil.processQuery_sesame(finalQueryString, sparqlUpdateService, sparql_baseAuth_userName,
                    sparql_baseAuth_pwd);
            log.debug("Update SPARQL Queries successful!");
        }

        Date finishProcessingData = new Date();

        log.info("finished dealing with TRS Provider: " + trsUriBase);
        log.debug("start dealing at: " + sdf.format(
                processingDateStart) + " . Finished dealing with provider at: " + sdf.format(finishProcessingData));

    }

/*    public void pollAndProcessChanges2() throws IOException, OAuthException, URISyntaxException,
    ServerRollBackException, IllegalAccessException, IllegalArgumentException, InstantiationException,
    InvocationTargetException, SecurityException, NoSuchMethodException, DatatypeConfigurationException,
    OslcCoreApplicationException, RepresentationRetrievalException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date processingDateStart = new Date();

        ExecutorService changeEventsAndBaseProcessingExecutor = null;

        List<String> queries = new ArrayList<>();
        AtomicLong modelSize = new AtomicLong(0);

        changeEventsAndBaseProcessingExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        log.info("started dealing with TRS Provider: " + trsUriBase);

        TrackedResourceSet updatedTrs = extractRemoteTrs();
        log.debug("Requesting changeLogs from Remote Server");

        List<URI> baseMembers = new ArrayList<URI>();
        boolean isIndexingStage = false;
        if (lastProcessedChangeEventUri == null) {
            log.debug("Indexing Stage.");
            log.debug("Requesting Base members from remote server");
            List<Base> bases = updateBases(updatedTrs);
            log.debug("Base members retrieved !");
            for (Base base : bases) {
                baseMembers.addAll(base.getMembers());
            }

            lastProcessedChangeEventUri = bases.get(0).getCutoffEvent();
            isIndexingStage = true;
        }

        List<ChangeLog> changeLogs = fetchUpdatedChangeLogs(updatedTrs);
        log.debug("change Logs Retrieved ! ");
        log.debug("Compressing the list of changes ! ");
        List<ChangeEvent> compressedChanges = optimizedChangesList(changeLogs);
        log.debug("Change list compressed ! ");

        if (isIndexingStage)
            indexingStage(compressedChanges, changeEventsAndBaseProcessingExecutor, queries, modelSize, baseMembers);

        log.debug("starting the processing of change events and base members creations");

        log.debug("Creating necessary sparql update queries");

        for (ChangeEvent compressedChangeEvent : compressedChanges) {
            ChangeEventHandler changeEventHandler = new ChangeEventHandler(oslcClient, sparqlQueryService,
                    sparqlUpdateService, baseAuth_userName, baseAuth_pwd, compressedChangeEvent, queries, modelSize);
            changeEventsAndBaseProcessingExecutor.execute(changeEventHandler);
            lastProcessedChangeEventUri = compressedChangeEvent.getAbout();
        }

        changeEventsAndBaseProcessingExecutor.shutdown();

        while (!changeEventsAndBaseProcessingExecutor.isTerminated()) {

        }

        if (!queries.isEmpty()) {
            queries.contains(null);
            if (queries.contains("")) {
                lastProcessedChangeEventUri = null;
                throw new RepresentationRetrievalException();
            }
            log.debug("number of processed queries: " + queries.size());
            log.debug("finished Creating necessary sparql update queries");
            StringBuilder queriesStringBuilder = new StringBuilder();

            for (String query : queries) {
                queriesStringBuilder.append(query);
                queriesStringBuilder.append("; \n");
            }

            queriesStringBuilder.replace(queriesStringBuilder.lastIndexOf("; \n"),
                    queriesStringBuilder.lastIndexOf("; \n") + 1, "");

            String finalQueryString = queriesStringBuilder.toString();
            log.debug(finalQueryString);
            log.debug("a total of: " + modelSize + " triple . In the sparql update query");
            log.debug("sending Update SPARQL Query to server");

            SparqlUtil.processQuery_sesame(finalQueryString, sparqlUpdateService, sparql_baseAuth_userName,
                    sparql_baseAuth_pwd);
            log.debug("Update SPARQL Queries successful!");
        }

        Date finishProcessingData = new Date();

        log.info("finished dealing with TRS Provider: " + trsUriBase);
        log.debug("start dealing at: " + sdf.format(processingDateStart) + " . Finished dealing with provider at: "
                + sdf.format(finishProcessingData));

    }*/
}

/*
 * Copyright (c) 2016-2018 KTH Royal Institute of Technology and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse
 * Public License v1.0 and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the
 * Eclipse Distribution
 * License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 *     Andrew Berezovskyi  -  Initial implementation
 *     Omar Kacimi         -  Original code
 *     Xufei Ning          -  MQTT modification
 */

package org.eclipse.lyo.oslc4j.trs.client.util;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import org.eclipse.lyo.oslc4j.trs.client.config.TrsConsumerConfiguration;
import org.eclipse.lyo.oslc4j.trs.client.config.TrsProviderConfiguration;
import org.eclipse.lyo.oslc4j.trs.client.handlers.ConcurrentTrsProviderHandler;
import org.eclipse.lyo.oslc4j.trs.client.handlers.TrsProviderHandler;
import org.eclipse.lyo.oslc4j.trs.client.mqtt.MqttTrsEventListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created on 2018-02-27
 *
 * @author Andrew Berezovskyi (andriib@kth.se)
 * @version $version-stub$
 * @since 0.0.1
 */
public class TrsConsumerUtils {
    private final static Logger log = LoggerFactory.getLogger(TrsConsumerUtils.class);

    public static List<TrsProviderHandler> buildHandlersSequential(
            final TrsConsumerConfiguration consumerConfig,
            final Collection<TrsProviderConfiguration> providerConfigs) {
        return buildHandlers(consumerConfig, providerConfigs, TrsConsumerUtils::providerFor);
    }

    public static List<TrsProviderHandler> buildHandlersConcurrent(
            final TrsConsumerConfiguration consumerConfig,
            final Collection<TrsProviderConfiguration> providerConfigs) {
        return buildHandlers(consumerConfig,
                providerConfigs,
                TrsConsumerUtils::concurrentProviderFor
        );
    }

    public static List<TrsProviderHandler> buildHandlers(
            final TrsConsumerConfiguration consumerConfig,
            final Collection<TrsProviderConfiguration> providerConfigs,
            final BiFunction<TrsConsumerConfiguration, TrsProviderConfiguration,
                    TrsProviderHandler> function) {
        final List<TrsProviderHandler> providers = new ArrayList<>();

        for (TrsProviderConfiguration cfg : providerConfigs) {
            TrsProviderHandler trsProvider = function.apply(consumerConfig, cfg);
            providers.add(trsProvider);
            if (!Strings.isNullOrEmpty(cfg.getMqttBroker()) && !Strings.isNullOrEmpty(cfg
                    .getMqttTopic())) {
                // for now an HTTP impl is still needed for MQTT

                // FIXME Andrew@2018-02-28: Rethink how we handle the new duality of the TRS updates
                /*
                So the idea is that we should always listen to MQTT and react quickly to it.
                However, after startup we might have processed the base and the old changelog yet.

                We should receive (and I would say, eagerly fetch corresponding TRS resources)
                the change events over MQTT until the initialisation is done (TrsProviderHandler
                actually uses a flag 'isIndexingStage' for exactly this purpose). In the very
                advanced future we could potentially keep a HashSet of the MQTT updates that
                invalidate any indexing stage efforts, further speeding it up.

                Another thing is that we shall skip the periodic check in case a change event has
                been received over MQTT during the update interval. Would be cumbersome to do it
                via the ScheduledExecutorServer, should better do it by simply yielding on the
                next invocation if the previous run occurred too recently.

                One thing why I am commenting this out is that without a proper approach because
                on synchronised queues and locking any "preindexing" will fail, because between
                the time T1 when the indexing call 'pollAndProcessChanges()' returns and time T3
                when the MqttTrsEventListener accepts an MQTT event there might be a missed event
                 Em, which arrived to the MQTT topic at the time T2, T1<=T2<=T3.

                 More immediate reason why I commented it out is that this invocation is done
                 before Listeners are registered.
                 */
//                try {
//                    // we actually need to wait here, but not in the listener
//                    trsProvider.pollAndProcessChanges();
//                } catch (OAuthException | JenaModelException | RepresentationRetrievalException |
//                        ServerRollBackException | URISyntaxException | IOException e) {
//                    log.error(
//                            "Error polling the service provider {} before initialising MQTT " +
//                                    "subscription",
//                            trsProvider
//                    );
//                }

                try {
                    log.trace("Attempting to connect to an MQTT broker at '{}'",
                            cfg.getMqttBroker()
                    );
                    MqttClient mqttClient = new MqttClient(cfg.getMqttBroker(),
                            consumerConfig.getMqttClientId()
                    );
                    log.info("Connected to an MQTT broker at '{}'", cfg.getMqttBroker());
                    mqttClient.setCallback(new MqttTrsEventListener(trsProvider,
                                                                    cfg,
                                                                    consumerConfig.getScheduler()
                    ));
                    mqttClient.connect();
                    // used to be fixed to 'TRS'
                    mqttClient.subscribe(cfg.getMqttTopic());
                } catch (MqttException e) {
                    log.error("Failed to connect to MQTT broker '{}', topic '{}':\n{}",
                            cfg.getMqttBroker(),
                            cfg.getMqttTopic(),
                            e
                    );
                }
            }
        }

        return providers;
    }

    private static TrsProviderHandler providerFor(final TrsConsumerConfiguration consumerConfig,
            final TrsProviderConfiguration cfg) {
        return new TrsProviderHandler(cfg.getTrsUri(),
                consumerConfig.getSparqlQueryUrl(),
                consumerConfig.getSparqlUpdateUrl(),
                consumerConfig.getHttpClient(),
                cfg.getBasicAuthUsername(),
                cfg.getBasicAuthPassword(),
                consumerConfig.getSparqlUsername(),
                consumerConfig.getSparqlPassword()
        );
    }

    private static TrsProviderHandler concurrentProviderFor(
            final TrsConsumerConfiguration consumerConfig, final TrsProviderConfiguration cfg) {
        return new ConcurrentTrsProviderHandler(cfg.getTrsUri(),
                                                consumerConfig.getSparqlQueryUrl(),
                                                consumerConfig.getSparqlUpdateUrl(),
                                                consumerConfig.getHttpClient(),
                                                cfg.getBasicAuthUsername(),
                                                cfg.getBasicAuthPassword(),
                                                consumerConfig.getSparqlUsername(),
                                                consumerConfig.getSparqlPassword()
        );
    }
}

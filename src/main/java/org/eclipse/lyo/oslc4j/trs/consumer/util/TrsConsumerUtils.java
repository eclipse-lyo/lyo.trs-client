/*
 * Copyright (c) 2016-2018 KTH Royal Institute of Technology and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the Eclipse Distribution
 * License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 *     Andrew Berezovskyi  -  Initial implementation
 *     Omar Kacimi         -  Original code
 *     Xufei Ning          -  MQTT modification
 */

package org.eclipse.lyo.oslc4j.trs.consumer.util;

import com.google.common.base.Strings;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.oauth.OAuthException;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.RepresentationRetrievalException;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.ServerRollBackException;
import org.eclipse.lyo.oslc4j.trs.consumer.handlers.ConcurrentTRSProviderHandler;
import org.eclipse.lyo.oslc4j.trs.consumer.handlers.TrsProviderHandler;
import org.eclipse.lyo.oslc4j.trs.consumer.exceptions.JenaModelException;
import org.eclipse.lyo.oslc4j.trs.consumer.mqtt.MqttTrsEventListener;
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

    /**
     * Create the TRS providers instances by loading the configuration information of the individual
     * TRS providers from their config files
     */
    public static List<TrsProviderHandler> loadTrsProviders(final TrsConsumerConfiguration consumerConfig,
            final Collection<TrsProviderConfiguration> providerConfigs) {
        final List<TrsProviderHandler> providers = new ArrayList<>();

        for (TrsProviderConfiguration cfg : providerConfigs) {
            TrsProviderHandler trsProvider = new ConcurrentTRSProviderHandler(cfg.getTrsUri(),
                    consumerConfig.getSparqlQueryUrl(), consumerConfig.getSparqlUpdateUrl(),
                    consumerConfig.getHttpClient(), cfg.getBasicAuthUsername(), cfg.getBasicAuthPassword(),
                    consumerConfig.getSparqlUsername(), consumerConfig.getSparqlPassword());
            providers.add(trsProvider);
            if (!Strings.isNullOrEmpty(cfg.getMqttBroker()) && !Strings.isNullOrEmpty(cfg.getMqttTopic())) {
                // for now an HTTP impl is still needed for MQTT

                try {
                    // we actually need to wait here, but not in the listener
                    trsProvider.pollAndProcessChanges();
                } catch (OAuthException | JenaModelException | RepresentationRetrievalException |
                        ServerRollBackException | URISyntaxException | IOException e) {
                    log.error("Error polling the service provider {} before initialising MQTT subscription",
                            trsProvider);
                }

                try {
                    log.trace("Attempting to connect to an MQTT broker at '{}'", cfg.getMqttBroker());
                    MqttClient mqttClient = new MqttClient(cfg.getMqttBroker(), consumerConfig.getMqttClientId());
                    log.info("Connected to an MQTT broker at '{}'", cfg.getMqttBroker());
                    mqttClient.setCallback(new MqttTrsEventListener(trsProvider, cfg, consumerConfig.getScheduler()));
                    mqttClient.connect();
                    // used to be fixed to 'TRS'
                    mqttClient.subscribe(cfg.getMqttTopic());
                } catch (MqttException e) {
                    log.error("Failed to connect to MQTT broker '{}', topic '{}':\n{}", cfg.getMqttBroker(),
                            cfg.getMqttTopic(), e);
                }
            }
        }

        return providers;
    }
}

/*
 * Copyright (c) 2017-2018 Xufei Ning and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 which
 * accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the
 * Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 * Xufei Ning          -  Initial implementation
 * Andrew Berezovskyi  -  Lyo contribution updates
 */

package org.eclipse.lyo.trs.consumer.mqtt;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.lyo.core.trs.ChangeEvent;
import org.eclipse.lyo.core.trs.Creation;
import org.eclipse.lyo.core.trs.Deletion;
import org.eclipse.lyo.core.trs.Modification;
import org.eclipse.lyo.trs.consumer.config.TrsProviderConfiguration;
import org.eclipse.lyo.trs.consumer.handlers.TrsProviderHandler;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttTrsEventListener implements MqttCallback {

    private final static Logger log = LoggerFactory.getLogger(MqttTrsEventListener.class);
    private final TrsProviderHandler providerHandler;
    private final TrsProviderConfiguration providerConfiguration;
    private final ScheduledExecutorService executorService;

    public MqttTrsEventListener(final TrsProviderHandler providerHandler,
            final TrsProviderConfiguration providerConfiguration, final ScheduledExecutorService executorService) {
        this.providerHandler = providerHandler;
        this.providerConfiguration = providerConfiguration;
        this.executorService = executorService;
    }

    public void connectionLost(Throwable throwable) {
        log.error("Connection with '{}' broker lost", providerConfiguration.getMqttBroker(), throwable);
    }

    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        final String payload = new String(mqttMessage.getPayload());
        log.info("Message received: " + payload);
        // receive "NEW"
        if (payload.equalsIgnoreCase("NEW")) {
            log.info("Plain 'NEW' ping message received");
            executorService.schedule(providerHandler, 0, TimeUnit.MILLISECONDS);
        }
        // receive ChangeEvent
        else {
            Runnable handleChangeEvent = new Runnable() {
                public void run() {
                    log.info("Full ChangeEvent received");
                    // read data from message
                    String message = payload.substring(
                            payload.indexOf("{") + 1,
                            payload.indexOf("}"));
                    String[] mapper = message.split(" ");
                    int index_order = Arrays.asList(mapper).indexOf("@trs:order");
                    int index_changed = Arrays.asList(mapper).indexOf("@trs:changed");
                    int index_type = Arrays.asList(mapper).indexOf("@rdf:type");
                    String about = mapper[0];
                    String[] changed = mapper[index_changed + 1].split(";"); // changed[0]
                    String[] order = mapper[index_order + 1].split("\""); // order[1]
                    String type = mapper[index_type + 1];
                    // create change event
                    ChangeEvent changeEvent = null;
                    final URI trackedResourceURI = URI.create(changed[0]);
                    final int eventOrder = Integer.parseInt(order[1]);
                    final URI eventURI = URI.create(about);
                    if (type.matches("(.*)Creation(.*)")) {
                        changeEvent = new Creation(eventURI, trackedResourceURI, eventOrder);
                    }
                    if (type.matches("(.*)Modification(.*)")) {
                        changeEvent = new Modification(eventURI, trackedResourceURI, eventOrder);
                    }
                    if (type.matches("(.*)Deletion(.*)")) {
                        changeEvent = new Deletion(eventURI, trackedResourceURI, eventOrder);
                    }
                    log.info(String.format(
                            "New event: URI=%s; order=%d",
                            trackedResourceURI,
                            eventOrder));
                    // update change log
                    try {
                        providerHandler.processChangeEvent(changeEvent);
                    } catch (IOException e) {
                        log.warn("Error processing event", e);
                    }
                }
            };
            executorService.submit(handleChangeEvent);
        }
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
    }

}

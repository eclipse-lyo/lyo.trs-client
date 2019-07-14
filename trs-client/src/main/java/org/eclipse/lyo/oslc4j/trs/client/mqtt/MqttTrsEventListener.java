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

package org.eclipse.lyo.oslc4j.trs.client.mqtt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.eclipse.lyo.core.trs.ChangeEvent;
import org.eclipse.lyo.core.trs.Creation;
import org.eclipse.lyo.core.trs.Deletion;
import org.eclipse.lyo.core.trs.Modification;
import org.eclipse.lyo.oslc4j.provider.jena.JenaModelHelper;
import org.eclipse.lyo.oslc4j.provider.jena.LyoJenaModelException;
import org.eclipse.lyo.oslc4j.trs.client.config.TrsProviderConfiguration;
import org.eclipse.lyo.oslc4j.trs.client.handlers.TrsProviderHandler;
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
                    try {
                        // read data from message
                        if (payload.startsWith("<ModelCom")) {
                            // TODO delete before the release, ppl should not see these horrors
                            ChangeEvent changeEvent = unmarshalChangeEventDirty(payload);
                            providerHandler.processChangeEvent(changeEvent);
                        } else {
                            final ChangeEventMessage eventMessage = unmarshalChangeEvent(payload);
                            if (!eventMessage.isFat()) {
                                // business as usual
                                providerHandler.processChangeEvent(eventMessage.getChangeEvent());
                            } else {
                                providerHandler.processFatChangeEvent(eventMessage);
                            }
                        }
                        // update change log
                    } catch (IOException | LyoJenaModelException | URISyntaxException e) {
                        log.warn("Error processing event", e);
                    }
                }
            };
            executorService.submit(handleChangeEvent);
        }
    }

    private ChangeEventMessage unmarshalChangeEvent(final String payload)
            throws LyoJenaModelException {
        log.debug("MQTT payload: {}", payload);
        ChangeEvent changeEvent;
        final Model payloadModel = ModelFactory.createDefaultModel();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(payload.getBytes(
                StandardCharsets.UTF_8));
        RDFDataMgr.read(payloadModel, inputStream, Lang.JSONLD);
        try {
            changeEvent = JenaModelHelper.unmarshalSingle(payloadModel, Modification.class);
        } catch (LyoJenaModelException | IllegalArgumentException e) {
            try {
                changeEvent = JenaModelHelper.unmarshalSingle(payloadModel, Creation.class);
            } catch (LyoJenaModelException | IllegalArgumentException e1) {
                try {
                    changeEvent = JenaModelHelper.unmarshalSingle(payloadModel, Deletion.class);
                } catch (LyoJenaModelException | IllegalArgumentException e2) {
                    log.error("Can't unmarshal the payload", e, e1, e2);
                    throw e2;
                }
            }
        }

        final URI tResourceUri = changeEvent.getChanged();
        return new ChangeEventMessage(payloadModel,
                                      changeEvent,
                                      payloadModel.containsResource(r(tResourceUri)));
    }

    /**
     * Dummy Jena Resource for a URI. Can be to do raw ops on a model.
     */
    private Resource r(final URI tResourceUri) {
        return ResourceFactory.createResource(tResourceUri.toString());
    }

    private ChangeEvent unmarshalChangeEventDirty(final String payload) {
        ChangeEvent changeEvent = null;
        String message = payload.substring(payload.indexOf("{") + 1, payload.indexOf("}"));
        String[] mapper = message.split(" ");
        int index_order = Arrays.asList(mapper).indexOf("@trs:order");
        int index_changed = Arrays.asList(mapper).indexOf("@trs:changed");
        int index_type = Arrays.asList(mapper).indexOf("@rdf:type");
        String about = mapper[0];
        String[] changed = mapper[index_changed + 1].split(";"); // changed[0]
        String[] order = mapper[index_order + 1].split("\""); // order[1]
        String type = mapper[index_type + 1];
        // create change event
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
        log.info(String.format("New event: URI=%s; order=%d", trackedResourceURI, eventOrder));
        return changeEvent;
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
    }

}

/*
 * Copyright (c) 2018 Andrew Berezovskyi.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the Eclipse Distribution
 * License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 * Andrew Berezovskyi    -  Initial implementation
 */

package org.eclipse.lyo.trs.consumer.config;

import org.eclipse.paho.client.mqttv3.MqttClient;

/**
 * Created on 2018-02-27
 *
 * @author Andrew Berezovskyi (andriib@kth.se)
 * @version $version-stub$
 * @since 0.0.1
 */
public class TrsProviderConfiguration {
    private final String trsUri;
    private final String basicAuthUsername;
    private final String basicAuthPassword;
    private final String mqttBroker;
    private final String mqttTopic;
    private final MqttClient mqttClient;

    public TrsProviderConfiguration(final String trsUri, final String basicAuthUsername, final String basicAuthPassword,
            @Deprecated final String mqttBroker, final String mqttTopic,
            final MqttClient mqttClient) {
        this.trsUri = trsUri;

        this.basicAuthUsername = basicAuthUsername;
        this.basicAuthPassword = basicAuthPassword;
        this.mqttBroker = mqttBroker;
        this.mqttTopic = mqttTopic;
        this.mqttClient = mqttClient;
    }

    public static TrsProviderConfiguration forHttp(final String trsEndpointUri) {
        return new TrsProviderConfiguration(trsEndpointUri, null, null, null, null, null);
    }

    public static TrsProviderConfiguration forHttpWithBasicAuth(final String trsEndpointUri,
            final String trsEndpointUsername, final String trsEndpointPassword) {
        return new TrsProviderConfiguration(trsEndpointUri, trsEndpointUsername,
                                            trsEndpointPassword, null, null, null
        );
    }

    public static TrsProviderConfiguration forMQTT(MqttClient client, String topic) {
        return new TrsProviderConfiguration(null, null, null, null, topic, client);
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public String getTrsUri() {
        return trsUri;
    }

    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }

    public String getBasicAuthPassword() {
        return basicAuthPassword;
    }

    @Deprecated
    public String getMqttBroker() {
        return mqttBroker;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }
}

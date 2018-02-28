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

    public TrsProviderConfiguration(final String trsUri, final String basicAuthUsername, final String basicAuthPassword,
            final String mqttBroker, final String mqttTopic) {
        this.trsUri = trsUri;
        this.basicAuthUsername = basicAuthUsername;
        this.basicAuthPassword = basicAuthPassword;
        this.mqttBroker = mqttBroker;
        this.mqttTopic = mqttTopic;
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

    public String getMqttBroker() {
        return mqttBroker;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }
}

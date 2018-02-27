/*
 * Copyright (c) 2016-2018 Andrew Berezovskyi.
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

package org.eclipse.lyo.oslc4j.trs.consumer.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import jdk.internal.joptsimple.internal.Strings;

/**
 * Created on 2018-02-27
 *
 * @author Andrew Berezovskyi (andriib@kth.se)
 * @version $version-stub$
 * @since 0.0.1
 */
public class TrsConfigurationLoader {
    public static TrsProviderConfiguration from(File f) throws IOException {
        if (f == null) {
            throw new IllegalArgumentException("File is null");
        }

        final InputStream input = new BufferedInputStream(new FileInputStream(f));
        Properties p = new Properties();
        p.load(input);

        String uri = p.getProperty("trs_uri");
        if (Strings.isNullOrEmpty(uri)) {
            throw new IllegalStateException("The 'trs_uri' field is missing in file " + f.getName());
        }

        String user = p.getProperty("baseAuth_user");
        String pass = p.getProperty("baseAuth_pwd");

        String broker = p.getProperty("mqtt_broker");
        String topic = p.getProperty("mqtt_topic");

        return new TrsProviderConfiguration(uri, user, pass, broker, topic);
    }
}

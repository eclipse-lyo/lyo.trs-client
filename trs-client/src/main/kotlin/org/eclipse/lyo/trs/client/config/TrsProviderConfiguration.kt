/*
 * Copyright (c) 2019 KTH Royal Institute of Technology and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.eclipse.lyo.trs.client.config

import java.net.URI

class TrsProviderConfiguration(val trsUri: URI, val basicAuthUsername: String?,
                               val basicAuthPassword: String?) {
    companion object {
        fun forHttp(trsEndpointUri: String): TrsProviderConfiguration {
            return TrsProviderConfiguration(URI.create(trsEndpointUri), null, null)
        }

        fun forHttpWithBasicAuth(trsEndpointUri: String, trsEndpointUsername: String,
                                 trsEndpointPassword: String): TrsProviderConfiguration {
            return TrsProviderConfiguration(URI.create(trsEndpointUri), trsEndpointUsername,
                    trsEndpointPassword)
        }
    }
}

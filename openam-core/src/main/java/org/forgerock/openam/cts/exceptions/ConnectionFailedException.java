/**
 * Copyright 2013 ForgeRock, Inc.
 *
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 */
package org.forgerock.openam.cts.exceptions;

import org.forgerock.openam.cts.api.CoreTokenConstants;

/**
 * Describes an error fetching a connection, or any operation that uses a connection that failed.
 *
 * @author robert.wapshott@forgerock.com
 */
public class ConnectionFailedException extends CoreTokenException {

    public ConnectionFailedException(Throwable cause) {
        super("\n" +
              CoreTokenConstants.DEBUG_HEADER +
              "Failed to get a connection", cause);
    }

    public ConnectionFailedException(String message) {
        super(message);
    }
}

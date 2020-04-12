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
import org.forgerock.openam.cts.impl.query.QueryBuilder;

import java.text.MessageFormat;

/**
 * Represents a failure to delete a Token from the Core Token Service.
 *
 * @author robert.wapshott@forgerock.com
 */
public class DeleteFailedException extends CoreTokenException {
    public DeleteFailedException(String tokenId, Throwable e) {
        super(MessageFormat.format(
                "\n" +
                CoreTokenConstants.DEBUG_HEADER +
                "Failed to delete DN: {0}",
                tokenId),
                e);
    }

    public DeleteFailedException(QueryBuilder queryBuilder, Throwable e) {
        super(MessageFormat.format(
                "\n" +
                CoreTokenConstants.DEBUG_HEADER +
                "Failed to delete based on query:\n" +
                "{0}",
                queryBuilder.toString()),
                e);
    }
}

/*
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
 *
 * Copyright 2013-2014 ForgeRock AS
 */
package org.forgerock.openam.cts.impl;

import com.google.inject.name.Named;
import com.sun.identity.common.configuration.ConfigurationObserver;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.openam.cts.CoreTokenConfigListener;
import org.forgerock.openam.cts.api.CoreTokenConstants;
import org.forgerock.openam.cts.api.tokens.Token;
import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.cts.exceptions.CreateFailedException;
import org.forgerock.openam.cts.exceptions.DeleteFailedException;
import org.forgerock.openam.cts.exceptions.LDAPOperationFailedException;
import org.forgerock.openam.cts.exceptions.SetFailedException;
import org.forgerock.openam.cts.impl.query.QueryBuilder;
import org.forgerock.openam.cts.impl.query.QueryFactory;
import org.forgerock.openam.cts.impl.query.QueryFilter;
import org.forgerock.openam.utils.IOUtils;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.responses.Result;

import javax.inject.Inject;
import java.text.MessageFormat;

/**
 * Primary interface to LDAP which provides a CRUDL style interface.
 *
 * Note: This class uses Token as its main means of adding data to and from LDAP and therefore is intended
 * for use by the Core Token Service.
 */
public class CoreTokenAdapter {
    // Injected
    private final ConnectionFactory connectionFactory;
    private final QueryFactory queryFactory;
    private final LDAPAdapter ldapAdapter;
    private final Debug debug;

    /**
     * Create a new instance of the CoreTokenAdapter with dependencies.
     * @param connectionFactory Required for connections to LDAP.
     * @param queryFactory Required for query instances.
     * @param ldapAdapter Required for all LDAP operations.
     * @param observer Required for configuration change notifications.
     * @param listener Required for configuration change notifications.
     * @param debug Required for debug logging
     */
    @Inject
    public CoreTokenAdapter(ConnectionFactory connectionFactory, QueryFactory queryFactory,
                            LDAPAdapter ldapAdapter, ConfigurationObserver observer,
                            CoreTokenConfigListener listener, @Named(CoreTokenConstants.CTS_DEBUG) Debug debug) {
        this.connectionFactory = connectionFactory;
        this.queryFactory = queryFactory;
        this.debug = debug;
        this.ldapAdapter = ldapAdapter;

        // Register the listener to respond to configuration changes which will trigger an update to the connection
        observer.addListener(listener);
    }

    /**
     * Create a token in the persistent store.
     *
     * @param token Token to create.
     * @throws CoreTokenException If the Token exists already of there was
     * an error as a result of this operation.
     */
    public void create(Token token) throws CoreTokenException {
        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            ldapAdapter.create(connection, token);

            if (debug.messageEnabled()) {
                debug.message(MessageFormat.format(
                        CoreTokenConstants.DEBUG_HEADER +
                        "Create: Created {0} Token {1}\n" +
                        "{2}",
                        token.getType(),
                        token.getTokenId(),
                        token));
            }

        } catch (ErrorResultException e) {
            throw new CreateFailedException(token, e);
        } finally {
            IOUtils.closeIfNotNull(connection);
        }
    }

    /**
     * Read the Token based on its Token ID.
     *
     * @param tokenId The non null Token ID to read from the Token store.
     * @return Null if the Token could not be found, otherwise a non null Token.
     * @throws CoreTokenException If there was an unexpected problem with the request.
     */
    public Token read(String tokenId) throws CoreTokenException {
        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            Token token = ldapAdapter.read(connection, tokenId);

            if (token == null) {
                if (debug.messageEnabled()) {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Read: {0} not found.",
                            tokenId));
                }
                return null;
            }

            if (debug.messageEnabled()) {
                debug.message(MessageFormat.format(
                        CoreTokenConstants.DEBUG_HEADER +
                        "Read: {0} successfully.",
                        tokenId));
            }

            return token;
        } catch (ErrorResultException e) {
            Result result = e.getResult();
            throw new LDAPOperationFailedException(result);
        } finally {
            IOUtils.closeIfNotNull(connection);
        }
    }

    /**
     * Start the query process.
     *
     * The API of the QueryBuilder will guide the caller through the available query options.
     *
     * @return A new QueryBuilder instance.
     */
    public QueryBuilder query() {
        return queryFactory.createInstance();
    }

    public QueryFilter buildFilter() {
        return queryFactory.createFilter();
    }

    /**
     * Update a Token in the LDAP store.
     *
     * This function will perform a read of the Token ID to determine if the Token has been
     * persisted already. If it has not been persisted, then delegates to the create function.
     *
     * Otherwise performs Modify operation based on the difference between the Token in
     * the store and the Token being stored.
     *
     * If this difference has no changes, then there is nothing to be done.
     *
     * @param token Token to update or create.
     * @throws CoreTokenException If there was an error updating the token, or if there were
     * multiple tokens present in the store that have the same id.
     */
    public void updateOrCreate(Token token) throws CoreTokenException {

        Connection connection = null;

        try {
            connection = connectionFactory.getConnection();

            // Start off by fetching the previous entry.
            Token previous = ldapAdapter.read(connection, token.getTokenId());

            if (debug.messageEnabled()) {
                if (previous == null) {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Read: {0} not found.",
                            token.getTokenId()));
                } else {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Read: {0} successfully.",
                            token.getTokenId()));
                }
            }

            // Handle create case
            if (previous == null) {
                ldapAdapter.create(connection, token);

                if (debug.messageEnabled()) {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Create: Created {0} Token {1}\n" +
                            "{2}",
                            token.getType(),
                            token.getTokenId(),
                            token));
                }

                return;
            }

            boolean updateResult = ldapAdapter.update(connection, previous, token);

            if (updateResult) {
                if (debug.messageEnabled()) {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Update: no modifications for Token {0}",
                            token.getTokenId()));
                }
            } else {
                if (debug.messageEnabled()) {
                    debug.message(MessageFormat.format(
                            CoreTokenConstants.DEBUG_HEADER +
                            "Update: Token {0} changed.\n" +
                            "Previous:\n" +
                            "{1}\n" +
                            "Current:\n" +
                            "{2}",
                            token.getTokenId(),
                            previous,
                            token));
                }
            }
        } catch (ErrorResultException e) {
            throw new SetFailedException(token, e);
        } finally {
            IOUtils.closeIfNotNull(connection);
        }
    }

    /**
     * Deletes a token from the store based on its token id.
     * @param tokenId Non null token id.
     * @throws DeleteFailedException If there was an error while trying to remove the token with the given Id.
     */
    public void delete(String tokenId) throws DeleteFailedException {
        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            ldapAdapter.delete(connection, tokenId);

            if (debug.messageEnabled()) {
                debug.message(MessageFormat.format(
                        CoreTokenConstants.DEBUG_HEADER +
                        "Delete: Deleted DN {0}",
                        tokenId));
            }

        } catch (ErrorResultException e) {
            throw new DeleteFailedException(tokenId, e);
        } catch (LDAPOperationFailedException e) {
            throw new DeleteFailedException(tokenId, e);
        } finally {
            IOUtils.closeIfNotNull(connection);
        }
    }
}

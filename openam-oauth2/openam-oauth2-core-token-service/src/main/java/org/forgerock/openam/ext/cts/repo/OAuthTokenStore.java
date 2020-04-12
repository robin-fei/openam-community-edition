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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.openam.ext.cts.repo;

import org.forgerock.openam.cts.CTSPersistentStore;
import org.forgerock.openam.cts.adapters.TokenAdapter;
import org.forgerock.openam.cts.api.fields.OAuthTokenField;
import org.forgerock.openam.cts.api.tokens.Token;
import org.forgerock.openam.cts.api.tokens.TokenIdFactory;
import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.cts.exceptions.DeleteFailedException;
import org.forgerock.openam.cts.impl.query.QueryFilter;
import org.forgerock.openam.cts.utils.LDAPDataConversion;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Filter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the OAuthTokenStore interface that uses the CoreTokenService for storing the tokens as JSON
 * objects.
 */
@Singleton
public class OAuthTokenStore {

    private final CTSPersistentStore cts;
    private final TokenAdapter<JsonValue> tokenAdapter;
    private final TokenIdFactory tokenIdFactory;

    private static final LDAPDataConversion conversion = new LDAPDataConversion();
    private static final QueryFilter queryFilter = new QueryFilter(conversion);

    /**
     * Constructs a new OAuthTokenStore instance.
     *
     * @param cts An instance of the CTSPersistentStore.
     * @param tokenAdapter An instance of a TokenAdapter.
     * @param tokenIdFactory An instance of the TokenIdFactory.
     */
    @Inject
    public OAuthTokenStore(CTSPersistentStore cts, TokenAdapter<JsonValue> tokenAdapter,
            TokenIdFactory tokenIdFactory) {
        this.cts = cts;
        this.tokenAdapter = tokenAdapter;
        this.tokenIdFactory = tokenIdFactory;
    }

    public void create(JsonValue token) throws CoreTokenException {
        cts.create(tokenAdapter.toToken(token));
    }

    public JsonValue read(String id) throws CoreTokenException {
        Token token = cts.read(tokenIdFactory.getOAuthTokenId(id));
        //The CTS will not throw exception, but return null when read does not return a value
        if (token == null) {
            return null;
        }
        return tokenAdapter.fromToken(token);
    }

    public void update(JsonValue token) throws CoreTokenException {
        cts.update(tokenAdapter.toToken(token));
    }

    public void delete(String id) throws DeleteFailedException {
        cts.delete(id);
    }

    public JsonValue query(Map<String, Object> queryParameters) throws CoreTokenException {
        return query(queryParameters, false);
    }

    public JsonValue query(Map<String, Object> queryParameters, boolean and) throws CoreTokenException {
        Collection<Token> tokens = cts.list(convertRequest(queryParameters, and));
        return convertResults(tokens);
    }

    /**
     * Converts the Map of filter parameters into an LDAP filter.
     *
     * @param filters A Map of filter parameters.
     * @param and Whether all the filter assertions needs to be satisfied.
     * @return A Mapping of CoreTokenField to Objects to query by.
     */
    private Filter convertRequest(Map<String, Object> filters, boolean and) {
        QueryFilter.QueryFilterBuilder builder;
        if (and) {
            builder = queryFilter.and();
        } else {
            builder = queryFilter.or();
        }
        for (OAuthTokenField field : OAuthTokenField.values()) {
            if (filters.containsKey(field.getOAuthField())) {
                builder.attribute(field.getField(), filters.get(field.getOAuthField()));
            }
        }

        return builder.build();
    }

    /**
     * Internal conversion function to handle the CTSPersistentStore query result.
     *
     * @param tokens A non null, but possibly empty collection of tokens.
     * @return The JsonValue expected by the caller.
     */
    private JsonValue convertResults(Collection<Token> tokens) {
        Set<Map<String, Object>> results = new HashSet<Map<String, Object>>();

        for (Token token : tokens) {
            results.add(convertToken(token));
        }

        return new JsonValue(results);
    }

    /**
     * Internal conversion function.
     *
     * @param token The token to convert.
     * @return A Token in String to Set of Strings representation.
     */
    private Map<String, Object> convertToken(Token token) {
        if (token == null){
            return null;
        }
        return tokenAdapter.fromToken(token).asMap();
    }
}

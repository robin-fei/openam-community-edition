/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of company]"
 */
package org.forgerock.restlet.ext.oauth2.flow;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.fest.assertions.Condition;
import org.fest.assertions.MapAssert;
import com.sun.identity.shared.OAuth2Constants;
import org.forgerock.openam.oauth2.model.CoreToken;
import org.forgerock.openam.oauth2.utils.OAuth2Utils;
import org.forgerock.restlet.ext.oauth2.consumer.BearerOAuth2Proxy;
import org.forgerock.openam.oauth2.model.BearerToken;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.testng.annotations.Test;

/**
 * @author $author$
 * @version $Revision$ $Date$
 */
public class RefreshTokenServerResourceTest extends AbstractFlowTest {
    @Test
    public void testValidRequest() throws Exception {

        Reference reference = new Reference("riap://component/test/oauth2/access_token");
        Request request = new Request(Method.POST, reference);
        Response response = new Response(request);

        CoreToken refreshToken =
                realm.getTokenStore().createRefreshToken(OAuth2Utils.split("read write", null),
                        "test", "admin", "cid", null, OAuth2Constants.Params.REFRESH_TOKEN);

        Form parameters = new Form();
        parameters.add(OAuth2Constants.Params.GRANT_TYPE, OAuth2Constants.Params.REFRESH_TOKEN);
        parameters.add(OAuth2Constants.Params.REFRESH_TOKEN, refreshToken.getTokenID());
        parameters.add(OAuth2Constants.Params.SCOPE, OAuth2Utils.join(refreshToken.getScope(), ""));
        parameters.add(OAuth2Constants.Params.STATE, "random");
        request.setEntity(parameters.getWebRepresentation());

        // handle
        getClient().handle(request, response);
        assertTrue(MediaType.APPLICATION_JSON.equals(response.getEntity().getMediaType()));
        JacksonRepresentation<Map> representation =
                new JacksonRepresentation<Map>(response.getEntity(), Map.class);

        // assert
        assertThat(representation.getObject()).includes(
                MapAssert.entry(OAuth2Constants.Params.TOKEN_TYPE, OAuth2Constants.Bearer.BEARER),
                MapAssert.entry(OAuth2Constants.Params.EXPIRES_IN, 3600)).is(new Condition<Map<?, ?>>() {
            @Override
            public boolean matches(Map<?, ?> value) {
                return value.containsKey(OAuth2Constants.Params.ACCESS_TOKEN)
                        && value.containsKey(OAuth2Constants.Params.REFRESH_TOKEN);
            }
        });
    }

    @Test
    public void testProxy() throws Exception {
        BearerOAuth2Proxy auth2Proxy = BearerOAuth2Proxy.popOAuth2Proxy(component.getContext());
        assertNotNull(auth2Proxy);
        CoreToken refreshToken =
                realm.getTokenStore().createRefreshToken(OAuth2Utils.split("read write", null),
                        "test", "admin", "cid", null, OAuth2Constants.Params.REFRESH_TOKEN);
        BearerToken token = auth2Proxy.flowRefreshToken(refreshToken.getTokenID());
        assertNotNull(token);
    }
}

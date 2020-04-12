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
 * Copyright 2013 ForgeRock Inc.
 */

package org.forgerock.openam.jaspi.modules.session;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenID;
import org.forgerock.openam.auth.shared.AuthUtilsWrapper;
import org.forgerock.openam.auth.shared.AuthnRequestUtils;
import org.forgerock.openam.auth.shared.SSOTokenFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author phill.cunnington@forgerock.com
 * @author robert.wapshott@forgerock.com
 */
public class LocalSSOTokenSessionModuleTest {

    private LocalSSOTokenSessionModule localSSOTokenSessionModule;
    private AuthnRequestUtils mockUtils;
    private SSOTokenFactory mockFactory;
    private AuthUtilsWrapper authUtilsWrapper;

    @BeforeMethod
    public void setUpMethod() {
        mockUtils = mock(AuthnRequestUtils.class);
        mockFactory = mock(SSOTokenFactory.class);
        authUtilsWrapper = mock(AuthUtilsWrapper.class);

        localSSOTokenSessionModule = new LocalSSOTokenSessionModule(mockUtils, mockFactory, authUtilsWrapper);
    }

    @Test
    public void shouldGetSupportedMessageTypes() {
        //Given

        //When
        Class[] supportedMessageTypes = localSSOTokenSessionModule.getSupportedMessageTypes();

        //Then
        assertEquals(supportedMessageTypes.length, 2);
        assertEquals(supportedMessageTypes[0], HttpServletRequest.class);
        assertEquals(supportedMessageTypes[1], HttpServletResponse.class);
    }

    @Test
    public void shouldValidateRequestWithCookiesNull() throws AuthException {

        //Given
        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);

        given(messageInfo.getRequestMessage()).willReturn(request);

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SEND_FAILURE);
    }

    @Test
    public void shouldValidateRequestWithCookiesEmpty() throws AuthException {

        //Given
        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);

        given(messageInfo.getRequestMessage()).willReturn(request);
        given(request.getCookies()).willReturn(new Cookie[0]);

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SEND_FAILURE);
    }

    @Test
    public void shouldValidateRequestWithCookiesNoSSOToken() throws AuthException {

        //Given
        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);

        given(messageInfo.getRequestMessage()).willReturn(request);
        given(request.getCookies()).willReturn(new Cookie[]{new Cookie("2", "2"), new Cookie("1", "1")});

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SEND_FAILURE);
    }

    @Test
    public void shouldValidateRequestWithCookiesIncludingInvalidSSOToken() throws SSOException, AuthException {

        //Given
        String tokenName = "SSO_TOKEN_ID";

        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);

        given(messageInfo.getRequestMessage()).willReturn(request);
        given(request.getCookies()).willReturn(new Cookie[]{new Cookie("2", "2"),
                new Cookie(AuthnRequestUtils.SSOTOKEN_COOKIE_NAME, tokenName), new Cookie("1", "1")});
        given(mockUtils.getTokenId(eq(request))).willReturn(tokenName);
        given(mockFactory.getTokenFromId(anyString())).willReturn(null);

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SEND_FAILURE);
    }

    @Test
    public void shouldValidateRequestWithCookiesIncludingValidSSOToken() throws SSOException, AuthException {

        //Given
        String tokenName = "SSO_TOKEN_ID";

        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);
        SSOToken ssoToken = mock(SSOToken.class);
        SSOTokenID ssoTokenID = mock(SSOTokenID.class);

        Principal principal = mock(Principal.class);
        Map<String, Object> map = new HashMap<String, Object>();
        Map<String, Object> context = new HashMap<String, Object>();
        given(ssoToken.getTokenID()).willReturn(ssoTokenID);
        given(ssoTokenID.toString()).willReturn(tokenName);
        given(ssoToken.getPrincipal()).willReturn(principal);
        given(principal.getName()).willReturn("PRINCIPAL");
        given(ssoToken.getAuthLevel()).willReturn(23);

        given(messageInfo.getRequestMessage()).willReturn(request);
        map.put("org.forgerock.authentication.context", context);
        given(messageInfo.getMap()).willReturn(map);

        given(request.getCookies()).willReturn(new Cookie[]{new Cookie("2", "2"),
                new Cookie(AuthnRequestUtils.SSOTOKEN_COOKIE_NAME, tokenName), new Cookie("1", "1")});
        given(mockUtils.getTokenId(eq(request))).willReturn(tokenName);
        given(mockFactory.getTokenFromId(eq(tokenName))).willReturn(ssoToken);

        CallbackHandler handler = mock(CallbackHandler.class);
        localSSOTokenSessionModule.initialize(null, null, handler, null);

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SUCCESS);
        assertEquals(context.size(), 2);
        assertEquals(context.get("authLevel"), 23);
        assertEquals(context.get("tokenId"), tokenName);
        assertTrue(clientSubject.getPrincipals().contains(principal));
    }

    @Test
    public void shouldValidateRequestWithApplicationRequesterToken() throws AuthException, SSOException {

        //Given
        String tokenName = "SSO_TOKEN_ID";

        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject clientSubject = new Subject();
        Subject serviceSubject = new Subject();
        HttpServletRequest request = mock(HttpServletRequest.class);
        SSOToken ssoToken = mock(SSOToken.class);
        SSOTokenID ssoTokenID = mock(SSOTokenID.class);

        Principal principal = mock(Principal.class);
        Map<String, Object> map = new HashMap<String, Object>();
        Map<String, Object> context = new HashMap<String, Object>();
        given(ssoToken.getTokenID()).willReturn(ssoTokenID);
        given(ssoTokenID.toString()).willReturn(tokenName);
        given(ssoToken.getPrincipal()).willReturn(principal);
        given(principal.getName()).willReturn("PRINCIPAL");
        given(ssoToken.getAuthLevel()).willReturn(23);

        given(messageInfo.getRequestMessage()).willReturn(request);
        map.put("org.forgerock.authentication.context", context);
        given(messageInfo.getMap()).willReturn(map);

        given(messageInfo.getRequestMessage()).willReturn(request);
        given(request.getParameter("requester")).willReturn("APPLICATION_TOKEN_ID");

        given(mockFactory.getTokenFromId("APPLICATION_TOKEN_ID")).willReturn(ssoToken);
        given((mockFactory.isTokenValid(ssoToken))).willReturn(true);

        given(request.getCookies()).willReturn(new Cookie[]{new Cookie("2", "2"),
                new Cookie(AuthnRequestUtils.SSOTOKEN_COOKIE_NAME, tokenName), new Cookie("1", "1")});
        given(mockUtils.getTokenId(eq(request))).willReturn(tokenName);
        given(mockFactory.getTokenFromId(eq(tokenName))).willReturn(ssoToken);

        CallbackHandler handler = mock(CallbackHandler.class);
        localSSOTokenSessionModule.initialize(null, null, handler, null);

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.validateRequest(messageInfo, clientSubject, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SUCCESS);
        assertEquals(context.size(), 2);
        assertEquals(context.get("authLevel"), 23);
        assertEquals(context.get("tokenId"), tokenName);
        assertTrue(clientSubject.getPrincipals().contains(principal));
    }

    @Test
    public void shouldSecureResponse() {

        //Given
        MessageInfo messageInfo = mock(MessageInfo.class);
        Subject serviceSubject = new Subject();

        //When
        AuthStatus authStatus = localSSOTokenSessionModule.secureResponse(messageInfo, serviceSubject);

        //Then
        assertEquals(authStatus, AuthStatus.SEND_SUCCESS);
    }
}

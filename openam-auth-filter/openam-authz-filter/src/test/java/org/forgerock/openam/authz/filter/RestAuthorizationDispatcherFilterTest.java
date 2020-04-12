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
 * Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.openam.authz.filter;

import org.forgerock.auth.common.AuditLogger;
import org.forgerock.auth.common.DebugLogger;
import org.forgerock.authz.AuthZFilter;
import org.forgerock.authz.AuthorizationConfigurator;
import org.forgerock.authz.AuthorizationFilter;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.openam.forgerockrest.RestDispatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;

public class RestAuthorizationDispatcherFilterTest {

    private RestAuthorizationDispatcherFilter restAuthorizationDispatcherFilter;
    private RestDispatcher restDispatcher;
    private AuthZFilter authZFilter;
    private static final Map<String, String> INIT_PARAMS;

    static {
        Map<String, String> initParams = new HashMap<String, String>();
        initParams.put("realmsAuthzConfigurator", AdminAuthzClass.class.getName());
        initParams.put("usersAuthzConfigurator", PassthroughAuthzClass.class.getName());
        initParams.put("groupsAuthzConfigurator", PassthroughAuthzClass.class.getName());
        initParams.put("agentsAuthzConfigurator", PassthroughAuthzClass.class.getName());
        initParams.put("serverInfoAuthzConfigurator", PassthroughAuthzClass.class.getName());
        initParams.put("sessionAuthzConfigurator", SessionResourceAuthZClass.class.getName());
        INIT_PARAMS = Collections.unmodifiableMap(initParams);
    }

    @BeforeMethod()
    public void setUpMocks() {
        authZFilter = mock(AuthZFilter.class);
        restDispatcher = mock(RestDispatcher.class);
        restAuthorizationDispatcherFilter = new RestAuthorizationDispatcherFilter(restDispatcher, authZFilter);
    }

    private void initFilter(Map<String, String> initParams) throws Exception {
        FilterConfig filterConfig = mock(FilterConfig.class);
        for (Map.Entry<String, String> entry : initParams.entrySet()) {
            given(filterConfig.getInitParameter(entry.getKey())).willReturn(entry.getValue());
        }
        restAuthorizationDispatcherFilter.init(filterConfig);

    }

    @DataProvider(name = "configurator")
    public String[][] getParameters() {
        return new String[][] {
                {"realmsAuthzConfigurator"},
                {"usersAuthzConfigurator"},
                {"groupsAuthzConfigurator"},
                {"agentsAuthzConfigurator"},
                {"serverInfoAuthzConfigurator"},
                {"sessionAuthzConfigurator"}
        };
    }

    @Test(dataProvider = "configurator", expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionWhenAnAuthZConfiguratorIsNotSet(String missing) throws Exception {
        Map<String, String> alteredInitParams = new HashMap<String, String>(INIT_PARAMS);
        alteredInitParams.remove(missing);
        initFilter(alteredInitParams);
    }

    @Test(expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionIfRequestIsNotHttpServletRequest() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSkipAuthorizationIfEndpointNotFound() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(restDispatcher.getRequestDetails(anyString())).willThrow(NotFoundException.class);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        verify(authZFilter, never()).init(Matchers.<FilterConfig>anyObject());
        verify(authZFilter, never()).doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void shouldFilterAuthorizationForRealms() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/realms");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"), AdminAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForUsers() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/users");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"),
                PassthroughAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForGroups() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/groups");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"),
                PassthroughAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForAgents() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/agents");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"),
                PassthroughAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForServerInfo() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/serverinfo");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"),
                PassthroughAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForSessions() throws Exception {
        //Given
        initFilter(INIT_PARAMS);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        Map<String, String> details = new HashMap<String, String>();
        details.put("resourceName", "/sessions");
        given(restDispatcher.getRequestDetails(anyString())).willReturn(details);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("configurator"),
                SessionResourceAuthZClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldDestroyFilter() {
        //Given

        //When
        restAuthorizationDispatcherFilter.destroy();

        //Then
    }

    private static final class AdminAuthzClass implements AuthorizationConfigurator {

        public AuthorizationFilter getAuthorizationFilter() {
            return null;
        }

        public DebugLogger getDebugLogger() {
            return null;
        }

        public AuditLogger getAuditLogger() {
            return null;
        }
    }

    private static final class PassthroughAuthzClass implements AuthorizationConfigurator {

        public AuthorizationFilter getAuthorizationFilter() {
            return null;
        }

        public DebugLogger getDebugLogger() {
            return null;
        }

        public AuditLogger getAuditLogger() {
            return null;
        }
    }

    private static final class SessionResourceAuthZClass implements AuthorizationConfigurator {

        public AuthorizationFilter getAuthorizationFilter() {
            return null;
        }

        public DebugLogger getDebugLogger() {
            return null;
        }

        public AuditLogger getAuditLogger() {
            return null;
        }
    }
}

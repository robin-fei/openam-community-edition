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

package org.forgerock.openam.forgerockrest.authn;

import com.iplanet.sso.SSOToken;
import com.sun.identity.authentication.spi.AuthLoginException;
import com.sun.identity.authentication.spi.PagePropertiesCallback;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.shared.locale.L10NMessageImpl;
import org.forgerock.json.fluent.JsonException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openam.forgerockrest.authn.callbackhandlers.RestAuthCallbackHandlerResponseException;
import org.forgerock.openam.forgerockrest.authn.core.AuthIndexType;
import org.forgerock.openam.forgerockrest.authn.core.AuthenticationContext;
import org.forgerock.openam.forgerockrest.authn.core.LoginAuthenticator;
import org.forgerock.openam.forgerockrest.authn.core.LoginConfiguration;
import org.forgerock.openam.forgerockrest.authn.core.LoginProcess;
import org.forgerock.openam.forgerockrest.authn.exceptions.RestAuthErrorCodeException;
import org.forgerock.openam.forgerockrest.authn.exceptions.RestAuthException;
import org.forgerock.openam.utils.JsonObject;
import org.forgerock.openam.utils.JsonValueBuilder;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SignatureException;

/**
 * Handles the initial authenticate and subsequent callback submit RESTful calls.
 */
public class RestAuthenticationHandler {

    private static final Debug DEBUG = Debug.getInstance("amAuthREST");

    private final LoginAuthenticator loginAuthenticator;
    private final RestAuthCallbackHandlerManager restAuthCallbackHandlerManager;
    private final AMAuthErrorCodeResponseStatusMapping amAuthErrorCodeResponseStatusMapping;
    private final AuthIdHelper authIdHelper;

    /**
     * Constructs an instance of the RestAuthenticationHandler.
     *
     * @param loginAuthenticator An instance of the LoginAuthenticator.
     * @param restAuthCallbackHandlerManager An instance of the RestAuthCallbackHandlerManager.
     * @param amAuthErrorCodeResponseStatusMapping An instance of the AMAuthErrorCodeResponseStatusMapping.
     * @param authIdHelper An instance of the AuthIdHelper.
     */
    @Inject
    public RestAuthenticationHandler(LoginAuthenticator loginAuthenticator,
            RestAuthCallbackHandlerManager restAuthCallbackHandlerManager,
            AMAuthErrorCodeResponseStatusMapping amAuthErrorCodeResponseStatusMapping, AuthIdHelper authIdHelper) {
        this.loginAuthenticator = loginAuthenticator;
        this.restAuthCallbackHandlerManager = restAuthCallbackHandlerManager;
        this.amAuthErrorCodeResponseStatusMapping = amAuthErrorCodeResponseStatusMapping;
        this.authIdHelper = authIdHelper;
    }

    /**
     * Handles authentication requests from HTTP both GET and POST. Will then either create the Login
     * Process, as the request will be a new authentication request.
     *
     * @param headers The HttpHeaders of the request.
     * @param request The HttpServletRequest.
     * @param response The HttpServletResponse.
     * @param authIndexType The authentication index type.
     * @param indexValue The authentication index value.
     * @param sessionUpgradeSSOTokenId The SSO Token Id of the user's current session, null if not performing a session
     *                                 upgrade.
     * @return The Response of the authentication request.
     */
    public Response initiateAuthentication(HttpHeaders headers, HttpServletRequest request,
            HttpServletResponse response, String authIndexType, String indexValue, String sessionUpgradeSSOTokenId) {
        return authenticate(headers, request, response, null, authIndexType, indexValue, sessionUpgradeSSOTokenId);
    }

    /**
     * Handles authentication requests from HTTP POST. Will then either create or retrieve the Login Process,
     * dependent on if the request is a new authentication request or a continuation of one.
     *
     * @param headers The HttpHeaders of the request.
     * @param request The HttpServletRequest.
     * @param response The HttpServletResponse.
     * @param postBody The JsonValue post body of the request.
     * @param sessionUpgradeSSOTokenId The SSO Token Id of the user's current session, null if not performing a session
     *                                 upgrade.
     * @return The Response of the authentication request.
     */
    public Response continueAuthentication(HttpHeaders headers, HttpServletRequest request,
            HttpServletResponse response, JsonValue postBody, String sessionUpgradeSSOTokenId) {
        return authenticate(headers, request, response, postBody, null, null, sessionUpgradeSSOTokenId);
    }

    /**
     * Handles either the creation or retrieval of the Login Process, dependent on if the request is a new
     * authentication request or a continuation of one.
     *
     * @param headers The HttpHeaders of the request.
     * @param request The HttpServletRequest.
     * @param response The HttpServletResponse.
     * @param postBody The post body of the request.
     * @param authIndexType The authentication index type.
     * @param indexValue The authentication index value.
     * @param sessionUpgradeSSOTokenId The SSO Token Id of the user's current session, null if not performing a session
     *                                 upgrade.
     * @return The Response of the authentication request.
     */
    private Response authenticate(HttpHeaders headers, HttpServletRequest request, HttpServletResponse response,
            JsonValue postBody, String authIndexType, String indexValue, String sessionUpgradeSSOTokenId) {

        Response.ResponseBuilder responseBuilder;
        LoginProcess loginProcess = null;
        try {
            AuthIndexType indexType = getAuthIndexType(authIndexType);

            String authId = null;

            if (postBody != null) {
                authId = postBody.get("authId").asString();
            }

            String sessionId = null;
            if (authId != null) {
                SignedJwt jwt = authIdHelper.reconstructAuthId(authId);
                sessionId = jwt.getClaimsSet().getClaim("sessionId", String.class);
                String authIndexTypeString = jwt.getClaimsSet().getClaim("authIndexType", String.class);
                indexType = getAuthIndexType(authIndexTypeString);
                indexValue = jwt.getClaimsSet().getClaim("authIndexValue", String.class);
                String realmDN = jwt.getClaimsSet().getClaim("realm", String.class);
                authIdHelper.verifyAuthId(realmDN, authId);
            }

            LoginConfiguration loginConfiguration = new LoginConfiguration()
                    .httpRequest(request)
                    .httpResponse(response)
                    .indexType(indexType)
                    .indexValue(indexValue)
                    .sessionId(sessionId)
                    .sessionUpgrade(sessionUpgradeSSOTokenId);

            loginProcess = loginAuthenticator.getLoginProcess(loginConfiguration);

            responseBuilder = processAuthentication(headers, request, response, postBody, authId,
                    loginProcess, loginConfiguration);

        } catch (RestAuthException e) {
            DEBUG.error(e.getMessage());

            if (loginProcess != null) {
                String failureUrl = loginProcess.getAuthContext().getFailureURL();
                return e.getResponse(failureUrl);
            } else {
                return e.getResponse();
            }
        } catch (L10NMessageImpl e) {
            DEBUG.error(e.getMessage(), e);
            return new RestAuthException(amAuthErrorCodeResponseStatusMapping.getAuthLoginExceptionResponseStatus(
                    e.getErrorCode()), e).getResponse();
        } catch (JsonException e)  {
            DEBUG.error(e.getMessage(), e);
            return new RestAuthException(Response.Status.INTERNAL_SERVER_ERROR, e).getResponse();
        } catch (SignatureException e) {
            DEBUG.error(e.getMessage(), e);
            return new RestAuthException(Response.Status.INTERNAL_SERVER_ERROR, e).getResponse();
        } catch (AuthLoginException e) {
            DEBUG.error(e.getMessage(), e);
            return new RestAuthException(amAuthErrorCodeResponseStatusMapping.getAuthLoginExceptionResponseStatus(
                    e.getErrorCode()), e).getResponse();
        } catch (RestAuthCallbackHandlerResponseException e) {
            // Construct a Response object from the exception.
            responseBuilder = Response.status(e.getResponseStatus());
            for (String key : e.getResponseHeaders().keySet()) {
                responseBuilder.header(key, e.getResponseHeaders().get(key));
            }
            responseBuilder.entity(e.getJsonResponse().toString());
        }

        responseBuilder.header("Cache-control", "no-cache");
        responseBuilder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE+"; charset=UTF-8" );
        return responseBuilder.build();
    }

    /**
     * Using the given LoginProcess will process the authentication by getting the required callbacks and either
     * completing and submitting them or sending the requirements back to the client as JSON. If the authentication
     * process has completed it will then check the completion status and will either return an error or the SSO Token
     * Id to the client.
     *
     * @param headers The HttpHeaders of the request.
     * @param request The HttpServletRequest.
     * @param response The HttpServletResponse.
     * @param postBody The post body of the request.
     * @param loginProcess The LoginProcess used to track the login.
     * @param loginConfiguration The LoginConfiguration used to configure the login process.
     * @return A ResponseBuilder which contains the contents of the response to return to the client.
     * @throws RestAuthCallbackHandlerResponseException If there is a problem handling the callbacks.
     * @throws AuthLoginException If there is a problem submitting the callbacks.
     * @throws SignatureException If there is a problem creating the JWT to use in the response to the client.
     */
    private Response.ResponseBuilder processAuthentication(HttpHeaders headers, HttpServletRequest request,
            HttpServletResponse response, JsonValue postBody, String authId,
            LoginProcess loginProcess, LoginConfiguration loginConfiguration)
            throws RestAuthCallbackHandlerResponseException, AuthLoginException, SignatureException {

        Response.ResponseBuilder responseBuilder = null;

        switch (loginProcess.getLoginStage()) {
        case REQUIREMENTS_WAITING: {

            Callback[] callbacks = loginProcess.getCallbacks();

            JsonValue jsonCallbacks;
            try {
                jsonCallbacks = handleCallbacks(headers, request, response, postBody, callbacks);
            } catch (RestAuthCallbackHandlerResponseException e) {
                // Include the authId in the JSON response.
                if (authId == null) {
                    authId = authIdHelper.createAuthId(loginConfiguration, loginProcess.getAuthContext());
                }
                e.getJsonResponse().put("authId", authId);
                throw e;
            }

            if (jsonCallbacks != null && jsonCallbacks.size() > 0) {
                JsonValue jsonValue = createJsonCallbackResponse(authId, loginConfiguration, loginProcess,
                        jsonCallbacks);
                responseBuilder = Response.status(Response.Status.OK);
                responseBuilder.entity(jsonValue.toString());

            } else {
                loginProcess = loginProcess.next(callbacks);
                responseBuilder = processAuthentication(headers, request, response, null, authId,
                        loginProcess, loginConfiguration);
            }
            break;
        }
        case COMPLETE: {

            if (loginProcess.isSuccessful()) {
                // send token to client
                JsonObject jsonResponseObject = JsonValueBuilder.jsonValue();

                SSOToken ssoToken = loginProcess.getAuthContext().getSSOToken();
                if (ssoToken != null) {
                    String tokenId = loginProcess.getAuthContext().getSSOToken().getTokenID().toString();
                    jsonResponseObject.put("tokenId", tokenId);
                } else {
                    jsonResponseObject.put("message", "Authentication Successful");
                }

                String successUrl = loginProcess.getAuthContext().getSuccessURL();
                jsonResponseObject.put("successUrl", successUrl);

                JsonValue jsonValue = jsonResponseObject.build();

                responseBuilder = Response.status(Response.Status.OK);
                responseBuilder.entity(jsonValue.toString());

            } else {
                // send Error to client
                AuthenticationContext authContext = loginProcess.getAuthContext();
                String errorCode = authContext.getErrorCode();
                String errorMessage = authContext.getErrorMessage();

                throw new RestAuthErrorCodeException(errorCode, errorMessage);
            }
            break;
        }
        }

        return responseBuilder;
    }

    private JsonValue handleCallbacks(HttpHeaders headers, HttpServletRequest request, HttpServletResponse response,
            JsonValue postBody, Callback[] callbacks)
            throws RestAuthCallbackHandlerResponseException {

        JsonValue jsonCallbacks = null;
        if (postBody == null || postBody.size() == 0) {
            jsonCallbacks = restAuthCallbackHandlerManager.handleCallbacks(headers, request, response, callbacks);

        } else if (!postBody.get("callbacks").isNull()) {
            JsonValue jCallbacks = postBody.get("callbacks");

            restAuthCallbackHandlerManager.handleJsonCallbacks(callbacks, jCallbacks);
        } else {
            restAuthCallbackHandlerManager.handleResponseCallbacks(headers, request, response, callbacks, postBody);
        }

        return jsonCallbacks;
    }

    private JsonValue createJsonCallbackResponse(String authId, LoginConfiguration loginConfiguration,
            LoginProcess loginProcess, JsonValue jsonCallbacks) throws SignatureException {

        PagePropertiesCallback pagePropertiesCallback = loginProcess.getPagePropertiesCallback();

        JsonObject jsonResponseObject = JsonValueBuilder.jsonValue();

        if (authId == null) {
            authId = authIdHelper.createAuthId(loginConfiguration, loginProcess.getAuthContext());
        }
        jsonResponseObject.put("authId", authId);
        if (pagePropertiesCallback != null) {
            jsonResponseObject.put("template", pagePropertiesCallback.getTemplateName());
            String moduleName = pagePropertiesCallback.getModuleName();
            String state = pagePropertiesCallback.getPageState();
            jsonResponseObject.put("stage", moduleName + state);
        }
        jsonResponseObject.put("callbacks", jsonCallbacks);

        return jsonResponseObject.build();
    }

    /**
     * Gets the AuthIndexType for the given authentication index type string.
     *
     * @param authIndexType The authentication index string.
     * @return The AuthIndexType enum.
     */
    private AuthIndexType getAuthIndexType(String authIndexType) {

        try {
            return AuthIndexType.getAuthIndexType(authIndexType);
        } catch (IllegalArgumentException e) {
            DEBUG.message("Unknown Authentication Index Type, " + authIndexType);
            throw new RestAuthException(Response.Status.BAD_REQUEST, "Unknown Authentication Index Type, "
                    + authIndexType);
        }
    }
}

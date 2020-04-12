/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS All rights reserved.
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
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openam.oauth2.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.*;

import com.iplanet.am.util.SystemProperties;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.idm.*;
import com.sun.identity.log.LogRecord;
import com.sun.identity.log.Logger;
import com.sun.identity.log.messageid.LogMessageProvider;
import com.sun.identity.log.messageid.MessageProviderFactory;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.security.DecodeAction;
import com.sun.identity.shared.Constants;
import com.sun.identity.shared.OAuth2Constants;
import com.sun.identity.shared.configuration.SystemPropertiesManager;
import com.sun.identity.sm.ServiceConfig;
import com.sun.identity.sm.ServiceConfigManager;
import org.forgerock.common.util.KeystoreManager;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openam.oauth2.model.CoreToken;
import org.forgerock.openam.oauth2.model.JWTToken;
import org.forgerock.openam.oauth2.provider.ClientVerifier;
import org.forgerock.openam.oauth2.provider.OAuth2ProviderSettings;
import org.forgerock.openam.oauth2.provider.OAuth2TokenStore;
import org.forgerock.openam.oauth2.exceptions.OAuthProblemException;
import org.forgerock.openam.oauth2.provider.impl.OAuth2ProviderSettingsImpl;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.engine.util.ChildContext;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.ext.servlet.ServletUtils;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.routing.Redirector;
import com.sun.identity.shared.debug.Debug;

import javax.servlet.http.HttpServletRequest;

/**
 * Utilities related to OAuth2.
 */
public class OAuth2Utils {

    public static Debug DEBUG;

    private static LogMessageProvider msgProvider;
    private static Logger accessLogger;
    private static Logger errorLogger;
    public static boolean logStatus = false;
    private static final Map<String, OAuth2ProviderSettings> settingsProviderMap =
            new HashMap<String, OAuth2ProviderSettings>();


    private final static String DEFAULT_KEYSTORE_FILE_PROP =
            "com.sun.identity.saml.xmlsig.keystore";
    private final static String DEFAULT_KEYSTORE_PASS_FILE_PROP =
            "com.sun.identity.saml.xmlsig.storepass";
    private final static String DEFAULT_KEYSTORE_TYPE_PROP =
            "com.sun.identity.saml.xmlsig.storetype";
    private final static String DEFAULT_PRIVATE_KEY_PASS_FILE_PROP  =
            "com.sun.identity.saml.xmlsig.keypass";

    static {
        DEBUG = Debug.getInstance("OAuth2Provider");

        String status = SystemProperties.get(Constants.AM_LOGSTATUS);
        logStatus = ((status != null) && status.equalsIgnoreCase("ACTIVE"));

        if (logStatus) {
            accessLogger = (Logger)Logger.getLogger(OAuth2Constants.ACCESS_LOG_NAME);
            errorLogger = (Logger)Logger.getLogger(OAuth2Constants.ERROR_LOG_NAME);
        }
    }

    /**
     * Logs an access message
     * @param msgIdName name of message id
     * @param data array of data to be logged
     * @param token session token of the user who did the operation
     * that triggered this logging
     */
    public static void logAccessMessage(
            String msgIdName,
            String data[],
            SSOToken token
    ) {
        try {
            if (msgProvider == null) {
                msgProvider = MessageProviderFactory.getProvider("OAuth2Provider");
            }
        } catch (IOException e) {
            DEBUG.error("OAuth2Utils.logAccessMessage()", e);
            DEBUG.error("OAuth2Utils.logAccessMessage():"
                    + "disabling logging");
            logStatus = false;
        }
        if ((accessLogger != null) && (msgProvider != null)) {
            LogRecord lr = msgProvider.createLogRecord(msgIdName, data, token);
            if (lr != null) {
                SSOToken ssoToken = (SSOToken)AccessController.doPrivileged(
                        AdminTokenAction.getInstance());
                    accessLogger.log(lr, ssoToken);
            }
        }
    }

    /**
     * Logs an error message
     * @param msgIdName name of message id
     * @param data array of data to be logged
     * @param token session token of the user who did the operation
     * that triggered this logging
     */
    public static void logErrorMessage(
            String msgIdName,
            String data[],
            SSOToken token
    ) {
        try {
            if (msgProvider == null) {
                msgProvider = MessageProviderFactory.getProvider("OAuth2Provider");
            }
        } catch (IOException e) {
            DEBUG.error("OAuth2Utils.logErrorMessage()", e);
            DEBUG.error("OAuth2Utils.logAccessMessage():"
                    + "disabling logging");
            logStatus = false;
        }
        if ((errorLogger != null) && (msgProvider != null)) {
            LogRecord lr = msgProvider.createLogRecord(msgIdName, data, token);
            if (lr != null) {
                SSOToken ssoToken = (SSOToken)AccessController.doPrivileged(
                        AdminTokenAction.getInstance());
                errorLogger.log(lr, ssoToken);
            }
        }
    }

    public static SSOToken getSSOToken(Request request){
        try {
            HttpServletRequest req = ServletUtils.getRequest(request);
            SSOTokenManager mgr = SSOTokenManager.getInstance();
            return mgr.createSSOToken(req);
        } catch (Exception e){
            if (OAuth2Utils.DEBUG.messageEnabled()){
                OAuth2Utils.DEBUG.message("OAuth2Utils::Unable to get sso token: ", e);
            }
        }
        return null;
    }

    public static enum ParameterLocation {
        HTTP_QUERY, HTTP_HEADER, HTTP_FRAGMENT, HTTP_BODY;

        @SuppressWarnings(value = "unchecked")
        public Map<String, String> getParameters(Request request) {
            Map<String, String> result = null;
            switch (this) {
            case HTTP_FRAGMENT:
                if (request.getReferrerRef() == null || request.getReferrerRef().getFragment() == null){
                    return null;
                }
                return new Form(request.getReferrerRef().getFragment()).getValuesMap();
            case HTTP_HEADER:
                if (null != request.getChallengeResponse()
                        && !request.getChallengeResponse().getParameters().isEmpty()) {
                    return new Form(request.getChallengeResponse().getParameters()).getValuesMap();
                }
                return null;
            case HTTP_QUERY:
                // Merge the parameterd from query and body
                result = request.getResourceRef().getQueryAsForm().getValuesMap();
            case HTTP_BODY:
                if (null == result) {
                    result = new LinkedHashMap<String, String>();
                }
                if (null != request.getEntity()) {
                    if (MediaType.APPLICATION_WWW_FORM.equals(request.getEntity().getMediaType())) {
                        Form form = new Form(request.getEntity());
                        // restore the entity body
                        request.setEntity(form.getWebRepresentation());
                        result.putAll(form.getValuesMap());
                    } else if (MediaType.APPLICATION_JSON
                            .equals(request.getEntity().getMediaType())) {
                        JacksonRepresentation<Map> representation =
                                new JacksonRepresentation<Map>(request.getEntity(), Map.class);
                        try {
                            result.putAll(representation.getObject());
                        } catch (IOException e) {
                            throw new ResourceException(e);
                        }
                        request.setEntity(representation);
                    }
                }
                return result;
            default:
                return null;
            }
        }

        /**
         * TODO How to use targetPattern?? TODO Use Custom Redirector to encode
         * variables in protected Reference getTargetRef(Request request,
         * Response response)
         * 
         * @param context
         * @return
         */
        public Redirector getRedirector(Context context, OAuthProblemException exception) {
            /*
             * 3.1.2.4. Invalid Endpoint
             * 
             * If an authorization request fails validation due to a missing,
             * invalid, or mismatching redirection URI, the authorization server
             * SHOULD inform the resource owner of the error, and MUST NOT
             * automatically redirect the user-agent to the invalid redirection
             * URI.
             */
            if (null != exception.getRedirectUri()) {
                // TODO handle non URL urn:ietf:wg:oauth:2.0:oob
                Reference cb = new Reference(exception.getRedirectUri());
                switch (this) {
                case HTTP_FRAGMENT: {
                    // Redirect URI can not contain Fragment so we can set it
                    cb.setFragment(exception.getErrorForm().getQueryString());
                    break;
                }
                case HTTP_QUERY: {
                    cb.addQueryParameters(exception.getErrorForm());
                    break;
                }
                default:
                    return null;
                }
                return new Redirector(context, cb.toString(), Redirector.MODE_CLIENT_FOUND);
            }
            return null;
        }
    }

    public static final String ACCESS_TOKEN_PATH = "access_token_path";
    public static final String AUTHORIZE_PATH = "authorize_path";
    public static final String TOKENINFO_PATH = "tokeninfo_path";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String AUTHORIZE = "authorize";
    public static final String TOKENINFO = "tokeninfo";
    public static final String SCOPE_DELIMITER_CONFIG = "scope_delimiter";
    public static final String SCOPE_DELIMITER = " ";
    public static final String SCOPE_LOCALE_DELIMITER = "|";

    /**
     * Returns the value of the "access_token_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "access_token_path" parameter.
     */
    public static String getAccessTokenPath(Context context) {
        String path = null;
        Context parent = context;
        do {
            path = parent.getParameters().getFirstValue(ACCESS_TOKEN_PATH);
            if (null == path && parent instanceof ChildContext) {
                try {
                    java.lang.reflect.Method getParentContext =
                            ChildContext.class.getDeclaredMethod("getParentContext");
                    getParentContext.setAccessible(true);
                    parent = (Context) getParentContext.invoke(parent, null);
                } catch (Exception e) {
                    parent = null;
                }
            } else {
                parent = null;
            }
        } while (null == path && null != parent);
        path = null != path ? path : ACCESS_TOKEN;
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "access_token_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "access_token_path" parameter.
     */
    public static String getAuthorizePath(Context context) {
        String path = null;
        Context parent = context;
        do {
            path = parent.getParameters().getFirstValue(AUTHORIZE_PATH);
            if (null == path && parent instanceof ChildContext) {
                try {
                    java.lang.reflect.Method getParentContext =
                            ChildContext.class.getDeclaredMethod("getParentContext");
                    getParentContext.setAccessible(true);
                    parent = (Context) getParentContext.invoke(parent, null);
                } catch (Exception e) {
                    parent = null;
                }
            } else {
                parent = null;
            }
        } while (null == path && null != parent);
        path = null != path ? path : AUTHORIZE;
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "tokeninfo_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "tokeninfo_path" parameter.
     */
    public static String getTokenInfoPath(Context context) {
        String path = context.getParameters().getFirstValue(TOKENINFO_PATH, TOKENINFO);
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "scope_delimiter" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "scope_delimiter" parameter.
     */
    public static String getScopeDelimiter(Context context) {
        if (null != context) {
            return context.getParameters().getFirstValue(SCOPE_DELIMITER_CONFIG, false,
                    SCOPE_DELIMITER);
        } else {
            return SCOPE_DELIMITER;
        }
    }

    /**
     * Returns the value of the "ClientVerifier" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "ClientVerifier" parameter.
     */
    public static ClientVerifier getClientVerifier(Context context) {
        Object o = context.getAttributes().get(ClientVerifier.class.getName());
        if (o instanceof ClientVerifier) {
            return (ClientVerifier) o;
        }
        return null;
    }

    /**
     * Returns the value of the "OAuth2TokenStore" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "OAuth2TokenStore" parameter.
     */
    public static OAuth2TokenStore getTokenStore(Context context) {
        Object o = context.getAttributes().get(OAuth2TokenStore.class.getName());
        if (o instanceof OAuth2TokenStore) {
            return (OAuth2TokenStore) o;
        }
        return null;
    }

    /**
     * Sets the value of the "access_token_path" parameter.
     * 
     * @param value
     *            The value of the "access_token_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setAccessTokenPath(String value, Context context) {
        context.getParameters().set(ACCESS_TOKEN_PATH, value);
    }

    /**
     * Sets the value of the "authorize_path" parameter.
     * 
     * @param value
     *            The value of the "authorize_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setAuthorizePath(String value, Context context) {
        context.getParameters().set(AUTHORIZE_PATH, value);
    }

    /**
     * Sets the value of the "tokeninfo_path" parameter.
     * 
     * @param value
     *            The value of the "tokeninfo_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setTokenInfoPath(String value, Context context) {
        context.getParameters().set(TOKENINFO_PATH, value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setScopeDelimiter(String value, Context context) {
        context.getParameters().set(SCOPE_DELIMITER_CONFIG, value);
    }

    /**
     * Sets the value of the "realm" parameter.
     * 
     * @param value
     *            The value of the "realm" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setContextRealm(String value, Context context) {
        context.getParameters().set(OAuth2Constants.Custom.REALM, value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setTokenStore(OAuth2TokenStore value, Context context) {
        context.getAttributes().put(OAuth2TokenStore.class.getName(), value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setClientVerifier(ClientVerifier value, Context context) {
        context.getAttributes().put(ClientVerifier.class.getName(), value);
    }

    /**
     * Get the realm from the Attributes first and then look for the realm in
     * the request.
     * <p/>
     * Example: Restlet Template populates the realm into the
     * {@link Request#attributes} {@code TemplateRoute route =
     * router.attach("/oauth2/ realm}/authorize", (Restlet)authorization);}
     * <p/>
     * Example: Custom code fetches it from the query, the body or more secure
     * from the User Session
     * @param request
     * @return the realm
     */
    public static String getRealm(Request request) {
        Object realm = request.getAttributes().get(OAuth2Constants.Custom.REALM);
        if (realm instanceof String) {
            return (String) realm;
        }
        String ret = getRequestParameter(request, OAuth2Constants.Custom.REALM, String.class);
        if (ret == null){
            return "/";
        } else {
            return ret;
        }
    }

    /**
     * Get the realm from the <code>HttpServletRequest</code>.
     * This will use the request parameter.
     * @param request
     * @return the realm - or top level realm if none is present.
     */
	public static String getRealm(HttpServletRequest request) {
        String realm = request.getParameter(OAuth2Constants.Custom.REALM);
        if (realm == null || realm.length() == 0) {
            realm = "/";
        }
        return realm;
	}

    public static String getModuleName(Request request) {
        Object module = request.getAttributes().get(OAuth2Constants.Custom.MODULE);
        if (module instanceof String) {
            return (String) module;
        }
        return getRequestParameter(request, OAuth2Constants.Custom.MODULE, String.class);
    }

    public static String getServiceName(Request request) {
        Object service = request.getAttributes().get(OAuth2Constants.Custom.SERVICE);
        if (service instanceof String) {
            return (String) service;
        }
        return getRequestParameter(request, OAuth2Constants.Custom.SERVICE, String.class);
    }

    public static String getLocale(Request request) {
        Object locale = request.getAttributes().get(OAuth2Constants.Custom.LOCALE);
        if (locale instanceof String) {
            return (String) locale;
        }
        return getRequestParameter(request, OAuth2Constants.Custom.LOCALE, String.class);
    }

    public static <T> T getRequestParameter(Request request, String parameterName, Class<T> clazz) {
        Object value = getRequestParameters(request).get(parameterName);
        if (null != value && clazz.isAssignableFrom(value.getClass())) {
            return clazz.cast(value);
        }
        return null;
    }

    /**
     * It copies the given parameters only once!!!
     * way the CallResolver can use it and the FreeMarker can list and add all
     * into the generated form
     * 
     * @param request
     *            incoming request object
     * @return The modifiable attributes map.
     */
    public static Map<String, Object> getRequestParameters(Request request) {
        Map<String, String> parameters = null;
        if (request.getAttributes().get(OAuth2Constants.Params.class.getName()) instanceof Map == false) {
            parameters = getParameters(request);
            if (null != parameters) {
                // Copy the parameter for CallResolver
                request.getAttributes().putAll(parameters);
            }
            // Avoid reprocess the request next time.
            request.getAttributes().put(OAuth2Constants.Params.class.getName(), parameters);
        }
        return request.getAttributes();
    }

    /**
     * Get the parameters from the request.
     * <p/>
     * If the method is GET then the parameters are fetched from the query If
     * the request has no body/payload then the parameters are fetched from the
     * query If the content type is "application/x-www-form-urlencoded" then the
     * parameters are fetched from the body
     * 
     * @param request
     *            incoming request object
     * @return null if the request does not contains any parameter
     */
    public static Map<String, String> getParameters(Request request) {
        if (Method.GET.equals(request.getMethod())
                || request.getEntity() instanceof EmptyRepresentation) {
            return OAuth2Utils.ParameterLocation.HTTP_QUERY.getParameters(request);
        } else {
            return OAuth2Utils.ParameterLocation.HTTP_QUERY.getParameters(request);
        }
    }

    /**
     * Determines if a string is empty. Empty is defined as null or empty
     * string.
     * <p/>
     * 
     * <pre>
     *  OAuth2Utils.isEmpty(null)               = true
     *  OAuth2Utils.isEmpty(&quot;&quot;)       = true
     *  OAuth2Utils.isEmpty(&quot; &quot;)      = false
     *  OAuth2Utils.isEmpty(&quot;bob&quot;)    = false
     *  OAuth2Utils.isEmpty(&quot; bob &quot;)  = false
     * </pre>
     * 
     * @param val
     *            string to evaluate as empty.
     * @return true if the string is empty else false.
     */
    public static boolean isEmpty(String val) {
        return (val == null) ? true : "".equals(val) ? true : false;
    }

    /**
     * Determines if a string is not empty. Its the exact opposite for
     * {@link #isEmpty(String)}.
     * 
     * @param val
     *            string to evaluate.
     * @return true if the string is not empty
     */
    public static boolean isNotEmpty(String val) {
        return !isEmpty(val);
    }

    /**
     * <pre>
     *      OAuth2Utils.isBlank(null)                = true
     *      OAuth2Utils.isBlank(&quot;&quot;)        = true
     *      OAuth2Utils.isBlank(&quot; &quot;)       = true
     *      OAuth2Utils.isBlank(&quot;bob&quot;)     = false
     *      OAuth2Utils.isBlank(&quot;  bob  &quot;) = false
     * </pre>
     */
    public static boolean isBlank(String val) {
        return (val == null) ? true : isEmpty(val.trim());
    }

    public static boolean isNotBlank(String val) {
        return !isBlank(val);
    }

    public static String join(Iterable<? extends Object> iterable, String delimiter) {
        if (null != iterable) {
            Iterator<? extends Object> iterator = iterable.iterator();
            if (!iterator.hasNext()) {
                return null;
            }
            StringBuilder buffer = new StringBuilder();
            buffer.append(iterator.next());
            String d = null != delimiter ? delimiter : SCOPE_DELIMITER;
            while (iterator.hasNext()) {
                buffer.append(d).append(iterator.next());
            }
            return buffer.toString();
        }
        return null;
    }

    public static Set<String> split(String string, String delimiter) {
        if (isNotBlank(string)) {
            StringTokenizer tokenizer =
                    new StringTokenizer(string, null != delimiter ? delimiter : SCOPE_DELIMITER);
            Set<String> result = new TreeSet<String>();
            while (tokenizer.hasMoreTokens()) {
                result.add(tokenizer.nextToken());
            }
            return Collections.unmodifiableSet(result);
        } else {
            return Collections.emptySet();
        }
    }


    /**
     * Scopes retrieved from the stored client can be in the form <scope>|<locale>|<description>. This method
     * parses out the actual scope value
     * @param maximumScope The allowed scopes for the client
     * @return A set of strings containing the scope value only
     */
    public static Set<String> parseScope(Set<String> maximumScope){
        Set<String> cleanScopes = new TreeSet<String>();
        for (String s : maximumScope){
            int index = s.indexOf(SCOPE_LOCALE_DELIMITER);
            if (index == -1){
                cleanScopes.add(s);
                continue;
            }
            cleanScopes.add(s.substring(0,index));
        }

        return cleanScopes;
    }

    public static Set<String> stringToSet(String string){
        if (string == null || string.isEmpty()){
            return Collections.EMPTY_SET;
        }
        String[] values = string.split(" ");
        Set<String> set = new HashSet<String>(Arrays.asList(values));
        return set;
    }

    /**
     * Gets the deployment URI of the OAuth2 authorization server.
     * @param request the {@link Request} to get the deployment uri of
     * @return deployment URI of the OAuth2 server
     */
    public static String getDeploymentURL(Request request){
        HttpServletRequest httpRequest = ServletUtils.getRequest(request);
        return getDeploymentURL(httpRequest);
    }

    /**
     * Gets the deployment URI of the OAuth2 authorization server
     * @param request the HttpServletRequest to get the deployment uri of
     * @return deployment URI of the OAuth2 server
     */
    public static String getDeploymentURL(HttpServletRequest request){
        String uri = request.getRequestURI();
        String deploymentURI = uri;
        int firstSlashIndex = uri.indexOf("/");
        int secondSlashIndex = uri.indexOf("/", firstSlashIndex + 1);
        if (secondSlashIndex != -1) {
            deploymentURI = uri.substring(0, secondSlashIndex);
        }
        StringBuffer sb = new StringBuffer(100);
        sb.append(request.getScheme()).append("://")
                .append(request.getServerName()).append(":")
                .append(request.getServerPort())
                .append(deploymentURI);
        return sb.toString();
    }

    /**
     * Constructor.
     */
    private OAuth2Utils() {
    }

    public static <T> T getOAuth2ProviderSetting(String setting, Class<T> clazz, Request request){
        SSOToken token = (SSOToken) AccessController.doPrivileged(AdminTokenAction.getInstance());
        ServiceConfig scm = null;
        ServiceConfigManager mgr = null;
        if (setting == null || setting.isEmpty()){
            return null;
        }
        try {
            mgr = new ServiceConfigManager(token, OAuth2Constants.OAuth2ProviderService.NAME, OAuth2Constants.OAuth2ProviderService.VERSION);
            scm = mgr.getOrganizationConfig(OAuth2Utils.getRealm(request), null);
        } catch (Exception e){
            OAuth2Utils.DEBUG.error("OAuth2Utils::Unable to get provider setting: ", e);
            throw OAuthProblemException.OAuthError.SERVER_ERROR.handle(null, "Not able to get provider setting");
        }
        Map<String, Set<String>> attrs = scm.getAttributes();
        Set<String> attr = attrs.get(setting);
        if (attr != null && attr.size() > 0){
            if (clazz.isAssignableFrom(String.class)){
                return clazz.cast(attr.iterator().next());
            } else if (clazz.isAssignableFrom(Set.class)){
                return clazz.cast(attr);
            } else {
                OAuth2Utils.DEBUG.error("OAuth2Utils::Provided an unsupported class type.");
                throw OAuthProblemException.OAuthError.SERVER_ERROR.handle(null, "Provided unsupported class type");
            }
        } else {
            return null;
        }
    }

    public static AMIdentity getClientIdentity(String uName, String realm) throws OAuthProblemException {
        SSOToken token = (SSOToken) AccessController.doPrivileged(AdminTokenAction.getInstance());
        AMIdentity theID = null;

        try {
            AMIdentityRepository amIdRepo = new AMIdentityRepository(token, realm);

            IdSearchControl idsc = new IdSearchControl();
            idsc.setRecursive(true);
            idsc.setAllReturnAttributes(true);
            // search for the identity
            Set<AMIdentity> results = Collections.EMPTY_SET;
            idsc.setMaxResults(0);
            IdSearchResults searchResults =
                    amIdRepo.searchIdentities(IdType.AGENTONLY, uName, idsc);
                results = searchResults.getSearchResults();

            if (results == null || results.size() != 1) {
                OAuth2Utils.DEBUG.error("OAuth2Utils.getClientIdentity()::No client profile or more than one profile found.");
                throw OAuthProblemException.OAuthError.UNAUTHORIZED_CLIENT.handle(null,
                        "Not able to get client from OpenAM");
            }

            theID = results.iterator().next();

            //if the client is deactivated return null
            if (theID.isActive()){
                return theID;
            } else {
                return null;
            }
        } catch (Exception e){
            OAuth2Utils.DEBUG.error("OAuth2Utils::Unable to get client AMIdentity: ", e);
            throw OAuthProblemException.OAuthError.UNAUTHORIZED_CLIENT.handle(null, "Not able to get client from OpenAM");
        }
    }

    public static AMIdentity getIdentity(String uName, String realm) throws OAuthProblemException {
        SSOToken token = (SSOToken) AccessController.doPrivileged(AdminTokenAction.getInstance());
        AMIdentity theID = null;

        try {
            AMIdentityRepository amIdRepo = new AMIdentityRepository(token, realm);

            IdSearchControl idsc = new IdSearchControl();
            idsc.setRecursive(true);
            idsc.setAllReturnAttributes(true);
            // search for the identity
            Set<AMIdentity> results = Collections.EMPTY_SET;
            idsc.setMaxResults(0);
            IdSearchResults searchResults =
                    amIdRepo.searchIdentities(IdType.USER, uName, idsc);
            if (searchResults != null && !searchResults.getResultAttributes().isEmpty()) {
                results = searchResults.getSearchResults();
            } else {
                OAuth2ProviderSettings settings = OAuth2Utils.getSettingsProvider(Request.getCurrent());
                Map<String, Set<String>> avPairs = toAvPairMap(settings.getListOfAttributesTheResourceOwnerIsAuthenticatedOn(),
                        uName);
                idsc.setSearchModifiers(IdSearchOpModifier.OR, avPairs);
                searchResults =
                        amIdRepo.searchIdentities(IdType.USER, "*", idsc);
                if (searchResults != null) {
                    results = searchResults.getSearchResults();
                }
            }

            if (results == null || results.size() != 1) {
                OAuth2Utils.DEBUG.error("ScopeImpl.getIdentity()::No user profile or more than one profile found.");
                throw OAuthProblemException.OAuthError.UNAUTHORIZED_CLIENT.handle(null,
                        "Not able to get user from OpenAM");
            }

            theID = results.iterator().next();

            //if the client is deactivated return null
            if (theID.isActive()){
                return theID;
            } else {
                return null;
            }
        } catch (Exception e){
            OAuth2Utils.DEBUG.error("ClientVerifierImpl::Unable to get client AMIdentity: ", e);
            throw OAuthProblemException.OAuthError.UNAUTHORIZED_CLIENT.handle(null, "Not able to get client from OpenAM");
        }
    }

    private static Map toAvPairMap(Set names, String token) {
        if (token == null) {
            return Collections.EMPTY_MAP;
        }
        Map map = new HashMap();
        Set set = new HashSet();
        set.add(token);
        if (names == null || names.isEmpty()) {
            return map;
        }
        Iterator it = names.iterator();
        while (it.hasNext()) {
            map.put((String) it.next(), set);
        }
        return map;
    }

    public static String decodePassword(String password)  {
        String decodedPassword = AccessController.doPrivileged(new DecodeAction(password));

        return decodedPassword == null ? password : decodedPassword;
    }

    /**
     * Gets the KeyPair based on the current restlet Request
     * @param request the restlet request
     * @return the  KeyPair 
     */
    public static KeyPair getServerKeyPair(org.restlet.Request request) {
        OAuth2ProviderSettings settings = getSettingsProvider(request);
        return getServerKeyPairFromSettings(settings);
    }

    /**
     * Gets the KeyPair based on the current HttpServletRequest 
     * @param request the HttpServletRequest
     * @return KeyPair
     */
    public static KeyPair getServerKeyPair(javax.servlet.http.HttpServletRequest request){
        OAuth2ProviderSettings settings = getSettingsProvider(request);
        return getServerKeyPairFromSettings(settings);
    }
    
    /*
     * Get the KeyPair from the OAuth2ProviderSettings provided.
     */
	private static KeyPair getServerKeyPairFromSettings(
			OAuth2ProviderSettings settings) {
		String alias = settings.getKeyStoreAlias();

        //get keystore password from file
        String kspfile = SystemPropertiesManager.get(DEFAULT_KEYSTORE_PASS_FILE_PROP);
        String keystorePass = null;
        if (kspfile != null) {
            try {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(new FileInputStream(kspfile)));
                    keystorePass = decodePassword(br.readLine());
                } finally {
                    if (br != null) {
                        br.close();
                    }
                }
            } catch (IOException e) {
                OAuth2Utils.DEBUG.error("OAuth2Utils.getServerKeyPair():: Unable to read keystore password file " + kspfile, e);
            }
        } else {
            OAuth2Utils.DEBUG.error("OAuth2Utils.getServerKeyPair():: keystore password is null");
        }

        String keypassfile = SystemPropertiesManager.get(DEFAULT_PRIVATE_KEY_PASS_FILE_PROP);
        String keypass = null;
        if (keypassfile != null) {
            try {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(new FileInputStream(keypassfile)));
                    keypass = decodePassword(br.readLine());
                } finally {
                    if (br != null) {
                        br.close();
                    }
                }
            } catch (IOException e) {
                OAuth2Utils.DEBUG.error("OAuth2Utils.getServerKeyPair():: Unable to read key password file " + keypassfile, e);
            }
        } else {
            OAuth2Utils.DEBUG.error("OAuth2Utils.getServerKeyPair():: key password is null");
        }

        //get private key password

        KeystoreManager keystoreManager = new KeystoreManager(
                keypass,
                SystemPropertiesManager.get(DEFAULT_KEYSTORE_TYPE_PROP, "JKS"),
                SystemPropertiesManager.get(DEFAULT_KEYSTORE_FILE_PROP),
                keystorePass);

        PrivateKey privateKey = keystoreManager.getPrivateKey(alias);
        PublicKey publicKey = keystoreManager.getPublicKey(alias);
        return new KeyPair(publicKey, privateKey);
	}

    /*
     * This method is called from multiple threads, and must initialize a new OAuth2ProviderSettings instance atomically.
     */
    public static OAuth2ProviderSettings getSettingsProvider(org.restlet.Request request) {
        HttpServletRequest httpRequest = ServletUtils.getRequest(request);
        // need to get realm using org.restlet.Request as the corresponding  HttpServletRequest
        // may have had its post parameters consumed. 
        String realm =  getRealm(request);
        return getSettingdProviderByRealm(httpRequest, realm);
    }

    /*
     * This method is called from multiple threads, and must initialize a new OAuth2ProviderSettings instance atomically.
     */
    public static OAuth2ProviderSettings getSettingsProvider(javax.servlet.http.HttpServletRequest request) {
        String realm = getRealm(request);
        return getSettingdProviderByRealm(request, realm);
    }

    
    /*
     * Gets the provider settings based on the realm.
     */
	private static OAuth2ProviderSettings getSettingdProviderByRealm(
			javax.servlet.http.HttpServletRequest request, String realm) {
		synchronized (settingsProviderMap) {
            OAuth2ProviderSettings setting = settingsProviderMap.get(realm);
            if (setting != null) {
                return setting;
            } else {
                setting = new OAuth2ProviderSettingsImpl(request, realm);
                settingsProviderMap.put(realm, setting);
                return setting;
            }
        }
	}
}

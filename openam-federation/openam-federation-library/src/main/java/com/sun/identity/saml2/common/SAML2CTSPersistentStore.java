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
package com.sun.identity.saml2.common;

import com.iplanet.dpro.session.SessionException;
import com.iplanet.dpro.session.service.SessionService;
import com.iplanet.dpro.session.share.SessionBundle;
import com.sun.identity.common.GeneralTaskRunnable;
import com.sun.identity.common.SystemTimer;
import com.sun.identity.coretoken.interfaces.AMTokenSAML2Repository;
import com.sun.identity.shared.Constants;
import com.sun.identity.shared.configuration.SystemPropertiesManager;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.openam.cts.CTSPersistentStore;
import org.forgerock.openam.cts.adapters.SAMLAdapter;
import org.forgerock.openam.cts.adapters.TokenAdapter;
import org.forgerock.openam.cts.api.fields.CoreTokenField;
import org.forgerock.openam.cts.api.fields.SAMLTokenField;
import org.forgerock.openam.cts.api.tokens.SAMLToken;
import org.forgerock.openam.cts.api.tokens.Token;
import org.forgerock.openam.cts.api.tokens.TokenIdFactory;
import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.guice.InjectorHolder;

import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * This class is used in SAML2  mode to store/recover serialized
 * state of Assertion/Response object.
 *
 * This class acts as a Proxy to perform distinct SAML2
 * operations and allow the CTSPersistentStore implementation
 * to handle the actual CRUD for Tokens.
 *
 */
public class SAML2CTSPersistentStore extends GeneralTaskRunnable implements AMTokenSAML2Repository {

    /**
     * Indicator for Persistence Store.
     */
    private static boolean isDatabaseUp = false;
    private static boolean lastLoggedDBStatusIsUp = false;

    /**
     * Provides a Reference the AMTokenSAML2Repository Implementation Class for our
     * persistence operations.
     *
     * This instance is this classes Singleton.
     */
    private static volatile AMTokenSAML2Repository instance = new SAML2CTSPersistentStore();

    /**
     * OpenAM CTS Repository.
     *
     * This instance actually brings in the implementation for all CRUD
     * Operations and main implementation.  @see CTSPersistentStore.
     */
    private static volatile CTSPersistentStore persistentStore = null;

    /**
     * grace period before expired session records are removed from the
     * repository
     */
    private static long gracePeriod = 5 * 60; /* 5 mins in secs */

    private static final String CLEANUP_GRACE_PERIOD =
        "com.sun.identity.session.repository.cleanupGracePeriod";

    private static final String BRIEF_DB_ERROR_MSG =
        "SAML2 failover service is not functional due to Token Persistent Store unavailability.";

    private static final String DB_ERROR_MSG =
        "SAML2 Token Persistent Store is not available at this moment."
            + "Please check with the system administrator " +
                    "for appropriate actions";

    private static final String LOG_MSG_DB_BACK_ONLINE =
        "SESSION_DATABASE_BACK_ONLINE";

    private static final String LOG_MSG_DB_UNAVAILABLE =
        "SESSION_DATABASE_UNAVAILABLE";

    public static final String CLEANUP_RUN_PERIOD =
        "com.sun.identity.saml2.repository.cleanupRunPeriod";

    /**
     * Time period between two successive runs of DBHealthChecker thread which
     * checks for Database availability.
     */
    private static long healthCheckPeriod = 1 * 60 * 1000;

    public static final String HEALTH_CHECK_RUN_PERIOD =
        "com.sun.identity.saml2.repository.healthCheckRunPeriod";

    /**
     * This period is actual one that is used by the thread. The value is set to
     * the smallest value of cleanUPPeriod and healthCheckPeriod.
     */
    private static long runPeriod = 1 * 60 * 1000; // 1 min in milliseconds

    private static final Debug debug = Debug.getInstance("amToken");

    private final TokenAdapter<SAMLToken> tokenAdapter = InjectorHolder.getInstance(SAMLAdapter.class);
    private final TokenIdFactory tokenIdFactory = InjectorHolder.getInstance(TokenIdFactory.class);

    /**
     * Static Initialization Stanza.
     */
    static {
        try {
            gracePeriod = Integer.parseInt(SystemPropertiesManager.get(
                    CLEANUP_GRACE_PERIOD, String.valueOf(gracePeriod)));
        } catch (Exception e) {
            debug.error("Invalid value for " + CLEANUP_GRACE_PERIOD
                    + ", using default");
        }

        try {
            healthCheckPeriod = Integer
                    .parseInt(SystemPropertiesManager.get(HEALTH_CHECK_RUN_PERIOD,
                            String.valueOf(healthCheckPeriod)));
        } catch (Exception e) {
            debug.error("Invalid value for " + HEALTH_CHECK_RUN_PERIOD
                    + ", using default");
        }

        // Instantiate the Singleton Instance.
        try {
            initialize();
        } catch (Exception e) {
            debug.error("Unable to initialize " + SAML2CTSPersistentStore.class.getName()
                    + " for SAML2 Persistence", e);
        }
    }

    /**
     * Package Protected from instantiation.
     */
    private SAML2CTSPersistentStore() {
    }

   /**
    *
    * Constructs new SAML2CTSPersistentStore
    * @exception Exception when cannot create a new SAML2 repository
    *
    */
   private static void initialize() throws SessionException {
        // Obtain OpenAM Instance Configuration Properties.
        String thisSessionServerProtocol = SystemPropertiesManager
                .get(Constants.AM_SERVER_PROTOCOL);
        String thisSessionServer = SystemPropertiesManager
                .get(Constants.AM_SERVER_HOST);
        String thisSessionServerPortAsString = SystemPropertiesManager
                .get(Constants.AM_SERVER_PORT);

        if (thisSessionServerProtocol == null
                || thisSessionServerPortAsString == null
                || thisSessionServer == null) {
            throw new SessionException(SessionBundle.rbName,
                    "propertyMustBeSet", null);
        }

        // Initialize our Persistence Layer.
        initPersistSession();   
        // Schedule our Runnable Background Thread Task. @see run() method for associated Task.
        SystemTimer.getTimer().schedule((SAML2CTSPersistentStore) instance, new Date((
            System.currentTimeMillis() / 1000) * 1000));
    }

    /**
     *
     * Initialize the Reference to our CTS Persistent Store.
     */
    private static void initPersistSession() {
        try {
            // Obtain our AM Token Repository Instance to provide the Backend CRUD for Tokens.
            persistentStore = InjectorHolder.getInstance(CTSPersistentStore.class);
            if (persistentStore != null) {
                isDatabaseUp = true;
            } else {
                // This Throw will be caught below.
                throw new Exception("Unable to acquire AMTokenSAML2Repository Implementation Reference from Factory!");
            }
        } catch (Exception e) {
            isDatabaseUp = false;
            debug.error(BRIEF_DB_ERROR_MSG);
            if (debug.messageEnabled()) {
                debug.message(DB_ERROR_MSG, e);
            }
        }
    }

    /**
     * Provide Service Instance Access to our Singleton
     *
     * @return AMTokenSAML2Repository Singleton Instance.
     */
    public static AMTokenSAML2Repository getInstance() {
        return instance;
    }

    /**
     * Retrives existing SAML2 object from persistent Repository.
     *
     * @param samlKey primary key
     * @return Object - SAML2 unMarshaled Object, if failed, return null.
     */
    @Override
    public Object retrieveSAML2Token(String samlKey) {
        if (!isDatabaseUp) {
            return null;
        }
        try {
            // Retrieve the SAML2 Token from the Repository using the SAML2 Primary Key.
            Token token = persistentStore.read(tokenIdFactory.toSAMLPrimaryTokenId(samlKey));
            SAMLToken samlToken = tokenAdapter.fromToken(token);
            return samlToken.getToken();
        } catch (CoreTokenException e) {
            isDatabaseUp = false;
            logDBStatus();
            debug.error(BRIEF_DB_ERROR_MSG, e);
            if (debug.messageEnabled()) {
                debug.message(DB_ERROR_MSG, e);
            }
            return null;
        } catch (Exception e) {
            debug.message("AMTokenSAML2Repository.retrieveSAML2Token(): failed retrieving "
                    + "SAML2 object", e);
            return null;
        }
    }

   /**
    * Retrives a list of existing SAML2 object from persistent datastore with
    * secodaryKey
    *
    * @param secKey Secondary Key 
    * @return SAML2 object, if failed, return null. 
    */
    @Override
    public List<Object> retrieveSAML2TokenWithSecondaryKey(String secKey) {
        if (!isDatabaseUp) {
            return null;
        }
        try {
            // Perform a query against the token store with the secondary key.
            Map<CoreTokenField, Object> queryMap = new EnumMap<CoreTokenField, Object>(CoreTokenField.class);
            queryMap.put(SAMLTokenField.SECONDARY_KEY.getField(), tokenIdFactory.toSAMLSecondaryTokenId(secKey));

            Collection<Token> tokens = persistentStore.list(queryMap);
            List<Object> results = new LinkedList<Object>();
            for (Token token : tokens) {
                SAMLToken samlToken = tokenAdapter.fromToken(token);
                results.add(samlToken.getToken());
            }

            return results;
        } catch (CoreTokenException e) {
            isDatabaseUp = false;
            logDBStatus();
            debug.error(BRIEF_DB_ERROR_MSG, e);
            if (debug.messageEnabled()) {
                debug.message(DB_ERROR_MSG, e);
            }
            return null;
        } catch (Exception e) {
            debug.message("AMTokenSAML2Repository.retrieveSAML2TokenWithSecondaryKey(): failed retrieving "
                    + "SAML2 object", e);
            return null;
        }
    }

   /**
    * Deletes the SAML2 object by given primary key from the repository
    * @param samlKey primary key 
    */
    @Override
    public void deleteSAML2Token(String samlKey)  {
        if (!isDatabaseUp) {
            return;
        }
        try {
            persistentStore.delete(tokenIdFactory.toSAMLPrimaryTokenId(samlKey));
        } catch (CoreTokenException e) {
            isDatabaseUp = false;
            logDBStatus();
            debug.error(BRIEF_DB_ERROR_MSG, e);
            if (debug.messageEnabled()) {
                debug.message(DB_ERROR_MSG, e);
            }
            debug.error("AMTokenSAML2Repository.deleteSAML2Token(): failed deleting "
                    + "SAML2 object", e);
        }
    }

    /**
     * Deletes expired SAML2 object from the repository
     */
    public void deleteExpiredSAML2Tokens()  {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveSAML2Token(String samlKey, Object samlObj, long expirationTime,
        String secKey) {
        // Is our Token Repository Available?
        if (!isDatabaseUp) {
            return;
        }
        // Save the SAML2 Token.
        try {
            // Perform the Save of the Token to the Token Repository.
            SAMLToken samlToken = new SAMLToken(samlKey, secKey, expirationTime, samlObj);
            Token token = tokenAdapter.toToken(samlToken);
            persistentStore.create(token);
        } catch (CoreTokenException e) {
            isDatabaseUp = false;
            logDBStatus();
            debug.error(BRIEF_DB_ERROR_MSG, e);
            if (debug.messageEnabled()) {
                debug.message(DB_ERROR_MSG, e);
            }
            debug.error("AMTokenSAML2Repository.saveSAML2Token(): failed "
                    + "to save SAML2 object", e);
        }
    }

    /**
     * This method is invoked to log a message in the following two cases:
     * 
     * (1) the DB is detected down by either the user requests
     * (retrieve/save/delete) or the background checker thread:
     * Log message: HA_DATABASE_UNAVAILABLE (2) the DB is detected
     * available again by the background health checker thread => Log message:
     * HA_DATABASE_BACK_ONLINE
     * 
     * The flag "lastLoggedDBStatusIsUp" is used to avoid logging the same DB
     * status again and again if the status actually doesn't change over time.
     * 
     * Please also note that if the DB is already down in the very beginning
     * when starting the AM instance, there will be no message being logged
     * since at this time the session service is not fully initialized yet
     * therefore no sso token can be generated and used for the logging purpose.
     * Nevertheless, the appropriate logging will be done later when the
     * background thread kicks in.
     * 
     */
    private void logDBStatus() {

        SessionService ss = SessionService.getSessionService();

        if (!isDatabaseUp && lastLoggedDBStatusIsUp) {
            ss.logSystemMessage(LOG_MSG_DB_UNAVAILABLE,
                    java.util.logging.Level.WARNING);
            lastLoggedDBStatusIsUp = false;
        }
        if (isDatabaseUp && !lastLoggedDBStatusIsUp) {
            ss.logSystemMessage(LOG_MSG_DB_BACK_ONLINE,
                    java.util.logging.Level.INFO);
            lastLoggedDBStatusIsUp = true;
        }
    }

    /**
     * Return Service Run Period.
     *
     * @return long current run period.
     */
    public long getRunPeriod() {
        return runPeriod;
    }

    /**
     * Service Method.
     *
     * @param obj
     * @return
     */
    public boolean addElement(Object obj) {
        return false;
    }

    /**
     * Service Method.
     *
     * @param obj
     * @return
     */
    public boolean removeElement(Object obj) {
        return false;
    }

    /**
     * Service Method.
     *
     * @return
     */
    public boolean isEmpty() {
        return true;
    }
  
    
    /**
     * Monitoring logic used by background thread This thread is used for both
     * cleanup of expired sessions in the repository and for the Repository health
     * checking. The thread always runs with smallest value of cleanUpPeriod and
     * healthCheckPeriod.
     */
     public void run() {
        String classMethod="SAML2CTSPersistentStore.run: ";
        try {

            if (debug.messageEnabled()) {
                debug.message(classMethod + "Cleaning expired SAML2 records");
            }

            /*
             * HealthChecking is done based on the runPeriod but only when
             * the Database is down.
             */
           if (SAML2Utils.isSAML2FailOverEnabled() && (!isDatabaseUp)) {
                initPersistSession();
                logDBStatus();
            }
        } catch (Exception e) {
            debug.error("SAML2CTSPersistentStore.run(): Exception in thread",
                    e);
        }
    }
}

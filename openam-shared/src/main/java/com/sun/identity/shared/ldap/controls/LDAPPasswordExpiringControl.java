/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * The contents of this file are subject to the Netscape Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/NPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is mozilla.org code.
 *
 * The Initial Developer of the Original Code is Netscape
 * Communications Corporation.  Portions created by Netscape are
 * Copyright (C) 1999 Netscape Communications Corporation. All
 * Rights Reserved.
 *
 * Contributor(s): 
 */
package com.sun.identity.shared.ldap.controls;

import com.sun.identity.shared.ldap.LDAPControl;
import com.sun.identity.shared.ldap.LDAPException;

/**
 * Represents an LDAP v3 server control that may be returned if a
 * password is about to expire, and password policy is enabled on the server.
 * The OID for this control is 2.16.840.1.113730.3.4.5.
 * <P>
 *
 * @version 1.0
 * @see com.sun.identity.shared.ldap.LDAPControl
 * @deprecated As of ForgeRock OpenAM 10.
 */
public class LDAPPasswordExpiringControl extends LDAPStringControl {
    public final static String EXPIRING = "2.16.840.1.113730.3.4.5";

    /**
     * Contructs an <CODE>LDAPPasswordExpiringControl</CODE> object.
     * This constructor is used by <CODE>LDAPControl.register</CODE> to 
     * instantiate password expiring controls.
     * <P>
     * To retrieve the number of seconds until this password expires,
     * call <CODE>getSecondsToExpiration</CODE>.
     * @param oid this parameter must be 
     * <CODE>LDAPPasswordExpiringControl.EXPIRING</CODE>
     * or an <CODE>LDAPException</CODE> is thrown
     * @param critical <code>true</code> if this control is critical
     * @param value the value associated with this control
     * @exception com.sun.identity.shared.ldap.LDAPException If oid is not 
     * <CODE>LDAPPasswordExpiringControl.EXPIRING.</CODE>
     * @see com.sun.identity.shared.ldap.LDAPControl#register
     */
    public LDAPPasswordExpiringControl( String oid, boolean critical, 
                                       byte[] value ) throws LDAPException {
        super( EXPIRING, critical, value );
	if ( !oid.equals( EXPIRING )) {
	    throw new LDAPException( "oid must be LDAPPasswordExpiringControl" +
				     ".EXPIRING", LDAPException.PARAM_ERROR );
	}
    }

    public int getType() {
        return LDAPControl.LDAP_PASSWORD_EXPIRING_CONTROL;
    }

    /**
     * Gets the number of seconds until the password expires returned by the 
     * server.
     * @return int the number of seconds until the password expires.
     * @exception java.lang.NumberFormatException If the server returned an
     * undecipherable message. In this case, use <CODE>getMessage</CODE> to 
     * retrieve the message as a string.
     */
    public int getSecondsToExpiration() {
        return Integer.parseInt( m_msg );
    }

    /**
     * Gets the value associated with this control parsed as a string.
     * @return the value associated with this control parsed as a string.
     */
    public String getMessage() {
        return m_msg;
    }

    /**
     * @param controls an array of <CODE>LDAPControl</CODE> objects,
     * representing the controls returned by the server.
     * after a search.  To get these controls, use the
     * <CODE>getResponseControls</CODE> method of the
     * <CODE>LDAPConnection</CODE> class.
     * @return an error message string, or null if none is in the control.
     * @see com.sun.identity.shared.ldap.LDAPConnection#getResponseControls
     * @deprecated LDAPPasswordExpiringControl controls are now automatically
     * instantiated.
     */
    public static String parseResponse( LDAPControl[] controls ) {
        return LDAPStringControl.parseResponse( controls, EXPIRING );
    }
    
    public String toString() {
         StringBuffer sb = new StringBuffer("{PasswordExpiringCtrl:");
        
        sb.append(" isCritical=");
        sb.append(isCritical());
        
        sb.append(" msg=");
        sb.append(m_msg);
        
        sb.append("}");

        return sb.toString();
    }
    
}

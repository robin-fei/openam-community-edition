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
package com.sun.identity.shared.ldap.util;

import java.util.Vector;
import com.sun.identity.shared.ldap.LDAPModification;

/**
 * An object of this class represents the content of an LDIF record that
 * specifies modifications to an entry.  This class implements the
 * <CODE>LDIFContent</CODE> interface.
 * <P>
 *
 * To get this object from an <CODE>LDIFRecord</CODE> object,
 * use the <CODE>getContent</CODE> method and cast the return value as
 * <CODE>LDIFModifyContent</CODE>.
 * <P>
 *
 * @version 1.0
 * @see com.sun.identity.shared.ldap.util.LDIFRecord#getContent
 * @deprecated As of ForgeRock OpenAM 10.
 */
public class LDIFModifyContent extends LDIFBaseContent {
    /**
     * Internal variables
     */
    private Vector m_mods = new Vector();
    static final long serialVersionUID = -710573832339780084L;

    /**
     * Constructs an empty <CODE>LDIFModifyContent</CODE> object.
     * To specify the modifications to be made to the entry, use
     * the <CODE>addElement</CODE> method.
     * @see com.sun.identity.shared.ldap.util.LDIFModifyContent#addElement
     */
    public LDIFModifyContent() {
    }

    /**
     * Returns the content type. You can use this with the
     * <CODE>getContent</CODE> method of the <CODE>LDIFRecord</CODE>
     * object to determine the type of content specified in the record.
     * @return the content type (which is
     * <CODE>LDIFContent.MODIFICATION_CONTENT</CODE>).
     * @see com.sun.identity.shared.ldap.util.LDIFRecord#getContent
     */
    public int getType() {
        return MODIFICATION_CONTENT;
    }

    /**
     * Specifies an additional modification that should be made to
     * the entry.
     * @param mod <CODE>LDAPModification</CODE> object representing
     * the change to make to the entry
     * @see com.sun.identity.shared.ldap.LDAPModification
     */
    public void addElement(LDAPModification mod) {
        m_mods.addElement(mod);
    }

    /**
     * Retrieves the list of the modifications specified in the content
     * of the LDIF record.
     * @return an array of <CODE>LDAPModification</CODE> objects that
     * represent the modifications specified in the content of the LDIF record.
     * @see com.sun.identity.shared.ldap.LDAPModification
     */
    public LDAPModification[] getModifications() {
        LDAPModification mods[] = new LDAPModification[m_mods.size()];
        for (int i = 0; i < m_mods.size(); i++) {
            mods[i] = (LDAPModification)m_mods.elementAt(i);
        }
        return mods;
    }

    /**
     * Returns the string representation of the content of the LDIF record.
     * @return the string representation of the content of the LDIF record.
     */
    public String toString() {
        String s = "";
        for (int i = 0; i < m_mods.size(); i++) {
            s = s + ((LDAPModification)m_mods.elementAt(i)).toString();
        }
        if ( getControls() != null ) {
            s += getControlString();
        }
        return "LDIFModifyContent {" + s + "}";
    }
}

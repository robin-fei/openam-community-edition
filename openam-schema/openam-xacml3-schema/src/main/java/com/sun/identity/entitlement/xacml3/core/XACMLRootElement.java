/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 ForgeRock US, Inc. All Rights Reserved
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
 *
 */
package com.sun.identity.entitlement.xacml3.core;


public interface XACMLRootElement {

    /**
     * XACML 3 Default Namespace.
     */
    public static final String XACML3_NAMESPACE = "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17";

    /**
     * Required Method within all Implementing Classes.
     *
     * @return String of Marshaled XACML POJO to XML.
     */
    public String toXML();

}

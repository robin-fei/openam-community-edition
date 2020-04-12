/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: IDebugProvider.java,v 1.2 2008/06/25 05:53:01 qcheng Exp $
 * *
 */

/**
 * Portions Copyrighted 2014 ForgeRock As
 */

package com.sun.identity.shared.debug;

/**
 * Allows a pluggable implementation of the Debug service within the Access
 * Manager SDK. The implementation of this interface as well as the
 * <code>com.sun.identity.util.IDebug</code> interface togehter provide the
 * necessary functionality to replace or enhance the Debug service.
 * </p>
 */
public interface IDebugProvider {
    /**
     * Returns an instance of <code>IDebug</code> type which
     * enable the <code>com.iplanet.am.util.Debug</code> instances to delegate
     * the service calls to the provider specific implementation.
     *
     * @param debugName name of the debug instance which will be returned.
     * @return an instance of <code>IDebug</code> type known by the given
     * <code>debugName</code> value.
     */
    IDebug getInstance(String debugName);
}

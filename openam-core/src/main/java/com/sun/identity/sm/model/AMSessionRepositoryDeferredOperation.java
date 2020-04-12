/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS Inc. All Rights Reserved
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
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyrighted [2010-2012] [ForgeRock AS]
 *
 */
package com.sun.identity.sm.model;

import java.io.Serializable;

/**
 * AMSession Replication Event
 *
 * Provides necessary information to perform a Session Repository Event against the
 * appropriate backend.
 *
 * @author jeff.schenk@forgerock.org
 *
 */
public class AMSessionRepositoryDeferredOperation implements Serializable {
    private static final long serialVersionUID = 101L;   //  10.1

    /**
     * Payload Operation.
     */
    private Enum<AMSessionRepositoryDeferredOperationType> operation;

    /**
     * Parameters for Operation
     */
    private Object[] parameters = null;

    /**
     * Constructor using AMRootEntity, so any Object extending our root object can be
     * used as a payload for our event.
     *
     * @param operation
     * @param parameters
     */
    public AMSessionRepositoryDeferredOperation(Enum<AMSessionRepositoryDeferredOperationType> operation, Object[] parameters) {
        this.setOperation(operation);
    }

    public Enum<AMSessionRepositoryDeferredOperationType> getOperation() {
        return operation;
    }

    public void setOperation(Enum<AMSessionRepositoryDeferredOperationType> operation) {
        this.operation = operation;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }
}

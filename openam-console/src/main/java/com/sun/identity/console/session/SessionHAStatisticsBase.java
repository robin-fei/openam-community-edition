/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007 Sun Microsystems Inc. All Rights Reserved
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
 *
 */

package com.sun.identity.console.session;

import com.iplanet.jato.view.event.RequestInvocationEvent;
import com.sun.identity.console.base.model.AMFormatUtils;
import com.sun.identity.console.base.model.AMModel;
import com.sun.identity.sm.SMSSchema;
import com.sun.web.ui.model.CCPageTitleModel;

import java.text.MessageFormat;

public abstract class SessionHAStatisticsBase
    extends SMProfileViewBean
{
    public SessionHAStatisticsBase(String name) {
        super(name);
    }

    protected static final String SESSION_HA_STATISTICS = "SessionHAStatistics";

    public CCPageTitleModel ptModel;

    /*
     * forward on to home session view page.
     */
    protected void forwardToSessionView(RequestInvocationEvent event) {
        // and now forward on to the session page...
        SMProfileViewBean vb = (SMProfileViewBean)getViewBean(
                SMProfileViewBean.class);
        passPgSessionMap(vb);
        vb.forwardTo(getRequestContext());
    }

}                                                                     

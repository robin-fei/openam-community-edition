#!/bin/sh
#
#------------------------------------------------------------------------------
#README file for OpenAM stand alone client sdk
#policy evaluation sample
#------------------------------------------------------------------------------
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
#Copyright (c) 2007 Sun Microsystems Inc. All Rights Reserved
#
#The contents of this file are subject to the terms
#of the Common Development and Distribution License
#(the License). You may not use this file except in
#compliance with the License.
#
#You can obtain a copy of the License at
#https://opensso.dev.java.net/public/CDDLv1.0.html or
#opensso/legal/CDDLv1.0.txt
#See the License for the specific language governing
#permission and limitations under the License.
#
#When distributing Covered Code, include this CDDL
#Header Notice in each file and include the License file
#at opensso/legal/CDDLv1.0.txt.
#If applicable, add the following below the CDDL Header,
#with the fields enclosed by brackets [] replaced by
#your own identifying information:
#"Portions Copyrighted [year] [name of copyright owner]"
#
#$Id: run-policy-evaluation-sample.sh,v 1.6 2008/08/19 19:11:25 veiming Exp $
#
# Portions Copyrighted 2013-2014 ForgeRock AS
#------------------------------------------------------------------------------
#
#Runs the client policy evaluation sample
#
#Requires one parameter: the name of the  resource file that defines
#property values used by the sample
#Default is policyEvaluationSamples
#The corresponding file that would be read from classpath 
#is policyEvaluationSample.properites 
#from classpath.
#A default template is included 
#at ../resources/policyEvaluationSample.properties
#See the template for more information on the properties
#Please update it to match your deployment
#You have to create user and policy at the server to get right policy decision
#see ../resources/policyEvaluationSample.properties for more information
#
#Requires ../resources/AMConfig.properties 
#
#Must run "setup.sh" once to configure the client to find the OpenAM server.
#
# Then, run this script
java -classpath resources:lib/openam-clientsdk-${project.version}.jar:lib/servlet-api-${servlet-api.version}.jar:lib/openam-example-clientsdk-cli-${project.version}.jar samples.policy.PolicyEvaluationSample policyEvaluationSample

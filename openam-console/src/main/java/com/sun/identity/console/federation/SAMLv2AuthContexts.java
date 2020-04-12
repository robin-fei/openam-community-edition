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
 * $Id: SAMLv2AuthContexts.java,v 1.4 2009/05/11 21:15:39 asyhuang Exp $
 *
 * Portions Copyrighted 2014 ForgeRock AS.
 */

package com.sun.identity.console.federation;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * This is collections of authentication contexts. This object can be serialized
 * between view beans forwarding.
 */
public class SAMLv2AuthContexts implements Serializable {

    private Map<String, SAMLv2AuthContext> collections = new HashMap<String, SAMLv2AuthContext>();
    
    /**
     * Default Constructor.
     */
    public SAMLv2AuthContexts() {
    }
    
    public List<String> toSPAuthContextInfo() {

        List<String> list = new ArrayList<String>();
        for (SAMLv2AuthContext entry : collections.values()) {
            if (entry.supported.equals("true")) {
                StringBuilder str = new StringBuilder();
                str.append(entry.name).append("|").append(entry.level).append("|");
                if (entry.isDefault) {
                    str.append("default");
                }
                list.add(str.toString());
            }
        }
       
        return list;
    }
    
    public List<String> toIDPAuthContextInfo() {

        List<String> list = new ArrayList<String>();
        for (SAMLv2AuthContext entry : collections.values()) {
            if (entry.supported.equals("true")) {
                StringBuilder str = new StringBuilder();
                str.append(entry.name).append("|").append(entry.level).append("|");
                if (!entry.key.equals("none")) {
                    str.append(entry.key).append("=").append(entry.value);
                }
                str.append("|");
                if (entry.isDefault) {
                    str.append("default");
                }
                list.add(str.toString());
            }
        }

        return list;
    }
    /**
     * Returns true if collection is empty.
     *
     * @return true if collection is empty.
     */
    public boolean isEmpty() {
        return collections.isEmpty();
    }
    
    /**
     * Returns number of entries in collection.
     *
     * @return number of entries in collection.
     */
    public int size() {
        return collections.size();
    }
    
    public Map<String, SAMLv2AuthContext> getCollections(){
        return collections;
    }
    
    /**
     * Adds SAMLv2AuthContext to the collection
     *
     * @param name Name of entry.
     * @param supported true if this entry is supported.
     * @param level Level of entry.
     * @param isDefault true if it is default.
     */
    public void put(
        String name,  
        String supported, 
        String level, 
        boolean isDefault
    ) {
        SAMLv2AuthContext c = new SAMLv2AuthContext();
        c.name = name;
        c.supported = supported;
        c.key = "";
        c.value = "";
        c.level = level;               
        c.isDefault = isDefault;       
        collections.put(name, c);
    }
    
    /**
     * Adds SAMLv2AuthContext to the collection.
     *
     * @param name Name of entry.
     * @param supported true if this entry is supported.
     * @param key Key of entry.
     * @param value Value of entry.
     * @param level Level of entry.
     * @param isDefault true if it is default.
     */
    
    public void put(
        String name,
        String supported,
        String key,
        String value,    
        String level,
        boolean isDefault
    ) {
        SAMLv2AuthContext c = new SAMLv2AuthContext();
        c.name = name;
        c.supported = supported;                   
        c.key = key;     
        c.value = value;
        c.level = level;               
        c.isDefault = isDefault;                       
        collections.put(name, c);
    }
    
     /**
     * Adds SAMLv2AuthContext to the collection.
     *
     * @param name Name of entry.
     * @param supported true if this entry is supported.
     * @param authScheme key/value pair of authScheme  
     * @param level Level of entry.
     * @param isDefault true if it is default.
     */
    public void put(
        String name,
        String supported,
        String authScheme,       
        String level,
        boolean isDefault
    ) {
        SAMLv2AuthContext c = new SAMLv2AuthContext();
        c.name = name;
        c.supported = supported;   
        
        if(authScheme.length()!=0 && authScheme != null){
            int index = authScheme.lastIndexOf("=");
            c.value = authScheme.substring(index + 1);
            c.key = authScheme.substring(0,index);                      
        } else {
            c.value = "";
            c.key = "";
        }
        
        c.level = level;               
        c.isDefault = isDefault;                       
        collections.put(name, c);
    }

    /**
     * Returns SAMLv2AuthContext in the collection.
     *
     * @param name Name of entry to return.
     * @return SAMLv2AuthContext in the collection.
     */
    public SAMLv2AuthContext get(String name) {
        return collections.get(name);
    }
    
    public class SAMLv2AuthContext
        implements Serializable {
        public String name;
        public String supported;
        public String key;
        public String value;
        public String level;
        public boolean isDefault;
        
        public SAMLv2AuthContext() {
        }
       
        public String toString(){
            String str = "context=" + name 
                + "|support=" +  supported 
                + "|key=" +  key + "|value="
                +  value + "|level=" +  level
                + "|isDefault=" + isDefault ;
           
            return str;
        }
    }
}

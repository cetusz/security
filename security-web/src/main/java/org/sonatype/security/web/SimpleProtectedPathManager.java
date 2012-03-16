/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.security.web;

import org.apache.shiro.web.filter.mgt.FilterChainManager;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Simple wrapper around the Shiro FilterChainManager.
 * @since 2.5
 */
@Singleton
@Typed( ProtectedPathManager.class )
@Named( "simple" )
public class SimpleProtectedPathManager
    implements ProtectedPathManager
{
    private final FilterChainManager filterChainManager;

    @Inject
    public SimpleProtectedPathManager( FilterChainManager filterChainManager )
    {
        this.filterChainManager = filterChainManager;
    }


    public void addProtectedResource( String pathPattern, String filterExpression )
    {
        this.filterChainManager.createChain( pathPattern, filterExpression );
    }
}

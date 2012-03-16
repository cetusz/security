/**
 * Copyright (c) 2007-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
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

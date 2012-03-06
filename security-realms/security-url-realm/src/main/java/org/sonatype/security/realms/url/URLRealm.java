/**
 * Copyright (c) 2008 Sonatype, Inc. All rights reserved.
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
package org.sonatype.security.realms.url;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.inject.Description;
import org.sonatype.security.realms.url.config.UrlRealmConfiguration;
import org.sonatype.security.usermanagement.UserManager;
import org.sonatype.security.usermanagement.UserNotFoundException;

/**
 * A Realm that connects to a remote URL to verify authorization.<BR/>
 * All URL realm users are given the role defined by ${url-authentication-default-role}.<BR/>
 * NOTE: Redirects are NOT followed.
 * 
 * @author Brian Demers
 */
@Singleton
@Typed( value = Realm.class )
@Named( value = "url" )
@Description( value = "URL Realm" )
public class URLRealm
    extends AuthorizingRealm
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final UserManager userManager;

    private final UrlRealmConfiguration urlRealmConfiguration;


    @Inject
    public URLRealm( UrlRealmConfiguration urlRealmConfiguration, @Named( "url" )UserManager userManager )
    {
        super();
        this.urlRealmConfiguration = urlRealmConfiguration;
        this.userManager = userManager;
        this.setAuthenticationCachingEnabled( true );
    }

    @Override
    public String getName()
    {
        return "url";
    }
    
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo( AuthenticationToken token )
        throws AuthenticationException
    {

        UsernamePasswordToken upToken = (UsernamePasswordToken) token;

        AuthenticationInfo authInfo = null;
        
        String username = upToken.getUsername();
        String pass = String.valueOf( upToken.getPassword() );

        // if the user can authenticate we are good to go
        if ( this.authenticateViaUrl( username, pass ) )
        {
            authInfo = buildAuthenticationInfo( username, upToken.getPassword() );
        }
        else
        {
            throw new AccountException( "User '" + username + "' cannot be authenticated." );
        }


        return authInfo;
    }

    
    protected AuthenticationInfo buildAuthenticationInfo( String username, char[] password )
    {
        return new SimpleAuthenticationInfo( username, password, getName() );
    }

    @Override
    public boolean supports( AuthenticationToken token )
    {
        return UsernamePasswordToken.class.isAssignableFrom( token.getClass() );
    }

    private boolean authenticateViaUrl( String username, String password )
    {
        Client restClient = new Client( new Context(), Protocol.HTTP );

        ChallengeScheme scheme = ChallengeScheme.HTTP_BASIC;
        ChallengeResponse authentication = new ChallengeResponse( scheme, username, password );

        Request request = new Request();
        request.setResourceRef( this.urlRealmConfiguration.getConfiguration().getUrl() );
        request.setMethod( Method.GET );
        request.setChallengeResponse( authentication );

        Response response = restClient.handle( request );
        try
        {
            this.logger.debug( "User: " + username + " url validation status: " + response.getStatus() );
            return response.getStatus().isSuccess();
        }
        finally
        {
            if ( response != null )
            {
                response.release();
            }
        }
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo( PrincipalCollection principals )
    {

        if ( principals == null )
        {
            throw new AuthorizationException( "Cannot authorize with no principals." );
        }

        String username = (String) principals.iterator().next();

        // we need to make sure the user can be managed by this realm
        try
        {
            if ( this.userManager.getUser( username ) == null )
            {
                throw new AuthorizationException( "User '" + username + "' is not managed by this realm." );
            }
        }
        catch ( UserNotFoundException e )
        {
            throw new AuthorizationException( "User '" + username + "' is not managed by this realm.", e );
        }

        // we don't have a list of users for this realm, so the default role effects ALL users

        Set<String> roles = new HashSet<String>();
        roles.add( this.urlRealmConfiguration.getConfiguration().getDefaultRole() );

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo( roles );

        return info;
    }
}

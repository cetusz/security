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
package org.sonatype.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import net.sf.ehcache.CacheManager;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.plexus.appevents.ApplicationEventMulticaster;
import org.sonatype.plexus.appevents.Event;
import org.sonatype.plexus.appevents.EventListener;
import org.sonatype.security.authentication.AuthenticationException;
import org.sonatype.security.authorization.AuthorizationException;
import org.sonatype.security.authorization.AuthorizationManager;
import org.sonatype.security.authorization.NoSuchAuthorizationManagerException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.configuration.SecurityConfigurationManager;
import org.sonatype.security.email.NullSecurityEmailer;
import org.sonatype.security.email.SecurityEmailer;
import org.sonatype.security.events.AuthorizationConfigurationChangedEvent;
import org.sonatype.security.events.SecurityConfigurationChangedEvent;
import org.sonatype.security.events.UserPrincipalsExpiredEvent;
import org.sonatype.security.usermanagement.InvalidCredentialsException;
import org.sonatype.security.usermanagement.NoSuchUserManagerException;
import org.sonatype.security.usermanagement.PasswordGenerator;
import org.sonatype.security.usermanagement.RoleIdentifier;
import org.sonatype.security.usermanagement.RoleMappingUserManager;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserManager;
import org.sonatype.security.usermanagement.UserManagerFacade;
import org.sonatype.security.usermanagement.UserNotFoundException;
import org.sonatype.security.usermanagement.UserSearchCriteria;
import org.sonatype.security.usermanagement.UserStatus;
import org.sonatype.sisu.ehcache.CacheManagerComponent;

/**
 * This implementation wraps a Shiro SecurityManager, and adds user management.
 */
@Singleton
@Typed( SecuritySystem.class )
@Named( "default" )
public class DefaultSecuritySystem
    implements SecuritySystem, EventListener
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private SecurityConfigurationManager securityConfiguration;

    private Map<String, RealmSecurityManager> securityManagers;

    private CacheManagerComponent cacheManagerComponent;

    private UserManagerFacade userManagerFacade;

    private Map<String, Realm> realmMap;

    private Map<String, AuthorizationManager> authorizationManagers;

    private PasswordGenerator passwordGenerator;

    private ApplicationEventMulticaster eventMulticaster;

    private List<SecurityEmailer> securityEmailers;

    private SecurityEmailer securityEmailer;

    private static final String ALL_ROLES_KEY = "all";

    @Inject
    public DefaultSecuritySystem( List<SecurityEmailer> securityEmailers, ApplicationEventMulticaster eventMulticaster,
                                  PasswordGenerator passwordGenerator,
                                  Map<String, AuthorizationManager> authorizationManagers, Map<String, Realm> realmMap,
                                  SecurityConfigurationManager securityConfiguration,
                                  Map<String, RealmSecurityManager> securityManagers,
                                  CacheManagerComponent cacheManagerComponent, UserManagerFacade userManagerFacade )
    {
        this.securityEmailers = securityEmailers;
        this.eventMulticaster = eventMulticaster;
        this.passwordGenerator = passwordGenerator;
        this.authorizationManagers = authorizationManagers;
        this.realmMap = realmMap;
        this.securityConfiguration = securityConfiguration;
        this.securityManagers = securityManagers;
        this.cacheManagerComponent = cacheManagerComponent;

        this.eventMulticaster.addEventListener( this );
        this.userManagerFacade = userManagerFacade;
        SecurityUtils.setSecurityManager( this.getSecurityManager() );
    }

    public Subject login( AuthenticationToken token )
        throws AuthenticationException
    {
        try
        {
            Subject subject = this.getSubject();
            subject.login( token );
            return subject;
        }
        catch ( org.apache.shiro.authc.AuthenticationException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }

    public AuthenticationInfo authenticate( AuthenticationToken token )
        throws AuthenticationException
    {
        try
        {
            return this.getSecurityManager().authenticate( token );
        }
        catch ( org.apache.shiro.authc.AuthenticationException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }

    // public Subject runAs( PrincipalCollection principal )
    // {
    // // TODO: we might need to bind this to the ThreadContext for this thread
    // // however if we do this we would need to unbind it so it doesn't leak
    // DelegatingSubject fakeLoggedInSubject = new DelegatingSubject( principal, true, null, null,
    // this.getApplicationSecurityManager() );
    //
    // // fake the login
    // ThreadContext.bind( fakeLoggedInSubject );
    // // this is un-bind when the user logs out.
    //
    // return fakeLoggedInSubject;
    // }

    public Subject getSubject()
    {
        // this gets the currently bound Subject to the thread
        return SecurityUtils.getSubject();
    }

    public void logout( Subject subject )
    {
        subject.logout();
    }

    public boolean isPermitted( PrincipalCollection principal, String permission )
    {
        return this.getSecurityManager().isPermitted( principal, permission );
    }

    public boolean[] isPermitted( PrincipalCollection principal, List<String> permissions )
    {
        return this.getSecurityManager().isPermitted( principal, permissions.toArray( new String[permissions.size()] ) );
    }

    public void checkPermission( PrincipalCollection principal, String permission )
        throws AuthorizationException
    {
        try
        {
            this.getSecurityManager().checkPermission( principal, permission );
        }
        catch ( org.apache.shiro.authz.AuthorizationException e )
        {
            throw new AuthorizationException( e.getMessage(), e );
        }

    }

    public void checkPermission( PrincipalCollection principal, List<String> permissions )
        throws AuthorizationException
    {
        try
        {
            this.getSecurityManager().checkPermissions( principal, permissions.toArray( new String[permissions.size()] ) );
        }
        catch ( org.apache.shiro.authz.AuthorizationException e )
        {
            throw new AuthorizationException( e.getMessage(), e );
        }
    }

    public boolean hasRole( PrincipalCollection principals, String string )
    {
        return this.getSecurityManager().hasRole( principals, string );
    }

    private Collection<Realm> getRealmsFromConfigSource()
    {
        List<Realm> realms = new ArrayList<Realm>();

        List<String> realmIds = this.securityConfiguration.getRealms();

        for ( String realmId : realmIds )
        {
            if ( this.realmMap.containsKey( realmId ) )
            {
                realms.add( this.realmMap.get( realmId ) );
            }
            else
            {
                this.logger.debug( "Failed to look up realm as a component, trying a Class.forName()" );
                // If that fails, will simply use reflection to load
                try
                {
                    realms.add( (Realm) Class.forName( realmId ).newInstance() );
                }
                catch ( Exception e )
                {
                    this.logger.error( "Unable to lookup security realms", e );
                }
            }
        }

        return realms;
    }

    public Set<Role> listRoles()
    {
        Set<Role> roles = new HashSet<Role>();
        for ( AuthorizationManager authzManager : this.authorizationManagers.values() )
        {
            Set<Role> tmpRoles = authzManager.listRoles();
            if ( tmpRoles != null )
            {
                roles.addAll( tmpRoles );
            }
        }

        return roles;
    }

    public Set<Role> listRoles( String sourceId )
        throws NoSuchAuthorizationManagerException
    {
        if ( ALL_ROLES_KEY.equalsIgnoreCase( sourceId ) )
        {
            return this.listRoles();
        }
        else
        {
            AuthorizationManager authzManager = this.getAuthorizationManager( sourceId );
            return authzManager.listRoles();
        }
    }

    public Set<Privilege> listPrivileges()
    {
        Set<Privilege> privileges = new HashSet<Privilege>();
        for ( AuthorizationManager authzManager : this.authorizationManagers.values() )
        {
            Set<Privilege> tmpPrivileges = authzManager.listPrivileges();
            if ( tmpPrivileges != null )
            {
                privileges.addAll( tmpPrivileges );
            }
        }

        return privileges;
    }

    // *********************
    // * user management
    // *********************

    public User addUser( User user )
        throws NoSuchUserManagerException, InvalidConfigurationException
    {
        return this.addUser( user, this.generatePassword() );
    }

    public User addUser( User user, String password )
        throws NoSuchUserManagerException, InvalidConfigurationException
    {
        // if the password is null, generate one
        if ( password == null )
        {
            password = this.generatePassword();
        }

        // first save the user
        // this is the UserManager that owns the user
        UserManager userManager = userManagerFacade.getUserManager( user.getSource() );

        if ( !userManager.supportsWrite() )
        {
            throw new InvalidConfigurationException( "UserManager: " + userManager.getSource()
                + " does not support writing." );
        }

        userManager.addUser( user, password );

        // then save the users Roles
        for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
        {
            // skip the user manager that owns the user, we already did that
            // these user managers will only save roles
            if ( !tmpUserManager.getSource().equals( user.getSource() )
                && RoleMappingUserManager.class.isInstance( tmpUserManager ) )
            {
                try
                {
                    RoleMappingUserManager roleMappingUserManager = (RoleMappingUserManager) tmpUserManager;
                    roleMappingUserManager.setUsersRoles( user.getUserId(), user.getSource(),
                                                          RoleIdentifier.getRoleIdentifiersForSource( user.getSource(),
                                                                                                      user.getRoles() ) );
                }
                catch ( UserNotFoundException e )
                {
                    this.logger.debug( "User '" + user.getUserId() + "' is not managed by the usermanager: "
                        + tmpUserManager.getSource() );
                }
            }
        }

        if ( UserStatus.active.equals( user.getStatus() ) )
        {
            // don't forget to email the user (if the user being added is active)!
            getSecurityEmailer().sendNewUserCreated( user.getEmailAddress(), user.getUserId(), password );
        }

        return user;
    }

    public User updateUser( User user )
        throws UserNotFoundException, NoSuchUserManagerException, InvalidConfigurationException
    {
        // first update the user
        // this is the UserManager that owns the user
        UserManager userManager = userManagerFacade.getUserManager( user.getSource() );

        if ( !userManager.supportsWrite() )
        {
            throw new InvalidConfigurationException( "UserManager: " + userManager.getSource()
                + " does not support writing." );
        }

        userManager.updateUser( user );

        // then save the users Roles
        for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
        {
            // skip the user manager that owns the user, we already did that
            // these user managers will only save roles
            if ( !tmpUserManager.getSource().equals( user.getSource() )
                && RoleMappingUserManager.class.isInstance( tmpUserManager ) )
            {
                try
                {
                    RoleMappingUserManager roleMappingUserManager = (RoleMappingUserManager) tmpUserManager;
                    roleMappingUserManager.setUsersRoles( user.getUserId(), user.getSource(),
                                                          RoleIdentifier.getRoleIdentifiersForSource( user.getSource(),
                                                                                                      user.getRoles() ) );
                }
                catch ( UserNotFoundException e )
                {
                    this.logger.debug( "User '" + user.getUserId() + "' is not managed by the usermanager: "
                        + tmpUserManager.getSource() );
                }
            }
        }

        // clear the realm caches
        this.eventMulticaster.notifyEventListeners( new AuthorizationConfigurationChangedEvent( null ) );

        return user;
    }

    public void deleteUser( String userId )
        throws UserNotFoundException
    {
        User user = this.getUser( userId );
        try
        {
            this.deleteUser( userId, user.getSource() );
        }
        catch ( NoSuchUserManagerException e )
        {
            this.logger.error( "User manager returned user, but could not be found: " + e.getMessage(), e );
            throw new IllegalStateException( "User manager returned user, but could not be found: " + e.getMessage(), e );
        }
    }

    public void deleteUser( String userId, String source )
        throws UserNotFoundException, NoSuchUserManagerException
    {
        UserManager userManager = userManagerFacade.getUserManager( source );
        userManager.deleteUser( userId );

        this.eventMulticaster.notifyEventListeners( new UserPrincipalsExpiredEvent( null, userId, source ) );
    }

    public Set<RoleIdentifier> getUsersRoles( String userId, String source )
        throws UserNotFoundException, NoSuchUserManagerException
    {
        User user = this.getUser( userId, source );
        return user.getRoles();
    }

    public void setUsersRoles( String userId, String source, Set<RoleIdentifier> roleIdentifiers )
        throws InvalidConfigurationException, UserNotFoundException
    {
        // TODO: this is a bit sticky, what we really want to do is just expose the RoleMappingUserManagers this way (i
        // think), maybe this is too generic

        boolean foundUser = false;

        for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
        {
            if ( RoleMappingUserManager.class.isInstance( tmpUserManager ) )
            {
                RoleMappingUserManager roleMappingUserManager = (RoleMappingUserManager) tmpUserManager;
                try
                {
                    foundUser = true;
                    roleMappingUserManager.setUsersRoles( userId,
                                                          source,
                                                          RoleIdentifier.getRoleIdentifiersForSource( tmpUserManager.getSource(),
                                                                                                      roleIdentifiers ) );
                }
                catch ( UserNotFoundException e )
                {
                    this.logger.debug( "User '" + userId + "' is not managed by the usermanager: "
                        + tmpUserManager.getSource() );
                }
            }
        }

        if ( !foundUser )
        {
            throw new UserNotFoundException( userId );
        }
    }

    public User getUser( String userId )
        throws UserNotFoundException
    {
        List<UserManager> orderedUserManagers = this.orderUserManagers();
        for ( UserManager userManager : orderedUserManagers )
        {
            try
            {
                return this.getUser( userId, userManager.getSource() );
            }
            catch ( UserNotFoundException e )
            {
                this.logger.debug( "User: '" + userId + "' was not found in: '" + userManager.getSource() + "' " );
            }
            catch ( NoSuchUserManagerException e )
            {
                // we should NEVER bet here
                this.logger.warn( "UserManager: '" + userManager.getSource()
                    + "' was not found, but is in the list of UserManagers", e );
            }
        }
        throw new UserNotFoundException( userId );
    }

    public User getUser( String userId, String source )
        throws UserNotFoundException, NoSuchUserManagerException
    {
        // first get the user
        // this is the UserManager that owns the user
        UserManager userManager = userManagerFacade.getUserManager( source );
        User user = userManager.getUser( userId );

        if ( user == null )
        {
            throw new UserNotFoundException( userId );
        }

        // add roles from other user managers
        this.addOtherRolesToUser( user );

        return user;
    }

    public Set<User> listUsers()
    {
        Set<User> users = new HashSet<User>();

        for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
        {
            users.addAll( tmpUserManager.listUsers() );
        }

        // now add all the roles to the users
        for ( User user : users )
        {
            // add roles from other user managers
            this.addOtherRolesToUser( user );
        }

        return users;
    }

    public Set<User> searchUsers( UserSearchCriteria criteria )
    {
        Set<User> users = new HashSet<User>();

        // if the source is not set search all realms.
        if ( StringUtils.isEmpty( criteria.getSource() ) )
        {
            // search all user managers
            for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
            {
                Set<User> result = tmpUserManager.searchUsers( criteria );
                if ( result != null )
                {
                    users.addAll( result );
                }
            }
        }
        else
        {
            try
            {
                users.addAll( userManagerFacade.getUserManager( criteria.getSource() ).searchUsers( criteria ) );
            }
            catch ( NoSuchUserManagerException e )
            {
                this.logger.warn( "UserManager: " + criteria.getSource() + " was not found.", e );
            }
        }

        // now add all the roles to the users
        for ( User user : users )
        {
            // add roles from other user managers
            this.addOtherRolesToUser( user );
        }

        return users;
    }

    /**
     * We need to order the UserManagers the same way as the Realms are ordered. We need to be able to find a user based
     * on the ID. This my never go away, but the current reason why we need it is:
     * https://issues.apache.org/jira/browse/KI-77 There is no (clean) way to resolve a realms roles into permissions.
     * take a look at the issue and VOTE!
     * 
     * @return the list of UserManagers in the order (as close as possible) to the list of realms.
     */
    private List<UserManager> orderUserManagers()
    {
        List<UserManager> orderedLocators = new ArrayList<UserManager>();

        List<UserManager> unOrderdLocators = new ArrayList<UserManager>( userManagerFacade.getUserManagers().values() );

        Map<String, UserManager> realmToUserManagerMap = new HashMap<String, UserManager>();

        for ( UserManager userManager : userManagerFacade.getUserManagers().values() )
        {
            if ( userManager.getAuthenticationRealmName() != null )
            {
                realmToUserManagerMap.put( userManager.getAuthenticationRealmName(), userManager );
            }
        }

        // get the sorted order of realms from the realm locator
        Collection<Realm> realms = this.getSecurityManager().getRealms();

        for ( Realm realm : realms )
        {
            // now user the realm.name to find the UserManager
            if ( realmToUserManagerMap.containsKey( realm.getName() ) )
            {
                UserManager userManager = realmToUserManagerMap.get( realm.getName() );
                // remove from unorderd and add to orderd
                unOrderdLocators.remove( userManager );
                orderedLocators.add( userManager );
            }
        }

        // now add all the un-ordered ones to the ordered ones, this way they will be at the end of the ordered list
        orderedLocators.addAll( unOrderdLocators );

        return orderedLocators;

    }

    private void addOtherRolesToUser( User user )
    {
        // then save the users Roles
        for ( UserManager tmpUserManager : userManagerFacade.getUserManagers().values() )
        {
            // skip the user manager that owns the user, we already did that
            // these user managers will only have roles
            if ( !tmpUserManager.getSource().equals( user.getSource() )
                && RoleMappingUserManager.class.isInstance( tmpUserManager ) )
            {
                try
                {
                    RoleMappingUserManager roleMappingUserManager = (RoleMappingUserManager) tmpUserManager;
                    Set<RoleIdentifier> roleIdentifiers =
                        roleMappingUserManager.getUsersRoles( user.getUserId(), user.getSource() );
                    if ( roleIdentifiers != null )
                    {
                        user.addAllRoles( roleIdentifiers );
                    }
                }
                catch ( UserNotFoundException e )
                {
                    this.logger.debug( "User '" + user.getUserId() + "' is not managed by the usermanager: "
                        + tmpUserManager.getSource() );
                }
            }
        }
    }

    public AuthorizationManager getAuthorizationManager( String source )
        throws NoSuchAuthorizationManagerException
    {
        if ( !this.authorizationManagers.containsKey( source ) )
        {
            throw new NoSuchAuthorizationManagerException( "AuthorizationManager with source: '" + source
                + "' could not be found." );
        }

        return this.authorizationManagers.get( source );
    }

    public String getAnonymousUsername()
    {
        return this.securityConfiguration.getAnonymousUsername();
    }

    public boolean isAnonymousAccessEnabled()
    {
        return this.securityConfiguration.isAnonymousAccessEnabled();
    }

    public boolean isSecurityEnabled()
    {
        return this.securityConfiguration.isEnabled();
    }

    public void changePassword( String userId, String oldPassword, String newPassword )
        throws UserNotFoundException, InvalidCredentialsException, InvalidConfigurationException
    {
        // first authenticate the user
        try
        {
            UsernamePasswordToken authenticationToken = new UsernamePasswordToken( userId, oldPassword );
            if ( this.getSecurityManager().authenticate( authenticationToken ) == null )
            {
                throw new InvalidCredentialsException();
            }
        }
        catch ( org.apache.shiro.authc.AuthenticationException e )
        {
            this.logger.debug( "User failed to change password reason: " + e.getMessage(), e );
            throw new InvalidCredentialsException();
        }

        // if that was good just change the password
        this.changePassword( userId, newPassword );
    }

    public void changePassword( String userId, String newPassword )
        throws UserNotFoundException, InvalidConfigurationException
    {
        User user = this.getUser( userId );

        try
        {
            UserManager userManager = userManagerFacade.getUserManager( user.getSource() );
            userManager.changePassword( userId, newPassword );
        }
        catch ( NoSuchUserManagerException e )
        {
            // this should NEVER happen
            this.logger.warn( "User '" + userId + "' with source: '" + user.getSource()
                + "' but could not find the UserManager for that source." );
        }

    }

    public void forgotPassword( String userId, String email )
        throws UserNotFoundException, InvalidConfigurationException
    {
        UserSearchCriteria criteria = new UserSearchCriteria();
        criteria.setEmail( email );
        criteria.setUserId( userId );

        Set<User> users = this.searchUsers( criteria );

        boolean found = false;

        for ( User user : users )
        {
            // TODO: criteria does not do exact matching
            if ( user.getUserId().equalsIgnoreCase( userId.trim() ) && user.getEmailAddress().equals( email ) )
            {
                found = true;
                break;
            }
        }

        if ( !found )
        {
            throw new UserNotFoundException( email );
        }

        resetPassword( userId );
    }

    public void forgotUsername( String email )
        throws UserNotFoundException
    {
        UserSearchCriteria criteria = new UserSearchCriteria();
        criteria.setEmail( email );

        Set<User> users = this.searchUsers( criteria );

        List<String> userIds = new ArrayList<String>();
        for ( User user : users )
        {
            // ignore the anon user
            if ( !user.getUserId().equalsIgnoreCase( this.getAnonymousUsername() )
                && email.equalsIgnoreCase( user.getEmailAddress() ) )
            {
                userIds.add( user.getUserId() );
            }
        }

        if ( userIds.size() > 0 )
        {

            this.getSecurityEmailer().sendForgotUsername( email, userIds );
        }
        else
        {
            throw new UserNotFoundException( email );
        }

    }

    public void resetPassword( String userId )
        throws UserNotFoundException, InvalidConfigurationException
    {
        String newClearTextPassword = this.generatePassword();

        User user = this.getUser( userId );

        this.changePassword( userId, newClearTextPassword );

        // send email
        this.getSecurityEmailer().sendResetPassword( user.getEmailAddress(), newClearTextPassword );

    }

    private String generatePassword()
    {
        return this.passwordGenerator.generatePassword( 10, 10 );
    }

    private SecurityEmailer getSecurityEmailer()
    {
        if ( this.securityEmailer == null )
        {
            Iterator<SecurityEmailer> i = this.securityEmailers.iterator();
            if ( i.hasNext() )
            {
                this.securityEmailer = i.next();
            }
            else
            {
                this.logger.error( "Failed to find a SecurityEmailer" );
                this.securityEmailer = new NullSecurityEmailer();
            }
        }
        return this.securityEmailer;
    }

    public List<String> getRealms()
    {
        return new ArrayList<String>( this.securityConfiguration.getRealms() );
    }

    public void setRealms( List<String> realms )
        throws InvalidConfigurationException
    {
        this.securityConfiguration.setRealms( realms );
        this.securityConfiguration.save();

        // update the realms in the security manager
        this.setSecurityManagerRealms();
    }

    public void setAnonymousAccessEnabled( boolean enabled )
    {
        this.securityConfiguration.setAnonymousAccessEnabled( enabled );
        this.securityConfiguration.save();
    }

    public void setAnonymousUsername( String anonymousUsername )
        throws InvalidConfigurationException
    {
        this.securityConfiguration.setAnonymousUsername( anonymousUsername );
        this.securityConfiguration.save();
    }

    public void setSecurityEnabled( boolean enabled )
    {
        this.securityConfiguration.setEnabled( enabled );
        this.securityConfiguration.save();
    }

    public String getAnonymousPassword()
    {
        return this.securityConfiguration.getAnonymousPassword();
    }

    public void setAnonymousPassword( String anonymousPassword )
        throws InvalidConfigurationException
    {
        this.securityConfiguration.setAnonymousPassword( anonymousPassword );
        this.securityConfiguration.save();
    }

    public void start()
    {
        // reload the config
        this.securityConfiguration.clearCache();

        // if we are restarting this component the getCacheManager will be null
        // TODO: need better lifecycle management of cache (done), make sure this works with the NEXUS tests before
        // removing comment
        CacheManager cacheManager = this.cacheManagerComponent.getCacheManager();
        // if( cacheManager == null)
        // {
        // try
        // {
        // cacheManager = this.cacheManagerComponent.buildCacheManager( null );
        // }
        // catch ( IOException e )
        // {
        // throw new IllegalStateException( "Failed to restart CacheManagerComponent" );
        // }
        // }

        // setup the CacheManager ( this could be injected if we where less coupled with ehcache)
        // The plexus wrapper can interpolate the config
        EhCacheManager ehCacheManager = new EhCacheManager();
        ehCacheManager.setCacheManager( cacheManager );
        this.getSecurityManager().setCacheManager( ehCacheManager );

        if ( org.apache.shiro.util.Initializable.class.isInstance( this.getSecurityManager() ) )
        {
            ( (org.apache.shiro.util.Initializable) this.getSecurityManager() ).init();
        }
        this.setSecurityManagerRealms();
    }

    public void stop()
    {
        if ( getSecurityManager().getRealms() != null )
        {
            for ( Realm realm : getSecurityManager().getRealms() )
            {
                if ( AuthenticatingRealm.class.isInstance( realm ) )
                {
                    ( (AuthenticatingRealm) realm ).setAuthenticationCache( null );
                }
                if ( AuthorizingRealm.class.isInstance( realm ) )
                {
                    ( (AuthorizingRealm) realm ).setAuthorizationCache( null );
                }
            }
        }

        // we need to kill caches on stop
        getSecurityManager().destroy();
        // cacheManagerComponent.shutdown();
    }

    private void setSecurityManagerRealms()
    {
        getSecurityManager().setRealms( new ArrayList<Realm>( this.getRealmsFromConfigSource() ) );
    }

    private void clearRealmCaches()
    {
        // NOTE: we don't need to iterate all the Sec Managers, they use the same Realms, so one is fine.
        if ( this.getSecurityManager().getRealms() != null )
        {
            for ( Realm realm : this.getSecurityManager().getRealms() )
            {
                // check if its a AuthorizingRealm, if so clear the cache
                if ( AuthorizingRealm.class.isInstance( realm ) )
                {
                    // clear the cache
                    AuthorizingRealm aRealm = (AuthorizingRealm) realm;

                    Cache cache = aRealm.getAuthorizationCache();
                    if ( cache != null )
                    {
                        cache.clear();
                    }
                }
            }
        }
    }

    public void onEvent( Event<?> evt )
    {
        if ( AuthorizationConfigurationChangedEvent.class.isInstance( evt ) )
        {
            this.clearRealmCaches();
        }

        if ( SecurityConfigurationChangedEvent.class.isInstance( evt ) )
        {
            this.clearRealmCaches();
            this.securityConfiguration.clearCache();

            this.setSecurityManagerRealms();
        }
    }

    public RealmSecurityManager getSecurityManager()
    {
        return this.securityManagers.get( this.securityConfiguration.getSecurityManager() );
    }
}

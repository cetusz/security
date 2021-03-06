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
package org.sonatype.security.usermanagement.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.inject.Description;
import org.sonatype.security.SecuritySystem;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.model.CRole;
import org.sonatype.security.model.CUser;
import org.sonatype.security.model.CUserRoleMapping;
import org.sonatype.security.realms.tools.ConfigurationManager;
import org.sonatype.security.realms.tools.NoSuchRoleMappingException;
import org.sonatype.security.usermanagement.AbstractUserManager;
import org.sonatype.security.usermanagement.DefaultUser;
import org.sonatype.security.usermanagement.NoSuchUserManagerException;
import org.sonatype.security.usermanagement.RoleIdentifier;
import org.sonatype.security.usermanagement.RoleMappingUserManager;
import org.sonatype.security.usermanagement.StringDigester;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserManager;
import org.sonatype.security.usermanagement.UserNotFoundException;
import org.sonatype.security.usermanagement.UserSearchCriteria;
import org.sonatype.security.usermanagement.UserStatus;

/**
 * A UserManager backed by the security.xml file. This UserManger supports all User CRUD operations.
 * 
 * @author Brian Demers
 */
@Singleton
@Typed( UserManager.class )
@Named( "default" )
@Description( "Default" )
public class SecurityXmlUserManager
    extends AbstractUserManager
    implements RoleMappingUserManager
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public static final String SOURCE = "default";

    private final ConfigurationManager configuration;

    private final SecuritySystem securitySystem;

    @Inject
    public SecurityXmlUserManager( @Named( "resourceMerging" ) ConfigurationManager configuration,
                                   SecuritySystem securitySystem )
    {
        this.configuration = configuration;
        this.securitySystem = securitySystem;
    }

    protected CUser toUser( User user )
    {
        if ( user == null )
        {
            return null;
        }

        CUser secUser = new CUser();

        secUser.setId( user.getUserId() );
        secUser.setFirstName( user.getFirstName() );
        secUser.setLastName( user.getLastName() );
        secUser.setEmail( user.getEmailAddress() );
        secUser.setStatus( user.getStatus().name() );
        // secUser.setPassword( password )// DO NOT set the users password!

        return secUser;
    }

    protected User toUser( CUser cUser )
    {
        if ( cUser == null )
        {
            return null;
        }

        DefaultUser user = new DefaultUser();

        user.setUserId( cUser.getId() );
        user.setFirstName( cUser.getFirstName() );
        user.setLastName( cUser.getLastName() );
        user.setEmailAddress( cUser.getEmail() );
        user.setSource( SOURCE );
        user.setStatus( UserStatus.valueOf( cUser.getStatus() ) );
        user.setReadOnly( false );

        try
        {
            user.setRoles( this.getUsersRoles( cUser.getId(), SOURCE ) );
        }
        catch ( UserNotFoundException e )
        {
            // We should NEVER get here
            this.logger.warn( "Could not find user: '" + cUser.getId() + "' of source: '" + SOURCE
                + "' while looking up the users roles.", e );
        }

        return user;
    }

    protected RoleIdentifier toRole( String roleId )
    {
        if ( roleId == null )
        {
            return null;
        }

        try
        {
            CRole role = configuration.readRole( roleId );

            RoleIdentifier roleIdentifier = new RoleIdentifier( SOURCE, role.getId() );
            return roleIdentifier;
        }
        catch ( NoSuchRoleException e )
        {
            return null;
        }
    }

    public Set<User> listUsers()
    {
        Set<User> users = new HashSet<User>();

        for ( CUser user : configuration.listUsers() )
        {
            users.add( toUser( user ) );
        }

        return users;
    }

    public Set<String> listUserIds()
    {
        Set<String> userIds = new HashSet<String>();

        for ( CUser user : configuration.listUsers() )
        {
            userIds.add( user.getId() );
        }

        return userIds;
    }

    public User getUser( String userId )
        throws UserNotFoundException
    {
        User user = toUser( configuration.readUser( userId ) );
        return user;
    }

    public String getSource()
    {
        return SOURCE;
    }

    public boolean supportsWrite()
    {
        return true;
    }

    public User addUser( User user, String password )
        throws InvalidConfigurationException
    {
        CUser secUser = this.toUser( user );
        secUser.setPassword( this.hashPassword( password ) );
        this.configuration.createUser( secUser, this.getRoleIdsFromUser( user ) );
        this.saveConfiguration();

        // TODO: i am starting to feel we shouldn't return a user.
        return user;
    }

    public void changePassword( String userId, String newPassword )
        throws UserNotFoundException, InvalidConfigurationException
    {
        CUser secUser = this.configuration.readUser( userId );
        Set<String> roles = new HashSet<String>();
        try
        {
            CUserRoleMapping userRoleMapping = this.configuration.readUserRoleMapping( userId, SOURCE );
            roles.addAll( userRoleMapping.getRoles() );
        }
        catch ( NoSuchRoleMappingException e )
        {
            this.logger.debug( "User: " + userId + " has no roles." );
        }
        secUser.setPassword( this.hashPassword( newPassword ) );
        this.configuration.updateUser( secUser, new HashSet<String>( roles ) );
        this.saveConfiguration();
    }

    public User updateUser( User user )
        throws UserNotFoundException, InvalidConfigurationException
    {
        // we need to pull the users password off off the old user object
        CUser oldSecUser = this.configuration.readUser( user.getUserId() );
        CUser newSecUser = this.toUser( user );
        newSecUser.setPassword( oldSecUser.getPassword() );

        this.configuration.updateUser( newSecUser, this.getRoleIdsFromUser( user ) );
        this.saveConfiguration();
        return user;
    }

    public void deleteUser( String userId )
        throws UserNotFoundException
    {
        this.configuration.deleteUser( userId );
        this.saveConfiguration();
    }

    public Set<RoleIdentifier> getUsersRoles( String userId, String source )
        throws UserNotFoundException
    {
        Set<RoleIdentifier> roles = new HashSet<RoleIdentifier>();

        CUserRoleMapping roleMapping;
        try
        {
            roleMapping = this.configuration.readUserRoleMapping( userId, source );

            if ( roleMapping != null )
            {
                for ( String roleId : (List<String>) roleMapping.getRoles() )
                {
                    RoleIdentifier role = toRole( roleId );
                    if ( role != null )
                    {
                        roles.add( role );
                    }
                }
            }
        }
        catch ( NoSuchRoleMappingException e )
        {
            this.logger.debug( "No user role mapping found for user: " + userId );
        }
        return roles;
    }

    private void saveConfiguration()
    {
        this.configuration.save();
    }

    public Set<User> searchUsers( UserSearchCriteria criteria )
    {
        Set<User> users = new HashSet<User>();
        users.addAll( this.filterListInMemeory( this.listUsers(), criteria ) );

        // we also need to search through the user role mappings.

        List<CUserRoleMapping> roleMappings = this.configuration.listUserRoleMappings();
        for ( CUserRoleMapping roleMapping : roleMappings )
        {
            if ( !SOURCE.equals( roleMapping.getSource() ) )
            {
                if ( this.matchesCriteria( roleMapping.getUserId(), roleMapping.getSource(), roleMapping.getRoles(),
                                           criteria ) )
                {
                    try
                    {
                        User user = this.getSecuritySystem().getUser( roleMapping.getUserId(), roleMapping.getSource() );
                        users.add( user );
                    }
                    catch ( UserNotFoundException e )
                    {
                        this.logger.debug( "User: '" + roleMapping.getUserId() + "' of source: '"
                                               + roleMapping.getSource() + "' could not be found.", e );
                    }
                    catch ( NoSuchUserManagerException e )
                    {
                        this.logger.warn( "User: '" + roleMapping.getUserId() + "' of source: '"
                                              + roleMapping.getSource() + "' could not be found.", e );
                    }

                }
            }
        }

        return users;
    }

    private SecuritySystem getSecuritySystem()
    {
        return this.securitySystem;
    }

    private String hashPassword( String clearPassword )
    {
        // set the password if its not null
        if ( clearPassword != null && clearPassword.trim().length() > 0 )
        {
            return StringDigester.getSha1Digest( clearPassword );
        }

        return clearPassword;
    }

    public void setUsersRoles( String userId, String userSource, Set<RoleIdentifier> roleIdentifiers )
        throws UserNotFoundException, InvalidConfigurationException
    {
        // delete if no roleIdentifiers
        if ( roleIdentifiers == null || roleIdentifiers.isEmpty() )
        {
            try
            {
                this.configuration.deleteUserRoleMapping( userId, userSource );
            }
            catch ( NoSuchRoleMappingException e )
            {
                this.logger.debug( "User role mapping for user: " + userId + " source: " + userSource
                    + " could not be deleted because it does not exist." );
            }
        }
        else
        {
            CUserRoleMapping roleMapping = new CUserRoleMapping();
            roleMapping.setUserId( userId );
            roleMapping.setSource( userSource );

            for ( RoleIdentifier roleIdentifier : roleIdentifiers )
            {
                // make sure we only save roles that we manage
                // TODO: although we shouldn't need to worry about this.
                if ( this.getSource().equals( roleIdentifier.getSource() ) )
                {
                    roleMapping.addRole( roleIdentifier.getRoleId() );
                }
            }

            // try to update first
            try
            {
                this.configuration.updateUserRoleMapping( roleMapping );
            }
            catch ( NoSuchRoleMappingException e )
            {
                // update failed try create
                this.logger.debug( "Update of user role mapping for user: " + userId + " source: " + userSource
                    + " did not exist, creating new one." );
                this.configuration.createUserRoleMapping( roleMapping );
            }
        }

        // save the config
        this.saveConfiguration();

    }

    public String getAuthenticationRealmName()
    {
        return "XmlAuthenticatingRealm";
    }

    private Set<String> getRoleIdsFromUser( User user )
    {
        Set<String> roles = new HashSet<String>();
        for ( RoleIdentifier roleIdentifier : user.getRoles() )
        {
            // TODO: should we just grab the Default roles?
            // these users are managed by this realm so they should ONLY have roles from it anyway.
            roles.add( roleIdentifier.getRoleId() );
        }
        return roles;
    }
}

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
package org.sonatype.security.realms.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.sonatype.configuration.validation.ValidationMessage;
import org.sonatype.configuration.validation.ValidationRequest;
import org.sonatype.configuration.validation.ValidationResponse;
import org.sonatype.security.model.CPrivilege;
import org.sonatype.security.model.CRole;
import org.sonatype.security.model.CRoleKey;
import org.sonatype.security.model.CUser;
import org.sonatype.security.model.CUserRoleMapping;
import org.sonatype.security.model.Configuration;
import org.sonatype.security.realms.privileges.PrivilegeDescriptor;

@Singleton
@Typed( value = SecurityConfigurationValidator.class )
@Named( value = "default" )
public class DefaultConfigurationValidator
    implements SecurityConfigurationValidator
{
    @Inject
    private Logger logger;
    
    @Inject
    private ConfigurationIdGenerator idGenerator;

    @Inject
    private List<PrivilegeDescriptor> privilegeDescriptors;

    private static String DEFAULT_SOURCE = "default";

    public ValidationResponse validateModel( ValidationRequest<Configuration> request )
    {
        ValidationResponse response = new ValidationResponse();
        response.setContext( new SecurityValidationContext() );

        Configuration model = request.getConfiguration();

        SecurityValidationContext context = (SecurityValidationContext) response.getContext();

        List<CPrivilege> privs = model.getPrivileges();

        if ( privs != null )
        {
            for ( CPrivilege priv : privs )
            {
                response.append( validatePrivilege( context, priv, false ) );
            }
        }

        List<CRole> roles = model.getRoles();

        if ( roles != null )
        {
            for ( CRole role : roles )
            {
                response.append( validateRole( context, role, false ) );
            }
        }

        response.append( validateRoleContainment( context ) );

        List<CUser> users = model.getUsers();

        if ( users != null )
        {
            for ( CUser user : users )
            {
                Set<CRoleKey> roleIds = new HashSet<CRoleKey>();
                for ( CUserRoleMapping userRoleMapping : model.getUserRoleMappings() )
                {
                    if ( userRoleMapping.getUserId() != null && userRoleMapping.getUserId().equals( user.getId() )
                        && ( DEFAULT_SOURCE.equals( userRoleMapping.getSource() ) ) )
                    {
                        roleIds.addAll( userRoleMapping.getRoles() );
                    }
                }

                response.append( validateUser( context, user, roleIds, false ) );
            }
        }

        List<CUserRoleMapping> userRoleMappings = model.getUserRoleMappings();
        if ( userRoleMappings != null )
        {
            for ( CUserRoleMapping userRoleMapping : userRoleMappings )
            {
                response.append( this.validateUserRoleMapping( context, userRoleMapping, false ) );
            }
        }

        // summary
        if ( response.getValidationErrors().size() > 0 || response.getValidationWarnings().size() > 0 )
        {
            logger.error( "* * * * * * * * * * * * * * * * * * * * * * * * * *" );

            logger.error( "Security configuration has validation errors/warnings" );

            logger.error( "* * * * * * * * * * * * * * * * * * * * * * * * * *" );

            if ( response.getValidationErrors().size() > 0 )
            {
                logger.error( "The ERRORS:" );

                for ( ValidationMessage msg : response.getValidationErrors() )
                {
                    logger.error( msg.toString() );
                }
            }

            if ( response.getValidationWarnings().size() > 0 )
            {
                logger.error( "The WARNINGS:" );

                for ( ValidationMessage msg : response.getValidationWarnings() )
                {
                    logger.error( msg.toString() );
                }
            }

            logger.error( "* * * * * * * * * * * * * * * * * * * * *" );
        }
        else
        {
            logger.info( "Security configuration validated succesfully." );
        }

        return response;
    }

    public ValidationResponse validatePrivilege( SecurityValidationContext ctx, CPrivilege privilege, boolean update )
    {
        ValidationResponse response = new ValidationResponse();

        if ( ctx != null )
        {
            response.setContext( ctx );
        }

        for ( PrivilegeDescriptor descriptor : privilegeDescriptors )
        {
            ValidationResponse resp = descriptor.validatePrivilege( privilege, ctx, update );

            if ( resp != null )
            {
                response.append( resp );
            }
        }

        return response;
    }

    public ValidationResponse validateRoleContainment( SecurityValidationContext ctx )
    {
        ValidationResponse response = new ValidationResponse();

        if ( ctx != null )
        {
            response.setContext( ctx );
        }

        SecurityValidationContext context = (SecurityValidationContext) response.getContext();

        if ( context.getExistingRoleIds() != null )
        {
            for ( Entry<String, List<String>> entry : context.getExistingRoleIds().entrySet() )
            {
                String source = entry.getKey();
                List<String> roleIds = entry.getValue();
                for ( String roleId : roleIds )
                {
                    CRoleKey key = new CRoleKey();
                    key.setSource( source );
                    key.setId( roleId );
                    response.append( isRecursive( key, key, ctx ) );
                }
            }
        }

        return response;
    }

    private boolean isRoleNameAlreadyInUse( Map<CRoleKey, String> existingRoleNameMap, CRole role )
    {
        for ( CRoleKey roleId : existingRoleNameMap.keySet() )
        {
            if ( roleId.equals( role.getKey() ) )
            {
                continue;
            }
            if ( existingRoleNameMap.get( roleId ).equals( role.getName() ) )
            {
                return true;
            }
        }
        return false;
    }

    private String getRoleTextForDisplay( CRoleKey roleId, SecurityValidationContext ctx )
    {
        String name = ctx.getExistingRoleNameMap().get( roleId );

        if ( StringUtils.isEmpty( name ) )
        {
            return roleId.getId();
        }

        return name;
    }

    private ValidationResponse isRecursive( CRoleKey baseRoleId, CRoleKey roleId, SecurityValidationContext ctx )
    {
        ValidationResponse response = new ValidationResponse();

        List<CRoleKey> containedRoles = ctx.getRoleContainmentMap().get( roleId );

        for ( CRoleKey containedRoleId : containedRoles )
        {
            // Only need to do this on the first level
            if ( baseRoleId.equals( roleId ) )
            {
                if ( !ctx.getExistingRoleIds().containsKey( roleId.getSource() ) )
                {
                    ValidationMessage message =
                        new ValidationMessage( "roles", "Role '" + getRoleTextForDisplay( baseRoleId, ctx )
                            + "' contains an invalid role", "Role cannot contain invalid role '"
                            + getRoleTextForDisplay( roleId, ctx ) + "'." );

                    response.addValidationError( message );
                }
                else if ( !ctx.getExistingRoleIds().get( roleId.getSource() ).contains( roleId.getId() ) )
                {
                    ValidationMessage message =
                        new ValidationMessage( "roles", "Role '" + getRoleTextForDisplay( baseRoleId, ctx )
                            + "' contains an invalid role", "Role cannot contain invalid role '"
                            + getRoleTextForDisplay( roleId, ctx ) + "'." );

                    response.addValidationError( message );
                }
            }

            if ( containedRoleId.equals( baseRoleId ) )
            {
                ValidationMessage message =
                    new ValidationMessage( "roles", "Role '" + getRoleTextForDisplay( baseRoleId, ctx )
                        + "' contains itself through Role '" + getRoleTextForDisplay( roleId, ctx )
                        + "'.  This is not valid.", "Role cannot contain itself recursively (via role '"
                        + getRoleTextForDisplay( roleId, ctx ) + "')." );

                response.addValidationError( message );

                break;
            }

            if ( ctx.getExistingRoleIds().containsKey( containedRoleId.getSource() )
                && ctx.getExistingRoleIds().get( containedRoleId.getSource() ).contains( containedRoleId.getId() ) )
            {
                response.append( isRecursive( baseRoleId, containedRoleId, ctx ) );
            }
            // Only need to do this on the first level
            else if ( baseRoleId.equals( roleId ) )
            {
                ValidationMessage message =
                    new ValidationMessage( "roles", "Role '" + getRoleTextForDisplay( roleId, ctx )
                        + "' contains an invalid role '" + getRoleTextForDisplay( containedRoleId, ctx ) + "'.",
                                           "Role cannot contain invalid role '"
                                               + getRoleTextForDisplay( containedRoleId, ctx ) + "'." );

                response.addValidationError( message );
            }
        }

        return response;
    }

    public ValidationResponse validateRole( SecurityValidationContext ctx, CRole role, boolean update )
    {
        ValidationResponse response = new ValidationResponse();

        if ( ctx != null )
        {
            response.setContext( ctx );
        }

        if ( role.getKey().getSource() == null )
        {
            ValidationMessage message = new ValidationMessage( "source", "Role source must be defined." );
            response.addValidationError( message );
        }

        SecurityValidationContext context = (SecurityValidationContext) response.getContext();

        Map<String, List<String>> existingIds = context.getExistingRoleIds();

        if ( existingIds == null )
        {
            context.addExistingRoleIds();

            existingIds = context.getExistingRoleIds();
        }

        if ( !update && existingIds.get( role.getKey().getSource() ) != null
            && existingIds.get( role.getKey().getSource() ).contains( role.getKey().getId() ) )
        {
            ValidationMessage message = new ValidationMessage( "id", "Role ID must be unique." );
            response.addValidationError( message );
        }

        if ( update && existingIds.get( role.getKey().getSource() ) != null
            && !existingIds.get( role.getKey().getSource() ).contains( role.getKey().getId() ) )
        {
            ValidationMessage message = new ValidationMessage( "id", "Role ID cannot be changed." );
            response.addValidationError( message );
        }

        if ( !update && ( StringUtils.isEmpty( role.getKey().getId() ) || "0".equals( role.getKey().getId() ) ) )
        {
            String newId = idGenerator.generateId();

            response.addValidationWarning( "Fixed wrong role ID from '" + role.getKey().getId() + "' to '" + newId
                + "'" );

            role.getKey().setId( newId );

            response.setModified( true );
        }

        Map<CRoleKey, String> existingRoleNameMap = context.getExistingRoleNameMap();

        if ( StringUtils.isEmpty( role.getName() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "name", "Role ID '" + role.getKey().getId() + "' requires a name.",
                    "Name is required." );
            response.addValidationError( message );
        }
        else if ( isRoleNameAlreadyInUse( existingRoleNameMap, role ) )
        {
            ValidationMessage message =
                new ValidationMessage( "name", "Role ID '" + role.getKey().getId() + "' can't use the name '"
                    + role.getName()
                    + "'.", "Name is already in use." );
            response.addValidationError( message );
        }
        else
        {
            existingRoleNameMap.put( role.getKey(), role.getName() );
        }

        if ( context.getExistingPrivilegeIds() != null )
        {
            List<String> privIds = role.getPrivileges();

            for ( String privId : privIds )
            {
                if ( !context.getExistingPrivilegeIds().contains( privId ) )
                {
                    ValidationMessage message =
                        new ValidationMessage( "privileges", "Role ID '" + role.getKey().getId()
                            + "' Invalid privilege id '"
                            + privId + "' found.", "Role cannot contain invalid privilege ID '" + privId + "'." );
                    response.addValidationError( message );
                }
            }
        }

        List<CRoleKey> roleIds = role.getRoles();

        List<CRoleKey> containedRoles = context.getRoleContainmentMap().get( role.getKey() );

        if ( containedRoles == null )
        {
            containedRoles = new ArrayList<CRoleKey>();
            context.getRoleContainmentMap().put( role.getKey(), containedRoles );
        }

        for ( CRoleKey roleId : roleIds )
        {
            if ( roleId.equals( role.getKey() ) )
            {
                ValidationMessage message =
                    new ValidationMessage( "roles", "Role ID '" + role.getKey().getId() + "' cannot contain itself.",
                                           "Role cannot contain itself." );
                response.addValidationError( message );
            }
            else if ( context.getRoleContainmentMap() != null )
            {
                containedRoles.add( roleId );
            }
        }

        // It is expected that a full context is built upon update
        if ( update )
        {
            response.append( isRecursive( role.getKey(), role.getKey(), context ) );
        }

        if ( !existingIds.containsKey( role.getKey().getSource() ) )
        {
            existingIds.put( role.getKey().getSource(), new ArrayList<String>() );
        }
        existingIds.get( role.getKey().getSource() ).add( role.getKey().getId() );

        return response;
    }

    public ValidationResponse validateUser( SecurityValidationContext ctx, CUser user, Collection<CRoleKey> roles,
                                            boolean update )
    {
        ValidationResponse response = new ValidationResponse();

        if ( ctx != null )
        {
            response.setContext( ctx );
        }

        SecurityValidationContext context = (SecurityValidationContext) response.getContext();

        List<String> existingIds = context.getExistingUserIds();

        if ( existingIds == null )
        {
            context.addExistingUserIds();

            existingIds = context.getExistingUserIds();
        }

        if ( !update && StringUtils.isEmpty( user.getId() ) )
        {
            ValidationMessage message = new ValidationMessage( "userId", "User ID is required.", "User ID is required." );
            
            response.addValidationError( message );
        }
        
        if ( !update && StringUtils.isNotEmpty( user.getId() ) && existingIds.contains( user.getId() ) )
        {
            ValidationMessage message = new ValidationMessage( "userId", "User ID '" + user.getId()
                + "' is already in use.", "User ID '" + user.getId() + "' is already in use." );

            response.addValidationError( message );
        }
        
        if ( StringUtils.isNotEmpty( user.getId() ) && user.getId().contains( " " ) )
        {
            ValidationMessage message = new ValidationMessage( "userId", "User ID '" + user.getId()
                + "' cannot contain spaces.", "User ID '" + user.getId() + "' cannot contain spaces." );

            response.addValidationError( message );
        }

        if( StringUtils.isNotEmpty( user.getFirstName() ) )
        {
            user.setFirstName( user.getFirstName() );
        }

        if( StringUtils.isNotEmpty( user.getLastName() ) )
        {
            user.setLastName( user.getLastName() );
        }

        if ( StringUtils.isEmpty( user.getPassword() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "password", "User ID '" + user.getId()
                    + "' has no password.  This is a required field.", "Password is required." );
            response.addValidationError( message );
        }

        if ( StringUtils.isEmpty( user.getEmail() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "email", "User ID '" + user.getId() + "' has no email address",
                                       "Email address is required." );
            response.addValidationError( message );
        }
        else
        {
            try
            {
                if ( !user.getEmail().matches( ".+@.+" ) )
                {
                    ValidationMessage message = new ValidationMessage( "email", "User ID '" + user.getId()
                        + "' has an invalid email address.", "Email address is invalid." );
                    response.addValidationError( message );
                }
            }
            catch ( PatternSyntaxException e )
            {
                throw new IllegalStateException( "Regex did not compile: " + e.getMessage(), e );
            }

        }

        if ( !CUser.STATUS_ACTIVE.equals( user.getStatus() ) && !CUser.STATUS_DISABLED.equals( user.getStatus() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "status", "User ID '" + user.getId() + "' has invalid status '"
                    + user.getStatus() + "'.  (Allowed values are: " + CUser.STATUS_ACTIVE + " and "
                    + CUser.STATUS_DISABLED + ")", "Invalid Status selected." );
            response.addValidationError( message );
        }

        validateUserRoles( user.getId(), roles, response, context );

        if ( !StringUtils.isEmpty( user.getId() ) )
        {
            existingIds.add( user.getId() );
        }

        return response;
    }

    private void validateUserRoles( String userId, Collection<CRoleKey> roles, ValidationResponse response,
                                    SecurityValidationContext context )
    {
        if ( context.getExistingRoleIds() != null && context.getExistingUserRoleMap() != null )
        {

            if ( roles != null && roles.size() > 0 )
            {
                for ( CRoleKey key : roles )
                {
                    if ( !context.getExistingRoleIds().containsKey( key.getSource() ) )
                    {
                        ValidationMessage message =
                            new ValidationMessage( "source", "User ID '" + userId + "' Invalid source realm '"
                                + key.getSource() + "' found.", "User cannot contain invalid source realm '"
                                + key.getSource() + "'." );
                        response.addValidationError( message );
                    }
                    else if ( !context.getExistingRoleIds().get( key.getSource() ).contains( key.getId() ) )
                    {
                        ValidationMessage message =
                            new ValidationMessage( "roles", "User ID '" + userId + "' Invalid role id '" + key.getId()
                                + "' found.", "User cannot contain invalid role ID '" + key.getId() + "'." );
                        response.addValidationError( message );
                    }
                }
            }
        }
    }

    public ValidationResponse validateUserRoleMapping( SecurityValidationContext context,
                                                       CUserRoleMapping userRoleMapping, boolean update )
    {
        ValidationResponse response = new ValidationResponse();

        // ID must be not empty
        if ( StringUtils.isEmpty( userRoleMapping.getUserId() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "userId", "UserRoleMapping has no userId." + "  This is a required field.",
                                       "UserId is required." );
            response.addValidationError( message );
        }

        // source must be not empty
        if ( StringUtils.isEmpty( userRoleMapping.getSource() ) )
        {
            ValidationMessage message =
                new ValidationMessage( "source", "User Role Mapping for user '" + userRoleMapping.getUserId()
                    + "' has no source.  This is a required field.", "UserId is required." );
            response.addValidationError( message );
        }

        List<CRoleKey> roles = userRoleMapping.getRoles();
        validateUserRoles( userRoleMapping.getUserId(), roles, response, context );

        return response;
    }

}

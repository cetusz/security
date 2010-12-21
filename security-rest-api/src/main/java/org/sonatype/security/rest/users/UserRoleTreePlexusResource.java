package org.sonatype.security.rest.users;

import javax.enterprise.inject.Typed;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.codehaus.enunciate.contract.jaxrs.ResourceMethodSignature;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;
import org.sonatype.security.authorization.AuthorizationManager;
import org.sonatype.security.authorization.NoSuchAuthorizationManagerException;
import org.sonatype.security.authorization.NoSuchPrivilegeException;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.rest.AbstractSecurityPlexusResource;
import org.sonatype.security.rest.model.RoleTreeResource;
import org.sonatype.security.rest.model.RoleTreeResourceResponse;
import org.sonatype.security.usermanagement.RoleIdentifier;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserNotFoundException;

/**
 * REST resource to retrieve the tree of roles and privileges assigned to a user.
 */
@Singleton
@Typed( value = PlexusResource.class )
@Named( value = "UserRoleTreePlexusResource" )
@Produces( { "application/xml", "application/json" } )
@Consumes( { "application/xml", "application/json" } )
@Path( UserRoleTreePlexusResource.RESOURCE_URI )
public class UserRoleTreePlexusResource
    extends AbstractSecurityPlexusResource
{
    public static final String USER_ID_KEY = "userId";

    public static final String RESOURCE_URI = "/role_tree/{" + USER_ID_KEY + "}";

    @Override
    public Object getPayloadInstance()
    {
        return null;
    }

    @Override
    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( "/role_tree/*", "authcBasic,perms[security:users]" );
    }

    @Override
    public String getResourceUri()
    {
        return RESOURCE_URI;
    }

    /**
     * Retrieves the list of privileges assigned to the user.
     */
    @Override
    @GET
    @ResourceMethodSignature( output = RoleTreeResourceResponse.class )
    public Object get( Context context, Request request, Response response, Variant variant )
        throws ResourceException
    {
        String userId = getUserId( request );

        try
        {
            RoleTreeResourceResponse responseResource = new RoleTreeResourceResponse();

            AuthorizationManager authzManager = getSecuritySystem().getAuthorizationManager( "default" );

            if ( Boolean.parseBoolean( request.getResourceRef().getQueryAsForm().getFirstValue( "isRole" ) ) )
            {
                Role role = authzManager.getRole( userId );

                handleRoles( role, authzManager, responseResource );
            }
            else
            {
                User user = getSecuritySystem().getUser( userId );

                handleRoles( user, authzManager, responseResource );
            }

            return responseResource;
        }
        catch ( UserNotFoundException e )
        {
            throw new ResourceException( Status.CLIENT_ERROR_BAD_REQUEST, "User: " + userId + " could not be found." );
        }
        catch ( NoSuchAuthorizationManagerException e )
        {
            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, "Unable to load default authorization manager" );
        }
        catch ( NoSuchRoleException e )
        {
            throw new ResourceException( Status.CLIENT_ERROR_BAD_REQUEST, "Role: " + userId + " could not be found." );
        }
    }

    protected void handleRoles( User user, AuthorizationManager authzManager, RoleTreeResourceResponse response )
    {
        for ( RoleIdentifier roleIdentifier : user.getRoles() )
        {
            try
            {
                Role role = authzManager.getRole( roleIdentifier.getRoleId() );

                RoleTreeResource resource = new RoleTreeResource();
                resource.setId( role.getRoleId() );
                resource.setName( role.getName() );
                resource.setType( "role" );
                response.addData( resource );

                handleRole( role, authzManager, resource );
            }
            catch ( NoSuchRoleException e )
            {
                getLogger().debug(
                    "Invalid roleId: " + roleIdentifier.getRoleId() + " from source: " + roleIdentifier.getSource()
                        + " not found." );
            }
        }
    }

    protected void handleRoles( Role role, AuthorizationManager authzManager, RoleTreeResourceResponse response )
    {
        for ( String roleId : role.getRoles() )
        {
            try
            {
                Role childRole = authzManager.getRole( roleId );

                RoleTreeResource resource = new RoleTreeResource();
                resource.setId( childRole.getRoleId() );
                resource.setName( childRole.getName() );
                resource.setType( "role" );
                response.addData( resource );

                handleRole( childRole, authzManager, resource );
            }
            catch ( NoSuchRoleException e )
            {
                getLogger().debug(
                    "Invalid roleId: " + roleId + " from source: " + authzManager.getSource() + " not found." );
            }
        }
    }

    protected void handleRole( Role role, AuthorizationManager authzManager, RoleTreeResource resource )
    {
        for ( String roleId : role.getRoles() )
        {
            try
            {
                Role childRole = authzManager.getRole( roleId );
                RoleTreeResource childResource = new RoleTreeResource();
                childResource.setId( childRole.getRoleId() );
                childResource.setName( childRole.getName() );
                childResource.setType( "role" );
                resource.addChildren( childResource );
                handleRole( childRole, authzManager, childResource );
            }
            catch ( NoSuchRoleException e )
            {
                getLogger().debug( "handleRole() failed, roleId: " + roleId + " not found" );
            }
        }

        for ( String privilegeId : role.getPrivileges() )
        {
            try
            {
                Privilege childPrivilege = authzManager.getPrivilege( privilegeId );
                RoleTreeResource childResource = new RoleTreeResource();
                childResource.setId( childPrivilege.getId() );
                childResource.setName( childPrivilege.getName() );
                childResource.setType( "privilege" );
                resource.addChildren( childResource );
            }
            catch ( NoSuchPrivilegeException e )
            {
                getLogger().debug( "handleRole() failed, privilegeId: " + privilegeId + " not found" );
            }
        }
    }

    protected String getUserId( Request request )
    {
        return request.getAttributes().get( USER_ID_KEY ).toString();
    }
}
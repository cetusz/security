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
package org.sonatype.security.rest.roles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.inject.Typed;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.codehaus.enunciate.contract.jaxrs.ResourceMethodSignature;
import org.codehaus.plexus.util.StringUtils;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;
import org.sonatype.security.authorization.NoSuchAuthorizationManagerException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.rest.AbstractSecurityPlexusResource;
import org.sonatype.security.rest.model.PlexusRoleResource;
import org.sonatype.security.rest.model.RoleAndPrivilegeListFilterResourceRequest;
import org.sonatype.security.rest.model.RoleAndPrivilegeListResource;
import org.sonatype.security.rest.model.RoleAndPrivilegeListResourceResponse;
import org.sonatype.security.rest.model.RoleListResourceResponse;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserNotFoundException;

/**
 * REST resource for listing security roles and privileges. Supports pagination
 */
@Singleton
@Typed( PlexusResource.class )
@Named( "RoleAndPrivilegeListPlexusResource" )
@Produces( { "application/xml", "application/json" } )
@Consumes( { "application/xml", "application/json" } )
@Path( RoleAndPrivilegeListPlexusResource.RESOURCE_URI )
public class RoleAndPrivilegeListPlexusResource
    extends AbstractSecurityPlexusResource
{
    public static final String RESOURCE_URI = "/rolesAndPrivs";

    public static final String REQUEST_SORT = "sort";

    public static final String REQUEST_DIR = "dir";

    public static final String REQUEST_START = "start";

    public static final String REQUEST_LIMIT = "limit";

    public RoleAndPrivilegeListPlexusResource()
    {
        this.setReadable( false );
        this.setModifiable( true );
    }

    @Override
    public Object getPayloadInstance()
    {
        return new RoleAndPrivilegeListFilterResourceRequest();
    }

    @Override
    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( getResourceUri(), "authcBasic,perms[security:roles]" );
    }

    @Override
    public String getResourceUri()
    {
        return RESOURCE_URI;
    }

    @POST
    @Override
    @ResourceMethodSignature( input = RoleAndPrivilegeListFilterResourceRequest.class, output = RoleListResourceResponse.class )
    public Object post( Context context, Request request, Response response, Object payload )
        throws ResourceException
    {
        RoleAndPrivilegeListResourceResponse result = new RoleAndPrivilegeListResourceResponse();

        List<RoleAndPrivilegeListResource> resources = new ArrayList<RoleAndPrivilegeListResource>();

        RoleAndPrivilegeListFilterResourceRequest filterRequest = (RoleAndPrivilegeListFilterResourceRequest) payload;

        try
        {
            Form form = request.getResourceRef().getQueryAsForm();

            FilterRequest filter = new FilterRequest( filterRequest );

            for ( Role role : getSecuritySystem().getAuthorizationManager( DEFAULT_SOURCE ).listRoles() )
            {
                RoleAndPrivilegeListResource res = toDTO( role );

                if ( filter.applies( res ) )
                {
                    resources.add( res );
                }
            }

            for ( Privilege privilege : getSecuritySystem().getAuthorizationManager( DEFAULT_SOURCE ).listPrivileges() )
            {
                RoleAndPrivilegeListResource res = toDTO( privilege );

                if ( filter.applies( res ) )
                {
                    resources.add( res );
                }
            }

            if ( !StringUtils.isEmpty( filterRequest.getData().getUserId() ) )
            {
                try
                {
                    User user = getSecuritySystem().getUser( filterRequest.getData().getUserId() );

                    List<PlexusRoleResource> plexusRoles = securityToRestModel( user ).getRoles();

                    for ( PlexusRoleResource plexusRole : plexusRoles )
                    {
                        if ( !DEFAULT_SOURCE.equals( plexusRole.getSource() ) )
                        {
                            RoleAndPrivilegeListResource res = toDTO( plexusRole );

                            if ( filter.applies( res ) )
                            {
                                resources.add( res );
                            }
                        }
                    }
                }
                catch ( UserNotFoundException e )
                {
                    getLogger().warn( "Unable to load user, and retrieve any external roles assigned", e );
                }
            }

            result.setTotalCount( resources.size() );

            result.setData( generateResultSet( resources, form ) );
        }
        catch ( NoSuchAuthorizationManagerException e )
        {
            this.getLogger().error( "Unable to find AuthorizationManager 'default'", e );
            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, "Unable to find AuthorizationManager 'default'" );
        }

        return result;
    }

    protected RoleAndPrivilegeListResource toDTO( Role role )
    {
        RoleAndPrivilegeListResource resource = new RoleAndPrivilegeListResource();

        resource.setId( role.getRoleId() );
        resource.setName( role.getName() );
        resource.setDescription( role.getDescription() );
        resource.setType( "role" );

        return resource;
    }

    protected RoleAndPrivilegeListResource toDTO( Privilege privilege )
    {
        RoleAndPrivilegeListResource resource = new RoleAndPrivilegeListResource();

        resource.setId( privilege.getId() );
        resource.setName( privilege.getName() );
        resource.setDescription( privilege.getDescription() );
        resource.setType( "privilege" );

        return resource;
    }

    protected RoleAndPrivilegeListResource toDTO( PlexusRoleResource roleResource )
    {
        RoleAndPrivilegeListResource resource = new RoleAndPrivilegeListResource();

        resource.setId( roleResource.getRoleId() );
        resource.setName( roleResource.getName() );
        resource.setDescription( "External role from the " + roleResource.getSource()
            + " realm, this role cannot be removed." );
        resource.setType( "role" );
        resource.setExternal( true );

        return resource;
    }

    protected List<RoleAndPrivilegeListResource> generateResultSet( List<RoleAndPrivilegeListResource> resources,
                                                                    Form form )
    {
        // First we need to sort
        String sort = form.getFirstValue( REQUEST_SORT );
        String dir = form.getFirstValue( REQUEST_DIR );

        sortResultSet( resources, sort, dir );

        // now paginate
        String start = form.getFirstValue( REQUEST_START );
        String limit = form.getFirstValue( REQUEST_LIMIT );

        resources = paginateResultSet( resources, start, limit );

        return resources;
    }

    protected void sortResultSet( List<RoleAndPrivilegeListResource> resources, String sort, String dir )
    {
        if ( !StringUtils.isEmpty( sort ) )
        {
            RoleAndPrivilegeListResourceComparator comparator = new RoleAndPrivilegeListResourceComparator( sort, dir );
            Collections.sort( resources, comparator );
        }
    }

    protected List<RoleAndPrivilegeListResource> paginateResultSet( List<RoleAndPrivilegeListResource> resources,
                                                                    String startStr, String limitStr )
    {
        int start;
        int limit;

        try
        {
            start = Integer.parseInt( startStr );
        }
        catch ( Throwable t )
        {
            start = 0;
        }

        try
        {
            limit = Integer.parseInt( limitStr );
        }
        catch ( Throwable t )
        {
            limit = Integer.MAX_VALUE;
        }

        List<RoleAndPrivilegeListResource> result = new ArrayList<RoleAndPrivilegeListResource>();

        for ( int i = start; i < ( start + limit ) && i < resources.size(); i++ )
        {
            result.add( resources.get( i ) );
        }

        return result;
    }
}

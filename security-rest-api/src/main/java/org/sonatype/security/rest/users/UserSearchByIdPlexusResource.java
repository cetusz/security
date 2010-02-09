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
package org.sonatype.security.rest.users;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.codehaus.enunciate.contract.jaxrs.ResourceMethodSignature;
import org.codehaus.plexus.component.annotations.Component;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;
import org.sonatype.security.rest.model.PlexusUserListResourceResponse;
import org.sonatype.security.usermanagement.UserSearchCriteria;

/** 
 * REST resource that searches for users based on a users source and partial user Id (user Id starts with xxx).
 * 
 * @author bdemers
 *
 */
@Component( role = PlexusResource.class, hint = "UserSearchByIdPlexusResource" )
@Produces( { "application/xml", "application/json" } )
@Consumes( { "application/xml", "application/json" } )
@Path( UserSearchByIdPlexusResource.RESOURCE_URI )
public class UserSearchByIdPlexusResource extends AbstractUserSearchPlexusResource
{
    public static final String USER_ID_KEY = "userId";

    public static final String RESOURCE_URI = "/user_search/{" + USER_SOURCE_KEY +"}/{" + USER_ID_KEY + "}";
    
    public UserSearchByIdPlexusResource()
    {
        setModifiable( false );
    }
    
    @Override
    public Object getPayloadInstance()
    {
        return null;
    }

    @Override
    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( "/user_search/*", "authcBasic,perms[security:users]" );
    }

    @Override
    public String getResourceUri()
    {
        return RESOURCE_URI;
    }

    /**
     * Returns a list of users in which the source and partial user id matches the parameters. 
     */
    @Override 
    @GET
    @ResourceMethodSignature( output = PlexusUserListResourceResponse.class )
    public Object get( Context context, Request request, Response response, Variant variant )
        throws ResourceException
    {
        
        UserSearchCriteria criteria = new UserSearchCriteria();
        criteria.setUserId( this.getSearchArg( request ) );
        criteria.setSource( this.getUserSource( request ) );
        
        return this.search( criteria );
    }
    
    
    protected String getSearchArg( Request request )
    {
        return request.getAttributes().get( USER_ID_KEY ).toString();
    }
    
}

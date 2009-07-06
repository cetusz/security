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
package org.sonatype.security.rest.privileges;

import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.rest.model.PrivilegeListResourceResponse;
import org.sonatype.security.rest.model.PrivilegeResourceRequest;
import org.sonatype.security.rest.model.PrivilegeStatusResource;

/**
 * Handles the GET and POST request for the Security privileges.
 * 
 * @author tstevens
 */
@Component( role = PlexusResource.class, hint = "PrivilegeListPlexusResource" )
public class PrivilegeListPlexusResource
    extends AbstractPrivilegePlexusResource
{

    @Override
    public Object getPayloadInstance()
    {
        return new PrivilegeResourceRequest();
    }

    @Override
    public String getResourceUri()
    {
        return "/privileges";
    }

    @Override
    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( getResourceUri(), "authcBasic,perms[security:privileges]" );
    }

    @Override
    public Object get( Context context, Request request, Response response, Variant variant )
        throws ResourceException
    {
        PrivilegeListResourceResponse result = new PrivilegeListResourceResponse();

        Set<Privilege> privs = getSecuritySystem().listPrivileges();

        for ( Privilege priv : privs )
        {
            PrivilegeStatusResource res = securityToRestModel( priv, request, true );

            if ( res != null )
            {
                result.addData( res );
            }
        }

        return result;
    }
}
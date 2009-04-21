/**
 * Sonatype Nexus (TM) Open Source Version.
 * Copyright (c) 2008 Sonatype, Inc. All rights reserved.
 * Includes the third-party code listed at http://nexus.sonatype.org/dev/attributions.html
 * This program is licensed to you under Version 3 only of the GNU General Public License as published by the Free Software Foundation.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License Version 3 for more details.
 * You should have received a copy of the GNU General Public License Version 3 along with this program.
 * If not, see http://www.gnu.org/licenses/.
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc.
 * "Sonatype" and "Sonatype Nexus" are trademarks of Sonatype, Inc.
 */
package org.sonatype.nexus.rest.users;

import java.util.List;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.util.StringUtils;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.jsecurity.locators.users.PlexusUserLocator;
import org.sonatype.plexus.rest.resource.AbstractPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;
import org.sonatype.security.rest.model.PlexusComponentListResource;
import org.sonatype.security.rest.model.PlexusComponentListResourceResponse;

@Component( role = PlexusResource.class, hint = "UserLocatorComponentListPlexusResource" )
public class UserLocatorComponentListPlexusResource
    extends AbstractPlexusResource
{
    @Requirement
    private PlexusContainer container;
    
    @Override
    public Object getPayloadInstance()
    {
        return null;
    }
    
    @Override
    public String getResourceUri()
    {
        return "/components/userLocators";
    }

    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( getResourceUri(), "authcBasic,perms[security:componentsuserlocatortypes]" );
    }

    protected String getRole( Request request )
    {
        return PlexusUserLocator.class.getName();
    }

    // TODO: this was copied from the Nexus AbstractComponentListPlexusResource
    @SuppressWarnings( "unchecked" )
    @Override
    public Object get( Context context, Request request, Response response, Variant variant )
        throws ResourceException
    {
        PlexusComponentListResourceResponse result = new PlexusComponentListResourceResponse();

        // get role from request
        String role = getRole( request );

        // get component descriptors
        List<ComponentDescriptor<?>> componentMap = container.getComponentDescriptorList( role );

        // check if valid role
        if ( componentMap == null || componentMap.isEmpty() )
        {
            throw new ResourceException( Status.CLIENT_ERROR_NOT_FOUND );
        }

        // loop and convert all objects of this role to a PlexusComponentListResource
        for ( ComponentDescriptor componentDescriptor : componentMap )
        {
            PlexusComponentListResource resource = new PlexusComponentListResource();

            resource.setRoleHint( componentDescriptor.getRoleHint() );
            resource.setDescription( ( StringUtils.isNotEmpty( componentDescriptor.getDescription() ) )
                ? componentDescriptor.getDescription()
                : componentDescriptor.getRoleHint() );

            // add it to the collection
            result.addData( resource );
        }

        return result;
    }
}
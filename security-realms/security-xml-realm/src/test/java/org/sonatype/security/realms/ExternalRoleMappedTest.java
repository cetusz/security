package org.sonatype.security.realms;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.sonatype.security.AbstractSecurityTestCase;
import org.sonatype.security.SecuritySystem;
import org.sonatype.security.authorization.AuthorizationException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.authorization.RoleKey;
import org.sonatype.security.mock.MockRealm;
import org.sonatype.security.realms.privileges.application.ApplicationPrivilegeDescriptor;
import org.sonatype.security.realms.privileges.application.ApplicationPrivilegeMethodPropertyDescriptor;
import org.sonatype.security.realms.privileges.application.ApplicationPrivilegePermissionPropertyDescriptor;

public class ExternalRoleMappedTest
    extends AbstractSecurityTestCase
{

    private final String SECURITY_CONFIG_FILE_PATH = getBasedir() + "/target/plexus-home/conf/security.xml";

    @Override
    public void configure( Properties properties )
    {
        properties.put( PLEXUS_SECURITY_XML_FILE, SECURITY_CONFIG_FILE_PATH );
        super.configure( properties );
    }

    public void testUserHasPermissionFromExternalRole()
        throws Exception
    {
        // delete the security conf first, start clean
        new File( SECURITY_CONFIG_FILE_PATH ).delete();

        SecuritySystem securitySystem = this.lookup( SecuritySystem.class );

        Map<String, String> properties = new HashMap<String, String>();
        properties.put( ApplicationPrivilegeMethodPropertyDescriptor.ID, "read" );
        properties.put( ApplicationPrivilegePermissionPropertyDescriptor.ID, "permissionOne" );

        securitySystem.getAuthorizationManager( "default" ).addPrivilege( new Privilege(
                                                                                         "randomId",
                                                                                         "permissionOne",
                                                                                         "permissionOne",
                                                                                         ApplicationPrivilegeDescriptor.TYPE,
                                                                                         properties, false ) );
        securitySystem.getAuthorizationManager( "default" ).addRole(
            new Role( new RoleKey( "mockrole1", "default" ), "mockrole1", "default", false, null,
                Collections.singleton( "randomId" ) ) );

        // add MockRealm to config
        List<String> realms = new ArrayList<String>();
        realms.add( "Mock" );
        realms.add( XmlAuthorizingRealm.ROLE );
        securitySystem.setRealms( realms );
        securitySystem.start();

        // jcohen has the role mockrole1, there is also xml role with the same ID, which means jcohen automaticly has
        // this xml role

        PrincipalCollection jcohen = new SimplePrincipalCollection( "jcohen", new MockRealm().getName() );

        try
        {
            securitySystem.checkPermission( jcohen, "permissionOne:invalid" );
            Assert.fail( "Expected AuthorizationException" );
        }
        catch ( AuthorizationException e )
        {
            // expected
        }

        securitySystem.checkPermission( jcohen, "permissionOne:read" ); // throws on error, so this is all we need to do

    }
}

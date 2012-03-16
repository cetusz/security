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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.Assert;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.security.AbstractSecurityTestCase;
import org.sonatype.security.SecuritySystem;
import org.sonatype.security.model.CUser;
import org.sonatype.security.model.CUserRoleMapping;
import org.sonatype.security.model.Configuration;
import org.sonatype.security.model.io.xpp3.SecurityConfigurationXpp3Reader;
import org.sonatype.security.model.io.xpp3.SecurityConfigurationXpp3Writer;
import org.sonatype.security.realms.tools.ConfigurationManager;
import org.sonatype.security.usermanagement.DefaultUser;
import org.sonatype.security.usermanagement.RoleIdentifier;
import org.sonatype.security.usermanagement.StringDigester;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserManager;
import org.sonatype.security.usermanagement.UserNotFoundException;
import org.sonatype.security.usermanagement.UserStatus;

public class UserManagerTest
    extends AbstractSecurityTestCase
{

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        // copy the securityConf into place
        String securityXml = this.getClass().getName().replaceAll( "\\.", "\\/" ) + "-security.xml";
        FileUtils.copyURLToFile( Thread.currentThread().getContextClassLoader().getResource( securityXml ), new File(
            CONFIG_DIR,
            "security.xml" ) );
    }

    public SecuritySystem getSecuritySystem()
        throws Exception
    {
        return this.lookup( SecuritySystem.class );
    }

    public UserManager getUserManager()
        throws Exception
    {
        return this.lookup( UserManager.class );
    }

    public ConfigurationManager getConfigurationManager()
        throws Exception
    {
        return lookup( ConfigurationManager.class, "resourceMerging" );
    }

    public void testGetUser()
        throws Exception
    {
        UserManager userManager = this.getUserManager();

        User user = userManager.getUser( "test-user" );

        Assert.assertEquals( user.getUserId(), "test-user" );
        Assert.assertEquals( user.getEmailAddress(), "changeme1@yourcompany.com" );
        Assert.assertEquals( user.getName(), "Test User" );
        // not exposed anymore
        // Assert.assertEquals( user.getPassword(), "b2a0e378437817cebdf753d7dff3dd75483af9e0" );
        Assert.assertEquals( user.getStatus().name(), "active" );

        List<String> roleIds = this.getRoleIds( user );

        Assert.assertTrue( roleIds.contains( "role1" ) );
        Assert.assertTrue( roleIds.contains( "role2" ) );
        Assert.assertEquals( 2, roleIds.size() );
    }

    public void testAddUser()
        throws Exception
    {
        UserManager userManager = this.getUserManager();

        User user = new DefaultUser();
        user.setUserId( "testCreateUser" );
        user.setName( user.getUserId() + "-name" );
        user.setSource( user.getUserId() + "default" );
        user.setEmailAddress( "email@email" );
        user.setStatus( UserStatus.active );
        user.addRole( new RoleIdentifier( "default", "role1" ) );
        user.addRole( new RoleIdentifier( "default", "role3" ) );

        userManager.addUser( user, "my-password" );

        ConfigurationManager config = this.getConfigurationManager();

        CUser secUser = config.readUser( user.getUserId() );
        Assert.assertEquals( secUser.getId(), user.getUserId() );
        Assert.assertEquals( secUser.getEmail(), user.getEmailAddress() );
        Assert.assertEquals( secUser.getFirstName(), user.getFirstName() );
        Assert.assertEquals( secUser.getLastName(), user.getLastName() );
        Assert.assertEquals( secUser.getPassword(), StringDigester.getSha1Digest( "my-password" ) );

        Assert.assertEquals( secUser.getStatus(), user.getStatus().name() );

        CUserRoleMapping roleMapping = config.readUserRoleMapping( "testCreateUser", "default" );
        
        Assert.assertTrue( roleMapping.getRoles().contains( "role1" ) );
        Assert.assertTrue( roleMapping.getRoles().contains( "role3" ) );
        Assert.assertEquals( 2, roleMapping.getRoles().size() );
    }

    public void testSupportsWrite()
        throws Exception
    {
        Assert.assertTrue( this.getUserManager().supportsWrite() );
    }

    public void testChangePassword()
        throws Exception
    {
        UserManager userManager = this.getUserManager();
        userManager.changePassword( "test-user", "new-user-password" );

        Assert.assertEquals( this.getConfigurationManager().readUser( "test-user" ).getPassword(), StringDigester
            .getSha1Digest( "new-user-password" ) );
    }

    public void testUpdateUser()
        throws Exception
    {
        UserManager userManager = this.getUserManager();

        User user = userManager.getUser( "test-user" );

        user.setName( "new Name" );
        user.setEmailAddress( "newemail@foo" );

        Set<RoleIdentifier> roles = new HashSet<RoleIdentifier>();
        roles.add( new RoleIdentifier( "default", "role3" ) );
        user.setRoles( roles );
        userManager.updateUser( user );

        ConfigurationManager config = this.getConfigurationManager();

        CUser secUser = config.readUser( user.getUserId() );
        Assert.assertEquals( secUser.getId(), user.getUserId() );
        Assert.assertEquals( secUser.getEmail(), user.getEmailAddress() );
        Assert.assertEquals( secUser.getFirstName(), user.getFirstName() );
        Assert.assertEquals( secUser.getLastName(), user.getLastName() );
        Assert.assertEquals( secUser.getPassword(), "b2a0e378437817cebdf753d7dff3dd75483af9e0" );

        Assert.assertEquals( secUser.getStatus(), user.getStatus().name() );

        CUserRoleMapping roleMapping = config.readUserRoleMapping( "test-user", "default" );
        
        Assert.assertTrue( roleMapping.getRoles().contains( "role3" ) );
        Assert.assertEquals( "roles: " + roleMapping.getRoles(), 1, roleMapping.getRoles().size() );
    }

    public void testDeleteUser()
        throws Exception
    {
        UserManager userManager = this.getUserManager();
        try
        {
            userManager.deleteUser( "INVALID-USERNAME" );
            Assert.fail( "Expected UserNotFoundException" );
        }
        catch ( UserNotFoundException e )
        {
            // expected
        }

        // this one will work
        userManager.deleteUser( "test-user" );

        // this one should fail
        try
        {
            userManager.deleteUser( "test-user" );
            Assert.fail( "Expected UserNotFoundException" );
        }
        catch ( UserNotFoundException e )
        {
            // expected
        }

        try
        {
            userManager.getUser( "test-user" );
            Assert.fail( "Expected UserNotFoundException" );
        }
        catch ( UserNotFoundException e )
        {
            // expected
        }

        try
        {
            this.getConfigurationManager().readUser( "test-user" );
            Assert.fail( "Expected UserNotFoundException" );
        }
        catch ( UserNotFoundException e )
        {
            // expected
        }
    }

    public void testDeleteUserAndUserRoleMappings()
        throws Exception
    {

        String userId = "testDeleteUserAndUserRoleMappings";

        UserManager userManager = this.getUserManager();

        User user = new DefaultUser();
        user.setUserId( userId );
        user.setName( user.getUserId() + "-name" );
        user.setSource( user.getUserId() + "default" );
        user.setEmailAddress( "email@email" );
        user.setStatus( UserStatus.active );
        user.addRole( new RoleIdentifier( "default", "role1" ) );
        user.addRole( new RoleIdentifier( "default", "role3" ) );

        userManager.addUser( user, "my-password" );

        // now delete the user
        userManager.deleteUser( userId );

        Configuration securityModel = this.getSecurityConfiguration();

        for ( CUser tmpUser : securityModel.getUsers() )
        {
            if ( userId.equals( tmpUser.getId() ) )
            {
                Assert.fail( "User " + userId + " was not removed." );
            }
        }

        for ( CUserRoleMapping userRoleMapping : securityModel.getUserRoleMappings() )
        {
            if ( userId.equals( userRoleMapping.getUserId() ) && "default".equals( userRoleMapping.getSource() ) )
            {
                Assert.fail( "User Role Mapping was not deleted when user: " + userId + " was removed." );
            }
        }
    }

    public void testSetUsersRoles()
        throws Exception
    {
        SecuritySystem securitySystem = this.getSecuritySystem();

        Set<RoleIdentifier> roleIdentifiers = new HashSet<RoleIdentifier>();
        RoleIdentifier roleIdentifier = new RoleIdentifier( "default", "role2" );
        roleIdentifiers.add( roleIdentifier );

        securitySystem.setUsersRoles( "admin", "default", roleIdentifiers );

        Configuration securityModel = this.getSecurityConfiguration();

        boolean found = false;
        for ( CUserRoleMapping roleMapping : securityModel.getUserRoleMappings() )
        {
            if ( roleMapping.getUserId().equals( "admin" ) )
            {
                found = true;
                
                Assert.assertEquals( 1, roleMapping.getRoles().size() );
                Assert.assertEquals( "role2", roleMapping.getRoles().get( 0 ) );
            }
        }
        
        Assert.assertTrue( "did not find admin user in role mapping", found );
    }
    
    public void testSetUserRolesForAnonymous()
        throws Exception
    {
        SecuritySystem securitySystem = this.getSecuritySystem();

        User anon = securitySystem.getUser( securitySystem.getAnonymousUsername(), "default" );

        Set<RoleIdentifier> roles = new HashSet<RoleIdentifier>();

        roles.add( new RoleIdentifier( "default", "role3" ) );

        securitySystem.setUsersRoles( anon.getUserId(), anon.getSource(), roles );

        boolean found = false;
        for ( CUserRoleMapping roleMapping : getSecurityConfiguration().getUserRoleMappings() )
        {
            if ( roleMapping.getUserId().equals( securitySystem.getAnonymousUsername() ) )
            {
                found = true;

                Assert.assertEquals( 1, roleMapping.getRoles().size() );
                Assert.assertEquals( "role3", roleMapping.getRoles().get( 0 ) );
            }
        }

        Assert.assertTrue( "did not find anon user in role mapping", found );
    }
    
    public void testLoadConfigWithInvalidEmail() throws Exception
    {   
        File securityXML = new File( CONFIG_DIR, "security.xml" );
        FileInputStream fis = null;
        FileWriter fileWriter = null;
        Configuration config = null;
        
        String userId = null;
        
        try
        {
            fis = new FileInputStream( securityXML );
            SecurityConfigurationXpp3Reader reader = new SecurityConfigurationXpp3Reader();
            config = reader.read( fis );
            
            IOUtil.close( fis );
            
            fileWriter = new FileWriter( securityXML );
            
            config.getUsers().get( 0 ).setEmail( "testLoadConfigWithInvalidEmail" );
            userId = config.getUsers().get( 0 ).getId();
            
            SecurityConfigurationXpp3Writer writer = new SecurityConfigurationXpp3Writer();
            writer.write( fileWriter, config );
            
        }
        finally
        {
            IOUtil.close( fis );
            IOUtil.close( fileWriter );
        }
        
        // now restart the security
        this.getSecuritySystem().start();
        
        // that should have went well,
        User user = this.getSecuritySystem().getUser( userId, "default" );
        Assert.assertEquals( "testLoadConfigWithInvalidEmail", user.getEmailAddress() );
        
    }

    private List<String> getRoleIds( User user )
    {
        List<String> roleIds = new ArrayList<String>();

        for ( RoleIdentifier role : user.getRoles() )
        {
            roleIds.add( role.getRoleId() );
        }

        return roleIds;
    }
    
    

}

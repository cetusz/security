package org.sonatype.security.mock.authorization;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.Typed;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.security.authorization.AbstractReadOnlyAuthorizationManager;
import org.sonatype.security.authorization.AuthorizationManager;
import org.sonatype.security.authorization.NoSuchPrivilegeException;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.authorization.RoleKey;

@Singleton
@Typed( value = AuthorizationManager.class )
@Named( value = "sourceB" )
public class MockAuthorizationManagerB
    extends AbstractReadOnlyAuthorizationManager
{

    public String getSource()
    {
        return "sourceB";
    }

    public Set<String> listPermissions()
    {
        Set<String> permissions = new HashSet<String>();

        permissions.add( "from-role:read" );
        permissions.add( "from-role:delete" );

        return permissions;
    }

    public Set<Role> listRoles()
    {
        Set<Role> roles = new HashSet<Role>();

        Role role1 = new Role();
        role1.setName( "Role 1" );
        role1.setKey( new RoleKey( "test-role-1", getSource() ) );
        role1.addPrivilege( "from-role1:read" );
        role1.addPrivilege( "from-role1:delete" );

        Role role2 = new Role();
        role2.setName( "Role 2" );
        role2.setKey( new RoleKey( "test-role-2", getSource() ) );
        role2.addPrivilege( "from-role2:read" );
        role2.addPrivilege( "from-role2:delete" );

        roles.add( role1 );
        roles.add( role2 );

        return roles;
    }

    public Privilege getPrivilege( String privilegeId )
        throws NoSuchPrivilegeException
    {
        return null;
    }

    public Role getRole( String roleId, String source )
        throws NoSuchRoleException
    {
        return null;
    }

    public Set<Privilege> listPrivileges()
    {
        return null;
    }

}

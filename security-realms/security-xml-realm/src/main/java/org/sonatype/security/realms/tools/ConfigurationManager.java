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
package org.sonatype.security.realms.tools;

import java.util.List;
import java.util.Set;

import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.security.authorization.NoSuchPrivilegeException;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.model.CPrivilege;
import org.sonatype.security.model.CRole;
import org.sonatype.security.model.CUser;
import org.sonatype.security.model.CUserRoleMapping;
import org.sonatype.security.realms.privileges.PrivilegeDescriptor;
import org.sonatype.security.realms.validator.SecurityValidationContext;
import org.sonatype.security.usermanagement.UserNotFoundException;

/**
 * The ConfigurationManager is a facade in front of the security modello model. It supports CRUD operations for
 * users/roles/privileges and user to role mappings.
 * 
 * @author Brian Demers
 */
public interface ConfigurationManager
{
    /**
     * Retrieve all users
     * 
     * @return
     */
    List<CUser> listUsers();

    /**
     * Retrieve all roles
     * 
     * @return
     */
    List<CRole> listRoles();

    /**
     * Retrieve all privileges
     * 
     * @return
     */
    List<CPrivilege> listPrivileges();

    /**
     * Retrieve all descriptors of available privileges
     * 
     * @return
     */
    List<PrivilegeDescriptor> listPrivilegeDescriptors();

    /**
     * Create a new user.
     * 
     * @param user
     */
    void createUser( CUser user, Set<String> roles )
        throws InvalidConfigurationException;

    /**
     * Create a new user and sets the password.
     * 
     * @param user
     * @param password
     */
    void createUser( CUser user, String password, Set<String> roles )
        throws InvalidConfigurationException;

    /**
     * Create a new user with a context to validate in.
     * 
     * @param user
     */
    void createUser( CUser user, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException;

    /**
     * Create a new user/password with a context to validate in.
     * 
     * @param user
     * @param password
     */
    void createUser( CUser user, String password, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException;

    /**
     * Create a new role
     * 
     * @param role
     */
    void createRole( CRole role )
        throws InvalidConfigurationException;

    /**
     * Create a new role with a context to validate in
     * 
     * @param role
     */
    void createRole( CRole role, SecurityValidationContext context )
        throws InvalidConfigurationException;

    /**
     * Create a new privilege
     * 
     * @param privilege
     */
    void createPrivilege( CPrivilege privilege )
        throws InvalidConfigurationException;

    /**
     * Create a new privilege with a context to validate in
     * 
     * @param privilege
     */
    void createPrivilege( CPrivilege privilege, SecurityValidationContext context )
        throws InvalidConfigurationException;

    /**
     * Retrieve an existing user
     * 
     * @param id
     * @return
     */
    CUser readUser( String id )
        throws UserNotFoundException;

    /**
     * Retrieve an existing role
     * 
     * @param id
     * @return
     */
    CRole readRole( String id )
        throws NoSuchRoleException;

    /**
     * Retrieve an existing privilege
     * 
     * @param id
     * @return
     */
    CPrivilege readPrivilege( String id )
        throws NoSuchPrivilegeException;

    /**
     * Update an existing user
     * 
     * @param user
     */
    void updateUser( CUser user, Set<String> roles )
        throws InvalidConfigurationException, UserNotFoundException;

    /**
     * Update an existing user with a context to validate in
     * 
     * @param user
     */
    void updateUser( CUser user, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException, UserNotFoundException;

    /**
     * Update an existing role
     * 
     * @param role
     */
    void updateRole( CRole role )
        throws InvalidConfigurationException, NoSuchRoleException;

    /**
     * Update an existing role with a context to validate in
     * 
     * @param role
     */
    void updateRole( CRole role, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchRoleException;

    void createUserRoleMapping( CUserRoleMapping userRoleMapping )
        throws InvalidConfigurationException;

    void createUserRoleMapping( CUserRoleMapping userRoleMapping, SecurityValidationContext context )
        throws InvalidConfigurationException;

    void updateUserRoleMapping( CUserRoleMapping userRoleMapping )
        throws InvalidConfigurationException, NoSuchRoleMappingException;

    void updateUserRoleMapping( CUserRoleMapping userRoleMapping, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchRoleMappingException;

    CUserRoleMapping readUserRoleMapping( String userId, String source )
        throws NoSuchRoleMappingException;

    List<CUserRoleMapping> listUserRoleMappings();

    void deleteUserRoleMapping( String userId, String source )
        throws NoSuchRoleMappingException;

    /**
     * Update an existing privilege
     * 
     * @param privilege
     */
    void updatePrivilege( CPrivilege privilege )
        throws InvalidConfigurationException, NoSuchPrivilegeException;

    /**
     * Update an existing privilege with a context to validate in
     * 
     * @param privilege
     */
    void updatePrivilege( CPrivilege privilege, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchPrivilegeException;

    /**
     * Delete an existing user
     * 
     * @param id
     */
    void deleteUser( String id )
        throws UserNotFoundException;

    /**
     * Delete an existing role
     * 
     * @param id
     */
    void deleteRole( String id )
        throws NoSuchRoleException;

    /**
     * Delete an existing privilege
     * 
     * @param id
     */
    void deletePrivilege( String id )
        throws NoSuchPrivilegeException;

    /**
     * Helper method to retrieve a property from the privilege
     * 
     * @param privilege
     * @param key
     * @return
     */
    String getPrivilegeProperty( CPrivilege privilege, String key );

    /**
     * Helper method to retrieve a property from the privilege
     * 
     * @param id
     * @param key
     * @return
     */
    String getPrivilegeProperty( String id, String key )
        throws NoSuchPrivilegeException;

    /**
     * Clear the cache and reload from file
     */
    void clearCache();

    /**
     * Save to disk what is currently cached in memory
     */
    void save();

    /**
     * Initialize the context used for validation
     * 
     * @return
     */
    SecurityValidationContext initializeContext();

    void cleanRemovedRole( String roleId );

    void cleanRemovedPrivilege( String privilegeId );
}

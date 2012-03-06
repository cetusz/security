package org.sonatype.security.web;

import java.util.Map;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.pam.FirstSuccessfulStrategy;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.security.authentication.FirstSuccessfulModularRealmAuthenticator;
import org.sonatype.security.authorization.ExceptionCatchingModularRealmAuthorizer;

/**
 * A Web implementation of the jsecurity SecurityManager. TODO: This duplicates what the DefaultRealmSecurityManager
 * does, because the have different parents. We should look into a better way of doing this. Something like pushing the
 * configuration into the SecuritySystem. The downside to that is we would need to expose an accessor for it. ( This
 * component is loaded from a servelet ), but that might be cleaner then what we are doing now.
 *
 * @deprecated use shiro-guice or other injection to wire up a RealmSecurityManager.
 */
@Singleton
@Typed( value = RealmSecurityManager.class )
@Named( value = "web" )
@Deprecated
public class WebRealmSecurityManager
    extends DefaultWebSecurityManager
//    implements org.apache.shiro.util.Initializable
{
    private Map<String, RolePermissionResolver> rolePermissionResolverMap;

    private Logger logger  = LoggerFactory.getLogger( getClass() );

    @Inject
    public WebRealmSecurityManager( Map<String, RolePermissionResolver> rolePermissionResolverMap )
    {
        this.rolePermissionResolverMap = rolePermissionResolverMap;

        // set the realm authenticator, that will automatically deligate the authentication to all the realms.
        FirstSuccessfulModularRealmAuthenticator realmAuthenticator = new FirstSuccessfulModularRealmAuthenticator();
        realmAuthenticator.setAuthenticationStrategy( new FirstSuccessfulStrategy() );

        // Authenticator
        this.setAuthenticator( realmAuthenticator );

        initialize();
    }

    public void initialize()
    {
        // This could be injected
        // Authorizer
        ExceptionCatchingModularRealmAuthorizer authorizer =
            new ExceptionCatchingModularRealmAuthorizer( this.getRealms() );

        // if we have a Role Permission Resolver, set it, if not, don't worry about it
        if ( !rolePermissionResolverMap.isEmpty() )
        {
            if ( rolePermissionResolverMap.containsKey( "default" ) )
            {
                authorizer.setRolePermissionResolver( rolePermissionResolverMap.get( "default" ) );
            }
            else
            {
                authorizer.setRolePermissionResolver( rolePermissionResolverMap.values().iterator().next() );
            }
            logger.debug( "RolePermissionResolver was set to " + authorizer.getRolePermissionResolver() );
        }
        else
        {
            logger.warn( "No RolePermissionResolver is set" );
        }
        this.setAuthorizer( authorizer );
    }

//    public void init()
//        throws ShiroException
//    {
//        // use cacheing for the sessions, we can tune this with a props file per application if needed
//        DefaultWebSessionManager webSessionManager = new DefaultWebSessionManager();
//        webSessionManager.setSessionDAO( new EnterpriseCacheSessionDAO() );
//        this.setSessionManager( webSessionManager );
//    }
}

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
package org.sonatype.security.realms.kenai;

import com.sonatype.security.realms.kenai.config.model.Configuration;
import org.sonatype.jettytestsuite.ServletInfo;
import org.sonatype.jettytestsuite.ServletServer;
import org.sonatype.jettytestsuite.WebappContext;
import org.sonatype.security.AbstractSecurityTestCase;
import org.sonatype.security.realms.kenai.config.KenaiRealmConfiguration;

import java.io.File;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;


public abstract class AbstractKenaiRealmTest
    extends AbstractSecurityTestCase
{

    protected final String username = "test-user";

    protected final String password = "password123";

    private ServletServer server;

    protected final static String DEFAULT_ROLE = "default-url-role";

    protected static final String AUTH_APP_NAME = "auth_app";

    protected int getTotalNumberOfProjects()
    {
        return 10;
    }

    protected ServletServer getServletServer()
        throws Exception
    {
        ServletServer server = new ServletServer();

        ServerSocket socket = new ServerSocket( 0 );
        int freePort = socket.getLocalPort();
        socket.close();

        server.setPort( freePort );

        WebappContext webapp = new WebappContext();
        server.setWebappContexts( Arrays.asList( webapp ) );

        webapp.setName( "auth_app" );
        org.sonatype.jettytestsuite.AuthenticationInfo authInfo = new org.sonatype.jettytestsuite.AuthenticationInfo();
        webapp.setAuthenticationInfo( authInfo );

        authInfo.setAuthMethod( "BASIC" );
        authInfo.setCredentialsFilePath( getSmarterBasedir() + "/target/test-classes/credentials.properties" );
        authInfo.setAuthPathSpec( "/api/login/*" );

        ServletInfo servletInfoAuthc = new ServletInfo();
        servletInfoAuthc.setName( "authc" );
        servletInfoAuthc.setMapping( "/api/login/*" );
        servletInfoAuthc.setServletClass( KenaiMockAuthcServlet.class.getName() );
        servletInfoAuthc.setParameters( new Properties() );

        ServletInfo servletInfoAuthz = new ServletInfo();
        servletInfoAuthz.setName( "authz" );
        servletInfoAuthz.setMapping( "/api/projects" );
        servletInfoAuthz.setServletClass( KenaiMockAuthzServlet.class.getName() );
        Properties params = new Properties();
        params.setProperty( KenaiMockAuthzServlet.TOTAL_PROJECTS_KEY, Integer.toString( getTotalNumberOfProjects() ) );
        servletInfoAuthz.setParameters( params );
        params.put( "resourceBase", getSmarterBasedir() + "/target/test-classes/data/" );

        webapp.setServletInfos( Arrays.asList( servletInfoAuthc, servletInfoAuthz ) );

        server.initialize();

        return server;
    }

    public String getSmarterBasedir()
    {
        String classFileName = "/" + getClass().getName().replaceAll( "\\.", "/" ) + ".class";
        URL classUrl = this.getClass().getResource( classFileName );

        String filename = classUrl.getFile().substring( 0, classUrl.getFile().indexOf( classFileName ) );
        File baseDir =
            new File( filename ).getParentFile().getParentFile(); // this should give us the directory above /target

        if ( baseDir.exists() )
        {
            return baseDir.getAbsolutePath();
        }
        else
        {
            return getBasedir();
        }
    }

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        server = this.getServletServer();
        // start the server
        server.start();

        KenaiRealmConfiguration kenaiRealmConfiguration = this.lookup( KenaiRealmConfiguration.class );
        Configuration configuration = kenaiRealmConfiguration.getConfiguration();
        configuration.setDefaultRole( DEFAULT_ROLE );
        configuration.setEmailDomain( "sonateyp.org" );
        configuration.setBaseUrl( server.getUrl( AUTH_APP_NAME ) + "/" ); // add the '/' to the end
        // kenaiRealmConfiguration.updateConfiguration( configuration );
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        server.stop();
        super.tearDown();
    }
}

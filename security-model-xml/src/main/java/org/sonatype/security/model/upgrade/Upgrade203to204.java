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
package org.sonatype.security.model.upgrade;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.configuration.upgrade.ConfigurationIsCorruptedException;
import org.sonatype.configuration.upgrade.UpgradeMessage;
import org.sonatype.security.model.v2_0_3.io.xpp3.SecurityConfigurationXpp3Reader;
import org.sonatype.security.model.v2_0_4.CUser;
import org.sonatype.security.model.v2_0_4.upgrade.BasicVersionUpgrade;

@Singleton
@Typed( SecurityUpgrader.class )
@Named( "2.0.3" )
public class Upgrade203to204
    implements SecurityUpgrader
{
    private static String DEFAULT_SOURCE = "default";

    private Logger logger = LoggerFactory.getLogger( getClass() );
    
    public Object loadConfiguration( File file )
        throws IOException,
            ConfigurationIsCorruptedException
    {
        FileReader fr = null;

        try
        {
            // reading without interpolation to preserve user settings as variables
            fr = new FileReader( file );

            SecurityConfigurationXpp3Reader reader = new SecurityConfigurationXpp3Reader();

            return reader.read( fr );
        }
        catch ( XmlPullParserException e )
        {
            throw new ConfigurationIsCorruptedException( file.getAbsolutePath(), e );
        }
        finally
        {
            if ( fr != null )
            {
                fr.close();
            }
        }
    }

    public void upgrade( UpgradeMessage message )
        throws ConfigurationIsCorruptedException
    {
        org.sonatype.security.model.v2_0_3.Configuration oldc = ( org.sonatype.security.model.v2_0_3.Configuration ) message.getConfiguration();

        org.sonatype.security.model.v2_0_4.Configuration newc =
            new SecurityVersionUpgrade().upgradeConfiguration( oldc );
        
        newc.setVersion( org.sonatype.security.model.v2_0_4.Configuration.MODEL_VERSION );
        message.setModelVersion( org.sonatype.security.model.v2_0_4.Configuration.MODEL_VERSION );
        message.setConfiguration( newc );
    }
    
    class SecurityVersionUpgrade extends BasicVersionUpgrade
    {

        @Override
        public CUser upgradeCUser( org.sonatype.security.model.v2_0_3.CUser cUser, CUser value )
        {
            CUser upgradedUser =  super.upgradeCUser( cUser, value );
            
            // get the old users name
            String name = cUser.getName();
            String[] nameParts = name.trim().split( " ", 2 );
            
            // the first name is everything to the left of the first space
            upgradedUser.setFirstName( nameParts[0] );
            
            // last name is everything else ( if it exists )
            if( nameParts.length > 1 )
            {
                upgradedUser.setLastName( nameParts[1] );
            }
            
            return upgradedUser;
        }
    }
}

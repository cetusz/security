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
package org.sonatype.security.locators.users;

import junit.framework.Assert;

import org.sonatype.guice.bean.containers.InjectedTestCase;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.authorization.RoleKey;

public class RoleTest
    extends InjectedTestCase
{

    public void testCompareDifferentId()
    {
        Role roleA = new Role();
        roleA.setName( "ID1" );
        roleA.setKey( new RoleKey( "ID1", "source" ) );

        Role roleB = new Role();
        roleB.setName( "ID2" );
        roleB.setKey( new RoleKey( "ID2", "source" ) );

        Assert.assertEquals( -1, roleA.compareTo( roleB ) );
        Assert.assertEquals( 1, roleB.compareTo( roleA ) );

    }

    public void testCompareDifferentSource()
    {
        Role roleA = new Role();
        roleA.setName( "ID1" );
        roleA.setKey( new RoleKey( "ID1", "source1" ) );

        Role roleB = new Role();
        roleB.setName( "ID1" );
        roleB.setKey( new RoleKey( "ID1", "source2" ) );

        Assert.assertEquals( -1, roleA.compareTo( roleB ) );
        Assert.assertEquals( 1, roleB.compareTo( roleA ) );

    }

}

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

import org.sonatype.security.model.Configuration;

/**
 * An abstract class that removes the boiler plate code of reading in the dynamic security configuration.
 * 
 * @author Brian Demers
 */
public abstract class AbstractDynamicSecurityResource
    implements DynamicSecurityResource
{

    protected boolean dirty = true;

    public boolean isDirty()
    {
        return dirty;
    }

    protected void setDirty( boolean dirty )
    {
        this.dirty = dirty;
    }

    protected abstract Configuration doGetConfiguration();

    public Configuration getConfiguration()
    {
        Configuration config = doGetConfiguration();
        // unset the dirty flag
        this.setDirty( false );
        return config;
    }
}

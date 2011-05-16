package org.sonatype.security.rest.roles;

import java.util.List;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;
import org.sonatype.security.rest.model.RoleAndPrivilegeListFilterResourceRequest;
import org.sonatype.security.rest.model.RoleAndPrivilegeListResource;
import org.sonatype.security.rest.model.RoleKeyResource;

public class FilterRequest
{
    private final boolean showPrivileges;

    private final boolean showRoles;

    private final boolean showExternalRoles;

    private final boolean onlySelected;

    private final String text;

    private final List<RoleKeyResource> roleIds;

    private final List<String> privilegeIds;

    private final List<RoleKeyResource> hiddenRoleIds;

    private final List<String> hiddenPrivilegeIds;

    private final String userId;

    public FilterRequest( RoleAndPrivilegeListFilterResourceRequest request )
    {
        this.showPrivileges = !request.getData().isNoPrivileges();
        this.showRoles = !request.getData().isNoRoles();
        this.showExternalRoles = !request.getData().isNoExternalRoles();
        this.onlySelected = request.getData().isOnlySelected();
        this.text = request.getData().getName();
        this.roleIds = request.getData().getSelectedRoleIds();
        this.privilegeIds = request.getData().getSelectedPrivilegeIds();
        this.hiddenRoleIds = request.getData().getHiddenRoleIds();
        this.hiddenPrivilegeIds = request.getData().getHiddenPrivilegeIds();
        this.userId = request.getData().getUserId();
    }

    public boolean isShowPrivileges()
    {
        return showPrivileges;
    }

    public boolean isShowRoles()
    {
        return showRoles;
    }

    public boolean isShowExternalRoles()
    {
        return showExternalRoles;
    }

    public boolean isOnlySelected()
    {
        return onlySelected;
    }

    public String getText()
    {
        return text;
    }

    public List<RoleKeyResource> getRoleIds()
    {
        return roleIds;
    }

    public List<String> getPrivilegeIds()
    {
        return privilegeIds;
    }

    public List<RoleKeyResource> getHiddenRoleIds()
    {
        return hiddenRoleIds;
    }

    public List<String> getHiddenPrivilegeIds()
    {
        return hiddenPrivilegeIds;
    }

    public String getUserId()
    {
        return userId;
    }

    public boolean applies( RoleAndPrivilegeListResource resource )
    {
        if ( resource != null )
        {
            if ( resource.getType().equals( "role" ) )
            {
                if ( ( ( isShowRoles() && !resource.isExternal() && !( getUserId() != null && getRoleIds().isEmpty() ) ) || ( isShowExternalRoles() && resource.isExternal() ) )
                    && ( !getHiddenRoleIds().contains( toRoleKey( resource.getId(), resource.getSource() ) ) )
                    && ( resource.isExternal() || ( ( ( getRoleIds().isEmpty() && !isOnlySelected() ) || getRoleIds().contains(
                        toRoleKey( resource.getId(), resource.getSource() ) ) ) ) )
                    && ( StringUtils.isEmpty( getText() ) || Pattern.compile( Pattern.quote( getText() ), Pattern.CASE_INSENSITIVE ).matcher( resource.getName() ).find() ) )
                {
                    return true;
                }
            }
            else if ( resource.getType().equals( "privilege" ) )
            {
                if ( isShowPrivileges()
                    && ( !getHiddenPrivilegeIds().contains( resource.getId() ) )
                    && ( ( getPrivilegeIds().isEmpty() && !isOnlySelected() ) || getPrivilegeIds().contains(
                        resource.getId() ) )
                    && ( StringUtils.isEmpty( getText() ) || Pattern.compile( Pattern.quote( getText() ), Pattern.CASE_INSENSITIVE ).matcher( resource.getName() ).find() ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    private RoleKeyResource toRoleKey( String id, String source )
    {
        RoleKeyResource r = new RoleKeyResource();
        r.setId( id );
        r.setSource( source );
        return r;
    }
}

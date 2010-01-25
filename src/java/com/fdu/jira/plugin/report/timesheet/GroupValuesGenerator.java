package com.fdu.jira.plugin.report.timesheet;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.security.PermissionManager;
import com.opensymphony.user.Group;
import com.opensymphony.user.User;
import com.opensymphony.user.UserManager;

public class GroupValuesGenerator implements ValuesGenerator {

    public Map getValues(Map params) {
        User u = (User) params.get("User");
        Map values = new TreeMap();
        values.put("", "");
        if (ManagerFactory.getPermissionManager()
                .hasPermission(Permissions.USER_PICKER, u)) {
            UserManager userManager = UserManager.getInstance();
            List groups = userManager.getGroups();
            for (Iterator i = groups.iterator(); i.hasNext();) {
                Group group = (Group) i.next();
                values.put(group.getName(), group.getName());
            }
        } else {
        }
        return values;
    }

}

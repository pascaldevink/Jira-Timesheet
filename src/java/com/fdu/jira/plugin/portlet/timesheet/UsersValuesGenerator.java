package com.fdu.jira.plugin.portlet.timesheet;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.core.user.FullNameComparator;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.security.Permissions;
import com.opensymphony.user.User;
import com.opensymphony.user.UserManager;
import com.opensymphony.user.EntityNotFoundException;
import org.apache.log4j.Logger;

public class UsersValuesGenerator implements ValuesGenerator {
    private static final Logger log = Logger.getLogger(UsersValuesGenerator.class);

    public Map getValues(Map params) {
        Map values = new LinkedHashMap();
        values.put("", "");
        UserManager userManager = UserManager.getInstance();
        User u = (User) params.get("User");
        List users = null;
        if (ManagerFactory.getPermissionManager()
                .hasPermission(Permissions.USER_PICKER, u)) {
            try {
                List userNames = userManager.getGroup("jira-users").getUsers();
                log.info("show only users from 'jira-users' group");
                users = new LinkedList();
                for (Iterator i = userNames.iterator(); i.hasNext();) {
                    String username = (String) i.next();
                    users.add(userManager.getUser(username));
                }
            } catch (EntityNotFoundException e) {
                log.info("'jira-users' group not found, show all users");
                users = userManager.getUsers();
            }
            Collections.sort(users, new FullNameComparator());

            for (Iterator i = users.iterator(); i.hasNext();) {
                User user = (User) i.next();
                values.put(user.getName(), user.getFullName());
            }
        }
        return values;
    }
}

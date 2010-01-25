package com.fdu.jira.plugin.report.timesheet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.priority.Priority;

public class PrioritiesValuesGenerator implements ValuesGenerator {

    public Map getValues(Map arg0) {
        Map values = new TreeMap();
        values.put("", "");
        ConstantsManager constantsManager =
            ManagerFactory.getConstantsManager();
        Collection priorities = constantsManager.getPriorityObjects();

        for (Iterator i = priorities.iterator(); i.hasNext();) {
            Priority priority = (Priority) i.next();
            values.put(priority.getId(), priority.getName());
        }
        return values;
    }

}

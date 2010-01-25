package com.fdu.jira.plugin.report.timesheet;

import java.util.Map;

public class ProjectValuesGenerator extends  com.atlassian.jira.portal.ProjectValuesGenerator{

	public Map getValues(Map arg0) {
        Map values = super.getValues(arg0);
        values.put("", "All Projects");
        return values;
    }
}


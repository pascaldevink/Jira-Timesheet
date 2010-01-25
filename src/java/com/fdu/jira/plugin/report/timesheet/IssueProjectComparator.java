package com.fdu.jira.plugin.report.timesheet;

import java.util.Comparator;

import com.atlassian.jira.issue.Issue;

/**
 * Created by IntelliJ IDEA.
 * User: avalez
 * Date: Aug 4, 2007
 * Time: 9:55:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class IssueProjectComparator implements Comparator {
    public int compare(Object o1, Object o2) {
        if((o1 == null || (o1 instanceof Issue)) && (o2 == null || (o2 instanceof Issue)))
        {
            if(o1 == null && o2 == null)
                return 0;
            if(o1 == null)
                return -1;
            if(o2 == null)
                return 1;
            else
                return ((Issue)o1).getKey().compareTo(((Issue)o2).getKey());
        } else
        {
            throw new IllegalArgumentException("Object passed must be null or of type Issue");
        }
    }
}

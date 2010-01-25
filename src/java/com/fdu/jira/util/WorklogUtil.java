package com.fdu.jira.util;

import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.issue.worklog.WorklogImpl;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import org.ofbiz.core.entity.GenericValue;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Temporary class, should removed when WorklogManager will have methods we need.
 */
public class WorklogUtil {
    // copied from OfBizWorklogStore (originated from WorklogManager)
    public static Worklog convertToWorklog(GenericValue gv, WorklogManager worklogManager, IssueManager issueManager) {
        Timestamp startDateTS = gv.getTimestamp("startdate");
        Timestamp createdTS = gv.getTimestamp("created");
        Timestamp updatedTS = gv.getTimestamp("updated");
        Issue issue = issueManager.getIssueObject(gv.getLong("issue"));
        Worklog worklog = new WorklogImpl(worklogManager, issue, gv.getLong("id"),
                gv.getString("author"), gv.getString("body"),
                startDateTS != null ? new Date(startDateTS.getTime()) : null,
                gv.getString("grouplevel"), gv.getLong("rolelevel"),
                gv.getLong("timeworked"), gv.getString("updateauthor"),
                createdTS != null ? new Date(createdTS.getTime()) : null,
                updatedTS != null ? new Date(updatedTS.getTime()) : null);
        return worklog;
    }


}

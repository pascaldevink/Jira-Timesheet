/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */
package com.fdu.jira.plugin.report.pivot;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.ofbiz.core.entity.EntityExpr;
import org.ofbiz.core.entity.EntityOperator;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.ofbiz.core.util.UtilMisc;

import com.atlassian.core.ofbiz.CoreFactory;
import com.atlassian.core.user.FullNameComparator;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.comparator.IssueKeyComparator;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverter;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverterImpl;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.web.util.OutlookDateManager;
import com.fdu.jira.util.TextUtil;
import com.fdu.jira.util.WorklogUtil;
import com.opensymphony.user.EntityNotFoundException;
import com.opensymphony.user.User;
import com.opensymphony.user.UserManager;

/**
 * Generate a summary of worked hours in a specified period. The time period is
 * divided by the specified value for display.
 */
public class Pivot extends AbstractReport {
    private static final Logger log = Logger.getLogger(Pivot.class);

    private final OutlookDateManager outlookDateManager;

    private final PermissionManager permissionManager;
    private WorklogManager worklogManager;
    private IssueManager issueManager;

    private final JiraAuthenticationContext authenticationContext;

    private static UserManager userManager = UserManager.getInstance();

    private Map allWorkLogs = new Hashtable();

    private Map workedIssues = new TreeMap(new IssueKeyComparator());

    private Map workedUsers = new TreeMap(new FullNameComparator());

	private SearchProvider searchProvider;

	private FieldVisibilityManager fieldVisibilityManager;

    public Pivot(JiraAuthenticationContext authenticationContext,
            OutlookDateManager outlookDateManager,
            PermissionManager permissionManager,
            WorklogManager worklogManager,
            IssueManager issueManager,
            SearchProvider searchProvider,
            FieldVisibilityManager fieldVisibilityManager) {
        this.authenticationContext = authenticationContext;
        this.outlookDateManager = outlookDateManager;
        this.permissionManager = permissionManager;
        this.worklogManager = worklogManager;
        this.issueManager = issueManager;
		this.searchProvider = searchProvider;
		this.fieldVisibilityManager = fieldVisibilityManager;
    }

    public boolean isExcelViewSupported() {
        return true;
    }

    // Retrieve time user has spent for period
    public void getTimeSpents(User remoteUser, Date startDate, Date endDate,
        Long projectId, Long filterId, boolean excelView) throws SearchException,
        GenericEntityException, EntityNotFoundException {
    	
        Set filteredIssues = new TreeSet();
        if (filterId != null) {
            log.info("Using filter: " + filterId);
            SearchRequest filter = ManagerFactory.getSearchRequestManager().getSearchRequest(remoteUser, filterId);
            SearchResults issues = searchProvider.search(filter.getQuery(), remoteUser, PagerFilter.getUnlimitedFilter());
            for (Iterator i = issues.getIssues().iterator(); i.hasNext();) {
	        	GenericValue value = (GenericValue) i.next();
	        	filteredIssues.add(value.getLong("id"));
	        }
        }
    	
        EntityExpr startExpr = new EntityExpr("startdate",
                EntityOperator.GREATER_THAN_EQUAL_TO, new Timestamp(
                        startDate.getTime()));
        EntityExpr endExpr = new EntityExpr("startdate",
                EntityOperator.LESS_THAN, new Timestamp(endDate.getTime()));
        List worklogs = CoreFactory.getGenericDelegator().findByAnd(
                "Worklog", UtilMisc.toList(startExpr, endExpr));

        log.info("Query returned : " + worklogs.size() + " worklogs");
        for (Iterator worklogsIterator = worklogs.iterator(); worklogsIterator
                .hasNext();) {
            GenericValue genericWorklog = (GenericValue) worklogsIterator
                    .next();
            //Worklog worklog = new Worklog(genericWorklog, remoteUser);
            Worklog worklog = WorklogUtil.convertToWorklog(genericWorklog, worklogManager, issueManager);
            Issue issue = ManagerFactory.getIssueManager().getIssueObject(
                    genericWorklog.getLong("issue"));

            if (issue != null 
            		&& (projectId == null || projectId.equals(issue.getLong("project")))
            		&& (filterId == null || filteredIssues.contains(issue.getId()))) {
                if (permissionManager.hasPermission(Permissions.BROWSE,
                        issue, remoteUser)) {
                    if (excelView) {
                        // excel view shows complete work log
                        List issueWorklogs = (List) allWorkLogs.get(issue);
                        if (issueWorklogs == null) {
                            issueWorklogs = new ArrayList();
                            allWorkLogs.put(issue, issueWorklogs);
                        }
                        issueWorklogs.add(worklog);
                    } else {
                        // html view shows summary hours per issue for each user
                        Map issueWorkLog = (Map) workedIssues.get(issue);
                        if (issueWorkLog == null) {
                            issueWorkLog = new Hashtable();
                            workedIssues.put(issue, issueWorkLog);
                        }

                        // user per issue
                        User user = userManager.getUser(worklog
                                .getAuthor());
                        long timespent = worklog.getTimeSpent().longValue();
                        Long worked = (Long) issueWorkLog.get(user);
                        if (worked != null) {
                            timespent += worked.longValue();
                        }
                        ;
                        worked = new Long(timespent);
                        issueWorkLog.put(user, worked);

                        // user total
                        timespent = worklog.getTimeSpent().longValue();
                        worked = (Long) workedUsers.get(user);
                        if (worked != null) {
                            timespent += worked.longValue();
                        }
                        worked = new Long(timespent);
                        workedUsers.put(user, worked);
                    }
                }
            }
        }
    }

    // Generate the report
    public String generateReport(ProjectActionSupport action, Map params,
        boolean excelView) throws Exception {
        User remoteUser = action.getRemoteUser();
        I18nBean i18nBean = new I18nBean(remoteUser);

        // Retrieve the project parameter
        Long projectId = ParameterUtils.getLongParam(params, "projectid");
        //      Retrieve the filter parameter
        Long filterId = ParameterUtils.getLongParam(params, "filterid");
        // Retrieve the start and end dates and the time interval specified by
        // the user
        Date endDate = getEndDate(params, i18nBean);
        Date startDate = getStartDate(params, i18nBean, endDate);

        // get time spents
        getTimeSpents(remoteUser, startDate, endDate, projectId, filterId, excelView);

        // Pass the issues to the velocity template
        DatePickerConverter dpc = new DatePickerConverterImpl(
                authenticationContext);
        params.put("startDate", dpc.getString(startDate));
        Calendar calendarDate = Calendar.getInstance();
        calendarDate.setTime(endDate);
        // timesheet report will add 1 day
        calendarDate.add(Calendar.DAY_OF_YEAR, -1); 
        params.put("endDate", dpc.getString(calendarDate.getTime()));
        params.put("outlookDate", outlookDateManager.getOutlookDate(i18nBean
                .getLocale()));
        params.put("fieldVisibility", fieldVisibilityManager);
        params.put("textUtil", new TextUtil(i18nBean));
        if (excelView) {
            params.put("allWorkLogs", allWorkLogs);
        } else {
            params.put("workedIssues", workedIssues);
            params.put("workedUsers", workedUsers);
        }

        return descriptor.getHtml(excelView ? "excel" : "view", params);
    }

    // Generate html report
    public String generateReportHtml(ProjectActionSupport action, Map params)
        throws Exception {
        return generateReport(action, params, false);
    }

    // Generate excel, report
    public String generateReportExcel(ProjectActionSupport action, Map params)
        throws Exception {
        return generateReport(action, params, true);
    }

    // Validate the parameters set by the user.
    public void validate(ProjectActionSupport action, Map params) {
        User remoteUser = action.getRemoteUser();
        I18nHelper i18nBean = new I18nBean(remoteUser);

        Date startDate = ParameterUtils.getDateParam(params, "startDate",
                i18nBean.getLocale());
        Date endDate = ParameterUtils.getDateParam(params, "endDate",
                i18nBean.getLocale());

        // The end date must be after the start date
        if (startDate != null && endDate != null
                && endDate.before(startDate)) {
            action.addError("endDate", action
                    .getText("report.pivot.before.startdate"));
        }
    }

    public static Date getEndDate(Map params, I18nBean i18nBean) {
        Date endDate = ParameterUtils.getDateParam(params, "endDate",
                i18nBean.getLocale());
        // set endDate rigth after the date user has specified
        Calendar calendarDate = Calendar.getInstance();
        if (endDate == null) {
            // round to midnight, do not include today
            calendarDate.set(Calendar.HOUR_OF_DAY, 0);
            calendarDate.set(Calendar.MINUTE, 0);
            calendarDate.set(Calendar.SECOND, 0);
            calendarDate.set(Calendar.MILLISECOND, 0);
        } else {
            // include the specified date
            calendarDate.setTime(endDate);
            calendarDate.add(Calendar.DAY_OF_YEAR, 1);
        }
        endDate = calendarDate.getTime();

        return endDate;
    }

    public static Date getStartDate(Map params, I18nBean i18nBean,
        Date endDate) {
        Date startDate = ParameterUtils.getDateParam(params, "startDate",
                i18nBean.getLocale());
        // set endDate rigth after the date user has specified
        if (startDate == null) {
            Calendar calendarDate = Calendar.getInstance();
            calendarDate.setTime(endDate);
            calendarDate.add(Calendar.WEEK_OF_YEAR, -1);
            startDate = calendarDate.getTime();
        }

        return startDate;
    }
}

/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */
package com.fdu.jira.plugin.report.timesheet;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.ofbiz.core.entity.EntityExpr;
import org.ofbiz.core.entity.EntityOperator;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.ofbiz.core.util.UtilMisc;

import com.atlassian.core.ofbiz.CoreFactory;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.issue.util.VisibilityValidator;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.comparator.IssueKeyComparator;
import com.atlassian.jira.issue.comparator.UserComparator;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.jira.web.bean.FieldVisibilityBean;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.web.util.OutlookDateManager;
import com.atlassian.jira.web.util.OutlookDate;
import com.fdu.jira.plugin.report.pivot.Pivot;
import com.fdu.jira.util.TextUtil;
import com.fdu.jira.util.WeekPortletHeader;
import com.fdu.jira.util.WorklogUtil;
import com.opensymphony.user.EntityNotFoundException;
import com.opensymphony.user.Group;
import com.opensymphony.user.User;
import com.opensymphony.user.UserManager;


/**
 * Generate a summary of worked hours in a specified period. The time period is
 * divided by the specifed value for display.
 */
public class TimeSheet extends AbstractReport {
    private static final Logger log = Logger.getLogger(TimeSheet.class);

    // A collection of interval start dates - correlating with the timeSpents
    // collection.    
    private List weekDays = new ArrayList();

    private final OutlookDateManager outlookDateManager;

    private Map allWorkLogs = new Hashtable();
    private Map weekWorkLog = new TreeMap(new UserComparator());

    private Map weekWorkLogShort = new TreeMap(new IssueKeyComparator());
    private Map weekTotalTimeSpents = new Hashtable();
    private Map userTotalTimeSpents = new Hashtable();
    private Map projectTimeSpents = new Hashtable();
    private Map userDayTotal = new Hashtable();

    /**
    * <p>Variable that contains the project times grouped by a specific field.</p>
    * <p>This Variable represents/contains tree nested maps [project to [fieldname to [date to time]]]</p>
    */
    private Map projectGroupedByFieldTimeSpents = new Hashtable();
    private PermissionManager permissionManager;
    private IssueManager issueManager;
    private WorklogManager worklogManager;
    private UserManager userManager;
    private VisibilityValidator visibilityValidator;

    public Map getWeekTotalTimeSpents() {
        return weekTotalTimeSpents;
    }

    public Map getWeekWorkLogShort() {
        return weekWorkLogShort;
    }

    public List getWeekDays() {
        return weekDays;
    }

    public TimeSheet(OutlookDateManager outlookDateManager,
            PermissionManager permissionManager,
            WorklogManager worklogManager,
            IssueManager issueManager,
            UserManager userManager,
            VisibilityValidator visibilityValidator) {
        this.worklogManager = worklogManager;
        this.outlookDateManager = outlookDateManager;
        this.permissionManager = permissionManager;
        this.issueManager = issueManager;
        this.userManager = userManager;
        this.visibilityValidator = visibilityValidator;
    }

    // Retrieve time user has spent for period
    public void getTimeSpents(User remoteUser, Date startDate, Date endDate, String targetUserName, 
    		boolean excelView, String priority, String targetGroup, Long projectId, Boolean showWeekends,
            Boolean showUsers, String groupByField, OutlookDate outlookDate)

            throws SearchException, GenericEntityException, EntityNotFoundException {
        EntityExpr startExpr = new EntityExpr("startdate",
                EntityOperator.GREATER_THAN_EQUAL_TO, new Timestamp(
                        startDate.getTime()));
        EntityExpr endExpr = new EntityExpr("startdate",
                EntityOperator.LESS_THAN, new Timestamp(endDate.getTime()));
        EntityExpr userExpr;
        if (targetGroup != null && targetGroup.length() != 0) {
            Group group = userManager.getGroup(targetGroup);
            userExpr = new EntityExpr("author",
                EntityOperator.IN, group.getUsers());
        } else {
            userExpr = new EntityExpr("author",
                EntityOperator.EQUALS, targetUserName);
        }

        log.info("Searching worklogs created since '" + startDate
                + "', till '" + endDate + "', by '" + targetUserName + "'");

        List worklogs = CoreFactory.getGenericDelegator().findByAnd(
                "Worklog",
                UtilMisc.toList(startExpr, endExpr, userExpr));
        log.info("Query returned : " + worklogs.size() + " worklogs");
        
        for (Iterator worklogsIterator = worklogs.iterator(); worklogsIterator
                .hasNext();) {
            GenericValue genericWorklog = (GenericValue) worklogsIterator
                    .next();
            Worklog worklog = WorklogUtil.convertToWorklog(genericWorklog, worklogManager, issueManager);

            ErrorCollection errorCollection = new SimpleErrorCollection();
            boolean isValidVisibility = visibilityValidator.isValidVisibilityData(new JiraServiceContextImpl(remoteUser,
                    errorCollection), "worklog", worklog.getIssue(), worklog.getGroupLevel(),
                    worklog.getRoleLevelId() != null ? worklog.getRoleLevelId().toString() : null);
            if (!isValidVisibility) {
                continue;
            }

            Issue issue = ManagerFactory.getIssueManager().getIssueObject(
                    genericWorklog.getLong("issue"));
            Project project = issue.getProjectObject();
            
            if (priority != null && priority.length() != 0
                    && !issue.getString("priority").equals(priority)) {
                continue; // exlude issues with other priorites than (if) selected
            }
            
            if (projectId != null && !project.getId().equals(projectId)){
            	continue; // exclude issues from other projects than (if) selected
            }
            
            User workedUser = userManager.getUser(genericWorklog.getString("author"));
            
            Date dateCreated = worklog.getStartDate();
            WeekPortletHeader weekDay = new WeekPortletHeader(dateCreated);
            if(showWeekends != null && !showWeekends.booleanValue()  && weekDay.isNonBusinessDay()){
            	continue; // exclude worklogs and issues that were started on weekends if no weekends desired to show
            }
            
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateCreated);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            Date dateOfTheDay = cal.getTime();
            Long dateCreatedLong  = new Long(cal.getTimeInMillis());

            long spent;
            
            if (!permissionManager.hasPermission(Permissions.BROWSE, issue,
                    remoteUser)) {
                continue; // exclude issues that users can't (shouldn't be
                            // allowed to) see
            }

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
                // per entry (report)
            	
                // per issue (portlet)
                Map weekTimeSpents = (Map) weekWorkLogShort.get(issue);
                if (weekTimeSpents == null) {
                    weekTimeSpents = new Hashtable();
                    weekWorkLogShort.put(issue, weekTimeSpents); // portlet
                }
                
                spent = worklog.getTimeSpent().longValue();
                Long dateSpent = (Long) weekTimeSpents.get(dateOfTheDay);
                
                if (dateSpent != null) {
                    spent += dateSpent.longValue();
                }

                weekTimeSpents.put(dateOfTheDay, new Long(spent));

                // per project per day                
                Map projectWorkLog = (Map) projectTimeSpents.get(project);
                if(projectWorkLog == null){
                	projectWorkLog = new Hashtable();
                	projectTimeSpents.put(project, projectWorkLog);
                }
                
                spent = worklog.getTimeSpent().longValue();
                
                Long projectSpent = (Long) projectWorkLog.get(dateOfTheDay);
                
                if(projectSpent != null){
                	spent += projectSpent.longValue();
                }
                
                projectWorkLog.put(dateOfTheDay, new Long(spent));

                // per project and field
                calculateTimesForProjectGroupedByField(groupByField, worklog,
                        issue, project, dateOfTheDay, outlookDate);

                // total per day
                spent = worklog.getTimeSpent().longValue();
                dateSpent = (Long) weekTotalTimeSpents.get(dateCreatedLong);
                if (dateSpent != null) {
                    spent += dateSpent.longValue();
                }
                weekTotalTimeSpents.put(dateCreatedLong, new Long((int)spent));
            	
                spent = worklog.getTimeSpent().longValue();
            	if(showUsers != null && showUsers.booleanValue()){ // is nul in portlet
	                Map userWorkLog = (Map) weekWorkLog.get(workedUser);
	                if (userWorkLog == null) {
	                    userWorkLog = new TreeMap(new IssueProjectComparator());
	                    weekWorkLog.put(workedUser, userWorkLog);
	                }
	                Map issueWorkLog = (Map) userWorkLog
	                        .get(issue);
	                if (issueWorkLog == null) {
	                    issueWorkLog = new Hashtable();
	                    userWorkLog.put(issue, issueWorkLog);
	                }
	                issueWorkLog.put(worklog, new Long(spent));

	                // total per issue
	                spent = worklog.getTimeSpent().longValue();
	                Map issueTotalTimeSpents = (Map) userTotalTimeSpents.get(workedUser);
	                if (issueTotalTimeSpents == null) {
	                    issueTotalTimeSpents = new TreeMap(new IssueKeyComparator());
	                    userTotalTimeSpents.put(workedUser, issueTotalTimeSpents);
	                }
	                Long issueSpent = (Long) issueTotalTimeSpents.get(issue);
	                if (issueSpent != null) {
	                    spent += issueSpent.longValue();
	                }            	
	                issueTotalTimeSpents.put(issue, new Long(spent));
            	}
            	
            	// TODO: Add logic for total worklog per user per day
            	log.warn("[TS] WorkedUser: " + workedUser);
            	log.warn("[TS] Issue: " + worklog.getIssue().getKey());
            	log.warn("[TS] Time spent: " + worklog.getTimeSpent());
            	log.warn("[TS] Day date: " + dateOfTheDay);
            	
            	Map userWorkDayLog = (Map) userDayTotal.get(workedUser);
            	
            	Integer t;
            	
            	if (userWorkDayLog == null) {
            		userWorkDayLog = new HashMap();
            		t = Integer.valueOf(worklog.getTimeSpent().intValue());
            	} else {
            		Integer timeAlreadySpent = (Integer) userWorkDayLog.get(dateOfTheDay);
            		
            		if (timeAlreadySpent == null) {
            			t = Integer.valueOf(worklog.getTimeSpent().intValue());
            		} else {
            			t = Integer.valueOf(timeAlreadySpent.intValue() + worklog.getTimeSpent().intValue());
            		}
            	}
            	
            	userWorkDayLog.put(dateOfTheDay, t);
            	userDayTotal.put(workedUser, userWorkDayLog);            	
            	
            	log.warn(userDayTotal.toString());

            }
        }
        I18nBean i18nBean = new I18nBean(remoteUser);
        
        // fill dates (ordered list) and week days (corresponding to each date)
        SimpleDateFormat df = new SimpleDateFormat("E",i18nBean.getLocale()); //, Locale.ENGLISH        
        Calendar calendarDate = Calendar.getInstance();
        calendarDate.setTime(startDate);
        while (endDate.after(calendarDate.getTime())) {
        	WeekPortletHeader wph = new WeekPortletHeader();
        	wph.setWeekDayDate(calendarDate.getTime());
        	wph.setWeekDayKey(df.format(wph.getWeekDayDate()));
        	
        	String businessDay = "";
        	if (calendarDate.get(Calendar.DATE) == Calendar.getInstance().get(Calendar.DATE) &&
        		calendarDate.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH) &&
        		calendarDate.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR))
        	{
        		businessDay = "toDay";
        	}
        	else if (wph.isNonBusinessDay() == true)
        	{
        		businessDay = "nonBusinessDay";
        	}
        	
        	wph.setWeekDayCSS(businessDay); //rowHeaderDark redText red-highlight
        	
        	if(showWeekends == null || showWeekends.booleanValue()  || !wph.isNonBusinessDay()){ // check if allowed to show weekends and if this it is a weekend
        		weekDays.add(wph);
        	}
            calendarDate.add(Calendar.DAY_OF_YEAR, 1);
        }        
    }

    /**
     * Calculates the times spend per project, grouped by a field.
     * <p/>
     * Stores results in {@link #projectGroupedByFieldTimeSpents}.
     *
     * @param groupByFieldID - id of the field the group by should base on
     * @param worklog        - reference to for-loop variable from @see #getTimeSpents
     * @param issue          - reference to for-loop variable from @see #getTimeSpents
     * @param project        - reference to for-loop variable from @see #getTimeSpents
     * @param dateOfTheDay   - reference to for-loop variable from @see #getTimeSpents
     */
    private void calculateTimesForProjectGroupedByField(String groupByFieldID,
            Worklog worklog, Issue issue, Project project, Date dateOfTheDay,
                OutlookDate outlookDate) {
        long spent;

        if (groupByFieldID != null) {

            // Set field value
            String fieldValue = TextUtil.getFieldValue(groupByFieldID, issue, outlookDate);

            // dimension Project to field
            Map projectToFieldWorkLog = (Map) projectGroupedByFieldTimeSpents
                    .get(project);
            if (projectToFieldWorkLog == null) {
                projectToFieldWorkLog = new Hashtable();
                projectGroupedByFieldTimeSpents.put(project,
                        projectToFieldWorkLog);
            }

            // dimension Field to Time
            Map fieldToTimeWorkLog = (Map) projectToFieldWorkLog
                    .get(fieldValue);
            if (fieldToTimeWorkLog == null) {
                fieldToTimeWorkLog = new Hashtable();
                projectToFieldWorkLog.put(fieldValue, fieldToTimeWorkLog);
            }

            spent = worklog.getTimeSpent().longValue();
            Long projectGroupedSpent = (Long) fieldToTimeWorkLog
                    .get(dateOfTheDay);
            if (projectGroupedSpent != null) {
                spent += projectGroupedSpent.longValue();
            }

            fieldToTimeWorkLog.put(dateOfTheDay, new Long(spent));
		}
	}

    // Generate the report
    public String generateReport(ProjectActionSupport action, Map params,
        boolean excelView) throws Exception {
        User remoteUser = action.getRemoteUser();
        I18nBean i18nBean = new I18nBean(remoteUser);

        // Retrieve the start and end date specified by the user
        Date endDateTmp = Pivot.getEndDate(params, i18nBean);
        Date startDateTmp = Pivot.getStartDate(params, i18nBean, endDateTmp);
        
        // For a good precision, we work with this date's format : YYYY-MM-DDT00:00:00.000
        // And sometimes, when we don't have informed the parameter startDate or/and endDate, we have this format : YYYY-MM-DDT00:00:00.mmm
        // So the report is not correct
        Date endDate = new Date (endDateTmp.getYear(), endDateTmp.getMonth(), endDateTmp.getDate());
        Date startDate = new Date (startDateTmp.getYear(), startDateTmp.getMonth(), startDateTmp.getDate());
        
        User targetUser = ParameterUtils.getUserParam(params, "targetUser");
        String priority = ParameterUtils.getStringParam(params, "priority");
        String targetGroup = ParameterUtils.getStringParam(params, "targetGroup");

        Long projectId = null; 
        if(!"".equals(ParameterUtils.getStringParam(params, "project"))){
        	projectId = ParameterUtils.getLongParam(params, "project");
        }
        
        Boolean showWeekends = null;
        if ("true".equalsIgnoreCase(ParameterUtils.getStringParam(params, "weekends"))) 
        	showWeekends=new Boolean (true); 
        else 
        	showWeekends=new Boolean (false);
        
        Boolean showUsers = null;
        if ("true".equalsIgnoreCase(ParameterUtils.getStringParam(params, "showUsers"))) 
        	showUsers=new Boolean (true); 
        else 
        	showUsers=new Boolean (false);
        
        if (targetUser == null) {
            targetUser = remoteUser;
        }

        // read ID of the 'group by' field from parameter map
        String groupByField = ParameterUtils.getStringParam(params, "groupByField");
        if (groupByField != null) {
            if (groupByField.trim().length() == 0) {
                groupByField = null;
            }  else if (ManagerFactory.getFieldManager().getField(groupByField) == null) {
                log.error("GroupByField ' " + groupByField + "' does not exist");
                groupByField = null;
            }
        }

        OutlookDate outlookDate = outlookDateManager
                .getOutlookDate(i18nBean.getLocale());

        // get time spents
        if (remoteUser != null) {
            getTimeSpents(remoteUser, startDate, endDate, targetUser.getName(),
                    excelView, priority, targetGroup, projectId, showWeekends,
                    showUsers, groupByField, outlookDate);
        }

        // Pass the issues to the velocity template
        Map velocityParams = new HashMap();
        velocityParams.put("startDate", startDate);
        velocityParams.put("endDate", endDate);
        velocityParams.put("weekDays", weekDays);
        velocityParams.put("showUsers", showUsers);
        
        if (excelView) {
            velocityParams.put("allWorkLogs", allWorkLogs);
        } else {
            if (showUsers.booleanValue()) {
                velocityParams.put("weekWorkLog", weekWorkLog);
            } else {
                velocityParams.put("weekWorkLog", weekWorkLogShort);
            }
            velocityParams.put("weekTotalTimeSpents", weekTotalTimeSpents);
            velocityParams.put("userTotalTimeSpents", userTotalTimeSpents);
            velocityParams.put("projectTimeSpents", projectTimeSpents);
            velocityParams.put("projectGroupedTimeSpents", projectGroupedByFieldTimeSpents);
            velocityParams.put("userDayTotal", userDayTotal);
        }
        velocityParams.put("groupByField", groupByField);
        velocityParams.put("outlookDate", outlookDate);
        velocityParams.put("fieldVisibility", new FieldVisibilityBean());
        velocityParams.put("textUtil", new TextUtil(i18nBean));

        return descriptor.getHtml(excelView ? "excel" : "view",
                velocityParams);
    }

    // Validate the parameters set by the user.
    public void validate(ProjectActionSupport action, Map params) {
        // nothing to do, all parameters are optional,
        // and there is no longer restriction for one month period
    }

    public boolean isExcelViewSupported() {
        return true;
    }

    // Generate html report
    public String generateReportHtml(ProjectActionSupport action, Map params) throws Exception {
        return generateReport(action, params, false);
    }

    // Generate excel, report
    public String generateReportExcel(ProjectActionSupport action, Map params) throws Exception {
        return generateReport(action, params, true);
    }

}

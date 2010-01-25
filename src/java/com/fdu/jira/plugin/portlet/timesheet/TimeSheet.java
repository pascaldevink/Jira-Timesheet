/*
 * Created on 03.04.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.fdu.jira.plugin.portlet.timesheet;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.configurable.ObjectConfigurationException;
import com.atlassian.jira.bc.issue.util.VisibilityValidator;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverter;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverterImpl;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.portal.PortletConfiguration;
import com.atlassian.jira.portal.PortletImpl;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.web.bean.FieldVisibilityBean;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.web.util.OutlookDateManager;
import com.fdu.jira.util.TextUtil;
import com.opensymphony.user.EntityNotFoundException;
import com.opensymphony.user.User;
import com.opensymphony.user.UserManager;

import org.apache.log4j.Logger;

/**
 * Generate a summary of worked hours for current week.
 */
public class TimeSheet extends PortletImpl {
    private static final Logger log = Logger.getLogger(TimeSheet.class);

    // References to managers required for this portlet
    private final OutlookDateManager outlookDateManager;
    private WorklogManager worklogManager;
    private IssueManager issueManager;
    private UserManager userManager;
    private VisibilityValidator visibilityValidator;

    public TimeSheet(JiraAuthenticationContext authenticationContext,
            PermissionManager permissionManager,
            ApplicationProperties applicationProperties,
            OutlookDateManager outlookDateManager,
            WorklogManager worklogManager,
            IssueManager issueManager,
            UserManager userManager,
            VisibilityValidator visibilityValidator) {
		super(authenticationContext, permissionManager, applicationProperties);
		this.outlookDateManager = outlookDateManager;
        this.worklogManager = worklogManager;
        this.issueManager = issueManager;
        this.userManager = userManager;
        this.visibilityValidator = visibilityValidator;
    }

    // TODO: move to util
    public int getPortletParamInt(PortletConfiguration portletConfiguration,
        String paramName, int defaultValue) {

        
        
        Long paramValue = new Long(defaultValue);
        try {
            paramValue = portletConfiguration.getLongProperty(paramName);
        } catch (ObjectConfigurationException oce) {
            oce.printStackTrace();
        }
        
        return paramValue.intValue();
    }
    
 

    // Pass the data required for the portlet display to the view template
    protected Map getVelocityParams(PortletConfiguration portletConfiguration) {
        Map params = new HashMap();
        User user = authenticationContext.getUser();
        
        I18nBean i18nBean = new I18nBean(user);
        // Retrieve the number of minute to add for a Low Worklog
        int numOfWeeks = getPortletParamInt(portletConfiguration,
                "numOfWeeks", 1);
        // Retrieve the week day that is to be a first day
        int reporting_day = getPortletParamInt(portletConfiguration,
                "reportingDay", Calendar.MONDAY);

        // Calculate the start and end dates
        Calendar currentDate = Calendar.getInstance();
        Calendar endDate = Calendar.getInstance();

        if (reporting_day != 0 /* today */) {
            endDate.set(Calendar.DAY_OF_WEEK, reporting_day);
        } else {
            endDate.add(Calendar.DAY_OF_MONTH, 1); // include today
        }

        endDate.set(Calendar.HOUR_OF_DAY, 0);
        endDate.set(Calendar.MINUTE, 0);
        endDate.set(Calendar.SECOND, 0);
        endDate.set(Calendar.MILLISECOND, 0);

        if (endDate.before(currentDate)) {
            endDate.add(Calendar.WEEK_OF_MONTH, 1);
        }

        Calendar startDate = Calendar.getInstance();
        startDate.setTime(endDate.getTime());
        startDate.add(Calendar.WEEK_OF_YEAR, -numOfWeeks);

        try {
            // get time spents
            com.fdu.jira.plugin.report.timesheet.TimeSheet ts = new com.fdu.jira.plugin.report.timesheet.TimeSheet(
                    outlookDateManager,
                    permissionManager,
                    worklogManager,
                    issueManager,
                    userManager,
                    visibilityValidator);

            User targetUser = user;

            // Retrieve user to show timesheet for
            String targetUserName =
                portletConfiguration.getProperty("targetUser");

            if (targetUserName.length() != 0) {
                 try {
                     targetUser = UserManager.getInstance(
                             ).getUser(targetUserName);
                 } catch (EntityNotFoundException e) {
                     if (targetUser != null) { // anonymous
                         targetUserName = targetUser.getName();
                         log.warn("Can't find specified user, will use current one", e);
                     }
                 }
            } else if (targetUser != null) /* anonymous access */ {
                targetUserName = targetUser.getName();
            }
            params.put("targetUser", targetUser);
            
            ts.getTimeSpents(user, startDate.getTime(), endDate.getTime(),
                    targetUserName, false, null, null, null, null, null, null, null);

            // pass parameters
            params.put("weekDays", ts.getWeekDays());
            params.put("weekWorkLog", ts.getWeekWorkLogShort());
            params.put("weekTotalTimeSpents", ts.getWeekTotalTimeSpents());
            params.put("fieldVisibility", new FieldVisibilityBean());
            DatePickerConverter dpc = new DatePickerConverterImpl(
                    authenticationContext);
            params.put("dpc", dpc);
            params.put("startDate", startDate.getTime());
            endDate.add(Calendar.DAY_OF_YEAR, -1); // timeshet report will
            // add 1 day
            params.put("endDate", endDate.getTime());
            params.put("textUtil", new TextUtil(i18nBean));
            params.put("outlookDate", outlookDateManager.getOutlookDate(i18nBean.getLocale()));
            params.put("loggedin", new Boolean(authenticationContext.getUser() != null));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return params;

    }
}

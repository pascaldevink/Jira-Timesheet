package com.fdu.jira.util;

import java.util.Calendar;
import java.util.Map;

import org.apache.commons.collections.map.LinkedMap;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.web.bean.I18nBean;

public class WeekDaysValuesGenerator implements ValuesGenerator {

    public Map getValues(Map params) {
        Map weekDays = new LinkedMap();
        JiraAuthenticationContext authenticationContext =
            ComponentManager.getInstance().getJiraAuthenticationContext();
        I18nBean i18n = new I18nBean(authenticationContext.getUser(),"com.fdu.jira.util.weekdays");
        weekDays.put(new Long(0),i18n.getText("com.fdu.jira.util.weekdays.today"));
        weekDays.put(new Long(Calendar.MONDAY),i18n.getText("com.fdu.jira.util.weekdays.monday"));
        weekDays.put(new Long(Calendar.TUESDAY),i18n.getText("com.fdu.jira.util.weekdays.tuesday"));
        weekDays.put(new Long(Calendar.WEDNESDAY),i18n.getText("com.fdu.jira.util.weekdays.wednesday"));
        weekDays.put(new Long(Calendar.THURSDAY),i18n.getText("com.fdu.jira.util.weekdays.thursday"));
        weekDays.put(new Long(Calendar.FRIDAY),i18n.getText("com.fdu.jira.util.weekdays.friday"));
        weekDays.put(new Long(Calendar.SATURDAY),i18n.getText("com.fdu.jira.util.weekdays.saturday"));
        weekDays.put(new Long(Calendar.SUNDAY),i18n.getText("com.fdu.jira.util.weekdays.sunday"));
        return weekDays;
    }

}

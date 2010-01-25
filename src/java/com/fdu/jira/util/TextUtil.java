package com.fdu.jira.util;

import com.atlassian.core.util.DateUtils;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.ofbiz.OfBizValueWrapper;
import com.atlassian.jira.issue.fields.*;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.web.util.OutlookDate;

import java.text.NumberFormat;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.*;

/**
 * @author avalez
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class TextUtil {
	private int hoursPerDay;
	private int daysPerWeek;
	private ResourceBundle resourceBundle;
	private NumberFormat decimalFormat;
	private NumberFormat percentFormat;
	private Pattern servletUrlPattern;
	
	/**
	 * No such constructor
	 *
	 */
	private TextUtil()
	{
	}
	
	/**
	 * Instantiate TextUtil object
	 * @param i18nBean
	 */
	public TextUtil(I18nBean i18nBean) {
		hoursPerDay = (new Integer(ManagerFactory.getApplicationProperties().getDefaultBackedString("jira.timetracking.hours.per.day"))).intValue();
		daysPerWeek = (new Integer(ManagerFactory.getApplicationProperties().getDefaultBackedString("jira.timetracking.days.per.week"))).intValue();
		resourceBundle = i18nBean.getDefaultResourceBundle();
		decimalFormat = NumberFormat.getInstance(i18nBean.getLocale());
		percentFormat = NumberFormat.getPercentInstance(i18nBean.getLocale());
		servletUrlPattern = Pattern.compile("^(.+?)://(.+?)/(.+)$");
	}

	
	/**
	 * Format duration value
	 * @param value
	 * @return pretty formatted value, using Jira settings for hours in day, and days in week.
	 */
    public String getPrettyDuration(long value)
	{
		return DateUtils.getDurationPretty(value, hoursPerDay, daysPerWeek, resourceBundle);
	}
    
	/**
	 * Format duration value in hours
	 * @param value
	 * @return value
	 */
    public String getPrettyHours(long value)
    {
    	return getHours(value) + "h";
    }

	/**
	 * Format duration value in hours
	 * @param value
	 * @return pretty formatted value
	 */
    public String getHours(long value)
    {
    	return decimalFormat.format(((float)value) / 60 / 60);
    }
    
    /**
     * Expand relative url to absolute path
     */
    public String expandUrl(HttpServletRequest req, String url) {
    	String path = req.getRequestURL().toString();
    	Matcher m = servletUrlPattern.matcher(path);
    	if (m.matches()) {
    		return m.group(1) + "://" + m.group(2) + url;
    	} else {
    		return url;
    	}
    }

    /** 
     * Convert to percents.
     * 
     * @param value value
     * @param hundred hundreds of value
     * @return percetns
     */
    public String getPercents(long value, long hundred) {
        if (hundred == 0L) {
            return "&nbsp;";
        }
        float percents = ((float)value) * 100 / hundred;
        return percentFormat.format(percents);
    }

    /**
     * Get issue field value by field id for the issue.
     *
     * @param groupByFieldID
     * @param issue
     * @param outlookDate
     * @return String value concatenated for multi-select or null.
     */
    public static String getFieldValue(String groupByFieldID, Issue issue,
                                       OutlookDate  outlookDate) {
        Field groupByField = ManagerFactory.getFieldManager().getField(
                groupByFieldID);

        // Set field value
        String fieldValue = null;
        if (groupByField instanceof CustomField) {
            Object value = issue
                    .getCustomFieldValue((CustomField) groupByField);
            if (value != null) {
                if (value instanceof List) {
                    fieldValue = getMultiValue((List) value);
                } else if (value instanceof Date ) {
                    fieldValue = outlookDate.format((Date) value);
                } else {
                    fieldValue = value.toString();
                }
            }
        } else if (groupByField instanceof ComponentsSystemField) {
            /*
               * Implementation to handle GroupBy Component. Issue TIME-54.
               * Caveat: When there are multiple components assigned to one
               * issue, the component names are concatenated and the grouping
               * is done by the concatenated string. The issue isn't
               * counted/grouped for each component.
               */
            fieldValue = getMultiValue(issue.getComponents());
        } else if (groupByField instanceof AffectedVersionsSystemField) {
            fieldValue = getMultiValue(issue.getAffectedVersions());
        } else if (groupByField instanceof FixVersionsSystemField) {
            fieldValue = getMultiValue(issue.getFixVersions());
        } else {
            // TODO Couldn't find an easy way to get each fields value as
            // string. Workaround.
            try {
                fieldValue = (String) issue.getString(groupByFieldID);
            } catch (RuntimeException e) {
                fieldValue = "FieldTypeValueNotApplicableForGrouping";
            }
        }

        // need a string as reference element in map for grouping
        if (fieldValue == null || fieldValue.trim().length() == 0) {
            fieldValue = "NoValueForFieldOnIssue";
        }

        return fieldValue;        
    }

    private static String getMultiValue(Collection values) {
        StringBuffer fieldValue = new StringBuffer();
        for (Iterator i = values.iterator(); i.hasNext();) {
            Object o = i.next();
            String value;
            if (o instanceof Map) {
                Map map= (Map) o;
                // do not check if (map.containsKey("name")) intentionally
                // for better diagnosability
                value = (String) map.get("name");
            } else if (o instanceof OfBizValueWrapper) {
                OfBizValueWrapper map= (OfBizValueWrapper) o;
                value = map.getString("name");
            } else {
                value = o.toString();
            }
            if (fieldValue.length() != 0) {
                fieldValue.append(", ");
            }
            fieldValue.append(value);
        }
        return fieldValue.toString();
    }

    public static String getFieldName(String fieldID) {
        Field groupByField = ManagerFactory.getFieldManager().getField(
                fieldID);
        return groupByField.getName();
    }
}

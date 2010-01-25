package com.fdu.jira.plugin.report.timesheet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import org.apache.commons.collections.map.LinkedMap;

public class GroupByFieldValuesGenerator implements ValuesGenerator {
    private ApplicationProperties applicationProperties;

    public GroupByFieldValuesGenerator() {
        this.applicationProperties = ComponentManager.getInstance().getApplicationProperties();
    }

    public Map getValues(Map arg0) {
        Map values = new LinkedMap();
        values.put("", "");

        Set fields = ManagerFactory.getFieldManager().getAllSearchableFields();

        Set sortedFields = new TreeSet(new Comparator() {
            public int compare(Object o, Object other) {
                // no need to check for nulls or type
                Field f = (Field) o;
                Field otherF = (Field) other;
                return f.getName().compareTo(otherF.getName());
            }
        });
        sortedFields.addAll(fields);


        String groupByFieldsP = applicationProperties.
                getDefaultString("jira.plugin.timesheet.groupbyfields");
        Collection groupByFields = null;
        if (groupByFieldsP != null) {
            groupByFields = Arrays.asList(groupByFieldsP.split(","));
        }

        for (Iterator i = sortedFields.iterator(); i.hasNext();) {
            Field field = (Field) i.next();
            if (groupByFields == null ||
                    groupByFields.contains(field.getId()) ||
                    groupByFields.contains(field.getName())) {
                values.put(field.getId(), field.getName());
            }
        }

        return values;
    }

   

}

package com.fdu.jira.plugin.report.pivot;

import java.util.Map;

import com.atlassian.jira.portal.SearchRequestValuesGenerator;

public class OptionalSearchRequestValuesGenerator extends
		SearchRequestValuesGenerator {

	public Map getValues(Map arg0) {
		Map values = super.getValues(arg0);
		values.put("", "");
		return values;
	}

}

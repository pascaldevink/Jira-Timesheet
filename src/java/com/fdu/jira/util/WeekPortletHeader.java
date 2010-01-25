package com.fdu.jira.util;

import java.util.Date;
import java.util.Calendar;

public class WeekPortletHeader {
	 private String weekDayKey;
	 private Date weekDayDate;   	 
	 private String weekDayCSS;
	 
	 public WeekPortletHeader() {
		 
	 }
	 
	 public WeekPortletHeader(Date date) { 
		 this.weekDayDate = date;
	 }
	 
	 public String getWeekDayKey() {
		 return weekDayKey;
	 }
	 
	 public void setWeekDayKey(String aWeekDayKey) {
		 this.weekDayKey = aWeekDayKey;
	 }
	 
	 public Date getWeekDayDate() {
		 return weekDayDate;
	 }
	 
	 public void setWeekDayDate(Date aWeekDayDate) {
		 this.weekDayDate = aWeekDayDate;
	 }
	 
	 public String getWeekDayCSS() {
		 return weekDayCSS;
	 }
	 
	 public void setWeekDayCSS(String aWeekDayCSS) {
		 this.weekDayCSS = aWeekDayCSS;
	 }
	 
	 public boolean isBusinessDay() {
		 return !isNonBusinessDay();
	 }
 
	 public boolean isNonBusinessDay() {
		 Calendar calendarHeaderDate = Calendar.getInstance();
	     calendarHeaderDate.setTime(weekDayDate);
	     int dayOfWeek = calendarHeaderDate.get(Calendar.DAY_OF_WEEK);
		 return (dayOfWeek == 7 || dayOfWeek == 1);
	 }
}
	


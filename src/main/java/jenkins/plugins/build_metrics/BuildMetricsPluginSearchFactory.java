package jenkins.plugins.build_metrics;

import jenkins.plugins.build_metrics.stats.StatsFactory;
import jenkins.plugins.build_metrics.stats.StatsHelper;
import hudson.plugins.global_build_stats.GlobalBuildStatsPlugin;
import hudson.plugins.global_build_stats.business.GlobalBuildStatsBusiness;
import hudson.plugins.global_build_stats.model.BuildSearchCriteria;
import hudson.plugins.global_build_stats.model.BuildHistorySearchCriteria;
import hudson.plugins.global_build_stats.model.JobBuildSearchResult;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.Job;
import hudson.model.Cause;

import org.kohsuke.stapler.StaplerRequest;

import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;

public class BuildMetricsPluginSearchFactory {
	/* the following static values come back from the search form*/
	public final static String RANGE_DAYS = "Days";
	public final static String RANGE_WEEKS = "Weeks";
	public final static String RANGE_MONTHS = "Months";
	public final static String RANGE_YEARS = "Years";
	
	public BuildMetricsPluginSearchFactory(){}
	
	public StatsFactory getBuildStats(BuildMetricsSearch bms){
	  return StatsFactory.generateStats(searchBuilds(bms),bms);
	}
	
	public List<JobBuildSearchResult> searchBuilds(BuildMetricsSearch bms){
	  GlobalBuildStatsBusiness business = GlobalBuildStatsPlugin.getPluginBusiness();
	  BuildSearchCriteria criteria = createBuildSearchCriteria(bms);
	  return business.searchBuilds(createBuildHistorySearchCriteria(bms, criteria));
	}

	public List<BuildMetricsBuild> getFailedBuilds(BuildMetricsSearch bms){
	  GlobalBuildStatsBusiness business = GlobalBuildStatsPlugin.getPluginBusiness();
	  BuildSearchCriteria criteria = createFailedBuildSearchCriteria(bms);
	  List<JobBuildSearchResult> results = business.searchBuilds(createBuildHistorySearchCriteria(bms, criteria));
	  List<BuildMetricsBuild> failedBuilds = new ArrayList<BuildMetricsBuild>();
	  boolean addThis=true;
	  for(JobBuildSearchResult result: results){
		  
		if ( !bms.getCauseFilter().isEmpty() ) {
			String cause = StatsHelper.findBuildDescription(result.getJobName(), result.getBuildNumber());
			String regexp = StatsHelper.fieldRegex(bms.getCauseFilter());
			if ( cause!=null && cause.matches(regexp) ) {
				  addThis=true;
			} else
				addThis=false;
		} else
			addThis=true;

		if ( addThis )
		failedBuilds.add(
		    new BuildMetricsBuild( 
		      result.getBuildNumber(),
			    result.getJobName(),
			    result.getNodeName(),
			    result.getUserName(),
			    result.getBuildDate(),
			    result.getDuration(),
			    result.getResult().getLabel(),
			    StatsHelper.findBuildDescription(result.getJobName(), result.getBuildNumber())
			));
	  }
	  return failedBuilds;
	}
	
	public Long getStartDate(int range, String rangeUnits){
		int iRange = range * -1;
		int iUnits = Calendar.DAY_OF_YEAR;//default = RANGE_DAYS
		if(RANGE_WEEKS.equals(rangeUnits)){
		  iUnits = Calendar.WEEK_OF_YEAR;
		}else if(RANGE_MONTHS.equals(rangeUnits)){
	      iUnits = Calendar.MONTH;
		}else if(RANGE_YEARS.equals(rangeUnits)){
	      iUnits = Calendar.YEAR;
		}
		Calendar tmpCal = Calendar.getInstance();
		tmpCal.add(iUnits, iRange);
		return Long.valueOf(tmpCal.getTimeInMillis());
	}
	
	public Long getDefaultStartDate(){
		Calendar tmpCal = Calendar.getInstance();
		tmpCal.roll(Calendar.WEEK_OF_YEAR, -2);
		return Long.valueOf(tmpCal.getTimeInMillis());
	}
	
	public Long getDefaultEndDate(){
		return Long.valueOf(Calendar.getInstance().getTimeInMillis());
	}
	
	public BuildMetricsSearch createBuildMetricsSearch(StaplerRequest req){
		Long startTime = null;
		Long endTime = null;
		int range = 2; // Default value
		String rangeUnits = "Weeks"; // Default value
		
		String startTimeParam = req.getParameter("startTime");
		String endTimeParam = req.getParameter("endTime");
		String rangeParam = req.getParameter("range");
		String rangeUnitsParam = req.getParameter("rangeUnits");
		String timeRangeType = req.getParameter("timeRangeType");
		
		// Only process parameters relevant to the selected time range type
		if ("absolute".equals(timeRangeType)) {
			// For absolute time range, only use start and end time parameters
			if (startTimeParam != null && !startTimeParam.isEmpty()) {
				startTime = parseTimeStringToLong(startTimeParam);
			}
			
			if (endTimeParam != null && !endTimeParam.isEmpty()) {
				endTime = parseTimeStringToLong(endTimeParam);
			}
			
			// Reset range parameters to default since they're not used for absolute time range
			range = 2;
			rangeUnits = "Weeks";
		} else {
			// For relative time range, only use range parameters
			if (rangeParam != null && !rangeParam.isEmpty()) {
				range = Integer.parseInt(rangeParam);
			}
			
			if (rangeUnitsParam != null && !rangeUnitsParam.isEmpty()) {
				rangeUnits = rangeUnitsParam;
			}
			
			// Reset start and end time to null since they're not used for relative time range
			startTime = null;
			endTime = null;
		}
		
		// Validate that both start and end time are provided when absolute time range is selected
		if ("absolute".equals(timeRangeType)) {
			// If either start or end time is missing, reset both to null
			// This will trigger the use of relative time range in createBuildHistorySearchCriteria
			if (startTime == null || endTime == null) {
				startTime = null;
				endTime = null;
			}
		}
		
		return new BuildMetricsSearch(
				req.getParameter("label"),
				range, 
				rangeUnits,
				startTime,
				endTime,
				req.getParameter("jobFilter"), 
				req.getParameter("nodeFilter"), 
				req.getParameter("launcherFilter"),
				req.getParameter("causeFilter")
				);
	}
	
	/**
	 * Parse time string to long timestamp
	 * @param timeString time string in format like "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd"
	 * @return long timestamp or null if parsing failed
	 */
	private Long parseTimeStringToLong(String timeString) {
		try {
			// SimpleDateFormat is not thread-safe, so we create a new instance each time
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			
			// If timeString doesn't contain time part, add it
			if (timeString.length() <= 10) {
				timeString += " 00:00:00";
			}
			
			return sdf.parse(timeString).getTime();
		} catch (Exception e) {
			// If parsing with seconds fails, try without seconds
			try {
				java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
				return sdf.parse(timeString).getTime();
			} catch (Exception e2) {
				// If all parsing fails, return null
				return null;
			}
		}
	}
	
	public BuildHistorySearchCriteria createBuildHistorySearchCriteria(BuildMetricsSearch bms, BuildSearchCriteria criteria){
		Long start = bms.getStartTime();
		Long end = bms.getEndTime();
		
		// If either start or end time is provided, both must be provided
		if ((start != null && end == null) || (start == null && end != null)) {
			// Fallback to default values if validation fails
			start = getStartDate(bms.getRange(), bms.getRangeUnits());
			end = getDefaultEndDate();
		} else if (start == null && end == null) {
			// Use relative time range if no absolute time provided
			start = getStartDate(bms.getRange(), bms.getRangeUnits());
			end = getDefaultEndDate();
		}
		
		return new BuildHistorySearchCriteria(start, end, criteria);
	}
	
	public BuildSearchCriteria createBuildSearchCriteria(BuildMetricsSearch bms){
		BuildSearchCriteria criteria = new BuildSearchCriteria(bms.getJobFilter(), 
				bms.getNodeFilter(), 
				bms.getLauncherFilter(),
				Boolean.TRUE, //successShown
				Boolean.TRUE, //failuresShown
				Boolean.TRUE, //unstablesShown
				Boolean.TRUE, //abortedShown
				Boolean.TRUE //notBuildsShown
				);
		return criteria;
	}
	
	public BuildSearchCriteria createFailedBuildSearchCriteria(BuildMetricsSearch bms){
		BuildSearchCriteria criteria = new BuildSearchCriteria(bms.getJobFilter(), 
				bms.getNodeFilter(), 
				bms.getLauncherFilter(),
				Boolean.FALSE, //successShown
				Boolean.TRUE, //failuresShown
				Boolean.TRUE, //unstablesShown
				Boolean.TRUE, //abortedShown
				Boolean.TRUE //notBuildsShown
				);
		return criteria;
	}
}
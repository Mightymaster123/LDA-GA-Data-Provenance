import java.util.concurrent.TimeUnit;

public class ResultStatistics {
	public population_config cfg;
	public double precision_percentage;
	public double recall_percentage;
	public long execution_milliseconds;

	public String to_string(String head)
	{
		long hours = execution_milliseconds / 1000 / 60 / 60;
		long minutes = (execution_milliseconds / 1000 / 60) % 60;
		long seconds = (execution_milliseconds / 1000) % 60;
		long milliseconds = execution_milliseconds % 1000;
		String time =  	String.format("Execution_time:%12d milliseconds (",execution_milliseconds);
		if(hours>0)
		{
			time += hours+"h ";
		}
		if(minutes>0)
		{
			time += minutes+"m ";
		}
		if(seconds>0)
		{
			time += seconds+"s ";
		}
		if(milliseconds>0)
		{
			time += milliseconds+"ms";
		}
		time += ") ";
		while(time.length()<60)
		{
			time +=" ";
		}
		String precision = String.format("Precision:%.3f ", precision_percentage);
		String recall = String.format("Recall:%.3f ", recall_percentage);
		
		String str = head +  "  " + time + "    " + precision + "    " + recall + "    " + (cfg!=null?cfg.to_string():"");
		return str;
	}
}
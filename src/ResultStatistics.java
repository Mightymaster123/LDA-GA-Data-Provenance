import java.util.concurrent.TimeUnit;

public class ResultStatistics {
	public PopulationConfig cfg;
	public double precision_percentage;
	public double recall_percentage;
	public long genetic_milliseconds;
	public long execution_milliseconds;
	public long LDA_call_count;

	public String to_string(String head)
	{
		String time =  	"Execution_time:" + time_to_str(execution_milliseconds, 40);
		String precision = String.format("Precision:%.3f ", precision_percentage);
		String recall = String.format("Recall:%.3f ", recall_percentage);
		String lda = String.format("LDA-call-count:%d ", LDA_call_count);
		if(LDA_call_count>0)
		{
			lda += "  average-time-per-LDA:" + time_to_str(genetic_milliseconds/LDA_call_count, 0);
		}
		
		String str = head +  "  " + time + "  "+ precision + "  " + recall + "  " + (cfg!=null?cfg.to_string():"") + "  " + lda ;
		return str;
	}
	
	String time_to_str(long milliseconds, long string_min_length)
	{
		long hours = milliseconds / 1000 / 60 / 60;
		long minutes = (milliseconds / 1000 / 60) % 60;
		long seconds = (milliseconds / 1000) % 60;
		long ms = milliseconds % 1000;
		String str =  	String.format("%dms (",milliseconds);
		if(hours>0)
		{
			str += hours+"h ";
		}
		if(minutes>0)
		{
			str += minutes+"m ";
		}
		if(seconds>0)
		{
			str += seconds+"s ";
		}
		if(ms>0)
		{
			str += ms+"ms";
		}
		str += ") ";
		while(str.length()<string_min_length)
		{
			str +=" ";
		}
		return str;
	}
}
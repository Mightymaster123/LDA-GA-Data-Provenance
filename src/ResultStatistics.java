import java.util.concurrent.TimeUnit;

public class ResultStatistics {
	public PopulationConfig cfg;
	public double precision_percentage;
	public double recall_percentage;
	public long execution_milliseconds;

	private long LDA_count;
	private long LDA_time;

	public String to_string(String head) {
		String time = "Execution_time:" + time_to_str(execution_milliseconds, 30);
		String precision = String.format("Precision:%.3f ", precision_percentage);
		String recall = String.format("Recall:%.3f ", recall_percentage);
		String lda = String.format("LDA-call-count:%d ", LDA_count);
		if (LDA_count > 0) {
			lda += "  LDA-average-time:" + time_to_str(LDA_time / LDA_count, 0);
		}

		String str = head + "  " + time + "  " + precision + "  " + recall + "  " + (cfg != null ? cfg.to_string() : "") + "  " + lda;
		return str;
	}

	String time_to_str(long milliseconds, long string_min_length) {
		long hours = milliseconds / 1000 / 60 / 60;
		long minutes = (milliseconds / 1000 / 60) % 60;
		long seconds = (milliseconds / 1000) % 60;
		long ms = milliseconds % 1000;
		String str = String.format("%dms (", milliseconds);
		if (hours > 0) {
			str += hours + "h ";
		}
		if (minutes > 0) {
			str += minutes + "m ";
		}
		if (seconds > 0) {
			str += seconds + "s ";
		}
		if (ms > 0) {
			str += ms + "ms";
		}
		str += ") ";
		while (str.length() < string_min_length) {
			str += " ";
		}
		return str;
	}

	public void OnLDAFinish(PopulationConfig cfg) {
		if(cfg!=null && cfg.LDA_execution_milliseconds>=0)
		{
			LDA_time += cfg.LDA_execution_milliseconds;
			++LDA_count;
		}
		if(cfg!=null && cfg.fitness_value==0.0f)
		{
			System.out.println(to_string("fitness_value should not be 0 : "));
		}
	}
}

public class population_config implements java.io.Serializable{
	public int number_of_topics;
	public int number_of_iterations;
	public double fitness_value;

	public void copy(population_config rhs) {
		number_of_topics = rhs.number_of_topics;
		number_of_iterations = rhs.number_of_iterations;
		fitness_value = rhs.fitness_value;
	}
	
	public void random()
	{
		random_topic();
		random_iteration();
	}
	
	public void random_topic()
	{
		number_of_topics = (int) Math.floor(Math.random() * 12 + 3);
		fitness_value = 0;
	}
	
	public void random_iteration()
	{
		number_of_iterations = (int) Math.floor(Math.random() * 1000 + 1);
		fitness_value = 0;
	}
	
	public String to_string()
	{
		String str = "number_of_topics: " + number_of_topics + "  number_of_iterations:" + number_of_iterations + "  fitness_value:" + fitness_value;
		return str;
	}
}
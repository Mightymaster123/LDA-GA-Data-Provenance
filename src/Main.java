import java.io.IOException;
import java.util.ArrayList;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

public class Main {

	private static void testLDAPerformance() {
		final int TEST_COUNT = 3;
		
		//preprocess data
		NetworkManager.getInstance().waitForAllSlaves();
		DataProvenance dataProvenance = new DataProvenance();
		dataProvenance.run(false);
		NetworkManager.getInstance().sendProtocol_StopAllSlaves();

		PopulationConfig[][] results = new PopulationConfig[10][10];
		for (int i_topic = 0; i_topic < results.length; ++i_topic) {
			final int number_of_topics = (i_topic + 1) * 2;
			for (int i_iteration = 0; i_iteration < results[0].length; ++i_iteration) {
				final int number_of_iterations = (i_iteration + 1) * 100;
				PopulationConfig result = new PopulationConfig();
				results[i_topic][i_iteration] = result;
				result.number_of_topics = number_of_topics;
				result.number_of_iterations = number_of_iterations;

				long original_time = 0;
				long new_time = 0;
				for (int i = 0; i < TEST_COUNT; ++i) {
					PopulationConfig cfg = new PopulationConfig();
					cfg.copy(result);
					try {
						TopicModelling tm = new TopicModelling();
						tm.LDA(cfg, true, true);
						System.out.println("original  " + cfg.to_string_all());
						original_time += cfg.LDA_execution_milliseconds;

						tm = new TopicModelling();
						tm.LDA(cfg, true, false);
						System.out.println("new       " + cfg.to_string_all());
						new_time += cfg.LDA_execution_milliseconds;

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				original_time /= TEST_COUNT;
				new_time /= TEST_COUNT;
				// Record speedup ratio in variable fitness_value
				if (new_time <= 0) {
					result.fitness_value = -1.0;
				} else {
					result.fitness_value = (double) original_time / (double) new_time;
				}
			}
		}
		System.out.println("\n\n\nSpeed-up ratio (Original-verstion's execution time / New-version's execution time):");
		String str = "topics";
		for (int i_iteration = 0; i_iteration < results[0].length; ++i_iteration) {
			str += String.format("    iteration_%-4d", results[0][i_iteration].number_of_iterations);
		}
		System.out.println(str);
		for (int i_topic = 0; i_topic < results.length; ++i_topic) {
			str = String.format("  %2d", results[i_topic][0].number_of_topics);
			for (int i_iteration = 0; i_iteration < results[0].length; ++i_iteration) {
				str += i_iteration == 0 ? "    " : "        ";
				str += String.format("%10.2f", results[i_topic][i_iteration].fitness_value);
			}
			System.out.println(str);
		}
	}

	public static void main(String[] argv) throws IOException, InterruptedException, ClassNotFoundException {

//		testLDAPerformance();

		// Build connection between multiple machines: 1 master, multiple slaves
		if(!NetworkManager.getInstance().init())
		{
			return;
		}

		if (NetworkManager.getInstance().isMaster()) {
			Master master = new Master();
			master.run();
		} else {
			Slave slave = new Slave();
			slave.run();
		}

		NetworkManager.getInstance().close();

	}
}

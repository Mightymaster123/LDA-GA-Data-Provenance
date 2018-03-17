import java.io.File;
import java.io.IOException;

public class geneticLogicOriginal {
	public boolean isRunning = true;
	private PopulationConfig mPopulationConfigFromSlave = null; // means one of the slave machines has finished the job

	public ResultStatistics run() throws IOException, InterruptedException, ClassNotFoundException {
		ResultStatistics result = new ResultStatistics();

		if (NetworkManager.getInstance().isMaster()) {
			System.out.println("Start geneticLogicOriginal: Waiting for slaves ");
			NetworkManager.getInstance().waitForAllSlaves();
			NetworkManager.getInstance().sendProtocol_StartOriginal();
		}
		System.out.println("Start geneticLogicOriginal: begin to work ");

		// the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number
		// of cores on each machine

		final int POPULATION_COUNT = NetworkManager.getInstance().getMachineCount() * geneticLogic.THREADS_PER_MACHINE;
		PopulationConfig[] initialPopulation = PopulationConfig.initArray(POPULATION_COUNT);

		boolean maxFitnessFound = false;

		// populating the initial population
		for (int i = 0; i < initialPopulation.length; i++) {
			initialPopulation[i].random();
		}
		final int BEST_POPULATION_SIZE = initialPopulation.length / 3;

		while (!maxFitnessFound && isRunning) {
			NetworkManager.getInstance().dispatchProtocols();
			/**
			 * the total number of documents that are being processed. Put them in a folder
			 * and add the folder path here.
			 */
			int numberOfDocuments = new File("txtData").listFiles().length;
			long startTime = System.currentTimeMillis();

			// int coresNum = 4;
			Thread threads[] = new Thread[geneticLogic.THREADS_PER_MACHINE];
			for (int i = 0; i < threads.length; i++) {
				int population_index = geneticLogic.THREADS_PER_MACHINE * (NetworkManager.getInstance().getMyMachineID() + 1) + i;
				threads[i] = new Thread(new MyThread(i, initialPopulation[population_index], population_index, numberOfDocuments, true));
				System.out.println("Original thread " + i + " begin start...");
				threads[i].start();
				System.out.println("Original thread " + i + " end start...");
			}

			for (int i = 0; i < geneticLogic.THREADS_PER_MACHINE; i++) {
				threads[i].join();
				System.out.println("Original thread " + i + " joined");
			}
			for(int i=0; i<initialPopulation.length; ++i)
			{
				result.OnLDAFinish(initialPopulation[i]);
			}
			NetworkManager.getInstance().dispatchProtocols();

			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");

			// ranking and ordering the chromosomes based on the fitness function.
			// no sorting code found?(by Xiaolin)
			// We need only the top 1/3rd of the chromosomes with high fitness values -
			// Silhouette coefficient
			PopulationConfig[] newPopulation = PopulationConfig.initArray(POPULATION_COUNT);
			// copy only the top 1/3rd of the chromosomes to the new population
			for (int iPopulation = 0; iPopulation < BEST_POPULATION_SIZE && isRunning; iPopulation++) {
				double maxFitness = Integer.MIN_VALUE;
				int maxFitnessChromosome = -1;
				for (int j = 0; j < initialPopulation.length && isRunning; j++) {
					if (initialPopulation[j].fitness_value > maxFitness) {
						maxFitness = initialPopulation[j].fitness_value;

						// stop reproducing or creating new generations if the expected fitness is
						// reached by one of the machines
						/**
						 * Please find what would be a suitable fitness to classify the set of documents
						 * that you choose
						 */
						// if other machines has finished
						if (mPopulationConfigFromSlave != null) {
							// if this machine is master, stop all slaves
							NetworkManager.getInstance().sendProtocol_StopAllSlaves();

							// create an instance of the topic modelling class
							TopicModelling tm = new TopicModelling();
							tm.LDA(mPopulationConfigFromSlave, true, true);
							System.out.println("the best distribution is " + mPopulationConfigFromSlave.to_string());
							result.cfg = mPopulationConfigFromSlave;
							result.OnLDAFinish(result.cfg);
							maxFitnessFound = true;
							break;
						}
						// set fitness threshold here!!!
						if (maxFitness > geneticLogic.FITNESS_THRESHHOLD) {
							// when maxFitness satisfies the requirement, stop running GA
							// if this machine is slave, tell the master what the best combination is
							if (NetworkManager.getInstance().isSlave()) {
								NetworkManager.getInstance().sendProtocol_FinishOriginal(initialPopulation[j]);
								System.out.println("message sent!  " + initialPopulation[j].to_string());
							} else {
								// if this machine is master, stop all slaves
								NetworkManager.getInstance().sendProtocol_StopAllSlaves();

								// run the function again to get the words in each topic
								// the third parameter states that the topics are to be written to a file
								// create an instance of the topic modelling class
								TopicModelling tm = new TopicModelling();
								tm.LDA(initialPopulation[j], true, true);
								System.out.println("the best distribution is: " + initialPopulation[j].to_string());
								result.cfg = initialPopulation[j];
								result.OnLDAFinish(result.cfg);
							}
							maxFitnessFound = true;
							break;
						}
						maxFitnessChromosome = j;
					}
				}
				if (maxFitnessFound) {
					break;
				}

				// copy the chromosome with high fitness to the next generation
				newPopulation[iPopulation].copy(initialPopulation[maxFitnessChromosome]);
				initialPopulation[maxFitnessChromosome].fitness_value = Integer.MIN_VALUE;
			}

			if (maxFitnessFound || !isRunning) {
				break;
			}

			// perform crossover - to fill the rest of the 2/3rd of the initial Population
			// for(int i = 0 ; i < BEST_POPULATION_SIZE ; i++ ) {
			// newPopulation[(i+1)*2][0] = newPopulation[i][0];
			// newPopulation[(i+1)*2][1] = (int) Math.floor(Math.random()*1000 + 1);
			// newPopulation[(i+1)*2+1][0] = (int) Math.floor(Math.random()*12 + 2);
			// newPopulation[(i+1)*2+1][1] = newPopulation[i][1];
			// }

			// perform crossover - to fill the rest of the 2/3rd of the initial Population
			if (BEST_POPULATION_SIZE <= 0) {
				for (int i = 0; i < newPopulation.length; ++i) {
					newPopulation[i].random();
				}
			} else {
				final double MUTATION_RATIO = 0.5;
				for (int i = BEST_POPULATION_SIZE; i < newPopulation.length; ++i) {
					int iParent = i % BEST_POPULATION_SIZE;
					newPopulation[i].copy(newPopulation[iParent]);
					newPopulation[i].fitness_value = 0;
					if (Math.random() < MUTATION_RATIO) {
						newPopulation[i].random_topic();
					}
					if (Math.random() < MUTATION_RATIO) {
						newPopulation[i].random_iteration();
					}
				}
			}

			// substitute the initial population with the new population and continue
			initialPopulation = newPopulation;

			long endTime = System.currentTimeMillis();
			System.out.println("other part takes " + (endTime - paraEndTime) + "ms");

			/**
			 * The genetic algorithm loop will not exit until the required fitness is
			 * reached. For some cases, we might expect a very high fitness that will never
			 * be reached. In such cases add a variable to check how many times the GA loop
			 * is repeated. Terminate the loop in predetermined number of iterations.
			 */
		}
		return result;
	}

	public void SlaveFinish(NetworkManager.ReceivedProtocol protocol) {
		System.out.println("Receive result from slave " + protocol.targetMachineID + "  " + protocol.to_string());
		mPopulationConfigFromSlave = (protocol.obj instanceof PopulationConfig) ? (PopulationConfig) protocol.obj : null;
	}
}

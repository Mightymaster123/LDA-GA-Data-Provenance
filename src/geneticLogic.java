import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class geneticLogic {

	public static final int THREADS_PER_MACHINE = 3;
	public static final double FITNESS_THRESHHOLD = 0.292;

	public boolean isRunning = true;
	private PopulationConfig[] mInitialPopulation = null;
	private int mFinishedSlaveCount = 0;

	public ResultStatistics run() throws IOException, InterruptedException, ClassNotFoundException {
		if (NetworkManager.getInstance().isMaster()) {
			// master
			return run_master();
		} else {
			// Slaves only have to prepare in the first stage.
		}
		return null;
	}

	public ResultStatistics run_master() throws IOException, InterruptedException, ClassNotFoundException {
		ResultStatistics result = new ResultStatistics();
		// the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number
		// of cores on each machine

		final int POPULATION_COUNT = NetworkManager.getInstance().getMachineCount() * THREADS_PER_MACHINE;
		mInitialPopulation = PopulationConfig.initArray(POPULATION_COUNT);

		boolean maxFitnessFound = false;

		// populating the initial population
		for (int i = 0; i < mInitialPopulation.length; i++) {
			mInitialPopulation[i].random();
		}
		NetworkManager.getInstance().waitForAllSlaves();
		NetworkManager.getInstance().sendProtocol_PrepareNew();

		while (!maxFitnessFound && isRunning) {
			NetworkManager.getInstance().dispatchProtocols();
			long startTime = System.currentTimeMillis();
			boolean error = false;
			// send populations to slaves
			NetworkManager.getInstance().waitForAllSlaves();
			mFinishedSlaveCount = 0;
			for (int iSlave = 0; iSlave < NetworkManager.getInstance().getSlaveCount(); ++iSlave) {
				PopulationConfig[] subPopulation = new PopulationConfig[THREADS_PER_MACHINE];
				for (int j = 0; j < THREADS_PER_MACHINE; ++j) {
					subPopulation[j] = mInitialPopulation[THREADS_PER_MACHINE * (iSlave + 1) + j];
				}
				if (!NetworkManager.getInstance().sendProtocol_ProcessSubPopulationNew(iSlave, subPopulation)) {
					error = true;
					break;
				}
			}
			if (error) {
				break;
			}
			/**
			 * the total number of documents that are being processed. Put them in a folder
			 * and add the folder path here.
			 */
			int numberOfDocuments = new File("txtData").listFiles().length;
			// create an instance of the topic modelling class
			TopicModelling tm = new TopicModelling();

			Thread threads[] = new Thread[THREADS_PER_MACHINE];
			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				int population_index = THREADS_PER_MACHINE * (NetworkManager.getInstance().getMyMachineID() + 1) + i;
				threads[i] = new Thread(new MyThread(i, mInitialPopulation[population_index], population_index, tm, numberOfDocuments, false));
				// System.out.println("Thread " + i + " begin start...");
				threads[i].start();
				// System.out.println("Thread " + i + " end start...");
			}

			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				threads[i].join();
				// System.out.println("Thread " + i + " joined");
			}
			NetworkManager.getInstance().dispatchProtocols();

			// receive populations from slaves
			while (mFinishedSlaveCount < NetworkManager.getInstance().getSlaveCount() && isRunning) {
				NetworkManager.getInstance().dispatchProtocols();
				Thread.sleep(1);
			}

			if (!isRunning) {
				break;
			}

			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");

			// ranking and ordering the chromosomes based on the fitness function.
			// no sorting code found?(by Xiaolin)
			// We need only the top 1/3rd of the chromosomes with high fitness values -
			// Silhouette coefficient
			PopulationConfig[] newPopulation = PopulationConfig.initArray(POPULATION_COUNT);
			// copy only the top 1/3rd of the population to the new population
			final int BEST_POPULATION_SIZE = mInitialPopulation.length / 3;
			for (int i = 0; i < BEST_POPULATION_SIZE; i++) {
				double maxFitness = Integer.MIN_VALUE;
				int maxFitnessChromosome = -1;
				for (int j = 0; j < mInitialPopulation.length; j++) {
					if (mInitialPopulation[j].fitness_value > maxFitness) {
						maxFitness = mInitialPopulation[j].fitness_value;

						// stop reproducing or creating new generations if the expected fitness is
						// reached by one of the machines
						/**
						 * Please find what would be a suitable fitness to classify the set of documents
						 * that you choose
						 */
						// set fitness threshold here!!!
						if (maxFitness > FITNESS_THRESHHOLD) {
							// run the function again to get the words in each topic
							// the third parameter states that the topics are to be written to a file
							tm.LDA(mInitialPopulation[j].number_of_topics, mInitialPopulation[j].number_of_iterations, true, false);
							System.out.println("The best distribution is: " + mInitialPopulation[j].to_string());
							result.cfg = mInitialPopulation[j];
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
				newPopulation[i].copy(mInitialPopulation[maxFitnessChromosome]);
				mInitialPopulation[maxFitnessChromosome].fitness_value = Integer.MIN_VALUE;
			}

			if (maxFitnessFound) {
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
			mInitialPopulation = newPopulation;

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
		PopulationConfig[] cfgs = (PopulationConfig[]) protocol.obj;
		if (cfgs == null) {
			return;
		}
		if (cfgs.length != THREADS_PER_MACHINE) {
			System.out.println("Receive result from slave " + protocol.targetMachineID + ". Result count does not match: " + cfgs.length + " != " + THREADS_PER_MACHINE);
			return;
		}

		for (int j = 0; j < cfgs.length; ++j) {
			mInitialPopulation[THREADS_PER_MACHINE * (protocol.targetMachineID + 1) + j] = cfgs[j];
		}

		++mFinishedSlaveCount;
	}

	public void StartSubPopulation(NetworkManager.ReceivedProtocol protocol) {
		System.out.println("Receive sub population from master " + protocol.targetMachineID + "  " + protocol.to_string());
		PopulationConfig[] subPopulation = (PopulationConfig[]) protocol.obj;
		if (subPopulation == null) {
			return;
		}
		if (!isRunning) {
			return;
		}
		if(subPopulation.length != THREADS_PER_MACHINE)
		{
			System.out.println("StartSubPopulation sub-population count:"+subPopulation.length+" does not match threads count:"+THREADS_PER_MACHINE);
			return;
		}
		NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_WORKING);
		/**
		 * the total number of documents that are being processed. Put them in a folder
		 * and add the folder path here.
		 */
		int numberOfDocuments = new File("txtData").listFiles().length;
		// create an instance of the topic modelling class
		TopicModelling tm = new TopicModelling();

		long startTime = System.currentTimeMillis();

		Thread threads[] = new Thread[subPopulation.length];
		for (int i = 0; i < subPopulation.length; i++) {
			int population_index = THREADS_PER_MACHINE * (NetworkManager.getInstance().getMyMachineID() + 1) + i;
			threads[i] = new Thread(new MyThread(i, subPopulation[i], population_index, tm, numberOfDocuments, false));
			// System.out.println("Thread " + i + " begin start...");
			threads[i].start();
			// System.out.println("Thread " + i + " end start...");
		}

		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// System.out.println("Thread " + i + " joined");
		}
		long paraEndTime = System.currentTimeMillis();
		System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");
		if (!NetworkManager.getInstance().sendProtocol_FinishNew(subPopulation)) {
			System.out.println("Slave " + NetworkManager.getInstance().getMyMachineID() + " failed to send population: " + NetworkManager.to_string(subPopulation));
		}
		NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_IDLE);
	}

}

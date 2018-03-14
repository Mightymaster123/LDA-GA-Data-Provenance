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

	private static int nMachines;
	private static int machineId;
	private static MySocket my_socket[] = null;

	public static long genetic_logic(MultiMachineSocket mms) throws IOException, InterruptedException, ClassNotFoundException {
		Socket sockets[] = mms.connect();
		long start_time = System.currentTimeMillis();
		nMachines = mms.getNumSlaves() + 1;
		machineId = mms.getId();

		if (machineId == -1) {
			// master
			my_socket = new MySocket[nMachines - 1];
			for (int i = 0; i < nMachines - 1; i++) {
				my_socket[i] = new MySocket(sockets[i], i);
			}
			genetic_logic_master(mms);
		} else {
			// slave
			my_socket = new MySocket[1];
			my_socket[0] = new MySocket(sockets[0], -1);
			genetic_logic_slave(mms);
		}
		for (int i = 0; i < my_socket.length; ++i) {
			my_socket[i].close();
		}
		return start_time;
	}

	public static void genetic_logic_master(MultiMachineSocket mms) throws IOException, InterruptedException, ClassNotFoundException {
		// the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number
		// of cores on each machine

		final int POPULATION_COUNT = nMachines * THREADS_PER_MACHINE;
		population_config[] initialPopulation = init_population_array(POPULATION_COUNT);

		boolean maxFitnessFound = false;

		// populating the initial population
		for (int i = 0; i < initialPopulation.length; i++) {
			initialPopulation[i].random();
		}

		while (!maxFitnessFound) {
			long startTime = System.currentTimeMillis();
			boolean error = false;
			// send chromesomes to slaves
			for (int i_socket = 0; i_socket < my_socket.length; ++i_socket) {
				population_config[] slavePopulation = new population_config[THREADS_PER_MACHINE];
				for (int j = 0; j < THREADS_PER_MACHINE; ++j) {
					slavePopulation[j] = initialPopulation[THREADS_PER_MACHINE * (my_socket[i_socket].target_machine_id + 1) + j];
				}
				if (!my_socket[i_socket].send(slavePopulation)) {
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

			// int coresNum = 4;
			Thread threads[] = new Thread[THREADS_PER_MACHINE];
			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				int population_index = THREADS_PER_MACHINE * (machineId + 1) + i;
				threads[i] = new Thread(new MyThread(i, initialPopulation[population_index], population_index, tm, numberOfDocuments));
				// System.out.println("Thread " + i + " begin start...");
				threads[i].start();
				// System.out.println("Thread " + i + " end start...");
			}

			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				threads[i].join();
				// System.out.println("Thread " + i + " joined");
			}

			// receive chromesomes from slaves
			for (int i_socket = 0; i_socket < my_socket.length; ++i_socket) {
				population_config[] slavePopulation = my_socket[i_socket].receive();
				if (slavePopulation == null || slavePopulation.length != THREADS_PER_MACHINE) {
					error = true;
					break;
				}
				for (int j = 0; j < THREADS_PER_MACHINE; ++j) {
					initialPopulation[THREADS_PER_MACHINE * (my_socket[i_socket].target_machine_id + 1) + j] = slavePopulation[j];
				}
			}
			if (error) {
				break;
			}

			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");

			// ranking and ordering the chromosomes based on the fitness function.
			// no sorting code found?(by Xiaolin)
			// We need only the top 1/3rd of the chromosomes with high fitness values -
			// Silhouette coefficient
			population_config[] newPopulation = init_population_array(POPULATION_COUNT);
			// copy only the top 1/3rd of the chromosomes to the new population
			final int BEST_POPULATION_SIZE = initialPopulation.length / 3;
			for (int i = 0; i < BEST_POPULATION_SIZE; i++) {
				double maxFitness = Integer.MIN_VALUE;
				int maxFitnessChromosome = -1;
				for (int j = 0; j < initialPopulation.length; j++) {
					if (initialPopulation[j].fitness_value > maxFitness) {
						maxFitness = initialPopulation[j].fitness_value;

						// stop reproducing or creating new generations if the expected fitness is
						// reached by one of the machines
						/**
						 * Please find what would be a suitable fitness to classify the set of documents
						 * that you choose
						 */
						// set fitness threshold here!!!
						if (maxFitness > FITNESS_THRESHHOLD) {
							for (int i_socket = 0; i_socket < my_socket.length; ++i_socket) {
								my_socket[i_socket].close();
							}

							// run the function again to get the words in each topic
							// the third parameter states that the topics are to be written to a file
							tm.LDA(initialPopulation[j].number_of_topics, initialPopulation[j].number_of_iterations, true);
							System.out.println("The best distribution is: " + initialPopulation[j].to_string());
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
				newPopulation[i].copy(initialPopulation[maxFitnessChromosome]);
				initialPopulation[maxFitnessChromosome].fitness_value = Integer.MIN_VALUE;
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
			if(BEST_POPULATION_SIZE <= 0)
			{
				for (int i = 0; i < initialPopulation.length; ++i) {
					newPopulation[i].random();
				}
			}else
			{
				final double MUTATION_RATIO = 0.5;
				for (int i = BEST_POPULATION_SIZE; i < initialPopulation.length; ++i) {
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

	}

	public static void genetic_logic_slave(MultiMachineSocket mms) throws IOException, InterruptedException, ClassNotFoundException {

		// the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number
		// of cores on each machine

		final int POPULATION_COUNT = nMachines * THREADS_PER_MACHINE;
		while (true) {
			population_config[] slavePopulation = my_socket[0].receive();
			if (slavePopulation == null || slavePopulation.length != THREADS_PER_MACHINE) {
				if (slavePopulation != null) {
					System.out.println("Slave " + machineId + " failed to receive population: " + slavePopulation.length);
				}
				break;
			}
			/**
			 * the total number of documents that are being processed. Put them in a folder
			 * and add the folder path here.
			 */
			int numberOfDocuments = new File("txtData").listFiles().length;
			// create an instance of the topic modelling class
			TopicModelling tm = new TopicModelling();

			long startTime = System.currentTimeMillis();

			// int coresNum = 4;
			Thread threads[] = new Thread[THREADS_PER_MACHINE];
			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				int population_index = THREADS_PER_MACHINE * (machineId + 1) + i;
				threads[i] = new Thread(new MyThread(i, slavePopulation[i], population_index, tm, numberOfDocuments));
				// System.out.println("Thread " + i + " begin start...");
				threads[i].start();
				// System.out.println("Thread " + i + " end start...");
			}

			for (int i = 0; i < THREADS_PER_MACHINE; i++) {
				threads[i].join();
				// System.out.println("Thread " + i + " joined");
			}
			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");
			if (!my_socket[0].send(slavePopulation)) {
				if (slavePopulation != null) {
					System.out.println("Slave " + machineId + " failed to send population: " + slavePopulation.length);
				}
				break;
			}
		}

	}

	public static population_config[] init_population_array(int count) {
		population_config[] array = new population_config[count];
		for (int i = 0; i < count; ++i) {
			array[i] = new population_config();
		}
		return array;
	}

}

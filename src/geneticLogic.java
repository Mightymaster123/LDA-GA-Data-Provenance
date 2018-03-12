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

	private static int nMachines;
	private static int machineId;
	private static boolean finishedByOthers = false; // means one of the machines has finished the job, only make sense
														// when on master machine
	private static int[] msgFromOther = new int[2];

	// this method must be thread safe
	synchronized private static void setMsgFromOthers(int topics, int iterations) {
		msgFromOther[0] = topics;
		msgFromOther[1] = iterations;
	}

	// public static void main(String[] args) throws IOException,
	// InterruptedException {
	public static void genetic_logic(MultiMachineSocket mms) throws IOException, InterruptedException, ClassNotFoundException {

		Socket sockets[] = mms.connect();
		nMachines = mms.getNumSlaves() + 1;
		machineId = mms.getId();
		Listener listeners[] = null;
		// if this machine is master, create threads to listen to the slaves
		if (machineId == -1) {
			listeners = new Listener[nMachines - 1];
			for (int i = 0; i < nMachines - 1; i++) {
				listeners[i] = new Listener(sockets[i], i);
				listeners[i].start();
			}
		}

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
				threads[i] = new Thread(new MyThread(i, initialPopulation[population_index], population_index, tm, numberOfDocuments));
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
						// if other machines has finished
						if (finishedByOthers) {
							tm.LDA(msgFromOther[0], msgFromOther[1], true);
							System.out.println("the best distribution is " + msgFromOther[0] + " topics and " + msgFromOther[1] + "iterations and fitness is " + maxFitness);
							maxFitnessFound = true;
							break;
						}
						// set fitness threshold here!!!
						if (maxFitness > 0.5) {
							// when maxFitness satisfies the requirement, stop running GA
							// if this machine is slave, tell the master what the best combination is
							if (machineId != -1) {
								ObjectOutputStream output = null;
								output = new ObjectOutputStream(sockets[0].getOutputStream());
								output.writeObject(initialPopulation[j]);
								System.out.println("message sent! ");
								initialPopulation[j].println();
							}
							// if this machine is master, stop all listener threads and then stop GA
							// else{
							// for(int i = 0; i < numMachines - 1; i++){
							// listeners[i].end();
							// break;
							// }

							// run the function again to get the words in each topic
							// the third parameter states that the topics are to be written to a file
							tm.LDA(initialPopulation[j].number_of_topics, initialPopulation[j].number_of_iterations, true);
							System.out.print("the best distribution is: ");
							initialPopulation[j].println();
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

			// perform crossover and mutation
			// final double CROSS_OVER_FROM_BEST_POPULATION_RATIO = 0.7;
			// final double MUTATION_RATIO = 0.2;
			// for(int i = BEST_POPULATION_SIZE ; i < initialPopulation.length; ++i ) {
			// //cross over
			// int[] parent_a = new int[2];
			// if(Math.random()<CROSS_OVER_FROM_BEST_POPULATION_RATIO)
			// {
			// parent_a = newPopulation[(int)(Math.random() * BEST_POPULATION_SIZE)];
			// //cross over from best 1/3
			// }else
			// {
			// parent_a = initialPopulation[(int)(Math.random() *
			// initialPopulation.length)]; //cross over from any part
			// }
			//
			// int[] parent_b = new int[2];
			// if(Math.random()<CROSS_OVER_FROM_BEST_POPULATION_RATIO)
			// {
			// parent_b = newPopulation[(int)(Math.random() * BEST_POPULATION_SIZE)];
			// //cross over from best 1/3
			// }else
			// {
			// parent_b = initialPopulation[(int)(Math.random() *
			// initialPopulation.length)]; //cross over from any part
			// }
			// newPopulation[i][0] = (parent_a[0] + parent_b[0])/2;
			// newPopulation[i][1] = (parent_a[1] + parent_b[1])/2;
			//
			// //mutation
			// if(Math.random()<MUTATION_RATIO)
			// {
			// newPopulation[i][0] = (int) Math.floor(Math.random()*12 + 3);
			// }
			// if(Math.random()<MUTATION_RATIO)
			// {
			// newPopulation[i][1] = (int) Math.floor(Math.random()*1000 + 1);
			// }
			// }

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
	
	public static population_config[] init_population_array(int count)
	{
		population_config[] array = new population_config[count];
		for(int i=0; i<count; ++i)
		{
			array[i] = new population_config();
		}
		return array;
	}
	

	private static class Listener extends Thread {
		private Socket socket;
		private int listenerId;

		public Listener(Socket s, int i) {
			socket = s;
			listenerId = i;
		}

		@Override
		public void run() {
			ObjectInputStream input = null;
			try {
				input = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (input != null) {
				finishedByOthers = true;
				System.out.println("slave " + listenerId + " has finished the job");
				population_config cfg = null;
				try {
					cfg = (population_config) input.readObject();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.print("slave: ");
				cfg.println();
				setMsgFromOthers(cfg.number_of_topics, cfg.number_of_iterations);
			}
		}
	}

}

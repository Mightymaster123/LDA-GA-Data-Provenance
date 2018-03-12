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
	
	private static int numMachines;
	private static int machineId;
	private static boolean finishedByOthers = false; // means one of the machines has finished the job, only make sense when on master machine
	private static int[] msgFromOther = new int[2];
	// this method must be thread safe
	synchronized private static void setMsgFromOthers(int topics, int iterations){ 
		msgFromOther[0] = topics;
		msgFromOther[1] = iterations;
	}
	
	//public static void main(String[] args) throws IOException, InterruptedException {
	public static void geneticLogic(MultiMachineSocket mms) throws IOException, InterruptedException, ClassNotFoundException {
		
		Socket sockets[] = mms.connect();
		numMachines = mms.getNumSlaves() + 1;
		machineId = mms.getId();
		Listener listeners[] = null;		
		//if this machine is master, create threads to listen to the slaves
		if(machineId == -1){
			listeners = new Listener[numMachines - 1];
			for(int i = 0; i < numMachines - 1; i++){
				listeners[i] = new Listener(sockets[i], i);
				listeners[i].start();
			}
		}
		
		
		//the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number of cores on each machine
		
		
		int population = numMachines * THREADS_PER_MACHINE;
		int[][] initialPopulation = new int[population][2];
		double[] initialFitnessValues = new double[population];
		
		boolean maxFitnessFound = false;
		
		
		//populating the initial population
		for(int i = 0 ; i < initialPopulation.length ; i++ ) {
			
			//the first value is the number of topics. Assign a range which you think is reasonable
			//the second value is the number of iterations
			initialPopulation[i][0] = (int) Math.floor(Math.random()*12 + 3);
			initialPopulation[i][1] = (int) Math.floor(Math.random()*1000 + 1);
			
			//initialPopulation[i][0] = 2;
		    //initialPopulation[i][1] = 500;
		}
		
		while( !maxFitnessFound) {		
			/**
			 * the total number of documents that are being processed. Put them in a folder and add the folder path here.
			 */
			int numberOfDocuments = new File("txtData").listFiles().length;
			//create an instance of the topic modelling class
			TopicModelling tm = new TopicModelling();
			
			
			
			long startTime = System.currentTimeMillis();
		
			//int coresNum = 4;
			Thread threads[] = new Thread[THREADS_PER_MACHINE];
			for(int i = 0; i < THREADS_PER_MACHINE; i++){
				int population_index = THREADS_PER_MACHINE * (machineId+1) + i;
				threads[i] = new Thread( new MyThread(i, initialPopulation[population_index][0], initialPopulation[population_index][1], tm, numberOfDocuments, initialFitnessValues, population_index));			
				//System.out.println("Thread " + i + " begin start...");
				threads[i].start();
				//System.out.println("Thread " + i + " end start...");
			}
			
			for(int i = 0; i < THREADS_PER_MACHINE; i++){
				threads[i].join();
				//System.out.println("Thread " + i + " joined");
			}
			
			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");		
		
			//ranking and ordering the chromosomes based on the fitness function. 
			//no sorting code found?(by Xiaolin)
			//We need only the top 1/3rd of the chromosomes with high fitness values - Silhouette coefficient
			int[][] newPopulation = new int[initialPopulation.length][2];
			double[] newFitnessValues = new double[population];
			//copy only the top 1/3rd of the chromosomes to the new population 
			final int BEST_POPULATION_SIZE = initialPopulation.length / 3;
			for(int i = 0 ; i < BEST_POPULATION_SIZE ; i++) {
				double maxFitness = Integer.MIN_VALUE;
				int maxFitnessChromosome = -1;
				for(int j = 0 ; j < initialPopulation.length ; j++) {
					if(initialFitnessValues[j] > maxFitness) {
						maxFitness = initialFitnessValues[j];
						
						//stop reproducing or creating new generations if the expected fitness is reached by one of the machines
						/**
						 * Please find what would be a suitable fitness to classify the set of documents that you choose
						 */
						// if other machines has finished 
						if(finishedByOthers){					
							tm.LDA(msgFromOther[0],msgFromOther[1], true);
							System.out.println("the best distribution is " + msgFromOther[0] + " topics and " + msgFromOther[1] + "iterations and fitness is " + maxFitness);
							maxFitnessFound = true;
							break;
						}
						// set fitness threshold here!!!
						if(maxFitness > 0.5) {
						// when maxFitness satisfies the requirement, stop running GA

							// if this machine is slave, tell the master what the best combination is
							if(machineId != -1){
								ObjectOutputStream output = null;
								output = new ObjectOutputStream(sockets[0].getOutputStream());
								int[] msg = new int[2];
								msg[0] = initialPopulation[j][0];
								msg[1] = initialPopulation[j][1];
								output.writeObject(msg);
								System.out.println("message sent!");
							 	System.out.println("topic: " + msg[0] + " interation: " + msg[1]);
							}
							// if this machine is master, stop all listener threads and  then stop GA
//							else{
//							for(int i = 0; i < numMachines - 1; i++){
//								listeners[i].end();
//								break;
//							}
							
							//run the function again to get the words in each topic
							//the third parameter states that the topics are to be written to a file
							tm.LDA(initialPopulation[j][0],initialPopulation[j][1], true);
							System.out.println("the best distribution is " + initialPopulation[j][0] + " topics and " + initialPopulation[j][1] + "iterations and fitness is " + maxFitness);
							maxFitnessFound = true;
							break;						
						}
						maxFitnessChromosome = j;
					}
				}
				
				if(maxFitnessFound) {
					break;
				}
			
				//copy the chromosome with high fitness to the next generation
				newPopulation[i] = initialPopulation[maxFitnessChromosome];
				newFitnessValues[i] = initialFitnessValues[maxFitnessChromosome];
				initialFitnessValues[maxFitnessChromosome] = Integer.MIN_VALUE;
			}
			
			if(maxFitnessFound) {
				break;
			}
		
		
			//perform crossover - to fill the rest of the 2/3rd of the initial Population
//			for(int i = 0 ; i < BEST_POPULATION_SIZE  ; i++ ) {
//				newPopulation[(i+1)*2][0] = newPopulation[i][0];
//				newPopulation[(i+1)*2][1] = (int) Math.floor(Math.random()*1000 + 1);
//				newPopulation[(i+1)*2+1][0] = (int) Math.floor(Math.random()*12 + 2);
//				newPopulation[(i+1)*2+1][1] = newPopulation[i][1];
//			}
			
			//perform crossover - to fill the rest of the 2/3rd of the initial Population
			final double MUTATION_RATIO = 0.5;
			for(int i = BEST_POPULATION_SIZE ; i < initialPopulation.length; ++i ) 
			{
				int iParent = i % BEST_POPULATION_SIZE;
				newPopulation[i][0] = newPopulation[iParent][0];
				newPopulation[i][1] = newPopulation[iParent][1];
				if(Math.random()<MUTATION_RATIO)
				{
					newPopulation[i][0] = (int) Math.floor(Math.random()*12 + 3);
				}
				if(Math.random()<MUTATION_RATIO)
				{
					newPopulation[i][1] = (int) Math.floor(Math.random()*1000 + 1);
				}
			}
			
			//perform crossover and mutation
//			final double CROSS_OVER_FROM_BEST_POPULATION_RATIO = 0.7;
//			final double MUTATION_RATIO = 0.2;
//			for(int i = BEST_POPULATION_SIZE ; i < initialPopulation.length; ++i ) {
//				//cross over
//				int[] parent_a = new int[2];
//				if(Math.random()<CROSS_OVER_FROM_BEST_POPULATION_RATIO)
//				{
//					parent_a = newPopulation[(int)(Math.random() * BEST_POPULATION_SIZE)]; //cross over from best 1/3
//				}else
//				{
//					parent_a = initialPopulation[(int)(Math.random() * initialPopulation.length)]; //cross over from any part
//				}
//
//				int[] parent_b = new int[2];
//				if(Math.random()<CROSS_OVER_FROM_BEST_POPULATION_RATIO)
//				{
//					parent_b = newPopulation[(int)(Math.random() * BEST_POPULATION_SIZE)]; //cross over from best 1/3
//				}else
//				{
//					parent_b = initialPopulation[(int)(Math.random() * initialPopulation.length)]; //cross over from any part
//				}
//				newPopulation[i][0] = (parent_a[0] + parent_b[0])/2;
//				newPopulation[i][1] = (parent_a[1] + parent_b[1])/2;
//				
//				//mutation
//				if(Math.random()<MUTATION_RATIO)
//				{
//					newPopulation[i][0] = (int) Math.floor(Math.random()*12 + 3);
//				}
//				if(Math.random()<MUTATION_RATIO)
//				{
//					newPopulation[i][1] = (int) Math.floor(Math.random()*1000 + 1);
//				}
//			}
		
			//substitute the initial population with the new population and continue 
			initialPopulation = newPopulation;
			initialFitnessValues = newFitnessValues;			
			
			long endTime = System.currentTimeMillis();
			System.out.println("other part takes " + (endTime - paraEndTime) + "ms");
			
			/**The genetic algorithm loop will not exit until the required fitness is reached.
			 * For some cases, we might expect a very high fitness that will never be reached.
			 * In such cases add a variable to check how many times the GA loop is repeated.
			 * Terminate the loop in predetermined number of iterations.
			 */
		}		
		
	}
	private static class Listener extends Thread{
		private Socket socket;
		private int listenerId;
		public Listener(Socket s, int i){
			socket = s;
			listenerId = i;
		}
		
		@Override
        public void run( ){
			ObjectInputStream input = null;
			try {
				input = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(input != null){
				finishedByOthers = true;
				System.out.println("slave " + listenerId + " has finished the job");
				int[] msg = new int[2];
				try {
					msg = (int[])input.readObject();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("slave topic: " + msg[0] + " iterations: " + msg[1]);
				setMsgFromOthers(msg[0], msg[1]);
			}
		}
	}
	
	class population_unit
	{
		public int number_of_topics;
		public int number_of_iterations;
	}
	
	
}
	
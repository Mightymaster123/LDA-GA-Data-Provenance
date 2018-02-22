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
				listeners[i] = new Listener(sockets, i);
				listeners[i].start();
			}
		}
		
		
		//the initial population of size 6(numMachines * 3)
		// to make paralleling work easier, make it size = number of machines * number of cores on each machine
		
		int population = numMachines * 3;
		int[][] initialPopulation = new int[population][2];
		
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
			
			//to get the fitness values
			double[] fitnessValues = new double[population];
		
			/**
			 * the total number of documents that are being processed. Put them in a folder and add the folder path here.
			 */
			int numberOfDocuments = new File("txtData").listFiles().length;
			//create an instance of the topic modelling class
			TopicModelling tm = new TopicModelling();
			
			
			
			long startTime = System.currentTimeMillis();
		
			int coresNum = 4;
			int newThreadsNum = 3;
			Thread threads[] = new Thread[newThreadsNum];
			for(int i = 0; i < newThreadsNum; i++){
				threads[i] = new Thread( new MyThread(i, numMachines, machineId, initialPopulation, tm, numberOfDocuments, fitnessValues));
				System.out.println("Thread " + i + " begin start...");
				threads[i].start();
				System.out.println("Thread " + i + " end start...");
			}
			
			for(int i = 0; i < newThreadsNum; i++){
				threads[i].join();
				System.out.println("Thread " + i + " joined");
			}
			
			long paraEndTime = System.currentTimeMillis();
			System.out.println("parallel part takes " + (paraEndTime - startTime) + "ms");		
		
			//ranking and ordering the chromosomes based on the fitness function. 
			//no sorting code found?(by Xiaolin)
			//We need only the top 1/3rd of the chromosomes with high fitness values - Silhouette coefficient
			int[][] newPopulation = new int[initialPopulation.length][2];
			//copy only the top 1/3rd of the chromosomes to the new population 
			for(int i = 0 ; i < (initialPopulation.length / 3) ; i++) {
				double maxFitness = Integer.MIN_VALUE;
				int maxFitnessChromosome = -1;
				for(int j = 0 ; j < initialPopulation.length ; j++) {
					if(fitnessValues[j] > maxFitness) {
						maxFitness = fitnessValues[j];
						
						//stop reproducing or creating new generations if the expected fitness is reached by one of the machiens
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
						if(maxFitness > 0.75) {
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
				fitnessValues[maxFitnessChromosome] = Integer.MIN_VALUE;
			}
			
			if(maxFitnessFound) {
				break;
			}
		
		
			//perform crossover - to fill the rest of the 2/3rd of the initial Population
			for(int i = 0 ; i < initialPopulation.length / 3  ; i++ ) {
				newPopulation[(i+1)*2][0] = newPopulation[i][0];
				newPopulation[(i+1)*2][1] = (int) Math.floor(Math.random()*1000 + 1);
				newPopulation[(i+1)*2+1][0] = (int) Math.floor(Math.random()*12 + 2);
				newPopulation[(i+1)*2+1][1] = newPopulation[i][1];
			}
		
			//substitute the initial population with the new population and continue 
			initialPopulation = newPopulation;
			
			
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
		private Socket sockets[];
		private int listenerId;
		public Listener(Socket s[], int i){
			sockets = s;
			listenerId = i;
		}
		
		@Override
        public void run( ){
			ObjectInputStream input = null;
			try {
				input = new ObjectInputStream(sockets[listenerId].getInputStream());
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
				System.out.println("topic: " + msg[0] + " interations: " + msg[1]);
				setMsgFromOthers(msg[0], msg[1]);
			}
		}
	}
}
	
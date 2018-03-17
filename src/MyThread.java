import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class MyThread implements Runnable {
	private int thread_index;
	private PopulationConfig population_cfg;
	private int population_index;
	private TopicModelling tm;
	private int numberOfDocuments;
	private boolean original_version;

	public MyThread(int _thread_index, PopulationConfig _population_cfg, int _population_index, TopicModelling tm, int numberOfDocuments, boolean _original_version) {
		thread_index = _thread_index;// thread index on one machine
		population_cfg = _population_cfg;
		population_index = _population_index;
		this.tm = tm;
		this.numberOfDocuments = numberOfDocuments;
		original_version = _original_version;
	}

	public void run() {
		try {
			if (!original_version && population_cfg.fitness_value > 0.0f) {
				// we have already got the fitness value. For example: this is one of the best
				// chromosomes in last round
				System.out.println("New thread " + thread_index + ": Use one of the best chromosomes in last round. Don't have to call LDA algorithm again. " + population_cfg.to_string() + "  ************************************************");
				return;
			}
			System.out.println((original_version ? "Original" : "New") + " thread " + thread_index + " start running " + population_cfg.to_string());

			// invoke the LDA function
			tm.LDA(population_cfg.number_of_topics, population_cfg.number_of_iterations, false, population_index, original_version);

			// clustermatrix - matrix explaining the distribution of documents into
			// different topics
			// the distibution is written to a text file by the name "distribution.txt"
			double[][] clusterMatrix = new double[numberOfDocuments - 1][population_cfg.number_of_topics];
			if (original_version) {
				System.out.println((original_version ? "Original" : "New") + " thread " + thread_index + " is about to sleep");
				Thread.sleep(2000);
				System.out.println((original_version ? "Original" : "New") + " thread " + thread_index + " is out of sleep");
			}

			// Map to save the documents that belong to each cluster
			// An arraylist multimap allows to save <Key, Value[]> combination
			// each topic will have a cluster
			Multimap<Integer, Integer> clusterMap = ArrayListMultimap.create();

			// reading the values from distribution.txt and populating the cluster matrix
			int rowNumber = 0, columnNumber = 0;
			Scanner scanner = null;
			try {
				scanner = new Scanner(new File("distribution" + population_index + ".txt"));
				scanner.nextLine();

				int documentCount = 0;
				// read the values for every document
				while (documentCount < (numberOfDocuments - 1)) {

					rowNumber = scanner.nextInt();
					scanner.next();

					for (int z = 0; z < population_cfg.number_of_topics; z++) {
						columnNumber = scanner.nextInt();
						if (z == 0) {
							clusterMap.put(columnNumber, rowNumber);
						}
						clusterMatrix[rowNumber][columnNumber] = scanner.nextDouble();
					}
					documentCount = documentCount + 1;
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (scanner != null) {
					scanner.close();
					scanner = null;
				}
			}

			// getting the centroid of each cluster by calculating the average of their
			// cluster distribution
			double[][] clusterCentroids = new double[population_cfg.number_of_topics][population_cfg.number_of_topics];
			for (int k : clusterMap.keySet()) {
				List<Integer> values = (List<Integer>) clusterMap.get(k);

				for (int j = 0; j < values.size(); j++) {
					int docNo = values.get(j);
					for (int y = 0; y < population_cfg.number_of_topics; y++) {
						clusterCentroids[k][y] = clusterCentroids[k][y] + clusterMatrix[docNo][y];
					}
				}
				for (int y = 0; y < population_cfg.number_of_topics; y++) {
					clusterCentroids[k][y] = clusterCentroids[k][y] / values.size();
				}
			}

			// finding the distance of each documents in each cluster finding max distance
			// from other documents in the same cluster
			double[] maxDistanceInsideCluster = new double[numberOfDocuments - 1];
			for (int k : clusterMap.keySet()) {
				List<Integer> values = (List<Integer>) clusterMap.get(k);

				// for each of the documents find the maxDistance from other cluster members
				for (int y = 0; y < values.size(); y++) {
					int docNo = values.get(y);
					maxDistanceInsideCluster[docNo] = 0;
					for (int z = 0; z < values.size(); z++) {
						int otherDocNo = values.get(z);
						if (otherDocNo == docNo) {
							continue;
						}

						// finding euclidean distance between the two points/docuemnts
						double distance = 0;
						for (int h = 0; h < population_cfg.number_of_topics; h++) {
							distance = distance + Math.pow((clusterMatrix[otherDocNo][h] - clusterMatrix[docNo][h]), 2);
						}
						distance = Math.sqrt(distance);
						if (distance > maxDistanceInsideCluster[docNo]) {
							maxDistanceInsideCluster[docNo] = distance;
						}
					}
				}
			}

			// finding each documents minimum distance to the centroids of other clusters
			double[] minDistanceOutsideCluster = new double[numberOfDocuments - 1];
			for (int k : clusterMap.keySet()) {
				List<Integer> values = (List<Integer>) clusterMap.get(k);

				// find the documents min distance from the centroid of other clusters
				for (int y = 0; y < values.size(); y++) {
					int docNo = values.get(y);
					minDistanceOutsideCluster[docNo] = Integer.MAX_VALUE;
					for (int z = 0; z < population_cfg.number_of_topics; z++) {

						// don't calculate the distance to the same cluster
						if (z == k) {
							continue;
						}
						double distance = 0;
						for (int h = 0; h < population_cfg.number_of_topics; h++) {
							distance = distance + Math.pow((clusterCentroids[z][h] - clusterMatrix[docNo][h]), 2);
						}
						distance = Math.sqrt(distance);
						if (distance < minDistanceOutsideCluster[docNo]) {
							minDistanceOutsideCluster[docNo] = distance;
						}
					}
				}
			}

			// calculate the Silhouette coefficient for each document
			double[] silhouetteCoefficient = new double[numberOfDocuments - 1];
			for (int m = 0; m < (numberOfDocuments - 1); m++) {
				silhouetteCoefficient[m] = (minDistanceOutsideCluster[m] - maxDistanceInsideCluster[m]) / Math.max(minDistanceOutsideCluster[m], maxDistanceInsideCluster[m]);
			}

			// find the average of the Silhouette coefficient of all the documents - fitness
			// criteria
			double total = 0;
			for (int m = 0; m < (numberOfDocuments - 1); m++) {
				total = total + silhouetteCoefficient[m];
			}
			population_cfg.fitness_value = total / (numberOfDocuments - 1);
			System.out.println((original_version ? "Original" : "New") + " thread " + thread_index + " finish  " + population_cfg.to_string());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
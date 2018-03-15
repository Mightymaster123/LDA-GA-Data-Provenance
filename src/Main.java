
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {

	static final int CHECK_COUNT = 1;

	public static void main(String[] argv) throws IOException, InterruptedException, ClassNotFoundException {
		ArrayList<ResultStatistics> listResultOriginal = new ArrayList<ResultStatistics>(CHECK_COUNT);
		for (int i = 0; i < CHECK_COUNT; ++i) {
			DataProvenance data_provenance = new DataProvenance();
			listResultOriginal.add(data_provenance.process(true));
		}
		ArrayList<ResultStatistics> listResultNew = new ArrayList<ResultStatistics>(CHECK_COUNT);
		for (int i = 0; i < CHECK_COUNT; ++i) {
			DataProvenance data_provenance = new DataProvenance();
			listResultNew.add(data_provenance.process(false));
		}

		System.out.println("\n\n\n original_version:");
		for (int i = 0; i < listResultOriginal.size(); ++i) {
			ResultStatistics result = listResultOriginal.get(i);
			if (result != null && result.is_master) {
				System.out.println(result.to_string("    " + (i + 1) + "."));
			}
		}
		System.out.println("\n\n\n new_version:");
		for (int i = 0; i < listResultNew.size(); ++i) {
			ResultStatistics result = listResultNew.get(i);
			if (result != null && result.is_master) {
				System.out.println(result.to_string(String.format("    %3d.", (i + 1))));
			}
		}
		System.out.println("\n\n\n");
	}
}

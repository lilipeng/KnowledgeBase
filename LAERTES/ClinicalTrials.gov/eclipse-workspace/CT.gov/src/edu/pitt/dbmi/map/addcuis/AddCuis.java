package edu.pitt.dbmi.map.addcuis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.pitt.terminology.client.IndexFinderTerminology;
import edu.pitt.terminology.lexicon.Concept;
import edu.pitt.terminology.lexicon.SemanticType;
import edu.pitt.terminology.lexicon.Source;
import edu.pitt.terminology.util.TerminologyException;

/**
 * Adds CUIs to the file in the Git "Example-CT.gov-data-v3-v011.csv"
 * 
 * Adds the MeSH CUIs to the MeSH Condition and MeSH Interventions in a pipe delimited format
 * from the triads MeSH exact string mapper which does with amazing accuracy.
 * 
 * MedDRA, SNOMED_CT, and RxNorm CUIs from Nobletools jar NLP program from the text column
 * Using the IndexFinderTerminology class.
 * 
 * Usage:
 * - In Eclipse:
 * $ java AddCuis <input>
 * 
 * - In the jar:
 * $ java-jar AddCuis.jar <input>
 * 
 * @author epicstar
 *
 */

public class AddCuis {
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * This is the container for the exact MeSH String matching with its CUIs. The keys are non-capitalized
	 */
	private HashMap<String, String> meshMap;
	
	/**
	 * This is the Nobletools section of the code to get the MDR, MeSH, and SNOMEDCT_US CUIs from the text portion of the input
	 */
	private IndexFinderTerminology term;
	
	/**
	 * The verbose "log" file of missing outputs
	 */
	private PrintWriter pseudolog;
	
	/**
	 * will report the amount of missing CUIs from the text files
	 */
	private HashMap<Source, Integer> missingNoblecoderCUIs;
	
	/**
	 * The MedDRA, SNOMEDCT_US, and MSH vocabs to get from Nobletools getCodes() output
	 */
	private static final Source[] nobleCoderVocabs = {
		new Source("MDR"), 
		new Source("SNOMEDCT_US"), 
		new Source("MSH")
	};

	/**
	 * Finds the number of missing MeSH exact matches from both the msh_condition
	 */
	private int missingExactCondition;
	
	/**
	 * Finds the number of missing MeSH exact matches from both the msh_intervention
	 */
	private int missingExactIntervention;
	
	/**
	 * amount of rows in the file
	 */
	private int lines;
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	
	
	
	/**
	 * Initial Constructor to add the CUIs. The Outputs will usually be put in the parent directory.
	 * @throws FileNotFoundException
	 */
	public AddCuis() throws FileNotFoundException {
		meshMap = null;
		term = null;
		pseudolog = new PrintWriter("../../ctgov-inout/missingCUIs.txt");
		missingExactCondition = 0;
		missingExactIntervention = 0;
		initializeMissingNobletoolsMap();
	}
	
	/**
	 * initializes missingNoblecoderCUIs
	 */
	private void initializeMissingNobletoolsMap() {
		missingNoblecoderCUIs = new HashMap<Source, Integer>(nobleCoderVocabs.length + 1);
		for(Source voc: nobleCoderVocabs)
			missingNoblecoderCUIs.put(voc, 0);
		missingNoblecoderCUIs.put(new Source("All missing"), 0);
	}
	
	/**
	 * Method that adds the CUIs to the input file
	 * @param input Clinicaltrials.gov table. In this case I used "../Example-CT.gov-data-v3-v011.csv" tab-deliminated
	 * @param output Clinicaltrials.gov table with the added CUIs tab-deliminated in this case
	 * @return True if successful, False if an error was caught
	 */
	@SuppressWarnings("unchecked")
	public boolean addCuis(String input, String output, String meshLocation) {
		
		
		
		try {
			
			BufferedReader in = new BufferedReader(new FileReader(input));
			PrintWriter out = new PrintWriter(output);
			List<String> line = new LinkedList<String>(Arrays.asList(in.readLine().split("\t")));
			
			System.out.println("Starting the CUI adder");
			insert(line, 8, "msh_intervention_CUI");
			insert(line, 7, "msh_condition_CUI");
			
			insert(line, 2, "text_MedDRA_CUI");
			insert(line, 2, "text_SNOMED_CT_CUI");
			insert(line, 2, "text_MeSH_CUI");
			
			out.write(outputString(line, "\t") + "\n");			
			
			ObjectInputStream meshMapfile = new ObjectInputStream(new FileInputStream(meshLocation));
			meshMap = (HashMap<String, String>)meshMapfile.readObject();
			setupNobleCoder();
			meshMapfile.close();
			
			System.out.println("Adding CUIs. This may take take a while.");
			
			//Reading the input row-by-row
			while(in.ready()) {
				
				line = new ArrayList<String>(Arrays.asList(in.readLine().split("\t")));
				addMesh(line, 8);
				addMesh(line, 7);
				addFromNobleCoder(line);
				out.write(outputString(line, "\t") + "\n");
				++lines;
			}
			
			writeReport(output + "_report.txt");
			System.out.println("Finished!");
			in.close();
			out.close();
			pseudolog.close();
			
		
		} catch(IOException e) {
			System.out.println(e.getMessage());
			return false;
		} catch(ClassNotFoundException e) {
			System.out.println(e.getMessage());
			return false;
		}
			
		return true;
		
	}
	
	/**
	 * Outputs a human readable report on the amount of missing CUIs
	 * @param output the output file
	 */
	private void writeReport(String outfile) {
		PrintWriter report;
		try {
			System.out.println("Writing Report");
			report = new PrintWriter(outfile);
			
			report.write("Rows: " + lines + "\n\n");
			
			report.write("Nobletools Properties....\n");
			
			for(Entry<Object, Object> properties : term.getSearchProperties().entrySet()) {
				report.write("\t" + properties.getKey().toString() + ": " + properties.getValue().toString() + "\n");
			}
			
			report.write("\nMissing Text CUIs....\n");
			for(Entry<Source, Integer> pairs : missingNoblecoderCUIs.entrySet())
				report.write(pairs.getKey().toString() + ": " + pairs.getValue().toString() + "\n");
//			report.write("Nobletools could not find in any Vocabulary: " + missingNoblecoderCUIs.get(new Source("All three missing")) + "\n");
			
			report.write("\nMissing Exact MeSH matches....\n");
			report.write("Missing exact MSH Conditions: " + missingExactCondition + "\n");
			report.write("Missing exact MSH Interventions: " + missingExactIntervention);
			report.close();
			System.out.println("Done Writing report!");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds the CUIs retrieved from Nobletools programmatically
	 * 
	 * It looks through every result possible and adds CUIs that are specified by
	 * nobeCoderVocabs if the previously temporarily stored CUI isn't null.
	 * 
	 * The assumption here is that the accuracy of the Nobletools results is higher 
	 * when it is higher on the Results list.
	 * 
	 * @param line row represented as a List
	 */
	private void addFromNobleCoder(List<String> line) {
		String find = line.get(1);
		try {
			
			Concept[] results = term.search(find);
			if(results.length > 0) {

				
				Map<Source, String> tempCUIs = new HashMap<Source, String>(nobleCoderVocabs.length);
				Map<Source, String> tempNames = new HashMap<Source, String>(nobleCoderVocabs.length);
				for(Concept result : results) {
					@SuppressWarnings("rawtypes")
					Map nobleCodes = result.getCodes();
					for(Source voc : nobleCoderVocabs) {
						if(tempCUIs.get(voc) == null || find.equalsIgnoreCase(result.getName())) {
							tempCUIs.put(voc, (String)nobleCodes.get(voc));
							tempNames.put(voc, result.getName());
						}
					}
				}
				addCUIs(line, tempCUIs, tempNames);
			}
			else {
				for(Source vocab : nobleCoderVocabs) {
					insert(line, 2, "");
					missingNoblecoderCUIs.put(vocab, missingNoblecoderCUIs.get(vocab) + 1);
				}
				missingNoblecoderCUIs.put(new Source("All missing"), missingNoblecoderCUIs.get(new Source("All missing")) + 1);
				pseudolog.write(find + " not found in NobleCoder." + "\n");
			}
		} catch (TerminologyException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds CUIs to the list. In this case,
	 * @param line row represented as a List
	 * @param CUIs the added cuis from addFromNobleCoder
	 */
	private void addCUIs(List<String> line, Map<Source, String> CUIs, Map<Source, String> tempNames) {
		String cui, name;
		int missing = 0;
		StringBuffer log = new StringBuffer();
		for(Source voc : nobleCoderVocabs) {
			cui = CUIs.get(voc);
			name = tempNames.get(voc);
			if(cui == null) {
				insert(line, 2, "");
				missingNoblecoderCUIs.put(voc, missingNoblecoderCUIs.get(voc) + 1);
				++missing;
			}
			else
				insert(line, 2, cui + "|" + name);
		}
		if(missing == nobleCoderVocabs.length)
			missingNoblecoderCUIs.put(new Source("All missing"), missingNoblecoderCUIs.get(new Source("All missing")) + 1);
	}
	
	/**
	 * Sets up Nobletools to the settings needed to run accurately with the least noise
	 * @throws IOException
	 */
	private void setupNobleCoder() throws IOException {

			term = new IndexFinderTerminology();
			term.load("triads-test-Feb2014");
			term.setIgnoreAcronyms(false);
			term.setOverlapMode(false);
			term.setSelectBestCandidate(false);
			term.setDefaultSearchMethod("best-match");
			term.setSubsumptionMode(false);
			term.setFilterSources(term.getSources("MDR,MSH,SNOMEDCT_US"));
			term.
				setFilterSemanticType(SemanticType
								.getSemanticTypes(new String []{
										"Event",
										"Pathologic Function",
										"Body Substance",
										"Functional Concept",
										"Mental or Behavioral Concept",
										"Mental or Behavioral Dysfunction",
										"Finding",
										"Sign or Symptom",
										"Individual Behavior",
										"Disease or Syndrome",
										"Mental Process",
										"Body Part, Organ, or Organ Component",
										"Therapeutic or Preventive Procedure",
										"Laboratory Procedure",
										"Tissue",
										"Pharmacological Substance",
										"Amino Acid, Peptide, or Protein",
										"Organic Chemical",
										"Body System",
										"Injury or Poisoning",
										"Cell",
										"Acquired Abnormality"
															   }
												)
									  );
			pseudolog.write(term.getSearchProperties().toString() + "\n");
	}
	
	/**
	 * Finds the MeSH exact String mapping
	 * @param line the row represented as a list
	 * @param index location where the mesh column is
	 */
	private void addMesh(List<String> line, int index) {
		String queryThis = line.get(index  - 1);
		List<String> cuis = new LinkedList<String>();
		for(String word : queryThis.split("\\|")) {
			String lower = word.toLowerCase();
			if(meshMap.containsKey(lower))
				cuis.add(meshMap.get(lower));
			else {
				cuis.add("");
				if(!"".equals(lower)) {
					pseudolog.write("MESH: No exact match for \"" + word + "\"" + "\n");
					if(index == 7)
						++missingExactCondition;
					else
						++missingExactIntervention;
				}
			}
		}
		line.add(index, outputString(cuis, "|"));
	}
	
	/**
	 * Inserts a column to the row being fed
	 * @param line the row represented as a list
	 * @param index location where the column needed to add is
	 * @param add String being added to the row
	 */
	private void insert(List<String> line, int index, String add) {
		line.add(index, add);
	}
	
	/**
	 * Turns the row into a string representation of itself
	 * @param string the row represented as a List
	 * @param delim delimiter used to output
	 * @return a "string" delimited line
	 */
	private String outputString(List<String> string, String delim) {
		
		StringBuffer buf = new StringBuffer();
		int lastIndex = string.size() - 1;
		
		for(int i=0;i<string.size();++i) {
			buf.append(string.get(i));
			if(i < lastIndex)
				buf.append(delim);
			else
				return buf.toString();
		}
		
		return "";
	}
	
	/**
	 * Main to run to add CUIs to the main module
	 * @param args not used
	 */
	public static void main(String[] args) {
		
		final String inputfile = args[0];
		final String outputfile = args[0] + "_CUIs_v3.csv";
		final String meshLocation = args[1];
		AddCuis add;
		try {
			add = new AddCuis();
			add.addCuis(inputfile, outputfile, meshLocation);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}

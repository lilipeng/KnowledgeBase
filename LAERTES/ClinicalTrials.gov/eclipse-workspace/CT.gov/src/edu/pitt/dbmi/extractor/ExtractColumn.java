package edu.pitt.dbmi.extractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class ExtractColumn {
	public static void main(String[] args) {
		final String inputfile = "../Example-CT.gov-data-v3-v011.csv";
		final String outputfile = args[0];
		final int index = Integer.parseInt(args[1]);
		
		
		try {
			
			
			BufferedReader in = new BufferedReader(new FileReader(inputfile));
			
			in.readLine();
			int i = 0;
			while(in.ready()) {
				PrintWriter out = new PrintWriter(new File(outputfile + "/" + i++));
				out.write(in.readLine().split("\t")[index].trim() + "\n");
				out.close();
				if(i == 100)
					break;
			}
			
			in.close();
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

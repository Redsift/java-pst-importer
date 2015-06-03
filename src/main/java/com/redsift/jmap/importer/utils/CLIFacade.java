package com.redsift.jmap.importer.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.redsift.jmap.importer.PSTImporter;

/**
 * CLI facade for PSTImporter command line arguments
 * 
 * @author randalpinto
 *
 */
public class CLIFacade {
	private String[] args = null;
	private Options options = new Options();

	public CLIFacade(String args[]) {
		options.addOption("a", true,
				"Whether to parse attachments and folder name where to store them.");
		options.addOption("o", true,
				"Whether to write messages to file rather than Aerospike and folder name where to store them.");
		options.addOption("f", true, "The PST file to import.");
		options.addOption("i", true, "The PST file inventory to import.");
		options.addOption("n", true, "Aerospike namespace.");
		options.addOption("s", true, "Aerospike set name.");
		options.addOption("h", true, "Aerospike hostname. Default: localhost");
		options.addOption("p", true, "Aerospike port. Default: 3000");
		options.addOption("help", true, "Displays this help");

		this.args = args;
	}

	public CommandLine parse() {
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			if (cmd.hasOption("help")) {
				help();
				return null;
			}
			// Needs to have a file or inventory at least
			if (!(cmd.hasOption("f") ^ cmd.hasOption("i"))) {
				help();
				return null;
			}
		} catch (ParseException e) {
			e.printStackTrace();
			help();
		}
		return cmd;
	}

	public String[] parseInventoryFile(String name)
			throws FileNotFoundException {
		ArrayList<String> inventory = new ArrayList<String>();
		File file = new File(name);
		Scanner input = new Scanner(file);

		while (input.hasNext()) {
			String item = input.nextLine().trim();
			if (!item.isEmpty()) {
				inventory.add(item);
			}
		}
		input.close();

		return inventory.toArray(new String[0]);
	}

	private void help() {
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp(PSTImporter.class.getName(), options);
	}
}

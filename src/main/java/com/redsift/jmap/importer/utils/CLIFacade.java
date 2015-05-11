package com.redsift.jmap.importer.utils;

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
		options.addOption("a", true, "Whether to parse attachments and folder name where to store them.");
		options.addOption("f", true, "The PST file to import.");
		options.addOption("n", true, "Aerospike namespace.");
		options.addOption("s", true, "Aerospike set name.");
		options.addOption("h", true,
				"Aerospike hostname. Default: localhost");
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
			// Needs to have a file at least
			if (!cmd.hasOption("f") || !cmd.hasOption("n") || !cmd.hasOption("s")) {
				help();
				return null;
			}
		} catch (ParseException e) {
			e.printStackTrace();
			help();
		}
		return cmd;
	}

	private void help() {
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp(PSTImporter.class.getName(), options);
	}
}

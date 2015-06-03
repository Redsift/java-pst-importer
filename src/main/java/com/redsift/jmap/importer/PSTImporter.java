package com.redsift.jmap.importer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;

import com.aerospike.client.AerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jmap.converter.JMAPMessageConverter;
import com.jmap.utils.JMAPFileUtils;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.redsift.jmap.importer.utils.AerospikeFacade;
import com.redsift.jmap.importer.utils.CLIFacade;

public class PSTImporter {

	private String attachmentsFolder;
	private String outputFolder;
	private AerospikeClient client;
	private String namespace;
	private String set;

	public static void main(String[] args) {
		try {
			CLIFacade cli = new CLIFacade(args);
			CommandLine cmd = cli.parse();
			if (cmd != null) {
				String host = "localhost";
				int port = 3000;
				String attFolder = null;
				String outFolder = null;
				if (cmd.hasOption("h")) {
					host = cmd.getOptionValue("h");
				}
				if (cmd.hasOption("p")) {
					port = (Integer) cmd.getParsedOptionValue("p");
				}
				if (cmd.hasOption("a")) {
					attFolder = cmd.getOptionValue("a");
				}
				if (cmd.hasOption("o")) {
					outFolder = cmd.getOptionValue("o");
				}
				String namespace = cmd.getOptionValue("n");
				String set = cmd.getOptionValue("s");
				String[] files = null;
				if (cmd.hasOption("f")) {
					files = new String[] { cmd.getOptionValue("f") };
				} else {
					files = cli.parseInventoryFile(cmd.getOptionValue("i"));
				}

				AerospikeClient client = null;
				// If not outputing it to file
				if(!cmd.hasOption("o")) {
					// Create Aerospike client with default policy
					client = AerospikeFacade.createClient(host,
							port);
				}
				PSTImporter importer = new PSTImporter(client, namespace, set,
						attFolder, outFolder);
				for (int i = 0; i < files.length; i++) {
					PSTFile pstFile = new PSTFile(files[i]);
					System.out.format("Processing file: %s%n", files[i]);
					importer.processFolder(pstFile.getMessageStore()
							.getDisplayName(), pstFile.getRootFolder());
					System.out.format("msgTotal: %d compTotal: %d%n", AerospikeFacade.msgTotal, AerospikeFacade.compTotal);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private List<String> folderPath = new ArrayList<String>();

	public PSTImporter(AerospikeClient client, String namespace, String set,
			String attachmentsFolder, String outputFolder) {
		this.client = client;
		this.namespace = namespace;
		this.set = set;
		this.attachmentsFolder = attachmentsFolder;
		this.outputFolder = outputFolder;
	}

	public void processFolder(String mboxName, PSTFolder folder)
			throws PSTException, java.io.IOException {
		// the root folder doesn't have a display name
		String fname = folder.getDisplayName();
		if (fname != null && !fname.isEmpty()) {
			this.folderPath.add(fname);
			// System.out.println(this.folderPath);
		}

		// go through the folders...
		if (folder.hasSubfolders()) {
			Vector<PSTFolder> childFolders = folder.getSubFolders();
			for (PSTFolder childFolder : childFolders) {
				processFolder(mboxName, childFolder);
			}
		}

		// and now the emails for this folder
		if (folder.getContentCount() > 0) {
			PSTMessage email = (PSTMessage) folder.getNextChild();
			while (email != null) {
				// System.out.println("Processing: " + email.toString());
				ObjectMapper mapper = new ObjectMapper();
				if(this.outputFolder != null) {
					// Indent for better readability
					mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
					mapper.configure(
							SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);				
				}
				String msg = null;
				if (this.attachmentsFolder != null) {
					// Parse message and write attachment to file
					msg = mapper.writeValueAsString(JMAPMessageConverter
							.getJMAPMessageWithAttachments(mboxName, email,
									this.folderPath, this.attachmentsFolder));
				} else {
					// Parse message and don't write attachment
					msg = mapper.writeValueAsString(JMAPMessageConverter
							.getJMAPMessage(mboxName, email,
									this.folderPath));
				}
				if(this.outputFolder != null) {
					// Write message to file
					File f = new File(this.outputFolder + File.separator
							+ JMAPFileUtils.sha256(email
							.getInternetMessageId()));
					f.getParentFile().mkdirs();
					FileUtils.writeStringToFile(f, msg);
				}
				else {
					AerospikeFacade.putMessage(this.client, this.namespace,
							this.set, msg);
				}
				// Move to the next message
				email = (PSTMessage) folder.getNextChild();
			}
		}
		// Pop the current folder from the path as we are returning from
		// recursion
		if (folderPath.size() > 0) {
			folderPath.remove(folderPath.size() - 1);
		}
	}
}

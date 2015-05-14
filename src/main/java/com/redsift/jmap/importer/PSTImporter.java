package com.redsift.jmap.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;

import com.aerospike.client.AerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmap.converter.JMAPMessageConverter;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.redsift.jmap.importer.utils.AerospikeFacade;
import com.redsift.jmap.importer.utils.CLIFacade;

public class PSTImporter {

	// private final String OUTPUT_DIRECTORY = "output/";
	private String attachmentsFolder;
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
				if (cmd.hasOption("h")) {
					host = cmd.getOptionValue("h");
				}
				if (cmd.hasOption("p")) {
					port = (Integer) cmd.getParsedOptionValue("p");
				}
				if (cmd.hasOption("a")) {
					attFolder = cmd.getOptionValue("a");
				}
				String namespace = cmd.getOptionValue("n");
				String set = cmd.getOptionValue("s");
				String[] files = null;
				if (cmd.hasOption("f")) {
					files = new String[] { cmd.getOptionValue("f") };
				} else {
					files = cli.parseInventoryFile(cmd.getOptionValue("i"));
				}

				// Create Aerospike client with default policy
				AerospikeClient client = AerospikeFacade.createClient(host,
						port);

				PSTImporter importer = new PSTImporter(client, namespace, set,
						attFolder);
				for (int i = 0; i < files.length; i++) {
					PSTFile pstFile = new PSTFile(files[i]);
					System.out.format("Processing file: %s%n", files[i]);
					importer.processFolder(pstFile.getMessageStore()
							.getDisplayName(), pstFile.getRootFolder());
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private List<String> folderPath = new ArrayList<String>();

	public PSTImporter(AerospikeClient client, String namespace, String set,
			String attachmentsFolder) {
		this.client = client;
		this.namespace = namespace;
		this.set = set;
		this.attachmentsFolder = attachmentsFolder;
	}

	public void processFolder(String mboxName, PSTFolder folder)
			throws PSTException, java.io.IOException {
		// the root folder doesn't have a display name
		String fname = folder.getDisplayName();
		if (fname != null && !fname.isEmpty()) {
			folderPath.add(fname);
			// System.out.println(folderPath);
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

				// TODO: remove indentation from production system
				// mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
				// mapper.configure(
				// SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
				String msg = null;
				if (this.attachmentsFolder != null) {
					msg = mapper.writeValueAsString(JMAPMessageConverter
							.getJMAPMessageWithAttachments(mboxName, email,
									folderPath, attachmentsFolder));
				} else {
					msg = mapper.writeValueAsString(JMAPMessageConverter
							.getJMAPMessageWithoutAttachments(mboxName, email,
									folderPath));
				}
				AerospikeFacade.putMessage(this.client, this.namespace,
						this.set, msg);

				// DEBUG: Write message to file
				// mapper.writeValue(
				// new File(attachmentsFolder
				// + JMAPFileUtils.sha256(email
				// .getInternetMessageId())), msg);

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

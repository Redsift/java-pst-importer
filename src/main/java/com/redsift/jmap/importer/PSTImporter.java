package com.redsift.jmap.importer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jmap.converter.JMAPMessageConverter;
import com.jmap.utils.JMAPFileUtils;
import com.pff.PSTException;
import com.pff.PSTFolder;
import com.pff.PSTMessage;

public class PSTImporter {

	private final String OUTPUT_DIRECTORY = "output/";
	private List<String> folderPath = new ArrayList<String>();

	private static final String AEROSPIKE_NAMESPACE =  "test";
	private static final String AEROSPIKE_METADATA_SET = "metadata";
	private static final String AEROSPIKE_SEQ_KEY = "seq";

	private static final String AEROSPIKE_LAST_BIN = "last";

	public static void main(String[] args) {
		AerospikeClient client = new AerospikeClient(null, "localhost", 3000);
		
		// Get new JobID
		Key kseq = new Key(PSTImporter.AEROSPIKE_NAMESPACE, PSTImporter.AEROSPIKE_METADATA_SET, PSTImporter.AEROSPIKE_SEQ_KEY);
		Bin lbin = new Bin(PSTImporter.AEROSPIKE_LAST_BIN, (long)1);
		
		Record rec = client.operate(null, kseq, Operation.add(lbin), Operation.get());
		Long id = (Long)rec.bins.get(lbin.name);
		
		System.out.format("Id: %s%n", id.toString());
			
		Key key = new Key(PSTImporter.AEROSPIKE_NAMESPACE, "test-set", id.toString());
		Bin binStatus = new Bin("status", "READY");
		Bin binBody = new Bin("body", "<JSON>");
		
		
		System.out.format("Single Bin Put: namespace=%s set=%s key=%s bin=%s value=%s bin=%s value=%s%n",
			key.namespace, key.setName, key.userKey, binStatus.name, binStatus.value, binBody.name, binBody.value);
		
		WritePolicy writePolicy = new WritePolicy();
		writePolicy.commitLevel = CommitLevel.COMMIT_MASTER;
		writePolicy.sendKey = true;
		writePolicy.recordExistsAction = RecordExistsAction.CREATE_ONLY;

		client.put(writePolicy, key, binStatus, binBody);

		System.out.format("Single Bin Get: namespace=%s set=%s key=%s%n", key.namespace, key.setName, key.userKey);

		Record record = client.get(null, key);
		
		if (record == null) {
			System.out.format(
					"Failed to get: namespace=%s set=%s key=%s", key.namespace, key.setName, key.userKey);
		}

		System.out.println(record.toString());
		
		client.close();
		
//		try {
//			TestJMAP importer = new TestJMAP();
//			PSTFile pstFile = new PSTFile(args[0]);
//			importer.processFolder(pstFile.getMessageStore().getDisplayName(),
//					pstFile.getRootFolder());
//		} catch (Exception err) {
//			err.printStackTrace();
//		}
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
				System.out.println("Processing: " + email.toString());
				ObjectMapper mapper = new ObjectMapper();

				// TODO: remove indentation from production system
				mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
				mapper.configure(
						SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
				mapper.writeValue(
						new File(OUTPUT_DIRECTORY
								+ JMAPFileUtils.sha256(email
										.getInternetMessageId())),
						JMAPMessageConverter.getJMAPMessageWithAttachments(
								mboxName, email, folderPath, OUTPUT_DIRECTORY));

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

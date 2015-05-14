package com.redsift.jmap.importer.utils;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;

/**
 * 
 * @author randalpinto
 *
 */
public class AerospikeFacade {
	// private static final String AEROSPIKE_NAMESPACE = "test";
	// private static final String AEROSPIKE_TEST_SET = "test-set";
	private static final String AEROSPIKE_METADATA_SET = "metadata";
	private static final String AEROSPIKE_SEQ_KEY = "seq";

	private static final String AEROSPIKE_LAST_BIN = "last";

	/**
	 * 
	 * @param policy
	 * @param host
	 * @param port
	 * @throws AerospikeException
	 */
	public static AerospikeClient createClient(String host, int port)
			throws AerospikeException {
		return new AerospikeClient(null, host, port);
	}

	public static void closeClient(AerospikeClient client) {
		client.close();
	}

	/**
	 * Get new JobId for a given namespace
	 * 
	 * @param namespace
	 * @return
	 * @throws AerospikeException
	 */

	private static Long getJobId(AerospikeClient client, String namespace)
			throws AerospikeException {
		Key kseq = new Key(namespace, AerospikeFacade.AEROSPIKE_METADATA_SET,
				AerospikeFacade.AEROSPIKE_SEQ_KEY);
		Bin lbin = new Bin(AerospikeFacade.AEROSPIKE_LAST_BIN, (long) 1);

		Record rec = client.operate(null, kseq, Operation.add(lbin),
				Operation.get());
		Long id = (Long) rec.bins.get(lbin.name);
		// System.out.format("Id: %s%n", id.toString());
		return id;
	}

	public static void putMessage(AerospikeClient client, String namespace,
			String set, String message) {
		LZ4Factory factory = LZ4Factory.fastestInstance();

		// Compress Message
		LZ4Compressor compressor = factory.fastCompressor();
		byte[] compressedMsg = compressor.compress(message.getBytes());

		// DEBUG: Compression ratios
		//double compression = (double) compressedLength
		//		/ (double) message.length();
		//System.out.format("Compression: %s (%d -> %d)%n",
		//		MessageFormat.format("{0,number,#.##%}", compression),
		//		message.length(), compressedLength);

		Key key = new Key(namespace, set,
		getJobId(client, namespace).toString());
		Bin binStatus = new Bin("status", "READY");
		Bin binBody = new Bin("body", compressedMsg);

		// DEBUG: Put operation
		// System.out
		// .format("Single Bin Put: namespace=%s set=%s key=%s bin=%s value=%s bin=%s value=%s%n",
		// key.namespace, key.setName, key.userKey,
		// binStatus.name, binStatus.value, binBody.name,
		// binBody.value);

		WritePolicy writePolicy = new WritePolicy();
		writePolicy.commitLevel = CommitLevel.COMMIT_MASTER;
		writePolicy.sendKey = true;
		writePolicy.recordExistsAction = RecordExistsAction.CREATE_ONLY;

		// Skip messages > 1MB (Aerospike limitation)
		if (compressedMsg.length > (1024 * 1024)) {
			// DEBUG: Large messages
			// Shouldn't happen though as they are now compressed
			System.out.format("Skipped message. Size: %d%n", compressedMsg.length);
		} else {
			client.put(writePolicy, key, binStatus, binBody);
		}
		
		// DEBUG: Get the record just written
		// Record r = client.get(null, key);
		// System.out.format("Got: %s%n", r.toString());
	}
}

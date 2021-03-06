/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.hashtrees.test.utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.hashtrees.HashTreesIdProvider;
import org.hashtrees.HashTreesImpl;
import org.hashtrees.ModuloSegIdProvider;
import org.hashtrees.SegmentIdProvider;
import org.hashtrees.SimpleTreeIdProvider;
import org.hashtrees.store.HashTreesMemStore;
import org.hashtrees.store.HashTreesPersistentStore;
import org.hashtrees.store.HashTreesStore;
import org.hashtrees.store.SimpleMemStore;

public class HashTreesImplTestUtils {

	private static final Random RANDOM = new Random(System.currentTimeMillis());
	public static final int ROOT_NODE = 0;
	public static final int DEFAULT_TREE_ID = 1;
	public static final int DEFAULT_SEG_DATA_BLOCKS_COUNT = 1 << 5;
	public static final int DEFAULT_HTREE_SERVER_PORT_NO = 11111;
	public static final SegIdProviderTest SEG_ID_PROVIDER = new SegIdProviderTest();
	public static final SimpleTreeIdProvider TREE_ID_PROVIDER = new SimpleTreeIdProvider();

	/**
	 * Default SegId provider which expects the key to be an integer wrapped as
	 * bytes.
	 * 
	 */
	public static class SegIdProviderTest implements SegmentIdProvider {

		@Override
		public int getSegmentId(byte[] key) {
			try {
				ByteBuffer bb = ByteBuffer.wrap(key);
				return bb.getInt();
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Exception occurred while converting the string");
			}
		}

	}

	public static class HTreeComponents {

		public final HashTreesStore hTStore;
		public final SimpleMemStore store;
		public final HashTreesImpl hTree;

		public HTreeComponents(final HashTreesStore hTStore,
				final SimpleMemStore store, final HashTreesImpl hTree) {
			this.hTStore = hTStore;
			this.store = store;
			this.hTree = hTree;
		}
	}

	public static byte[] randomBytes() {
		byte[] emptyBuffer = new byte[8];
		RANDOM.nextBytes(emptyBuffer);
		return emptyBuffer;
	}

	public static ByteBuffer randomByteBuffer() {
		byte[] random = new byte[8];
		RANDOM.nextBytes(random);
		return ByteBuffer.wrap(random);
	}

	public static String randomDirName() {
		return "/tmp/test/random" + RANDOM.nextInt();
	}

	public static HTreeComponents createHashTree(int noOfSegDataBlocks,
			boolean enabledNonBlockingCalls,
			final HashTreesIdProvider treeIdProv,
			final SegmentIdProvider segIdPro, final HashTreesStore hTStore) {
		SimpleMemStore store = new SimpleMemStore();
		HashTreesImpl hTree = new HashTreesImpl.Builder(store, treeIdProv,
				hTStore).setNoOfSegments(noOfSegDataBlocks)
				.setEnabledNonBlockingCalls(enabledNonBlockingCalls)
				.setSegmentIdProvider(segIdPro).build();
		store.registerHashTrees(hTree);
		return new HTreeComponents(hTStore, store, hTree);
	}

	public static HTreeComponents createHashTree(int noOfSegments,
			boolean enabledNonBlockingCalls, final HashTreesStore hTStore) {
		SimpleMemStore store = new SimpleMemStore();
		ModuloSegIdProvider segIdProvider = new ModuloSegIdProvider(
				noOfSegments);
		HashTreesImpl hTree = new HashTreesImpl.Builder(store,
				TREE_ID_PROVIDER, hTStore).setNoOfSegments(noOfSegments)
				.setEnabledNonBlockingCalls(enabledNonBlockingCalls)
				.setSegmentIdProvider(segIdProvider).build();
		store.registerHashTrees(hTree);
		return new HTreeComponents(hTStore, store, hTree);
	}

	public static HashTreesMemStore generateInMemoryStore() {
		return new HashTreesMemStore();
	}

	public static HashTreesPersistentStore generatePersistentStore()
			throws IOException {
		return new HashTreesPersistentStore(randomDirName());
	}

	public static HashTreesStore[] generateInMemoryAndPersistentStores()
			throws IOException {
		HashTreesStore[] stores = new HashTreesStore[2];
		stores[0] = generateInMemoryStore();
		stores[1] = generatePersistentStore();
		return stores;
	}

	public static void closeStores(HashTreesStore... stores) {
		for (HashTreesStore store : stores) {
			if (store instanceof HashTreesPersistentStore) {
				HashTreesPersistentStore pStore = (HashTreesPersistentStore) store;
				FileUtils.deleteQuietly(new File(pStore.getDbDir()));
			}
		}
	}
}

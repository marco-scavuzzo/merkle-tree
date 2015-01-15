package org.hashtrees.test;

import static org.hashtrees.test.utils.HashTreesImplTestUtils.DEFAULT_SEG_DATA_BLOCKS_COUNT;
import static org.hashtrees.test.utils.HashTreesImplTestUtils.TREE_ID_PROVIDER;
import static org.hashtrees.test.utils.HashTreesImplTestUtils.generateInMemoryStore;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.hashtrees.HashTreesConstants;
import org.hashtrees.store.HashTreesManagerStore;
import org.hashtrees.store.HashTreesMemStore;
import org.hashtrees.store.HashTreesStore;
import org.hashtrees.store.SimpleMemStore;
import org.hashtrees.synch.HashTreesManager;
import org.hashtrees.test.utils.HashTreesImplTestObj;
import org.hashtrees.test.utils.HashTreesImplTestObj.HTSynchEvent;
import org.hashtrees.test.utils.HashTreesImplTestUtils;
import org.hashtrees.thrift.generated.RemoteTreeInfo;
import org.hashtrees.thrift.generated.ServerName;
import org.junit.Test;

public class HashTreesManagerTest {

	private static void waitForTheEvent(BlockingQueue<HTSynchEvent> events,
			HTSynchEvent expectedEvent, long maxWaitTime)
			throws InterruptedException {
		HTSynchEvent event = null;
		long startTime = System.currentTimeMillis();
		while (true) {
			event = events.poll(1000, TimeUnit.MILLISECONDS);
			if (event == expectedEvent)
				break;
			else if (event == null) {
				long diff = System.currentTimeMillis() - startTime;
				if (diff > maxWaitTime)
					break;
			}
		}
		Assert.assertNotNull(event);
		Assert.assertEquals(event, expectedEvent);
	}

	private static class HashTreeSyncManagerComponents {
		volatile HashTreesStore htStore;
		volatile HashTreesManager syncMgrImpl;
		volatile SimpleMemStore storeImplTest;
	}

	private static HashTreeSyncManagerComponents createHashTreeSyncManager(
			BlockingQueue<HTSynchEvent> events, int portNo,
			long fullRebuildTimeInterval, long schedPeriod) {
		HashTreesMemStore inMemoryStore = generateInMemoryStore();
		HashTreesStore htStore = inMemoryStore;
		HashTreesManagerStore syncMgrStore = inMemoryStore;

		SimpleMemStore store = new SimpleMemStore();

		HashTreesImplTestObj hTree = new HashTreesImplTestObj(
				DEFAULT_SEG_DATA_BLOCKS_COUNT, htStore, store, events);
		HashTreesManager syncManager = new HashTreesManager.Builder(
				"localhost", portNo, hTree, TREE_ID_PROVIDER, syncMgrStore)
				.setFullRebuildPeriod(fullRebuildTimeInterval)
				.schedule(schedPeriod).build();
		store.registerHashTrees(hTree);

		HashTreeSyncManagerComponents components = new HashTreeSyncManagerComponents();
		components.htStore = htStore;
		components.syncMgrImpl = syncManager;
		components.storeImplTest = store;

		return components;
	}

	@Test
	public void testSegmentUpdate() throws InterruptedException {
		BlockingQueue<HTSynchEvent> events = new ArrayBlockingQueue<HTSynchEvent>(
				1000);
		HashTreeSyncManagerComponents components = createHashTreeSyncManager(
				events, HashTreesConstants.DEFAULT_HASH_TREE_SERVER_PORT_NO,
				30 * 1000, 3000000);
		HashTreesManager syncManager = components.syncMgrImpl;
		HashTreesStore hashTreesStore = components.htStore;

		hashTreesStore.setCompleteRebuiltTimestamp(1,
				System.currentTimeMillis());
		syncManager.init();
		waitForTheEvent(events, HTSynchEvent.UPDATE_SEGMENT, 10000);
		syncManager.shutdown();
	}

	@Test
	public void testFullTreeUpdate() throws InterruptedException {
		BlockingQueue<HTSynchEvent> events = new ArrayBlockingQueue<HTSynchEvent>(
				1000);
		HashTreeSyncManagerComponents components = createHashTreeSyncManager(
				events, HashTreesConstants.DEFAULT_HASH_TREE_SERVER_PORT_NO,
				30 * 1000, 3000000);
		HashTreesManager syncManager = components.syncMgrImpl;

		syncManager.init();
		waitForTheEvent(events, HTSynchEvent.UPDATE_FULL_TREE, 10000);
		syncManager.shutdown();
	}

	@Test
	public void testSynch() throws Exception {
		BlockingQueue<HTSynchEvent> localEvents = new ArrayBlockingQueue<HTSynchEvent>(
				10000);
		HashTreeSyncManagerComponents componentsLocal = createHashTreeSyncManager(
				localEvents,
				HashTreesConstants.DEFAULT_HASH_TREE_SERVER_PORT_NO, 3000, 300);
		HashTreesManager localSyncManager = componentsLocal.syncMgrImpl;
		componentsLocal.storeImplTest.put(HashTreesImplTestUtils.randomBytes(),
				HashTreesImplTestUtils.randomBytes());

		BlockingQueue<HTSynchEvent> remoteEvents = new ArrayBlockingQueue<HTSynchEvent>(
				10000);
		HashTreeSyncManagerComponents componentsRemote = createHashTreeSyncManager(
				remoteEvents, 8999, 3000, 300);
		HashTreesManager remoteSyncManager = componentsRemote.syncMgrImpl;

		remoteSyncManager.init();
		localSyncManager.addToSyncList(new RemoteTreeInfo(new ServerName(
				"localhost", 8999), 1));
		localSyncManager.init();

		waitForTheEvent(localEvents, HTSynchEvent.SYNCH, 10000000000l);
		waitForTheEvent(remoteEvents, HTSynchEvent.SYNCH_INITIATED, 10000);
		localSyncManager.shutdown();
		remoteSyncManager.shutdown();
	}
}

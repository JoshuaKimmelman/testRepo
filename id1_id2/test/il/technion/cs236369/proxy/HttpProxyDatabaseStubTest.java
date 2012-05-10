package il.technion.cs236369.proxy;

import static org.junit.Assert.*;

import org.junit.Test;

public class HttpProxyDatabaseStubTest {

	@Test
	public void testHttpProxyDatabaseStub() {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		assertNotNull(db);
	}

	@Test
	public void testAddToDb() {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		db.addToDb("a.b.c", "good: yes", "hi");
		assertTrue(db.contains("a.b.c"));
	}

	@Test
	public void testRemoveFromDb() {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		db.addToDb("a.b.c", "good: yes", "hi");
		assertTrue(db.contains("a.b.c"));
		db.removeFromDb("a.b.c");
		assertFalse(db.contains("a.b.c"));
		db.removeFromDb("a.b.c");
	}

	@Test
	public void testGetHeaders() throws DbException {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		db.addToDb("a.b.c", "good: yes", "hi");
		assertTrue(db.contains("a.b.c"));
		assertEquals("good: yes", db.getHeaders("a.b.c"));
		db.addToDb("a.b.c", "good: no", "hi");
		assertTrue(db.contains("a.b.c"));
		assertEquals("good: no", db.getHeaders("a.b.c"));
		try {
			db.getHeaders("a.b.d");
			assertTrue(false);
		} catch (DbException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testGetBody() throws DbException {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		db.addToDb("a.b.c", "good: yes", "hi");
		assertTrue(db.contains("a.b.c"));
		assertEquals("hi", db.getBody("a.b.c"));
		db.addToDb("a.b.c", "good: no", "hi2");
		assertTrue(db.contains("a.b.c"));
		assertEquals("hi2", db.getBody("a.b.c"));
		try {
			db.getBody("a.b.d");
			assertTrue(false);
		} catch (DbException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testContains() {
		HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
		db.addToDb("a.b.c", "good: yes", "hi");
		assertTrue(db.contains("a.b.c"));
		assertFalse(db.contains("a.b.d"));
	}
	
	@Test
	public void testEquals() {
		Entry e = new Entry("a.b.c", "good: yes", "hi");
		Entry e2 = new Entry("a.b.c", "asd", "asd");
		Entry e3 = new Entry("a.b.d", "good: yes", "hi");
		Entry e4 = new Entry(e.url, "", "");
		assertTrue(e.equals(e));
		assertTrue(e.equals(e2));
		assertFalse(e.equals(e3));
		assertTrue(e.equals(e4));
	}

}

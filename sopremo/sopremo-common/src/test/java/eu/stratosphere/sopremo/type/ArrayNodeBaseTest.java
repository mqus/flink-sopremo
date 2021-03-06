package eu.stratosphere.sopremo.type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;

import eu.stratosphere.core.testing.AssertUtil;

@Ignore
public abstract class ArrayNodeBaseTest<T extends IArrayNode<IJsonNode>> extends JsonNodeTest<T> {

	// protected T node;

	@Before
	public abstract void initArrayNode();

	@Override
	@Before
	public void setUp() {
	}

	@Test
	public void shouldAddNodes() {
		this.node.add(TextNode.valueOf("firstname"));
		this.node.add(TextNode.valueOf("lastname"));

		Assert.assertEquals(TextNode.valueOf("firstname"), this.node.get(this.node.size() - 2));
		Assert.assertEquals(TextNode.valueOf("lastname"), this.node.get(this.node.size() - 1));
	}

	@Test
	public void shouldCalculateTheCorrectSize() {
		final int initialSize = this.node.size();
		final int numberOfNewNodes = 6;

		for (int i = 0; i < numberOfNewNodes; i++)
			this.node.add(TextNode.valueOf("newNode: " + i));

		Assert.assertEquals(initialSize + numberOfNewNodes, this.node.size());
	}

	@Test
	public void shouldClearTheNode() {
		this.node.add(TextNode.valueOf("firstname"));
		this.node.add(TextNode.valueOf("lastname"));

		Assert.assertTrue(this.node.size() != 0);

		this.node.clear();
		Assert.assertEquals(0, this.node.size());
	}

	@Test
	public void shouldCreateIterator() {
		this.node.clear();
		final List<IJsonNode> expected = new ArrayList<IJsonNode>();

		for (int i = 0; i < 10; i++) {
			final IJsonNode value = IntNode.valueOf(i);

			expected.add(value);
			this.node.add(value);
		}

		final Iterator<IJsonNode> it = this.node.iterator();
		AssertUtil.assertIteratorEquals(expected.iterator(), it);
	}

	@Test
	public void shouldRemoveNodes() {

		this.node.add(0, TextNode.valueOf("firstname"));
		this.node.add(1, TextNode.valueOf("lastname"));

		final int initialSize = this.node.size();

		this.node.remove(0);
		Assert.assertEquals(initialSize - 1, this.node.size());

		// index of following nodes should be decremented by 1 after removal of a node
		this.node.remove(0);
		Assert.assertEquals(initialSize - 2, this.node.size());
	}

	@Test
	public void shouldRemoveNothingIfRemoveIndexOutOfRange() {
		// index range of node: 0 to size - 1
		final IArrayNode<IJsonNode> clone = this.node.clone();
		this.node.remove(this.node.size());
		Assert.assertEquals(clone, this.node);
	}

	@Test
	public void shouldReturnCorrectNodesAfterRemoval() {
		final IJsonNode value1 = TextNode.valueOf("firstname");
		final IJsonNode value2 = TextNode.valueOf("lastname");

		final List<IJsonNode> expectedNodes = Lists.newArrayList(this.node);
		this.node.add(0, value1);
		this.node.add(1, value2);

		this.node.remove(0);
		this.node.remove(0);
		Assert.assertEquals(new ArrayNode<IJsonNode>(expectedNodes), this.node);
	}

	@Test
	public void shouldReturnMissingIfGetIndexOutOfRange() {
		// index range of node: 0 to size -1
		Assert.assertSame(MissingNode.getInstance(), this.node.get(this.node.size()));
	}

	@Test
	public void shouldReturnTheCorrectNode() {
		this.node.add(0, TextNode.valueOf("lastname"));
		this.node.add(0, TextNode.valueOf("firstname"));

		Assert.assertEquals(TextNode.valueOf("firstname"), this.node.get(0));
		Assert.assertEquals(TextNode.valueOf("lastname"), this.node.get(1));

	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void shouldThrowExceptionIfAddingWithWrongIndex() {
		this.node.add(this.node.size() + 1, TextNode.valueOf("firstname"));
	}
}
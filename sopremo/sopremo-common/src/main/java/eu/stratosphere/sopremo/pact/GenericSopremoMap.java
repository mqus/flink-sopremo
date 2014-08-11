package eu.stratosphere.sopremo.pact;

import org.apache.flink.api.common.functions.AbstractRichFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

import com.google.common.reflect.TypeToken;

import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.SopremoEnvironment;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IObjectNode;
import eu.stratosphere.sopremo.type.typed.TypedObjectNode;

/**
 * An abstract implementation of the {@link GenericMapper}. GenericSopremoMap provides the functionality to convert the
 * standard input of the GenericMapper to a more manageable representation (the input is converted to a subclass of
 * {@link IJsonNode}).
 */
public abstract class GenericSopremoMap<In extends IJsonNode, Out extends IJsonNode> extends AbstractRichFunction implements
		FlatMapFunction<SopremoRecord, SopremoRecord>, SopremoFunction {
	private EvaluationContext context;

	private JsonCollector<Out> collector;

	private TypedObjectNode typedInputNode;

	@Override
	public final EvaluationContext getContext() {
		return this.context;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.api.record.functions.MapFunction#map(eu.stratosphere.pact.common
	 * .type.PactRecord, eu.stratosphere.api.record.functions.Collector)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void flatMap(final SopremoRecord record, final Collector<SopremoRecord> out) {
		final IJsonNode input = record.getNode();
		if (SopremoUtil.LOG.isTraceEnabled())
			SopremoUtil.LOG.trace(String.format("%s %s", this.getContext().getOperatorDescription(), input));
		this.collector.configure(out);
		try {
			this.map(
				(In) (this.typedInputNode == null ? input : this.typedInputNode.withBackingNode((IObjectNode) input)),
				this.collector);
		} catch (final RuntimeException e) {
			SopremoUtil.LOG.error(String.format(
				"Error occurred @ %s with %s: %s", this.getContext().getOperatorDescription(), input, e));
			throw e;
		}
	}

	@Override
	public void open(final Configuration parameters) {
		SopremoEnvironment.getInstance().load(parameters);
		this.context = SopremoEnvironment.getInstance().getEvaluationContext();
		this.collector = new JsonCollector<Out>(this.context);
		this.typedInputNode =
			SopremoUtil.getTypedNodes(TypeToken.of(this.getClass()).getSupertype(GenericSopremoMap.class))[0];
		SopremoUtil.configureWithTransferredState(this, GenericSopremoMap.class, parameters);
	}

	/**
	 * This method must be implemented to provide a user implementation of a map.
	 * 
	 * @param value
	 *        the {IJsonNode} to be mapped
	 * @param out
	 *        a collector that collects all output nodes
	 */
	protected abstract void map(In value, JsonCollector<Out> out);
}

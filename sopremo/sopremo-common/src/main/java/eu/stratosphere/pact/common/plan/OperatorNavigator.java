package eu.stratosphere.pact.common.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.flink.api.common.operators.IterationOperator;
import org.apache.flink.api.common.operators.Operator;
import org.apache.flink.api.common.operators.base.BulkIterationBase;
import org.apache.flink.api.common.operators.base.DeltaIterationBase;

import eu.stratosphere.util.dag.ConnectionNavigator;

/**
 * {@link ConnectionNavigator} for traversing a graph of {@link Operator<?>}s.
 * 
 * @see ConnectionNavigator
 */
public class OperatorNavigator implements ConnectionNavigator<Operator<?>> {
	/**
	 * The default stateless instance that should be used in most cases.
	 */
	public static final OperatorNavigator INSTANCE = new OperatorNavigator();

	@Override
	public List<Operator<?>> getConnectedNodes(final Operator<?> node) {
		final List<Operator<?>> inputs = new ArrayList<Operator<?>>(OperatorUtil.getInputs(node));
		if (node instanceof IterationOperator) {

			if (node instanceof BulkIterationBase) {
				inputs.add(((BulkIterationBase<?>) node).getPartialSolution());
				inputs.add(((BulkIterationBase<?>) node).getTerminationCriterion());
			}
			else if (node instanceof DeltaIterationBase) {
				inputs.add(((DeltaIterationBase<?, ?>) node).getNextWorkset());
				inputs.add(((DeltaIterationBase<?, ?>) node).getSolutionSetDelta());
			}
			else
				throw new IllegalArgumentException("Unknown node " + node);
		}
		return inputs;
	}
}

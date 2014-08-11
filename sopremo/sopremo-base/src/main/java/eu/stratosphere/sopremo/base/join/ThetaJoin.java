package eu.stratosphere.sopremo.base.join;

import java.util.ArrayList;
import java.util.List;

import org.apache.flink.api.common.operators.Operator;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import eu.stratosphere.pact.common.plan.OperatorUtil;
import eu.stratosphere.pact.common.plan.PactModule;
import eu.stratosphere.sopremo.base.Projection;
import eu.stratosphere.sopremo.base.Selection;
import eu.stratosphere.sopremo.expressions.BooleanExpression;
import eu.stratosphere.sopremo.expressions.ConstantExpression;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.UnaryExpression;
import eu.stratosphere.sopremo.operator.InputCardinality;
import eu.stratosphere.sopremo.operator.Internal;
import eu.stratosphere.sopremo.operator.SopremoModule;
import eu.stratosphere.sopremo.pact.SopremoCross;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.BooleanNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.MissingNode;

@InputCardinality(min = 2, max = 2)
@Internal
public class ThetaJoin extends TwoSourceJoinBase<ThetaJoin> {
	private BooleanExpression condition = new UnaryExpression(new ConstantExpression(true));

	public BooleanExpression getCondition() {
		return this.condition;
	}

	public void setCondition(final BooleanExpression condition) {
		if (condition == null)
			throw new NullPointerException("condition must not be null");

		this.condition = condition;
	}

	public ThetaJoin withCondition(final BooleanExpression condition) {
		this.setCondition(condition);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.operator.ElementaryOperator#asPactModule()
	 */
	@Override
	public PactModule asPactModule() {
		if (getResultProjection() == EvaluationExpression.VALUE) {
			PactModule module = super.asPactModule();
			final PactModule selectionModule = new Selection().withCondition(new UnaryExpression(EvaluationExpression.VALUE)).asPactModule();
			final Operator<SopremoRecord> lastJoinOp = module.getOutput(0).getInput();
			for (Operator<?> operator : selectionModule.getReachableNodes()) {
				List<Operator<?>> inputs = new ArrayList<Operator<?>>(OperatorUtil.getInputs(operator));
				inputs = Lists.transform(inputs, new Function<Operator<?>, Operator<?>>() {
					@Override
					public Operator<?> apply(Operator<?> input) {
						return input == selectionModule.getInput(0) ? lastJoinOp : input;
					}
				});
				OperatorUtil.setInputs(operator, inputs);
			}
			module.getOutput(0).setInput(selectionModule.getOutput(0).getInput());
			return module;
		}
		SopremoModule module = new SopremoModule(2, 1);
		module.getOutput(0).setInputs(
			new Projection().withResultProjection(getResultProjection()).withInputs(
				this.clone().withInputs(module.getInputs()).withResultProjection(EvaluationExpression.VALUE)));
		return module.asElementary().asPactModule();
	}

	public static class Implementation extends SopremoCross {
		private transient final IArrayNode<IJsonNode> inputs = new ArrayNode<IJsonNode>();

		private BooleanExpression condition;

		@Override
		protected IJsonNode cross(final IJsonNode value1, final IJsonNode value2) {
			this.inputs.set(0, value1);
			this.inputs.set(1, value2);
			if (this.condition.evaluate(this.inputs) == BooleanNode.TRUE)
				return this.inputs;
			return MissingNode.getInstance();
		}
	}
}
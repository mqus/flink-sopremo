/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import eu.stratosphere.api.common.operators.base.FileDataSourceBase;
import eu.stratosphere.api.common.operators.base.GenericDataSinkBase;
import eu.stratosphere.api.common.operators.base.GenericDataSourceBase;
import eu.stratosphere.api.java.record.operators.GenericDataSink;
import eu.stratosphere.pact.common.plan.OperatorUtil;
import eu.stratosphere.pact.common.plan.PactModule;
import eu.stratosphere.sopremo.Schema;
import eu.stratosphere.sopremo.SopremoEnvironment;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.io.Sink;
import eu.stratosphere.sopremo.io.Source;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.util.IdentityList;
import eu.stratosphere.util.dag.GraphTraverseListener;
import eu.stratosphere.util.dag.OneTimeTraverser;

/**
 * A {@link SopremoModule} that only contains {@link ElementaryOperator}s.
 */
public class ElementarySopremoModule extends SopremoModule {

	/**
	 * Initializes ElementarySopremoModule.
	 * 
	 * @param numberOfInputs
	 * @param numberOfOutputs
	 */
	public ElementarySopremoModule(final int numberOfInputs, final int numberOfOutputs) {
		super(numberOfInputs, numberOfOutputs);
	}

	/**
	 * Initializes ElementarySopremoModule.
	 */
	ElementarySopremoModule() {
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.SopremoModule#asElementary()
	 */
	@Override
	public ElementarySopremoModule asElementary() {
		return this;
	}

	/**
	 * Converts the Sopremo module to a Pact module.
	 * 
	 * @return the converted Pact module
	 */
	public PactModule asPactModule() {
		return PactModule.valueOf(this.assemblePact());
	}

	/**
	 * Assembles the Pacts of the contained Sopremo operators and returns a list of all Pact sinks. These sinks may
	 * either be directly a {@link GenericDataSource} or an unconnected
	 * {@link eu.stratosphere.api.common.operators.Operator<?>}.
	 * 
	 * @return a list of Pact sinks
	 */
	public Collection<eu.stratosphere.api.common.operators.Operator<?>> assemblePact() {
		// if(layout == null)
		// layout = SopremoRecordLayout.create(this.schema.getKeyExpressions());
		return new PactAssembler().assemble();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.operator.SopremoModule#clone()
	 */
	@Override
	public ElementarySopremoModule clone() {
		final ElementarySopremoModule module =
			new ElementarySopremoModule(this.getNumInputs(), this.getNumOutputs());
		module.copyPropertiesFrom(this);
		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.util.dag.GraphModule#getReachableNodes()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Iterable<? extends ElementaryOperator<?>> getReachableNodes() {
		return (Iterable<? extends ElementaryOperator<?>>) super.getReachableNodes();
	}

	/**
	 */
	public Schema getSchema() {
		final Set<EvaluationExpression> keyExpressions = new HashSet<EvaluationExpression>();
		for (final ElementaryOperator<?> operator : this.getReachableNodes())
			keyExpressions.addAll(operator.getAllKeyExpressions());

		return new Schema(new ArrayList<EvaluationExpression>(keyExpressions));
	}

	/**
	 * Wraps the graph given by the sinks and referenced contracts in a ElementarySopremoModule.
	 * 
	 * @param sinks
	 *        all sinks that span the graph to wrap
	 * @return a ElementarySopremoModule representing the given graph
	 */
	public static ElementarySopremoModule valueOf(final Collection<? extends Operator<?>> sinks) {
		final List<Operator<?>> inputs = findInputs(sinks);
		final ElementarySopremoModule module = new ElementarySopremoModule(inputs.size(), sinks.size());
		connectOutputs(module, sinks);
		connectInputs(module, inputs);
		return module;
	}

	/**
	 * Wraps the graph given by the sinks and referenced contracts in a ElementarySopremoModule.
	 * 
	 * @param sinks
	 *        all sinks that span the graph to wrap
	 * @return a ElementarySopremoModule representing the given graph
	 */
	public static ElementarySopremoModule valueOf(final Operator<?>... sinks) {
		return valueOf(Arrays.asList(sinks));
	}

	/**
	 * Helper class needed to assemble a Pact program from the {@link PactModule}s of several
	 * {@link eu.stratosphere.api.common.operators.Operator<?>}s.
	 */
	private class PactAssembler {
		private final Map<Operator<?>, PactModule> modules = new IdentityHashMap<Operator<?>, PactModule>();

		private final Map<Operator<?>, List<eu.stratosphere.api.common.operators.Operator<SopremoRecord>>> operatorOutputs =
			new IdentityHashMap<Operator<?>, List<eu.stratosphere.api.common.operators.Operator<SopremoRecord>>>();

		public Collection<eu.stratosphere.api.common.operators.Operator<?>> assemble() {
			this.convertDAGToModules();

			this.connectModules();

			final List<eu.stratosphere.api.common.operators.Operator<?>> pactSinks = this.findPACTSinks();

			return pactSinks;
		}

		private eu.stratosphere.api.common.operators.Operator<SopremoRecord> traceOperatorInput(final Operator<?> operator,
				final eu.stratosphere.api.common.operators.Operator<SopremoRecord> input) {
			final int inputIndex =
				new IdentityList<GenericDataSourceBase<?, ?>>(this.modules.get(operator).getInputs()).indexOf(input);
			
			if (inputIndex >= operator.getInputs().size() || inputIndex == -1) 
				return input;
			
			final Operator.Output inputSource = operator.getInputs().get(inputIndex).getSource();
			final eu.stratosphere.api.common.operators.Operator<SopremoRecord> outputtingOperator =
				(eu.stratosphere.api.common.operators.Operator<SopremoRecord>) this.operatorOutputs.get(inputSource.getOperator()).get(inputSource.getIndex());
			if (outputtingOperator instanceof FileDataSourceBase && !(inputSource.getOperator() instanceof Source))
				return traceOperatorInput(inputSource.getOperator(), outputtingOperator);
			return outputtingOperator;
		}

		private void connectModules() {
			for (final Entry<Operator<?>, PactModule> operatorModule : this.modules.entrySet()) {
				final Operator<?> operator = operatorModule.getKey();
				final PactModule module = operatorModule.getValue();

				for (final eu.stratosphere.api.common.operators.Operator<?> contract : module.getReachableNodes()) {
					final List<eu.stratosphere.api.common.operators.Operator<SopremoRecord>> inputs = OperatorUtil.getInputs(contract);

					for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++)
						inputs.set(inputIndex, traceOperatorInput(operator, inputs.get(inputIndex)));
					OperatorUtil.setInputs(contract, inputs);
				}
			}
		}

		private void convertDAGToModules() {
			OneTimeTraverser.INSTANCE.traverse(ElementarySopremoModule.this.getAllOutputs(),
				OperatorNavigator.ELEMENTARY, new GraphTraverseListener<ElementaryOperator<?>>() {
					@Override
					public void nodeTraversed(final ElementaryOperator<?> node) {
						SopremoEnvironment.getInstance().getEvaluationContext().setOperatorDescription(node.getName());
						final PactModule module = node.asPactModule();

						PactAssembler.this.modules.put(node, module);
						final List<GenericDataSinkBase<SopremoRecord>> outputFunctions = module.getOutputs();
						final List<eu.stratosphere.api.common.operators.Operator<SopremoRecord>> outputOperators =
							new ArrayList<eu.stratosphere.api.common.operators.Operator<SopremoRecord>>();
						for (final GenericDataSinkBase<SopremoRecord> sink : outputFunctions)
							outputOperators.add(sink.getInput());
						PactAssembler.this.operatorOutputs.put(node, outputOperators);
					}
				});

			for (final PactModule module : this.modules.values())
				module.validate();
		}

		private List<eu.stratosphere.api.common.operators.Operator<?>> findPACTSinks() {
			final List<eu.stratosphere.api.common.operators.Operator<?>> pactSinks =
				new ArrayList<eu.stratosphere.api.common.operators.Operator<?>>();
			for (final Operator<?> sink : ElementarySopremoModule.this.getAllOutputs())
				for (final GenericDataSinkBase<?> outputFunction : this.modules.get(sink).getAllOutputs())
					if (sink instanceof Sink)
						pactSinks.add(outputFunction);
					else
						pactSinks.add(outputFunction.getInput());
			return pactSinks;
		}
	}
}

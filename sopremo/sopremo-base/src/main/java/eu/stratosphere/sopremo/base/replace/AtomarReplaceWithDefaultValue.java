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
package eu.stratosphere.sopremo.base.replace;

import java.util.Iterator;

import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.PathSegmentExpression;
import eu.stratosphere.sopremo.operator.InputCardinality;
import eu.stratosphere.sopremo.operator.Internal;
import eu.stratosphere.sopremo.operator.Property;
import eu.stratosphere.sopremo.pact.JsonCollector;
import eu.stratosphere.sopremo.pact.SopremoCoGroup;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IStreamNode;

@InputCardinality(min = 2, max = 2)
@Internal
public class AtomarReplaceWithDefaultValue extends AtomarReplaceBase<AtomarReplaceWithDefaultValue> {
	private EvaluationExpression defaultExpression = EvaluationExpression.VALUE;

	public EvaluationExpression getDefaultExpression() {
		return this.defaultExpression;
	}

	@Property
	public void setDefaultExpression(final EvaluationExpression defaultExpression) {
		if (defaultExpression == null)
			throw new NullPointerException("defaultExpression must not be null");

		this.defaultExpression = defaultExpression;
	}

	public AtomarReplaceWithDefaultValue withDefaultExpression(final EvaluationExpression prop) {
		this.setDefaultExpression(prop);
		return this;
	}

	public static class Implementation extends SopremoCoGroup {
		private PathSegmentExpression replaceExpression;

		private EvaluationExpression dictionaryValueExtraction, defaultExpression;

		private transient IJsonNode cloneTarget;

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.pact.SopremoCoGroup#coGroup(eu.stratosphere.sopremo.type.IArrayNode,
		 * eu.stratosphere.sopremo.type.IArrayNode, eu.stratosphere.sopremo.pact.JsonCollector)
		 */
		@Override
		protected void coGroup(final IStreamNode<IJsonNode> values1, final IStreamNode<IJsonNode> values2,
				final JsonCollector<IJsonNode> out) {
			final Iterator<IJsonNode> valueIterator = values1.iterator();
			if (!valueIterator.hasNext())
				return;

			final Iterator<IJsonNode> replaceValueIterator = values2.iterator();
			final IJsonNode replaceValue = replaceValueIterator.hasNext() ?
				this.dictionaryValueExtraction.evaluate(replaceValueIterator.next()) : null;

			while (valueIterator.hasNext()) {
				final IJsonNode value = SopremoUtil.copyInto(valueIterator.next(), this.cloneTarget);
				final IJsonNode replacement;
				if (replaceValue != null)
					replacement = replaceValue;
				else
					replacement = this.defaultExpression.evaluate(value);
				out.collect(this.replaceExpression.set(value, replacement));
			}
		}
	}
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.map;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.ByModulating;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.GroupStepHelper;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ReducingBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.util.TraverserSet;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalUtil;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.function.HashMapSupplier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GroupStep<S, K, V> extends ReducingBarrierStep<S, Map<K, V>> implements TraversalParent, ByModulating {

    private char state = 'k';

    private Traversal.Admin<S, K> keyTraversal = null;
    private Traversal.Admin<S, ?> valueTraversal = this.integrateChild(__.identity().asAdmin());   // used in OLAP
    private Traversal.Admin<?, V> reduceTraversal = this.integrateChild(__.fold().asAdmin());      // used in OLAP
    private Traversal.Admin<S, V> valueReduceTraversal = this.integrateChild(__.fold().asAdmin()); // used in OLTP

    public GroupStep(final Traversal.Admin traversal) {
        super(traversal);
        this.setSeedSupplier(HashMapSupplier.instance());
        this.setReducingBiOperator(new GroupBiOperator<>(this));
    }

    @Override
    public void onGraphComputer() {
        super.onGraphComputer();
        this.setReducingBiOperator(new GroupBiOperator<>());
    }

    @Override
    public Map<K, V> projectTraverser(final Traverser.Admin<S> traverser) {
        final K key = TraversalUtil.applyNullable(traverser, this.keyTraversal);
        final TraverserSet traverserSet = new TraverserSet();
        if (this.onGraphComputer) {
            this.valueTraversal.reset();
            this.valueTraversal.addStart(traverser);
            this.valueTraversal.getEndStep().forEachRemaining(t -> traverserSet.add(t.asAdmin()));
        } else
            traverserSet.add(traverser);

        return Collections.singletonMap(key, (V) traverserSet);
    }

    @Override
    public List<Traversal.Admin<?, ?>> getLocalChildren() {
        final List<Traversal.Admin<?, ?>> children = new ArrayList<>(4);
        if (null != this.keyTraversal)
            children.add((Traversal.Admin) this.keyTraversal);
        children.add(this.valueReduceTraversal);
        children.add(this.valueTraversal);
        children.add(this.reduceTraversal);
        return children;
    }

    @Override
    public void modulateBy(final Traversal.Admin<?, ?> kvTraversal) {
        if ('k' == this.state) {
            this.keyTraversal = this.integrateChild(kvTraversal);
            this.state = 'v';
        } else if ('v' == this.state) {
            this.valueReduceTraversal = this.integrateChild(GroupStepHelper.convertValueTraversal(kvTraversal));
            final List<Traversal.Admin<?, ?>> splitTraversal = GroupStepHelper.splitOnBarrierStep(this.valueReduceTraversal);
            this.valueTraversal = this.integrateChild(splitTraversal.get(0));
            this.reduceTraversal = this.integrateChild(splitTraversal.get(1));
            this.state = 'x';
        } else {
            throw new IllegalStateException("The key and value traversals for group()-step have already been set: " + this);
        }
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.getSelfAndChildRequirements(TraverserRequirement.OBJECT, TraverserRequirement.BULK);
    }

    @Override
    public GroupStep<S, K, V> clone() {
        final GroupStep<S, K, V> clone = (GroupStep<S, K, V>) super.clone();
        if (null != this.keyTraversal)
            clone.keyTraversal = clone.integrateChild(this.keyTraversal.clone());
        clone.valueReduceTraversal = clone.integrateChild(this.valueReduceTraversal.clone());
        clone.valueTraversal = clone.integrateChild(this.valueTraversal.clone());
        clone.reduceTraversal = clone.integrateChild(this.reduceTraversal.clone());
        return clone;
    }

    @Override
    public int hashCode() {
        int result = this.valueReduceTraversal.hashCode();
        if (this.keyTraversal != null) result ^= this.keyTraversal.hashCode();
        return result;
    }


    @Override
    public String toString() {
        return StringFactory.stepString(this, this.keyTraversal, this.valueReduceTraversal);
    }

    @Override
    public Map<K, V> generateFinalReduction(final Object traverserMap) {
        final Map<K, V> reducedMap = new HashMap<>();
        if (this.onGraphComputer) {
            for (final K key : ((Map<K, TraverserSet>) traverserMap).keySet()) {
                final Traversal.Admin<?, V> reduceClone = this.reduceTraversal.clone();
                reduceClone.addStarts(((Map<K, TraverserSet>) traverserMap).get(key).iterator());
                reducedMap.put(key, reduceClone.next());
            }
        } else {
            for (final K key : ((Map<K, Traversal.Admin>) traverserMap).keySet()) {
                reducedMap.put(key, (V) (((Map<K, Traversal.Admin>) traverserMap).get(key)).next());
            }
        }
        return reducedMap;
    }

    ///////////

    public static final class GroupBiOperator<K, V> implements BinaryOperator<Map<K, V>>, Serializable {

        private transient GroupStep groupStep;
        private final boolean onGraphComputer;

        public GroupBiOperator(final GroupStep groupStep) {
            this.groupStep = groupStep;
            this.onGraphComputer = false;
        }

        public GroupBiOperator() {
            this.onGraphComputer = true;
        }

        @Override
        public Map<K, V> apply(final Map<K, V> mutatingSeed, final Map<K, V> map) {
            for (final K key : map.keySet()) {
                if (this.onGraphComputer) {
                    TraverserSet<?> traverserSet = (TraverserSet) mutatingSeed.get(key);
                    if (null == traverserSet) {
                        traverserSet = new TraverserSet<>();
                        mutatingSeed.put(key, (V) traverserSet);
                    }
                    traverserSet.addAll((TraverserSet) map.get(key));
                } else {
                    final TraverserSet<?> traverserSet = (TraverserSet<?>) map.get(key);
                    Traversal.Admin valueReduceTraversal = (Traversal.Admin) mutatingSeed.get(key);
                    if (null == valueReduceTraversal) {
                        valueReduceTraversal = this.groupStep.valueReduceTraversal.clone();
                        mutatingSeed.put(key, (V) valueReduceTraversal);
                    }
                    traverserSet.forEach(valueReduceTraversal::addStart);
                }
            }
            return mutatingSeed;
        }
    }
}